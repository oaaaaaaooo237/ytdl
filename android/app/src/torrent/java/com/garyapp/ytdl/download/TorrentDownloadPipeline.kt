package com.garyapp.ytdl.download

import android.content.Context
import android.net.Uri
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.storage.ExportController
import com.garyapp.ytdl.torrent.TorrentEngine
import com.garyapp.ytdl.torrent.isTorrentMagnet
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Uses the same durable queue, service, checkpoints, history and exports as every download. */
internal class TorrentDownloadPipeline(private val context: Context) {
    fun run(request: DownloadRequest, outputDirectory: File, cancellation: DownloadCancellation,
            onStateChanged: (DownloadTaskState) -> Unit): DownloadPipelineResult {
        val route = requireNotNull(request.route as? DownloadRoute.Torrent)
        var state = DownloadTaskState.waiting(request)
        var checkpoint: PipelineCheckpoint? = null
        fun publish(next: DownloadTaskState) {
            state = next
            checkpoint?.save(next.stage, next.progress, next.stopReason)
            onStateChanged(next)
        }
        fun stopped(): DownloadPipelineResult {
            val reason = cancellation.stopReason ?: DownloadStopReason.LifecycleEnd
            val next = if (reason == DownloadStopReason.UserCancel) state.canceled()
                else state.copy(stage = if (reason == DownloadStopReason.NetworkUnavailable) DownloadStage.WaitingForNetwork else DownloadStage.Paused,
                    stopReason = reason)
            publish(next)
            return DownloadPipelineResult(next, emptyList())
        }
        try {
            checkpoint = PipelineCheckpoint.open(TaskWorkspace(outputDirectory), request)
            val task = checkpoint.directory
            ExportController.markPrivateTaskStarted(task).getOrThrow()
            state = state.copy(progress = checkpoint.progress())
            publish(state.copy(stage = DownloadStage.DownloadingFile, statusMessage = "正在连接种子节点…"))
            val guard = ControlledDirectory(task)
            val metadataFile = guard.child(task, "source.torrent")
            val payload = guard.child(task, "payload").also { check(it.mkdirs() || it.isDirectory) }
            val legacySource = if (Uri.parse(route.source).scheme == "file") {
                val root = File(context.filesDir, "torrents").canonicalFile
                val file = File(requireNotNull(Uri.parse(route.source).path))
                require(file.absoluteFile == file.canonicalFile && file.name == "source.torrent" && file.path.startsWith(root.path + File.separator))
                file
            } else null
            TorrentEngine().use { engine ->
                if (cancellation.isCancellationRequested) return stopped()
                val metadata = if (metadataFile.isFile) readMetadata(metadataFile.inputStream()) else {
                    val bytes = when {
                        isTorrentMagnet(route.source) -> engine.loadMagnet(route.source, payload) { cancellation.isCancellationRequested }
                        legacySource != null -> readMetadata(legacySource.inputStream())
                        Uri.parse(route.source).scheme == "content" -> readMetadata(requireNotNull(context.contentResolver.openInputStream(Uri.parse(route.source))))
                        else -> {
                            val url = requireNotNull(UrlPolicy.evaluate(route.source).rawUrlForExecution)
                            val connection = URL(url).openConnection() as HttpURLConnection
                            try {
                                connection.connectTimeout = 15_000
                                connection.readTimeout = 15_000
                                require(connection.responseCode == 200) { "无法读取种子文件。" }
                                readMetadata(connection.inputStream)
                            } finally { connection.disconnect() }
                        }
                    }
                    AtomicSnapshotWriter().write(metadataFile, bytes, guard)
                    bytes
                }
                if (cancellation.isCancellationRequested) return stopped()
                if (legacySource != null) {
                    val oldPayload = File(legacySource.parentFile, "payload")
                    if (oldPayload.isDirectory) copyLegacyPayload(oldPayload, payload, cancellation)
                }
                val savedMagnet = legacySource?.parentFile?.let { File(it, "magnet.txt") }
                    ?.takeIf { it.isFile && it.length() <= 16_384 }?.readText()?.takeIf(::isTorrentMagnet)
                val info = engine.download(metadata, payload, route.source.takeIf(::isTorrentMagnet) ?: savedMagnet) {}
                while (true) {
                    if (cancellation.isCancellationRequested) return stopped()
                    val progress = engine.progress(info)
                    if (progress != null) {
                        val total = progress.totalBytes
                        publish(state.copy(statusMessage = null, progress = DownloadProgress("downloading",
                            if (total > 0) progress.downloadedBytes * 100.0 / total else null,
                            progress.downloadedBytes, total.takeIf { it > 0 }, progress.bytesPerSecond.toDouble(), null, null)))
                        if (progress.finished) {
                            val files = info.files()
                            val outputs = (0 until files.numFiles()).filterNot(files::padFileAt).map { index ->
                                val file = File(payload, files.filePath(index))
                                guard.checkSafe(file)
                                check(file.isFile && file.length() == files.fileSize(index))
                                DownloadOutputFile(DownloadOutputKind.File, file.path, file.length(), outputDirectory.canonicalPath,
                                    mimeType = ExportController.mimeTypeFor(file.name))
                            }
                            check(outputs.isNotEmpty())
                            checkpoint.record("torrent", File(outputs.first().path))
                            val completed = state.completeWith(outputs).getOrThrow()
                            publish(completed)
                            return DownloadPipelineResult(completed, outputs)
                        }
                    }
                    Thread.sleep(500)
                }
            }
        } catch (error: Exception) {
            if (cancellation.isCancellationRequested) return stopped()
            publish(state.failed("种子下载失败，请检查来源或节点后重试。"))
            return DownloadPipelineResult(state, emptyList())
        }
    }

