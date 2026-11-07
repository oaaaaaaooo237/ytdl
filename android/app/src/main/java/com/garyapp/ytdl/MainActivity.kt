package com.garyapp.ytdl

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.garyapp.ytdl.core.policy.sharedDownloadUrl
import com.garyapp.ytdl.ui.YtdlApp

class MainActivity : ComponentActivity() {
    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.garyapp.ytdl.core.ytdlp.ParserRuntimeBootstrap.prepareSelectedVersionAsync(applicationContext)
        sharedUrl.value = readSharedUrl(intent)
        configureLightSystemBars()
        setContent {
            YtdlApp(
                showFirstRunGuide = true,
                sharedUrl = sharedUrl.value,
                onSharedUrlConsumed = {
                    sharedUrl.value = null
                    intent.removeExtra(Intent.EXTRA_TEXT)
                },
            )
        }
        Thread({ runCatching { com.garyapp.ytdl.download.restoreLegacyTorrentDownloads(applicationContext) } },
            "ytdl-torrent-recovery").apply { isDaemon = true }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrl.value = readSharedUrl(intent)
    }

    private fun readSharedUrl(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return sharedDownloadUrl(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
    }

    private fun configureLightSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }
}
