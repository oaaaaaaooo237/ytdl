package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.files.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.File
import java.nio.file.Files
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HttpFilePipelineTest {
    private lateinit var server: HttpServer
    private lateinit var root: File
    private lateinit var url: String
    private var body = ByteArray(600_000) { (it % 251).toByte() }
    private var ignoreRange = false
    private var truncated = false
    private var unknownLength = false
    private var lastRange: String? = null
    @Before fun setUp() {
        root = Files.createTempDirectory("file-pipeline").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/report.pdf") { e ->
            try {
                e.responseHeaders.add("ETag", "\"one\"")
                e.responseHeaders.add("Content-Type", "application/pdf")
                lastRange = e.requestHeaders.getFirst("Range")
                val start = if (ignoreRange) 0 else lastRange?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                if (start > 0) e.responseHeaders.add("Content-Range", "bytes $start-${body.lastIndex}/${body.size}")
                if (!unknownLength) e.responseHeaders.add("Content-Length", (body.size - start).toString())
                e.sendResponseHeaders(if (start > 0) 206 else 200, if (body.isEmpty()) -1 else if (unknownLength) 0 else (body.size - start).toLong())
                if (body.isNotEmpty()) e.responseBody.write(body, start, if (truncated) 100_000 else body.size - start)
            } finally { e.close() }
        }
        server.start(); url = "http://127.0.0.1:${server.address.port}/report.pdf"
    }
    @After fun tearDown() { server.stop(0); root.deleteRecursively() }
    private fun request(): DownloadRequest {
        val metadata = (HttpFileProbe().probe(url) as FileProbeResult.FileFound).metadata
        return DownloadRequest(url, "报告", DownloadRoute.HttpFile(metadata, "用户改名.pdf"))
    }
    @Test fun resumeDisabledRestartsInterruptedHttpTransferFromZero() {
        val request = request().copy(resumeTransfers = false)
        val token = MutableDownloadCancellation()
        val pipeline = HttpFilePipeline()
        val paused = pipeline.run(request, root, token) { state ->
            if ((state.progress?.downloadedBytes ?: 0) >= 100_000) token.pause()
        }
        assertEquals(DownloadStage.Paused, paused.state.stage)
        val resumed = pipeline.run(request, root)
        assertEquals(DownloadStage.Completed, resumed.state.stage)
        assertNull(lastRange)
        assertArrayEquals(body, File(resumed.outputs.single().path).readBytes())
    }

    @Test fun interruptedCopyUnderAskResumesOwnedTarget() {
        verifyPendingPublication(HttpFileConflictPolicy.Ask, body.copyOf(500), false)
    }

    @Test fun fullyCopiedUncommittedTargetIsRebuiltWithoutAnotherSuffix() {
        // Matching length alone must never make the uncommitted copy a finished output.
        verifyPendingPublication(HttpFileConflictPolicy.AutoRename, ByteArray(body.size), true)
    }

    private fun verifyPendingPublication(policy: HttpFileConflictPolicy, interruptedCopy: ByteArray, nameAlreadyTaken: Boolean) {
        val base = request()
        val route = (base.route as DownloadRoute.HttpFile).copy(conflictPolicy = policy)
        val req = base.copy(route = route)
        val session = PipelineCheckpoint.open(TaskWorkspace(root), req)
        val outputs = File(session.directory, "outputs").apply { mkdirs() }
        val preexisting = File(outputs, if (nameAlreadyTaken) route.fileName else "other.pdf").apply { writeText("keep") }
        val name = if (nameAlreadyTaken) "用户改名 (1).pdf" else route.fileName
        val target = File(outputs, name)
        val http = File(session.directory, "http").apply { mkdirs() }
        val transfer = ResumableFileDownloader().download(route.metadata, File(http, "body.part"), File(http, "resume.bin"))
        assertEquals(FileDownloadResult.Completed(body.size.toLong()), transfer)
        session.prepareFilePublication(target, body.size.toLong())
        session.save(DownloadStage.DownloadingFile, null, null)
        target.writeBytes(interruptedCopy)
        server.stop(0)

        val resumed = HttpFilePipeline().run(req, root)

        assertEquals(DownloadStage.Completed, resumed.state.stage)
        assertEquals(target, File(resumed.outputs.single().path))
        assertArrayEquals(body, target.readBytes())
        assertEquals(setOf(preexisting.name, name), outputs.list()!!.toSet())
        assertEquals("keep", preexisting.readText())
        assertFalse(http.exists())
        assertNull(TaskWorkspace(root).loadCheckpoint(req.taskId).getOrThrow().pendingFilePublication)
    }

    @Test fun recordedOutputRecoveryRemovesOnlyTransportFiles() {
        val req = request()
        val first = HttpFilePipeline().run(req, root)
        val finished = File(first.outputs.single().path)
        val http = File(finished.parentFile!!.parentFile, "http").apply { mkdirs() }
        File(http, "body.part").writeBytes(body)
        File(http, "resume.bin").writeText("old transport receipt")
        val unrelated = File(http, "keep.txt").apply { writeText("keep") }

        val resumed = HttpFilePipeline().run(req, root)

        assertEquals(DownloadStage.Completed, resumed.state.stage)
        assertArrayEquals(body, File(resumed.outputs.single().path).readBytes())
        assertEquals(listOf("keep.txt"), http.list()!!.toList())
        assertEquals("keep", unrelated.readText())
    }

    @Test fun unsupportedCookieInputIsExplicitlyRejectedAndManagedTemporaryFileIsRemoved() {
        val temporary = com.garyapp.ytdl.cookies.TemporaryCookiesFile.materializeFromStream(
            "private-cookie-content".byteInputStream(), File(root, "cache"), "file-test").getOrThrow()
        val req = request().copy(cookiesPath = temporary.file.path, usesCookies = true)
        val result = HttpFilePipeline().run(req, root)
        assertEquals(DownloadStage.Failed, result.state.stage)
        assertTrue(result.state.errorMessage.orEmpty().contains("有效直链"))
        assertFalse(result.state.errorMessage.orEmpty().contains("private-cookie-content"))
        assertFalse(temporary.file.exists())
        assertFalse(TaskWorkspace(root).exists(req.taskId))
    }

    @Test fun unknownLengthReportsBytesWithoutPercentage() {
        unknownLength = true; val states = mutableListOf<DownloadTaskState>()
        val result = HttpFilePipeline().run(request(), root, onStateChanged = { states += it })
        assertEquals(DownloadStage.Completed, result.state.stage)
        val progress = states.mapNotNull { it.progress }
        assertTrue(progress.isNotEmpty())
        progress.forEach { assertNull(it.totalBytes); assertNull(it.percent); assertNull(it.etaSeconds) }
        assertEquals(body.size.toLong(), progress.last().downloadedBytes)
    }
    @Test fun networkStopRetainsPartialAndActiveCancelRemovesOnlyItsWorkspace() {
        val req = request(); val token = MutableDownloadCancellation()
        val stopped = HttpFilePipeline().run(req, root, token) { if ((it.progress?.downloadedBytes ?: 0) > 0) token.waitForNetwork() }
        assertEquals(DownloadStage.WaitingForNetwork, stopped.state.stage)
        assertTrue(File(root, "task-${req.taskId}/http/body.part").isFile)
        assertEquals(DownloadStage.Completed, HttpFilePipeline().run(req, root).state.stage)
        val cancelReq = request(); val cancel = MutableDownloadCancellation()
        val canceled = HttpFilePipeline().run(cancelReq, root, cancel) { if ((it.progress?.downloadedBytes ?: 0) > 0) cancel.cancel() }
        assertEquals(DownloadStage.Canceled, canceled.state.stage)
        assertFalse(TaskWorkspace(root).exists(cancelReq.taskId)); assertTrue(TaskWorkspace(root).exists(req.taskId))
    }
    @Test fun fileRouteClassifiesDirectVideoAndRejectsEmptyMedia() {
        val req = request().let { it.copy(route = (it.route as DownloadRoute.HttpFile).let { route -> route.copy(metadata = route.metadata.copy(kind = FileKind.Video)) }) }
        val result = HttpFilePipeline().run(req, root)
        assertEquals(DownloadOutputKind.Media, result.outputs.single().kind)
        body = byteArrayOf()
        val empty = request().let { it.copy(route = (it.route as DownloadRoute.HttpFile).let { route -> route.copy(metadata = route.metadata.copy(kind = FileKind.Audio)) }) }
        assertEquals(DownloadStage.Failed, HttpFilePipeline().run(empty, root).state.stage)
    }

    @Test fun completeFileUsesSeparateOutputAndRealBytes() {
        val result = HttpFilePipeline().run(request(), root)
        assertEquals(DownloadStage.Completed, result.state.stage)
        val output = result.outputs.single()
        assertEquals(DownloadOutputKind.File, output.kind)
        assertEquals("用户改名.pdf", File(output.path).name)
        assertEquals("outputs", requireNotNull(File(output.path).parentFile).name)
        assertArrayEquals(body, File(output.path).readBytes())
    }
    @Test fun localOutputConflictNumbersByDefaultAndAskPreservesExistingFile() {
        val req = request(); val task = TaskWorkspace(root).directory(req.taskId, true)
        TaskWorkspace(root).saveCheckpoint(req.taskId, TaskCheckpoint(req.taskId, DownloadStage.Waiting.name)).getOrThrow()
        val output = File(task, "outputs").apply { mkdirs() }
        File(output, "用户改名.pdf").writeText("existing")
        val auto = HttpFilePipeline().run(req, root)
        assertEquals("用户改名 (1).pdf", File(auto.outputs.single().path).name)
        assertEquals("existing", File(output, "用户改名.pdf").readText())
        val ask = request().let { it.copy(route = (it.route as DownloadRoute.HttpFile).copy(conflictPolicy = HttpFileConflictPolicy.Ask)) }
        TaskWorkspace(root).saveCheckpoint(ask.taskId, TaskCheckpoint(ask.taskId, DownloadStage.Waiting.name)).getOrThrow()
        val askOutputs = File(root, "task-${ask.taskId}/outputs").apply { mkdirs() }
        File(askOutputs, "用户改名.pdf").writeText("keep")
        val result = HttpFilePipeline().run(ask, root)
        assertEquals(FileRecoveryAction.ChooseFileName, result.state.fileRecoveryAction)
        assertEquals("keep", File(askOutputs, "用户改名.pdf").readText())
    }

    @Test fun legitimateEmptyFileCompletesAndRecovers() {
        body = byteArrayOf(); val req = request()
        val first = HttpFilePipeline().run(req, root)
        assertEquals(DownloadStage.Completed, first.state.stage)
        assertEquals(0L, first.outputs.single().bytesWritten)
        assertEquals(DownloadStage.Completed, HttpFilePipeline().run(req, root).state.stage)
    }
    @Test fun pauseThenNewInstanceResumesExactBytes() {
        val req = request(); val cancellation = MutableDownloadCancellation()
        val paused = HttpFilePipeline().run(req, root, cancellation) { if ((it.progress?.downloadedBytes ?: 0) > 0) cancellation.pause() }
        assertEquals(DownloadStage.Paused, paused.state.stage)
        val resumed = HttpFilePipeline().run(req, root)
        assertEquals(DownloadStage.Completed, resumed.state.stage); assertNotNull(lastRange)
        assertArrayEquals(body, File(resumed.outputs.single().path).readBytes())
    }
    @Test fun changedRangeRequiresConfirmationAndKeepsPartial() {
        val req = request(); val cancellation = MutableDownloadCancellation()
        HttpFilePipeline().run(req, root, cancellation) { if ((it.progress?.downloadedBytes ?: 0) > 0) cancellation.pause() }
        val partial = File(root, "task-${req.taskId}/http/body.part")
        val before = partial.readBytes(); ignoreRange = true
        val result = HttpFilePipeline().run(req, root)
        assertEquals(FileRecoveryAction.ConfirmRestart, result.state.fileRecoveryAction)
        assertArrayEquals(before, partial.readBytes())
        HttpFilePipeline.confirmRestart(TaskWorkspace(root), req).getOrThrow()
        assertEquals(DownloadStage.Completed, HttpFilePipeline().run(req, root).state.stage)
    }
    @Test fun truncatedTransferRemainsRetryableAndCancelDeletesOnlyOwnTask() {
        val req = request(); truncated = true
        val failed = HttpFilePipeline().run(req, root)
        assertEquals(DownloadStage.Failed, failed.state.stage)
        assertEquals(FileRecoveryAction.Retry, failed.state.fileRecoveryAction)
        truncated = false
        assertEquals(DownloadStage.Completed, HttpFilePipeline().run(req, root).state.stage)
        val other = File(root, "unrelated").apply { writeText("keep") }
        val cancellation = MutableDownloadCancellation().apply { cancel() }
        assertEquals(DownloadStage.Canceled, HttpFilePipeline().run(req, root, cancellation).state.stage)
        assertFalse(File(root, "task-${req.taskId}").exists()); assertTrue(other.exists())
    }
}
