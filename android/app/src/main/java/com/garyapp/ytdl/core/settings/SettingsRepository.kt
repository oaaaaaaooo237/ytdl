package com.garyapp.ytdl.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.storage.StorageTargets
import org.json.JSONArray
import org.json.JSONObject

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

    fun setSiteCookies(name: String, domain: String, reference: CookiesReference?): AppSettings {
        val normalized = CookieSites.normalizeDomain(domain)
            ?: throw IllegalArgumentException("请输入有效域名，例如 x.com，不含路径。")
        val label = name.trim().take(60).ifBlank { normalized }
        require(!label.contains('\n') && !label.contains('\r')) { "网站名称不可换行。" }
        return update { it.copy(siteCookies = it.siteCookies + (normalized to SiteCookies(label, normalized, reference))) }
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
            .putString(KeySiteCookies, JSONArray().apply {
                settings.siteCookies.values.forEach { site ->
                    put(JSONObject().put("name", site.name).put("domain", site.domain)
                        .put("reference", site.reference?.value).put("displayName", site.reference?.displayName))
                }
            }.toString())
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
        private const val KeySiteCookies = "site_cookies"
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
                    siteCookies = readSites(prefs.getString(KeySiteCookies, null)),
                ),
                preferences = prefs,
            )
        }

        private fun readSites(raw: String?): Map<String, SiteCookies> {
            val entries = runCatching { JSONArray(raw ?: "[]") }.getOrElse { JSONArray() }
            return buildMap {
                for (i in 0 until entries.length()) {
                    val item = entries.optJSONObject(i) ?: continue
                    val domain = CookieSites.normalizeDomain(item.optString("domain")) ?: continue
                    val name = item.optString("name").take(60).ifBlank { domain }
                    val reference = CookiesReference.fromUserReference(item.optString("reference"), item.optString("displayName"))
                    put(domain, SiteCookies(name, domain, reference))
                }
            }
        }
    }
}
