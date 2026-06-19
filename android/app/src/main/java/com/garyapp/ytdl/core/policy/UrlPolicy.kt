package com.garyapp.ytdl.core.policy

import java.net.URI
import java.security.MessageDigest

object UrlPolicy {
    private val allowedSchemes = setOf("http", "https")

    private val adultDomains = setOf(
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "redtube.com",
        "youporn.com",
        "onlyfans.com",
    )

    fun evaluate(userInput: String): UrlPolicyResult {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) {
            return UrlPolicyResult.rejected(
                blockReason = UrlPolicyBlockReason.BlankInput,
                userMessage = "请输入公开视频页面地址。",
                logSummary = UrlLogSummary(category = "blank_input"),
            )
        }

        val uri = trimmed.toUriOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (scheme !in allowedSchemes) {
            return UrlPolicyResult.rejected(
                blockReason = UrlPolicyBlockReason.UnsupportedScheme,
                userMessage = "仅支持 http 或 https 开头的公开视频地址。",
                logSummary = UrlLogSummary(category = "unsupported_scheme"),
            )
        }

        val parsedUri = uri ?: return UrlPolicyResult.rejected(
            blockReason = UrlPolicyBlockReason.InvalidUrl,
            userMessage = "请输入有效的公开视频页面地址。",
            logSummary = UrlLogSummary(category = "invalid_url"),
        )
        val host = parsedUri.host?.normalizeHost()
        if (host.isNullOrEmpty()) {
            return UrlPolicyResult.rejected(
                blockReason = UrlPolicyBlockReason.InvalidUrl,
                userMessage = "请输入有效的公开视频页面地址。",
                logSummary = UrlLogSummary(category = "invalid_url"),
            )
        }

        val hostHash = host.sha256Prefix()
        if (adultDomains.any { host == it || host.endsWith(".$it") }) {
            return UrlPolicyResult.rejected(
                blockReason = UrlPolicyBlockReason.AdultDomain,
                userMessage = "此地址不适合 Google Play 版使用，请更换已授权的公开视频地址。",
                logSummary = UrlLogSummary(
                    category = "blocked_adult_domain",
                    hostHash = hostHash,
                ),
            )
        }

        return UrlPolicyResult.allowed(
            normalizedUrl = trimmed,
            logSummary = UrlLogSummary(
                category = "allowed",
                hostHash = hostHash,
            ),
        )
    }

    private fun String.toUriOrNull(): URI? = runCatching { URI(this) }.getOrNull()

    private fun String.normalizeHost(): String = trim().trimEnd('.').lowercase()

    private fun String.sha256Prefix(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString(separator = "") { "%02x".format(it) }
    }
}

enum class UrlPolicyDecision {
    Allowed,
    Rejected,
}

enum class UrlPolicyBlockReason {
    BlankInput,
    UnsupportedScheme,
    InvalidUrl,
    AdultDomain,
}

data class UrlLogSummary(
    val category: String,
    val hostHash: String? = null,
)

data class UrlPolicyResult(
    val decision: UrlPolicyDecision,
    val normalizedUrl: String? = null,
    val blockReason: UrlPolicyBlockReason? = null,
    val userMessage: String? = null,
    val logSummary: UrlLogSummary,
) {
    val isAllowed: Boolean
        get() = decision == UrlPolicyDecision.Allowed

    companion object {
        fun allowed(
            normalizedUrl: String,
            logSummary: UrlLogSummary,
        ): UrlPolicyResult = UrlPolicyResult(
            decision = UrlPolicyDecision.Allowed,
            normalizedUrl = normalizedUrl,
            logSummary = logSummary,
        )

        fun rejected(
            blockReason: UrlPolicyBlockReason,
            userMessage: String,
            logSummary: UrlLogSummary,
        ): UrlPolicyResult = UrlPolicyResult(
            decision = UrlPolicyDecision.Rejected,
            blockReason = blockReason,
            userMessage = userMessage,
            logSummary = logSummary,
        )
    }
}
