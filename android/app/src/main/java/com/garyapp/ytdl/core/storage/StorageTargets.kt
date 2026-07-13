package com.garyapp.ytdl.core.storage

import java.net.URI

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

object StorageTargets {
    const val TypeAppPrivate = "app_private"
    const val TypeSafTree = "saf_tree"

    fun sanitizeDefault(target: StorageTarget): StorageTarget {
        return when (target) {
            StorageTarget.AppPrivate -> StorageTarget.AppPrivate
            is StorageTarget.SafTree -> {
                if (isValidTreeUri(target.treeUri)) {
                    target.copy(displayName = safeDisplayName(target.displayName))
                } else {
                    StorageTarget.AppPrivate
                }
            }
            else -> StorageTarget.AppPrivate
        }
    }

    fun restoreDefault(
        type: String?,
        treeUri: String?,
        displayName: String?,
    ): StorageTarget {
        if (type != TypeSafTree) return StorageTarget.AppPrivate
        return sanitizeDefault(
            StorageTarget.SafTree(
                treeUri = treeUri.orEmpty(),
                displayName = displayName,
            ),
        )
    }

    fun persistedType(target: StorageTarget): String {
        return if (sanitizeDefault(target) is StorageTarget.SafTree) TypeSafTree else TypeAppPrivate
    }

    fun displayName(target: StorageTarget): String {
        return when (val safeTarget = sanitizeDefault(target)) {
            StorageTarget.AppPrivate -> "App 私有目录"
            is StorageTarget.SafTree -> safeTarget.displayName ?: "所选文件夹"
            else -> "App 私有目录"
        }
    }

    private fun isValidTreeUri(value: String): Boolean {
        return runCatching {
            val uri = URI(value.trim())
            uri.scheme.equals("content", ignoreCase = true) &&
                !uri.authority.isNullOrBlank() &&
                !uri.path.isNullOrBlank() &&
                uri.rawPath.orEmpty().contains("/tree/")
        }.getOrDefault(false)
    }

    private fun safeDisplayName(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.contains("://") || trimmed.contains('/') || trimmed.contains('\\')) {
            return "所选文件夹"
        }
        return trimmed
            .replace(Regex("""[:*?\"<>|\r\n\t]"""), "_")
            .take(64)
            .trim('.', ' ')
            .ifBlank { "所选文件夹" }
    }
}
