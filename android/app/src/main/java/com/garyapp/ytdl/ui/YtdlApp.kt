package com.garyapp.ytdl.ui

import android.app.Activity
import android.Manifest
import com.garyapp.ytdl.core.settings.cookiesForUrl
import android.content.ClipboardManager
import android.content.ClipData
import com.garyapp.ytdl.data.HistoryItemEntity
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.garyapp.ytdl.BuildConfig
import com.garyapp.ytdl.R
import com.garyapp.ytdl.core.files.FileKind
import com.garyapp.ytdl.download.outputType
import com.garyapp.ytdl.core.files.FileProbeResult
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Switch
import com.garyapp.ytdl.download.FileRecoveryAction
import com.garyapp.ytdl.download.HttpFileConflictPolicy
import com.garyapp.ytdl.download.TaskOutputType
import com.garyapp.ytdl.core.files.HttpFileProbe
import com.garyapp.ytdl.core.policy.DownloadLinkBatch
import com.garyapp.ytdl.core.policy.DownloadLinkImport
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.settings.AppLanguage
import com.garyapp.ytdl.core.settings.AppSettings
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.core.settings.CookiesReference as SettingsCookiesReference
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.storage.StorageTargets
import com.garyapp.ytdl.core.ytdlp.ParserRuntimeBootstrap
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
import com.garyapp.ytdl.ui.theme.YtdlThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import com.garyapp.ytdl.ui.theme.ytdlAppPaletteForPreset
import com.garyapp.ytdl.ui.theme.ytdlColorPresets
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Executor
import android.util.LruCache
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private val DefaultPalette = ytdlAppPaletteForPreset(AppearanceSettings.ColorPresetMorandi)
private val BottomBarGestureBuffer = 4.dp
private val HistoryFailureRed = Color(0xFFFF5B63)

