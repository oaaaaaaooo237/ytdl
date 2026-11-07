package com.garyapp.ytdl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.garyapp.ytdl.core.ytdlp.ParserRuntimeBootstrap
import com.garyapp.ytdl.core.ytdlp.ParserRuntimeState
import com.garyapp.ytdl.core.ytdlp.ParserVersionManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Requires a previously downloaded and selected parser, then a fresh target process. */
@RunWith(AndroidJUnit4::class)
class ParserRestartDeviceTest {
    @Test fun coldProcessLoadsPersistedDownloadedParser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = ParserVersionManager.fromContext(context).listVersions().single { it.isSelected }
        org.junit.Assume.assumeFalse("Requires a previously downloaded parser", selected.isBuiltIn)
        ParserRuntimeBootstrap.restoreSelection(context)
        val python = ParserRuntimeBootstrap.python(context)
        val actual = python.getModule("yt_dlp.version")["__version__"].toString()
        assertEquals(selected.version, actual)
        assertEquals(selected.version, ParserRuntimeState.activeVersion)
        assertEquals(selected.version, ParserVersionManager.fromContext(context).listVersions().single { it.isSelected }.version)
    }
}
