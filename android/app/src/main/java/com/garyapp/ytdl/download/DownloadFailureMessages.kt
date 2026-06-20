package com.garyapp.ytdl.download

import com.garyapp.ytdl.core.privacy.SensitiveText
import com.garyapp.ytdl.core.ytdlp.AnalysisErrorCategory
import com.garyapp.ytdl.core.ytdlp.YtdlpDownloadException

object DownloadFailureMessages {
    fun network(detail: String? = null): String {
        return "网络连接失败，请检查网络后重试。"
    }

    fun missingSubtitle(language: String? = null, detail: String? = null): String {
        val safeLanguage = language?.takeIf { it.matches(Regex("""^[A-Za-z0-9._-]{1,24}$""")) }
        return if (safeLanguage.isNullOrBlank()) {
            "所选字幕不可用，请取消字幕或重新分析后再试。"
        } else {
            "所选字幕 $safeLanguage 不可用，请取消字幕或重新分析后再试。"
        }
    }

    fun processing(detail: String? = null): String {
        return "文件处理失败，请重试或选择其他格式。"
    }

    fun saveExportDenied(detail: String? = null): String {
        return "未获得保存位置授权，导出已取消。请重新选择保存位置。"
    }

    fun canceled(): String {
        return "下载已取消，可重新开始。"
    }

    fun unmanagedCookiesReference(): String {
        return "cookies 文件需要先复制到 App 私有临时文件后才能用于下载，请重新选择 cookies 文件。"
    }

    fun cookiesCleanupFailed(): String {
        return "cookies 临时文件清理失败，请重试或重新选择 cookies 文件。"
    }

    fun historyWriteFailed(): String {
        return "历史记录写入失败，下载文件可能已保存，请在输出目录中查看或重试。"
    }

    fun fromException(exc: Exception): String {
        if (exc is YtdlpDownloadException) {
            return when (exc.category) {
                AnalysisErrorCategory.Network -> network(exc.safeMessage)
                AnalysisErrorCategory.Unsupported -> "当前地址或格式暂不支持，请检查地址格式或重新选择格式。"
                AnalysisErrorCategory.Permission -> "需要授权或 cookies 文件，请确认你有权访问该内容后重试。"
                AnalysisErrorCategory.Parser -> processing(exc.safeMessage)
                AnalysisErrorCategory.Canceled -> canceled()
                AnalysisErrorCategory.Unknown -> fromErrorText(exc.safeMessage)
            }
        }
        return fromErrorText(exc.message.orEmpty())
    }

    fun fromErrorText(message: String?): String {
        val safe = SensitiveText.redact(message.orEmpty())
        return when {
            safe.contains("网络", ignoreCase = true) ||
                safe.contains("network", ignoreCase = true) ||
                safe.contains("timeout", ignoreCase = true) -> network(safe)
            safe.contains("字幕", ignoreCase = true) ||
                safe.contains("subtitle", ignoreCase = true) -> missingSubtitle(detail = safe)
            safe.contains("导出", ignoreCase = true) ||
                safe.contains("保存", ignoreCase = true) ||
                safe.contains("permission", ignoreCase = true) -> saveExportDenied(safe)
            safe.contains("取消", ignoreCase = true) ||
                safe.contains("cancel", ignoreCase = true) -> canceled()
            else -> processing(safe)
        }
    }
}
