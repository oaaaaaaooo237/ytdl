package com.garyapp.ytdl.core.ytdlp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidMetadataHttpFallbackTest {
    @Test
    fun fetchesBoundedHttpsTextAndFiltersUnsafeRequestHeaders() {
        val connection = FakeConnection(
            url = URL("https://example.com/page"),
            body = "<html>ok</html>".toByteArray(),
            contentTypeValue = "text/html; charset=utf-8",
        )
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { connection },
            bodyEncoder = { "encoded" },
        )

        val result = JSONObject(
            fallback.fetch(
                "https://example.com/page",
                """{"User-Agent":"agent","Cookie":"age=1","Range":"bytes=0-9","Host":"bad"}""",
            ),
        )

        assertTrue(result.getBoolean("ok"))
        assertEquals("agent", connection.requestProperties["User-Agent"])
        assertEquals("age=1", connection.requestProperties["Cookie"])
        assertFalse(connection.requestProperties.containsKey("Range"))
        assertFalse(connection.requestProperties.containsKey("Host"))
    }

    @Test
    fun rejectsNonHttpsBinaryAndOversizedResponses() {
        var connectionCalls = 0
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { url ->
                connectionCalls += 1
                FakeConnection(url, byteArrayOf(1), "image/jpeg")
            },
        )

        assertFalse(JSONObject(fallback.fetch("http://example.com/page", "{}")).getBoolean("ok"))
        assertEquals(0, connectionCalls)
        assertFalse(JSONObject(fallback.fetch("https://example.com/image", "{}")).getBoolean("ok"))

        val oversized = AndroidMetadataHttpFallback(
            maxResponseBytes = 4,
            connectionFactory = { url ->
                FakeConnection(url, "12345".toByteArray(), "application/json")
            },
        )
        assertFalse(JSONObject(oversized.fetch("https://example.com/data", "{}")).getBoolean("ok"))
    }

    @Test
    fun rejectsHttpsToHttpRedirectWithoutForwardingRequestHeaders() {
        assertUnsafeRedirectRejected("http://redirect.example/page")
    }

    @Test
    fun rejectsRedirectWithUserInfoWithoutForwardingRequestHeaders() {
        assertUnsafeRedirectRejected("https://user:password@redirect.example/page")
    }

    @Test
    fun followsValidatedSameOriginRedirectAndPreservesAllowedHeaders() {
        val initialUrl = URL("https://example.com/page")
        val finalUrl = URL("https://example.com/final")
        val redirect = RedirectingFakeConnection(initialUrl, finalUrl)
        val finalConnection = FakeConnection(
            url = finalUrl,
            body = "<html>ok</html>".toByteArray(),
            contentTypeValue = "text/html",
        )
        val connectionUrls = mutableListOf<URL>()
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { url ->
                connectionUrls += url
                when (url) {
                    initialUrl -> redirect
                    finalUrl -> finalConnection
                    else -> error("unexpected URL")
                }
            },
        )

        val result = JSONObject(
            fallback.fetch(
                initialUrl.toString(),
                """{"User-Agent":"agent","Accept":"text/html","Accept-Language":"zh-CN","Cookie":"age=1","Referer":"https://example.com/ref","Origin":"https://example.com","Sec-Fetch-Mode":"navigate","Authorization":"Bearer secret"}""",
            ),
        )

        assertTrue(result.getBoolean("ok"))
        assertEquals(listOf(initialUrl, finalUrl), connectionUrls)
        assertEquals("agent", finalConnection.requestProperties["User-Agent"])
        assertEquals("text/html", finalConnection.requestProperties["Accept"])
        assertEquals("zh-CN", finalConnection.requestProperties["Accept-Language"])
        assertEquals("age=1", finalConnection.requestProperties["Cookie"])
        assertEquals("https://example.com/ref", finalConnection.requestProperties["Referer"])
        assertEquals("https://example.com", finalConnection.requestProperties["Origin"])
        assertEquals("navigate", finalConnection.requestProperties["Sec-Fetch-Mode"])
        assertFalse(finalConnection.requestProperties.containsKey("Authorization"))
    }

    @Test
    fun stripsSensitiveHeadersAfterCrossOriginRedirectAndDoesNotRestoreThem() {
        val initialUrl = URL("https://example.com/start")
        val crossOriginUrl = URL("https://redirect.example/step")
        val finalUrl = URL("https://redirect.example/final")
        val initialRedirect = RedirectingFakeConnection(initialUrl, crossOriginUrl)
        val sameOriginRedirect = RedirectingFakeConnection(crossOriginUrl, finalUrl)
        val finalConnection = FakeConnection(
            url = finalUrl,
            body = "<html>ok</html>".toByteArray(),
            contentTypeValue = "text/html",
        )
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { url ->
                when (url) {
                    initialUrl -> initialRedirect
                    crossOriginUrl -> sameOriginRedirect
                    finalUrl -> finalConnection
                    else -> error("unexpected URL")
                }
            },
        )

        val result = JSONObject(
            fallback.fetch(
                initialUrl.toString(),
                """{"User-Agent":"agent","Accept":"text/html","Accept-Language":"zh-CN","Cookie":"age=1","Referer":"https://example.com/ref","Origin":"https://example.com","Sec-Fetch-Mode":"navigate","Authorization":"Bearer secret"}""",
            ),
        )

        assertTrue(result.getBoolean("ok"))
        for (requestProperties in listOf(
            sameOriginRedirect.requestProperties,
            finalConnection.requestProperties,
        )) {
            assertEquals(
                setOf("Accept-Encoding", "User-Agent", "Accept", "Accept-Language", "Sec-Fetch-Mode"),
                requestProperties.keys,
            )
            assertEquals("agent", requestProperties["User-Agent"])
            assertEquals("text/html", requestProperties["Accept"])
            assertEquals("zh-CN", requestProperties["Accept-Language"])
            assertEquals("navigate", requestProperties["Sec-Fetch-Mode"])
            assertFalse(requestProperties.containsKey("Cookie"))
            assertFalse(requestProperties.containsKey("Referer"))
            assertFalse(requestProperties.containsKey("Origin"))
            assertFalse(requestProperties.containsKey("Authorization"))
        }
    }

    @Test
    fun rejectsRedirectsBeyondThreeHops() {
        val connectionUrls = mutableListOf<URL>()
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { url ->
                connectionUrls += url
                RedirectingFakeConnection(
                    url = url,
                    redirectUrl = URL("https://example.com/hop-${connectionUrls.size}"),
                )
            },
        )

        val result = JSONObject(fallback.fetch("https://example.com/start", "{}"))

        assertFalse(result.getBoolean("ok"))
        assertEquals(4, connectionUrls.size)
    }

    @Test
    fun rejectsUnapprovedTextContentTypes() {
        for (contentType in listOf("text/csv", "text/event-stream")) {
            val fallback = AndroidMetadataHttpFallback(
                connectionFactory = { url ->
                    FakeConnection(url, "payload".toByteArray(), contentType)
                },
            )

            assertFalse(
                contentType,
                JSONObject(fallback.fetch("https://example.com/data", "{}")).getBoolean("ok"),
            )
        }
    }

    @Test
    fun acceptsTextPlainOnlyAfterValidatedM3u8Redirect() {
        val initialUrl = URL("https://example.com/playlist")
        val finalUrl = URL("https://example.com/playlist.m3u8?token=value")
        val redirect = RedirectingFakeConnection(initialUrl, finalUrl)
        val finalConnection = FakeConnection(
            url = finalUrl,
            body = "#EXTM3U".toByteArray(),
            contentTypeValue = "text/plain; charset=utf-8",
        )
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { url ->
                when (url) {
                    initialUrl -> redirect
                    finalUrl -> finalConnection
                    else -> error("unexpected URL")
                }
            },
        )

        assertTrue(JSONObject(fallback.fetch(initialUrl.toString(), "{}")).getBoolean("ok"))
    }

    @Test
    fun rejectsTextPlainForNonM3u8Response() {
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { url ->
                FakeConnection(url, "plain".toByteArray(), "text/plain")
            },
        )

        assertFalse(
            JSONObject(fallback.fetch("https://example.com/page", "{}")).getBoolean("ok"),
        )
    }

    private fun assertUnsafeRedirectRejected(redirectUrl: String) {
        val connection = RedirectingFakeConnection(
            url = URL("https://example.com/page"),
            redirectUrl = URL(redirectUrl),
        )
        val connectionUrls = mutableListOf<URL>()
        val fallback = AndroidMetadataHttpFallback(
            connectionFactory = { url ->
                connectionUrls += url
                connection
            },
        )

        val result = JSONObject(
            fallback.fetch(
                "https://example.com/page",
                """{"User-Agent":"agent","Cookie":"age=1"}""",
            ),
        )

        assertFalse(result.getBoolean("ok"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.forwardedRequestProperties.isEmpty())
        assertEquals(listOf(URL("https://example.com/page")), connectionUrls)
    }
}

