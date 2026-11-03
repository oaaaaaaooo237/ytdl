package com.garyapp.ytdl.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RetryDraftStoreInstrumentedTest {
    @Test
    fun androidKeyStoreEncryptsAndRestoresRetryDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.noBackupFilesDir, "retry-draft-instrumented").apply {
            deleteRecursively()
        }
        val store = FileRetryDraftStore(root, AndroidKeyStoreRetryPayloadCipher())
        val draft = RetryDownloadDraft(
            url = "https://example.com/watch?v=retry",
            route = DownloadRoute.MergeRequired("video-720", "audio-140"),
        )

        try {
            val saveResult = store.save(1L, draft)
            assertTrue(saveResult.exceptionOrNull()?.stackTraceToString().orEmpty(), saveResult.isSuccess)
            assertTrue(store.isAvailable(1L))
            assertEquals(draft, store.load(1L).getOrThrow())
            assertTrue(store.delete(1L).getOrThrow())
            assertFalse(store.isAvailable(1L))
        } finally {
            root.deleteRecursively()
        }
    }
}
