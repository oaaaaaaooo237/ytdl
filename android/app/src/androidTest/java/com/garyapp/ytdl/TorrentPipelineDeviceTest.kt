package com.garyapp.ytdl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.garyapp.ytdl.download.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic native-engine check. No external trackers or peers are required. */
@RunWith(AndroidJUnit4::class)
class TorrentPipelineDeviceTest {
    @Test fun stoppedTaskResumesAndProducesVerifiedFileThroughSharedPipeline() {
        org.junit.Assume.assumeTrue("Requires the Torrent build", BuildConfig.TORRENT_ENABLED)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "torrent-pipeline-${System.nanoTime()}")
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 500 }
        val address = "http://127.0.0.1:${server.localPort}"
        val pieces = ByteArrayOutputStream().apply {
            for (offset in bytes.indices step 16384) {
                write(MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(offset, minOf(offset + 16384, bytes.size))))
            }
        }.toByteArray()
        val seed = ByteArrayOutputStream().apply {
            write("d4:infod6:lengthi${bytes.size}e4:name11:fixture.bin12:piece lengthi16384e6:pieces${pieces.size}:".toByteArray())
            write(pieces)
            write("e8:url-list${"$address/fixture.bin".toByteArray().size}:$address/fixture.bine".toByteArray())
        }.toByteArray()
        val running = AtomicBoolean(true)
        val serving = Thread({
            while (running.get()) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        val headers = mutableListOf<String>()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            headers.add(line)
                        }
                        val data = if (request.contains("/fixture.torrent")) seed else bytes
                        val span = headers.firstOrNull { it.startsWith("Range:", true) }?.substringAfter("bytes=")
                        val start = span?.substringBefore('-')?.toIntOrNull() ?: 0
                        val end = span?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(data.lastIndex) ?: data.lastIndex
                        val body = data.copyOfRange(start, end + 1)
                        val response = buildString {
                            append("HTTP/1.1 ${if (span == null) "200 OK" else "206 Partial Content"}\r\n")
                            append("Content-Length: ${body.size}\r\nContent-Type: application/octet-stream\r\n")
                            if (span != null) append("Content-Range: bytes $start-$end/${data.size}\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        socket.getOutputStream().apply {
                            write(response.toByteArray())
                            for (offset in body.indices step 8192) {
                                write(body, offset, minOf(8192, body.size - offset))
                                flush()
                                if (!request.contains("/fixture.torrent")) Thread.sleep(5)
                            }
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Recheck the test shutdown flag.
                } catch (_: java.net.SocketException) {
                    // The native client can close a web-seed request as soon as its range is complete.
                } catch (error: Exception) {
                    if (running.get()) throw error
                }
            }
        }, "torrent-test-web-seed").apply { isDaemon = true; start() }
        var descriptor: File? = null
        try {
            val source = "$address/fixture.torrent"
            val request = DownloadRequest(source, "本地测试种子", DownloadRoute.Torrent(source), usesCookies = false)
            descriptor = File(context.filesDir, "torrent-descriptors/${request.taskId}.torrent")
            val pipeline = TorrentDownloadPipeline(context)
            val pauseRequested = AtomicBoolean(false)
            val firstDeadline = System.nanoTime() + 30_000_000_000L
            val stopped = object : DownloadCancellation {
                override val isCancellationRequested get() = pauseRequested.get() || System.nanoTime() >= firstDeadline
                override val stopReason get() = if (pauseRequested.get()) DownloadStopReason.UserPause
                    else if (isCancellationRequested) DownloadStopReason.LifecycleEnd else null
            }
            val paused = pipeline.run(request, root, stopped) { state ->
                val downloaded = state.progress?.downloadedBytes ?: 0
                if (downloaded > 0 && downloaded < bytes.size) pauseRequested.set(true)
            }
            assertTrue("The pause must happen after receiving payload bytes", pauseRequested.get())
            assertEquals(DownloadStage.Paused, paused.state.stage)
            assertTrue(requireNotNull(paused.state.progress?.downloadedBytes) > 0)
            val deadline = System.nanoTime() + 30_000_000_000L
            val timeout = object : DownloadCancellation {
                override val isCancellationRequested get() = System.nanoTime() >= deadline
            }
            val result = pipeline.run(request, root, timeout) {}
            assertEquals(result.state.errorMessage, DownloadStage.Completed, result.state.stage)
            assertArrayEquals(bytes, File(result.outputs.single().path).readBytes())
            assertEquals(request.taskId, result.state.request?.taskId)
            val item = com.garyapp.ytdl.ui.HistoryUiItem(1L, "种子", "Torrent", "完成", "",
                com.garyapp.ytdl.data.HistoryItemEntity.STATUS_COMPLETED, 1L, taskId = request.taskId)
            val retained = com.garyapp.ytdl.ui.historySourceTarget(item, RetryDownloadDraft.fromRequest(request), context.filesDir)
                as com.garyapp.ytdl.ui.HistorySourceTarget.TorrentFile
            assertArrayEquals(seed, retained.file.readBytes())
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", retained.file)
            context.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(seed, it.readBytes()) }
        } finally {
            running.set(false)
            server.close()
            serving.join(2000)
            root.deleteRecursively()
            descriptor?.delete()
        }
    }
}
