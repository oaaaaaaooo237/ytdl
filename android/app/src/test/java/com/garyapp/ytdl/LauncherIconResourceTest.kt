package com.garyapp.ytdl

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconResourceTest {
    @Test
    fun manifestUsesAppOwnedLauncherAndRoundIcons() {
        val manifest = projectFile(
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml",
        ).readText()
        val icon = projectFile(
            "app/src/main/res/drawable-nodpi/ic_launcher_ytdl.png",
            "src/main/res/drawable-nodpi/ic_launcher_ytdl.png",
        )

        assertTrue(manifest.contains("android:icon=\"@drawable/ic_launcher_ytdl\""))
        assertTrue(manifest.contains("android:roundIcon=\"@drawable/ic_launcher_ytdl\""))
        assertTrue(icon.length() > 0L)
    }

    private fun projectFile(vararg candidates: String): File {
        return candidates.map(::File).firstOrNull(File::exists)
            ?: error("Missing project file: ${candidates.joinToString()}")
    }
}
