package com.garyapp.ytdl.core.files

enum class FileKind { Video, Audio, Other }
enum class ResumeSupport { Supported, Unknown, Unavailable }
data class FileMetadata(
    val sourceUrl: String,
    val finalUrl: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long?,
    val etag: String?,
    val lastModified: String?,
    val kind: FileKind,
    val resumeSupport: ResumeSupport,
)
sealed interface FileProbeResult {
    data class FileFound(val metadata: FileMetadata) : FileProbeResult
    data class NotFile(val reason: String) : FileProbeResult
    data class Rejected(val reason: String) : FileProbeResult
}
sealed interface FileDownloadResult {
    data class Completed(val bytes: Long) : FileDownloadResult
    data class Stopped(val bytes: Long) : FileDownloadResult
    data class RestartRequired(val reason: String) : FileDownloadResult
    data class Failed(val reason: String) : FileDownloadResult
}
