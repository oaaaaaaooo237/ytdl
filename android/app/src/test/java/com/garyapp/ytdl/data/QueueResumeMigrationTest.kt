package com.garyapp.ytdl.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.testing.ProjectTestPaths
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueResumeMigrationTest {
    @Test fun sameTaskIdentityCannotCreateTwoQueueRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, YtdlDatabase::class.java).allowMainThreadQueries().build()
        try {
            val first = QueueItemEntity().apply { status = "paused" }
            db.queueDao().insert(first)
            db.queueDao().insert(QueueItemEntity().apply { taskId = first.taskId; status = "queued" })
            assertEquals(1, db.queueDao().listAll().size)
        } finally { db.close() }
    }

    @Test fun queueDaoPreservesResumeMetadataAndOrderThroughInsertAndUpdate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, YtdlDatabase::class.java).allowMainThreadQueries().build()
        try {
            val row = QueueItemEntity.createSafe("任务", 0, "https", "hash", "web", null, "原始", "paused", 20, "", "", "", 1, 2)
            row.queueOrder = 5
            row.phase = "DownloadingAudio"
            row.stopReason = "UserPause"
            row.downloadedBytes = 42
            row.totalBytes = 100L
            row.checkpointAt = 12
            row.id = db.queueDao().insert(row)
            val second = QueueItemEntity().apply { status = "queued"; queueOrder = 2 }
            db.queueDao().insert(second)
            db.queueDao().insert(QueueItemEntity().apply { status = "completed"; queueOrder = 1 })
            val restored = db.queueDao().getByTaskId(row.taskId)
            assertEquals(42L, restored.downloadedBytes)
            assertEquals(100L, restored.totalBytes)
            assertEquals("UserPause", restored.stopReason)
            assertEquals("DownloadingAudio", restored.phase)
            assertEquals(12L, restored.checkpointAt)
            restored.downloadedBytes = 50
            db.queueDao().update(restored)
            assertEquals(50L, db.queueDao().getByTaskId(row.taskId).downloadedBytes)
            assertEquals(listOf(second.taskId, row.taskId), db.queueDao().listNonTerminal().map { it.taskId })
            assertEquals(1, db.queueDao().deleteByTaskId(row.taskId))
            assertNotNull(db.queueDao().getByTaskId(second.taskId))
        } finally { db.close() }
    }

    @Test fun version3MigrationKeepsOldQueueAndHistoryAndAddsResumeFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = ProjectTestPaths.createTempDirectory("queue-migration-").resolve("version3.db").toString()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(path).callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val base = "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT, durationSeconds INTEGER NOT NULL, " +
                        "sourceScheme TEXT, sourceHostHash TEXT, sourceCategory TEXT, outputUri TEXT, formatSummary TEXT, " +
                        "status TEXT, progress INTEGER NOT NULL, speed TEXT, eta TEXT, errorSummary TEXT, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL"
                    db.execSQL("CREATE TABLE queue_items ($base)")
                    db.execSQL("CREATE TABLE history_items ($base, completedAt INTEGER NOT NULL, thumbnailUrl TEXT, subtitleOutputUris TEXT)")
                    db.execSQL("INSERT INTO queue_items(id,title,durationSeconds,status,progress,createdAt,updatedAt) VALUES(7,'旧队列',8,'queued',23,10,20)")
                    db.execSQL("INSERT INTO history_items(id,title,durationSeconds,status,progress,createdAt,updatedAt,completedAt) VALUES(9,'旧历史',8,'completed',100,10,20,30)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val migration = YtdlDatabase::class.java.fields.firstOrNull { it.name == "MIGRATION_3_4" }
        assertNotNull("Room 3 to 4 migration is required", migration)
        val database = Room.databaseBuilder(context, YtdlDatabase::class.java, path)
            .addMigrations(migration!!.get(null) as androidx.room.migration.Migration).allowMainThreadQueries().build()
        try {
            val row = database.queueDao().getById(7)
            assertEquals("旧队列", row.title)
            assertEquals(23, row.progress)
            assertEquals("旧历史", database.historyDao().listRecent(1).single().title)
            val taskField = QueueItemEntity::class.java.getField("taskId")
            assertTrue((taskField.get(row) as String).matches(Regex("^[0-9a-f]{32}$")))
            assertEquals(7L, QueueItemEntity::class.java.getField("queueOrder").getLong(row))
        } finally { database.close() }
    }
}
