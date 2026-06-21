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
        assertFalse(source.contains("SettingLineCard(\"通知权限\", \"待系统确认"))
        assertFalse(source.contains("M6 下载管线"))
        assertTrue(source.contains("真实任务队列"))
        assertTrue(source.contains("暂无真实下载任务"))
        assertTrue(source.contains("暂无真实历史记录，完成下载后会显示"))
        assertTrue(source.contains("notificationPermissionSubtitle("))
        assertTrue(source.contains("ytdl-settings-notification-permission"))
        assertFalse(source.contains("SettingLineCard(\"隐私与授权说明\", \"查看说明\""))
        assertTrue(source.contains("settingsPrivacyLegalLines()"))
        assertTrue(source.contains("ytdl-settings-privacy-legal"))
    }

    @Test
    fun ytdlAppRegistersCoordinatorListenerForRealForegroundProgress() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("DownloadCoordinator.addListener"))
        assertTrue(source.contains("runtimeState.withPipelineState(state)"))
        assertTrue(source.contains("subscription.close()"))
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
        val label = subtitleSelectionLabelForUiTest(null, emptyList())

        assertFalse(label.contains("已选择"))
        assertFalse(label.contains("下载文件"))
        assertTrue(label.contains("不下载字幕"))
        assertTrue(label.contains("默认不下载"))
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
        assertEquals("导出中", userVisibleDownloadStatus(DownloadStage.Exporting))
        assertEquals("下载完成", userVisibleDownloadStatus(DownloadStage.Completed))
        assertEquals("下载失败", userVisibleDownloadStatus(DownloadStage.Failed))
        assertEquals("已取消", userVisibleDownloadStatus(DownloadStage.Canceled))
    }

    @Test
    fun notificationPermissionLabelsExposeRealRuntimeState() {
        assertEquals("系统无需单独授权", notificationPermissionSubtitleForUiTest(isGranted = true, runtimePermissionRequired = false))
        assertEquals("已允许", notificationPermissionSubtitleForUiTest(isGranted = true, runtimePermissionRequired = true))
        assertEquals("未授权 · 下载仍在应用内显示进度", notificationPermissionSubtitleForUiTest(isGranted = false, runtimePermissionRequired = true))

        assertEquals("请求", notificationPermissionTrailingForUiTest(isGranted = false, runtimePermissionRequired = true))
        assertEquals("已允许", notificationPermissionTrailingForUiTest(isGranted = true, runtimePermissionRequired = true))
        assertEquals("系统", notificationPermissionTrailingForUiTest(isGranted = true, runtimePermissionRequired = false))
    }

    @Test
    fun notificationPermissionStateRefreshesFromSystemOnResume() {
        assertEquals(
            false,
            refreshedNotificationPermissionStateForUiTest(
                currentValue = true,
                systemValue = false,
                runtimePermissionRequired = true,
            ),
        )
        assertEquals(
            true,
            refreshedNotificationPermissionStateForUiTest(
                currentValue = false,
                systemValue = true,
                runtimePermissionRequired = true,
            ),
        )
        assertEquals(
            true,
            refreshedNotificationPermissionStateForUiTest(
                currentValue = false,
                systemValue = false,
                runtimePermissionRequired = false,
            ),
        )
    }

    @Test
    fun settingsPrivacyLegalTextStatesConcreteBoundariesWithoutSecrets() {
        val lines = settingsPrivacyLegalLinesForUiTest()
        val joined = lines.joinToString("\n")

        assertTrue(lines.size >= 4)
        assertTrue(joined.contains("http/https"))
        assertTrue(joined.contains("Cookies"))
        assertTrue(joined.contains("文件引用"))
        assertTrue(joined.contains("不保存内容"))
        assertTrue(joined.contains("App 私有目录"))
        assertTrue(joined.contains("导出"))
        assertTrue(joined.contains("DRM") || joined.contains("未授权"))
        listOf("SID=secret", "Authorization", "Bearer raw-token", "--cookies D:/private/cookies.txt").forEach {
            assertFalse("privacy text leaked $it", joined.contains(it))
        }
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
        val completed = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState(
                stage = DownloadStage.Completed,
                request = request,
                outputs = listOf(DownloadOutputFile(DownloadOutputKind.Media, "done.mp4", 10L)),
            ),
        )
        val failed = RuntimeDownloadState().withPipelineStateForUiTest(DownloadTaskState.waiting(request).failed("下载失败"))
        val canceled = RuntimeDownloadState().withPipelineStateForUiTest(DownloadTaskState.waiting(request).canceled())

        assertEquals("最近任务已完成", queueHeaderTitleForUiTest(completed))
        assertEquals("最近任务失败", queueHeaderTitleForUiTest(failed))
        assertEquals("最近任务已取消", queueHeaderTitleForUiTest(canceled))
    }

    @Test
    fun completedStateWithoutMediaOutputDoesNotRenderAsCompletedDownload() {
        val state = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState(
                stage = DownloadStage.Completed,
                request = request(),
                outputs = emptyList(),
            ),
        )

        assertEquals("下载失败", state.downloadStatus)
        assertEquals(null, state.progressPercent)
        assertFalse(state.userMessage.contains("下载完成"))
        assertEquals("最近任务失败", queueHeaderTitleForUiTest(state))
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

    @Test
    fun ytdlAppSourceDoesNotHardBindComponentsToReferencePaletteConstants() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        listOf(
            "private val FormatAccent =",
            "private val HistoryAccent =",
            "private val SuccessGreen =",
            "private val SoftText =",
            "color = FormatAccent",
            "accent = HistoryAccent",
            "color = SuccessGreen",
            "color = SoftText",
        ).forEach { forbidden ->
            assertFalse("source still hard-binds UI to reference palette: $forbidden", source.contains(forbidden))
        }
        assertTrue(source.contains("LocalYtdlAppPalette.current"))
        assertTrue(source.contains("ytdl-settings-color-preset"))
    }
    @Test
    fun startDownloadGateRequiresAnalysisAndUserAuthorization() {
        val analyzed = RuntimeDownloadState(
            analysis = analysisWith(progressiveFormat(id = "18", height = 360)),
        )

        assertFalse(canStartDownloadForUiTest(RuntimeDownloadState(), hasUserConfirmed = true))
        assertFalse(canStartDownloadForUiTest(analyzed, hasUserConfirmed = false))
        assertFalse(canStartDownloadForUiTest(analyzed.copy(isAnalyzing = true), hasUserConfirmed = true))
        assertFalse(canStartDownloadForUiTest(analyzed.copy(isDownloading = true), hasUserConfirmed = true))
        assertTrue(canStartDownloadForUiTest(analyzed, hasUserConfirmed = true))
    }

    @Test
    fun queueScrollIndicatorOnlyAppearsForRealQueueState() {
        assertFalse(shouldShowQueueScrollIndicatorForUiTest(RuntimeDownloadState()))
        assertTrue(shouldShowQueueScrollIndicatorForUiTest(RuntimeDownloadState(isDownloading = true)))
        assertTrue(shouldShowQueueScrollIndicatorForUiTest(RuntimeDownloadState(downloadStatus = "下载视频")))
    }

    @Test
    fun historySearchAndTypeFilterUseLoadedLocalItems() {
        val video = HistoryUiItem(
            id = 1,
            title = "航拍测试视频",
            meta = "视频+音频 · 720p MP4",
            badge = "完成",
            outputUri = "app-private://outputs/video.mp4",
            status = "completed",
            completedAt = 1_000L,
        )
        val audio = HistoryUiItem(
            id = 2,
            title = "访谈音频",
            meta = "仅音频 · m4a",
            badge = "完成",
            outputUri = "app-private://outputs/audio.m4a",
            status = "completed",
            completedAt = 2_000L,
        )
        val items = listOf(video, audio)

        assertEquals(listOf(video), filterHistoryItemsForUiTest(items, query = "航拍", selectedFilterIndex = 0))
        assertEquals(listOf(video), filterHistoryItemsForUiTest(items, query = "", selectedFilterIndex = 1))
        assertEquals(listOf(audio), filterHistoryItemsForUiTest(items, query = "", selectedFilterIndex = 2))
        assertEquals(emptyList<HistoryUiItem>(), filterHistoryItemsForUiTest(items, query = "不存在", selectedFilterIndex = 0))
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
