package com.garyapp.ytdl.core.settings

import com.garyapp.ytdl.core.storage.StorageTarget

data class AppSettings(
    val defaultStorageTarget: StorageTarget = StorageTarget.AppPrivate,
    val cookiesReference: CookiesReference? = null,
)

class CookiesReference private constructor(
    val value: String,
    val displayName: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CookiesReference) return false

        return value == other.value && displayName == other.displayName
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "CookiesReference(displayName=$displayName, kind=${referenceKind(value)})"
    }

    companion object {
        private val sensitiveHeaderPattern =
            Regex("""(?i)\b(cookie|cookies|set-cookie|authorization)\s*:""")

        private val sensitiveCookieNamePattern =
            Regex("""(?i)(^|[;\s])(sid|pref|session|cookie|cookies|auth|authorization|token|jwt|xsrf|csrf)[a-z0-9_-]*\s*=""")

        private val repeatedCookiePairPattern =
            Regex("""(?i)[a-z0-9!#$%&'*+.^_`|~-]{2,}\s*=[^;=\r\n]+;\s*[a-z0-9!#$%&'*+.^_`|~-]{2,}\s*=""")

        private val contentOrFileUriPattern =
            Regex("""(?i)^(content|file)://\S+$""")

        private val androidAbsolutePathPattern =
            Regex("""^/(?!/).+\S$""")

        private val windowsAbsolutePathPattern =
            Regex("""^[A-Za-z]:[\\/].+\S$""")

        fun fromUserReference(reference: String, displayName: String? = null): CookiesReference? {
            val trimmedReference = reference.trim()
            if (trimmedReference.isEmpty()) return null
            if (looksLikeCookieContent(trimmedReference)) return null
            if (!looksLikeUserSelectedReference(trimmedReference)) return null

            return CookiesReference(
                value = trimmedReference,
                displayName = sanitizeDisplayName(displayName),
            )
        }

        private fun looksLikeCookieContent(reference: String): Boolean {
            return sensitiveHeaderPattern.containsMatchIn(reference) ||
                sensitiveCookieNamePattern.containsMatchIn(reference) ||
                reference.contains('\n') ||
                reference.contains('\r') ||
                reference.contains('\t') ||
                repeatedCookiePairPattern.containsMatchIn(reference)
        }

        private fun looksLikeUserSelectedReference(reference: String): Boolean {
            return contentOrFileUriPattern.matches(reference) ||
                androidAbsolutePathPattern.matches(reference) ||
                windowsAbsolutePathPattern.matches(reference)
        }

        private fun referenceKind(reference: String): String {
            return when {
                contentOrFileUriPattern.matches(reference) -> "uri"
                androidAbsolutePathPattern.matches(reference) -> "path"
                windowsAbsolutePathPattern.matches(reference) -> "path"
                else -> "unknown"
            }
        }

        private fun sanitizeDisplayName(displayName: String?): String? {
            val trimmedDisplayName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (looksLikeCookieContent(trimmedDisplayName)) return "cookies 文件"
            if (looksLikeUserSelectedReference(trimmedDisplayName)) return "cookies 文件"

            return trimmedDisplayName
        }
    }
}
