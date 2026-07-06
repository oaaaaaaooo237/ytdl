package com.garyapp.ytdl.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.data.YtdlDatabase
import com.garyapp.ytdl.ui.FormatMode
import com.garyapp.ytdl.ui.FormatSelection
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadHistoryRecorderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: YtdlDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, YtdlDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun recordsTerminalCompletedFailedAndCanceledStatesToHistory() {
        val output = temp.newFile("completed.mp4").apply { writeText("media") }
        val recorder = DownloadHistoryRecorder(
            historyDao = database.historyDao(),
            clock = { 42_000L },
        )
        val completed = DownloadTaskState.waiting(request(title = "完成视频")).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, output.absolutePath, output.length())),
        ).getOrThrow()
        val failed = DownloadTaskState.waiting(request(title = "失败视频"))
            .failed("network failed Cookie: SID=secret")
        val canceled = DownloadTaskState.waiting(request(title = "取消视频")).canceled()

        assertTrue(recorder.recordTerminal(completed, "360p").isSuccess)
        assertTrue(recorder.recordTerminal(failed, "360p").isSuccess)
        assertTrue(recorder.recordTerminal(canceled, "360p").isSuccess)

        val rows = database.historyDao().listRecent(10)

        assertEquals(
            listOf(
                HistoryItemEntity.STATUS_CANCELED,
                HistoryItemEntity.STATUS_FAILED,
                HistoryItemEntity.STATUS_COMPLETED,
            ),
            rows.map { it.status },
        )
        assertEquals(42_000L, rows.first().completedAt)
        listOf("SID=secret", "Cookie:", "watch?v=").forEach {
            assertTrue("history should not leak $it", !rows.joinToString().contains(it))
        }
    }

    @Test
    fun refusesToRecordCompletedHistoryWhenMediaOutputIsMissing() {
        val recorder = DownloadHistoryRecorder(
            historyDao = database.historyDao(),
            clock = { 43_000L },
        )
        val missingOutput = File(temp.root, "missing.mp4")
        val forgedCompleted = DownloadTaskState(
            stage = DownloadStage.Completed,
            request = request(title = "伪完成"),
            outputs = listOf(DownloadOutputFile(DownloadOutputKind.Media, missingOutput.absolutePath, 1024L)),
        )

        val result = recorder.recordTerminal(forgedCompleted, "1080p")

        assertTrue(result.isFailure)
        assertTrue(database.historyDao().listRecent(10).isEmpty())
    }

    @Test
    fun recordsSafeThumbnailUrlForCompletedHistory() {
        val output = temp.newFile("completed-with-thumb.mp4").apply { writeText("media") }
        val recorder = DownloadHistoryRecorder(
            historyDao = database.historyDao(),
            clock = { 44_000L },
        )
        val completed = DownloadTaskState.waiting(
            request(
                title = "带缩略图视频",
                thumbnailUrl = "https://i.ytimg.com/vi/tkxzMEfp49Q/hqdefault.jpg?token=secret#frag",
            ),
        ).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, output.absolutePath, output.length())),
        ).getOrThrow()

        assertTrue(recorder.recordTerminal(completed, "1080p").isSuccess)

        val row = database.historyDao().listRecent(1).single()
        assertEquals("https://i.ytimg.com/vi/tkxzMEfp49Q/hqdefault.jpg", row.thumbnailUrl)
        assertTrue(!row.toString().contains("token=secret"))
        assertTrue(!row.toString().contains("#frag"))
    }

    @Test
    fun rejectsSensitiveThumbnailUrlPartsBeforeHistoryPersistence() {
        val output = temp.newFile("completed-with-sensitive-thumb.mp4").apply { writeText("media") }
        val recorder = DownloadHistoryRecorder(
            historyDao = database.historyDao(),
            clock = { 45_000L },
        )
        val completed = DownloadTaskState.waiting(
            request(
                title = "敏感缩略图视频",
                thumbnailUrl = "https://user:pass@example.com/path/token-secret/hqdefault.jpg?token=secret#frag",
            ),
        ).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, output.absolutePath, output.length())),
        ).getOrThrow()

        assertTrue(recorder.recordTerminal(completed, "1080p").isSuccess)

        val row = database.historyDao().listRecent(1).single()
        assertEquals(null, row.thumbnailUrl)
        listOf("user:pass", "token-secret", "token=secret", "#frag").forEach {
            assertTrue("thumbnail history leaked $it", !row.toString().contains(it))
        }
    }

    @Test
    fun rejectsSensitiveThumbnailUrlHostBeforeHistoryPersistence() {
        val output = temp.newFile("completed-with-sensitive-host-thumb.mp4").apply { writeText("media") }
        val recorder = DownloadHistoryRecorder(
            historyDao = database.historyDao(),
            clock = { 46_000L },
        )
        val completed = DownloadTaskState.waiting(
            request(
                title = "敏感主机缩略图视频",
                thumbnailUrl = "https://token-secret.example.com/hqdefault.jpg",
            ),
        ).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, output.absolutePath, output.length())),
        ).getOrThrow()

        assertTrue(recorder.recordTerminal(completed, "1080p").isSuccess)

        val row = database.historyDao().listRecent(1).single()
        assertEquals(null, row.thumbnailUrl)
        assertTrue(!row.toString().contains("token-secret"))
    }

    @Test
    fun historyRecordingFailureCreatesVisibleSafeFailureState() {
        val output = temp.newFile("completed-before-history-failure.mp4").apply { writeText("media") }
        val completed = DownloadTaskState.waiting(request(title = "完成视频")).completeWith(
            listOf(DownloadOutputFile(DownloadOutputKind.Media, output.absolutePath, output.length())),
        ).getOrThrow()

        val finalState = applyHistoryRecordingResult(
            state = completed,
            recordResult = Result.failure(IllegalStateException("insert failed --cookies D:/private/cookies.txt Authorization: Bearer raw-token")),
        )

        assertEquals(DownloadStage.Failed, finalState.stage)
        assertTrue(finalState.errorMessage.orEmpty().contains("历史"))
        listOf("--cookies", "D:/private", "raw-token", "Authorization").forEach {
            assertTrue("history write failure leaked $it", !finalState.errorMessage.orEmpty().contains(it))
        }
    }

    private fun request(title: String, thumbnailUrl: String? = null): DownloadRequest {
        return DownloadRequest.fromAnalysis(
            url = "https://www.youtube.com/watch?v=tkxzMEfp49Q",
            analysis = VideoAnalysis(
                title = title,
                durationSeconds = 60,
                thumbnailUrl = thumbnailUrl,
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
        ).getOrThrow()
    }
}
