package com.garyapp.ytdl.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.garyapp.ytdl.core.ytdlp.ParserVersionControl
import com.garyapp.ytdl.core.ytdlp.ParserVersionInfo
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParserUpdateSettingsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startupUpdateDownloadsSelectsAndPromptsForRestartWithoutReleasePageAction() {
        val versions = FakeParserVersionControl()
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.3.17") {
                        """{"info":{"version":"2026.4.1"}}"""
                    },
                    executor = Executor(Runnable::run),
                ),
                parserVersionControl = versions,
                parserVersionExecutor = Executor(Runnable::run),
            )
        }

        composeRule.onNodeWithText("发现新版解析器").assertExists()
        composeRule.onNodeWithText("发现 yt-dlp 2026.4.1，可下载并在重启应用后使用。").assertExists()
        composeRule.onNodeWithText("稍后").assertExists()
        composeRule.onNodeWithText("更新").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("打开发布页").assertDoesNotExist()
        composeRule.onNodeWithText("解析器 2026.4.1 已下载并选择，重启应用后生效。").assertExists()
        assertEquals("2026.4.1", versions.selectedVersion)
    }

    @Test
    fun latestBuiltInDoesNotClaimDownloadOrRestart() {
        val versions = FakeParserVersionControl(latestIsBuiltIn = true)
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker(YtdlpBridge.PINNED_YTDLP_VERSION) {
                        """{"info":{"version":"${YtdlpBridge.PINNED_YTDLP_VERSION}"}}"""
                    },
                    executor = QueuedExecutor(),
                ),
                parserVersionControl = versions,
                parserVersionExecutor = Executor(Runnable::run),
            )
        }

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithTag("ytdl-parser-download-latest").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("最新版已内置，当前无需下载。").assertCountEquals(2)
        composeRule.onNodeWithText("已下载并选择", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("重启应用后生效", substring = true).assertDoesNotExist()
        assertEquals(emptyList<String>(), versions.selectCalls)
    }

    @Test
    fun latestBuiltInReplacesOldDownloadedSelectionAndPromptsForRestart() {
        val versions = FakeParserVersionControl(
            downloadedVersions = mutableListOf("2026.2.1"),
            initialSelectedVersion = "2026.2.1",
            latestIsBuiltIn = true,
        )
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker(YtdlpBridge.PINNED_YTDLP_VERSION) {
                        """{"info":{"version":"${YtdlpBridge.PINNED_YTDLP_VERSION}"}}"""
                    },
                    executor = QueuedExecutor(),
                ),
                parserVersionControl = versions,
                parserVersionExecutor = Executor(Runnable::run),
            )
        }

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithTag("ytdl-parser-download-latest").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("已选择内置最新版，重启后生效。").assertCountEquals(2)
        assertEquals(YtdlpBridge.PINNED_YTDLP_VERSION, versions.selectedVersion)
        assertEquals(listOf(YtdlpBridge.PINNED_YTDLP_VERSION), versions.selectCalls)
    }

    @Test
    fun failedStartupDownloadShowsOnlySafeChineseMessage() {
        val versions = FakeParserVersionControl(
            downloadFailure = IllegalStateException(
                "https://files.pythonhosted.org/secret.whl D:/private hash=abc ip=127.0.0.1",
            ),
        )
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.3.17") {
                        """{"info":{"version":"2026.4.1"}}"""
                    },
                    executor = Executor(Runnable::run),
                ),
                parserVersionControl = versions,
                parserVersionExecutor = Executor(Runnable::run),
            )
        }

        composeRule.onNodeWithText("更新").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("解析器更新失败，请稍后重试。").assertExists()
        composeRule.onNodeWithText("secret.whl", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("127.0.0.1", substring = true).assertDoesNotExist()
    }

    @Test
    fun versionDialogShowsRuntimeBoundaryAndSupportsSelectAndConfirmedDelete() {
        val versions = FakeParserVersionControl(downloadedVersions = mutableListOf("2026.4.1"))
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.3.17") {
                        """{"info":{"version":"2026.4.1"}}"""
                    },
                    executor = Executor(Runnable::run),
                ),
                parserVersionControl = versions,
                parserVersionExecutor = Executor(Runnable::run),
                currentProcessParserVersion = "2026.3.17",
            )
        }

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()

        composeRule.onNodeWithText("内置版本：yt-dlp 2026.3.17").assertExists()
        composeRule.onNodeWithText("本进程实际版本：yt-dlp 2026.3.17").assertExists()
        composeRule.onNodeWithText("已下载：yt-dlp 2026.4.1").assertExists()

        composeRule.onNodeWithTag("ytdl-parser-select-2026.4.1").performClick()
        composeRule.onNodeWithText("已选择 2026.4.1，重启应用后生效。").assertExists()

        composeRule.onNodeWithTag("ytdl-parser-delete-2026.4.1").performClick()
        composeRule.onNodeWithText("确认删除解析器 2026.4.1？").assertExists()
        composeRule.onNodeWithTag("ytdl-parser-delete-confirm").performClick()
        composeRule.onNodeWithText("已删除解析器 2026.4.1。下次启动将使用内置版本。").assertExists()
        composeRule.onNodeWithText("已下载：yt-dlp 2026.4.1").assertDoesNotExist()
    }

    @Test
    fun processParserVersionExecutorRunsOperationsOneAtATimeInFifoOrder() {
        val field = Class.forName("com.garyapp.ytdl.ui.YtdlAppKt")
            .declaredFields
            .single { it.name.contains("ProcessParserVersionExecutor") }
            .apply { isAccessible = true }
        val executor = field.get(null) as Executor
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        try {
            executor.execute {
                firstStarted.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            executor.execute { secondStarted.countDown() }

            assertFalse(secondStarted.await(150, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
        }
    }

    @Test
    fun parserVersionOperationDisablesConflictingActionsUntilFinalStateRefresh() {
        val operationExecutor = QueuedExecutor()
        val versions = FakeParserVersionControl(downloadedVersions = mutableListOf("2026.4.1"))
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker(YtdlpBridge.PINNED_YTDLP_VERSION) {
                        """{"info":{"version":"${YtdlpBridge.PINNED_YTDLP_VERSION}"}}"""
                    },
                    executor = QueuedExecutor(),
                ),
                parserVersionControl = versions,
                parserVersionExecutor = operationExecutor,
            )
        }

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithTag("ytdl-parser-select-2026.4.1").performClick()

        composeRule.onNodeWithTag("ytdl-parser-select-2026.4.1").assertIsNotEnabled()
        composeRule.onNodeWithTag("ytdl-parser-delete-2026.4.1").assertIsNotEnabled()
        composeRule.onNodeWithTag("ytdl-parser-download-latest").assertIsNotEnabled()

        operationExecutor.runNext()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ytdl-parser-delete-2026.4.1").assertIsEnabled()
        composeRule.onNodeWithTag("ytdl-parser-download-latest").assertIsEnabled()
        composeRule.onNodeWithText("已选择 2026.4.1，重启应用后生效。").assertExists()
    }

    @Test
    fun parserVersionOperationSurvivesCompositionRecreationAndDeliversFinalState() {
        val operationExecutor = QueuedExecutor()
        val versions = FakeParserVersionControl()
        val versionOperations = ParserVersionOperationCoordinator(versions, operationExecutor)
        val updateCoordinator = ParserUpdateCoordinator(
            checker = ParserUpdateChecker(YtdlpBridge.PINNED_YTDLP_VERSION) {
                """{"info":{"version":"${YtdlpBridge.PINNED_YTDLP_VERSION}"}}"""
            },
            executor = QueuedExecutor(),
        )
        val generation = mutableStateOf(0)
        composeRule.setContent {
            key(generation.value) {
                YtdlApp(
                    parserUpdateCoordinator = updateCoordinator,
                    parserVersionOperations = versionOperations,
                )
            }
        }

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithTag("ytdl-parser-download-latest").performClick()
        composeRule.onNodeWithTag("ytdl-parser-download-latest").assertIsNotEnabled()

        composeRule.runOnIdle { generation.value += 1 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithTag("ytdl-parser-download-latest").assertIsNotEnabled()

        operationExecutor.runNext()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(
            "解析器 2026.4.1 已下载并选择，重启应用后生效。",
        ).assertCountEquals(2)
        assertEquals("2026.4.1", versions.selectedVersion)
    }

    @Test
    fun initialSettingsSummaryUsesProcessVersionWhileDialogKeepsNextLaunchSelection() {
        val checkExecutor = QueuedExecutor()
        val versions = FakeParserVersionControl(
            downloadedVersions = mutableListOf("2026.5.1"),
            initialSelectedVersion = "2026.5.1",
        )
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.4.1") {
                        """{"info":{"version":"2026.5.1"}}"""
                    },
                    executor = checkExecutor,
                ),
                parserVersionControl = versions,
                currentProcessParserVersion = "2026.4.1",
            )
        }

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithText("本进程：2026.4.1；下次启动：2026.5.1").assertExists()

        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithText("当前选择：yt-dlp 2026.5.1").assertExists()
        composeRule.onNodeWithText("本进程实际版本：yt-dlp 2026.4.1").assertExists()
    }

    @Test
    fun manualAlreadyLatestSummaryUsesCurrentProcessVersion() {
        val checkExecutor = QueuedExecutor()
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(
                    checker = ParserUpdateChecker("2026.4.1") {
                        """{"info":{"version":"2026.4.1"}}"""
                    },
                    executor = checkExecutor,
                ),
                parserVersionControl = FakeParserVersionControl(),
                currentProcessParserVersion = "2026.4.1",
            )
        }

        checkExecutor.runNext()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-parser-version"))
        composeRule.onNodeWithTag("ytdl-settings-parser-version").performClick()
        composeRule.onNodeWithText("重新检查").performClick()
        checkExecutor.runNext()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("关闭").performClick()

        composeRule.onNodeWithText("已是最新版本：2026.4.1").assertExists()
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
        composeRule.onNodeWithText("下载最新版").assertExists()
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

