package com.garyapp.ytdl.download

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.garyapp.ytdl.data.HistoryItemEntity
import java.io.File
import java.io.FileNotFoundException
import java.net.URI

internal data class SafDocument(val documentId: String, val mimeType: String, val canDelete: Boolean)

internal interface HistorySafDocuments {
    /** Null means a confirmed missing document; errors must propagate. */
    fun inspect(uri: String, documentId: String): SafDocument?
    fun delete(uri: String): Boolean
}

internal class AndroidHistorySafDocuments(
    private val context: Context,
    private val queryDocument: (Uri, Array<String>) -> Cursor? = { uri, projection ->
        context.contentResolver.query(uri, projection, null, null, null)
    },
    private val isDocumentUri: (Uri) -> Boolean = { DocumentsContract.isDocumentUri(context, it) },
) : HistorySafDocuments {
    override fun inspect(uri: String, documentId: String): SafDocument? {
        val parsed = Uri.parse(uri)
        check(isDocumentUri(parsed) &&
            (!DocumentsContract.isTreeUri(parsed) ||
                DocumentsContract.getDocumentId(parsed) != DocumentsContract.getTreeDocumentId(parsed))) {
            "文档引用不可用。"
        }
        return try {
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS,
            )
            val cursor = queryDocument(parsed, projection)
                ?: error("文档状态不可读取。")
            cursor.use {
                if (!it.moveToFirst()) return null
                SafDocument(it.getString(0), it.getString(1),
                    it.getLong(2) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() != 0L)
            }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun delete(uri: String): Boolean =
        DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri))
}

internal class HistoryFileDeleteFailure(val deletedCount: Int) : IllegalStateException("下载文件未全部删除。")

