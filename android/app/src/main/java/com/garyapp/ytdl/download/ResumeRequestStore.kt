package com.garyapp.ytdl.download

import android.content.Context
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

internal interface ResumeRequestStore {
    fun save(request: DownloadRequest): Result<Unit>
    fun saveNew(request: DownloadRequest): Result<Unit> = save(request)
    fun load(taskId: String): Result<DownloadRequest>
    fun delete(taskId: String): Result<Unit>
}

internal class FileResumeRequestStore(
    rootDirectory: File,
    private val cipher: RetryPayloadCipher,
    private val writer: AtomicSnapshotWriter = AtomicSnapshotWriter(),
) : ResumeRequestStore {
    private val guard = ControlledDirectory(rootDirectory)

    override fun saveNew(request: DownloadRequest): Result<Unit> = synchronized(NewRequestLock) {
        runCatching {
            requireTaskId(request.taskId)
            val target = guard.child(guard.directory(true), "${request.taskId}.bin")
            check(!target.exists()) { "恢复信息已存在。" }
            save(request).getOrThrow()
        }
    }

    @Synchronized
    override fun save(request: DownloadRequest): Result<Unit> = runCatching {
        requireTaskId(request.taskId)
        val encrypted = cipher.encrypt(encode(request))
        require(encrypted.size in 1..MaxEncryptedBytes) { "恢复信息过大。" }
        val target = guard.child(guard.directory(true), "${request.taskId}.bin")
        writer.write(target, encrypted, guard)
    }

    @Synchronized
    override fun load(taskId: String): Result<DownloadRequest> = runCatching {
        requireTaskId(taskId)
        val file = guard.child(guard.directory(false), "$taskId.bin")
        check(file.isFile && file.length() in 1..MaxEncryptedBytes.toLong()) { "恢复信息不可用。" }
        decode(cipher.decrypt(file.readBytes())).also {
            require(it.taskId == taskId) { "恢复任务标识不一致。" }
        }
    }

    @Synchronized
    override fun delete(taskId: String): Result<Unit> = runCatching {
        requireTaskId(taskId)
        guard.checkSafe(guard.root)
        if (!guard.root.exists()) return@runCatching
        val file = guard.child(guard.directory(false), "$taskId.bin")
        if (file.exists()) {
            check(file.isFile && file.delete()) { "恢复信息待清理。" }
        }
    }

    private fun encode(request: DownloadRequest): ByteArray {
        require(request.selectedSubtitles.size <= 256) { "恢复信息过大。" }
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(PayloadMagic)
                output.writeInt(2)
                output.writeSafeString(request.taskId)
                output.writeSafeString(request.url)
                output.writeSafeString(request.title)
                output.writeSafeString(request.formatSummary)
                output.writeBoolean(request.usesCookies)
                when (val target = request.storageTarget) {
                    null -> output.writeInt(0)
                    StorageTarget.AppPrivate -> output.writeInt(1)
                    is StorageTarget.SafTree -> {
                        output.writeInt(2)
                        output.writeSafeString(target.treeUri)
                        output.writeBoolean(target.displayName != null)
                        target.displayName?.let { output.writeSafeString(it) }
                    }
                }
                when (val route = request.route) {
                    is DownloadRoute.DirectSingleFile -> { output.writeInt(1); output.writeSafeString(route.formatId) }
                    is DownloadRoute.VideoOnly -> { output.writeInt(2); output.writeSafeString(route.videoFormatId) }
                    is DownloadRoute.AudioOnly -> { output.writeInt(3); output.writeSafeString(route.audioFormatId) }
                    is DownloadRoute.MergeRequired -> {
                        output.writeInt(4)
                        output.writeSafeString(route.videoFormatId)
                        output.writeSafeString(route.audioFormatId)
                    }
                }
                output.writeInt(request.selectedSubtitles.size)
                request.selectedSubtitles.forEach {
                    output.writeSafeString(it.language)
                    output.writeSafeString(it.ext)
                    output.writeSafeString(it.source.name)
                }
                output.writeBoolean(request.cookiesSelectionPinned)
                output.writeBoolean(request.pinnedCookiesReference != null)
                request.pinnedCookiesReference?.let {
                    output.writeSafeString(it.value)
                    output.writeSafeString(it.displayName.orEmpty())
                }
            }
        }.toByteArray().also { require(it.size <= MaxPayloadBytes) { "恢复信息过大。" } }
    }

    private fun decode(payload: ByteArray): DownloadRequest {
        require(payload.size in 1..MaxPayloadBytes) { "恢复信息不可用。" }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == PayloadMagic) { "恢复信息不可用。" }
            val version = input.readInt()
            require(version in 1..2) { "恢复信息不可用。" }
            val id = input.readUTF()
            val url = input.readUTF()
            val title = input.readUTF()
            val summary = input.readUTF()
            val usesCookies = input.readBoolean()
            val target = when (input.readInt()) {
                0 -> null
                1 -> StorageTarget.AppPrivate
                2 -> StorageTarget.SafTree(input.readUTF(), if (input.readBoolean()) input.readUTF() else null)
                else -> error("恢复保存位置不可用。")
            }
            val route = when (input.readInt()) {
                1 -> DownloadRoute.DirectSingleFile(input.readUTF())
                2 -> DownloadRoute.VideoOnly(input.readUTF())
                3 -> DownloadRoute.AudioOnly(input.readUTF())
                4 -> DownloadRoute.MergeRequired(input.readUTF(), input.readUTF())
                else -> error("恢复格式不可用。")
            }
            val count = input.readInt()
            require(count in 0..256) { "恢复信息不可用。" }
            val subtitles = List(count) {
                SubtitleInfo(input.readUTF(), input.readUTF(), SubtitleSource.valueOf(input.readUTF()))
            }
            val pinned = version >= 2 && input.readBoolean()
            val reference = if (version >= 2 && input.readBoolean()) {
                com.garyapp.ytdl.core.settings.CookiesReference.fromUserReference(input.readUTF(), input.readUTF())
                    ?: error("恢复授权引用不可用。")
            } else null
            require(input.available() == 0) { "恢复信息不可用。" }
            DownloadRequest(url, title, route, selectedSubtitles = subtitles, formatSummary = summary,
                taskId = id, usesCookies = usesCookies, storageTarget = target,
                cookiesSelectionPinned = pinned, pinnedCookiesReference = reference)
        }
    }

    private fun DataOutputStream.writeSafeString(value: String) {
        require(value.length <= 16 * 1024) { "恢复信息字段过长。" }
        writeUTF(value)
    }

    companion object {
        private val NewRequestLock = Any()
        fun fromContext(context: Context) = FileResumeRequestStore(
            File(context.noBackupFilesDir, "resume-requests"), AndroidKeyStoreRetryPayloadCipher(),
        )
        private const val PayloadMagic = 0x59545253
        private const val MaxPayloadBytes = 1024 * 1024
        private const val MaxEncryptedBytes = MaxPayloadBytes + 1024
    }
}
