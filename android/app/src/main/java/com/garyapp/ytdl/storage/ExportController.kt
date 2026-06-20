package com.garyapp.ytdl.storage

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

object ExportController {
    class AppPrivateOutput internal constructor(
        val displayName: String,
        val mimeType: String,
        val bytesWritten: Long,
        val appPrivateUri: String,
        internal val sourceFile: File,
    ) {
        override fun toString(): String {
            return "AppPrivateOutput(displayName=$displayName, mimeType=$mimeType, bytesWritten=$bytesWritten, appPrivateUri=$appPrivateUri)"
        }
    }

    fun discoverAppPrivateOutput(
        outputFile: File,
        appPrivateRoot: File,
    ): Result<AppPrivateOutput> {
        return runCatching {
            val canonicalRoot = appPrivateRoot.canonicalFile
            val canonicalOutput = outputFile.canonicalFile
            require(canonicalOutput.isFile && canonicalOutput.length() > 0L) {
                "输出文件不存在或为空，不能导出。"
            }
            require(isInside(canonicalOutput, canonicalRoot)) {
                "只能导出 App 私有目录内的输出文件。"
            }

            val displayName = canonicalOutput.name.safeDisplayName()
            AppPrivateOutput(
                displayName = displayName,
                mimeType = mimeTypeFor(displayName),
                bytesWritten = canonicalOutput.length(),
                appPrivateUri = appPrivateOutputUri(canonicalOutput.absolutePath),
                sourceFile = canonicalOutput,
            )
        }
    }

    fun createDocumentIntent(output: AppPrivateOutput): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(output.mimeType)
            .putExtra(Intent.EXTRA_TITLE, output.displayName)
    }

    fun mediaStoreValues(output: AppPrivateOutput): ContentValues {
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, output.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, output.mimeType)
            put(MediaStore.MediaColumns.SIZE, output.bytesWritten)
        }
    }

    fun copyToStream(
        output: AppPrivateOutput,
        destination: OutputStream,
    ): Result<Long> {
        return runCatching {
            val copiedBytes = output.sourceFile.inputStream().use { input ->
                input.copyTo(destination)
            }
            copiedBytes
        }
    }

    @JvmStatic
    fun appPrivateOutputUri(path: String?): String {
        val displayName = path
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).name }
            ?.safeDisplayName()
            ?: "output"
        return "app-private://outputs/${Uri.encode(displayName)}"
    }

    fun exportDeniedMessage(detail: String? = null): String {
        return "未获得保存位置授权，导出已取消。请重新选择保存位置。"
    }

    private fun isInside(file: File, root: File): Boolean {
        var current: File? = file
        while (current != null) {
            if (current == root) return true
            current = current.parentFile
        }
        return false
    }

    private fun String.safeDisplayName(): String {
        val normalized = replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
            .take(96)
            .trim('.', ' ')
        return normalized.ifBlank { "output" }
    }

    private fun mimeTypeFor(displayName: String): String {
        return when (displayName.substringAfterLast('.', "").lowercase()) {
            "mp4" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "vtt" -> "text/vtt"
            "srt" -> "application/x-subrip"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
