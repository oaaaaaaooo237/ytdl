package com.garyapp.ytdl.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.download.DownloadOutputKind
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val AppBackground = Color(0xFFFFFCF7)
private val BottomBarBackground = Color(0xFFF8F0FB)
private val CardBackground = Color(0xFFFFFFFF)
private val MutedCardBackground = Color(0xFFF6F5F0)
private val BorderColor = Color(0xFFE8E2DA)
private val DownloadAccent = Color(0xFFFF5B55)
private val FormatAccent = Color(0xFF138F88)
private val QueueAccent = Color(0xFFFF7A1A)
private val HistoryAccent = Color(0xFF7357C8)
private val SettingsAccent = Color(0xFF2E86DE)
private val SuccessGreen = Color(0xFF18A15F)
private val SoftText = Color(0xFF5E625C)

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

fun ytdlNavigationDestinations(): List<YtdlDestination> = listOf(
    YtdlDestination(
        route = "download",
        label = "下载",
        title = "视频地址提取器",
        summary = "粘贴公开视频页面地址，分析后再开始保存。",
        icon = "↓",
        accent = DownloadAccent,
    ),
    YtdlDestination(
        route = "formats",
        label = "格式",
        title = "格式",
        summary = "设置下载格式偏好，不做强制转码承诺。",
        icon = "▦",
        accent = FormatAccent,
    ),
    YtdlDestination(
        route = "queue",
        label = "队列",
        title = "队列",
        summary = "查看进行中、等待、完成和失败任务。",
        icon = "≡",
        accent = QueueAccent,
    ),
    YtdlDestination(
        route = "history",
        label = "历史",
        title = "历史",
        summary = "搜索、打开、分享或删除本地记录。",
        icon = "◷",
        accent = HistoryAccent,
    ),
    YtdlDestination(
        route = "settings",
        label = "设置",
        title = "设置",
        summary = "管理保存位置、解析器、媒体处理、隐私和外观。",
        icon = "⚙",
        accent = SettingsAccent,
        reservedEntries = listOf("外观与颜色"),
    ),
)

fun ytdlVisibleContentLabels(): Map<String, List<String>> = mapOf(
    "download" to listOf("粘贴公开视频页面地址", "分析", "等待真实分析", "保存位置", "下载模式", "开始下载"),
    "formats" to listOf("视频+音频", "仅音频", "仅视频", "分辨率", "1080p", "需合并", "容器格式", "字幕", "本阶段默认不下载"),
    "queue" to listOf("下载进行中", "当前阶段", "等待真实任务", "暂无真实下载任务", "最近任务已完成", "最近任务失败", "最近任务已取消", "下载视频", "下载音频", "原生合并", "已取消"),
    "history" to listOf("搜索历史", "全部", "视频", "音频", "暂无真实历史记录", "完成下载后会显示"),
    "settings" to listOf("默认保存位置", "Cookies 文件", "解析器版本", "媒体处理能力", "通知权限", "前台验收待完成", "隐私与授权说明", "外观与颜色", "Codex 风格", "MVP2"),
)

