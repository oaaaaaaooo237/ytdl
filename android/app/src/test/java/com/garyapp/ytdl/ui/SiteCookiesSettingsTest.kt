package com.garyapp.ytdl.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.garyapp.ytdl.core.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SiteCookiesSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun userCanAddSiteChooseLoginAndClearOnlyItsReference() {
        val repo = SettingsRepository()
        val settings = mutableStateOf(repo.getSettings())
        val choices = mutableListOf<String>()
        val logins = mutableListOf<String>()
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SiteCookiesSettings(settings.value,
                onChoose = { _, domain -> choices += domain },
                onLogin = { _, domain -> logins += domain },
                onClear = { domain -> settings.value = repo.setSiteCookies(domain, domain, null) },
                onCheck = {},
                    onAdd = { name, domain -> settings.value = repo.setSiteCookies(name, domain, null) })
            }
        }
        compose.onNodeWithTag("site-cookie-status-youtube.com", useUnmergedTree = true)
            .assertTextEquals("未配置 · 匿名访问")
        compose.onNodeWithTag("site-cookie-name").performScrollTo().performTextInput("测试网站")
        compose.onNodeWithTag("site-cookie-domain").performScrollTo().performTextInput("video.example.com")
        compose.onNodeWithTag("site-cookie-add").performScrollTo().performClick()
        compose.onNodeWithTag("site-cookie-file-video.example.com").performScrollTo().performClick()
        assertTrue(choices.isEmpty())
        compose.onNodeWithTag("site-cookie-import-confirm").performClick()
        compose.onNodeWithTag("site-cookie-login-video.example.com").performScrollTo().performClick()
        assertEquals(listOf("video.example.com"), choices)
        assertEquals(choices, logins)
        compose.runOnIdle {
            settings.value = repo.setSiteCookies("测试网站", "video.example.com",
                CookiesReference.fromUserReference("content://files/custom", "custom.txt"))
        }
        compose.onNodeWithTag("site-cookie-clear-video.example.com").performScrollTo().performClick()
        compose.onNodeWithTag("site-cookie-status-video.example.com", useUnmergedTree = true)
            .assertTextEquals("未配置 · 匿名访问")
    }
}
