package com.garyapp.ytdl.data;

import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.garyapp.ytdl.download.TaskOutputTypeKt;

@Entity(tableName = "queue_items", indices = {@Index(value = {"taskId"}, unique = true)})
public class QueueItemEntity {
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELED = "canceled";
    public static final String STATUS_NETWORK_FAILED = "network_failed";
    public static final String STATUS_PERMISSION_FAILED = "permission_failed";

    @PrimaryKey(autoGenerate = true)
    public long id;
    public String outputType;
    public String title;
    public long durationSeconds;
    public String sourceScheme;
    public String sourceHostHash;
    public String sourceCategory;
    public String outputUri;
    public String formatSummary;
    public String status;
    public int progress;
    public String speed;
    public String eta;
    public String errorSummary;
    public long createdAt;
    public long updatedAt;
    public String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
    @ColumnInfo(defaultValue = "0")
    public long queueOrder;
    public String phase;
    public String stopReason;
    @ColumnInfo(defaultValue = "0")
    public long downloadedBytes;
    public Long totalBytes;
    @ColumnInfo(defaultValue = "0")
    public long checkpointAt;

    public QueueItemEntity() {
    }

    @Ignore
    public QueueItemEntity(
            long id,
            String title,
            long durationSeconds,
            String sourceScheme,
            String sourceHostHash,
            String sourceCategory,
            String outputUri,
            String formatSummary,
            String status,
            int progress,
            String speed,
            String eta,
            String errorSummary,
            long createdAt,
            long updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.durationSeconds = durationSeconds;
        this.sourceScheme = sourceScheme;
        this.sourceHostHash = sourceHostHash;
        this.sourceCategory = sourceCategory;
        this.outputUri = outputUri;
        this.formatSummary = formatSummary;
        this.status = status;
        this.progress = progress;
        this.speed = speed;
        this.eta = eta;
        this.errorSummary = errorSummary;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static QueueItemEntity createSafe(
            String title,
            long durationSeconds,
            String sourceScheme,
            String sourceHostHash,
            String sourceCategory,
            String outputUri,
            String formatSummary,
            String status,
            int progress,
            String speed,
            String eta,
            String errorSummary,
            long createdAt,
            long updatedAt
    ) {
        return new QueueItemEntity(
                0,
                PersistenceSanitizer.clean(title),
                durationSeconds,
                PersistenceSanitizer.clean(sourceScheme),
                sourceHostHash,
                PersistenceSanitizer.clean(sourceCategory),
                PersistenceSanitizer.clean(outputUri),
                PersistenceSanitizer.clean(formatSummary),
                status,
                progress,
                PersistenceSanitizer.clean(speed),
                PersistenceSanitizer.clean(eta),
                PersistenceSanitizer.clean(errorSummary),
                createdAt,
                updatedAt
        );
    }

    QueueItemEntity sanitizedCopy() {
        if (taskId == null || !taskId.matches("^[0-9a-f]{32}$")) {
            throw new IllegalArgumentException("任务标识不可用。");
        }
        if (downloadedBytes < 0 || (totalBytes != null && totalBytes < 0) || checkpointAt < 0) {
            throw new IllegalArgumentException("任务进度不可用。");
        }
        QueueItemEntity copy = new QueueItemEntity(
                id,
                PersistenceSanitizer.clean(title),
                durationSeconds,
                PersistenceSanitizer.clean(sourceScheme),
                PersistenceSanitizer.clean(sourceHostHash),
                PersistenceSanitizer.clean(sourceCategory),
                PersistenceSanitizer.clean(outputUri),
                PersistenceSanitizer.clean(formatSummary),
                status,
                progress,
                PersistenceSanitizer.clean(speed),
                PersistenceSanitizer.clean(eta),
                PersistenceSanitizer.clean(errorSummary),
                createdAt,
                updatedAt
        );
        copy.taskId = taskId;
        copy.outputType = TaskOutputTypeKt.taskOutputTypeFromStored(outputType) != null ? outputType : null;
        copy.queueOrder = queueOrder;
        copy.phase = safeState(phase);
        copy.stopReason = safeState(stopReason);
        copy.downloadedBytes = downloadedBytes;
        copy.totalBytes = totalBytes;
        copy.checkpointAt = checkpointAt;
        return copy;
    }

    private static String safeState(String value) {
        if (value != null && !value.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("任务状态不可用。");
        }
        return value;
    }

    @Override
    public String toString() {
        return "QueueItemEntity{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", durationSeconds=" + durationSeconds +
                ", sourceScheme='" + sourceScheme + '\'' +
                ", sourceHostHash='" + sourceHostHash + '\'' +
                ", sourceCategory='" + sourceCategory + '\'' +
                ", outputUri='" + outputUri + '\'' +
                ", formatSummary='" + formatSummary + '\'' +
                ", status='" + status + '\'' +
                ", progress=" + progress +
                ", speed='" + speed + '\'' +
                ", eta='" + eta + '\'' +
                ", errorSummary='" + errorSummary + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
