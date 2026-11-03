package com.garyapp.ytdl.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.storage.StorageTargets

class SettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    private val preferences: SharedPreferences? = null,
) {
    private var currentSettings: AppSettings = initialSettings.sanitized()

    fun getSettings(): AppSettings = currentSettings

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        currentSettings = transform(currentSettings).sanitized()
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
        val target = StorageTargets.sanitizeDefault(settings.defaultStorageTarget)
        val editor = prefs.edit()
            .putString(KeyCookiesReference, settings.cookiesReference?.value)
            .putString(KeyCookiesDisplayName, settings.cookiesReference?.displayName)
            .putString(KeyThemeMode, settings.themeModeId)
            .putString(KeyColorPreset, settings.colorPresetId)
            .putString(KeyStorageTargetType, StorageTargets.persistedType(target))
        if (target is StorageTarget.SafTree) {
            editor
                .putString(KeyStorageTreeUri, target.treeUri)
                .putString(KeyStorageDisplayName, target.displayName)
        } else {
            editor
                .remove(KeyStorageTreeUri)
                .remove(KeyStorageDisplayName)
        }
        editor.apply()
    }

    private fun AppSettings.sanitized(): AppSettings = copy(
        defaultStorageTarget = StorageTargets.sanitizeDefault(defaultStorageTarget),
        themeModeId = AppearanceSettings.normalizeThemeModeId(themeModeId),
        colorPresetId = AppearanceSettings.normalizeColorPresetId(colorPresetId),
    )

    companion object {
        private const val PreferencesName = "ytdl-settings"
        private const val KeyCookiesReference = "cookies_reference"
        private const val KeyCookiesDisplayName = "cookies_display_name"
        private const val KeyThemeMode = "theme_mode"
        private const val KeyColorPreset = "color_preset"
        private const val KeyStorageTargetType = "storage_target_type"
        private const val KeyStorageTreeUri = "storage_tree_uri"
        private const val KeyStorageDisplayName = "storage_display_name"

        fun fromContext(context: Context): SettingsRepository {
            val prefs = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            return SettingsRepository(
                initialSettings = AppSettings(
                    defaultStorageTarget = StorageTargets.restoreDefault(
                        type = prefs.getString(KeyStorageTargetType, null),
                        treeUri = prefs.getString(KeyStorageTreeUri, null),
                        displayName = prefs.getString(KeyStorageDisplayName, null),
                    ),
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
