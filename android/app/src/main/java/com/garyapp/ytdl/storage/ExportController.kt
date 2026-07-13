package com.garyapp.ytdl.storage

import android.content.ContentValues
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Path

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
                appPrivateUri = appPrivateOutputUri(canonicalOutput.absolutePath, canonicalRoot.absolutePath),
                sourceFile = canonicalOutput,
            )
        }
    }

    fun discoverAppPrivateOutputUri(
        appPrivateUri: String?,
        appPrivateRoot: File,
        legacyRoots: List<File> = emptyList(),
    ): Result<AppPrivateOutput> {
        return runCatching {
            val uri = URI(appPrivateUri.orEmpty())
            require(uri.scheme == "app-private" && uri.host == "outputs") {
                "历史记录没有可导出的本地输出。"
            }
            val relativeSegments = uri.rawPath.orEmpty()
                .trimStart('/')
                .split('/')
                .filter { it.isNotBlank() }
                .map { decodeSegment(it).safeDisplayName() }
                .filter { it.isNotBlank() }
            require(relativeSegments.isNotEmpty()) {
                "历史记录没有可导出的本地输出。"
            }
            val relativePath = relativeSegments.fold(File("")) { current, segment ->
                File(current, segment)
            }
            val primary = discoverAppPrivateOutput(File(appPrivateRoot, relativePath.path), appPrivateRoot)
            if (primary.isSuccess) {
                return@runCatching primary.getOrThrow()
            }
            legacyRoots.firstNotNullOfOrNull { legacyRoot ->
                discoverAppPrivateOutput(File(legacyRoot, relativePath.path), legacyRoot).getOrNull()
            } ?: primary.getOrThrow()
        }
    }

    fun createDocumentIntent(
        output: AppPrivateOutput,
        suggestedDisplayName: String = output.displayName,
    ): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(output.mimeType)
            .putExtra(Intent.EXTRA_TITLE, suggestedDisplayName.safeDisplayName())
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
    fun copyToSafTree(
        treeUri: String,
        outputs: List<AppPrivateOutput>,
        createDocument: (parentDocumentUri: String, mimeType: String, displayName: String) -> String?,
        openOutputStream: (documentUri: String) -> OutputStream?,
        deleteDocument: (documentUri: String) -> Unit,
    ): Long {
        val createdDocuments = mutableListOf<String>()
        return try {
            val parentDocumentUri = treeDocumentUri(treeUri)
            outputs.sumOf { output ->
                val documentUri = createDocument(parentDocumentUri, output.mimeType, output.displayName)
                    ?: throw IllegalStateException("无法创建导出文件。")
                createdDocuments += documentUri
                val destination = openOutputStream(documentUri)
                    ?: throw IllegalStateException("无法打开导出文件。")
                destination.use { stream ->
                    copyToStream(output, stream).getOrThrow()
                }
            }
        } catch (_: Exception) {
            createdDocuments.asReversed().forEach { documentUri ->
                runCatching { deleteDocument(documentUri) }
            }
            throw IllegalStateException(treeExportFailureMessage())
        }
    }

    fun copyToSafTree(
        contentResolver: ContentResolver,
        treeUri: String,
        outputs: List<AppPrivateOutput>,
    ): Result<Long> {
        return runCatching {
            copyToSafTree(
                treeUri = treeUri,
                outputs = outputs,
                createDocument = { parentDocumentUri, mimeType, displayName ->
                    DocumentsContract.createDocument(
                        contentResolver,
                        Uri.parse(parentDocumentUri),
                        mimeType,
                        displayName,
                    )?.toString()
                },
                openOutputStream = { documentUri ->
                    contentResolver.openOutputStream(Uri.parse(documentUri), "w")
                },
                deleteDocument = { documentUri ->
                    DocumentsContract.deleteDocument(contentResolver, Uri.parse(documentUri))
                },
            )
        }
    }

    @JvmStatic
    fun appPrivateOutputUri(path: String?): String {
        return appPrivateOutputUri(path, null)
    }

    @JvmStatic
    fun appPrivateOutputUri(path: String?, appPrivateRootPath: String?): String {
        val relativeSegments = relativeSegments(path, appPrivateRootPath)
        if (relativeSegments.isNotEmpty()) {
            return "app-private://outputs/${relativeSegments.joinToString("/") { encodeSegment(it.safeDisplayName()) }}"
        }
        val displayName = path
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).name }
            ?.safeDisplayName()
            ?: "output"
        return "app-private://outputs/${encodeSegment(displayName)}"
    }

    fun exportDeniedMessage(detail: String? = null): String {
        return "未获得保存位置授权，导出已取消。请重新选择保存位置。"
    }

    fun treeExportFailureMessage(): String {
        return "保存到所选文件夹失败，App 私有文件已保留；请重新选择文件夹后从历史导出。"
    }

    private fun isInside(file: File, root: File): Boolean {
        var current: File? = file
        while (current != null) {
            if (current == root) return true
            current = current.parentFile
        }
        return false
    }

    private fun treeDocumentUri(treeUri: String): String {
        val parsed = URI(treeUri)
        require(parsed.scheme.equals("content", ignoreCase = true) && !parsed.rawAuthority.isNullOrBlank())
        val marker = "/tree/"
        val rawPath = parsed.rawPath.orEmpty()
        val treeId = rawPath.substringAfter(marker, "").substringBefore('/').takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("无效的保存位置。")
        return "content://${parsed.rawAuthority}$marker$treeId/document/$treeId"
    }

    private fun String.safeDisplayName(): String {
        val normalized = replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
            .take(96)
            .trim('.', ' ')
        return normalized.ifBlank { "output" }
    }

    private fun relativeSegments(path: String?, appPrivateRootPath: String?): List<String> {
        if (path.isNullOrBlank() || appPrivateRootPath.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            val root = File(appPrivateRootPath).canonicalFile
            val output = File(path).canonicalFile
            if (!isInside(output, root)) {
                return emptyList()
            }
            root.toPath()
                .relativize(output.toPath())
                .safePathSegments()
                .map { it.safeDisplayName() }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun Path.safePathSegments(): List<String> {
        return map { it.toString() }
            .filter { it.isNotBlank() && it != "." && it != ".." }
    }

    private fun encodeSegment(segment: String): String {
        return URLEncoder.encode(segment, Charsets.UTF_8.name())
            .replace("+", "%20")
    }

    private fun decodeSegment(segment: String): String {
        return runCatching {
            URLDecoder.decode(segment, Charsets.UTF_8.name())
        }.getOrDefault(segment)
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
