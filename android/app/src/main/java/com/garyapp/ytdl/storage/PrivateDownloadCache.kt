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
        val removedQueueRecordCount: Int,
        val removedHistoryRecordCount: Int,
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
) : DownloadCacheControl {
    private val canonicalRoot = rootDirectory.canonicalFile

    override fun inspect(): CacheStats {
        if (!canonicalRoot.exists()) return CacheStats(0, 0)
        var bytes = 0L
        var fileCount = 0
        visitFiles { file ->
            bytes += file.length()
            fileCount += 1
        }
        return CacheStats(bytes, fileCount)
    }

    override fun clear(): CacheClearResult {
        check(canonicalRoot.mkdirs() || canonicalRoot.isDirectory) {
            "无法创建 App 私有下载目录。"
        }

        val deletedFiles = mutableListOf<Pair<File, Long>>()
        val visitedDirectories = mutableSetOf<String>()
        canonicalRoot.listFiles().orEmpty().forEach { child ->
            deleteChild(child, visitedDirectories, deletedFiles)
        }
        val deletedUris = deletedFiles
            .map { (file, _) ->
                ExportController.appPrivateOutputUri(file.absolutePath, canonicalRoot.absolutePath)
            }
            .toSet()
        val queueIds = databaseQueueIdsFor(deletedUris)
        val historyIds = databaseHistoryIdsFor(deletedUris)
        val removedQueueRecords = if (queueIds.isEmpty()) 0 else queueDao.deleteByIds(queueIds)
        val removedHistoryRecords = if (historyIds.isEmpty()) 0 else historyDao.deleteByIds(historyIds)

        return CacheClearResult.Success(
            freedBytes = deletedFiles.sumOf { it.second },
            deletedFileCount = deletedFiles.size,
            removedQueueRecordCount = removedQueueRecords,
            removedHistoryRecordCount = removedHistoryRecords,
        )
    }

    private fun visitFiles(onFile: (File) -> Unit) {
        val visitedDirectories = mutableSetOf<String>()
        fun visit(candidate: File) {
            val canonical = candidate.canonicalFile
            if (!isStrictChild(canonical)) return
            if (canonical.isDirectory) {
                if (!visitedDirectories.add(canonical.path)) return
                canonical.listFiles().orEmpty().forEach(::visit)
            } else if (canonical.isFile) {
                onFile(canonical)
            }
        }
        canonicalRoot.listFiles().orEmpty().forEach(::visit)
    }

    private fun deleteChild(
        candidate: File,
        visitedDirectories: MutableSet<String>,
        deletedFiles: MutableList<Pair<File, Long>>,
    ) {
        val canonical = candidate.canonicalFile
        if (!isStrictChild(canonical)) return
        if (canonical.isDirectory) {
            if (!visitedDirectories.add(canonical.path)) return
            canonical.listFiles().orEmpty().forEach { child ->
                deleteChild(child, visitedDirectories, deletedFiles)
            }
            canonical.delete()
        } else if (canonical.isFile) {
            val bytes = canonical.length()
            if (canonical.delete()) {
                deletedFiles += canonical to bytes
            }
        }
    }

    private fun isStrictChild(candidate: File): Boolean {
        return candidate.path.startsWith(canonicalRoot.path + File.separator)
    }

    private fun databaseQueueIdsFor(deletedUris: Set<String>): List<Long> {
        if (deletedUris.isEmpty()) return emptyList()
        return queueDao.listAll()
            .filter { it.outputUri in deletedUris }
            .map { it.id }
    }

    private fun databaseHistoryIdsFor(deletedUris: Set<String>): List<Long> {
        if (deletedUris.isEmpty()) return emptyList()
        return historyDao.listAll()
            .filter { row ->
                row.outputUri in deletedUris || row.subtitleOutputUris
                    .orEmpty()
                    .lineSequence()
                    .any { it in deletedUris }
            }
            .map { it.id }
    }
}
