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
import com.garyapp.ytdl.download.DownloadRoute
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
    val thumbnailUrl: String? = null,
    val subtitleOutputUris: List<String> = emptyList(),
    val formatBadge: String = "",
) {
    val hasOutput: Boolean
        get() = outputUri.startsWith("app-private://outputs/") &&
            status in setOf(HistoryItemEntity.STATUS_COMPLETED, HistoryItemEntity.STATUS_FAILED)
    val hasSubtitleOutput: Boolean
        get() = hasOutput && subtitleOutputUris.isNotEmpty()
    val primarySubtitleOutputUri: String?
        get() = subtitleOutputUris.firstOrNull()
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
            thumbnailUrl = row.thumbnailUrl?.takeIf { it.isNotBlank() },
            subtitleOutputUris = historySubtitleOutputUris(row.subtitleOutputUris),
            formatBadge = formatResolutionBadgeForSummary(row.formatSummary.orEmpty()),
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
    val hasSubtitleOutputs = historySubtitleOutputUris(row.subtitleOutputUris).isNotEmpty()
    val parts = listOfNotNull(
        row.formatSummary
            ?.takeIf { it.isNotBlank() }
            ?.let(::userVisibleFormatSummaryLabel)
            ?.takeIf { it.isNotBlank() },
        row.sourceCategory?.takeIf { it.isNotBlank() },
        row.completedAt.takeIf { it > 0L }?.let { formatHistoryTime(it) },
        row.outputUri?.takeIf { it.isNotBlank() }?.let {
            if (hasSubtitleOutputs) {
                "媒体文件 + 独立字幕文件"
            } else if (it.startsWith("app-private://outputs/")) {
                "媒体文件"
            } else {
                historyOutputLabel(it)
            }
        },
        row.errorSummary?.takeIf { it.isNotBlank() },
    )
    return redactHistoryUiText(parts.joinToString(" · ")).ifBlank { "本地记录" }
}

internal fun userVisibleFormatSummaryLabel(formatSummary: String): String {
    val normalized = formatSummary.trim()
    Regex("""^视频\s*(\S+)\s*(?:\+\s*)?音频\s*\S+(?:\s*\+\s*字幕\s+.+)?$""")
        .matchEntire(normalized)
        ?.let { return "视频+音频 · 原生合并" }

    return when {
        Regex("""^格式\s+(\S+)$""").matchEntire(normalized) != null -> {
            "单文件格式"
        }
        Regex("""^仅视频\s+(\S+)$""").matchEntire(normalized) != null -> {
            "仅视频"
        }
        Regex("""^仅音频\s+\S+$""").matches(normalized) -> "仅音频"
        else -> removeResolutionFromFormatMeta(normalized)
    }
}

private fun removeResolutionFromFormatMeta(value: String): String {
    return value
        .replace(Regex("""(?i)(^| · )\d{3,4}p(?:\d{2})?\s*"""), "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('·')
        .trim()
}

internal fun formatResolutionBadgeForRequest(request: DownloadRequest?): String {
    if (request == null) return ""
    return formatResolutionBadgeForSummary(request.formatSummary)
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

private fun historySubtitleOutputUris(value: String?): List<String> {
    return value.orEmpty()
        .lineSequence()
        .map { redactHistoryUiText(it.substringBefore('?').trim()) }
        .filter { it.startsWith("app-private://outputs/") }
        .distinct()
        .toList()
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
        buildList {
            add("打开")
            add("分享")
            add("导出")
            if (item.hasSubtitleOutput) {
                add("分享字幕")
                add("导出字幕")
            }
            add("删除")
        }
    } else {
        listOf("删除")
    }
}

fun suggestedExportDisplayName(item: HistoryUiItem, fallbackDisplayName: String): String {
    val extension = fallbackDisplayName
        .substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it.length <= 8 }
        ?.let { ".$it" }
        .orEmpty()
    val baseName = item.title
        .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
        .trim('.', ' ')
        .take(72)
        .ifBlank { fallbackDisplayName.substringBeforeLast('.').ifBlank { "ytdl-export" } }
    val suffix = item.completedAt
        .takeIf { it > 0L }
        ?.let { SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date(it)) }
        ?: "auto"
    return "$baseName-$suffix$extension"
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
        DownloadStage.Exporting -> "导出中"
        DownloadStage.Completed -> "下载完成"
        DownloadStage.Failed -> "下载失败"
        DownloadStage.Canceled -> "已取消"
    }
}

