package com.garyapp.ytdl.core.ytdlp

import com.chaquo.python.Python
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.policy.UrlPolicyBlockReason
import org.json.JSONObject

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
        } catch (exc: Throwable) {
            Result.failure(exc.toSafeAnalysisException())
        }
    }

    companion object {
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

        fun mapSubtitles(raw: Map<String, List<Map<String, Any?>>>): List<SubtitleInfo> {
            return raw.flatMap { (language, entries) ->
                entries.mapNotNull { item ->
                    val ext = item["ext"]?.toString()?.takeIf { it.isNotBlank() }
                    ext?.let { SubtitleInfo(language = language, ext = it) }
                }
            }
        }

        fun mapErrorCategory(raw: String?): AnalysisErrorCategory {
            return when (raw?.lowercase()) {
                "network" -> AnalysisErrorCategory.Network
                "unsupported" -> AnalysisErrorCategory.Unsupported
                "permission" -> AnalysisErrorCategory.Permission
                "parser" -> AnalysisErrorCategory.Parser
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

            val subtitlesObject = root.optJSONObject("subtitles")
            val subtitlesRaw = mutableMapOf<String, List<Map<String, Any?>>>()
            subtitlesObject?.keys()?.forEach { language ->
                val entries = subtitlesObject.optJSONArray(language)
                subtitlesRaw[language] = buildList {
                    for (index in 0 until (entries?.length() ?: 0)) {
                        val item = entries?.optJSONObject(index) ?: continue
                        add(item.toMap())
                    }
                }
            }

            return Result.success(
                VideoAnalysis(
                    title = root.optString("title"),
                    durationSeconds = root.optLong("duration"),
                    thumbnailUrl = root.optString("thumbnail").takeIf { it.isNotBlank() },
                    formats = rawFormats.map { mapFormat(it, hasStandaloneAudio) },
                    subtitles = mapSubtitles(subtitlesRaw),
                ),
            )
        }

        private fun JSONObject.toMap(): Map<String, Any?> {
            return keys().asSequence().associateWith { key -> opt(key) }
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
            val redacted = message
                .replace(Regex("""https?://\S+"""), "[已隐藏]")
                .replace(Regex("""(?i)--cookies\s+\S+"""), "--cookies [已隐藏]")
                .replace(Regex("""(?i)authorization:\s*[^\s]+(?:\s+[^\s]+)?"""), "Authorization: [已隐藏]")
                .replace(Regex("""(?i)cookie:\s*[^\r\n]+"""), "Cookie: [已隐藏]")
                .replace(Regex("""(?i)(token|access_token|auth|signature|sig|key)=([^&\s]+)"""), "$1=[已隐藏]")
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
                UrlPolicyBlockReason.AdultDomain,
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
    }
}

class YtdlpAnalysisException(
    val category: AnalysisErrorCategory,
    val safeMessage: String,
) : RuntimeException(safeMessage)
