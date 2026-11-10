package com.garyapp.ytdl.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.garyapp.ytdl.core.ytdlp.ParserVersionInfo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class ParserVersionDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rowsStageOneVersionAndFourEqualActionsApplyOnlyOnConfirmation() {
        var selected: String? = null
        var deleted: String? = null
        val versions = listOf(ParserVersionInfo("2026.3.17", true, true), ParserVersionInfo("2026.10.26", false, false))
        rule.setContent {
            ParserVersionDialog(versions, "2026.3.17", null, false, false, "已是最新版本", null,
                { selected = it }, {}, { deleted = it }, {}, {})
        }
        rule.onNodeWithTag("ytdl-parser-row-2026.3.17").assertIsSelected()
        rule.onNodeWithTag("ytdl-parser-delete").assertIsNotEnabled()
        rule.onNodeWithTag("ytdl-parser-row-2026.10.26").performClick()
        assertNull(selected)
        rule.onNodeWithTag("ytdl-parser-row-2026.3.17").assertIsNotSelected()
        rule.onNodeWithTag("ytdl-parser-row-2026.10.26").assertIsSelected()
        rule.onNodeWithContentDescription("选择解析器").performClick()
        assertEquals("2026.10.26", selected)
        rule.onNodeWithContentDescription("删除解析器").performClick()
        assertEquals("2026.10.26", deleted)
        val boxes = listOf("select", "delete", "refresh", "close").map {
            rule.onNodeWithTag("ytdl-parser-$it").fetchSemanticsNode().boundsInRoot
        }
        assertTrue(boxes.all { it.width == boxes[0].width && it.height == boxes[0].height })
        assertEquals(boxes[0].top, boxes[1].top)
        assertEquals(boxes[2].top, boxes[3].top)
        assertTrue(boxes[2].top > boxes[0].bottom)
    }

    @Test fun availableVersionDownloadsFromSameSelectionAndBusyStateDisablesMutations() {
        var downloaded: String? = null
        val busy = androidx.compose.runtime.mutableStateOf(false)
        val versions = androidx.compose.runtime.mutableStateOf(listOf(ParserVersionInfo("2026.3.17", true, true)))
        rule.setContent {
            ParserVersionDialog(versions.value, "2026.3.17", "2026.10.26",
                busy.value, false, "发现新版本 2026.10.26", null, {}, {
                    downloaded = it
                    versions.value += ParserVersionInfo(it, false, false)
                }, {}, {}, {})
        }
        rule.onNodeWithText("新").assertExists()
        rule.onNodeWithTag("ytdl-parser-select").assertIsNotEnabled()
        rule.onNodeWithTag("ytdl-parser-delete").assertIsNotEnabled()
        rule.onNodeWithContentDescription("下载解析器 2026.10.26").performClick()
        assertEquals("2026.10.26", downloaded)
        rule.onNodeWithTag("ytdl-parser-row-2026.3.17").assertIsSelected()
        rule.onNodeWithTag("ytdl-parser-row-2026.10.26").performClick()
        rule.onNodeWithTag("ytdl-parser-select").assertIsEnabled()
        rule.runOnIdle { busy.value = true }
        rule.onNodeWithTag("ytdl-parser-select").assertIsNotEnabled()
        rule.onNodeWithTag("ytdl-parser-delete").assertIsNotEnabled()
        rule.onNodeWithTag("ytdl-parser-close").assertIsEnabled()
    }
}
