package com.garyapp.ytdl.core.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadLinkImportTest {
    @Test
    fun importsMarkdownLinksAndListsWithoutTurningTheirLabelsIntoAddresses() {
        val batch = DownloadLinkImport.read("""
            # Downloads
            - [Video](https://example.com/video.mp4)
            1. https://example.com/file.bin
            - [ ] <https://example.com/other.bin>
            [Duplicate](https://example.com/file.bin)
        """.trimIndent().reader())
        assertEquals(listOf("https://example.com/video.mp4", "https://example.com/file.bin", "https://example.com/other.bin"), batch.urls)
        assertEquals(1, batch.duplicateCount)
        assertEquals(0, batch.invalidCount)
    }

    @Test
    fun browserShareAcceptsTitleWithOneLinkAndRejectsAmbiguousText() {
        assertEquals("https://example.com/a?q=one%20two", sharedDownloadUrl(
            "Page title\nhttps://example.com/a?q=one%20two",
        ))
        assertEquals(null, sharedDownloadUrl("https://example.com/a https://example.com/b"))
    }

    @Test
    fun acceptsBomAndCrLfAndPreservesOrderWhileCountingRejectedLines() {
        val batch = DownloadLinkImport.read(
            "\uFEFF https://example.com/a\r\n\r\nftp://example.com/a\r\nhttps://example.com/a\nhttp://example.org/b"
                .reader(),
        )

        assertEquals(listOf("https://example.com/a", "http://example.org/b"), batch.urls)
        assertEquals(1, batch.duplicateCount)
        assertEquals(1, batch.invalidCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedInput() {
        DownloadLinkImport.read("x".repeat(256 * 1024 + 1).reader())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooManyDistinctLinks() {
        DownloadLinkImport.read((1..501).joinToString("\n") { "https://example.com/$it" }.reader())
    }
}
