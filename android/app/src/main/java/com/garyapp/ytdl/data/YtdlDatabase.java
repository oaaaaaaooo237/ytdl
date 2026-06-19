package com.garyapp.ytdl.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                QueueItemEntity.class,
                HistoryItemEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class YtdlDatabase extends RoomDatabase {
    public abstract QueueDao queueDao();

    public abstract HistoryDao historyDao();
}
