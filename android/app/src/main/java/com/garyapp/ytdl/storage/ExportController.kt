package com.garyapp.ytdl.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Path
import java.util.concurrent.CancellationException

object ExportController {
    class AppPrivateOutput internal constructor(
        val displayName: String,
        val mimeType: String,
        val bytesWritten: Long,
        val appPrivateUri: String,
        internal val sourceFile: File,
        internal val appPrivateRoot: File,
    ) {
        override fun toString(): String {
            return "AppPrivateOutput(displayName=$displayName, mimeType=$mimeType, bytesWritten=$bytesWritten, appPrivateUri=$appPrivateUri)"
        }
    }

    data class SafTreeExportResult(
        val bytesWritten: Long,
        val documentUris: List<String>,
    )

    fun discoverAppPrivateOutput(
        outputFile: File,
        appPrivateRoot: File,
    ): Result<AppPrivateOutput> {
        return runCatching {
            val canonicalRoot = appPrivateRoot.canonicalFile
            val canonicalOutput = outputFile.canonicalFile
            require(canonicalOutput.isFile && canonicalOutput.length() > 0L) {
                "输出文件不存在或为空，无法继续操作。"
            }
            require(isInside(canonicalOutput, canonicalRoot)) {
                "只能访问 App 私有目录内的输出文件。"
            }

            val displayName = canonicalOutput.name.safeDisplayName()
            AppPrivateOutput(
                displayName = displayName,
                mimeType = mimeTypeFor(displayName),
                bytesWritten = canonicalOutput.length(),
                appPrivateUri = appPrivateOutputUri(canonicalOutput.absolutePath, canonicalRoot.absolutePath),
                sourceFile = canonicalOutput,
                appPrivateRoot = canonicalRoot,
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
                "历史记录没有可用的本地输出。"
            }
            val relativeSegments = uri.rawPath.orEmpty()
                .trimStart('/')
                .split('/')
                .filter { it.isNotBlank() }
                .map { decodeSegment(it).safeDisplayName() }
                .filter { it.isNotBlank() }
            require(relativeSegments.isNotEmpty()) {
                "历史记录没有可用的本地输出。"
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

    fun copyToSafTreeWithDocuments(
        treeUri: String,
        outputs: List<AppPrivateOutput>,
        createDocument: (parentDocumentUri: String, mimeType: String, displayName: String) -> String?,
        openOutputStream: (documentUri: String) -> OutputStream?,
        deleteDocument: (documentUri: String) -> Unit,
        isCancellationRequested: () -> Boolean,
    ): SafTreeExportResult {
        val createdDocuments = mutableListOf<String>()
        return try {
            val parentDocumentUri = treeDocumentUri(treeUri)
            val bytesWritten = outputs.sumOf { output ->
                throwIfExportCanceled(isCancellationRequested)
                val documentUri = createDocument(parentDocumentUri, output.mimeType, output.displayName)
                    ?: throw IllegalStateException("无法创建保存文件。")
                createdDocuments += documentUri
                val destination = openOutputStream(documentUri)
                    ?: throw IllegalStateException("无法打开保存文件。")
                destination.use { stream ->
                    copyToStream(output, stream, isCancellationRequested)
                }
            }
            SafTreeExportResult(
                bytesWritten = bytesWritten,
                documentUris = createdDocuments.toList(),
            )
        } catch (error: Exception) {
            createdDocuments.asReversed().forEach { documentUri ->
                runCatching { deleteDocument(documentUri) }
            }
            if (error is CancellationException) throw error
            throw IllegalStateException(treeExportFailureMessage())
        }
    }

    fun copyToSafTreeWithDocuments(
        contentResolver: ContentResolver,
        treeUri: String,
        outputs: List<AppPrivateOutput>,
        isCancellationRequested: () -> Boolean = { false },
    ): Result<SafTreeExportResult> {
        return runCatching {
            copyToSafTreeWithDocuments(
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
                isCancellationRequested = isCancellationRequested,
            )
        }
    }

    fun markPrivateTaskStarted(taskDirectory: File): Result<Unit> {
        return runCatching {
            val canonicalTask = taskDirectory.canonicalFile
            require(canonicalTask.isDirectory && canonicalTask.name.startsWith("task-")) {
                "无法初始化私有任务目录。"
            }
            val marker = File(canonicalTask, IncompleteTaskMarker)
            marker.writeText("")
            check(marker.isFile) { "无法标记私有任务目录。" }
        }
    }

    fun markAppPrivateTaskCompleted(outputs: List<AppPrivateOutput>): Result<Unit> {
        return runCatching {
            val taskDirectory = controlledTaskDirectory(outputs)
            val completedMarker = File(taskDirectory, CompletedTaskMarker)
            completedMarker.writeText("")
            check(completedMarker.isFile) { "无法保护 App 私有完成文件。" }
            File(taskDirectory, IncompleteTaskMarker).delete()
        }
    }

    fun cleanupExportedPrivateTask(
        outputs: List<AppPrivateOutput>,
        deleteTaskDirectory: (File) -> Boolean = { it.deleteRecursively() },
    ): Result<Unit> {
        return runCatching {
            val taskDirectory = controlledTaskDirectory(outputs)
            check(deleteTaskDirectory(taskDirectory) && !taskDirectory.exists()) {
                "App 私有中转文件未能立即清理。"
            }
        }
    }

    internal fun isIncompleteTaskDirectory(directory: File): Boolean {
        return File(directory, IncompleteTaskMarker).isFile && !isCompletedTaskDirectory(directory)
    }

    internal fun isCompletedTaskDirectory(directory: File): Boolean {
        return File(directory, CompletedTaskMarker).isFile
    }

    internal fun isTaskLifecycleMarker(file: File): Boolean {
        return file.name == IncompleteTaskMarker || file.name == CompletedTaskMarker
    }

    private fun controlledTaskDirectory(outputs: List<AppPrivateOutput>): File {
        require(outputs.isNotEmpty()) { "缺少需要处理的私有输出文件。" }
        val roots = outputs.map { it.appPrivateRoot.canonicalFile }.distinctBy { it.path }
        require(roots.size == 1) { "私有输出文件不属于同一受控目录。" }
        val root = roots.single()
        val taskDirectories = outputs.map { output ->
            requireNotNull(output.sourceFile.canonicalFile.parentFile) {
                "私有输出文件缺少任务父目录。"
            }
        }.distinctBy { it.path }
        require(taskDirectories.size == 1) { "私有输出文件不属于同一任务目录。" }
        val taskDirectory = taskDirectories.single()
        require(taskDirectory.parentFile == root && taskDirectory.name.startsWith("task-")) {
            "只能处理受控私有目录中的任务子目录。"
        }
        require(outputs.all { it.sourceFile.canonicalFile.parentFile == taskDirectory }) {
            "私有输出文件路径不安全。"
        }
        require(taskDirectory.isDirectory) { "私有任务目录不存在。" }
        return taskDirectory
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

    fun treeExportFailureMessage(): String {
        return "保存到所选文件夹失败，私有中转文件暂未清理；请重新选择文件夹后重试下载。"
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

    private fun copyToStream(
        output: AppPrivateOutput,
        destination: OutputStream,
        isCancellationRequested: () -> Boolean,
    ): Long {
        var copiedBytes = 0L
        output.sourceFile.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                throwIfExportCanceled(isCancellationRequested)
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                destination.write(buffer, 0, bytesRead)
                copiedBytes += bytesRead
            }
        }
        throwIfExportCanceled(isCancellationRequested)
        return copiedBytes
    }

    private fun throwIfExportCanceled(isCancellationRequested: () -> Boolean) {
        if (isCancellationRequested()) {
            throw CancellationException("SAF save canceled")
        }
    }

    private fun String.safeDisplayName(): String {
        val normalized = replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
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

    private const val IncompleteTaskMarker = ".ytdl-incomplete"
    private const val CompletedTaskMarker = ".ytdl-completed"
}
