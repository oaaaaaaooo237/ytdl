package com.garyapp.ytdl.download

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun requireTaskId(taskId: String) {
    require(taskId.matches(Regex("^[0-9a-f]{32}$"))) { "任务标识不可用。" }
}

internal class ControlledDirectory(directory: File) {
    // Canonicalize trusted ancestors, but never accept a link at the controlled root itself.
    val root = File(requireNotNull(directory.absoluteFile.parentFile).canonicalFile, directory.name)

    fun directory(create: Boolean): File {
        checkSafe(root)
        if (create) check(root.mkdirs() || root.isDirectory) { "任务目录不可创建。" }
        checkSafe(root)
        check(root.isDirectory) { "任务目录不可用。" }
        return root
    }

    fun child(parent: File, name: String): File {
        checkSafe(root)
        check(parent == root || parent.canonicalPath.startsWith(root.path + File.separator)) {
            "任务路径不可用。"
        }
        return File(parent, name).also(::checkSafe)
    }

    fun checkSafe(file: File) {
        check(file.absoluteFile == file.canonicalFile && !isLink(file)) { "任务路径不可用。" }
    }

    private fun isLink(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26) return Files.isSymbolicLink(file.toPath())
        return try {
            OsConstants.S_ISLNK(Os.lstat(file.path).st_mode)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) false else throw error
        }
    }
}

internal class AtomicSnapshotWriter(
    private val replace: (File, File) -> Unit = { source, target ->
        if (!source.renameTo(target)) {
            if (Build.VERSION.SDK_INT >= 26) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } else {
                error("任务信息不可保存。")
            }
        }
    },
) {
    fun write(target: File, bytes: ByteArray, guard: ControlledDirectory) = synchronized(WriteLock) {
        guard.checkSafe(target)
        val temporary = guard.child(requireNotNull(target.parentFile), ".${target.name}.tmp")
        check(!target.exists() || target.isFile) { "任务快照路径不可用。" }
        check(!temporary.exists() || temporary.isFile) { "任务临时路径不可用。" }
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            guard.checkSafe(target)
            guard.checkSafe(temporary)
            replace(temporary, target)
        } finally {
            // A replaced or untrusted temporary entry is never followed during cleanup.
            if (runCatching { guard.checkSafe(temporary) }.isSuccess) temporary.delete()
        }
    }

    private companion object {
        val WriteLock = Any()
    }
}

data class CompletedTaskFile(
    val role: String,
    val formatId: String,
    val relativePath: String,
    val sizeBytes: Long,
)

data class PendingFilePublication(val identity: String, val relativePath: String, val sizeBytes: Long)

data class TaskCheckpoint(
    val taskId: String,
    val phase: String,
    val stopReason: String? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val checkpointAt: Long = System.currentTimeMillis(),
    val completedFiles: List<CompletedTaskFile> = emptyList(),
    val fileRecoveryAction: String? = null,
    val pendingFilePublication: PendingFilePublication? = null,
)

