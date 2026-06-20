package com.garyapp.ytdl.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.garyapp.ytdl.core.storage.StorageTarget

class SettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    private val preferences: SharedPreferences? = null,
) {
    private var currentSettings: AppSettings = initialSettings

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

    private fun persist(settings: AppSettings) {
        val prefs = preferences ?: return
        prefs.edit()
            .putString(KeyCookiesReference, settings.cookiesReference?.value)
            .putString(KeyCookiesDisplayName, settings.cookiesReference?.displayName)
            .apply()
    }

    companion object {
        private const val PreferencesName = "ytdl-settings"
        private const val KeyCookiesReference = "cookies_reference"
        private const val KeyCookiesDisplayName = "cookies_display_name"

        fun fromContext(context: Context): SettingsRepository {
            val prefs = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            return SettingsRepository(
                initialSettings = AppSettings(
                    cookiesReference = CookiesReference.fromUserReference(
                        reference = prefs.getString(KeyCookiesReference, null).orEmpty(),
                        displayName = prefs.getString(KeyCookiesDisplayName, null),
                    ),
                ),
                preferences = prefs,
            )
        }
    }
}
