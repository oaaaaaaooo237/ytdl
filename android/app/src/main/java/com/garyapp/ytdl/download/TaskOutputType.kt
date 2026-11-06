package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.files.FileKind
import com.garyapp.ytdl.storage.ExportController

typealias TaskOutputType = FileKind

internal fun taskOutputTypeFromMime(mimeType: String?): TaskOutputType = when {
    mimeType.orEmpty().startsWith("video/", ignoreCase = true) -> TaskOutputType.Video
    mimeType.orEmpty().startsWith("audio/", ignoreCase = true) -> TaskOutputType.Audio
    else -> TaskOutputType.Other
}

internal fun taskOutputTypeFromStored(value: String?): TaskOutputType? =
    TaskOutputType.entries.firstOrNull { it.name == value }

internal fun DownloadRequest.outputType(): TaskOutputType = when (val selectedRoute = route) {
    is DownloadRoute.HttpFile -> selectedRoute.metadata.kind
    is DownloadRoute.AudioOnly -> TaskOutputType.Audio
    is DownloadRoute.DirectSingleFile, is DownloadRoute.VideoOnly, is DownloadRoute.MergeRequired -> TaskOutputType.Video
}

internal fun DownloadTaskState.primaryOutput(): DownloadOutputFile? =
    outputs.firstOrNull { it.kind != DownloadOutputKind.Subtitle }

internal fun DownloadOutputFile.resolvedMimeType(request: DownloadRequest): String {
    val httpMetadata = (request.route as? DownloadRoute.HttpFile)?.metadata
    safeTaskMimeType(mimeType ?: httpMetadata?.mimeType)?.let { return it }
    if (httpMetadata != null) return ExportController.mimeTypeFor(httpMetadata.fileName)
    return ExportController.mimeTypeFor(path, audioOnly = request.route is DownloadRoute.AudioOnly && kind != DownloadOutputKind.Subtitle)
}

internal fun DownloadTaskState.outputType(): TaskOutputType {
    val currentRequest = request ?: return TaskOutputType.Other
    val mime = primaryOutput()?.resolvedMimeType(currentRequest)
    return if (mime == null || mime == "application/octet-stream") currentRequest.outputType()
    else taskOutputTypeFromMime(mime)
}

internal fun safeTaskMimeType(value: String?): String? = value?.trim()?.takeIf(MimeTypePattern::matches)

private val MimeTypePattern = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")
