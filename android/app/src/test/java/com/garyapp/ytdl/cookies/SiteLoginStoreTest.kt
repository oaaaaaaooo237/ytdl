package com.garyapp.ytdl.cookies

import com.garyapp.ytdl.download.AesGcmRetryPayloadCipher
import com.garyapp.ytdl.testing.ProjectTestPaths
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SiteLoginStoreTest {
    @Test fun savesEncryptedPerSiteSnapshotsAndClearsOnlyRequestedSite() {
        val dir = ProjectTestPaths.createTempDirectory("site-login-").toFile()
        val cipher = AesGcmRetryPayloadCipher(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))
        val store = SiteLoginStore(dir, "com.garyapp.ytdl.sitecookies", cipher)
        val x = store.save("x.com", "auth_token=private-test-value; ct0=csrf")
        val newer = store.save("x.com", "auth_token=other-value")
        val yt = store.save("youtube.com", "SID=youtube-value")
        assertNotEquals(x, newer)
        assertTrue(store.read(x.value).toString(Charsets.UTF_8).contains(".x.com\tTRUE"))
        assertTrue(store.read(x.value).toString(Charsets.UTF_8).contains("private-test-value"))
        assertFalse(dir.listFiles()!!.any { it.readBytes().toString(Charsets.UTF_8).contains("private-test-value") })
        assertTrue(runCatching { store.read("content://com.garyapp.ytdl.sitecookies/../../file") }.isFailure)
        store.clear("x.com")
        assertTrue(runCatching { store.read(x.value) }.isFailure)
        assertTrue(runCatching { store.read(newer.value) }.isFailure)
        assertTrue(store.read(yt.value).toString(Charsets.UTF_8).contains("youtube-value"))
        dir.deleteRecursively()
    }

    @Test fun serializationRejectsEmptyAndInjectedCookies() {
        assertTrue(runCatching { SiteLoginStore.netscapeCookies("x.com", "") }.isFailure)
        assertTrue(runCatching { SiteLoginStore.netscapeCookies("x.com", "a=b\nother=secret") }.isFailure)
        val result = SiteLoginStore.netscapeCookies("x.com", "a=b=c; a=second; ct0=token")
        assertTrue(result.startsWith("# Netscape HTTP Cookie File\n"))
        assertTrue(result.contains("\ta\tb=c\n"))
        assertFalse(result.contains("second"))
    }
}
