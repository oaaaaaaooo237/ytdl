package com.garyapp.ytdl.media

import java.io.File

data class MediaMergeRequest(
    val videoInput: File,
    val audioInput: File,
    val outputFile: File,
    val outputContainer: MediaOutputContainer,
    val expectedVideoFormatId: String,
    val expectedAudioFormatId: String,
)

enum class MediaOutputContainer(
    val extension: String,
) {
    Mp4("mp4"),
}

data class MediaProcessingResult(
    val outputFile: File,
    val bytesWritten: Long,
    val videoTrackCount: Int,
    val audioTrackCount: Int,
    val processorName: String,
)

class MediaProcessingValidationException(
    message: String,
) : IllegalArgumentException(message)
