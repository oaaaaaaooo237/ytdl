package com.garyapp.ytdl.core.files

internal object HttpDownloadRanges {
    private const val MinimumPartBytes = 4L * 1024 * 1024
    private const val MaximumParts = 16

    fun plan(totalBytes: Long, maxConnections: Int): List<LongRange> {
        require(totalBytes > 0)
        require(maxConnections in 1..MaximumParts)
        val count = (totalBytes / MinimumPartBytes).coerceIn(1, maxConnections.toLong()).toInt()
        val partSize = totalBytes / count
        val remainder = totalBytes % count
        var start = 0L
        return List(count) { index ->
            val end = start + partSize + if (index < remainder) 1 else 0
            (start until end).also { start = end }
        }
    }
}
