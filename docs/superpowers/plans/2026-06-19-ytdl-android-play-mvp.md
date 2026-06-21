# YTDL Android Play MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android Google Play MVP as a real runnable Android app with the approved five-page GUI, yt-dlp analysis/download path, native Android MediaMuxer merge path, separate subtitle-file output, foreground progress, local history, Play-safe storage/cookies handling, and real emulator verification.

**Architecture:** Android lives in a new `android/` project so Windows code remains isolated. Kotlin/Compose owns UI, state, Room, foreground service, storage, and policy gates; Chaquopy embeds Python `yt-dlp` for analysis/download; Android `MediaProcessor` uses `MediaExtractor + MediaMuxer` for compatible split video/audio merge, while subtitle files are downloaded and exported separately in MVP1. FFmpeg-based subtitle embed/burn is MVP2 scope.

**Tech Stack:** Gradle Wrapper 9.4.1, AGP 9.2.1, Kotlin 2.3.0, Jetpack Compose Material 3, Room 2.8.4, Chaquopy 17.0.0, compileSdk/targetSdk 37, minSdk 24, Python 3.12 from `D:/garyapp/ytdl/.venv/Scripts/python.exe`, Android API37 x86_64 Play emulator for primary smoke.

---

## Preconditions

- Run `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1` before starting Android work in each session.
- Keep `AGENTS.md` and `.qa-android-env/` ignored and uncommitted.
- Do not edit Windows GUI/source files unless a task explicitly says so.
- Use test URL for full-flow verification: `https://www.youtube.com/watch?v=tkxzMEfp49Q`.
- Do not mark a task complete until its tests and required real runtime check have fresh output.
- UI fidelity is a hard requirement, not a loose theme hint: final Android screens must visually match `docs/android-gui-reference-v3.png` as closely as the native Android runtime allows, including the five-page composition, bottom navigation, card density, accent colors, top safe area, queue scrolling, and progress presentation.
- The Settings page must include an appearance/color section modeled after Codex-style appearance settings: mode selection plus color preset selection. The default preset remains `reference_v3`; a `codex` preset must be available.
- Current corrective priority after user review: stop treating GUI shell/label checks as progress until the core media path exists. Immediate order is required URL core merge -> subtitle-file download/output model -> download orchestration/foreground state -> GUI binding -> history/export/privacy/failure coverage -> foreground Computer Use full acceptance. Backend, instrumentation, adb, and build checks remain supporting evidence only; Android acceptance requires a foreground visible emulator window GUI run for the relevant flow.
- Agent hygiene: use one fresh subagent only for the active plan task or its audit, close it as soon as the result is integrated, and do not keep explorer/audit/worker agents idle. Do not reuse an old worker to continue a different task. If an old agent ID is unavailable after compaction, do not assume it is still active; continue with the current manager state and create a fresh task-scoped agent only when needed.
- Testing hygiene: no mock download, mock progress, static queue demo, background-only automation, or Android soft-keyboard driven path can be counted as acceptance. Such checks may be recorded only as auxiliary evidence, never as final pass.

## Task Map

### Task 1: Android Project Skeleton

**Purpose:** Create the formal Android project under `android/` with the verified toolchain and a five-tab Compose shell that launches on the API37 emulator.

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/gradlew.bat`
- Create: `android/gradlew`
- Create: `android/gradle/wrapper/gradle-wrapper.jar`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/garyapp/ytdl/MainActivity.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/theme/Theme.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/SmokeUnitTest.kt`
- Modify: `.gitignore` only if Android build outputs are not already ignored.

