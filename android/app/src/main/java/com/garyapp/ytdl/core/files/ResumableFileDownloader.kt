package com.garyapp.ytdl.core.files

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.Properties

/** Owns only the supplied task workspace. Restart confirmation and workspace deletion belong to the caller. */
class ResumableFileDownloader {
    fun download(metadata: FileMetadata, partialFile: File, checkpointFile: File,
                 shouldStop: () -> Boolean = { false },
                 onProgress: (Long, Long?) -> Unit = { _, _ -> }): FileDownloadResult {
        fun restart(reason: String) = FileDownloadResult.RestartRequired(reason)
        if (!safePaths(partialFile, checkpointFile)) return restart("下载工作区路径不安全")
        val binding = metadataBinding(metadata)
        val existing = partialFile.exists() || checkpointFile.exists()
        var downloaded = 0L
        if (existing) {
            if (!partialFile.isFile || !checkpointFile.isFile) return restart("下载文件或恢复记录缺失")
            val properties = runCatching { Properties().apply { checkpointFile.inputStream().use { load(it) } } }.getOrNull()
                ?: return restart("恢复记录损坏")
            downloaded = properties.getProperty("bytes")?.toLongOrNull() ?: return restart("恢复记录损坏")
            if (properties.getProperty("binding") != binding || downloaded < 0 || partialFile.length() != downloaded)
                return restart("文件信息或本地文件长度已变化")
            if (metadata.totalBytes != null && downloaded > metadata.totalBytes) return restart("本地文件超过预期长度")
            if (metadata.etag == null && metadata.lastModified == null) return restart("源文件缺少可靠身份，无法安全续传")
        }
        if (shouldStop()) return FileDownloadResult.Stopped(downloaded)
        if (!existing) {
            val parallelResult = try {
                ParallelHttpFileDownload.tryDownload(metadata, partialFile, shouldStop, onProgress)
            } catch (_: java.io.IOException) {
                if (shouldStop()) FileDownloadResult.Stopped(0) else null
            }
            if (parallelResult is FileDownloadResult.Completed) {
                writeCheckpoint(checkpointFile, binding, parallelResult.bytes)
                onProgress(parallelResult.bytes, metadata.totalBytes)
            }
            if (parallelResult is FileDownloadResult.Stopped && parallelResult.bytes > 0) {
                writeCheckpoint(checkpointFile, binding, parallelResult.bytes)
                onProgress(parallelResult.bytes, metadata.totalBytes)
            }
            if (parallelResult != null) return parallelResult
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = openFileConnection(metadata.finalUrl, if (existing) downloaded else null, metadata.etag ?: metadata.lastModified)
            val c = connection
            if (!identityEncoding(c)) return restart("服务器内容编码已变化")
            val tag = strongEtag(c.getHeaderField("ETag"))
            val modified = reliableLastModified(c)
            if ((metadata.etag != null && tag != metadata.etag) ||
                (metadata.etag == null && metadata.lastModified != null && modified != metadata.lastModified))
                return restart("源文件身份已变化")
            val status = c.responseCode
            var expectedEnd: Long? = null
            if (existing) {
                if (status == 416) {
                    val total = Regex("bytes \\*/(\\d+)").matchEntire(c.getHeaderField("Content-Range") ?: "")?.groupValues?.get(1)?.toLongOrNull()
                    return if (metadata.totalBytes != null && downloaded == metadata.totalBytes && total == downloaded)
                        FileDownloadResult.Completed(downloaded) else restart("服务器拒绝续传范围")
                }
                if (status != 206) return restart("服务器未接受断点范围，请确认重新下载")
                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(c.getHeaderField("Content-Range") ?: "")
                    ?: return restart("服务器返回错误的续传范围")
                val start = range.groupValues[1].toLongOrNull()
                val end = range.groupValues[2].toLongOrNull()
                val total = range.groupValues[3].toLongOrNull()
                if (start != downloaded || end == null || total == null || end < downloaded || end != total - 1 ||
                    (metadata.totalBytes != null && total != metadata.totalBytes) ||
                    (contentLength(c) != null && contentLength(c) != end - downloaded + 1))
                    return restart("服务器返回的范围或总长度已变化")
                expectedEnd = end + 1
            } else {
                if (status != 200) return FileDownloadResult.Failed("服务器拒绝下载（$status）")
                if (metadata.totalBytes != null && contentLength(c) != metadata.totalBytes) return restart("源文件总长度已变化")
                expectedEnd = contentLength(c)
            }
            val mime = c.getHeaderField("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            if (isWebPage(mime, byteArrayOf())) return restart("链接返回网页或登录页面")
            c.inputStream.use { input ->
                val buffer = ByteArray(256 * 1024)
                // Inspect before creating or appending a file, even for mislabeled login pages.
                val prefix = readPrefix(input, shouldStop)
                if (shouldStop()) return FileDownloadResult.Stopped(downloaded)
                prefix.copyInto(buffer)
                var count = prefix.size
                if (downloaded == 0L && count > 0 && isWebPage(mime, buffer.copyOf(count))) return restart("链接返回网页或登录页面")
                if (shouldStop()) return FileDownloadResult.Stopped(downloaded)
                if (!existing && !partialFile.createNewFile()) return restart("目标文件已存在")
                FileOutputStream(partialFile, true).use { output ->
                    if (!existing) writeCheckpoint(checkpointFile, binding, downloaded)
                    while (count >= 0) {
                        if (shouldStop()) return FileDownloadResult.Stopped(downloaded)
                        if (count > 0) {
                            if (expectedEnd != null && downloaded + count > expectedEnd) return FileDownloadResult.Failed("服务器数据超过声明长度")
                            if (!HttpTransferLimit.account(count, shouldStop)) return FileDownloadResult.Stopped(downloaded)
                            output.write(buffer, 0, count)
                            output.fd.sync()
                            downloaded += count
                            writeCheckpoint(checkpointFile, binding, downloaded)
                            onProgress(downloaded, metadata.totalBytes)
                        }
                        if (shouldStop()) return FileDownloadResult.Stopped(downloaded)
                        // Aggregate network fragments so durable snapshots are per block,
                        // rather than one fsync and rename for every small network packet.
                        count = 0
                        while (count < buffer.size && !shouldStop()) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) { if (count == 0) count = -1; break }
                            count += read
                        }
                    }
                }
            }
            if (expectedEnd != null && downloaded != expectedEnd) FileDownloadResult.Failed("服务器数据提前结束，可稍后重试")
            else if (metadata.totalBytes != null && downloaded != metadata.totalBytes) FileDownloadResult.Failed("文件长度不完整，可稍后重试")
            else { onProgress(downloaded, metadata.totalBytes); FileDownloadResult.Completed(downloaded) }
        } catch (_: Exception) {
            if (shouldStop()) FileDownloadResult.Stopped(downloaded) else FileDownloadResult.Failed("下载中断，可稍后重试")
        } finally { connection?.disconnect() }
    }

    private fun metadataBinding(metadata: FileMetadata): String {
        // A digest binds the encrypted request held by the caller without storing its URL here.
        val fields = listOf(metadata.sourceUrl, metadata.finalUrl, metadata.fileName, metadata.mimeType,
            metadata.totalBytes?.toString(), metadata.etag, metadata.lastModified, metadata.kind.name)
        val input = fields.joinToString("") { value -> if (value == null) "-1:" else "${value.length}:$value" }
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun safePaths(partial: File, checkpoint: File): Boolean = runCatching {
        val a = partial.absoluteFile
        val b = checkpoint.absoluteFile
        val temporary = File(b.parentFile, b.name + ".tmp")
        a != b && a != temporary && a.parentFile == b.parentFile && a.parentFile?.isDirectory == true &&
            listOf(a, b, temporary).all { it.canonicalFile == it.absoluteFile && (!it.exists() || it.isFile) } &&
            !temporary.exists()
    }.getOrDefault(false)

    private fun writeCheckpoint(file: File, binding: String, bytes: Long) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        check(temporary.createNewFile())
        try {
            FileOutputStream(temporary).use { output ->
                Properties().apply { setProperty("binding", binding); setProperty("bytes", bytes.toString()) }.store(output, null)
                output.fd.sync()
            }
            // Same-directory rename is atomic on Android/Linux and replaces the prior checkpoint.
            if (!temporary.renameTo(file)) {
                // Android uses POSIX rename, including API 24/25. Windows needs atomic replace.
                check(System.getProperty("os.name").orEmpty().startsWith("Windows"))
                java.nio.file.Files.move(temporary.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }
}
