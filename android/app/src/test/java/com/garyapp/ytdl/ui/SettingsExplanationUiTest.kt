package com.garyapp.ytdl.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsExplanationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mediaProcessorRowOpensAccurateNativeCapabilityExplanation() {
        setQuietContent()

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-media-processor"))
        composeRule.onNodeWithTag("ytdl-settings-media-processor").performClick()

        composeRule.onNodeWithTag("ytdl-settings-explanation-dialog").assertExists()
        composeRule.onNodeWithText(
            "当前使用 Android 原生 MediaExtractor + MediaMuxer，将已下载的分离视频流和音频流封装合并。" +
                "它不进行转码。" +
                "源轨道或容器不兼容时可能无法合并。",
        ).assertExists()
    }

    @Test
    fun urlValidationRowExplainsExactCurrentRules() {
        setQuietContent()

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-url-validation"))
        composeRule.onNodeWithTag("ytdl-settings-url-validation").performClick()

        composeRule.onNodeWithTag("ytdl-settings-explanation-dialog").assertExists()
        composeRule.onNodeWithText(
            "当前只检查三类问题：地址为空、地址格式无效（包括缺少有效主机名），以及协议不是 http 或 https。" +
                "此校验不判断站点是否受支持、内容权限、登录状态或网络是否可用。",
        ).assertExists()
    }

    private fun setQuietContent() {
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.3.17") {
                        """{"info":{"version":"2026.3.17"}}"""
                    },
                    executor = java.util.concurrent.Executor(Runnable::run),
                ),
            )
        }
    }
}
