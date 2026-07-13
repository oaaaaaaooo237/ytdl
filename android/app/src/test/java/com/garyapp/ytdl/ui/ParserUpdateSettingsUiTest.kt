package com.garyapp.ytdl.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
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
    fun versionRowOpensStatusDialogAndManualRecheckUpdatesFailure() {
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

        executor.runNext()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()

        composeRule.onNodeWithTag("ytdl-parser-status-dialog").assertExists()
        composeRule.onNodeWithText("内置版本：yt-dlp ${YtdlpBridge.PINNED_YTDLP_VERSION}").assertExists()
        composeRule.onNodeWithText("检查失败，请稍后重试").assertExists()

        composeRule.onNodeWithText("重新检查").performClick()
        composeRule.onAllNodesWithText("正在检查解析器更新...").assertCountEquals(2)

        executor.runNext()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("检查失败，请稍后重试").assertCountEquals(2)
    }

    @Test
    fun startupCheckDoesNotBlockNavigationOrStatusDialog() {
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

        composeRule.onNodeWithTag("ytdl-screen-download").assertExists()
        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()

        composeRule.onNodeWithTag("ytdl-parser-status-dialog").assertExists()
        composeRule.onNodeWithText("正在检查解析器更新...").assertExists()
    }

    @Test
    fun uiMirrorRejectsOutOfOrderSnapshotsAcrossRecreationAndKeepsRecheckAvailable() {
        val checkExecutor = QueuedExecutor()
        val uiStateExecutor = ReorderableExecutor()
        val responses = ArrayDeque(
            listOf(
                """{"info":{"version":"2026.4.1"}}""",
                """{"info":{"version":"2026.3.17"}}""",
            ),
        )
        val coordinator = ParserUpdateCoordinator(
            checker = ParserUpdateChecker("2026.3.17") { responses.removeFirst() },
            executor = checkExecutor,
        )
        val generation = mutableStateOf(0)
        composeRule.setContent {
            key(generation.value) {
                YtdlApp(
                    parserUpdateCoordinator = coordinator,
                    parserUiStateDeliveryExecutor = uiStateExecutor,
                )
            }
        }

        uiStateExecutor.runLast()
        composeRule.waitForIdle()
        uiStateExecutor.runFirst()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithText("正在检查解析器更新...").assertExists()

        checkExecutor.runNext()
        uiStateExecutor.runFirst()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("发现新版本 2026.4.1").assertExists()

        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onAllNodesWithTag("ytdl-parser-update-dialog").assertCountEquals(1)
        composeRule.onNodeWithText("稍后").performClick()

        composeRule.runOnIdle { generation.value += 1 }
        composeRule.waitForIdle()
        uiStateExecutor.runAllLastFirst()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("ytdl-parser-update-dialog").assertCountEquals(0)
        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithText("发现新版本 2026.4.1").assertExists()

        composeRule.onNodeWithText("重新检查").performClick()
        checkExecutor.runNext()
        uiStateExecutor.runLast()
        composeRule.waitForIdle()
        uiStateExecutor.runFirst()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已是最新版本").assertExists()
        composeRule.onAllNodesWithText("正在检查解析器更新...").assertCountEquals(0)
        composeRule.onNodeWithText("重新检查").assertIsEnabled()

        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onAllNodesWithTag("ytdl-parser-update-dialog").assertCountEquals(0)
        composeRule.runOnIdle { generation.value += 1 }
        composeRule.waitForIdle()
        uiStateExecutor.runAllLastFirst()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("ytdl-parser-update-dialog").assertCountEquals(0)
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

private class ReorderableExecutor : Executor {
    private val commands = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        commands.addLast(command)
    }

    fun runFirst() {
        commands.removeFirst().run()
    }

    fun runLast() {
        commands.removeLast().run()
    }

    fun runAllLastFirst() {
        while (commands.isNotEmpty()) {
            commands.removeLast().run()
        }
    }
}
