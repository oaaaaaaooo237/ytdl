package com.garyapp.ytdl.core.files

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpDownloadRangesTest {
    @Test fun connectionLimitControlsActualPartCount() {
        val total = 64L * 1024 * 1024
        for (limit in listOf(1, 2, 4, 8, 16)) assertEquals(limit, HttpDownloadRanges.plan(total, limit).size)
        assertEquals(2, HttpDownloadRanges.plan(8L * 1024 * 1024, 16).size)
    }

    @Test
    fun smallFilesUseOneConnection() {
        assertEquals(listOf(0L..1023L), HttpDownloadRanges.plan(1024, 16))
    }

    @Test
    fun partitionsCoverTheFileWithoutGapsOrOverlap() {
        for (size in listOf(12_000_001L, 123_456_789L, Long.MAX_VALUE)) {
            val parts = HttpDownloadRanges.plan(size, 16)
            assertEquals(0L, parts.first().first)
            assertEquals(size - 1, parts.last().last)
            parts.zipWithNext().forEach { (left, right) -> assertEquals(left.last + 1, right.first) }
        }
        assertEquals(16, HttpDownloadRanges.plan(Long.MAX_VALUE, 16).size)
    }
}