private class FakeConnection(
    url: URL,
    private val body: ByteArray,
    private val contentTypeValue: String,
) : HttpURLConnection(url) {
    val requestProperties = linkedMapOf<String, String>()

    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun connect() = Unit
    override fun getResponseCode(): Int = 200
    override fun getContentType(): String = contentTypeValue
    override fun getContentLengthLong(): Long = body.size.toLong()
    override fun getInputStream() = ByteArrayInputStream(body)
    override fun getHeaderFields(): Map<String?, List<String>> = mapOf(
        "Content-Type" to listOf(contentTypeValue),
    )

    override fun setRequestProperty(key: String, value: String) {
        requestProperties[key] = value
    }
}

private class RedirectingFakeConnection(
    url: URL,
    private val redirectUrl: URL,
) : HttpURLConnection(url) {
    val requestProperties = linkedMapOf<String, String>()
    val forwardedRequestProperties = linkedMapOf<String, String>()

    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun connect() = Unit

    override fun getResponseCode(): Int {
        if (instanceFollowRedirects) {
            forwardedRequestProperties.putAll(requestProperties)
            url = redirectUrl
            return 200
        }
        return HTTP_MOVED_TEMP
    }

    override fun getContentType(): String = "text/html"
    override fun getContentLengthLong(): Long = 2
    override fun getInputStream() = ByteArrayInputStream("ok".toByteArray())
    override fun getHeaderFields(): Map<String?, List<String>> = mapOf(
        "Content-Type" to listOf("text/html"),
        "Location" to listOf(redirectUrl.toString()),
    )

    override fun getHeaderField(name: String?): String? {
        return if (name.equals("Location", ignoreCase = true)) redirectUrl.toString() else null
    }

    override fun setRequestProperty(key: String, value: String) {
        requestProperties[key] = value
    }
}