private const val DefaultRuntimeMessage = "等待输入下载链接。"
private const val AnalysisCompleteRuntimeMessage = "分析完成，可以开始下载。"
private const val ThumbnailDecodeMaxBytes = 2 * 1024 * 1024
private val ProcessParserUpdateCoordinator = ParserUpdateCoordinator(
    checker = ParserUpdateChecker.forCurrentVersion(
        currentVersionProvider = { ParserRuntimeState.configuredVersion },
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
    moveTo(2f, 20f); lineTo(2f, 6f)
    quadTo(2f, 4f, 4f, 4f); lineTo(9f, 4f)
    lineTo(12f, 7f); lineTo(19f, 7f)
    quadTo(21f, 7f, 21f, 9f); lineTo(21f, 10f)
    lineTo(19f, 10f); lineTo(19f, 9f); lineTo(11f, 9f)
    lineTo(8f, 6f); lineTo(4f, 6f); lineTo(4f, 20f); close()
    moveTo(2f, 20f); lineTo(5f, 11f)
    quadTo(5.4f, 10f, 7f, 10f); lineTo(22f, 10f)
    quadTo(23.5f, 10f, 23f, 11.5f); lineTo(20f, 20f)
    quadTo(19.5f, 21f, 18f, 21f); lineTo(3f, 21f); close()
    moveTo(4.5f, 19f); lineTo(18.2f, 19f); lineTo(20.7f, 12f)
    lineTo(7f, 12f); close()
}

private val HistoryShareIcon = tabIcon("HistoryShare") {
    moveTo(18f, 16.08f)
    curveTo(17.24f, 16.08f, 16.56f, 16.38f, 16.04f, 16.85f)
    lineTo(8.91f, 12.7f)
    curveTo(8.96f, 12.47f, 9f, 12.24f, 9f, 12f)
    reflectiveCurveTo(8.96f, 11.53f, 8.91f, 11.3f)
    lineTo(15.96f, 7.19f)
    curveTo(16.5f, 7.69f, 17.21f, 8f, 18f, 8f)
    curveTo(19.66f, 8f, 21f, 6.66f, 21f, 5f)
    reflectiveCurveTo(19.66f, 2f, 18f, 2f)
    reflectiveCurveTo(15f, 3.34f, 15f, 5f)
    curveTo(15f, 5.24f, 15.04f, 5.47f, 15.09f, 5.7f)
    lineTo(8.04f, 9.81f)
    curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
    curveTo(4.34f, 9f, 3f, 10.34f, 3f, 12f)
    reflectiveCurveTo(4.34f, 15f, 6f, 15f)
    curveTo(6.79f, 15f, 7.5f, 14.69f, 8.04f, 14.19f)
    lineTo(15.16f, 18.35f)
    curveTo(15.11f, 18.56f, 15.08f, 18.78f, 15.08f, 19f)
    curveTo(15.08f, 20.61f, 16.39f, 21.92f, 18f, 21.92f)
    reflectiveCurveTo(20.92f, 20.61f, 20.92f, 19f)
    reflectiveCurveTo(19.61f, 16.08f, 18f, 16.08f)
    close()
}

private val HistoryRetryIcon = tabIcon("HistoryRetry") {
    moveTo(17.65f, 6.35f)
    curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
    curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
    reflectiveCurveTo(7.58f, 20f, 12f, 20f)
    curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
    horizontalLineTo(17.65f)
    curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
    curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
    reflectiveCurveTo(8.69f, 6f, 12f, 6f)
    curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
    lineTo(13f, 11f)
    horizontalLineTo(20f)
    verticalLineTo(4f)
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
    val displayName: String = "",
    val localFile: File? = null,
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
    val torrentSource: String? = null,
    val torrentTitle: String = "",
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
    val taskMessage: String = "",
    val taskActionMessage: String = "",
    val progressPercent: Double? = null,
    val overallProgressPercent: Double? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Double? = null,
    val etaSeconds: Long? = null,
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
    val eta: String? = null,
)

internal data class FormatSettingSummaries(
    val frameRate: String,
    val videoCodec: String,
    val container: String,
)

fun ytdlNavigationDestinations(): List<YtdlDestination> = ytdlNavigationDestinations(DefaultPalette)

private fun ytdlNavigationDestinations(
    palette: YtdlAppPalette,
    language: AppLanguage = AppLanguage.Chinese,
): List<YtdlDestination> = listOf(
    YtdlDestination(
        route = "download",
        label = language.pick("下载", "Download"),
        title = language.pick("新建下载", "New download"),
        summary = language.pick("识别链接后，显示对应的下载选项", "Analyze a link to see its download options"),
        icon = DownloadTabIcon,
        accent = palette.downloadAccent,
    ),
    YtdlDestination(
        route = "tasks",
        label = language.pick("任务", "Tasks"),
        title = language.pick("全部任务", "All tasks"),
        summary = language.pick("查看当前、等待和历史任务。", "View active, queued, and past tasks."),
        icon = QueueTabIcon,
        accent = palette.queueAccent,
    ),
    YtdlDestination(
        route = "settings",
        label = language.pick("设置", "Settings"),
        title = language.pick("设置", "Settings"),
        summary = language.pick("管理保存位置、解析器和外观。", "Manage storage, the parser, and appearance."),
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
    showFirstRunGuide: Boolean = false,
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
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
    var focusedDownloadTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var taskStatusNames by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var taskTypeNames by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var selectedSettingsCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFileRecovery by remember { mutableStateOf<ResumeTaskItem?>(null) }
    var clearHistorySnapshot by remember { mutableStateOf<List<HistoryUiItem>?>(null) }
    var exportSourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var exportIsTorrent by rememberSaveable { mutableStateOf(false) }
    BackHandler(selectedRoute == "settings" && selectedSettingsCategory != null) {
        selectedSettingsCategory = null
    }
    val settingsRepository = remember { SettingsRepository.fromContext(context.applicationContext) }
    val retryDraftStore = remember(retryDraftStoreOverride) {
        retryDraftStoreOverride ?: FileRetryDraftStore.fromContext(context.applicationContext)
    }
    var appSettings by remember { mutableStateOf(settingsRepository.getSettings()) }
    val appLanguage = appSettings.language.resolve(LocalConfiguration.current.locales[0])
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
    var showStorageChoice by remember { mutableStateOf(false) }
    var queueImportAfterStorageChoice by remember { mutableStateOf(false) }
    var showDownloadCacheConfirmation by remember { mutableStateOf(false) }
    var savedCacheSnapshot by remember { mutableStateOf(emptyList<DownloadCoordinator.SavedCacheEntry>()) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val downloadCache = remember { downloadCacheFactory(context.applicationContext) }
    val downloadCacheRefreshGeneration = remember { AtomicLong(0) }
    val bridge = remember(context) { YtdlpBridge(pythonProvider = { ParserRuntimeBootstrap.python(context.applicationContext) }) }
    val notificationController = remember { NotificationController(context.applicationContext) }
    val notificationRuntimePermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var historyAvailabilityRevision by remember { mutableStateOf(0) }
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
                .listAll()
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
        if (route != selectedRoute) runtimeState = runtimeState.copy(taskActionMessage = "")
        selectedRoute = route
        if (route == "tasks") {
            refreshHistory()
        } else if (route == "settings") {
            refreshDownloadCacheStats()
        }
    }

    fun persistStorageTarget(target: StorageTarget) {
        // Existing requests pin their directory, and history can still reference documents there.
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

    fun showCacheCleanup() {
        if (savedDownloadTasks.isEmpty()) {
            savedCacheSnapshot = emptyList()
            showDownloadCacheConfirmation = true
            return
        }
        downloadCacheExecutor.execute {
            val entries = DownloadCoordinator.inspectSavedCache()
            mainHandler.post {
                savedCacheSnapshot = entries
                showDownloadCacheConfirmation = true
            }
        }
    }

    fun clearDownloadCache() {
        val confirmed = savedCacheSnapshot
        var removedTasks = 0
        downloadCacheExecutor.execute {
            val result = runCatching {
                when (val cleanup = DownloadCoordinator.runWhenIdle {
                    removedTasks = DownloadCoordinator.clearSavedCache(context.applicationContext, confirmed)
                    downloadCache.clear()
                }) {
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
                if (removedTasks > 0) refreshHistory()
                val tasksResult = if (confirmed.isEmpty()) "" else appLanguage.pick("\n已清理 $removedTasks / ${confirmed.size} 项任务缓存。", "\nCleared temporary files for $removedTasks of ${confirmed.size} tasks.")
                settingsExplanation = result.fold(
                    onSuccess = { clearResult ->
                        when (clearResult) {
                            CacheClearResult.BlockedByActiveDownload -> SettingsExplanation(
                                title = appLanguage.pick("无法清理缓存", "Could not clear cache"),
                                body = appLanguage.pick("当前有下载任务正在运行。为避免删除任务仍在使用的文件，请等待下载结束后再清理。", "Wait for active downloads to finish before clearing their temporary files."),
                            )
                            is CacheClearResult.Success -> SettingsExplanation(
                                title = appLanguage.pick("清理完成", "Cache cleared"),
                                body = appLanguage.pick("已释放 ${formatBytes(clearResult.freedBytes)}，删除 ${clearResult.deletedFileCount} 个文件。$tasksResult", "Freed ${formatBytes(clearResult.freedBytes)}. Files removed: ${clearResult.deletedFileCount}.$tasksResult"),
                            )
                            is CacheClearResult.Incomplete -> SettingsExplanation(
                                title = appLanguage.pick("部分临时文件未能清理", "Some temporary files could not be removed"),
                                body = appLanguage.pick(
                                    "已释放 ${formatBytes(clearResult.freedBytes)}，删除 ${clearResult.deletedFileCount} 个文件；仍有 ${formatBytes(clearResult.remainingBytes)}、${clearResult.remainingFileCount} 个临时文件，请稍后重试。",
                                    "Freed ${formatBytes(clearResult.freedBytes)}. Files removed: ${clearResult.deletedFileCount}. Remaining: ${formatBytes(clearResult.remainingBytes)} in ${clearResult.remainingFileCount} temporary files. Try again later.",
                                ),
                            )
                        }
                    },
                    onFailure = {
                        SettingsExplanation(
                            title = appLanguage.pick("清理失败", "Could not clear cache"),
                            body = appLanguage.pick("无法清理 App 私有临时缓存，请稍后重试。", "Could not remove temporary files. Try again later."),
                        )
                    },
                )
            }
        }
    }

    var importedBatch by remember { mutableStateOf<DownloadLinkBatch?>(null) }
    var importSubmissionCount by remember { mutableStateOf<Int?>(null) }
    var importQueueFull by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }
    var importingLinks by remember { mutableStateOf(false) }
    val importError = appLanguage.pick(
        "无法导入。请选择 UTF-8 TXT 或 Markdown 地址列表，最多 500 条链接。",
        "Choose a UTF-8 TXT or Markdown address list with up to 500 links.",
    )
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
            settingsExplanation = SettingsExplanation("Cookies 文件", "文件引用无效，请重新选择 cookies.txt。")
        } else {
            appSettings = if (pendingCookiesDomain.isBlank()) settingsRepository.setCookiesReference(reference)
            else settingsRepository.setSiteCookies(pendingCookiesName, pendingCookiesDomain, reference)
        }
    }

    val siteLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val domain = com.garyapp.ytdl.core.settings.CookieSites.normalizeDomain(data.getStringExtra("domain").orEmpty())
            val reference = SettingsCookiesReference.fromUserReference(data.getStringExtra("reference").orEmpty(), "App 内登录")
            if (domain != null && domain == pendingCookiesDomain && reference != null) {
                appSettings = settingsRepository.setSiteCookies(pendingCookiesName, domain, reference)
            }
        }
    }

    val storageTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            if (queueImportAfterStorageChoice) previewOpen = true
            queueImportAfterStorageChoice = false
            return@rememberLauncherForActivityResult
        }
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
            queueImportAfterStorageChoice = false
            val message = "无法保存该文件夹授权，请重新选择保存位置。"
            if (selectedRoute == "settings") settingsExplanation = SettingsExplanation("保存位置", message)
            else runtimeState = runtimeState.copy(userMessage = message)
        } else {
            persistStorageTarget(target)
        }
    }

    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = !notificationRuntimePermissionRequired || granted

    }

    DisposableEffect(Unit) {
        if (currentSelectedRoute == "tasks") refreshHistory()
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
        Thread { runCatching { DownloadCoordinator.restoreScheduledDownloads(context.applicationContext) } }.start()
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
                historyAvailabilityRevision++
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

    var scheduledAtMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSchedulePermission by remember { mutableStateOf(false) }

    fun replaceUrl(updatedUrl: String) {
        if (updatedUrl == runtimeState.url) return
        scheduledAtMillis = null
        analysisRequestRevision.incrementAndGet()
        confirmExtensionChange = false
        showAdvancedFormats = false
        runtimeState = runtimeState.withNewUrl(updatedUrl)
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            selectedRoute = "download"
            replaceUrl(sharedUrl)
            onSharedUrlConsumed()
        }
    }

    var submittingImport by remember { mutableStateOf(false) }

    fun queueImportedFiles(batch: DownloadLinkBatch) {
        if (submittingImport) return
        if (!appSettings.storageChoiceConfirmed) {
            queueImportAfterStorageChoice = true
            previewOpen = false
            showStorageChoice = true
            return
        }
        val settings = appSettings
        val appContext = context.applicationContext
        submittingImport = true
        Thread {
            val accepted = mutableSetOf<String>()
            var queueFull = false
            val probe = HttpFileProbe()
            val output = File(appContext.filesDir, "gui-downloads").apply { mkdirs() }
            for (url in batch.urls) {
                val result = runCatching { fileProbeProvider?.invoke(url) ?: probe.probe(url) }.getOrNull()
                val metadata = (result as? FileProbeResult.FileFound)?.metadata ?: continue
                if (metadata.kind != com.garyapp.ytdl.core.files.FileKind.Other || metadata.isStreamManifest()) continue
                val selection = FileDownloadSelection(metadata, conflictPolicy = settings.fileConflictPolicy)
                val request = selection.request(settings.defaultStorageTarget) ?: continue
                val started = runCatching {
                    downloadStarter(appContext, request.copy(resumeTransfers = settings.resumeDownloads), output)
                }.getOrElse { Result.failure(it) }
                if (started.isSuccess) accepted += url
                if (started.exceptionOrNull() is com.garyapp.ytdl.download.WaitingQueueFullException) {
                    queueFull = true
                    break
                }
            }
            mainHandler.post {
                importedBatch = batch.copy(urls = batch.urls.filterNot(accepted::contains))
                importSubmissionCount = accepted.size
                importQueueFull = queueFull
                submittingImport = false
            }
        }.start()
    }

    LaunchedEffect(queueImportAfterStorageChoice, appSettings.storageChoiceConfirmed) {
        if (queueImportAfterStorageChoice && appSettings.storageChoiceConfirmed) {
            queueImportAfterStorageChoice = false
            importedBatch?.let {
                previewOpen = true
                queueImportedFiles(it)
            }
        }
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
        onTorrentFound: ((String) -> Unit)? = null,
    ) {
        val url = requestedUrl.trim()
        if (com.garyapp.ytdl.core.policy.isMagnetDownloadAddress(url)) {
            if (onTorrentFound != null) onTorrentFound(url)
            else runtimeState = runtimeState.copy(userMessage = appLanguage.pick("请使用 Torrent 版分析磁力链接。", "Use CaptainFlash Torrent for magnet links."))
            return
        }
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
            val probe = if (retryDraft == null) {
                when {
                    fileProbeProvider != null -> fileProbeProvider(url)
                    analysisProvider == null -> HttpFileProbe().probe(url)
                    else -> null
                }
            } else null
            val probedFile = (probe as? FileProbeResult.FileFound)?.metadata
            if (probedFile != null && (
                    probedFile.mimeType?.substringBefore(';')?.trim().equals("application/x-bittorrent", ignoreCase = true) ||
                        probedFile.fileName.endsWith(".torrent", ignoreCase = true)
                    )
            ) {
                mainHandler.post {
                    if (analysisRequestRevision.get() == requestRevision) {
                        runtimeState = runtimeState.copy(isAnalyzing = false)
                        if (onTorrentFound != null) onTorrentFound(probedFile.finalUrl)
                        else runtimeState = runtimeState.copy(userMessage = appLanguage.pick(
                            "请使用 Torrent 版分析种子链接。", "Use CaptainFlash Torrent to analyze torrent links.",
                        ))
                    }
                }
                return@Thread
            }
            val fileSelection = probedFile?.takeUnless { it.isStreamManifest() }?.let { metadata ->
                FileDownloadSelection(
                    metadata = metadata,
                    fileName = metadata.fileName,
                    conflictPolicy = settings.fileConflictPolicy,
                )
            }
            if (fileSelection != null && fileSelection.metadata.kind == FileKind.Other) {
                mainHandler.post {
                    if (analysisRequestRevision.get() == requestRevision) {
                        runtimeState = runtimeState.withFileRecognition(fileSelection)
                    }
                }
                return@Thread
            }
            if (analysisRequestRevision.get() != requestRevision) return@Thread
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
                        val analyzed = runtimeState.withAnalysisResult(analysis).let { result ->
                            if (!appSettings.downloadDetectedSubtitles || result.appliedFormatSelection.mode == FormatMode.AudioOnly) result
                            else result.copy(selectedSubtitles = analysis.subtitles
                                .sortedBy { it.source.ordinal }.distinctBy { it.language.lowercase(java.util.Locale.ROOT) }
                                .take(256))
                        }
                        if (retryDraft == null) {
                            analyzed
                        } else {
                            val restoredSelection = retryFormatSelection(analysis, retryDraft)
                            showAdvancedFormats = true
                            analyzed.copy(
                                formatSelection = restoredSelection,
                                appliedFormatSelection = restoredSelection,
                                selectedSubtitles = if (restoredSelection.mode == FormatMode.AudioOnly) emptyList()
                                    else retryDraft.selectedSubtitles.filter { it in analysis.subtitles },
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
        if (!appSettings.storageChoiceConfirmed) {
            showStorageChoice = true
            return
        }
        val scheduledAt = scheduledAtMillis
        if (scheduledAt != null && scheduledAt <= System.currentTimeMillis()) {
            scheduledAtMillis = null
            runtimeState = runtimeState.copy(userMessage = "预约时间已过，请重新选择。")
            return
        }
        if (scheduledAt != null && Build.VERSION.SDK_INT >= 31 &&
            !context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            showSchedulePermission = true
            return
        }
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
            val selectedCookies = if (fileSelection == null && captured.torrentSource == null) settings.cookiesForUrl(url) else null
            val cookiesResult = prepareTemporaryCookiesForDownload(
                settingsReference = selectedCookies,
                context = context.applicationContext,
                taskId = "download-${System.nanoTime()}",
            )
            val temporaryCookies = cookiesResult.getOrNull()
            val requestResult = if (captured.torrentSource != null) {
                Result.success(DownloadRequest(url = captured.torrentSource, title = captured.torrentTitle,
                    route = DownloadRoute.Torrent(captured.torrentSource), formatSummary = "Torrent",
                    usesCookies = false, storageTarget = target))
            } else if (fileSelection != null) {
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
                downloadStarter(context.applicationContext, it.copy(
                    resumeTransfers = settings.resumeDownloads,
                    scheduledAtMillis = scheduledAt,
                ), outputDir)
            }
            if (startResult?.isSuccess != true) temporaryCookies?.delete()
            mainHandler.post {
                runtimeState = runtimeState.copy(isStarting = false)
                if (analysisRequestRevision.get() != revision) return@post
                runtimeState = when {
                    startResult?.isSuccess == true -> {
                        scheduledAtMillis = null
                        val accepted = startResult.getOrThrow()
                        if (settings.jumpToDownloadTask || captured.torrentSource != null) {
                            focusedDownloadTaskId = accepted.request?.taskId ?: requestResult.getOrNull()?.taskId
                            historyQuery = ""
                            taskStatusNames = arrayListOf()
                            taskTypeNames = arrayListOf()
                            selectRoute("tasks")
                        }
                        runtimeState.withAcceptedDownload(accepted)
                    }
                    startResult?.exceptionOrNull() is com.garyapp.ytdl.download.WaitingQueueFullException ->
                        runtimeState.copy(userMessage = startResult.exceptionOrNull()?.message.orEmpty())
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

    fun redownload(draft: RetryDownloadDraft) {
        val route = draft.route
        if (route is DownloadRoute.Torrent) {
            selectRoute("download")
            runtimeState = runtimeState.withNewUrl(route.source).copy(torrentSource = route.source, torrentTitle = "Torrent")
            return
        }
        if (route !is DownloadRoute.HttpFile) {
            selectRoute("download")
            analyzeUrl(requestedUrl = draft.url, retryDraft = draft)
            return
        }
        val settings = appSettings
        val request = DownloadRequest(
            url = draft.url,
            title = route.fileName,
            route = route,
            storageTarget = settings.defaultStorageTarget,
            resumeTransfers = settings.resumeDownloads,
        )
        Thread {
            val outputDir = File(context.filesDir, "gui-downloads").apply { mkdirs() }
            val result = downloadStarter(context.applicationContext, request, outputDir)
            mainHandler.post {
                result.fold(
                    onSuccess = {
                        focusedDownloadTaskId = it.request?.taskId ?: request.taskId
                        historyQuery = ""
                        taskStatusNames = arrayListOf()
                        taskTypeNames = arrayListOf()
                        runtimeState = runtimeState.copy(taskActionMessage = "")
                    },
                    onFailure = {
                        val message = if (it is com.garyapp.ytdl.download.WaitingQueueFullException)
                            it.message.orEmpty() else "无法启动下载，请重试。"
                        runtimeState = runtimeState.copy(taskActionMessage = message)
                    },
                )
            }
        }.start()
    }

    fun retryHistoryItem(item: HistoryUiItem) {
        Thread {
            val draftResult = retryDraftStore.load(item.id)
            mainHandler.post {
                draftResult.fold(
                    onSuccess = ::redownload,
                    onFailure = {
                        runtimeState = runtimeState.copy(taskActionMessage = "原下载信息读取失败，任务记录已保留。")
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

    fun openHistoryOutputStream(sourceUri: String): InputStream {
        if (sourceUri.startsWith("app-private:")) {
            return outputForAppPrivateUri(sourceUri).getOrThrow().sourceFile.inputStream()
        }
        return requireNotNull(context.contentResolver.openInputStream(Uri.parse(sourceUri)))
    }

    fun checkHistoryOutputReadable(sourceUri: String) {
        openHistoryOutputStream(sourceUri).use { it.read() }
    }

    fun readableHistoryOutput(reference: HistoryOutputReference): HistoryOutputTarget? = runCatching {
        val uri = Uri.parse(reference.uri)
        val resolver = context.contentResolver
        val privateOutput = outputForAppPrivateUri(reference.uri).getOrNull()
        val output = if (privateOutput != null) {
            HistoryOutputTarget(reference.uri, reference.mimeType ?: privateOutput.mimeType, privateOutput.sourceFile.name,
                localFile = privateOutput.sourceFile)
        } else {
            requireNotNull(historyContentOutputTarget(reference.uri,
                reference.mimeType ?: runCatching { resolver.getType(uri) }.getOrNull()))
                .copy(displayName = displayNameForUri(context, uri, reference.label))
        }
        checkHistoryOutputReadable(output.uri)
        output
    }.getOrNull()

    val historyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val sourceUri = exportSourceUri
        val torrentExport = exportIsTorrent
        exportSourceUri = null
        exportIsTorrent = false
        val destination = result.data?.data
        if (sourceUri != null && destination != null && result.resultCode == Activity.RESULT_OK) {
            Thread {
                val outcome = runCatching {
                    check(Uri.parse(sourceUri) != destination)
                    val resolver = context.contentResolver
                    openHistoryOutputStream(sourceUri).use { input ->
                        requireNotNull(resolver.openOutputStream(destination, "wt")).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                mainHandler.post {
                    runtimeState = runtimeState.copy(taskActionMessage = if (outcome.isSuccess) {
                        if (torrentExport) appLanguage.pick("种子文件已导出。", "Torrent file exported.") else "已导出文件。"
                    } else "导出未完成，请检查源文件和目标位置。")
                }
            }.start()
        }
    }

    fun launchHistoryOutputAction(action: HistoryFileAction, output: HistoryOutputTarget) {
        val uri = Uri.parse(output.uri)
        if (action == HistoryFileAction.Export) {
            exportSourceUri = output.uri
            exportIsTorrent = output.mimeType == "application/x-bittorrent"
            runCatching {
                historyExportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(output.mimeType)
                    .putExtra(Intent.EXTRA_TITLE, output.displayName))
            }.onFailure {
                exportSourceUri = null
                runtimeState = runtimeState.copy(taskActionMessage = "无法打开系统文件选择器。")
            }
            return
        }
        val intent = if (action == HistoryFileAction.Open) {
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, output.mimeType)
        } else {
            Intent(Intent.ACTION_SEND).setType(output.mimeType).putExtra(Intent.EXTRA_STREAM, uri)
                .apply { clipData = android.content.ClipData.newRawUri(output.displayName, uri) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            context.startActivity(Intent.createChooser(intent, "${action.label}下载文件"))
        }.onFailure {
            runtimeState = runtimeState.copy(taskActionMessage = "没有可用应用${action.label}该文件。")
        }
    }

    fun copyHistorySource(item: HistoryUiItem) {
        Thread {
            val result = runCatching {
                historySourceTarget(item, retryDraftStore.load(item.id).getOrThrow(), context.filesDir)
            }
            mainHandler.post {
                result.fold(onSuccess = { target ->
                    when (target) {
                        is HistorySourceTarget.Address -> {
                            val copied = runCatching {
                                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                    ClipData.newPlainText(appLanguage.pick("下载地址", "Download address"), target.value),
                                )
                            }
                            runtimeState = runtimeState.copy(taskActionMessage = if (copied.isSuccess)
                                appLanguage.pick("复制成功", "Copied") else appLanguage.pick("复制失败，请重试。", "Could not copy. Try again."))
                            if (copied.isSuccess) android.widget.Toast.makeText(context,
                                appLanguage.pick("复制成功", "Copied"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is HistorySourceTarget.TorrentFile -> launchHistoryOutputAction(HistoryFileAction.Export,
                            HistoryOutputTarget(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target.file).toString(),
                                "application/x-bittorrent", "torrent-${item.id}.torrent", target.file))
                    }
                }, onFailure = {
                    runtimeState = runtimeState.copy(taskActionMessage = appLanguage.pick(
                        "原下载地址或种子文件不可用。", "The original address or torrent file is unavailable.",
                    ))
                })
            }
        }.start()
    }

    fun performHistoryOutputAction(action: HistoryFileAction, output: HistoryOutputTarget) {
        Thread {
            val checked = runCatching {
                checkHistoryOutputReadable(output.uri)
                if (action == HistoryFileAction.Export || !output.uri.startsWith("app-private:")) {
                    output
                } else {
                    val privateFile = outputForAppPrivateUri(output.uri).getOrThrow()
                    output.copy(uri = fileProviderUri(privateFile).toString())
                }
            }.getOrNull()
            mainHandler.post {
                if (checked != null) {
                    launchHistoryOutputAction(action, checked)
                } else {
                    historyAvailabilityRevision++
                    runtimeState = runtimeState.copy(taskActionMessage = historyMissingLocalOutputMessage())
                }
            }
        }.start()
    }

    fun deleteHistoryItems(items: List<HistoryUiItem>, deleteFiles: Boolean, fromSettings: Boolean = false) {
        Thread {
            val database = YtdlDatabaseProvider.get(context.applicationContext)
            val historyDao = database.historyDao()
            val fileDeletion = HistoryFileDeletion(
                File(context.filesDir, "gui-downloads"),
                File(context.cacheDir, "gui-downloads"),
                AndroidHistorySafDocuments(context.applicationContext),
            )
            val outcomes = items.map { item ->
                val removeRecord = {
                    deleteHistoryWithRetryPayload(item.id, retryDraftStore, { historyDao.deleteById(item.id) },
                        beforeDeleteFiles = {
                            val selected = historyDao.getById(item.id)
                            if (selected == null) Result.failure(IllegalStateException(appLanguage.pick("历史记录已不存在。", "The history entry is already gone.")))
                            else fileDeletion.delete(selected, historyDao.listAll(),
                                database.queueDao().listNonTerminal().map { it.taskId }.toSet()).map { Unit }
                        })
                }
                val idleResult = if (deleteFiles) fileDeletion.runWhenSafe(historyDao.getById(item.id), removeRecord)
                    else IdleDownloadActionResult.Executed(deleteHistoryWithRetryPayload(item.id, retryDraftStore,
                        deleteHistory = { historyDao.deleteById(item.id) }))
                when (idleResult) {
                    is IdleDownloadActionResult.Executed -> idleResult.value
                    IdleDownloadActionResult.ActiveDownload -> Result.failure(IllegalStateException("下载任务正在执行。"))
                }
            }
            val deleted = outcomes.sumOf { it.getOrDefault(0) }
            val failure = outcomes.firstNotNullOfOrNull { it.exceptionOrNull() }
            val refreshed = historyUiItemsFromRows(historyDao.listAll(), retryDraftStore::isAvailable)
            mainHandler.post {
                historyItems = refreshed
                val message = when {
                    failure != null && items.size > 1 -> appLanguage.pick("已清除 $deleted 条记录，其余记录保留，请检查后重试。", "Removed $deleted records. Check the remaining records and try again.")
                    failure is HistoryFileDeleteFailure && failure.deletedCount > 0 ->
                        appLanguage.pick("部分文件已删除，历史记录已保留，请检查后重试。", "Some files were deleted. The history entry is still available. Check the files and try again.")
                    failure is HistoryFileDeleteFailure ->
                        appLanguage.pick("文件未删除，历史记录已保留，请检查权限、共享引用或任务状态。", "The files could not be deleted. Check permissions and whether another task uses the files.")
                    failure != null -> appLanguage.pick("删除未完成，请检查是否有下载或导出正在进行。", "Deletion did not complete. Check for active downloads or exports.")
                    deleted > 0 && deleteFiles -> appLanguage.pick("已删除历史记录，关联文件已处理。", "History removed and associated files processed.")
                    deleted > 0 -> appLanguage.pick("已删除历史记录。", "History entry removed.")
                    else -> appLanguage.pick("历史记录已不存在。", "The history entry is already gone.")
                }
                runtimeState = runtimeState.copy(taskActionMessage = message)
                if (fromSettings) settingsExplanation = SettingsExplanation(appLanguage.pick("清除任务记录", "Clear task history"), message)
            }
        }.start()
    }

    fun runRecoveryAction(task: ResumeTaskItem, name: String? = null) {
        Thread {
            val application = context.applicationContext
            val outcome = runCatching {
                when (task.state.fileRecoveryAction) {
                    FileRecoveryAction.ConfirmRestart -> {
                        val url = requireNotNull(task.state.request).url
                        val probe = HttpFileProbe().probe(url)
                        val metadata = (probe as? FileProbeResult.FileFound)?.metadata
                            ?: error("未识别到可下载的文件。")
                        DownloadCoordinator.confirmFileRestart(application, task.taskId, metadata).getOrThrow()
                    }
                    FileRecoveryAction.ChooseFileName ->
                        DownloadCoordinator.chooseFileName(application, task.taskId, requireNotNull(name)).getOrThrow()
                    else -> DownloadCoordinator.resume(application, task.taskId).getOrThrow()
                }
            }
            mainHandler.post {
                if (outcome.isFailure) {
                    runtimeState = runtimeState.copy(taskActionMessage = "无法继续下载，任务已保留，请检查链接或文件名后重试。")
                }
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
    YtdlTheme(config = themeConfig, appLanguage = appLanguage) {
        val openTorrentDownload: ((String) -> Unit)? = if (BuildConfig.TORRENT_ENABLED) { source ->
            val uri = Uri.parse(source)
            val magnetName = source.substringAfter('?', "").split('&').firstOrNull { it.startsWith("dn=") }
                ?.substringAfter('=')?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            val title = if (uri.scheme == "content") displayNameForUri(context, uri, "Torrent")
                else if (uri.scheme == "magnet") magnetName?.takeIf { it.isNotBlank() }?.take(240) ?: "Torrent"
                else runCatching { uri.getQueryParameter("dn") }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "Torrent"
            runtimeState = runtimeState.withNewUrl(source).copy(torrentSource = source, torrentTitle = title)
        } else null
        val importLinksPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null || importingLinks) return@rememberLauncherForActivityResult
            if (openTorrentDownload != null && (
                    context.contentResolver.getType(uri) == "application/x-bittorrent" ||
                        displayNameForUri(context, uri, "").endsWith(".torrent", ignoreCase = true)
                    )
            ) {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                openTorrentDownload(uri.toString())
                return@rememberLauncherForActivityResult
            }
            importingLinks = true
            Thread {
                val result = runCatching {
                    requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use(DownloadLinkImport::read)
                }
                mainHandler.post {
                    importingLinks = false
                    result.onSuccess {
                        importedBatch = it
                        importSubmissionCount = null
                        importQueueFull = false
                        previewOpen = true
                    }.onFailure {
                        runtimeState = runtimeState.copy(userMessage = importError)
                    }
                }
            }.start()
        }
        var browserOpen by remember { mutableStateOf(false) }
        if (browserOpen) {
            DownloadBrowser(runtimeState.url, onClose = { browserOpen = false }) { url ->
                browserOpen = false
                replaceUrl(url)
                analyzeUrl(url, onTorrentFound = openTorrentDownload)
            }
            return@YtdlTheme
        }
        importedBatch?.let { batch ->
            if (previewOpen) {
                AlertDialog(
                    onDismissRequest = { if (!submittingImport) previewOpen = false },
                    title = { Text(appText("导入链接", "Imported links")) },
                    text = {
                        Column {
                            Text(appText(
                                "${batch.urls.size} 条待处理 · ${batch.duplicateCount} 条重复 · ${batch.invalidCount} 条无效",
                                "${batch.urls.size} pending · ${batch.duplicateCount} duplicates · ${batch.invalidCount} invalid",
                            ))
                            if (!submittingImport) {
                                importSubmissionCount?.let { count ->
                                    Text(appText("本次已提交 $count 条。", "$count submitted in this batch."))
                                }
                                if (importQueueFull) Text(appText(
                                    "等待队列已满，其余链接仍在列表中。",
                                    "The queue is full. The remaining links are still in this list.",
                                ))
                            }
                            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                                items(batch.urls.size) { index ->
                                    TextButton(enabled = !submittingImport, onClick = {
                                        previewOpen = false
                                        replaceUrl(batch.urls[index])
                                        importedBatch = batch.copy(urls = batch.urls.filterIndexed { i, _ -> i != index })
                                    }) {
                                        Text(batch.urls[index], maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = batch.urls.isNotEmpty() && !submittingImport,
                            onClick = { queueImportedFiles(batch) },
                        ) {
                            Text(if (submittingImport) appText("正在识别…", "Checking…")
                            else appText("普通文件入队", "Queue regular files"))
                        }
                    },
                    dismissButton = {
                        TextButton(enabled = !submittingImport, onClick = { previewOpen = false }) {
                            Text(appText("关闭", "Close"))
                        }
                    },
                )
            }
        }
        val palette = LocalYtdlAppPalette.current
        val destinations = ytdlNavigationDestinations(palette, appLanguage)
        val selected = destinations.firstOrNull { it.route == selectedRoute } ?: destinations.first()

        Scaffold(
            containerColor = palette.appBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Column(Modifier.background(palette.appBackground)) {
                    if (selected.route == "download" && canStartDownload(runtimeState)) {
                        DownloadStartButton(runtimeState, onClick = { startRealDownload() }, scheduled = scheduledAtMillis != null)
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
                key(selected.route, selectedSettingsCategory.takeIf { selected.route == "settings" }) {
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
                            onAnalyze = {
                                if (runtimeState.isAnalyzing) {
                                    analysisRequestRevision.incrementAndGet()
                                    runtimeState = runtimeState.copy(isAnalyzing = false, userMessage = DefaultRuntimeMessage)
                                } else {
                                    val input = runtimeState.url.trim()
                                    if (input.startsWith("magnet:", ignoreCase = true) ||
                                        android.net.Uri.parse(input).path.orEmpty().endsWith(".torrent", ignoreCase = true)
                                    ) {
                                        if (openTorrentDownload != null) openTorrentDownload(input)
                                        else runtimeState = runtimeState.copy(userMessage = appLanguage.pick("请使用 Torrent 版分析种子链接。", "Use CaptainFlash Torrent to analyze torrent links."))
                                    } else analyzeUrl(input, onTorrentFound = openTorrentDownload)
                                }
                            },
                            onStartDownload = { startRealDownload() },
                            onPaste = ::pasteDownloadUrl,
                            onImportLinks = {
                                if (importedBatch?.urls.orEmpty().isNotEmpty()) previewOpen = true
                                else importLinksPicker.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "application/octet-stream", "application/x-bittorrent"))
                            },
                            pendingImportCount = importedBatch?.urls.orEmpty().size,
                            importEnabled = !importingLinks,
                            onOpenBrowser = { browserOpen = true },
                            scheduleControls = {
                                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                                    val scheduledAt = scheduledAtMillis
                                    if (scheduledAt != null) {
                                        SettingsActionButton(onClick = { scheduledAtMillis = null }) {
                                            Text(appText("清除预约时间", "Clear scheduled time"))
                                        }
                                    }
                                    SettingsActionButton(onClick = {
                                        val date = java.util.Calendar.getInstance()
                                        if (scheduledAt != null) date.timeInMillis = scheduledAt
                                        android.app.DatePickerDialog(context, { _, year, month, day ->
                                            android.app.TimePickerDialog(context, { _, hour, minute ->
                                                date.set(year, month, day, hour, minute, 0)
                                                date.set(java.util.Calendar.MILLISECOND, 0)
                                                if (date.timeInMillis > System.currentTimeMillis()) {
                                                    scheduledAtMillis = date.timeInMillis
                                                } else {
                                                    runtimeState = runtimeState.copy(userMessage = "请选择未来的时间。")
                                                }
                                            }, date.get(java.util.Calendar.HOUR_OF_DAY), date.get(java.util.Calendar.MINUTE),
                                                android.text.format.DateFormat.is24HourFormat(context)).show()
                                        }, date.get(java.util.Calendar.YEAR), date.get(java.util.Calendar.MONTH),
                                            date.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                                            datePicker.minDate = System.currentTimeMillis()
                                        }.show()
                                    }) {
                                        Text(if (scheduledAt == null) appText("选择预约时间", "Schedule download")
                                        else java.text.DateFormat.getDateTimeInstance(
                                            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
                                        ).format(java.util.Date(scheduledAt)))
                                    }
                                }
                            },
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
                            focusedTaskId = focusedDownloadTaskId,
                            taskListState = pageListState,
                            onTaskFocused = { focusedDownloadTaskId = null },
                            userMessage = runtimeState.taskActionMessage,
                            onCancelDownload = { taskId ->
                                context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.ActionCancel)
                                    .putExtra(DownloadService.ExtraTaskId, taskId))
                                runtimeState = runtimeState.copy(taskActionMessage = "已请求取消所选任务。")
                            },
                            onHistoryQueryChange = { historyQuery = it },
                            onRetry = ::retryHistoryItem,
                            onDelete = ::requestDeleteHistoryItem,
                            resolveOutput = ::readableHistoryOutput,
                            onOutputAction = ::performHistoryOutputAction,
                            onCopySource = ::copyHistorySource,
                            historyAvailabilityRevision = historyAvailabilityRevision,
                            filters = TaskFilters(taskStatusNames.map(TaskStatus::valueOf).toSet(),
                                taskTypeNames.map(TaskOutputType::valueOf).toSet()),
                            onFiltersChange = {
                                taskStatusNames = ArrayList(it.statuses.map(TaskStatus::name))
                                taskTypeNames = ArrayList(it.types.map(TaskOutputType::name))
                            },
                            onRedownloadRequest = { request, title ->
                                if (request != null) redownload(RetryDownloadDraft.fromRequest(request))
                                else {
                                    runtimeState = runtimeState.copy(taskActionMessage = "原下载信息读取失败，任务记录已保留。")
                                }
                            },
                            onRecoverTask = { task ->
                                when (task.state.fileRecoveryAction) {
                                    FileRecoveryAction.ConfirmRestart, FileRecoveryAction.ChooseFileName -> pendingFileRecovery = task
                                    else -> runRecoveryAction(task)
                                }
                            },
                            savedTasks = savedDownloadTasks,
                            onPauseDownload = { taskId ->
                                context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.ActionPause)
                                    .putExtra(DownloadService.ExtraTaskId, taskId))
                            },
                            onResumeTask = { id -> Thread {
                                val result = DownloadCoordinator.resume(context.applicationContext, id)
                                mainHandler.post { if (result.isFailure) runtimeState = runtimeState.copy(taskActionMessage = "继续下载失败，任务已保留，请重试。") }
                            }.start() },
                            onCancelSavedTask = { id -> Thread {
                                val result = DownloadCoordinator.cancelSaved(context.applicationContext, id)
                                mainHandler.post {
                                    if (result.isSuccess) refreshHistory()
                                    else runtimeState = runtimeState.copy(taskActionMessage = "取消尚未完成，任务已保留，请重试。")
                                }
                            }.start() },
                        )
                        "settings" -> settingsPageItems(
                            settings = appSettings,
                            categoryId = if (selectedSettingsCategory == "components") "legal" else selectedSettingsCategory,
                            onSelectCategory = { selectedSettingsCategory = it },
                            onSettingsChange = { appSettings = settingsRepository.update(it) },
                            onClearHistory = {
                                Thread {
                                    val rows = YtdlDatabaseProvider.get(context.applicationContext).historyDao().listAll()
                                    val items = historyUiItemsFromRows(rows)
                                    mainHandler.post { clearHistorySnapshot = items }
                                }.start()
                            },
                            parserVersionSubtitle = parserVersionSubtitle,
                            downloadCacheSubtitle = "${formatBytes(downloadCacheStats.bytes)} · " +
                                appLanguage.pick(
                                    "${downloadCacheStats.fileCount} 个文件",
                                    if (downloadCacheStats.fileCount == 1) "1 file" else "${downloadCacheStats.fileCount} files",
                                ),
                            notificationsAllowed = notificationsAllowed,
                            notificationRuntimePermissionRequired = notificationRuntimePermissionRequired,
                            siteCookiesContent = {
                                SiteCookiesSettings(
                                    settings = appSettings,
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
                                        }.onFailure { settingsExplanation = SettingsExplanation("网站与登录", "清理登录状态失败，请重试。") }
                                    },
                                    onCheck = { domain ->
                                        val reference = if (domain.isBlank()) appSettings.cookiesReference else appSettings.siteCookies[domain]?.reference
                                        Thread {
                                            val temporary = prepareTemporaryCookiesForDownload(reference, context.applicationContext, "readability-check")
                                            val readable = reference != null && temporary.isSuccess
                                            temporary.getOrNull()?.delete()
                                            mainHandler.post {
                                                settingsExplanation = SettingsExplanation("检查 Cookies 文件",
                                                    if (readable) "文件可读取。可重新识别链接以验证登录。" else "文件不可读取，请重新选择。")
                                            }
                                        }.start()
                                    },
                                )
                            },
                            onSelectStorageTarget = { storageTreePicker.launch(null) },
                            onResetStorageTarget = {
                                persistStorageTarget(StorageTarget.AppPrivate)
                            },
                            onShowParserStatus = {
                                showParserStatusDialog = true
                            },
                            onShowOpenSource = { showOpenSource = true },
                            onShowMediaProcessorExplanation = { settingsExplanation = it },
                            onRequestDownloadCacheClear = ::showCacheCleanup,
                            onRequestNotifications = {
                                val systemSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                } else {
                                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", context.packageName, null))
                                }
                                if (notificationRuntimePermissionRequired && !notificationsAllowed &&
                                    !notificationPermissionRequested) {
                                    notificationPermissionRequested = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(systemSettings)
                                }
                            },
                            onThemeModeChange = { modeId ->
                                appSettings = settingsRepository.setThemeMode(modeId)
                            },
                            onShowDeclaration = { settingsExplanation = it },
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
        val permissionGuideVisible = showFirstRunGuide && !appSettings.permissionGuideCompleted
        if (permissionGuideVisible) {
            FirstRunPermissionGuide(
                onChooseFolder = { storageTreePicker.launch(null) },
                onFinish = {
                    appSettings = settingsRepository.update { it.copy(permissionGuideCompleted = true) }
                },
            )
        }
        pendingFileRecovery?.let { task ->
            FileRecoveryDialog(task, onDismiss = { pendingFileRecovery = null }) { name ->
                pendingFileRecovery = null
                runRecoveryAction(task, name)
            }
        }
        clearHistorySnapshot?.let { items ->
            var includeFiles by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { clearHistorySnapshot = null },
                title = { Text(appText("清除任务记录", "Clear task history")) },
                text = {
                    Column {
                        Text(
                            if (items.isEmpty()) appText("当前没有已结束的任务记录。", "There are no finished tasks.")
                            else appText("清除 ${items.size} 条已结束的任务记录。", "Remove finished tasks from history (count: ${items.size})."),
                        )
                        if (items.any { it.hasOutput }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = includeFiles, onCheckedChange = { includeFiles = it })
                                Text(appText("同时删除关联文件", "Also delete associated files"))
                            }
                        }
                    }
                },
                confirmButton = {
                    SettingsActionButton(enabled = items.isNotEmpty(), onClick = {
                        clearHistorySnapshot = null
                        deleteHistoryItems(items, includeFiles, fromSettings = true)
                    }) { Text(appText("清除记录", "Clear history")) }
                },
                dismissButton = { SettingsActionButton(onClick = { clearHistorySnapshot = null }) { Text(appText("返回", "Back")) } },
            )
        }
        if (confirmExtensionChange) {
            AlertDialog(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                onDismissRequest = { confirmExtensionChange = false },
                title = { Text(appText("确认更改扩展名", "Change file extension?")) },
                text = { Text(appText("将保存为 ${runtimeState.fileSelection?.fileName.orEmpty()}。更改扩展名可能影响打开方式。", "Save as ${runtimeState.fileSelection?.fileName.orEmpty()}? Changing the extension can affect which apps can open the file.")) },
                confirmButton = {
                    SettingsActionButton(
                        modifier = Modifier.testTag("ytdl-download-extension-confirm"),
                        onClick = {
                            confirmExtensionChange = false
                            startRealDownload(extensionConfirmed = true)
                        },
                    ) { Text(appText("确认下载", "Confirm download")) }
                },
                dismissButton = { SettingsActionButton(onClick = { confirmExtensionChange = false }) { Text(appText("返回修改", "Go back")) } },
            )
        }
        if (showSchedulePermission) {
            val settingsUnavailable = appText("无法打开系统设置。", "Could not open system settings.")
            AlertDialog(
                onDismissRequest = { showSchedulePermission = false },
                title = { Text(appText("允许定时下载", "Allow scheduled downloads")) },
                text = { Text(appText(
                    "请在系统设置中允许设置精确闹钟。返回后，点击确认预约。",
                    "Allow exact alarms in system settings, then return and confirm the schedule.",
                )) },
                confirmButton = {
                    SettingsActionButton(onClick = {
                        showSchedulePermission = false
                        runCatching {
                            context.startActivity(Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.fromParts("package", context.packageName, null),
                            ))
                        }.onFailure {
                            runtimeState = runtimeState.copy(userMessage = settingsUnavailable)
                        }
                    }) { Text(appText("打开系统设置", "Open system settings")) }
                },
                dismissButton = {
                    TextButton(onClick = { showSchedulePermission = false }) {
                        Text(appText("取消", "Cancel"))
                    }
                },
            )
        }
        if (showStorageChoice) {
            AlertDialog(
                onDismissRequest = {
                    showStorageChoice = false
                    if (queueImportAfterStorageChoice) previewOpen = true
                    queueImportAfterStorageChoice = false
                },
                title = { Text(appText("选择保存位置", "Choose where to save")) },
                text = { Text(appText("系统目录可在文件管理器中直接找到。App 私有目录中的文件可在任务页打开、分享或导出，卸载应用会清除。", "A system folder is visible in your file manager. Files in app storage can be opened, shared, or exported from Tasks and are removed when you uninstall the app.")) },
                confirmButton = {
                    SettingsActionButton(onClick = {
                        showStorageChoice = false
                        storageTreePicker.launch(null)
                    }) { Text(appText("选择系统目录", "Choose a folder")) }
                },
                dismissButton = {
                    SettingsActionButton(onClick = {
                        persistStorageTarget(StorageTarget.AppPrivate)
                        showStorageChoice = false
                        if (!queueImportAfterStorageChoice) startRealDownload()
                    }, modifier = Modifier.testTag("ytdl-first-storage-private")) { Text(appText("使用私有目录", "Use app storage")) }
                },
            )
        }
        if (showDownloadCacheConfirmation) {
            AlertDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-cache-clear-dialog"),
                onDismissRequest = { showDownloadCacheConfirmation = false },
                title = { Text(appText("确认清理临时缓存", "Clear temporary files?")) },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text(appText("可清理 ${formatBytes(downloadCacheStats.bytes)} · ${downloadCacheStats.fileCount} 个未引用的临时文件。", "Reclaim ${formatBytes(downloadCacheStats.bytes)} from unused temporary files (count: ${downloadCacheStats.fileCount})."))
                        if (savedCacheSnapshot.isEmpty()) {
                            Text(appText("已完成的文件与可恢复任务的进度会保留。", "Completed files and resumable downloads will be kept."))
                        } else {
                            Text(appText("以下任务会被取消，已保留的进度将清除。已完成的文件会保留。", "These tasks will be canceled and their progress cleared. Completed files will be kept."))
                            savedCacheSnapshot.forEach { entry -> Text("${entry.title} · ${formatBytes(entry.bytes)}") }
                        }
                    }
                },
                confirmButton = {
                    SettingsActionButton(
                        modifier = Modifier.testTag("ytdl-cache-clear-confirm"),
                        onClick = {
                            showDownloadCacheConfirmation = false
                            clearDownloadCache()
                        },
                    ) {
                        Text(appText("清理", "Clear"))
                    }
                },
                dismissButton = {
                    SettingsActionButton(onClick = { showDownloadCacheConfirmation = false }) {
                        Text(appText("取消", "Cancel"))
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
                title = { Text(appText("解析器版本", "Parser versions")) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val builtIn = parserVersions.first { it.isBuiltIn }
                        Text(appText("内置版本：yt-dlp ${builtIn.version}", "Built-in: yt-dlp ${builtIn.version}"))
                        Text(appText("当前选择：yt-dlp ${parserVersions.first { it.isSelected }.version}", "Selected: yt-dlp ${parserVersions.first { it.isSelected }.version}"))
                        Text(appText("本进程实际版本：yt-dlp $currentProcessParserVersion", "Running: yt-dlp $currentProcessParserVersion"))
                        SettingsActionButton(
                            enabled = !parserOperationRunning && !builtIn.isSelected,
                            modifier = Modifier.testTag("ytdl-parser-select-${builtIn.version}"),
                            onClick = { selectParserVersion(builtIn.version) },
                        ) {
                            Text(if (builtIn.isSelected) appText("当前选择", "Selected") else appText("使用内置版本", "Use built-in"))
                        }
                        parserVersions.filterNot(ParserVersionInfo::isBuiltIn).forEach { version ->
                            Text(appText("已下载：yt-dlp ${version.version}", "Downloaded: yt-dlp ${version.version}"))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsActionButton(
                                    enabled = !parserOperationRunning && !version.isSelected,
                                    modifier = Modifier.testTag("ytdl-parser-select-${version.version}"),
                                    onClick = { selectParserVersion(version.version) },
                                ) {
                                    Text(if (version.isSelected) appText("当前选择", "Selected") else appText("使用", "Use"))
                                }
                                SettingsActionButton(
                                    enabled = !parserOperationRunning,
                                    modifier = Modifier.testTag("ytdl-parser-delete-${version.version}"),
                                    onClick = {
                                        pendingDeleteParserVersion = version.version
                                        showParserStatusDialog = false
                                    },
                                ) {
                                    Text(appText("删除", "Delete"))
                                }
                            }
                        }
                        SettingsActionButton(
                            enabled = !parserOperationRunning,
                            modifier = Modifier.testTag("ytdl-parser-download-latest"),
                            onClick = { downloadParserVersion(expectedVersion = null) },
                        ) {
                            Text(appText("下载最新版", "Download latest version"))
                        }
                        Text(appParserStatus(parserUpdateStatusLabel(parserUpdateState)))
                        parserOperationMessage?.let { Text(it) }
                    }
                },
                confirmButton = {
                    SettingsActionButton(
                        enabled = !parserUpdateState.isRunning,
                        onClick = { requestParserUpdateCheck(manual = true) },
                    ) {
                        Text(appText("重新检查", "Check again"))
                    }
                },
                dismissButton = {
                    SettingsActionButton(onClick = { showParserStatusDialog = false }) {
                        Text(appText("关闭", "Close"))
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
                title = { Text(appText("确认删除解析器 $deleteVersion？", "Delete parser $deleteVersion?")) },
                text = { Text(appText("删除后不能在下次启动使用该版本；内置版本不会受影响。", "This version will be unavailable at the next launch. The built-in version will remain available.")) },
                confirmButton = {
                    SettingsActionButton(
                        enabled = !parserOperationRunning,
                        modifier = Modifier.testTag("ytdl-parser-delete-confirm"),
                        onClick = {
                            pendingDeleteParserVersion = null
                            showParserStatusDialog = true
                            deleteParserVersion(deleteVersion)
                        },
                    ) {
                        Text(appText("删除", "Delete"))
                    }
                },
                dismissButton = {
                    SettingsActionButton(
                        enabled = !parserOperationRunning,
                        onClick = {
                            pendingDeleteParserVersion = null
                            showParserStatusDialog = true
                        },
                    ) {
                        Text(appText("取消", "Cancel"))
                    }
                },
            )
        }
        val parserUpdateVersion = pendingParserUpdateVersion
        if (parserUpdateVersion != null && !showParserStatusDialog && !permissionGuideVisible) {
            AlertDialog(
                modifier = Modifier
                    .semantics { testTagsAsResourceId = true }
                    .testTag("ytdl-parser-update-dialog"),
                onDismissRequest = { dismissParserUpdatePrompt(parserUpdateVersion) },
                title = { Text(appText("发现新版解析器", "Parser update available")) },
                text = {
                    Text(appText("发现 yt-dlp $parserUpdateVersion，可下载并在重启应用后使用。", "yt-dlp $parserUpdateVersion is available. Download it and restart the app to use it."))
                },
                confirmButton = {
                    SettingsActionButton(
                        enabled = !parserOperationRunning,
                        onClick = {
                            dismissParserUpdatePrompt(parserUpdateVersion)
                            downloadParserVersion(parserUpdateVersion)
                        },
                    ) {
                        Text(appText("更新", "Update"))
                    }
                },
                dismissButton = {
                    SettingsActionButton(onClick = { dismissParserUpdatePrompt(parserUpdateVersion) }) {
                        Text(appText("稍后", "Later"))
                    }
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
                title = { Text(appMessage(explanation.title)) },
                text = { Text(appMessage(explanation.body)) },
                confirmButton = {
                    SettingsActionButton(onClick = { settingsExplanation = null }) {
                        Text(appText("知道了", "OK"))
                    }
                },
            )
        }
        val deleteTarget = pendingDeleteHistoryItem
        if (deleteTarget != null) {
            HistoryDeleteDialog(deleteTarget, onDismiss = { pendingDeleteHistoryItem = null },
                onConfirm = { includeFiles ->
                    pendingDeleteHistoryItem = null
                    deleteHistoryItems(listOf(deleteTarget), includeFiles)
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
        title = { Text(appText("确认删除历史记录", "Delete history entry?")) },
        text = {
            Column {
                Text(appText("将删除“${item.title}”的历史记录。", "Remove “${item.title}” from task history?"))
                Row {
                    Checkbox(
                        checked = includeFiles,
                        onCheckedChange = { includeFiles = it },
                        modifier = Modifier.testTag("ytdl-history-delete-files"),
                    )
                    Text(appText("同时删除下载文件", "Also delete the downloaded files"))
                }
            }
        },
        confirmButton = {
            SettingsActionButton(
                modifier = Modifier.testTag("ytdl-history-delete-confirm"),
                enabled = !submitted,
                onClick = {
                    if (!submitted) {
                        submitted = true
                        onConfirm(includeFiles)
                    }
                },
            ) { Text(appText("删除", "Delete")) }
        },
        dismissButton = {
            SettingsActionButton(modifier = Modifier.testTag("ytdl-history-delete-cancel"),
                enabled = !submitted, onClick = onDismiss) { Text(appText("取消", "Cancel")) }
        },
    )
}

internal fun RuntimeDownloadState.withAnalysisForUiTest(analysis: VideoAnalysis): RuntimeDownloadState = withAnalysisResult(analysis)

private fun RuntimeDownloadState.withNewUrl(url: String): RuntimeDownloadState = copy(
    url = url,
    analysis = null,
    fileSelection = null,
    torrentSource = null,
    torrentTitle = "",
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
    torrentSource = null,
    torrentTitle = "",
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
        taskMessage = "下载已加入队列，当前阶段：$stageText。",
    )
}

internal fun RuntimeDownloadState.withAcceptedDownloadForUiTest(state: DownloadTaskState): RuntimeDownloadState =
    withAcceptedDownload(state)

private fun RuntimeDownloadState.withAcceptedDownload(state: DownloadTaskState): RuntimeDownloadState {
    val accepted = if (isDownloading) {
        copy(taskMessage = "已加入等待队列，将按顺序自动开始。")
    } else {
        withForegroundStartState(state)
    }
    return accepted.withNewUrl("").copy(
        taskMessage = accepted.taskMessage,
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
            taskMessage = "下载结果无有效输出，请重试。",
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
            taskMessage = DefaultRuntimeMessage,
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
        taskMessage = if (state.fileRecoveryAction != null) DefaultRuntimeMessage else when (state.stage) {
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
        etaSeconds = progress?.etaSeconds?.takeIf { it > 0 && state.stage in TransferringStages },
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

private object TaskPreviewLoader {
    private val cache = LruCache<String, Bitmap>(64)
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheLock = Any()

    fun load(thumbnailUrl: String, onLoaded: (Bitmap?) -> Unit): AutoCloseable {
        val cached = synchronized(cacheLock) { cache.get(thumbnailUrl) }
        if (cached != null) {
            mainHandler.post { onLoaded(cached) }
            return AutoCloseable { }
        }

        val closed = AtomicBoolean(false)
        executor.execute {
            val bitmap = loadThumbnailBitmap(thumbnailUrl, targetSizePx = 216)
            if (bitmap != null) {
                synchronized(cacheLock) { cache.put(thumbnailUrl, bitmap) }
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

@Composable
private fun taskPreview(url: String?): Bitmap? {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(url) {
        val request = url?.takeIf(String::isNotBlank)?.let { TaskPreviewLoader.load(it) { bitmap = it } }
        onDispose { request?.close() }
    }
    return bitmap
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val selected = destination.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("ytdl-tab-${destination.route}")
                        .background(if (selected) palette.actionContainer else Color.Transparent)
                        .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab) { onSelected(destination.route) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Box(
                        modifier = Modifier.size(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                            tint = palette.neutralText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = destination.label,
                        color = palette.neutralText,
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
    onImportLinks: (() -> Unit)? = null,
    pendingImportCount: Int = 0,
    importEnabled: Boolean = true,
    onOpenBrowser: (() -> Unit)? = null,
    scheduleControls: (@Composable () -> Unit)? = null,
    showAdvancedFormats: Boolean = true,
    onToggleAdvancedFormats: () -> Unit = {},
    onFileSelectionChange: (FileDownloadSelection) -> Unit = {},
    onSubtitlesChange: (List<SubtitleInfo>) -> Unit = {},
    includeInlineStart: Boolean = true,
) {
    item {
        val palette = LocalYtdlAppPalette.current
        val showKeyboardOnFocus = shouldShowUrlInputKeyboardOnFocus(LocalConfiguration.current.keyboard)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlInputField(
                    value = state.url,
                    onValueChange = onUrlChange,
                    showKeyboardOnFocus = showKeyboardOnFocus,
                    modifier = Modifier.weight(1f).testTag("ytdl-url-input"),
                    onPaste = onPaste,
                )
                Button(
                    onClick = onAnalyze,
                    enabled = state.url.isNotBlank() || state.isAnalyzing,
                    modifier = Modifier.size(48.dp).testTag("ytdl-analyze-button"),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.formatAccent, contentColor = palette.controlActionText),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        if (state.isAnalyzing) CancelTaskIcon else SearchIcon,
                        contentDescription = if (state.isAnalyzing) appText("取消分析", "Cancel analysis") else appText("分析地址", "Analyze address"),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onImportLinks != null) {
                    SettingsActionButton(
                        onClick = onImportLinks,
                        enabled = importEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                    ) {
                        Text(
                            when {
                                !importEnabled -> appText("正在导入…", "Importing…")
                                pendingImportCount > 0 -> appText("待处理地址 ($pendingImportCount)", "Pending addresses ($pendingImportCount)")
                                else -> appText("导入地址列表", "Import address list")
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (onOpenBrowser != null) {
                    SettingsActionButton(
                        onClick = onOpenBrowser,
                        enabled = !state.url.trimStart().startsWith("magnet:", ignoreCase = true),
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag("ytdl-download-open-browser"),
                    ) {
                        Text(appText("内置浏览器打开", "Open in browser"), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    if (shouldShowRuntimeMessage(state.userMessage)) {
        item { RuntimeMessageCard(state.userMessage) }
    }

    val analysis = state.analysis
    val file = state.fileSelection
    when {
        state.torrentSource != null -> item {
            IdentificationLabel("Torrent")
            Text(state.torrentTitle, style = MaterialTheme.typography.titleMedium, color = LocalYtdlAppPalette.current.titleText)
            Text(appText("任务中查看进度，可暂停、继续和导出文件。", "Track progress, pause, resume, and export files in Tasks."),
                color = LocalYtdlAppPalette.current.softText)
            DownloadControl(appText("保存位置", "Save location"), storageTargetSummary(storageTarget, LocalAppLanguage.current),
                "ytdl-download-storage-target", actionLabel = appText("更改", "Change"), onClick = onSelectStorageTarget)
        }
        analysis != null -> {
            item {
                IdentificationLabel(appText("媒体", "Media"))
                DownloadPreviewCard(state)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadChoice(
                        appText("下载内容", "Content"), formatModeLabel(state.formatSelection.mode), "ytdl-download-mode", Modifier.weight(1f),
                    ) { dismiss ->
                        FormatMode.entries.forEach { mode ->
                            val suffix = when (mode) {
                                FormatMode.VideoAndAudio -> "av"
                                FormatMode.VideoOnly -> "video"
                                FormatMode.AudioOnly -> "audio"
                            }
                            ChoiceItem(formatModeLabel(mode), "ytdl-download-mode-$suffix", isFormatModeAvailable(analysis, mode)) {
                                dismiss()
                                onModeSelected(mode)
                            }
                        }
                    }
                    DownloadControl(
                        appText("下载格式", "Format"), appFormatText(formatSelectionSummary(analysis, state.appliedFormatSelection)),
                        "ytdl-download-format-toggle", Modifier.weight(1f),
                        actionLabel = if (showAdvancedFormats) appText("收起", "Collapse") else appText("展开", "Expand"),
                        selected = showAdvancedFormats,
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
            val label = listOf(appMessage(file.metadata.kind.identificationLabel()), file.formatLabel)
                .filter { it.isNotBlank() }.joinToString(" · ")
            IdentificationLabel(label)
            FileDownloadSection(file, onFileSelectionChange)
        }
        state.isAnalyzing -> item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                RecognitionProgress()
                Text(appText("正在识别链接", "Analyzing link"))
            }
        }
        else -> item {
            val palette = LocalYtdlAppPalette.current
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 24.dp)) {
                Icon(DownloadTabIcon, contentDescription = null, tint = palette.formatAccent)
                Text(appText("先识别，再选择", "Analyze a link to get started"), style = MaterialTheme.typography.titleMedium, color = palette.titleText)
                Text(appText("识别后可选择媒体格式，或设置普通文件的保存方式。", "Then choose a media format or how to save the file."), color = palette.softText)
            }
        }
    }
    if (analysis != null || file != null) {
        item {
            DownloadControl(appText("保存位置", "Save location"), storageTargetSummary(storageTarget, LocalAppLanguage.current), "ytdl-download-storage-target",
                actionLabel = appText("更改", "Change"), onClick = onSelectStorageTarget)
        }
    }
    if (file?.metadata?.kind == FileKind.Other) {
        item { FileConflictChoice(file, onFileSelectionChange) }
    }
    if (scheduleControls != null && canStartDownload(state)) item { scheduleControls() }
    if (includeInlineStart) item { DownloadStartButton(state, onStartDownload) }
}

@Composable
private fun IdentificationLabel(type: String) {
    Text(
        appText("已识别 · $type", "Identified · $type"),
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
        0 -> appText("不下载字幕", "No subtitles")
        1 -> appText("${selected.single().language} · 独立字幕", "${selected.single().language} · Separate file")
        else -> appText("${selected.size} 种独立字幕", "${selected.size} subtitle files")
    }
    DownloadChoice(appText("字幕", "Subtitles"), summary, "ytdl-format-subtitle-toggle") { dismiss ->
        subtitles.forEach { subtitle ->
            val source = if (subtitle.source == SubtitleSource.Manual) appText("人工", "Manual") else appText("自动生成", "Automatic")
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
private fun formatModeLabel(mode: FormatMode): String = when (mode) {
    FormatMode.VideoAndAudio -> appText(mode.label, "Video + audio")
    FormatMode.VideoOnly -> appText(mode.label, "Video")
    FormatMode.AudioOnly -> appText(mode.label, "Audio")
}

@Composable
private fun DownloadStartButton(state: RuntimeDownloadState, onClick: () -> Unit, scheduled: Boolean = false) {
    val palette = LocalYtdlAppPalette.current
    val canStart = canStartDownload(state)
    val size = state.fileSelection?.metadata?.totalBytes
    val label = when {
        state.isStarting -> appText("正在提交任务", "Submitting task")
        state.isAnalyzing -> appText("正在识别链接", "Analyzing link")
        state.fileSelection != null && !canStart -> appText("请检查文件名", "Check the file name")
        !canStart -> appText("识别后可下载", "Analyze link to download")
        scheduled -> appText("确认预约", "Confirm schedule")
        size != null -> appText("开始下载 · ${formatBytes(size)}", "Start download · ${formatBytes(size)}")
        else -> appText("开始下载", "Start download")
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
        (state.fileSelection?.route != null || state.analysis != null || state.torrentSource != null)

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
    onPaste: (() -> Unit)? = null,
) {
    val palette = LocalYtdlAppPalette.current
    val inputTextColor = palette.titleText
    val inputHintColor = palette.softText
    val urlHint = if (BuildConfig.TORRENT_ENABLED) appText("输入网址或磁力链接", "Enter a URL or magnet link")
    else appText("输入网页或文件地址", "Enter a page or file URL")
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
                    hint = urlHint
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
                editText.hint = urlHint
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
        if (onPaste != null) {
            androidx.compose.material3.IconButton(
                onClick = onPaste,
                modifier = Modifier.size(40.dp).testTag("ytdl-download-paste"),
            ) {
                Icon(ClipboardIcon, contentDescription = appText("从剪贴板获取", "Paste from clipboard"), tint = palette.formatAccent)
            }
        }
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
                        contentDescription = appText("视频预览图", "Video thumbnail"),
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
        val details = listOfNotNull(duration, if (analysis.subtitles.isNotEmpty()) appText("可下载独立字幕", "Separate subtitles available") else null)
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
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Text(
            text = appMessage(message),
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

@Composable
private fun RecognitionProgress() {
    val palette = LocalYtdlAppPalette.current
    val transition = rememberInfiniteTransition(label = "recognition-progress")
    val position by transition.animateFloat(
        initialValue = -0.22f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1350, easing = androidx.compose.animation.core.LinearEasing)),
        label = "recognition-progress-position",
    )
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(4.dp).clip(RectangleShape).background(palette.borderColor),
    ) {
        Box(
            Modifier.offset(x = maxWidth * position)
                .width(maxWidth * 0.22f).fillMaxHeight().background(palette.downloadAccent),
        )
    }
}

@Composable
private fun DownloadCountSetting(
    title: String,
    value: String,
    choices: Map<out Int?, String>,
    tag: String,
    subtitle: String = "",
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingLineCard(title, subtitle, "↓", "",
        modifier = Modifier.clickable { expanded = true }.testTag(tag),
        trailingContent = {
            Box {
                val palette = LocalYtdlAppPalette.current
                Text(
                    "$value ▾",
                    modifier = if (expanded) Modifier.background(palette.settingsAccent).padding(horizontal = 6.dp) else Modifier,
                    color = if (expanded) palette.primaryActionText else palette.titleText,
                    style = MaterialTheme.typography.titleSmall,
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 200.dp).widthIn(min = 80.dp),
                    shape = RectangleShape,
                    containerColor = LocalYtdlAppPalette.current.cardBackground,
                ) {
                    choices.forEach { (choice, label) ->
                        ChoiceItem(label, "$tag-${choice?.let { it - 1 } ?: "unlimited"}") {
                            onSelect(choice)
                            expanded = false
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun BackgroundDownloadSetting() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val power = remember(context) { context.getSystemService(android.os.PowerManager::class.java) }
    var exempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = power.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingLineCard(
        appText("后台下载", "Background downloads"),
        if (exempt) appText("电池优化已豁免", "Battery optimization disabled")
        else appText("受电池优化限制，锁屏后可能暂停", "Screen-off downloads may pause"),
        "↓", "",
        trailingContent = {
            SettingsActionButton(onClick = {
                val settingsIntent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                runCatching { context.startActivity(settingsIntent) }.onFailure {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                }
            }) { Text(appText("系统设置", "System settings")) }
        },
    )
}

@Composable
private fun DownloadBehaviorSwitch(title: String, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    SettingLineCard(title, "", "↓", "", trailingContent = {
        Switch(enabled, onChange, modifier = Modifier.testTag("ytdl-settings-$tag"))
    })
}

@Composable
private fun FormatDetailLine(
    title: String,
    value: String,
    tag: String,
    options: Map<String, FormatSelection> = emptyMap(),
    onSelect: (FormatSelection) -> Unit = {},
) {
    val palette = LocalYtdlAppPalette.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .background(if (expanded) palette.downloadAccent else palette.cardBackground)
                .border(1.dp, palette.borderColor)
                .clickable(enabled = options.size > 1) { expanded = true }
                .testTag(tag)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = if (expanded) palette.primaryActionText else palette.titleText, maxLines = 1)
            Text(appFormatText(value), modifier = Modifier.weight(1f), color = if (expanded) palette.primaryActionText else palette.softText,
                textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (options.size > 1) Text("▾", color = if (expanded) palette.primaryActionText else palette.formatAccent)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RectangleShape,
            containerColor = palette.cardBackground,
        ) {
            options.forEach { (label, selection) ->
                ChoiceItem(appFormatText(label), "$tag-option-$label") {
                    expanded = false
                    onSelect(selection)
                }
            }
        }
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
            FormatDetailLine(appText("视频分辨率", "Video resolution"), appText("音频模式不适用", "Unavailable in audio mode"), "ytdl-format-video-resolution-line")
        }
        item {
            FormatDetailLine(appText("帧率", "Frame rate"), appText("音频模式不适用", "Unavailable in audio mode"), "ytdl-format-frame-rate-line")
        }
        item {
            FormatDetailLine(appText("视频编码", "Video codec"), appText("音频模式不适用", "Unavailable in audio mode"), "ytdl-format-video-codec-line")
        }
        item {
            AppCard(modifier = Modifier.testTag("ytdl-format-audio-card")) {
                SectionTitle(appText("音频格式", "Audio format"))
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
            FormatDetailLine(appText("音频容器", "Audio container"), summaries.container, "ytdl-format-container-line")
        }
    } else {
        item {
            AppCard(modifier = Modifier.testTag("ytdl-format-resolution-card")) {
                SectionTitle(appText("分辨率", "Resolution"))
                if (rows.isEmpty()) {
                    Text(appText("当前模式没有可下载格式", "No formats available for this mode"), color = LocalYtdlAppPalette.current.softText)
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
        val selected = rows.firstOrNull { it.selected && it.selectable }
        val height = analysis.formats.firstOrNull { it.id == selected?.videoFormatId }?.height
        val alternatives = analysis.formats.mapNotNull { format ->
            if (selected == null || !format.isSupported || !format.hasVideo || format.height != height) return@mapNotNull null
            if (selection.mode == FormatMode.VideoAndAudio &&
                (format.hasAudio || !format.isNativeMp4MergeVideoCompatible())) return@mapNotNull null
            val candidate = selection.copy(selectedHeight = height, selectedVideoFormatId = format.id)
            formatSettingSummariesForRow(analysis, selection.mode, selected.copy(videoFormatId = format.id)) to candidate
        }
        fun choices(value: String, property: (FormatSettingSummaries) -> String): Map<String, FormatSelection> =
            buildMap {
                put(value, selection)
                alternatives.sortedByDescending { (candidate, _) ->
                    listOf(candidate.frameRate == summaries.frameRate, candidate.videoCodec == summaries.videoCodec,
                        candidate.container == summaries.container).count { it }
                }.forEach { (candidate, format) -> putIfAbsent(property(candidate), format) }
            }
        item {
            FormatDetailLine(appText("帧率", "Frame rate"), summaries.frameRate, "ytdl-format-frame-rate-line",
                choices(summaries.frameRate) { it.frameRate }, onSelectionChange)
        }
        item {
            FormatDetailLine(appText("视频编码", "Video codec"), summaries.videoCodec, "ytdl-format-video-codec-line",
                choices(summaries.videoCodec) { it.videoCodec }, onSelectionChange)
        }
        item {
            FormatDetailLine(appText("容器格式", "Container"), summaries.container, "ytdl-format-container-line",
                choices(summaries.container) { it.container }, onSelectionChange)
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
    return formatSettingSummariesForRow(analysis, selection.mode, row)
}

private fun formatSettingSummariesForRow(
    analysis: VideoAnalysis,
    mode: FormatMode,
    row: FormatResolutionRow,
): FormatSettingSummaries {
    val video = row.videoFormatId?.let { id -> analysis.formats.firstOrNull { it.id == id } }
    val audio = row.audioFormatId?.let { id -> analysis.formats.firstOrNull { it.id == id } }

    val frameRate = when {
        mode == FormatMode.AudioOnly -> "不适用"
        video?.fps != null -> "${String.format(Locale.US, "%.2f", video.fps).trimEnd('0').trimEnd('.')}fps"
        else -> "未提供"
    }
    val videoCodec = when {
        mode == FormatMode.AudioOnly -> "不适用"
        else -> video?.videoCodec
            ?.takeIf { it.isNotBlank() && it != "none" }
            ?: "未提供"
    }
    val container = when {
        row.mergeRequired -> "MP4（原生合并输出）"
        mode == FormatMode.AudioOnly -> audio?.ext?.uppercase(Locale.US)?.ifBlank { null } ?: "音频容器未提供"
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
        is DownloadRoute.Torrent -> listOf(QueueStageSpec("下载文件", DownloadStage.DownloadingFile))
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
    val fraction = (state.overallProgressPercent ?: state.progressPercent)?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
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
    if (currentStagePercent == null && state.stage in TransferringStages) return null
    val currentIndex = specs.indexOfFirst { it.stage == state.stage }
    if (currentIndex < 0) return currentStagePercent
    val currentStageFraction = ((currentStagePercent ?: 0.0).coerceIn(0.0, 100.0)) / 100.0
    return ((currentIndex + currentStageFraction) / specs.size) * 100.0
}

private val TransferringStages = setOf(DownloadStage.DownloadingFile, DownloadStage.DownloadingVideo,
    DownloadStage.DownloadingAudio, DownloadStage.DownloadingSubtitles)

private fun queueCardStatus(state: RuntimeDownloadState): String = when (state.activeStage) {
    DownloadStage.Completed -> "完成"
    DownloadStage.Failed -> "失败"
    DownloadStage.Canceled -> "取消"
    DownloadStage.Waiting, DownloadStage.WaitingForNetwork, DownloadStage.Pausing,
    DownloadStage.Resuming, DownloadStage.Paused -> userVisibleDownloadStatus(state.activeStage)
    else -> (state.overallProgressPercent ?: state.progressPercent)
        ?.takeIf(Double::isFinite)?.let { "${it.toInt()}%" }
        ?: state.downloadStatus.ifBlank { userVisibleDownloadStatus(state.activeStage) }
}

internal fun queueCardStatusForUiTest(state: RuntimeDownloadState): String = queueCardStatus(state)

private fun queueCardMeta(state: RuntimeDownloadState): QueueProgressMeta {
    val speed = state.speedBytesPerSecond
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { "${formatProgressBytes(it)}/s" }
        ?: "--"
    val downloaded = formatProgressBytes((state.downloadedBytes ?: 0L).toDouble())
    val total = state.totalBytes?.let { formatProgressBytes(it.toDouble()) } ?: "--"
    val eta = state.etaSeconds?.takeIf { it > 0 }?.let { "剩余 ${formatDuration(it)}" }
    return QueueProgressMeta(
        speed = speed,
        transferred = "$downloaded/$total",
        contentDescription = "下载速度 $speed，已下载 $downloaded，总容量 $total" + eta?.let { "，$it" }.orEmpty(),
        eta = eta,
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

private fun storageTargetSummary(target: StorageTarget, language: AppLanguage = AppLanguage.Chinese): String {
    return when (val safeTarget = StorageTargets.sanitizeDefault(target)) {
        StorageTarget.AppPrivate -> language.pick("App 私有目录；完成文件由 App 保留", "App storage. Finished files stay in the app.")
        is StorageTarget.SafTree -> language.pick(
            "${StorageTargets.displayName(safeTarget)}；完成后清理 App 内中转文件",
            "${StorageTargets.displayName(safeTarget)} · Temporary files are removed after saving",
        )
    }
}

internal fun storageTargetSummaryForUiTest(target: StorageTarget): String = storageTargetSummary(target)

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
            placeholder = { Text(appText("搜索历史", "Search history")) },
            leadingIcon = {
                Icon(SearchIcon, contentDescription = null, tint = LocalYtdlAppPalette.current.softText)
            },
            singleLine = true,
            shape = RectangleShape,
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
        shape = RectangleShape,
        modifier = Modifier
            .size(52.dp)
            .clip(RectangleShape)
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
    userMessage: String,
    onCancelDownload: (String?) -> Unit,
    onHistoryQueryChange: (String) -> Unit,
    onOpen: (HistoryUiItem) -> Unit = {},
    onShare: (HistoryUiItem) -> Unit = {},
    onRetry: (HistoryUiItem) -> Unit,
    onDelete: (HistoryUiItem) -> Unit,
    savedTasks: List<ResumeTaskItem> = emptyList(),
    activeStates: Map<String, DownloadTaskState>? = null,
    onPauseDownload: (String?) -> Unit = {},
    onResumeTask: (String) -> Unit = {},
    onCancelSavedTask: (String) -> Unit = {},
    onRecoverTask: (ResumeTaskItem) -> Unit = { onResumeTask(it.taskId) },
    onRedownloadRequest: (DownloadRequest?, String) -> Unit = { _, _ -> },
    resolveOutput: ((HistoryOutputReference) -> HistoryOutputTarget?)? = null,
    onOutputAction: ((HistoryFileAction, HistoryOutputTarget) -> Unit)? = null,
    historyAvailabilityRevision: Int = 0,
    onCopySource: (HistoryUiItem) -> Unit = {},
    filters: TaskFilters = TaskFilters(),
    onFiltersChange: (TaskFilters) -> Unit = {},
    focusedTaskId: String? = null,
    taskListState: LazyListState? = null,
    onTaskFocused: () -> Unit = {},
) {
    val liveStates = activeStates?.values?.toList() ?: listOfNotNull(state.activeRequest?.let {
        DownloadTaskState(state.activeStage, it)
    }).filter { shouldShowCurrentTask(state) }
    val liveIds = liveStates.mapNotNull { it.request?.taskId }.toSet()
    val waiting = pendingRequests.distinctBy { it.taskId }.filterNot { it.taskId in liveIds }
    val waitingIds = waiting.map { it.taskId }.toSet()
    val tasks = unifiedUiTasks(liveStates + waiting.map { DownloadTaskState.waiting(it) }, savedTasks, historyItems)
    val visibleTasks = tasks.filter { task ->
        val text = task.title + task.history?.meta?.let { " $it" }.orEmpty()
        filters.matches(task.status, task.type, text, historyQuery)
    }
    val currentTasks = visibleTasks.filter { it.id in liveIds && it.recovery == null }.mapNotNull { task ->
        val active = task.state ?: return@mapNotNull null
        if (activeStates == null) state else RuntimeDownloadState().withPipelineState(active)
    }
    val visibleWaitingIds = visibleTasks.filter { it.state != null && it.recovery == null }.map { it.id }.toSet().intersect(waitingIds)
    val visibleSavedTasks = visibleTasks.mapNotNull { it.recovery }
    val visibleHistory = visibleTasks.filter { it.state == null }.mapNotNull { it.history }
    val taskFeedback = userMessage.takeIf(::shouldShowRuntimeMessage)
        ?: state.taskMessage.takeIf { state.activeStage == DownloadStage.Failed && shouldShowRuntimeMessage(it) }
    val showMessage = taskFeedback != null
    val orderedTaskIds = currentTasks.mapNotNull { it.activeRequest?.taskId } +
        waiting.filter { it.taskId in visibleWaitingIds }.map { it.taskId } +
        visibleSavedTasks.map { it.taskId } + visibleHistory.map { it.taskId }
    val focusedIndex = focusedTaskId?.let(orderedTaskIds::indexOf) ?: -1
    item {
        TaskFilterBar(filters, onFiltersChange)
        LaunchedEffect(focusedTaskId, focusedIndex) {
            if (focusedIndex >= 0 && taskListState != null) {
                taskListState.scrollToItem(3 + focusedIndex + if (showMessage) 1 else 0)
                onTaskFocused()
            }
        }
    }
    item {
        OutlinedTextField(
            value = historyQuery,
            onValueChange = onHistoryQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("ytdl-history-search"),
            placeholder = { Text(appText("搜索任务", "Search tasks")) },
            leadingIcon = { Icon(SearchIcon, contentDescription = null) },
            singleLine = true,
            shape = RectangleShape,
        )
    }
    if (showMessage) {
        item { RuntimeMessageCard(requireNotNull(taskFeedback)) }
    }
    if (visibleTasks.isEmpty() && visibleHistory.isEmpty()) item {
        AppCard(modifier = Modifier.testTag("ytdl-tasks-empty")) {
            Text(if (tasks.isEmpty() && historyItems.isEmpty()) "暂无下载任务" else "没有符合条件的任务")
            if (filters != TaskFilters() || historyQuery.isNotBlank()) {
                SettingsActionButton(onClick = { onFiltersChange(TaskFilters()); onHistoryQueryChange("") }) { Text(appText("清除筛选", "Clear filters")) }
            }
        }
    }
    currentTasks.forEach { state ->
        item(key = "task-${state.activeRequest?.taskId}") {
            val request = state.activeRequest ?: return@item
            val progress = queueProgressPresentation(state)
            val meta = queueCardMeta(state)
            val pause = state.activeStage in TransferringStages || state.activeStage == DownloadStage.Merging
            val cancel = state.activeStage !in TerminalDownloadStages
            TaskLine(
                title = request.title.ifBlank { "下载任务" },
                titleTag = "ytdl-current-task-title",
                kind = request.outputType(),
                preview = taskPreview(request.thumbnailUrl.takeIf { request.outputType() == TaskOutputType.Video }),
                details = listOf(queueCardFormatBadge(state), queueCardCodecBadge(state), meta.transferred)
                    .filter(String::isNotBlank).joinToString(" · "),
                status = if (progress.isIndeterminate) appMessage(state.downloadStatus)
                    else listOf(appMessage(state.downloadStatus), appMessage(queueCardStatus(state))).distinct().joinToString(" · "),
                trailing = listOfNotNull(meta.speed, meta.eta?.let { appText(it, "${it.removePrefix("剩余 ")} remaining") }).joinToString(" · "),
                progress = progress.takeIf { state.activeStage in TransferringStages },
                modifier = Modifier.testTag("ytdl-real-queue-card"),
                actions = {
                    if (pause) TaskIconAction("暂停", PauseTaskIcon, "ytdl-queue-pause-action") { onPauseDownload(request.taskId) }
                    if (cancel) TaskIconAction("取消", CancelTaskIcon, "ytdl-queue-cancel-action") { onCancelDownload(request.taskId) }
                },
            )
        }
    }
    waiting.forEachIndexed { index, request ->
        if (request.taskId !in visibleWaitingIds) return@forEachIndexed
        item(key = "task-${request.taskId}") {
            TaskLine(
                title = request.title.ifBlank { "等待下载的任务" },
                titleTag = "ytdl-pending-task-title-$index",
                kind = request.outputType(),
                preview = taskPreview(request.thumbnailUrl.takeIf { request.outputType() == TaskOutputType.Video }),
                details = listOf(formatResolutionBadgeForRequest(request), formatCodecBadgeForRequest(request))
                    .filter(String::isNotBlank).joinToString(" · "),
                status = request.scheduledAtMillis?.let { scheduledAt ->
                    val time = java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
                    ).format(java.util.Date(scheduledAt))
                    appText("预约于 $time", "Scheduled for $time")
                } ?: appText("等待中", "Queued"),
                trailing = if ((request.scheduledAtMillis ?: 0L) > System.currentTimeMillis()) {
                    appText("等待预约时间", "Waiting for scheduled time")
                } else {
                    appText("等待空闲下载位", "Waiting for a download slot")
                },
                modifier = Modifier.testTag("ytdl-pending-queue-card-$index"),
                actions = {
                    TaskIconAction("取消", CancelTaskIcon, "ytdl-queue-cancel-action") { onCancelDownload(request.taskId) }
                },
            )
        }
    }
    visibleSavedTasks.forEach { task ->
        item(key = "task-${task.taskId}") {
            val progress = task.state.progress
            val rowStatus = visibleTasks.first { it.id == task.taskId }.status
            val failed = rowStatus == TaskStatus.Failed
            val recovery = task.state.fileRecoveryAction
            TaskLine(
                title = task.title,
                titleTag = "ytdl-saved-task-title-${task.taskId}",
                kind = task.state.request?.outputType() ?: task.outputType,
                preview = taskPreview(task.state.request?.thumbnailUrl.takeIf { (task.state.request?.outputType() ?: task.outputType) == TaskOutputType.Video }),
                details = appMessage(task.recoveryError ?: task.state.statusMessage ?: if (failed) "下载未完成，可重新下载。" else ""),
                status = listOfNotNull(rowStatus.localized(LocalAppLanguage.current), progress?.percent?.takeIf(Double::isFinite)?.let { "${it.toInt()}%" })
                    .joinToString(" · "),
                trailing = progress?.downloadedBytes?.takeIf { it > 0 }?.let { bytes ->
                    appText("已下载 ${formatBytes(bytes)}", "Downloaded ${formatBytes(bytes)}") +
                        (progress.totalBytes?.let { total -> " / ${formatBytes(total)}" } ?: "")
                }.orEmpty(),
                progress = progress?.percent?.takeIf(Double::isFinite)?.let {
                    QueueProgressPresentation((it / 100).toFloat().coerceIn(0f, 1f), false)
                },
                modifier = Modifier.testTag("ytdl-saved-task-${task.taskId}"),
                actions = {
                    if (failed) {
                        TaskIconAction("重新下载", HistoryRetryIcon, "ytdl-saved-redownload-${task.taskId}") {
                            if (recovery == FileRecoveryAction.ConfirmRestart || recovery == FileRecoveryAction.ChooseFileName) {
                                onRecoverTask(task)
                            } else {
                                onRedownloadRequest(task.state.request, task.title)
                            }
                        }
                    } else if (task.recoveryError == null) {
                        TaskIconAction("继续", ContinueTaskIcon, "ytdl-saved-task-recover-${task.taskId}") {
                            onRecoverTask(task)
                        }
                    }
                    TaskIconAction("取消", CancelTaskIcon, "ytdl-saved-task-cancel-${task.taskId}") { onCancelSavedTask(task.taskId) }
                },
                accent = if (rowStatus == TaskStatus.Failed) HistoryFailureRed else LocalYtdlAppPalette.current.queueAccent,
            )
        }
    }
    items(visibleHistory, key = { "task-${it.taskId ?: "history:${it.id}"}" }) { item ->
        HistoryCard(
            item = item,
            onOpen = { onOpen(item) },
            onShare = { onShare(item) },
            onRetry = { onRetry(item) },
            onDelete = { onDelete(item) },
            resolveOutput = resolveOutput,
            onOutputAction = onOutputAction,
            onCopySource = { onCopySource(item) },
            availabilityRevision = historyAvailabilityRevision,
            modifier = Modifier.testTag("ytdl-history-real-card"),
        )
    }
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
    categoryId: String?,
    onSelectCategory: (String?) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onClearHistory: () -> Unit,
    parserVersionSubtitle: String,
    downloadCacheSubtitle: String,
    notificationsAllowed: Boolean,
    notificationRuntimePermissionRequired: Boolean,
    siteCookiesContent: @Composable () -> Unit,
    onSelectStorageTarget: () -> Unit,
    onResetStorageTarget: () -> Unit,
    onShowParserStatus: () -> Unit,
    onShowOpenSource: () -> Unit,
    onShowMediaProcessorExplanation: (SettingsExplanation) -> Unit,
    onRequestDownloadCacheClear: () -> Unit,
    onRequestNotifications: () -> Unit,
    onThemeModeChange: (String) -> Unit,
    onShowDeclaration: (SettingsExplanation) -> Unit,
) {
    if (categoryId == null) {
        items(SettingsCategories, key = { it.id }) { category ->
            SettingLineCard(appText(category.title, category.englishTitle), appText(category.description, category.englishDescription), category.symbol, "›",
                modifier = Modifier.clickable { onSelectCategory(category.id) }
                    .testTag("ytdl-settings-category-${category.id}"))
        }
        return
    }
    item {
        SettingsActionButton(onClick = { onSelectCategory(null) }) { Text(appText("返回设置", "Back to settings")) }
        val category = SettingsCategories.first { it.id == categoryId }
        SectionTitle(appText(category.title, category.englishTitle))
    }
    if (categoryId == "storage") item {
        SettingLineCard(
            appText("保存位置", "Save location"),
            when (val target = StorageTargets.sanitizeDefault(settings.defaultStorageTarget)) {
                StorageTarget.AppPrivate -> appText("App 私有目录", "App storage")
                is StorageTarget.SafTree -> StorageTargets.displayName(target)
            },
            "▣",
            "",
            LocalYtdlAppPalette.current.settingsAccent,
            subtitleMaxLines = 1,
            subtitleIsValue = true,
            modifier = Modifier
                .clickable(onClick = onSelectStorageTarget)
                .testTag("ytdl-settings-storage-target"),
            trailingContent = {
                SettingsActionButton(
                    onClick = onResetStorageTarget,
                    enabled = StorageTargets.sanitizeDefault(settings.defaultStorageTarget) is StorageTarget.SafTree,
                    modifier = Modifier.testTag("ytdl-settings-storage-target-reset"),
                ) {
                    Text(appText("恢复默认路径", "Restore default folder"))
                }
            },
        )
    }
    if (categoryId == "storage") item {
        SettingLineCard(
            appText("清理下载缓存", "Clear download cache"),
            downloadCacheSubtitle,
            "⌫",
            "›",
            LocalYtdlAppPalette.current.settingsAccent,
            modifier = Modifier
                .clickable(onClick = onRequestDownloadCacheClear)
                .testTag("ytdl-settings-cache-clear"),
        )
    }
    if (categoryId == "sites") item { siteCookiesContent() }
    if (categoryId == "behavior") item {
        SettingLineCard(
            appText("解析器版本", "Parser versions"),
            appParserStatus(parserVersionSubtitle),
            "◇",
            "›",
            LocalYtdlAppPalette.current.formatAccent,
            subtitleIsValue = true,
            modifier = Modifier
                .clickable(onClick = onShowParserStatus)
                .testTag("ytdl-settings-parser-version"),
        )
    }
    if (categoryId == "notifications") item {
        SettingLineCard(
            appText("通知权限", "Notification permission"),
            appMessage(notificationPermissionSubtitle(notificationsAllowed, notificationRuntimePermissionRequired)),
            "●",
            appMessage(notificationPermissionTrailing(notificationsAllowed, notificationRuntimePermissionRequired)),
            LocalYtdlAppPalette.current.formatAccent,
            modifier = Modifier
                .clickable(onClick = onRequestNotifications)
                .testTag("ytdl-settings-notification-permission"),
        )
    }
    when (categoryId) {
        "appearance" -> item {
            AppCard(shape = RectangleShape) {
                SectionTitle(appText("语言", "Language"))
                SegmentedRow(
                    options = listOf(appText("跟随系统", "System"), "简体中文", "English"),
                    selectedIndex = settings.language.ordinal,
                    accent = LocalYtdlAppPalette.current.settingsAccent,
                    testTagPrefix = "ytdl-settings-language",
                    onSelected = { index -> onSettingsChange { it.copy(language = AppLanguage.entries[index]) } },
                )
                SectionTitle(appText("主题模式", "Theme"))
                SegmentedRow(
                    options = listOf(appText("跟随系统", "System"), appText("浅色", "Light"), appText("深色", "Dark")),
                    selectedIndex = selectedThemeModeIndex(settings),
                    accent = LocalYtdlAppPalette.current.settingsAccent,
                    testTagPrefix = "ytdl-settings-theme-mode",
                    onSelected = { index -> onThemeModeChange(ThemeModeOptions[index].id) },
                )
                val dark = when (themeConfigForSettings(settings).mode) {
                    YtdlThemeMode.Dark -> true
                    YtdlThemeMode.Light -> false
                    YtdlThemeMode.System -> isSystemInDarkTheme()
                }
                Text(if (dark) appText("当前生效：深色", "Active theme: Dark") else appText("当前生效：浅色", "Active theme: Light"))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val palette = LocalYtdlAppPalette.current
                    listOf(appText("背景", "Background") to palette.appBackground, appText("辅助色", "Secondary") to palette.formatAccent, appText("主操作", "Primary") to palette.downloadAccent).forEach { (label, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(28.dp).background(color)
                                .border(1.dp, palette.borderColor, RectangleShape))
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        "behavior" -> {
            item { BackgroundDownloadSetting() }
            item {
                DownloadCountSetting(
                    appText("单文件最大连接数", "Connections per file"), settings.httpMaxConnections.toString(),
                    listOf(1, 2, 4, 8, 16).associateWith { it.toString() }, "ytdl-settings-file-connections",
                    appText("下次下载生效；小文件或服务器限制时自动减少", "Applies to new downloads; fewer connections for small files or server limits"),
                ) { value -> onSettingsChange { it.copy(httpMaxConnections = requireNotNull(value)) } }
            }
            item {
                val unlimited = appText("不限速", "Unlimited")
                DownloadCountSetting(
                    appText("普通文件总限速", "File download speed limit"),
                    if (settings.httpLimitKib == 0) unlimited else "${settings.httpLimitKib} KiB/s",
                    listOf(0, 256, 512, 1024, 2048, 5120, 10240).associateWith {
                        if (it == 0) unlimited else "$it KiB/s"
                    },
                    "ytdl-settings-http-limit",
                ) { value -> onSettingsChange { it.copy(httpLimitKib = value ?: 0) } }
            }
            item {
                DownloadCountSetting(appText("同时下载的任务数", "Simultaneous downloads"), appText("${settings.maxConcurrentDownloads} 个", "${settings.maxConcurrentDownloads}"),
                    (1..5).associateWith { appText("$it 个", "$it") }, "ytdl-settings-concurrency") { value ->
                    onSettingsChange { it.copy(maxConcurrentDownloads = requireNotNull(value)) }
                }
            }
            item {
                DownloadCountSetting(appText("等待任务上限", "Queue limit"), appText("${settings.maxWaitingDownloads ?: 5} 个", "${settings.maxWaitingDownloads ?: 5}"),
                    (0..50).associateWith { appText("$it 个", "$it") },
                    "ytdl-settings-waiting-limit", appText("不计正在下载的任务", "Excludes active downloads")) { value ->
                    onSettingsChange { it.copy(maxWaitingDownloads = value) }
                }
            }
            item {
                DownloadBehaviorSwitch(appText("开始后跳转到对应任务", "Show task after starting"), settings.jumpToDownloadTask, "jump-to-task") { value ->
                    onSettingsChange { it.copy(jumpToDownloadTask = value) }
                }
            }
            item {
                AppCard(shape = RectangleShape) {
                    SectionTitle(appText("文件重名时", "When a file already exists"))
                    SegmentedRow(listOf(appText("自动编号", "Auto-rename"), appText("每次询问", "Ask each time")), settings.fileConflictPolicy.ordinal,
                        LocalYtdlAppPalette.current.settingsAccent, "ytdl-settings-file-conflict") { index ->
                        onSettingsChange { it.copy(fileConflictPolicy = HttpFileConflictPolicy.entries[index]) }
                    }
                    Text(appText("自动编号会保留两个文件。", "Auto-rename keeps both files."))
                }
            }
            item {
                DownloadBehaviorSwitch(appText("断点续传", "Resume interrupted downloads"), settings.resumeDownloads, "resume-downloads") { value ->
                    onSettingsChange { it.copy(resumeDownloads = value) }
                }
            }
            item {
                DownloadBehaviorSwitch(appText("识别到字幕时下载", "Download available subtitles"), settings.downloadDetectedSubtitles, "auto-subtitles") { value ->
                    onSettingsChange { it.copy(downloadDetectedSubtitles = value) }
                }
            }
        }
        "notifications" -> {
            item {
                SettingLineCard(appText("完成提醒", "Completion alerts"), appText("下载成功时提醒", "Notify when a download completes"), "✓", "", trailingContent = {
                    Switch(settings.notifyDownloadCompleted, { value ->
                        onSettingsChange { it.copy(notifyDownloadCompleted = value) }
                    }, modifier = Modifier.testTag("ytdl-settings-notify-completed"))
                })
            }
            item {
                SettingLineCard(appText("失败提醒", "Failure alerts"), appText("下载失败时提醒", "Notify when a download fails"), "!", "", trailingContent = {
                    Switch(settings.notifyDownloadFailed, { value ->
                        onSettingsChange { it.copy(notifyDownloadFailed = value) }
                    }, modifier = Modifier.testTag("ytdl-settings-notify-failed"))
                })
            }
            item { SettingLineCard(appText("下载进度通知", "Download progress"), appText("下载时由 Android 前台服务显示进度。", "Android shows progress while the download service is running."), "●", "") }
        }
        "storage" -> item {
            SettingLineCard(appText("清除任务记录", "Clear task history"), appText("清除已结束的任务记录，可选择同时删除文件。", "Clear finished task records and optionally delete their files."), "⌫", "›",
                modifier = Modifier.clickable(onClick = onClearHistory).testTag("ytdl-settings-clear-history"))
        }
        "legal" -> {
            item {
                SettingLineCard(appText("应用版本", "App version"), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", "i", "",
                    subtitleIsValue = true)
            }
            item {
                val mediaProcessorExplanation = SettingsExplanation(
                    appText("媒体处理能力", "Media processing"),
                    appText(
                        "使用 Android 原生组件合并独立的音视频轨道。部分 HLS 下载由 FFmpeg 封装为 MP4，保留原编码。",
                        "Android merges separate video and audio tracks. FFmpeg remuxes some HLS downloads into MP4 while preserving the original codecs.",
                    ),
                )
                SettingLineCard(
                    mediaProcessorExplanation.title,
                    appText(settingsMediaProcessorLabel(), "Track merging and HLS remuxing"),
                    "⚙",
                    "›",
                    LocalYtdlAppPalette.current.settingsAccent,
                    modifier = Modifier
                        .clickable { onShowMediaProcessorExplanation(mediaProcessorExplanation) }
                        .testTag("ytdl-settings-media-processor"),
                )
            }
            item {
                val localPrivacy = SettingsExplanation(
                    appText("本地数据与隐私", "Local data and privacy"),
                    appText(
                        "任务记录和恢复信息保存在本机。下载文件保存在所选位置。网站登录与 Cookies 在网站与登录中管理。点击剪贴板按钮时读取剪贴板。",
                        "Task and resume information stays on this device. Downloads go to the selected location. Manage site sign-ins and cookies under Sites and sign-in. The clipboard is read when you tap Paste.",
                    ),
                )
                SettingLineCard(appText("本地数据与隐私", "Local data and privacy"), appText("任务记录、文件与网站登录引用", "Task records, files, and site sign-ins"), "i", "›",
                    modifier = Modifier.clickable {
                        onShowDeclaration(localPrivacy)
                    }.testTag("ytdl-settings-local-privacy"))
            }
            item {
                SettingLineCard(appText("第三方开源许可", "Open-source licenses"), appText("组件、许可证与项目来源", "Components, licenses, and source projects"), "i", "›",
                    modifier = Modifier.clickable(onClick = onShowOpenSource).testTag("captainflash-open-source"))
            }
        }
    }
}

private data class SettingsCategory(
    val id: String,
    val title: String,
    val description: String,
    val symbol: String,
    val englishTitle: String,
    val englishDescription: String,
)

private val SettingsCategories = listOf(
    SettingsCategory("appearance", "外观", "浅色、深色与跟随系统", "◐", "Appearance", "Language and theme"),
    SettingsCategory("behavior", "下载行为", "解析器、并发、重名与续传", "↓", "Downloads", "Parser, concurrency, naming, and resume"),
    SettingsCategory("sites", "网站与登录", "授权会话与 Cookies 文件", "▤", "Sites and sign-in", "Site sessions and cookie files"),
    SettingsCategory("notifications", "通知", "系统权限与下载结果提醒", "●", "Notifications", "System permissions and download alerts"),
    SettingsCategory("storage", "存储与记录", "保存目录、缓存与任务记录", "▣", "Storage and history", "Save location, cache, and task history"),
    SettingsCategory("legal", "关于与开源", "版本、媒体处理、本地数据与许可", "i", "About and open source", "Version, media processing, local data, and licenses"),
)

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
private fun AppCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RectangleShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalYtdlAppPalette.current
    Surface(
        modifier = modifier,
        color = palette.cardBackground,
        shape = shape,
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
            text = appFormatText(if (row.label == "自动（推荐）") row.summary.removeSuffix(" 单文件").removeSuffix(" 需原生合并") else row.label),
            modifier = if (row.label == "自动（推荐）") Modifier.weight(1f) else Modifier.defaultMinSize(minWidth = 54.dp),
            color = if (row.selectable) palette.titleText else palette.softText.copy(alpha = 0.55f),
            fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (row.codecOptions.isEmpty()) {
            if (row.label != "自动（推荐）") Spacer(modifier = Modifier.weight(1f))
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
                            appFormatText(option.label),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (badge != null) {
            Surface(color = palette.mutedCardBackground, shape = RectangleShape) {
                Text(
                    appFormatText(badge),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = palette.softText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
internal fun SettingsActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val palette = LocalYtdlAppPalette.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderColor),
        content = content,
    )
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
    subtitleIsValue: Boolean = false,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
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
                    .background(leadingBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(leading, color = leadingColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = titleColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = if (subtitleIsValue) titleColor else subtitleColor,
                        style = if (subtitleIsValue) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailingContent != null) trailingContent()
            else if (trailing.isNotBlank()) Text(trailing, color = subtitleColor, style = MaterialTheme.typography.titleSmall)
        }
    }

    if (inCard) {
        AppCard(
            modifier = modifier.semantics {
                if (!enabled) disabled()
            },
            shape = RectangleShape,
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

private val VideoTaskIcon = tabIcon("VideoTask") {
    moveTo(3f, 6f); lineTo(15f, 6f); lineTo(15f, 18f); lineTo(3f, 18f); close()
    moveTo(5f, 8f); lineTo(5f, 16f); lineTo(13f, 16f); lineTo(13f, 8f); close()
    moveTo(15f, 10f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(15f, 14f); close()
}
private val AudioTaskIcon = tabIcon("AudioTask") {
    moveTo(10f, 5f); lineTo(20f, 3f); lineTo(20f, 16f); lineTo(18f, 18f); lineTo(14f, 18f)
    lineTo(13f, 16f); lineTo(14f, 14f); lineTo(18f, 14f); lineTo(18f, 7f); lineTo(12f, 8f)
    lineTo(12f, 18f); lineTo(10f, 20f); lineTo(6f, 20f); lineTo(4f, 18f); lineTo(6f, 16f); lineTo(10f, 16f); close()
}
private val FileTaskIcon = tabIcon("FileTask") {
    moveTo(5f, 2f); lineTo(14f, 2f); lineTo(20f, 8f); lineTo(20f, 22f); lineTo(5f, 22f); close()
    moveTo(7f, 4f); lineTo(7f, 20f); lineTo(18f, 20f); lineTo(18f, 9f); lineTo(13f, 9f); lineTo(13f, 4f); close()
    moveTo(9f, 12f); lineTo(16f, 12f); lineTo(16f, 14f); lineTo(9f, 14f); close()
    moveTo(9f, 16f); lineTo(16f, 16f); lineTo(16f, 17f); lineTo(9f, 17f); close()
}
private val PauseTaskIcon = tabIcon("PauseTask") {
    moveTo(8f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(8f, 19f); close()
    moveTo(14f, 5f); lineTo(16f, 5f); lineTo(16f, 19f); lineTo(14f, 19f); close()
}
private val ContinueTaskIcon = tabIcon("ContinueTask") {
    moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close()
}
private val CancelTaskIcon = tabIcon("CancelTask") {
    moveTo(5f, 6f); lineTo(6f, 5f); lineTo(19f, 18f); lineTo(18f, 19f); close()
    moveTo(18f, 5f); lineTo(19f, 6f); lineTo(6f, 19f); lineTo(5f, 18f); close()
}
private val CopySourceIcon = tabIcon("CopySource") {
    moveTo(7f, 7f); lineTo(21f, 7f); lineTo(21f, 21f); lineTo(7f, 21f); close()
    moveTo(9f, 9f); lineTo(9f, 19f); lineTo(19f, 19f); lineTo(19f, 9f); close()
    moveTo(3f, 3f); lineTo(17f, 3f); lineTo(17f, 5f); lineTo(5f, 5f)
    lineTo(5f, 17f); lineTo(3f, 17f); close()
}

private val ExportTaskIcon = tabIcon("ExportTask") {
    moveTo(4f, 3f); lineTo(12f, 3f); lineTo(16f, 7f); lineTo(16f, 9f)
    lineTo(14f, 9f); lineTo(14f, 8f); lineTo(11f, 5f); lineTo(6f, 5f)
    lineTo(6f, 19f); lineTo(14f, 19f); lineTo(14f, 16f); lineTo(16f, 16f)
    lineTo(16f, 21f); lineTo(4f, 21f); close()
    moveTo(16.5f, 8.5f); lineTo(18f, 7f); lineTo(23f, 12f); lineTo(18f, 17f)
    lineTo(16.5f, 15.5f); lineTo(19f, 13f); lineTo(10f, 13f)
    lineTo(10f, 11f); lineTo(19f, 11f); close()
}

@Composable
private fun TaskIconAction(label: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag(tag)) {
        Icon(icon, contentDescription = appMessage(label), modifier = Modifier.size(18.dp),
            tint = LocalYtdlAppPalette.current.queueAccent)
    }
}

@Composable
private fun TaskLine(
    title: String,
    titleTag: String,
    kind: FileKind,
    details: String,
    status: String,
    trailing: String,
    modifier: Modifier = Modifier,
    progress: QueueProgressPresentation? = null,
    accent: Color = LocalYtdlAppPalette.current.queueAccent,
    preview: Bitmap? = null,
    actions: @Composable () -> Unit,
) {
    val palette = LocalYtdlAppPalette.current
    val icon = when (kind) {
        FileKind.Video -> VideoTaskIcon
        FileKind.Audio -> AudioTaskIcon
        FileKind.Other -> FileTaskIcon
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = appText("视频预览图", "Video thumbnail"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 72.dp, height = 48.dp)
                        .clip(RoundedCornerShape(7.dp)).testTag("ytdl-history-thumbnail-image"),
                )
            } else Box(Modifier.size(38.dp).clip(RoundedCornerShape(7.dp))
                .background(accent.copy(alpha = 0.16f)).testTag("ytdl-task-type-icon"),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = appMessage(kind.identificationLabel()), modifier = Modifier.size(20.dp), tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TaskCardTitle(title, titleTag)
                if (details.isNotBlank()) Text(details, color = palette.softText, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        progress?.let { YtdlQueueProgressBar(it, accent, Modifier.fillMaxWidth()) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(status, color = accent, style = MaterialTheme.typography.labelSmall)
                if (trailing.isNotBlank()) {
                    Text(trailing, color = palette.softText, style = MaterialTheme.typography.labelSmall)
                }
            }
            actions()
        }
        androidx.compose.material3.HorizontalDivider(color = palette.borderColor)
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
    resolveOutput: ((HistoryOutputReference) -> HistoryOutputTarget?)? = null,
    onOutputAction: ((HistoryFileAction, HistoryOutputTarget) -> Unit)? = null,
    availabilityRevision: Int = 0,
    onCopySource: (() -> Unit)? = null,
) {
    val palette = LocalYtdlAppPalette.current
    val references = remember(item.outputUri, item.outputMimeType, item.subtitleOutputUris) { item.outputReferences() }
    val verifiedOutputs by key(availabilityRevision, references) {
        produceState<List<VerifiedHistoryOutput>?>(null) {
            if (resolveOutput == null || references.isEmpty()) {
                value = emptyList()
            } else {
                value = withContext(Dispatchers.IO) {
                    references.mapNotNull { ref -> resolveOutput(ref)?.let { VerifiedHistoryOutput(ref, it) } }
                }
            }
        }
    }
    var pendingAction by remember(item.id, availabilityRevision) { mutableStateOf<HistoryFileAction?>(null) }
    val usableOutputs = verifiedOutputs.orEmpty()
    val chooseOutput = references.size > 1 || references.singleOrNull()?.isSubtitle == true
    pendingAction?.let { action ->
        if (onOutputAction != null && usableOutputs.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                title = { Text(appText("选择要${action.label}的文件", when (action) {
                    HistoryFileAction.Open -> "Choose a file to open"
                    HistoryFileAction.Share -> "Choose a file to share"
                    HistoryFileAction.Export -> "Choose a file to export"
                })) },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        usableOutputs.forEach { output ->
                            SettingsActionButton(onClick = {
                                pendingAction = null
                                onOutputAction(action, output.target)
                            }) {
                                Text(listOf(output.reference.label, output.target.displayName).distinct().joinToString(" · "))
                            }
                        }
                    }
                },
                confirmButton = { SettingsActionButton(onClick = { pendingAction = null }) { Text(appText("取消", "Cancel")) } },
            )
        }
    }
    val availabilityMessage = if (resolveOutput == null) null else when {
        verifiedOutputs == null || references.isEmpty() && item.badge != "完成" -> null
        usableOutputs.isEmpty() -> "保存的文件不存在或无法读取，请检查存储权限。"
        usableOutputs.size < references.size -> "部分文件不存在或无法读取。"
        else -> null
    }
    val actions = if (resolveOutput == null) historyActionLabels(item) else buildList {
        if (usableOutputs.isNotEmpty()) addAll(HistoryFileAction.entries.filter { it != HistoryFileAction.Export }.map { it.label })
        add("再次下载")
        if (onCopySource != null && item.status == HistoryItemEntity.STATUS_COMPLETED) add("复制地址")
        add("删除")
    }
    fun runAction(action: String) {
        val fileAction = HistoryFileAction.entries.firstOrNull { it.label == action }
        when {
            action == "复制地址" -> onCopySource?.invoke()
            fileAction != null && onOutputAction != null -> {
                if (chooseOutput) pendingAction = fileAction
                else onOutputAction(fileAction, usableOutputs.single().target)
            }
            action == "打开" -> onOpen()
            action == "分享" -> onShare()
            action == "再次下载" -> onRetry()
            else -> onDelete()
        }
    }
    val context = LocalContext.current
    val videoTask = item.outputType == TaskOutputType.Video
    val thumbnail = taskPreview(item.thumbnailUrl.takeIf { videoTask })
    val video = usableOutputs.firstOrNull { !it.reference.isSubtitle && videoTask }?.target
    val preview by key(video, thumbnail, availabilityRevision) {
        produceState<Bitmap?>(null) {
            if (video != null && thumbnail == null) value = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        val localFile = video.localFile
                        if (localFile != null) {
                            localFile.inputStream().use { retriever.setDataSource(it.fd) }
                        } else {
                            retriever.setDataSource(context, Uri.parse(video.uri))
                        }
                        if (android.os.Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(-1, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 216, 144)
                        } else {
                            retriever.frameAtTime
                        }
                    } finally {
                        retriever.release()
                    }
                }.getOrNull()
            }
        }
    }
    TaskLine(
        title = item.title,
        titleTag = "ytdl-history-title-${item.id}",
        kind = item.outputType,
        details = listOf(item.meta.split(" · ").map { appMessage(it) }.joinToString(" · "), item.formatBadge, item.codecBadge,
            item.subtitleOutputUris.takeIf { it.isNotEmpty() }?.let {
                if (item.isTorrent) appText("${it.size + 1} 个文件", "${it.size + 1} files")
                else appText("${it.size} 个独立字幕文件", if (it.size == 1) "1 subtitle file" else "${it.size} subtitle files")
            }.orEmpty())
            .filter(String::isNotBlank).joinToString(" · "),
        status = appMessage(when (item.badge) {
            "完成" -> "已完成"
            "取消" -> "已取消"
            else -> item.badge
        }),
        trailing = appMessage(availabilityMessage.orEmpty()),
        modifier = modifier,
        accent = historyStatusBadgeAccent(item, palette),
        preview = thumbnail ?: preview,
        actions = {
            actions.forEach { action ->
                TaskIconAction(action, historyActionIcon(action), "ytdl-history-action-${item.id}-$action") { runAction(action) }
            }
        },
    )
}

private fun historyActionIcon(action: String): ImageVector = when (action) {
    "复制地址" -> CopySourceIcon
    "打开" -> HistoryOpenIcon
    "分享" -> HistoryShareIcon
    "导出" -> ExportTaskIcon
    "再次下载" -> HistoryRetryIcon
    else -> HistoryDeleteIcon
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
