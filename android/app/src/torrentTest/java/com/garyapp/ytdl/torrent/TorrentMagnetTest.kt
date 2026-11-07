package com.garyapp.ytdl.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentMagnetTest {
    @Test
    fun acceptsV1V2AndEncodedTopics() {
        assertTrue(isTorrentMagnet("magnet:?xt=urn:btih:${"a".repeat(40)}"))
        assertTrue(isTorrentMagnet("magnet:?xt=urn:btih:${"Z7".repeat(16)}"))
        assertTrue(isTorrentMagnet("magnet:?dn=example&xt=urn%3Abtmh%3A1220${"e".repeat(64)}"))
    }

    @Test
    fun rejectsMissingMalformedAndOversizedTopics() {
        assertFalse(isTorrentMagnet("magnet:?dn=example"))
        assertFalse(isTorrentMagnet("magnet:?xt=urn:btih:${"0".repeat(32)}"))
        assertFalse(isTorrentMagnet("magnet:?xt=urn%ZZbtih"))
        assertFalse(isTorrentMagnet("https://example.org/?xt=urn:btih:${"a".repeat(40)}"))
        assertFalse(isTorrentMagnet("magnet:?xt=urn:btih:${"a".repeat(40)}&dn=${"a".repeat(16384)}"))
    }
}
