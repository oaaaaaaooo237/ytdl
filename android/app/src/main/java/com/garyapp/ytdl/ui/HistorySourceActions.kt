package com.garyapp.ytdl.ui

import com.garyapp.ytdl.core.policy.isMagnetDownloadAddress
import com.garyapp.ytdl.download.AtomicSnapshotWriter
import com.garyapp.ytdl.download.ControlledDirectory
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.RetryDownloadDraft
import java.io.File

internal sealed interface HistorySourceTarget {
    data class Address(val value: String) : HistorySourceTarget
    data class TorrentFile(val file: File) : HistorySourceTarget
}

internal fun historySourceTarget(item: HistoryUiItem, draft: RetryDownloadDraft, filesDirectory: File): HistorySourceTarget {
    val root = filesDirectory.canonicalFile
    val route = draft.route
    if (route !is DownloadRoute.Torrent || isMagnetDownloadAddress(route.source)) {
        require(draft.url.isNotBlank())
        return HistorySourceTarget.Address(draft.url)
    }
    if (route.source.startsWith("file:")) {
        val source = File(java.net.URI(route.source)).canonicalFile
        val legacyRoot = File(root, "torrents")
        require(source.name == "source.torrent" && source.path.startsWith(legacyRoot.path + File.separator))
        val magnet = File(source.parentFile, "magnet.txt")
        if (magnet.isFile && magnet.absoluteFile == magnet.canonicalFile && magnet.length() in 1..16_384L) {
            val address = magnet.readText().takeIf(::isMagnetDownloadAddress)
            if (address != null) return HistorySourceTarget.Address(address)
        }
    }
    val id = requireNotNull(item.taskId)
    require(id.matches(Regex("[0-9a-f]{32}")))
    val saved = File(root, "torrent-descriptors/$id.torrent")
    val source = saved.takeIf { it.isFile } ?: File(root, "gui-downloads/task-$id/source.torrent")
    require(source.absoluteFile == source.canonicalFile && source.isFile && source.length() in 1..4L * 1024 * 1024)
    if (source != saved) {
        val guard = ControlledDirectory(requireNotNull(saved.parentFile))
        AtomicSnapshotWriter().write(guard.child(guard.directory(true), saved.name), source.readBytes(), guard)
    }
    return HistorySourceTarget.TorrentFile(saved)
}
