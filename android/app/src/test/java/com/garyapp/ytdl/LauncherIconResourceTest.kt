package com.garyapp.ytdl

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val adaptiveIcon = projectFile(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            "src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
        ).readText()

        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher\""))
        assertTrue(adaptiveIcon.contains("android:drawable=\"@color/ic_launcher_background\""))
        assertTrue(adaptiveIcon.contains("android:drawable=\"@drawable/ic_launcher_ytdl\""))
        assertTrue(icon.length() > 0L)
    }

    @Test
    fun launcherIconIsSquareAndHasFullBleedArtwork() {
        val icon = projectFile(
            "app/src/main/res/drawable-nodpi/ic_launcher_ytdl.png",
            "src/main/res/drawable-nodpi/ic_launcher_ytdl.png",
        )
        val image = ImageIO.read(icon)

        assertEquals(image.width, image.height)
        listOf(
            image.getRGB(0, 0),
            image.getRGB(image.width - 1, 0),
            image.getRGB(0, image.height - 1),
            image.getRGB(image.width - 1, image.height - 1),
        ).forEach { pixel ->
            val red = pixel shr 16 and 0xFF
            val green = pixel shr 8 and 0xFF
            val blue = pixel and 0xFF
            assertFalse(red > 240 && green > 240 && blue > 240)
        }
    }

    private fun projectFile(vararg candidates: String): File {
        return candidates.map(::File).firstOrNull(File::exists)
            ?: error("Missing project file: ${candidates.joinToString()}")
    }
}
