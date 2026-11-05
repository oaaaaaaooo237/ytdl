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
            .performScrollToNode(hasTestTag("ytdl-settings-category-components"))
        composeRule.onNodeWithTag("ytdl-settings-category-components").performClick()
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