**Steps:**
- [ ] Generate Gradle Wrapper 9.4.1 from `D:\DevTools\gradle-9.4.1\bin\gradle.bat wrapper --gradle-version 9.4.1`.
- [ ] Configure plugins/toolchain: `com.android.application` 9.2.1, Kotlin/Compose 2.3.0, `org.jetbrains.kotlin.plugin.compose` 2.3.0, `com.chaquo.python` 17.0.0. If AGP 9.2.1 rejects direct `org.jetbrains.kotlin.android` application in the app module, keep Kotlin Android support in the verified AGP-integrated form as long as `compileDebugKotlin`, unit tests, and `assembleDebug` pass.
- [ ] Configure Android: namespace `com.garyapp.ytdl`, `compileSdk = 37`, `targetSdk = 37`, `minSdk = 24`, app id `com.garyapp.ytdl`.
- [ ] Configure Chaquopy with `buildPython("D:/garyapp/ytdl/.venv/Scripts/python.exe")`.
- [ ] Implement a real `MainActivity` and Compose shell with bottom navigation labels `下载`, `格式`, `队列`, `历史`, `设置`.
- [ ] Add a unit test asserting the five navigation labels remain present in the app model.
- [ ] Reserve the appearance/color settings model in the shell: `System/Light/Dark` theme mode and at least `reference_v3` plus `codex` color presets, without building the full settings UI yet.
- [ ] Verify:
  - `.\gradlew.bat :app:testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
  - Install and launch on `ytdl_api37_play_x86_64` using `adb install` and `adb shell am start`.
- [ ] Record Task 1 smoke evidence under `docs/qa/` if a runtime timing caveat or launch issue is discovered.
- [ ] Commit with message `android: scaffold Play MVP app`.

### Task 2: Policy, Settings, and Storage Core

**Purpose:** Add the non-negotiable Play-safe policy layer before any network/download code.

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/policy/UrlPolicy.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/settings/AppSettings.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/settings/SettingsRepository.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/storage/StorageTargets.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/privacy/SensitiveText.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/core/policy/UrlPolicyTest.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/core/privacy/SensitiveTextTest.kt`

**Steps:**
- [ ] Add only basic URL checks for empty input, malformed URLs, and non-http/https schemes.
- [ ] Do not hard-block domains in `UrlPolicy`; domain-specific policy belongs outside the first Android MVP unless the user explicitly approves it.
- [ ] Add cookies reference model that stores only URI/path references, never contents.
- [ ] Add sanitizer tests proving cookies, authorization headers, and query secrets are redacted.
- [ ] Add storage target model for app-private, MediaStore Downloads, single-document export, and SAF tree URI.
- [ ] Verify `.\gradlew.bat :app:testDebugUnitTest`.
- [ ] Commit with message `android: add Play-safe policy core`.

### Task 3: Room Queue and History State

**Purpose:** Persist queue, history, settings summaries, and recoverable states without sensitive data leakage.

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/data/YtdlDatabase.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/data/QueueItemEntity.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/data/HistoryItemEntity.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/data/QueueDao.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/data/HistoryDao.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/data/QueueHistoryRepositoryTest.kt`

**Steps:**
- [ ] Add Room entities with fields for title, duration, safe source summary, output URI, format summary, status, progress, speed, ETA, created/updated time.
- [ ] Do not persist cookies, raw headers, full command lines, or unredacted sensitive query strings.
- [ ] Add DAO tests for insert/update/complete/fail/history listing.
- [ ] Verify `.\gradlew.bat :app:testDebugUnitTest`.
- [ ] Commit with message `android: add queue and history persistence`.

### Task 4: Chaquopy yt-dlp Analysis Bridge

**Purpose:** Analyze a single authorized/public URL through Python `yt-dlp` without blocking app startup.

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/python/ytdl_bridge.py`
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/YtdlpBridge.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/VideoAnalysis.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/core/ytdlp/FormatMappingTest.kt`

**Steps:**
- [ ] Package a tested Python `yt-dlp` dependency through Chaquopy, pinned in Gradle.
- [ ] Implement `analyze(url, cookiesPath?)` returning title, duration, thumbnail URL, formats, subtitles, and safe error category.
- [ ] Ensure startup performs no network or parser update check.
- [ ] Add Kotlin format mapping tests for supported/unsupported resolutions and merge-required labeling.
- [ ] Verify `.\gradlew.bat :app:testDebugUnitTest` and `.\gradlew.bat :app:assembleDebug`.
- [ ] Real check: run an instrumentation or debug helper against `https://www.youtube.com/watch?v=tkxzMEfp49Q` on API37 and confirm non-empty title/formats.
- [ ] Commit with message `android: bridge yt-dlp analysis`.

### Task 4.5: Five-Page Visible GUI Shell

**Purpose:** Correct the visual gap before adding more backend flow: replace the current placeholder shell with a real five-page Compose UI that visibly matches the approved Android reference layout closely enough for foreground emulator inspection.

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Modify or create: `android/app/src/main/java/com/garyapp/ytdl/ui/theme/Theme.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/ui/YtdlAppModelTest.kt`
- Optional create if needed to keep files focused: `android/app/src/main/java/com/garyapp/ytdl/ui/AppTabs.kt`, `android/app/src/main/java/com/garyapp/ytdl/ui/ReferenceScreens.kt`

