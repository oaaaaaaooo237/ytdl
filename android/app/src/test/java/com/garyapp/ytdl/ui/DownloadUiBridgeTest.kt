package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.download.DownloadOutputFile
import com.garyapp.ytdl.download.DownloadOutputKind
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadUiBridgeTest {
    @Test
    fun startRealDownloadSourceDoesNotCallLegacySingleFileFallback() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertFalse(source.contains("downloadSingleFile("))
    }

    @Test
    fun ytdlAppSourceDoesNotExposeDemoQueueFakeHistoryOrUncheckedPermissionState() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertFalse(source.contains("真实下载路由将在后续任务接入"))
        assertFalse(source.contains("演示队列"))
        assertFalse(source.contains("已完成（18）"))
        assertFalse(source.contains("失败（1）"))
        assertFalse(source.contains("自然风光演示片段"))
        assertFalse(source.contains("海边散步片段"))
        assertFalse(source.contains("SettingLineCard(\"通知权限\", \"已允许\""))
        assertTrue(source.contains("M6 下载管线"))
        assertTrue(source.contains("暂无真实下载任务"))
        assertTrue(source.contains("暂无真实历史记录，完成下载后会显示"))
        assertTrue(source.contains("前台验收待完成"))
    }

    @Test
    fun ytdlAppSourceDoesNotUseRunningQueueCopyForTerminalStates() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertFalse(source.contains("if (state.hasRealTask) \"1 个真实任务正在处理\" else \"暂无真实下载任务\""))
        assertFalse(source.contains("subtitle = \"真实下载中 ·"))
        assertTrue(source.contains("最近任务已完成"))
        assertTrue(source.contains("最近任务失败"))
        assertTrue(source.contains("最近任务已取消"))
        assertTrue(source.contains("当前阶段"))
    }

    @Test
    fun buildAppliedDownloadRequestRequiresCompletedAnalysis() {
        val result = buildAppliedDownloadRequest(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = null,
            appliedSelection = FormatSelection(),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("请先分析"))
    }

    @Test
    fun ytdlAppSourceDoesNotMarkRequestBuildFailureAsRealQueueTask() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertFalse(source.contains("requestResult.isFailure") && source.contains("downloadStatus = \"下载失败\""))
        assertTrue(source.contains("DownloadCoordinator.startForegroundDownload"))
    }

    @Test
    fun formatPageDoesNotPretendSubtitleFileIsAlreadySelected() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertFalse(source.contains("SettingLineCard(\"字幕\", \"下载文件\""))
        assertTrue(source.contains("字幕待选择"))
    }

    @Test
    fun buildAppliedDownloadRequestUsesAppliedSelectionForProgressiveMedia() {
        val result = buildAppliedDownloadRequest(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            appliedSelection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
            ),
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        assertEquals(DownloadRoute.DirectSingleFile(formatId = "18"), result.getOrThrow().route)
        assertTrue(result.getOrThrow().selectedSubtitles.isEmpty())
    }

    @Test
    fun pipelineStagesMapToUserVisibleDownloadStatus() {
        assertEquals("空闲", userVisibleDownloadStatus(DownloadStage.Idle))
        assertEquals("下载视频", userVisibleDownloadStatus(DownloadStage.DownloadingVideo))
        assertEquals("下载音频", userVisibleDownloadStatus(DownloadStage.DownloadingAudio))
        assertEquals("下载字幕", userVisibleDownloadStatus(DownloadStage.DownloadingSubtitles))
        assertEquals("原生合并", userVisibleDownloadStatus(DownloadStage.Merging))
        assertEquals("导出待接入", userVisibleDownloadStatus(DownloadStage.Exporting))
        assertEquals("下载完成", userVisibleDownloadStatus(DownloadStage.Completed))
        assertEquals("下载失败", userVisibleDownloadStatus(DownloadStage.Failed))
        assertEquals("已取消", userVisibleDownloadStatus(DownloadStage.Canceled))
    }

    @Test
    fun queueViewStateDoesNotCarryProgressIntoStageWithoutProgress() {
        val state = RuntimeDownloadState()
            .withPipelineStateForUiTest(
                DownloadTaskState(stage = DownloadStage.DownloadingVideo).withProgress(
                    DownloadProgress(
                        status = "downloading",
                        percent = 42.0,
                        downloadedBytes = 42L,
                        totalBytes = 100L,
                        speedBytesPerSecond = null,
                        etaSeconds = null,
                        filename = null,
                    ),
                ),
            )
            .withPipelineStateForUiTest(DownloadTaskState(stage = DownloadStage.DownloadingAudio))

        assertEquals(null, state.progressPercent)
        assertEquals(null, state.downloadedBytes)
        assertEquals(null, state.totalBytes)
        assertEquals("当前阶段 · 下载音频", queueCardSubtitleForUiTest(state))
        assertEquals("0%", queueCardStatusForUiTest(state))
    }

    @Test
    fun terminalQueueHeaderDoesNotSayDownloading() {
        val request = request()
        val completed = RuntimeDownloadState().withPipelineStateForUiTest(DownloadTaskState(stage = DownloadStage.Completed, request = request))
        val failed = RuntimeDownloadState().withPipelineStateForUiTest(DownloadTaskState.waiting(request).failed("下载失败"))
        val canceled = RuntimeDownloadState().withPipelineStateForUiTest(DownloadTaskState.waiting(request).canceled())

        assertEquals("最近任务已完成", queueHeaderTitleForUiTest(completed))
        assertEquals("最近任务失败", queueHeaderTitleForUiTest(failed))
        assertEquals("最近任务已取消", queueHeaderTitleForUiTest(canceled))
    }

    @Test
    fun idleStateClearsFalseQueueTaskAfterForegroundStartFailure() {
        val state = RuntimeDownloadState()
            .withPipelineStateForUiTest(DownloadTaskState(stage = DownloadStage.Waiting))
            .withPipelineStateForUiTest(DownloadTaskState.idle())

        assertFalse(state.hasRealTask)
        assertEquals("暂无真实下载任务", queueHeaderTitleForUiTest(state))
        assertEquals("暂无真实下载任务", queueHeaderSummaryForUiTest(state))
    }

    @Test
    fun failedServiceStateWithoutRequestDoesNotCreateRealQueueTask() {
        val state = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState(stage = DownloadStage.Failed, request = null, errorMessage = "没有待处理的下载任务。"),
        )

        assertFalse(state.hasRealTask)
        assertEquals("暂无真实下载任务", queueHeaderTitleForUiTest(state))
    }

    @Test
    fun newWaitingTaskClearsPreviousOutputMetadata() {
        val completed = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState(
                stage = DownloadStage.Completed,
                outputs = listOf(DownloadOutputFile(DownloadOutputKind.Media, "old-output.mp4", 10L)),
            ),
        )
        val next = completed.withPipelineStateForUiTest(DownloadTaskState(stage = DownloadStage.Waiting))

        assertEquals("", next.outputPath)
        assertEquals(0L, next.outputBytes)
    }

    private fun analysisWith(vararg formats: VideoFormat) = VideoAnalysis(
        title = "测试视频",
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = formats.toList(),
        subtitles = emptyList<SubtitleInfo>(),
    )

    private fun progressiveFormat(id: String, height: Int) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = "avc1",
        audioCodec = "mp4a",
    )

    private fun request(): DownloadRequest {
        return DownloadRequest.fromAnalysis(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
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
