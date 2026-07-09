package com.garyapp.ytdl.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.garyapp.ytdl.R
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.core.settings.CookiesReference as SettingsCookiesReference
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.download.DownloadOutputKind
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.download.NotificationController
import com.garyapp.ytdl.storage.ExportController
import com.garyapp.ytdl.ui.theme.LocalYtdlAppPalette
import com.garyapp.ytdl.ui.theme.YtdlAppPalette
import com.garyapp.ytdl.ui.theme.YtdlTheme
import com.garyapp.ytdl.ui.theme.themeConfigForSettings
import com.garyapp.ytdl.ui.theme.ytdlAppPaletteForPreset
import com.garyapp.ytdl.ui.theme.ytdlColorPresets
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val DefaultPalette = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetReferenceV3)
private val HistoryFailureRed = Color(0xFFFF5B63)

private const val DefaultRuntimeMessage = "等待输入公开视频页面地址。"
private const val AnalysisCompleteRuntimeMessage = "分析完成，可以开始下载。"
private const val QueueThumbnailImageTag = "ytdl-queue-thumbnail-image"
private const val QueueThumbnailPlaceholderTag = "ytdl-queue-thumbnail-placeholder"
private const val HistoryThumbnailImageTag = "ytdl-history-thumbnail-image"
private const val HistoryThumbnailPlaceholderTag = "ytdl-history-thumbnail-placeholder"
private const val HistoryThumbnailTargetPx = 160
private const val HistoryThumbnailCacheMaxItems = 64
private const val ThumbnailDecodeMaxBytes = 2 * 1024 * 1024
private val BottomBarGestureBuffer = 32.dp
private val SettingsAppearanceBottomBuffer = 96.dp

private fun tabIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) { draw() }
    }.build()

private val DownloadTabIcon = tabIcon("DownloadTab") {
    moveTo(11f, 4f)
    lineTo(13f, 4f)
    lineTo(13f, 12.8f)
    lineTo(15.8f, 10f)
    lineTo(17.2f, 11.4f)
    lineTo(12f, 16.6f)
    lineTo(6.8f, 11.4f)
    lineTo(8.2f, 10f)
    lineTo(11f, 12.8f)
    close()
    moveTo(5f, 18f)
    lineTo(19f, 18f)
    lineTo(19f, 20f)
    lineTo(5f, 20f)
    close()
}

private val FormatTabIcon = tabIcon("FormatTab") {
    moveTo(4f, 4f)
    lineTo(10f, 4f)
    lineTo(10f, 10f)
    lineTo(4f, 10f)
    close()
    moveTo(14f, 4f)
    lineTo(20f, 4f)
    lineTo(20f, 10f)
    lineTo(14f, 10f)
    close()
    moveTo(4f, 14f)
    lineTo(10f, 14f)
    lineTo(10f, 20f)
    lineTo(4f, 20f)
    close()
    moveTo(14f, 14f)
    lineTo(20f, 14f)
    lineTo(20f, 20f)
    lineTo(14f, 20f)
    close()
}

private val QueueTabIcon = tabIcon("QueueTab") {
    moveTo(5f, 6f)
    lineTo(19f, 6f)
    lineTo(19f, 8f)
    lineTo(5f, 8f)
    close()
    moveTo(5f, 11f)
    lineTo(19f, 11f)
    lineTo(19f, 13f)
    lineTo(5f, 13f)
    close()
    moveTo(5f, 16f)
    lineTo(19f, 16f)
    lineTo(19f, 18f)
    lineTo(5f, 18f)
    close()
}

private val HistoryTabIcon = tabIcon("HistoryTab") {
    moveTo(12f, 3f)
    lineTo(18.5f, 6.5f)
    lineTo(21f, 13f)
    lineTo(17.5f, 19f)
    lineTo(11f, 21f)
    lineTo(5.5f, 17.5f)
    lineTo(3f, 11f)
    lineTo(6.5f, 5f)
    close()
    moveTo(11f, 7f)
    lineTo(13f, 7f)
    lineTo(13f, 12f)
    lineTo(16.5f, 14f)
    lineTo(15.5f, 15.8f)
    lineTo(11f, 13.2f)
    close()
}

private val SettingsTabIcon = tabIcon("SettingsTab") {
    moveTo(10.5f, 3f)
    lineTo(13.5f, 3f)
    lineTo(14.2f, 5.2f)
    lineTo(16.3f, 6.1f)
    lineTo(18.3f, 5.1f)
    lineTo(20.4f, 7.2f)
    lineTo(19.4f, 9.2f)
    lineTo(20.2f, 11.4f)
    lineTo(22f, 12.5f)
    lineTo(21.2f, 15.4f)
    lineTo(19f, 15.7f)
    lineTo(17.8f, 17.6f)
    lineTo(18.2f, 19.8f)
    lineTo(15.5f, 21.2f)
    lineTo(13.9f, 19.6f)
    lineTo(11.6f, 19.6f)
    lineTo(10f, 21.2f)
    lineTo(7.3f, 19.8f)
    lineTo(7.7f, 17.6f)
    lineTo(6.5f, 15.7f)
    lineTo(4.3f, 15.4f)
    lineTo(3.5f, 12.5f)
    lineTo(5.3f, 11.4f)
    lineTo(6.1f, 9.2f)
    lineTo(5.1f, 7.2f)
    lineTo(7.2f, 5.1f)
    lineTo(9.2f, 6.1f)
    lineTo(10.3f, 5.2f)
    close()
    moveTo(12f, 9f)
    lineTo(15f, 12f)
    lineTo(12f, 15f)
    lineTo(9f, 12f)
    close()
}

internal val YtdlColorPresetIdKey = SemanticsPropertyKey<String>("YtdlColorPresetId")
internal var SemanticsPropertyReceiver.ytdlColorPresetId by YtdlColorPresetIdKey
internal val YtdlSettingsAccentArgbKey = SemanticsPropertyKey<String>("YtdlSettingsAccentArgb")
internal var SemanticsPropertyReceiver.ytdlSettingsAccentArgb by YtdlSettingsAccentArgbKey

internal fun colorArgbHexForUiTest(color: Color): String = String.format(Locale.US, "#%08X", color.toArgb())

internal fun urlInputShowKeyboardOnFocusForUiTest(keyboard: Int): Boolean = shouldShowUrlInputKeyboardOnFocus(keyboard)

internal fun urlInputDisableAutoHandwritingForUiTest(sdkInt: Int): Boolean = shouldDisableUrlInputAutoHandwriting(sdkInt)

@Suppress("UNUSED_PARAMETER")
private fun shouldShowUrlInputKeyboardOnFocus(keyboard: Int): Boolean = true

private fun EditText.showUrlInputKeyboard() {
    requestFocus()
    post {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.showSoftInput(this, 0)
    }
}

