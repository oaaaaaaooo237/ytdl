package com.garyapp.ytdl.core.ytdlp

data class VideoAnalysis(
    val title: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val formats: List<VideoFormat>,
    val subtitles: List<SubtitleInfo>,
)

data class VideoFormat(
    val id: String,
    val ext: String,
    val height: Int?,
    val label: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val mergeRequired: Boolean,
    val isSupported: Boolean,
    val filesizeBytes: Long? = null,
    val fps: Double? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
)

data class SubtitleInfo(
    val language: String,
    val ext: String,
)

enum class AnalysisErrorCategory {
    Network,
    Unsupported,
    Permission,
    Parser,
    Unknown,
}

data class AnalysisFailure(
    val category: AnalysisErrorCategory,
    val message: String,
)
