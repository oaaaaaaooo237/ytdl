package com.garyapp.ytdl.core.ytdlp

import android.os.Build
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtdlpBridgeInstrumentedTest {
    @Test
    fun analyzesRequiredYoutubeSmokeUrl() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(androidx.test.core.app.ApplicationProvider.getApplicationContext()))
        }

        val result = YtdlpBridge().analyze("https://www.youtube.com/watch?v=tkxzMEfp49Q")

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val analysis = result.getOrThrow()
        val highestHeight = analysis.formats.mapNotNull { it.height }.maxOrNull()
        val evidence = "YTDL_ANALYSIS_SMOKE sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} title=${analysis.title.take(80)} formats=${analysis.formats.size} highest=${highestHeight ?: 0} subtitles=${analysis.subtitles.size}"
        println(evidence)
        Log.i("YtdlpBridgeSmoke", evidence)
        assertEquals(37, Build.VERSION.SDK_INT)
        assertTrue(analysis.title.isNotBlank())
        assertTrue(analysis.formats.isNotEmpty())
    }
}