**Steps:**
- [ ] Add a UI model for the five bottom tabs: `下载`, `格式`, `队列`, `历史`, `设置`, with selected color accents matching the reference.
- [ ] Rebuild the Download page with URL input row, analysis button, thumbnail/result card, save location card, mode options, authorization checkbox, and bottom primary action placeholder.
- [ ] Rebuild the Format page with `视频+音频` / `仅音频` / `仅视频` segmented tabs, resolution rows, merge-required badges, frame/codec/container/subtitle rows, and an applied-summary card.
- [ ] Rebuild the Queue page with a scrollable list that contains running, waiting, completed, and failed examples, progress, speed, ETA, pause/cancel affordances, and visible scroll behavior.
- [ ] Rebuild the History page with search/filter controls and local-history cards with open/share/delete actions.
- [ ] Rebuild the Settings page with default save location, cookies file, parser version, native media capability, MVP2 subtitle-processing note, notification permission, privacy/legal notes, and appearance/color mode settings (`reference_v3` default and `codex` preset available).
- [ ] Ensure top safe area and status-bar readability on the API37 emulator; no text clipping or bottom-nav overlap on Xiaomi 14-like portrait dimensions.
- [ ] Add unit tests proving all five tabs, the appearance settings labels, and the key download/format/queue/history/settings labels remain present in the UI model.
- [ ] Verify `.\gradlew.bat :app:testDebugUnitTest` and `.\gradlew.bat :app:assembleDebug`.
- [ ] Foreground visible GUI check: install and launch the APK on `ytdl_api37_play_x86_64`, use Computer Use or an equivalent desktop screenshot to inspect all five tabs in the visible emulator window, and do not mark this task complete unless screenshots show the relevant pages.
- [ ] Commit with message `android: build visible five-page gui shell`.

### Task 5: Download Worker and Foreground Service

**Purpose:** Download a single active task to app-private storage with real progress, notification, cancel path, and Room state updates.

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/download/DownloadService.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/download/DownloadCoordinator.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/download/ProgressParser.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/download/NotificationController.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/test/java/com/garyapp/ytdl/download/ProgressParserTest.kt`

**Steps:**
- [ ] Add foreground service declaration with the correct service type and notification permission handling.
- [ ] Parse real yt-dlp progress events; do not synthesize fake progress.
- [ ] Support queued, running, completed, failed, canceled, network-failed, permission-failed states.
- [ ] Save output to app-private storage by default.
- [ ] Verify `.\gradlew.bat :app:testDebugUnitTest`.
- [ ] Real check on API37: start one download for the test URL, observe in-app queue progress and output file existence.
- [ ] Commit with message `android: add real foreground download flow`.

### Task 6: Android MediaProcessor

**Status:** 历史任务入口，已被下方 `Continuation Task M1` 到 `M5` 细分路线取代。不要按本节创建 `FfmpegBinary.kt`、旧 mobile-ffmpeg wrapper，或直接提交不明来源的 `jniLibs/.../ffmpeg` 二进制。

**Purpose:** Provide first-phase real media operations on Android.

**Current Split:**
- M1/M2: Android 原生 `MediaExtractor + MediaMuxer` 合同与真实兼容流合并，已完成。
- M3/M4: yt-dlp 指定 format id 分离下载和 required URL 核心合并 smoke，已完成能力层验证。
- M5: 字幕文件独立下载/输出模型，已完成能力层验证。
- M6-M8: 下载编排、前台服务、GUI 绑定、历史/导出/cookies/失败恢复和 Play 草案已推进到单元/绑定层；仍未完成最终前台 Computer Use 全量验收。
- MVP2: 自编译最小 LGPL FFmpeg 动态库、自有 JNI/命令桥、字幕嵌入/烧录、三合一输出、许可证/ABI/16KB/体积证据。

**Execution Rule:** 后续 worker 先完成 MVP1 的原生合并和独立字幕文件闭环，不得把 FFmpeg 构建、字幕嵌入或字幕烧录作为 MVP1 阻断项。

### Task 7: Five-Page Compose UI

**Purpose:** Implement the approved five-page mobile GUI and connect it to real state.

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/download/DownloadScreen.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/formats/FormatsScreen.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/queue/QueueScreen.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/history/HistoryScreen.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/settings/SettingsScreen.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/ui/components/YtdlCards.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`

