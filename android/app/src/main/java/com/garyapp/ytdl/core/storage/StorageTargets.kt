package com.garyapp.ytdl.core.storage

sealed interface StorageTarget {
    data object AppPrivate : StorageTarget

    data class MediaStoreDownloads(
        val displayName: String,
        val mimeType: String,
    ) : StorageTarget

    data class CreateDocument(
        val uri: String,
        val displayName: String,
        val mimeType: String,
    ) : StorageTarget

    data class SafTree(
        val treeUri: String,
        val displayName: String? = null,
    ) : StorageTarget
}
