package com.garyapp.ytdl.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

interface MediaProcessor {
    val processorName: String

    val supportsSubtitleEmbed: Boolean
        get() = false

    val supportsSubtitleBurn: Boolean
        get() = false

    fun mergeVideoAndAudio(request: MediaMergeRequest): Result<MediaProcessingResult>

    fun embedSubtitles(
        inputFile: File,
        subtitlesFile: File,
        outputFile: File,
    ): Result<MediaProcessingResult> = unsupportedSubtitleOperation("字幕嵌入")

    fun burnSubtitles(
        inputFile: File,
        subtitlesFile: File,
        outputFile: File,
    ): Result<MediaProcessingResult> = unsupportedSubtitleOperation("字幕烧录")

    private fun unsupportedSubtitleOperation(operationName: String): Result<MediaProcessingResult> {
        return Result.failure(
            UnsupportedOperationException(
                "$processorName 不支持$operationName；请路由到后续 Android ffmpeg processor。",
            ),
        )
    }
}

class NativeMuxerMediaProcessor(
    private val controlledOutputRoot: File,
) : MediaProcessor {
    override val processorName: String = "android-native-muxer"

    override fun mergeVideoAndAudio(request: MediaMergeRequest): Result<MediaProcessingResult> {
        return try {
            validateMergeRequest(request)
            Result.success(mergeValidatedRequest(request))
        } catch (exc: Throwable) {
            deleteOutputIfSafe(request)
            Result.failure(exc)
        }
    }

    private fun mergeValidatedRequest(request: MediaMergeRequest): MediaProcessingResult {
        val videoInput = request.videoInput.canonicalFile
        val audioInput = request.audioInput.canonicalFile
        val outputFile = request.outputFile.canonicalFile

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists() && !outputFile.delete()) {
            throw MediaProcessingValidationException("无法覆盖已有输出文件。")
        }

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            videoExtractor.setDataSource(videoInput.absolutePath)
            audioExtractor.setDataSource(audioInput.absolutePath)

            val videoTrack = requireTrack(videoExtractor, "video/", "视频轨")
            val audioTrack = requireTrack(audioExtractor, "audio/", "音频轨")

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val outputVideoTrack = muxer.addTrack(videoFormat)
            val outputAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)
            copySelectedTrack(videoExtractor, muxer, outputVideoTrack, videoFormat)
            copySelectedTrack(audioExtractor, muxer, outputAudioTrack, audioFormat)

            muxer.stop()
            muxerStarted = false
        } finally {
            videoExtractor.release()
            audioExtractor.release()
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            muxer?.release()
        }

        if (!outputFile.isFile || outputFile.length() <= 0L) {
            throw MediaProcessingValidationException("合并输出文件为空。")
        }

        return MediaProcessingResult(
            outputFile = outputFile,
            bytesWritten = outputFile.length(),
            videoTrackCount = 1,
            audioTrackCount = 1,
            processorName = processorName,
        )
    }

    private fun requireTrack(
        extractor: MediaExtractor,
        mimePrefix: String,
        trackLabel: String,
    ): Int {
        for (trackIndex in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(trackIndex)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
            if (mime.startsWith(mimePrefix)) {
                return trackIndex
            }
        }
        throw MediaProcessingValidationException("输入文件缺少$trackLabel。")
    }

    private fun copySelectedTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outputTrackIndex: Int,
        sourceFormat: MediaFormat,
    ) {
        val bufferSize = if (sourceFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            sourceFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(DefaultCopyBufferSize)
        } else {
            DefaultCopyBufferSize
        }
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) {
                break
            }
            val sampleTimeUs = extractor.sampleTime.let { if (it >= 0L) it else 0L }
            bufferInfo.set(
                0,
                sampleSize,
                sampleTimeUs,
                extractor.sampleFlags,
            )
            muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }

    private fun validateMergeRequest(request: MediaMergeRequest) {
        val videoInput = request.videoInput.canonicalFile
        val audioInput = request.audioInput.canonicalFile
        val outputFile = request.outputFile.canonicalFile
        val outputRoot = controlledOutputRoot.canonicalFile

        requireReadableFile(videoInput, "videoInput")
        requireReadableFile(audioInput, "audioInput")

        if (videoInput == audioInput) {
            throw MediaProcessingValidationException("videoInput 和 audioInput 必须是两个不同文件。")
        }

        if (outputFile == videoInput || outputFile == audioInput) {
            throw MediaProcessingValidationException("outputFile 不能覆盖输入文件。")
        }

        if (!outputFile.isInside(outputRoot)) {
            throw MediaProcessingValidationException("outputFile 必须位于受控输出目录内。")
        }

        if (outputFile.exists() && outputFile.isDirectory) {
            throw MediaProcessingValidationException("outputFile 不能是目录。")
        }

        if (request.expectedVideoFormatId.isBlank()) {
            throw MediaProcessingValidationException("expectedVideoFormatId 不能为空。")
        }

        if (request.expectedAudioFormatId.isBlank()) {
            throw MediaProcessingValidationException("expectedAudioFormatId 不能为空。")
        }

        if (request.outputContainer != MediaOutputContainer.Mp4) {
            throw MediaProcessingValidationException("原生 muxer 第一阶段仅支持 MP4 输出。")
        }
    }

    private fun requireReadableFile(file: File, fieldName: String) {
        if (!file.isFile || !file.canRead()) {
            throw MediaProcessingValidationException("$fieldName 必须是可读取文件。")
        }
    }

    private fun File.isInside(root: File): Boolean {
        return this == root || path.startsWith(root.path + File.separator)
    }

    private fun deleteOutputIfSafe(request: MediaMergeRequest) {
        runCatching {
            val outputFile = request.outputFile.canonicalFile
            val outputRoot = controlledOutputRoot.canonicalFile
            val videoInput = request.videoInput.canonicalFile
            val audioInput = request.audioInput.canonicalFile

            if (
                outputFile.isInside(outputRoot) &&
                outputFile != videoInput &&
                outputFile != audioInput &&
                outputFile.isFile
            ) {
                outputFile.delete()
            }
        }
    }

    private companion object {
        private const val DefaultCopyBufferSize = 1024 * 1024
    }
}
