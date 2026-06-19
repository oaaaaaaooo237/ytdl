package com.garyapp.ytdl.core.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextTest {
    @Test
    fun redactsCookieAndAuthorizationHeaders() {
        val raw = """
            Cookie: SID=abc123; PREF=visible
            Authorization: Bearer super-secret-token
            cookies: session=hidden
        """.trimIndent()

        val redacted = SensitiveText.redact(raw)

        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("super-secret-token"))
        assertFalse(redacted.contains("session=hidden"))
        assertTrue(redacted.contains("Cookie: [已隐藏]"))
        assertTrue(redacted.contains("Authorization: [已隐藏]"))
        assertTrue(redacted.contains("cookies: [已隐藏]"))
    }

    @Test
    fun redactsBearerTokensEmbeddedInText() {
        val redacted = SensitiveText.redact("download failed with bearer abc.def.ghi in headers")

        assertFalse(redacted.contains("abc.def.ghi"))
        assertTrue(redacted.contains("bearer [已隐藏]"))
    }

    @Test
    fun redactsInlineHeaderFormsInCommandsAndJson() {
        val raw = """
            yt-dlp --add-header "Cookie: SID=abc123; PREF=visible" --add-header 'Authorization: Bearer json-secret'
            {"headers":{"set-cookie":"SID=from-json","Authorization":"Bearer another-secret"}}
        """.trimIndent()

        val redacted = SensitiveText.redact(raw)

        listOf("abc123", "visible", "json-secret", "from-json", "another-secret").forEach {
            assertFalse("Should redact $it", redacted.contains(it))
        }
        assertTrue(redacted.contains("Cookie: [已隐藏]"))
        assertTrue(redacted.contains("Authorization: [已隐藏]"))
        assertTrue(redacted.contains("set-cookie\":\"[已隐藏]"))
    }

    @Test
    fun redactsSensitiveQueryParameters() {
        val raw = "https://example.com/watch?id=1&token=abc&access_token=def&auth=g&cookie=h&password=i&pass=j&session=k&sig=l&signature=m&secret=n&api_key=o&key=p&sid=q&jwt=r"

        val redacted = SensitiveText.redact(raw)

        listOf(
            "abc",
            "def",
            "auth=g",
            "cookie=h",
            "password=i",
            "pass=j",
            "session=k",
            "sig=l",
            "signature=m",
            "secret=n",
            "api_key=o",
            "key=p",
            "sid=q",
            "jwt=r",
        ).forEach {
            assertFalse("Should redact $it", redacted.contains(it))
        }
        assertTrue(redacted.contains("id=1"))
        assertTrue(redacted.contains("token=[已隐藏]"))
        assertTrue(redacted.contains("access_token=[已隐藏]"))
    }
}
