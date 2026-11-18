package com.garyapp.ytdl

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.garyapp.ytdl.core.ytdlp.ParserRuntimeState
import com.garyapp.ytdl.core.ytdlp.ParserRuntimeBootstrap
import com.garyapp.ytdl.core.ytdlp.ParserVersionManager
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.ui.YtdlApp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        ParserRuntimeBootstrap.initializeOnce {
            val parserVersionManager = ParserVersionManager.fromContext(this)
            val runtimeWheel = parserVersionManager.prepareSelectedRuntimeWheel(
                File(noBackupFilesDir, ParserVersionManager.RuntimeDirectoryName),
            )
            if (runtimeWheel != null) {
                val inserted = runCatching {
                    requireNotNull(Python.getInstance().getModule("sys")["path"])
                        .callAttr("insert", 0, runtimeWheel.absolutePath)
                }.isSuccess
                if (inserted) {
                    ParserRuntimeState.activeRuntimeWheelPath = runtimeWheel.absolutePath
                } else {
                    parserVersionManager.select(YtdlpBridge.PINNED_YTDLP_VERSION)
                }
            }
            ParserRuntimeState.activeVersion = parserVersionManager.listVersions()
                .first { it.isSelected }
                .version
        }
        configureLightSystemBars()
        setContent {
            YtdlApp()
        }
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