    private fun copyLegacyPayload(source: File, target: File, cancellation: DownloadCancellation) {
        val sourceGuard = ControlledDirectory(source)
        val targetGuard = ControlledDirectory(target)
        fun copy(directory: File, destination: File) {
            sourceGuard.checkSafe(directory)
            check(destination.mkdirs() || destination.isDirectory)
            targetGuard.checkSafe(destination)
            directory.listFiles().orEmpty().forEach { file ->
                sourceGuard.checkSafe(file)
                val output = targetGuard.child(destination, file.name)
                check(!cancellation.isCancellationRequested)
                if (file.isDirectory) copy(file, output)
                else if (!output.exists()) file.inputStream().use { input -> output.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        check(!cancellation.isCancellationRequested)
                        val count = input.read(buffer)
                        if (count < 0) break
                        out.write(buffer, 0, count)
                    }
                } }
            }
        }
        copy(source, target)
    }

    private fun readMetadata(input: InputStream): ByteArray = input.use {
        val buffer = ByteArray(8192)
        val result = java.io.ByteArrayOutputStream()
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            require(result.size() + count <= TorrentEngine.MaxMetadataBytes)
            result.write(buffer, 0, count)
        }
        result.toByteArray().also { bytes -> require(bytes.isNotEmpty()) }
    }
}

internal fun restoreLegacyTorrentDownloads(context: Context) {
    val preferences = context.getSharedPreferences("torrent-exports", Context.MODE_PRIVATE)
    val migrated = preferences.getStringSet("unified_migrated_ids", emptySet()).orEmpty().toMutableSet()
    val ids = linkedSetOf<String>()
    preferences.getString("pending_id", null)?.let(ids::add)
    runCatching {
        val outputs = org.json.JSONArray(preferences.getString("latest_outputs", "[]"))
        for (index in 0 until outputs.length()) ids.add(outputs.getString(index).substringBefore('/').substringBefore('\\'))
    }
    for (id in ids) {
        if (id in migrated || runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false).not()) continue
        val source = File(File(context.filesDir, "torrents/$id"), "source.torrent")
        if (!source.isFile || source.length() !in 1..TorrentEngine.MaxMetadataBytes.toLong()) continue
        val url = Uri.fromFile(source).toString()
        val request = DownloadRequest(url, "Torrent", DownloadRoute.Torrent(url), taskId = id.replace("-", ""),
            usesCookies = false, formatSummary = "Torrent", storageTarget = com.garyapp.ytdl.core.storage.StorageTarget.AppPrivate)
        if (DownloadCoordinator.startForegroundDownload(context, request, File(context.filesDir, "gui-downloads")).isSuccess) {
            migrated.add(id)
            check(preferences.edit().putStringSet("unified_migrated_ids", migrated).commit())
        }
    }
}
