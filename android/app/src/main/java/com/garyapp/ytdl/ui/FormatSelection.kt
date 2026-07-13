package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat

enum class FormatMode(val label: String) {
    VideoAndAudio("视频+音频"),
    AudioOnly("仅音频"),
    VideoOnly("视频下载"),
}

data class FormatSelection(
    val mode: FormatMode = FormatMode.VideoAndAudio,
    val selectedHeight: Int? = null,
    val selectedVideoFormatId: String? = null,
    val selectedAudioFormatId: String? = null,
    val mergeRequired: Boolean = false,
)

data class FormatResolutionRow(
    val height: Int?,
    val label: String,
    val selectable: Boolean,
    val selected: Boolean,
    val mergeRequired: Boolean,
    val direct: Boolean,
    val reason: String?,
    val summary: String,
    val videoFormatId: String?,
    val audioFormatId: String?,
)

fun buildFormatResolutionRows(
    analysis: VideoAnalysis?,
    selection: FormatSelection,
): List<FormatResolutionRow> {
    if (analysis == null) return emptyList()
    return when (selection.mode) {
        FormatMode.VideoAndAudio -> videoAndAudioRows(analysis, selection)
        FormatMode.VideoOnly -> videoDownloadRows(analysis, selection)
        FormatMode.AudioOnly -> audioDownloadRows(analysis, selection)
    }
}

private fun videoAndAudioRows(
    analysis: VideoAnalysis,
    selection: FormatSelection,
): List<FormatResolutionRow> {
    val audio = bestStandaloneAudioForNativeMp4Merge(analysis) ?: return emptyList()
    val choices = analysis.formats
        .filter { it.isSupported && it.hasVideo && !it.hasAudio && it.isNativeMp4MergeVideoCompatible() }
        .sortedByDescending { it.height ?: 0 }
        .map { FormatChoice(video = it, audio = audio, mergeRequired = true) }
    return rowsFromVideoChoices(choices, selection)
}

private fun videoDownloadRows(
    analysis: VideoAnalysis,
    selection: FormatSelection,
): List<FormatResolutionRow> {
    val choices = analysis.formats
        .filter { it.isSupported && it.hasVideo }
        .sortedByDescending { it.height ?: 0 }
        .map { FormatChoice(video = it, audio = null, mergeRequired = false) }
    return rowsFromVideoChoices(choices, selection)
}

private fun rowsFromVideoChoices(
    choices: List<FormatChoice>,
    selection: FormatSelection,
): List<FormatResolutionRow> {
    val best = choices.maxWithOrNull(
        compareBy<FormatChoice> { it.video.height ?: 0 }
            .thenBy { it.video.fps ?: 0.0 }
            .thenBy { it.video.filesizeBytes ?: 0L },
    ) ?: return emptyList()
    val auto = rowFromChoice(
        height = null,
        label = "自动（推荐）",
        choice = best,
        selected = selection.selectedHeight == null,
        prefix = "自动（推荐） · ",
    )
    val specific = choices.map { choice ->
        val format = choice.video
        rowFromChoice(
            height = format.height,
            label = "${format.height}p",
            choice = choice,
            selected = selection.selectedHeight == format.height &&
                (selection.selectedVideoFormatId == null || selection.selectedVideoFormatId == format.id),
        )
    }
    return listOf(auto) + specific
}

private fun audioDownloadRows(
    analysis: VideoAnalysis,
    selection: FormatSelection,
): List<FormatResolutionRow> {
    val audio = bestStandaloneAudio(analysis) ?: return emptyList()
    return listOf(
        audioOnlyRow(
            height = null,
            label = "自动（推荐）",
            audio = audio,
            selected = selection.selectedHeight == null,
            prefix = "自动（推荐） · ",
        ),
    )
}

fun selectionFromRow(mode: FormatMode, row: FormatResolutionRow): FormatSelection {
    return FormatSelection(
        mode = mode,
        selectedHeight = row.height,
        selectedVideoFormatId = row.videoFormatId,
        selectedAudioFormatId = row.audioFormatId,
        mergeRequired = row.mergeRequired,
    )
}