private fun shouldDisableUrlInputAutoHandwriting(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal class UrlInputTextChangeGuard {
    private var programmaticTextUpdateDepth = 0

    fun <T> runProgrammaticTextUpdate(block: () -> T): T {
        programmaticTextUpdateDepth += 1
        return try {
            block()
        } finally {
            programmaticTextUpdateDepth -= 1
        }
    }

    fun dispatchUserTextChange(value: String, onValueChange: (String) -> Unit) {
        if (programmaticTextUpdateDepth == 0) {
            onValueChange(value)
        }
    }
}

@Immutable
data class YtdlDestination(
    val route: String,
    val label: String,
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val accent: Color,
    val reservedEntries: List<String> = emptyList(),
)

internal data class RuntimeDownloadState(
    val url: String = "",
    val analysis: VideoAnalysis? = null,
    val activeRequest: DownloadRequest? = null,
    val activeStage: DownloadStage = DownloadStage.Idle,
    val formatSelection: FormatSelection = FormatSelection(),
    val appliedFormatSelection: FormatSelection = FormatSelection(),
    val selectedSubtitles: List<SubtitleInfo> = emptyList(),
    val thumbnailBitmap: Bitmap? = null,
    val thumbnailStatus: String = "",
    val isAnalyzing: Boolean = false,
    val isDownloading: Boolean = false,
    val userMessage: String = DefaultRuntimeMessage,
    val progressPercent: Double? = null,
    val overallProgressPercent: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val downloadStatus: String = "",
    val outputPath: String = "",
    val outputBytes: Long = 0L,
    val subtitleOutputCount: Int = 0,
    val subtitleOutputBytes: Long = 0L,
) {
    val hasRealTask: Boolean
        get() = downloadStatus.isNotBlank() ||
            progressPercent != null ||
            outputPath.isNotBlank() ||
            outputBytes > 0L ||
            subtitleOutputCount > 0 ||
            isDownloading
}

internal enum class QueueStageStatus {
    Pending,
    Current,
    Completed,
}

internal data class QueueStageItem(
    val label: String,
    val status: QueueStageStatus,
)

internal data class QueueProgressPresentation(
    val fraction: Float?,
    val isIndeterminate: Boolean,
)

internal data class FormatSettingSummaries(
    val frameRate: String,
    val videoCodec: String,
    val container: String,
)

fun ytdlNavigationDestinations(): List<YtdlDestination> = ytdlNavigationDestinations(DefaultPalette)

private fun ytdlNavigationDestinations(palette: YtdlAppPalette): List<YtdlDestination> = listOf(
    YtdlDestination(
        route = "download",
        label = "下载",
        title = "视频地址提取器",
        summary = "粘贴公开视频页面地址，分析后再开始保存。",
        icon = DownloadTabIcon,
        accent = palette.downloadAccent,
    ),
    YtdlDestination(
        route = "formats",
        label = "格式",
        title = "格式",
        summary = "设置下载格式偏好，不做强制转码承诺。",
        icon = FormatTabIcon,
        accent = palette.formatAccent,
    ),
    YtdlDestination(
        route = "queue",
        label = "队列",
        title = "队列",
        summary = "查看进行中、等待、完成和失败任务。",
        icon = QueueTabIcon,
        accent = palette.queueAccent,
    ),
    YtdlDestination(
        route = "history",
        label = "历史",
        title = "历史",
        summary = "搜索、打开、分享或删除本地记录。",
        icon = HistoryTabIcon,
        accent = palette.historyAccent,
    ),
    YtdlDestination(
        route = "settings",
        label = "设置",
        title = "设置",
        summary = "管理保存位置、解析器、媒体处理、隐私和外观。",
        icon = SettingsTabIcon,
        accent = palette.settingsAccent,
        reservedEntries = listOf("外观与颜色"),
    ),
)

internal fun ytdlNavigationAccentHexesForUiTest(
    presetId: String,
    darkTheme: Boolean = false,
): Map<String, String> {
    return ytdlNavigationDestinations(ytdlAppPaletteForPreset(presetId, darkTheme))
        .associate { destination -> destination.label to colorArgbHexForUiTest(destination.accent) }
}
fun ytdlVisibleContentLabels(): Map<String, List<String>> = mapOf(
    "download" to listOf("粘贴公开视频页面地址", "分析", "等待真实分析", "保存位置", "下载模式", "开始下载"),
    "formats" to listOf("视频+音频", "仅音频", "仅视频", "分辨率", "1080p", "需合并", "容器格式", "字幕", "本阶段默认不下载"),
    "queue" to listOf("下载进行中", "当前阶段", "等待真实任务", "暂无真实下载任务", "最近任务已完成", "最近任务失败", "最近任务已取消", "下载视频", "下载音频", "原生合并", "已取消"),
    "history" to listOf("搜索历史", "全部", "视频", "音频", "暂无真实历史记录", "完成下载后会显示"),
    "settings" to listOf("默认保存位置", "Cookies 文件", "解析器版本", "媒体处理能力", "通知权限", "下载仍在应用内显示进度", "隐私与授权说明", "不保存内容", "App 私有目录", "外观与颜色", "Codex 风格", "MVP2"),
)

@Composable
fun YtdlApp() {
    val context = LocalContext.current
    var selectedRoute by rememberSaveable { mutableStateOf("download") }
    var runtimeState by remember { mutableStateOf(RuntimeDownloadState()) }
    var hasUserConfirmed by rememberSaveable { mutableStateOf(false) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyFilterIndex by rememberSaveable { mutableStateOf(0) }
    val settingsRepository = remember { SettingsRepository.fromContext(context.applicationContext) }
    var appSettings by remember { mutableStateOf(settingsRepository.getSettings()) }
    var historyItems by remember { mutableStateOf(emptyList<HistoryUiItem>()) }
    var pendingExportOutput by remember { mutableStateOf<ExportController.AppPrivateOutput?>(null) }
    var pendingDeleteHistoryItem by remember { mutableStateOf<HistoryUiItem?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember { YtdlpBridge() }
    val notificationController = remember { NotificationController(context.applicationContext) }
    val notificationRuntimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsAllowed by remember { mutableStateOf(notificationController.canPostNotifications()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshHistory() {
        Thread {
            val rows = YtdlDatabaseProvider.get(context.applicationContext)
                .historyDao()
                .listRecent(50)
            val items = historyUiItemsFromRows(rows)
            mainHandler.post {
                historyItems = items
            }
        }.start()
    }

    fun selectRoute(route: String) {
        selectedRoute = route
        if (route == "history") {
            refreshHistory()
        }
    }

    val cookiesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val reference = SettingsCookiesReference.fromUserReference(
            reference = uri.toString(),
            displayName = displayNameForUri(context, uri),
        )
        if (reference == null) {
            runtimeState = runtimeState.copy(userMessage = "cookies 文件引用无效，请重新选择 cookies.txt。")
        } else {
            appSettings = settingsRepository.setCookiesReference(reference)
            runtimeState = runtimeState.copy(userMessage = "已保存 cookies 文件引用，仅任务运行时临时读取。")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val destinationUri = result.data?.data
        val output = pendingExportOutput
        pendingExportOutput = null
        if (result.resultCode != Activity.RESULT_OK || destinationUri == null || output == null) {
            runtimeState = runtimeState.copy(userMessage = ExportController.exportDeniedMessage())
            return@rememberLauncherForActivityResult
        }
        Thread {
            val copyResult = runCatching {
                context.contentResolver.openOutputStream(destinationUri)?.use { stream ->
                    ExportController.copyToStream(output, stream).getOrThrow()
                } ?: throw IllegalStateException("无法打开导出位置。")
            }
            mainHandler.post {
                runtimeState = runtimeState.copy(
                    userMessage = copyResult.fold(
                        onSuccess = { bytes -> "导出完成：${formatBytes(bytes)}。" },
                        onFailure = { error -> "导出失败：${error.message.orEmpty().ifBlank { "请重新选择位置。" }}" },
                    ),
                )
            }
        }.start()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = !notificationRuntimePermissionRequired || granted
        runtimeState = runtimeState.copy(
            userMessage = if (notificationsAllowed) {
                "通知权限已允许。"
            } else {
                "通知权限未允许；下载仍会在应用内显示进度。"
            },
        )
    }

    DisposableEffect(Unit) {
        val subscription = DownloadCoordinator.addListener { state ->
            mainHandler.post {
                runtimeState = runtimeState.withPipelineState(state)
                if (state.stage in TerminalDownloadStages && selectedRoute == "history") {
                    refreshHistory()
                }
            }
        }
        onDispose { subscription.close() }
    }

    DisposableEffect(lifecycleOwner, notificationRuntimePermissionRequired) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = refreshedNotificationPermissionState(
                    currentValue = notificationsAllowed,
                    systemValue = notificationController.canPostNotifications(),
                    runtimePermissionRequired = notificationRuntimePermissionRequired,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun loadThumbnail(thumbnailUrl: String) {
        Thread {
            val bitmap = loadThumbnailBitmap(thumbnailUrl)
            mainHandler.post {
                if (runtimeState.analysis?.thumbnailUrl == thumbnailUrl) {
                    runtimeState = runtimeState.copy(
                        thumbnailBitmap = bitmap,
                        thumbnailStatus = if (bitmap != null) "预览图已加载" else "预览图加载失败",
                    )
                }
            }
        }.start()
    }

    fun analyzeCurrentUrl() {
        val url = runtimeState.url.trim()
        if (url.isBlank()) {
            runtimeState = runtimeState.copy(userMessage = "请先输入公开视频页面地址。")
            return
        }

        runtimeState = runtimeState.copy(
            isAnalyzing = true,
            userMessage = "正在真实分析地址...",
            analysis = null,
            formatSelection = FormatSelection(),
            appliedFormatSelection = FormatSelection(),
            selectedSubtitles = emptyList(),
            thumbnailBitmap = null,
            thumbnailStatus = "",
        )
        Thread {
            val temporaryCookiesResult = prepareTemporaryCookiesForDownload(
                settingsReference = appSettings.cookiesReference,
                context = context.applicationContext,
                taskId = "analyze-${System.currentTimeMillis()}",
            )
            if (temporaryCookiesResult.isFailure) {
                mainHandler.post {
                    runtimeState = runtimeState.copy(
                        isAnalyzing = false,
                        userMessage = "cookies 文件读取失败，请重新选择 cookies 文件。",
                    )
                }
                return@Thread
            }
            val temporaryCookies = temporaryCookiesResult.getOrNull()
            val result = try {
                bridge.analyze(url, temporaryCookies?.file?.absolutePath)
            } finally {
                temporaryCookies?.delete()
            }
            mainHandler.post {
                runtimeState = result.fold(
                    onSuccess = { analysis ->
                        runtimeState.withAnalysisResult(analysis)
                    },
                    onFailure = { error ->
                        runtimeState.copy(
                            isAnalyzing = false,
                            userMessage = "分析失败：${error.message.orEmpty().ifBlank { "请检查地址或网络。" }}",
                        )
                    },
                )
                runtimeState.analysis?.thumbnailUrl
                    ?.takeIf { it.isNotBlank() && runtimeState.thumbnailBitmap == null }
                    ?.let(::loadThumbnail)
            }
        }.start()
    }

    fun startRealDownload() {
        if (!canStartDownload(runtimeState, hasUserConfirmed)) {
            runtimeState = runtimeState.copy(
                userMessage = if (runtimeState.analysis == null) {
                    "请先分析公开视频页面地址。"
                } else {
                    "请先确认有权保存该内容。"
                },
            )
            return
        }
        val url = runtimeState.url.trim()
        val temporaryCookies = prepareTemporaryCookiesForDownload(
            settingsReference = appSettings.cookiesReference,
            context = context.applicationContext,
            taskId = "download-${System.currentTimeMillis()}",
        ).getOrElse {
            runtimeState = runtimeState.copy(
                isDownloading = false,
                userMessage = "cookies 文件读取失败，请重新选择 cookies 文件。",
            )
            return
        }
        val requestResult = buildAppliedDownloadRequest(
            url = url,
            analysis = runtimeState.analysis,
            appliedSelection = runtimeState.appliedFormatSelection,
            selectedSubtitles = runtimeState.selectedSubtitles,
            cookiesPath = temporaryCookies?.file?.absolutePath,
        )
        if (requestResult.isFailure) {
            temporaryCookies?.delete()
            val message = requestResult.exceptionOrNull()?.message.orEmpty()
                .ifBlank { "格式选择错误，请重新分析或应用格式。" }
            runtimeState = runtimeState.copy(
                isDownloading = false,
                userMessage = if (message.contains("请先")) message else "格式选择错误：$message",
            )
            return
        }
        val request = requestResult.getOrThrow()

        val outputDir = File(context.filesDir, "gui-downloads").apply { mkdirs() }
        val startResult = DownloadCoordinator.startForegroundDownload(
            context = context.applicationContext,
            request = request,
            outputDirectory = outputDir,
        )
        runtimeState = startResult.fold(
            onSuccess = { waiting ->
                runtimeState.withForegroundStartState(waiting)
            },
            onFailure = { error ->
                temporaryCookies?.delete()
                runtimeState.withPipelineState(DownloadTaskState.idle()).copy(
                    isDownloading = false,
                    userMessage = "启动前台下载失败：${error.message.orEmpty().ifBlank { "请检查系统权限。" }}",
                )
            },
        )
    }

    fun outputForAppPrivateUri(appPrivateUri: String?): Result<ExportController.AppPrivateOutput> {
        return ExportController.discoverAppPrivateOutputUri(
            appPrivateUri = appPrivateUri,
            appPrivateRoot = File(context.filesDir, "gui-downloads"),
            legacyRoots = listOf(File(context.cacheDir, "gui-downloads")),
        )
    }

    fun outputForHistoryItem(item: HistoryUiItem): Result<ExportController.AppPrivateOutput> {
        return outputForAppPrivateUri(item.outputUri)
    }

    fun subtitleOutputForHistoryItem(item: HistoryUiItem): Result<ExportController.AppPrivateOutput> {
        return item.primarySubtitleOutputUri
            ?.let(::outputForAppPrivateUri)
            ?: Result.failure(IllegalStateException("该历史记录没有独立字幕文件。"))
    }

    fun fileProviderUri(output: ExportController.AppPrivateOutput): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            output.sourceFile,
        )
    }

    fun openHistoryItem(item: HistoryUiItem) {
        val output = outputForHistoryItem(item).getOrElse { error ->
            runtimeState = runtimeState.copy(userMessage = historyMissingLocalOutputMessage(error))
            return
        }
        val uri = fileProviderUri(output)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, output.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "打开下载文件"))
        }.onFailure {
            runtimeState = runtimeState.copy(userMessage = "没有可用应用打开该文件，可先导出到本机。")
        }
    }

    fun shareHistoryItem(item: HistoryUiItem) {
        val output = outputForHistoryItem(item).getOrElse { error ->
            runtimeState = runtimeState.copy(userMessage = historyMissingLocalOutputMessage(error))
            return
        }
        val uri = fileProviderUri(output)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(output.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享下载文件"))
        }.onFailure {
            runtimeState = runtimeState.copy(userMessage = "没有可用应用分享该文件。")
        }
    }

    fun shareSubtitleHistoryItem(item: HistoryUiItem) {
        val output = subtitleOutputForHistoryItem(item).getOrElse { error ->
            runtimeState = runtimeState.copy(userMessage = historyMissingSubtitleOutputMessage(error))
            return
        }
        val uri = fileProviderUri(output)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(output.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享字幕文件"))
        }.onFailure {
            runtimeState = runtimeState.copy(userMessage = "没有可用应用分享字幕文件。")
        }
    }

    fun exportHistoryItem(item: HistoryUiItem) {
        val output = outputForHistoryItem(item).getOrElse { error ->
            runtimeState = runtimeState.copy(userMessage = historyMissingLocalOutputMessage(error))
            return
        }
        pendingExportOutput = output
        exportLauncher.launch(
            ExportController.createDocumentIntent(
                output = output,
                suggestedDisplayName = suggestedExportDisplayName(item, output.displayName),
            ),
        )
    }

    fun exportSubtitleHistoryItem(item: HistoryUiItem) {
        val output = subtitleOutputForHistoryItem(item).getOrElse { error ->
            runtimeState = runtimeState.copy(userMessage = historyMissingSubtitleOutputMessage(error))
            return
        }
        pendingExportOutput = output
        exportLauncher.launch(
            ExportController.createDocumentIntent(
                output = output,
                suggestedDisplayName = suggestedExportDisplayName(item, output.displayName),
            ),
        )
    }

    fun deleteHistoryItem(item: HistoryUiItem) {
        Thread {
            val deleted = YtdlDatabaseProvider.get(context.applicationContext)
                .historyDao()
                .deleteById(item.id)
            val rows = YtdlDatabaseProvider.get(context.applicationContext)
                .historyDao()
                .listRecent(50)
            val items = historyUiItemsFromRows(rows)
            mainHandler.post {
                historyItems = items
                runtimeState = runtimeState.copy(
                    userMessage = if (deleted > 0) "已删除历史记录。" else "历史记录已不存在。",
                )
            }
        }.start()
    }

    fun requestDeleteHistoryItem(item: HistoryUiItem) {
        pendingDeleteHistoryItem = item
    }

    fun selectDownloadMode(mode: FormatMode) {
        val selection = selectBestAvailableFormatSelection(
            analysis = runtimeState.analysis,
            mode = mode,
            preferredHeight = runtimeState.formatSelection.selectedHeight,
        )
        runtimeState = runtimeState.copy(
            formatSelection = selection,
            appliedFormatSelection = selection,
            userMessage = if (runtimeState.analysis == null) {
                "已切换下载模式：${mode.label}，请先分析后生成真实格式。"
            } else {
                "已切换下载模式：${formatSelectionSummary(runtimeState.analysis, selection)}。"
            },
        )
    }

    val themeConfig = themeConfigForSettings(appSettings)
    YtdlTheme(config = themeConfig) {
        val palette = LocalYtdlAppPalette.current
        val destinations = ytdlNavigationDestinations(palette)
        val selected = destinations.firstOrNull { it.route == selectedRoute } ?: destinations.first()

        Scaffold(
            containerColor = palette.appBackground,
            bottomBar = {
                YtdlBottomBar(
                    destinations = destinations,
                    selectedRoute = selected.route,
                    onSelected = ::selectRoute,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.appBackground)
                    .padding(innerPadding),
            ) {
                key(selected.route) {
                    LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            testTagsAsResourceId = true
                            ytdlColorPresetId = themeConfig.colorPreset.id
                            ytdlSettingsAccentArgb = colorArgbHexForUiTest(palette.settingsAccent)
                        }
                        .testTag("ytdl-screen-${selected.route}"),
                    contentPadding = PaddingValues(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { PageHeader(selected) }
                    when (selected.route) {
                        "download" -> downloadPageItems(
                            state = runtimeState,
                            hasUserConfirmed = hasUserConfirmed,
                            onUrlChange = {
                                hasUserConfirmed = false
                                runtimeState = runtimeState.copy(
                                    url = it,
                                    analysis = null,
                                    formatSelection = FormatSelection(),
                                    appliedFormatSelection = FormatSelection(),
                                    selectedSubtitles = emptyList(),
                                    thumbnailBitmap = null,
                                    thumbnailStatus = "",
                                )
                            },
                            onAnalyze = ::analyzeCurrentUrl,
                            onStartDownload = ::startRealDownload,
                            onUserConfirmedChange = { hasUserConfirmed = it },
                            onModeSelected = ::selectDownloadMode,
                        )
                        "formats" -> formatPageItems(
                            analysis = runtimeState.analysis,
                            selection = runtimeState.formatSelection,
                            selectedSubtitles = runtimeState.selectedSubtitles,
                            onSelectionChange = { selection ->
                                runtimeState = runtimeState.copy(formatSelection = selection)
                            },
                            onSubtitleSelectionChange = { subtitles ->
                                runtimeState = runtimeState.copy(selectedSubtitles = subtitles)
                            },
                            onApplySelection = {
                                val summary = formatSelectionSummaryWithSubtitles(
                                    runtimeState.analysis,
                                    runtimeState.formatSelection,
                                    runtimeState.selectedSubtitles,
                                )
                                mainHandler.post {
                                    runtimeState = runtimeState.copy(
                                        appliedFormatSelection = runtimeState.formatSelection,
                                        userMessage = "已应用格式选择：$summary",
                                    )
                                    selectedRoute = "download"
                                }
                            },
                        )
                        "queue" -> queuePageItems(
                            state = runtimeState,
                            onCancelDownload = {
                                DownloadCoordinator.cancelActive()
                                runtimeState = runtimeState.copy(userMessage = "已请求取消当前下载。")
                            },
                        )
                        "history" -> historyPageItems(
                            historyItems = historyItems,
                            historyQuery = historyQuery,
                            selectedFilterIndex = historyFilterIndex,
                            userMessage = runtimeState.userMessage,
                            onHistoryQueryChange = { historyQuery = it },
                            onHistoryFilterChange = { historyFilterIndex = it },
                            onOpen = ::openHistoryItem,
                            onShare = ::shareHistoryItem,
                            onExport = ::exportHistoryItem,
                            onShareSubtitle = ::shareSubtitleHistoryItem,
                            onExportSubtitle = ::exportSubtitleHistoryItem,
                            onDelete = ::requestDeleteHistoryItem,
                        )
                        "settings" -> settingsPageItems(
                            settings = appSettings,
                            notificationsAllowed = notificationsAllowed,
                            notificationRuntimePermissionRequired = notificationRuntimePermissionRequired,
                            onSelectCookies = {
                                cookiesPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                            },
                            onRequestNotifications = {
                                notificationsAllowed = notificationController.canPostNotifications()
                                if (!notificationRuntimePermissionRequired) {
                                    runtimeState = runtimeState.copy(userMessage = "当前系统无需单独授权通知。")
                                } else if (notificationsAllowed) {
                                    runtimeState = runtimeState.copy(userMessage = "通知权限已允许。")
                                } else {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onThemeModeChange = { modeId ->
                                appSettings = settingsRepository.setThemeMode(modeId)
                                runtimeState = runtimeState.copy(userMessage = "已切换外观模式。")
                            },
                            onColorPresetChange = { presetId ->
                                appSettings = settingsRepository.setColorPreset(presetId)
                                runtimeState = runtimeState.copy(userMessage = "已切换颜色方案。")
                            },
                        )
                    }
                    }
                }
                if (selected.route == "queue" && shouldShowQueueScrollIndicator(runtimeState)) {
                    QueueScrollIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 5.dp)
                            .semantics { testTagsAsResourceId = true }
                            .testTag("ytdl-queue-scroll-indicator"),
                    )
                }
            }
        }
        val deleteTarget = pendingDeleteHistoryItem
        if (deleteTarget != null) {
            AlertDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-history-delete-dialog"),
                onDismissRequest = { pendingDeleteHistoryItem = null },
                title = { Text("确认删除历史记录") },
                text = { Text("将删除“${deleteTarget.title}”的历史记录，不会删除已保存的媒体文件。") },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("ytdl-history-delete-confirm"),
                        onClick = {
                            pendingDeleteHistoryItem = null
                            deleteHistoryItem(deleteTarget)
                        },
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.testTag("ytdl-history-delete-cancel"),
                        onClick = { pendingDeleteHistoryItem = null },
                    ) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

internal fun RuntimeDownloadState.withAnalysisForUiTest(analysis: VideoAnalysis): RuntimeDownloadState = withAnalysisResult(analysis)

private fun RuntimeDownloadState.withAnalysisResult(analysis: VideoAnalysis): RuntimeDownloadState {
    val freshSelection = defaultFormatSelection(analysis)
    return copy(
        analysis = analysis,
        formatSelection = freshSelection,
        appliedFormatSelection = freshSelection,
        selectedSubtitles = emptyList(),
        thumbnailBitmap = null,
        thumbnailStatus = if (analysis.thumbnailUrl.isNullOrBlank()) {
            "无可用预览图"
        } else {
            "正在加载预览图"
        },
        isAnalyzing = false,
        userMessage = AnalysisCompleteRuntimeMessage,
    )
}

internal fun RuntimeDownloadState.withForegroundStartStateForUiTest(state: DownloadTaskState): RuntimeDownloadState = withForegroundStartState(state)

private fun RuntimeDownloadState.withForegroundStartState(state: DownloadTaskState): RuntimeDownloadState {
    val stageText = userVisibleDownloadStatus(state.stage)
    return withPipelineState(state).copy(
        userMessage = "真实下载已加入前台队列，当前阶段：$stageText。",
    )
}

internal fun RuntimeDownloadState.withPipelineStateForUiTest(state: DownloadTaskState): RuntimeDownloadState = withPipelineState(state)

private fun RuntimeDownloadState.withPipelineState(state: DownloadTaskState): RuntimeDownloadState {
    val statusText = userVisibleDownloadStatus(state.stage)
    val progress = state.progress
    val mediaOutput = state.outputs.firstOrNull { it.kind == DownloadOutputKind.Media }
    val subtitleOutputs = state.outputs.filter { it.kind == DownloadOutputKind.Subtitle }
    if (state.stage == DownloadStage.Completed && mediaOutput == null) {
        return copy(
            isDownloading = false,
            userMessage = "下载结果无有效输出，请重试。",
            progressPercent = null,
            overallProgressPercent = null,
            downloadedBytes = null,
            totalBytes = null,
            downloadStatus = userVisibleDownloadStatus(DownloadStage.Failed),
            activeRequest = state.request,
            activeStage = DownloadStage.Failed,
            outputPath = "",
            outputBytes = 0L,
            subtitleOutputCount = 0,
            subtitleOutputBytes = 0L,
        )
    }
    if (state.stage == DownloadStage.Idle || (state.request == null && state.outputs.isEmpty() && state.stage == DownloadStage.Failed)) {
        return copy(
            isDownloading = false,
            userMessage = DefaultRuntimeMessage,
            progressPercent = null,
            overallProgressPercent = null,
            downloadedBytes = null,
            totalBytes = null,
            downloadStatus = "",
            activeRequest = null,
            activeStage = DownloadStage.Idle,
            outputPath = "",
            outputBytes = 0L,
            subtitleOutputCount = 0,
            subtitleOutputBytes = 0L,
        )
    }
    return copy(
        isDownloading = state.stage !in TerminalDownloadStages,
        userMessage = when (state.stage) {
            DownloadStage.Failed -> if (mediaOutput != null) {
                "媒体文件已保存，但${state.errorMessage.orEmpty().ifBlank { "附加文件处理失败。" }}"
            } else {
                "下载失败：${state.errorMessage.orEmpty().ifBlank { "请检查网络或授权状态。" }}"
            }
            DownloadStage.Canceled -> "下载已取消。"
            DownloadStage.Completed -> if (subtitleOutputs.isNotEmpty()) {
                "下载完成：媒体文件 + 独立字幕文件已保存，可在历史中查看。"
            } else {
                "下载完成：媒体文件已保存，可在历史中打开或导出。"
            }
            else -> "正在$statusText..."
        },
        downloadStatus = statusText,
        activeRequest = state.request,
        activeStage = state.stage,
        progressPercent = when (state.stage) {
            DownloadStage.Completed -> 100.0
            else -> progress?.percent
        },
        overallProgressPercent = queueOverallProgressPercent(state, progress?.percent),
        downloadedBytes = progress?.downloadedBytes ?: mediaOutput?.bytesWritten,
        totalBytes = progress?.totalBytes ?: mediaOutput?.bytesWritten,
        outputPath = mediaOutput?.path.orEmpty(),
        outputBytes = mediaOutput?.bytesWritten ?: 0L,
        subtitleOutputCount = subtitleOutputs.size,
        subtitleOutputBytes = subtitleOutputs.sumOf { it.bytesWritten },
    )
}

private val TerminalDownloadStages = setOf(
    DownloadStage.Completed,
    DownloadStage.Failed,
    DownloadStage.Canceled,
    DownloadStage.Idle,
)

private fun loadThumbnailBitmap(thumbnailUrl: String, targetSizePx: Int? = null): Bitmap? {
    return runCatching {
        val connection = (URL(thumbnailUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "YTDL-Android/0.1")
            setRequestProperty("Accept", "image/*")
        }
        if (targetSizePx != null && connection.contentLengthLong > ThumbnailDecodeMaxBytes) {
            return@runCatching null
        }
        connection.inputStream.use { stream ->
            if (targetSizePx == null) {
                BitmapFactory.decodeStream(stream)
            } else {
                val bytes = readCappedBytes(stream, ThumbnailDecodeMaxBytes) ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds, targetSizePx)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        }
    }.getOrNull()
}

private fun readCappedBytes(stream: InputStream, maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) {
            return output.toByteArray()
        }
        total += read
        if (total > maxBytes) {
            return null
        }
        output.write(buffer, 0, read)
    }
}

