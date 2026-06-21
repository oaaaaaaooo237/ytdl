package com.garyapp.ytdl.ui

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGuiBindingTest {
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
                "app-private://outputs/video.mp4",
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
        assertTrue(serialized.contains("app-private://outputs/video.mp4"))
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
            videoOnlyFormat(id = "137", height = 1080, fps = 60.0, ext = "webm", videoCodec = "vp9"),
            audioOnlyFormat(id = "140", ext = "m4a"),
        )
        val selection = selectBestAvailableFormatSelection(
            analysis = analysis,
            mode = FormatMode.VideoAndAudio,
            preferredHeight = 1080,
        )

        val summaries = formatSettingSummariesForUiTest(analysis, selection)

        assertEquals("60fps", summaries.frameRate)
        assertEquals("vp9", summaries.videoCodec)
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

    private companion object {
        const val TestUrl = "https://www.youtube.com/watch?v=tkxzMEfp49Q"
    }
}
