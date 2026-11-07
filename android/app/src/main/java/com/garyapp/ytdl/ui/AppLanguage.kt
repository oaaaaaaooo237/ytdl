package com.garyapp.ytdl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.garyapp.ytdl.core.settings.AppLanguage

internal val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Chinese }

@Composable
internal fun appText(chinese: String, english: String): String =
    LocalAppLanguage.current.pick(chinese, english)

@Composable
internal fun appMessage(message: String): String =
    if (LocalAppLanguage.current == AppLanguage.English) englishMessages[message] ?: message else message

private val englishMessages = mapOf(
    "预约时间已过，请重新选择。" to "The scheduled time has passed. Choose a new time.",
    "请选择未来的时间。" to "Choose a time in the future.",
    "下载已取消，可重新开始。" to "Download canceled. You can download it again.",
    "检查 Cookies 文件" to "Check cookies file",
    "文件可读取。可重新识别链接以验证登录。" to "The file is readable. Analyze the link again to check your sign-in.",
    "文件不可读取，请重新选择。" to "Could not read the file. Choose it again.",
    "Cookies 文件" to "Cookies file",
    "文件引用无效，请重新选择 cookies.txt。" to "The file is unavailable. Choose cookies.txt again.",
    "网站与登录" to "Sites and sign-in",
    "清理登录状态失败，请重试。" to "Could not clear the sign-in. Try again.",
    "下载音频" to "Downloading audio",
    "下载文件" to "Downloading file",
    "下载视频" to "Downloading video",
    "合并音视频" to "Merging video and audio",
    "重新下载" to "Download again",
    "下载未完成，可重新下载。" to "Download incomplete. You can download it again.",
    "下载中断，可重新下载。" to "Download interrupted. You can download it again.",
    "视频+音频" to "Video + audio",
    "仅音频" to "Audio only",
    "仅视频" to "Video only",
    "视频" to "Video",
    "音频" to "Audio",
    "普通文件" to "File",
    "媒体 · 视频直链" to "Direct video link",
    "媒体 · 音频直链" to "Direct audio link",
    "系统无需单独授权" to "No separate system permission required",
    "已允许" to "Allowed",
    "未授权 · 下载仍在应用内显示进度" to "Not allowed · Download progress is still visible in the app",
    "系统" to "System",
    "请求" to "Request",
    "已完成" to "Completed",
    "完成" to "Completed",
    "已取消" to "Canceled",
    "取消" to "Cancel",
    "失败" to "Failed",
    "已暂停" to "Paused",
    "暂停" to "Pause",
    "继续" to "Resume",
    "等待中" to "Waiting",
    "等待网络" to "Waiting for network",
    "正在暂停" to "Pausing",
    "正在恢复" to "Resuming",
    "打开" to "Open",
    "分享" to "Share",
    "导出" to "Export",
    "再次下载" to "Download again",
    "删除" to "Delete",
    "保存的文件不存在或无法读取，请检查存储权限。" to "The saved file is missing or unreadable. Check storage permissions.",
    "部分文件不存在或无法读取。" to "Some files are missing or unreadable.",
    "剪贴板中没有链接文本。" to "No link found on the clipboard.",
    "请先输入下载链接。" to "Enter a download link.",
    "请输入有效的 HTTP 或 HTTPS 下载链接。" to "Enter a valid HTTP or HTTPS download link.",
    "Cookies 文件读取失败，请重新选择文件。" to "Could not read the cookies file. Choose the file again.",
    "原地址已重新识别，请确认下载格式。" to "The original link has been analyzed again. Confirm your download format.",
    "无法启动下载，请检查系统权限后重试。" to "Could not start the download. Check Android permissions and try again.",
    "无法启动下载，请重试。" to "Could not start the download. Try again.",
    "原下载信息读取失败，任务记录已保留。" to "Could not read the original download details. The task record is still available.",
    "无法打开系统文件选择器。" to "Could not open the Android file picker.",
    "无法继续下载，任务已保留，请检查链接或文件名后重试。" to "Could not resume. Check the link or file name and try again.",
    "已请求取消所选任务。" to "Cancellation requested.",
    "继续下载失败，任务已保留，请重试。" to "Could not resume the download. The task is saved. Try again.",
    "取消尚未完成，任务已保留，请重试。" to "Cancellation did not complete. The task is saved. Try again.",
    "网络连接失败，请检查网络后重试。" to "Could not connect. Check your network and try again.",
    "文件处理失败，请重试或选择其他格式。" to "Could not process the file. Try again or choose a different format.",
    "设备存储空间不足，请清理空间后重试。" to "Free up storage on your device and try again.",
    "字幕下载请求过多，请稍后重试。" to "Too many subtitle requests. Try again later.",
    "已导出文件。" to "File exported.",
)

@Composable
internal fun appFormatText(text: String): String =
    if (LocalAppLanguage.current != AppLanguage.English) text
    else formatPhrases.entries.fold(text) { result, (chinese, english) -> result.replace(chinese, english) }

private val formatPhrases = mapOf(
    "原始格式" to "Original format",
    "自动（推荐）" to "Auto (recommended)",
    "需原生合并" to "Requires merging",
    "单文件" to "Single file",
    "请选择可用格式" to "Choose an available format",
    "分析后显示可用格式" to "Analyze the link to see formats",
    "音频模式不适用" to "Unavailable in audio mode",
    "MP4（原生合并输出）" to "MP4 (merged)",
    "音频容器未提供" to "Audio container unavailable",
    "分析后显示" to "Shown after analysis",
    "不适用" to "Not applicable",
    "未提供" to "Unavailable",
    "音频" to "Audio",
    "未知" to "Unknown",
    "其他" to "Other",
)

@Composable
internal fun appParserStatus(status: String): String = appText(
    status,
    status.replace("本进程：", "Running: ")
        .replace("；下次启动：", " · Next launch: ")
        .replace("正在检查解析器更新...", "Checking for parser updates…")
        .replace("已是最新版本：", "Up to date: ")
        .replace("已是最新版本", "Up to date")
        .replace("发现新版本：", "Update available: ")
        .replace("发现新版本 ", "Update available: ")
        .replace("检查失败，请稍后重试", "Could not check for updates. Try again later.")
        .replace("尚未检查", "Not checked yet"),
)