private fun calculateInSampleSize(bounds: BitmapFactory.Options, targetSizePx: Int): Int {
    val height = bounds.outHeight
    val width = bounds.outWidth
    var inSampleSize = 1
    if (height <= 0 || width <= 0) {
        return inSampleSize
    }
    while (height / (inSampleSize * 2) >= targetSizePx && width / (inSampleSize * 2) >= targetSizePx) {
        inSampleSize *= 2
    }
    return inSampleSize
}

private object HistoryThumbnailLoader {
    private val historyThumbnailCache = LruCache<String, Bitmap>(HistoryThumbnailCacheMaxItems)
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheLock = Any()

    fun load(thumbnailUrl: String, onLoaded: (Bitmap?) -> Unit): AutoCloseable {
        val cached = synchronized(cacheLock) { historyThumbnailCache.get(thumbnailUrl) }
        if (cached != null) {
            mainHandler.post { onLoaded(cached) }
            return AutoCloseable { }
        }

        val closed = AtomicBoolean(false)
        executor.execute {
            val bitmap = loadThumbnailBitmap(thumbnailUrl, targetSizePx = HistoryThumbnailTargetPx)
            if (bitmap != null) {
                synchronized(cacheLock) { historyThumbnailCache.put(thumbnailUrl, bitmap) }
            }
            mainHandler.post {
                if (!closed.get()) {
                    onLoaded(bitmap)
                }
            }
        }
        return AutoCloseable { closed.set(true) }
    }
}