private class FakeParserVersionControl(
    private val downloadedVersions: MutableList<String> = mutableListOf(),
    private val downloadFailure: Throwable? = null,
    initialSelectedVersion: String = YtdlpBridge.PINNED_YTDLP_VERSION,
    private val latestIsBuiltIn: Boolean = false,
) : ParserVersionControl {
    var selectedVersion = initialSelectedVersion
        private set
    val selectCalls = mutableListOf<String>()

    override fun listVersions(): List<ParserVersionInfo> {
        return listOf(
            ParserVersionInfo(
                version = YtdlpBridge.PINNED_YTDLP_VERSION,
                isBuiltIn = true,
                isSelected = selectedVersion == YtdlpBridge.PINNED_YTDLP_VERSION,
            ),
        ) + downloadedVersions.map { version ->
            ParserVersionInfo(
                version = version,
                isBuiltIn = false,
                isSelected = selectedVersion == version,
            )
        }
    }

    override fun downloadLatest(expectedVersion: String?): Result<ParserVersionInfo> {
        downloadFailure?.let { return Result.failure(it) }
        if (latestIsBuiltIn) {
            return Result.success(
                ParserVersionInfo(
                    version = YtdlpBridge.PINNED_YTDLP_VERSION,
                    isBuiltIn = true,
                    isSelected = selectedVersion == YtdlpBridge.PINNED_YTDLP_VERSION,
                ),
            )
        }
        val version = expectedVersion ?: "2026.4.1"
        if (version !in downloadedVersions) downloadedVersions += version
        return Result.success(
            ParserVersionInfo(version = version, isBuiltIn = false, isSelected = false),
        )
    }

    override fun select(version: String): Result<Unit> {
        selectCalls += version
        if (version != YtdlpBridge.PINNED_YTDLP_VERSION && version !in downloadedVersions) {
            return Result.failure(IllegalArgumentException("missing"))
        }
        selectedVersion = version
        return Result.success(Unit)
    }

    override fun delete(version: String): Result<Unit> {
        if (version == YtdlpBridge.PINNED_YTDLP_VERSION || !downloadedVersions.remove(version)) {
            return Result.failure(IllegalArgumentException("not deletable"))
        }
        if (selectedVersion == version) selectedVersion = YtdlpBridge.PINNED_YTDLP_VERSION
        return Result.success(Unit)
    }
}
