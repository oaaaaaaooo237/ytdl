package com.garyapp.ytdl.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val DefaultPalette = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetReferenceV3)

private const val QueueThumbnailImageTag = "ytdl-queue-thumbnail-image"
private const val QueueThumbnailPlaceholderTag = "ytdl-queue-thumbnail-placeholder"

internal val YtdlColorPresetIdKey = SemanticsPropertyKey<String>("YtdlColorPresetId")
internal var SemanticsPropertyReceiver.ytdlColorPresetId by YtdlColorPresetIdKey
internal val YtdlSettingsAccentArgbKey = SemanticsPropertyKey<String>("YtdlSettingsAccentArgb")
internal var SemanticsPropertyReceiver.ytdlSettingsAccentArgb by YtdlSettingsAccentArgbKey

internal fun colorArgbHexForUiTest(color: Color): String = String.format(Locale.US, "#%08X", color.toArgb())

@Immutable
data class YtdlDestination(
    val route: String,
    val label: String,
    val title: String,
    val summary: String,
    val icon: String,
    val accent: Color,
    val reservedEntries: List<String> = emptyList(),
)

internal data class RuntimeDownloadState(
    val url: String = "",
    val analysis: VideoAnalysis? = null,
    val formatSelection: FormatSelection = FormatSelection(),
    val appliedFormatSelection: FormatSelection = FormatSelection(),
    val selectedSubtitles: List<SubtitleInfo> = emptyList(),
    val thumbnailBitmap: Bitmap? = null,
    val thumbnailStatus: String = "",
    val isAnalyzing: Boolean = false,
    val isDownloading: Boolean = false,
    val userMessage: String = "等待输入公开视频页面地址。",
    val progressPercent: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val downloadStatus: String = "",
    val outputPath: String = "",
    val outputBytes: Long = 0L,
) {
    val hasRealTask: Boolean
        get() = downloadStatus.isNotBlank() || progressPercent != null || outputPath.isNotBlank() || outputBytes > 0L || isDownloading
}

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
        icon = "↓",
        accent = palette.downloadAccent,
    ),
    YtdlDestination(
        route = "formats",
        label = "格式",
        title = "格式",
        summary = "设置下载格式偏好，不做强制转码承诺。",
        icon = "▦",
        accent = palette.formatAccent,
    ),
    YtdlDestination(
        route = "queue",
        label = "队列",
        title = "队列",
        summary = "查看进行中、等待、完成和失败任务。",
        icon = "≡",
        accent = palette.queueAccent,
    ),
    YtdlDestination(
        route = "history",
        label = "历史",
        title = "历史",
        summary = "搜索、打开、分享或删除本地记录。",
        icon = "◷",
        accent = palette.historyAccent,
    ),
    YtdlDestination(
        route = "settings",
        label = "设置",
        title = "设置",
        summary = "管理保存位置、解析器、媒体处理、隐私和外观。",
        icon = "⚙",
        accent = palette.settingsAccent,
        reservedEntries = listOf("外观与颜色"),
    ),
)

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

        val outputDir = File(context.cacheDir, "gui-downloads").apply { mkdirs() }
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

    fun outputForHistoryItem(item: HistoryUiItem): Result<ExportController.AppPrivateOutput> {
        return ExportController.discoverAppPrivateOutputUri(
            appPrivateUri = item.outputUri,
            appPrivateRoot = File(context.cacheDir, "gui-downloads"),
        )
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
            runtimeState = runtimeState.copy(userMessage = error.message.orEmpty().ifBlank { "历史记录没有可打开的本地输出。" })
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
            runtimeState = runtimeState.copy(userMessage = error.message.orEmpty().ifBlank { "历史记录没有可分享的本地输出。" })
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

    fun exportHistoryItem(item: HistoryUiItem) {
        val output = outputForHistoryItem(item).getOrElse { error ->
            runtimeState = runtimeState.copy(userMessage = error.message.orEmpty().ifBlank { "历史记录没有可导出的本地输出。" })
            return
        }
        pendingExportOutput = output
        exportLauncher.launch(ExportController.createDocumentIntent(output))
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            testTagsAsResourceId = true
                            ytdlColorPresetId = themeConfig.colorPreset.id
                            ytdlSettingsAccentArgb = colorArgbHexForUiTest(palette.settingsAccent)
                        }
                        .testTag("ytdl-screen-${selected.route}"),
                    contentPadding = PaddingValues(start = 18.dp, top = 26.dp, end = 18.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                val summary = formatSelectionSummary(runtimeState.analysis, runtimeState.formatSelection)
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
                            onHistoryQueryChange = { historyQuery = it },
                            onHistoryFilterChange = { historyFilterIndex = it },
                            onOpen = ::openHistoryItem,
                            onShare = ::shareHistoryItem,
                            onExport = ::exportHistoryItem,
                            onDelete = ::deleteHistoryItem,
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
        userMessage = "分析完成，可以开始下载。",
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
    if (state.stage == DownloadStage.Completed && mediaOutput == null) {
        return copy(
            isDownloading = false,
            userMessage = "下载结果无有效输出，请重试。",
            progressPercent = null,
            downloadedBytes = null,
            totalBytes = null,
            downloadStatus = userVisibleDownloadStatus(DownloadStage.Failed),
            outputPath = "",
            outputBytes = 0L,
        )
    }
    if (state.stage == DownloadStage.Idle || (state.request == null && state.outputs.isEmpty() && state.stage == DownloadStage.Failed)) {
        return copy(
            isDownloading = false,
            userMessage = "等待输入公开视频页面地址。",
            progressPercent = null,
            downloadedBytes = null,
            totalBytes = null,
            downloadStatus = "",
            outputPath = "",
            outputBytes = 0L,
        )
    }
    return copy(
        isDownloading = state.stage !in TerminalDownloadStages,
        userMessage = when (state.stage) {
            DownloadStage.Failed -> "下载失败：${state.errorMessage.orEmpty().ifBlank { "请检查网络或授权状态。" }}"
            DownloadStage.Canceled -> "下载已取消。"
            DownloadStage.Completed -> "下载完成：${mediaOutput?.path?.let { File(it).name }.orEmpty().ifBlank { "输出文件" }}"
            else -> "正在$statusText..."
        },
        downloadStatus = statusText,
        progressPercent = when (state.stage) {
            DownloadStage.Completed -> 100.0
            else -> progress?.percent
        },
        downloadedBytes = progress?.downloadedBytes ?: mediaOutput?.bytesWritten,
        totalBytes = progress?.totalBytes ?: mediaOutput?.bytesWritten,
        outputPath = mediaOutput?.path.orEmpty(),
        outputBytes = mediaOutput?.bytesWritten ?: 0L,
    )
}

private val TerminalDownloadStages = setOf(
    DownloadStage.Completed,
    DownloadStage.Failed,
    DownloadStage.Canceled,
    DownloadStage.Idle,
)

private fun loadThumbnailBitmap(thumbnailUrl: String): Bitmap? {
    return runCatching {
        val connection = (URL(thumbnailUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "YTDL-Android/0.1")
            setRequestProperty("Accept", "image/*")
        }
        connection.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
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
    Surface(
        color = palette.bottomBarBackground,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = destination.route == selectedRoute
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .testTag("ytdl-tab-${destination.route}")
                        .clickable { onSelected(destination.route) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 28.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) destination.accent.copy(alpha = 0.16f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = destination.icon,
                            color = if (selected) destination.accent else palette.neutralText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ytdl-url-input"),
                placeholder = { Text("粘贴公开视频页面地址") },
                leadingIcon = { Text("🔗") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Button(
                onClick = onAnalyze,
                enabled = !state.isAnalyzing,
                modifier = Modifier.testTag("ytdl-analyze-button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.downloadAccent),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(if (state.isAnalyzing) "分析中" else "分析", fontWeight = FontWeight.Bold)
            }
        }
    }
    item { DownloadPreviewCard(state) }
    item {
        val palette = LocalYtdlAppPalette.current
        Surface(
            color = if (state.userMessage.contains("失败")) {
                palette.downloadAccent.copy(alpha = 0.11f)
            } else {
                palette.formatAccent.copy(alpha = 0.11f)
            },
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (state.userMessage.contains("失败")) {
                    palette.downloadAccent.copy(alpha = 0.35f)
                } else {
                    palette.formatAccent.copy(alpha = 0.35f)
                },
            ),
        ) {
            Text(
                text = state.userMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ytdl-runtime-message")
                    .padding(13.dp),
                color = if (state.userMessage.contains("失败")) palette.downloadAccent else palette.formatAccent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    item { SettingLineCard(title = "保存位置", subtitle = "App 私有目录", leading = "□", trailing = "›") }
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

internal fun downloadModeSelectionsForUiTest(state: RuntimeDownloadState): Map<FormatMode, Boolean> = downloadModeSelections(state)

private fun downloadModeSelections(state: RuntimeDownloadState): Map<FormatMode, Boolean> {
    return FormatMode.entries.associateWith { mode -> mode == state.appliedFormatSelection.mode }
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

internal fun downloadPreviewFormatSummaryForUiTest(state: RuntimeDownloadState): String = downloadPreviewFormatSummary(state)

private fun downloadPreviewFormatSummary(state: RuntimeDownloadState): String {
    return formatSelectionSummary(state.analysis, state.appliedFormatSelection)
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
        val newSelection = if (selectedSubtitles.isEmpty() && hasSubtitles) {
            listOf(subtitles.first())
        } else {
            emptyList()
        }
        SettingLineCard(
            "字幕",
            subtitleSelectionLabel(analysis, selectedSubtitles),
            "▾",
            if (hasSubtitles) "切换" else "无",
            modifier = Modifier
                .clickable(enabled = hasSubtitles) { onSubtitleSelectionChange(newSelection) }
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
                Text("实际下载：${formatSelectionSummary(analysis, selection)}", color = palette.formatAccent, fontWeight = FontWeight.Bold)
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
    if (state.hasRealTask) {
        item { SectionTitle("真实任务（1）") }
        item {
            val palette = LocalYtdlAppPalette.current
            val progress = ((state.progressPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
            val downloaded = state.downloadedBytes?.let(::formatBytes) ?: "0 B"
            val total = state.totalBytes?.let(::formatBytes) ?: "未知大小"
            val title = state.analysis?.title?.takeIf { it.isNotBlank() } ?: "真实下载任务"
            Box(modifier = Modifier.testTag("ytdl-real-queue-card")) {
                QueueCard(
                    title = title,
                    subtitle = queueCardSubtitle(state),
                    progress = progress,
                    status = queueCardStatus(state),
                    meta = "$downloaded / $total${if (state.outputPath.isNotBlank()) " · ${File(state.outputPath).name}" else ""}",
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
                progress = 0f,
                status = "待开始",
                meta = "这里不会显示假进度",
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

private fun queueCardStatus(state: RuntimeDownloadState): String {
    return when (state.downloadStatus) {
        "下载完成" -> "100%"
        "下载失败" -> "失败"
        "已取消" -> "取消"
        else -> "${(state.progressPercent ?: 0.0).toInt()}%"
    }
}

internal fun queueCardStatusForUiTest(state: RuntimeDownloadState): String = queueCardStatus(state)

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
        val searchable = listOf(item.title, item.meta, item.badge).joinToString(" ").lowercase(Locale.ROOT)
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
    onHistoryQueryChange: (String) -> Unit,
    onHistoryFilterChange: (Int) -> Unit,
    onOpen: (HistoryUiItem) -> Unit,
    onShare: (HistoryUiItem) -> Unit,
    onExport: (HistoryUiItem) -> Unit,
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
    item { SettingLineCard("默认保存位置", "App 私有目录", "▣", "›", LocalYtdlAppPalette.current.settingsAccent) }
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
            SettingLineCard("颜色方案", appearanceSummaryForUiTest(settings), "▣", "›", palette.settingsAccent, inCard = false)
        }
    }
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
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderColor),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    val palette = LocalYtdlAppPalette.current
    Surface(color = palette.mutedCardBackground, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, color = palette.softText, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModeCard(icon: String, label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalYtdlAppPalette.current
    Surface(
        modifier = modifier.height(72.dp),
        color = if (selected) palette.downloadAccent else palette.cardBackground,
        contentColor = if (selected) Color.White else palette.neutralText,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, palette.borderColor),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
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
            .clip(RoundedCornerShape(18.dp))
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
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(vertical = 10.dp),
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
            .padding(horizontal = 8.dp, vertical = 10.dp),
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
    modifier: Modifier = Modifier,
) {
    val palette = LocalYtdlAppPalette.current
    val resolvedAccent = accent ?: palette.formatAccent
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(resolvedAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(leading, color = resolvedAccent, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = palette.softText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, color = palette.softText, style = MaterialTheme.typography.titleMedium)
        }
    }

    if (inCard) {
        AppCard(modifier = modifier, content = content)
    } else {
        Column(modifier = modifier, content = content)
    }
}

@Composable
private fun QueueCard(
    title: String,
    subtitle: String,
    progress: Float,
    status: String,
    meta: String,
    accent: Color,
    actions: List<String>,
    modifier: Modifier = Modifier,
    thumbnailBitmap: Bitmap? = null,
    onCancel: (() -> Unit)? = null,
) {
    val palette = LocalYtdlAppPalette.current
    AppCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = "队列任务缩略图",
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(QueueThumbnailImageTag),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag(QueueThumbnailPlaceholderTag)
                        .background(Brush.linearGradient(listOf(Color(0xFF74B9E7), Color(0xFFE8C27D)))),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = palette.softText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = accent,
                        trackColor = palette.borderColor,
                    )
                }
                Text(meta, color = palette.softText, style = MaterialTheme.typography.labelSmall)
                if (actions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.forEach { action ->
                            val modifier = if (action == "取消" && onCancel != null) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onCancel)
                                    .testTag("ytdl-queue-cancel-action")
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            } else {
                                Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            }
                            Text(
                                action,
                                modifier = modifier,
                                color = if (action == "取消") palette.downloadAccent else palette.neutralText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Surface(color = accent.copy(alpha = 0.12f), shape = CircleShape) {
                Text(status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryUiItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalYtdlAppPalette.current
    AppCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF97C9E8), Color(0xFF8EBE8A)))),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(color = palette.successGreen.copy(alpha = 0.14f), shape = RoundedCornerShape(9.dp)) {
                        Text(item.badge, color = palette.successGreen, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(item.meta, color = palette.softText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    historyActionLabels(item).forEach { action ->
                        val callback = when (action) {
                            "打开" -> onOpen
                            "分享" -> onShare
                            "导出" -> onExport
                            else -> onDelete
                        }
                        Text(
                            action,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = callback)
                                .testTag("ytdl-history-action-$action")
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            color = if (action == "删除") palette.downloadAccent else palette.neutralText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
