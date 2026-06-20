package com.garyapp.ytdl.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaProcessorContractTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun mergeRequiresDistinctReadableVideoAndAudioInputs() {
        val outputRoot = tempFolder.newFolder("outputs")
        val processor = NativeMuxerMediaProcessor(outputRoot)
        val sharedInput = tempFolder.newFile("shared.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val sameInputResult = processor.mergeVideoAndAudio(
            request(
                videoInput = sharedInput,
                audioInput = sharedInput,
                outputFile = File(outputRoot, "same-input.mp4"),
            ),
        )

        assertTrue(sameInputResult.isFailure)
        assertTrue(sameInputResult.exceptionOrNull() is MediaProcessingValidationException)

        val missingAudioResult = processor.mergeVideoAndAudio(
            request(
                videoInput = sharedInput,
                audioInput = File(tempFolder.root, "missing-audio.m4a"),
                outputFile = File(outputRoot, "missing-audio.mp4"),
            ),
        )

        assertTrue(missingAudioResult.isFailure)
        assertTrue(missingAudioResult.exceptionOrNull() is MediaProcessingValidationException)
    }

    @Test
    fun mergeOutputMustStayInsideControlledRootAndMustNotOverwriteInputs() {
        val outputRoot = tempFolder.newFolder("outputs")
        val processor = NativeMuxerMediaProcessor(outputRoot)
        val videoInput = tempFolder.newFile("video-only.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val audioInput = tempFolder.newFile("audio-only.m4a").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }

        val outsideRootResult = processor.mergeVideoAndAudio(
            request(
                videoInput = videoInput,
                audioInput = audioInput,
                outputFile = File(tempFolder.root, "outside-root.mp4"),
            ),
        )

        assertTrue(outsideRootResult.isFailure)
        assertTrue(outsideRootResult.exceptionOrNull() is MediaProcessingValidationException)

        val overwriteInputResult = processor.mergeVideoAndAudio(
            request(
                videoInput = videoInput,
                audioInput = audioInput,
                outputFile = videoInput,
            ),
        )

        assertTrue(overwriteInputResult.isFailure)
        assertTrue(overwriteInputResult.exceptionOrNull() is MediaProcessingValidationException)
    }

    @Test
    fun invalidOutsideOutputIsNotDeletedByFailureCleanup() {
        val outputRoot = tempFolder.newFolder("outputs")
        val processor = NativeMuxerMediaProcessor(outputRoot)
        val videoInput = tempFolder.newFile("video-only.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val audioInput = tempFolder.newFile("audio-only.m4a").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val outsideRootOutput = tempFolder.newFile("outside-root-existing.mp4").apply {
            writeBytes(byteArrayOf(9, 9, 9, 9))
        }

        val result = processor.mergeVideoAndAudio(
            request(
                videoInput = videoInput,
                audioInput = audioInput,
                outputFile = outsideRootOutput,
            ),
        )

        assertTrue(result.isFailure)
        assertTrue(outsideRootOutput.isFile)
        assertEquals(4L, outsideRootOutput.length())
    }

    @Test
    fun nativeMuxerExplicitlyKeepsSubtitleWorkForMvp2Processor() {
        val outputRoot = tempFolder.newFolder("outputs")
        val processor = NativeMuxerMediaProcessor(outputRoot)
        val videoInput = tempFolder.newFile("input.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val subtitlesInput = tempFolder.newFile("captions.srt").apply {
            writeText("1\n00:00:00,000 --> 00:00:01,000\nhello\n")
        }

        assertEquals("android-native-muxer", processor.processorName)
        assertFalse(processor.supportsSubtitleEmbed)
        assertFalse(processor.supportsSubtitleBurn)

        val embedResult = processor.embedSubtitles(
            inputFile = videoInput,
            subtitlesFile = subtitlesInput,
            outputFile = File(outputRoot, "embedded.mp4"),
        )
        val burnResult = processor.burnSubtitles(
            inputFile = videoInput,
            subtitlesFile = subtitlesInput,
            outputFile = File(outputRoot, "burned.mp4"),
        )

        assertTrue(embedResult.isFailure)
        assertTrue(burnResult.isFailure)
        assertTrue(embedResult.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(burnResult.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(embedResult.exceptionOrNull()?.message.orEmpty().contains("MVP2", ignoreCase = true))
        assertTrue(burnResult.exceptionOrNull()?.message.orEmpty().contains("MVP2", ignoreCase = true))
    }

    private fun request(
        videoInput: File,
        audioInput: File,
        outputFile: File,
    ): MediaMergeRequest = MediaMergeRequest(
        videoInput = videoInput,
        audioInput = audioInput,
        outputFile = outputFile,
        outputContainer = MediaOutputContainer.Mp4,
        expectedVideoFormatId = "137",
        expectedAudioFormatId = "140",
    )
}
