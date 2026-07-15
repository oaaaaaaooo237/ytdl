package com.garyapp.ytdl.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.data.QueueItemEntity
import com.garyapp.ytdl.data.YtdlDatabase
import com.garyapp.ytdl.testing.ProjectTestPaths
import com.garyapp.ytdl.core.ytdlp.ParserVersionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivateDownloadCacheTest {
    private lateinit var database: YtdlDatabase
    private lateinit var testRoot: File
    private lateinit var privateRoot: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YtdlDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testRoot = ProjectTestPaths.createTempDirectory("task-4-cache-unit-").toFile()
        privateRoot = File(testRoot, "files/gui-downloads")
    }

    @After
    fun tearDown() {
        database.close()
        testRoot.deleteRecursively()
        assertFalse("测试临时目录必须自动清理", testRoot.exists())
    }

    @Test
    fun inspectAndClearDeleteOnlyUnreferencedTemporaryFiles() {
        val media = File(privateRoot, "task-1/video.mp4").writeBytesAfterCreating(byteArrayOf(1, 2, 3))
        val subtitle = File(privateRoot, "task-1/video.zh.srt").writeBytesAfterCreating(byteArrayOf(4, 5, 6, 7))
        val orphanTemporary = File(privateRoot, "task-orphan/video.part")
            .writeBytesAfterCreating(byteArrayOf(10, 11, 12, 13, 14))
        val externalCopy = File(testRoot, "saf-copy/video.mp4").writeBytesAfterCreating(byteArrayOf(8, 9))
        val mediaUri = ExportController.appPrivateOutputUri(media.absolutePath, privateRoot.absolutePath)
        val subtitleUri = ExportController.appPrivateOutputUri(subtitle.absolutePath, privateRoot.absolutePath)
        val externalUri = "content://com.android.externalstorage.documents/document/video.mp4"
        database.queueDao().insert(queueItem("私有队列", mediaUri))
        database.queueDao().insert(queueItem("外部队列", externalUri))
        database.historyDao().insert(historyItem("私有历史", mediaUri, null))
        database.historyDao().insert(historyItem("字幕历史", externalUri, subtitleUri))
        database.historyDao().insert(historyItem("外部历史", externalUri, null))
        val cache = PrivateDownloadCache(privateRoot, database.queueDao(), database.historyDao())

        assertEquals(CacheStats(bytes = 5, fileCount = 1), cache.inspect())
        val result = cache.clear()

        assertEquals(
            CacheClearResult.Success(
                freedBytes = 5,
                deletedFileCount = 1,
            ),
            result,
        )
        assertTrue(privateRoot.isDirectory)
        assertTrue(media.isFile)
        assertTrue(subtitle.isFile)
        assertFalse(orphanTemporary.exists())
        assertTrue(externalCopy.isFile)
        assertEquals(2, database.queueDao().listAll().size)
        assertEquals(3, database.historyDao().listAll().size)
    }

    @Test
    fun completedPrivateTaskSurvivesHistoryDeletionAndCacheClear() {
        val completedTask = File(privateRoot, "task-completed").apply { mkdirs() }
        val completedMedia = File(completedTask, "merged-137-140.mp4")
            .writeBytesAfterCreating(byteArrayOf(1, 2, 3, 4))
        val completedOutput = ExportController.discoverAppPrivateOutput(completedMedia, privateRoot).getOrThrow()
        ExportController.markPrivateTaskStarted(completedTask).getOrThrow()
        ExportController.markAppPrivateTaskCompleted(listOf(completedOutput)).getOrThrow()

        val incompleteTask = File(privateRoot, "task-incomplete").apply { mkdirs() }
        val orphanStream = File(incompleteTask, "download-video-video.mp4")
            .writeBytesAfterCreating(byteArrayOf(5, 6, 7))
        ExportController.markPrivateTaskStarted(incompleteTask).getOrThrow()
        val cache = PrivateDownloadCache(privateRoot, database.queueDao(), database.historyDao())

        assertEquals(CacheStats(bytes = 3, fileCount = 1), cache.inspect())
        assertEquals(CacheClearResult.Success(freedBytes = 3, deletedFileCount = 1), cache.clear())
        assertTrue("删除历史后也必须保留用户完成文件", completedMedia.isFile)
        assertFalse(orphanStream.exists())
    }

    @Test
    fun clearReportsIncompleteWhenTemporaryFileCannotBeDeleted() {
        val incompleteTask = File(privateRoot, "task-delete-failure").apply { mkdirs() }
        val temporary = File(incompleteTask, "download-video-video.mp4")
            .writeBytesAfterCreating(byteArrayOf(1, 2, 3))
        ExportController.markPrivateTaskStarted(incompleteTask).getOrThrow()
        val cache = PrivateDownloadCache(
            rootDirectory = privateRoot,
            queueDao = database.queueDao(),
            historyDao = database.historyDao(),
            deleteFile = { false },
        )

        assertEquals(
            CacheClearResult.Incomplete(
                freedBytes = 0,
                deletedFileCount = 0,
                remainingBytes = 3,
                remainingFileCount = 1,
            ),
            cache.clear(),
        )
        assertTrue(temporary.isFile)
        assertEquals(CacheStats(bytes = 3, fileCount = 1), cache.inspect())
    }

    @Test
    fun missingCacheIsZeroItemSuccessAndCreatesPreservedRoot() {
        assertFalse(privateRoot.exists())
        val cache = PrivateDownloadCache(privateRoot, database.queueDao(), database.historyDao())

        assertEquals(CacheStats(bytes = 0, fileCount = 0), cache.inspect())
        assertEquals(
            CacheClearResult.Success(0, 0),
            cache.clear(),
        )
        assertTrue(privateRoot.isDirectory)
    }

    @Test
    fun cacheClearCannotEnterParserVersionOrRuntimeDirectories() {
        val parserWheel = File(
            testRoot,
            "files/${ParserVersionManager.VersionsDirectoryName}/yt_dlp-2026.4.1-py3-none-any.whl",
        ).writeBytesAfterCreating(byteArrayOf(1, 2, 3))
        val runtimeWheel = File(
            testRoot,
            "no-backup/${ParserVersionManager.RuntimeDirectoryName}/yt_dlp-2026.4.1-hash.whl",
        ).writeBytesAfterCreating(byteArrayOf(4, 5, 6))
        File(privateRoot, "orphan.part").writeBytesAfterCreating(byteArrayOf(7))
        val cache = PrivateDownloadCache(privateRoot, database.queueDao(), database.historyDao())

        assertEquals(CacheClearResult.Success(1, 1), cache.clear())

        assertTrue(parserWheel.isFile)
        assertTrue(runtimeWheel.isFile)
    }

    private fun queueItem(title: String, outputUri: String): QueueItemEntity = QueueItemEntity(
        0,
        title,
        1,
        "https",
        "safehash",
        "youtube",
        outputUri,
        "测试格式",
        QueueItemEntity.STATUS_COMPLETED,
        100,
        "",
        "",
        null,
        1,
        1,
    )

    private fun historyItem(
        title: String,
        outputUri: String,
        subtitleOutputUris: String?,
    ): HistoryItemEntity = HistoryItemEntity(
        0,
        title,
        1,
        "https",
        "safehash",
        "youtube",
        outputUri,
        subtitleOutputUris,
        "测试格式",
        null,
        HistoryItemEntity.STATUS_COMPLETED,
        100,
        "",
        "",
        null,
        1,
        1,
        1,
    )
}

private fun File.writeBytesAfterCreating(bytes: ByteArray): File {
    val parent = requireNotNull(parentFile)
    check(parent.mkdirs() || parent.isDirectory)
    writeBytes(bytes)
    return this
}
