# YTDL Android Play MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android Google Play MVP as a real runnable Android app with the approved five-page GUI, yt-dlp analysis/download path, Android ffmpeg media processing, foreground progress, local history, Play-safe storage/cookies handling, and real emulator verification.

**Architecture:** Android lives in a new `android/` project so Windows code remains isolated. Kotlin/Compose owns UI, state, Room, foreground service, storage, and policy gates; Chaquopy embeds Python `yt-dlp` for analysis/download; Android-packaged ffmpeg is wrapped behind `MediaProcessor` for merge/embed/burn operations. Work proceeds in small reviewed tasks, with a fresh subagent per task and an independent audit thread after each completed segment.

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
- Current corrective priority after user review: stop treating GUI shell/label checks as progress until the core media path exists. Immediate order is required URL core merge -> Android ffmpeg subtitle/embed/burn capability -> download orchestration/foreground state -> GUI binding -> history/export/privacy/failure coverage -> foreground Computer Use full acceptance. Backend, instrumentation, adb, and build checks remain supporting evidence only; Android acceptance requires a foreground visible emulator window GUI run for the relevant flow.
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
- [ ] Add URL risk checks for empty URLs, unsupported schemes, known adult domains, and local static deny rules.
- [ ] Return neutral Chinese user messages without echoing blocked adult URLs.
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
- [ ] Rebuild the Settings page with default save location, cookies file, parser version, ffmpeg capability, notification permission, privacy/legal notes, and appearance/color mode settings (`reference_v3` default and `codex` preset available).
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

**Status:** 历史任务入口，已被下方 `Continuation Task M1` 到 `M5e` 细分路线取代。不要按本节创建 `FfmpegBinary.kt`、旧 mobile-ffmpeg wrapper，或直接提交不明来源的 `jniLibs/.../ffmpeg` 二进制。

**Purpose:** Provide first-phase real media operations on Android.

**Current Split:**
- M1/M2: Android 原生 `MediaExtractor + MediaMuxer` 合同与真实兼容流合并，已完成。
- M3/M4: yt-dlp 指定 format id 分离下载和 required URL 核心合并 smoke，已完成能力层验证。
- M5a-M5e: 自编译最小 LGPL FFmpeg 8.1.x 动态库、自有 JNI/命令桥、字幕嵌入/烧录、许可证/ABI/16KB/体积证据，当前执行。

**Execution Rule:** 后续 worker 必须执行 M5a-M5e 的细分任务，不得回到旧的 FFmpegKit/mobile-ffmpeg/AAR 路线。

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
- [ ] Format page: show only supported choices, gray unsupported visible choices with reason, mark ffmpeg-required paths.
- [ ] Queue page: real progress, speed, ETA, pause/cancel affordances, completed/failed sections.
- [ ] History page: search, open, share/export, delete record.
- [ ] Settings page: default storage, cookies URI, parser version, ffmpeg status, privacy/legal/license entries.
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

**Last recorded proven state:**

- Environment preflight passed with `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`; API37 `emulator-5554` was connected in the recorded run.
- The app can analyze the required URL through Chaquopy/yt-dlp and can perform the older direct single-file download path in the recorded run.
- The Format page now has an initial real selection model, but GUI format selection is not the next acceptance target.
- Native MediaProcessor contract, native `MediaMuxer` merge, real YouTube split-stream download, and required URL merge smoke evidence exist in the current working tree/test ledger.
- yt-dlp explicit split-stream download evidence exists in the current working tree/test ledger for `tkxzMEfp49Q`: video-only format `394` and audio-only format `139` downloaded as separate files on API37.
- Android ffmpeg is not packaged; subtitle embed and subtitle burn are not complete. This is the next execution target.
- High-resolution YouTube video+audio merge through the full app path is not complete.
- Previous visible GUI evidence that used the Android soft keyboard is not valid as final acceptance evidence.

**Adjusted priority:** stop repeating GUI tests around `需 ffmpeg 合并` labels until the media capability exists. Build the real media processing/download capability first, including Android ffmpeg subtitle/embed/burn capability before GUI acceptance, then bind it to foreground service, queue, history, export/privacy flows, and perform one complete foreground visible test path.

**Execution rule:** one active implementation task at a time. Each task gets one fresh worker, then a fresh audit reviewer. After the main session integrates fixes, close the worker/reviewer. Do not keep idle agents open.

