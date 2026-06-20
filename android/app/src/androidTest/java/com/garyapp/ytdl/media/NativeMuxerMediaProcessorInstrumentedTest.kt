package com.garyapp.ytdl.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeMuxerMediaProcessorInstrumentedTest {
    @Test
    fun mergesRuntimeGeneratedVideoOnlyAndAudioOnlyStreamsIntoMp4() {
        assertEquals("T8B 必须在 API37 模拟器执行。", 37, Build.VERSION.SDK_INT)
        val workspace = freshWorkspace("merge-success")
        val videoInput = File(workspace, "video-only.mp4")
        val audioInput = File(workspace, "audio-only.m4a")
        val outputRoot = File(workspace, "outputs").apply { mkdirs() }
        val outputFile = File(outputRoot, "merged.mp4")

        writeVideoOnlyMp4(videoInput)
        writeAudioOnlyM4a(audioInput)

        val result = NativeMuxerMediaProcessor(outputRoot).mergeVideoAndAudio(
            request(videoInput, audioInput, outputFile),
        )

        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val merged = result.getOrThrow()
        val trackCounts = countTracks(merged.outputFile)
        val evidence = "NATIVE_MUXER_T8B sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL} " +
            "videoInput=${videoInput.length()} audioInput=${audioInput.length()} " +
            "output=${merged.outputFile.absolutePath} bytes=${merged.bytesWritten} " +
            "videoTracks=${trackCounts.video} audioTracks=${trackCounts.audio}"
        println(evidence)
        Log.i("NativeMuxerT8B", evidence)

        assertTrue("输出文件不存在：${merged.outputFile}", merged.outputFile.isFile)
        assertTrue("输出文件为空：${merged.outputFile}", merged.bytesWritten > 0L)
        assertEquals(1, merged.videoTrackCount)
        assertEquals(1, merged.audioTrackCount)
        assertEquals("android-native-muxer", merged.processorName)
        assertEquals(1, trackCounts.video)
        assertEquals(1, trackCounts.audio)
    }

    @Test
    fun failsWhenInputsDoNotContainTheRequiredMediaTrack() {
        assertEquals("T8B 必须在 API37 模拟器执行。", 37, Build.VERSION.SDK_INT)
        val workspace = freshWorkspace("missing-track")
        val videoOnly = File(workspace, "video-only.mp4")
        val audioOnly = File(workspace, "audio-only.m4a")
        val outputRoot = File(workspace, "outputs").apply { mkdirs() }
        writeVideoOnlyMp4(videoOnly)
        writeAudioOnlyM4a(audioOnly)

        val audioOnlyCopy = File(audioOnly.parentFile, "audio-only-copy.m4a").also {
            audioOnly.copyTo(it, overwrite = true)
        }
        val missingVideoOutput = File(outputRoot, "missing-video.mp4")
        val missingVideoResult = NativeMuxerMediaProcessor(outputRoot).mergeVideoAndAudio(
            request(
                videoInput = audioOnlyCopy,
                audioInput = audioOnly,
                outputFile = missingVideoOutput,
            ),
        )

        assertTrue(missingVideoResult.isFailure)
        assertTrue(
            missingVideoResult.exceptionOrNull()?.message.orEmpty(),
            missingVideoResult.exceptionOrNull()?.message.orEmpty().contains("视频轨"),
        )
        assertFalse("缺少视频轨时不能产出假成功文件。", missingVideoOutput.exists() && missingVideoOutput.length() > 0L)

        val missingAudioOutput = File(outputRoot, "missing-audio.mp4")
        val missingAudioResult = NativeMuxerMediaProcessor(outputRoot).mergeVideoAndAudio(
            request(
                videoInput = videoOnly,
                audioInput = File(videoOnly.parentFile, "video-only-copy.mp4").also {
                    videoOnly.copyTo(it, overwrite = true)
                },
                outputFile = missingAudioOutput,
            ),
        )

        assertTrue(missingAudioResult.isFailure)
        assertTrue(
            missingAudioResult.exceptionOrNull()?.message.orEmpty(),
            missingAudioResult.exceptionOrNull()?.message.orEmpty().contains("音频轨"),
        )
        assertFalse("缺少音频轨时不能产出假成功文件。", missingAudioOutput.exists() && missingAudioOutput.length() > 0L)
    }

    private fun freshWorkspace(name: String): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return File(context.cacheDir, "native-muxer-t8b/$name").apply {
            deleteRecursively()
            mkdirs()
        }
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

    private fun countTracks(file: File): TrackCounts {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTracks = 0
            var audioTracks = 0
            for (trackIndex in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> videoTracks += 1
                    mime.startsWith("audio/") -> audioTracks += 1
                }
            }
            return TrackCounts(video = videoTracks, audio = audioTracks)
        } finally {
            extractor.release()
        }
    }

    private fun writeVideoOnlyMp4(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val width = 96
        val height = 96
        val frameRate = 15
        val frameCount = 12
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val bufferInfo = MediaCodec.BufferInfo()
        var inputSurface: CodecInputSurface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = CodecInputSurface(encoder.createInputSurface(), width, height)
            encoder.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                        }
                        encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "视频编码器输出格式重复变化。" }
                            val activeMuxer = muxer
                            trackIndex = activeMuxer.addTrack(encoder.outputFormat)
                            activeMuxer.start()
                            muxerStarted = true
                        }
                        encoderStatus >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(encoderStatus)
                                ?: throw IllegalStateException("视频编码器输出 buffer 为空。")
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0) {
                                check(muxerStarted) { "视频 muxer 尚未 start。" }
                                val activeMuxer = muxer
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                activeMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            val reachedEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            encoder.releaseOutputBuffer(encoderStatus, false)
                            if (reachedEnd) return
                        }
                    }
                }
            }

            inputSurface.makeCurrent()
            for (frameIndex in 0 until frameCount) {
                inputSurface.drawFrame(frameIndex)
                inputSurface.setPresentationTime(frameIndex * 1_000_000_000L / frameRate)
                inputSurface.swapBuffers()
                drainEncoder(endOfStream = false)
            }
            drainEncoder(endOfStream = true)
        } finally {
            inputSurface?.release()
            encoder.stop()
            encoder.release()
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        }

        assertTrue("video-only fixture 未生成。", outputFile.isFile && outputFile.length() > 0L)
        assertEquals(TrackCounts(video = 1, audio = 0), countTracks(outputFile))
    }

    private fun writeAudioOnlyM4a(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val sampleRate = 44_100
        val channelCount = 1
        val durationUs = 600_000L
        val totalSamples = (durationUs * sampleRate / 1_000_000L).toInt()
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096)
        }
        val bufferInfo = MediaCodec.BufferInfo()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        var samplesWritten = 0
        var inputDone = false
        var outputDone = false

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("音频编码器输入 buffer 为空。")
                        inputBuffer.clear()
                        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        val samplesThisBuffer = min(1024, totalSamples - samplesWritten)
                        val presentationTimeUs = samplesWritten * 1_000_000L / sampleRate
                        if (samplesThisBuffer <= 0) {
                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            repeat(samplesThisBuffer) { sampleOffset ->
                                val theta = 2.0 * PI * 440.0 * (samplesWritten + sampleOffset) / sampleRate
                                val sample = (sin(theta) * Short.MAX_VALUE * 0.18).toInt().toShort()
                                inputBuffer.putShort(sample)
                            }
                            encoder.queueInputBuffer(
                                inputIndex,
                                0,
                                samplesThisBuffer * BYTES_PER_PCM_16_SAMPLE * channelCount,
                                presentationTimeUs,
                                0,
                            )
                            samplesWritten += samplesThisBuffer
                        }
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "音频编码器输出格式重复变化。" }
                        val activeMuxer = muxer
                        trackIndex = activeMuxer.addTrack(encoder.outputFormat)
                        activeMuxer.start()
                        muxerStarted = true
                    }
                    outputIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(outputIndex)
                            ?: throw IllegalStateException("音频编码器输出 buffer 为空。")
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0) {
                            check(muxerStarted) { "音频 muxer 尚未 start。" }
                            val activeMuxer = muxer
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            activeMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            encoder.stop()
            encoder.release()
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
        }

        assertTrue("audio-only fixture 未生成。", outputFile.isFile && outputFile.length() > 0L)
        assertEquals(TrackCounts(video = 0, audio = 1), countTracks(outputFile))
    }

    private class CodecInputSurface(
        private val surface: Surface,
        private val width: Int,
        private val height: Int,
    ) {
        private val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val eglContext: android.opengl.EGLContext
        private val eglSurface: android.opengl.EGLSurface

        init {
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "无法获取 EGL display。" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "EGL 初始化失败。" }

            val config = chooseConfig()
            val contextAttributes = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                2,
                EGL14.EGL_NONE,
            )
            eglContext = EGL14.eglCreateContext(
                eglDisplay,
                config,
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0,
            )
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "EGL context 创建失败。" }

            eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                config,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "EGL surface 创建失败。" }
        }

        fun makeCurrent() {
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                "EGL makeCurrent 失败。"
            }
        }

        fun drawFrame(frameIndex: Int) {
            val phase = frameIndex % 3
            val red = if (phase == 0) 0.92f else 0.18f
            val green = if (phase == 1) 0.82f else 0.22f
            val blue = if (phase == 2) 0.72f else 0.28f
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(red, green, blue, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        fun setPresentationTime(nanoseconds: Long) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nanoseconds)
        }

        fun swapBuffers() {
            check(EGL14.eglSwapBuffers(eglDisplay, eglSurface)) { "EGL swapBuffers 失败。" }
        }

        fun release() {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
            surface.release()
        }

        private fun chooseConfig(): EGLConfig {
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            check(EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, configs.size, configCount, 0)) {
                "EGL config 查询失败。"
            }
            return configs[0] ?: error("没有可用的 EGL config。")
        }
    }

    private data class TrackCounts(
        val video: Int,
        val audio: Int,
    )

    private companion object {
        private const val TIMEOUT_US = 10_000L
        private const val BYTES_PER_PCM_16_SAMPLE = 2
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