internal class TaskWorkspace(
    rootDirectory: File,
    private val writer: AtomicSnapshotWriter = AtomicSnapshotWriter(),
) {
    private val guard = ControlledDirectory(rootDirectory)

    fun exists(taskId: String): Boolean {
        requireTaskId(taskId)
        guard.checkSafe(guard.root)
        if (!guard.root.exists()) return false
        return guard.child(guard.directory(false), "task-$taskId").exists()
    }

    @Synchronized
    fun directory(taskId: String, create: Boolean = false): File {
        requireTaskId(taskId)
        val root = guard.directory(create)
        val task = guard.child(root, "task-$taskId")
        if (create) check(task.mkdirs() || task.isDirectory) { "任务目录不可创建。" }
        guard.checkSafe(task)
        check(task.isDirectory) { "任务目录不可用。" }
        return task
    }

    @Synchronized
    fun saveCheckpoint(taskId: String, checkpoint: TaskCheckpoint): Result<Unit> = runCatching {
        require(checkpoint.taskId == taskId) { "任务检查点标识不一致。" }
        val task = directory(taskId, true)
        validate(taskId, task, checkpoint)
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(CheckpointMagic)
                output.writeInt(3)
                output.writeUTF(taskId)
                output.writeUTF(checkpoint.phase)
                output.writeUTF(checkpoint.stopReason.orEmpty())
                output.writeLong(checkpoint.downloadedBytes)
                output.writeLong(checkpoint.totalBytes ?: -1)
                output.writeLong(checkpoint.checkpointAt)
                output.writeInt(checkpoint.completedFiles.size)
                output.writeUTF(checkpoint.fileRecoveryAction.orEmpty())
                checkpoint.completedFiles.forEach {
                    output.writeUTF(it.role)
                    output.writeUTF(it.formatId)
                    output.writeUTF(it.relativePath)
                    output.writeLong(it.sizeBytes)
                }
                val pending = checkpoint.pendingFilePublication
                output.writeBoolean(pending != null)
                if (pending != null) {
                    output.writeUTF(pending.identity)
                    output.writeUTF(pending.relativePath)
                    output.writeLong(pending.sizeBytes)
                }
            }
        }.toByteArray()
        check(bytes.size <= MaxSnapshotBytes) { "任务检查点过大。" }
        writer.write(guard.child(task, "checkpoint.bin"), bytes, guard)
    }

    @Synchronized
    fun loadCheckpoint(taskId: String): Result<TaskCheckpoint> = runCatching {
        val task = directory(taskId)
        val source = guard.child(task, "checkpoint.bin")
        check(source.isFile && source.length() in 1..MaxSnapshotBytes.toLong()) { "任务检查点不可用。" }
        val checkpoint = DataInputStream(ByteArrayInputStream(source.readBytes())).use { input ->
            check(input.readInt() == CheckpointMagic) { "任务检查点不可用。" }
            val version = input.readInt()
            check(version in 1..3) { "任务检查点不可用。" }
            val storedId = input.readUTF()
            val phase = input.readUTF()
            val reason = input.readUTF().ifEmpty { null }
            val downloaded = input.readLong()
            val total = input.readLong().let { if (it == -1L) null else it }
            val timestamp = input.readLong()
            val count = input.readInt()
            check(count in 0..256) { "任务检查点不可用。" }
            val recoveryAction = if (version >= 2) input.readUTF().ifEmpty { null } else null
            val files = List(count) {
                CompletedTaskFile(input.readUTF(), input.readUTF(), input.readUTF(), input.readLong())
            }
            val pending = if (version >= 3 && input.readBoolean())
                PendingFilePublication(input.readUTF(), input.readUTF(), input.readLong()) else null
            check(input.available() == 0) { "任务检查点不可用。" }
            TaskCheckpoint(storedId, phase, reason, downloaded, total, timestamp, files, recoveryAction, pending)
        }
        validate(taskId, task, checkpoint)
        checkpoint
    }

    @Synchronized
    fun saveExportedDocuments(taskId: String, uris: List<String>): Result<Unit> = runCatching {
        require(uris.size in 1..257)
        uris.forEach { require(it.length <= 16_384 && java.net.URI(it).scheme == "content") }
        val task = directory(taskId)
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(taskId)
                output.writeInt(uris.size)
                uris.forEach(output::writeUTF)
            }
        }.toByteArray()
        check(bytes.size <= MaxSnapshotBytes)
        writer.write(guard.child(task, "exported.bin"), bytes, guard)
    }

    @Synchronized
    fun loadExportedDocuments(taskId: String): Result<List<String>?> = runCatching {
        val source = guard.child(directory(taskId), "exported.bin")
        if (!source.exists()) return@runCatching null
        check(source.isFile && source.length() in 1..MaxSnapshotBytes.toLong())
        DataInputStream(ByteArrayInputStream(source.readBytes())).use { input ->
            check(input.readUTF() == taskId)
            val count = input.readInt()
            check(count in 1..257)
            List(count) { input.readUTF().also { check(java.net.URI(it).scheme == "content") } }.also { check(input.available() == 0) }
        }
    }

    @Synchronized
    fun deleteTask(taskId: String): Result<Unit> = runCatching {
        requireTaskId(taskId)
        guard.checkSafe(guard.root)
        if (!guard.root.exists()) return@runCatching
        val task = guard.child(guard.directory(false), "task-$taskId")
        if (!task.exists()) return@runCatching
        check(task.isDirectory) { "任务目录不可用。" }
        val entries = mutableListOf<File>()
        fun inspect(entry: File) {
            guard.checkSafe(entry)
            if (entry.isDirectory) {
                val children = checkNotNull(entry.listFiles()) { "任务目录不可读取。" }
                children.forEach(::inspect)
            } else check(entry.isFile) { "任务文件不可用。" }
            entries += entry
        }
        // Validate the entire tree before any mutation; links conservatively block cleanup.
        inspect(task)
        for (entry in entries) {
            guard.checkSafe(entry)
            check(entry.delete()) { "任务文件待清理。" }
        }
    }

    private fun validate(taskId: String, task: File, checkpoint: TaskCheckpoint) {
        require(checkpoint.taskId == taskId && checkpoint.phase.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")) &&
            (checkpoint.stopReason == null || checkpoint.stopReason.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")))) {
            "任务检查点不可用。"
        }
        require(checkpoint.fileRecoveryAction == null || FileRecoveryAction.entries.any { it.name == checkpoint.fileRecoveryAction })
        require(checkpoint.downloadedBytes >= 0 && (checkpoint.totalBytes == null || checkpoint.totalBytes >= 0)) {
            "任务进度不可用。"
        }
        require(checkpoint.checkpointAt >= 0 && checkpoint.completedFiles.size <= 256) { "任务检查点不可用。" }
        require(checkpoint.completedFiles.map { it.relativePath }.distinct().size == checkpoint.completedFiles.size) {
            "任务检查点不可用。"
        }
        checkpoint.pendingFilePublication?.let { pending ->
            require(checkpoint.completedFiles.isEmpty() && pending.identity.matches(Regex("[0-9a-f]{64}")) && pending.sizeBytes >= 0)
            val parts = pending.relativePath.split('/')
            require(parts.size == 2 && parts.first() == "outputs" && parts.last().length in 1..255)
            val name = parts.last()
            require(name !in setOf(".", "..") && name.none { it == '\\' || it == ':' || it.isISOControl() })
            val target = guard.child(guard.child(task, "outputs"), name)
            check(!target.exists() || target.isFile)
        }
        checkpoint.completedFiles.forEach { completed ->
            require(completed.role.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,63}")) &&
                completed.formatId.matches(Regex("\\S{1,4096}")) && !completed.formatId.contains("://") &&
                completed.relativePath.length <= 4096) {
                "任务格式不可用。"
            }
            val parts = completed.relativePath.split('/')
            require(parts.all { it.isNotEmpty() && it != "." && it != ".." &&
                !it.contains('\\') && !it.contains(':') && !it.contains('\u0000') }) { "任务文件路径不可用。" }
            var file = task
            parts.forEach { file = guard.child(file, it) }
            check(file.isFile && (completed.sizeBytes > 0 || completed.role == "file" && completed.sizeBytes == 0L) && file.length() == completed.sizeBytes) { "任务文件校验失败。" }
        }
    }

    companion object {
        fun fromContext(context: Context) = TaskWorkspace(File(context.filesDir, "gui-downloads"))
        private const val CheckpointMagic = 0x59544350
        private const val MaxSnapshotBytes = 1024 * 1024
    }
}
