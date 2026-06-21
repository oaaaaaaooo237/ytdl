package com.garyapp.ytdl.core.settings

import com.garyapp.ytdl.core.storage.StorageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStorageTest {
    @Test
    fun cookiesReferenceStoresOnlyReferenceAndDisplayName() {
        val reference = CookiesReference.fromUserReference(
            reference = "content://com.android.providers.downloads.documents/document/42",
            displayName = "cookies.txt",
        )
        val settings = AppSettings(cookiesReference = reference)

        assertEquals("cookies.txt", settings.cookiesReference?.displayName)
        assertEquals("content://com.android.providers.downloads.documents/document/42", settings.cookiesReference?.value)
        assertFalse(settings.toString().contains("SID=secret"))
    }

    @Test
    fun cookiesReferenceRejectsCookieContentLikeStrings() {
        val reference = CookiesReference.fromUserReference(
            reference = "SID=secret; PREF=visible",
            displayName = "cookies.txt",
        )

        assertNull(reference)
    }

    @Test
    fun cookiesReferenceRejectsNetscapeCookieFileContent() {
        val reference = CookiesReference.fromUserReference(
            reference = """
                # Netscape HTTP Cookie File
                .youtube.com	TRUE	/	FALSE	2147483647	SID	secret-value
            """.trimIndent(),
            displayName = "cookies.txt",
        )

        assertNull(reference)
    }

    @Test
    fun cookiesReferenceRejectsArbitraryNonPathText() {
        val reference = CookiesReference.fromUserReference(
            reference = "this is not a user selected file uri or path",
            displayName = "cookies.txt",
        )

        assertNull(reference)
    }

    @Test
    fun cookiesReferenceAcceptsFileUriAndAbsolutePaths() {
        val contentUri = CookiesReference.fromUserReference(
            reference = "content://com.android.providers.downloads.documents/document/42",
            displayName = "cookies.txt",
        )
        val fileUri = CookiesReference.fromUserReference(
            reference = "file:///storage/emulated/0/Download/cookies.txt",
            displayName = "cookies.txt",
        )
        val androidPath = CookiesReference.fromUserReference(
            reference = "/storage/emulated/0/Download/cookies.txt",
            displayName = "cookies.txt",
        )
        val windowsPath = CookiesReference.fromUserReference(
            reference = "D:/safe/reference/cookies.txt",
            displayName = "cookies.txt",
        )

        assertEquals("content://com.android.providers.downloads.documents/document/42", contentUri?.value)
        assertEquals("file:///storage/emulated/0/Download/cookies.txt", fileUri?.value)
        assertEquals("/storage/emulated/0/Download/cookies.txt", androidPath?.value)
        assertEquals("D:/safe/reference/cookies.txt", windowsPath?.value)
    }

    @Test
    fun cookiesReferenceToStringDoesNotExposeStoredReference() {
        val reference = CookiesReference.fromUserReference(
            reference = "content://com.android.providers.downloads.documents/document/42",
            displayName = "cookies.txt",
        )

        assertFalse(reference.toString().contains("content://"))
        assertTrue(reference.toString().contains("cookies.txt"))
    }

    @Test
    fun cookiesReferenceSanitizesSensitiveDisplayName() {
        val reference = CookiesReference.fromUserReference(
            reference = "content://com.android.providers.downloads.documents/document/42",
            displayName = "SID=secret; PREF=visible",
        )

        assertEquals("cookies 文件", reference?.displayName)
        assertFalse(reference.toString().contains("secret"))
        assertFalse(reference.toString().contains("visible"))
    }

    @Test
    fun cookiesReferenceSanitizesReferenceLikeDisplayName() {
        val reference = CookiesReference.fromUserReference(
            reference = "content://com.android.providers.downloads.documents/document/42",
            displayName = "content://com.android.providers.downloads.documents/document/42",
        )

        assertEquals("cookies 文件", reference?.displayName)
        assertFalse(reference.toString().contains("content://"))
    }

    @Test
    fun defaultSettingsUseAppPrivateStorage() {
        val settings = AppSettings()

        assertEquals(StorageTarget.AppPrivate, settings.defaultStorageTarget)
        assertEquals(AppearanceSettings.ThemeModeSystem, settings.themeModeId)
        assertEquals(AppearanceSettings.ColorPresetReferenceV3, settings.colorPresetId)
    }

    @Test
    fun storageTargetsModelPlaySafeDestinations() {
        val targets = listOf(
            StorageTarget.AppPrivate,
            StorageTarget.MediaStoreDownloads(displayName = "video.mp4", mimeType = "video/mp4"),
            StorageTarget.CreateDocument(uri = "content://export/video", displayName = "video.mp4", mimeType = "video/mp4"),
            StorageTarget.SafTree(treeUri = "content://tree/downloads", displayName = "项目目录"),
        )

        assertEquals(4, targets.distinct().size)
        assertTrue(targets.any { it is StorageTarget.MediaStoreDownloads })
        assertTrue(targets.any { it is StorageTarget.CreateDocument })
        assertTrue(targets.any { it is StorageTarget.SafTree })
    }

    @Test
    fun settingsRepositoryUpdatesReferencesWithoutCookieContents() {
        val repository = SettingsRepository()

        repository.setCookiesReference(
            CookiesReference.fromUserReference(
                reference = "D:/safe/reference/cookies.txt",
                displayName = "cookies.txt",
            ),
        )
        repository.setDefaultStorageTarget(
            StorageTarget.SafTree(
                treeUri = "content://tree/user-selected",
                displayName = "用户选择目录",
            ),
        )

        val settings = repository.getSettings()
        assertEquals("cookies.txt", settings.cookiesReference?.displayName)
        assertTrue(settings.defaultStorageTarget is StorageTarget.SafTree)
        assertFalse(settings.toString().contains("SID=secret"))
    }

    @Test
    fun settingsRepositoryPersistsAppearanceChoiceAsSafeIds() {
        val repository = SettingsRepository()

        repository.setThemeMode(AppearanceSettings.ThemeModeDark)
        repository.setColorPreset(AppearanceSettings.ColorPresetCodex)

        val settings = repository.getSettings()
        assertEquals(AppearanceSettings.ThemeModeDark, settings.themeModeId)
        assertEquals(AppearanceSettings.ColorPresetCodex, settings.colorPresetId)
    }

    @Test
    fun settingsRepositoryFallsBackForUnknownAppearanceIds() {
        val repository = SettingsRepository(
            initialSettings = AppSettings(
                themeModeId = "unexpected",
                colorPresetId = "unknown-preset",
            ),
        )

        assertEquals(AppearanceSettings.ThemeModeSystem, repository.getSettings().themeModeId)
        assertEquals(AppearanceSettings.ColorPresetReferenceV3, repository.getSettings().colorPresetId)

        repository.setThemeMode("bad-mode")
        repository.setColorPreset("bad-preset")

        assertEquals(AppearanceSettings.ThemeModeSystem, repository.getSettings().themeModeId)
        assertEquals(AppearanceSettings.ColorPresetReferenceV3, repository.getSettings().colorPresetId)
    }
}
