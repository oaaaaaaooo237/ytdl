package com.garyapp.ytdl.core.settings

import java.util.Locale

enum class AppLanguage(val id: String) {
    System("system"), Chinese("zh-Hans"), English("en");

    fun resolve(systemLocale: Locale): AppLanguage {
        if (this != System) return this
        if (systemLocale.language != "zh") return English
        return when (systemLocale.script) {
            "Hans" -> Chinese
            "" -> if (systemLocale.country in setOf("TW", "HK", "MO")) English else Chinese
            else -> English
        }
    }

    fun pick(chinese: String, english: String): String = if (this == English) english else chinese

    companion object {
        fun fromId(id: String): AppLanguage = entries.firstOrNull { it.id == id } ?: System
    }
}
