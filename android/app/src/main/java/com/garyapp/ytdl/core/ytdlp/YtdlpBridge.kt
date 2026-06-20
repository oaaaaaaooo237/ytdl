package com.garyapp.ytdl.core.ytdlp

import com.chaquo.python.Python
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.policy.UrlPolicyBlockReason
import com.garyapp.ytdl.core.privacy.SensitiveText
import org.json.JSONObject
import java.io.File

class YtdlpBridge(
    private val pythonProvider: () -> Python = { Python.getInstance() },
) {
    fun analyze(url: String, cookiesPath: String? = null): Result<VideoAnalysis> {
        val policy = UrlPolicy.evaluate(url)
        if (!policy.isAllowed) {
            return Result.failure(
                YtdlpAnalysisException(
                    category = policy.blockReason.toAnalysisCategory(),
                    safeMessage = sanitizeFailureMessage(policy.userMessage.orEmpty()),
                ),
            )
        }

        return try {
            val module = pythonProvider().getModule("ytdl_bridge")
            val json = module.callAttr("analyze", policy.rawUrlForExecution, cookiesPath).toString()
            parseAnalysisJson(json)
        } catch (exc: Exception) {
            Result.failure(exc.toSafeAnalysisException())
        }
    }

    fun downloadSingleFile(
        url: String,
        outputDirectory: File,
        cookiesPath: String? = null,
        listener: DownloadProgressListener? = null,
    ): Result<DownloadResult> {
        val policy = UrlPolicy.evaluate(url)
        if (!policy.isAllowed) {
            return Result.failure(
                YtdlpDownloadException(
                    category = policy.blockReason.toAnalysisCategory(),
                    safeMessage = sanitizeFailureMessage(policy.userMessage.orEmpty()),
                ),
            )
        }

        return try {
            outputDirectory.mkdirs()
            val module = pythonProvider().getModule("ytdl_bridge")
            val json = module.callAttr(
                "download_single_file",
                policy.rawUrlForExecution,
                outputDirectory.absolutePath,
                cookiesPath,
                PythonProgressProxy(listener),
            ).toString()
            parseDownloadJson(json)
        } catch (exc: Exception) {
            Result.failure(exc.toSafeDownloadException())
        }
    }

    fun downloadFormat(
        url: String,
        outputDirectory: File,
        formatId: String,
        role: DownloadFormatRole,
        cookiesPath: String? = null,
        listener: DownloadProgressListener? = null,
    ): Result<DownloadResult> {
        val normalizedFormatId = formatId.trim()
        if (normalizedFormatId.isBlank() || !isExplicitFormatId(normalizedFormatId)) {
            return Result.failure(
                YtdlpDownloadException(
                    category = AnalysisErrorCategory.Unsupported,
                    safeMessage = "format id 必须是单个明确格式编号。",
                ),
            )
        }

        val policy = UrlPolicy.evaluate(url)
        if (!policy.isAllowed) {
            return Result.failure(
                YtdlpDownloadException(
                    category = policy.blockReason.toAnalysisCategory(),
                    safeMessage = sanitizeFailureMessage(policy.userMessage.orEmpty()),
                ),
            )
        }

        return try {
            outputDirectory.mkdirs()
            val module = pythonProvider().getModule("ytdl_bridge")
            val json = module.callAttr(
                "download_format",
                policy.rawUrlForExecution,
                outputDirectory.absolutePath,
                normalizedFormatId,
                role.pythonValue,
                cookiesPath,
                PythonProgressProxy(listener),
            ).toString()
            parseDownloadJson(json)
        } catch (exc: Exception) {
            Result.failure(exc.toSafeDownloadException())
        }
    }

    fun downloadSubtitle(
        url: String,
        outputDirectory: File,
        language: String,
        ext: String,
        source: SubtitleSource,
        cookiesPath: String? = null,
        listener: DownloadProgressListener? = null,
    ): Result<SubtitleDownloadResult> {
        val normalizedLanguage = language.trim()
        val normalizedExt = ext.trim()
        if (!isExplicitSubtitleToken(normalizedLanguage) || !isExplicitSubtitleToken(normalizedExt)) {
            return Result.failure(
                YtdlpDownloadException(
                    category = AnalysisErrorCategory.Unsupported,
                    safeMessage = "字幕语言和扩展名必须是分析结果中的明确值。",
                ),
            )
        }

        val policy = UrlPolicy.evaluate(url)
        if (!policy.isAllowed) {
            return Result.failure(
                YtdlpDownloadException(
                    category = policy.blockReason.toAnalysisCategory(),
                    safeMessage = sanitizeFailureMessage(policy.userMessage.orEmpty()),
                ),
            )
        }

        return try {
            outputDirectory.mkdirs()
            val module = pythonProvider().getModule("ytdl_bridge")
            val json = module.callAttr(
                "download_subtitle",
                policy.rawUrlForExecution,
                outputDirectory.absolutePath,
                normalizedLanguage,
                normalizedExt,
                source.pythonValue,
                cookiesPath,
                PythonProgressProxy(listener),
            ).toString()
            parseSubtitleDownloadJson(json)
        } catch (exc: Exception) {
            Result.failure(exc.toSafeDownloadException())
        }
    }

    companion object {
        const val PINNED_YTDLP_VERSION = "2026.3.17"

        fun mapFormat(
            raw: Map<String, Any?>,
            hasStandaloneAudio: Boolean = false,
        ): VideoFormat {
            val id = raw["format_id"]?.toString().orEmpty()
            val ext = raw["ext"]?.toString().orEmpty()
            val height = raw["height"].asIntOrNull()
            val vcodec = raw["vcodec"]?.toString().orEmpty().ifBlank { "none" }
            val acodec = raw["acodec"]?.toString().orEmpty().ifBlank { "none" }
            val hasVideo = vcodec != "none"
            val hasAudio = acodec != "none"
            val isSupported = height != null && hasVideo
            val mergeRequired = isSupported && hasVideo && !hasAudio && hasStandaloneAudio
            val label = when {
                !isSupported -> "不支持"
                mergeRequired -> "${height}p 需合并音频"
                else -> "${height}p"
            }

            return VideoFormat(
                id = id,
                ext = ext,
                height = height,
                label = label,
                hasVideo = hasVideo,
                hasAudio = hasAudio,
                mergeRequired = mergeRequired,
                isSupported = isSupported,
                filesizeBytes = raw["filesize"].asLongOrNull(),
                fps = raw["fps"].asDoubleOrNull(),
                videoCodec = vcodec,
                audioCodec = acodec,
            )
        }

        fun mapSubtitles(
            raw: Map<String, List<Map<String, Any?>>>,
            source: SubtitleSource = SubtitleSource.Manual,
        ): List<SubtitleInfo> {
            return raw.flatMap { (language, entries) ->
                entries.mapNotNull { item ->
                    val ext = item["ext"]?.toString()?.takeIf { it.isNotBlank() }
                    ext?.let { SubtitleInfo(language = language, ext = it, source = source) }
                }
            }
        }

        fun mapErrorCategory(raw: String?): AnalysisErrorCategory {
            return when (raw?.lowercase()) {
                "network" -> AnalysisErrorCategory.Network
                "unsupported" -> AnalysisErrorCategory.Unsupported
                "permission" -> AnalysisErrorCategory.Permission
                "parser" -> AnalysisErrorCategory.Parser
                "canceled" -> AnalysisErrorCategory.Canceled
                else -> AnalysisErrorCategory.Unknown
            }
        }

        fun parseAnalysisJson(json: String): Result<VideoAnalysis> {
            val root = JSONObject(json)
            if (!root.optBoolean("ok", false)) {
                return Result.failure(
                    YtdlpAnalysisException(
                        category = mapErrorCategory(root.optString("errorCategory")),
                        safeMessage = sanitizeFailureMessage(root.optString("errorMessage")),
                    ),
                )
            }

            val formatsJson = root.optJSONArray("formats")
            val rawFormats = buildList {
                for (index in 0 until (formatsJson?.length() ?: 0)) {
                    val item = formatsJson?.optJSONObject(index) ?: continue
                    add(item.toMap())
                }
            }
            val hasStandaloneAudio = rawFormats.any {
                it["vcodec"]?.toString() == "none" && it["acodec"]?.toString() != "none"
            }

            val subtitlesRaw = root.optJSONObject("subtitles").toSubtitleMap()
            val automaticCaptionsRaw = root.optJSONObject("automatic_captions").toSubtitleMap()

            return Result.success(
                VideoAnalysis(
                    title = root.optString("title"),
                    durationSeconds = root.optLong("duration"),
                    thumbnailUrl = root.optString("thumbnail").takeIf { it.isNotBlank() },
                    formats = rawFormats.map { mapFormat(it, hasStandaloneAudio) },
                    subtitles = mapSubtitles(subtitlesRaw, SubtitleSource.Manual) +
                        mapSubtitles(automaticCaptionsRaw, SubtitleSource.Automatic),
                ),
            )
        }

        fun parseDownloadJson(json: String): Result<DownloadResult> {
            val root = JSONObject(json)
            if (!root.optBoolean("ok", false)) {
                return Result.failure(
                    YtdlpDownloadException(
                        category = mapErrorCategory(root.optString("errorCategory")),
                        safeMessage = sanitizeFailureMessage(root.optString("errorMessage")),
                    ),
                )
            }

            val outputPath = root.optString("outputPath")
            val bytesWritten = root.optLong("bytesWritten")
            if (outputPath.isBlank() || bytesWritten <= 0L) {
                return Result.failure(
                    YtdlpDownloadException(
                        category = AnalysisErrorCategory.Unknown,
                        safeMessage = "输出文件无效，请重新下载。",
                    ),
                )
            }

            val role = root.optString("role").takeIf { it.isNotBlank() }?.let { rawRole ->
                DownloadFormatRole.fromPythonValue(rawRole)
                    ?: return Result.failure(
                        YtdlpDownloadException(
                            category = AnalysisErrorCategory.Unsupported,
                            safeMessage = "下载结果 role 无效。",
                        ),
                    )
            }

            return Result.success(
                DownloadResult(
                    outputPath = outputPath,
                    bytesWritten = bytesWritten,
                    title = root.optString("title"),
                    formatId = root.optString("formatId").takeIf { it.isNotBlank() },
                    role = role,
                ),
            )
        }

        fun parseSubtitleDownloadJson(json: String): Result<SubtitleDownloadResult> {
            val root = JSONObject(json)
            if (!root.optBoolean("ok", false)) {
                return Result.failure(
                    YtdlpDownloadException(
                        category = mapErrorCategory(root.optString("errorCategory")),
                        safeMessage = sanitizeFailureMessage(root.optString("errorMessage")),
                    ),
                )
            }

            val outputPath = root.optString("outputPath")
            val bytesWritten = root.optLong("bytesWritten")
            if (outputPath.isBlank() || bytesWritten <= 0L) {
                return Result.failure(
                    YtdlpDownloadException(
                        category = AnalysisErrorCategory.Unknown,
                        safeMessage = "字幕文件无效，请重新下载。",
                    ),
                )
            }

            val language = root.optString("language").trim()
            val ext = root.optString("ext").trim()
            if (!isExplicitSubtitleToken(language) || !isExplicitSubtitleToken(ext)) {
                return Result.failure(
                    YtdlpDownloadException(
                        category = AnalysisErrorCategory.Unsupported,
                        safeMessage = "字幕语言和扩展名必须来自分析结果。",
                    ),
                )
            }

            val source = SubtitleSource.fromPythonValue(root.optString("source"))
                ?: return Result.failure(
                    YtdlpDownloadException(
                        category = AnalysisErrorCategory.Unsupported,
                        safeMessage = "字幕下载结果 source 无效。",
                    ),
                )

            return Result.success(
                SubtitleDownloadResult(
                    outputPath = outputPath,
                    bytesWritten = bytesWritten,
                    language = language,
                    ext = ext,
                    source = source,
                    title = root.optString("title"),
                ),
            )
        }

        private fun isExplicitFormatId(value: String): Boolean {
            val lower = value.lowercase()
            return Regex("""^[A-Za-z0-9._-]+$""").matches(value) &&
                YTDLP_SELECTOR_ALIASES.none { alias -> lower == alias || lower.startsWith("$alias.") }
        }

        private fun isExplicitSubtitleToken(value: String): Boolean {
            val lower = value.lowercase()
            return Regex("""^[A-Za-z0-9._-]+$""").matches(value) &&
                YTDLP_SELECTOR_ALIASES.none { alias -> lower == alias || lower.startsWith("$alias.") }
        }

        private val YTDLP_SELECTOR_ALIASES = setOf(
            "best",
            "worst",
            "bestvideo",
            "bestaudio",
            "worstvideo",
            "worstaudio",
            "bv",
            "ba",
            "wv",
            "wa",
            "b",
            "w",
            "all",
            "mergeall",
        )

        private fun JSONObject.toMap(): Map<String, Any?> {
            return keys().asSequence().associateWith { key -> opt(key) }
        }

        private fun JSONObject?.toSubtitleMap(): Map<String, List<Map<String, Any?>>> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { language ->
                val entries = optJSONArray(language)
                buildList {
                    for (index in 0 until (entries?.length() ?: 0)) {
                        val item = entries?.optJSONObject(index) ?: continue
                        add(item.toMap())
                    }
                }
            }
        }

        private fun Any?.asIntOrNull(): Int? {
            return when (this) {
                is Int -> this
                is Number -> toInt()
                is String -> toIntOrNull()
                else -> null
            }
        }

        private fun Any?.asLongOrNull(): Long? {
            return when (this) {
                is Long -> this
                is Number -> toLong()
                is String -> toLongOrNull()
                else -> null
            }
        }

        private fun Any?.asDoubleOrNull(): Double? {
            return when (this) {
                is Double -> this
                is Number -> toDouble()
                is String -> toDoubleOrNull()
                else -> null
            }
        }

        private fun sanitizeFailureMessage(message: String): String {
            val redacted = SensitiveText.redact(message)
                .replace(Regex("""https?://\S+"""), "[已隐藏]")
                .trim()

            return redacted
                .ifBlank { "分析失败，请检查地址或授权状态。" }
                .take(240)
        }

        private fun UrlPolicyBlockReason?.toAnalysisCategory(): AnalysisErrorCategory {
            return when (this) {
                UrlPolicyBlockReason.BlankInput,
                UrlPolicyBlockReason.UnsupportedScheme,
                UrlPolicyBlockReason.InvalidUrl,
                null,
                -> AnalysisErrorCategory.Unsupported
            }
        }

        private fun Throwable.toSafeAnalysisException(): YtdlpAnalysisException {
            if (this is YtdlpAnalysisException) {
                return this
            }
            return YtdlpAnalysisException(
                category = AnalysisErrorCategory.Parser,
                safeMessage = sanitizeFailureMessage(message.orEmpty()),
            )
        }

        private fun Throwable.toSafeDownloadException(): YtdlpDownloadException {
            if (this is YtdlpDownloadException) {
                return this
            }
            return YtdlpDownloadException(
                category = AnalysisErrorCategory.Unknown,
                safeMessage = sanitizeFailureMessage(message.orEmpty()),
            )
        }
    }

    private class PythonProgressProxy(
        private val listener: DownloadProgressListener?,
    ) {
        @Suppress("unused")
        fun onProgress(
            status: String?,
            percent: Double?,
            downloadedBytes: Long?,
            totalBytes: Long?,
            speedBytesPerSecond: Double?,
            etaSeconds: Long?,
            filename: String?,
        ) {
            listener?.onProgress(
                DownloadProgress(
                    status = status.orEmpty(),
                    percent = percent,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speedBytesPerSecond = speedBytesPerSecond,
                    etaSeconds = etaSeconds,
                    filename = filename,
                ),
            )
        }
    }
}

class YtdlpAnalysisException(
    val category: AnalysisErrorCategory,
    val safeMessage: String,
) : RuntimeException(safeMessage)

class YtdlpDownloadException(
    val category: AnalysisErrorCategory,
    val safeMessage: String,
) : RuntimeException(safeMessage)
