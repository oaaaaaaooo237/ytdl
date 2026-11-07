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
    fun loadMagnet(magnet: String, workspace: File): ByteArray {
        check(!closed)
        require(isTorrentMagnet(magnet))
        return requireNotNull(session.fetchMagnet(magnet, 60, workspace)) {
            "未能取得种子信息。"
        }
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

internal fun isTorrentMagnet(link: String): Boolean {
    if (!link.startsWith("magnet:?") || link.length > 16_384) return false
    return link.substringAfter('?').split('&').any { parameter ->
        if (parameter.substringBefore('=') != "xt") return@any false
        val topic = runCatching {
            java.net.URLDecoder.decode(parameter.substringAfter('=', ""), "UTF-8")
        }.getOrNull() ?: return@any false
        TorrentExactTopic.matches(topic)
    }
}

private val TorrentExactTopic = Regex(
    "urn:(?:btih:(?:[0-9a-f]{40}|[a-z2-7]{32})|btmh:1220[0-9a-f]{64})",
    RegexOption.IGNORE_CASE,
)