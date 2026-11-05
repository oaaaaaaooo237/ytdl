package com.garyapp.ytdl.core.files

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HttpFileProbe {
    fun probe(sourceUrl: String): FileProbeResult = try {
        val connection = openFileConnection(sourceUrl)
        try {
            if (connection.responseCode != 200) FileProbeResult.Rejected("服务器拒绝请求（${connection.responseCode}）")
            else if (!identityEncoding(connection)) FileProbeResult.Rejected("服务器返回了不支持的内容编码")
            else {
                val prefix = connection.inputStream.use { readPrefix(it) }
                val mime = connection.getHeaderField("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                if (isWebPage(mime, prefix)) FileProbeResult.NotFile("链接返回网页，请使用媒体解析")
                else if (mime == null && connection.getHeaderField("Content-Disposition") == null) FileProbeResult.NotFile("服务器未提供文件类型")
                else {
                    val etag = strongEtag(connection.getHeaderField("ETag"))
                    val modified = reliableLastModified(connection)
                    val name = fileName(connection.getHeaderField("Content-Disposition"), connection.url.toString())
                    FileProbeResult.FileFound(FileMetadata(sourceUrl, connection.url.toString(), name, mime,
                        contentLength(connection), etag, modified, classify(mime, name),
                        if (etag != null || modified != null) ResumeSupport.Unknown else ResumeSupport.Unavailable))
                }
            }
        } finally { connection.disconnect() }
    } catch (_: Exception) { FileProbeResult.Rejected("无法读取文件信息") }

    companion object {
        fun fileName(disposition: String?, finalUrl: String): String {
            val extended = disposition?.let { Regex("(?:^|;)\\s*filename\\*\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))", RegexOption.IGNORE_CASE).find(it) }
            val encoded = extended?.let { it.groups[1]?.value ?: it.groups[2]?.value }?.trim()
            val decoded = encoded?.let {
                val parts = it.split('\'', limit = 3)
                if (parts.size == 3 && parts[0].equals("UTF-8", true)) runCatching { URLDecoder.decode(parts[2].replace("+", "%2B"), "UTF-8") }.getOrNull() else null
            }
            val plain = disposition?.let { Regex("(?:^|;)\\s*filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))", RegexOption.IGNORE_CASE).find(it) }
                ?.let { it.groups[1]?.value ?: it.groups[2]?.value }?.trim()
            val path = runCatching { URL(finalUrl).path.substringAfterLast('/').let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") } }.getOrNull()
            val raw = (decoded ?: plain ?: path ?: "下载文件").replace('\\', '/').substringAfterLast('/')
            var safe = raw.map { if (it.code < 32 || it.code == 127 || it in "<>:\"|?*") '_' else it }.joinToString("").trim().trimEnd('.', ' ')
            if (safe.isBlank() || safe == "." || safe == "..") safe = "下载文件"
            if (Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$").matches(safe)) safe = "_$safe"
            return safe.take(180)
        }
        private fun classify(mime: String?, name: String): FileKind = when {
            mime?.startsWith("video/") == true -> FileKind.Video
            mime?.startsWith("audio/") == true -> FileKind.Audio
            mime == "application/octet-stream" || mime == null -> when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "mp4", "mkv", "webm", "mov", "avi" -> FileKind.Video
                "mp3", "m4a", "flac", "wav", "ogg", "aac" -> FileKind.Audio
                else -> FileKind.Other
            }
            else -> FileKind.Other
        }
    }
}

internal fun openFileConnection(source: String, range: Long? = null, validator: String? = null): HttpURLConnection {
    var current = URL(source)
    repeat(6) { attempt ->
        require(current.protocol == "http" || current.protocol == "https")
        require(current.userInfo == null)
        val connection = current.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10000
        connection.readTimeout = 3000
        connection.setRequestProperty("Accept-Encoding", "identity")
        if (range != null) {
            connection.setRequestProperty("Range", "bytes=$range-")
            connection.setRequestProperty("If-Range", validator)
        }
        try {
            if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
            val next = URL(current, connection.getHeaderField("Location") ?: error("缺少重定向地址"))
            require(!(current.protocol == "https" && next.protocol == "http"))
            require(attempt < 5)
            current = next
        } catch (e: Exception) { connection.disconnect(); throw e }
        connection.disconnect()
    }
    error("重定向过多")
}
internal fun identityEncoding(c: HttpURLConnection): Boolean = c.getHeaderField("Content-Encoding").let { it == null || it.equals("identity", true) }
internal fun contentLength(c: HttpURLConnection): Long? = c.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 }
internal fun strongEtag(value: String?): String? = value?.trim()?.takeIf { it.startsWith('"') && it.endsWith('"') && it.length >= 2 && it.substring(1, it.lastIndex).all { character -> character.code == 0x21 || character.code in 0x23..0x7e || character.code in 0x80..0xff } }
internal fun validLastModified(value: String?): String? = value?.takeIf {
    val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
    format.isLenient = false
    format.timeZone = TimeZone.getTimeZone("GMT")
    runCatching { format.parse(it)?.let { parsed -> format.format(parsed) == it } == true }.getOrDefault(false)
}
internal fun isWebPage(mime: String?, prefix: ByteArray): Boolean {
    if (mime == "text/html" || mime == "application/xhtml+xml") return true
    val text = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t').lowercase(Locale.ROOT)
    return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head") || text.startsWith("<body")
}


/** RFC 9110: a client may use a date as a strong validator only with a 60-second margin. */
internal fun reliableLastModified(c: HttpURLConnection): String? {
    val modified = validLastModified(c.getHeaderField("Last-Modified")) ?: return null
    val date = validLastModified(c.getHeaderField("Date")) ?: return null
    val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("GMT")
    return modified.takeIf { format.parse(date)!!.time - format.parse(modified)!!.time >= 60000 }
}
internal fun readPrefix(input: InputStream): ByteArray {
    val prefix = ByteArray(512)
    var size = 0
    while (size < prefix.size) {
        val count = input.read(prefix, size, prefix.size - size)
        if (count < 0) break
        if (count > 0) size += count
    }
    return prefix.copyOf(size)
}