**Acceptance rule:** capability tests prove internals; GUI binding tests prove UI is wired; only the final Computer Use foreground visible run proves user acceptance. A task can be marked "ability passed" or "binding passed" before final acceptance, but not "MVP accepted".

### Continuation Task M1: MediaProcessor Route Decision and Contract

**Status:** 已完成并通过主会话复核；保留为上下文，不再重新执行。

**Purpose:** Establish the first real Android media-processing contract for separated video and audio downloads. The immediate implementation route is Android native `MediaExtractor + MediaMuxer` for stream copy merge into MP4 when codecs/containers are compatible; Android ffmpeg remains required later for subtitle burn and non-muxer cases.

**Files:**
- Create: `docs/android-media-processor.md`
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/MediaProcessor.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/MediaProcessingModels.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/media/MediaProcessorContractTest.kt`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [ ] Document the current facts: no Android ffmpeg binary in the repo, Windows `ffmpeg.exe` is unusable on Android, and Gradle cache has no existing ffmpeg-kit artifact.
- [ ] Define `MediaMergeRequest(videoInput, audioInput, outputFile, outputContainer, expectedVideoFormatId, expectedAudioFormatId)`.
- [ ] Define `MediaProcessingResult(outputFile, bytesWritten, videoTrackCount, audioTrackCount, processorName)`.
- [ ] Define `MediaProcessor` with `mergeVideoAndAudio(request): Result<MediaProcessingResult>`.
- [ ] Unit-test that merge requests require distinct readable video/audio inputs and an app-private output file.
- [ ] Unit-test that subtitle burn is explicitly unsupported by the native muxer processor and must route to the later ffmpeg processor.
- [ ] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** This task is accepted only when the media-processing contract exists, native-muxer vs ffmpeg responsibilities are documented, and tests prove subtitle burn cannot be falsely claimed by the native muxer.

### Continuation Task M2: Native Audio+Video Merge Processor

**Status:** 已完成并通过 API37 instrumentation；保留为上下文，不再重新执行。

**Purpose:** Implement real Android MP4 stream-copy merging for a downloaded video-only file and a downloaded audio-only file, using `MediaExtractor` and `MediaMuxer`.

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/media/MediaProcessor.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/NativeMuxerMediaProcessor.kt`
- Create: `android/app/src/androidTest/java/com/garyapp/ytdl/media/NativeMuxerMediaProcessorInstrumentedTest.kt`
- Create or update: `docs/android-media-processor.md`

**Steps:**
- [ ] Add a small test asset generator in the instrumented test using Android `MediaMuxer`/`MediaCodec` or copy a minimal generated fixture into app-private test storage.
- [ ] Write a failing instrumented test that calls `mergeVideoAndAudio()` and asserts the output file exists and has both video and audio tracks.
- [ ] Implement `NativeMuxerMediaProcessor` by copying encoded samples from the source video track and audio track into a new MP4 muxer output without shell commands.
- [ ] Add defensive checks for missing tracks, unreadable inputs, zero-byte outputs, and unsupported container choices.
- [ ] Verify:
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
- [ ] Add `download_format(url, output_dir, format_id, role, cookies_path, progress_listener)` in Python and return output path, bytes, title, and actual format id.
- [ ] Add Kotlin `downloadFormat()` wrapper that accepts an explicit format id and never substitutes `18/worst` unless the request says direct fallback is allowed.
- [ ] Unit-test JSON parsing and request validation for explicit video/audio format ids.
- [ ] Add an API37 instrumentation test against `https://www.youtube.com/watch?v=tkxzMEfp49Q` that downloads one selected video-only format and one selected audio-only format to app-private storage.
- [ ] Verify output files are non-empty and have separate roles recorded.

**Acceptance:** This task is accepted only when the required URL can produce two real files on API37: one video-only and one audio-only. This is still not GUI acceptance.

### Continuation Task M4: End-to-End Core Merge Smoke

**Status:** 已完成并通过 API37 required URL 能力层 smoke；保留为上下文，不再重新执行。

**Purpose:** Prove the non-GUI core can analyze the required URL, select separated streams, download both streams, and merge them into one playable MP4 file with audio and video tracks.

**Files:**
- Create: `android/app/src/androidTest/java/com/garyapp/ytdl/media/RequiredUrlMergeInstrumentedTest.kt`
- Create or update: `docs/android-media-processor.md`