private fun displayNameForUri(context: android.content.Context, uri: Uri): String {
    val queriedName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }.getOrNull()
    return queriedName
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "cookies 文件"
}

@Composable
private fun YtdlBottomBar(
    destinations: List<YtdlDestination>,
    selectedRoute: String,
    onSelected: (String) -> Unit,
) {
    val palette = LocalYtdlAppPalette.current
    val navigationBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    Surface(
        color = palette.bottomBarBackground,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .padding(
                    start = 10.dp,
                    top = 7.dp,
                    end = 10.dp,
                    bottom = 7.dp + navigationBottomPadding + BottomBarGestureBuffer,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = destination.route == selectedRoute
                Column(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) destination.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .testTag("ytdl-tab-${destination.route}")
                        .clickable { onSelected(destination.route) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 22.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = if (selected) destination.accent else palette.neutralText,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = destination.label,
                        color = if (selected) destination.accent else palette.neutralText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageHeader(destination: YtdlDestination) {
    val palette = LocalYtdlAppPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = destination.title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.titleText,
                fontWeight = FontWeight.Bold,
            )
            if (destination.route == "download") {
                Text(
                    text = "?",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, palette.borderColor, CircleShape)
                        .padding(top = 2.dp),
                    textAlign = TextAlign.Center,
                    color = palette.softText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = destination.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.softText,
        )
    }
}

@Composable
private fun QueueScrollIndicator(modifier: Modifier = Modifier) {
    val palette = LocalYtdlAppPalette.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("⌃", color = palette.softText.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 250.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.borderColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.softText),
            )
        }
        Text("⌄", color = palette.softText.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadPageItems(
    state: RuntimeDownloadState,
    hasUserConfirmed: Boolean,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onStartDownload: () -> Unit,
    onUserConfirmedChange: (Boolean) -> Unit,
    onModeSelected: (FormatMode) -> Unit,
) {
    val modeSelections = downloadModeSelections(state)
    item {
        val palette = LocalYtdlAppPalette.current
        val showKeyboardOnFocus = shouldShowUrlInputKeyboardOnFocus(LocalConfiguration.current.keyboard)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            UrlInputField(
                value = state.url,
                onValueChange = onUrlChange,
                showKeyboardOnFocus = showKeyboardOnFocus,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ytdl-url-input"),
            )
            Button(
                onClick = onAnalyze,
                enabled = !state.isAnalyzing,
                modifier = Modifier.testTag("ytdl-analyze-button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.downloadAccent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(if (state.isAnalyzing) "分析中" else "分析", fontWeight = FontWeight.Bold)
            }
        }
    }
    item { DownloadPreviewCard(state) }
    if (shouldShowRuntimeMessage(state.userMessage)) {
        item {
            RuntimeMessageCard(state.userMessage)
        }
    }
    item { SettingLineCard(title = "保存位置", subtitle = "App 私有目录 · 导出名：标题+时间；重名加序号", leading = "□", trailing = "›") }
    item {
        SectionTitle("下载模式")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ModeCard(
                "♫",
                "仅音频",
                selected = modeSelections[FormatMode.AudioOnly] == true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onModeSelected(FormatMode.AudioOnly) }
                    .testTag("ytdl-download-mode-audio"),
            )
            ModeCard(
                "▣",
                "视频+音频",
                selected = modeSelections[FormatMode.VideoAndAudio] == true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onModeSelected(FormatMode.VideoAndAudio) }
                    .testTag("ytdl-download-mode-av"),
            )
            ModeCard(
                "▤",
                "仅视频",
                selected = modeSelections[FormatMode.VideoOnly] == true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onModeSelected(FormatMode.VideoOnly) }
                    .testTag("ytdl-download-mode-video"),
            )
        }
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = hasUserConfirmed,
                onCheckedChange = onUserConfirmedChange,
                modifier = Modifier.testTag("ytdl-download-authorized-checkbox"),
            )
            Text("我确认有权保存该内容", style = MaterialTheme.typography.bodyMedium)
        }
    }
    item {
        val palette = LocalYtdlAppPalette.current
        Button(
            onClick = onStartDownload,
            enabled = canStartDownload(state, hasUserConfirmed),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ytdl-download-start"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.downloadAccent),
            contentPadding = PaddingValues(vertical = 13.dp),
        ) {
            Text(if (state.isDownloading) "↓  下载中" else "↓  开始下载", fontWeight = FontWeight.Bold)
        }
    }
}

