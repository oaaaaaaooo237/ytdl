package com.garyapp.ytdl.storage

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalDownloadProbeInstrumentedTest {
    @Test
    fun parsesToyboxLsOutputForUniqueExportFile() {
        val listing = """
            -rw-rw---- 1 root everybody      128 2026-07-05 20:01 unrelated.txt
            -rw-rw---- 1 root everybody     4096 2026-07-05 20:02 ytdl-export-1783250902008.mp4
        """.trimIndent()

        val match = ExternalDownloadProbe.findUniqueExportVariant(
            listing = listing,
            exportPrefix = "ytdl-export-1783250902008",
        )

        assertEquals("ytdl-export-1783250902008.mp4", match?.displayName)
        assertEquals(4096L, match?.bytes)
    }

    @Test
    fun returnsNullWhenMultipleExportVariantsMatch() {
        val listing = """
            -rw-rw---- 1 root everybody     4096 2026-07-05 20:02 ytdl-export-1783250902008.mp4
            -rw-rw---- 1 root everybody     4096 2026-07-05 20:03 ytdl-export-1783250902008 (1).mp4
        """.trimIndent()

        assertNull(
            ExternalDownloadProbe.findUniqueExportVariant(
                listing = listing,
                exportPrefix = "ytdl-export-1783250902008",
            ),
        )
    }

    @Test
    fun findsSmallExportFileFromRealDownloadDirectoryListing() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val prefix = "ytdl-export-probe-${System.currentTimeMillis()}"
        val fileName = "$prefix.mp4"
        val remotePath = "/sdcard/Download/$fileName"
        try {
            device.executeShellCommand("dd if=/dev/zero of=$remotePath bs=1 count=5")
            val listing = device.executeShellCommand("ls -l /sdcard/Download")

            val match = ExternalDownloadProbe.findUniqueExportVariant(
                listing = listing,
                exportPrefix = prefix,
            )

            assertNotNull("未识别刚写入的小导出文件：$listing", match)
            assertEquals(fileName, match?.displayName)
            assertEquals(5L, match?.bytes)
        } finally {
            device.executeShellCommand("rm -f $remotePath")
        }
    }
}
