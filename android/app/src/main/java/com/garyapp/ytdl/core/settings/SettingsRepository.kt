package com.garyapp.ytdl.core.settings

import com.garyapp.ytdl.core.storage.StorageTarget

class SettingsRepository(
    initialSettings: AppSettings = AppSettings(),
) {
    private var currentSettings: AppSettings = initialSettings

    fun getSettings(): AppSettings = currentSettings

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        currentSettings = transform(currentSettings)
        return currentSettings
    }

    fun setCookiesReference(reference: CookiesReference?): AppSettings = update {
        it.copy(cookiesReference = reference)
    }

    fun setDefaultStorageTarget(target: StorageTarget): AppSettings = update {
        it.copy(defaultStorageTarget = target)
    }
}
