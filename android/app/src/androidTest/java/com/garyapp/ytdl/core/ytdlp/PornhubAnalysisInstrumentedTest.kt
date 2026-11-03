package com.garyapp.ytdl.core.ytdlp

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PornhubAnalysisInstrumentedTest {
    @Test
    fun analyzesReportedPornhubUrl() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString("realPornhub")
            .toBoolean()
        assumeTrue("Set realPornhub=true to run the reported Pornhub analysis", enabled)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(ApplicationProvider.getApplicationContext()))
        }
        val result = YtdlpBridge().analyze(REPORTED_URL)
        val failureCategory = (result.exceptionOrNull() as? YtdlpAnalysisException)?.category
        assertTrue("Pornhub analysis failed: category=$failureCategory", result.isSuccess)
        val analysis = result.getOrThrow()
        assertTrue(analysis.title.isNotBlank())
        assertTrue(analysis.formats.isNotEmpty())
    }

    private companion object {
        const val REPORTED_URL =
            "https://cn.pornhub.com/view_video.php?viewkey=6a1750eb0f515"
    }
}
