package com.garyapp.ytdl.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.garyapp.ytdl.core.policy.SafeUrlSummary;
import com.garyapp.ytdl.core.policy.UrlPolicy;
import com.garyapp.ytdl.core.policy.UrlPolicyResult;
import com.garyapp.ytdl.download.DownloadFailureMessages;
import com.garyapp.ytdl.download.DownloadOutputFile;
import com.garyapp.ytdl.download.DownloadOutputKind;
import com.garyapp.ytdl.download.DownloadRequest;
import com.garyapp.ytdl.download.DownloadStage;
import com.garyapp.ytdl.download.DownloadTaskState;
import com.garyapp.ytdl.storage.ExportController;

import java.io.File;
import java.net.URI;
import java.util.List;

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

    public static HistoryItemEntity fromTaskState(
            DownloadTaskState state,
            String formatSummary,
            long eventTime
    ) {
        DownloadRequest request = state.getRequest();
        if (request == null) {
            throw new IllegalArgumentException("缺少下载请求，不能生成历史记录。");
        }

        DownloadStage stage = state.getStage();
        String status = terminalStatus(stage);
        UrlPolicyResult policy = UrlPolicy.INSTANCE.evaluate(request.getUrl());
        SafeUrlSummary safeUrl = policy.getSafeUrlSummary();
        String sourceScheme = safeUrl != null ? safeUrl.getScheme() : "";
        String sourceHostHash = safeUrl != null ? safeUrl.getHostHash() : "";
        String sourceCategory = sourceCategoryFor(request.getUrl(), policy.getLogSummary().getCategory());
        if (stage == DownloadStage.Completed && !hasValidMediaOutput(state.getOutputs())) {
            throw new IllegalArgumentException("缺少有效媒体输出，不能生成完成历史记录。");
        }
        String outputUri = safeOutputUri(state.getOutputs());
        String errorSummary = terminalErrorSummary(stage, state.getErrorMessage());
        int progress = terminalProgress(stage, state);

        return createSafe(
                request.getTitle(),
                0,
                sourceScheme,
                sourceHostHash,
                sourceCategory,
                outputUri,
                formatSummary,
                status,
                progress,
                "",
                "",
                errorSummary,
                eventTime,
                eventTime,
                eventTime
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

    private static String terminalStatus(DownloadStage stage) {
        if (stage == DownloadStage.Completed) {
            return STATUS_COMPLETED;
        }
        if (stage == DownloadStage.Failed) {
            return STATUS_FAILED;
        }
        if (stage == DownloadStage.Canceled) {
            return STATUS_CANCELED;
        }
        throw new IllegalArgumentException("只有完成、失败或取消任务可以写入历史。");
    }

    private static int terminalProgress(DownloadStage stage, DownloadTaskState state) {
        if (stage == DownloadStage.Completed) {
            return 100;
        }
        if (state.getProgress() != null && state.getProgress().getPercent() != null) {
            double percent = state.getProgress().getPercent();
            return (int) Math.max(0, Math.min(99, Math.round(percent)));
        }
        return 0;
    }

    private static String terminalErrorSummary(DownloadStage stage, String rawError) {
        if (stage == DownloadStage.Canceled) {
            return DownloadFailureMessages.INSTANCE.canceled();
        }
        if (stage == DownloadStage.Failed) {
            return DownloadFailureMessages.INSTANCE.fromErrorText(rawError);
        }
        return null;
    }

    private static String safeOutputUri(List<DownloadOutputFile> outputs) {
        for (DownloadOutputFile output : outputs) {
            if (output.getKind() == DownloadOutputKind.Media) {
                return ExportController.appPrivateOutputUri(output.getPath(), output.getAppPrivateRootPath());
            }
        }
        return "";
    }

    private static boolean hasValidMediaOutput(List<DownloadOutputFile> outputs) {
        for (DownloadOutputFile output : outputs) {
            File file = new File(output.getPath());
            if (
                    output.getKind() == DownloadOutputKind.Media
                            && output.getBytesWritten() > 0L
                            && file.isFile()
                            && file.length() > 0L
            ) {
                return true;
            }
        }
        return false;
    }

    private static String sourceCategoryFor(String url, String fallback) {
        try {
            String host = new URI(url).getHost();
            if (host == null) {
                return fallback;
            }
            String normalized = host.toLowerCase();
            if (normalized.equals("youtu.be") || normalized.endsWith(".youtube.com") || normalized.equals("youtube.com")) {
                return "youtube";
            }
            return "web";
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
