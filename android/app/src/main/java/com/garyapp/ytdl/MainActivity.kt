package com.garyapp.ytdl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.garyapp.ytdl.ui.YtdlApp
import com.garyapp.ytdl.ui.theme.YtdlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YtdlTheme {
                YtdlApp()
            }
        }
    }
}
