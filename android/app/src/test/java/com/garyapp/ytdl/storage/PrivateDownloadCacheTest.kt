package com.garyapp.ytdl.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.data.QueueItemEntity
import com.garyapp.ytdl.data.YtdlDatabase
import com.garyapp.ytdl.testing.ProjectTestPaths
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
    fun inspectAndClearDeleteOnlyPrivateChildrenAndBrokenRecords() {
        val media = File(privateRoot, "task-1/video.mp4").writeBytesAfterCreating(byteArrayOf(1, 2, 3))
        val subtitle = File(privateRoot, "task-1/video.zh.srt").writeBytesAfterCreating(byteArrayOf(4, 5, 6, 7))
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

        assertEquals(CacheStats(bytes = 7, fileCount = 2), cache.inspect())
        val result = cache.clear()

        assertEquals(
            CacheClearResult.Success(
                freedBytes = 7,
                deletedFileCount = 2,
                removedQueueRecordCount = 1,
                removedHistoryRecordCount = 2,
            ),
            result,
        )
        assertTrue(privateRoot.isDirectory)
        assertTrue(privateRoot.listFiles().orEmpty().isEmpty())
        assertTrue(externalCopy.isFile)
        assertEquals(listOf("外部队列"), database.queueDao().listAll().map { it.title })
        assertEquals(listOf("外部历史"), database.historyDao().listAll().map { it.title })
    }

    @Test
    fun missingCacheIsZeroItemSuccessAndCreatesPreservedRoot() {
        assertFalse(privateRoot.exists())
        val cache = PrivateDownloadCache(privateRoot, database.queueDao(), database.historyDao())

        assertEquals(CacheStats(bytes = 0, fileCount = 0), cache.inspect())
        assertEquals(
            CacheClearResult.Success(0, 0, 0, 0),
            cache.clear(),
        )
        assertTrue(privateRoot.isDirectory)
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
