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
    fun blocksAdultDomainsAndSubdomainsWithoutEchoingUrl() {
        val result = UrlPolicy.evaluate("https://video.pornhub.com/watch?v=private&token=secret")

        assertFalse(result.isAllowed)
        assertEquals(UrlPolicyBlockReason.AdultDomain, result.blockReason)
        assertEquals("此地址不适合 Google Play 版使用，请更换已授权的公开视频地址。", result.userMessage)
        assertFalse(result.userMessage.orEmpty().contains("pornhub", ignoreCase = true))
        assertFalse(result.userMessage.orEmpty().contains("secret", ignoreCase = true))
        assertEquals("blocked_adult_domain", result.logSummary.category)
        assertNotNull(result.logSummary.hostHash)
        assertFalse(result.logSummary.toString().contains("pornhub", ignoreCase = true))
        assertFalse(result.logSummary.toString().contains("token=secret", ignoreCase = true))
    }

    @Test
    fun blocksEveryConfiguredAdultDomainIncludingSubdomains() {
        val blockedUrls = listOf(
            "https://pornhub.com/view",
            "https://m.xvideos.com/view",
            "https://cdn.xnxx.com/view",
            "https://watch.xhamster.com/view",
            "https://redtube.com/view",
            "https://video.youporn.com/view",
            "https://onlyfans.com/example",
        )

        blockedUrls.forEach { url ->
            val result = UrlPolicy.evaluate(url)

            assertFalse("Expected blocked: $url", result.isAllowed)
            assertEquals("Expected adult block: $url", UrlPolicyBlockReason.AdultDomain, result.blockReason)
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
