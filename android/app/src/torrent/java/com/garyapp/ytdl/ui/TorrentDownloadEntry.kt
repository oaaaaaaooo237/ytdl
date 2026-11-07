package com.garyapp.ytdl.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import com.garyapp.ytdl.download.TorrentTransferService
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.garyapp.ytdl.torrent.TorrentEngine
import com.garyapp.ytdl.torrent.isTorrentMagnet
import com.garyapp.ytdl.storage.ExportController
import com.frostwire.jlibtorrent.TorrentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// The transfer state outlives the Activity; picker and dialog state remain local.
private data class QueuedTorrent(val magnet: String?, val read: (TorrentEngine, File) -> ByteArray)

private object TorrentTransferState {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)
    val engine = mutableStateOf<TorrentEngine?>(null)
    val active = mutableStateOf<TorrentInfo?>(null)
    val workspace = mutableStateOf<File?>(null)
    val busy = mutableStateOf(false)
    val paused = mutableStateOf(false)
    val message = mutableStateOf("")
    val completedFiles = mutableStateOf<List<ExportController.AppPrivateOutput>>(emptyList())
    val recoverableId = mutableStateOf<String?>(null)
    val waiting = mutableStateListOf<QueuedTorrent>()
    const val MaxWaiting = 5
}

@Composable
internal fun rememberTorrentDownloadAction(): ((String) -> Unit)? {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val outputRoot = remember(context) { File(context.filesDir, "torrents") }
    val exports = remember(context) { context.getSharedPreferences("torrent-exports", android.content.Context.MODE_PRIVATE) }
    var engine by TorrentTransferState.engine
    var active by TorrentTransferState.active
    var activeWorkspace by TorrentTransferState.workspace
    var recoverableId by TorrentTransferState.recoverableId
    var completedFiles by TorrentTransferState.completedFiles
    var open by remember { mutableStateOf(false) }
    var busy by TorrentTransferState.busy
    var paused by TorrentTransferState.paused
    var magnet by remember { mutableStateOf("") }
    var message by TorrentTransferState.message
    val failure = appText("无法启动种子下载。", "Could not start torrent download.")
    val completed = appText("下载完成", "Download complete")
    val started = appText("正在连接节点…", "Connecting to peers…")

    fun stopTransferService() {
        TorrentTransferService.onTransferTimeout = null
        context.stopService(Intent(context, TorrentTransferService::class.java))
    }

    suspend fun observeTransfer(info: TorrentInfo, appendToBatch: Boolean, startNext: () -> Unit) {
        while (active === info) {
            val status = withContext(Dispatchers.IO) { runCatching { engine?.progress(info) } }
            if (active !== info) break
            if (status.isFailure) {
                message = failure
                val failedEngine = engine
                active = null
                engine = null
                withContext(Dispatchers.IO) { failedEngine?.close() }
                stopTransferService()
                break
            }
            status.getOrNull()?.let { progress ->
                message = "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}" +
                    if (paused) "" else " · ${formatBytes(progress.bytesPerSecond.toLong())}/s"
                if (progress.finished) {
                    val directory = activeWorkspace ?: break
                    val previous = if (appendToBatch) completedFiles else emptyList()
                    val files = withContext(Dispatchers.IO) {
                        try {
                            val storage = info.files()
                            val paths = org.json.JSONArray(if (appendToBatch) exports.getString("latest_outputs", "[]") else "[]")
                            val outputs = (0 until storage.numFiles()).filterNot(storage::padFileAt).map { index ->
                                val output = File(directory, storage.filePath(index))
                                check(output.isFile && output.length() == storage.fileSize(index))
                                ExportController.discoverAppPrivateOutput(
                                    output, outputRoot, allowEmptyFile = true,
                                ).getOrThrow().also {
                                    paths.put(output.canonicalFile.relativeTo(outputRoot.canonicalFile).path)
                                }
                            }
                            check(outputs.isNotEmpty())
                            check(exports.edit().putString("latest_outputs", paths.toString()).remove("pending_id").commit())
                            previous + outputs
                        } catch (_: Exception) {
                            emptyList()
                        } finally {
                            engine?.let { session ->
                                session.remove(info)
                                session.close()
                            }
                        }
                    }
                    if (files.isNotEmpty()) {
                        completedFiles = files
                        recoverableId = null
                    }
                    engine = null
                    active = null
                    paused = false
                    message = if (files.isEmpty()) failure else completed
                    if (files.isNotEmpty() && TorrentTransferState.waiting.isNotEmpty()) startNext()
                    else stopTransferService()
                    break
                }
            }
            delay(500)
        }
    }

    fun submit(
        recoverId: String? = null,
        serviceAlreadyRunning: Boolean = false,
        magnetLink: String? = null,
        read: (TorrentEngine, File) -> ByteArray,
    ) {
        if (recoverId == null && (busy || active != null)) {
            if (TorrentTransferState.waiting.size < TorrentTransferState.MaxWaiting) {
                TorrentTransferState.waiting.add(QueuedTorrent(magnetLink, read))
            }
            return
        }
        if (busy || active != null || (recoverId == null && recoverableId != null)) return
        val service = Intent(context, TorrentTransferService::class.java)
        if (!serviceAlreadyRunning && runCatching { ContextCompat.startForegroundService(context, service) }.isFailure) {
            message = failure
            return
        }
        TorrentTransferService.onTransferTimeout = {
            val runningEngine = engine
            active = null
            engine = null
            paused = false
            message = failure
            TorrentTransferService.onTransferTimeout = null
            TorrentTransferState.scope.launch(Dispatchers.IO) { runningEngine?.close() }
        }
        busy = true
        message = started
        TorrentTransferState.scope.launch {
            try {
                val runningEngine = engine ?: withContext(Dispatchers.IO) { TorrentEngine() }.also { engine = it }
                val (info, directory) = withContext(Dispatchers.IO) {
                    val id = recoverId ?: UUID.randomUUID().toString()
                    require(UUID.fromString(id).toString() == id)
                    val job = File(outputRoot.canonicalFile, id)
                    require(job.absoluteFile == job.canonicalFile)
                    val workspace = File(job, "payload")
                    require(workspace.absoluteFile == workspace.canonicalFile)
                    val metadataFile = File(job, "source.torrent")
                    val metadata = if (recoverId == null) {
                        check(workspace.mkdirs())
                        read(runningEngine, workspace).also {
                            require(it.size in 1..TorrentEngine.MaxMetadataBytes)
                        }
                    } else {
                        require(metadataFile.isFile && metadataFile.absoluteFile == metadataFile.canonicalFile)
                        require(metadataFile.length() in 1..TorrentEngine.MaxMetadataBytes.toLong())
                        metadataFile.readBytes()
                    }
                    val magnetFile = File(job, "magnet.txt")
                    val savedMagnet = if (recoverId != null && magnetFile.isFile &&
                        magnetFile.absoluteFile == magnetFile.canonicalFile && magnetFile.length() <= 16_384
                    ) magnetFile.readText().takeIf(::isTorrentMagnet) else null
                    val info = runningEngine.download(metadata, workspace, magnetLink ?: savedMagnet) {
                        if (recoverId == null && magnetLink != null) magnetFile.writeText(magnetLink)
                        if (recoverId == null) metadataFile.writeBytes(metadata)
                        check(exports.edit().putString("pending_id", id).commit())
                    }
                    info to workspace
                }
                activeWorkspace = directory
                recoverableId = directory.parentFile?.name
                active = info
                TorrentTransferState.scope.launch {
                    observeTransfer(info, appendToBatch = serviceAlreadyRunning) {
                        val next = TorrentTransferState.waiting.removeAt(0)
                        submit(serviceAlreadyRunning = true, magnetLink = next.magnet, read = next.read)
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                recoverableId = exports.getString("pending_id", null)
                message = failure
            } catch (_: LinkageError) {
                message = failure
            } finally {
                if (active == null) {
                    val idleEngine = engine
                    engine = null
                    stopTransferService()
                    withContext(Dispatchers.IO) { runCatching { idleEngine?.close() } }
                }
                busy = false
            }
        }
    }

    fun control(cancel: Boolean = false) {
        val info = active ?: return
        val runningEngine = engine ?: return
        if (busy) return
        busy = true
        TorrentTransferState.scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when {
                        cancel -> {
                            runningEngine.remove(info)
                            runningEngine.close()
                            check(exports.edit().remove("pending_id").commit())
                        }
                        paused -> runningEngine.resume(info)
                        else -> runningEngine.pause(info)
                    }
                }
                if (cancel) {
                    engine = null
                    active = null
                    recoverableId = null
                    paused = false
                    message = ""
                } else {
                    paused = !paused
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                message = failure
            } finally {
                busy = false
                if (active == null) stopTransferService()
            }
        }
    }

    val canAddTorrent = TorrentTransferState.waiting.size < TorrentTransferState.MaxWaiting &&
        (recoverableId == null || active != null)
    var exporting by remember { mutableStateOf(false) }
    val exported = appText("已保存到所选目录", "Saved to the selected folder")
    val exportFailed = appText("导出失败，文件仍保存在应用内。", "Export failed. Files are still in the app.")
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !exporting && completedFiles.isNotEmpty()) {
            val files = completedFiles
            exporting = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ExportController.copyToSafTreeWithDocuments(context.contentResolver, uri.toString(), files)
                    }
                    message = exported
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    message = exportFailed
                } finally {
                    exporting = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (recoverableId == null && active == null) recoverableId = exports.getString("pending_id", null)
        val savedOutputs = withContext(Dispatchers.IO) {
            runCatching {
                val paths = org.json.JSONArray(exports.getString("latest_outputs", "[]"))
                List(paths.length()) { index ->
                    ExportController.discoverAppPrivateOutput(
                        File(outputRoot, paths.getString(index)), outputRoot, allowEmptyFile = true,
                    ).getOrNull()
                }.filterNotNull()
            }.getOrDefault(emptyList())
        }
        if (completedFiles.isEmpty()) completedFiles = savedOutputs
    }


    val localTorrent = android.net.Uri.parse(magnet.trim()).takeIf { it.scheme == "content" }
    val torrentUrl = com.garyapp.ytdl.core.policy.UrlPolicy.evaluate(magnet.trim()).rawUrlForExecution
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(appText("种子下载", "Torrent download")) },
            text = {
                Column {
                    Text(
                        text = localTorrent?.lastPathSegment?.substringAfterLast('/') ?: magnet,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (message.isNotEmpty()) Text(message)
                    val waiting = TorrentTransferState.waiting.size
                    if (waiting > 0) {
                        Text(appText("$waiting 条种子等待中", "$waiting torrent tasks queued"))
                        if (!busy && active == null && recoverableId == null) {
                            TextButton(onClick = { TorrentTransferState.waiting.removeAt(0).let { submit(magnetLink = it.magnet, read = it.read) } }) {
                                Text(appText("继续排队任务", "Continue queued tasks"))
                            }
                        }
                    }
                    if (completedFiles.isNotEmpty()) {
                        TextButton(enabled = !exporting, onClick = { exportPicker.launch(null) }) {
                            Text(appText("导出已下载文件", "Export downloaded files"))
                        }
                    }
                    val pendingId = recoverableId
                    if (active == null && pendingId != null) {
                        TextButton(enabled = !busy, onClick = { submit(pendingId) { _, _ -> error("Missing torrent metadata") } }) {
                            Text(appText("恢复上次传输", "Resume previous transfer"))
                        }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val removed = withContext(Dispatchers.IO) {
                                        exports.edit().remove("pending_id").commit()
                                    }
                                    if (removed) {
                                        recoverableId = null
                                        message = ""
                                    } else message = failure
                                } finally {
                                    busy = false
                                }
                            }
                        }) {
                            Text(appText("放弃恢复", "Discard recovery"))
                        }
                    }
                    if (active != null) {
                        TextButton(enabled = !busy, onClick = { control() }) {
                            Text(if (paused) appText("继续", "Resume") else appText("暂停", "Pause"))
                        }
                        TextButton(enabled = !busy, onClick = { control(cancel = true) }) {
                            Text(appText("取消传输", "Cancel transfer"))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = canAddTorrent && (isTorrentMagnet(magnet.trim()) || torrentUrl != null || localTorrent != null), onClick = {
                    val link = magnet.trim()
                    if (localTorrent != null) {
                        submit { _, _ ->
                            requireNotNull(context.contentResolver.openInputStream(localTorrent)).use(::readTorrentMetadata)
                        }
                    } else if (torrentUrl == null) {
                        submit(magnetLink = link) { torrent, workspace -> torrent.loadMagnet(link, workspace) }
                    } else {
                        submit { _, _ ->
                            val connection = java.net.URL(torrentUrl).openConnection() as java.net.HttpURLConnection
                            try {
                                connection.instanceFollowRedirects = false
                                connection.connectTimeout = 15_000
                                connection.readTimeout = 15_000
                                require(connection.responseCode == 200)
                                connection.inputStream.use {
                                    readTorrentMetadata(it).also { bytes ->
                                        require(bytes.size in 1..TorrentEngine.MaxMetadataBytes)
                                    }
                                }
                            } finally {
                                connection.disconnect()
                            }
                        }
                    }
                }) { Text(appText("开始下载", "Start download")) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(appText("关闭", "Close")) }
            },
        )
    }
    return { input -> magnet = input; open = true }
}

private fun readTorrentMetadata(input: java.io.InputStream): ByteArray {
    val bytes = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(bytes.size() + count <= TorrentEngine.MaxMetadataBytes)
        bytes.write(buffer, 0, count)
    }
    return bytes.toByteArray()
}
