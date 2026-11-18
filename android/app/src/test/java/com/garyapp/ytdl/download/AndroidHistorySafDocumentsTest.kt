package com.garyapp.ytdl.download

import android.content.Context
import android.database.MatrixCursor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import java.io.FileNotFoundException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidHistorySafDocumentsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val leaf = "content://provider/tree/root/document/leaf"

    @Test fun queryChecksActualProviderDocumentIdAndDeleteFlag() {
        val gateway = AndroidHistorySafDocuments(context, queryDocument = { _, columns ->
            MatrixCursor(columns).apply { addRow(arrayOf<Any>("leaf", "video/mp4",
                DocumentsContract.Document.FLAG_SUPPORTS_DELETE)) }
        }, isDocumentUri = { true })
        assertEquals(SafDocument("leaf", "video/mp4", true), gateway.inspect(leaf, "leaf"))
    }

    @Test fun nullCursorAndRevokedPermissionAreFailuresNotMissing() {
        val nullCursor = AndroidHistorySafDocuments(context, queryDocument = { _, _ -> null },
            isDocumentUri = { true })
        assertTrue(runCatching { nullCursor.inspect(leaf, "leaf") }.isFailure)
        val revoked = AndroidHistorySafDocuments(context, queryDocument = { _, _ -> throw SecurityException("revoked") },
            isDocumentUri = { true })
        assertTrue(runCatching { revoked.inspect(leaf, "leaf") }.isFailure)
        val missing = AndroidHistorySafDocuments(context, queryDocument = { _, columns -> MatrixCursor(columns) },
            isDocumentUri = { true })
        assertNull(missing.inspect(leaf, "leaf"))
        val missingException = AndroidHistorySafDocuments(context,
            queryDocument = { _, _ -> throw FileNotFoundException() }, isDocumentUri = { true })
        assertNull(missingException.inspect(leaf, "leaf"))
    }

    @Test fun documentProviderAndTreeRootMustBeRejected() {
        val query = { _: android.net.Uri, columns: Array<String> -> MatrixCursor(columns) }
        val untrusted = AndroidHistorySafDocuments(context, query, isDocumentUri = { false })
        assertTrue(runCatching { untrusted.inspect(leaf, "leaf") }.isFailure)
        val trusted = AndroidHistorySafDocuments(context, query, isDocumentUri = { true })
        assertTrue(runCatching { trusted.inspect("content://provider/tree/root/document/root", "root") }.isFailure)
    }
}
