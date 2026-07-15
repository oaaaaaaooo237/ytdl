package com.garyapp.ytdl.core.policy

import java.net.URI
import java.security.MessageDigest

object UrlPolicy {
    private val allowedSchemes = setOf("http", "https")

    fun evaluate(userInput: String): UrlPolicyResult {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) {
            return UrlPolicyResult.rejected(
                blockReason = UrlPolicyBlockReason.BlankInput,
                userMessage = "请输入公开视频页面地址。",
                logSummary = UrlLogSummary(category = "blank_input"),
            )
        }

        val uri = trimmed.toUriOrNull() ?: return UrlPolicyResult.rejected(
            blockReason = UrlPolicyBlockReason.InvalidUrl,
            userMessage = "请输入有效的公开视频页面地址。",
            logSummary = UrlLogSummary(category = "invalid_url"),
        )
        val scheme = uri.scheme?.lowercase()
        if (scheme !in allowedSchemes) {
            return UrlPolicyResult.rejected(
                blockReason = UrlPolicyBlockReason.UnsupportedScheme,
                userMessage = "仅支持 http 或 https 开头的公开视频地址。",
                logSummary = UrlLogSummary(category = "unsupported_scheme"),
            )
        }

        val host = uri.host?.normalizeHost()
        if (host.isNullOrEmpty()) {
            return UrlPolicyResult.rejected(
                blockReason = UrlPolicyBlockReason.InvalidUrl,
                userMessage = "请输入有效的公开视频页面地址。",
                logSummary = UrlLogSummary(category = "invalid_url"),
            )
        }

        val hostHash = host.sha256Prefix()
        return UrlPolicyResult.allowed(
            rawUrlForExecution = trimmed,
            safeUrlSummary = SafeUrlSummary(
                scheme = scheme.orEmpty(),
                hostHash = hostHash,
            ),
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
}

data class UrlLogSummary(
    val category: String,
    val hostHash: String? = null,
)

data class SafeUrlSummary(
    val scheme: String,
    val hostHash: String,
)

data class UrlPolicyResult(
    val decision: UrlPolicyDecision,
    val rawUrlForExecution: String? = null,
    val safeUrlSummary: SafeUrlSummary? = null,
    val blockReason: UrlPolicyBlockReason? = null,
    val userMessage: String? = null,
    val logSummary: UrlLogSummary,
) {
    val isAllowed: Boolean
        get() = decision == UrlPolicyDecision.Allowed

    companion object {
        fun allowed(
            rawUrlForExecution: String,
            safeUrlSummary: SafeUrlSummary,
            logSummary: UrlLogSummary,
        ): UrlPolicyResult = UrlPolicyResult(
            decision = UrlPolicyDecision.Allowed,
            rawUrlForExecution = rawUrlForExecution,
            safeUrlSummary = safeUrlSummary,
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