fun defaultFormatSelection(analysis: VideoAnalysis?): FormatSelection {
    val mode = when {
        isFormatModeAvailable(analysis, FormatMode.VideoAndAudio) -> FormatMode.VideoAndAudio
        isFormatModeAvailable(analysis, FormatMode.VideoOnly) -> FormatMode.VideoOnly
        isFormatModeAvailable(analysis, FormatMode.AudioOnly) -> FormatMode.AudioOnly
        else -> FormatMode.VideoOnly
    }
    return selectBestAvailableFormatSelection(
        analysis = analysis,
        mode = mode,
    )
}

fun isFormatModeAvailable(analysis: VideoAnalysis?, mode: FormatMode): Boolean {
    if (analysis == null) return false
    return buildFormatResolutionRows(analysis, FormatSelection(mode = mode)).any { it.selectable }
}

fun selectBestAvailableFormatSelection(
    analysis: VideoAnalysis?,
    mode: FormatMode,
    preferredHeight: Int? = null,
): FormatSelection {
    val baseSelection = FormatSelection(mode = mode, selectedHeight = preferredHeight)
    val rows = buildFormatResolutionRows(analysis, baseSelection)
    val row = rows.firstOrNull { it.selectable && it.height == preferredHeight }
        ?: rows.firstOrNull { it.selectable && it.height == null }
        ?: rows.firstOrNull { it.selectable }

    return row?.let { selectionFromRow(mode, it) } ?: baseSelection
}

fun formatSelectionSummary(
    analysis: VideoAnalysis?,
    selection: FormatSelection,
): String {
    if (analysis == null) return "分析后显示真实格式"
    val selectedRow = buildFormatResolutionRows(analysis, selection)
        .firstOrNull { it.selected }
        ?: return "请选择可用格式"
    return selectedRow.summary
}

private fun rowFromChoice(
    height: Int?,
    label: String,
    choice: FormatChoice,
    selected: Boolean,
    prefix: String = "",
): FormatResolutionRow {
    val formatHeight = choice.video.height?.let { "${it}p" } ?: label
    val ext = choice.video.ext.ifBlank { choice.audio?.ext.orEmpty() }.uppercase()
    val capability = if (choice.mergeRequired) "需原生合并" else "单文件"
    return FormatResolutionRow(
        height = height,
        label = label,
        selectable = true,
        selected = selected,
        mergeRequired = choice.mergeRequired,
        direct = !choice.mergeRequired,
        reason = null,
        summary = "$prefix$formatHeight $ext $capability",
        videoFormatId = choice.video.id,
        audioFormatId = choice.audio?.id,
    )
}

private fun audioOnlyRow(
    height: Int?,
    label: String,
    audio: VideoFormat,
    selected: Boolean,
    prefix: String = "",
): FormatResolutionRow {
    val ext = audio.ext.ifBlank { "audio" }.uppercase()
    return FormatResolutionRow(
        height = height,
        label = label,
        selectable = true,
        selected = selected,
        mergeRequired = false,
        direct = true,
        reason = null,
        summary = "${prefix}音频 $ext 单文件",
        videoFormatId = null,
        audioFormatId = audio.id,
    )
}

private data class FormatChoice(
    val video: VideoFormat,
    val audio: VideoFormat?,
    val mergeRequired: Boolean,
)

private fun bestStandaloneAudio(analysis: VideoAnalysis): VideoFormat? {
    return analysis.formats
        .filter { !it.hasVideo && it.hasAudio }
        .maxByOrNull { it.filesizeBytes ?: 0L }
}

private fun bestStandaloneAudioForNativeMp4Merge(analysis: VideoAnalysis): VideoFormat? {
    return analysis.formats
        .filter { !it.hasVideo && it.hasAudio && it.isNativeMp4MergeAudioCompatible() }
        .maxByOrNull { it.filesizeBytes ?: 0L }
}

internal fun VideoFormat.isNativeMp4MergeVideoCompatible(): Boolean {
    val normalizedExt = ext.lowercase()
    val normalizedCodec = videoCodec.orEmpty().lowercase()
    return normalizedExt == "mp4" && (
        normalizedCodec.startsWith("avc1") ||
            normalizedCodec.startsWith("avc3") ||
            normalizedCodec.startsWith("h264")
        )
}

internal fun VideoFormat.isNativeMp4MergeAudioCompatible(): Boolean {
    val normalizedExt = ext.lowercase()
    val normalizedCodec = audioCodec.orEmpty().lowercase()
    return normalizedExt in setOf("m4a", "mp4") && (
        normalizedCodec.startsWith("mp4a") ||
            normalizedCodec.startsWith("aac")
        )
}
