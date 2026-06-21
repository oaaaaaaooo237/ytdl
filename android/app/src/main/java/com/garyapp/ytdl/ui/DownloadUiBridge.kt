package com.garyapp.ytdl.ui

import android.content.Context
import com.garyapp.ytdl.cookies.CookiesReference
import com.garyapp.ytdl.cookies.TemporaryCookiesFile
import com.garyapp.ytdl.core.privacy.SensitiveText
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadStage
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun buildAppliedDownloadRequest(
    url: String,
    analysis: VideoAnalysis?,
    appliedSelection: FormatSelection,
    selectedSubtitles: List<SubtitleInfo> = emptyList(),
    cookiesPath: String? = null,
): Result<DownloadRequest> {
    return runCatching {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            throw IllegalArgumentException("请先输入公开视频页面地址。")
        }
        val currentAnalysis = analysis
            ?: throw IllegalStateException("请先分析视频。")

        DownloadRequest.fromAnalysis(
            url = normalizedUrl,
            analysis = currentAnalysis,
            selection = appliedSelection,
            selectedSubtitles = selectedSubtitles,
            cookiesPath = cookiesPath,
        ).getOrThrow()
    }
}

data class HistoryUiItem(
    val id: Long,
    val title: String,
    val meta: String,
    val badge: String,
    val outputUri: String,
    val status: String,
    val completedAt: Long,
) {
    val hasOutput: Boolean
        get() = outputUri.startsWith("app-private://outputs/") && status == HistoryItemEntity.STATUS_COMPLETED
}

fun historyUiItemsFromRows(rows: List<HistoryItemEntity>): List<HistoryUiItem> {
    return rows.map { row ->
        HistoryUiItem(
            id = row.id,
            title = redactHistoryUiText(row.title.orEmpty()).ifBlank { "未命名任务" },
            meta = historyMeta(row),
            badge = historyBadge(row.status.orEmpty()),
            outputUri = redactHistoryUiText(row.outputUri.orEmpty()),
            status = row.status.orEmpty(),
            completedAt = row.completedAt,
        )
    }
}

fun prepareTemporaryCookiesForDownload(
    settingsReference: com.garyapp.ytdl.core.settings.CookiesReference?,
    context: Context,
    taskId: String,
): Result<TemporaryCookiesFile?> {
    return runCatching {
        val reference = CookiesReference.fromSettings(settingsReference) ?: return@runCatching null
        TemporaryCookiesFile.materialize(
            context = context,
            reference = reference,
            taskId = taskId,
        ).getOrThrow()
    }
}

fun prepareTemporaryCookiesForDownload(
    settingsReference: com.garyapp.ytdl.core.settings.CookiesReference?,
    privateCacheDirectory: File,
    taskId: String,
): Result<TemporaryCookiesFile?> {
    return runCatching {
        val reference = CookiesReference.fromSettings(settingsReference) ?: return@runCatching null
        TemporaryCookiesFile.materialize(
            reference = reference,
            privateCacheDirectory = privateCacheDirectory,
            taskId = taskId,
        ).getOrThrow()
    }
}

private fun historyMeta(row: HistoryItemEntity): String {
    val parts = listOfNotNull(
        row.formatSummary?.takeIf { it.isNotBlank() },
        row.sourceCategory?.takeIf { it.isNotBlank() },
        row.completedAt.takeIf { it > 0L }?.let { formatHistoryTime(it) },
        row.outputUri?.takeIf { it.isNotBlank() }?.let(::historyOutputLabel),
        row.errorSummary?.takeIf { it.isNotBlank() },
    )
    return redactHistoryUiText(parts.joinToString(" · ")).ifBlank { "本地记录" }
}

private fun historyOutputLabel(outputUri: String): String {
    val leaf = outputUri.substringAfterLast('/').substringBefore('?').trim()
    return runCatching {
        URLDecoder.decode(leaf, Charsets.UTF_8.name())
    }.getOrDefault(leaf).ifBlank { "本地文件" }
}

private fun formatHistoryTime(timestampMillis: Long): String {
    return SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(Date(timestampMillis))
}

fun historyActionLabels(item: HistoryUiItem): List<String> {
    return if (item.hasOutput) {
        listOf("打开", "分享", "导出", "删除")
    } else {
        listOf("删除")
    }
}

private fun historyBadge(status: String): String {
    return when (status) {
        HistoryItemEntity.STATUS_COMPLETED -> "完成"
        HistoryItemEntity.STATUS_FAILED -> "失败"
        HistoryItemEntity.STATUS_CANCELED -> "取消"
        else -> "记录"
    }
}

private fun redactHistoryUiText(value: String): String {
    return SensitiveText.redact(value)
        .replace(Regex("""(?i)\b(authorization|cookie|cookies)\b"""), "[已隐藏]")
}

fun userVisibleDownloadStatus(stage: DownloadStage): String {
    return when (stage) {
        DownloadStage.Idle -> "空闲"
        DownloadStage.Analyzing -> "分析中"
        DownloadStage.Waiting -> "等待中"
        DownloadStage.DownloadingVideo -> "下载视频"
        DownloadStage.DownloadingAudio -> "下载音频"
        DownloadStage.DownloadingSubtitles -> "下载字幕"
        DownloadStage.Merging -> "原生合并"
        DownloadStage.Exporting -> "导出待接入"
        DownloadStage.Completed -> "下载完成"
        DownloadStage.Failed -> "下载失败"
        DownloadStage.Canceled -> "已取消"
    }
}

fun settingsParserVersionLabel(): String = "yt-dlp ${YtdlpBridge.PINNED_YTDLP_VERSION}"

fun settingsMediaProcessorLabel(): String = "原生合并 · 字幕独立文件 · 字幕嵌入/烧录属 MVP2"

fun subtitleSelectionLabel(
    analysis: VideoAnalysis?,
    selectedSubtitles: List<SubtitleInfo>,
): String {
    if (analysis == null) return "本阶段默认不下载字幕"
    if (analysis.subtitles.isEmpty()) return "当前视频未提供字幕"
    val selected = selectedSubtitles.firstOrNull()
        ?: return "有 ${analysis.subtitles.size} 个字幕可选 · 当前不下载"
    val source = when (selected.source) {
        SubtitleSource.Manual -> "手动字幕"
        SubtitleSource.Automatic -> "自动字幕"
    }
    return "已选择 ${selected.language} ${selected.ext} $source · 独立字幕文件"
}

internal fun settingsParserVersionLabelForUiTest(): String = settingsParserVersionLabel()

internal fun settingsMediaProcessorLabelForUiTest(): String = settingsMediaProcessorLabel()

internal fun subtitleSelectionLabelForUiTest(
    analysis: VideoAnalysis?,
    selectedSubtitles: List<SubtitleInfo>,
): String = subtitleSelectionLabel(analysis, selectedSubtitles)

internal fun historyActionLabelsForUiTest(item: HistoryUiItem): List<String> = historyActionLabels(item)
