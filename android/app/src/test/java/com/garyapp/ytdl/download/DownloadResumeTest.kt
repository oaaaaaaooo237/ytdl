package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.*
import com.garyapp.ytdl.media.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadResumeTest {
    @get:Rule val temp = TemporaryFolder()
    private fun request(route: DownloadRoute = DownloadRoute.DirectSingleFile("18")) =
        DownloadRequest("https://example.test/watch", "测试", route, taskId = "a".repeat(32))

    @Test fun sameTaskReusesCompletedOutputWithoutDownload() {
        val request = request()
        val engine = Engine()
        val pipeline = DownloadPipeline(engine, Merger())
        val first = pipeline.run(request, temp.root)
        val second = pipeline.run(request, temp.root)
        assertEquals(DownloadStage.Completed, second.state.stage)
        assertEquals(1, engine.calls.size)
        assertEquals(first.outputs, second.outputs)
        assertEquals("task-${request.taskId}", File(second.outputs.single().path).parentFile!!.name)
    }

    @Test fun singleFileWithTransportPacketsIsRemuxedBeforePublication() {
        val engine = Engine()
        engine.duringDownload = { directory, _ ->
            engine.returnedFile = File(directory, "transport.mp4").apply {
                writeBytes(ByteArray(188 * 4) { if (it % 188 == 0) 0x47 else 0 })
            }
        }
        val processor = object : MediaProcessor by Merger() {
            override fun remuxTransportStream(input: File, output: File, ensureActive: () -> Unit): Result<Unit> =
                runCatching {
                    ensureActive()
                    assertEquals("transport.mp4", input.name)
                    output.writeText("remuxed")
                }
        }
        val result = DownloadPipeline(engine, processor).run(request(), temp.root)
        assertEquals(DownloadStage.Completed, result.state.stage)
        val output = File(result.outputs.single().path)
        assertEquals("mp4", output.extension)
        assertEquals("remuxed", output.readText())
    }

    @Test fun exportedReceiptSurvivesFullPipelineResumeWithoutRedownloading() {
        val engine = Engine()
        val pipeline = DownloadPipeline(engine, Merger())
        pipeline.run(request(), temp.root)
        val uris = listOf("content://documents/document/exact-output")
        TaskWorkspace(temp.root).saveExportedDocuments(request().taskId, uris).getOrThrow()
        assertEquals(DownloadStage.Completed, pipeline.run(request(), temp.root).state.stage)
        assertEquals(uris, TaskWorkspace(temp.root).loadExportedDocuments(request().taskId).getOrThrow())
        assertEquals(1, engine.calls.size)
    }

    @Test fun networkFailureBeforeConnectivityCallbackRetainsPartWithoutRetrying() {
        val engine = Engine().apply { duringDownload = { directory, listener ->
            File(directory, "video.part").writeText("partial")
            listener?.onProgress(DownloadProgress("downloading", 30.0, 3, 10, 1.0, null, null))
            throw YtdlpDownloadException(AnalysisErrorCategory.Network, "network unavailable")
        } }
        val result = DownloadPipeline(engine, Merger(), isNetworkAvailable = { false }).run(request(), temp.root)
        assertEquals(DownloadStage.WaitingForNetwork, result.state.stage)
        assertEquals(1, engine.calls.size)
        assertEquals("partial", File(temp.root, "task-${request().taskId}/video.part").readText())
        assertEquals("NetworkUnavailable", TaskWorkspace(temp.root).loadCheckpoint(request().taskId).getOrThrow().stopReason)
    }

    @Test fun unclassifiedDownloaderFailureWhileOfflineRetainsPart() {
        val engine = Engine().apply { duringDownload = { directory, _ ->
            File(directory, "video.part").writeText("partial")
            throw YtdlpDownloadException(AnalysisErrorCategory.Unknown, "analysis failed")
        } }
        val result = DownloadPipeline(engine, Merger(), isNetworkAvailable = { false }).run(request(), temp.root)
        assertEquals(DownloadStage.WaitingForNetwork, result.state.stage)
        assertEquals(1, engine.calls.size)
        assertEquals("partial", File(temp.root, "task-${request().taskId}/video.part").readText())
    }

    @Test fun permanentSiteErrorIsNotHiddenByConcurrentNetworkLoss() {
        for (category in listOf(AnalysisErrorCategory.Permission, AnalysisErrorCategory.Unsupported)) {
            val stop = MutableDownloadCancellation()
            val engine = Engine().apply { duringDownload = { _, _ ->
                stop.waitForNetwork()
                throw YtdlpDownloadException(category, "site refusal")
            } }
            val result = DownloadPipeline(engine, Merger(), isNetworkAvailable = { false }).run(request(), temp.newFolder(), stop)
            assertEquals(DownloadStage.Failed, result.state.stage)
            assertEquals(1, engine.calls.size)
        }
    }

    @Test fun siteRefusalNeverBecomesOfflineWaitAndOnlineNetworkFailureHasFiniteRetries() {
        for (category in listOf(AnalysisErrorCategory.Permission, AnalysisErrorCategory.Network)) {
            val root = temp.newFolder()
            val engine = Engine().apply { duringDownload = { _, _ -> throw YtdlpDownloadException(category, "controlled") } }
            val result = DownloadPipeline(engine, Merger(), isNetworkAvailable = { category == AnalysisErrorCategory.Network }).run(request(), root)
            assertEquals(DownloadStage.Failed, result.state.stage)
            assertTrue(engine.calls.size in 1..3)
            if (category == AnalysisErrorCategory.Permission) assertEquals(1, engine.calls.size)
        }
    }

    @Test fun statePersistenceFailurePreservesPartialAndCheckpoint() {
        val engine = Engine().apply { duringDownload = { directory, listener ->
            File(directory, "video.part").writeText("partial")
            listener?.onProgress(DownloadProgress("downloading", 30.0, 3, 10, 1.0, null, null))
        } }
        val result = DownloadPipeline(engine, Merger()).run(request(), temp.root) { state ->
            if (state.progress != null && state.stage == DownloadStage.DownloadingVideo) throw PipelineCheckpointException(IllegalStateException("database unavailable"))
        }
        assertEquals(DownloadStage.Failed, result.state.stage)
        assertEquals("partial", File(temp.root, "task-${request().taskId}/video.part").readText())
        assertTrue(File(temp.root, "task-${request().taskId}/checkpoint.bin").exists())
    }

    @Test fun corruptCheckpointFailsWithoutMutatingTask() {
        val request = request()
        val task = TaskWorkspace(temp.root).directory(request.taskId, true)
        val checkpoint = File(task, "checkpoint.bin").apply { writeText("broken") }
        val partial = File(task, "download.part").apply { writeText("partial") }
        val engine = Engine()
        val result = DownloadPipeline(engine, Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Failed, result.state.stage)
        assertTrue(engine.calls.isEmpty())
        assertEquals("broken", checkpoint.readText())
        assertEquals("partial", partial.readText())
    }

    @Test fun completedVideoIsSkippedWhenAudioResumes() {
        val request = request(DownloadRoute.MergeRequired("137", "140"))
        val workspace = TaskWorkspace(temp.root)
        val task = workspace.directory(request.taskId, true)
        val video = File(task, ".parts/video.mp4").apply { parentFile!!.mkdirs(); writeText("video") }
        workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "DownloadingAudio", "UserPause", 3, 10,
            completedFiles = listOf(CompletedTaskFile("video", "137", ".parts/video.mp4", video.length())))).getOrThrow()
        val engine = Engine()
        val result = DownloadPipeline(engine, Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("audio:140"), engine.calls)
    }

    @Test fun pauseRetainsPartAndPersistsBeforeEmittingPaused() {
        val request = request()
        val stop = MutableDownloadCancellation()
        val engine = Engine().apply { duringDownload = { directory, listener ->
            File(directory, "download.part").writeText("partial")
            listener?.onProgress(DownloadProgress("downloading", 30.0, 3, 10, 2.0, 3, "secret"))
            stop.pause()
            try { listener?.onProgress(DownloadProgress("downloading", 40.0, 4, 10, 2.0, 3, null)) }
            catch (_: Exception) { throw YtdlpDownloadException(AnalysisErrorCategory.Canceled, "callback") }
        } }
        val result = DownloadPipeline(engine, Merger()).run(request, temp.root, stop) {
            if (it.stage == DownloadStage.Paused) {
                assertEquals("UserPause", TaskWorkspace(temp.root).loadCheckpoint(request.taskId).getOrThrow().stopReason)
            }
        }
        assertEquals(DownloadStage.Paused, result.state.stage)
        assertEquals(0.0, result.state.progress?.speedBytesPerSecond)
        assertNull(result.state.progress?.filename)
        val part = File(temp.root, "task-${request.taskId}/download.part")
        assertEquals("partial", part.readText())
        val stoppedSize = part.length()
        java.util.concurrent.TimeUnit.SECONDS.sleep(10)
        assertEquals(stoppedSize, part.length())
        assertFalse(engine.continued)
    }

    @Test fun offlineAndLifecycleStopsPreservePartialFiles() {
        for (offline in listOf(true, false)) {
            val root = temp.newFolder()
            val stop = MutableDownloadCancellation()
            val engine = Engine().apply { duringDownload = { dir, _ ->
                File(dir, "download.part").writeText("partial")
                if (offline) stop.waitForNetwork() else stop.stopForLifecycle()
                throw java.net.SocketTimeoutException("sensitive")
            } }
            val result = DownloadPipeline(engine, Merger()).run(request(), root, stop)
            assertEquals(if (offline) DownloadStage.WaitingForNetwork else DownloadStage.Paused, result.state.stage)
            assertTrue(File(root, "task-${request().taskId}/download.part").isFile)
            assertEquals(1, engine.calls.size)
        }
    }

    @Test fun cancelHasPriorityAndDeletesOnlyOwnTaskAfterDownloadExits() {
        val stop = MutableDownloadCancellation()
        stop.pause(); stop.waitForNetwork()
        assertEquals(DownloadStopReason.UserPause, stop.stopReason)
        stop.cancel(); stop.pause(); stop.stopForLifecycle()
        assertEquals(DownloadStopReason.UserCancel, stop.stopReason)
        val sentinel = File(temp.root, "sentinel").apply { writeText("keep") }
        val other = TaskWorkspace(temp.root).directory("b".repeat(32), true)
        File(other, "keep").writeText("keep")
        val result = DownloadPipeline(Engine(), Merger()).run(request(), temp.root, stop)
        assertEquals(DownloadStage.Canceled, result.state.stage)
        assertEquals("keep", sentinel.readText())
        assertEquals("keep", File(other, "keep").readText())
        assertFalse(File(temp.root, "task-${request().taskId}").exists())
    }

    @Test fun roleOrFormatSwapFailsClosed() {
        for ((role, format) in listOf("video" to "18", "media" to "22")) {
            val root = temp.newFolder()
            val workspace = TaskWorkspace(root)
            val file = workspace.directory(request().taskId, true).resolve("output.mp4").apply { writeText("keep") }
            workspace.saveCheckpoint(request().taskId, TaskCheckpoint(request().taskId, "DownloadingVideo",
                completedFiles = listOf(CompletedTaskFile(role, format, file.name, file.length())))).getOrThrow()
            val engine = Engine()
            val result = DownloadPipeline(engine, Merger()).run(request(), root)
            assertEquals(DownloadStage.Failed, result.state.stage)
            assertTrue(engine.calls.isEmpty())
            assertEquals("keep", file.readText())
        }
    }

    @Test fun completedVideoIsCheckpointedBeforePauseAndAudioStartsOnlyOnResume() {
        val request = request(DownloadRoute.MergeRequired("137", "140"))
        val stop = MutableDownloadCancellation()
        val engine = Engine().apply { afterDownload = { stop.pause() } }
        val first = DownloadPipeline(engine, Merger()).run(request, temp.root, stop)
        assertEquals(DownloadStage.Paused, first.state.stage)
        assertEquals("video", TaskWorkspace(temp.root).loadCheckpoint(request.taskId).getOrThrow().completedFiles.single().role)
        engine.afterDownload = {}
        val result = DownloadPipeline(engine, Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals(listOf("video:137", "audio:140"), engine.calls)
    }

    @Test fun interruptedMergeKeepsBothStreamsAndResumesOnlyMerge() {
        val request = request(DownloadRoute.MergeRequired("137", "140"))
        val stop = MutableDownloadCancellation()
        val merger = Merger().apply { duringMerge = { stop.pause(); throw IllegalStateException("interrupted") } }
        val engine = Engine()
        assertEquals(DownloadStage.Paused, DownloadPipeline(engine, merger).run(request, temp.root, stop).state.stage)
        merger.duringMerge = {}
        val pipeline = DownloadPipeline(engine, merger)
        assertEquals(DownloadStage.Completed, pipeline.run(request, temp.root).state.stage)
        assertEquals(DownloadStage.Completed, pipeline.run(request, temp.root).state.stage)
        assertEquals(listOf("video:137", "audio:140"), engine.calls)
        assertEquals(2, merger.calls)
        assertEquals(listOf("merged"), TaskWorkspace(temp.root).loadCheckpoint(request.taskId).getOrThrow().completedFiles.map { it.role })
    }

    @Test fun genuineFailureCleansPartialAndIsNotPaused() {
        val engine = Engine().apply { duringDownload = { dir, _ ->
            File(dir, "download.part").writeText("partial")
            throw IllegalStateException("HTTP 403 secret")
        } }
        val result = DownloadPipeline(engine, Merger()).run(request(), temp.root)
        assertEquals(DownloadStage.Failed, result.state.stage)
        assertFalse(temp.root.walkTopDown().any { it.extension == "part" })
    }

    @Test fun cancelCorruptCheckpointStillDeletesOnlyControlledTask() {
        val request = request()
        val dir = TaskWorkspace(temp.root).directory(request.taskId, true)
        File(dir, "checkpoint.bin").writeText("broken")
        val stop = MutableDownloadCancellation().apply { cancel() }
        val result = DownloadPipeline(Engine(), Merger()).run(request, temp.root, stop)
        assertEquals(DownloadStage.Canceled, result.state.stage)
        assertFalse(dir.exists())
    }

    @Test fun completedPhaseWithoutCompletedFileIsRejected() {
        val request = request()
        TaskWorkspace(temp.root).saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "Completed")).getOrThrow()
        val engine = Engine()
        assertEquals(DownloadStage.Failed, DownloadPipeline(engine, Merger()).run(request, temp.root).state.stage)
        assertTrue(engine.calls.isEmpty())
    }

    @Test fun resumeProgressDropHasSafeRestartMessage() {
        val request = request()
        val workspace = TaskWorkspace(temp.root)
        workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "DownloadingVideo", "UserPause", 80, 100)).getOrThrow()
        val engine = Engine().apply { duringDownload = { _, listener ->
            listener?.onProgress(DownloadProgress("downloading", 5.0, 5, 100, 1.0, null, "secret"))
        } }
        val result = DownloadPipeline(engine, Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Completed, result.state.stage)
        assertEquals("该网站无法接着下载，已重新下载当前文件。", result.state.statusMessage)
    }

    @Test fun wrappedProgressCheckpointFailurePreservesPartial() {
        val engine = Engine().apply { duringDownload = { dir, listener ->
            File(dir, "download.part").writeText("partial")
            File(dir, ".checkpoint.bin.tmp").mkdir()
            try { listener?.onProgress(DownloadProgress("downloading", 5.0, 5, 100, 1.0, null, null)) }
            catch (_: Exception) { throw YtdlpDownloadException(AnalysisErrorCategory.Unknown, "wrapped") }
        } }
        val result = DownloadPipeline(engine, Merger()).run(request(), temp.root)
        assertEquals(DownloadStage.Failed, result.state.stage)
        assertEquals("partial", File(temp.root, "task-${request().taskId}/download.part").readText())
        assertTrue(result.state.errorMessage.orEmpty().contains("保留"))
    }

    @Test fun interruptedTitlePublicationReusesIdenticalCopyWithoutDownloadingAgain() {
        val request = request()
        val workspace = TaskWorkspace(temp.root)
        val dir = workspace.directory(request.taskId, true)
        val raw = File(dir, "18.mp4").apply { writeText("video") }
        val titled = File(dir, "测试.mp4")
        raw.copyTo(titled)
        workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "DownloadingVideo",
            completedFiles = listOf(CompletedTaskFile("media", "18", raw.name, raw.length())))).getOrThrow()
        val engine = Engine()
        val result = DownloadPipeline(engine, Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Completed, result.state.stage)
        assertTrue(engine.calls.isEmpty())
        assertEquals(titled.path, result.outputs.single().path)
        assertFalse(raw.exists())
        assertTrue(workspace.loadCheckpoint(request.taskId).isSuccess)
    }

    @Test fun returnedExternalOutputCannotRenameOrDeleteOutsideSentinel() {
        val outside = temp.newFolder("outside")
        val file = File(outside, "original.mp4").apply { writeText("keep") }
        val root = temp.newFolder("downloads")
        val engine = Engine().apply { returnedFile = file }
        val result = DownloadPipeline(engine, Merger()).run(request(), root)
        assertEquals(DownloadStage.Failed, result.state.stage)
        assertEquals("keep", file.readText())
        assertFalse(File(outside, "测试.mp4").exists())
    }

    @Test fun pauseDuringTitleCopyPreservesRawCheckpointAndCanContinue() {
        val request = request()
        val workspace = TaskWorkspace(temp.root)
        val dir = workspace.directory(request.taskId, true)
        val raw = File(dir, "18.mp4").apply { writeBytes(ByteArray(256 * 1024) { 7 }) }
        workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "DownloadingVideo",
            completedFiles = listOf(CompletedTaskFile("media", "18", raw.name, raw.length())))).getOrThrow()
        val token = object : DownloadCancellation {
            private var stopped = false
            override val stopReason: DownloadStopReason? get() {
                if (dir.listFiles().orEmpty().any { it.name.startsWith(".title-") && it.length() > 0 }) stopped = true
                return if (stopped) DownloadStopReason.UserPause else null
            }
            override val isCancellationRequested get() = stopReason != null
        }
        val engine = Engine()
        val paused = DownloadPipeline(engine, Merger()).run(request, temp.root, token)
        assertEquals(DownloadStage.Paused, paused.state.stage)
        assertTrue(raw.exists())
        assertEquals(raw.name, workspace.loadCheckpoint(request.taskId).getOrThrow().completedFiles.single().relativePath)
        assertTrue(dir.listFiles().orEmpty().none { it.name.startsWith(".title-") })
        val finished = DownloadPipeline(engine, Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Completed, finished.state.stage)
        assertEquals(256 * 1024L, File(finished.outputs.single().path).length())
        assertTrue(engine.calls.isEmpty())
    }

    @Test fun titledCheckpointWriteFailureRetainsRawAndRecoversPublishedCopy() {
        val request = request()
        val workspace = TaskWorkspace(temp.root)
        val dir = workspace.directory(request.taskId, true)
        val raw = File(dir, "18.mp4").apply { writeText("video") }
        workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "DownloadingVideo",
            completedFiles = listOf(CompletedTaskFile("media", "18", raw.name, raw.length())))).getOrThrow()
        val blocker = File(dir, ".checkpoint.bin.tmp").apply { mkdir() }
        val engine = Engine()
        val failed = DownloadPipeline(engine, Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Failed, failed.state.stage)
        assertTrue(raw.exists())
        assertEquals("video", File(dir, "测试.mp4").readText())
        assertEquals(raw.name, workspace.loadCheckpoint(request.taskId).getOrThrow().completedFiles.single().relativePath)
        assertTrue(blocker.delete())
        assertEquals(DownloadStage.Completed, DownloadPipeline(engine, Merger()).run(request, temp.root).state.stage)
        assertFalse(raw.exists())
        assertTrue(engine.calls.isEmpty())
    }

    @Test fun unrelatedTitleCollisionKeepsBothFilesAndCheckpoint() {
        val request = request()
        val workspace = TaskWorkspace(temp.root)
        val dir = workspace.directory(request.taskId, true)
        val raw = File(dir, "18.mp4").apply { writeText("video") }
        val titled = File(dir, "测试.mp4").apply { writeText("other") }
        workspace.saveCheckpoint(request.taskId, TaskCheckpoint(request.taskId, "DownloadingVideo",
            completedFiles = listOf(CompletedTaskFile("media", "18", raw.name, raw.length())))).getOrThrow()
        val result = DownloadPipeline(Engine(), Merger()).run(request, temp.root)
        assertEquals(DownloadStage.Failed, result.state.stage)
        assertTrue(raw.isFile)
        assertTrue(titled.isFile)
        assertTrue(workspace.loadCheckpoint(request.taskId).isSuccess)
    }

    private class Engine : DownloadEngine {
        val calls = mutableListOf<String>()
        var duringDownload: (File, DownloadProgressListener?) -> Unit = { _, _ -> }
        var afterDownload: () -> Unit = {}
        var continued = false
        var returnedFile: File? = null
        override fun downloadFormat(url: String, outputDirectory: File, formatId: String, role: DownloadFormatRole,
            cookiesPath: String?, listener: DownloadProgressListener?,
            analysisKey: String?,
        ): Result<DownloadResult> {
            calls += "${role.pythonValue}:$formatId"
            duringDownload(outputDirectory, listener)
            continued = true
            val file = File(outputDirectory, "$formatId.mp4").apply { parentFile!!.mkdirs(); writeText("download") }
            afterDownload()
            val result = returnedFile ?: file
            return Result.success(DownloadResult(result.path, result.length(), "test", formatId, role))
        }
        override fun downloadSubtitle(url: String, outputDirectory: File, language: String, ext: String,
            source: SubtitleSource, cookiesPath: String?, listener: DownloadProgressListener?): Result<SubtitleDownloadResult> =
            Result.failure(IllegalStateException("unused"))
    }
    private class Merger : MediaProcessor {
        override val processorName = "fake"
        var duringMerge: () -> Unit = {}
        var calls = 0
        override fun mergeVideoAndAudio(request: MediaMergeRequest): Result<MediaProcessingResult> {
            calls++
            request.outputFile.writeText("partial merge")
            duringMerge()
            request.outputFile.writeText("merged")
            return Result.success(MediaProcessingResult(request.outputFile, 6, 1, 1, processorName))
        }
    }
}
