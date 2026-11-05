package com.garyapp.ytdl.core.files

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class HttpFilesTest {
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val bytes = ByteArray(100000) { (it % 251).toByte() }
    private var behavior = "normal"
    private var tag = "\"one\""
    private var mime = "application/octet-stream"
    private var disposition: String? = null
    private var modified: String? = null
    private var requestedRange: String? = null
    private var requestedValidator: String? = null
    private lateinit var url: String

    @Before fun setup() {
        dir = Files.createTempDirectory("http-files").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file.bin") { exchange -> respond(exchange) }
        server.createContext("/redirect") { e -> e.responseHeaders.add("Location", "/file.bin"); e.sendResponseHeaders(302, -1); e.close() }
        server.createContext("/loop") { e -> e.responseHeaders.add("Location", "/loop"); e.sendResponseHeaders(302, -1); e.close() }
        server.start()
        url = "http://127.0.0.1:${server.address.port}/file.bin"
    }
    @After fun cleanup() { server.stop(0); dir.deleteRecursively() }

    private fun respond(e: HttpExchange) {
        try {
            e.responseHeaders.add("Content-Type", mime)
            if (tag.isNotEmpty()) e.responseHeaders.add("ETag", tag)
            disposition?.let { e.responseHeaders.add("Content-Disposition", it) }
            modified?.let { e.responseHeaders.add("Last-Modified", it) }
            if (behavior == "encoded") e.responseHeaders.add("Content-Encoding", "gzip")
            requestedRange = e.requestHeaders.getFirst("Range")
            requestedValidator = e.requestHeaders.getFirst("If-Range")
            e.responseHeaders.add("Accept-Ranges", "bytes")
            val start = e.requestHeaders.getFirst("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
            if (behavior == "error") { e.sendResponseHeaders(403, -1); return }
            if (behavior == "416") {
                e.responseHeaders.add("Content-Range", "bytes */${bytes.size}")
                e.sendResponseHeaders(416, -1); return
            }
            val offset = if (start != null && behavior != "ignore") start else 0
            if (start != null && behavior != "ignore") {
                e.responseHeaders.add("Content-Range", "bytes ${if (behavior == "bad-range") offset + 1 else offset}-${if (behavior == "short-range") offset + 99 else bytes.lastIndex}/${bytes.size}")
            }
            val length = if (behavior == "empty") 0 else if (behavior == "short-range" && offset > 0) 100 else bytes.size - offset
            e.sendResponseHeaders(if (start != null && behavior != "ignore") 206 else 200, if (behavior == "unknown") 0 else if (length == 0) -1 else length.toLong())
            if (length > 0) {
                if (behavior == "html-body" || behavior == "fragmented-html") {
                    val body = "<!doctype html><html>login</html>".toByteArray()
                    if (behavior == "fragmented-html") { e.responseBody.write(body, 0, 1); e.responseBody.flush(); Thread.sleep(80); e.responseBody.write(body, 1, body.size - 1) }
                    else e.responseBody.write(body)
                }
                else e.responseBody.write(bytes, offset, if (behavior == "truncated") length / 2 else length)
            }
        } finally { e.close() }
    }
    private fun metadata(): FileMetadata = (HttpFileProbe().probe(url) as FileProbeResult.FileFound).metadata
    private fun download(m: FileMetadata, stop: () -> Boolean = { false }, progress: (Long, Long?) -> Unit = { _, _ -> }): FileDownloadResult =
        ResumableFileDownloader().download(m, File(dir, "body.part"), File(dir, "checkpoint"), stop, progress)
    private fun paused(m: FileMetadata): ByteArray {
        var stop = false
        val result = download(m, { stop }) { count, _ -> if (count > 0) stop = true }; assertTrue(result.toString(), result is FileDownloadResult.Stopped)
        return File(dir, "body.part").readBytes()
    }

    @Test fun filenameUsesExtendedUnicodeAndStripsPaths() {
        disposition = "attachment; filename=ignored.bin; filename*=UTF-8''..%2F%E4%B8%AD%E6%96%87%3A%00.zip"
        assertEquals("中文__.zip", metadata().fileName)
    }
    @Test fun filenameUsesPlainHeader() { disposition = "attachment; filename=\"C:\\temp\\report.pdf\""; assertEquals("report.pdf", metadata().fileName) }
    @Test fun htmlIsNotAFileEvenWithAttachment() { mime = "text/html"; disposition = "attachment; filename=x.zip"; assertTrue(HttpFileProbe().probe(url) is FileProbeResult.NotFile) }
    @Test fun errorIsRejected() { behavior = "error"; assertTrue(HttpFileProbe().probe(url) is FileProbeResult.Rejected) }
    @Test fun mediaClassificationAndRedirect() { mime = "video/mp4"; val m = (HttpFileProbe().probe(url.replace("file.bin", "redirect")) as FileProbeResult.FileFound).metadata; assertEquals(FileKind.Video, m.kind); assertEquals(url, m.finalUrl) }
    @Test fun audioClassification() { mime = "audio/mpeg"; assertEquals(FileKind.Audio, metadata().kind) }
    @Test fun unknownLengthReportsBytes() { behavior = "unknown"; val m = metadata(); assertNull(m.totalBytes); var actual = 0L; val result = download(m, progress = { n, total -> actual = n; assertNull(total) }); assertTrue(result.toString(), result is FileDownloadResult.Completed); assertEquals(bytes.size.toLong(), actual) }
    @Test fun pauseAndResumeAppendsExactBytes() { val m = metadata(); val old = paused(m); assertTrue(old.isNotEmpty()); assertTrue(download(m) is FileDownloadResult.Completed); assertArrayEquals(bytes, File(dir, "body.part").readBytes()) }
    @Test fun ignoredRangeKeepsOldBytes() { val m = metadata(); val old = paused(m); behavior = "ignore"; assertTrue(download(m) is FileDownloadResult.RestartRequired); assertArrayEquals(old, File(dir, "body.part").readBytes()) }
    @Test fun changedEtagKeepsOldBytes() { val m = metadata(); val old = paused(m); tag = "\"two\""; assertTrue(download(m) is FileDownloadResult.RestartRequired); assertArrayEquals(old, File(dir, "body.part").readBytes()) }
    @Test fun wrongContentRangeKeepsOldBytes() { val m = metadata(); val old = paused(m); behavior = "bad-range"; assertTrue(download(m) is FileDownloadResult.RestartRequired); assertArrayEquals(old, File(dir, "body.part").readBytes()) }
    @Test fun incomplete416IsRejected() { val m = metadata(); paused(m); behavior = "416"; assertTrue(download(m) is FileDownloadResult.RestartRequired) }
    @Test fun complete416RequiresVerifiedIdentity() { val m = metadata(); assertTrue(download(m) is FileDownloadResult.Completed); behavior = "416"; assertTrue(download(m) is FileDownloadResult.Completed); tag = "\"two\""; assertTrue(download(m) is FileDownloadResult.RestartRequired) }
    @Test fun truncatedResponseIsNotSuccess() { val m = metadata(); behavior = "truncated"; assertTrue(download(m) is FileDownloadResult.Failed) }
    @Test fun noValidatorCannotAppend() { tag = ""; val m = metadata(); val old = paused(m); assertTrue(download(m) is FileDownloadResult.RestartRequired); assertArrayEquals(old, File(dir, "body.part").readBytes()) }
    @Test fun missingPartialRequiresRestart() { val m = metadata(); paused(m); File(dir, "body.part").delete(); assertTrue(download(m) is FileDownloadResult.RestartRequired) }
    @Test fun changedLocalLengthRequiresRestart() { val m = metadata(); paused(m); File(dir, "body.part").appendBytes(byteArrayOf(1)); assertTrue(download(m) is FileDownloadResult.RestartRequired) }
    @Test fun checkpointContainsNoUrl() { val m = metadata(); paused(m); assertFalse(File(dir, "checkpoint").readText().contains("127.0.0.1")) }
    @Test fun preexistingPartialIsNeverOverwritten() { File(dir, "body.part").writeText("old"); assertTrue(download(metadata()) is FileDownloadResult.RestartRequired); assertEquals("old", File(dir, "body.part").readText()) }
    @Test fun zeroByteFileCompletes() { behavior = "empty"; assertTrue(download(metadata()) is FileDownloadResult.Completed); assertEquals(0, File(dir, "body.part").length()) }
    @Test fun unknownLengthCannotCompleteShortRange() { behavior = "unknown"; val m = metadata(); paused(m); behavior = "short-range"; assertTrue(download(m) is FileDownloadResult.RestartRequired) }
    @Test fun rangeAndIfRangeAreSent() { val m = metadata(); val old = paused(m); download(m); assertEquals("bytes=${old.size}-", requestedRange); assertEquals(tag, requestedValidator) }
    @Test fun validDateSupportsResumeWithoutStrongEtag() { tag = "W/\"weak\""; modified = "Wed, 21 Oct 2015 07:28:00 GMT"; val m = metadata(); assertNull(m.etag); paused(m); assertTrue(download(m) is FileDownloadResult.Completed); assertEquals(modified, requestedValidator) }
    @Test fun changedDateRejectsResume() { tag = ""; modified = "Wed, 21 Oct 2015 07:28:00 GMT"; val m = metadata(); val old = paused(m); modified = "Thu, 22 Oct 2015 07:28:00 GMT"; assertTrue(download(m) is FileDownloadResult.RestartRequired); assertArrayEquals(old, File(dir, "body.part").readBytes()) }
    @Test fun mislabeledHtmlProbeIsNotFile() { behavior = "html-body"; assertTrue(HttpFileProbe().probe(url) is FileProbeResult.NotFile) }
    @Test fun downloadRejectsLoginBodyBeforeWriting() { val m = metadata(); behavior = "html-body"; assertTrue(download(m) is FileDownloadResult.RestartRequired); assertFalse(File(dir, "body.part").exists()) }
    @Test fun encodedResponseRejected() { behavior = "encoded"; assertTrue(HttpFileProbe().probe(url) is FileProbeResult.Rejected) }
    @Test fun redirectLoopIsBounded() { assertTrue(HttpFileProbe().probe(url.replace("file.bin", "loop")) is FileProbeResult.Rejected) }
    @Test fun metadataMismatchKeepsBytes() { val m = metadata(); val old = paused(m); assertTrue(download(m.copy(fileName = "different.bin")) is FileDownloadResult.RestartRequired); assertArrayEquals(old, File(dir, "body.part").readBytes()) }
    @Test fun checkpointOutsideTaskDirectoryRejected() { val outer = File.createTempFile("checkpoint", ".tmp"); try { assertTrue(ResumableFileDownloader().download(metadata(), File(dir, "body.part"), outer) is FileDownloadResult.RestartRequired); assertEquals(0, outer.length()) } finally { outer.delete() } }
    @Test fun validatorDoesNotProveRangeSupport() { assertEquals(ResumeSupport.Unknown, metadata().resumeSupport) }
    @Test fun fragmentedHtmlIsRejected() { behavior = "fragmented-html"; assertTrue(HttpFileProbe().probe(url) is FileProbeResult.NotFile) }
    @Test fun fragmentedDownloadHtmlIsRejected() { val m = metadata(); behavior = "fragmented-html"; assertTrue(download(m) is FileDownloadResult.RestartRequired); assertFalse(File(dir, "body.part").exists()) }
    @Test fun malformedEtagIsNotReliable() { tag = "\"bad\"quote\""; assertNull(metadata().etag) }
    @Test fun recentDateIsNotReliable() { tag = ""; modified = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }.format(java.util.Date()); assertNull(metadata().lastModified) }
    @Test fun htmlBytesInsideResumedBinaryArePreserved() { "<html>".toByteArray().copyInto(bytes, 512); val m = metadata(); paused(m); assertTrue(download(m) is FileDownloadResult.Completed); assertArrayEquals(bytes, File(dir, "body.part").readBytes()) }
    @Test fun filenameRemovesC1ControlsWithoutDamagingChinese() {
        assertEquals("中_文_.zip", HttpFileProbe.fileName("attachment; filename=\"中\u0085文\u009f.zip\"", url))
    }
    @Test fun connectionsHaveFiniteConnectAndReadTimeouts() {
        val connection = openFileConnection(url)
        try { assertEquals(10000, connection.connectTimeout); assertEquals(3000, connection.readTimeout) }
        finally { connection.disconnect() }
    }
    @Test fun stalledResponseTimesOutAndReleasesConnection() {
        val released = CountDownLatch(1)
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val worker = thread(isDaemon = true) {
                listener.accept().use { socket ->
                    socket.soTimeout = 7000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume HTTP request. */ }
                    if (reader.read() == -1) released.countDown()
                }
            }
            val started = System.nanoTime()
            val result = HttpFileProbe().probe("http://127.0.0.1:${listener.localPort}/stall")
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue(result is FileProbeResult.Rejected)
            assertTrue("Expected the existing 3s read timeout, took ${elapsed}ms", elapsed in 2500..6500)
            assertTrue("Timed-out connection was not closed", released.await(2, TimeUnit.SECONDS))
            worker.join(1000)
        }
        assertTrue(HttpFileProbe().probe(url) is FileProbeResult.FileFound)
    }
    @Test fun pausedDownloadClosesResponseBeforeAnotherRequest() {
        assertDownloadReleasesConnection(pause = true)
    }
    @Test fun failedDownloadClosesResponseBeforeAnotherRequest() {
        assertDownloadReleasesConnection(pause = false)
    }
    private fun assertDownloadReleasesConnection(pause: Boolean) {
        val released = CountDownLatch(1)
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val worker = thread(isDaemon = true) {
                listener.accept().use { socket ->
                    socket.soTimeout = 7000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume HTTP request. */ }
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: 100000\r\nETag: \"one\"\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply { write(header.toByteArray()); write(bytes, 0, 512); flush() }
                    if (reader.read() == -1) released.countDown()
                }
            }
            val endpoint = "http://127.0.0.1:${listener.localPort}/file.bin"
            val m = metadata().copy(sourceUrl = endpoint, finalUrl = endpoint)
            var stop = false
            val result = download(m, { stop }) { _, _ -> if (pause) stop = true }
            if (pause) assertTrue(result is FileDownloadResult.Stopped) else assertTrue(result is FileDownloadResult.Failed)
            assertTrue("Download response was not closed", released.await(2, TimeUnit.SECONDS))
            worker.join(1000)
        }
        assertTrue(HttpFileProbe().probe(url) is FileProbeResult.FileFound)
    }
    @Test fun longChineseFilenameUsesUtf8BudgetAndKeepsExtension() {
        val name = HttpFileProbe.fileName("attachment; filename=\"${"中".repeat(180)}.zip\"", url)
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 230)
        assertTrue(name.endsWith(".zip"))
        assertTrue(name.removeSuffix(".zip").all { it == '中' })
    }
    @Test fun longEmojiFilenameKeepsWholeUnicodeCodepointsAndExtension() {
        val name = HttpFileProbe.fileName("attachment; filename=\"${"a".repeat(179)}😀${"😀".repeat(100)}.zip\"", url)
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 230)
        assertTrue(name.endsWith(".zip"))
        assertEquals(name, name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertTrue(name.contains("😀"))
    }
    @Test fun abnormalLongExtensionIsBounded() {
        val name = HttpFileProbe.fileName("attachment; filename=\"正文.${"扩".repeat(300)}\"", url)
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 230)
        assertTrue(name.startsWith("正文."))
        assertTrue(name.substringAfterLast('.').toByteArray(Charsets.UTF_8).size <= 32)
    }
    @Test fun truncatedFilenameHasNoTrailingDotOrSpace() {
        val name = HttpFileProbe.fileName("attachment; filename=\"${"a".repeat(225)} ${"b".repeat(30)}.zip\"", url)
        assertFalse(name.removeSuffix(".zip").endsWith(' '))
    }
    @Test fun tricklingPrefixCanBeStoppedAndConnectionIsReleased() {
        val stop = AtomicBoolean(false)
        val firstByte = CountDownLatch(1)
        val released = CountDownLatch(1)
        val accepted = AtomicReference<java.net.Socket>()
        val result = AtomicReference<FileDownloadResult>()
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val endpoint = "http://127.0.0.1:${listener.localPort}/trickle"
            val m = metadata().copy(sourceUrl = endpoint, finalUrl = endpoint)
            val worker = thread(isDaemon = true) {
                try {
                    listener.accept().use { socket ->
                        accepted.set(socket)
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { }
                        socket.soTimeout = 10
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nContent-Type: application/octet-stream\r\nETag: \"one\"\r\nConnection: close\r\n\r\n".toByteArray())
                        repeat(512) {
                            output.write(1); output.flush(); firstByte.countDown()
                            try { if (input.read() == -1) { released.countDown(); return@thread } }
                            catch (_: java.net.SocketTimeoutException) { }
                            Thread.sleep(200)
                        }
                    }
                } catch (_: java.io.IOException) { released.countDown() }
            }
            val client = thread(isDaemon = true) { result.set(download(m, { stop.get() })) }
            try {
                assertTrue(firstByte.await(2, TimeUnit.SECONDS))
                stop.set(true)
                client.join(1500)
                assertFalse("Cancellation stalled while gathering the sniff prefix", client.isAlive)
                assertTrue(result.get() is FileDownloadResult.Stopped)
                assertFalse(File(dir, "body.part").exists())
                assertTrue("Cancelled connection was not released", released.await(1500, TimeUnit.MILLISECONDS))
            } finally {
                stop.set(true); accepted.get()?.close(); listener.close()
                client.join(4000); worker.join(1000)
            }
        }
    }
    @Test fun stopImmediatelyAfterPartialCreationLeavesResumableCheckpoint() {
        val m = metadata()
        val partial = File(dir, "body.part")
        val result = download(m, { partial.exists() })
        assertEquals(FileDownloadResult.Stopped(0), result)
        assertTrue(File(dir, "checkpoint").exists())
        assertTrue(download(m) is FileDownloadResult.Completed)
        assertArrayEquals(bytes, partial.readBytes())
    }
}
