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
    val source: SubtitleSource = SubtitleSource.Manual,
)

data class DownloadProgress(
    val status: String,
    val percent: Double?,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val speedBytesPerSecond: Double?,
    val etaSeconds: Long?,
    val filename: String?,
)

data class DownloadResult(
    val outputPath: String,
    val bytesWritten: Long,
    val title: String,
    val formatId: String?,
    val role: DownloadFormatRole? = null,
)

data class SubtitleDownloadResult(
    val outputPath: String,
    val bytesWritten: Long,
    val language: String,
    val ext: String,
    val source: SubtitleSource,
    val title: String,
)

fun interface DownloadProgressListener {
    fun onProgress(progress: DownloadProgress)
}

enum class SubtitleSource(val pythonValue: String) {
    Manual("manual"),
    Automatic("automatic"),
    ;

    companion object {
        fun fromPythonValue(value: String): SubtitleSource? {
            return entries.firstOrNull { it.pythonValue == value }
        }
    }
}

enum class DownloadFormatRole(val pythonValue: String) {
    Video("video"),
    Audio("audio"),
    ;

    companion object {
        fun fromPythonValue(value: String): DownloadFormatRole? {
            return entries.firstOrNull { it.pythonValue == value }
        }
    }
}

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
