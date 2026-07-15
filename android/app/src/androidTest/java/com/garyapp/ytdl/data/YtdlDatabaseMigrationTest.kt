package com.garyapp.ytdl.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YtdlDatabaseMigrationTest {
    @Test
    fun migration1To3AddsThumbnailAndSubtitleOutputUrisWithoutDroppingHistoryRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-1-2-${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit
                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )

        helper.writableDatabase.use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `queue_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT,
                    `durationSeconds` INTEGER NOT NULL,
                    `sourceScheme` TEXT,
                    `sourceHostHash` TEXT,
                    `sourceCategory` TEXT,
                    `outputUri` TEXT,
                    `formatSummary` TEXT,
                    `status` TEXT,
                    `progress` INTEGER NOT NULL,
                    `speed` TEXT,
                    `eta` TEXT,
                    `errorSummary` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `history_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT,
                    `durationSeconds` INTEGER NOT NULL,
                    `sourceScheme` TEXT,
                    `sourceHostHash` TEXT,
                    `sourceCategory` TEXT,
                    `outputUri` TEXT,
                    `formatSummary` TEXT,
                    `status` TEXT,
                    `progress` INTEGER NOT NULL,
                    `speed` TEXT,
                    `eta` TEXT,
                    `errorSummary` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO history_items (
                    title, durationSeconds, sourceScheme, sourceHostHash, sourceCategory,
                    outputUri, formatSummary, status, progress, speed, eta, errorSummary,
                    createdAt, updatedAt, completedAt
                ) VALUES (
                    '旧历史', 8, 'https', 'old-host', 'youtube',
                    'app-private://outputs/task-old/merged.mp4', '视频+音频',
                    'completed', 100, '', '', '', 1, 2, 3
                )
                """.trimIndent(),
            )
        }

        helper.close()
        val migratedRoom = Room.databaseBuilder(context, YtdlDatabase::class.java, dbName)
            .addMigrations(HistoryItemEntity.MIGRATION_1_2, migration2To3())
            .allowMainThreadQueries()
            .build()
        try {
            val row = migratedRoom.historyDao().listRecent(1).single()
            assertEquals("旧历史", row.title)
            assertTrue(row.thumbnailUrl == null)
            assertTrue(subtitleOutputUris(row) == null)
        } finally {
            migratedRoom.close()
        }
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration2To3AddsSubtitleOutputUrisWithoutDroppingHistoryRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-2-3-${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit
                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )

        helper.writableDatabase.use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `queue_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT,
                    `durationSeconds` INTEGER NOT NULL,
                    `sourceScheme` TEXT,
                    `sourceHostHash` TEXT,
                    `sourceCategory` TEXT,
                    `outputUri` TEXT,
                    `formatSummary` TEXT,
                    `status` TEXT,
                    `progress` INTEGER NOT NULL,
                    `speed` TEXT,
                    `eta` TEXT,
                    `errorSummary` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `history_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT,
                    `durationSeconds` INTEGER NOT NULL,
                    `sourceScheme` TEXT,
                    `sourceHostHash` TEXT,
                    `sourceCategory` TEXT,
                    `outputUri` TEXT,
                    `formatSummary` TEXT,
                    `thumbnailUrl` TEXT,
                    `status` TEXT,
                    `progress` INTEGER NOT NULL,
                    `speed` TEXT,
                    `eta` TEXT,
                    `errorSummary` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO history_items (
                    title, durationSeconds, sourceScheme, sourceHostHash, sourceCategory,
                    outputUri, formatSummary, thumbnailUrl, status, progress, speed, eta,
                    errorSummary, createdAt, updatedAt, completedAt
                ) VALUES (
                    '旧 v2 历史', 8, 'https', 'old-host', 'youtube',
                    'app-private://outputs/task-old/merged.mp4', '视频+音频',
                    NULL, 'completed', 100, '', '', '', 1, 2, 3
                )
                """.trimIndent(),
            )
        }

        helper.close()
        val migratedRoom = Room.databaseBuilder(context, YtdlDatabase::class.java, dbName)
            .addMigrations(migration2To3())
            .allowMainThreadQueries()
            .build()
        try {
            val row = migratedRoom.historyDao().listRecent(1).single()
            assertEquals("旧 v2 历史", row.title)
            assertEquals("app-private://outputs/task-old/merged.mp4", row.outputUri)
            assertTrue(subtitleOutputUris(row) == null)
        } finally {
            migratedRoom.close()
        }
        context.deleteDatabase(dbName)
    }

    private fun migration2To3(): Migration {
        val field = HistoryItemEntity::class.java.getDeclaredField("MIGRATION_2_3")
        field.isAccessible = true
        return field.get(null) as Migration
    }

    private fun subtitleOutputUris(row: HistoryItemEntity): String? {
        val field = HistoryItemEntity::class.java.getDeclaredField("subtitleOutputUris")
        field.isAccessible = true
        return field.get(row) as String?
    }
}