**Steps:**
- [ ] Analyze `https://www.youtube.com/watch?v=tkxzMEfp49Q` on API37.
- [ ] Select a video-only format and a standalone audio format from actual `VideoAnalysis.formats`.
- [ ] Download both selected formats with `downloadFormat()`.
- [ ] Merge them with `NativeMuxerMediaProcessor.mergeVideoAndAudio()`.
- [ ] Inspect the merged output with `MediaExtractor` and assert video track count is 1 and audio track count is 1.
- [ ] Record output file path, bytes, format ids, SDK, device, and test timestamp in `docs/android-media-processor.md`.

**Acceptance:** This task is accepted only when the merged file exists and is proven to contain both tracks. No GUI flow is considered complete before this passes.

### Continuation Task M5: Android FFmpeg Subtitle Processor

**Status:** 当前执行任务；2026-06-20 审计已拒绝 `mobile-ffmpeg` / `bihe0832` AAR 作为主线方案。

**Purpose:** Add the Android FFmpeg route required by first-phase requirements for subtitle embed, subtitle burn, and non-native-muxer media operations. Native `MediaMuxer` remains the preferred compatible MP4 stream-copy path for split video+audio merge; FFmpeg covers subtitle embed/burn, filters, incompatible containers/codecs, and other work native muxer cannot honestly claim.

**Accepted Architecture:** Use two media lanes. Lane 1 is the existing Android `MediaExtractor + MediaMuxer` processor for compatible stream-copy merge. Lane 2 is a project-owned minimal LGPL FFmpeg 8.1.x dynamic-library build, loaded through a project-owned JNI/command bridge. Do not adopt retired FFmpegKit/mobile-ffmpeg wrappers or opaque Maven AARs as the product route.

#### M5a: Route Cleanup and Build Environment Gate

**Files:**
- Modify: `.gitignore`
- Modify: `android/app/build.gradle.kts`
- Modify: `docs/android-media-processor.md`
- Modify: `docs/qa/android-full-visual-test-plan.md`
- Modify: `docs/superpowers/plans/2026-06-19-ytdl-android-play-mvp.md`
- Delete if present: `android/app/src/main/java/com/garyapp/ytdl/media/FfmpegBinary.kt`
- Delete if present: `android/app/src/main/java/com/garyapp/ytdl/media/FfmpegMediaProcessor.kt`
- Delete if present: `android/app/src/main/assets/licenses/ffmpeg.md`
- Delete if present: `android/app/src/test/java/com/garyapp/ytdl/media/FfmpegCommandTest.kt`
- Delete if present: `android/app/src/androidTest/java/com/garyapp/ytdl/media/FfmpegMediaProcessorInstrumentedTest.kt`

**Steps:**
- [ ] Remove the rejected `com.bihe0832.android:lib-ffmpeg-mobile-aaf` dependency and untracked mobile-ffmpeg wrapper code from product paths.
- [ ] Keep local probe artifacts out of git with `.qa-android-ffmpeg-*.aar` and `.qa-ffmpeg-inspect/` ignore rules.
- [ ] Record rejected trials: retired FFmpegKit/mobile-ffmpeg, opaque bihe0832/mobile-ffmpeg AAR, pao11 AAR without x86_64, and Bytedeco/JavaCV not chosen for the MVP because of size/license/API-control cost.
- [ ] Record current local build facts: NDK `28.2.13676358` exists; NDK LLVM Windows toolchain exists; Git Bash exists and has Perl; Windows PATH has no `make`, `python`, `python3`, or `pkg-config`; WSL has no installed Linux distro.
- [ ] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** M5a is accepted when the rejected AAR route is no longer referenced by product code, the worktree contains only intentional tracked doc/build cleanup, and build/unit tests still pass. M5a does not complete Android FFmpeg capability.

#### M5b: Minimal LGPL FFmpeg Build Inputs

**Files:**
- Create or modify: `scripts/android_ffmpeg_env.ps1`
- Create or modify: `scripts/build_android_ffmpeg.ps1`
- Create or modify: `third_party/ffmpeg/README.md`
- Create or modify: `docs/android-media-processor.md`

**Steps:**
- [ ] Add a preflight script that prints exact pass/fail status for NDK, CMake, Git Bash, Perl, make, Python 3, pkg-config, LLVM target tools, and output directories.
- [ ] Add a build script skeleton for FFmpeg 8.1.x LGPL dynamic libraries, with explicit source archive path, SHA/signature fields, configure flags, ABI list, API level, and output directory.
- [ ] Configure the first build target as `x86_64` for API37 emulator and `arm64-v8a` for Xiaomi 14-class release validation.
- [ ] Keep GPL/nonfree disabled unless the user explicitly approves a GPL route.
- [ ] Document required subtitle burn libraries and risks: libass, freetype, fribidi, fontconfig or explicit bundled font handling.

