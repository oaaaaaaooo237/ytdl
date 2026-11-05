package com.garyapp.ytdl.ui

import android.Manifest
import com.garyapp.ytdl.core.settings.cookiesForUrl
import android.content.ClipboardManager
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
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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
import com.garyapp.ytdl.BuildConfig
import com.garyapp.ytdl.R
import com.garyapp.ytdl.core.files.FileKind
import com.garyapp.ytdl.core.files.FileProbeResult
import com.garyapp.ytdl.core.files.HttpFileProbe
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.core.settings.CookiesReference as SettingsCookiesReference
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.storage.StorageTargets
import com.garyapp.ytdl.core.ytdlp.AnalysisErrorCategory
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.core.ytdlp.YtdlpAnalysisException
import com.garyapp.ytdl.core.ytdlp.ParserUpdateChecker
import com.garyapp.ytdl.core.ytdlp.ParserUpdateCoordinator
import com.garyapp.ytdl.core.ytdlp.ParserUpdateResult
import com.garyapp.ytdl.core.ytdlp.ParserUpdateState
import com.garyapp.ytdl.core.ytdlp.ParserRuntimeState
import com.garyapp.ytdl.core.ytdlp.ParserVersionControl
import com.garyapp.ytdl.core.ytdlp.ParserVersionInfo
import com.garyapp.ytdl.core.ytdlp.ParserVersionManager
import com.garyapp.ytdl.data.YtdlDatabaseProvider
import com.garyapp.ytdl.download.DownloadCoordinator
import com.garyapp.ytdl.download.DownloadFailureMessages
import com.garyapp.ytdl.download.IdleDownloadActionResult
import com.garyapp.ytdl.download.DownloadOutputKind
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.download.ResumeTaskItem
import com.garyapp.ytdl.download.DownloadService
import com.garyapp.ytdl.download.FileRetryDraftStore
import com.garyapp.ytdl.download.NotificationController
import com.garyapp.ytdl.download.RetryDownloadDraft
import com.garyapp.ytdl.download.RetryDraftStore
import com.garyapp.ytdl.download.AndroidHistorySafDocuments
import com.garyapp.ytdl.download.HistoryFileDeleteFailure
import com.garyapp.ytdl.download.HistoryFileDeletion
import com.garyapp.ytdl.download.deleteHistoryWithRetryPayload
import com.garyapp.ytdl.storage.ExportController
import com.garyapp.ytdl.storage.CacheClearResult
import com.garyapp.ytdl.storage.CacheStats
import com.garyapp.ytdl.storage.DownloadCacheControl
import com.garyapp.ytdl.storage.PrivateDownloadCache
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
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val DefaultPalette = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetMorandi)
private val BottomBarGestureBuffer = 4.dp
private val HistoryFailureRed = Color(0xFFFF5B63)

private const val DefaultRuntimeMessage = "等待输入下载链接。"
private const val AnalysisCompleteRuntimeMessage = "分析完成，可以开始下载。"
private const val QueueThumbnailImageTag = "ytdl-queue-thumbnail-image"
private const val QueueThumbnailPlaceholderTag = "ytdl-queue-thumbnail-placeholder"
private const val HistoryThumbnailImageTag = "ytdl-history-thumbnail-image"
private const val HistoryThumbnailPlaceholderTag = "ytdl-history-thumbnail-placeholder"
private const val HistoryThumbnailTargetPx = 160
private const val HistoryThumbnailCacheMaxItems = 64
private const val ThumbnailDecodeMaxBytes = 2 * 1024 * 1024
private val CardPillBadgeMinWidth = 60.dp
private val CardPillBadgeMinHeight = 28.dp
private val SettingsAppearanceBottomBuffer = 96.dp
private val ProcessParserUpdateCoordinator = ParserUpdateCoordinator(
    checker = ParserUpdateChecker.forCurrentVersion(
        currentVersionProvider = { ParserRuntimeState.activeVersion },
    ),
    executor = Executor { command ->
        Thread(command, "ytdl-parser-update").apply { isDaemon = true }.start()
    },
)
private val ProcessDownloadCacheExecutor = Executor { command ->
    Thread(command, "ytdl-cache-settings").apply { isDaemon = true }.start()
}
private val ProcessParserVersionExecutor = Executors.newSingleThreadExecutor { command ->
    Thread(command, "ytdl-parser-version").apply { isDaemon = true }
}
private object ProcessParserVersionOperations {
    private var coordinator: ParserVersionOperationCoordinator? = null

    @Synchronized
    fun get(context: Context): ParserVersionOperationCoordinator {
        return coordinator ?: ParserVersionOperationCoordinator(
            control = ParserVersionManager.fromContext(context.applicationContext),
            executor = ProcessParserVersionExecutor,
        ).also { coordinator = it }
    }
}
private val ImmediateParserUiStateExecutor = Executor(Runnable::run)

private fun createDownloadCacheControl(context: Context): DownloadCacheControl {
    val database = YtdlDatabaseProvider.get(context.applicationContext)
    return PrivateDownloadCache(
        rootDirectory = File(context.filesDir, "gui-downloads"),
        queueDao = database.queueDao(),
        historyDao = database.historyDao(),
    )
}

private data class SettingsExplanation(
    val title: String,
    val body: String,
)

private val MediaProcessorExplanation = SettingsExplanation(
    title = "媒体处理能力",
    body = "当前使用 Android 原生 MediaExtractor + MediaMuxer，将已下载的分离视频流和音频流封装合并。" +
        "它不进行转码。" +
        "源轨道或容器不兼容时可能无法合并。",
)


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

private val ClipboardIcon = tabIcon("Clipboard") {
    moveTo(7f, 4f)
    lineTo(9f, 4f)
    lineTo(9f, 2f)
    lineTo(15f, 2f)
    lineTo(15f, 4f)
    lineTo(19f, 4f)
    lineTo(19f, 22f)
    lineTo(5f, 22f)
    lineTo(5f, 4f)
    close()
    moveTo(7f, 6f)
    lineTo(7f, 20f)
    lineTo(17f, 20f)
    lineTo(17f, 6f)
    lineTo(15f, 6f)
    lineTo(15f, 8f)
    lineTo(9f, 8f)
    lineTo(9f, 6f)
    close()
    moveTo(10f, 4f)
    lineTo(10f, 6f)
    lineTo(14f, 6f)
    lineTo(14f, 4f)
    close()
    moveTo(9f, 11f)
    lineTo(15f, 11f)
    lineTo(15f, 13f)
    lineTo(9f, 13f)
    close()
    moveTo(9f, 15f)
    lineTo(15f, 15f)
    lineTo(15f, 17f)
    lineTo(9f, 17f)
    close()
}

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

