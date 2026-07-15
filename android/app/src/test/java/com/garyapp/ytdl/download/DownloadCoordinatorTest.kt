package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.testing.ProjectTestPaths
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DownloadCoordinatorTest {
    private lateinit var tempRoot: File

    @Before
    fun resetCoordinator() {
        tempRoot = ProjectTestPaths.createTempDirectory("task-4-coordinator-").toFile()
        guardProjectTempPath(tempRoot.toPath())
        DownloadCoordinator.resetForTests()
    }

    @After
    fun removeProjectTempDirectory() {
        tempRoot.deleteRecursively()
        assertFalse("测试临时目录必须自动清理", tempRoot.exists())
    }

    @Test
    fun enqueuePublishesWaitingStateAndServiceClaimsExactlyOneLaunch() {
        val observed = mutableListOf<DownloadTaskState>()
        val close = DownloadCoordinator.addListener { observed += it }

        val request = request()
        val outputDir = newProjectTempFolder("downloads")
        val waiting = DownloadCoordinator.enqueueForServiceStart(request, outputDir).getOrThrow()
        val launch = DownloadCoordinator.claimPendingLaunch()

        close.close()

        assertEquals(DownloadStage.Waiting, waiting.stage)
        assertEquals(listOf(DownloadStage.Waiting), observed.map { it.stage })
        assertNotNull(launch)
        assertSame(request, launch!!.request)
        assertEquals(outputDir.absolutePath, launch.outputDirectory.absolutePath)
        assertNull(DownloadCoordinator.claimPendingLaunch())
    }

    @Test
    fun waitingQueueKeepsFifoOrderUntilEachLaunchActuallyStarts() {
        val snapshots = mutableListOf<List<String>>()
        val close = DownloadCoordinator.addPendingListener { requests ->
            snapshots += requests.map { it.title }
        }
        val first = request("第一条")
        val second = request("第二条")

        DownloadCoordinator.enqueueForServiceStart(first, newProjectTempFolder("first")).getOrThrow()
        DownloadCoordinator.enqueueForServiceStart(second, newProjectTempFolder("second")).getOrThrow()

        assertEquals(listOf("第一条", "第二条"), snapshots.last())
        val firstLaunch = DownloadCoordinator.claimPendingLaunch()
        assertNotNull(firstLaunch)
        assertEquals("第一条", firstLaunch!!.request.title)
        assertEquals("领取给服务后、真正执行前仍应显示等待", listOf("第一条", "第二条"), snapshots.last())

        DownloadCoordinator.activateLaunch(firstLaunch)
        assertEquals(listOf("第二条"), snapshots.last())
        DownloadCoordinator.finishActiveLaunch(firstLaunch)

        val secondLaunch = DownloadCoordinator.claimPendingLaunch()
        assertNotNull(secondLaunch)
        DownloadCoordinator.activateLaunch(secondLaunch!!)
        assertEquals(emptyList<String>(), snapshots.last())
        DownloadCoordinator.finishActiveLaunch(secondLaunch)
        close.close()
    }

    @Test
    fun cancelBeforeServiceAttachesCancellationIsRemembered() {
        val request = request()
        DownloadCoordinator.enqueueForServiceStart(request, newProjectTempFolder("downloads")).getOrThrow()

        DownloadCoordinator.cancelActive()
        val launch = DownloadCoordinator.claimPendingLaunch()
        DownloadCoordinator.activateLaunch(launch!!)
        val cancellation = MutableDownloadCancellation()
        DownloadCoordinator.attachCancellation(cancellation)

        assertTrue("早取消请求应传递给刚 attach 的任务", cancellation.isCancellationRequested)
    }

    @Test
    fun cleanupOwnsCoordinatorBoundaryUntilActionFinishes() {
        val cleanupEntered = CountDownLatch(1)
        val releaseCleanup = CountDownLatch(1)
        val launchAttempted = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val outputDirectory = newProjectTempFolder("task-4-coordinator-cleanup")
        val launchFailure = AtomicReference<Throwable?>()
        try {
            val cleanup = executor.submit<IdleDownloadActionResult<String>> {
                DownloadCoordinator.runWhenIdle {
                    cleanupEntered.countDown()
                    assertTrue(releaseCleanup.await(2, TimeUnit.SECONDS))
                    "cleared"
                }
            }
            assertTrue(cleanupEntered.await(2, TimeUnit.SECONDS))

            val launchThread = Thread {
                launchAttempted.countDown()
                runCatching {
                    DownloadCoordinator.enqueueForServiceStart(request(), outputDirectory).getOrThrow()
                }.onFailure(launchFailure::set)
            }.apply { start() }
            assertTrue(launchAttempted.await(2, TimeUnit.SECONDS))
            assertThreadBlocked(launchThread)

            releaseCleanup.countDown()
            assertEquals(IdleDownloadActionResult.Executed("cleared"), cleanup.get(2, TimeUnit.SECONDS))
            launchThread.join(2_000)
            assertFalse(launchThread.isAlive)
            assertNull(launchFailure.get())
        } finally {
            releaseCleanup.countDown()
            executor.shutdownNow()
            outputDirectory.deleteRecursively()
        }
    }

    @Test
    fun activeOrPendingDownloadRejectsCleanupWithoutRunningAction() {
        val nonTerminalStages = DownloadStage.entries.filterNot {
            it in setOf(
                DownloadStage.Completed,
                DownloadStage.Failed,
                DownloadStage.Canceled,
                DownloadStage.Idle,
            )
        }

        nonTerminalStages.forEach { stage ->
            DownloadCoordinator.resetForTests()
            DownloadCoordinator.publish(DownloadTaskState(stage = stage))
            var cleanupRan = false

            val result = DownloadCoordinator.runWhenIdle {
                cleanupRan = true
                "cleared"
            }

            assertEquals("stage=$stage", IdleDownloadActionResult.ActiveDownload, result)
            assertFalse("stage=$stage", cleanupRan)
        }

        DownloadCoordinator.resetForTests()
        val pendingDirectory = newProjectTempFolder("task-4-coordinator-pending")
        DownloadCoordinator.enqueueForServiceStart(
            request(),
            pendingDirectory,
        ).getOrThrow()

        assertEquals(
            IdleDownloadActionResult.ActiveDownload,
            DownloadCoordinator.runWhenIdle { "cleared" },
        )
        pendingDirectory.deleteRecursively()
    }

    @Test
    fun projectTempGuardRejectsNormalizedEscape() {
        val escaped = ProjectTestPaths.qaDataRoot.resolve("..").resolve("outside").normalize()

        assertTrue(runCatching { ProjectTestPaths.requireInsideQaData(escaped) }.isFailure)
    }

    @Test
    fun serviceSourceRunsRealPipelineAndUpdatesForegroundNotification() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
            "src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
        ).readText()
        val manifest = sourceFile(
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml",
        ).readText()

        assertTrue(source.contains("DownloadPipeline("))
        assertTrue(source.contains("YtdlpDownloadEngine("))
        assertTrue(source.contains("NativeMuxerMediaProcessor("))
        assertTrue(source.contains("notificationController.notifyForegroundState"))
        assertTrue(source.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST"))
        assertTrue(source.contains("Build.VERSION_CODES.Q"))
        assertTrue(source.contains("startForeground("))
        assertFalse(source.contains("ServiceCompat.startForeground"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertTrue(source.contains("DownloadCoordinator.publish"))
        assertTrue(source.contains("Executors.newSingleThreadExecutor"))
        assertFalse(source.contains("Thread {"))
        assertTrue(source.contains("if (intent?.action == ActionCancel)"))
        assertTrue(source.contains("DownloadCoordinator.cancelActive()"))
        assertTrue(source.contains("stopSelf(startId)"))
    }

    @Test
    fun serviceSavesAllCompletedOutputsAndCleansPrivateTaskBeforeRecordingHistory() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
            "src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
        ).readText()

        assertTrue(source.contains("DownloadStage.Exporting"))
        assertTrue(source.contains("ExportController.copyToSafTreeWithDocuments"))
        assertTrue(source.contains("ExportController.cleanupExportedPrivateTask"))
        assertTrue(source.contains(".onFailure {"))
        assertTrue(source.contains("Log.w("))
        assertTrue(source.contains("ExportController.markAppPrivateTaskCompleted"))
        assertTrue(source.contains("result.outputs.map"))
        assertTrue(
            source.indexOf("ExportController.copyToSafTreeWithDocuments") <
                source.indexOf("ExportController.cleanupExportedPrivateTask"),
        )
        assertTrue(source.indexOf("val terminalState = exportCompletedOutputs") < source.indexOf("historyRecorder.recordTerminal"))
        assertTrue(source.contains("ExportController.treeExportFailureMessage()"))
    }

    @Test
    fun servicePassesCancellationIntoSafTreeExportAndRecordsCanceledState() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
            "src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
        ).readText()

        assertTrue(source.contains("exportCompletedOutputs(result, storageTarget, cancellation)"))
        assertTrue(source.contains("isCancellationRequested = { cancellation.isCancellationRequested }"))
        assertTrue(source.contains("is CancellationException -> result.state.canceled()"))
        assertTrue(source.indexOf("val terminalState = exportCompletedOutputs") < source.indexOf("historyRecorder.recordTerminal"))
    }

    private fun request(title: String = "测试视频"): DownloadRequest {
        return DownloadRequest.fromAnalysis(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = VideoAnalysis(
                title = title,
                durationSeconds = 60,
                thumbnailUrl = null,
                formats = listOf(
                    VideoFormat(
                        id = "18",
                        ext = "mp4",
                        height = 360,
                        label = "360p",
                        hasVideo = true,
                        hasAudio = true,
                        mergeRequired = false,
                        isSupported = true,
                        videoCodec = "avc1",
                        audioCodec = "mp4a",
                    ),
                ),
                subtitles = emptyList(),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoOnly,
                selectedVideoFormatId = "18",
            ),
        ).getOrThrow()
    }

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("source file not found: ${candidates.joinToString()}")
    }

    private fun newProjectTempFolder(name: String): File {
        return File(tempRoot, name).also { directory ->
            check(directory.mkdirs())
            guardProjectTempPath(directory.toPath())
        }
    }

    private fun guardProjectTempPath(path: Path) {
        ProjectTestPaths.requireInsideQaData(path)
    }

    private fun assertThreadBlocked(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (thread.state != Thread.State.BLOCKED && thread.isAlive && System.nanoTime() < deadline) {
            Thread.onSpinWait()
        }
        assertEquals(Thread.State.BLOCKED, thread.state)
    }
}
