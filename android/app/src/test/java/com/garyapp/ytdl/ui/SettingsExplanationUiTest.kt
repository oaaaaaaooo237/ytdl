package com.garyapp.ytdl.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import java.io.File
import org.junit.Assert.assertEquals
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsExplanationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mediaProcessorRowOpensAccurateNativeCapabilityExplanation() {
        setQuietContent()

        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-category-legal"))
        composeRule.onNodeWithTag("ytdl-settings-category-legal").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-media-processor"))
        composeRule.onNodeWithTag("ytdl-settings-media-processor").performClick()

        composeRule.onNodeWithTag("ytdl-settings-explanation-dialog").assertExists()
        composeRule.onNodeWithText(
            "使用 Android 原生组件合并独立的音视频轨道。部分 HLS 下载由 FFmpeg 封装为 MP4，保留原编码。",
        ).assertExists()
    }

    @Test
    fun openSourceEntryShowsLicensesInsteadOfDefensiveStatements() {
        setQuietContent()
        composeRule.onNodeWithText("新建下载").assertExists()
        composeRule.onNodeWithText("v${com.garyapp.ytdl.BuildConfig.VERSION_NAME}").assertExists()
        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("ytdl-settings-category-legal"))
        composeRule.onNodeWithTag("ytdl-settings-category-legal").performClick()
        composeRule.onNodeWithTag("ytdl-screen-settings")
            .performScrollToNode(hasTestTag("captainflash-open-source"))
        composeRule.onNodeWithTag("captainflash-open-source").performClick()
        composeRule.onNodeWithTag("captainflash-open-source-dialog").assertExists()
        composeRule.onNodeWithText(CaptainFlashOpenSourceNotices).assertExists()
    }

    @Test
    fun downloadBehaviorControlsPersistTheirActualSettings() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = com.garyapp.ytdl.core.settings.SettingsRepository.fromContext(context)
        val before = repository.getSettings()
        try {
            setQuietContent()
            composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
            composeRule.onNodeWithTag("ytdl-settings-category-behavior").performClick()
            composeRule.onNodeWithTag("ytdl-screen-settings").performScrollToNode(hasTestTag("ytdl-settings-concurrency"))
            composeRule.onNodeWithTag("ytdl-settings-concurrency").performClick()
            composeRule.onNodeWithTag("ytdl-settings-concurrency-0").performClick()
            composeRule.onNodeWithTag("ytdl-screen-settings")
                .performScrollToNode(hasTestTag("ytdl-settings-file-conflict-1"))
            composeRule.onNodeWithTag("ytdl-settings-file-conflict-1").performClick()
            composeRule.runOnIdle {
                val saved = com.garyapp.ytdl.core.settings.SettingsRepository.fromContext(context).getSettings()
                org.junit.Assert.assertEquals(1, saved.maxConcurrentDownloads)
                org.junit.Assert.assertEquals(com.garyapp.ytdl.download.HttpFileConflictPolicy.Ask, saved.fileConflictPolicy)
            }
        } finally {
            repository.update { before }
        }
    }

    @Test
    fun changingAppearanceDoesNotShowFeedbackOnDownloadOrTasks() {
        com.garyapp.ytdl.download.DownloadCoordinator.resetForTests()
        setQuietContent()
        composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
        composeRule.onNodeWithTag("ytdl-settings-category-appearance").performClick()
        composeRule.onNodeWithTag("ytdl-settings-theme-mode-1").performClick()
        composeRule.onNodeWithTag("ytdl-tab-tasks").performClick()
        composeRule.onNodeWithTag("ytdl-runtime-message").assertDoesNotExist()
        composeRule.onNodeWithTag("ytdl-tab-download").performClick()
        composeRule.onNodeWithTag("ytdl-runtime-message").assertDoesNotExist()
    }

    @Test
    fun bulkHistoryClearKeepsDownloadedFileByDefault() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val history = YtdlDatabaseProvider.get(context).historyDao()
        val file = File(context.filesDir, "gui-downloads/task-bulk-qa/fixture.mp4")
        file.parentFile!!.mkdirs()
        file.writeText("completed download")
        val now = System.currentTimeMillis()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val ids = executor.submit<List<Long>> { (1..2).map { number ->
            history.insert(
                HistoryItemEntity.createSafe(
                    "Bulk test $number", 8, "https", "test-host", "video",
                    "app-private://outputs/task-bulk-qa/fixture.mp4",
                    "视频", HistoryItemEntity.STATUS_COMPLETED, 100, "", "", "", now, now, now,
                ),
            )
        }
        }.get()
        try {
            setQuietContent()
            composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
            composeRule.onNodeWithTag("ytdl-screen-settings").performScrollToNode(hasTestTag("ytdl-settings-category-storage"))
            composeRule.onNodeWithTag("ytdl-settings-category-storage").performClick()
            composeRule.onNodeWithTag("ytdl-screen-settings").performScrollToNode(hasTestTag("ytdl-settings-clear-history"))
            composeRule.onNodeWithTag("ytdl-settings-clear-history").performClick()
            composeRule.waitUntil(5_000) {
                runCatching { composeRule.onNodeWithText("清除记录").fetchSemanticsNode() }.isSuccess
            }
            composeRule.onNodeWithText("清除记录").performClick()
            composeRule.waitUntil(5_000) { executor.submit<Boolean> { ids.all { history.getById(it) == null } }.get() }
            assertEquals("completed download", file.readText())
        } finally {
            executor.submit { ids.forEach { history.deleteById(it) } }.get()
            executor.shutdownNow()
            file.delete()
        }
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
