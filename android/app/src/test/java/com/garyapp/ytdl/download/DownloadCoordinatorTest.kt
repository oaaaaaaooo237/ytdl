package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadCoordinatorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun resetCoordinator() {
        DownloadCoordinator.resetForTests()
    }

    @Test
    fun enqueuePublishesWaitingStateAndServiceConsumesExactlyOneLaunch() {
        val observed = mutableListOf<DownloadTaskState>()
        val close = DownloadCoordinator.addListener { observed += it }

        val request = request()
        val outputDir = temp.newFolder("downloads")
        val waiting = DownloadCoordinator.enqueueForServiceStart(request, outputDir).getOrThrow()
        val launch = DownloadCoordinator.consumePendingLaunch()

        close.close()

        assertEquals(DownloadStage.Waiting, waiting.stage)
        assertEquals(listOf(DownloadStage.Waiting), observed.map { it.stage })
        assertNotNull(launch)
        assertSame(request, launch!!.request)
        assertEquals(outputDir.absolutePath, launch.outputDirectory.absolutePath)
        assertNull(DownloadCoordinator.consumePendingLaunch())
    }

    @Test
    fun cancelBeforeServiceAttachesCancellationIsRemembered() {
        val request = request()
        DownloadCoordinator.enqueueForServiceStart(request, temp.newFolder("downloads")).getOrThrow()

        DownloadCoordinator.cancelActive()
        val launch = DownloadCoordinator.consumePendingLaunch()
        val cancellation = MutableDownloadCancellation()
        DownloadCoordinator.attachCancellation(cancellation)

        assertNotNull(launch)
        assertTrue("早取消请求应传递给刚 attach 的任务", cancellation.isCancellationRequested)
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
        assertTrue(source.contains("Thread"))
        assertTrue(source.contains("if (intent?.action == ActionCancel)"))
        assertTrue(source.contains("DownloadCoordinator.cancelActive()"))
        assertTrue(source.contains("stopSelf(startId)"))
    }

    @Test
    fun serviceExportsAllCompletedOutputsBeforeRecordingTerminalHistory() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
            "src/main/java/com/garyapp/ytdl/download/DownloadService.kt",
        ).readText()

        assertTrue(source.contains("DownloadStage.Exporting"))
        assertTrue(source.contains("ExportController.copyToSafTree"))
        assertTrue(source.contains("result.outputs.map"))
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

    private fun request(): DownloadRequest {
        return DownloadRequest.fromAnalysis(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = VideoAnalysis(
                title = "测试视频",
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
}
