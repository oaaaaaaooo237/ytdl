package com.garyapp.ytdl.storage

internal object ExternalDownloadProbe {
    data class ExportVariant(
        val displayName: String,
        val bytes: Long,
    )

    fun findUniqueExportVariant(
        listing: String,
        exportPrefix: String,
    ): ExportVariant? {
        val matches = listing
            .lineSequence()
            .mapNotNull { line -> parseLsLine(line) }
            .filter { variant ->
                variant.displayName == "$exportPrefix.mp4" ||
                    (variant.displayName.startsWith("$exportPrefix ") && variant.displayName.endsWith(".mp4"))
            }
            .toList()
        return matches.singleOrNull()
    }

    fun findExactExportFile(
        listing: String,
        expectedDisplayName: String,
    ): ExportVariant? {
        return listing
            .lineSequence()
            .mapNotNull { line -> parseLsLine(line) }
            .firstOrNull { variant -> variant.displayName == expectedDisplayName }
    }

    private fun parseLsLine(line: String): ExportVariant? {
        val parts = line.trim().split(Regex("\\s+"), limit = 8)
        if (parts.size < 8) return null
        val bytes = parts[4].toLongOrNull() ?: return null
        val displayName = parts[7].trim()
        if (displayName.isBlank()) return null
        return ExportVariant(displayName, bytes)
    }
}
