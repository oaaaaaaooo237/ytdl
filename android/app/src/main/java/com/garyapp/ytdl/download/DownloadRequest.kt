package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import com.garyapp.ytdl.ui.formatSelectionSummary
import com.garyapp.ytdl.ui.isNativeMp4MergeAudioCompatible
import com.garyapp.ytdl.ui.isNativeMp4MergeVideoCompatible

data class DownloadRequest(
    val url: String,
    val title: String,
    val route: DownloadRoute,
    val thumbnailUrl: String? = null,
    val selectedSubtitles: List<SubtitleInfo> = emptyList(),
    val cookiesPath: String? = null,
    val formatSummary: String = "",
) {
    companion object {
        fun fromAnalysis(
            url: String,
            analysis: VideoAnalysis,
            selection: FormatSelection,
            selectedSubtitles: List<SubtitleInfo> = emptyList(),
            cookiesPath: String? = null,
        ): Result<DownloadRequest> {
            return runCatching {
                val route = when (selection.mode) {
                    FormatMode.VideoAndAudio -> buildVideoAndAudioRoute(analysis, selection)
                    FormatMode.VideoOnly -> buildVideoOnlyRoute(analysis, selection)
                    FormatMode.AudioOnly -> buildAudioOnlyRoute(analysis, selection)
                }
                validateSelectedSubtitles(analysis, selectedSubtitles)
                DownloadRequest(
                    url = url.trim(),
                    title = analysis.title,
                    route = route,
                    formatSummary = historyFormatSummary(
                        analysis = analysis,
                        selection = selection,
                        selectedSubtitles = selectedSubtitles,
                    ),
                    thumbnailUrl = analysis.thumbnailUrl,
                    selectedSubtitles = selectedSubtitles,
                    cookiesPath = cookiesPath,
                )
            }
        }

        private fun buildVideoAndAudioRoute(
            analysis: VideoAnalysis,
            selection: FormatSelection,
        ): DownloadRoute {
            val videoFormat = analysis.requireFormat(selection.selectedVideoFormatId, "视频格式")
            if (!videoFormat.hasVideo) {
                throw DownloadRequestException("所选视频格式不包含视频流。")
            }
            if (videoFormat.hasAudio) {
                throw DownloadRequestException("视频+音频模式需要明确的独立视频流和独立音频流。")
            }

            val audioFormat = analysis.requireFormat(selection.selectedAudioFormatId, "音频格式")
            if (!audioFormat.hasAudio || audioFormat.hasVideo) {
                throw DownloadRequestException("所选音频格式必须是明确的独立音频流。")
            }
            if (
                !videoFormat.isNativeMp4MergeVideoCompatible() ||
                !audioFormat.isNativeMp4MergeAudioCompatible()
            ) {
                throw DownloadRequestException("所选视频和音频格式不兼容当前原生 MP4 合并。")
            }
            return DownloadRoute.MergeRequired(
                videoFormatId = videoFormat.id,
                audioFormatId = audioFormat.id,
            )
        }

        private fun buildVideoOnlyRoute(
            analysis: VideoAnalysis,
            selection: FormatSelection,
        ): DownloadRoute {
            val videoFormat = analysis.requireFormat(selection.selectedVideoFormatId, "视频格式")
            if (!videoFormat.hasVideo) {
                throw DownloadRequestException("所选格式不包含可下载的视频流。")
            }
            return if (videoFormat.audioCodec.equals("none", ignoreCase = true)) {
                DownloadRoute.VideoOnly(videoFormatId = videoFormat.id)
            } else {
                DownloadRoute.DirectSingleFile(formatId = videoFormat.id)
            }
        }

        private fun buildAudioOnlyRoute(
            analysis: VideoAnalysis,
            selection: FormatSelection,
        ): DownloadRoute {
            val audioFormat = analysis.requireFormat(selection.selectedAudioFormatId, "音频格式")
            if (!audioFormat.hasAudio || audioFormat.hasVideo) {
                throw DownloadRequestException("所选音频格式必须是明确独立音频流。")
            }
            return DownloadRoute.AudioOnly(audioFormatId = audioFormat.id)
        }

        private fun validateSelectedSubtitles(
            analysis: VideoAnalysis,
            selectedSubtitles: List<SubtitleInfo>,
        ) {
            selectedSubtitles.forEach { selected ->
                if (analysis.subtitles.none { it == selected }) {
                    throw DownloadRequestException("所选字幕必须来自当前分析结果。")
                }
            }
        }

        private fun historyFormatSummary(
            analysis: VideoAnalysis,
            selection: FormatSelection,
            selectedSubtitles: List<SubtitleInfo>,
        ): String {
            val mediaSummary = formatSelectionSummary(analysis, selection)
            if (selectedSubtitles.isEmpty()) return mediaSummary
            return "$mediaSummary · 独立字幕文件"
        }

        private fun VideoAnalysis.requireFormat(formatId: String?, label: String): VideoFormat {
            val normalized = formatId?.trim().orEmpty()
            if (normalized.isBlank()) {
                throw DownloadRequestException("$label 必须来自已应用格式选择，不能自动回退。")
            }
            return formats.firstOrNull { it.id == normalized }
                ?: throw DownloadRequestException("$label 不在当前分析结果中，不能自动回退。")
        }
    }
}

sealed interface DownloadRoute {
    data class DirectSingleFile(val formatId: String) : DownloadRoute
    data class VideoOnly(val videoFormatId: String) : DownloadRoute
    data class AudioOnly(val audioFormatId: String) : DownloadRoute
    data class MergeRequired(val videoFormatId: String, val audioFormatId: String) : DownloadRoute
}

class DownloadRequestException(
    message: String,
) : IllegalArgumentException(message)