**Steps:**
- [ ] Match `docs/android-gui-reference-v3.png`: five pages, bottom navigation, punch-hole safe top spacing, Bento/Material grouping, queue scroll.
- [ ] Download page: URL paste/input, analyze, thumbnail area, title/duration/format summary, save target, mode cards, authorization confirmation before download.
- [ ] Format page: show only supported choices, gray unsupported visible choices with reason, mark native-merge and separate-subtitle outputs.
- [ ] Queue page: real progress, speed, ETA, pause/cancel affordances, completed/failed sections.
- [ ] History page: search, open, share/export, delete record.
- [ ] Settings page: default storage, cookies URI, parser version, native media status, MVP2 subtitle-processing note, privacy/legal/license entries.
- [ ] Settings page: implement `外观与颜色` as a real section with theme mode choices and color preset choices. It must expose at least `基准图配色` and `Codex 风格`, persist the choice, and immediately apply the selected scheme.
- [ ] Add a visual comparison checklist against `docs/android-gui-reference-v3.png`; if screenshots differ, record and fix layout/color issues before moving on.
- [ ] Verify `.\gradlew.bat :app:assembleDebug`.
- [ ] Real check with screenshot or Computer Use on API37: navigate all five pages without layout clipping.
- [ ] Commit with message `android: implement five-page Compose UI`.

### Task 8: Export, Cookies, Privacy, and Play Artifacts

**Purpose:** Finish Play-facing storage, cookies, privacy, and compliance deliverables.

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/storage/ExportController.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/cookies/CookiesReference.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/cookies/TemporaryCookiesFile.kt`
- Create: `docs/android-play-data-safety.md`
- Create: `docs/android-privacy-policy-draft.md`
- Create: `docs/android-third-party-licenses.md`
- Create: `android/app/src/test/java/com/garyapp/ytdl/cookies/TemporaryCookiesFileTest.kt`

**Steps:**
- [ ] Implement ACTION_CREATE_DOCUMENT/MediaStore export flow.
- [ ] Implement cookies URI selection and temporary private file materialization/deletion for one task.
- [ ] Add tests proving cookies contents do not enter settings/history/log strings.
- [ ] Draft Data safety and privacy policy text aligned to actual behavior.
- [ ] Verify `.\gradlew.bat :app:testDebugUnitTest` and `.\gradlew.bat :app:assembleDebug`.
- [ ] Commit with message `android: add Play storage privacy artifacts`.

### Task 9: End-to-End Verification and Release Gate

**Purpose:** Prove the Android MVP works with the required real URL and is ready for the next milestone decision.

**Files:**
- Create: `docs/qa/android-mvp-smoke.md`
- Create: `scripts/android_real_smoke.ps1`
- Modify: `README.md` only if Android run instructions need a short user-facing note.

**Steps:**
- [ ] Run Python/shared tests if changed: `.\.venv\Scripts\python.exe -m pytest`.
- [ ] Run Android unit tests: `cd android; .\gradlew.bat :app:testDebugUnitTest`.
- [ ] Run Android build: `cd android; .\gradlew.bat :app:assembleDebug`.
- [ ] Run connected tests: `cd android; .\gradlew.bat :app:connectedDebugAndroidTest`.
- [ ] Use Computer Use or equivalent real emulator control on API37 to complete: launch app, input `https://www.youtube.com/watch?v=tkxzMEfp49Q`, analyze, choose a supported format, start download, observe real queue progress, complete, inspect history, export/open file.
- [ ] Save evidence to `docs/qa/android-mvp-smoke.md`, including commands, timestamps, emulator name, app version, output path, and known gaps.
- [ ] Commit with message `android: document MVP smoke evidence`.
- [ ] Push `feature/android-play-mvp-1`.

## Per-Task Review Gate

## Current Continuation Plan - 2026-06-20

This section records the adjusted continuation order after user review. From 2026-06-20 onward, the M4-M9 queue below is the executable continuation queue. Earlier Task 5-9 sections remain as scope inventory, but if their order conflicts with this queue, use this queue.

Checkboxes before this section are historical scope inventory and must not be used to determine current Android progress. Current progress is tracked by the M1-M9 continuation tasks below and the QA ledgers.

