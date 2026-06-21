package com.garyapp.ytdl.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.garyapp.ytdl.core.storage.StorageTarget

class SettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    private val preferences: SharedPreferences? = null,
) {
    private var currentSettings: AppSettings = initialSettings.sanitized()

    fun getSettings(): AppSettings = currentSettings

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        currentSettings = transform(currentSettings)
        persist(currentSettings)
        return currentSettings
    }

    fun setCookiesReference(reference: CookiesReference?): AppSettings = update {
        it.copy(cookiesReference = reference)
    }

    fun setDefaultStorageTarget(target: StorageTarget): AppSettings = update {
        it.copy(defaultStorageTarget = target)
    }

    fun setThemeMode(modeId: String): AppSettings = update {
        it.copy(themeModeId = AppearanceSettings.normalizeThemeModeId(modeId))
    }

    fun setColorPreset(presetId: String): AppSettings = update {
        it.copy(colorPresetId = AppearanceSettings.normalizeColorPresetId(presetId))
    }

    private fun persist(settings: AppSettings) {
        val prefs = preferences ?: return
        prefs.edit()
            .putString(KeyCookiesReference, settings.cookiesReference?.value)
            .putString(KeyCookiesDisplayName, settings.cookiesReference?.displayName)
            .putString(KeyThemeMode, settings.themeModeId)
            .putString(KeyColorPreset, settings.colorPresetId)
            .apply()
    }

    private fun AppSettings.sanitized(): AppSettings = copy(
        themeModeId = AppearanceSettings.normalizeThemeModeId(themeModeId),
        colorPresetId = AppearanceSettings.normalizeColorPresetId(colorPresetId),
    )

    companion object {
        private const val PreferencesName = "ytdl-settings"
        private const val KeyCookiesReference = "cookies_reference"
        private const val KeyCookiesDisplayName = "cookies_display_name"
        private const val KeyThemeMode = "theme_mode"
        private const val KeyColorPreset = "color_preset"

        fun fromContext(context: Context): SettingsRepository {
            val prefs = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            return SettingsRepository(
                initialSettings = AppSettings(
                    cookiesReference = CookiesReference.fromUserReference(
                        reference = prefs.getString(KeyCookiesReference, null).orEmpty(),
                        displayName = prefs.getString(KeyCookiesDisplayName, null),
                    ),
                    themeModeId = prefs.getString(KeyThemeMode, null).orEmpty(),
                    colorPresetId = prefs.getString(KeyColorPreset, null).orEmpty(),
                ),
                preferences = prefs,
            )
        }
    }
}
