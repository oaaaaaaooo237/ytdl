package com.garyapp.ytdl.storage

import com.garyapp.ytdl.data.HistoryDao
import com.garyapp.ytdl.data.QueueDao
import java.io.File

data class CacheStats(
    val bytes: Long,
    val fileCount: Int,
)

sealed interface CacheClearResult {
    data object BlockedByActiveDownload : CacheClearResult

    data class Success(
        val freedBytes: Long,
        val deletedFileCount: Int,
    ) : CacheClearResult

    data class Incomplete(
        val freedBytes: Long,
        val deletedFileCount: Int,
        val remainingBytes: Long,
        val remainingFileCount: Int,
    ) : CacheClearResult
}

interface DownloadCacheControl {
    fun inspect(): CacheStats
    fun clear(): CacheClearResult
}

class PrivateDownloadCache(
    rootDirectory: File,
    private val queueDao: QueueDao,
    private val historyDao: HistoryDao,
    private val deleteFile: (File) -> Boolean = { it.delete() },
) : DownloadCacheControl {
    private val canonicalRoot = rootDirectory.canonicalFile

    override fun inspect(): CacheStats {
        if (!canonicalRoot.exists()) return CacheStats(0, 0)
        val protectedUris = protectedOutputUris()
        var bytes = 0L
        var fileCount = 0
        visitTemporaryFiles { file ->
            if (!isProtectedOutput(file, protectedUris)) {
                bytes += file.length()
                fileCount += 1
            }
        }
        return CacheStats(bytes, fileCount)
    }

    override fun clear(): CacheClearResult {
        check(canonicalRoot.mkdirs() || canonicalRoot.isDirectory) {
            "无法创建 App 私有下载目录。"
        }

        val protectedUris = protectedOutputUris()
        val deletedFiles = mutableListOf<Pair<File, Long>>()
        canonicalRoot.listFiles().orEmpty().forEach { child ->
            when {
                child.isDirectory && ExportController.isCompletedTaskDirectory(child) -> Unit
                child.isDirectory && ExportController.isIncompleteTaskDirectory(child) -> {
                    deleteTemporaryTask(child, protectedUris, deletedFiles)
                }
                else -> deleteLegacyPartFiles(child, protectedUris, deletedFiles)
            }
        }

        val freedBytes = deletedFiles.sumOf { it.second }
        val remaining = inspect()
        return if (remaining.fileCount == 0) {
            CacheClearResult.Success(
                freedBytes = freedBytes,
                deletedFileCount = deletedFiles.size,
            )
        } else {
            CacheClearResult.Incomplete(
                freedBytes = freedBytes,
                deletedFileCount = deletedFiles.size,
                remainingBytes = remaining.bytes,
                remainingFileCount = remaining.fileCount,
            )
        }
    }

    private fun visitTemporaryFiles(onFile: (File) -> Unit) {
        val visitedDirectories = mutableSetOf<String>()
        fun visit(candidate: File, includeAllFiles: Boolean) {
            val canonical = candidate.canonicalFile
            if (!isStrictChild(canonical)) return
            if (canonical.isDirectory) {
                if (!visitedDirectories.add(canonical.path)) return
                if (ExportController.isCompletedTaskDirectory(canonical)) return
                val includeChildren = includeAllFiles || ExportController.isIncompleteTaskDirectory(canonical)
                canonical.listFiles().orEmpty().forEach { visit(it, includeChildren) }
            } else if (
                canonical.isFile &&
                !ExportController.isTaskLifecycleMarker(canonical) &&
                (includeAllFiles || canonical.name.endsWith(".part", ignoreCase = true))
            ) {
                onFile(canonical)
            }
        }
        canonicalRoot.listFiles().orEmpty().forEach { visit(it, includeAllFiles = false) }
    }

    private fun deleteTemporaryTask(
        taskDirectory: File,
        protectedUris: Set<String>,
        deletedFiles: MutableList<Pair<File, Long>>,
    ) {
        val visitedDirectories = mutableSetOf<String>()
        fun deleteCandidate(candidate: File) {
            val canonical = candidate.canonicalFile
            if (!isStrictChild(canonical)) return
            if (canonical.isDirectory) {
                if (!visitedDirectories.add(canonical.path)) return
                canonical.listFiles().orEmpty()
                    .filterNot(ExportController::isTaskLifecycleMarker)
                    .forEach(::deleteCandidate)
                canonical.delete()
            } else if (canonical.isFile && !ExportController.isTaskLifecycleMarker(canonical)) {
                if (isProtectedOutput(canonical, protectedUris)) return
                val bytes = canonical.length()
                if (deleteFile(canonical)) {
                    deletedFiles += canonical to bytes
                }
            }
        }
        taskDirectory.listFiles().orEmpty()
            .filterNot(ExportController::isTaskLifecycleMarker)
            .forEach(::deleteCandidate)

        val unprotectedPayloadRemains = hasUnprotectedPayload(taskDirectory, protectedUris)
        if (!unprotectedPayloadRemains) {
            taskDirectory.listFiles().orEmpty()
                .filter(ExportController::isTaskLifecycleMarker)
                .forEach(File::delete)
            taskDirectory.delete()
        }
    }

    private fun deleteLegacyPartFiles(
        candidate: File,
        protectedUris: Set<String>,
        deletedFiles: MutableList<Pair<File, Long>>,
    ) {
        val canonical = candidate.canonicalFile
        if (!isStrictChild(canonical)) return
        if (canonical.isDirectory) {
            if (ExportController.isCompletedTaskDirectory(canonical)) return
            canonical.listFiles().orEmpty().forEach { child ->
                deleteLegacyPartFiles(child, protectedUris, deletedFiles)
            }
            canonical.delete()
        } else if (canonical.isFile && canonical.name.endsWith(".part", ignoreCase = true)) {
            if (isProtectedOutput(canonical, protectedUris)) return
            val bytes = canonical.length()
            if (deleteFile(canonical)) {
                deletedFiles += canonical to bytes
            }
        }
    }

    private fun hasUnprotectedPayload(
        taskDirectory: File,
        protectedUris: Set<String>,
    ): Boolean {
        val visitedDirectories = mutableSetOf<String>()
        fun visit(candidate: File): Boolean {
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return true
            if (!isStrictChild(canonical)) return false
            if (canonical.isDirectory) {
                if (!visitedDirectories.add(canonical.path)) return false
                return canonical.listFiles().orEmpty().any(::visit)
            }
            return canonical.isFile &&
                !ExportController.isTaskLifecycleMarker(canonical) &&
                !isProtectedOutput(canonical, protectedUris)
        }
        return taskDirectory.listFiles().orEmpty()
            .filterNot(ExportController::isTaskLifecycleMarker)
            .any(::visit)
    }

    private fun isStrictChild(candidate: File): Boolean {
        return candidate.path.startsWith(canonicalRoot.path + File.separator)
    }

    private fun protectedOutputUris(): Set<String> {
        return buildSet {
            queueDao.listAll().forEach { row ->
                row.outputUri.orEmpty().takeIf(::isAppPrivateOutputUri)?.let(::add)
            }
            historyDao.listAll().forEach { row ->
                row.outputUri.orEmpty().takeIf(::isAppPrivateOutputUri)?.let(::add)
                row.subtitleOutputUris.orEmpty()
                    .lineSequence()
                    .map(String::trim)
                    .filter(::isAppPrivateOutputUri)
                    .forEach(::add)
            }
        }
    }

    private fun isProtectedOutput(file: File, protectedUris: Set<String>): Boolean {
        val uri = ExportController.appPrivateOutputUri(file.absolutePath, canonicalRoot.absolutePath)
        return uri in protectedUris
    }

    private fun isAppPrivateOutputUri(value: String): Boolean {
        return value.startsWith("app-private://outputs/")
    }
}
