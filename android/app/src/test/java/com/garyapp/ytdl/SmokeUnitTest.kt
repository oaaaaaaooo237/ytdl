package com.garyapp.ytdl

import com.garyapp.ytdl.ui.ytdlNavigationDestinations
import com.garyapp.ytdl.ui.ytdlVisibleContentLabels
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.ui.theme.YtdlThemeMode
import com.garyapp.ytdl.ui.theme.defaultYtdlThemeConfig
import com.garyapp.ytdl.ui.theme.themeConfigForSettings
import com.garyapp.ytdl.ui.theme.ytdlAppPaletteForPreset
import com.garyapp.ytdl.ui.theme.ytdlColorPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun visibleContentModelCoversFiveReferenceScreens() {
        val labels = ytdlVisibleContentLabels()

        assertEquals(setOf("download", "formats", "queue", "history", "settings"), labels.keys)
        assertTrue(labels.getValue("download").containsAll(listOf("粘贴公开视频页面地址", "分析", "开始下载")))
        assertTrue(labels.getValue("formats").containsAll(listOf("视频+音频", "1080p", "需合并", "字幕")))
        assertTrue(labels.getValue("queue").containsAll(listOf("下载进行中", "当前阶段", "暂无真实下载任务", "最近任务已完成", "原生合并", "已取消")))
        assertTrue(labels.getValue("history").containsAll(listOf("搜索历史", "暂无真实历史记录", "完成下载后会显示")))
        assertTrue(labels.getValue("settings").containsAll(listOf("Cookies 文件", "媒体处理能力", "通知权限", "下载仍在应用内显示进度", "外观与颜色", "Codex 风格")))
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

    @Test
    fun themeConfigUsesPersistedAppearanceSettings() {
        val config = themeConfigForSettings(
            AppSettings(
                themeModeId = AppearanceSettings.ThemeModeDark,
                colorPresetId = AppearanceSettings.ColorPresetCodex,
            ),
        )

        assertEquals(YtdlThemeMode.Dark, config.mode)
        assertEquals("codex", config.colorPreset.id)
    }

    @Test
    fun codexPaletteChangesVisibleAppAccents() {
        val reference = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetReferenceV3)
        val codex = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetCodex)

        assertNotEquals(reference.appBackground, codex.appBackground)
        assertNotEquals(reference.downloadAccent, codex.downloadAccent)
        assertNotEquals(reference.formatAccent, codex.formatAccent)
    }
}