private val StorageCapacityIcon = tabIcon("StorageCapacity") {
    moveTo(4f, 4f)
    lineTo(20f, 4f)
    lineTo(22f, 11f)
    lineTo(2f, 11f)
    close()
    moveTo(2f, 11f)
    lineTo(4f, 11f)
    lineTo(4f, 20f)
    lineTo(2f, 20f)
    close()
    moveTo(20f, 11f)
    lineTo(22f, 11f)
    lineTo(22f, 20f)
    lineTo(20f, 20f)
    close()
    moveTo(2f, 18f)
    lineTo(22f, 18f)
    lineTo(22f, 20f)
    lineTo(2f, 20f)
    close()
    moveTo(6f, 13f)
    lineTo(14f, 13f)
    lineTo(14f, 15f)
    lineTo(6f, 15f)
    close()
    moveTo(17f, 13f)
    lineTo(20f, 13f)
    lineTo(20f, 16f)
    lineTo(17f, 16f)
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

private val SearchIcon = tabIcon("Search") {
    moveTo(10.5f, 5f)
    lineTo(15.5f, 10f)
    lineTo(10.5f, 15f)
    lineTo(5.5f, 10f)
    close()
    moveTo(15f, 14f)
    lineTo(20f, 19f)
    lineTo(18.7f, 20.3f)
    lineTo(13.7f, 15.3f)
    close()
}

private val HistoryFilterIcon = tabIcon("HistoryFilter") {
    moveTo(4f, 6f)
    lineTo(20f, 6f)
    lineTo(14f, 13f)
    lineTo(14f, 18f)
    lineTo(10f, 20f)
    lineTo(10f, 13f)
    close()
}

private val HistoryOpenIcon = tabIcon("HistoryOpen") {
    moveTo(4f, 7f)
    lineTo(10f, 7f)
    lineTo(12f, 9f)
    lineTo(20f, 9f)
    lineTo(20f, 18f)
    lineTo(4f, 18f)
    close()
    moveTo(6f, 11f)
    lineTo(6f, 16f)
    lineTo(18f, 16f)
    lineTo(18f, 11f)
    close()
}

private val HistoryShareIcon = tabIcon("HistoryShare") {
    moveTo(18f, 5f)
    lineTo(21f, 8f)
    lineTo(18f, 11f)
    lineTo(18f, 9f)
    lineTo(12f, 9f)
    lineTo(12f, 7f)
    lineTo(18f, 7f)
    close()
    moveTo(6f, 10f)
    lineTo(12f, 14f)
    lineTo(18f, 10f)
    lineTo(18f, 13f)
    lineTo(12f, 17f)
    lineTo(6f, 13f)
    close()
}

private val HistoryRetryIcon = tabIcon("HistoryRetry") {
    moveTo(5f, 5f)
    lineTo(15f, 5f)
    lineTo(15f, 2f)
    lineTo(21f, 8f)
    lineTo(15f, 14f)
    lineTo(15f, 11f)
    lineTo(5f, 11f)
    close()
    moveTo(19f, 13f)
    lineTo(9f, 13f)
    lineTo(9f, 10f)
    lineTo(3f, 16f)
    lineTo(9f, 22f)
    lineTo(9f, 19f)
    lineTo(19f, 19f)
    close()
}

private val HistoryDeleteIcon = tabIcon("HistoryDelete") {
    moveTo(8f, 5f)
    lineTo(16f, 5f)
    lineTo(16f, 7f)
    lineTo(20f, 7f)
    lineTo(20f, 9f)
    lineTo(18f, 9f)
    lineTo(17f, 20f)
    lineTo(7f, 20f)
    lineTo(6f, 9f)
    lineTo(4f, 9f)
    lineTo(4f, 7f)
    lineTo(8f, 7f)
    close()
    moveTo(9f, 10f)
    lineTo(11f, 10f)
    lineTo(11f, 18f)
    lineTo(9f, 18f)
    close()
    moveTo(13f, 10f)
    lineTo(15f, 10f)
    lineTo(15f, 18f)
    lineTo(13f, 18f)
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
internal const val TaskTitleMarqueeIterations = Int.MAX_VALUE
internal const val TaskTitleMarqueeVelocityDp = 30
internal const val TaskTitleMaxLines = 1
internal const val TaskTitleSoftWrap = false
internal const val TaskProgressIconSizeDp = 14

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

internal const val UrlSelectionDeleteActionId = 0x7954646c

internal class UrlSelectionActionModeCallback(
    private val editText: EditText,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        if (menu.findItem(UrlSelectionDeleteActionId) == null) {
            menu.add(Menu.NONE, UrlSelectionDeleteActionId, Menu.NONE, "删除")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != UrlSelectionDeleteActionId) return false
        val start = minOf(editText.selectionStart, editText.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editText.selectionStart, editText.selectionEnd).coerceAtLeast(0)
        if (start < end) {
            editText.text.delete(start, end)
        } else {
            editText.text.clear()
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit
}

internal fun EditText.installUrlSelectionActionModeCallbacks() {
    val selectionActions = UrlSelectionActionModeCallback(this)
    customSelectionActionModeCallback = selectionActions
    customInsertionActionModeCallback = selectionActions
}

internal data class HistoryOutputTarget(
    val uri: String,
    val mimeType: String,
)

private fun historyContentOutputTarget(
    rawUri: String,
    resolverMimeType: String?,
): HistoryOutputTarget? {
    val normalizedUri = rawUri.trim()
    val uri = runCatching { URI(normalizedUri) }.getOrNull() ?: return null
    if (!uri.scheme.equals("content", ignoreCase = true) || uri.rawAuthority.isNullOrBlank()) return null
    return HistoryOutputTarget(
        uri = normalizedUri,
        mimeType = historyOutputMimeType(normalizedUri, resolverMimeType),
    )
}

private fun historyOutputMimeType(rawUri: String, resolverMimeType: String?): String {
    resolverMimeType?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val rawLeaf = runCatching { URI(rawUri).rawPath.orEmpty().substringAfterLast('/') }.getOrDefault("")
    val extension = runCatching { URLDecoder.decode(rawLeaf, Charsets.UTF_8.name()) }.getOrDefault(rawLeaf)
        .substringAfterLast('.', "")
        .lowercase(Locale.ROOT)
    return when (extension) {
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "vtt" -> "text/vtt"
        "srt" -> "application/x-subrip"
        "txt" -> "text/plain"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}

internal fun historyContentOutputTargetForUiTest(
    rawUri: String,
    resolverMimeType: String?,
): HistoryOutputTarget? = historyContentOutputTarget(rawUri, resolverMimeType)

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
    val fileSelection: FileDownloadSelection? = null,
    val selectedSubtitles: List<SubtitleInfo> = emptyList(),
    val isStarting: Boolean = false,
    val activeRequest: DownloadRequest? = null,
    val activeStage: DownloadStage = DownloadStage.Idle,
    val formatSelection: FormatSelection = FormatSelection(),
    val appliedFormatSelection: FormatSelection = FormatSelection(),
    val thumbnailBitmap: Bitmap? = null,
    val thumbnailStatus: String = "",
    val isAnalyzing: Boolean = false,
    val isDownloading: Boolean = false,
    val userMessage: String = DefaultRuntimeMessage,
    val progressPercent: Double? = null,
    val overallProgressPercent: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Double? = null,
    val downloadStatus: String = "",
    val outputPath: String = "",
    val outputBytes: Long = 0L,
) {
    val hasRealTask: Boolean
        get() = downloadStatus.isNotBlank() ||
            progressPercent != null ||
            outputPath.isNotBlank() ||
            outputBytes > 0L ||
            isDownloading
}

internal fun RuntimeDownloadState.withFormatSelection(selection: FormatSelection): RuntimeDownloadState {
    return copy(
        formatSelection = selection,
        appliedFormatSelection = selection,
    )
}

private fun parserUpdateStatusLabel(state: ParserUpdateState): String {
    return when {
        state.isRunning -> "正在检查解析器更新..."
        state.result == ParserUpdateResult.AlreadyLatest -> "已是最新版本"
        state.result is ParserUpdateResult.UpdateAvailable -> "发现新版本 ${state.result.latestVersion}"
        state.result == ParserUpdateResult.Failed -> "检查失败，请稍后重试"
        else -> "尚未检查"
    }
}

private fun parserVersionSummary(
    currentProcessVersion: String,
    versions: List<ParserVersionInfo>,
): String {
    val selectedVersion = versions.firstOrNull { it.isSelected }?.version ?: currentProcessVersion
    return if (selectedVersion == currentProcessVersion) {
        "本进程：$currentProcessVersion"
    } else {
        "本进程：$currentProcessVersion；下次启动：$selectedVersion"
    }
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

internal data class QueueProgressMeta(
    val speed: String,
    val transferred: String,
    val contentDescription: String,
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
        title = "新建下载",
        summary = "识别链接后，显示对应的下载选项",
        icon = DownloadTabIcon,
        accent = palette.downloadAccent,
    ),
    YtdlDestination(
        route = "tasks",
        label = "任务",
        title = "全部任务",
        summary = "查看当前、等待和历史任务。",
        icon = QueueTabIcon,
        accent = palette.queueAccent,
    ),
    YtdlDestination(
        route = "settings",
        label = "设置",
        title = "设置",
        summary = "管理保存位置、解析器和外观。",
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
    "download" to listOf("粘贴媒体链接或文件直链", "识别", "下载内容", "视频+音频", "视频", "音频", "分辨率", "视频编码", "容器格式", "字幕", "保存文件名", "重名处理", "保存位置", "开始下载"),
    "tasks" to listOf("当前任务", "等待中", "搜索历史", "全部", "视频", "音频", "暂无历史记录", "完成下载后会显示"),
    "settings" to listOf("保存位置", "恢复默认路径", "Cookies 文件", "解析器版本", "媒体处理能力", "通知权限", "下载仍在应用内显示进度", "App 私有目录", "外观与颜色", "Codex 风格", "开源声明"),
)

@Composable
internal fun YtdlApp(
    parserUpdateCoordinator: ParserUpdateCoordinator = ProcessParserUpdateCoordinator,
    parserUiStateDeliveryExecutor: Executor = ImmediateParserUiStateExecutor,
    parserVersionControl: ParserVersionControl? = null,
    parserVersionExecutor: Executor = ProcessParserVersionExecutor,
    parserVersionOperations: ParserVersionOperationCoordinator? = null,
    currentProcessParserVersion: String = ParserRuntimeState.activeVersion,
    downloadCacheFactory: (Context) -> DownloadCacheControl = ::createDownloadCacheControl,
    downloadCacheExecutor: Executor = ProcessDownloadCacheExecutor,
    analysisProvider: ((String, String?) -> Result<VideoAnalysis>)? = null,
    fileProbeProvider: ((String) -> FileProbeResult)? = null,
    downloadStarter: (Context, DownloadRequest, File) -> Result<DownloadTaskState> =
        DownloadCoordinator::startForegroundDownload,
    retryDraftStoreOverride: RetryDraftStore? = null,
    historyItemsProvider: (() -> List<HistoryUiItem>)? = null,
) {
    val context = LocalContext.current
    val versionOperations = remember(parserVersionOperations, parserVersionControl, parserVersionExecutor) {
        parserVersionOperations
            ?: parserVersionControl?.let { ParserVersionOperationCoordinator(it, parserVersionExecutor) }
            ?: ProcessParserVersionOperations.get(context.applicationContext)
    }
    var selectedRoute by rememberSaveable { mutableStateOf("download") }
    val currentSelectedRoute by rememberUpdatedState(selectedRoute)
    var runtimeState by remember { mutableStateOf(RuntimeDownloadState()) }
    val analysisRequestRevision = remember { AtomicLong(0L) }
    var showAdvancedFormats by rememberSaveable { mutableStateOf(false) }
    var confirmExtensionChange by remember { mutableStateOf(false) }
    var activeDownloadStates by remember { mutableStateOf(emptyMap<String, DownloadTaskState>()) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyFilterIndex by rememberSaveable { mutableStateOf(0) }
    val settingsRepository = remember { SettingsRepository.fromContext(context.applicationContext) }
    val retryDraftStore = remember(retryDraftStoreOverride) {
        retryDraftStoreOverride ?: FileRetryDraftStore.fromContext(context.applicationContext)
    }
    var appSettings by remember { mutableStateOf(settingsRepository.getSettings()) }
    var showSiteCookies by remember { mutableStateOf(false) }
    var pendingCookiesDomain by rememberSaveable { mutableStateOf("") }
    var pendingCookiesName by rememberSaveable { mutableStateOf("") }
    var historyItems by remember { mutableStateOf(emptyList<HistoryUiItem>()) }
    var pendingDownloadRequests by remember { mutableStateOf(emptyList<DownloadRequest>()) }
    var savedDownloadTasks by remember { mutableStateOf(emptyList<ResumeTaskItem>()) }
    var pendingDeleteHistoryItem by remember { mutableStateOf<HistoryUiItem?>(null) }
    var parserUpdateRevision by remember { mutableStateOf(-1L) }
    var parserUpdateState by remember { mutableStateOf(ParserUpdateState()) }
    val initialParserOperationState = remember(versionOperations) { versionOperations.currentState() }
    val initialParserVersions = initialParserOperationState.versions
    var parserVersions by remember { mutableStateOf(initialParserVersions) }
    var parserVersionSubtitle by remember {
        mutableStateOf(parserVersionSummary(currentProcessParserVersion, initialParserVersions))
    }
    var pendingParserUpdateVersion by remember { mutableStateOf<String?>(null) }
    var showParserStatusDialog by remember { mutableStateOf(false) }
    var parserOperationMessage by remember { mutableStateOf(initialParserOperationState.message) }
    var parserOperationRunning by remember { mutableStateOf(initialParserOperationState.isRunning) }
    var pendingDeleteParserVersion by remember { mutableStateOf<String?>(null) }
    var settingsExplanation by remember { mutableStateOf<SettingsExplanation?>(null) }
    var showOpenSource by remember { mutableStateOf(false) }
    var downloadCacheStats by remember { mutableStateOf(CacheStats(0, 0)) }
    var showDownloadCacheConfirmation by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val downloadCache = remember { downloadCacheFactory(context.applicationContext) }
    val downloadCacheRefreshGeneration = remember { AtomicLong(0) }
    val bridge = remember { YtdlpBridge() }
    val notificationController = remember { NotificationController(context.applicationContext) }
    val notificationRuntimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsAllowed by remember { mutableStateOf(notificationController.canPostNotifications()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshHistory() {
        if (historyItemsProvider != null) {
            historyItems = historyItemsProvider()
            return
        }
        Thread {
            val rows = YtdlDatabaseProvider.get(context.applicationContext)
                .historyDao()
                .listRecent(50)
            val items = historyUiItemsFromRows(rows, retryDraftStore::isAvailable)
            mainHandler.post {
                historyItems = items
            }
        }.start()
    }

    fun refreshDownloadCacheStats() {
        val generation = downloadCacheRefreshGeneration.incrementAndGet()
        downloadCacheExecutor.execute {
            val stats = runCatching(downloadCache::inspect).getOrDefault(CacheStats(0, 0))
            mainHandler.post {
                if (generation == downloadCacheRefreshGeneration.get()) {
                    downloadCacheStats = stats
                }
            }
        }
    }

    fun selectRoute(route: String) {
        selectedRoute = route
        if (route == "tasks") {
            refreshHistory()
        } else if (route == "settings") {
            refreshDownloadCacheStats()
        }
    }

    fun persistStorageTarget(target: StorageTarget) {
        storagePermissionUriToRelease(appSettings.defaultStorageTarget, target)?.let { treeUri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        appSettings = settingsRepository.setDefaultStorageTarget(target)
    }

    fun requestParserUpdateCheck(manual: Boolean) {
        if (manual) {
            parserUpdateCoordinator.requestManualCheck()
        } else {
            parserUpdateCoordinator.requestStartupCheck()
        }
    }

    fun applyParserUpdateState(state: ParserUpdateState) {
        parserUpdateState = state
        parserVersionSubtitle = when {
            state.isRunning && state.manualResultRequested -> "正在检查解析器更新..."
            state.isRunning -> parserVersionSummary(currentProcessParserVersion, parserVersions)
            state.result == ParserUpdateResult.AlreadyLatest && state.manualResultRequested -> {
                "已是最新版本：$currentProcessParserVersion"
            }
            state.result is ParserUpdateResult.UpdateAvailable -> {
                "发现新版本：${state.result.latestVersion}"
            }
            state.result == ParserUpdateResult.Failed && state.manualResultRequested -> {
                "检查失败，请稍后重试"
            }
            else -> parserVersionSummary(currentProcessParserVersion, parserVersions)
        }
        pendingParserUpdateVersion = state.promptVersion
    }

    fun dismissParserUpdatePrompt(version: String) {
        pendingParserUpdateVersion = null
        parserUpdateCoordinator.dismissUpdatePrompt(version)
    }

    fun selectParserVersion(version: String) {
        versionOperations.select(version)
    }

    fun downloadParserVersion(expectedVersion: String?) {
        versionOperations.download(expectedVersion)
    }

    fun deleteParserVersion(version: String) {
        versionOperations.delete(version)
    }

    fun clearDownloadCache() {
        downloadCacheExecutor.execute {
            val result = runCatching {
                when (val cleanup = DownloadCoordinator.runWhenIdle(downloadCache::clear)) {
                    IdleDownloadActionResult.ActiveDownload -> CacheClearResult.BlockedByActiveDownload
                    is IdleDownloadActionResult.Executed -> cleanup.value
                }
            }
            when (result.getOrNull()) {
                is CacheClearResult.Success,
                is CacheClearResult.Incomplete -> refreshDownloadCacheStats()
                else -> Unit
            }
            mainHandler.post {
                settingsExplanation = result.fold(
                    onSuccess = { clearResult ->
                        when (clearResult) {
                            CacheClearResult.BlockedByActiveDownload -> SettingsExplanation(
                                title = "无法清理缓存",
                                body = "当前有下载任务正在运行。为避免删除任务仍在使用的文件，请等待下载结束后再清理。",
                            )
                            is CacheClearResult.Success -> SettingsExplanation(
                                title = "清理完成",
                                body = "已释放 ${formatBytes(clearResult.freedBytes)}，删除 ${clearResult.deletedFileCount} 个文件。",
                            )
                            is CacheClearResult.Incomplete -> SettingsExplanation(
                                title = "部分临时文件未能清理",
                                body = "已释放 ${formatBytes(clearResult.freedBytes)}，删除 ${clearResult.deletedFileCount} 个文件；" +
                                    "仍有 ${formatBytes(clearResult.remainingBytes)}、${clearResult.remainingFileCount} 个临时文件，请稍后重试。",
                            )
                        }
                    },
                    onFailure = {
                        SettingsExplanation(
                            title = "清理失败",
                            body = "无法清理 App 私有临时缓存，请稍后重试。",
                        )
                    },
                )
            }
        }
    }

    val cookiesPicker = rememberLauncherForActivityResult(CookiesDocumentPicker()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val reference = SettingsCookiesReference.fromUserReference(
            reference = uri.toString(),
            displayName = displayNameForUri(context, uri, "cookies 文件"),
        )
        if (reference == null) {
            runtimeState = runtimeState.copy(userMessage = "cookies 文件引用无效，请重新选择 cookies.txt。")
        } else {
            appSettings = if (pendingCookiesDomain.isBlank()) settingsRepository.setCookiesReference(reference)
            else settingsRepository.setSiteCookies(pendingCookiesName, pendingCookiesDomain, reference)
            runtimeState = runtimeState.copy(userMessage = "已选择 cookies 文件。")
        }
    }

    val siteLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val domain = com.garyapp.ytdl.core.settings.CookieSites.normalizeDomain(data.getStringExtra("domain").orEmpty())
            val reference = SettingsCookiesReference.fromUserReference(data.getStringExtra("reference").orEmpty(), "App 内登录")
            if (domain != null && domain == pendingCookiesDomain && reference != null) {
                appSettings = settingsRepository.setSiteCookies(pendingCookiesName, domain, reference)
                runtimeState = runtimeState.copy(userMessage = "已保存该网站登录状态。")
            }
        }
    }

    val storageTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val permissionResult = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, permissionFlags)
        }
        val target = StorageTargets.sanitizeDefault(
            StorageTarget.SafTree(
                treeUri = uri.toString(),
                displayName = displayNameForUri(context, uri, "所选文件夹"),
            ),
        )
        if (permissionResult.isFailure || target !is StorageTarget.SafTree) {
            runtimeState = runtimeState.copy(userMessage = "无法保存该文件夹授权，请重新选择保存位置。")
        } else {
            persistStorageTarget(target)
            runtimeState = runtimeState.copy(userMessage = "已选择保存位置：${StorageTargets.displayName(target)}。")
        }
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
        refreshDownloadCacheStats()
        var disposed = false
        val versionOperationSubscription = versionOperations.addListener { state ->
            mainHandler.post {
                if (!disposed) {
                    parserVersions = state.versions
                    parserVersionSubtitle = parserVersionSummary(currentProcessParserVersion, state.versions)
                    parserOperationRunning = state.isRunning
                    parserOperationMessage = state.message
                    if (state.explanationTitle != null && state.message != null) {
                        settingsExplanation = SettingsExplanation(state.explanationTitle, state.message)
                    }
                }
            }
        }
        val parserSubscription = parserUpdateCoordinator.addVersionedListener { snapshot ->
            parserUiStateDeliveryExecutor.execute {
                mainHandler.post {
                    if (!disposed && snapshot.revision > parserUpdateRevision) {
                        parserUpdateRevision = snapshot.revision
                        applyParserUpdateState(snapshot.state)
                    }
                }
            }
        }
        requestParserUpdateCheck(manual = false)
        val subscription = DownloadCoordinator.addListener { state ->
            mainHandler.post {
                runtimeState = runtimeState.withPipelineState(state)
                if (state.stage in TerminalDownloadStages) {
                    refreshDownloadCacheStats()
                }
                if (state.stage in TerminalDownloadStages && currentSelectedRoute == "tasks") {
                    refreshHistory()
                }
            }
        }
        val activeSubscription = DownloadCoordinator.addActiveListener { states ->
            mainHandler.post { if (!disposed) activeDownloadStates = states }
        }
        val pendingSubscription = DownloadCoordinator.addPendingListener { requests ->
            mainHandler.post {
                pendingDownloadRequests = requests
            }
        }
        val savedSubscription = DownloadCoordinator.addSavedListener { tasks ->
            mainHandler.post { if (!disposed) savedDownloadTasks = tasks }
        }
        Thread { runCatching { DownloadCoordinator.restore(context.applicationContext) } }.start()
        onDispose {
            disposed = true
            versionOperationSubscription.close()
            parserSubscription.close()
            subscription.close()
            activeSubscription.close()
            pendingSubscription.close()
            analysisRequestRevision.incrementAndGet()
            savedSubscription.close()
        }
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

    fun loadThumbnail(thumbnailUrl: String, requestRevision: Long) {
        Thread {
            val bitmap = loadThumbnailBitmap(thumbnailUrl)
            mainHandler.post {
                if (analysisRequestRevision.get() == requestRevision && runtimeState.analysis?.thumbnailUrl == thumbnailUrl) {
                    runtimeState = runtimeState.copy(
                        thumbnailBitmap = bitmap,
                        thumbnailStatus = if (bitmap != null) "预览图已加载" else "预览图加载失败",
                    )
                }
            }
        }.start()
    }

    fun replaceUrl(updatedUrl: String) {
        if (updatedUrl == runtimeState.url) return
        analysisRequestRevision.incrementAndGet()
        confirmExtensionChange = false
        showAdvancedFormats = false
        runtimeState = runtimeState.withNewUrl(updatedUrl)
    }

    fun pasteDownloadUrl() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val pasted = runCatching { clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim() }.getOrNull()
        if (pasted.isNullOrBlank()) {
            runtimeState = runtimeState.copy(userMessage = "剪贴板中没有链接文本。")
        } else {
            replaceUrl(pasted)
        }
    }

    fun analyzeUrl(
        requestedUrl: String,
        retryDraft: RetryDownloadDraft? = null,
    ) {
        val url = requestedUrl.trim()
        val policy = UrlPolicy.evaluate(url)
        if (!policy.isAllowed) {
            val error = if (url.isBlank()) "请先输入下载链接。" else "请输入有效的 HTTP 或 HTTPS 下载链接。"
            runtimeState = runtimeState.copy(userMessage = error)
            return
        }
        val requestRevision = analysisRequestRevision.incrementAndGet()
        val settings = appSettings
        confirmExtensionChange = false
        showAdvancedFormats = false
        runtimeState = runtimeState.withNewUrl(url).copy(
            isAnalyzing = true,
            userMessage = DefaultRuntimeMessage,
        )
        Thread {
            val retryFileRoute = retryDraft?.route as? DownloadRoute.HttpFile
            val probe = if (retryDraft == null || retryFileRoute != null) {
                when {
                    fileProbeProvider != null -> fileProbeProvider(url)
                    analysisProvider == null -> HttpFileProbe().probe(url)
                    else -> null
                }
            } else null
            val probedFile = (probe as? FileProbeResult.FileFound)?.metadata
            val fileSelection = probedFile?.takeUnless { it.isStreamManifest() }?.let { metadata ->
                FileDownloadSelection(
                    metadata = metadata,
                    fileName = retryFileRoute?.fileName ?: metadata.fileName,
                    conflictPolicy = retryFileRoute?.conflictPolicy ?: settings.fileConflictPolicy,
                )
            }
            if (fileSelection != null && (retryFileRoute != null || fileSelection.metadata.kind == FileKind.Other)) {
                mainHandler.post {
                    if (analysisRequestRevision.get() == requestRevision) {
                        runtimeState = runtimeState.withFileRecognition(fileSelection)
                    }
                }
                return@Thread
            }
            if (analysisRequestRevision.get() != requestRevision) return@Thread
            if (retryFileRoute != null) {
                mainHandler.post {
                    if (analysisRequestRevision.get() == requestRevision) {
                        runtimeState = runtimeState.copy(isAnalyzing = false, userMessage = "无法重新识别原文件，请检查链接。")
                    }
                }
                return@Thread
            }
            val temporaryCookies = prepareTemporaryCookiesForDownload(
                settingsReference = settings.cookiesForUrl(url),
                context = context.applicationContext,
                taskId = "analyze-$requestRevision-${System.nanoTime()}",
            ).getOrElse {
                mainHandler.post {
                    if (analysisRequestRevision.get() == requestRevision) {
                        runtimeState = runtimeState.copy(
                            isAnalyzing = false,
                            userMessage = "Cookies 文件读取失败，请重新选择文件。",
                        )
                    }
                }
                return@Thread
            }
            val result = try {
                analysisProvider?.invoke(url, temporaryCookies?.file?.absolutePath)
                    ?: bridge.analyze(url, temporaryCookies?.file?.absolutePath)
            } finally {
                temporaryCookies?.delete()
            }
            mainHandler.post {
                if (analysisRequestRevision.get() != requestRevision) return@post
                runtimeState = result.fold(
                    onSuccess = { analysis ->
                        if (fileSelection != null && FormatMode.entries.none { isFormatModeAvailable(analysis, it) }) {
                            return@fold runtimeState.withFileRecognition(fileSelection)
                        }
                        val analyzed = runtimeState.withAnalysisResult(analysis)
                        if (retryDraft == null) {
                            analyzed
                        } else {
                            val restoredSelection = retryFormatSelection(analysis, retryDraft)
                            selectedRoute = "download"
                            showAdvancedFormats = true
                            analyzed.copy(
                                formatSelection = restoredSelection,
                                appliedFormatSelection = restoredSelection,
                                userMessage = "原地址已重新识别，请确认下载格式。",
                            )
                        }
                    },
                    onFailure = { error ->
                        val category = (error as? YtdlpAnalysisException)?.category
                        if (fileSelection != null && category in setOf(AnalysisErrorCategory.Unsupported, AnalysisErrorCategory.Parser)) {
                            runtimeState.withFileRecognition(fileSelection)
                        } else {
                            runtimeState.copy(isAnalyzing = false, userMessage = analysisFailureMessage(error))
                        }
                    },
                )
                runtimeState.analysis?.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { loadThumbnail(it, requestRevision) }
            }
        }.start()
    }

    fun startRealDownload(extensionConfirmed: Boolean = false) {
        if (!canStartDownload(runtimeState)) return
        val captured = runtimeState
        val fileSelection = captured.fileSelection
        if (fileSelection?.extensionChanged == true && !extensionConfirmed) {
            confirmExtensionChange = true
            return
        }
        val settings = appSettings
        val target = settings.defaultStorageTarget
        val revision = analysisRequestRevision.get()
        runtimeState = runtimeState.copy(isStarting = true, userMessage = DefaultRuntimeMessage)
        Thread {
            val url = captured.url.trim()
            val selectedCookies = if (fileSelection == null) settings.cookiesForUrl(url) else null
            val cookiesResult = prepareTemporaryCookiesForDownload(
                settingsReference = selectedCookies,
                context = context.applicationContext,
                taskId = "download-${System.nanoTime()}",
            )
            val temporaryCookies = cookiesResult.getOrNull()
            val requestResult = if (fileSelection != null) {
                runCatching { requireNotNull(fileSelection.request(target)) }
            } else {
                cookiesResult.mapCatching {
                    buildAppliedDownloadRequest(
                        url = url,
                        analysis = captured.analysis,
                        appliedSelection = captured.appliedFormatSelection,
                        selectedSubtitles = captured.selectedSubtitles,
                        cookiesPath = temporaryCookies?.file?.absolutePath,
                    ).getOrThrow().copy(
                        storageTarget = target,
                        pinnedCookiesReference = selectedCookies,
                        cookiesSelectionPinned = true,
                    )
                }
            }
            val startResult = requestResult.getOrNull()?.let {
                val outputDir = File(context.filesDir, "gui-downloads").apply { mkdirs() }
                downloadStarter(context.applicationContext, it, outputDir)
            }
            if (startResult?.isSuccess != true) temporaryCookies?.delete()
            mainHandler.post {
                runtimeState = runtimeState.copy(isStarting = false)
                if (analysisRequestRevision.get() != revision) return@post
                runtimeState = when {
                    startResult?.isSuccess == true -> runtimeState.withAcceptedDownload(startResult.getOrThrow())
                    cookiesResult.isFailure -> runtimeState.copy(userMessage = "Cookies 文件读取失败，请重新选择文件。")
                    requestResult.isFailure -> runtimeState.copy(
                        userMessage = if (fileSelection != null) "文件名不可用。" else
                            downloadRequestFailureMessage(requestResult.exceptionOrNull()),
                    )
                    else -> runtimeState.copy(userMessage = "无法启动下载，请检查系统权限后重试。")
                }
            }
        }.start()
    }

    fun reanalyzeRetryDraft(draft: RetryDownloadDraft) {
        if (draft.route is DownloadRoute.HttpFile) selectedRoute = "download"
        analyzeUrl(requestedUrl = draft.url, retryDraft = draft)
    }

    fun retryHistoryItem(item: HistoryUiItem) {
        Thread {
            val draftResult = retryDraftStore.load(item.id)
            mainHandler.post {
                draftResult.fold(
                    onSuccess = ::reanalyzeRetryDraft,
                    onFailure = {
                        runtimeState = runtimeState.copy(
                            userMessage = "该历史记录的重试信息不可用，请删除记录后重新分析。",
                        )
                    },
                )
            }
        }.start()
    }

    fun outputForAppPrivateUri(appPrivateUri: String?): Result<ExportController.AppPrivateOutput> {
        return ExportController.discoverAppPrivateOutputUri(
            appPrivateUri = appPrivateUri,
            appPrivateRoot = File(context.filesDir, "gui-downloads"),
            legacyRoots = listOf(File(context.cacheDir, "gui-downloads")),
        )
    }

    fun fileProviderUri(output: ExportController.AppPrivateOutput): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            output.sourceFile,
        )
    }

    fun outputTargetForHistoryUri(outputUri: String?, recordedMimeType: String? = null): Result<HistoryOutputTarget> {
        val normalizedUri = outputUri.orEmpty().trim()
        val contentUri = runCatching { Uri.parse(normalizedUri) }.getOrNull()
        val contentTarget = contentUri?.let { uri ->
            historyContentOutputTarget(
                rawUri = normalizedUri,
                resolverMimeType = recordedMimeType ?: runCatching { context.contentResolver.getType(uri) }.getOrNull(),
            )
        }
        if (contentTarget != null) return Result.success(contentTarget)

        return outputForAppPrivateUri(normalizedUri).map { output ->
            HistoryOutputTarget(
                uri = fileProviderUri(output).toString(),
                mimeType = recordedMimeType ?: output.mimeType,
            )
        }
    }

    fun outputForHistoryItem(item: HistoryUiItem): Result<HistoryOutputTarget> {
        return outputTargetForHistoryUri(item.outputUri, item.outputMimeType)
    }

    fun openHistoryItem(item: HistoryUiItem) {
        val output = outputForHistoryItem(item).getOrElse { _ ->
            runtimeState = runtimeState.copy(userMessage = historyMissingLocalOutputMessage())
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(output.uri), output.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "打开下载文件"))
        }.onFailure {
            runtimeState = runtimeState.copy(userMessage = "没有可用应用打开该文件，请安装支持该文件类型的应用。")
        }
    }

    fun shareHistoryItem(item: HistoryUiItem) {
        val output = outputForHistoryItem(item).getOrElse { _ ->
            runtimeState = runtimeState.copy(userMessage = historyMissingLocalOutputMessage())
            return
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType(output.mimeType)
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(output.uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享下载文件"))
        }.onFailure {
            runtimeState = runtimeState.copy(userMessage = "没有可用应用分享该文件。")
        }
    }

    fun deleteHistoryItem(item: HistoryUiItem, deleteFiles: Boolean) {
        Thread {
            val database = YtdlDatabaseProvider.get(context.applicationContext)
            val historyDao = database.historyDao()
            val fileDeletion = HistoryFileDeletion(
                File(context.filesDir, "gui-downloads"),
                File(context.cacheDir, "gui-downloads"),
                AndroidHistorySafDocuments(context.applicationContext),
            )
            val removeRecord = {
                deleteHistoryWithRetryPayload(item.id, retryDraftStore, { historyDao.deleteById(item.id) },
                    beforeDeleteFiles = {
                        val selected = historyDao.getById(item.id)
                        if (selected == null) Result.failure(IllegalStateException("历史记录已不存在。"))
                        else fileDeletion.delete(selected, historyDao.listAll(),
                            database.queueDao().listNonTerminal().map { it.taskId }.toSet()).map { Unit }
                    })
            }
            val idleResult = if (deleteFiles) fileDeletion.runWhenSafe(historyDao.getById(item.id), removeRecord)
                else IdleDownloadActionResult.Executed(deleteHistoryWithRetryPayload(item.id, retryDraftStore,
                    deleteHistory = { historyDao.deleteById(item.id) }))
            val deleteResult = when (idleResult) {
                is IdleDownloadActionResult.Executed -> idleResult.value
                IdleDownloadActionResult.ActiveDownload -> Result.failure(IllegalStateException("下载任务正在执行。"))
            }
            val deleted = deleteResult.getOrDefault(0)
            val rows = historyDao.listRecent(50)
            val items = historyUiItemsFromRows(rows, retryDraftStore::isAvailable)
            mainHandler.post {
                historyItems = items
                runtimeState = runtimeState.copy(
                    userMessage = when {
                        idleResult == IdleDownloadActionResult.ActiveDownload -> "正在下载或导出，请稍后再删除文件。"
                        deleteResult.exceptionOrNull() is HistoryFileDeleteFailure &&
                            (deleteResult.exceptionOrNull() as HistoryFileDeleteFailure).deletedCount > 0 ->
                            "部分文件已删除，历史记录已保留，请检查后重试。"
                        deleteResult.exceptionOrNull() is HistoryFileDeleteFailure ->
                            "文件未删除，历史记录已保留，请检查权限、共享引用或任务状态。"
                        deleteResult.isFailure -> "删除失败，请重试。"
                        deleted > 0 && deleteFiles -> "已删除历史记录，关联文件已处理。"
                        deleted > 0 -> "已删除历史记录。"
                        else -> "历史记录已不存在。"
                    },
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
            selectedSubtitles = if (mode == FormatMode.AudioOnly) emptyList() else runtimeState.selectedSubtitles,
            userMessage = DefaultRuntimeMessage,
        )
    }

    val themeConfig = themeConfigForSettings(appSettings)
    YtdlTheme(config = themeConfig) {
        val palette = LocalYtdlAppPalette.current
        val destinations = ytdlNavigationDestinations(palette)
        val selected = destinations.firstOrNull { it.route == selectedRoute } ?: destinations.first()

        Scaffold(
            containerColor = palette.appBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Column(Modifier.background(palette.appBackground)) {
                    if (selected.route == "download") {
                        DownloadStartButton(runtimeState, onClick = { startRealDownload() })
                    }
                    YtdlBottomBar(
                        destinations = destinations,
                        selectedRoute = selected.route,
                        onSelected = ::selectRoute,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.appBackground)
                    .padding(innerPadding),
            ) {
                key(selected.route) {
                    val pageListState = rememberLazyListState()
                    LazyColumn(
                    state = pageListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            testTagsAsResourceId = true
                            ytdlColorPresetId = themeConfig.colorPreset.id
                            ytdlSettingsAccentArgb = colorArgbHexForUiTest(palette.settingsAccent)
                        }
                        .testTag("ytdl-screen-${selected.route}"),
                    contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { PageHeader(selected) }
                    when (selected.route) {
                        "download" -> downloadPageItems(
                            state = runtimeState,
                            storageTarget = appSettings.defaultStorageTarget,
                            onUrlChange = ::replaceUrl,
                            onAnalyze = { analyzeUrl(runtimeState.url) },
                            onStartDownload = { startRealDownload() },
                            onPaste = ::pasteDownloadUrl,
                            showAdvancedFormats = showAdvancedFormats,
                            onToggleAdvancedFormats = { showAdvancedFormats = !showAdvancedFormats },
                            onFileSelectionChange = { runtimeState = runtimeState.copy(fileSelection = it) },
                            onSubtitlesChange = { runtimeState = runtimeState.copy(selectedSubtitles = it) },
                            includeInlineStart = false,
                            onModeSelected = ::selectDownloadMode,
                            onSelectStorageTarget = { storageTreePicker.launch(null) },
                            onFormatSelectionChange = { selection ->
                                runtimeState = runtimeState.withFormatSelection(selection)
                            },
                        )
                        "tasks" -> tasksPageItems(
                            state = runtimeState,
                            pendingRequests = pendingDownloadRequests,
                            activeStates = activeDownloadStates,
                            historyItems = historyItems,
                            historyQuery = historyQuery,
                            selectedFilterIndex = historyFilterIndex,
                            userMessage = runtimeState.userMessage,
                            onCancelDownload = { taskId ->
                                context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.ActionCancel)
                                    .putExtra(DownloadService.ExtraTaskId, taskId))
                                runtimeState = runtimeState.copy(userMessage = "已请求取消所选任务。")
                            },
                            onHistoryQueryChange = { historyQuery = it },
                            onHistoryFilterChange = { historyFilterIndex = it },
                            onOpen = ::openHistoryItem,
                            onShare = ::shareHistoryItem,
                            onRetry = ::retryHistoryItem,
                            onDelete = ::requestDeleteHistoryItem,
                            savedTasks = savedDownloadTasks,
                            onPauseDownload = { taskId ->
                                context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.ActionPause)
                                    .putExtra(DownloadService.ExtraTaskId, taskId))
                            },
                            onResumeTask = { id -> Thread {
                                val result = DownloadCoordinator.resume(context.applicationContext, id)
                                mainHandler.post { if (result.isFailure) runtimeState = runtimeState.copy(userMessage = "继续下载失败，任务已保留，请重试。") }
                            }.start() },
                            onCancelSavedTask = { id -> Thread {
                                val result = DownloadCoordinator.cancelSaved(context.applicationContext, id)
                                mainHandler.post {
                                    if (result.isSuccess) refreshHistory()
                                    else runtimeState = runtimeState.copy(userMessage = "取消尚未完成，任务已保留，请重试。")
                                }
                            }.start() },
                        )
                        "settings" -> settingsPageItems(
                            settings = appSettings,
                            parserVersionSubtitle = parserVersionSubtitle,
                            downloadCacheSubtitle = "${formatBytes(downloadCacheStats.bytes)} · ${downloadCacheStats.fileCount} 个文件",
                            notificationsAllowed = notificationsAllowed,
                            notificationRuntimePermissionRequired = notificationRuntimePermissionRequired,
                            onSelectCookies = {
                                showSiteCookies = true
                            },
                            onSelectStorageTarget = { storageTreePicker.launch(null) },
                            onResetStorageTarget = {
                                persistStorageTarget(StorageTarget.AppPrivate)
                                runtimeState = runtimeState.copy(userMessage = "已恢复默认路径：App 私有目录。")
                            },
                            onShowParserStatus = {
                                showParserStatusDialog = true
                            },
                            onShowOpenSource = { showOpenSource = true },
                            onShowMediaProcessorExplanation = { settingsExplanation = MediaProcessorExplanation },
                            onRequestDownloadCacheClear = { showDownloadCacheConfirmation = true },
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

                    if (selected.route == "tasks") {
                        TaskListScrollIndicator(
                            state = pageListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 5.dp),
                        )
                    }
                }
            }
        }
        if (confirmExtensionChange) {
            AlertDialog(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                onDismissRequest = { confirmExtensionChange = false },
                title = { Text("确认更改扩展名") },
                text = { Text("将保存为 ${runtimeState.fileSelection?.fileName.orEmpty()}。更改扩展名可能影响打开方式。") },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("ytdl-download-extension-confirm"),
                        onClick = {
                            confirmExtensionChange = false
                            startRealDownload(extensionConfirmed = true)
                        },
                    ) { Text("确认下载") }
                },
                dismissButton = { TextButton(onClick = { confirmExtensionChange = false }) { Text("返回修改") } },
            )
        }
        if (showDownloadCacheConfirmation) {
            AlertDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-cache-clear-dialog"),
                onDismissRequest = { showDownloadCacheConfirmation = false },
                title = { Text("确认清理临时缓存") },
                text = { Text("只删除未被下载记录引用的 App 私有临时文件；不会删除完成文件、合并文件或所选文件夹中的文件。") },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag("ytdl-cache-clear-confirm"),
                        onClick = {
                            showDownloadCacheConfirmation = false
                            clearDownloadCache()
                        },
                    ) {
                        Text("清理")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadCacheConfirmation = false }) {
                        Text("取消")
                    }
                },
            )
        }
        if (showParserStatusDialog) {
            AlertDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-parser-status-dialog"),
                onDismissRequest = { showParserStatusDialog = false },
                title = { Text("解析器版本") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val builtIn = parserVersions.first { it.isBuiltIn }
                        Text("内置版本：yt-dlp ${builtIn.version}")
                        Text("当前选择：yt-dlp ${parserVersions.first { it.isSelected }.version}")
                        Text("本进程实际版本：yt-dlp $currentProcessParserVersion")
                        TextButton(
                            enabled = !parserOperationRunning && !builtIn.isSelected,
                            modifier = Modifier.testTag("ytdl-parser-select-${builtIn.version}"),
                            onClick = { selectParserVersion(builtIn.version) },
                        ) {
                            Text(if (builtIn.isSelected) "当前选择" else "使用内置版本")
                        }
                        parserVersions.filterNot(ParserVersionInfo::isBuiltIn).forEach { version ->
                            Text("已下载：yt-dlp ${version.version}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    enabled = !parserOperationRunning && !version.isSelected,
                                    modifier = Modifier.testTag("ytdl-parser-select-${version.version}"),
                                    onClick = { selectParserVersion(version.version) },
                                ) {
                                    Text(if (version.isSelected) "当前选择" else "使用")
                                }
                                TextButton(
                                    enabled = !parserOperationRunning,
                                    modifier = Modifier.testTag("ytdl-parser-delete-${version.version}"),
                                    onClick = {
                                        pendingDeleteParserVersion = version.version
                                        showParserStatusDialog = false
                                    },
                                ) {
                                    Text("删除")
                                }
                            }
                        }
                        TextButton(
                            enabled = !parserOperationRunning,
                            modifier = Modifier.testTag("ytdl-parser-download-latest"),
                            onClick = { downloadParserVersion(expectedVersion = null) },
                        ) {
                            Text("下载最新版")
                        }
                        Text(parserUpdateStatusLabel(parserUpdateState))
                        parserOperationMessage?.let { Text(it) }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !parserUpdateState.isRunning,
                        onClick = { requestParserUpdateCheck(manual = true) },
                    ) {
                        Text("重新检查")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showParserStatusDialog = false }) {
                        Text("关闭")
                    }
                },
            )
        }
        val deleteVersion = pendingDeleteParserVersion
        if (deleteVersion != null) {
            AlertDialog(
                modifier = Modifier.testTag("ytdl-parser-delete-dialog"),
                onDismissRequest = {
                    pendingDeleteParserVersion = null
                    showParserStatusDialog = true
                },
                title = { Text("确认删除解析器 $deleteVersion？") },
                text = { Text("删除后不能在下次启动使用该版本；内置版本不会受影响。") },
                confirmButton = {
                    TextButton(
                        enabled = !parserOperationRunning,
                        modifier = Modifier.testTag("ytdl-parser-delete-confirm"),
                        onClick = {
                            pendingDeleteParserVersion = null
                            showParserStatusDialog = true
                            deleteParserVersion(deleteVersion)
                        },
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !parserOperationRunning,
                        onClick = {
                            pendingDeleteParserVersion = null
                            showParserStatusDialog = true
                        },
                    ) {
                        Text("取消")
                    }
                },
            )
        }
        val parserUpdateVersion = pendingParserUpdateVersion
        if (parserUpdateVersion != null && !showParserStatusDialog) {
            AlertDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-parser-update-dialog"),
                onDismissRequest = { dismissParserUpdatePrompt(parserUpdateVersion) },
                title = { Text("发现新版解析器") },
                text = {
                    Text("发现 yt-dlp $parserUpdateVersion，可下载并在重启应用后使用。")
                },
                confirmButton = {
                    TextButton(
                        enabled = !parserOperationRunning,
                        onClick = {
                            dismissParserUpdatePrompt(parserUpdateVersion)
                            downloadParserVersion(parserUpdateVersion)
                        },
                    ) {
                        Text("更新")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dismissParserUpdatePrompt(parserUpdateVersion) }) {
                        Text("稍后")
                    }
                },
            )
        }
        if (showSiteCookies) {
            SiteCookiesDialog(
                settings = appSettings,
                onDismiss = { showSiteCookies = false },
                onAdd = { name, domain ->
                    val previous = appSettings.siteCookies[domain]?.reference
                    appSettings = settingsRepository.setSiteCookies(name, domain, previous)
                },
                onChoose = { name, domain ->
                    pendingCookiesDomain = domain
                    pendingCookiesName = name
                    cookiesPicker.launch(arrayOf("*/*"))
                },
                onLogin = { name, domain ->
                    pendingCookiesDomain = domain
                    pendingCookiesName = name
                    siteLoginLauncher.launch(Intent(context, com.garyapp.ytdl.cookies.SiteLoginActivity::class.java)
                        .putExtra("domain", domain))
                },
                onClear = { domain ->
                    runCatching {
                        if (domain.isBlank()) appSettings = settingsRepository.setCookiesReference(null)
                        else {
                            com.garyapp.ytdl.cookies.SiteLoginStore.fromContext(context).clear(domain)
                            val name = appSettings.siteCookies[domain]?.name ?: domain
                            appSettings = settingsRepository.setSiteCookies(name, domain, null)
                        }
                    }.onFailure { runtimeState = runtimeState.copy(userMessage = "清理登录状态失败，请重试。") }
                },
                onCheck = { domain ->
                    val reference = if (domain.isBlank()) appSettings.cookiesReference else appSettings.siteCookies[domain]?.reference
                    Thread {
                        val temporary = prepareTemporaryCookiesForDownload(reference, context.applicationContext, "readability-check")
                        val readable = reference != null && temporary.isSuccess
                        temporary.getOrNull()?.delete()
                        mainHandler.post {
                            runtimeState = runtimeState.copy(userMessage =
                                if (readable) "文件可读取；登录是否有效需重新分析网站确认。" else "文件不可读取，请重新选择。")
                            settingsExplanation = SettingsExplanation("cookies 可读性",
                                if (readable) "文件可读取；登录是否有效需重新分析网站确认。" else "文件不可读取，请重新选择。")
                        }
                    }.start()
                },
            )
        }
        if (showOpenSource) OpenSourceNoticesDialog { showOpenSource = false }
        val explanation = settingsExplanation
        if (explanation != null) {
            AlertDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-settings-explanation-dialog"),
                onDismissRequest = { settingsExplanation = null },
                title = { Text(explanation.title) },
                text = { Text(explanation.body) },
                confirmButton = {
                    TextButton(onClick = { settingsExplanation = null }) {
                        Text("知道了")
                    }
                },
            )
        }
        val deleteTarget = pendingDeleteHistoryItem
        if (deleteTarget != null) {
            HistoryDeleteDialog(deleteTarget, onDismiss = { pendingDeleteHistoryItem = null },
                onConfirm = { includeFiles ->
                    pendingDeleteHistoryItem = null
                    deleteHistoryItem(deleteTarget, includeFiles)
                })
        }
    }
}

