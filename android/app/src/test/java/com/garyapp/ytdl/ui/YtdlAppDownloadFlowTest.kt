package com.garyapp.ytdl.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.EditText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.R
import com.garyapp.ytdl.core.files.FileKind
import com.garyapp.ytdl.core.files.HttpFileProbe
import com.garyapp.ytdl.core.files.FileMetadata
import com.garyapp.ytdl.core.files.FileProbeResult
import com.garyapp.ytdl.core.files.ResumeSupport
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.download.HttpFileConflictPolicy
import com.garyapp.ytdl.core.ytdlp.AnalysisErrorCategory
import com.garyapp.ytdl.core.ytdlp.YtdlpAnalysisException
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.download.RetryDownloadDraft
import com.garyapp.ytdl.download.RetryDraftStore
import com.garyapp.ytdl.data.HistoryItemEntity
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtdlAppDownloadFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        DownloadCoordinator.resetForTests()
    }

    @After
    fun tearDown() {
        DownloadCoordinator.resetForTests()
    }

    @Test
    fun bottomNavigationCarriesDirect240pIntoFinalDownloadRequest() {
        val request = runDownloadFlow(
            analysis = analysisWith(
                progressiveFormat(id = "720-direct", height = 720),
                progressiveFormat(id = "240-direct", height = 240),
            ),
            formatRowTag = "ytdl-format-row-240",
            expectedSummary = "240p MP4 H.264 单文件",
        )

        assertEquals(DownloadRoute.DirectSingleFile(formatId = "240-direct"), request.route)
    }

    @Test
    fun bottomNavigationKeepsSplitStreamsOnMergeRequiredRoute() {
        val request = runDownloadFlow(
            analysis = analysisWith(
                videoOnlyFormat(id = "1080-video", height = 1080),
                videoOnlyFormat(id = "240-video", height = 240),
                audioOnlyFormat(id = "140-audio"),
            ),
            formatRowTag = "ytdl-format-row-240",
            expectedSummary = "240p MP4 H.264 需原生合并",
        )

        assertEquals(
            DownloadRoute.MergeRequired(videoFormatId = "240-video", audioFormatId = "140-audio"),
            request.route,
        )
    }

    @Test
    fun failedHistoryRetryReanalyzesOriginalUrlAndRestoresPreviousFormatBeforeDownload() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        SettingsRepository.fromContext(appContext)
            .setCookiesReference(null)
        val originalRequest = DownloadRequest(
            url = "https://example.com/video?token=original",
            title = "失败任务原始标题",
            route = DownloadRoute.DirectSingleFile(formatId = "av1-1080"),
            thumbnailUrl = "https://example.com/thumb.jpg",
            cookiesPath = "/data/user/0/com.garyapp.ytdl/cache/temporary-cookies/stale.txt",
            formatSummary = "1080p MP4 AV1 单文件",
        )
        val retryAnalysis = analysisWith(
            progressiveFormat(id = "h264-1080", height = 1080),
            progressiveFormat(id = "av1-1080", height = 1080, videoCodec = "av01"),
        )
        val analyzedUrl = AtomicReference<String>()
        val unexpectedDownloadRequest = AtomicReference<DownloadRequest>()
        val historyId = 98_765L
        val retryStore = InMemoryRetryDraftStore()
        retryStore.save(historyId, RetryDownloadDraft.fromRequest(originalRequest))
            .getOrThrow()
        val historyItem = HistoryUiItem(
            id = historyId,
            title = originalRequest.title,
            meta = "07/15 12:00 · 网络连接中断",
            badge = "失败",
            outputUri = "",
            status = HistoryItemEntity.STATUS_FAILED,
            completedAt = System.currentTimeMillis(),
            formatBadge = "1080p",
            codecBadge = "AV1",
            retryAvailable = true,
        )
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = offlineParserCoordinator(),
                analysisProvider = { url, _ ->
                    analyzedUrl.set(url)
                    Result.success(retryAnalysis)
                },
                downloadStarter = { _, request, _ ->
                    unexpectedDownloadRequest.set(request)
                    Result.success(DownloadTaskState.waiting(request))
                },
                retryDraftStoreOverride = retryStore,
                historyItemsProvider = { listOf(historyItem) },
            )
        }
        composeRule.onNodeWithTag("ytdl-tab-tasks").performClick()
        composeRule.onNodeWithTag("ytdl-screen-tasks")
            .performScrollToNode(hasTestTag("ytdl-history-action-$historyId-再次下载"))
        composeRule.onNodeWithText(originalRequest.title).assertExists()
        composeRule.onNodeWithTag("ytdl-history-action-$historyId-再次下载").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ytdl-screen-download").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ytdl-screen-download")
            .performScrollToNode(hasTestTag("ytdl-download-format-toggle"))

        assertEquals(originalRequest.url, analyzedUrl.get())
        assertEquals(null, unexpectedDownloadRequest.get())
        composeRule.onNodeWithText("1080p MP4 AV1 单文件", substring = true).assertExists()
        composeRule.onNodeWithTag("ytdl-download-filename").assertDoesNotExist()
    }

    @Test
    fun latePreviousAnalysisCannotReplaceCurrentUrlResult() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        SettingsRepository.fromContext(appContext).setCookiesReference(null)
        val firstAnalysisStarted = CountDownLatch(1)
        val releaseFirstAnalysis = CountDownLatch(1)
        val firstAnalysisReturned = CountDownLatch(1)
        lateinit var hostContext: Context
        composeRule.setContent {
            hostContext = LocalContext.current
            YtdlApp(
                parserUpdateCoordinator = offlineParserCoordinator(),
                analysisProvider = { url, _ ->
                    if (url == FirstUrl) {
                        firstAnalysisStarted.countDown()
                        check(releaseFirstAnalysis.await(5, TimeUnit.SECONDS)) {
                            "第一条分析等待超时"
                        }
                        firstAnalysisReturned.countDown()
                        Result.success(analysisNamed("第一条结果"))
                    } else {
                        Result.success(analysisNamed("第二条结果"))
                    }
                },
            )
        }

        composeRule.runOnIdle {
            hostContext.requireActivity()
                .findViewById<EditText>(R.id.ytdl_url_input)
                .setText(FirstUrl)
        }
        composeRule.onNodeWithTag("ytdl-analyze-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { firstAnalysisStarted.count == 0L }

        composeRule.runOnIdle {
            hostContext.requireActivity()
                .findViewById<EditText>(R.id.ytdl_url_input)
                .setText(SecondUrl)
        }
        try {
            composeRule.onNodeWithTag("ytdl-analyze-button")
                .assertIsEnabled()
                .performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("第二条结果", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            releaseFirstAnalysis.countDown()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { firstAnalysisReturned.count == 0L }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("第二条结果", substring = true).assertExists()
        composeRule.onAllNodesWithText("第一条结果", substring = true).assertCountEquals(0)
    }

    @Test
    fun freshDownloadShowsThreeDestinationsAndNoTypeSpecificControls() {
        composeRule.setContent {
            YtdlApp(parserUpdateCoordinator = offlineParserCoordinator())
        }
        listOf("download", "tasks", "settings").forEach { route ->
            composeRule.onNodeWithTag("ytdl-tab-$route").performClick()
            composeRule.onNodeWithTag("ytdl-screen-$route").assertExists()
        }
        composeRule.onNodeWithTag("ytdl-tab-download").performClick()
        composeRule.onNodeWithTag("ytdl-download-paste").assertExists()
        composeRule.onNodeWithTag("ytdl-download-mode-av").assertDoesNotExist()
        composeRule.onNodeWithTag("ytdl-download-filename").assertDoesNotExist()
    }

    @Test
    fun fileIdentificationKeepsMediaOptionsHiddenAndSubmitsEditedNameAfterConfirmation() {
        val capturedRequest = AtomicReference<DownloadRequest>()
        val target = StorageTarget.AppPrivate
        val metadata = FileMetadata(FirstUrl, FirstUrl, "原始.zip", "application/zip", 1_024L,
            null, null, FileKind.Other, ResumeSupport.Unavailable)
        lateinit var hostContext: Context
        composeRule.setContent {
            hostContext = LocalContext.current
            YtdlApp(
                parserUpdateCoordinator = offlineParserCoordinator(),
                analysisProvider = { _, _ -> Result.success(analysisNamed("媒体识别结果")) },
                fileProbeProvider = { url ->
                    if (url == FirstUrl) FileProbeResult.FileFound(metadata) else FileProbeResult.NotFile("网页")
                },
                downloadStarter = { _, request, _ ->
                    capturedRequest.set(request)
                    Result.success(DownloadTaskState.waiting(request))
                },
            )
        }
        fun identify(url: String) {
            composeRule.runOnIdle {
                hostContext.requireActivity().findViewById<EditText>(R.id.ytdl_url_input).setText(url)
            }
            composeRule.onNodeWithTag("ytdl-analyze-button").performClick()
        }

        identify(FirstUrl)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ytdl-download-filename").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ytdl-download-mode").assertDoesNotExist()
        composeRule.onNodeWithTag("ytdl-download-filename").performTextReplacement("备份.bin")
        composeRule.onNodeWithTag("ytdl-screen-download").performScrollToNode(hasTestTag("ytdl-download-extension-warning"))
        composeRule.onNodeWithTag("ytdl-download-extension-warning").assertExists()
        composeRule.onNodeWithTag("ytdl-screen-download").performScrollToNode(hasTestTag("ytdl-download-conflict"))
        composeRule.onNodeWithTag("ytdl-download-conflict").performClick()
        composeRule.onNodeWithTag("ytdl-file-conflict-Ask").performClick()
        composeRule.onNodeWithTag("ytdl-download-start").performClick()
        assertEquals(null, capturedRequest.get())
        composeRule.onNodeWithTag("ytdl-download-extension-confirm").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { capturedRequest.get() != null }
        val request = capturedRequest.get()
        assertEquals(DownloadRoute.HttpFile(metadata, "备份.bin", HttpFileConflictPolicy.Ask), request.route)
        assertEquals(target, request.storageTarget)
    }

    @Test
    fun onlyActiveClipboardPasteRunsRecognitionAndStreamManifestUsesMediaSubtitles() {
        val capturedRequest = AtomicReference<DownloadRequest>()
        val subtitle = SubtitleInfo("zh-Hans", "vtt")
        val manifest = FileMetadata(FirstUrl, FirstUrl, "playlist.m3u8", "application/mpegurl",
            null, null, null, FileKind.Other, ResumeSupport.Unknown)
        lateinit var hostContext: Context
        composeRule.setContent {
            hostContext = LocalContext.current
            YtdlApp(
                parserUpdateCoordinator = offlineParserCoordinator(),
                analysisProvider = { _, _ -> Result.success(analysisNamed("媒体结果").copy(subtitles = listOf(subtitle))) },
                fileProbeProvider = { FileProbeResult.FileFound(manifest) },
                downloadStarter = { _, request, _ ->
                    capturedRequest.set(request)
                    Result.success(DownloadTaskState.waiting(request))
                },
            )
        }
        composeRule.runOnIdle {
            val context = hostContext.requireActivity()
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("地址", FirstUrl))
            assertEquals("", context.findViewById<EditText>(R.id.ytdl_url_input).text.toString())
        }
        composeRule.onNodeWithTag("ytdl-download-paste").performClick()
        composeRule.runOnIdle {
            assertEquals(FirstUrl, hostContext.requireActivity().findViewById<EditText>(R.id.ytdl_url_input).text.toString())
        }
        composeRule.onNodeWithTag("ytdl-analyze-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ytdl-download-mode").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ytdl-download-filename").assertDoesNotExist()
        composeRule.onNodeWithTag("ytdl-screen-download").performScrollToNode(hasTestTag("ytdl-format-subtitle-toggle"))
        composeRule.onNodeWithTag("ytdl-format-subtitle-toggle").performClick()
        composeRule.onNodeWithTag("ytdl-subtitle-zh-Hans-manual").performClick()
        composeRule.onNodeWithTag("ytdl-download-start").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { capturedRequest.get() != null }
        assertEquals(listOf(subtitle), capturedRequest.get().selectedSubtitles)
    }

    @Test
    fun realVideoProbeKeepsMediaModesAndOnlyFallsBackForUnavailableParsing() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/coast.mp4") { exchange ->
            val bytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112)
            exchange.responseHeaders.add("Content-Type", "video/mp4")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/coast.mpd") { exchange ->
            val bytes = "<MPD/>".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/xml")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}/coast.mp4"
            val captured = AtomicReference<DownloadRequest>()
            lateinit var hostContext: Context
            composeRule.setContent {
                hostContext = LocalContext.current
                YtdlApp(
                    parserUpdateCoordinator = offlineParserCoordinator(),
                    fileProbeProvider = HttpFileProbe()::probe,
                    analysisProvider = { url, _ ->
                        when (url.substringAfter('?')) {
                            "unavailable" -> Result.failure(YtdlpAnalysisException(AnalysisErrorCategory.Unsupported, "解析不可用"))
                            "private" -> Result.failure(YtdlpAnalysisException(AnalysisErrorCategory.Permission, "需要授权"))
                            "protected" -> Result.failure(YtdlpAnalysisException(AnalysisErrorCategory.Drm, "受 DRM 保护"))
                            "dash" -> Result.success(analysisNamed("DASH 媒体"))
                            else -> Result.success(analysisNamed("海岸媒体").copy(
                                formats = listOf(progressiveFormat(id = "coast-720", height = 720)),
                            ))
                        }
                    },
                    downloadStarter = { _, request, _ ->
                        captured.set(request)
                        Result.success(DownloadTaskState.waiting(request))
                    },
                )
            }
            fun identify(query: String, url: String = baseUrl) {
                composeRule.onNodeWithTag("ytdl-screen-download").performScrollToNode(hasTestTag("ytdl-url-input"))
                composeRule.runOnIdle {
                    hostContext.requireActivity().findViewById<EditText>(R.id.ytdl_url_input)
                        .setText("$url?$query")
                }
                composeRule.onNodeWithTag("ytdl-analyze-button").performClick()
            }

            identify("parsed")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("海岸媒体").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("ytdl-screen-download").performScrollToNode(hasTestTag("ytdl-download-mode"))
            composeRule.onNodeWithTag("ytdl-download-mode").assertExists()
            composeRule.onNodeWithTag("ytdl-download-start").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { captured.get() != null }
            assertEquals(DownloadRoute.DirectSingleFile("coast-720"), captured.getAndSet(null).route)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("识别后可下载").fetchSemanticsNodes().isNotEmpty()
            }

            identify("unavailable")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("媒体 · 视频直链", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("ytdl-download-mode").assertDoesNotExist()
            composeRule.onNodeWithTag("ytdl-download-filename").assertDoesNotExist()
            composeRule.onNodeWithTag("ytdl-download-start").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { captured.get() != null }
            assertEquals(FileKind.Video, (captured.getAndSet(null).route as DownloadRoute.HttpFile).metadata.kind)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("识别后可下载").fetchSemanticsNodes().isNotEmpty()
            }

            identify("private")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("网站需要登录", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("ytdl-download-identification").assertDoesNotExist()
            identify("protected")
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("该内容受 DRM 保护，无法下载。").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("ytdl-download-identification").assertDoesNotExist()
            identify("dash", baseUrl.replace(".mp4", ".mpd"))
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("DASH 媒体").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("ytdl-download-filename").assertDoesNotExist()
        } finally {
            server.stop(0)
        }
    }

    private fun runDownloadFlow(
        analysis: VideoAnalysis,
        formatRowTag: String,
        expectedSummary: String,
    ): DownloadRequest {
        val capturedRequest = AtomicReference<DownloadRequest>()
        lateinit var hostContext: Context
        composeRule.setContent {
            hostContext = LocalContext.current
            YtdlApp(
                parserUpdateCoordinator = offlineParserCoordinator(),
                analysisProvider = { _, _ -> Result.success(analysis) },
                downloadStarter = { _, request, _ ->
                    capturedRequest.set(request)
                    Result.success(DownloadTaskState.waiting(request))
                },
            )
        }

        composeRule.runOnIdle {
            hostContext.requireActivity()
                .findViewById<EditText>(R.id.ytdl_url_input)
                .setText(TestUrl)
        }
        composeRule.onNodeWithTag("ytdl-analyze-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(analysis.title, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("ytdl-screen-download")
            .performScrollToNode(hasTestTag("ytdl-download-format-toggle"))
        composeRule.onNodeWithTag("ytdl-download-format-toggle").performClick()
        composeRule.onNodeWithTag("ytdl-screen-download")
            .performScrollToNode(hasTestTag(formatRowTag))
        composeRule.onNodeWithTag(formatRowTag).performClick()

        composeRule.onNodeWithTag("ytdl-screen-download").performScrollToNode(hasTestTag("ytdl-download-format-toggle"))
        composeRule.onNodeWithText(expectedSummary, substring = true).assertExists()
        composeRule.onNodeWithTag("ytdl-download-start")
            .assertIsEnabled()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { capturedRequest.get() != null }
        return capturedRequest.get()
    }

    private class InMemoryRetryDraftStore : RetryDraftStore {
        private val drafts = mutableMapOf<Long, RetryDownloadDraft>()

        override fun save(historyId: Long, draft: RetryDownloadDraft): Result<Unit> = runCatching {
            drafts[historyId] = draft
        }

        override fun load(historyId: Long): Result<RetryDownloadDraft> = runCatching {
            drafts[historyId] ?: error("重试信息不可用。")
        }

        override fun isAvailable(historyId: Long): Boolean = historyId in drafts

        override fun delete(historyId: Long): Result<Boolean> = Result.success(drafts.remove(historyId) != null)
    }

    private fun offlineParserCoordinator(): ParserUpdateCoordinator {
        return ParserUpdateCoordinator(
            checker = ParserUpdateChecker("2026.3.17") { error("offline") },
            executor = Executor(Runnable::run),
        )
    }

    private fun analysisWith(vararg formats: VideoFormat) = VideoAnalysis(
        title = "YtdlApp 闭环测试视频",
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = formats.toList(),
        subtitles = emptyList<SubtitleInfo>(),
    )

    private fun analysisNamed(title: String) = VideoAnalysis(
        title = title,
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = listOf(progressiveFormat(id = title, height = 360)),
        subtitles = emptyList<SubtitleInfo>(),
    )

    private fun progressiveFormat(
        id: String,
        height: Int,
        videoCodec: String = "avc1",
    ) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = videoCodec,
        audioCodec = "mp4a",
    )

    private fun videoOnlyFormat(id: String, height: Int) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p 需合并音频",
        hasVideo = true,
        hasAudio = false,
        mergeRequired = true,
        isSupported = true,
        videoCodec = "avc1",
        audioCodec = "none",
    )

    private fun audioOnlyFormat(id: String) = VideoFormat(
        id = id,
        ext = "m4a",
        height = null,
        label = "音频",
        hasVideo = false,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = "none",
        audioCodec = "mp4a",
    )

    private fun Context.requireActivity(): Activity {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity ?: error("Compose host activity not found")
    }

    private companion object {
        const val TestUrl = "https://example.com/video"
        const val FirstUrl = "https://example.com/first"
        const val SecondUrl = "https://example.com/second"
    }
}