@Composable
fun YtdlApp() {
    val context = LocalContext.current
    val destinations = ytdlNavigationDestinations()
    var selectedRoute by rememberSaveable { mutableStateOf(destinations.first().route) }
    var runtimeState by remember { mutableStateOf(RuntimeDownloadState()) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember { YtdlpBridge() }
    val selected = destinations.firstOrNull { it.route == selectedRoute } ?: destinations.first()

    DisposableEffect(Unit) {
        val subscription = DownloadCoordinator.addListener { state ->
            mainHandler.post {
                runtimeState = runtimeState.withPipelineState(state)
            }
        }
        onDispose { subscription.close() }
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
            thumbnailBitmap = null,
            thumbnailStatus = "",
        )
        Thread {
            val result = bridge.analyze(url)
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
        val url = runtimeState.url.trim()
        val requestResult = buildAppliedDownloadRequest(
            url = url,
            analysis = runtimeState.analysis,
            appliedSelection = runtimeState.appliedFormatSelection,
        )
        if (requestResult.isFailure) {
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
                runtimeState.withPipelineState(DownloadTaskState.idle()).copy(
                    isDownloading = false,
                    userMessage = "启动前台下载失败：${error.message.orEmpty().ifBlank { "请检查系统权限。" }}",
                )
            },
        )
    }

    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            YtdlBottomBar(
                destinations = destinations,
                selectedRoute = selected.route,
                onSelected = { selectedRoute = it },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-screen-${selected.route}"),
                contentPadding = PaddingValues(start = 18.dp, top = 26.dp, end = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { PageHeader(selected) }
                when (selected.route) {
                    "download" -> downloadPageItems(
                        state = runtimeState,
                        onUrlChange = {
                            runtimeState = runtimeState.copy(
                                url = it,
                                analysis = null,
                                formatSelection = FormatSelection(),
                                appliedFormatSelection = FormatSelection(),
                                thumbnailBitmap = null,
                                thumbnailStatus = "",
                            )
                        },
                        onAnalyze = ::analyzeCurrentUrl,
                        onStartDownload = ::startRealDownload,
                    )
                    "formats" -> formatPageItems(
                        analysis = runtimeState.analysis,
                        selection = runtimeState.formatSelection,
                        onSelectionChange = { selection ->
                            runtimeState = runtimeState.copy(formatSelection = selection)
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
                    "queue" -> queuePageItems(runtimeState)
                    "history" -> historyPageItems()
                    "settings" -> settingsPageItems()
                }
            }
            if (selected.route == "queue") {
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

internal fun RuntimeDownloadState.withAnalysisForUiTest(analysis: VideoAnalysis): RuntimeDownloadState = withAnalysisResult(analysis)

private fun RuntimeDownloadState.withAnalysisResult(analysis: VideoAnalysis): RuntimeDownloadState {
    val freshSelection = defaultFormatSelection(analysis)
    return copy(
        analysis = analysis,
        formatSelection = freshSelection,
        appliedFormatSelection = freshSelection,
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

@Composable
private fun YtdlBottomBar(
    destinations: List<YtdlDestination>,
    selectedRoute: String,
    onSelected: (String) -> Unit,
) {
    Surface(
        color = BottomBarBackground,
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
                            color = if (selected) destination.accent else Color(0xFF3D403B),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = destination.label,
                        color = if (selected) destination.accent else Color(0xFF3D403B),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = destination.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF161914),
                fontWeight = FontWeight.Bold,
            )
            if (destination.route == "download") {
                Text(
                    text = "?",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, BorderColor, CircleShape)
                        .padding(top = 2.dp),
                    textAlign = TextAlign.Center,
                    color = SoftText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = destination.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = SoftText,
        )
    }
}

@Composable
private fun QueueScrollIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("⌃", color = Color(0xFFB7AFA5), style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 250.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE3DED7)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF8C847A)),
            )
        }
        Text("⌄", color = Color(0xFFB7AFA5), style = MaterialTheme.typography.labelSmall)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.downloadPageItems(
    state: RuntimeDownloadState,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onStartDownload: () -> Unit,
) {
    val modeSelections = downloadModeSelections(state)
    item {
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
                colors = ButtonDefaults.buttonColors(containerColor = DownloadAccent),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(if (state.isAnalyzing) "分析中" else "分析", fontWeight = FontWeight.Bold)
            }
        }
    }
    item { DownloadPreviewCard(state) }
    item {
        Surface(
            color = if (state.userMessage.contains("失败")) Color(0xFFFFF0EE) else Color(0xFFEAF8F5),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (state.userMessage.contains("失败")) Color(0xFFFFC8C2) else Color(0xFFCBE9E3),
            ),
        ) {
            Text(
                text = state.userMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ytdl-runtime-message")
                    .padding(13.dp),
                color = if (state.userMessage.contains("失败")) DownloadAccent else FormatAccent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    item { SettingLineCard(title = "保存位置", subtitle = "App 私有目录", leading = "□", trailing = "›") }
    item {
        SectionTitle("下载模式")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ModeCard("♫", "仅音频", selected = modeSelections[FormatMode.AudioOnly] == true, modifier = Modifier.weight(1f))
            ModeCard("▣", "视频+音频", selected = modeSelections[FormatMode.VideoAndAudio] == true, modifier = Modifier.weight(1f))
            ModeCard("▤", "仅视频", selected = modeSelections[FormatMode.VideoOnly] == true, modifier = Modifier.weight(1f))
        }
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = true, onCheckedChange = {})
            Text("我确认有权保存该内容", style = MaterialTheme.typography.bodyMedium)
        }
    }
    item {
        Button(
            onClick = onStartDownload,
            enabled = !state.isDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ytdl-download-start"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DownloadAccent),
            contentPadding = PaddingValues(vertical = 13.dp),
        ) {
            Text(if (state.isDownloading) "↓  下载中" else "↓  开始下载", fontWeight = FontWeight.Bold)
        }
    }
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
        Text(if (analysis == null) "请输入地址并分析" else "分析完成 · 公开授权内容由用户确认", color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
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
    onSelectionChange: (FormatSelection) -> Unit,
    onApplySelection: () -> Unit,
) {
    item {
        SegmentedRow(
            options = listOf(FormatMode.VideoAndAudio, FormatMode.AudioOnly, FormatMode.VideoOnly).map { it.label },
            selectedIndex = selection.mode.ordinal,
            accent = FormatAccent,
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
            AppCard(modifier = Modifier.testTag("ytdl-format-empty-card")) {
                Text("请先分析视频", color = SoftText, fontWeight = FontWeight.Bold)
                Text("格式页会根据当前视频真实提供的格式生成可选项。", color = SoftText, style = MaterialTheme.typography.bodySmall)
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
    item { SettingLineCard("字幕", subtitleSelectionLabel(), "▾", "›") }
    item {
        Surface(
            modifier = Modifier.testTag("ytdl-format-summary"),
            color = Color(0xFFEAF8F5),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBE9E3)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("实际下载：${formatSelectionSummary(analysis, selection)}", color = FormatAccent, fontWeight = FontWeight.Bold)
                Text("开始下载会按当前格式选择进入真实任务队列。", color = SoftText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item {
        Button(
            onClick = onApplySelection,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ytdl-format-apply"),
            colors = ButtonDefaults.buttonColors(containerColor = FormatAccent),
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

private fun androidx.compose.foundation.lazy.LazyListScope.queuePageItems(state: RuntimeDownloadState) {
    item {
        Surface(color = Color(0xFFFFF2DE), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(queueHeaderTitle(state), color = Color(0xFF9A4A00), fontWeight = FontWeight.Bold)
                Text(
                    queueHeaderSummary(state),
                    color = SoftText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (state.hasRealTask) {
        item { SectionTitle("真实任务（1）") }
        item {
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
                    accent = queueCardAccent(state),
                )
            }
        }
    } else {
        item { SectionTitle("等待真实任务") }
        item {
            QueueCard(
                title = "尚未开始真实下载",
                subtitle = "请在下载页输入地址并点击开始下载",
                progress = 0f,
                status = "待开始",
                meta = "这里不会显示假进度",
                accent = QueueAccent,
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

private fun queueCardAccent(state: RuntimeDownloadState): Color {
    return when (state.downloadStatus) {
        "下载完成" -> SuccessGreen
        "下载失败", "已取消" -> DownloadAccent
        else -> QueueAccent
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return "%.1f KB".format(kib)
    val mib = kib / 1024.0
    if (mib < 1024) return "%.1f MB".format(mib)
    return "%.2f GB".format(mib / 1024.0)
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyPageItems() {
    item {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索历史") },
            leadingIcon = { Text("⌕") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
    }
    item { SegmentedRow(listOf("全部", "视频", "音频"), selectedIndex = 0, accent = HistoryAccent) }
    item {
        AppCard(modifier = Modifier.testTag("ytdl-history-empty-card")) {
            Text("暂无真实历史记录，完成下载后会显示", color = Color(0xFF181B17), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("M7/M9 接入真实历史持久化前，这里不会展示样片记录。", color = SoftText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsPageItems() {
    item { SettingLineCard("默认保存位置", "App 私有目录", "▣", "›", SettingsAccent) }
    item { SettingLineCard("Cookies 文件", "仅保存文件引用，不保存内容", "▤", "›", SettingsAccent) }
    item { SettingLineCard("解析器版本", settingsParserVersionLabel(), "◇", "›", Color(0xFFE7A600)) }
    item { SettingLineCard("媒体处理能力", settingsMediaProcessorLabel(), "⚙", "›", SettingsAccent, modifier = Modifier.testTag("ytdl-settings-media-processor")) }
    item { SettingLineCard("通知权限", "待系统确认 · 前台验收待完成", "●", "›", FormatAccent) }
    item { SettingLineCard("隐私与授权说明", "查看说明", "◆", "›", SuccessGreen) }
    item { SettingLineCard("不适用网站提示", "该地址不适合 Play 版处理", "!", "›", DownloadAccent) }
    item {
        AppCard {
            SectionTitle("外观与颜色")
            SegmentedRow(listOf("跟随系统", "浅色", "深色"), selectedIndex = 0, accent = SettingsAccent)
            Spacer(Modifier.height(10.dp))
            SettingLineCard("颜色方案", "基准图配色 · Codex 风格可选", "▣", "›", SettingsAccent, inCard = false)
        }
    }
    item { SettingLineCard("关于", "版本 1.0.0", "i", "›", Color(0xFF55606C)) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF181B17),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        color = CardBackground,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
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
    Surface(color = MutedCardBackground, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, color = SoftText, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModeCard(icon: String, label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(72.dp),
        color = if (selected) DownloadAccent else CardBackground,
        contentColor = if (selected) Color.White else Color(0xFF2C302B),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF1EEE8))
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
                color = if (selected) accent else Color.White.copy(alpha = 0.7f),
                contentColor = if (selected) Color.White else Color(0xFF292C28),
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
            .background(if (row.selected) Color(0xFFEAF8F5) else Color.Transparent)
            .clickable(enabled = row.selectable, onClick = onSelect)
            .testTag("ytdl-format-row-$tagSuffix")
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (row.selected) "✓" else "○",
            color = if (row.selected) FormatAccent else Color(0xFF979C96),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = row.label,
            modifier = Modifier.weight(1f),
            color = if (row.selectable) Color(0xFF191D1A) else Color(0xFFB7B1AA),
            fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (badge != null) {
            Surface(color = Color(0xFFEDE9E3), shape = RoundedCornerShape(10.dp)) {
                Text(badge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = SoftText, style = MaterialTheme.typography.labelSmall)
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
    accent: Color = FormatAccent,
    inCard: Boolean = true,
    modifier: Modifier = Modifier,
) {
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
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(leading, color = accent, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = SoftText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, color = SoftText, style = MaterialTheme.typography.titleMedium)
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
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF74B9E7), Color(0xFFE8C27D)))),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = SoftText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = accent,
                        trackColor = Color(0xFFEDE8E0),
                    )
                }
                Text(meta, color = SoftText, style = MaterialTheme.typography.labelSmall)
                if (progress in 0.01f..0.99f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("暂停", color = Color(0xFF263447), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("取消", color = DownloadAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Surface(color = accent.copy(alpha = 0.12f), shape = CircleShape) {
                Text(status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class HistoryItem(val title: String, val meta: String, val badge: String)

@Composable
private fun HistoryCard(item: HistoryItem, modifier: Modifier = Modifier) {
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
                    Surface(color = Color(0xFFDFF5E7), shape = RoundedCornerShape(9.dp)) {
                        Text(item.badge, color = SuccessGreen, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(item.meta, color = SoftText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("打开", color = Color(0xFF263447), style = MaterialTheme.typography.labelMedium)
                    Text("分享", color = Color(0xFF263447), style = MaterialTheme.typography.labelMedium)
                    Text("删除", color = DownloadAccent, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
