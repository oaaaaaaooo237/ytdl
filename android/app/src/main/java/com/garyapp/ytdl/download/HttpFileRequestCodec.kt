package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.files.*
import java.io.DataInputStream
import java.io.DataOutputStream

/** Only used inside encrypted request payloads. */
internal fun DataOutputStream.writeHttpFileRoute(route: DownloadRoute.HttpFile) {
    fun field(value: String) { require(value.length <= 16 * 1024); writeUTF(value) }
    val m = route.metadata
    field(route.fileName)
    field(m.sourceUrl); field(m.finalUrl); field(m.fileName)
    writeBoolean(m.mimeType != null); m.mimeType?.let(::field)
    writeLong(m.totalBytes ?: -1L)
    writeBoolean(m.etag != null); m.etag?.let(::field)
    writeBoolean(m.lastModified != null); m.lastModified?.let(::field)
    field(m.kind.name); field(m.resumeSupport.name); field(route.conflictPolicy.name)
}

internal fun DataInputStream.readHttpFileRoute(): DownloadRoute.HttpFile {
    val fileName = readUTF()
    val source = readUTF(); val finalUrl = readUTF(); val serverName = readUTF()
    val mime = if (readBoolean()) readUTF() else null
    val bytes = readLong().also { require(it >= -1) }.takeUnless { it == -1L }
    val etag = if (readBoolean()) readUTF() else null
    val modified = if (readBoolean()) readUTF() else null
    return DownloadRoute.HttpFile(FileMetadata(source, finalUrl, serverName, mime, bytes, etag, modified,
        FileKind.valueOf(readUTF()), ResumeSupport.valueOf(readUTF())), fileName, HttpFileConflictPolicy.valueOf(readUTF()))
}
