package com.garyapp.ytdl.core.ytdlp

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.garyapp.ytdl.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NoodleMagazineAnalysisInstrumentedTest {
    @Test
    fun analyzesReportedNoodleMagazineUrlWithSelectedParser() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Set realNoodleMagazine=true to run the reported NoodleMagazine analysis",
            arguments.getString("realNoodleMagazine").toBoolean(),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            assertEquals(EXPECTED_PARSER_VERSION, ParserRuntimeState.activeVersion)
            val result = YtdlpBridge().analyze(REPORTED_URL)
            val failureCategory = (result.exceptionOrNull() as? YtdlpAnalysisException)?.category
            assertTrue("NoodleMagazine analysis failed: category=$failureCategory", result.isSuccess)
            val analysis = result.getOrThrow()
            assertTrue(analysis.title.isNotBlank())
            assertTrue(analysis.formats.isNotEmpty())
        }
    }

    private companion object {
        const val EXPECTED_PARSER_VERSION = "2026.7.4"
        const val REPORTED_URL =
            "https://noodlemagazine.com/watch/-207085431_456241493"
    }
}
