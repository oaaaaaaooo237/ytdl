package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.DownloadOutputFile
import com.garyapp.ytdl.download.DownloadOutputKind
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.ui.theme.ytdlAppPaletteForPreset
import androidx.compose.ui.graphics.Color
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
    fun ytdlAppStoresLargeDownloadsOutsideCacheDirectory() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()
        val fileProviderPaths = sourceFile(
            "app/src/main/res/xml/ytdl_file_paths.xml",
            "src/main/res/xml/ytdl_file_paths.xml",
        ).readText()

        assertFalse(source.contains("val outputDir = File(context.cacheDir, \"gui-downloads\")"))
        assertTrue(source.contains("File(context.filesDir, \"gui-downloads\")"))
        assertTrue(source.contains("legacyRoots = listOf(File(context.cacheDir, \"gui-downloads\"))"))
        assertTrue(fileProviderPaths.contains("<files-path"))
        assertTrue(fileProviderPaths.contains("name=\"legacy_gui_downloads\""))
    }

    @Test
    fun queueCancelActionUsesStableTouchTarget() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("testTag(\"ytdl-queue-cancel-action\")"))
        assertTrue(source.contains("defaultMinSize(minWidth = 56.dp, minHeight = 36.dp)"))
    }

    @Test
    fun historyDeleteRequiresUserConfirmationDialog() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("pendingDeleteHistoryItem"))
        assertTrue(source.contains("AlertDialog"))
        assertTrue(source.contains("确认删除历史记录"))
        assertTrue(source.contains("ytdl-history-delete-dialog"))
        assertTrue(source.contains("ytdl-history-delete-confirm"))
        assertTrue(source.contains("ytdl-history-delete-cancel"))
        assertTrue(source.contains("ytdl-history-action-${'$'}{item.id}-${'$'}action"))
    }

    @Test
    fun historyPageRendersRuntimeRecoveryMessage() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("userMessage = runtimeState.userMessage"))
        assertTrue(source.contains("userMessage: String,\n    onHistoryQueryChange"))
        assertTrue(source.contains("if (shouldShowRuntimeMessage(userMessage))"))
    }

    @Test
    fun missingHistoryOutputMessageIsActionNeutralAndPathFree() {
        val message = historyMissingLocalOutputMessageForUiTest(
            IllegalStateException("输出文件不存在或为空，不能导出。 D:/private/cookies.txt"),
        )

        assertTrue(message.contains("本地文件不存在或为空"))
        assertTrue(message.contains("重新下载"))
        assertFalse(message.contains("不能导出"))
        assertFalse(message.contains("D:/private"))
        assertFalse(message.contains("cookies.txt"))
    }

    @Test
    fun connectedUiTestDoesNotClearAllHistoryRowsBeforeLaunch() {
        val source = sourceFile(
            "app/src/androidTest/java/com/garyapp/ytdl/ui/YtdlAppUiTest.kt",
            "src/androidTest/java/com/garyapp/ytdl/ui/YtdlAppUiTest.kt",
        ).readText()

        assertFalse(source.contains("clearHistoryRows("))
        assertFalse(source.contains("rows.forEach { item ->"))
        assertFalse(source.contains("deleteById(item.id)"))
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
    fun recommendedSubtitlePrefersEnglishVttOverFirstJson3AutomaticSubtitle() {
        val json3Automatic = subtitle(language = "ab", ext = "json3", source = SubtitleSource.Automatic)
        val englishVttAutomatic = subtitle(language = "en", ext = "vtt", source = SubtitleSource.Automatic)

        val recommended = recommendedSubtitleForUiTest(listOf(json3Automatic, englishVttAutomatic))

        assertEquals(englishVttAutomatic, recommended)
    }

    @Test
    fun recommendedSubtitleUsesLanguagePriorityAndManualTieBreakWithinVtt() {
        val englishManual = subtitle(language = "en", ext = "vtt", source = SubtitleSource.Manual)
        val simplifiedChineseAutomatic = subtitle(language = "zh-Hans", ext = "vtt", source = SubtitleSource.Automatic)
        val simplifiedChineseManual = subtitle(language = "zh-Hans", ext = "vtt", source = SubtitleSource.Manual)

        val recommended = recommendedSubtitleForUiTest(
            listOf(englishManual, simplifiedChineseAutomatic, simplifiedChineseManual),
        )

        assertEquals(simplifiedChineseManual, recommended)
    }

    @Test
    fun recommendedSubtitleFallsBackToPreferredLanguageWhenVttIsUnavailableThenFirstSubtitle() {
        val firstUnknown = subtitle(language = "ab", ext = "json3", source = SubtitleSource.Automatic)
        val englishSrv = subtitle(language = "en", ext = "srv3", source = SubtitleSource.Manual)

        assertEquals(englishSrv, recommendedSubtitleForUiTest(listOf(firstUnknown, englishSrv)))
        assertEquals(firstUnknown, recommendedSubtitleForUiTest(listOf(firstUnknown)))
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
        assertTrue(joined.contains("历史缩略图"))
        assertTrue(joined.contains("公开预览图"))
        assertTrue(joined.contains("不携带 Cookies"))
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
    fun activeQueueStageWithoutReliablePercentUsesIndeterminateProgress() {
        val state = RuntimeDownloadState()
            .withPipelineStateForUiTest(
                DownloadTaskState.waiting(mergeRequest())
                    .atStage(DownloadStage.DownloadingAudio),
            )

        val progress = queueProgressPresentationForUiTest(state)

        assertEquals(null, progress.fraction)
        assertTrue(progress.isIndeterminate)
        assertEquals("33%", queueCardStatusForUiTest(state))
    }

    @Test
    fun indeterminateQueueProgressUsesAnimatedInProgressBar() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("rememberInfiniteTransition"))
        assertTrue(source.contains("private fun animatedQueueIndeterminateFraction()"))
        assertTrue(source.contains("progress.isIndeterminate -> animatedQueueIndeterminateFraction()"))
        assertTrue(source.contains("queue-indeterminate-progress-width"))
        assertTrue(source.contains("RepeatMode.Reverse"))
        assertFalse(source.contains("?: if (progress.isIndeterminate) 0.08f else 0f"))
    }

    @Test
    fun mergeQueueStateSeparatesStageProgressFromOverallProgress() {
        val request = mergeRequest()
        val state = RuntimeDownloadState()
            .withPipelineStateForUiTest(
                DownloadTaskState.waiting(request)
                    .atStage(DownloadStage.DownloadingVideo)
                    .withProgress(
                        DownloadProgress(
                            status = "downloading",
                            percent = 50.0,
                            downloadedBytes = 50L,
                            totalBytes = 100L,
                            speedBytesPerSecond = null,
                            etaSeconds = null,
                            filename = "video.mp4",
                        ),
                    ),
            )

        assertEquals(50.0, state.progressPercent)
        assertEquals(16.666666666666664, state.overallProgressPercent)
        assertEquals("16%", queueCardStatusForUiTest(state))
        assertEquals(
            listOf(
                QueueStageItem("下载视频", QueueStageStatus.Current),
                QueueStageItem("下载音频", QueueStageStatus.Pending),
                QueueStageItem("原生合并", QueueStageStatus.Pending),
            ),
            queueStageItemsForUiTest(state),
        )
        assertFalse(queueStageItemsForUiTest(state).any { it.label == "字幕文件" })
    }

    @Test
    fun mergeQueueStateMarksCompletedStagesBeforeCurrentStage() {
        val request = mergeRequest()
        val state = RuntimeDownloadState()
            .withPipelineStateForUiTest(
                DownloadTaskState.waiting(request)
                    .atStage(DownloadStage.Merging),
            )

        assertEquals(
            listOf(
                QueueStageItem("下载视频", QueueStageStatus.Completed),
                QueueStageItem("下载音频", QueueStageStatus.Completed),
                QueueStageItem("原生合并", QueueStageStatus.Current),
            ),
            queueStageItemsForUiTest(state),
        )
        assertEquals("66%", queueCardStatusForUiTest(state))
    }

    @Test
    fun mergeQueueStateAddsSubtitleStageOnlyWhenSubtitleSelected() {
        val request = mergeRequest().copy(
            selectedSubtitles = listOf(subtitle(language = "zh-Hans", ext = "vtt")),
        )
        val state = RuntimeDownloadState()
            .withPipelineStateForUiTest(
                DownloadTaskState.waiting(request)
                    .atStage(DownloadStage.DownloadingSubtitles),
            )

        assertEquals(
            listOf(
                QueueStageItem("下载视频", QueueStageStatus.Completed),
                QueueStageItem("下载音频", QueueStageStatus.Completed),
                QueueStageItem("原生合并", QueueStageStatus.Completed),
                QueueStageItem("字幕文件", QueueStageStatus.Current),
            ),
            queueStageItemsForUiTest(state),
        )
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
    fun queueMetaExplainsPrivateOutputUsesTitleTimeExportRuleInsteadOfInternalMergedName() {
        val state = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState(
                stage = DownloadStage.Completed,
                request = mergeRequest(),
                outputs = listOf(
                    DownloadOutputFile(
                        DownloadOutputKind.Media,
                        "/data/user/0/com.garyapp.ytdl/files/gui-downloads/task-1/merged-136-140.mp4",
                        4096L,
                    ),
                ),
            ),
        )

        val meta = queueCardMetaForUiTest(state)

        assertTrue(meta.contains("4.0 KB / 4.0 KB"))
        assertTrue(meta.contains("App 私有目录"))
        assertTrue(meta.contains("导出名：标题+时间；重名加序号"))
        assertFalse(meta.contains("merged-136-140.mp4"))
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

    @Test
    fun historyModelAndCardSupportRealThumbnails() {
        val historyEntityFields = HistoryItemEntity::class.java.declaredFields.map { it.name }
        val historyUiFields = HistoryUiItem::class.java.declaredFields.map { it.name }
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(historyEntityFields.contains("thumbnailUrl"))
        assertTrue(historyUiFields.contains("thumbnailUrl"))
        assertTrue(historyUiFields.contains("formatBadge"))
        assertTrue(source.contains("ytdl-history-thumbnail-image"))
        assertTrue(source.contains("\"ytdl-history-status-badge\""))
        assertTrue(source.contains("\"ytdl-history-format-badge\""))
        assertTrue(source.contains("verticalArrangement = Arrangement.SpaceBetween"))
        assertTrue(source.contains("defaultMinSize(minWidth = CardPillBadgeMinWidth, minHeight = CardPillBadgeMinHeight)"))
        assertTrue(source.contains("private val CardPillBadgeMinWidth = 64.dp"))
        assertTrue(source.contains("private val CardPillBadgeMinHeight = 30.dp"))
        assertTrue(source.contains("HistoryThumbnailLoader.load(item.thumbnailUrl.orEmpty())"))
    }

    @Test
    fun historySearchHeaderUsesRightSideFilterIconAffordance() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("HistorySearchHeader("))
        assertTrue(source.contains("HistoryFilterButton("))
        assertTrue(source.contains("tag = \"ytdl-history-filter-action\""))
        assertTrue(source.contains("Icon(SearchIcon"))
        assertTrue(source.contains("HistoryFilterIcon"))
        assertTrue(source.contains("contentDescription = \"筛选："))
        assertTrue(source.contains("onFilterClick = { onHistoryFilterChange(nextHistoryFilterIndex(selectedFilterIndex)) }"))
        assertFalse(source.contains("leadingIcon = { Text(\"⌕\") }"))
    }

    @Test
    fun historyActionsRenderAsIconTextChipsWithoutLosingCallbacks() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("HistoryActionChip("))
        assertTrue(source.contains("icon = historyActionIcon(action)"))
        assertTrue(source.contains("contentDescription = action"))
        assertTrue(source.contains("testTag(\"ytdl-history-action-${'$'}{item.id}-${'$'}action\")"))
        assertTrue(source.contains("\"打开\" -> onOpen"))
        assertTrue(source.contains("\"分享\" -> onShare"))
        assertTrue(source.contains("\"导出\" -> onExport"))
        assertTrue(source.contains("else -> onDelete"))
        assertTrue(source.contains("filterNot { it == \"删除\" }"))
    }

    @Test
    fun historyThumbnailLoadingUsesBoundedCacheInsteadOfPerCardRawThreads() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("HistoryThumbnailLoader"))
        assertTrue(source.contains("LruCache<String, Bitmap>(HistoryThumbnailCacheMaxItems)"))
        assertTrue(source.contains("Executors.newFixedThreadPool(2)"))
        assertTrue(source.contains("AtomicBoolean(false)"))
        assertTrue(source.contains("historyThumbnailCache"))
        assertTrue(source.contains("loadThumbnailBitmap(thumbnailUrl, targetSizePx = HistoryThumbnailTargetPx)"))
        assertTrue(source.contains("BitmapFactory.Options"))
        assertTrue(source.contains("calculateInSampleSize"))
        assertFalse(source.contains("Thread {\n            val bitmap = loadThumbnailBitmap(item.thumbnailUrl.orEmpty())"))
        assertFalse(source.contains("ConcurrentHashMap<String, Bitmap>"))
    }

    @Test
    fun historyRowsPassThumbnailUrlToUiModel() {
        val rows = listOf(
            HistoryItemEntity.createSafe(
                "完成视频",
                60,
                "https",
                "host-hash",
                "youtube",
                "app-private://outputs/video.mp4",
                "1080p",
                "https://i.ytimg.com/vi/tkxzMEfp49Q/hqdefault.jpg?token=secret",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                null,
                1_000,
                1_000,
                1_000,
            ),
        )

        val item = historyUiItemsFromRows(rows).single()

        assertEquals("https://i.ytimg.com/vi/tkxzMEfp49Q/hqdefault.jpg", item.thumbnailUrl)
        assertTrue(!item.thumbnailUrl.orEmpty().contains("token=secret"))
    }

    @Test
    fun historyMetaAndActionsExposeIndependentSubtitleOnlyWhenPresent() {
        val withSubtitle = HistoryItemEntity.createSafe(
            "带字幕视频",
            60,
            "https",
            "host-hash",
            "youtube",
            "app-private://outputs/task-subtitle/merged-299-140.mp4",
            "视频 299 + 音频 140 + 字幕 en.vtt",
            HistoryItemEntity.STATUS_COMPLETED,
            100,
            "",
            "",
            null,
            1_000,
            1_000,
            1_000,
        ).also {
            setSubtitleOutputUris(it, "app-private://outputs/task-subtitle/captions.en.vtt")
        }
        val mediaOnly = HistoryItemEntity.createSafe(
            "无字幕视频",
            60,
            "https",
            "host-hash",
            "youtube",
            "app-private://outputs/task-media/video.mp4",
            "360p",
            HistoryItemEntity.STATUS_COMPLETED,
            100,
            "",
            "",
            null,
            2_000,
            2_000,
            2_000,
        )

        val items = historyUiItemsFromRows(listOf(withSubtitle, mediaOnly))

        assertTrue(items[0].meta.contains("媒体文件 + 独立字幕文件"))
        assertFalse(items[0].meta.contains("merged-299-140.mp4"))
        assertFalse(items[0].meta.contains("captions.en.vtt"))
        assertEquals(listOf("打开", "分享", "导出", "分享字幕", "导出字幕", "删除"), historyActionLabelsForUiTest(items[0]))
        assertFalse(items[1].meta.contains("独立字幕文件"))
        assertEquals(listOf("打开", "分享", "导出", "删除"), historyActionLabelsForUiTest(items[1]))
    }

    @Test
    fun resolutionBadgesUseRecordedSummaryInsteadOfGuessingFromLegacyFormatIds() {
        val legacyMerge = HistoryItemEntity.createSafe(
            "旧合并记录",
            60,
            "https",
            "host-hash",
            "youtube",
            "app-private://outputs/task-media/merged-137-140.mp4",
            "视频 137 + 音频 140",
            HistoryItemEntity.STATUS_COMPLETED,
            100,
            "",
            "",
            null,
            1_000,
            1_000,
            1_000,
        )
        val legacyShorts = HistoryItemEntity.createSafe(
            "旧 Shorts 记录",
            60,
            "https",
            "host-hash",
            "youtube",
            "app-private://outputs/task-media/merged-136-140.mp4",
            "视频 136 + 音频 140",
            HistoryItemEntity.STATUS_COMPLETED,
            100,
            "",
            "",
            null,
            2_000,
            2_000,
            2_000,
        )
        val legacyMultiSubtitle = HistoryItemEntity.createSafe(
            "旧多字幕记录",
            60,
            "https",
            "host-hash",
            "youtube",
            "app-private://outputs/task-media/merged-299-140.mp4",
            "视频 299 + 音频 140 + 字幕 en.vtt, zh-Hans.vtt",
            HistoryItemEntity.STATUS_COMPLETED,
            100,
            "",
            "",
            null,
            3_000,
            3_000,
            3_000,
        )

        val items = historyUiItemsFromRows(listOf(legacyMerge, legacyShorts, legacyMultiSubtitle))
        val item = items[0]
        val shorts = items[1]
        val multiSubtitle = items[2]

        assertEquals("", item.formatBadge)
        assertTrue(item.meta.contains("视频+音频"))
        assertTrue(item.meta.contains("原生合并"))
        assertFalse(item.meta.contains("137"))
        assertFalse(item.meta.contains("140"))
        assertEquals("", shorts.formatBadge)
        assertFalse(shorts.meta.contains("136"))
        assertFalse(shorts.meta.contains("140"))
        assertEquals("", multiSubtitle.formatBadge)
        assertTrue(multiSubtitle.meta.contains("视频+音频"))
        assertTrue(multiSubtitle.meta.contains("原生合并"))
        assertFalse(multiSubtitle.meta.contains("299"))
        assertFalse(multiSubtitle.meta.contains("140"))

        val shortVideoRequest = DownloadRequest(
            url = "https://www.youtube.com/shorts/example",
            title = "短视频",
            route = DownloadRoute.MergeRequired(videoFormatId = "137", audioFormatId = "140"),
            formatSummary = "720p MP4 需原生合并",
        )

        assertEquals("720p", formatResolutionBadgeForRequest(shortVideoRequest))
    }

    @Test
    fun historyFailureBadgeUsesRedAcrossColorPresets() {
        val codexLight = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetCodex, darkTheme = false)
        val codexDark = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetCodex, darkTheme = true)

        assertEquals(Color(0xFFFF5B63), historyStatusBadgeAccentForUiTest("失败", codexLight))
        assertEquals(Color(0xFFFF5B63), historyStatusBadgeAccentForUiTest("失败", codexDark))
        assertFalse(historyStatusBadgeAccentForUiTest("失败", codexLight) == codexLight.downloadAccent)
        assertFalse(historyStatusBadgeAccentForUiTest("失败", codexDark) == codexDark.downloadAccent)
    }

    @Test
    fun queueTerminalStatusBadgeUsesCompactStateTextAndOutcomeColor() {
        val palette = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetCodex)
        val request = mergeRequest()
        val completed = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState(
                stage = DownloadStage.Completed,
                request = request,
                outputs = listOf(DownloadOutputFile(DownloadOutputKind.Media, "done.mp4", 4096L)),
            ),
        )
        val failed = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState.waiting(request).failed("下载失败"),
        )

        assertEquals("完成", queueCardStatusForUiTest(completed))
        assertEquals(palette.successGreen, queueCardAccentForUiTest(completed, palette))
        assertEquals("失败", queueCardStatusForUiTest(failed))
        assertEquals(Color(0xFFFF5B63), queueCardAccentForUiTest(failed, palette))
    }

    @Test
    fun queueFormatBadgeUsesActiveRequestResolutionInsteadOfHardcodedValue() {
        val shortsRequest = DownloadRequest(
            url = "https://youtube.com/shorts/QBwpO9f0oAw",
            title = "短视频",
            route = DownloadRoute.MergeRequired(videoFormatId = "136", audioFormatId = "140"),
            formatSummary = "720p MP4 需原生合并",
        )
        val audioRequest = DownloadRequest(
            url = "https://youtu.be/audio",
            title = "音频",
            route = DownloadRoute.AudioOnly(audioFormatId = "140"),
            formatSummary = "音频 M4A 单文件",
        )

        assertEquals("720p", queueCardFormatBadgeForUiTest(RuntimeDownloadState(activeRequest = shortsRequest)))
        assertEquals("", queueCardFormatBadgeForUiTest(RuntimeDownloadState(activeRequest = audioRequest)))
    }

    @Test
    fun queueCardSourceRendersDedicatedFormatBadge() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("formatBadge = queueCardFormatBadge(state)"))
        assertTrue(source.contains("\"ytdl-queue-status-badge\""))
        assertTrue(source.contains("\"ytdl-queue-format-badge\""))
        assertTrue(source.contains("verticalArrangement = Arrangement.SpaceBetween"))
        assertTrue(source.contains("defaultMinSize(minWidth = CardPillBadgeMinWidth, minHeight = CardPillBadgeMinHeight)"))
        assertTrue(source.contains("private val CardPillBadgeMinWidth = 64.dp"))
        assertTrue(source.contains("private val CardPillBadgeMinHeight = 30.dp"))
        assertTrue(source.contains("style = MaterialTheme.typography.labelMedium"))
        assertFalse(source.contains("\"下载失败\", \"已取消\" -> palette.downloadAccent"))
        assertTrue(source.contains("\"下载失败\" -> HistoryFailureRed"))
    }

    @Test
    fun failedSubtitleRecordWithMediaOutputKeepsMediaActionsOnly() {
        val mediaSavedSubtitleFailed = HistoryItemEntity.createSafe(
            "媒体已保存字幕失败",
            60,
            "https",
            "host-hash",
            "youtube",
            "app-private://outputs/task-subtitle/merged-299-140.mp4",
            "视频 299 + 音频 140 + 字幕 zh-Hans.vtt",
            HistoryItemEntity.STATUS_FAILED,
            0,
            "",
            "",
            "所选字幕 zh-Hans 不可用，请取消字幕或重新分析后再试。",
            1_000,
            1_000,
            1_000,
        )

        val item = historyUiItemsFromRows(listOf(mediaSavedSubtitleFailed)).single()

        assertTrue(item.meta.contains("媒体文件"))
        assertTrue(item.meta.contains("所选字幕 zh-Hans 不可用"))
        assertEquals(listOf("打开", "分享", "导出", "删除"), historyActionLabelsForUiTest(item))
    }

    @Test
    fun completedQueueMetaShowsMediaAndSubtitleOutputsWithoutRawFileNames() {
        val state = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState(
                stage = DownloadStage.Completed,
                request = request(),
                outputs = listOf(
                    DownloadOutputFile(
                        DownloadOutputKind.Media,
                        "/data/user/0/com.garyapp.ytdl/files/gui-downloads/task-1/merged-299-140.mp4",
                        4096L,
                    ),
                    DownloadOutputFile(
                        DownloadOutputKind.Subtitle,
                        "/data/user/0/com.garyapp.ytdl/files/gui-downloads/task-1/captions.en.vtt",
                        512L,
                    ),
                ),
            ),
        )

        val meta = queueCardMetaForUiTest(state)

        assertTrue(meta.contains("媒体文件 + 独立字幕文件"))
        assertFalse(meta.contains("merged-299-140.mp4"))
        assertFalse(meta.contains("captions.en.vtt"))
        assertTrue(state.userMessage.contains("媒体文件 + 独立字幕文件"))
        assertFalse(state.userMessage.contains("merged-299-140.mp4"))
        assertFalse(state.userMessage.contains("captions.en.vtt"))
    }

    @Test
    fun historyExportSuggestionUsesTitleAndTimestampToAvoidRepeatedMergedNames() {
        val item = HistoryUiItem(
            id = 1,
            title = "Jalen/Brunson: Captain?",
            meta = "视频+音频",
            badge = "完成",
            outputUri = "app-private://outputs/task-1/merged-299-140.mp4",
            status = "completed",
            completedAt = 1_783_250_902_008L,
        )

        val name = suggestedExportDisplayNameForUiTest(item, "merged-299-140.mp4")

        assertTrue(name.startsWith("Jalen_Brunson_ Captain_"))
        assertTrue(name.endsWith(".mp4"))
        assertTrue(Regex("""Jalen_Brunson_ Captain_-\d{8}-\d{6}\.mp4""").matches(name))
        listOf("/", ":", "?").forEach { forbidden ->
            assertFalse(name.contains(forbidden))
        }
        assertFalse(name.startsWith("merged-299-140"))
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

    private fun subtitle(
        language: String,
        ext: String,
        source: SubtitleSource = SubtitleSource.Manual,
    ) = SubtitleInfo(
        language = language,
        ext = ext,
        source = source,
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

    private fun mergeRequest() = DownloadRequest(
        url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
        title = "测试视频",
        route = DownloadRoute.MergeRequired(videoFormatId = "137", audioFormatId = "140"),
    )

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("source file not found: ${candidates.joinToString()}")
    }

    private fun setSubtitleOutputUris(row: HistoryItemEntity, value: String) {
        val field = HistoryItemEntity::class.java.getDeclaredField("subtitleOutputUris")
        field.isAccessible = true
        field.set(row, value)
    }
}
