package com.garyapp.ytdl.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "history_items")
public class HistoryItemEntity {
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELED = "canceled";

    @PrimaryKey(autoGenerate = true)
    public long id;
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
    public long completedAt;

    public HistoryItemEntity() {
    }

    @Ignore
    public HistoryItemEntity(
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
            long updatedAt,
            long completedAt
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
        this.completedAt = completedAt;
    }

    public static HistoryItemEntity createSafe(
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
            long updatedAt,
            long completedAt
    ) {
        return new HistoryItemEntity(
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
                updatedAt,
                completedAt
        );
    }

    HistoryItemEntity sanitizedCopy() {
        return new HistoryItemEntity(
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
                updatedAt,
                completedAt
        );
    }

    @Override
    public String toString() {
        return "HistoryItemEntity{" +
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
                ", completedAt=" + completedAt +
                '}';
    }
}
