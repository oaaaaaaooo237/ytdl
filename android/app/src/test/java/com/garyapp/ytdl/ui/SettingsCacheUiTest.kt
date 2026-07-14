package com.garyapp.ytdl.ui

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import com.garyapp.ytdl.storage.CacheClearResult
import com.garyapp.ytdl.storage.CacheStats
import com.garyapp.ytdl.storage.DownloadCacheControl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsCacheUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun resetDownloadCoordinator() {
        DownloadCoordinator.resetForTests()
    }

    @Test
    fun enteringSettingsRefreshesCacheStatsAfterInitialComposition() {
        val cache = FakeDownloadCache(
            initialStats = CacheStats(1, 1),
            clearResult = CacheClearResult.Success(0, 0),
        )
        setContent(cache)
        composeRule.waitForIdle()

        cache.stats = CacheStats(9, 3)
        openCacheRow()

        composeRule.onNodeWithText("9 B · 3 个文件").assertExists()
    }

    @Test
    fun olderCacheInspectionCannotOverwriteNewerVisibleStats() {
        val executor = ControllableExecutor()
        val cache = FakeDownloadCache(
            initialStats = CacheStats(1, 1),
            clearResult = CacheClearResult.Success(0, 0),
        )
        setContent(cache, executor)
        openCacheRow()
        assertEquals(2, executor.pendingCount)

        cache.stats = CacheStats(9, 3)
        executor.runLast()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("9 B · 3 个文件").assertExists()

        cache.stats = CacheStats(1, 1)
        executor.runNext()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9 B · 3 个文件").assertExists()
        composeRule.onNodeWithText("1 B · 1 个文件").assertDoesNotExist()
    }

    @Test
    fun terminalDownloadRefreshesCacheStatsWhileSettingsIsVisible() {
        val cache = FakeDownloadCache(
            initialStats = CacheStats(1, 1),
            clearResult = CacheClearResult.Success(0, 0),
        )
        setContent(cache)
        openCacheRow()

        cache.stats = CacheStats(12, 4)
        DownloadCoordinator.publish(DownloadTaskState(stage = DownloadStage.Completed))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("12 B · 4 个文件").assertExists()
    }

    @Test
    fun cacheRowConfirmsThenReportsFreedBytesAndFiles() {
        val cache = FakeDownloadCache(
            initialStats = CacheStats(7, 2),
            clearResult = CacheClearResult.Success(7, 2),
        )
        setContent(cache)

        openCacheRow()
        composeRule.onNodeWithText("7 B · 2 个文件").assertExists()
        composeRule.onNodeWithTag("ytdl-settings-cache-clear").performClick()
        composeRule.onNodeWithText("确认清理临时缓存").assertExists()
        composeRule.onNodeWithText("只删除未被下载记录引用的 App 私有临时文件；不会删除完成文件、合并文件或所选文件夹中的文件。").assertExists()

        composeRule.onNodeWithTag("ytdl-cache-clear-confirm").performClick()
        composeRule.onNodeWithText("清理完成").assertExists()
        composeRule.onNodeWithText("已释放 7 B，删除 2 个文件。").assertExists()
        assertEquals(1, cache.clearCallCount)
    }

    @Test
    fun blockedCleanupExplainsActiveDownloadInChinese() {
        val cache = FakeDownloadCache(
            initialStats = CacheStats(3, 1),
            clearResult = CacheClearResult.Success(3, 1),
        )
        setContent(cache)
        DownloadCoordinator.publish(DownloadTaskState(stage = DownloadStage.Waiting))

        openCacheRow()
        composeRule.onNodeWithTag("ytdl-settings-cache-clear").performClick()
        composeRule.onNodeWithTag("ytdl-cache-clear-confirm").performClick()

        composeRule.onNodeWithText("无法清理缓存").assertExists()
        composeRule.onNodeWithText("当前有下载任务正在运行。为避免删除任务仍在使用的文件，请等待下载结束后再清理。").assertExists()
        assertEquals(0, cache.clearCallCount)
    }

    @Test
    fun incompleteCleanupReportsRemainingTemporaryFiles() {
        val cache = FakeDownloadCache(
            initialStats = CacheStats(7, 2),
            clearResult = CacheClearResult.Incomplete(
                freedBytes = 3,
                deletedFileCount = 1,
                remainingBytes = 4,
                remainingFileCount = 1,
            ),
        )
        setContent(cache)

        openCacheRow()
        composeRule.onNodeWithTag("ytdl-settings-cache-clear").performClick()
        composeRule.onNodeWithTag("ytdl-cache-clear-confirm").performClick()

        composeRule.onNodeWithText("部分临时文件未能清理").assertExists()
        composeRule.onNodeWithText("已释放 3 B，删除 1 个文件；仍有 4 B、1 个临时文件，请稍后重试。").assertExists()
    }

    private fun setContent(
        cache: FakeDownloadCache,
        executor: Executor = Executor(Runnable::run),
    ) {
        composeRule.setContent {
            YtdlApp(
                parserUpdateCoordinator = successfulParserUpdateCoordinator(),
                downloadCacheFactory = { _: Context -> cache },
                downloadCacheExecutor = executor,
            )
        }
    }

    private fun successfulParserUpdateCoordinator(): ParserUpdateCoordinator {
        return ParserUpdateCoordinator(
            checker = ParserUpdateChecker("2026.3.17") {
                """{"info":{"version":"2026.3.17"}}"""
            },
            executor = Executor(Runnable::run),
        )
    }

    private fun openCacheRow() {
        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-cache-clear"))
    }
}

private class ControllableExecutor : Executor {
    private val commands = ArrayDeque<Runnable>()

    val pendingCount: Int
        get() = commands.size

    override fun execute(command: Runnable) {
        commands.addLast(command)
    }

    fun runNext() {
        commands.removeFirst().run()
    }

    fun runLast() {
        commands.removeLast().run()
    }
}

private class FakeDownloadCache(
    initialStats: CacheStats,
    private val clearResult: CacheClearResult,
) : DownloadCacheControl {
    var stats = initialStats
    var clearCallCount = 0
        private set

    override fun inspect(): CacheStats = stats

    override fun clear(): CacheClearResult {
        clearCallCount += 1
        if (clearResult is CacheClearResult.Success) {
            stats = CacheStats(0, 0)
        } else if (clearResult is CacheClearResult.Incomplete) {
            stats = CacheStats(clearResult.remainingBytes, clearResult.remainingFileCount)
        }
        return clearResult
    }
}
