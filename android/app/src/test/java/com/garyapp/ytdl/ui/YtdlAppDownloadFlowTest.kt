package com.garyapp.ytdl.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.EditText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.garyapp.ytdl.R
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadTaskState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
            expectedSummary = "240p MP4 单文件",
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
            expectedSummary = "240p MP4 需原生合并",
        )

        assertEquals(
            DownloadRoute.MergeRequired(videoFormatId = "240-video", audioFormatId = "140-audio"),
            request.route,
        )
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

        composeRule.onNodeWithTag("ytdl-tab-formats").performClick()
        composeRule.onNodeWithTag("ytdl-screen-formats")
            .performScrollToNode(hasTestTag(formatRowTag))
        composeRule.onNodeWithTag(formatRowTag).performClick()
        composeRule.onNodeWithTag("ytdl-tab-download").performClick()

        composeRule.onNodeWithText(expectedSummary).assertExists()
        composeRule.onNodeWithTag("ytdl-screen-download")
            .performScrollToNode(hasTestTag("ytdl-download-authorized-checkbox"))
        composeRule.onNodeWithTag("ytdl-download-authorized-checkbox").performClick()
        composeRule.onNodeWithTag("ytdl-screen-download")
            .performScrollToNode(hasTestTag("ytdl-download-start"))
        composeRule.onNodeWithTag("ytdl-download-start").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { capturedRequest.get() != null }
        return capturedRequest.get()
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