**Last recorded proven state:**

- Environment preflight passed on 2026-06-21 with `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`; API37 `emulator-5554` was connected.
- M1-M8 are complete at capability, binding, or auxiliary-instrumentation level: native MediaProcessor contract, native `MediaMuxer` merge, explicit split-stream downloads, required URL core merge, separate subtitle-file output, foreground download orchestration, GUI binding, history/export/cookies privacy, failure messages, Play data-safety/privacy/license drafts, and M9 preflight fixes exist in the current tree and QA ledger.
- API37 auxiliary checks have proven real analysis, real split downloads, real native merge, independent subtitle download, UIAutomator real download/queue/history binding, and five-page adb screenshot visual sanity.
- Android FFmpeg is not packaged; subtitle embed, subtitle burn, and video+audio+subtitle three-in-one output are MVP2 scope and do not block MVP1.
- Computer Use foreground visible full acceptance is still blocked by the current tool-layer `sandboxPolicy` error and must not be counted as passed.
- Previous visible GUI evidence that used the Android soft keyboard remains invalid as final acceptance evidence.

**Adjusted priority:** stop repeating GUI tests around the old FFmpeg merge label. MVP1 uses native MediaMuxer for compatible split video/audio merge and outputs subtitles as separate files. Build subtitle-file download/output, foreground service, queue, history, export/privacy flows, and then perform one complete foreground visible test path.

**Execution rule:** one active implementation task at a time. Each task gets one fresh worker, then a fresh audit reviewer. After the main session integrates fixes, close the worker/reviewer. Do not keep idle agents open.

**Acceptance rule:** capability tests prove internals; GUI binding tests prove UI is wired; only the final Computer Use foreground visible run proves user acceptance. A task can be marked "ability passed" or "binding passed" before final acceptance, but not "MVP accepted".

### Continuation Task M1: MediaProcessor Route Decision and Contract

**Status:** 已完成并通过主会话复核；保留为上下文，不再重新执行。

**Purpose:** Establish the first real Android media-processing contract for separated video and audio downloads. The MVP1 implementation route is Android native `MediaExtractor + MediaMuxer` for stream copy merge into MP4 when codecs/containers are compatible; FFmpeg subtitle embed/burn is MVP2 scope.

**Files:**
- Create: `docs/android-media-processor.md`
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/MediaProcessor.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/MediaProcessingModels.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/media/MediaProcessorContractTest.kt`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [x] Document the current facts: MVP1 does not package Android FFmpeg, Windows `ffmpeg.exe` is unusable on Android, and native `MediaMuxer` is the MVP1 merge path.
- [x] Define `MediaMergeRequest(videoInput, audioInput, outputFile, outputContainer, expectedVideoFormatId, expectedAudioFormatId)`.
- [x] Define `MediaProcessingResult(outputFile, bytesWritten, videoTrackCount, audioTrackCount, processorName)`.
- [x] Define `MediaProcessor` with `mergeVideoAndAudio(request): Result<MediaProcessingResult>`.
- [x] Unit-test that merge requests require distinct readable video/audio inputs and an app-private output file.
- [x] Unit-test that subtitle embed/burn is explicitly unsupported by the native muxer processor and must route to MVP2.
- [x] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** This task is accepted only when the media-processing contract exists, native-muxer MVP1 responsibilities are documented, and tests prove subtitle embed/burn cannot be falsely claimed by the native muxer.

### Continuation Task M2: Native Audio+Video Merge Processor

**Status:** 已完成并通过 API37 instrumentation；保留为上下文，不再重新执行。

**Purpose:** Implement real Android MP4 stream-copy merging for a downloaded video-only file and a downloaded audio-only file, using `MediaExtractor` and `MediaMuxer`.

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/media/MediaProcessor.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/NativeMuxerMediaProcessor.kt`
- Create: `android/app/src/androidTest/java/com/garyapp/ytdl/media/NativeMuxerMediaProcessorInstrumentedTest.kt`
- Create or update: `docs/android-media-processor.md`

**Steps:**
- [x] Add a small test asset generator in the instrumented test using Android `MediaMuxer`/`MediaCodec` or copy a minimal generated fixture into app-private test storage.
- [x] Write a failing instrumented test that calls `mergeVideoAndAudio()` and asserts the output file exists and has both video and audio tracks.
- [x] Implement `NativeMuxerMediaProcessor` by copying encoded samples from the source video track and audio track into a new MP4 muxer output without shell commands.
- [x] Add defensive checks for missing tracks, unreadable inputs, zero-byte outputs, and unsupported container choices.
- [x] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`
  - `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.media.NativeMuxerMediaProcessorInstrumentedTest"`

