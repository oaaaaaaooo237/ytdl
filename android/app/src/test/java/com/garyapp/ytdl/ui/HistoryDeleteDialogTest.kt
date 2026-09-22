package com.garyapp.ytdl.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryDeleteDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun checkboxDefaultsOffAndResetsAfterCancelAndReopen() {
        val showing = mutableStateOf(true)
        val decisions = mutableListOf<Boolean>()
        val row = HistoryUiItem(1, "测试记录", "", "", "", "completed", 0)
        composeRule.setContent {
            if (showing.value) HistoryDeleteDialog(row,
                onDismiss = { showing.value = false },
                onConfirm = { decisions += it; showing.value = false })
        }
        composeRule.onNodeWithTag("ytdl-history-delete-files").assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithTag("ytdl-history-delete-cancel").performClick()
        composeRule.runOnIdle { showing.value = true }
        composeRule.onNodeWithTag("ytdl-history-delete-files").assertIsOff()
        composeRule.onNodeWithTag("ytdl-history-delete-confirm").performClick()
        assertEquals(listOf(false), decisions)
        composeRule.runOnIdle { showing.value = true }
        composeRule.onNodeWithTag("ytdl-history-delete-files").performClick().assertIsOn()
        composeRule.onNodeWithTag("ytdl-history-delete-confirm").performClick()
        assertEquals(listOf(false, true), decisions)
    }
}
