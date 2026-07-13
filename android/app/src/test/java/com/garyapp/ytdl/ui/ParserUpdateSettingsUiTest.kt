package com.garyapp.ytdl.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParserUpdateSettingsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startupUpdateShowsChinesePromptWithReleasePageAction() {
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.3.17") {
                        """{"info":{"version":"2026.4.1"}}"""
                    },
                    executor = Executor(Runnable::run),
                ),
            )
        }

        composeRule.onNodeWithText("发现新版解析器").assertExists()
        composeRule.onNodeWithText("发现 yt-dlp 2026.4.1。请更新应用以使用新版解析器，应用不会在内部下载或热更新解析器代码。").assertExists()
        composeRule.onNodeWithText("打开发布页").assertExists()
    }

    @Test
    fun manualRecheckShowsProgressThenChineseFailure() {
        val executor = QueuedExecutor()
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.3.17") {
                        throw IllegalStateException("offline")
                    },
                    executor = executor,
                ),
            )
        }

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithText("正在检查解析器更新...").assertExists()

        executor.runNext()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("检查失败，请稍后重试").assertExists()
    }

    @Test
    fun inFlightUpdateSurvivesCompositionRecreationAndPromptsOnce() {
        val executor = QueuedExecutor()
        val coordinator = ParserUpdateCoordinator(
            checker = ParserUpdateChecker("2026.3.17") {
                """{"info":{"version":"2026.4.1"}}"""
            },
            executor = executor,
        )
        val generation = mutableStateOf(0)
        composeRule.setContent {
            key(generation.value) {
                YtdlApp(parserUpdateCoordinator = coordinator)
            }
        }

        composeRule.runOnIdle { generation.value += 1 }
        executor.runNext()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ytdl-parser-update-dialog").assertExists()
        composeRule.onNodeWithText("稍后").performClick()
        composeRule.runOnIdle { generation.value += 1 }

        composeRule.onNodeWithTag("ytdl-parser-update-dialog").assertDoesNotExist()
    }
}

private class QueuedExecutor : Executor {
    private val commands = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        commands.addLast(command)
    }

    fun runNext() {
        commands.removeFirst().run()
    }
}
