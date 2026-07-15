package com.garyapp.ytdl.core.ytdlp

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class AndroidMetadataHttpFallback(
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
    private val bodyEncoder: (ByteArray) -> String = { body ->
        Base64.encodeToString(body, Base64.NO_WRAP)
    },
) {
    @Suppress("unused")
    fun fetch(url: String, headersJson: String): String {
        var currentUrl = validatedInitialHttpsUrl(url)
            ?: return failureJson()
        var redirectCount = 0
        var includeSensitiveHeaders = true

        while (true) {
            val connection = try {
                connectionFactory(currentUrl)
            } catch (_: Exception) {
                return failureJson()
            }
            try {
                val status = try {
                    configure(connection, headersJson, includeSensitiveHeaders)
                    connection.responseCode
                } catch (_: Exception) {
                    return failureJson()
                }
                if (status in REDIRECT_STATUS_CODES) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        return failureJson()
                    }
                    val location = try {
                        connection.getHeaderField("Location").orEmpty()
                    } catch (_: Exception) {
                        return failureJson()
                    }
                    val redirectUrl = validatedRedirectUrl(location, currentUrl) ?: return failureJson()
                    val staysOnCurrentOrigin = hasSameOrigin(currentUrl, redirectUrl)
                    includeSensitiveHeaders = includeSensitiveHeaders && staysOnCurrentOrigin
                    currentUrl = redirectUrl
                    redirectCount += 1
                    continue
                }

                if (status !in 200..299) {
                    return failureJson()
                }
                val contentType: String
                val contentLength: Long
                try {
                    contentType = connection.contentType.orEmpty()
                    contentLength = connection.contentLengthLong
                } catch (_: Exception) {
                    return failureJson()
                }
                if (isRejectedMime(contentType, currentUrl)) {
                    return failureJson()
                }
                if (contentLength > maxResponseBytes) {
                    return failureJson()
                }

                val body = try {
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxResponseBytes) {
                                return failureJson()
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                } catch (_: Exception) {
                    return failureJson()
                }

                val result = try {
                    JSONObject()
                        .put("ok", true)
                        .put("status", status)
                        .put("url", connection.url.toString())
                        .put("headers", responseHeaders(connection))
                        .put("bodyBase64", bodyEncoder(body))
                        .toString()
                } catch (_: Exception) {
                    return failureJson()
                }
                return result
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun validatedInitialHttpsUrl(rawUrl: String): URL? {
        return try {
            val uri = URI(rawUrl)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() || uri.rawUserInfo != null) {
                null
            } else {
                uri.toURL()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun validatedRedirectUrl(rawUrl: String, baseUrl: URL): URL? {
        val uri = try {
            baseUrl.toURI().resolve(rawUrl)
        } catch (_: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return null
        }
        if (uri.rawUserInfo != null) {
            return null
        }
        if (uri.host.isNullOrBlank()) {
            return null
        }
        return try {
            uri.toURL()
        } catch (_: Exception) {
            null
        }
    }

    private fun configure(
        connection: HttpURLConnection,
        headersJson: String,
        includeSensitiveHeaders: Boolean,
    ) {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        connection.setRequestProperty("Accept-Encoding", "identity")

        val headers = runCatching { JSONObject(headersJson) }.getOrElse { JSONObject() }
        val allowedRequestHeaders = if (includeSensitiveHeaders) {
            ALLOWED_REQUEST_HEADERS
        } else {
            CROSS_ORIGIN_REQUEST_HEADERS
        }
        headers.keys().forEach { name ->
            val canonicalName = allowedRequestHeaders[name.lowercase()] ?: return@forEach
            val value = headers.optString(name).trim()
            if (value.isNotEmpty()) connection.setRequestProperty(canonicalName, value)
        }
    }

    private fun hasSameOrigin(first: URL, second: URL): Boolean {
        return first.protocol.equals(second.protocol, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)
    }

    private fun effectivePort(url: URL): Int = if (url.port == -1) url.defaultPort else url.port

    private fun responseHeaders(connection: HttpURLConnection): JSONObject {
        val result = JSONObject()
        connection.headerFields.forEach { (name, values) ->
            if (!name.isNullOrBlank() && !values.isNullOrEmpty()) {
                result.put(name, values.joinToString(", "))
            }
        }
        return result
    }

    private fun isRejectedMime(rawContentType: String, responseUrl: URL): Boolean {
        val contentType = rawContentType.substringBefore(';').trim().lowercase()
        val allowed = contentType == "text/html" ||
            contentType == "text/xml" ||
            (contentType == "text/plain" && responseUrl.path.endsWith(".m3u8", ignoreCase = true)) ||
            contentType == "application/json" ||
            contentType.endsWith("+json") ||
            contentType == "application/xml" ||
            contentType.endsWith("+xml") ||
            contentType in M3U8_CONTENT_TYPES
        return !allowed
    }

    private fun failureJson(): String = JSONObject().put("ok", false).toString()

    companion object {
        private const val DEFAULT_MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REDIRECTS = 3

        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

        private val ALLOWED_REQUEST_HEADERS = mapOf(
            "accept" to "Accept",
            "accept-language" to "Accept-Language",
            "cookie" to "Cookie",
            "referer" to "Referer",
            "origin" to "Origin",
            "sec-fetch-mode" to "Sec-Fetch-Mode",
        )

        private val CROSS_ORIGIN_REQUEST_HEADERS = mapOf(
            "accept" to "Accept",
            "accept-language" to "Accept-Language",
            "sec-fetch-mode" to "Sec-Fetch-Mode",
        )

        private val M3U8_CONTENT_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )
    }
}
