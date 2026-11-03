package com.garyapp.ytdl.core.ytdlp

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.garyapp.ytdl.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class EpornerAnalysisInstrumentedTest {
    @Test
    fun analyzesReportedEpornerUrlWithSelectedParser() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Set realEporner=true to run the reported Eporner analysis",
            arguments.getString("realEporner").toBoolean(),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            assertEquals(EXPECTED_PARSER_VERSION, ParserRuntimeState.activeVersion)
            val result = YtdlpBridge().analyze(REPORTED_URL)
            val failureCategory = (result.exceptionOrNull() as? YtdlpAnalysisException)?.category
            assertTrue("Eporner analysis failed: category=$failureCategory", result.isSuccess)
            val analysis = result.getOrThrow()
            assertTrue(analysis.title.isNotBlank())
            assertTrue(analysis.formats.isNotEmpty())
        }
    }

    private companion object {
        const val EXPECTED_PARSER_VERSION = "2026.7.4"
        const val REPORTED_URL =
            "https://www.eporner.com/video-5czAhpxw6bT/gender-x-date-switch-jade-venus-and-ariel-demure/"
    }
}
