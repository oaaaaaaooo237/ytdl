package com.garyapp.ytdl.data

import android.content.Intent
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.download.DownloadFailureMessages
import com.garyapp.ytdl.download.DownloadOutputFile
import com.garyapp.ytdl.download.DownloadOutputKind
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.storage.ExportController
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoryPrivacyTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: YtdlDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YtdlDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        historyDao = database.historyDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun completedTaskCreatesHistoryWithoutCookiesHeadersCommandsOrSensitiveQuery() {
        val output = temp.newFile("download.mp4").apply { writeText("media") }
        val request = request(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q&access_token=raw-secret",
            title = "标题 Cookie: SID=secret",
            cookiesPath = "D:/private/SID-secret-cookies.txt",
        )
        val completed = DownloadTaskState.waiting(request).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, output.absolutePath, output.length())),
        ).getOrThrow()

        val history = HistoryItemEntity.fromTaskState(
            completed,
            "1080p --cookies \"D:/private cookies/cookies.txt\" Authorization: Bearer raw-token",
            1_000,
        )
        val stored = history.toString()

        assertEquals(HistoryItemEntity.STATUS_COMPLETED, history.status)
        assertEquals(100, history.progress)
        assertTrue(history.outputUri.startsWith("app-private://outputs/"))
        listOf("raw-secret", "SID=secret", "SID-secret", "--cookies", "raw-token", "watch?v=").forEach {
            assertFalse("history leaked $it", stored.contains(it))
        }
        assertTrue(stored.contains("[已隐藏]"))
    }

    @Test
    fun failedAndCanceledTasksCreateRecoverableSafeHistory() {
        val request = request(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q&cookies=secret-cookie",
            title = "测试视频",
            cookiesPath = "content://provider/cookies",
        )
        val failed = DownloadTaskState.waiting(request)
            .failed("network failed Cookie: SID=secret Authorization: Bearer raw-token")
        val canceled = DownloadTaskState.waiting(request).canceled()

        val failedHistory = HistoryItemEntity.fromTaskState(failed, "720p", 2_000)
        val canceledHistory = HistoryItemEntity.fromTaskState(canceled, "720p", 3_000)
        val serialized = listOf(failedHistory, canceledHistory).joinToString()

        assertEquals(HistoryItemEntity.STATUS_FAILED, failedHistory.status)
        assertEquals(HistoryItemEntity.STATUS_CANCELED, canceledHistory.status)
        assertTrue(failedHistory.errorSummary.contains("网络"))
        assertEquals("下载已取消，可重新开始。", canceledHistory.errorSummary)
        listOf("secret-cookie", "SID=secret", "raw-token", "content://provider/cookies").forEach {
            assertFalse("history leaked $it", serialized.contains(it))
        }
    }

    @Test
    fun historyDaoPersistsTerminalTaskStatesSafely() {
        val output = temp.newFile("dao-download.mp4").apply { writeText("media") }
        val completedRequest = request(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q&jwt=raw-jwt",
            title = "完成视频",
            cookiesPath = "D:/private/cookies.txt",
        )
        val failedRequest = request(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q&cookies=raw-cookie",
            title = "失败视频 Authorization: Bearer title-token",
            cookiesPath = "content://provider/private-cookies",
        )
        val completed = DownloadTaskState.waiting(completedRequest).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, output.absolutePath, output.length())),
        ).getOrThrow()
        val failed = DownloadTaskState.waiting(failedRequest)
            .failed("processing failed --cookies D:/private/cookies.txt Authorization: Bearer raw-token")

        historyDao.insertFromTaskState(completed, "1080p --cookies D:/private/cookies.txt", 10_000)
        historyDao.insertFromTaskState(failed, "720p Authorization: Bearer raw-token", 20_000)

        val rows = historyDao.listRecent(10)
        val serialized = rows.joinToString()

        assertEquals(listOf(HistoryItemEntity.STATUS_FAILED, HistoryItemEntity.STATUS_COMPLETED), rows.map { it.status })
        listOf("raw-jwt", "raw-cookie", "title-token", "raw-token", "--cookies", "content://provider/private-cookies").forEach {
            assertFalse("history DAO leaked $it", serialized.contains(it))
        }
        assertTrue(serialized.contains("[已隐藏]"))
    }

    @Test
    fun completedHistoryRequiresRealMediaOutput() {
        val completedWithoutOutput = DownloadTaskState(
            stage = DownloadStage.Completed,
            request = request(
                url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
                title = "无输出完成态",
                cookiesPath = null,
            ),
            outputs = emptyList(),
        )

        val result = runCatching {
            HistoryItemEntity.fromTaskState(completedWithoutOutput, "1080p", 30_000)
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("媒体输出"))
    }

    @Test
    fun completedHistoryRequiresExistingMediaOutputFile() {
        val missingOutput = File(temp.root, "missing-output.mp4")
        val completedWithMissingOutput = DownloadTaskState(
            stage = DownloadStage.Completed,
            request = request(
                url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
                title = "缺失输出完成态",
                cookiesPath = null,
            ),
            outputs = listOf(
                DownloadOutputFile(
                    kind = DownloadOutputKind.Media,
                    path = missingOutput.absolutePath,
                    bytesWritten = 1024,
                ),
            ),
        )

        val result = runCatching {
            HistoryItemEntity.fromTaskState(completedWithMissingOutput, "1080p", 31_000)
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("媒体输出"))
    }

    @Test
    fun appPrivateDiscoveryAndExportContractsDoNotExposeRawPathsOrClaimSuccess() {
        val appRoot = temp.newFolder("app-files")
        val output = File(appRoot, "video.mp4").apply { writeText("media") }

        val discovered = ExportController.discoverAppPrivateOutput(output, appRoot).getOrThrow()
        val createDocumentIntent = ExportController.createDocumentIntent(discovered)
        val mediaStoreValues = ExportController.mediaStoreValues(discovered)

        assertEquals("video.mp4", discovered.displayName)
        assertEquals("video/mp4", discovered.mimeType)
        assertTrue(discovered.appPrivateUri.startsWith("app-private://outputs/"))
        assertFalse(discovered.toString().contains(appRoot.absolutePath))
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, createDocumentIntent.action)
        assertTrue(createDocumentIntent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        assertEquals("video/mp4", createDocumentIntent.type)
        assertEquals("video.mp4", createDocumentIntent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("video.mp4", mediaStoreValues.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("video/mp4", mediaStoreValues.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        val exportedBytes = ByteArrayOutputStream()
        val bytesCopied = ExportController.copyToStream(discovered, exportedBytes).getOrThrow()
        assertEquals(output.length(), bytesCopied)
        assertEquals("media", exportedBytes.toString(Charsets.UTF_8.name()))
        assertFalse(ExportController.exportDeniedMessage("D:/private/cookies.txt").contains("D:/private"))
    }

    @Test
    fun userReadableFailureMessagesCoverM8CasesWithoutSensitiveLeakage() {
        val messages = listOf(
            UrlPolicy.evaluate("  ").userMessage.orEmpty(),
            UrlPolicy.evaluate("https://exa mple.com/watch?token=secret").userMessage.orEmpty(),
            UrlPolicy.evaluate("ftp://example.com/watch?token=secret").userMessage.orEmpty(),
            DownloadFailureMessages.network("failed Cookie: SID=secret"),
            DownloadFailureMessages.missingSubtitle("en", "secret-token"),
            DownloadFailureMessages.processing("mux failed Authorization: Bearer raw-token"),
            DownloadFailureMessages.saveExportDenied("content://provider/cookies"),
            DownloadFailureMessages.cookiesCleanupFailed(),
            DownloadFailureMessages.historyWriteFailed(),
            DownloadFailureMessages.canceled(),
        )
        val joined = messages.joinToString("\n")

        listOf("secret", "raw-token", "SID=", "content://provider/cookies").forEach {
            assertFalse("failure message leaked $it", joined.contains(it, ignoreCase = true))
        }
        listOf("请输入", "有效", "http", "网络", "字幕", "处理", "导出", "cookies", "清理", "历史", "取消").forEach {
            assertTrue("missing readable cue $it", joined.contains(it))
        }
    }

    private fun request(
        url: String,
        title: String,
        cookiesPath: String?,
    ): DownloadRequest {
        return DownloadRequest.fromAnalysis(
            url = url,
            analysis = VideoAnalysis(
                title = title,
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
}
