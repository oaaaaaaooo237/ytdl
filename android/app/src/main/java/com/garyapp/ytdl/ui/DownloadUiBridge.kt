package com.garyapp.ytdl.ui

import android.content.Context
import com.garyapp.ytdl.cookies.CookiesReference
import com.garyapp.ytdl.cookies.TemporaryCookiesFile
import com.garyapp.ytdl.core.privacy.SensitiveText
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import java.io.File
import java.net.URI
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
    val thumbnailUrl: String? = null,
    val formatBadge: String = "",
    val codecBadge: String = "",
    val retryAvailable: Boolean = false,
    val isAudioOnly: Boolean = false,
) {
    val hasOutput: Boolean
        get() = isHistoryOutputUri(outputUri) &&
            status in setOf(HistoryItemEntity.STATUS_COMPLETED, HistoryItemEntity.STATUS_FAILED)
}

fun historyUiItemsFromRows(
    rows: List<HistoryItemEntity>,
    retryDraftAvailable: (Long) -> Boolean = { false },
): List<HistoryUiItem> {
    return rows.map { row ->
        HistoryUiItem(
            id = row.id,
            title = redactHistoryUiText(row.title.orEmpty()).ifBlank { "未命名任务" },
            meta = historyMeta(row),
            badge = historyBadge(row.status.orEmpty()),
            outputUri = redactHistoryUiText(row.outputUri.orEmpty()),
            status = row.status.orEmpty(),
            completedAt = row.completedAt,
            thumbnailUrl = row.thumbnailUrl?.takeIf { it.isNotBlank() },
            formatBadge = formatResolutionBadgeForSummary(row.formatSummary.orEmpty()),
            codecBadge = formatCodecBadgeForSummary(row.formatSummary.orEmpty()),
            isAudioOnly = isAudioOnlyFormatSummary(row.formatSummary.orEmpty()),
            retryAvailable = row.status in setOf(
                HistoryItemEntity.STATUS_FAILED,
                HistoryItemEntity.STATUS_CANCELED,
            ) && retryDraftAvailable(row.id),
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
        historyTypeLabel(row.formatSummary.orEmpty()).takeIf { it.isNotBlank() },
        historyOutputFileName(row.outputUri.orEmpty()),
        row.completedAt.takeIf { it > 0L }?.let { formatHistoryTime(it) },
        row.errorSummary?.takeIf { it.isNotBlank() && !it.contains("字幕") },
    )
    return redactHistoryUiText(parts.joinToString(" · ")).trim()
}

internal fun formatResolutionBadgeForRequest(request: DownloadRequest?): String {
    if (request == null) return ""
    return formatResolutionBadgeForSummary(request.formatSummary)
}

internal fun formatCodecBadgeForRequest(request: DownloadRequest?): String {
    if (request == null) return ""
    return formatCodecBadgeForSummary(request.formatSummary)
}

private fun formatCodecBadgeForSummary(formatSummary: String): String {
    val match = Regex("""(?i)(?:^|\s)(H\.264|H\.265|VP9|AV1)(?=\s|$)""")
        .find(formatSummary.trim())
        ?: return ""
    return when (match.groupValues[1].uppercase(Locale.ROOT)) {
        "H.264" -> "H.264"
        "H.265" -> "H.265"
        "VP9" -> "VP9"
        "AV1" -> "AV1"
        else -> ""
    }
}

private fun formatResolutionBadgeForSummary(formatSummary: String): String {
    val normalized = formatSummary.trim()
    formatResolutionLabelFromSummary(normalized)?.let { return it }
    return ""
}

private fun formatResolutionLabelFromSummary(formatSummary: String): String? {
    val match = Regex("""(?i)\b(\d{3,4})p(?:\d{2})?\b""").find(formatSummary) ?: return null
    return match.value.lowercase(Locale.ROOT)
}

private fun isAudioOnlyFormatSummary(formatSummary: String): Boolean {
    return historyTypeLabel(formatSummary) == "仅音频"
}

private fun historyTypeLabel(formatSummary: String): String {
    val normalized = formatSummary.trim()
    return when {
        normalized.contains("仅音频") ||
            (normalized.contains("音频") && !normalized.contains("视频")) -> "仅音频"
        normalized.contains("仅视频") -> "仅视频"
        normalized.contains("需原生合并") ||
            (normalized.contains("视频") && normalized.contains("音频")) -> "视频+音频"
        Regex("""(?i)\b\d{3,4}p(?:\d{2})?\b""").containsMatchIn(normalized) ||
            normalized.startsWith("格式 ") ||
            normalized.contains("单文件") -> "视频"
        else -> ""
    }
}

private fun isHistoryOutputUri(value: String): Boolean {
    val normalized = value.trim()
    return normalized.startsWith("app-private://outputs/") ||
        normalized.startsWith("content://") && normalized.length > "content://".length
}

private fun historyOutputFileName(outputUri: String): String? {
    if (!isHistoryOutputUri(outputUri)) return null
    val rawLeaf = runCatching { URI(outputUri).rawPath.orEmpty().substringAfterLast('/') }
        .getOrDefault("")
    val decoded = runCatching { URLDecoder.decode(rawLeaf, Charsets.UTF_8.name()) }
        .getOrDefault(rawLeaf)
    val fileName = decoded
        .substringAfterLast('/')
        .substringAfterLast(':')
        .replace(Regex("""[\u0000-\u001F\u007F]"""), "")
        .trim()
    if (fileName.isBlank() || fileName.contains("://")) return null
    return redactHistoryUiText(fileName)
        .truncateHistoryFileName()
        .takeIf { it.isNotBlank() }
}

private fun String.truncateHistoryFileName(maxChars: Int = 120): String {
    if (length <= maxChars) return this
    val extension = substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.matches(Regex("""[A-Za-z0-9]{1,10}""")) }
        ?.let { ".$it" }
        .orEmpty()
    val prefixLength = (maxChars - extension.length - 1).coerceAtLeast(1)
    return take(prefixLength).trimEnd() + "…" + extension
}