**Acceptance:** M5b is accepted only when the build preflight produces exact local pass/fail facts and the build scripts are reproducible enough for a worker to run without hidden manual steps. If tools are missing, the script must report the missing tool by name and path expectation.

#### M5c: FFmpeg JNI/Command Bridge

**Files:**
- Create or modify: `android/app/src/main/cpp/ffmpeg_bridge.cpp`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/media/FfmpegBridge.kt`
- Create or modify: `android/app/src/main/java/com/garyapp/ytdl/media/FfmpegMediaProcessor.kt`
- Create or modify: `android/app/src/test/java/com/garyapp/ytdl/media/FfmpegCommandTest.kt`
- Modify: `android/app/build.gradle.kts`

**Steps:**
- [ ] Load only project-built native libraries from app packaging, not shell binaries from system PATH.
- [ ] Expose `version()`, `embedSubtitle(...)`, `burnSubtitle(...)`, and `fallbackMerge(...)` through argument arrays or JNI-safe structures; no shell string concatenation.
- [ ] Redact cookies paths and sensitive URLs from logs/errors.
- [ ] Unit-test command construction, output-root enforcement, extension/container choices, and redaction.
- [ ] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** M5c is accepted only when Kotlin can call the packaged bridge and unit tests prove command/path safety. This still does not prove real subtitle output.

#### M5d: Subtitle Embed and Burn Runtime Proof

**Files:**
- Create or modify: `android/app/src/androidTest/java/com/garyapp/ytdl/media/FfmpegMediaProcessorInstrumentedTest.kt`
- Create or modify: `android/app/src/androidTest/assets/media/`
- Modify: `docs/android-media-processor.md`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [ ] Add tiny local media fixtures or generate tiny media during instrumentation.
- [ ] Run FFmpeg `-version` on API37 x86_64 16KB emulator.
- [ ] Produce a subtitle-embedded output and verify the output contains a subtitle stream.
- [ ] Produce a subtitle-burned output and verify the output contains readable video/audio tracks and no separate subtitle stream is required for display.
- [ ] Include at least one Chinese subtitle/font-path case or document the exact reason it cannot run yet and keep M5d incomplete.
- [ ] Verify:
  - `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.media.FfmpegMediaProcessorInstrumentedTest"`

**Acceptance:** M5d is accepted only when API37 executes the packaged FFmpeg route and produces real embed and burn outputs. Until this passes, no wording may claim first-phase Android FFmpeg subtitle capability is complete.

#### M5e: Play, License, ABI, Size, and Device Evidence

**Files:**
- Create or modify: `android/app/src/main/assets/licenses/ffmpeg.md`
- Create or modify: `docs/android-media-processor.md`
- Create or modify: `docs/qa/android-full-visual-test-plan.md`
- Create or modify: `docs/android-release-evidence.md`

**Steps:**
- [ ] Record FFmpeg source version, source URL, signature/hash, configure flags, enabled libraries, disabled GPL/nonfree status, and exact build command.
- [ ] Check every packaged `.so` ELF LOAD alignment for 16KB compatibility.
- [ ] Build APK/AAB and record package-size impact.
- [ ] Check AAB 16KB page-size readiness with Android tooling available locally.
- [ ] Run x86_64 API37 emulator proof and record command output.
- [ ] Run `arm64-v8a` true-device proof when Xiaomi 14 or equivalent is available; until then mark arm64 release validation incomplete.
- [ ] Provide LGPL source/notice instructions in app assets/docs.

**Acceptance:** M5e is accepted only when license, ABI, 16KB, package size, emulator runtime, and arm64 validation status are recorded with current evidence. Missing true-device evidence blocks release acceptance but does not erase emulator capability evidence.

### Continuation Task M6: Download Orchestration and Foreground State

**Status:** 未开始；M5 通过后执行。

