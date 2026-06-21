package com.garyapp.ytdl.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.garyapp.ytdl.download.DownloadTaskState;

import java.util.List;

@Dao
public abstract class HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insertRaw(HistoryItemEntity item);

    @Query("SELECT * FROM history_items ORDER BY completedAt DESC, id DESC LIMIT :limit")
    public abstract List<HistoryItemEntity> listRecent(int limit);

    @Query("DELETE FROM history_items WHERE id = :id")
    public abstract int deleteById(long id);

    public long insert(HistoryItemEntity item) {
        return insertRaw(item.sanitizedCopy());
    }

    public long insertFromTaskState(
            DownloadTaskState state,
            String formatSummary,
            long eventTime
    ) {
        return insert(HistoryItemEntity.fromTaskState(state, formatSummary, eventTime));
    }
}