**Acceptance:** This task is accepted only when API37 produces a real merged MP4 file and the instrumented test inspects the output with `MediaExtractor` to prove one video track and one audio track exist.

### Continuation Task M3: yt-dlp Split-Stream Download

**Status:** 已完成并通过 API37 真实分离下载；保留为上下文，不再重新执行。

**Purpose:** Download the selected video-only format and selected audio-only format as separate files, instead of forcing the old `18/worst` single-file fallback.

**Files:**
- Modify: `android/app/src/main/python/ytdl_bridge.py`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/YtdlpBridge.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/VideoAnalysis.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/core/ytdlp/SplitDownloadRequestTest.kt`
- Modify: `android/app/src/androidTest/java/com/garyapp/ytdl/core/ytdlp/YtdlpBridgeInstrumentedTest.kt`

**Steps:**
- [x] Add `download_format(url, output_dir, format_id, role, cookies_path, progress_listener)` in Python and return output path, bytes, title, and actual format id.
- [x] Add Kotlin `downloadFormat()` wrapper that accepts an explicit format id and never substitutes `18/worst` unless the request says direct fallback is allowed.
- [x] Unit-test JSON parsing and request validation for explicit video/audio format ids.
- [x] Add an API37 instrumentation test against `https://www.youtube.com/watch?v=tkxzMEfp49Q` that downloads one selected video-only format and one selected audio-only format to app-private storage.
- [x] Verify output files are non-empty and have separate roles recorded.

**Acceptance:** This task is accepted only when the required URL can produce two real files on API37: one video-only and one audio-only. This is still not GUI acceptance.

### Continuation Task M4: End-to-End Core Merge Smoke

**Status:** 已完成并通过 API37 required URL 能力层 smoke；保留为上下文，不再重新执行。

**Purpose:** Prove the non-GUI core can analyze the required URL, select separated streams, download both streams, and merge them into one playable MP4 file with audio and video tracks.

**Files:**
- Create: `android/app/src/androidTest/java/com/garyapp/ytdl/media/RequiredUrlMergeInstrumentedTest.kt`
- Create or update: `docs/android-media-processor.md`

**Steps:**
- [x] Analyze `https://www.youtube.com/watch?v=tkxzMEfp49Q` on API37.
- [x] Select a video-only format and a standalone audio format from actual `VideoAnalysis.formats`.
- [x] Download both selected formats with `downloadFormat()`.
- [x] Merge them with `NativeMuxerMediaProcessor.mergeVideoAndAudio()`.
- [x] Inspect the merged output with `MediaExtractor` and assert video track count is 1 and audio track count is 1.
- [x] Record output file path, bytes, format ids, SDK, device, and test timestamp in `docs/android-media-processor.md`.

**Acceptance:** This task is accepted only when the merged file exists and is proven to contain both tracks. No GUI flow is considered complete before this passes.

### Continuation Task M5: Subtitle File Output for MVP1

**Status:** 已完成并通过 API37 真实字幕文件下载；保留为上下文，不再重新执行。

**Purpose:** Add the MVP1 subtitle path: when subtitles are requested and available, download them as separate subtitle files and associate them with the merged video+audio output for queue, history, and export. Do not require video+audio+subtitle in one container for MVP1.

