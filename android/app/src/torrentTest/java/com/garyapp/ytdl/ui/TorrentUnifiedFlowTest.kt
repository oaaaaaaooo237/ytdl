package com.garyapp.ytdl.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.EditText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.R
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadTaskState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrentUnifiedFlowTest {
    @get:Rule val composeRule = createComposeRule()
    @After fun reset() { DownloadCoordinator.resetForTests() }

    @Test fun magnetUsesTheMainDownloadResultAndSharedStarterThenOpensTasks() {
        DownloadCoordinator.resetForTests()
        SettingsRepository.fromContext(ApplicationProvider.getApplicationContext<Context>())
            .setDefaultStorageTarget(StorageTarget.AppPrivate)
        val captured = AtomicReference<DownloadRequest>()
        lateinit var host: Context
        composeRule.setContent {
            host = LocalContext.current
            YtdlApp(
                parserUpdateCoordinator = ParserUpdateCoordinator(ParserUpdateChecker("2026.3.17") { error("offline") }, Executor(Runnable::run)),
                downloadStarter = { _, request, _ -> captured.set(request); Result.success(DownloadTaskState.waiting(request)) },
            )
        }
        val link = "magnet:?xt=urn:btih:27DB34AE33365674C0F982EB7FD0E312B8703E8F&dn=Test+Torrent"
        composeRule.runOnIdle {
            var current = host
            while (current is ContextWrapper && current !is Activity) current = current.baseContext
            (current as Activity).findViewById<EditText>(R.id.ytdl_url_input).setText(link)
        }
        composeRule.onNodeWithTag("ytdl-analyze-button").performClick()
        composeRule.onNodeWithTag("ytdl-download-start").performClick()
        composeRule.waitUntil(5000) { captured.get() != null }
        assertEquals(DownloadRoute.Torrent(link), captured.get().route)
        assertEquals("Test Torrent", captured.get().title)
        assertFalse(captured.get().usesCookies)
        composeRule.onNodeWithTag("ytdl-screen-tasks").assertExists()
    }
}
