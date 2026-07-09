package com.garyapp.ytdl.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.ui.theme.YtdlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadGuiBindingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appRootSemanticsExposeReferencePaletteAccent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SettingsRepository.fromContext(context).setColorPreset(AppearanceSettings.ColorPresetReferenceV3)

        composeRule.setContent { YtdlApp() }

        composeRule.onNodeWithTag("ytdl-screen-download")
            .assert(SemanticsMatcher.expectValue(YtdlColorPresetIdKey, AppearanceSettings.ColorPresetReferenceV3))
            .assert(SemanticsMatcher.expectValue(YtdlSettingsAccentArgbKey, "#FF2E86DE"))
    }

    @Test
    fun appRootSemanticsExposeCodexPaletteAccent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository.fromContext(context)
        repository.setColorPreset(AppearanceSettings.ColorPresetCodex)

        try {
            composeRule.setContent { YtdlApp() }

            composeRule.onNodeWithTag("ytdl-screen-download")
                .assert(SemanticsMatcher.expectValue(YtdlColorPresetIdKey, AppearanceSettings.ColorPresetCodex))
                .assert(SemanticsMatcher.expectValue(YtdlSettingsAccentArgbKey, "#FF2F6D80"))
        } finally {
            repository.setColorPreset(AppearanceSettings.ColorPresetReferenceV3)
        }
    }

    @Test
    fun navigationAccentsMatchReferenceAndCodexPalettes() {
        assertEquals(
            mapOf(
                "下载" to "#FFFF5B55",
                "格式" to "#FF138F88",
                "队列" to "#FFFF7A1A",
                "历史" to "#FF7357C8",
                "设置" to "#FF2E86DE",
            ),
            ytdlNavigationAccentHexesForUiTest(AppearanceSettings.ColorPresetReferenceV3),
        )

        assertEquals(
            mapOf(
                "下载" to "#FF315C6B",
                "格式" to "#FF7C705E",
                "队列" to "#FFA26E35",
                "历史" to "#FF5D5D79",
                "设置" to "#FF2F6D80",
            ),
            ytdlNavigationAccentHexesForUiTest(AppearanceSettings.ColorPresetCodex),
        )
    }

    @Test
    fun bottomNavigationKeepsTabTargetsAboveSystemGestureArea() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("WindowInsets.navigationBars"))
        assertTrue(source.contains("calculateBottomPadding()"))
        assertTrue(source.contains("BottomBarGestureBuffer"))
        assertFalse(source.contains("navigationBarsPadding()"))
    }

    @Test
    fun queueProgressUsesPlainBarForFrequentStageUpdates() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("YtdlQueueProgressBar("))
        assertFalse(source.contains("LinearProgressIndicator("))
    }

    @Test
    fun bottomTabRouteChangesRecreateLazyListSlots() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("key(selected.route)"))
    }

    @Test
    fun passiveRuntimeMessagesDoNotOccupyDownloadLayout() {
        assertFalse(shouldShowRuntimeMessageForUiTest("等待输入公开视频页面地址。"))
        assertFalse(shouldShowRuntimeMessageForUiTest("分析完成，可以开始下载。"))
        assertTrue(shouldShowRuntimeMessageForUiTest("请先输入公开视频页面地址。"))
        assertTrue(shouldShowRuntimeMessageForUiTest("真实下载已加入前台队列，当前阶段：等待中。"))
    }

    @Test
    fun urlBoundaryFailureMessagesStayVisibleAndDoNotExposeSensitiveInput() {
        val messages = listOf(
            "请先输入公开视频页面地址。",
            "分析失败：${UrlPolicy.evaluate("https://exa mple.com/watch?token=secret").userMessage.orEmpty()}",
            "分析失败：${UrlPolicy.evaluate("ftp://example.com/watch?token=secret").userMessage.orEmpty()}",
        )
        val joined = messages.joinToString("\n")

        messages.forEach { message ->
            assertTrue("message should be visible: $message", shouldShowRuntimeMessageForUiTest(message))
        }
        listOf("请先输入", "有效的公开视频", "http 或 https").forEach { cue ->
            assertTrue("missing readable cue $cue", joined.contains(cue))
        }
        listOf("token=secret", "secret", "exa mple.com", "example.com").forEach { sensitive ->
            assertFalse("URL boundary message leaked $sensitive", joined.contains(sensitive, ignoreCase = true))
        }
    }

    @Test
    fun urlInputShowsSoftwareKeyboardForRealisticForegroundInput() {
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_QWERTY))
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_12KEY))
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_NOKEYS))
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_UNDEFINED))
    }

    @Test
    fun urlInputActivelyRequestsSoftwareKeyboardOnForegroundTouch() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("InputMethodManager"))
        assertTrue(source.contains("showSoftInput(this, 0)"))
        assertTrue(source.contains("setOnFocusChangeListener"))
        assertTrue(source.contains("setOnClickListener"))
    }

    @Test
    fun urlInputDisablesAutoHandwritingOnAndroid14AndNewer() {
        assertFalse(urlInputDisableAutoHandwritingForUiTest(33))
        assertTrue(urlInputDisableAutoHandwritingForUiTest(34))
        assertTrue(urlInputDisableAutoHandwritingForUiTest(37))
    }

    @Test
    fun urlInputProgrammaticTextSyncDoesNotEmitUserUrlChange() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("UrlInputTextChangeGuard"))
        assertTrue(source.contains("textChangeGuard.runProgrammaticTextUpdate"))
        assertTrue(source.contains("textChangeGuard.dispatchUserTextChange"))

        val guard = Class.forName("com.garyapp.ytdl.ui.UrlInputTextChangeGuard")
            .getDeclaredConstructor()
            .newInstance()
        val runProgrammaticTextUpdate = guard.javaClass.declaredMethods
            .single { it.name.contains("runProgrammaticTextUpdate") }
        val dispatchUserTextChange = guard.javaClass.declaredMethods
            .single { it.name.contains("dispatchUserTextChange") }
        val emitted = mutableListOf<String>()
        val onValueChange: (String) -> Unit = { emitted += it }

        runProgrammaticTextUpdate.invoke(guard, {
            dispatchUserTextChange.invoke(guard, "https://example.com/programmatic", onValueChange)
            Unit
        })
        dispatchUserTextChange.invoke(guard, "https://example.com/user", onValueChange)

        assertEquals(listOf("https://example.com/user"), emitted)
    }

    @Test
    fun queueRuntimeMessagesOnlyShowUserActionFeedback() {
        assertFalse(shouldShowQueueRuntimeMessageForUiTest("等待输入公开视频页面地址。"))
        assertFalse(shouldShowQueueRuntimeMessageForUiTest("正在下载视频..."))
        assertFalse(shouldShowQueueRuntimeMessageForUiTest("下载完成：merged-299-140.mp4"))
        assertTrue(shouldShowQueueRuntimeMessageForUiTest("已请求取消当前下载。"))
        assertTrue(shouldShowQueueRuntimeMessageForUiTest("下载失败：请检查网络或授权状态。"))
    }

    @Test
    fun formatRowsComeFromCurrentAnalysisAndDisabledRowsExplainWhy() {
        val analysis = analysisWith(
            progressiveFormat(id = "18", height = 360),
            videoOnlyFormat(id = "137", height = 1080),
            audioOnlyFormat(id = "140"),
        )
        val rows = buildFormatResolutionRows(
            analysis = analysis,
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 1080),
        )

        val supported = rows.single { it.height == 1080 }
        assertTrue(supported.selectable)
        assertTrue(supported.mergeRequired)
        assertEquals("137", supported.videoFormatId)
        assertEquals("140", supported.audioFormatId)

        val unavailable = rows.single { it.height == 720 }
        assertFalse(unavailable.selectable)
        assertEquals("当前视频未提供", unavailable.reason)
        assertEquals(null, unavailable.videoFormatId)
        assertEquals(null, unavailable.audioFormatId)
    }

    @Test
    fun modeSelectionFallsBackToExecutableChoiceForCurrentAnalysis() {
        val analysis = analysisWith(
            progressiveFormat(id = "18", height = 360),
            audioOnlyFormat(id = "140"),
        )

        val selection = selectBestAvailableFormatSelection(
            analysis = analysis,
            mode = FormatMode.AudioOnly,
            preferredHeight = 1080,
        )
        val request = buildAppliedDownloadRequest(TestUrl, analysis, selection).getOrThrow()

        assertEquals(FormatMode.AudioOnly, selection.mode)
        assertEquals(null, selection.selectedHeight)
        assertEquals("140", selection.selectedAudioFormatId)
        assertEquals(DownloadRoute.AudioOnly(audioFormatId = "140"), request.route)
    }

    @Test
    fun buildAppliedDownloadRequestCarriesManagedTemporaryCookiesPath() {
        val analysis = analysisWith(progressiveFormat(id = "18", height = 360))
        val selection = selectBestAvailableFormatSelection(
            analysis = analysis,
            mode = FormatMode.VideoAndAudio,
            preferredHeight = 360,
        )

        val request = buildAppliedDownloadRequest(
            url = TestUrl,
            analysis = analysis,
            appliedSelection = selection,
            cookiesPath = "/data/user/0/com.garyapp.ytdl/cache/temporary-cookies/ytdl-cookies-task.txt",
        ).getOrThrow()

        assertEquals("/data/user/0/com.garyapp.ytdl/cache/temporary-cookies/ytdl-cookies-task.txt", request.cookiesPath)
    }

    @Test
    fun historyRowsRenderAsRealHistoryCardsWithoutRawSecrets() {
        val rows = listOf(
            HistoryItemEntity.createSafe(
                "完成视频 Cookie: SID=secret",
                60,
                "https",
                "host-hash",
                "youtube",
                "app-private://outputs/%E6%B5%8B%E8%AF%95%20video.mp4",
                "1080p --cookies D:/private/cookies.txt",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                null,
                1_000,
                1_000,
                1_000,
            ),
            HistoryItemEntity.createSafe(
                "失败视频",
                60,
                "https",
                "host-hash",
                "web",
                "",
                "720p",
                HistoryItemEntity.STATUS_FAILED,
                0,
                "",
                "",
                "network failed Authorization: Bearer raw-token",
                2_000,
                2_000,
                2_000,
            ),
        )

        val cards = historyUiItemsFromRows(rows)
        val serialized = cards.joinToString()

        assertEquals(listOf("完成", "失败"), cards.map { it.badge })
        assertTrue(serialized.contains("完成视频"))
        assertTrue(serialized.contains("失败视频"))
        assertTrue(serialized.contains("app-private://outputs/%E6%B5%8B%E8%AF%95%20video.mp4"))
        assertFalse(cards.first().meta.contains("app-private://"))
        assertFalse(cards.first().meta.contains("%20"))
        assertTrue(cards.first().meta.contains("媒体文件"))
        assertFalse(cards.first().meta.contains("测试 video.mp4"))
        assertEquals(listOf("打开", "分享", "导出", "删除"), historyActionLabelsForUiTest(cards.first()))
        assertEquals(listOf("删除"), historyActionLabelsForUiTest(cards.last()))
        listOf("SID=secret", "--cookies", "raw-token", "Authorization").forEach {
            assertFalse("history UI leaked $it", serialized.contains(it))
        }
    }

    @Test
    fun newAnalysisReplacesStaleAppliedFormatIdsAndPreviewSummary() {
        val oldAnalysis = analysisWith(progressiveFormat(id = "18", height = 360))
        val oldState = RuntimeDownloadState(
            url = TestUrl,
            analysis = oldAnalysis,
            appliedFormatSelection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
            ),
        )
        val newAnalysis = analysisWith(progressiveFormat(id = "22", height = 720))

        val newState = oldState.withAnalysisForUiTest(newAnalysis)
        val request = buildAppliedDownloadRequest(
            url = TestUrl,
            analysis = newState.analysis,
            appliedSelection = newState.appliedFormatSelection,
        ).getOrThrow()

        assertEquals("22", newState.appliedFormatSelection.selectedVideoFormatId)
        assertEquals(DownloadRoute.DirectSingleFile(formatId = "22"), request.route)
        assertTrue(downloadPreviewFormatSummaryForUiTest(newState).contains("720p"))
        assertFalse(downloadPreviewFormatSummaryForUiTest(newState).contains("360p"))
    }

    @Test
    fun foregroundStartShowsImmediateQueueAndCurrentStage() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val state = RuntimeDownloadState()
            .withForegroundStartStateForUiTest(DownloadTaskState.waiting(request))

        assertTrue(state.hasRealTask)
        assertTrue(state.isDownloading)
        assertEquals("等待中", state.downloadStatus)
        assertEquals("下载进行中", queueHeaderTitleForUiTest(state))
        assertEquals("当前阶段 · 等待中", queueCardSubtitleForUiTest(state))
        assertTrue(state.userMessage.contains("已加入前台队列"))
        assertTrue(state.userMessage.contains("当前阶段：等待中"))
    }

    @Test
    fun queueActionsOnlyExposeRealCancelForRunningTask() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val running = RuntimeDownloadState()
            .withPipelineStateForUiTest(
                DownloadTaskState.waiting(request)
                    .atStage(DownloadStage.DownloadingVideo)
                    .withProgress(
                        DownloadProgress(
                            status = "downloading",
                            percent = 42.0,
                            downloadedBytes = 42,
                            totalBytes = 100,
                            speedBytesPerSecond = 10.0,
                            etaSeconds = 6,
                            filename = "video.mp4",
                        ),
                    ),
            )
        val completed = RuntimeDownloadState()
            .withPipelineStateForUiTest(DownloadTaskState.waiting(request).atStage(DownloadStage.Completed))

        assertEquals(listOf("取消"), queueCardActionsForUiTest(running))
        assertTrue(queueCardActionsForUiTest(completed).isEmpty())
    }

    @Test
    fun realQueueCardUsesAnalysisThumbnailWhenAvailableAndFallsBackWhenMissing() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val thumbnail = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val runningWithThumbnail = RuntimeDownloadState(thumbnailBitmap = thumbnail)
            .withPipelineStateForUiTest(DownloadTaskState.waiting(request))
        val runningWithoutThumbnail = RuntimeDownloadState()
            .withPipelineStateForUiTest(DownloadTaskState.waiting(request))

        assertEquals("ytdl-queue-thumbnail-image", queueThumbnailTagForUiTest(runningWithThumbnail))
        assertEquals("ytdl-queue-thumbnail-placeholder", queueThumbnailTagForUiTest(runningWithoutThumbnail))
    }

    @Test
    fun realQueuePageRendersAnalysisThumbnailNodeWhenAvailable() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val thumbnail = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val runningWithThumbnail = RuntimeDownloadState(thumbnailBitmap = thumbnail)
            .withPipelineStateForUiTest(DownloadTaskState.waiting(request))

        renderQueuePage(runningWithThumbnail)

        composeRule.onAllNodesWithTag("ytdl-real-queue-card").assertCountEquals(1)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-image").assertCountEquals(1)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-placeholder").assertCountEquals(0)
    }

    @Test
    fun realQueuePageRendersPlaceholderNodeWhenThumbnailIsMissing() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val runningWithoutThumbnail = RuntimeDownloadState()
            .withPipelineStateForUiTest(DownloadTaskState.waiting(request))

        renderQueuePage(runningWithoutThumbnail)

        composeRule.onAllNodesWithTag("ytdl-real-queue-card").assertCountEquals(1)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-image").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-placeholder").assertCountEquals(1)
    }

    @Test
    fun downloadModeCardsReflectAppliedSelection() {
        val audioState = RuntimeDownloadState(
            appliedFormatSelection = FormatSelection(mode = FormatMode.AudioOnly),
        )
        val videoOnlyState = RuntimeDownloadState(
            appliedFormatSelection = FormatSelection(mode = FormatMode.VideoOnly),
        )

        assertTrue(downloadModeSelectionsForUiTest(audioState).getValue(FormatMode.AudioOnly))
        assertFalse(downloadModeSelectionsForUiTest(audioState).getValue(FormatMode.VideoAndAudio))
        assertFalse(downloadModeSelectionsForUiTest(audioState).getValue(FormatMode.VideoOnly))

        assertTrue(downloadModeSelectionsForUiTest(videoOnlyState).getValue(FormatMode.VideoOnly))
        assertFalse(downloadModeSelectionsForUiTest(videoOnlyState).getValue(FormatMode.VideoAndAudio))
    }

    @Test
    fun formatSettingSummariesComeFromSelectedFormats() {
        val analysis = analysisWith(
            videoOnlyFormat(id = "137", height = 1080, fps = 60.0, ext = "mp4", videoCodec = "avc1"),
            audioOnlyFormat(id = "140", ext = "m4a"),
        )
        val selection = selectBestAvailableFormatSelection(
            analysis = analysis,
            mode = FormatMode.VideoAndAudio,
            preferredHeight = 1080,
        )

        val summaries = formatSettingSummariesForUiTest(analysis, selection)

        assertEquals("60fps", summaries.frameRate)
        assertEquals("avc1", summaries.videoCodec)
        assertEquals("MP4（原生合并输出）", summaries.container)
    }

    @Test
    fun settingsLabelsReflectPinnedParserAndMvp1MediaCapabilities() {
        assertEquals("yt-dlp ${YtdlpBridge.PINNED_YTDLP_VERSION}", settingsParserVersionLabelForUiTest())

        val mediaLabel = settingsMediaProcessorLabelForUiTest()
        assertTrue(mediaLabel.contains("原生合并"))
        assertTrue(mediaLabel.contains("字幕独立文件"))
        assertTrue(mediaLabel.contains("MVP2"))
        assertFalse(mediaLabel.contains("字幕嵌入已支持"))
        assertFalse(mediaLabel.contains("字幕烧录已支持"))
    }

    @Test
    fun subtitleLabelDoesNotPretendGuiHasSelectedSubtitles() {
        assertEquals("本阶段默认不下载字幕", subtitleSelectionLabelForUiTest(null, emptyList()))

        val request = requestFor(progressiveFormat(id = "18", height = 360))
        assertTrue(request.selectedSubtitles.isEmpty())
    }

    @Test
    fun subtitleToggleIsAvailableOnlyWhenCurrentAnalysisProvidesSubtitles() {
        val noAnalysis = subtitleSelectionUiStateForUiTest(null, emptyList())
        assertFalse(noAnalysis.canToggle)

        val noSubtitleAnalysis = analysisWith(progressiveFormat(id = "18", height = 360))
        val unavailable = subtitleSelectionUiStateForUiTest(noSubtitleAnalysis, emptyList())
        assertFalse(unavailable.canToggle)
        assertEquals("无可选", unavailable.trailing)
        assertEquals("当前视频未提供字幕", unavailable.label)

        val subtitle = SubtitleInfo(language = "en", ext = "vtt", source = SubtitleSource.Automatic)
        val subtitleAnalysis = noSubtitleAnalysis.copy(subtitles = listOf(subtitle))
        val available = subtitleSelectionUiStateForUiTest(subtitleAnalysis, emptyList())
        assertTrue(available.canToggle)
        assertEquals("选择", available.trailing)

        val selected = subtitleSelectionUiStateForUiTest(subtitleAnalysis, listOf(subtitle))
        assertTrue(selected.canToggle)
        assertEquals("取消", selected.trailing)
    }

    @Test
    fun selectedSubtitleIsCarriedIntoDownloadRequestAsSeparateFile() {
        val subtitle = SubtitleInfo(language = "en", ext = "vtt", source = SubtitleSource.Automatic)
        val analysis = analysisWith(progressiveFormat(id = "18", height = 360)).copy(subtitles = listOf(subtitle))
        val selection = defaultFormatSelection(analysis)

        val request = buildAppliedDownloadRequest(
            url = TestUrl,
            analysis = analysis,
            appliedSelection = selection,
            selectedSubtitles = listOf(subtitle),
        ).getOrThrow()

        assertEquals(listOf(subtitle), request.selectedSubtitles)
        assertEquals("已选择 en vtt 自动字幕 · 独立字幕文件", subtitleSelectionLabelForUiTest(analysis, listOf(subtitle)))
        assertEquals("有 1 个字幕可选 · 当前不下载", subtitleSelectionLabelForUiTest(analysis, emptyList()))
    }

    @Test
    fun downloadPreviewSummaryMentionsIndependentSubtitleWhenSelected() {
        val subtitle = SubtitleInfo(language = "zh-Hans", ext = "vtt", source = SubtitleSource.Automatic)
        val analysis = analysisWith(progressiveFormat(id = "18", height = 360)).copy(subtitles = listOf(subtitle))
        val state = RuntimeDownloadState(
            analysis = analysis,
            appliedFormatSelection = defaultFormatSelection(analysis),
            selectedSubtitles = listOf(subtitle),
        )

        val summary = downloadPreviewFormatSummaryForUiTest(state)

        assertTrue(summary.contains("360p"))
        assertTrue(summary.contains("独立字幕文件"))
    }

    @Test
    fun unsupportedAnalysisInputReturnsChineseErrorBeforePythonStarts() {
        val bridge = YtdlpBridge { error("不应启动 Python") }

        val result = bridge.analyze("ftp://example.com/video")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("http"))
    }

    private fun requestFor(vararg formats: VideoFormat): DownloadRequest {
        val analysis = analysisWith(*formats)
        return DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysis,
            selection = defaultFormatSelection(analysis),
        ).getOrThrow()
    }

    private fun renderQueuePage(state: RuntimeDownloadState) {
        composeRule.setContent {
            YtdlTheme {
                LazyColumn {
                    queuePageItems(
                        state = state,
                        onCancelDownload = {},
                    )
                }
            }
        }
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

    private fun videoOnlyFormat(
        id: String,
        height: Int,
        fps: Double? = null,
        ext: String = "mp4",
        videoCodec: String = "avc1",
    ) = VideoFormat(
        id = id,
        ext = ext,
        height = height,
        label = "${height}p 需合并音频",
        hasVideo = true,
        hasAudio = false,
        mergeRequired = true,
        isSupported = true,
        videoCodec = videoCodec,
        audioCodec = "none",
        fps = fps,
    )

    private fun audioOnlyFormat(
        id: String,
        ext: String = "m4a",
    ) = VideoFormat(
        id = id,
        ext = ext,
        height = null,
        label = "音频",
        hasVideo = false,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = "none",
        audioCodec = "mp4a",
    )

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("source file not found: ${candidates.joinToString()}")
    }

    private companion object {
        const val TestUrl = "https://www.youtube.com/watch?v=tkxzMEfp49Q"
    }
}
