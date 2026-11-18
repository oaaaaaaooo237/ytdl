package com.garyapp.ytdl.core.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SiteCookiesTest {
    private fun ref(name: String) = CookiesReference.fromUserReference("content://files/$name", name)!!

    @Test fun selectsAliasesAndSubdomainsWithoutSubstringMatches() {
        val repo = SettingsRepository()
        val x = ref("x.txt")
        val yt = ref("youtube.txt")
        repo.setSiteCookies("X", "x.com", x)
        repo.setSiteCookies("YouTube", "youtube.com", yt)
        val settings = repo.getSettings()
        assertEquals(x, settings.cookiesForUrl("https://mobile.twitter.com/u/status/1"))
        assertEquals(yt, settings.cookiesForUrl("https://youtu.be/123"))
        assertNull(settings.cookiesForUrl("https://x.com.evil.example/watch"))
        assertNull(settings.cookiesForUrl("https://notx.com/watch"))
    }

    @Test fun specificDomainOverridesParentAndLegacyFileRemainsExplicitFallback() {
        val old = ref("legacy.txt")
        val repo = SettingsRepository(AppSettings(cookiesReference = old))
        repo.setSiteCookies("主站", "example.com", ref("parent.txt"))
        repo.setSiteCookies("子站", "media.example.com", ref("child.txt"))
        assertEquals(ref("child.txt"), repo.getSettings().cookiesForUrl("https://media.example.com/video"))
        assertEquals(old, repo.getSettings().cookiesForUrl("https://unconfigured.example/video"))
        repo.setSiteCookies("子站", "media.example.com", null)
        assertNull(repo.getSettings().cookiesForUrl("https://media.example.com/video"))
    }

    @Test fun siteReferencesSurviveRestartAndClearWithoutChangingOthers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("ytdl-settings", Context.MODE_PRIVATE).edit().clear().commit()
        val repo = SettingsRepository.fromContext(context)
        repo.setSiteCookies("自定义", "video.example.com", ref("custom.txt"))
        repo.setSiteCookies("X", "twitter.com", ref("x.txt"))
        val restored = SettingsRepository.fromContext(context)
        assertEquals(ref("x.txt"), restored.getSettings().cookiesForUrl("https://x.com/a"))
        restored.setSiteCookies("X", "x.com", null)
        val final = SettingsRepository.fromContext(context).getSettings()
        assertNull(final.cookiesForUrl("https://x.com/a"))
        assertEquals(ref("custom.txt"), final.cookiesForUrl("https://video.example.com/a"))
    }

    @Test fun rejectsInvalidOrAmbiguousDomains() {
        val repo = SettingsRepository()
        listOf("https://x.com/path", "*.x.com", "x.com:443", "x.com/other", "localhost", "a..com").forEach {
            assertTrue(runCatching { repo.setSiteCookies("网站", it, ref("file")) }.isFailure)
        }
    }
}
