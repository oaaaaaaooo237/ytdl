package com.garyapp.ytdl.core.files

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

internal object ParallelHttpFileDownload {
    fun tryDownload(
        metadata: FileMetadata,
        target: File,
        shouldStop: () -> Boolean,
        onProgress: (Long, Long?) -> Unit,
    ): FileDownloadResult? {
        val total = metadata.totalBytes?.takeIf { it > 0 } ?: return null
        val etag = metadata.etag ?: return null
        val ranges = HttpDownloadRanges.plan(total, 4)
        if (ranges.size == 1) return null

        val temp = File(target.parentFile, target.name + ".segments")
        if (temp.canonicalFile != temp.absoluteFile || temp.isDirectory) return null
        if (temp.exists() && !temp.delete()) return null
        if (!temp.createNewFile()) return null

        val aborted = AtomicBoolean()
        val bytesWritten = AtomicLong()
        val partBytes = AtomicLongArray(ranges.size)
        val finished = CountDownLatch(ranges.size)
        val workers = Executors.newFixedThreadPool(ranges.size)
        return try {
            RandomAccessFile(temp, "rw").use { it.setLength(total) }
            val jobs = ranges.mapIndexed { index, range ->
                workers.submit<Boolean> {
                    val done = runCatching {
                        val connection = openFileConnection(metadata.finalUrl, range.first, etag, range.last)
                        try {
                            val size = range.last - range.first + 1
                            val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                            val declaredLength = contentLength(connection)
                            if (connection.responseCode != 206 || !identityEncoding(connection) ||
                                (declaredLength != null && declaredLength != size) ||
                                strongEtag(connection.getHeaderField("ETag")) != etag ||
                                connection.getHeaderField("Content-Range") != "bytes ${range.first}-${range.last}/$total" ||
                                isWebPage(mime, byteArrayOf())
                            ) return@runCatching false
                            connection.inputStream.use { input ->
                                RandomAccessFile(temp, "rw").use { output ->
                                    output.seek(range.first)
                                    var remaining = size
                                    val buffer = ByteArray(128 * 1024)
                                    while (remaining > 0 && !aborted.get() && !shouldStop()) {
                                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                        if (count < 0) return@runCatching false
                                        if (remaining == size && range.first == 0L && isWebPage(mime, buffer.copyOf(count))) {
                                            return@runCatching false
                                        }
                                        if (!HttpTransferLimit.account(count) { aborted.get() || shouldStop() }) {
                                            return@runCatching false
                                        }
                                        output.write(buffer, 0, count)
                                        remaining -= count
                                        bytesWritten.addAndGet(count.toLong())
                                        partBytes.addAndGet(index, count.toLong())
                                    }
                                    remaining == 0L && input.read() == -1
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                    }.getOrDefault(false)
                    if (!done) aborted.set(true)
                    finished.countDown()
                    done
                }
            }
            var reported = 0L
            while (!finished.await(350, TimeUnit.MILLISECONDS)) {
                if (aborted.get() || shouldStop()) continue
                val written = bytesWritten.get()
                if (written > reported && written < total) {
                    onProgress(written, total)
                    reported = written
                }
            }
            val results = jobs.map { it.get() }
            if (reported > 0 && results.any { !it }) onProgress(0, total)
            when {
                shouldStop() -> {
                    var prefix = 0L
                    for ((index, range) in ranges.withIndex()) {
                        val written = partBytes.get(index)
                        prefix += written
                        if (written != range.last - range.first + 1) break
                    }
                    if (prefix > 0) {
                        RandomAccessFile(temp, "rw").use {
                            it.setLength(prefix)
                            it.fd.sync()
                        }
                        check(!target.exists() && temp.renameTo(target))
                    }
                    FileDownloadResult.Stopped(prefix)
                }
                results.any { !it } -> null
                else -> {
                    RandomAccessFile(temp, "rw").use { it.fd.sync() }
                    check(!target.exists() && temp.renameTo(target))
                    FileDownloadResult.Completed(total)
                }
            }
        } finally {
            aborted.set(true)
            workers.shutdown()
            if (finished.count == 0L) temp.delete()
        }
    }
}
