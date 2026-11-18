package com.garyapp.ytdl.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                QueueItemEntity.class,
                HistoryItemEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class YtdlDatabase extends RoomDatabase {
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE queue_items ADD COLUMN taskId TEXT");
            database.execSQL("ALTER TABLE queue_items ADD COLUMN queueOrder INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE queue_items ADD COLUMN phase TEXT");
            database.execSQL("ALTER TABLE queue_items ADD COLUMN stopReason TEXT");
            database.execSQL("ALTER TABLE queue_items ADD COLUMN downloadedBytes INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE queue_items ADD COLUMN totalBytes INTEGER");
            database.execSQL("ALTER TABLE queue_items ADD COLUMN checkpointAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE queue_items SET taskId = lower(hex(randomblob(16))), queueOrder = id");
            database.execSQL("CREATE UNIQUE INDEX index_queue_items_taskId ON queue_items(taskId)");
        }
    };

    public abstract QueueDao queueDao();

    public abstract HistoryDao historyDao();
}
