package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.RetryDownloadDraft

enum class FormatMode(val label: String) {
    VideoAndAudio("视频+音频"),
    AudioOnly("音频"),
    VideoOnly("视频"),
}

data class FormatSelection(
    val mode: FormatMode = FormatMode.VideoAndAudio,
    val selectedHeight: Int? = null,
    val selectedVideoFormatId: String? = null,
    val selectedAudioFormatId: String? = null,
    val mergeRequired: Boolean = false,
)

data class FormatCodecOption(
    val label: String,
    val videoFormatId: String,
    val selected: Boolean,
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
    val codecOptions: List<FormatCodecOption> = emptyList(),
    val selectedCodecLabel: String? = null,
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
    val audio = bestStandaloneAudioForNativeMp4Merge(
        analysis = analysis,
        preferredFormatId = selection.selectedAudioFormatId,
    ) ?: return emptyList()
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
            .thenBy { codecCompatibilityRank(videoCodecLabel(it.video)) }
            .thenBy { if (it.video.hasAudio) 1 else 0 }
            .thenBy { it.video.fps ?: 0.0 }
            .thenBy { it.video.filesizeBytes ?: 0L },
    ) ?: return emptyList()
    val explicitAutoChoice = selection.selectedVideoFormatId?.let { formatId ->
        choices.firstOrNull { it.video.id == formatId }
    }
    val autoChoice = explicitAutoChoice ?: best
    val auto = rowFromChoice(
        height = null,
        label = "自动（推荐）",
        choice = autoChoice,
        selected = selection.selectedHeight == null &&
            (selection.selectedVideoFormatId == null || explicitAutoChoice != null),
        prefix = "自动（推荐） · ",
        selectedCodecLabel = reliableCodecLabel(autoChoice.video),
    )
    val specific = choices
        .groupBy { it.video.height }
        .entries
        .sortedByDescending { it.key ?: 0 }
        .map { (height, heightChoices) ->
            val explicitChoice = selection.selectedVideoFormatId?.let { formatId ->
                heightChoices.firstOrNull { it.video.id == formatId }
            }
            val codecChoices = heightChoices
                .groupBy { videoCodecLabel(it.video) }
                .map { (label, matchingChoices) ->
                    val choice = matchingChoices.firstOrNull {
                        it.video.id == selection.selectedVideoFormatId
                    } ?: bestChoiceForSameCodec(matchingChoices)
                    label to choice
                }
                .sortedWith(
                    compareByDescending<Pair<String, FormatChoice>> {
                        codecCompatibilityRank(it.first)
                    }.thenBy { it.first },
                )
            val choice = if (selection.selectedHeight == height && explicitChoice != null) {
                explicitChoice
            } else {
                codecChoices.first().second
            }
            val codecOptions = codecChoices.map { (label, codecChoice) ->
                FormatCodecOption(
                    label = label,
                    videoFormatId = codecChoice.video.id,
                    selected = codecChoice.video.id == choice.video.id,
                )
            }
            rowFromChoice(
                height = height,
                label = "${height}p",
                choice = choice,
                selected = selection.selectedHeight == height &&
                    (selection.selectedVideoFormatId == null || explicitChoice != null),
                codecOptions = codecOptions,
                selectedCodecLabel = reliableCodecLabel(choice.video),
            )
        }
    return listOf(auto) + specific
}