internal fun canStartDownloadForUiTest(state: RuntimeDownloadState, hasUserConfirmed: Boolean): Boolean = canStartDownload(state, hasUserConfirmed)

private fun canStartDownload(state: RuntimeDownloadState, hasUserConfirmed: Boolean): Boolean {
    return !state.isAnalyzing && !state.isDownloading && state.analysis != null && hasUserConfirmed
}

internal fun shouldShowRuntimeMessageForUiTest(message: String): Boolean = shouldShowRuntimeMessage(message)

private fun shouldShowRuntimeMessage(message: String): Boolean {
    val trimmed = message.trim()
    return trimmed.isNotEmpty() &&
        trimmed != DefaultRuntimeMessage &&
        trimmed != AnalysisCompleteRuntimeMessage
}

internal fun historyMissingLocalOutputMessageForUiTest(error: Throwable?): String = historyMissingLocalOutputMessage(error)

private fun historyMissingLocalOutputMessage(error: Throwable?): String {
    val fallback = "历史记录对应的本地文件不存在或为空，请重新下载或删除该记录。"
    val message = error?.message.orEmpty()
    return if (message.contains("不存在") || message.contains("为空") || message.contains("本地输出")) {
        fallback
    } else {
        message.ifBlank { fallback }
    }
}

private fun historyMissingSubtitleOutputMessage(error: Throwable?): String {
    val fallback = "历史记录对应的字幕文件不存在或为空，请重新下载或删除该记录。"
    val message = error?.message.orEmpty()
    return if (message.contains("不存在") || message.contains("为空") || message.contains("本地输出")) {
        fallback
    } else {
        message.ifBlank { fallback }
    }
}

internal fun shouldShowQueueRuntimeMessageForUiTest(message: String): Boolean = shouldShowQueueRuntimeMessage(message)

private fun shouldShowQueueRuntimeMessage(message: String): Boolean {
    val trimmed = message.trim()
    return shouldShowRuntimeMessage(trimmed) &&
        !trimmed.startsWith("正在") &&
        !trimmed.startsWith("下载完成：")
}

private fun isRuntimeWarningMessage(message: String): Boolean {
    return listOf("失败", "无效", "未获得", "请先", "无法", "错误").any(message::contains)
}

internal fun downloadModeSelectionsForUiTest(state: RuntimeDownloadState): Map<FormatMode, Boolean> = downloadModeSelections(state)

private fun downloadModeSelections(state: RuntimeDownloadState): Map<FormatMode, Boolean> {
    return FormatMode.entries.associateWith { mode -> mode == state.appliedFormatSelection.mode }
}

