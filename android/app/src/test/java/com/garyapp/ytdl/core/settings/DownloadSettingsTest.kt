package com.garyapp.ytdl.core.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.download.HttpFileConflictPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadSettingsTest {
    @Test fun legacySettingsKeepAppearanceAndGainDownloadDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("ytdl-settings", Context.MODE_PRIVATE).edit().putString("theme_mode", "dark").commit()
        val settings = SettingsRepository.fromContext(context).getSettings()
        assertEquals("dark", settings.themeModeId)
        assertEquals(2, settings.maxConcurrentDownloads)
        assertEquals(HttpFileConflictPolicy.AutoRename, settings.fileConflictPolicy)
        assertTrue(settings.notifyDownloadCompleted)
        assertTrue(settings.notifyDownloadFailed)
        assertEquals(5, settings.maxWaitingDownloads)
        assertFalse(settings.jumpToDownloadTask)
        assertTrue(settings.resumeDownloads)
        assertFalse(settings.downloadDetectedSubtitles)
    }

    @Test fun waitingLimitMigratesUnlimitedAndClampsOversizedValues() {
        val repo = SettingsRepository()
        assertEquals(5, repo.update { it.copy(maxWaitingDownloads = null) }.maxWaitingDownloads)
        assertEquals(5, repo.update { it.copy(maxWaitingDownloads = -1) }.maxWaitingDownloads)
        assertEquals(50, repo.update { it.copy(maxWaitingDownloads = 100) }.maxWaitingDownloads)
    }

    @Test fun downloadPreferencesSurviveRecreationAndInvalidConcurrencyUsesDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository.fromContext(context)
        repository.update { it.copy(maxConcurrentDownloads = 5, maxWaitingDownloads = 0, jumpToDownloadTask = true,
            resumeDownloads = false, downloadDetectedSubtitles = true, fileConflictPolicy = HttpFileConflictPolicy.Ask,
            notifyDownloadCompleted = false, notifyDownloadFailed = false) }
        val saved = SettingsRepository.fromContext(context).getSettings()
        assertEquals(5, saved.maxConcurrentDownloads)
        assertEquals(0, saved.maxWaitingDownloads)
        assertTrue(saved.jumpToDownloadTask)
        assertFalse(saved.resumeDownloads)
        assertTrue(saved.downloadDetectedSubtitles)
        assertEquals(HttpFileConflictPolicy.Ask, saved.fileConflictPolicy)
        assertFalse(saved.notifyDownloadCompleted)
        assertFalse(saved.notifyDownloadFailed)
        assertEquals(2, repository.update { it.copy(maxConcurrentDownloads = 6) }.maxConcurrentDownloads)
    }
}
