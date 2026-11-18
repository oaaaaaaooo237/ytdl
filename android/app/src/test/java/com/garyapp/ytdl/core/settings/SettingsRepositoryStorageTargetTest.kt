package com.garyapp.ytdl.core.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.core.storage.StorageTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryStorageTargetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearSettings() {
        context.getSharedPreferences("ytdl-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun safTreeTargetSurvivesRepositoryRecreation() {
        val target = StorageTarget.SafTree(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies",
            displayName = "视频保存",
        )

        SettingsRepository.fromContext(context).setDefaultStorageTarget(target)

        assertEquals(target, SettingsRepository.fromContext(context).getSettings().defaultStorageTarget)
    }

    @Test
    fun invalidSafTreeFallsBackToAppPrivate() {
        val repository = SettingsRepository(
            initialSettings = AppSettings(
                defaultStorageTarget = StorageTarget.SafTree(
                    treeUri = "file:///storage/emulated/0/Download",
                    displayName = "不安全目录",
                ),
            ),
        )

        assertEquals(StorageTarget.AppPrivate, repository.getSettings().defaultStorageTarget)
    }

    @Test
    fun contentDocumentUriIsNotAcceptedAsTreeTarget() {
        val repository = SettingsRepository(
            initialSettings = AppSettings(
                defaultStorageTarget = StorageTarget.SafTree(
                    treeUri = "content://com.android.externalstorage.documents/document/primary%3AMovies%2Fvideo.mp4",
                    displayName = "单个文件",
                ),
            ),
        )

        assertEquals(StorageTarget.AppPrivate, repository.getSettings().defaultStorageTarget)
    }

    @Test
    fun contentAuthorityNamedTreeWithoutTreePathIsNotAccepted() {
        val repository = SettingsRepository(
            initialSettings = AppSettings(
                defaultStorageTarget = StorageTarget.SafTree(
                    treeUri = "content://tree/user-selected",
                    displayName = "伪树目录",
                ),
            ),
        )

        assertEquals(StorageTarget.AppPrivate, repository.getSettings().defaultStorageTarget)
    }
}