@Composable
private fun UrlInputField(
    value: String,
    onValueChange: (String) -> Unit,
    showKeyboardOnFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalYtdlAppPalette.current
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val textChangeGuard = remember { UrlInputTextChangeGuard() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .border(1.dp, palette.borderColor, shape)
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔗", color = palette.softText)
        Spacer(Modifier.size(8.dp))
        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    id = R.id.ytdl_url_input
                    setSingleLine(true)
                    setPadding(0, 0, 0, 0)
                    background = null
                    includeFontPadding = false
                    textSize = 16f
                    hint = "粘贴公开视频页面地址"
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_URI or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    setShowSoftInputOnFocus(showKeyboardOnFocus)
                    setOnFocusChangeListener { view, hasFocus ->
                        if (hasFocus && showKeyboardOnFocus) {
                            (view as EditText).showUrlInputKeyboard()
                        }
                    }
                    setOnClickListener {
                        if (showKeyboardOnFocus) {
                            showUrlInputKeyboard()
                        }
                    }
                    if (shouldDisableUrlInputAutoHandwriting(Build.VERSION.SDK_INT)) {
                        setAutoHandwritingEnabled(false)
                    }
                    addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                            override fun afterTextChanged(s: Editable?) {
                                textChangeGuard.dispatchUserTextChange(
                                    s?.toString().orEmpty(),
                                    currentOnValueChange.value,
                                )
                            }
                        },
                    )
                }
            },
            update = { editText ->
                editText.hint = "粘贴公开视频页面地址"
                editText.setTextColor(palette.titleText.toArgb())
                editText.setHintTextColor(palette.softText.toArgb())
                editText.setShowSoftInputOnFocus(showKeyboardOnFocus)
                if (shouldDisableUrlInputAutoHandwriting(Build.VERSION.SDK_INT)) {
                    editText.setAutoHandwritingEnabled(false)
                }
                if (editText.text.toString() != value) {
                    textChangeGuard.runProgrammaticTextUpdate {
                        editText.setText(value)
                        editText.setSelection(editText.text.length)
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun DownloadPreviewCard(state: RuntimeDownloadState) {
    val analysis = state.analysis
    val duration = analysis?.durationSeconds?.let(::formatDuration) ?: "未分析"
    val title = analysis?.title?.ifBlank { "未命名视频" } ?: "等待真实分析"
    val formatSummary = downloadPreviewFormatSummary(state)

    AppCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            if (state.thumbnailBitmap != null) {
                Image(
                    bitmap = state.thumbnailBitmap.asImageBitmap(),
                    contentDescription = "视频预览图",
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("ytdl-thumbnail-image"),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("ytdl-thumbnail-placeholder")
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF78B6E8), Color(0xFF7CBF7D), Color(0xFFE7D5A7)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.thumbnailStatus.ifBlank { "预览图" },
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = duration,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (analysis == null) "请输入地址并分析" else "分析完成 · 公开授权内容由用户确认",
            color = LocalYtdlAppPalette.current.successGreen,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            InfoPill("时长", duration)
            InfoPill("格式", formatSummary)
        }
    }
}

@Composable
private fun RuntimeMessageCard(message: String) {
    val palette = LocalYtdlAppPalette.current
    val isWarning = isRuntimeWarningMessage(message)
    val accent = if (isWarning) palette.downloadAccent else palette.formatAccent
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ytdl-runtime-message")
                .padding(horizontal = 10.dp, vertical = 8.dp),
            color = accent,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

internal fun downloadPreviewFormatSummaryForUiTest(state: RuntimeDownloadState): String = downloadPreviewFormatSummary(state)

private fun downloadPreviewFormatSummary(state: RuntimeDownloadState): String {
    return formatSelectionSummaryWithSubtitles(
        state.analysis,
        state.appliedFormatSelection,
        state.selectedSubtitles,
    )
}

private fun formatSelectionSummaryWithSubtitles(
    analysis: VideoAnalysis?,
    selection: FormatSelection,
    selectedSubtitles: List<SubtitleInfo>,
): String {
    val mediaSummary = formatSelectionSummary(analysis, selection)
    if (selectedSubtitles.isEmpty()) return mediaSummary
    return "$mediaSummary · 独立字幕文件"
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.formatPageItems(
    analysis: VideoAnalysis?,
    selection: FormatSelection,
    selectedSubtitles: List<SubtitleInfo>,
    onSelectionChange: (FormatSelection) -> Unit,
    onSubtitleSelectionChange: (List<SubtitleInfo>) -> Unit,
    onApplySelection: () -> Unit,
) {
    item {
        val palette = LocalYtdlAppPalette.current
        SegmentedRow(
            options = listOf(FormatMode.VideoAndAudio, FormatMode.AudioOnly, FormatMode.VideoOnly).map { it.label },
            selectedIndex = selection.mode.ordinal,
            accent = palette.formatAccent,
            testTagPrefix = "ytdl-format-mode",
            onSelected = { index ->
                val mode = FormatMode.entries[index]
                onSelectionChange(
                    selectBestAvailableFormatSelection(
                        analysis = analysis,
                        mode = mode,
                        preferredHeight = selection.selectedHeight,
                    ),
                )
            },
        )
    }
    if (analysis == null) {
        item {
            val palette = LocalYtdlAppPalette.current
            AppCard(modifier = Modifier.testTag("ytdl-format-empty-card")) {
                Text("请先分析视频", color = palette.softText, fontWeight = FontWeight.Bold)
                Text("格式页会根据当前视频真实提供的格式生成可选项。", color = palette.softText, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    val rows = buildFormatResolutionRows(analysis, selection)
    val summaries = formatSettingSummaries(analysis, selection)
    item {
        AppCard(modifier = Modifier.testTag("ytdl-format-resolution-card")) {
            SectionTitle("分辨率")
            rows.forEach { row ->
                ResolutionRow(
                    row = row,
                    onSelect = {
                        if (row.selectable) {
                            onSelectionChange(selectionFromRow(selection.mode, row))
                        }
                    },
                )
            }
        }
    }
    item { SettingLineCard("帧率", summaries.frameRate, "▾", "›") }
    item { SettingLineCard("视频编码", summaries.videoCodec, "▾", "›") }
    item { SettingLineCard("容器格式", summaries.container, "▾", "›") }
    item {
        val subtitles = analysis.subtitles
        val hasSubtitles = subtitles.isNotEmpty()
        val subtitleUi = subtitleSelectionUiState(analysis, selectedSubtitles)
        val newSelection = if (selectedSubtitles.isEmpty() && hasSubtitles) {
            listOfNotNull(recommendedSubtitle(subtitles))
        } else {
            emptyList()
        }
        SettingLineCard(
            "字幕",
            subtitleUi.label,
            "▾",
            subtitleUi.trailing,
            enabled = subtitleUi.canToggle,
            modifier = Modifier
                .clickable(enabled = subtitleUi.canToggle) { onSubtitleSelectionChange(newSelection) }
                .testTag("ytdl-format-subtitle-toggle"),
        )
    }
    item {
        val palette = LocalYtdlAppPalette.current
        Surface(
            modifier = Modifier.testTag("ytdl-format-summary"),
            color = palette.formatAccent.copy(alpha = 0.11f),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.formatAccent.copy(alpha = 0.35f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("实际下载：${formatSelectionSummaryWithSubtitles(analysis, selection, selectedSubtitles)}", color = palette.formatAccent, fontWeight = FontWeight.Bold)
                Text("开始下载会按当前格式选择进入真实任务队列。", color = palette.softText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item {
        val palette = LocalYtdlAppPalette.current
        Button(
            onClick = onApplySelection,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ytdl-format-apply"),
            colors = ButtonDefaults.buttonColors(containerColor = palette.formatAccent),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 15.dp),
        ) {
            Text("应用选择", fontWeight = FontWeight.Bold)
        }
    }
}

internal fun formatSettingSummariesForUiTest(
    analysis: VideoAnalysis?,
    selection: FormatSelection,
): FormatSettingSummaries = formatSettingSummaries(analysis, selection)

private fun formatSettingSummaries(
    analysis: VideoAnalysis?,
    selection: FormatSelection,
): FormatSettingSummaries {
    if (analysis == null) {
        return FormatSettingSummaries(
            frameRate = "分析后显示",
            videoCodec = "分析后显示",
            container = "分析后显示",
        )
    }
    val row = buildFormatResolutionRows(analysis, selection).firstOrNull { it.selected && it.selectable }
        ?: return FormatSettingSummaries(
            frameRate = "请选择可用格式",
            videoCodec = "请选择可用格式",
            container = "请选择可用格式",
        )
    val video = row.videoFormatId?.let { id -> analysis.formats.firstOrNull { it.id == id } }
    val audio = row.audioFormatId?.let { id -> analysis.formats.firstOrNull { it.id == id } }

    val frameRate = when {
        selection.mode == FormatMode.AudioOnly -> "不适用"
        video?.fps != null -> "${String.format(Locale.US, "%.2f", video.fps).trimEnd('0').trimEnd('.')}fps"
        else -> "未提供"
    }
    val videoCodec = when {
        selection.mode == FormatMode.AudioOnly -> "不适用"
        else -> video?.videoCodec
            ?.takeIf { it.isNotBlank() && it != "none" }
            ?: "未提供"
    }
    val container = when {
        row.mergeRequired -> "MP4（原生合并输出）"
        selection.mode == FormatMode.AudioOnly -> audio?.ext?.uppercase(Locale.US)?.ifBlank { null } ?: "音频容器未提供"
        else -> video?.ext?.uppercase(Locale.US)?.ifBlank { null } ?: "未提供"
    }

    return FormatSettingSummaries(
        frameRate = frameRate,
        videoCodec = videoCodec,
        container = container,
    )
}

internal fun androidx.compose.foundation.lazy.LazyListScope.queuePageItems(
    state: RuntimeDownloadState,
    onCancelDownload: () -> Unit,
) {
    item {
        val palette = LocalYtdlAppPalette.current
        Surface(color = palette.queueAccent.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(queueHeaderTitle(state), color = palette.queueAccent, fontWeight = FontWeight.Bold)
                Text(
                    queueHeaderSummary(state),
                    color = palette.softText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (shouldShowQueueRuntimeMessage(state.userMessage)) {
        item {
            RuntimeMessageCard(state.userMessage)
        }
    }
    if (state.hasRealTask) {
        item { SectionTitle("真实任务（1）") }
        item {
            val palette = LocalYtdlAppPalette.current
            val progress = queueProgressPresentation(state)
            val title = state.analysis?.title?.takeIf { it.isNotBlank() } ?: "真实下载任务"
            Box(modifier = Modifier.testTag("ytdl-real-queue-card")) {
                QueueCard(
                    title = title,
                    subtitle = queueCardSubtitle(state),
                    progress = progress,
                    status = queueCardStatus(state),
                    meta = queueCardMeta(state),
                    formatBadge = queueCardFormatBadge(state),
                    stageItems = queueStageItems(state),
                    accent = queueCardAccent(state, palette),
                    actions = queueCardActions(state),
                    thumbnailBitmap = state.thumbnailBitmap,
                    onCancel = onCancelDownload,
                )
            }
        }
    } else {
        item { SectionTitle("等待真实任务") }
        item {
            val palette = LocalYtdlAppPalette.current
            QueueCard(
                title = "尚未开始真实下载",
                subtitle = "请在下载页输入地址并点击开始下载",
                progress = QueueProgressPresentation(fraction = null, isIndeterminate = false),
                status = "待开始",
                meta = "这里不会显示假进度",
                formatBadge = "",
                stageItems = emptyList(),
                accent = palette.queueAccent,
                actions = emptyList(),
                modifier = Modifier.testTag("ytdl-queue-active-card"),
            )
        }
    }
}

private fun queueHeaderTitle(state: RuntimeDownloadState): String {
    if (!state.hasRealTask) return "暂无真实下载任务"
    return when (state.downloadStatus) {
        "下载完成" -> "最近任务已完成"
        "下载失败" -> "最近任务失败"
        "已取消" -> "最近任务已取消"
        else -> "下载进行中"
    }
}

internal fun queueHeaderTitleForUiTest(state: RuntimeDownloadState): String = queueHeaderTitle(state)

private fun queueHeaderSummary(state: RuntimeDownloadState): String {
    if (!state.hasRealTask) return "暂无真实下载任务"
    return when (state.downloadStatus) {
        "下载完成" -> "最近任务已完成"
        "下载失败" -> "最近任务失败"
        "已取消" -> "最近任务已取消"
        else -> "1 个真实任务正在处理"
    }
}

internal fun queueHeaderSummaryForUiTest(state: RuntimeDownloadState): String = queueHeaderSummary(state)

internal fun shouldShowQueueScrollIndicatorForUiTest(state: RuntimeDownloadState): Boolean = shouldShowQueueScrollIndicator(state)

private fun shouldShowQueueScrollIndicator(state: RuntimeDownloadState): Boolean = state.hasRealTask

private fun queueCardSubtitle(state: RuntimeDownloadState): String {
    val status = state.downloadStatus.ifBlank { "等待进度" }
    return when (state.downloadStatus) {
        "下载完成", "下载失败", "已取消" -> status
        else -> "当前阶段 · $status"
    }
}

internal fun queueCardSubtitleForUiTest(state: RuntimeDownloadState): String = queueCardSubtitle(state)

private data class QueueStageSpec(
    val label: String,
    val stage: DownloadStage,
)

private fun queueStageSpecs(request: DownloadRequest?): List<QueueStageSpec> {
    if (request == null) return emptyList()
    val mediaStages = when (request.route) {
        is DownloadRoute.DirectSingleFile -> listOf(QueueStageSpec("下载视频", DownloadStage.DownloadingVideo))
        is DownloadRoute.VideoOnly -> listOf(QueueStageSpec("下载视频", DownloadStage.DownloadingVideo))
        is DownloadRoute.AudioOnly -> listOf(QueueStageSpec("下载音频", DownloadStage.DownloadingAudio))
        is DownloadRoute.MergeRequired -> listOf(
            QueueStageSpec("下载视频", DownloadStage.DownloadingVideo),
            QueueStageSpec("下载音频", DownloadStage.DownloadingAudio),
            QueueStageSpec("原生合并", DownloadStage.Merging),
        )
    }
    if (request.selectedSubtitles.isEmpty()) return mediaStages
    return mediaStages + QueueStageSpec("字幕文件", DownloadStage.DownloadingSubtitles)
}

private fun queueStageItems(state: RuntimeDownloadState): List<QueueStageItem> {
    val specs = queueStageSpecs(state.activeRequest)
    if (specs.isEmpty()) return emptyList()
    if (state.activeStage == DownloadStage.Completed) {
        return specs.map { QueueStageItem(it.label, QueueStageStatus.Completed) }
    }

    val currentIndex = specs.indexOfFirst { it.stage == state.activeStage }
    return specs.mapIndexed { index, spec ->
        val status = when {
            currentIndex < 0 -> QueueStageStatus.Pending
            index < currentIndex -> QueueStageStatus.Completed
            index == currentIndex -> QueueStageStatus.Current
            else -> QueueStageStatus.Pending
        }
        QueueStageItem(spec.label, status)
    }
}

internal fun queueStageItemsForUiTest(state: RuntimeDownloadState): List<QueueStageItem> = queueStageItems(state)

private fun queueProgressPresentation(state: RuntimeDownloadState): QueueProgressPresentation {
    val fraction = state.progressPercent?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
    val hasCurrentStage = queueStageItems(state).any { it.status == QueueStageStatus.Current }
    return QueueProgressPresentation(
        fraction = fraction,
        isIndeterminate = fraction == null && state.isDownloading && hasCurrentStage,
    )
}

internal fun queueProgressPresentationForUiTest(state: RuntimeDownloadState): QueueProgressPresentation =
    queueProgressPresentation(state)

private fun queueOverallProgressPercent(state: DownloadTaskState, currentStagePercent: Double?): Double? {
    if (state.stage == DownloadStage.Completed) return 100.0
    if (state.stage in setOf(DownloadStage.Failed, DownloadStage.Canceled, DownloadStage.Idle)) return null
    val specs = queueStageSpecs(state.request)
    if (specs.isEmpty()) return currentStagePercent
    if (state.stage == DownloadStage.Waiting) return 0.0
    val currentIndex = specs.indexOfFirst { it.stage == state.stage }
    if (currentIndex < 0) return currentStagePercent
    val currentStageFraction = ((currentStagePercent ?: 0.0).coerceIn(0.0, 100.0)) / 100.0
    return ((currentIndex + currentStageFraction) / specs.size) * 100.0
}

private fun queueCardStatus(state: RuntimeDownloadState): String {
    return when (state.downloadStatus) {
        "下载完成" -> "100%"
        "下载失败" -> "失败"
        "已取消" -> "取消"
        else -> "${(state.overallProgressPercent ?: state.progressPercent ?: 0.0).toInt()}%"
    }
}

internal fun queueCardStatusForUiTest(state: RuntimeDownloadState): String = queueCardStatus(state)

private fun queueCardMeta(state: RuntimeDownloadState): String {
    val downloaded = state.downloadedBytes?.let(::formatBytes) ?: "0 B"
    val total = state.totalBytes?.let(::formatBytes) ?: "未知大小"
    val outputSummary = when {
        state.subtitleOutputCount > 0 -> " · 媒体文件 + 独立字幕文件"
        state.outputPath.isNotBlank() -> " · 媒体文件"
        else -> ""
    }
    val outputPolicy = if (state.outputPath.isNotBlank()) {
        " · App 私有目录 · 导出名：标题+时间；重名加序号"
    } else {
        ""
    }
    return "$downloaded / $total$outputSummary$outputPolicy"
}

internal fun queueCardMetaForUiTest(state: RuntimeDownloadState): String = queueCardMeta(state)

private fun queueCardFormatBadge(state: RuntimeDownloadState): String = formatResolutionBadgeForRequest(state.activeRequest)

internal fun queueCardFormatBadgeForUiTest(state: RuntimeDownloadState): String = queueCardFormatBadge(state)

private fun queueCardAccent(
    state: RuntimeDownloadState,
    palette: YtdlAppPalette = DefaultPalette,
): Color {
    return when (state.downloadStatus) {
        "下载完成" -> palette.successGreen
        "下载失败", "已取消" -> palette.downloadAccent
        else -> palette.queueAccent
    }
}

private fun queueCardActions(state: RuntimeDownloadState): List<String> {
    if (!state.hasRealTask) return emptyList()
    return when (state.downloadStatus) {
        "下载完成", "下载失败", "已取消" -> emptyList()
        else -> listOf("取消")
    }
}

internal fun queueCardActionsForUiTest(state: RuntimeDownloadState): List<String> = queueCardActions(state)

private fun queueThumbnailTag(state: RuntimeDownloadState): String {
    return if (state.hasRealTask && state.thumbnailBitmap != null) {
        QueueThumbnailImageTag
    } else {
        QueueThumbnailPlaceholderTag
    }
}

internal fun queueThumbnailTagForUiTest(state: RuntimeDownloadState): String = queueThumbnailTag(state)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return "%.1f KB".format(kib)
    val mib = kib / 1024.0
    if (mib < 1024) return "%.1f MB".format(mib)
    return "%.2f GB".format(mib / 1024.0)
}

private fun settingsCookiesSubtitle(settings: AppSettings): String {
    val reference = settings.cookiesReference
        ?: return "未选择 · 仅保存文件引用，不保存内容"
    return "${reference.displayName ?: "cookies 文件"} · 仅保存引用"
}

private data class AppearanceOption(
    val id: String,
    val label: String,
)

private val ThemeModeOptions = listOf(
    AppearanceOption(AppearanceSettings.ThemeModeSystem, "跟随系统"),
    AppearanceOption(AppearanceSettings.ThemeModeLight, "浅色"),
    AppearanceOption(AppearanceSettings.ThemeModeDark, "深色"),
)

private fun selectedThemeModeIndex(settings: AppSettings): Int {
    val id = AppearanceSettings.normalizeThemeModeId(settings.themeModeId)
    return ThemeModeOptions.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
}

private fun selectedColorPresetIndex(settings: AppSettings): Int {
    val id = AppearanceSettings.normalizeColorPresetId(settings.colorPresetId)
    return ytdlColorPresets().indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
}

internal fun appearanceSummaryForUiTest(settings: AppSettings): String {
    val mode = ThemeModeOptions[selectedThemeModeIndex(settings)].label
    val preset = ytdlColorPresets()[selectedColorPresetIndex(settings)].label
    return "$preset · $mode"
}

private val HistoryFilterOptions = listOf("全部", "视频", "音频")

internal fun filterHistoryItemsForUiTest(
    historyItems: List<HistoryUiItem>,
    query: String,
    selectedFilterIndex: Int,
): List<HistoryUiItem> = filterHistoryItems(historyItems, query, selectedFilterIndex)

private fun filterHistoryItems(
    historyItems: List<HistoryUiItem>,
    query: String,
    selectedFilterIndex: Int,
): List<HistoryUiItem> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return historyItems.filter { item ->
        val searchable = listOf(item.title, item.meta, item.badge, item.formatBadge).joinToString(" ").lowercase(Locale.ROOT)
        val matchesQuery = normalizedQuery.isBlank() || searchable.contains(normalizedQuery)
        val matchesType = when (selectedFilterIndex) {
            1 -> !isAudioOnlyHistory(item)
            2 -> isAudioOnlyHistory(item)
            else -> true
        }
        matchesQuery && matchesType
    }
}

private fun isAudioOnlyHistory(item: HistoryUiItem): Boolean {
    val searchable = "${item.title} ${item.meta}".lowercase(Locale.ROOT)
    return searchable.contains("仅音频") || (searchable.contains("音频") && !searchable.contains("视频"))
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyPageItems(
    historyItems: List<HistoryUiItem>,
    historyQuery: String,
    selectedFilterIndex: Int,
    userMessage: String,
    onHistoryQueryChange: (String) -> Unit,
    onHistoryFilterChange: (Int) -> Unit,
    onOpen: (HistoryUiItem) -> Unit,
    onShare: (HistoryUiItem) -> Unit,
    onExport: (HistoryUiItem) -> Unit,
    onShareSubtitle: (HistoryUiItem) -> Unit,
    onExportSubtitle: (HistoryUiItem) -> Unit,
    onDelete: (HistoryUiItem) -> Unit,
) {
    val visibleItems = filterHistoryItems(historyItems, historyQuery, selectedFilterIndex)
    item {
        OutlinedTextField(
            value = historyQuery,
            onValueChange = onHistoryQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ytdl-history-search"),
            placeholder = { Text("搜索历史") },
            leadingIcon = { Text("⌕") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
    }
    item {
        SegmentedRow(
            HistoryFilterOptions,
            selectedIndex = selectedFilterIndex.coerceIn(HistoryFilterOptions.indices),
            accent = LocalYtdlAppPalette.current.historyAccent,
            testTagPrefix = "ytdl-history-filter",
            onSelected = onHistoryFilterChange,
        )
    }
    if (shouldShowRuntimeMessage(userMessage)) {
        item {
            RuntimeMessageCard(userMessage)
        }
    }
    if (visibleItems.isEmpty()) {
        item {
            AppCard(modifier = Modifier.testTag("ytdl-history-empty-card")) {
                val palette = LocalYtdlAppPalette.current
                Text(if (historyItems.isEmpty()) "暂无真实历史记录，完成下载后会显示" else "没有匹配的历史记录", color = palette.titleText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (historyItems.isEmpty()) "历史页已接入本地 Room 记录；当前数据库为空。" else "请调整搜索关键词或分类筛选。", color = palette.softText, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        items(visibleItems) { item ->
            HistoryCard(
                item = item,
                onOpen = { onOpen(item) },
                onShare = { onShare(item) },
                onExport = { onExport(item) },
                onShareSubtitle = { onShareSubtitle(item) },
                onExportSubtitle = { onExportSubtitle(item) },
                onDelete = { onDelete(item) },
                modifier = Modifier.testTag("ytdl-history-real-card"),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsPageItems(
    settings: AppSettings,
    notificationsAllowed: Boolean,
    notificationRuntimePermissionRequired: Boolean,
    onSelectCookies: () -> Unit,
    onRequestNotifications: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onColorPresetChange: (String) -> Unit,
) {
    item { SettingLineCard("默认保存位置", "App 私有目录 · 导出名：标题+时间；重名加序号", "▣", "›", LocalYtdlAppPalette.current.settingsAccent) }
    item {
        SettingLineCard(
            "Cookies 文件",
            settingsCookiesSubtitle(settings),
            "▤",
            "选择",
            LocalYtdlAppPalette.current.settingsAccent,
            modifier = Modifier
                .clickable(onClick = onSelectCookies)
                .testTag("ytdl-settings-cookies-picker"),
        )
    }
    item { SettingLineCard("解析器版本", settingsParserVersionLabel(), "◇", "›", Color(0xFFE7A600)) }
    item { SettingLineCard("媒体处理能力", settingsMediaProcessorLabel(), "⚙", "›", LocalYtdlAppPalette.current.settingsAccent, modifier = Modifier.testTag("ytdl-settings-media-processor")) }
    item {
        SettingLineCard(
            "通知权限",
            notificationPermissionSubtitle(notificationsAllowed, notificationRuntimePermissionRequired),
            "●",
            notificationPermissionTrailing(notificationsAllowed, notificationRuntimePermissionRequired),
            LocalYtdlAppPalette.current.formatAccent,
            modifier = Modifier
                .clickable(
                    enabled = notificationRuntimePermissionRequired && !notificationsAllowed,
                    onClick = onRequestNotifications,
                )
                .testTag("ytdl-settings-notification-permission"),
        )
    }
    item {
        val palette = LocalYtdlAppPalette.current
        AppCard(modifier = Modifier.testTag("ytdl-settings-privacy-legal")) {
            SectionTitle("隐私与授权说明")
            settingsPrivacyLegalLines().forEach { line ->
                Text("• $line", color = palette.softText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item { SettingLineCard("地址校验提示", "仅校验空地址、非法地址和非 http/https", "!", "›", LocalYtdlAppPalette.current.downloadAccent) }
    item {
        val palette = LocalYtdlAppPalette.current
        val presets = ytdlColorPresets()
        AppCard {
            SectionTitle("外观与颜色")
            SegmentedRow(
                options = ThemeModeOptions.map { it.label },
                selectedIndex = selectedThemeModeIndex(settings),
                accent = palette.settingsAccent,
                testTagPrefix = "ytdl-settings-theme-mode",
                onSelected = { index -> onThemeModeChange(ThemeModeOptions[index].id) },
            )
            Spacer(Modifier.height(10.dp))
            SegmentedRow(
                options = presets.map { it.label },
                selectedIndex = selectedColorPresetIndex(settings),
                accent = palette.settingsAccent,
                testTagPrefix = "ytdl-settings-color-preset",
                onSelected = { index -> onColorPresetChange(presets[index].id) },
            )
            Spacer(Modifier.height(10.dp))
            SettingLineCard(
                "颜色方案",
                appearanceSummaryForUiTest(settings),
                "▣",
                "›",
                palette.settingsAccent,
                inCard = false,
                modifier = Modifier.testTag("ytdl-settings-appearance-summary"),
            )
        }
    }
    item { Spacer(Modifier.height(SettingsAppearanceBottomBuffer)) }
    item { SettingLineCard("关于", "版本 1.0.0", "i", "›", Color(0xFF55606C)) }
}

@Composable
private fun SectionTitle(text: String) {
    val palette = LocalYtdlAppPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = palette.titleText,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalYtdlAppPalette.current
    Surface(
        modifier = modifier,
        color = palette.cardBackground,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderColor),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    val palette = LocalYtdlAppPalette.current
    Surface(color = palette.mutedCardBackground, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(label, color = palette.softText, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModeCard(icon: String, label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalYtdlAppPalette.current
    Surface(
        modifier = modifier.height(64.dp),
        color = if (selected) palette.downloadAccent else palette.cardBackground,
        contentColor = if (selected) Color.White else palette.neutralText,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, palette.borderColor),
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    accent: Color,
    testTagPrefix: String? = null,
    onSelected: ((Int) -> Unit)? = null,
) {
    val palette = LocalYtdlAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.segmentedBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onSelected != null) {
                            Modifier.clickable { onSelected(index) }
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("$testTagPrefix-$index")
                        } else {
                            Modifier
                        },
                    ),
                color = if (selected) accent else palette.cardBackground.copy(alpha = 0.7f),
                contentColor = if (selected) Color.White else palette.neutralText,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ResolutionRow(row: FormatResolutionRow, onSelect: () -> Unit) {
    val palette = LocalYtdlAppPalette.current
    val tagSuffix = row.height?.toString() ?: "auto"
    val badge = when {
        row.mergeRequired -> "需原生合并"
        row.direct -> "单文件"
        row.reason != null -> row.reason
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (row.selected) palette.formatAccent.copy(alpha = 0.11f) else Color.Transparent)
            .clickable(enabled = row.selectable, onClick = onSelect)
            .testTag("ytdl-format-row-$tagSuffix")
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (row.selected) "✓" else "○",
            color = if (row.selected) palette.formatAccent else palette.softText.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = row.label,
            modifier = Modifier.weight(1f),
            color = if (row.selectable) palette.titleText else palette.softText.copy(alpha = 0.55f),
            fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (badge != null) {
            Surface(color = palette.mutedCardBackground, shape = RoundedCornerShape(10.dp)) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = palette.softText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SettingLineCard(
    title: String,
    subtitle: String,
    leading: String,
    trailing: String,
    accent: Color? = null,
    inCard: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = LocalYtdlAppPalette.current
    val resolvedAccent = accent ?: palette.formatAccent
    val titleColor = if (enabled) palette.titleText else palette.softText.copy(alpha = 0.58f)
    val subtitleColor = if (enabled) palette.softText else palette.softText.copy(alpha = 0.46f)
    val leadingBackground = resolvedAccent.copy(alpha = if (enabled) 0.15f else 0.06f)
    val leadingColor = if (enabled) resolvedAccent else palette.softText.copy(alpha = 0.5f)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(leadingBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(leading, color = leadingColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = titleColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = subtitleColor, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, color = subtitleColor, style = MaterialTheme.typography.titleSmall)
        }
    }

    if (inCard) {
        AppCard(modifier = modifier, content = content)
    } else {
        Column(modifier = modifier, content = content)
    }
}

@Composable
private fun QueueStageStrip(stageItems: List<QueueStageItem>) {
    val palette = LocalYtdlAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ytdl-queue-stage-strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stageItems.forEachIndexed { index, item ->
            val completed = item.status == QueueStageStatus.Completed
            val current = item.status == QueueStageStatus.Current
            Text(
                text = if (completed) "${item.label} ✓" else item.label,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ytdl-queue-stage-$index"),
                color = when {
                    completed -> palette.successGreen
                    current -> palette.softText
                    else -> palette.softText.copy(alpha = 0.55f)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (current || completed) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (current) TextDecoration.Underline else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QueueCard(
    title: String,
    subtitle: String,
    progress: QueueProgressPresentation,
    status: String,
    meta: String,
    formatBadge: String,
    stageItems: List<QueueStageItem>,
    accent: Color,
    actions: List<String>,
    modifier: Modifier = Modifier,
    thumbnailBitmap: Bitmap? = null,
    onCancel: (() -> Unit)? = null,
) {
    val palette = LocalYtdlAppPalette.current
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = "队列任务缩略图",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(QueueThumbnailImageTag),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(QueueThumbnailPlaceholderTag)
                        .background(Brush.linearGradient(listOf(Color(0xFF74B9E7), Color(0xFFE8C27D)))),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = palette.softText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (stageItems.isNotEmpty()) {
                    QueueStageStrip(stageItems)
                }
                if (stageItems.isNotEmpty() || progress.fraction != null || progress.isIndeterminate) {
                    YtdlQueueProgressBar(
                        progress = progress,
                        accent = accent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(meta, color = palette.softText, style = MaterialTheme.typography.labelSmall)
                if (actions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.forEach { action ->
                            if (action == "取消" && onCancel != null) {
                                Box(
                                    modifier = Modifier
                                        .testTag("ytdl-queue-cancel-action")
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(onClick = onCancel)
                                        .defaultMinSize(minWidth = 56.dp, minHeight = 36.dp)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        action,
                                        color = palette.downloadAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                Text(
                                    action,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = palette.neutralText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                CardPillBadge(
                    text = status,
                    accent = accent,
                    tag = "ytdl-queue-status-badge",
                )
                if (formatBadge.isNotBlank()) {
                    CardPillBadge(
                        text = formatBadge,
                        accent = palette.formatAccent,
                        tag = "ytdl-queue-format-badge",
                    )
                }
            }
        }
    }
}

@Composable
private fun YtdlQueueProgressBar(
    progress: QueueProgressPresentation,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val palette = LocalYtdlAppPalette.current
    val fraction = when {
        progress.fraction != null -> progress.fraction.coerceIn(0f, 1f)
        progress.isIndeterminate -> animatedQueueIndeterminateFraction()
        else -> 0f
    }
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(palette.borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(5.dp))
                .background(accent),
        )
    }
}

@Composable
private fun animatedQueueIndeterminateFraction(): Float {
    val transition = rememberInfiniteTransition(label = "queue-indeterminate-progress")
    val indeterminateFraction by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queue-indeterminate-progress-width",
    )
    return indeterminateFraction
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryCard(
    item: HistoryUiItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onShareSubtitle: () -> Unit,
    onExportSubtitle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalYtdlAppPalette.current
    var thumbnailBitmap by remember(item.thumbnailUrl) { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(item.thumbnailUrl) {
        if (item.thumbnailUrl.isNullOrBlank()) {
            thumbnailBitmap = null
            return@DisposableEffect onDispose { }
        }
        val closeable = HistoryThumbnailLoader.load(item.thumbnailUrl.orEmpty()) { bitmap ->
            thumbnailBitmap = bitmap
        }
        onDispose { closeable.close() }
    }
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap!!.asImageBitmap(),
                    contentDescription = "历史记录缩略图",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(HistoryThumbnailImageTag),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(HistoryThumbnailPlaceholderTag)
                        .background(Brush.linearGradient(listOf(Color(0xFF97C9E8), Color(0xFF8EBE8A)))),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.meta, color = palette.softText, style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    historyActionLabels(item).forEach { action ->
                        val callback = when (action) {
                            "打开" -> onOpen
                            "分享" -> onShare
                            "导出" -> onExport
                            "分享字幕" -> onShareSubtitle
                            "导出字幕" -> onExportSubtitle
                            else -> onDelete
                        }
                        Text(
                            action,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = callback)
                                .testTag("ytdl-history-action-${item.id}-$action")
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            color = if (action == "删除") palette.downloadAccent else palette.neutralText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                CardPillBadge(
                    text = item.badge,
                    accent = historyStatusBadgeAccent(item, palette),
                    tag = "ytdl-history-status-badge",
                )
                if (item.formatBadge.isNotBlank()) {
                    CardPillBadge(
                        text = item.formatBadge,
                        accent = palette.formatAccent,
                        tag = "ytdl-history-format-badge",
                    )
                }
            }
        }
    }
}

@Composable
private fun CardPillBadge(
    text: String,
    accent: Color,
    tag: String,
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 26.dp)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun historyStatusBadgeAccent(
    item: HistoryUiItem,
    palette: YtdlAppPalette,
): Color = historyStatusBadgeAccent(item.badge, palette)

internal fun historyStatusBadgeAccentForUiTest(
    badge: String,
    palette: YtdlAppPalette,
): Color = historyStatusBadgeAccent(badge, palette)

private fun historyStatusBadgeAccent(
    badge: String,
    palette: YtdlAppPalette,
): Color {
    return when (badge) {
        "完成" -> palette.successGreen
        "失败", "取消" -> HistoryFailureRed
        else -> palette.neutralText
    }
}
