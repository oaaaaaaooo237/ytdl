package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat

enum class FormatMode(val label: String) {
    VideoAndAudio("视频+音频"),
    AudioOnly("仅音频"),
    VideoOnly("仅视频"),
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

private val StandardHeights = listOf<Int?>(null, 2160, 1440, 1080, 720, 480, 360, 240)

fun buildFormatResolutionRows(
    analysis: VideoAnalysis?,
    selection: FormatSelection,
): List<FormatResolutionRow> {
    if (analysis == null) {
        return StandardHeights.map { height ->
            FormatResolutionRow(
                height = height,
                label = height?.let { "${it}p" } ?: "自动（推荐）",
                selectable = false,
                selected = false,
                mergeRequired = false,
                direct = false,
                reason = "请先分析视频",
                summary = "请先分析视频",
                videoFormatId = null,
                audioFormatId = null,
            )
        }
    }

    return StandardHeights.map { height ->
        buildRowForHeight(analysis, selection, height)
    }
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
    val auto = buildFormatResolutionRows(analysis, FormatSelection()).first()
    return selectionFromRow(FormatMode.VideoAndAudio, auto)
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

private fun buildRowForHeight(
    analysis: VideoAnalysis,
    selection: FormatSelection,
    height: Int?,
): FormatResolutionRow {
    if (height == null) {
        val best = bestVideoAndAudioChoice(analysis)
        return if (best == null) {
            unavailableRow(height, "自动（推荐）", selection, "当前视频未提供")
        } else {
            rowFromChoice(
                height = null,
                label = "自动（推荐）",
                selection = selection,
                choice = best,
                selected = selection.selectedHeight == null,
                prefix = "自动（推荐） · ",
            )
        }
    }

    return when (selection.mode) {
        FormatMode.VideoAndAudio -> {
            val direct = analysis.formats
                .filter { it.isSupported && it.height == height && it.hasVideo && it.hasAudio }
                .bestByQuality()
            if (direct != null) {
                rowFromChoice(height, "${height}p", selection, FormatChoice(direct, null, false), selection.selectedHeight == height)
            } else {
                val video = analysis.formats
                    .filter { it.isSupported && it.height == height && it.hasVideo && !it.hasAudio }
                    .bestByQuality()
                val audio = bestStandaloneAudio(analysis)
                if (video != null && audio != null) {
                    rowFromChoice(height, "${height}p", selection, FormatChoice(video, audio, true), selection.selectedHeight == height)
                } else {
                    unavailableRow(height, "${height}p", selection, "当前视频未提供")
                }
            }
        }
        FormatMode.VideoOnly -> {
            val video = analysis.formats
                .filter { it.isSupported && it.height == height && it.hasVideo }
                .bestByQuality()
            if (video == null) {
                unavailableRow(height, "${height}p", selection, "当前视频未提供")
            } else {
                rowFromChoice(height, "${height}p", selection, FormatChoice(video, null, false), selection.selectedHeight == height)
            }
        }
        FormatMode.AudioOnly -> unavailableRow(height, "${height}p", selection, "仅音频不使用分辨率")
    }
}

private fun rowFromChoice(
    height: Int?,
    label: String,
    selection: FormatSelection,
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

private fun unavailableRow(
    height: Int?,
    label: String,
    selection: FormatSelection,
    reason: String,
): FormatResolutionRow {
    return FormatResolutionRow(
        height = height,
        label = label,
        selectable = false,
        selected = false,
        mergeRequired = false,
        direct = false,
        reason = reason,
        summary = reason,
        videoFormatId = null,
        audioFormatId = null,
    )
}

private data class FormatChoice(
    val video: VideoFormat,
    val audio: VideoFormat?,
    val mergeRequired: Boolean,
)

private fun bestVideoAndAudioChoice(analysis: VideoAnalysis): FormatChoice? {
    val direct = analysis.formats
        .filter { it.isSupported && it.hasVideo && it.hasAudio }
        .bestByQuality()
    val videoOnly = analysis.formats
        .filter { it.isSupported && it.hasVideo && !it.hasAudio }
        .bestByQuality()
    val audio = bestStandaloneAudio(analysis)
    val merged = if (videoOnly != null && audio != null) FormatChoice(videoOnly, audio, true) else null

    return listOfNotNull(direct?.let { FormatChoice(it, null, false) }, merged)
        .maxByOrNull { it.video.height ?: 0 }
}

private fun bestStandaloneAudio(analysis: VideoAnalysis): VideoFormat? {
    return analysis.formats
        .filter { !it.hasVideo && it.hasAudio }
        .maxByOrNull { it.filesizeBytes ?: 0L }
}

private fun List<VideoFormat>.bestByQuality(): VideoFormat? {
    return maxWithOrNull(
        compareBy<VideoFormat> { it.height ?: 0 }
            .thenBy { it.fps ?: 0.0 }
            .thenBy { it.filesizeBytes ?: 0L },
    )
}
