package com.garyapp.ytdl.core.settings

import com.garyapp.ytdl.core.storage.StorageTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsStorageTest {
    @Test
    fun cookiesReferenceStoresOnlyReferenceAndDisplayName() {
        val reference = CookiesReference(
            reference = "content://com.android.providers.downloads.documents/document/42",
            displayName = "cookies.txt",
        )
        val settings = AppSettings(cookiesReference = reference)

        assertEquals("cookies.txt", settings.cookiesReference?.displayName)
        assertEquals("content://com.android.providers.downloads.documents/document/42", settings.cookiesReference?.reference)
        assertFalse(settings.toString().contains("SID=secret"))
    }

    @Test
    fun defaultSettingsUseAppPrivateStorage() {
        val settings = AppSettings()

        assertEquals(StorageTarget.AppPrivate, settings.defaultStorageTarget)
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
            CookiesReference(
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
}