**Files:**
- Modify: `android/app/src/main/python/ytdl_bridge.py`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/YtdlpBridge.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/VideoAnalysis.kt`
- Create or modify: `android/app/src/test/java/com/garyapp/ytdl/core/ytdlp/SubtitleDownloadRequestTest.kt`
- Create or modify: `android/app/src/androidTest/java/com/garyapp/ytdl/core/ytdlp/SubtitleDownloadInstrumentedTest.kt`
- Modify: `docs/android-media-processor.md`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [x] Parse available subtitles and automatic subtitles from yt-dlp analysis without logging sensitive request data.
- [x] Add an explicit subtitle download request that selects language and extension; reject selector/fallback expressions.
- [x] Download subtitle files to app-private storage and return path, bytes, language, extension, and source type.
- [x] Associate subtitle outputs with the same queue/history item as the merged video+audio file.
- [x] Unit-test request validation, JSON parsing, and sensitive text redaction.
- [x] API37 instrumentation: analyze `tkxzMEfp49Q`, download one available subtitle or record a clear `no subtitles available` status with a second fixture URL if needed.
- [x] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** M5 is accepted when MVP1 can honestly produce a merged video+audio file and, when selected subtitles exist, a separate subtitle file tied to the same task. It must not claim subtitle embed, subtitle burn, or three-in-one output.

### Continuation Task M6: Download Orchestration and Foreground State

**Status:** 已完成核心编排与绑定层验证；最终前台可视验收仍在 M9/T12。

**Purpose:** Build the real download pipeline that turns an analyzed format choice into direct download, split video/audio download, native merge, or separate subtitle-file download, while exposing honest queue progress and foreground-service state.

**Files:**
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/download/DownloadRequest.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/download/DownloadPipeline.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/download/DownloadService.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/download/DownloadState.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/download/NotificationController.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create or modify: `android/app/src/test/java/com/garyapp/ytdl/download/DownloadRequestRoutingTest.kt`
- Create or modify: `android/app/src/test/java/com/garyapp/ytdl/download/DownloadStateTest.kt`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [x] Build `DownloadRequest` from `VideoAnalysis` and applied `FormatSelection`.
- [x] Route direct single-file choices to the existing single-file path only when the chosen format truly contains both audio and video.
- [x] Route merge-required choices to explicit video/audio format downloads and `MediaProcessor`; they must not call `downloadSingleFile()`.
- [x] Route subtitle choices to separate subtitle-file download in MVP1.
- [x] Model queue states separately: analyzing, waiting, downloading video, downloading audio, downloading subtitles, merging, exporting, completed, failed, canceled.
- [x] Add foreground service declaration and notification controller; notification permission denial must not hide in-app progress.
- [x] Unit-test direct, video-only, audio-only, merge-required, subtitle-file, cancellation, and failure routing.
- [x] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** This task is accepted only when tests prove merge-required and subtitle-file selections cannot silently fall back to 360p/direct single-file downloads, and queue state cannot show completed before all selected outputs exist.

### Continuation Task M7: GUI Binding and Anti-Fallback UX

**Status:** 绑定层已完成并通过审计；最终前台可视全量验收仍在 M9/T12。

**Purpose:** Bind the approved five-page GUI to the real pipeline from M4-M6, so visible choices, queue progress, settings, and errors reflect actual capabilities instead of demo values.

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/FormatSelection.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/ui/download/DownloadScreen.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/ui/formats/FormatsScreen.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/ui/queue/QueueScreen.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/ui/settings/SettingsScreen.kt`
- Create or modify: `android/app/src/test/java/com/garyapp/ytdl/ui/DownloadGuiBindingTest.kt`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [x] Analyze button must call the real `YtdlpBridge.analyze()` and never block startup.
- [x] Format page must display only choices derived from the current `VideoAnalysis`; unsupported visible rows are disabled with a reason.
- [x] Download page summary must update when the user applies a format choice; it must not keep showing stale 360p/default summary.
- [x] Start-download feedback must be immediate and visible: task added, current state, and queue entry.
- [x] Queue page must show real stage names and progress from `DownloadState`, not static sample rows.
- [x] Settings page must show real parser version and media processor status, including native muxer support and MVP2 FFmpeg note.
- [x] Unit-test UI model binding for supported/unsupported formats, stale summary prevention, and stage text.
- [x] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** GUI binding is accepted only after tests prove the five pages consume real state and cannot present unavailable formats or completed downloads that the pipeline did not produce.

### Continuation Task M8: History, Export, Cookies Privacy, and Failure Recovery

**Status:** 单元/绑定层已完成；Play 文档草案已补齐 Data safety、隐私政策和第三方许可证工作稿。真实设置页 cookies 选择、历史打开/分享/导出/删除和失败恢复仍留到 M9/T12 前台可视验收。

**Purpose:** Complete the non-happy-path and Play-facing behavior before final visible acceptance: output discovery/export, local history, cookies reference safety, and user-readable failures.

