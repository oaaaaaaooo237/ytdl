package com.garyapp.ytdl.download

import com.garyapp.ytdl.ui.unifiedTasks
import com.garyapp.ytdl.ui.outputReferences
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TorrentTaskContractTest {
    @get:Rule val temp = TemporaryFolder()
    private val source = "magnet:?xt=urn:btih:" + "a".repeat(40)
    private fun request() = DownloadRequest(source, "测试种子", DownloadRoute.Torrent(source),
        taskId = "b".repeat(32), usesCookies = false, formatSummary = "Torrent")

    @Test fun torrentRequestAndProgressSurviveReopeningStores() {
        val cipher = object : RetryPayloadCipher {
            override fun encrypt(plain: ByteArray) = plain
            override fun decrypt(encrypted: ByteArray) = encrypted
        }
        val root = File(temp.root, "requests")
        FileResumeRequestStore(root, cipher).save(request()).getOrThrow()
        assertEquals(request(), FileResumeRequestStore(root, cipher).load(request().taskId).getOrThrow())
        val retryRoot = File(temp.root, "retry")
        val draft = RetryDownloadDraft.fromRequest(request())
        FileRetryDraftStore(retryRoot, cipher).save(9L, draft).getOrThrow()
        assertEquals(draft, FileRetryDraftStore(retryRoot, cipher).load(9L).getOrThrow())
        val checkpoint = PipelineCheckpoint.open(TaskWorkspace(temp.root), request())
        checkpoint.save(DownloadStage.DownloadingFile,
            com.garyapp.ytdl.core.ytdlp.DownloadProgress("downloading", 25.0, 25, 100, 5.0, null, null), DownloadStopReason.UserPause)
        val restored = PipelineCheckpoint.open(TaskWorkspace(temp.root), request())
        assertEquals(25L, restored.progress()?.downloadedBytes)
        val row = unifiedTasks(listOf(DownloadTaskState(DownloadStage.DownloadingFile, request(), progress = restored.progress())), emptyList(), emptyList()).single()
        assertEquals(request().taskId, row.id)
        assertEquals("测试种子", row.title)
    }

    @Test fun torrentCompletionKeepsEveryFileIncludingEmptyFiles() {
        val one = temp.newFile("one.bin").apply { writeText("payload") }
        val empty = temp.newFile("empty.bin")
        val outputs = listOf(one, empty).map { DownloadOutputFile(DownloadOutputKind.File, it.path, it.length()) }
        val state = DownloadTaskState.waiting(request()).completeWith(outputs).getOrThrow()
        assertEquals(DownloadStage.Completed, state.stage)
        assertEquals(2, state.outputs.size)
    }

    @Test fun torrentHistoryRetainsEveryOutputForExportWithoutLabelingFilesAsSubtitles() {
        val files = listOf(temp.newFile("one.bin"), temp.newFile("two.bin"))
        files.forEach { it.writeText("payload") }
        val outputs = files.map { DownloadOutputFile(DownloadOutputKind.File, it.path, it.length(), temp.root.canonicalPath) }
        val state = DownloadTaskState.waiting(request()).completeWith(outputs).getOrThrow()
        val record = com.garyapp.ytdl.data.HistoryItemEntity.fromTaskState(state, "Torrent", 1L)
        val item = com.garyapp.ytdl.ui.historyUiItemsFromRows(listOf(record)).single()
        assertTrue(item.isTorrent)
        assertEquals(2, item.outputReferences().size)
        assertFalse(item.outputReferences().last().isSubtitle)
    }
}
