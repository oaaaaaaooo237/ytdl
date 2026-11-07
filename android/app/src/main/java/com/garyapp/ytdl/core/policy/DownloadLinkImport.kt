package com.garyapp.ytdl.core.policy

import java.io.Reader

internal data class DownloadLinkBatch(
    val urls: List<String>,
    val duplicateCount: Int,
    val invalidCount: Int,
)

internal object DownloadLinkImport {
    private const val MaxCharacters = 256 * 1024
    private const val MaxLinks = 500
    private val MarkdownLink = Regex("""\[[^\]]*\]\(<?((?:https?://|magnet:\?)[^\s<>]+?)>?(?:\s+"[^"]*")?\)|<((?:https?://|magnet:\?)[^<>\s]+)>""")
    private val ListPrefix = Regex("""^(?:[-*+]\s+(?:\[[ xX]\]\s+)?|\d+[.)]\s+)""")

    fun read(reader: Reader): DownloadLinkBatch {
        val text = StringBuilder()
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(text.length + count <= MaxCharacters)
            text.append(buffer, 0, count)
        }

        val urls = linkedSetOf<String>()
        var duplicates = 0
        var invalid = 0
        text.toString().removePrefix("\uFEFF").lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val trimmed = line.trim()
            val direct = downloadAddress(trimmed)
            val links = if (direct != null) listOf(direct) else {
                val markdown = MarkdownLink.findAll(trimmed)
                    .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
                if (markdown.isNotEmpty()) markdown else {
                    val item = trimmed.replaceFirst(ListPrefix, "").trim().removeSurrounding("<", ">")
                    listOfNotNull(downloadAddress(item))
                }
            }
            if (links.isEmpty() && !trimmed.startsWith("#") && trimmed !in listOf("```", "~~~")) invalid++
            links.forEach { candidate ->
                val url = downloadAddress(candidate)
                when {
                    url == null -> invalid++
                    !urls.add(url) -> duplicates++
                    else -> require(urls.size <= MaxLinks)
                }
            }
        }
        return DownloadLinkBatch(urls.toList(), duplicates, invalid)
    }
}

internal fun sharedDownloadUrl(text: CharSequence?): String? {
    if (text == null || text.length > 16_384) return null
    return text.trim().split(Regex("\\s+")).asSequence()
        .mapNotNull(::downloadAddress)
        .distinct()
        .take(2)
        .toList()
        .singleOrNull()
}

internal fun downloadAddress(value: String): String? = value.trim().let { text ->
    UrlPolicy.evaluate(text).rawUrlForExecution ?: text.takeIf(::isMagnetDownloadAddress)
}

internal fun isMagnetDownloadAddress(link: String): Boolean {
    if (!link.startsWith("magnet:?") || link.length > 16_384) return false
    return link.substringAfter('?').split('&').any { parameter ->
        if (parameter.substringBefore('=') != "xt") return@any false
        val topic = runCatching { java.net.URLDecoder.decode(parameter.substringAfter('=', ""), "UTF-8") }
            .getOrNull() ?: return@any false
        MagnetExactTopic.matches(topic)
    }
}

private val MagnetExactTopic = Regex("urn:(?:btih:(?:[0-9a-f]{40}|[a-z2-7]{32})|btmh:1220[0-9a-f]{64})", RegexOption.IGNORE_CASE)
