package com.garyapp.ytdl.torrent

import com.frostwire.jlibtorrent.AddTorrentParams
import com.frostwire.jlibtorrent.FileStorage
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentInfo
import java.io.Closeable
import java.io.File

internal data class TorrentProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Int,
    val finished: Boolean,
)

internal class TorrentEngine : Closeable {
    private val session = SessionManager().apply { start() }
    private var closed = false

    @Synchronized
    fun loadMagnet(magnet: String, workspace: File, shouldStop: () -> Boolean = { false }): ByteArray {
        check(!closed)
        require(isTorrentMagnet(magnet))
        repeat(12) {
            check(!shouldStop()) { "种子分析已停止。" }
            session.fetchMagnet(magnet, 5, workspace)?.let { return it }
        }
        error("未能取得种子信息。")
    }

    @Synchronized
    fun download(metadata: ByteArray, workspace: File, magnet: String? = null, beforeSubmit: () -> Unit): TorrentInfo {
        check(!closed)
        require(metadata.size in 1..MaxMetadataBytes)
        require(workspace.isDirectory && workspace.absoluteFile == workspace.canonicalFile)
        val info = TorrentInfo(metadata)
        require(info.isValid)
        val files = info.files()
        for (index in 0 until files.numFiles()) {
            require(!files.fileFlags(index).and_(FileStorage.FLAG_SYMLINK).nonZero())
            val path = files.filePath(index)
            require(path.isNotBlank() && !File(path).isAbsolute)
            require(path.split('/', '\\').none { it == ".." })
            val output = File(workspace, path).canonicalFile
            require(output.path.startsWith(workspace.path + File.separator))
        }
        val hints = magnet?.let(AddTorrentParams::parseMagnetUri)
        hints?.trackers()?.forEach { info.addTracker(it) }
        hints?.urlSeeds()?.forEach { info.addUrlSeed(it) }
        beforeSubmit()
        session.download(info, workspace, null, null, hints?.peers(), torrent_flags_t())
        return info
    }

    @Synchronized
    fun progress(info: TorrentInfo): TorrentProgress? {
        check(!closed)
        val handle = session.find(info)?.takeIf { it.isValid } ?: return null
        val status = handle.status()
        check(status.errorCode().value() == 0) { "种子传输失败。" }
        return TorrentProgress(status.totalWantedDone(), status.totalWanted(), status.downloadRate(), status.isFinished)
    }

    @Synchronized
    fun pause(info: TorrentInfo) {
        check(!closed)
        session.find(info)?.takeIf { it.isValid }?.pause()
    }

    @Synchronized
    fun resume(info: TorrentInfo) {
        check(!closed)
        session.find(info)?.takeIf { it.isValid }?.resume()
    }

    @Synchronized
    fun remove(info: TorrentInfo) {
        check(!closed)
        session.find(info)?.takeIf { it.isValid }?.let { session.remove(it) }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        session.stop()
    }

    companion object {
        const val MaxMetadataBytes = 4 * 1024 * 1024
    }
}

internal fun isTorrentMagnet(link: String): Boolean = com.garyapp.ytdl.core.policy.isMagnetDownloadAddress(link)
