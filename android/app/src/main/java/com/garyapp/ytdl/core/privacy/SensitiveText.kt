package com.garyapp.ytdl.core.privacy

object SensitiveText {
    private const val Hidden = "[已隐藏]"

    private val sensitiveHeaderPattern =
        Regex("""(?im)^(\s*)(cookie|cookies|set-cookie|authorization)\s*:\s*[^\r\n]*""")

    private val bearerPattern =
        Regex("""(?i)\b(bearer)\s+[^\s,;]+""")

    private val sensitiveQueryPattern =
        Regex("""(?i)\b(access_token|token|auth|cookie|password|pass|session)=([^&#\s]+)""")

    fun redact(text: String): String {
        if (text.isEmpty()) return text

        return text
            .replace(sensitiveHeaderPattern) { match ->
                val indent = match.groupValues[1]
                val headerName = match.groupValues[2]
                "$indent$headerName: $Hidden"
            }
            .replace(bearerPattern) { match ->
                "${match.groupValues[1]} $Hidden"
            }
            .replace(sensitiveQueryPattern) { match ->
                "${match.groupValues[1]}=$Hidden"
            }
    }
}
