package com.garyapp.ytdl.core.settings

import java.net.IDN
import java.net.URI
import java.util.Locale

data class SiteCookies(val name: String, val domain: String, val reference: CookiesReference?)

object CookieSites {
    fun normalizeDomain(value: String): String? {
        val raw = value.trim().lowercase(Locale.ROOT).removeSuffix(".")
        if (raw.any { it in "/:@?#*\\" } || !raw.contains('.')) return null
        val ascii = runCatching { IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES) }.getOrNull() ?: return null
        if (ascii.length > 253 || ascii.split('.').any { it.isEmpty() || it.length > 63 }) return null
        return when (ascii) {
            "twitter.com", "www.twitter.com", "www.x.com" -> "x.com"
            "youtu.be", "www.youtube.com" -> "youtube.com"
            else -> ascii
        }
    }

    fun hostForUrl(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http")) return null
        val host = normalizeDomain(uri.host.orEmpty()) ?: return null
        return when {
            host.endsWith(".twitter.com") -> "x.com"
            else -> host
        }
    }

    fun matches(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")
}

fun AppSettings.cookiesForUrl(url: String): CookiesReference? {
    val host = CookieSites.hostForUrl(url) ?: return null
    val site = siteCookies.values.filter { CookieSites.matches(host, it.domain) }.maxByOrNull { it.domain.length }
    return if (site != null) site.reference else cookiesReference
}
