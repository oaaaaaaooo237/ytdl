package com.garyapp.ytdl.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test
    fun rejectsBlankInputWithChineseMessage() {
        val result = UrlPolicy.evaluate("   ")

        assertFalse(result.isAllowed)
        assertEquals(UrlPolicyBlockReason.BlankInput, result.blockReason)
        assertEquals("请输入公开视频页面地址。", result.userMessage)
    }

    @Test
    fun rejectsUnsupportedSchemes() {
        val result = UrlPolicy.evaluate("ftp://example.com/video")

        assertFalse(result.isAllowed)
        assertEquals(UrlPolicyBlockReason.UnsupportedScheme, result.blockReason)
        assertEquals("仅支持 http 或 https 开头的公开视频地址。", result.userMessage)
    }

    @Test
    fun rejectsMalformedUrlsAsInvalidUrl() {
        val result = UrlPolicy.evaluate("https://exa mple.com/watch")

        assertFalse(result.isAllowed)
        assertEquals(UrlPolicyBlockReason.InvalidUrl, result.blockReason)
        assertEquals("请输入有效的公开视频页面地址。", result.userMessage)
        assertEquals("invalid_url", result.logSummary.category)
    }

    @Test
    fun allowsHttpHostsForExtractorHandling() {
        val result = UrlPolicy.evaluate("https://video.example.com/watch?v=private&token=secret")

        assertTrue(result.isAllowed)
        assertEquals("allowed", result.logSummary.category)
        assertEquals("https://video.example.com/watch?v=private&token=secret", result.rawUrlForExecution)
        assertFalse(result.logSummary.toString().contains("example.com", ignoreCase = true))
        assertFalse(result.logSummary.toString().contains("token=secret", ignoreCase = true))
    }

    @Test
    fun doesNotHardBlockDomainsBeyondBasicUrlValidation() {
        val urls = listOf(
            "https://example.com/view",
            "https://m.example.net/view",
            "https://cdn.example.org/view",
            "http://localhost:8080/view",
            "https://sub.domain.example/view",
        )

        urls.forEach { url ->
            val result = UrlPolicy.evaluate(url)

            assertTrue("Expected policy to allow http(s) URL for extractor handling: $url", result.isAllowed)
            assertEquals("allowed", result.logSummary.category)
        }
    }

    @Test
    fun allowsHttpAndHttpsWithSafeLogSummary() {
        val result = UrlPolicy.evaluate("https://Example.com/watch?v=public&token=secret")

        assertTrue(result.isAllowed)
        assertEquals("https://Example.com/watch?v=public&token=secret", result.rawUrlForExecution)
        assertEquals("allowed", result.logSummary.category)
        assertNotNull(result.logSummary.hostHash)
        assertFalse(result.logSummary.toString().contains("token=secret", ignoreCase = true))
        assertNotNull(result.safeUrlSummary)
        assertFalse(result.safeUrlSummary.toString().contains("token=secret", ignoreCase = true))
        assertFalse(result.safeUrlSummary.toString().contains("v=public", ignoreCase = true))
    }
}
