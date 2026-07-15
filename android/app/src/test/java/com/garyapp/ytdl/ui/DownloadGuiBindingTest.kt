package com.garyapp.ytdl.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.text.InputType
import android.widget.EditText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.test.core.app.ApplicationProvider
import com.garyapp.ytdl.core.policy.UrlPolicy
import com.garyapp.ytdl.core.settings.AppearanceSettings
import com.garyapp.ytdl.core.settings.SettingsRepository
import com.garyapp.ytdl.core.storage.StorageTarget
import com.garyapp.ytdl.core.ytdlp.DownloadProgress
import com.garyapp.ytdl.core.ytdlp.SubtitleInfo
import com.garyapp.ytdl.core.ytdlp.SubtitleSource
import com.garyapp.ytdl.core.ytdlp.VideoAnalysis
import com.garyapp.ytdl.core.ytdlp.VideoFormat
import com.garyapp.ytdl.core.ytdlp.YtdlpBridge
import com.garyapp.ytdl.data.HistoryItemEntity
import com.garyapp.ytdl.download.DownloadRequest
import com.garyapp.ytdl.download.DownloadRoute
import com.garyapp.ytdl.download.DownloadStage
import com.garyapp.ytdl.download.DownloadTaskState
import com.garyapp.ytdl.download.RetryDownloadDraft
import com.garyapp.ytdl.ui.theme.YtdlTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadGuiBindingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appRootSemanticsExposeReferencePaletteAccent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SettingsRepository.fromContext(context).setColorPreset(AppearanceSettings.ColorPresetReferenceV3)

        composeRule.setContent { YtdlApp() }

        composeRule.onNodeWithTag("ytdl-screen-download")
            .assert(SemanticsMatcher.expectValue(YtdlColorPresetIdKey, AppearanceSettings.ColorPresetReferenceV3))
            .assert(SemanticsMatcher.expectValue(YtdlSettingsAccentArgbKey, "#FF2E86DE"))
    }

    @Test
    fun appRootSemanticsExposeCodexPaletteAccent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository.fromContext(context)
        repository.setColorPreset(AppearanceSettings.ColorPresetCodex)

        try {
            composeRule.setContent { YtdlApp() }

            composeRule.onNodeWithTag("ytdl-screen-download")
                .assert(SemanticsMatcher.expectValue(YtdlColorPresetIdKey, AppearanceSettings.ColorPresetCodex))
                .assert(SemanticsMatcher.expectValue(YtdlSettingsAccentArgbKey, "#FF2F6D80"))
        } finally {
            repository.setColorPreset(AppearanceSettings.ColorPresetReferenceV3)
        }
    }

    @Test
    fun settingsStorageCardShowsTargetResetsWithoutPickerAndOpensFolderPicker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SettingsRepository.fromContext(context)
        repository.setDefaultStorageTarget(
            StorageTarget.SafTree(
                treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMovies",
                displayName = "视频保存",
            ),
        )

        try {
            lateinit var hostContext: Context
            composeRule.setContent {
                hostContext = LocalContext.current
                YtdlApp()
            }

            composeRule.onNodeWithTag("ytdl-screen-download")
                .performScrollToNode(hasTestTag("ytdl-download-storage-target"))
            composeRule.onNodeWithTag("ytdl-download-storage-target")
                .assertHasClickAction()
            composeRule.onNodeWithText("视频保存；完成后清理 App 内中转文件").assertExists()

            composeRule.onNodeWithTag("ytdl-tab-settings").performClick()
            composeRule.onNodeWithTag("ytdl-screen-settings")
                .performScrollToNode(hasTestTag("ytdl-settings-storage-target"))
            composeRule.onNodeWithTag("ytdl-settings-storage-target")
                .assertHasClickAction()
            composeRule.onNodeWithText("保存位置").assertExists()
            composeRule.onNodeWithText("默认保存位置").assertDoesNotExist()
            composeRule.onNodeWithText("视频保存").assertExists()
            composeRule.onNodeWithText("content://", substring = true).assertDoesNotExist()
            composeRule.onNodeWithTag("ytdl-settings-storage-target-reset")
                .assertIsEnabled()

            val shadowActivity = shadowOf(hostContext.requireActivity())
            assertEquals(null, shadowActivity.nextStartedActivityForResult)
            composeRule.onNodeWithTag("ytdl-settings-storage-target-reset").performClick()

            assertEquals(
                StorageTarget.AppPrivate,
                SettingsRepository.fromContext(context).getSettings().defaultStorageTarget,
            )
            composeRule.onNodeWithText("App 私有目录").assertExists()
            composeRule.onNodeWithTag("ytdl-settings-storage-target-reset")
                .assertIsNotEnabled()
            assertEquals(null, shadowActivity.nextStartedActivityForResult)

            composeRule.onNodeWithTag("ytdl-settings-storage-target").performClick()
            assertEquals(
                Intent.ACTION_OPEN_DOCUMENT_TREE,
                shadowActivity.nextStartedActivityForResult.intent.action,
            )

            assertEquals(
                "App 私有目录；完成文件由 App 保留",
                storageTargetSummaryForUiTest(StorageTarget.AppPrivate),
            )
            val source = sourceFile(
                "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
                "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            ).readText()
            assertEquals(
                2,
                Regex("""onSelectStorageTarget = \{ storageTreePicker\.launch\(null\) \}""")
                    .findAll(source)
                    .count(),
            )
            assertEquals(2, Regex("""subtitleMaxLines = 1""").findAll(source).count())
            assertFalse(source.contains("showStorageTargetDialog"))
            assertFalse(source.contains("ytdl-storage-target-dialog"))
        } finally {
            repository.setDefaultStorageTarget(StorageTarget.AppPrivate)
        }
    }

    @Test
    fun storagePickerBindingUsesOpenDocumentTreeAndPersistableReadWriteGrant() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue(source.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(source.contains("Intent.FLAG_GRANT_WRITE_URI_PERMISSION"))
        assertTrue(source.contains("takePersistableUriPermission"))
        assertTrue(source.contains("releasePersistableUriPermission"))
    }

    @Test
    fun replacingStorageTargetReleasesOnlyThePreviousTreePermission() {
        val oldTarget = StorageTarget.SafTree(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AOld",
            displayName = "旧目录",
        )
        val newTarget = StorageTarget.SafTree(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3ANew",
            displayName = "新目录",
        )

        assertEquals(oldTarget.treeUri, storagePermissionUriToReleaseForUiTest(oldTarget, newTarget))
        assertEquals(oldTarget.treeUri, storagePermissionUriToReleaseForUiTest(oldTarget, StorageTarget.AppPrivate))
        assertEquals(null, storagePermissionUriToReleaseForUiTest(oldTarget, oldTarget.copy(displayName = "旧目录新名称")))
        assertEquals(null, storagePermissionUriToReleaseForUiTest(StorageTarget.AppPrivate, newTarget))
    }

    @Test
    fun navigationAccentsMatchReferenceAndCodexPalettes() {
        assertEquals(
            mapOf(
                "下载" to "#FFFF5B55",
                "任务" to "#FFFF7A1A",
                "设置" to "#FF2E86DE",
            ),
            ytdlNavigationAccentHexesForUiTest(AppearanceSettings.ColorPresetReferenceV3),
        )

        assertEquals(
            mapOf(
                "下载" to "#FF315C6B",
                "任务" to "#FFA26E35",
                "设置" to "#FF2F6D80",
            ),
            ytdlNavigationAccentHexesForUiTest(AppearanceSettings.ColorPresetCodex),
        )
    }

    @Test
    fun bottomNavigationHasOnlyDownloadTasksAndSettings() {
        assertEquals(
            listOf("download", "tasks", "settings"),
            ytdlNavigationDestinations().map { it.route },
        )
        assertEquals(
            listOf("下载", "任务", "设置"),
            ytdlNavigationDestinations().map { it.label },
        )
    }

    @Test
    fun bottomNavigationKeepsTabTargetsAboveSystemGestureArea() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("WindowInsets.navigationBars"))
        assertTrue(source.contains("calculateBottomPadding()"))
        assertTrue(source.contains("BottomBarGestureBuffer"))
        assertFalse(source.contains("navigationBarsPadding()"))
    }

    @Test
    fun settingsAppearanceSectionKeepsExtraBottomScrollBuffer() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("SettingsAppearanceBottomBuffer"))
        assertTrue(source.contains("Spacer(Modifier.height(SettingsAppearanceBottomBuffer))"))
    }

    @Test
    fun bottomNavigationUsesVectorIconsInsteadOfTextSymbols() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("val icon: ImageVector"))
        assertTrue(source.contains("Icon("))
        assertFalse(source.contains("text = destination.icon"))
        assertFalse(source.contains("icon = \"↓\""))
        assertFalse(source.contains("icon = \"▦\""))
        assertFalse(source.contains("icon = \"≡\""))
        assertFalse(source.contains("icon = \"◷\""))
        assertFalse(source.contains("icon = \"⚙\""))
    }

    @Test
    fun appUsesSystemSafeDrawingAndContentSpacingWithoutPunchHole() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("contentWindowInsets = WindowInsets.safeDrawing,"))
        assertTrue(source.contains("WindowInsets.navigationBars"))
        assertTrue(source.contains("private val TopContentSpacing = 16.dp"))
        val topContentSpacer = "item { Spacer(Modifier.height(TopContentSpacing)) }"
        assertTrue(source.contains(topContentSpacer))
        assertTrue(source.indexOf(topContentSpacer) < source.indexOf("item { PageHeader(selected) }"))
        assertFalse(source.contains("TopPunchHoleSafeArea"))
        assertFalse(source.contains("TopSafeAreaHeight"))
        assertFalse(source.contains("TopPunchHoleSize"))
        assertFalse(source.contains("ytdl-top-safe-area"))
        assertFalse(source.contains("ytdl-top-punch-hole"))
        assertTrue(source.contains(".testTag(\"ytdl-page-header\")"))

        composeRule.setContent { YtdlApp() }

        composeRule.onAllNodesWithTag("ytdl-top-safe-area").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ytdl-top-punch-hole").assertCountEquals(0)
        composeRule.onNodeWithTag("ytdl-page-header").assertExists()
    }

    @Test
    fun queueProgressUsesPlainBarForFrequentStageUpdates() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("YtdlQueueProgressBar("))
        assertFalse(source.contains("LinearProgressIndicator("))
    }

    @Test
    fun bottomTabRouteChangesRecreateLazyListSlots() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("key(selected.route)"))
    }

    @Test
    fun passiveRuntimeMessagesDoNotOccupyDownloadLayout() {
        assertFalse(shouldShowRuntimeMessageForUiTest("等待输入公开视频页面地址。"))
        assertFalse(shouldShowRuntimeMessageForUiTest("分析完成，可以开始下载。"))
        assertTrue(shouldShowRuntimeMessageForUiTest("请先输入公开视频页面地址。"))
        assertTrue(shouldShowRuntimeMessageForUiTest("真实下载已加入前台队列，当前阶段：等待中。"))
    }

    @Test
    fun urlBoundaryFailureMessagesStayVisibleAndDoNotExposeSensitiveInput() {
        val messages = listOf(
            "请先输入公开视频页面地址。",
            "分析失败：${UrlPolicy.evaluate("https://exa mple.com/watch?token=secret").userMessage.orEmpty()}",
            "分析失败：${UrlPolicy.evaluate("ftp://example.com/watch?token=secret").userMessage.orEmpty()}",
        )
        val joined = messages.joinToString("\n")

        messages.forEach { message ->
            assertTrue("message should be visible: $message", shouldShowRuntimeMessageForUiTest(message))
        }
        listOf("请先输入", "有效的公开视频", "http 或 https").forEach { cue ->
            assertTrue("missing readable cue $cue", joined.contains(cue))
        }
        listOf("token=secret", "secret", "exa mple.com", "example.com").forEach { sensitive ->
            assertFalse("URL boundary message leaked $sensitive", joined.contains(sensitive, ignoreCase = true))
        }
    }

    @Test
    fun urlInputShowsSoftwareKeyboardForRealisticForegroundInput() {
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_QWERTY))
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_12KEY))
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_NOKEYS))
        assertTrue(urlInputShowKeyboardOnFocusForUiTest(Configuration.KEYBOARD_UNDEFINED))
    }

    @Test
    fun urlInputWrapsFromOneToFiveLinesAndItsContainerCanGrow() {
        lateinit var hostContext: Context
        composeRule.setContent {
            hostContext = LocalContext.current
            YtdlApp()
        }

        lateinit var editText: EditText
        composeRule.runOnIdle {
            editText = hostContext.requireActivity().findViewById(com.garyapp.ytdl.R.id.ytdl_url_input)
        }

        assertEquals(1, editText.minLines)
        assertEquals(5, editText.maxLines)
        assertTrue(editText.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)

        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()
        val fieldSource = source
            .substringAfter("private fun UrlInputField(")
            .substringBefore("private fun DownloadPreviewCard(")
        assertTrue(fieldSource.contains(".defaultMinSize(minHeight = 54.dp)"))
        assertTrue(fieldSource.contains("setSingleLine(false)"))
        assertTrue(fieldSource.contains("minLines = 1"))
        assertTrue(fieldSource.contains("maxLines = 5"))
        assertFalse(fieldSource.contains(".height(54.dp)"))
    }

    @Test
    fun urlInputKeepsDarkTextOnItsLightSurfaceInDarkTheme() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SettingsRepository.fromContext(context)
            .setThemeMode(AppearanceSettings.ThemeModeDark)

        lateinit var hostContext: Context
        composeRule.setContent {
            hostContext = LocalContext.current
            YtdlApp()
        }

        lateinit var editText: EditText
        composeRule.runOnIdle {
            editText = hostContext.requireActivity().findViewById(com.garyapp.ytdl.R.id.ytdl_url_input)
        }

        assertEquals(0xFF181B17.toInt(), editText.currentTextColor)
        assertEquals(0xFF5E625C.toInt(), editText.hintTextColors.defaultColor)
    }

    @Test
    fun urlInputActivelyRequestsSoftwareKeyboardOnForegroundTouch() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("InputMethodManager"))
        assertTrue(source.contains("showSoftInput(this, 0)"))
        assertTrue(source.contains("setOnFocusChangeListener"))
        assertTrue(source.contains("setOnClickListener"))
    }

    @Test
    fun urlInputDisablesAutoHandwritingOnAndroid14AndNewer() {
        assertFalse(urlInputDisableAutoHandwritingForUiTest(33))
        assertTrue(urlInputDisableAutoHandwritingForUiTest(34))
        assertTrue(urlInputDisableAutoHandwritingForUiTest(37))
    }

    @Test
    fun urlInputProgrammaticTextSyncDoesNotEmitUserUrlChange() {
        val source = sourceFile(
            "app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
            "src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt",
        ).readText()

        assertTrue(source.contains("UrlInputTextChangeGuard"))
        assertTrue(source.contains("textChangeGuard.runProgrammaticTextUpdate"))
        assertTrue(source.contains("textChangeGuard.dispatchUserTextChange"))

        val guard = Class.forName("com.garyapp.ytdl.ui.UrlInputTextChangeGuard")
            .getDeclaredConstructor()
            .newInstance()
        val runProgrammaticTextUpdate = guard.javaClass.declaredMethods
            .single { it.name.contains("runProgrammaticTextUpdate") }
        val dispatchUserTextChange = guard.javaClass.declaredMethods
            .single { it.name.contains("dispatchUserTextChange") }
        val emitted = mutableListOf<String>()
        val onValueChange: (String) -> Unit = { emitted += it }

        runProgrammaticTextUpdate.invoke(guard, {
            dispatchUserTextChange.invoke(guard, "https://example.com/programmatic", onValueChange)
            Unit
        })
        dispatchUserTextChange.invoke(guard, "https://example.com/user", onValueChange)

        assertEquals(listOf("https://example.com/user"), emitted)
    }

    @Test
    fun formatRowsComeFromCurrentAnalysisAndDisabledRowsExplainWhy() {
        val analysis = analysisWith(
            progressiveFormat(id = "18", height = 360),
            videoOnlyFormat(id = "137", height = 1080),
            audioOnlyFormat(id = "140"),
        )
        val rows = buildFormatResolutionRows(
            analysis = analysis,
            selection = FormatSelection(mode = FormatMode.VideoAndAudio, selectedHeight = 1080),
        )

        val supported = rows.single { it.height == 1080 }
        assertTrue(supported.selectable)
        assertTrue(supported.mergeRequired)
        assertEquals("137", supported.videoFormatId)
        assertEquals("140", supported.audioFormatId)

        assertFalse(rows.any { it.height == 720 })
    }

    @Test
    fun singleFileAnalysisGreysVideoAndAudioModeOnDownloadPage() {
        val analysis = analysisWith(unknownSingleFileFormat(id = "single-file", height = 1080))
        val state = RuntimeDownloadState().withAnalysisForUiTest(analysis)

        renderDownloadPage(state)

        composeRule.onNodeWithTag("ytdl-download-mode-av").assertIsNotEnabled()
        composeRule.onNodeWithTag("ytdl-download-mode-video").assertIsEnabled()
        composeRule.onNodeWithText("视频").assertExists()
    }

    @Test
    fun audioModeOnDownloadPageDisablesVideoSettingsAndShowsAudioFormat() {
        val analysis = analysisWith(
            progressiveFormat(id = "18", height = 360),
            audioOnlyFormat(id = "140"),
        )
        val selection = selectBestAvailableFormatSelection(analysis, FormatMode.AudioOnly)
        val state = RuntimeDownloadState()
            .withAnalysisForUiTest(analysis)
            .copy(formatSelection = selection, appliedFormatSelection = selection)

        renderDownloadPage(state)

        composeRule.onNodeWithTag("test-download-list")
            .performScrollToNode(hasTestTag("ytdl-format-audio-card"))
        composeRule.onNodeWithText("音频格式").assertExists()
        composeRule.onNodeWithTag("ytdl-format-video-resolution-line").assertIsNotEnabled()
        composeRule.onNodeWithTag("ytdl-format-frame-rate-line").assertIsNotEnabled()
        composeRule.onNodeWithTag("ytdl-format-video-codec-line").assertIsNotEnabled()
        composeRule.onNodeWithTag("test-download-list")
            .performScrollToNode(hasTestTag("ytdl-format-summary"))
        composeRule.onNodeWithText("音频 M4A 单文件", substring = true).assertExists()
    }

    @Test
    fun selecting240pPersistsToDownloadSummaryAcrossNavigation() {
        val analysis = analysisWith(
            progressiveFormat(id = "720-direct", height = 720),
            progressiveFormat(id = "240-direct", height = 240),
        )
        val state = mutableStateOf(RuntimeDownloadState().withAnalysisForUiTest(analysis))
        composeRule.setContent {
            YtdlTheme {
                LazyColumn(Modifier.testTag("test-download-list")) {
                    downloadPageItems(
                        state = state.value,
                        storageTarget = StorageTarget.AppPrivate,
                        hasUserConfirmed = false,
                        onUrlChange = {},
                        onAnalyze = {},
                        onStartDownload = {},
                        onUserConfirmedChange = {},
                        onModeSelected = {},
                        onSelectStorageTarget = {},
                        onFormatSelectionChange = { selection ->
                            state.value = state.value.withFormatSelection(selection)
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("test-download-list")
            .performScrollToNode(hasTestTag("ytdl-format-row-240"))
        composeRule.onNodeWithTag("ytdl-format-row-240").performClick()

        composeRule.onNodeWithTag("test-download-list")
            .performScrollToNode(hasTestTag("ytdl-format-summary"))
        composeRule.onNodeWithText("240p MP4 H.264 单文件", substring = true).assertExists()
    }

    @Test
    fun selectingCodecKeepsOneResolutionRowAndEmitsExactFormatId() {
        val analysis = analysisWith(
            progressiveFormat(id = "h264-1080", height = 1080, videoCodec = "avc1"),
            progressiveFormat(id = "av1-1080", height = 1080, videoCodec = "av01"),
        )
        val selection = mutableStateOf(defaultFormatSelection(analysis))
        composeRule.setContent {
            YtdlTheme {
                LazyColumn(Modifier.testTag("test-download-list")) {
                    formatSelectionItems(
                        analysis = analysis,
                        selection = selection.value,
                        onSelectionChange = { selection.value = it },
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag("ytdl-format-row-1080").assertCountEquals(1)
        composeRule.onNodeWithTag("ytdl-format-codec-1080-h264").assertExists()
        composeRule.onNodeWithTag("ytdl-format-codec-1080-av1").performClick()

        composeRule.runOnIdle {
            assertEquals(1080, selection.value.selectedHeight)
            assertEquals("av1-1080", selection.value.selectedVideoFormatId)
        }
    }

    @Test
    fun retryAnalysisRestoresPreviousModeCodecAndResolutionWhenStillAvailable() {
        val analysis = analysisWith(
            progressiveFormat(id = "h264-1080", height = 1080, videoCodec = "avc1"),
            progressiveFormat(id = "av1-1080", height = 1080, videoCodec = "av01"),
            audioOnlyFormat(id = "audio-140"),
        )

        val restored = retryFormatSelection(
            analysis,
            RetryDownloadDraft(
                url = TestUrl,
                route = DownloadRoute.DirectSingleFile("av1-1080"),
            ),
        )

        assertEquals(FormatMode.VideoOnly, restored.mode)
        assertEquals(1080, restored.selectedHeight)
        assertEquals("av1-1080", restored.selectedVideoFormatId)
        assertEquals(null, restored.selectedAudioFormatId)

        val fallback = retryFormatSelection(
            analysis,
            RetryDownloadDraft(
                url = TestUrl,
                route = DownloadRoute.DirectSingleFile("missing-video"),
            ),
        )
        assertEquals(FormatMode.VideoOnly, fallback.mode)
        assertEquals("h264-1080", fallback.selectedVideoFormatId)
        assertEquals(null, fallback.selectedAudioFormatId)
    }

    @Test
    fun retryAnalysisRestoresExactAudioFormatWhenStillAvailable() {
        val analysis = analysisWith(
            videoOnlyFormat(id = "video-137", height = 1080),
            audioOnlyFormat(id = "audio-140"),
            audioOnlyFormat(id = "audio-251"),
        )

        val mergeSelection = retryFormatSelection(
            analysis,
            RetryDownloadDraft(
                url = TestUrl,
                route = DownloadRoute.MergeRequired("video-137", "audio-251"),
            ),
        )
        assertEquals(FormatMode.VideoAndAudio, mergeSelection.mode)
        assertEquals("video-137", mergeSelection.selectedVideoFormatId)
        assertEquals("audio-251", mergeSelection.selectedAudioFormatId)

        val audioSelection = retryFormatSelection(
            analysis,
            RetryDownloadDraft(
                url = TestUrl,
                route = DownloadRoute.AudioOnly("audio-251"),
            ),
        )
        assertEquals(FormatMode.AudioOnly, audioSelection.mode)
        assertEquals("audio-251", audioSelection.selectedAudioFormatId)
    }

    @Test
    fun downloadPageWithoutAnalysisHidesFormatDetailsAndFormatTab() {
        composeRule.setContent { YtdlApp() }

        composeRule.onAllNodesWithTag("ytdl-tab-formats").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ytdl-format-resolution-card").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ytdl-format-row-auto").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ytdl-format-row-1080").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ytdl-format-summary").assertCountEquals(0)
        composeRule.onAllNodesWithText("返回下载页").assertCountEquals(0)
    }

    @Test
    fun modeSelectionFallsBackToExecutableChoiceForCurrentAnalysis() {
        val analysis = analysisWith(
            progressiveFormat(id = "18", height = 360),
            audioOnlyFormat(id = "140"),
        )

        val selection = selectBestAvailableFormatSelection(
            analysis = analysis,
            mode = FormatMode.AudioOnly,
            preferredHeight = 1080,
        )
        val request = buildAppliedDownloadRequest(TestUrl, analysis, selection).getOrThrow()

        assertEquals(FormatMode.AudioOnly, selection.mode)
        assertEquals(null, selection.selectedHeight)
        assertEquals("140", selection.selectedAudioFormatId)
        assertEquals(DownloadRoute.AudioOnly(audioFormatId = "140"), request.route)
    }

    @Test
    fun buildAppliedDownloadRequestCarriesManagedTemporaryCookiesPath() {
        val analysis = analysisWith(progressiveFormat(id = "18", height = 360))
        val selection = selectBestAvailableFormatSelection(
            analysis = analysis,
            mode = FormatMode.VideoOnly,
            preferredHeight = 360,
        )

        val request = buildAppliedDownloadRequest(
            url = TestUrl,
            analysis = analysis,
            appliedSelection = selection,
            cookiesPath = "/data/user/0/com.garyapp.ytdl/cache/temporary-cookies/ytdl-cookies-task.txt",
        ).getOrThrow()

        assertEquals("/data/user/0/com.garyapp.ytdl/cache/temporary-cookies/ytdl-cookies-task.txt", request.cookiesPath)
    }

    @Test
    fun historyRowsRenderAsRealHistoryCardsWithoutRawSecrets() {
        val rows = listOf(
            HistoryItemEntity.createSafe(
                "完成视频 Cookie: SID=secret",
                60,
                "https",
                "host-hash",
                "youtube",
                "app-private://outputs/%E6%B5%8B%E8%AF%95%20video.mp4",
                "1080p --cookies D:/private/cookies.txt",
                HistoryItemEntity.STATUS_COMPLETED,
                100,
                "",
                "",
                null,
                1_000,
                1_000,
                1_000,
            ),
            HistoryItemEntity.createSafe(
                "失败视频",
                60,
                "https",
                "host-hash",
                "web",
                "",
                "720p",
                HistoryItemEntity.STATUS_FAILED,
                0,
                "",
                "",
                "network failed Authorization: Bearer raw-token",
                2_000,
                2_000,
                2_000,
            ),
        )

        val cards = historyUiItemsFromRows(rows)
        val serialized = cards.joinToString()

        assertEquals(listOf("完成", "失败"), cards.map { it.badge })
        assertTrue(serialized.contains("完成视频"))
        assertTrue(serialized.contains("失败视频"))
        assertTrue(serialized.contains("app-private://outputs/%E6%B5%8B%E8%AF%95%20video.mp4"))
        assertFalse(cards.first().meta.contains("app-private://"))
        assertFalse(cards.first().meta.contains("%20"))
        assertFalse(cards.first().meta.contains("媒体文件"))
        assertTrue(cards.first().meta.contains("测试 video.mp4"))
        assertEquals(listOf("打开", "分享", "删除"), historyActionLabelsForUiTest(cards.first()))
        assertEquals(listOf("删除"), historyActionLabelsForUiTest(cards.last()))
        listOf("SID=secret", "--cookies", "raw-token", "Authorization").forEach {
            assertFalse("history UI leaked $it", serialized.contains(it))
        }
    }

    @Test
    fun retryableHistoryCardInvokesAgainDownloadCallback() {
        val item = HistoryUiItem(
            id = 8L,
            title = "断线任务",
            meta = "720p MP4 H.264 单文件",
            badge = "失败",
            outputUri = "",
            status = HistoryItemEntity.STATUS_FAILED,
            completedAt = 1_000L,
            retryAvailable = true,
        )
        var retriedHistoryId: Long? = null
        composeRule.setContent {
            YtdlTheme {
                LazyColumn {
                    historyPageItems(
                        historyItems = listOf(item),
                        historyQuery = "",
                        selectedFilterIndex = 0,
                        userMessage = "",
                        onHistoryQueryChange = {},
                        onHistoryFilterChange = {},
                        onOpen = {},
                        onShare = {},
                        onRetry = { retriedHistoryId = it.id },
                        onDelete = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("ytdl-history-action-8-再次下载").performClick()

        composeRule.runOnIdle { assertEquals(8L, retriedHistoryId) }
    }

    @Test
    fun newAnalysisReplacesStaleAppliedFormatIdsAndPreviewSummary() {
        val oldAnalysis = analysisWith(progressiveFormat(id = "18", height = 360))
        val oldState = RuntimeDownloadState(
            url = TestUrl,
            analysis = oldAnalysis,
            appliedFormatSelection = FormatSelection(
                mode = FormatMode.VideoAndAudio,
                selectedHeight = 360,
                selectedVideoFormatId = "18",
            ),
        )
        val newAnalysis = analysisWith(progressiveFormat(id = "22", height = 720))

        val newState = oldState.withAnalysisForUiTest(newAnalysis)
        val request = buildAppliedDownloadRequest(
            url = TestUrl,
            analysis = newState.analysis,
            appliedSelection = newState.appliedFormatSelection,
        ).getOrThrow()

        assertEquals("22", newState.appliedFormatSelection.selectedVideoFormatId)
        assertEquals(DownloadRoute.DirectSingleFile(formatId = "22"), request.route)
        assertTrue(downloadPreviewFormatSummaryForUiTest(newState).contains("720p"))
        assertFalse(downloadPreviewFormatSummaryForUiTest(newState).contains("360p"))
    }

    @Test
    fun foregroundStartShowsImmediateQueueAndCurrentStage() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val state = RuntimeDownloadState()
            .withForegroundStartStateForUiTest(DownloadTaskState.waiting(request))

        assertTrue(state.hasRealTask)
        assertTrue(state.isDownloading)
        assertEquals("等待中", state.downloadStatus)
        assertTrue(shouldShowCurrentTaskForUiTest(state))
        assertEquals("当前阶段 · 等待中", queueCardSubtitleForUiTest(state))
        assertTrue(state.userMessage.contains("已加入前台队列"))
        assertTrue(state.userMessage.contains("当前阶段：等待中"))
    }

    @Test
    fun currentTaskOnlyExposesCancelAction() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val running = RuntimeDownloadState()
            .withPipelineStateForUiTest(
                DownloadTaskState.waiting(request)
                    .atStage(DownloadStage.DownloadingVideo)
                    .withProgress(
                        DownloadProgress(
                            status = "downloading",
                            percent = 42.0,
                            downloadedBytes = 42,
                            totalBytes = 100,
                            speedBytesPerSecond = 10.0,
                            etaSeconds = 6,
                            filename = "video.mp4",
                        ),
                    ),
            )
        var canceled = false

        renderTasksPage(running, emptyList(), emptyList(), onCancelDownload = { canceled = true })
        composeRule.onNodeWithTag("ytdl-queue-cancel-action").performClick()

        composeRule.runOnIdle { assertTrue(canceled) }
    }

    @Test
    fun realQueuePageRendersAnalysisThumbnailNodeWhenAvailable() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val thumbnail = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val runningWithThumbnail = RuntimeDownloadState(thumbnailBitmap = thumbnail)
            .withPipelineStateForUiTest(DownloadTaskState.waiting(request))

        renderTasksPage(runningWithThumbnail, emptyList(), emptyList())

        composeRule.onAllNodesWithTag("ytdl-real-queue-card").assertCountEquals(1)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-image").assertCountEquals(1)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-placeholder").assertCountEquals(0)
    }

    @Test
    fun realQueuePageRendersPlaceholderNodeWhenThumbnailIsMissing() {
        val request = requestFor(progressiveFormat(id = "18", height = 360))
        val runningWithoutThumbnail = RuntimeDownloadState()
            .withPipelineStateForUiTest(DownloadTaskState.waiting(request))

        renderTasksPage(runningWithoutThumbnail, emptyList(), emptyList())

        composeRule.onAllNodesWithTag("ytdl-real-queue-card").assertCountEquals(1)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-image").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ytdl-queue-thumbnail-placeholder").assertCountEquals(1)
    }

    @Test
    fun downloadModeCardsReflectAppliedSelection() {
        val audioState = RuntimeDownloadState(
            appliedFormatSelection = FormatSelection(mode = FormatMode.AudioOnly),
        )
        val videoOnlyState = RuntimeDownloadState(
            appliedFormatSelection = FormatSelection(mode = FormatMode.VideoOnly),
        )

        assertTrue(downloadModeSelectionsForUiTest(audioState).getValue(FormatMode.AudioOnly))
        assertFalse(downloadModeSelectionsForUiTest(audioState).getValue(FormatMode.VideoAndAudio))
        assertFalse(downloadModeSelectionsForUiTest(audioState).getValue(FormatMode.VideoOnly))

        assertTrue(downloadModeSelectionsForUiTest(videoOnlyState).getValue(FormatMode.VideoOnly))
        assertFalse(downloadModeSelectionsForUiTest(videoOnlyState).getValue(FormatMode.VideoAndAudio))
    }

    @Test
    fun formatSettingSummariesComeFromSelectedFormats() {
        val analysis = analysisWith(
            videoOnlyFormat(id = "137", height = 1080, fps = 60.0, ext = "mp4", videoCodec = "avc1"),
            audioOnlyFormat(id = "140", ext = "m4a"),
        )
        val selection = selectBestAvailableFormatSelection(
            analysis = analysis,
            mode = FormatMode.VideoAndAudio,
            preferredHeight = 1080,
        )

        val summaries = formatSettingSummariesForUiTest(analysis, selection)

        assertEquals("60fps", summaries.frameRate)
        assertEquals("avc1", summaries.videoCodec)
        assertEquals("MP4（原生合并输出）", summaries.container)
    }

    @Test
    fun settingsLabelsReflectPinnedParserAndMvp1MediaCapabilities() {
        assertEquals("yt-dlp ${YtdlpBridge.PINNED_YTDLP_VERSION}", settingsParserVersionLabelForUiTest())

        val mediaLabel = settingsMediaProcessorLabelForUiTest()
        assertTrue(mediaLabel.contains("原生合并"))
        assertTrue(mediaLabel.contains("不转码"))
        assertFalse(mediaLabel.contains("字幕"))
    }

    @Test
    fun backendRequestCanStillCarryExplicitSubtitleSelection() {
        val subtitle = SubtitleInfo(language = "en", ext = "vtt", source = SubtitleSource.Automatic)
        val analysis = analysisWith(progressiveFormat(id = "18", height = 360)).copy(subtitles = listOf(subtitle))
        val selection = defaultFormatSelection(analysis)

        val request = buildAppliedDownloadRequest(
            url = TestUrl,
            analysis = analysis,
            appliedSelection = selection,
            selectedSubtitles = listOf(subtitle),
        ).getOrThrow()

        assertEquals(listOf(subtitle), request.selectedSubtitles)
    }

    @Test
    fun downloadPreviewSummaryHidesSubtitleContent() {
        val subtitle = SubtitleInfo(language = "zh-Hans", ext = "vtt", source = SubtitleSource.Automatic)
        val analysis = analysisWith(progressiveFormat(id = "18", height = 360)).copy(subtitles = listOf(subtitle))
        val state = RuntimeDownloadState(
            analysis = analysis,
            appliedFormatSelection = defaultFormatSelection(analysis),
        )

        val summary = downloadPreviewFormatSummaryForUiTest(state)

        assertTrue(summary.contains("360p"))
        assertFalse(summary.contains("字幕"))
    }

    @Test
    fun unsupportedAnalysisInputReturnsChineseErrorBeforePythonStarts() {
        val bridge = YtdlpBridge { error("不应启动 Python") }

        val result = bridge.analyze("ftp://example.com/video")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("http"))
    }

    @Test
    fun tasksPageShowsCurrentAndWaitingCardsTogether() {
        val current = requestFor(progressiveFormat(id = "18", height = 360))
        val pending = requestFor(progressiveFormat(id = "22", height = 720)).copy(title = "下一条视频")
        val running = RuntimeDownloadState().withPipelineStateForUiTest(
            DownloadTaskState.waiting(current),
        )

        renderTasksPage(running, listOf(pending), emptyList())

        composeRule.onNodeWithTag("ytdl-real-queue-card").assertExists()
        composeRule.onNodeWithTag("ytdl-pending-queue-card-0").assertExists()
        composeRule.onNodeWithText("等待中（1）").assertExists()
        composeRule.onNodeWithText("下一条视频").assertExists()
    }

    @Test
    fun tasksScrollbarAppearsForOverflowAndTracksScroll() {
        val historyItems = (1L..20L).map { id ->
            HistoryUiItem(
                id = id,
                title = "历史任务 $id",
                meta = "07/15 12:00",
                badge = "完成",
                outputUri = "app-private://outputs/video-$id.mp4",
                status = HistoryItemEntity.STATUS_COMPLETED,
                completedAt = id,
                formatBadge = "720p",
                codecBadge = "H.264",
            )
        }
        composeRule.setContent {
            YtdlApp(historyItemsProvider = { historyItems })
        }

        composeRule.onNodeWithTag("ytdl-tab-tasks").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("ytdl-tasks-scroll-thumb")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val initialTop = composeRule.onNodeWithTag("ytdl-tasks-scroll-thumb")
            .getUnclippedBoundsInRoot().top

        composeRule.onNodeWithTag("ytdl-screen-tasks").performTouchInput { swipeUp() }

        composeRule.waitUntil(5_000) {
            composeRule.onNodeWithTag("ytdl-tasks-scroll-thumb")
                .getUnclippedBoundsInRoot().top > initialTop
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp")
    fun tasksScrollbarStaysHiddenWhenHistoryFits() {
        val historyItem = HistoryUiItem(
            id = 1L,
            title = "单条历史任务",
            meta = "07/15 12:00",
            badge = "完成",
            outputUri = "app-private://outputs/video.mp4",
            status = HistoryItemEntity.STATUS_COMPLETED,
            completedAt = 1L,
        )
        composeRule.setContent {
            YtdlApp(historyItemsProvider = { listOf(historyItem) })
        }

        composeRule.onNodeWithTag("ytdl-tab-tasks").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("单条历史任务").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("单条历史任务").assertExists()
        composeRule.onAllNodesWithTag("ytdl-tasks-scroll-indicator").assertCountEquals(0)
    }

    @Test
    fun tasksPageUsesHistoryCardAfterCurrentTaskEnds() {
        val history = HistoryUiItem(
            id = 99L,
            title = "已完成的视频",
            meta = "07/15 12:00",
            badge = "完成",
            outputUri = "app-private://outputs/video.mp4",
            status = HistoryItemEntity.STATUS_COMPLETED,
            completedAt = 99L,
        )
        val completed = RuntimeDownloadState(
            activeStage = DownloadStage.Completed,
            downloadStatus = "下载完成",
        )

        renderTasksPage(completed, emptyList(), listOf(history))

        composeRule.onAllNodesWithTag("ytdl-real-queue-card").assertCountEquals(0)
        composeRule.onNodeWithTag("ytdl-history-real-card").assertExists()
        composeRule.onNodeWithText("已完成的视频").assertExists()
    }

    @Test
    fun historyCardKeepsDeleteBesideOtherActions() {
        val completed = HistoryUiItem(
            id = 101L,
            title = "已完成的视频",
            meta = "07/15 12:00",
            badge = "完成",
            outputUri = "app-private://outputs/video.mp4",
            status = HistoryItemEntity.STATUS_COMPLETED,
            completedAt = 101L,
        )
        val canceled = HistoryUiItem(
            id = 102L,
            title = "已取消的视频",
            meta = "07/15 12:01",
            badge = "取消",
            outputUri = "",
            status = HistoryItemEntity.STATUS_CANCELED,
            completedAt = 102L,
            retryAvailable = true,
        )

        renderTasksPage(RuntimeDownloadState(), emptyList(), listOf(completed, canceled))

        val openTop = composeRule.onNodeWithTag("ytdl-history-action-101-打开")
            .getUnclippedBoundsInRoot().top
        val shareTop = composeRule.onNodeWithTag("ytdl-history-action-101-分享")
            .getUnclippedBoundsInRoot().top
        val completedDeleteTop = composeRule.onNodeWithTag("ytdl-history-action-101-删除")
            .getUnclippedBoundsInRoot().top
        assertEquals(openTop, shareTop)
        assertEquals(openTop, completedDeleteTop)

        val retryTop = composeRule.onNodeWithTag("ytdl-history-action-102-再次下载")
            .getUnclippedBoundsInRoot().top
        val canceledDeleteTop = composeRule.onNodeWithTag("ytdl-history-action-102-删除")
            .getUnclippedBoundsInRoot().top
        assertEquals(retryTop, canceledDeleteTop)
    }

    @Test
    fun historyCardKeepsThreeTrailingBadgesEvenlySeparated() {
        val completed = HistoryUiItem(
            id = 103L,
            title = "三枚标签的视频",
            meta = "视频 · 07/15 12:02",
            badge = "完成",
            outputUri = "app-private://outputs/video.mp4",
            status = HistoryItemEntity.STATUS_COMPLETED,
            completedAt = 103L,
            formatBadge = "720p",
            codecBadge = "H.264",
        )

        renderTasksPage(RuntimeDownloadState(), emptyList(), listOf(completed))

        val statusBounds = composeRule.onNodeWithTag("ytdl-history-status-badge")
            .getUnclippedBoundsInRoot()
        val codecBounds = composeRule.onNodeWithTag("ytdl-history-codec-badge")
            .getUnclippedBoundsInRoot()
        val formatBounds = composeRule.onNodeWithTag("ytdl-history-format-badge")
            .getUnclippedBoundsInRoot()
        val firstGap = codecBounds.top - statusBounds.bottom
        val secondGap = formatBounds.top - codecBounds.bottom

        assertTrue(firstGap.value >= 0f)
        assertTrue(secondGap.value >= 0f)
        assertEquals(firstGap.value, secondGap.value, 0.5f)
        assertTrue((statusBounds.bottom - statusBounds.top).value <= 28.5f)
        assertTrue((codecBounds.bottom - codecBounds.top).value <= 28.5f)
        assertTrue((formatBounds.bottom - formatBounds.top).value <= 28.5f)
    }

    private fun requestFor(vararg formats: VideoFormat): DownloadRequest {
        val analysis = analysisWith(*formats)
        return DownloadRequest.fromAnalysis(
            url = TestUrl,
            analysis = analysis,
            selection = defaultFormatSelection(analysis),
        ).getOrThrow()
    }

    private fun renderTasksPage(
        state: RuntimeDownloadState,
        pendingRequests: List<DownloadRequest>,
        historyItems: List<HistoryUiItem>,
        onCancelDownload: () -> Unit = {},
    ) {
        composeRule.setContent {
            YtdlTheme {
                LazyColumn {
                    tasksPageItems(
                        state = state,
                        pendingRequests = pendingRequests,
                        historyItems = historyItems,
                        historyQuery = "",
                        selectedFilterIndex = 0,
                        userMessage = "",
                        onCancelDownload = onCancelDownload,
                        onHistoryQueryChange = {},
                        onHistoryFilterChange = {},
                        onOpen = {},
                        onShare = {},
                        onRetry = {},
                        onDelete = {},
                    )
                }
            }
        }
    }

    private fun renderDownloadPage(state: RuntimeDownloadState) {
        composeRule.setContent {
            YtdlTheme {
                LazyColumn(Modifier.testTag("test-download-list")) {
                    downloadPageItems(
                        state = state,
                        storageTarget = StorageTarget.AppPrivate,
                        hasUserConfirmed = false,
                        onUrlChange = {},
                        onAnalyze = {},
                        onStartDownload = {},
                        onUserConfirmedChange = {},
                        onModeSelected = {},
                        onSelectStorageTarget = {},
                    )
                }
            }
        }
    }

    private fun analysisWith(vararg formats: VideoFormat) = VideoAnalysis(
        title = "测试视频",
        durationSeconds = 60,
        thumbnailUrl = null,
        formats = formats.toList(),
        subtitles = emptyList<SubtitleInfo>(),
    )

    private fun progressiveFormat(
        id: String,
        height: Int,
        videoCodec: String = "avc1",
    ) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = videoCodec,
        audioCodec = "mp4a",
    )

    private fun videoOnlyFormat(
        id: String,
        height: Int,
        fps: Double? = null,
        ext: String = "mp4",
        videoCodec: String = "avc1",
    ) = VideoFormat(
        id = id,
        ext = ext,
        height = height,
        label = "${height}p 需合并音频",
        hasVideo = true,
        hasAudio = false,
        mergeRequired = true,
        isSupported = true,
        videoCodec = videoCodec,
        audioCodec = "none",
        fps = fps,
    )

    private fun unknownSingleFileFormat(id: String, height: Int) = VideoFormat(
        id = id,
        ext = "mp4",
        height = height,
        label = "${height}p",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = null,
        audioCodec = null,
    )

    private fun audioOnlyFormat(
        id: String,
        ext: String = "m4a",
    ) = VideoFormat(
        id = id,
        ext = ext,
        height = null,
        label = "音频",
        hasVideo = false,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = "none",
        audioCodec = "mp4a",
    )

    private fun sourceFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("source file not found: ${candidates.joinToString()}")
    }

    private companion object {
        const val TestUrl = "https://www.youtube.com/watch?v=tkxzMEfp49Q"
    }

    private fun Context.requireActivity(): Activity {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity ?: error("Compose host activity not found")
    }
}
