package com.garyapp.ytdl.ui

import android.app.Activity
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
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.R
import com.garyapp.ytdl.core.settings.SettingsRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executor
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
            .performScrollToNode(hasTestTag("ytdl-format-summary"))

        assertEquals(originalRequest.url, analyzedUrl.get())
        assertEquals(null, unexpectedDownloadRequest.get())
        composeRule.onNodeWithText("实际下载：1080p MP4 AV1 单文件").assertExists()
        composeRule.onAllNodesWithTag("ytdl-format-subtitle-toggle").assertCountEquals(0)
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
            .performScrollToNode(hasTestTag(formatRowTag))
        composeRule.onNodeWithTag(formatRowTag).performClick()

        composeRule.onNodeWithText(expectedSummary).assertExists()
        composeRule.onNodeWithTag("ytdl-screen-download")
            .performScrollToNode(hasTestTag("ytdl-download-authorized-checkbox"))
        composeRule.onNodeWithTag("ytdl-download-authorized-checkbox")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("ytdl-download-start").assertIsEnabled()
        composeRule.onNodeWithTag("ytdl-screen-download")
            .performScrollToNode(hasTestTag("ytdl-download-start"))
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
    }
}