@Composable
internal fun HistoryDeleteDialog(
    item: HistoryUiItem,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var includeFiles by remember(item.id) { mutableStateOf(false) }
    var submitted by remember(item.id) { mutableStateOf(false) }
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("ytdl-history-delete-dialog"),
        onDismissRequest = { if (!submitted) onDismiss() },
        title = { Text("确认删除历史记录") },
        text = {
            Column {
                Text("将删除“${item.title}”的历史记录。")
                Row {
                    Checkbox(
                        checked = includeFiles,
                        onCheckedChange = { includeFiles = it },
                        modifier = Modifier.testTag("ytdl-history-delete-files"),
                    )
                    Text("同时删除下载文件")
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("ytdl-history-delete-confirm"),
                enabled = !submitted,
                onClick = {
                    if (!submitted) {
                        submitted = true
                        onConfirm(includeFiles)
                    }
                },
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(modifier = Modifier.testTag("ytdl-history-delete-cancel"),
                enabled = !submitted, onClick = onDismiss) { Text("取消") }
        },
    )
}

internal fun RuntimeDownloadState.withAnalysisForUiTest(analysis: VideoAnalysis): RuntimeDownloadState = withAnalysisResult(analysis)

private fun RuntimeDownloadState.withNewUrl(url: String): RuntimeDownloadState = copy(
    url = url,
    analysis = null,
    fileSelection = null,
    selectedSubtitles = emptyList(),
    formatSelection = FormatSelection(),
    appliedFormatSelection = FormatSelection(),
    thumbnailBitmap = null,
    thumbnailStatus = "",
    isAnalyzing = false,
    userMessage = DefaultRuntimeMessage,
)

private fun RuntimeDownloadState.withFileRecognition(selection: FileDownloadSelection): RuntimeDownloadState =
    copy(fileSelection = selection, isAnalyzing = false, userMessage = DefaultRuntimeMessage)

private fun RuntimeDownloadState.withAnalysisResult(analysis: VideoAnalysis): RuntimeDownloadState {
    val freshSelection = defaultFormatSelection(analysis)
    return copy(
        analysis = analysis,
        fileSelection = null,
        selectedSubtitles = emptyList(),
        formatSelection = freshSelection,
        appliedFormatSelection = freshSelection,
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
        userMessage = "下载已加入队列，当前阶段：$stageText。",
    )
}

internal fun RuntimeDownloadState.withAcceptedDownloadForUiTest(state: DownloadTaskState): RuntimeDownloadState =
    withAcceptedDownload(state)

private fun RuntimeDownloadState.withAcceptedDownload(state: DownloadTaskState): RuntimeDownloadState {
    val accepted = if (isDownloading) {
        copy(userMessage = "已加入等待队列，将按顺序自动开始。")
    } else {
        withForegroundStartState(state)
    }
    return accepted.withNewUrl("").copy(
        userMessage = accepted.userMessage,
        thumbnailBitmap = if (isDownloading) null else accepted.thumbnailBitmap,
    )
}

internal fun RuntimeDownloadState.withPipelineStateForUiTest(state: DownloadTaskState): RuntimeDownloadState = withPipelineState(state)

private fun RuntimeDownloadState.withPipelineState(state: DownloadTaskState): RuntimeDownloadState {
    val statusText = userVisibleDownloadStatus(state.stage)
    val progress = state.progress
    val mediaOutput = state.outputs.firstOrNull { it.kind == DownloadOutputKind.Media || it.kind == DownloadOutputKind.File }
    if (state.stage == DownloadStage.Completed && mediaOutput == null) {
        return copy(
            isDownloading = false,
            userMessage = "下载结果无有效输出，请重试。",
            progressPercent = null,
            overallProgressPercent = null,
            downloadedBytes = null,
            totalBytes = null,
            speedBytesPerSecond = null,
            downloadStatus = userVisibleDownloadStatus(DownloadStage.Failed),
            activeRequest = state.request,
            activeStage = DownloadStage.Failed,
            outputPath = "",
            outputBytes = 0L,
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
            speedBytesPerSecond = null,
            downloadStatus = "",
            activeRequest = null,
            activeStage = DownloadStage.Idle,
            outputPath = "",
            outputBytes = 0L,
        )
    }
    val sameDownloadStage = activeRequest == state.request && activeStage == state.stage
    val downloadedBytes = progress?.downloadedBytes ?: 0L
    val reportedTotalBytes = progress?.totalBytes?.takeIf { it > 0L && it >= downloadedBytes }
    val stableTotalBytes = when {
        state.stage in TerminalDownloadStages -> mediaOutput?.bytesWritten
        sameDownloadStage -> totalBytes?.takeIf { it > 0L && it >= downloadedBytes } ?: reportedTotalBytes
        else -> reportedTotalBytes
    }
    return copy(
        isDownloading = state.stage !in TerminalDownloadStages && state.stage != DownloadStage.Paused,
        userMessage = when (state.stage) {
            DownloadStage.Failed -> if (mediaOutput != null) {
                "文件已保存，但${foregroundFailureReason(state.errorMessage, mediaSaved = true)}"
            } else {
                "下载失败：${foregroundFailureReason(state.errorMessage, mediaSaved = false)}"
            }
            DownloadStage.Canceled -> "下载已取消。"
            DownloadStage.Completed -> "下载完成"
            else -> state.statusMessage ?: "正在$statusText..."
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
        totalBytes = stableTotalBytes,
        speedBytesPerSecond = progress?.speedBytesPerSecond?.takeIf { it > 0.0 },
        outputPath = mediaOutput?.path.orEmpty(),
        outputBytes = mediaOutput?.bytesWritten ?: 0L,
    )
}

private fun foregroundFailureReason(errorMessage: String?, mediaSaved: Boolean): String {
    val message = errorMessage.orEmpty()
    if (message.contains("字幕", ignoreCase = true) || message.contains("subtitle", ignoreCase = true)) {
        return if (mediaSaved) {
            "附加文件处理失败。"
        } else {
            "文件处理失败，请重试或选择其他格式。"
        }
    }
    return DownloadFailureMessages.fromErrorText(message)
}

private val TerminalDownloadStages = setOf(
    DownloadStage.Completed,
    DownloadStage.Failed,
    DownloadStage.Canceled,
    DownloadStage.Idle,
)

private fun analysisFailureMessage(error: Throwable): String {
    if ((error as? YtdlpAnalysisException)?.safeMessage == YtdlpBridge.NO_FORMATS_MESSAGE) {
        return "分析失败：${YtdlpBridge.NO_FORMATS_MESSAGE}"
    }
    val category = (error as? YtdlpAnalysisException)?.category
        ?: return "分析失败，请检查地址或网络。"
    return when (category) {
        AnalysisErrorCategory.XNoVideo -> "X 未返回这条帖子的视频。请在 X 打开原帖；若登录后可播放，可在设置中选择 X 的 cookies 文件再试。"
        AnalysisErrorCategory.Network -> "分析失败：网络连接失败，请检查网络后重试。"
        AnalysisErrorCategory.Unsupported -> "分析失败：当前地址不受支持或格式不正确。"
        AnalysisErrorCategory.Permission -> "分析失败：网站需要登录或 cookies 文件，请确认授权后重试。"
        AnalysisErrorCategory.Drm -> "该内容受 DRM 保护，无法下载。"
        AnalysisErrorCategory.Parser -> "分析失败：解析器暂时无法处理该地址，请稍后重试。"
        AnalysisErrorCategory.Canceled -> "分析已取消。"
        AnalysisErrorCategory.Unknown -> "分析失败，请检查地址或网络。"
    }
}

internal fun analysisFailureMessageForUiTest(error: Throwable): String = analysisFailureMessage(error)

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

private fun displayNameForUri(context: android.content.Context, uri: Uri, fallback: String): String {
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
        ?: fallback
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
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderColor),
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
                ).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = destination.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("ytdl-tab-${destination.route}")
                        .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab) { onSelected(destination.route) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 26.dp)
                            .background(if (selected) palette.actionContainer else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = if (selected) destination.accent else palette.neutralText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = destination.label,
                        color = if (selected) destination.accent else palette.neutralText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageHeader(destination: YtdlDestination) {
    val palette = LocalYtdlAppPalette.current
    Column(
        modifier = Modifier.testTag("ytdl-page-header"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.titleText,
                    fontWeight = FontWeight.Bold,
                )
                if (destination.route == "download") {
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                        color = palette.softText, modifier = Modifier.testTag("captainflash-version"))
                }
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
private fun TaskListScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val metrics by remember(state) {
        derivedStateOf {
            if (!state.canScrollBackward && !state.canScrollForward) {
                return@derivedStateOf null
            }
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                return@derivedStateOf null
            }
            val firstItem = visibleItems.first()
            val lastItem = visibleItems.last()
            val visibleExtent = (lastItem.offset + lastItem.size - firstItem.offset).coerceAtLeast(1)
            val calculated = lazyListScrollbarMetrics(
                totalItemsCount = layoutInfo.totalItemsCount,
                firstVisibleItemIndex = state.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                averageVisibleItemExtent = visibleExtent.toFloat() / visibleItems.size,
                viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
            ) ?: return@derivedStateOf null
            calculated.copy(
                offsetFraction = when {
                    !state.canScrollBackward -> 0f
                    !state.canScrollForward -> 1f
                    else -> calculated.offsetFraction
                },
            )
        }
    }
    val currentMetrics = metrics ?: return
    val palette = LocalYtdlAppPalette.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(8.dp)
            .padding(vertical = 16.dp)
            .semantics { testTagsAsResourceId = true }
            .testTag("ytdl-tasks-scroll-indicator"),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.borderColor.copy(alpha = 0.65f)),
        )
        val thumbHeight = (maxHeight * currentMetrics.thumbFraction)
            .coerceAtLeast(44.dp)
            .coerceAtMost(maxHeight)
        val thumbOffset = (maxHeight - thumbHeight) * currentMetrics.offsetFraction
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = thumbOffset)
                .width(3.dp)
                .height(thumbHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.softText.copy(alpha = 0.9f))
                .testTag("ytdl-tasks-scroll-thumb"),
        )
    }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.downloadPageItems(
    state: RuntimeDownloadState,
    storageTarget: StorageTarget,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onStartDownload: () -> Unit,
    onModeSelected: (FormatMode) -> Unit,
    onSelectStorageTarget: () -> Unit,
    onFormatSelectionChange: (FormatSelection) -> Unit = {},
    onPaste: () -> Unit = {},
    showAdvancedFormats: Boolean = true,
    onToggleAdvancedFormats: () -> Unit = {},
    onFileSelectionChange: (FileDownloadSelection) -> Unit = {},
    onSubtitlesChange: (List<SubtitleInfo>) -> Unit = {},
    includeInlineStart: Boolean = true,
) {
    item {
        val palette = LocalYtdlAppPalette.current
        val showKeyboardOnFocus = shouldShowUrlInputKeyboardOnFocus(LocalConfiguration.current.keyboard)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            UrlInputField(
                value = state.url,
                onValueChange = onUrlChange,
                showKeyboardOnFocus = showKeyboardOnFocus,
                modifier = Modifier.weight(1f).testTag("ytdl-url-input"),
            )
            Button(
                onClick = onPaste,
                modifier = Modifier.size(48.dp).testTag("ytdl-download-paste"),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = palette.segmentedBackground, contentColor = palette.formatAccent),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(ClipboardIcon, contentDescription = "从剪贴板获取")
            }
            Button(
                onClick = onAnalyze,
                enabled = state.url.isNotBlank() && !state.isAnalyzing,
                modifier = Modifier.heightIn(min = 48.dp).testTag("ytdl-analyze-button"),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = palette.formatAccent, contentColor = palette.controlActionText),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (state.isAnalyzing) "识别中" else "识别", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (shouldShowRuntimeMessage(state.userMessage)) {
        item { RuntimeMessageCard(state.userMessage) }
    }

    val analysis = state.analysis
    val file = state.fileSelection
    when {
        analysis != null -> {
            item {
                IdentificationLabel("媒体")
                DownloadPreviewCard(state)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadChoice(
                        "下载内容", state.formatSelection.mode.label, "ytdl-download-mode", Modifier.weight(1f),
                    ) { dismiss ->
                        FormatMode.entries.forEach { mode ->
                            val suffix = when (mode) {
                                FormatMode.VideoAndAudio -> "av"
                                FormatMode.VideoOnly -> "video"
                                FormatMode.AudioOnly -> "audio"
                            }
                            ChoiceItem(mode.label, "ytdl-download-mode-$suffix", isFormatModeAvailable(analysis, mode)) {
                                dismiss()
                                onModeSelected(mode)
                            }
                        }
                    }
                    DownloadControl(
                        "下载格式", formatSelectionSummary(analysis, state.appliedFormatSelection),
                        "ytdl-download-format-toggle", Modifier.weight(1f),
                        actionLabel = if (showAdvancedFormats) "收起" else "展开",
                        onClick = onToggleAdvancedFormats,
                    )
                }
            }
            if (showAdvancedFormats) {
                formatSelectionItems(analysis, state.formatSelection, onFormatSelectionChange)
            }
            if (state.formatSelection.mode != FormatMode.AudioOnly) {
                item { SubtitleChoices(analysis, state.selectedSubtitles, onSubtitlesChange) }
            }
        }
        file != null -> item {
            val label = listOf(file.metadata.kind.identificationLabel(), file.formatLabel)
                .filter { it.isNotBlank() }.joinToString(" · ")
            IdentificationLabel(label)
            FileDownloadSection(file, onFileSelectionChange)
        }
        state.isAnalyzing -> item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在识别链接")
            }
        }
        else -> item {
            val palette = LocalYtdlAppPalette.current
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 24.dp)) {
                Icon(DownloadTabIcon, contentDescription = null, tint = palette.formatAccent)
                Text("先识别，再选择", style = MaterialTheme.typography.titleMedium, color = palette.titleText)
                Text("识别后可选择媒体格式，或设置普通文件的保存方式。", color = palette.softText)
            }
        }
    }
    if (analysis != null || file != null) {
        item {
            DownloadControl("保存位置", storageTargetSummary(storageTarget), "ytdl-download-storage-target",
                actionLabel = "更改", onClick = onSelectStorageTarget)
        }
    }
    if (file?.metadata?.kind == FileKind.Other) {
        item { FileConflictChoice(file, onFileSelectionChange) }
    }
    if (includeInlineStart) item { DownloadStartButton(state, onStartDownload) }
}

