package com.garyapp.ytdl.core.settings

import com.garyapp.ytdl.core.storage.StorageTarget

data class AppSettings(
    val defaultStorageTarget: StorageTarget = StorageTarget.AppPrivate,
    val cookiesReference: CookiesReference? = null,
)

data class CookiesReference(
    val reference: String,
    val displayName: String? = null,
)
