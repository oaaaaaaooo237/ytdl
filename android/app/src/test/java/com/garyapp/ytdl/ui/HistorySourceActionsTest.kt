package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.files.*
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.*
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
class HistorySourceActionsTest {
    @get:Rule val temp = TemporaryFolder()
    private val id = "c".repeat(32)
    private fun item() = HistoryUiItem(1L, "下载", "Torrent", "完成", "", HistoryItemEntity.STATUS_COMPLETED, 1L, taskId = id)

    @Test fun mediaSourceIsTheSubmittedPageRatherThanTheDownloadedOutput() {
        val page = "https://example.com/watch?id=1&list=original"
        val target = historySourceTarget(item(), RetryDownloadDraft(page, DownloadRoute.DirectSingleFile("1080p")), temp.root)
        assertEquals(HistorySourceTarget.Address(page), target)
    }

    @Test fun fileSourceIsTheOriginalUrlRatherThanTheRedirectedFileUrl() {
        val original = "https://example.com/download?id=42"
        val metadata = FileMetadata(original, "https://cdn.example.com/file.bin", "file.bin", "application/octet-stream",
            50L, null, null, FileKind.Other, ResumeSupport.Unknown)
        assertEquals(HistorySourceTarget.Address(original), historySourceTarget(item(),
            RetryDownloadDraft(original, DownloadRoute.HttpFile(metadata)), temp.root))
    }

    @Test fun magnetRetainsItsEncodedNameAndEveryTracker() {
        val magnet = "magnet:?xt=urn:btih:${"a".repeat(40)}&dn=Test+Name&tr=udp%3A%2F%2Fone&tr=https%3A%2F%2Ftwo"
        assertEquals(HistorySourceTarget.Address(magnet), historySourceTarget(item(),
            RetryDownloadDraft(magnet, DownloadRoute.Torrent(magnet)), temp.root))
    }

    @Test fun torrentExportsTheDescriptorAndRetainsItAfterTheTaskWorkspaceIsRemoved() {
        val source = File(temp.root, "gui-downloads/task-$id/source.torrent").apply { parentFile!!.mkdirs(); writeText("saved seed metadata") }
        File(source.parentFile, "movie.mkv").writeText("downloaded payload")
        val draft = RetryDownloadDraft("https://example.com/file.torrent", DownloadRoute.Torrent("https://example.com/file.torrent"))
        val exported = historySourceTarget(item(), draft, temp.root) as HistorySourceTarget.TorrentFile
        assertEquals(source.readText(), exported.file.readText())
        source.parentFile!!.deleteRecursively()
        assertEquals(exported, historySourceTarget(item(), draft, temp.root))
    }

    @Test fun recoveredLegacyMagnetUsesItsSavedAddress() {
        val source = File(temp.root, "torrents/old/source.torrent").apply { parentFile!!.mkdirs(); writeText("seed") }
        val link = "magnet:?xt=urn:btih:${"b".repeat(40)}&dn=original"
        File(source.parentFile, "magnet.txt").writeText(link)
        val address = source.toURI().toString()
        assertEquals(HistorySourceTarget.Address(link), historySourceTarget(item(),
            RetryDownloadDraft(address, DownloadRoute.Torrent(address)), temp.root))
    }
}