fun settingsParserVersionLabel(): String = "yt-dlp ${YtdlpBridge.PINNED_YTDLP_VERSION}"

fun settingsMediaProcessorLabel(): String = "原生合并 · 字幕独立文件 · 字幕嵌入/烧录属 MVP2"

data class SubtitleSelectionUiState(
    val label: String,
    val canToggle: Boolean,
    val trailing: String,
)

fun subtitleSelectionUiState(
    analysis: VideoAnalysis?,
    selectedSubtitles: List<SubtitleInfo>,
): SubtitleSelectionUiState {
    val label = subtitleSelectionLabel(analysis, selectedSubtitles)
    val subtitles = analysis?.subtitles.orEmpty()
    if (subtitles.isEmpty()) {
        return SubtitleSelectionUiState(
            label = label,
            canToggle = false,
            trailing = if (analysis == null) "先分析" else "无可选",
        )
    }

    return SubtitleSelectionUiState(
        label = label,
        canToggle = true,
        trailing = if (selectedSubtitles.isEmpty()) "选择" else "取消",
    )
}

fun settingsPrivacyLegalLines(): List<String> = listOf(
    "仅处理用户粘贴的公开 http/https 页面地址。",
    "Cookies 只保存文件引用，不保存内容；任务运行时临时读取并清理。",
    "下载结果默认保存在 App 私有目录；导出、打开和分享由系统授权。",
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

fun recommendedSubtitle(subtitles: List<SubtitleInfo>): SubtitleInfo? {
    if (subtitles.isEmpty()) return null

    fun languageRank(language: String): Int {
        val normalized = language.trim().lowercase(Locale.ROOT)
        return listOf("zh-hans", "zh-hant", "zh", "en").indexOf(normalized)
            .takeIf { it >= 0 }
            ?: Int.MAX_VALUE
    }

    fun sourceRank(subtitle: SubtitleInfo): Int {
        return if (subtitle.source == SubtitleSource.Manual) 0 else 1
    }

    fun best(candidates: List<IndexedValue<SubtitleInfo>>): SubtitleInfo? {
        return candidates
            .minWithOrNull(
                compareBy<IndexedValue<SubtitleInfo>> { languageRank(it.value.language) }
                    .thenBy { sourceRank(it.value) }
                    .thenBy { it.index },
            )
            ?.value
    }

    val indexed = subtitles.withIndex().toList()
    best(indexed.filter { it.value.ext.equals("vtt", ignoreCase = true) })?.let { return it }
    best(indexed.filter { languageRank(it.value.language) != Int.MAX_VALUE })?.let { return it }
    return subtitles.first()
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

internal fun subtitleSelectionLabelForUiTest(
    analysis: VideoAnalysis?,
    selectedSubtitles: List<SubtitleInfo>,
): String = subtitleSelectionLabel(analysis, selectedSubtitles)

internal fun subtitleSelectionUiStateForUiTest(
    analysis: VideoAnalysis?,
    selectedSubtitles: List<SubtitleInfo>,
): SubtitleSelectionUiState = subtitleSelectionUiState(analysis, selectedSubtitles)

internal fun recommendedSubtitleForUiTest(subtitles: List<SubtitleInfo>): SubtitleInfo? = recommendedSubtitle(subtitles)

internal fun historyActionLabelsForUiTest(item: HistoryUiItem): List<String> = historyActionLabels(item)

internal fun suggestedExportDisplayNameForUiTest(
    item: HistoryUiItem,
    fallbackDisplayName: String,
): String = suggestedExportDisplayName(item, fallbackDisplayName)
