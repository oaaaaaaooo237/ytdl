package com.garyapp.ytdl.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CookiesDocumentPickerTest {
    @Test
    fun startsAtSharedStorageRootAndAllowsAllFileTypes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = CookiesDocumentPicker().createIntent(context, arrayOf("*/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertEquals("*/*", intent.type)
        assertTrue(intent.getBooleanExtra("android.content.extra.SHOW_ADVANCED", false))
        assertEquals(
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:"),
            intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java),
        )
        val selected = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2Fcookies.txt")
        val picker = CookiesDocumentPicker()
        assertEquals(selected, picker.parseResult(Activity.RESULT_OK, Intent().setData(selected)))
        assertNull(picker.parseResult(Activity.RESULT_CANCELED, null))
    }

    @Test
    @Config(sdk = [24])
    fun olderAndroidUsesSystemStorageNavigation() {
        val intent = CookiesDocumentPicker().createIntent(
            ApplicationProvider.getApplicationContext(), arrayOf("*/*"),
        )
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.getBooleanExtra("android.content.extra.SHOW_ADVANCED", false))
        assertFalse(intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }
}
