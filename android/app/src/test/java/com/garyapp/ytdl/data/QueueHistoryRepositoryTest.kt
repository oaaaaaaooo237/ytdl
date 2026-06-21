package com.garyapp.ytdl.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueHistoryRepositoryTest {
    private lateinit var database: YtdlDatabase
    private lateinit var queueDao: QueueDao
    private lateinit var historyDao: HistoryDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YtdlDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queueDao = database.queueDao()
        historyDao = database.historyDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndUpdateQueueItemKeepsSafeSourceSummaryOnly() {
        val item = sampleQueueItem(status = QueueItemEntity.STATUS_QUEUED)

        val id = queueDao.insert(item)
        queueDao.update(
            sampleQueueItem(
                id = id,
                status = QueueItemEntity.STATUS_RUNNING,
                progress = 42,
                speed = "1.2 MB/s",
                eta = "00:30",
                updatedAt = 2_000,
            ),
        )

        val stored = queueDao.getById(id)

        require(stored != null)
        assertEquals("示例视频", stored.title)
        assertEquals("https", stored.sourceScheme)
        assertEquals("abc123safehash", stored.sourceHostHash)
        assertEquals("youtube", stored.sourceCategory)
        assertEquals(QueueItemEntity.STATUS_RUNNING, stored.status)
        assertEquals(42, stored.progress)
        assertEquals("1.2 MB/s", stored.speed)
        assertEquals("00:30", stored.eta)
        assertFalse(stored.toString().contains("watch?v="))
        assertFalse(stored.toString().contains("SID="))
    }

    @Test
    fun completeAndFailQueueItemsAreListedByUpdatedTime() {
        val completedId = queueDao.insert(
            sampleQueueItem(
                title = "已完成",
                status = QueueItemEntity.STATUS_RUNNING,
                updatedAt = 1_000,
            ),
        )
        val failedId = queueDao.insert(
            sampleQueueItem(
                title = "失败",
                status = QueueItemEntity.STATUS_RUNNING,
                updatedAt = 2_000,
            ),
        )

        queueDao.markCompleted(
            completedId,
            "content://media/downloads/video.mp4",
            3_000,
        )
        queueDao.markFailed(
            failedId,
            "网络超时，请稍后重试",
            4_000,
        )

        val rows = queueDao.listAll()

        assertEquals(listOf("失败", "已完成"), rows.map { it.title })
        assertEquals(QueueItemEntity.STATUS_FAILED, rows[0].status)
        assertEquals("网络超时，请稍后重试", rows[0].errorSummary)
        assertEquals(QueueItemEntity.STATUS_COMPLETED, rows[1].status)
        assertEquals(100, rows[1].progress)
        assertEquals("content://media/downloads/video.mp4", rows[1].outputUri)
    }

    @Test
    fun historyListingUsesSafeFieldsAndNewestFirst() {
        historyDao.insert(
            sampleHistoryItem(
                title = "较早记录",
                status = HistoryItemEntity.STATUS_COMPLETED,
                createdAt = 1_000,
                completedAt = 2_000,
            ),
        )
        historyDao.insert(
            sampleHistoryItem(
                title = "较新记录",
                status = HistoryItemEntity.STATUS_FAILED,
                createdAt = 3_000,
                completedAt = 4_000,
                errorSummary = "格式不可用，已隐藏敏感参数",
            ),
        )

        val rows = historyDao.listRecent(10)

        assertEquals(listOf("较新记录", "较早记录"), rows.map { it.title })
        assertEquals(HistoryItemEntity.STATUS_FAILED, rows.first().status)
        assertEquals("格式不可用，已隐藏敏感参数", rows.first().errorSummary)
        assertFalse(rows.joinToString().contains("access_token="))
        assertFalse(rows.joinToString().contains("--cookies"))
    }

    @Test
    fun historyDeleteRemovesOnlySelectedRecord() {
        val olderId = historyDao.insert(
            sampleHistoryItem(
                title = "保留记录",
                status = HistoryItemEntity.STATUS_COMPLETED,
                createdAt = 1_000,
                completedAt = 1_000,
            ),
        )
        val newerId = historyDao.insert(
            sampleHistoryItem(
                title = "删除记录",
                status = HistoryItemEntity.STATUS_FAILED,
                createdAt = 2_000,
                completedAt = 2_000,
            ),
        )

        assertEquals(1, historyDao.deleteById(newerId))

        val rows = historyDao.listRecent(10)
        assertEquals(listOf("保留记录"), rows.map { it.title })
        assertEquals(olderId, rows.single().id)
    }

    @Test
    fun queueEntitySanitizesFreeTextBeforePersistence() {
        val id = queueDao.insert(
            QueueItemEntity(
                0,
                "标题 Cookie: SID=secret",
                180,
                "https",
                "abc123safehash",
                "youtube",
                "content://downloads/video.mp4?access_token=secret-token",
                "1080p --cookies D:/private/cookies.txt Authorization: Bearer abc",
                QueueItemEntity.STATUS_FAILED,
                0,
                "",
                "",
                "yt-dlp failed with Cookie: SID=secret and access_token=secret-token",
                1_000,
                2_000,
            ),
        )

        val stored = queueDao.getById(id).toString()

        assertFalse(stored.contains("SID=secret"))
        assertFalse(stored.contains("secret-token"))
        assertFalse(stored.contains("--cookies"))
        assertFalse(stored.contains("Bearer abc"))
        assertTrue(stored.contains("[已隐藏]"))
    }

    @Test
    fun historyEntitySanitizesFreeTextBeforePersistence() {
        historyDao.insert(
            HistoryItemEntity(
                0,
                "历史 Authorization: Bearer abc",
                180,
                "https",
                "abc123safehash",
                "youtube",
                "content://media/downloads/video.mp4?jwt=secret-jwt",
                "720p --cookies cookies.txt",
                HistoryItemEntity.STATUS_FAILED,
                0,
                "",
                "",
                "download failed Cookie: SID=secret",
                1_000,
                2_000,
                2_000,
            ),
        )

        val stored = historyDao.listRecent(10).joinToString()

        assertFalse(stored.contains("Bearer abc"))
        assertFalse(stored.contains("secret-jwt"))
        assertFalse(stored.contains("--cookies"))
        assertFalse(stored.contains("SID=secret"))
        assertTrue(stored.contains("[已隐藏]"))
    }

    @Test
    fun persistenceSanitizerRemovesQuotedCookiesPathWithSpaces() {
        val id = queueDao.insert(
            QueueItemEntity(
                0,
                "示例视频",
                180,
                "https",
                "abc123safehash",
                "youtube",
                "content://downloads/video.mp4",
                "yt-dlp --cookies \"C:/Users/Gary R/private cookies/cookies.txt\"",
                QueueItemEntity.STATUS_FAILED,
                0,
                "",
                "",
                "failed with --cookies 'D:/private cookie folder/cookies.txt'",
                1_000,
                2_000,
            ),
        )

        val stored = queueDao.getById(id).toString()

        assertFalse(stored.contains("Gary R"))
        assertFalse(stored.contains("private cookies"))
        assertFalse(stored.contains("private cookie folder"))
        assertFalse(stored.contains("cookies.txt"))
        assertFalse(stored.contains("--cookies"))
        assertTrue(stored.contains("[已隐藏]"))
    }

    @Test
    fun entitiesDoNotDeclareSensitivePersistenceFields() {
        val queueFieldNames = QueueItemEntity::class.java.declaredFields.map { it.name.lowercase(Locale.ROOT) }
        val historyFieldNames = HistoryItemEntity::class.java.declaredFields.map { it.name.lowercase(Locale.ROOT) }
        val allFieldNames = queueFieldNames + historyFieldNames

        assertTrue("sourceScheme" in QueueItemEntity::class.java.declaredFields.map { it.name })
        assertTrue("sourceHostHash" in QueueItemEntity::class.java.declaredFields.map { it.name })
        assertFalse(allFieldNames.any { it.contains("rawurl") })
        assertFalse(allFieldNames.any { it.contains("cookie") })
        assertFalse(allFieldNames.any { it.contains("header") })
        assertFalse(allFieldNames.any { it.contains("command") })
    }

    private fun sampleQueueItem(
        id: Long = 0,
        title: String = "示例视频",
        status: String,
        outputUri: String = "content://downloads/pending.mp4",
        progress: Int = 0,
        speed: String = "",
        eta: String = "",
        errorSummary: String? = null,
        updatedAt: Long = 1_000,
    ): QueueItemEntity = QueueItemEntity(
        id,
        title,
        180,
        "https",
        "abc123safehash",
        "youtube",
        outputUri,
        "1080p MP4 + AAC",
        status,
        progress,
        speed,
        eta,
        errorSummary,
        1_000,
        updatedAt,
    )

    private fun sampleHistoryItem(
        title: String,
        status: String,
        createdAt: Long,
        completedAt: Long,
        errorSummary: String? = null,
    ): HistoryItemEntity = HistoryItemEntity(
        0,
        title,
        180,
        "https",
        "abc123safehash",
        "youtube",
        "content://media/downloads/video.mp4",
        "1080p MP4 + AAC",
        status,
        if (status == HistoryItemEntity.STATUS_COMPLETED) 100 else 0,
        "",
        "",
        errorSummary,
        createdAt,
        completedAt,
        completedAt,
    )
}