private fun audioDownloadRows(
    analysis: VideoAnalysis,
    selection: FormatSelection,
): List<FormatResolutionRow> {
    val audio = bestStandaloneAudio(
        analysis = analysis,
        preferredFormatId = selection.selectedAudioFormatId,
    ) ?: return emptyList()
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

internal fun retryFormatSelection(
    analysis: VideoAnalysis,
    draft: RetryDownloadDraft,
): FormatSelection {
    val mode = when (draft.route) {
        is DownloadRoute.MergeRequired -> FormatMode.VideoAndAudio
        is DownloadRoute.AudioOnly -> FormatMode.AudioOnly
        is DownloadRoute.DirectSingleFile,
        is DownloadRoute.VideoOnly,
        -> FormatMode.VideoOnly
    }
    if (!isFormatModeAvailable(analysis, mode)) return defaultFormatSelection(analysis)

    val preferredVideoFormatId = when (val route = draft.route) {
        is DownloadRoute.DirectSingleFile -> route.formatId
        is DownloadRoute.VideoOnly -> route.videoFormatId
        is DownloadRoute.MergeRequired -> route.videoFormatId
        is DownloadRoute.AudioOnly -> null
    }
    val preferredAudioFormatId = when (val route = draft.route) {
        is DownloadRoute.MergeRequired -> route.audioFormatId
        is DownloadRoute.AudioOnly -> route.audioFormatId
        is DownloadRoute.DirectSingleFile,
        is DownloadRoute.VideoOnly,
        -> null
    }

    val preferredHeight = analysis.formats
        .firstOrNull { it.id == preferredVideoFormatId }
        ?.height
    val preferred = FormatSelection(
        mode = mode,
        selectedHeight = preferredHeight,
        selectedVideoFormatId = preferredVideoFormatId,
        selectedAudioFormatId = preferredAudioFormatId,
    )
    return buildFormatResolutionRows(analysis, preferred)
        .firstOrNull { it.selectable && it.selected }
        ?.let { selectionFromRow(mode, it) }
        ?: selectBestAvailableFormatSelection(analysis, mode, preferredHeight)
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
    codecOptions: List<FormatCodecOption> = emptyList(),
    selectedCodecLabel: String? = reliableCodecLabel(choice.video),
): FormatResolutionRow {
    val formatHeight = choice.video.height?.let { "${it}p" } ?: label
    val ext = choice.video.ext.ifBlank { choice.audio?.ext.orEmpty() }.uppercase()
    val capability = if (choice.mergeRequired) "需原生合并" else "单文件"
    val codecSummary = selectedCodecLabel?.let { " $it" }.orEmpty()
    return FormatResolutionRow(
        height = height,
        label = label,
        selectable = true,
        selected = selected,
        mergeRequired = choice.mergeRequired,
        direct = !choice.mergeRequired,
        reason = null,
        summary = "$prefix$formatHeight $ext$codecSummary $capability",
        videoFormatId = choice.video.id,
        audioFormatId = choice.audio?.id,
        codecOptions = codecOptions,
        selectedCodecLabel = selectedCodecLabel,
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

private fun bestStandaloneAudio(
    analysis: VideoAnalysis,
    preferredFormatId: String? = null,
): VideoFormat? {
    val candidates = analysis.formats
        .filter { !it.hasVideo && it.hasAudio }
    return preferredFormatId
        ?.let { preferred -> candidates.firstOrNull { it.id == preferred } }
        ?: candidates.maxByOrNull { it.filesizeBytes ?: 0L }
}

private fun bestChoiceForSameCodec(choices: List<FormatChoice>): FormatChoice {
    return choices.maxWithOrNull(
        compareBy<FormatChoice> { if (it.video.hasAudio) 1 else 0 }
            .thenBy { it.video.fps ?: 0.0 }
            .thenBy { it.video.filesizeBytes ?: 0L },
    ) ?: error("编码分组不能为空")
}

internal fun videoCodecLabel(format: VideoFormat): String {
    val codec = format.videoCodec.orEmpty().lowercase()
    return when {
        codec.startsWith("avc1") ||
            codec.startsWith("avc3") ||
            codec.startsWith("h264") -> "H.264"

        codec.startsWith("hev1") ||
            codec.startsWith("hvc1") ||
            codec.startsWith("hevc") ||
            codec.startsWith("h265") -> "H.265"

        codec.startsWith("vp09") || codec.startsWith("vp9") -> "VP9"
        codec.startsWith("av01") || codec.startsWith("av1") -> "AV1"
        else -> "其他"
    }
}

private fun reliableCodecLabel(format: VideoFormat): String? {
    return videoCodecLabel(format).takeUnless { it == "其他" }
}

private fun codecCompatibilityRank(label: String): Int {
    return when (label) {
        "H.264" -> 4
        "H.265" -> 3
        "VP9" -> 2
        "AV1" -> 1
        else -> 0
    }
}

private fun bestStandaloneAudioForNativeMp4Merge(
    analysis: VideoAnalysis,
    preferredFormatId: String? = null,
): VideoFormat? {
    val candidates = analysis.formats
        .filter { !it.hasVideo && it.hasAudio && it.isNativeMp4MergeAudioCompatible() }
    return preferredFormatId
        ?.let { preferred -> candidates.firstOrNull { it.id == preferred } }
        ?: candidates.maxByOrNull { it.filesizeBytes ?: 0L }
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
