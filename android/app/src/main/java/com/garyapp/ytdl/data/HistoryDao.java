package com.garyapp.ytdl.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public abstract class HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insertRaw(HistoryItemEntity item);

    @Query("SELECT * FROM history_items ORDER BY completedAt DESC, id DESC LIMIT :limit")
    public abstract List<HistoryItemEntity> listRecent(int limit);

    public long insert(HistoryItemEntity item) {
        return insertRaw(item.sanitizedCopy());
    }
}
