package com.garyapp.ytdl.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.spec.SecretKeySpec

class RetryDraftStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun encryptedStoreRoundTripsMinimalRetryDraftWithoutPlaintext() {
        val root = temporaryFolder.newFolder("retry-requests")
        val cipher = AesGcmRetryPayloadCipher(
            SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES"),
        )
        val draft = RetryDownloadDraft(
            url = "https://example.com/watch?v=private-token",
            route = DownloadRoute.MergeRequired(
                videoFormatId = "video-av1-1080",
                audioFormatId = "audio-opus-251",
            ),
        )

        assertTrue(FileRetryDraftStore(root, cipher).save(42L, draft).isSuccess)

        val restored = FileRetryDraftStore(root, cipher).load(42L).getOrThrow()
        assertEquals(draft, restored)

        val encryptedBytes = root.resolve("42.bin").readBytes()
        val encryptedText = encryptedBytes.toString(Charsets.ISO_8859_1)
        listOf(
            draft.url,
            "video-av1-1080",
            "audio-opus-251",
        ).forEach { secret ->
            assertFalse(encryptedText.contains(secret))
        }
    }

    @Test
    fun storePreservesEveryRouteTypeAndRejectsCorruptPayload() {
        val root = temporaryFolder.newFolder("route-payloads")
        val cipher = AesGcmRetryPayloadCipher(
            SecretKeySpec(ByteArray(32) { index -> (index + 11).toByte() }, "AES"),
        )
        val store = FileRetryDraftStore(root, cipher)
        val routes = listOf(
            DownloadRoute.DirectSingleFile("direct-18"),
            DownloadRoute.VideoOnly("video-137"),
            DownloadRoute.AudioOnly("audio-140"),
            DownloadRoute.MergeRequired("video-399", "audio-140"),
        )

        routes.forEachIndexed { index, route ->
            val draft = RetryDownloadDraft(
                url = "https://example.com/$index",
                route = route,
            )
            val historyId = index.toLong() + 1L
            assertTrue(store.save(historyId, draft).isSuccess)
            assertEquals(draft, store.load(historyId).getOrThrow())
        }

        root.resolve("99.bin").writeBytes(byteArrayOf(1, 2, 3, 4))
        assertTrue(store.load(99L).isFailure)
        assertFalse(store.isAvailable(99L))
        assertTrue(store.delete(99L).getOrThrow())
        assertFalse(store.isAvailable(99L))
    }
}