**Purpose:** Build the real download pipeline that turns an analyzed format choice into direct download, split video/audio download, native merge, or ffmpeg subtitle processing, while exposing honest queue progress and foreground-service state.

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
- [ ] Build `DownloadRequest` from `VideoAnalysis` and applied `FormatSelection`.
- [ ] Route direct single-file choices to the existing single-file path only when the chosen format truly contains both audio and video.
- [ ] Route merge-required choices to explicit video/audio format downloads and `MediaProcessor`; they must not call `downloadSingleFile()`.
- [ ] Route subtitle embed/burn choices to the Android ffmpeg processor after M5 passes.
- [ ] Model queue states separately: analyzing, waiting, downloading video, downloading audio, merging, embedding subtitles, burning subtitles, exporting, completed, failed, canceled.
- [ ] Add foreground service declaration and notification controller; notification permission denial must not hide in-app progress.
- [ ] Unit-test direct, video-only, audio-only, merge-required, subtitle-embed, subtitle-burn, cancellation, and failure routing.
- [ ] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** This task is accepted only when tests prove merge-required and subtitle-required selections cannot silently fall back to 360p/direct single-file downloads, and queue state cannot show completed before the final output exists.

### Continuation Task M7: GUI Binding and Anti-Fallback UX

**Status:** 未开始；M6 通过后执行。

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
- [ ] Analyze button must call the real `YtdlpBridge.analyze()` and never block startup.
- [ ] Format page must display only choices derived from the current `VideoAnalysis`; unsupported visible rows are disabled with a reason.
- [ ] Download page summary must update when the user applies a format choice; it must not keep showing stale 360p/default summary.
- [ ] Start-download feedback must be immediate and visible: task added, current state, and queue entry.
- [ ] Queue page must show real stage names and progress from `DownloadState`, not static sample rows.
- [ ] Settings page must show real parser version and media processor status, including ffmpeg available/unavailable state.
- [ ] Unit-test UI model binding for supported/unsupported formats, stale summary prevention, and stage text.
- [ ] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** GUI binding is accepted only after tests prove the five pages consume real state and cannot present unavailable formats or completed downloads that the pipeline did not produce.

### Continuation Task M8: History, Export, Cookies Privacy, and Failure Recovery

**Status:** 未开始；M7 通过后执行。

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
- [ ] Persist completed and failed tasks to history without cookies contents, authorization headers, raw command lines, or sensitive query strings.
- [ ] Implement app-private output discovery plus ACTION_CREATE_DOCUMENT/MediaStore export path.
- [ ] Store only cookies URI/path references; materialize temporary cookies files per task and delete them after completion/failure/cancel.
- [ ] Add failure messages for blank URL, invalid URL, unsupported domain, network failure, missing ffmpeg, processing failure, save/export denial, and cancellation.
- [ ] Add tests proving sensitive strings are redacted from settings, history, logs, and error text.
- [ ] Verify:
  - `cd android; .\gradlew.bat :app:testDebugUnitTest`
  - `cd android; .\gradlew.bat :app:assembleDebug`

**Acceptance:** This task is accepted only when the Play-facing privacy/storage behavior is test-covered and failure states are recoverable without sensitive leakage.

### Continuation Task M9: Full Foreground Visible Acceptance Flow

**Status:** 未开始；M8 通过后执行。

**Purpose:** Run the meaningful GUI test only after media capability, ffmpeg, queue, history, export, and privacy behavior exist: user-visible analysis, format choice, split download, merge, ffmpeg subtitle path where applicable, foreground progress, history, export, settings, and failures.

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
- [ ] Exercise subtitle embed or burn path on a short fixture or a URL/subtitle case that the app can legally process.
- [ ] Inspect history, output summary, export/open behavior, settings parser/media status, and privacy/cookies boundary text.
- [ ] Run one Shorts compatibility sample with `https://www.youtube.com/shorts/QBwpO9f0oAw` for analysis and a short download path, without duplicating every normal-video assertion.
- [ ] Save screenshots and command/test outputs in `docs/qa/android-mvp-smoke.md`.

**Acceptance:** This is the first point where the Android MVP can be called accepted. Earlier unit, instrumentation, adb, UIAutomator, or screenshot checks are necessary evidence but not final acceptance.

After each task commit:

1. Open a fresh independent audit thread against the task commit and plan section.
2. Ask the audit thread for P0/P1/P2 findings only: spec gaps, security/privacy leaks, failing build/test risk, Play-policy mismatch, or GUI mismatch.
3. Read the audit result in the main session.
4. Fix P0/P1/P2 findings before moving to the next task.
5. Re-run the task's verification commands.

## Completion Gate

The Android MVP is not complete until Task 9 passes with fresh evidence. Passing unit tests alone is not enough; the final gate requires real emulator GUI operation with `https://www.youtube.com/watch?v=tkxzMEfp49Q`.