/** Only deletes outputs named by the selected database row, after validating every target. */
internal class HistoryFileDeletion(
    private val filesRoot: File,
    private val legacyCacheRoot: File,
    private val saf: HistorySafDocuments,
) {
    fun <T> runWhenSafe(selected: HistoryItemEntity?, action: () -> T): IdleDownloadActionResult<T> =
        DownloadCoordinator.withTaskFilesLocked {
            if (selected == null || outputUris(selected).any { !it.startsWith("app-private://outputs/") }) {
                DownloadCoordinator.runWhenIdle(action)
            } else IdleDownloadActionResult.Executed(action())
        }

    private sealed interface Target {
        val identity: String
        data class Private(val uri: String, val file: File?, override val identity: String,
            val taskId: String) : Target
        data class Document(val uri: String, val documentId: String, val missing: Boolean,
            override val identity: String) : Target
    }

    fun delete(
        selected: HistoryItemEntity,
        otherRows: List<HistoryItemEntity>,
        nonTerminalTaskIds: Set<String>,
    ): Result<Int> {
        var deleted = 0
        return try {
            val selectedUris = outputUris(selected)
            val targets = selectedUris.map(::preflight).distinctBy { it.identity }
            val shared = otherRows.filter { it.id != selected.id }
                .flatMap(::outputUris)
                .mapNotNull { runCatching { identity(it) }.getOrNull() }
                .toSet()
            check(targets.none { it.identity in shared }) { "下载文件仍被其他记录使用。" }
            check(targets.none { it is Target.Private && it.taskId in nonTerminalTaskIds }) {
                "下载任务尚未结束。"
            }
            targets.forEach { target ->
                when (target) {
                    is Target.Private -> {
                        val current = preflight(target.uri) as Target.Private
                        check(current.file == target.file) { "私有输出文件已变化。" }
                        current.file?.let { file ->
                            check(file.delete()) { "下载文件删除失败。" }
                            deleted++
                        }
                    }
                    is Target.Document -> if (!target.missing) {
                        val current = saf.inspect(target.uri, target.documentId)
                        if (current != null) {
                            checkSafDocument(current, target.documentId)
                            check(saf.delete(target.uri)) { "文档删除失败。" }
                            deleted++
                        }
                    }
                }
            }
            Result.success(deleted)
        } catch (_: Exception) {
            Result.failure(HistoryFileDeleteFailure(deleted))
        }
    }

    private fun outputUris(row: HistoryItemEntity): List<String> = buildList {
        row.outputUri?.takeIf { it.isNotBlank() }?.let(::add)
        row.subtitleOutputUris?.lineSequence()?.filter { it.isNotBlank() }?.forEach(::add)
    }

    private fun identity(uri: String): String {
        val parsed = URI(uri)
        return when (parsed.scheme) {
            "app-private" -> privateIdentity(parsed).first
            "content" -> documentIdentity(parsed)
            else -> error("下载文件引用不可用。")
        }
    }

    private fun preflight(uri: String): Target {
        val parsed = URI(uri)
        return when (parsed.scheme) {
            "app-private" -> {
                val (identity, parts) = privateIdentity(parsed)
                val matches = listOf(filesRoot, legacyCacheRoot).mapNotNull { root -> privateFile(root, parts) }
                check(matches.size <= 1) { "多个私有文件与记录匹配。" }
                Target.Private(uri, matches.singleOrNull(), identity, parts.first().removePrefix("task-"))
            }
            "content" -> {
                val key = documentIdentity(parsed)
                val documentId = DocumentsContract.getDocumentId(Uri.parse(uri))
                val info = saf.inspect(uri, documentId)
                if (info != null) checkSafDocument(info, documentId)
                Target.Document(uri, documentId, info == null, key)
            }
            else -> error("下载文件引用不可用。")
        }
    }

    private fun privateIdentity(uri: URI): Pair<String, List<String>> {
        check(uri.rawAuthority == "outputs" && (uri.rawQuery == null || uri.rawQuery == "kind=file") && uri.rawFragment == null &&
            uri.rawUserInfo == null && uri.port == -1) { "私有输出引用不可用。" }
        val raw = uri.rawPath ?: error("私有输出引用不可用。")
        check(raw.startsWith('/') && !raw.startsWith("//")) { "私有输出引用不可用。" }
        val parts = raw.removePrefix("/").split('/').map { segment ->
            val decoded = Uri.decode(segment)
            check(decoded.isNotEmpty() && decoded != "." && decoded != ".." &&
                !decoded.contains('/') && !decoded.contains('\\') && !decoded.contains('\u0000') &&
                !decoded.contains(':')) { "私有输出路径不可用。" }
            decoded
        }
        check(parts.size >= 2 && parts.first().matches(
            Regex("task-(?:[0-9a-f]{32}|[0-9]{13}-[0-9a-z]+)"))) {
            "私有输出任务不可用。"
        }
        return "private/${parts.joinToString("/")}" to parts
    }

    private fun privateFile(root: File, parts: List<String>): File? {
        val guard = ControlledDirectory(root)
        guard.checkSafe(guard.root)
        if (!guard.root.exists()) return null
        check(guard.root.isDirectory) { "私有输出目录不可用。" }
        var current = guard.root
        parts.forEachIndexed { index, part ->
            current = guard.child(current, part)
            if (!current.exists()) return null
            if (index < parts.lastIndex) check(current.isDirectory) { "私有输出父目录不可用。" }
        }
        checkedPrivateFile(current)
        return current
    }

    private fun checkedPrivateFile(file: File) {
        val guard = listOf(filesRoot, legacyCacheRoot).map(::ControlledDirectory).firstOrNull { candidate ->
            file.absolutePath.startsWith(candidate.root.path + File.separator)
        } ?: error("私有输出路径不可用。")
        guard.checkSafe(guard.root)
        var current = guard.root
        val relative = file.absolutePath.removePrefix(guard.root.path + File.separator)
        relative.split(File.separatorChar).forEachIndexed { index, part ->
            current = guard.child(current, part)
            check(if (index == relative.split(File.separatorChar).lastIndex) current.isFile else current.isDirectory) {
                "私有输出文件不可用。"
            }
        }
    }

    private fun documentIdentity(uri: URI): String {
        check(uri.rawAuthority?.isNotEmpty() == true && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawUserInfo == null && uri.port == -1) { "文档引用不可用。" }
        val rawParts = uri.rawPath?.removePrefix("/")?.split('/') ?: emptyList()
        val direct = rawParts.size == 2 && rawParts[0] == "document" && rawParts[1].isNotEmpty()
        val treeChild = rawParts.size == 4 && rawParts[0] == "tree" && rawParts[1].isNotEmpty() &&
            rawParts[2] == "document" && rawParts[3].isNotEmpty()
        check(direct || treeChild) { "文档引用不可用。" }
        val parsed = Uri.parse(uri.toString())
        val id = DocumentsContract.getDocumentId(parsed)
        check(id.isNotEmpty()) { "文档引用不可用。" }
        if (treeChild) check(id != DocumentsContract.getTreeDocumentId(parsed)) { "不能删除文件夹根目录。" }
        return "document/${uri.authority}/$id"
    }

    private fun checkSafDocument(info: SafDocument, documentId: String) {
        check(info.documentId == documentId &&
            info.mimeType != DocumentsContract.Document.MIME_TYPE_DIR && info.canDelete) {
            "文档不允许删除。"
        }
    }
}
