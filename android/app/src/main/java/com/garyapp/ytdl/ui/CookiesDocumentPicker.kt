package com.garyapp.ytdl.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

internal class CookiesDocumentPicker : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Ask DocumentsUI to expose device storage in its navigation drawer.
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:"),
                )
            }
        }
    }
}
