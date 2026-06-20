package com.garyapp.ytdl

import com.garyapp.ytdl.ui.ytdlNavigationDestinations
import com.garyapp.ytdl.ui.ytdlVisibleContentLabels
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
    fun visibleContentModelCoversFiveReferenceScreens() {
        val labels = ytdlVisibleContentLabels()

        assertEquals(setOf("download", "formats", "queue", "history", "settings"), labels.keys)
        assertTrue(labels.getValue("download").containsAll(listOf("粘贴公开视频页面地址", "分析", "开始下载")))
        assertTrue(labels.getValue("formats").containsAll(listOf("视频+音频", "1080p", "需合并", "字幕")))
        assertTrue(labels.getValue("queue").containsAll(listOf("下载进行中", "真实下载中", "等待中", "已完成", "失败", "暂停", "取消")))
        assertTrue(labels.getValue("history").containsAll(listOf("搜索历史", "打开", "分享", "删除")))
        assertTrue(labels.getValue("settings").containsAll(listOf("Cookies 文件", "媒体处理能力", "外观与颜色", "Codex 风格")))
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