private fun formatHistoryTime(timestampMillis: Long): String {
    return SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(Date(timestampMillis))
}

fun historyActionLabels(item: HistoryUiItem): List<String> {
    return if (item.hasOutput) {
        buildList {
            add("打开")
            add("分享")
            if (item.retryAvailable) {
                add("再次下载")
            }
            add("删除")
        }
    } else {
        if (item.retryAvailable) listOf("再次下载", "删除") else listOf("删除")
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
        DownloadStage.DownloadingSubtitles -> "处理附加文件"
        DownloadStage.Merging -> "原生合并"
        DownloadStage.Exporting -> "保存中"
        DownloadStage.Completed -> "下载完成"
        DownloadStage.Failed -> "下载失败"
        DownloadStage.Canceled -> "已取消"
    }
}

fun settingsParserVersionLabel(): String = "yt-dlp ${YtdlpBridge.PINNED_YTDLP_VERSION}"

fun settingsMediaProcessorLabel(): String = "原生合并 · 不转码"

fun settingsPrivacyLegalLines(): List<String> = listOf(
    "仅处理用户粘贴的公开 http/https 页面地址。",
    "Cookies 只保存文件引用，不保存内容；任务运行时临时读取并清理。",
    "下载结果默认保存在 App 私有目录；打开和分享由系统授权。",
    "历史缩略图可能刷新公开预览图；请求不携带 Cookies 或授权信息。",
    "不绕过 DRM、付费墙或未授权访问限制。",
)

fun notificationPermissionSubtitle(
    isGranted: Boolean,
    runtimePermissionRequired: Boolean,
): String {
    if (!runtimePermissionRequired) return "系统无需单独授权"
    return if (isGranted) {
        "已允许"
    } else {
        "未授权 · 下载仍在应用内显示进度"
    }
}

fun notificationPermissionTrailing(
    isGranted: Boolean,
    runtimePermissionRequired: Boolean,
): String {
    if (!runtimePermissionRequired) return "系统"
    return if (isGranted) "已允许" else "请求"
}

fun refreshedNotificationPermissionState(
    currentValue: Boolean,
    systemValue: Boolean,
    runtimePermissionRequired: Boolean,
): Boolean {
    if (!runtimePermissionRequired) return true
    return if (currentValue == systemValue) currentValue else systemValue
}

internal fun settingsParserVersionLabelForUiTest(): String = settingsParserVersionLabel()

internal fun settingsMediaProcessorLabelForUiTest(): String = settingsMediaProcessorLabel()

internal fun settingsPrivacyLegalLinesForUiTest(): List<String> = settingsPrivacyLegalLines()

internal fun notificationPermissionSubtitleForUiTest(
    isGranted: Boolean,
    runtimePermissionRequired: Boolean,
): String = notificationPermissionSubtitle(isGranted, runtimePermissionRequired)

internal fun notificationPermissionTrailingForUiTest(
    isGranted: Boolean,
    runtimePermissionRequired: Boolean,
): String = notificationPermissionTrailing(isGranted, runtimePermissionRequired)

internal fun refreshedNotificationPermissionStateForUiTest(
    currentValue: Boolean,
    systemValue: Boolean,
    runtimePermissionRequired: Boolean,
): Boolean = refreshedNotificationPermissionState(currentValue, systemValue, runtimePermissionRequired)

internal fun historyActionLabelsForUiTest(item: HistoryUiItem): List<String> = historyActionLabels(item)
