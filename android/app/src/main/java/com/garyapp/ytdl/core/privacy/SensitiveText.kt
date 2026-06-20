package com.garyapp.ytdl.core.privacy

object SensitiveText {
    private const val Hidden = "[已隐藏]"

    private val sensitiveHeaderPattern =
        Regex("""(?im)^(\s*)(cookie|cookies|set-cookie|authorization)\s*:\s*[^\r\n]*""")

    private val inlineSensitiveHeaderPattern =
        Regex("""(?i)\b(cookie|cookies|set-cookie|authorization)\s*:\s*[^"'\r\n,}]+""")

    private val jsonSensitiveHeaderPattern =
        Regex("""(?i)(["'])(cookie|cookies|set-cookie|authorization)\1\s*:\s*(["'])[^"'\r\n]*\3""")

    private val bearerPattern =
        Regex("""(?i)\b(bearer)\s+[^\s,;]+""")

    private val cookiesArgumentPattern =
        Regex("""(?i)--cookies(?:=|\s+)(?:"[^"]*"|'[^']*'|\S+)""")

    private val cookiesFromBrowserArgumentPattern =
        Regex("""(?i)--cookies-from-browser(?:=|\s+)(?:"[^"]*"|'[^']*'|\S+)""")

    private val sensitiveQueryPattern =
        Regex("""(?i)\b(access_token|token|auth|cookie|cookies|password|pass|session|sig|signature|secret|api_key|key|sid|jwt)=([^&#\s]+)""")

    fun redact(text: String): String {
        if (text.isEmpty()) return text

        return text
            .replace(jsonSensitiveHeaderPattern) { match ->
                val keyQuote = match.groupValues[1]
                val headerName = match.groupValues[2]
                val valueQuote = match.groupValues[3]
                "$keyQuote$headerName$keyQuote:$valueQuote$Hidden$valueQuote"
            }
            .replace(sensitiveHeaderPattern) { match ->
                val indent = match.groupValues[1]
                val headerName = match.groupValues[2]
                "$indent$headerName: $Hidden"
            }
            .replace(inlineSensitiveHeaderPattern) { match ->
                "${match.groupValues[1]}: $Hidden"
            }
            .replace(bearerPattern) { match ->
                "${match.groupValues[1]} $Hidden"
            }
            .replace(cookiesFromBrowserArgumentPattern) {
                "--cookies-from-browser $Hidden"
            }
            .replace(cookiesArgumentPattern) {
                "--cookies $Hidden"
            }
            .replace(sensitiveQueryPattern) { match ->
                "${match.groupValues[1]}=$Hidden"
            }
    }
}