@Composable
private fun IdentificationLabel(type: String) {
    Text(
        "已识别 · $type",
        modifier = Modifier.padding(vertical = 10.dp).testTag("ytdl-download-identification"),
        color = LocalYtdlAppPalette.current.formatAccent,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun SubtitleChoices(
    analysis: VideoAnalysis,
    selected: List<SubtitleInfo>,
    onSelectionChange: (List<SubtitleInfo>) -> Unit,
) {
    val subtitles = analysis.subtitles.distinctBy { it.language to it.source }
    if (subtitles.isEmpty()) return
    val summary = when (selected.size) {
        0 -> "不下载字幕"
        1 -> "${selected.single().language} · 独立字幕"
        else -> "${selected.size} 种独立字幕"
    }
    DownloadChoice("字幕", summary, "ytdl-format-subtitle-toggle") { dismiss ->
        subtitles.forEach { subtitle ->
            val source = if (subtitle.source == SubtitleSource.Manual) "人工" else "自动生成"
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("${subtitle.language} · $source · ${subtitle.ext}") },
                leadingIcon = { Checkbox(checked = subtitle in selected, onCheckedChange = null) },
                onClick = {
                    dismiss()
                    onSelectionChange(
                        if (subtitle in selected) selected - subtitle
                        else selected.filterNot { it.language == subtitle.language } + subtitle,
                    )
                },
                modifier = Modifier.testTag("ytdl-subtitle-${subtitle.language}-${subtitle.source.pythonValue}"),
            )
        }
    }
}