**Files:**
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/storage/ExportController.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/cookies/CookiesReference.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/cookies/TemporaryCookiesFile.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/data/HistoryItemEntity.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/core/privacy/SensitiveText.kt`
- Create or modify: `android/app/src/test/java/com/garyapp/ytdl/cookies/TemporaryCookiesFileTest.kt`
- Create or modify: `android/app/src/test/java/com/garyapp/ytdl/data/HistoryPrivacyTest.kt`
- Create or modify: `docs/android-play-data-safety.md`
- Create or modify: `docs/android-privacy-policy-draft.md`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [x] Persist completed and failed tasks to history without cookies contents, authorization headers, raw command lines, or sensitive query strings.
- [x] Implement app-private output discovery plus ACTION_CREATE_DOCUMENT/MediaStore export path.
- [x] Store only cookies URI/path references; materialize temporary cookies files per task and delete them after completion/failure/cancel.
- [x] Add failure messages for blank URL, invalid URL, non-http/https URL, network failure, missing subtitle, processing failure, save/export denial, and cancellation.
- [x] Add tests proving sensitive strings are redacted from settings, history, logs, and error text.
- [x] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** This task is accepted only when the Play-facing privacy/storage behavior is test-covered and failure states are recoverable without sensitive leakage.

### Continuation Task M9: Full Foreground Visible Acceptance Flow

**Status:** 阻断中；M8 前置能力已到位，但 Computer Use 前台可见模拟器全流程仍未通过，不能称为 MVP 验收完成。

**Purpose:** Run the meaningful GUI test only after media capability, subtitle-file output, queue, history, export, and privacy behavior exist: user-visible analysis, format choice, split download, merge, separate subtitle file where applicable, foreground progress, history, export, settings, and failures.

**Files:**
- Create or update: `docs/qa/android-mvp-smoke.md`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [ ] Install the current APK on `ytdl_api37_play_x86_64`.
- [ ] Use Computer Use in the foreground visible emulator window; do not use Android soft keyboard, candidate bar, Gboard menu, or background-only automation.
- [ ] Input `https://www.youtube.com/watch?v=tkxzMEfp49Q`.
- [ ] Analyze and confirm title, duration, thumbnail state, and real supported format rows.
- [ ] Select a merge-required high-resolution `视频+音频` option.
- [ ] Start download and observe queue states for video download, audio download, merge, and completion.
- [ ] Exercise separate subtitle-file output on a short fixture or a URL/subtitle case that the app can legally process; subtitle embed/burn remains MVP2 scope.
- [ ] Inspect history, output summary, export/open behavior, settings parser/media status, and privacy/cookies boundary text.
- [ ] Run one Shorts compatibility sample with `https://www.youtube.com/shorts/QBwpO9f0oAw` for analysis and a short download path, without duplicating every normal-video assertion.
- [ ] Save screenshots and command/test outputs in `docs/qa/android-mvp-smoke.md`.

**Acceptance:** This is the first point where the Android MVP can be called accepted. Earlier unit, instrumentation, adb, UIAutomator, or screenshot checks are necessary evidence but not final acceptance.

**M9 preflight fixes, 2026-06-21:** Before attempting T12, the main session closed several GUI-binding gaps found by review: appearance mode/color preset now persist and apply through `YtdlTheme`, queue and notification cancellation call the real cancellation path, history cards expose real open/share/export/delete actions through app-private output resolution and Room deletion, the format page can select an available subtitle as an independent subtitle-file output, repeated downloads now use unique task output directories with app-private relative history URIs, notification cancel stops the service start id, notification permission reflects the real runtime state, and the Settings page now shows concrete privacy/authorization boundaries instead of an empty “view details” entry. `testDebugUnitTest`、`assembleDebug` and relevant connected/UIAutomator auxiliary checks have fresh passing output, but foreground visible Computer Use acceptance is still blocked by the current Computer Use tool bootstrap error and must not be counted as passed.

After each task commit:

1. Open a fresh independent audit thread against the task commit and plan section.
2. Ask the audit thread for P0/P1/P2 findings only: spec gaps, security/privacy leaks, failing build/test risk, Play-policy mismatch, or GUI mismatch.
3. Read the audit result in the main session.
4. Fix P0/P1/P2 findings before moving to the next task.
5. Re-run the task's verification commands.

## Completion Gate

The Android MVP is not complete until Task 9 passes with fresh evidence. Passing unit tests alone is not enough; the final gate requires real emulator GUI operation with `https://www.youtube.com/watch?v=tkxzMEfp49Q`.
