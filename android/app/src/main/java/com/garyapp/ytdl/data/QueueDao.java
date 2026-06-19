package com.garyapp.ytdl.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public abstract class QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insertRaw(QueueItemEntity item);

    @Update
    protected abstract void updateRaw(QueueItemEntity item);

    @Query("SELECT * FROM queue_items WHERE id = :id")
    public abstract QueueItemEntity getById(long id);

    @Query("SELECT * FROM queue_items ORDER BY updatedAt DESC, id DESC")
    public abstract List<QueueItemEntity> listAll();

    public long insert(QueueItemEntity item) {
        return insertRaw(item.sanitizedCopy());
    }

    public void update(QueueItemEntity item) {
        updateRaw(item.sanitizedCopy());
    }

    @Query(
            "UPDATE queue_items " +
                    "SET status = :status, progress = 100, outputUri = :outputUri, " +
                    "errorSummary = NULL, updatedAt = :updatedAt " +
                    "WHERE id = :id"
    )
    protected abstract void markCompletedWithStatusRaw(long id, String outputUri, long updatedAt, String status);

    public void markCompleted(long id, String outputUri, long updatedAt) {
        markCompletedWithStatusRaw(
                id,
                PersistenceSanitizer.clean(outputUri),
                updatedAt,
                QueueItemEntity.STATUS_COMPLETED
        );
    }

    @Query(
            "UPDATE queue_items " +
                    "SET status = :status, errorSummary = :errorSummary, updatedAt = :updatedAt " +
                    "WHERE id = :id"
    )
    protected abstract void markFailedWithStatusRaw(long id, String errorSummary, long updatedAt, String status);

    public void markFailed(long id, String errorSummary, long updatedAt) {
        markFailedWithStatusRaw(
                id,
                PersistenceSanitizer.clean(errorSummary),
                updatedAt,
                QueueItemEntity.STATUS_FAILED
        );
    }
}