@Composable
private fun DownloadStartButton(state: RuntimeDownloadState, onClick: () -> Unit) {
    val palette = LocalYtdlAppPalette.current
    val canStart = canStartDownload(state)
    val size = state.fileSelection?.metadata?.totalBytes
    val label = when {
        state.isStarting -> "正在提交任务"
        state.isAnalyzing -> "正在识别链接"
        state.fileSelection != null && !canStart -> "请检查文件名"
        !canStart -> "识别后可下载"
        size != null -> "开始下载 · ${formatBytes(size)}"
        else -> "开始下载"
    }
    Button(
        onClick = onClick,
        enabled = canStart,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            .heightIn(min = 48.dp).semantics { testTagsAsResourceId = true }.testTag("ytdl-download-start"),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = palette.downloadAccent, contentColor = palette.primaryActionText),
    ) {
        Icon(DownloadTabIcon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

internal fun canStartDownloadForUiTest(state: RuntimeDownloadState): Boolean = canStartDownload(state)

private fun canStartDownload(state: RuntimeDownloadState): Boolean =
    !state.isAnalyzing && !state.isStarting &&
        (state.fileSelection?.route != null || state.analysis != null)

internal fun shouldShowRuntimeMessageForUiTest(message: String): Boolean = shouldShowRuntimeMessage(message)

private fun shouldShowRuntimeMessage(message: String): Boolean {
    val trimmed = message.trim()
    return trimmed.isNotEmpty() &&
        trimmed != DefaultRuntimeMessage &&
        trimmed != AnalysisCompleteRuntimeMessage
}

internal fun historyMissingLocalOutputMessageForUiTest(error: Throwable?): String = historyMissingLocalOutputMessage()

private fun historyMissingLocalOutputMessage(): String =
    "历史记录对应的本地文件不存在或为空，请重新下载或删除该记录。"

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
    val inputTextColor = palette.titleText
    val inputHintColor = palette.softText
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val textChangeGuard = remember { UrlInputTextChangeGuard() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .border(1.dp, palette.borderColor)
            .background(palette.cardBackground)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    id = R.id.ytdl_url_input
                    setSingleLine(false)
                    minLines = 1
                    maxLines = 5
                    setHorizontallyScrolling(false)
                    setPadding(0, 0, 0, 0)
                    background = null
                    includeFontPadding = false
                    textSize = 16f
                    hint = "粘贴媒体链接或文件直链"
                    inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_URI or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    installUrlSelectionActionModeCallbacks()
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
                editText.hint = "粘贴媒体链接或文件直链"
                editText.setTextColor(inputTextColor.toArgb())
                editText.setHintTextColor(inputHintColor.toArgb())
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
    val analysis = requireNotNull(state.analysis)
    val duration = analysis.durationSeconds.takeIf { it > 0 }?.let(::formatDuration)
    val palette = LocalYtdlAppPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!analysis.thumbnailUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(7.dp))
                    .background(palette.segmentedBackground),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = state.thumbnailBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "视频预览图",
                        modifier = Modifier.fillMaxSize().testTag("ytdl-thumbnail-image"),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(state.thumbnailStatus, color = palette.titleText,
                        modifier = Modifier.testTag("ytdl-thumbnail-placeholder"))
                }
                if (duration != null) {
                    Text(
                        duration,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.64f)).padding(horizontal = 6.dp, vertical = 3.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Text(analysis.title.ifBlank { "未命名媒体" }, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = palette.titleText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val details = listOfNotNull(duration, if (analysis.subtitles.isNotEmpty()) "可下载独立字幕" else null)
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), color = palette.softText, style = MaterialTheme.typography.bodySmall)
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

internal fun androidx.compose.foundation.lazy.LazyListScope.formatSelectionItems(
    analysis: VideoAnalysis,
    selection: FormatSelection,
    onSelectionChange: (FormatSelection) -> Unit,
) {
    val rows = buildFormatResolutionRows(analysis, selection)
    val summaries = formatSettingSummaries(analysis, selection)
    val audioMode = selection.mode == FormatMode.AudioOnly

    if (audioMode) {
        item {
            SettingLineCard(
                title = "视频分辨率",
                subtitle = "音频模式不适用",
                leading = "—",
                trailing = "",
                enabled = false,
                modifier = Modifier.testTag("ytdl-format-video-resolution-line"),
            )
        }
        item {
            SettingLineCard(
                title = "帧率",
                subtitle = "音频模式不适用",
                leading = "—",
                trailing = "",
                enabled = false,
                modifier = Modifier.testTag("ytdl-format-frame-rate-line"),
            )
        }
        item {
            SettingLineCard(
                title = "视频编码",
                subtitle = "音频模式不适用",
                leading = "—",
                trailing = "",
                enabled = false,
                modifier = Modifier.testTag("ytdl-format-video-codec-line"),
            )
        }
        item {
            AppCard(modifier = Modifier.testTag("ytdl-format-audio-card")) {
                SectionTitle("音频格式")
                rows.forEach { row ->
                    ResolutionRow(
                        row = row,
                        onSelect = {
                            if (row.selectable) {
                                onSelectionChange(selectionFromRow(selection.mode, row))
                            }
                        },
                        onCodecSelect = { option ->
                            if (row.selectable) {
                                onSelectionChange(
                                    selectionFromRow(
                                        selection.mode,
                                        row.copy(videoFormatId = option.videoFormatId),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
        item {
            SettingLineCard(
                "音频容器",
                summaries.container,
                "♫",
                "",
                modifier = Modifier.testTag("ytdl-format-container-line"),
            )
        }
    } else {
        item {
            AppCard(modifier = Modifier.testTag("ytdl-format-resolution-card")) {
                SectionTitle("分辨率")
                if (rows.isEmpty()) {
                    Text("当前模式没有可下载格式", color = LocalYtdlAppPalette.current.softText)
                } else {
                    rows.forEach { row ->
                        ResolutionRow(
                            row = row,
                            onSelect = {
                                if (row.selectable) {
                                    onSelectionChange(selectionFromRow(selection.mode, row))
                                }
                            },
                            onCodecSelect = { option ->
                                if (row.selectable) {
                                    onSelectionChange(
                                        selectionFromRow(
                                            selection.mode,
                                            row.copy(videoFormatId = option.videoFormatId),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        item {
            SettingLineCard(
                "帧率",
                summaries.frameRate,
                "",
                "",
                modifier = Modifier.testTag("ytdl-format-frame-rate-line"),
            )
        }
        item {
            SettingLineCard(
                "视频编码",
                summaries.videoCodec,
                "",
                "",
                modifier = Modifier.testTag("ytdl-format-video-codec-line"),
            )
        }
        item {
            SettingLineCard(
                "容器格式",
                summaries.container,
                "",
                "",
                modifier = Modifier.testTag("ytdl-format-container-line"),
            )
        }
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
                Text(
                    "实际下载：${formatSelectionSummary(analysis, selection)}",
                    color = palette.formatAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
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

internal data class LazyListScrollbarMetrics(
    val thumbFraction: Float,
    val offsetFraction: Float,
)

internal fun lazyListScrollbarMetricsForUiTest(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    averageVisibleItemExtent: Float,
    viewportSize: Int,
): LazyListScrollbarMetrics? = lazyListScrollbarMetrics(
    totalItemsCount = totalItemsCount,
    firstVisibleItemIndex = firstVisibleItemIndex,
    firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
    averageVisibleItemExtent = averageVisibleItemExtent,
    viewportSize = viewportSize,
)

private fun lazyListScrollbarMetrics(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    averageVisibleItemExtent: Float,
    viewportSize: Int,
): LazyListScrollbarMetrics? {
    if (totalItemsCount <= 0 || averageVisibleItemExtent <= 0f || viewportSize <= 0) return null
    val estimatedContentSize = averageVisibleItemExtent * totalItemsCount
    if (estimatedContentSize <= viewportSize) return null
    val maximumScroll = estimatedContentSize - viewportSize
    val currentScroll = firstVisibleItemIndex * averageVisibleItemExtent + firstVisibleItemScrollOffset
    return LazyListScrollbarMetrics(
        thumbFraction = (viewportSize / estimatedContentSize).coerceIn(0f, 1f),
        offsetFraction = (currentScroll / maximumScroll).coerceIn(0f, 1f),
    )
}

internal fun shouldShowCurrentTaskForUiTest(state: RuntimeDownloadState): Boolean = shouldShowCurrentTask(state)

private fun shouldShowCurrentTask(state: RuntimeDownloadState): Boolean {
    return state.isDownloading && state.activeStage !in TerminalDownloadStages
}

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
        is DownloadRoute.HttpFile -> listOf(QueueStageSpec("下载文件", DownloadStage.DownloadingFile))
        is DownloadRoute.DirectSingleFile -> listOf(QueueStageSpec("下载视频", DownloadStage.DownloadingVideo))
        is DownloadRoute.VideoOnly -> listOf(QueueStageSpec("下载视频", DownloadStage.DownloadingVideo))
        is DownloadRoute.AudioOnly -> listOf(QueueStageSpec("下载音频", DownloadStage.DownloadingAudio))
        is DownloadRoute.MergeRequired -> listOf(
            QueueStageSpec("下载视频", DownloadStage.DownloadingVideo),
            QueueStageSpec("下载音频", DownloadStage.DownloadingAudio),
            QueueStageSpec("原生合并", DownloadStage.Merging),
        )
    }
    return mediaStages
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
        "下载完成" -> "完成"
        "下载失败" -> "失败"
        "已取消" -> "取消"
        else -> "${(state.overallProgressPercent ?: state.progressPercent ?: 0.0).toInt()}%"
    }
}

internal fun queueCardStatusForUiTest(state: RuntimeDownloadState): String = queueCardStatus(state)

private fun queueCardMeta(state: RuntimeDownloadState): QueueProgressMeta {
    val speed = state.speedBytesPerSecond
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { "${formatProgressBytes(it)}/s" }
        ?: "--"
    val downloaded = formatProgressBytes((state.downloadedBytes ?: 0L).toDouble())
    val total = state.totalBytes?.let { formatProgressBytes(it.toDouble()) } ?: "--"
    return QueueProgressMeta(
        speed = speed,
        transferred = "$downloaded/$total",
        contentDescription = "下载速度 $speed，已下载 $downloaded，总容量 $total",
    )
}

internal fun queueCardMetaForUiTest(state: RuntimeDownloadState): QueueProgressMeta = queueCardMeta(state)

private fun queueCardFormatBadge(state: RuntimeDownloadState): String = formatResolutionBadgeForRequest(state.activeRequest)

internal fun queueCardFormatBadgeForUiTest(state: RuntimeDownloadState): String = queueCardFormatBadge(state)

private fun queueCardCodecBadge(state: RuntimeDownloadState): String = formatCodecBadgeForRequest(state.activeRequest)

internal fun queueCardCodecBadgeForUiTest(state: RuntimeDownloadState): String = queueCardCodecBadge(state)


private fun queueCardAccent(
    state: RuntimeDownloadState,
    palette: YtdlAppPalette = DefaultPalette,
): Color {
    return when (state.downloadStatus) {
        "下载完成" -> palette.successGreen
        "下载失败" -> HistoryFailureRed
        "已取消" -> palette.downloadAccent
        else -> palette.queueAccent
    }
}

internal fun queueCardAccentForUiTest(
    state: RuntimeDownloadState,
    palette: YtdlAppPalette = DefaultPalette,
): Color = queueCardAccent(state, palette)

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return "%.1f KB".format(kib)
    val mib = kib / 1024.0
    if (mib < 1024) return "%.1f MB".format(mib)
    return "%.2f GB".format(mib / 1024.0)
}

private fun formatProgressBytes(bytes: Double): String {
    val safeBytes = bytes.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val units = listOf("B", "KB", "MB", "GB")
    var value = safeBytes
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}

internal fun formatProgressBytesForUiTest(bytes: Double): String = formatProgressBytes(bytes)

private fun settingsCookiesSubtitle(settings: AppSettings): String {
    if (settings.siteCookies.isNotEmpty()) {
        return "已配置 ${settings.siteCookies.values.count { it.reference != null }} 个网站 · 自动匹配域名"
    }
    val reference = settings.cookiesReference
        ?: return "未配置网站登录"
    return "${reference.displayName ?: "cookies 文件"}"
}

private fun storageTargetSummary(target: StorageTarget): String {
    return when (val safeTarget = StorageTargets.sanitizeDefault(target)) {
        StorageTarget.AppPrivate -> "App 私有目录；完成文件由 App 保留"
        is StorageTarget.SafTree -> "${StorageTargets.displayName(safeTarget)}；完成后清理 App 内中转文件"
    }
}

internal fun storageTargetSummaryForUiTest(target: StorageTarget): String = storageTargetSummary(target)

private fun storagePermissionUriToRelease(
    previous: StorageTarget,
    next: StorageTarget,
): String? {
    val previousTree = previous as? StorageTarget.SafTree ?: return null
    val nextTreeUri = (next as? StorageTarget.SafTree)?.treeUri
    return previousTree.treeUri.takeIf { it != nextTreeUri }
}

internal fun storagePermissionUriToReleaseForUiTest(
    previous: StorageTarget,
    next: StorageTarget,
): String? = storagePermissionUriToRelease(previous, next)

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

private fun nextHistoryFilterIndex(selectedFilterIndex: Int): Int =
    (selectedFilterIndex.coerceIn(HistoryFilterOptions.indices) + 1) % HistoryFilterOptions.size

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
        val searchable = item.title.lowercase(Locale.ROOT)
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
    return item.isAudioOnly ||
        searchable.contains("仅音频") ||
        (searchable.contains("音频") && !searchable.contains("视频"))
}

@Composable
private fun HistorySearchHeader(
    value: String,
    onValueChange: (String) -> Unit,
    selectedFilterIndex: Int,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .testTag("ytdl-history-search"),
            placeholder = { Text("搜索历史") },
            leadingIcon = {
                Icon(SearchIcon, contentDescription = null, tint = LocalYtdlAppPalette.current.softText)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        HistoryFilterButton(
            selectedFilterIndex = selectedFilterIndex,
            onClick = onFilterClick,
            tag = "ytdl-history-filter-action",
        )
    }
}

@Composable
private fun HistoryFilterButton(
    selectedFilterIndex: Int,
    onClick: () -> Unit,
    tag: String,
) {
    val palette = LocalYtdlAppPalette.current
    Surface(
        color = palette.historyAccent.copy(alpha = 0.12f),
        shape = CircleShape,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                HistoryFilterIcon,
                contentDescription = "筛选：${HistoryFilterOptions[selectedFilterIndex.coerceIn(HistoryFilterOptions.indices)]}",
                tint = palette.historyAccent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.tasksPageItems(
    state: RuntimeDownloadState,
    pendingRequests: List<DownloadRequest>,
    historyItems: List<HistoryUiItem>,
    historyQuery: String,
    selectedFilterIndex: Int,
    userMessage: String,
    onCancelDownload: (String?) -> Unit,
    onHistoryQueryChange: (String) -> Unit,
    onHistoryFilterChange: (Int) -> Unit,
    onOpen: (HistoryUiItem) -> Unit,
    onShare: (HistoryUiItem) -> Unit,
    onRetry: (HistoryUiItem) -> Unit,
    onDelete: (HistoryUiItem) -> Unit,
    savedTasks: List<ResumeTaskItem> = emptyList(),
    activeStates: Map<String, DownloadTaskState>? = null,
    onPauseDownload: (String?) -> Unit = {},
    onResumeTask: (String) -> Unit = {},
    onCancelSavedTask: (String) -> Unit = {},
) {
    val currentTasks = if (activeStates == null) {
        listOf(state).filter(::shouldShowCurrentTask)
    } else {
        activeStates.values.map { RuntimeDownloadState().withPipelineState(it) }.filter(::shouldShowCurrentTask)
    }
    item {
        val palette = LocalYtdlAppPalette.current
        Surface(
            modifier = Modifier.testTag("ytdl-tasks-summary-card"),
            color = palette.queueAccent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("当前和等待", color = palette.queueAccent, fontWeight = FontWeight.Bold)
                Text(
                    if (currentTasks.isNotEmpty() || pendingRequests.isNotEmpty()) {
                        "正在下载 ${currentTasks.size} · 等待 ${pendingRequests.size}"
                    } else {
                        "当前没有进行中或等待任务"
                    },
                    color = palette.softText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (currentTasks.isNotEmpty()) {
        item { SectionTitle("当前任务") }
    }
    currentTasks.forEach { state ->
        item(key = "active-${state.activeRequest?.taskId}") {
            val palette = LocalYtdlAppPalette.current
            val title = state.activeRequest?.title?.takeIf { it.isNotBlank() } ?: "下载任务"
            val thumbnailUrl = state.activeRequest?.thumbnailUrl
            var taskThumbnail by remember(thumbnailUrl) { mutableStateOf(state.thumbnailBitmap) }
            DisposableEffect(thumbnailUrl) {
                val loader = thumbnailUrl?.takeIf { taskThumbnail == null }?.let { url ->
                    HistoryThumbnailLoader.load(url) { taskThumbnail = it }
                }
                onDispose { loader?.close() }
            }
            Box(modifier = Modifier.testTag("ytdl-real-queue-card")) {
                QueueCard(
                    title = title,
                    titleTag = "ytdl-current-task-title",
                    subtitle = queueCardSubtitle(state),
                    progress = queueProgressPresentation(state),
                    status = queueCardStatus(state),
                    progressMeta = queueCardMeta(state),
                    formatBadge = queueCardFormatBadge(state),
                    codecBadge = queueCardCodecBadge(state),
                    stageItems = queueStageItems(state),
                    accent = queueCardAccent(state, palette),
                    thumbnailBitmap = taskThumbnail,
                    onCancel = { onCancelDownload(state.activeRequest?.taskId) },
                    onPause = if (state.activeStage in setOf(DownloadStage.Pausing, DownloadStage.WaitingForNetwork)) null else { { onPauseDownload(state.activeRequest?.taskId) } },
                )
            }
        }
    }
    if (pendingRequests.isNotEmpty()) {
        item { SectionTitle("等待中（${pendingRequests.size}）") }
        pendingRequests.forEachIndexed { index, request ->
            item(key = "pending-${request.title}-$index") {
                val palette = LocalYtdlAppPalette.current
                val waitingState = RuntimeDownloadState(
                    activeRequest = request,
                    activeStage = DownloadStage.Waiting,
                    downloadStatus = "等待中",
                )
                QueueCard(
                    title = request.title.ifBlank { "等待下载的任务" },
                    titleTag = "ytdl-pending-task-title-$index",
                    subtitle = "已加入队列 · 按顺序等待",
                    progress = QueueProgressPresentation(fraction = 0f, isIndeterminate = false),
                    status = "等待中",
                    supportingText = "前方 ${index + currentTasks.size} 个任务",
                    formatBadge = formatResolutionBadgeForRequest(request),
                    codecBadge = formatCodecBadgeForRequest(request),
                    stageItems = queueStageItems(waitingState),
                    accent = palette.queueAccent,
                    modifier = Modifier.testTag("ytdl-pending-queue-card-$index"),
                    onCancel = { onCancelDownload(request.taskId) },
                )
            }
        }
    }
    if (savedTasks.isNotEmpty()) {
        item { SectionTitle("已暂停（${savedTasks.size}）") }
        savedTasks.forEach { task ->
            item(key = "saved-${task.taskId}") {
                AppCard(modifier = Modifier.testTag("ytdl-saved-task-${task.taskId}")) {
                    TaskCardTitle(task.title, "ytdl-saved-task-title-${task.taskId}")
                    Text(task.recoveryError ?: task.state.statusMessage ?: "已暂停，点击继续。")
                    Text("已下载 ${formatBytes(task.state.progress?.downloadedBytes ?: 0L)}" +
                        (task.state.progress?.totalBytes?.let { " / ${formatBytes(it)}" } ?: ""))
                    YtdlQueueProgressBar(QueueProgressPresentation(task.state.progress?.percent?.let { (it / 100).toFloat().coerceIn(0f, 1f) }, false),
                        LocalYtdlAppPalette.current.queueAccent, Modifier.fillMaxWidth())
                    Row {
                        if (task.recoveryError == null) TextButton(onClick = { onResumeTask(task.taskId) }) { Text("继续") }
                        TextButton(onClick = { onCancelSavedTask(task.taskId) }) { Text("取消") }
                    }
                }
            }
        }
    }
    item { SectionTitle("历史记录") }
    historyPageItems(
        historyItems = historyItems,
        historyQuery = historyQuery,
        selectedFilterIndex = selectedFilterIndex,
        userMessage = userMessage,
        onHistoryQueryChange = onHistoryQueryChange,
        onHistoryFilterChange = onHistoryFilterChange,
        onOpen = onOpen,
        onShare = onShare,
        onRetry = onRetry,
        onDelete = onDelete,
    )
}

internal fun androidx.compose.foundation.lazy.LazyListScope.historyPageItems(
    historyItems: List<HistoryUiItem>,
    historyQuery: String,
    selectedFilterIndex: Int,
    userMessage: String,
    onHistoryQueryChange: (String) -> Unit,
    onHistoryFilterChange: (Int) -> Unit,
    onOpen: (HistoryUiItem) -> Unit,
    onShare: (HistoryUiItem) -> Unit,
    onRetry: (HistoryUiItem) -> Unit,
    onDelete: (HistoryUiItem) -> Unit,
) {
    val visibleItems = filterHistoryItems(historyItems, historyQuery, selectedFilterIndex)
    item {
        HistorySearchHeader(
            value = historyQuery,
            onValueChange = onHistoryQueryChange,
            selectedFilterIndex = selectedFilterIndex,
            onFilterClick = { onHistoryFilterChange(nextHistoryFilterIndex(selectedFilterIndex)) },
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
                Text(if (historyItems.isEmpty()) "暂无历史记录，完成下载后会显示" else "没有匹配的历史记录", color = palette.titleText, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (historyItems.isEmpty()) "完成下载后会显示在这里。" else "请调整搜索关键词或分类筛选。", color = palette.softText, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        items(visibleItems) { item ->
            HistoryCard(
                item = item,
                onOpen = { onOpen(item) },
                onShare = { onShare(item) },
                onRetry = { onRetry(item) },
                onDelete = { onDelete(item) },
                modifier = Modifier.testTag("ytdl-history-real-card"),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsPageItems(
    settings: AppSettings,
    parserVersionSubtitle: String,
    downloadCacheSubtitle: String,
    notificationsAllowed: Boolean,
    notificationRuntimePermissionRequired: Boolean,
    onSelectCookies: () -> Unit,
    onSelectStorageTarget: () -> Unit,
    onResetStorageTarget: () -> Unit,
    onShowParserStatus: () -> Unit,
    onShowOpenSource: () -> Unit,
    onShowMediaProcessorExplanation: () -> Unit,
    onRequestDownloadCacheClear: () -> Unit,
    onRequestNotifications: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onColorPresetChange: (String) -> Unit,
) {
    item {
        SettingLineCard(
            "保存位置",
            StorageTargets.displayName(StorageTargets.sanitizeDefault(settings.defaultStorageTarget)),
            "▣",
            "›",
            LocalYtdlAppPalette.current.settingsAccent,
            subtitleMaxLines = 1,
            modifier = Modifier
                .clickable(onClick = onSelectStorageTarget)
                .testTag("ytdl-settings-storage-target"),
            supportingContent = {
                TextButton(
                    onClick = onResetStorageTarget,
                    enabled = StorageTargets.sanitizeDefault(settings.defaultStorageTarget) is StorageTarget.SafTree,
                    modifier = Modifier.testTag("ytdl-settings-storage-target-reset"),
                ) {
                    Text("恢复默认路径")
                }
            },
        )
    }
    item {
        SettingLineCard(
            "清理下载缓存",
            downloadCacheSubtitle,
            "⌫",
            "›",
            LocalYtdlAppPalette.current.settingsAccent,
            modifier = Modifier
                .clickable(onClick = onRequestDownloadCacheClear)
                .testTag("ytdl-settings-cache-clear"),
        )
    }
    item {
        SettingLineCard(
            "Cookies 文件",
            settingsCookiesSubtitle(settings),
            "▤",
            "按网站管理",
            LocalYtdlAppPalette.current.settingsAccent,
            modifier = Modifier
                .clickable(onClick = onSelectCookies)
                .testTag("ytdl-settings-cookies-picker"),
        )
    }
    item {
        SettingLineCard(
            "解析器版本",
            parserVersionSubtitle,
            "◇",
            "›",
            Color(0xFFE7A600),
            modifier = Modifier
                .clickable(onClick = onShowParserStatus)
                .testTag("ytdl-settings-parser-version"),
        )
    }
    item {
        SettingLineCard(
            "媒体处理能力",
            settingsMediaProcessorLabel(),
            "⚙",
            "›",
            LocalYtdlAppPalette.current.settingsAccent,
            modifier = Modifier
                .clickable(onClick = onShowMediaProcessorExplanation)
                .testTag("ytdl-settings-media-processor"),
        )
    }
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
    item { SettingLineCard("关于", "版本 ${BuildConfig.VERSION_NAME}", "i", "›", Color(0xFF55606C)) }
    item {
        SettingLineCard("开源声明", "组件、许可证与项目来源", "i", "›",
            LocalYtdlAppPalette.current.settingsAccent,
            modifier = Modifier.clickable(onClick = onShowOpenSource).testTag("captainflash-open-source"))
    }
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
private fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    accent: Color,
    testTagPrefix: String? = null,
    enabledOptions: List<Boolean> = List(options.size) { true },
    onSelected: ((Int) -> Unit)? = null,
) {
    val palette = LocalYtdlAppPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.segmentedBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val enabled = enabledOptions.getOrElse(index) { true }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onSelected != null) {
                            Modifier.clickable(enabled = enabled) { onSelected(index) }
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
                color = when {
                    !enabled -> palette.mutedCardBackground
                    selected -> accent
                    else -> palette.cardBackground.copy(alpha = 0.7f)
                },
                contentColor = when {
                    !enabled -> palette.softText.copy(alpha = 0.55f)
                    selected -> palette.primaryActionText
                    else -> palette.neutralText
                },
                shape = RectangleShape,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolutionRow(
    row: FormatResolutionRow,
    onSelect: () -> Unit,
    onCodecSelect: (FormatCodecOption) -> Unit,
) {
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
            .heightIn(min = 48.dp)
            .background(if (row.selected) palette.formatAccent.copy(alpha = 0.11f) else Color.Transparent)
            .clickable(enabled = row.selectable, onClick = onSelect)
            .testTag("ytdl-format-row-$tagSuffix")
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (row.selected) "✓" else "○",
            color = if (row.selected) palette.formatAccent else palette.softText.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = row.label,
            modifier = Modifier.defaultMinSize(minWidth = 54.dp),
            color = if (row.selectable) palette.titleText else palette.softText.copy(alpha = 0.55f),
            fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (row.codecOptions.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.codecOptions.forEach { option ->
                    val codecTagSuffix = option.label.lowercase().filter { it.isLetterOrDigit() }
                    Surface(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("ytdl-format-codec-$tagSuffix-$codecTagSuffix")
                            .clickable(enabled = row.selectable) { onCodecSelect(option) },
                        color = if (option.selected) palette.formatAccent else palette.mutedCardBackground,
                        contentColor = if (option.selected) palette.controlActionText else palette.softText,
                        shape = RectangleShape,
                    ) {
                        Text(
                            option.label,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
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
    subtitleMaxLines: Int = 2,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
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
                Text(subtitle, color = subtitleColor, style = MaterialTheme.typography.bodySmall, maxLines = subtitleMaxLines, overflow = TextOverflow.Ellipsis)
                supportingContent?.invoke(this)
            }
            Text(trailing, color = subtitleColor, style = MaterialTheme.typography.titleSmall)
        }
    }

    if (inCard) {
        AppCard(
            modifier = modifier.semantics {
                if (!enabled) disabled()
            },
            content = content,
        )
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
    titleTag: String,
    subtitle: String,
    progress: QueueProgressPresentation,
    status: String,
    progressMeta: QueueProgressMeta? = null,
    supportingText: String = "",
    formatBadge: String,
    codecBadge: String,
    stageItems: List<QueueStageItem>,
    accent: Color,
    modifier: Modifier = Modifier,
    thumbnailBitmap: Bitmap? = null,
    onCancel: (() -> Unit)? = null,
    onPause: (() -> Unit)? = null,
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
                TaskCardTitle(title, titleTag)
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
                if (progressMeta != null) {
                    QueueProgressMetaRow(progressMeta)
                }
                if (supportingText.isNotBlank()) {
                    Text(supportingText, color = palette.softText, style = MaterialTheme.typography.labelSmall)
                }
                Row {
                if (onPause != null) {
                    TextButton(onClick = onPause, modifier = Modifier.height(36.dp).testTag("ytdl-queue-pause-action")) { Text("暂停") }
                }
                if (onCancel != null) {
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
                            "取消",
                            color = palette.downloadAccent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                }
            }
            CardTrailingBadges(
                status = status,
                statusAccent = accent,
                formatBadge = formatBadge,
                codecBadge = codecBadge,
                formatAccent = palette.formatAccent,
                statusTag = "ytdl-queue-status-badge",
                formatTag = "ytdl-queue-format-badge",
                codecTag = "ytdl-queue-codec-badge",
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TaskCardTitle(
    text: String,
    tag: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(
                iterations = TaskTitleMarqueeIterations,
                velocity = TaskTitleMarqueeVelocityDp.dp,
            )
            .testTag(tag),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = TaskTitleMaxLines,
        softWrap = TaskTitleSoftWrap,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun QueueProgressMetaRow(meta: QueueProgressMeta) {
    val color = LocalYtdlAppPalette.current.softText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = meta.contentDescription },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                DownloadTabIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(TaskProgressIconSizeDp.dp)
                    .testTag("ytdl-queue-download-speed-icon"),
                tint = color,
            )
            Text(meta.speed, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                StorageCapacityIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(TaskProgressIconSizeDp.dp)
                    .testTag("ytdl-queue-storage-icon"),
                tint = color,
            )
            Text(meta.transferred, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
    onRetry: () -> Unit,
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
                TaskCardTitle(item.title, "ytdl-history-title-${item.id}")
                if (item.meta.isNotBlank()) {
                    Text(item.meta, color = palette.softText, style = MaterialTheme.typography.bodySmall)
                }
                val actions = historyActionLabels(item)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    actions.forEach { action ->
                        val callback = when (action) {
                            "打开" -> onOpen
                            "分享" -> onShare
                            "再次下载" -> onRetry
                            else -> onDelete
                        }
                        HistoryActionChip(
                            action = action,
                            icon = historyActionIcon(action),
                            accent = if (action == "删除") palette.downloadAccent else palette.neutralText,
                            onClick = callback,
                            modifier = Modifier.testTag("ytdl-history-action-${item.id}-$action"),
                        )
                    }
                }
            }
            CardTrailingBadges(
                status = item.badge,
                statusAccent = historyStatusBadgeAccent(item, palette),
                formatBadge = item.formatBadge,
                codecBadge = item.codecBadge,
                formatAccent = palette.formatAccent,
                statusTag = "ytdl-history-status-badge",
                formatTag = "ytdl-history-format-badge",
                codecTag = "ytdl-history-codec-badge",
            )
        }
    }
}

private fun historyActionIcon(action: String): ImageVector = when (action) {
    "打开" -> HistoryOpenIcon
    "分享" -> HistoryShareIcon
    "再次下载" -> HistoryRetryIcon
    else -> HistoryDeleteIcon
}

@Composable
private fun HistoryActionChip(
    action: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(9.dp),
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 30.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = action,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                action,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CardTrailingBadges(
    status: String,
    statusAccent: Color,
    formatBadge: String,
    codecBadge: String,
    formatAccent: Color,
    statusTag: String,
    formatTag: String,
    codecTag: String,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CardPillBadge(
            text = status,
            accent = statusAccent,
            tag = statusTag,
        )
        if (codecBadge.isNotBlank()) {
            CardPillBadge(
                text = codecBadge,
                accent = formatAccent,
                tag = codecTag,
            )
        }
        if (formatBadge.isNotBlank()) {
            CardPillBadge(
                text = formatBadge,
                accent = formatAccent,
                tag = formatTag,
            )
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
        modifier = Modifier
            .size(width = CardPillBadgeMinWidth, height = CardPillBadgeMinHeight)
            .testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .defaultMinSize(minWidth = CardPillBadgeMinWidth, minHeight = CardPillBadgeMinHeight)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
