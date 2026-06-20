package com.garyapp.ytdl.cookies

import com.garyapp.ytdl.core.ytdlp.DownloadFormatRole
import com.garyapp.ytdl.core.ytdlp.DownloadProgressListener
import com.garyapp.ytdl.core.ytdlp.DownloadResult
import com.garyapp.ytdl.core.ytdlp.SubtitleDownloadResult
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.download.DownloadEngine
import com.garyapp.ytdl.download.DownloadOutputKind
import com.garyapp.ytdl.download.DownloadPipeline
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.download.MutableDownloadCancellation
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.media.MediaMergeRequest
import com.garyapp.ytdl.media.MediaProcessingResult
import com.garyapp.ytdl.media.MediaProcessor
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import com.garyapp.ytdl.ui.prepareTemporaryCookiesForDownload
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TemporaryCookiesFileTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun materializesReferenceIntoTaskScopedPrivateFileWithoutLeakingContent() {
        val source = temp.newFile("source-cookies.txt").apply {
            writeText("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tFALSE\t2147483647\tSID\tsecret-value\n")
        }
        val privateCache = temp.newFolder("private-cache")
        val reference = CookiesReference.fromUserSelectedReference(
            value = source.absolutePath,
            displayName = "SID=secret-value cookies.txt",
        ).getOrThrow()

        val temporary = TemporaryCookiesFile.materialize(
            reference = reference,
            privateCacheDirectory = privateCache,
            taskId = "task with spaces",
        ).getOrThrow()

        assertTrue(temporary.file.isFile)
        assertTrue(temporary.file.canonicalPath.startsWith(privateCache.canonicalPath))
        assertTrue(temporary.file.name.startsWith("ytdl-cookies-task_with_spaces"))
        assertEquals(source.readText(), temporary.file.readText())
        assertFalse(reference.toString().contains(source.absolutePath))
        assertFalse(reference.toString().contains("secret-value"))
        assertFalse(temporary.toString().contains("secret-value"))

        temporary.delete()

        assertFalse(temporary.file.exists())
    }

    @Test
    fun uiPreparationMaterializesSettingsReferenceBeforeDownloadRequest() {
        val source = temp.newFile("settings-cookies.txt").apply {
            writeText("SID=raw-secret")
        }
        val settingsReference = com.garyapp.ytdl.core.settings.CookiesReference.fromUserReference(
            reference = source.absolutePath,
            displayName = "cookies.txt",
        )

        val prepared = prepareTemporaryCookiesForDownload(
            settingsReference = settingsReference,
            privateCacheDirectory = temp.newFolder("ui-cache"),
            taskId = "ui task",
        ).getOrThrow()

        assertTrue(prepared != null)
        assertTrue(prepared!!.file.isFile)
        assertTrue(TemporaryCookiesFile.isManagedTemporaryPath(prepared.file.absolutePath))
        assertFalse(prepared.file.absolutePath == source.absolutePath)

        assertTrue(prepared.delete())
    }

    @Test
    fun rejectsCookieContentAsAReference() {
        val rejected = CookiesReference.fromUserSelectedReference(
            value = "SID=secret-value; PREF=visible",
            displayName = "cookies.txt",
        )

        assertTrue(rejected.isFailure)
        assertFalse(rejected.exceptionOrNull()?.message.orEmpty().contains("secret-value"))
    }

    @Test
    fun deleteIfManagedPathDoesNotDeleteLookalikeUserFile() {
        val userDirectory = temp.newFolder("temporary-cookies")
        val userFile = File(userDirectory, "ytdl-cookies-user-file.txt").apply {
            writeText("user-owned cookies reference")
        }

        val deleted = TemporaryCookiesFile.deleteIfManagedPath(userFile.absolutePath)

        assertFalse(deleted)
        assertTrue(userFile.exists())
    }

    @Test
    fun failedDeleteKeepsManagedPathSoCleanupCanRetry() {
        val source = temp.newFile("source-locked-cookies.txt").apply {
            writeText("SID=delete-retry-secret")
        }
        val temporary = TemporaryCookiesFile.materialize(
            reference = CookiesReference.fromUserSelectedReference(source.absolutePath, "cookies.txt").getOrThrow(),
            privateCacheDirectory = temp.newFolder("retry-cache"),
            taskId = "delete retry",
        ).getOrThrow()
        val path = temporary.file.absolutePath
        temporary.file.delete()
        temporary.file.mkdirs()
        File(temporary.file, "child.txt").writeText("force non-empty directory delete failure")

        val deletedNonEmptyDirectory = TemporaryCookiesFile.deleteIfManagedPath(path)

        assertFalse(deletedNonEmptyDirectory)
        assertTrue(temporary.file.exists())
        assertTrue(TemporaryCookiesFile.isManagedTemporaryPath(path))

        File(temporary.file, "child.txt").delete()
        assertTrue(TemporaryCookiesFile.deleteIfManagedPath(path))
        assertFalse(temporary.file.exists())
        assertFalse(TemporaryCookiesFile.isManagedTemporaryPath(path))
    }

    @Test
    fun materializeFromStreamDeletesPartialFileWhenCopyFails() {
        val privateCache = temp.newFolder("private-cache-copy-failure")

        val result = TemporaryCookiesFile.materializeFromStream(
            input = FailingInputStream("partial-cookie-data".toByteArray(Charsets.UTF_8), failAfterBytes = 7),
            privateCacheDirectory = privateCache,
            taskId = "copy failure",
        )

        assertTrue(result.isFailure)
        val temporaryDirectory = File(privateCache, "temporary-cookies")
        val remainingFiles = if (temporaryDirectory.exists()) {
            temporaryDirectory.walkTopDown().filter { it.isFile }.toList()
        } else {
            emptyList()
        }
        assertTrue("partial cookies files should be removed: $remainingFiles", remainingFiles.isEmpty())
    }

    @Test
    fun pipelineDeletesTemporaryCookiesAfterCompletionFailureAndCancellation() {
        val success = runPipelineWithTemporaryCookies(EngineMode.Success)
        assertEquals(DownloadStage.Completed, success.stage)

        val failure = runPipelineWithTemporaryCookies(EngineMode.Failure)
        assertEquals(DownloadStage.Failed, failure.stage)

        val canceled = runPipelineWithTemporaryCookies(EngineMode.CancelBeforeStart)
        assertEquals(DownloadStage.Canceled, canceled.stage)
    }

    @Test
    fun pipelineFailsSafelyWhenTemporaryCookiesCleanupFails() {
        val state = runPipelineWithTemporaryCookies(
            mode = EngineMode.CleanupFailure,
            expectTemporaryDeleted = false,
        )

        assertEquals(DownloadStage.Failed, state.stage)
        assertTrue(state.errorMessage.orEmpty().contains("cookies", ignoreCase = true))
    }

    @Test
    fun pipelineRejectsUnmanagedCookiesPathBeforeCallingEngine() {
        val rawUserCookiesFile = temp.newFile("user-cookies.txt").apply {
            writeText("SID=raw-user-secret")
        }
        val engine = UnreachableEngine()

        val result = DownloadPipeline(
            engine = engine,
            mediaProcessor = RecordingMediaProcessor(),
        ).run(
            request = request(cookiesPath = rawUserCookiesFile.absolutePath),
            outputDirectory = temp.newFolder("downloads-unmanaged-cookies"),
        )

        assertEquals(DownloadStage.Failed, result.state.stage)
        assertFalse(engine.called)
        assertTrue(rawUserCookiesFile.exists())
        assertTrue(result.state.errorMessage.orEmpty().contains("cookies", ignoreCase = true))
        assertFalse(result.state.errorMessage.orEmpty().contains(rawUserCookiesFile.absolutePath))
        assertFalse(result.state.errorMessage.orEmpty().contains("raw-user-secret"))
    }

    private fun runPipelineWithTemporaryCookies(
        mode: EngineMode,
        expectTemporaryDeleted: Boolean = true,
    ): DownloadTaskState {
        val source = temp.newFile("source-${mode.name}.txt").apply {
            writeText(".youtube.com\tTRUE\t/\tFALSE\t2147483647\tSID\t${mode.name}-secret\n")
        }
        val temporary = TemporaryCookiesFile.materialize(
            reference = CookiesReference.fromUserSelectedReference(source.absolutePath, "cookies.txt").getOrThrow(),
            privateCacheDirectory = temp.newFolder("cache-${mode.name}"),
            taskId = mode.name,
        ).getOrThrow()
        val request = request(cookiesPath = temporary.file.absolutePath)
        val cancellation = MutableDownloadCancellation()
        if (mode == EngineMode.CancelBeforeStart) {
            cancellation.cancel()
        }

        val result = DownloadPipeline(
            engine = RecordingEngine(temp.root, mode),
            mediaProcessor = RecordingMediaProcessor(),
        ).run(
            request = request,
            outputDirectory = temp.newFolder("downloads-${mode.name}"),
            cancellation = cancellation,
        )

        if (expectTemporaryDeleted) {
            assertFalse("temporary cookies file should be deleted for $mode", temporary.file.exists())
        } else {
            assertTrue("temporary cookies cleanup failure should leave retryable path for $mode", temporary.file.exists())
            File(temporary.file, "child.txt").delete()
            assertTrue(TemporaryCookiesFile.deleteIfManagedPath(temporary.file.absolutePath))
        }
        assertTrue(result.outputs.all { it.kind == DownloadOutputKind.Media })
        return result.state
    }

    private fun request(cookiesPath: String): DownloadRequest {
        return DownloadRequest.fromAnalysis(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = VideoAnalysis(
                title = "测试视频",
                durationSeconds = 60,
                thumbnailUrl = null,
                formats = listOf(
                    VideoFormat(
                        id = "18",
                        ext = "mp4",
                        height = 360,
                        label = "360p",
                        hasVideo = true,
                        hasAudio = true,
                        mergeRequired = false,
                        isSupported = true,
                        videoCodec = "avc1",
                        audioCodec = "mp4a",
                    ),
                ),
                subtitles = emptyList(),
            ),
            selection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedVideoFormatId = "18",
            ),
            cookiesPath = cookiesPath,
        ).getOrThrow()
    }

    private enum class EngineMode {
        Success,
        Failure,
        CancelBeforeStart,
        CleanupFailure,
    }

    private class RecordingEngine(
        private val root: File,
        private val mode: EngineMode,
    ) : DownloadEngine {
        override fun downloadFormat(
            url: String,
            outputDirectory: File,
            formatId: String,
            role: DownloadFormatRole,
            cookiesPath: String?,
            listener: DownloadProgressListener?,
        ): Result<DownloadResult> {
            val safeCookiesPath = requireNotNull(cookiesPath)
            require(File(safeCookiesPath).isFile)
            if (mode == EngineMode.Failure) {
                return Result.failure(IllegalStateException("network failed Cookie: SID=secret"))
            }
            val output = File(root, "download-$formatId.mp4").apply {
                writeText("media")
            }
            if (mode == EngineMode.CleanupFailure) {
                val cookiesFile = File(safeCookiesPath)
                cookiesFile.delete()
                cookiesFile.mkdirs()
                File(cookiesFile, "child.txt").writeText("force cleanup failure")
            }
            return Result.success(
                DownloadResult(
                    outputPath = output.absolutePath,
                    bytesWritten = output.length(),
                    title = "测试视频",
                    formatId = formatId,
                    role = role,
                ),
            )
        }

        override fun downloadSubtitle(
            url: String,
            outputDirectory: File,
            language: String,
            ext: String,
            source: SubtitleSource,
            cookiesPath: String?,
            listener: DownloadProgressListener?,
        ): Result<SubtitleDownloadResult> {
            error("not used")
        }
    }

    private class UnreachableEngine : DownloadEngine {
        var called: Boolean = false

        override fun downloadFormat(
            url: String,
            outputDirectory: File,
            formatId: String,
            role: DownloadFormatRole,
            cookiesPath: String?,
            listener: DownloadProgressListener?,
        ): Result<DownloadResult> {
            called = true
            throw AssertionError("engine must not be called with unmanaged cookies path")
        }

        override fun downloadSubtitle(
            url: String,
            outputDirectory: File,
            language: String,
            ext: String,
            source: SubtitleSource,
            cookiesPath: String?,
            listener: DownloadProgressListener?,
        ): Result<SubtitleDownloadResult> {
            called = true
            throw AssertionError("engine must not be called with unmanaged cookies path")
        }
    }

    private class RecordingMediaProcessor : MediaProcessor {
        override val processorName: String = "test"

        override fun mergeVideoAndAudio(request: MediaMergeRequest): Result<MediaProcessingResult> {
            error("not used")
        }
    }

    private class FailingInputStream(
        private val bytes: ByteArray,
        private val failAfterBytes: Int,
    ) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index >= failAfterBytes) {
                throw IOException("simulated copy failure")
            }
            if (index >= bytes.size) {
                return -1
            }
            return bytes[index++].toInt() and 0xff
        }
    }
}
