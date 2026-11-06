package com.garyapp.ytdl.core.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun automaticUsesChineseOnlyForSimplifiedChinese() {
        for (tag in listOf("zh-CN", "zh-SG", "zh-Hans", "zh-Hans-HK")) {
            assertEquals(tag, AppLanguage.Chinese, AppLanguage.System.resolve(Locale.forLanguageTag(tag)))
        }
        for (tag in listOf("zh-TW", "zh-HK", "zh-Hant", "en-US", "ja-JP", "fr-FR")) {
            assertEquals(tag, AppLanguage.English, AppLanguage.System.resolve(Locale.forLanguageTag(tag)))
        }
    }

    @Test fun manualSelectionOverridesSystemLanguage() {
        assertEquals(AppLanguage.English, AppLanguage.English.resolve(Locale.SIMPLIFIED_CHINESE))
        assertEquals(AppLanguage.Chinese, AppLanguage.Chinese.resolve(Locale.JAPANESE))
        assertEquals(AppLanguage.System, AppLanguage.fromId("unknown"))
    }
}
