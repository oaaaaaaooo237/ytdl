package com.garyapp.ytdl

import com.garyapp.ytdl.ui.ytdlNavigationDestinations
import com.garyapp.ytdl.ui.theme.YtdlThemeMode
import com.garyapp.ytdl.ui.theme.defaultYtdlThemeConfig
import com.garyapp.ytdl.ui.theme.ytdlColorPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeUnitTest {
    @Test
    fun navigationLabelsExposeFiveAndroidTabs() {
        assertEquals(
            listOf("下载", "格式", "队列", "历史", "设置"),
            ytdlNavigationDestinations().map { it.label },
        )
    }

    @Test
    fun settingsTabReservesAppearanceAndColorEntry() {
        val settings = ytdlNavigationDestinations().single { it.route == "settings" }

        assertTrue(settings.reservedEntries.contains("外观与颜色"))
    }

    @Test
    fun themeModelReservesModeAndColorPresetSettings() {
        val defaultConfig = defaultYtdlThemeConfig()
        val presets = ytdlColorPresets()

        assertEquals(YtdlThemeMode.System, defaultConfig.mode)
        assertEquals("reference_v3", defaultConfig.colorPreset.id)
        assertTrue(presets.any { it.id == "reference_v3" && it.label == "基准图配色" })
        assertTrue(presets.any { it.id == "codex" && it.label == "Codex 风格" })
    }
}
