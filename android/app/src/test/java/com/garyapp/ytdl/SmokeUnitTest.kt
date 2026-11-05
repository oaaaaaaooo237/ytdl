package com.garyapp.ytdl

import androidx.compose.ui.unit.sp
import com.garyapp.ytdl.ui.ytdlNavigationDestinations
import com.garyapp.ytdl.ui.ytdlVisibleContentLabels
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.ui.theme.YtdlThemeMode
import com.garyapp.ytdl.ui.theme.defaultYtdlThemeConfig
import com.garyapp.ytdl.ui.theme.themeConfigForSettings
import com.garyapp.ytdl.ui.theme.ytdlAppPaletteForPreset
import com.garyapp.ytdl.ui.theme.ytdlCompactTypographyForUiTest
import com.garyapp.ytdl.ui.theme.ytdlColorPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeUnitTest {
    @Test
    fun packageVersionIsReleaseVersion() {
        assertEquals("1.0.10", BuildConfig.VERSION_NAME)
        assertEquals(11, BuildConfig.VERSION_CODE)
    }

    @Test
    fun navigationLabelsExposeThreeAndroidTabs() {
        assertEquals(
            listOf("下载", "任务", "设置"),
            ytdlNavigationDestinations().map { it.label },
        )
    }

    @Test
    fun settingsTabReservesAppearanceAndColorEntry() {
        val settings = ytdlNavigationDestinations().single { it.route == "settings" }

        assertTrue(settings.reservedEntries.contains("外观与颜色"))
    }

    @Test
    fun visibleContentModelCoversThreeReferenceScreens() {
        val labels = ytdlVisibleContentLabels()

        assertEquals(setOf("download", "tasks", "settings"), labels.keys)
        assertTrue(labels.getValue("download").containsAll(listOf("粘贴媒体链接或文件直链", "识别", "视频+音频", "视频", "音频", "分辨率", "开始下载")))
        assertTrue(labels.getValue("download").containsAll(listOf("字幕", "保存文件名", "重名处理")))
        assertTrue(labels.getValue("tasks").containsAll(listOf("当前任务", "等待中", "搜索历史", "暂无历史记录", "完成下载后会显示")))
        assertTrue(labels.getValue("settings").containsAll(listOf("Cookies 文件", "媒体处理能力", "通知权限", "下载仍在应用内显示进度", "App 私有目录", "外观与颜色", "Codex 风格")))
    }

    @Test
    fun themeModelReservesModeAndColorPresetSettings() {
        val defaultConfig = defaultYtdlThemeConfig()
        val presets = ytdlColorPresets()

        assertEquals(YtdlThemeMode.System, defaultConfig.mode)
        assertEquals("morandi", defaultConfig.colorPreset.id)
        assertTrue(presets.any { it.id == "morandi" && it.label == "莫兰迪" })
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

    @Test
    fun compactTypographyMatchesReferenceDensity() {
        val typography = ytdlCompactTypographyForUiTest()

        assertEquals(20.sp, typography.headlineMedium.fontSize)
        assertEquals(14.sp, typography.bodyLarge.fontSize)
        assertEquals(13.sp, typography.labelLarge.fontSize)
        assertEquals(10.sp, typography.labelSmall.fontSize)
    }
}
