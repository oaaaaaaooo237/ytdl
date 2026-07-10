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
- Use current real test URLs for full-flow verification:
  - primary normal video: `https://www.youtube.com/watch?v=PqQNXB6hhUs`
  - backup normal video: `https://www.youtube.com/watch?v=svoD582Pas4`
  - Shorts sample: `https://www.youtube.com/shorts/oXFad1nt6v0`
  - old `tkxzMEfp49Q` / `QBwpO9f0oAw` / `lcFR2mFSmSs` / `auNezUzwCZg` / `jWTrleK2_MU` URLs are historical evidence only.
- Real YouTube connected tests are skipped by default; run them only as single targeted checks with `-Pandroid.testInstrumentationRunnerArguments.realYoutube=true`. Real subtitle download remains paused unless the user explicitly restores it, and then also requires `-Pandroid.testInstrumentationRunnerArguments.realYoutubeSubtitle=true`.
- Keep 429-safe spacing for real YouTube requests: at least 10 minutes between analysis/short samples, at least 30 minutes between full downloads, and stop YouTube real requests for the day if 429 appears.
- Do not mark a task complete until its tests and required real runtime check have fresh output.
- UI fidelity is a hard requirement, not a loose theme hint: final Android screens must visually match `docs/android-gui-reference-v3.png` as closely as the native Android runtime allows, including the five-page composition, bottom navigation, card density, accent colors, top safe area, queue scrolling, and progress presentation.
- The Settings page must include an appearance/color section modeled after Codex-style appearance settings: mode selection plus color preset selection. The default preset remains `reference_v3`; a `codex` preset must be available.
- Current corrective priority after user review: stop treating GUI shell/label checks as progress until the core media path exists. Immediate order is required URL core merge -> subtitle-file download/output model -> download orchestration/foreground state -> GUI binding -> history/export/privacy/failure coverage -> foreground Computer Use full acceptance. Backend, instrumentation, adb, and build checks remain supporting evidence only; Android acceptance requires a foreground visible emulator window GUI run for the relevant flow. Per the 2026-07-05 user clarification, Xiaomi 14 or equivalent `arm64-v8a` real-device validation replaces the later Google Play store-delivery step when that step is reached; it is not the current immediate task while no phone is connected.
- Agent hygiene: use one fresh subagent only for the active plan task or its audit, close it as soon as the result is integrated, and do not keep explorer/audit/worker agents idle. Do not reuse an old worker to continue a different task. If an old agent ID is unavailable after compaction, do not assume it is still active; continue with the current manager state and create a fresh task-scoped agent only when needed.
- Testing hygiene: no mock download, mock progress, static queue demo, or background-only automation can be counted as acceptance. Android foreground acceptance must use a visible emulator window and, for URL entry, the realistic Android system soft keyboard path; saved evidence must show or reference that the URL was entered without candidate replacement, auto-complete, handwriting overlay, Gboard menu, adb background injection, hardware-key shortcuts, or clipboard substitution.

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
- [ ] Real check: run a targeted instrumentation or debug helper against `https://www.youtube.com/watch?v=PqQNXB6hhUs` on API37 with `realYoutube=true` and confirm non-empty title/formats.
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
- M5: 可选字幕文件独立下载/输出模型，已完成能力层验证；无字幕或未选择字幕时不下载字幕文件。
- M6-M8: 下载编排、前台服务、GUI 绑定、历史/导出/cookies/失败恢复和 Play 草案已推进到单元/绑定层；相关前台行为已在后续 M9/T12 API37 模拟器 Computer Use 验收中复核。
- MVP2: 自编译最小 LGPL FFmpeg 动态库、自有 JNI/命令桥、字幕嵌入/烧录、三合一输出、许可证/ABI/16KB/体积证据。

**Execution Rule:** 后续 worker 先完成 MVP1 的原生合并和可选独立字幕文件闭环，不得把 FFmpeg 构建、字幕嵌入、字幕烧录或真实字幕成功专项作为 MVP1 主路径阻断项。

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
- [ ] Run connected tests: `cd android; .\gradlew.bat :app:connectedDebugAndroidTest`. This should not trigger real YouTube by default; run real network checks separately and with spacing.
- [ ] Use Computer Use or equivalent real emulator control on API37 to complete: launch app, input `https://www.youtube.com/watch?v=PqQNXB6hhUs`, analyze, choose a supported format, start download, observe real queue progress, complete, inspect history, export/open file.
- [ ] Save evidence to `docs/qa/android-mvp-smoke.md`, including commands, timestamps, emulator name, app version, output path, and known gaps.
- [ ] Commit with message `android: document MVP smoke evidence`.
- [ ] Push `feature/android-play-mvp-1`.

## Per-Task Review Gate

## Current Continuation Plan - 2026-06-20

This section records the adjusted continuation order after user review. From 2026-06-20 onward, the M4-M9 queue below is the executable continuation queue. Earlier Task 5-9 sections remain as scope inventory, but if their order conflicts with this queue, use this queue.

Checkboxes before this section are historical scope inventory and must not be used to determine current Android progress. Current progress is tracked by the M1-M9 continuation tasks below and the QA ledgers.

**Last recorded proven state:**

- Environment preflight passed on 2026-06-21 with `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`; API37 `emulator-5554` was connected.
- M1-M8 are complete at capability, binding, or auxiliary-instrumentation level: native MediaProcessor contract, native `MediaMuxer` merge, explicit split-stream downloads, required URL core merge, separate subtitle-file output, foreground download orchestration, GUI binding, history/export/cookies privacy, failure messages, Play-safe data-safety/privacy/license drafts, and M9 preflight fixes exist in the current tree and QA ledger.
- API37 auxiliary checks have proven real analysis, real split downloads, real native merge, independent subtitle download, UIAutomator real download/queue/history binding, and five-page adb screenshot visual sanity.
- Android FFmpeg is not packaged; subtitle embed, subtitle burn, and video+audio+subtitle three-in-one output are MVP2 scope and do not block MVP1.
- Computer Use foreground visible acceptance is no longer globally blocked as of 2026-07-05: the required URL and a Shorts sample have been operated in the foreground visible API37 emulator window with real analysis, split download, native merge, queue progress, and history landing. A later 2026-07-05 cold-start flow also covered blank URL failure, clean hardware-key URL input, 1080p selection, real download, native merge, history landing, system video open, share sheet invocation without sending, export save UI invocation without saving, settings privacy/cookies boundary visibility, and cookies picker invocation without selecting a file. M9 is still not complete because history delete is not executed without user confirmation, real cookies selection needs a user-provided `cookies.txt`, external export write-out was intentionally not completed, notification/cancel coverage still needs a foreground pass, and final visual-fidelity audit remains incomplete.
- Older visible GUI evidence remains historical only when it came from an outdated APK, old merge labels, or the prior non-realistic input policy. Soft-keyboard input itself is now the required realistic foreground acceptance path when the current APK is tested in a visible emulator window.

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

**Status:** 已完成核心编排、绑定层验证，并已在后续 M9/T12 API37 模拟器前台流程中复核。

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

**Status:** 绑定层已完成并通过审计，已在后续 M9/T12 API37 模拟器前台流程中复核。

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

**Status:** 单元/绑定层已完成；Play-safe 文档草案已补齐 Data safety、隐私政策和第三方许可证工作稿，但正式 Google Play 上架交付不是当前任务。真实设置页 cookies 选择、历史打开/分享/导出/删除和失败恢复已在 M9.x/M9.17 前台可视验收中复核。等推进到后续第 7 项时，只做小米 14 真机验收，不做 Google Play 商店交付。

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

**Status:** API37 模拟器前台 M9/T12 已通过；M8 前置能力已到位，Computer Use 已恢复并完成当前新主地址与当前 Shorts 样本的前台真实流程。真机 M10、Play 签名/商店素材和正式发布交付仍未开始。

**Purpose:** Run the meaningful GUI test only after media capability, subtitle-file output, queue, history, export, and privacy behavior exist: user-visible analysis, format choice, split download, merge, separate subtitle file where applicable, foreground progress, history, export, settings, and failures.

**Files:**
- Create or update: `docs/qa/android-mvp-smoke.md`
- Modify: `docs/qa/android-full-visual-test-plan.md`

**Steps:**
- [x] Install the current APK on `ytdl_api37_play_x86_64`.
- [x] Use Computer Use in the foreground visible emulator window; URL input must mimic a real phone by focusing the field, allowing the Android system keyboard to appear, and entering text through that visible keyboard. Do not rely on candidate replacement, autocomplete, handwriting overlays, Gboard menus, background adb/script writes, or background-only automation.
- [x] Input `https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G`.
- [x] Analyze and confirm title, duration, thumbnail state, and real supported format rows.
- [x] Select or verify a merge-required high-resolution `视频+音频` option; the final primary run used the automatic default, which resolved to `1080p MP4 需原生合并`.
- [x] Start download and observe queue states for video download, audio download, merge, and completion.
- [x] Do not select subtitles while subtitle testing is paused. Verify only that subtitle UI follows the current analysis result: unavailable when the video has no subtitle file, selectable only when subtitles exist, and no `字幕文件` queue stage appears when subtitles are not selected.
- [x] Inspect history, output summary, export/open behavior, settings parser/media status, and privacy/cookies boundary text.
- [x] Run one Shorts compatibility sample with `https://youtube.com/shorts/jWTrleK2_MU?si=1hOoGpC7JM__M4Sf` for analysis and a short download path, without duplicating every normal-video assertion.
- [x] Save screenshots and command/test outputs in `docs/qa/android-mvp-smoke.md`.

**Acceptance:** This is the first point where the Android MVP can be called accepted. Earlier unit, instrumentation, adb, UIAutomator, or screenshot checks are necessary evidence but not final acceptance.

**M9 preflight fixes, 2026-06-21:** Before attempting T12, the main session closed several GUI-binding gaps found by review: appearance mode/color preset now persist and apply through `YtdlTheme`, queue and notification cancellation call the real cancellation path, history cards expose real open/share/export/delete actions through app-private output resolution and Room deletion, the format page can select an available subtitle as an independent subtitle-file output, repeated downloads now use unique task output directories with app-private relative history URIs, notification cancel stops the service start id, notification permission reflects the real runtime state, and the Settings page now shows concrete privacy/authorization boundaries instead of an empty “view details” entry. `testDebugUnitTest`, `assembleDebug` and relevant connected/UIAutomator auxiliary checks have fresh passing output. On 2026-06-21 20:15, `YtdlAppUiTest` passed 4/4 tests on API37 and covered the required `tkxzMEfp49Q` real analysis/download/history path plus one Shorts analysis sample. Foreground visible Computer Use acceptance is still blocked: Computer Use can run, list apps, and passively capture windows, but input actions cannot activate emulator or ordinary app windows (`failed to activate captured window`), so M9/T12 must not be counted as passed.

**M9 visual-fidelity follow-up, 2026-06-21:** The 21:48 follow-up added connected coverage that the Settings page shows `基准图配色` and `Codex 风格`, updates the user-facing summary, and resets the preset after the test. This follow-up then made palette application itself test-observable without adding user-visible debug text: the root screen exposes the active color preset id and settings accent color through Compose semantics, and Robolectric Compose tests assert the `reference_v3` and `codex` values. The Computer Use foreground acceptance gate remains unchanged.

**M9 foreground update, 2026-07-05:** Computer Use can now connect to `Android Emulator - ytdl_api37_play_x86_64:5554` and operate the foreground window. The QA ledger records real foreground evidence for `https://www.youtube.com/watch?v=tkxzMEfp49Q`: preview/title/duration/format analysis, 1080p video+audio native-merge selection, real queue progress through video download and merge, completed history entry, and output files. The same ledger records a Shorts sample `https://www.youtube.com/shorts/QBwpO9f0oAw` from blank input through analysis, preview, real download, merge, queue completion, and history. A later cold-start run covered the normal required URL flow plus history open/share/export entry points. Remaining M9 work: history delete is not executed without user confirmation, real cookies selection needs a user-provided `cookies.txt`, external export write-out was intentionally not completed, notification/cancel coverage still needs a foreground pass, final screenshot-level visual-fidelity audit against `docs/android-gui-reference-v3.png` remains incomplete, and a fresh audit subagent must approve the current evidence boundaries.

**M9 cold-start update, 2026-07-05 16:20:** A freshly built and reinstalled debug APK was launched on API37, then Computer Use completed a cold-start foreground flow: blank URL failure, clean hardware-key URL input with `mInputShown=false`, required URL analysis, 1080p merge-required format selection, authorization confirmation, real queue progress, audio download, native merge, 100% completion, history landing, system video open, share sheet invocation without sending, export save UI invocation without saving, settings privacy/cookies boundary visibility, and cookies picker invocation without selecting a file. Remaining M9 work: history delete is not executed without user confirmation, real cookies selection needs a user-provided `cookies.txt`, notification/cancel and export-write frontstage paths still need coverage, and a fresh audit subagent must review this evidence and plan state.

**M9 visual-density update, 2026-07-05:** The GUI screenshot audit found oversized default Material typography and overly roomy shared components compared with `docs/android-gui-reference-v3.png`. The follow-up added compact app typography, tightened bottom navigation/cards/segmented rows/list rows, and stopped passive default runtime messages from occupying a large download-page card. Fresh checks passed: `:app:testDebugUnitTest`, `:app:assembleDebug`, and API37 `YtdlAppUiTest` 5/5. Static screenshots are saved under `docs/qa/android-visual-audit-20260705-compactfix/`. This is visual evidence only; it does not replace final Computer Use full-flow acceptance.

**M9 cancel-path update, 2026-07-05:** A fresh audit found that canceling during the tiny gap before `DownloadService` attached its cancellation token could be lost. The fix records early cancellation in `DownloadCoordinator` and applies it as soon as `MutableDownloadCancellation` is attached. Fresh checks passed: targeted race unit test, queue runtime-message unit test, full `:app:testDebugUnitTest`, `:app:assembleDebug`, API37 connected `YtdlAppUiTest#downloadPageCanCancelRunningForegroundTask`, and full API37 connected `YtdlAppUiTest` 6/6. The connected test uses the required YouTube URL, starts a real foreground download, cancels from the queue page immediately after the real queue card appears, and asserts Room latest history is `canceled`, not empty and not completed. Screenshot evidence is saved under `docs/qa/android-cancel-20260705/`. Computer Use could enumerate the emulator window in this later run but failed to activate it (`failed to activate captured window`), so system notification cancel and final foreground Computer Use acceptance remain open.

**M9 current install state, 2026-07-06:** API35 has a non-final foreground regression for clean URL paste, format application preserving the analyzed result, 1080p video+audio native merge, and cleanup of separate video/audio intermediate files after a successful merge. API37 temporarily failed to install the latest APK because previous exported `.mp4` test files left only about `230M` free; after user approval those old top-level test videos were deleted, free space recovered, the latest `app-debug.apk` installed successfully, and `com.garyapp.ytdl/.MainActivity` is foreground. Continue M9/T12 from this latest API37 install state; do not count API35 as final acceptance.

**M9 403 retry update, 2026-07-06:** API37 foreground testing found a real failure after video format `299` completed: audio format `140` returned `HTTP Error 403: Forbidden`, leaving only the video stream and surfacing as a file-processing failure. The fix classifies 403/Forbidden as a network error and retries only the failed format segment once, so a successful video stream is not downloaded again. Fresh checks passed: targeted retry unit test, full `DownloadRequestRoutingTest`, full `:app:testDebugUnitTest`, and `:app:assembleDebug`. After reinstalling the APK, Computer Use foreground testing on API37 completed real analysis, 1080p video+audio download, staged progress, audio retry, native merge, history write, and retained only `merged-299-140.mp4` in the latest task directory. Remaining M9/T12 work is still confirmation-delete, real cookies file selection, and more foreground failure-recovery paths. 2026-07-06 latest user rule changed the input acceptance standard: final foreground validation must use realistic system-keyboard URL input; earlier hardware-key input evidence is historical support only.

**M9.1 next slice, 2026-07-06:** Before another long full-download run, close one non-destructive T12 gap: foreground visible failure recovery and URL input boundary evidence on API37. This slice must not execute destructive history deletion and must not require a user-provided real cookies file. It should: (1) inspect current failure UI and URL-input code; (2) add or adjust unit tests only if the UI can hide or sanitize the relevant error incorrectly; (3) use Computer Use in the foreground emulator to verify at least one recoverable failure such as invalid URL or non-http/https URL; (4) input URLs by mimicking a real Android phone: tap the field, allow the system keyboard, type through the visible keyboard, and confirm the entered text was not changed by candidates, autocomplete, handwriting overlays, or menus; (5) update `docs/qa/android-mvp-smoke.md` and `docs/qa/android-full-visual-test-plan.md` with exact evidence and remaining boundaries. Fresh checks remain `:app:testDebugUnitTest`, `:app:assembleDebug`, and foreground visible Computer Use verification for the affected flow.

**M9.1 soft-keyboard result, 2026-07-06:** URL input now always allows the Android system keyboard and actively requests it on focus/click, including AVDs with `hw.keyboard=yes`. Fresh checks passed: `DownloadGuiBindingTest`, full `:app:testDebugUnitTest`, and `:app:assembleDebug`. API37 foreground Computer Use testing installed the new APK, set `show_ime_with_hard_keyboard=1`, tapped the URL field, observed the visible Gboard soft keyboard, entered invalid text through the soft keyboard without selecting candidates, and confirmed the Chinese error appears while no real queue task is created. Screenshots and boundaries are recorded in `docs/qa/android-mvp-smoke.md`.

**M9.2 next slice, 2026-07-06:** Continue toward T12 with a non-destructive required-URL foreground path using the updated input standard. Install the current APK on API37, use Computer Use in the visible emulator window to tap the URL field, type `https://www.youtube.com/watch?v=tkxzMEfp49Q` through the visible Android system keyboard, verify the full URL in the field without candidate/autocomplete rewriting, run real analysis, and confirm the Download and Format pages show the actual title/duration/thumbnail/available high-resolution options. Do not count this as final T12 because it does not yet require another long download, confirmation delete, or real cookies selection. Save screenshots and exact boundaries in `docs/qa/android-mvp-smoke.md`; fresh checks remain `android_env.ps1`, `:app:testDebugUnitTest`, `:app:assembleDebug`, and a foreground Computer Use run.

**M9.2 result, 2026-07-06:** API37 foreground Computer Use installed the current APK, focused the URL input, showed the Android system keyboard, and clicked Gboard keys to enter the full required URL `https://www.youtube.com/watch?v=tkxzMEfp49Q`. `uiautomator` text tree confirmed the exact URL value. Real analysis succeeded and showed the real thumbnail, title `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`, duration `08:02`, and `自动（推荐） · 1080p MP4 需原生合并`. The Format page then showed current-video-derived rows: 2160p/1440p disabled with unavailable reasons and 1080p available with native-merge labeling. Evidence is saved under `docs/qa/android-computer-use-20260706-m9-required-url/` and summarized in `docs/qa/android-mvp-smoke.md`. This remains a non-destructive M9 slice, not final T12, because the system-keyboard path has not yet continued through download, queue, history, settings, confirmation delete, or real cookies selection.

**M9.3 result, 2026-07-06:** API37 foreground Computer Use continued the same system-keyboard input acceptance path from M9.2 through a real high-resolution `视频+音频` download. The implementation remains serial rather than parallel (`下载视频 -> 下载音频 -> 原生合并`). Saved M9.3 evidence proves the video stage progressed through visible byte counts, then the completed queue card showed `下载视频✓ / 下载音频✓ / 原生合并✓`, `100%`, file size, App 私有目录 and export naming guidance. Current naming rule is `清理后的标题-yyyyMMdd-HHmmss.ext`, with same-name conflicts handled by the system save picker prompt or suffix behavior, not silent app overwrite. History received the completed item, and Settings still showed parser/media/cookies/privacy boundaries. The current saved M9.3 evidence does not include an independent URL-input screenshot/XML or an audio/merge in-progress screenshot/XML; URL input evidence is the M9.2 saved system-keyboard evidence, and M9.3 evidence starts at the queue lifecycle. Evidence is saved under `docs/qa/android-computer-use-20260706-m9-download-mainpath/` and summarized in `docs/qa/android-mvp-smoke.md`. This remains a non-destructive M9 slice, not final T12. At the time of M9.3, confirmation-delete, real cookies selection, and more failure-recovery frontstage paths were still open; the first two are closed by M9.4 below.

**M9.4 next slice, 2026-07-06:** Close two frontstage gaps without touching user-owned data: history delete confirmation and cookies file selection. Use only a uniquely named test history record for delete confirmation, and use a synthetic `cookies.txt` test file created for this QA run to exercise the Android file picker/cookies-reference UI; do not read, log, display, or persist cookies contents. Required checks: (1) install current APK on the single visible API37 emulator; (2) use Computer Use in the foreground visible emulator window; (3) insert or create a test history record, open History, tap delete, observe the confirmation dialog, cancel once and verify the record remains, then confirm deletion only for that test record and verify it disappears; (4) open Settings, launch the cookies file picker, select the synthetic `cookies.txt`, verify Settings shows only a safe reference/name and no file contents; (5) run `:app:testDebugUnitTest` and `:app:assembleDebug`; (6) save screenshots/XML and record the boundary in `docs/qa/android-mvp-smoke.md` and `docs/qa/android-full-visual-test-plan.md`. This still does not complete final T12 because the final full-flow pass must include the required URL main path and any remaining failure-recovery checks in one coherent foreground run.

**M9.4 result, 2026-07-06:** API37 foreground Computer Use used a single visible emulator window and completed the privacy/action checks with only synthetic data. A test-only `UITEST_FOREGROUND_DELETE_M9_4` history record was inserted, History showed it, Delete opened `确认删除历史记录`, Cancel preserved it, and after explicit user approval Confirm removed only that test record. Settings launched the Android file picker and selected a synthetic `cookies.txt`; Settings then showed `cookies.txt · 仅保存引用` without displaying file contents. Evidence is saved under `docs/qa/android-computer-use-20260706-m9-privacy-actions/`. This closes the M9.4 history-delete and cookies-selection foreground gaps, but final T12 remains open until more failure-recovery checks and one coherent full-flow foreground pass are complete.

**M9.5 result, 2026-07-06:** Closed a non-destructive foreground recovery gap without repeating large downloads. The History page now renders runtime recovery messages, so `打开` / `分享` / `导出` on a history item whose app-private output file is missing visibly reports `历史记录对应的本地文件不存在或为空，请重新下载或删除该记录。` The queue progress bar now animates when the current stage is active but yt-dlp has not emitted a reliable stage percent, instead of looking like a fixed `0%` bar. Fresh checks passed: targeted `DownloadUiBridgeTest` unit tests, API37 connected `YtdlAppUiTest#historyMissingOutputActionsShowVisibleRecovery`, explicit foreground seed/cleanup helpers, and Computer Use foreground clicking of `打开` / `分享` / `导出` on a synthetic `UITEST_MISSING_OUTPUT_M9_5_*` record. Evidence is saved under `docs/qa/android-computer-use-20260706-m9-missing-output/`. This is failure-recovery coverage only, not final T12.

**M9.6 result, 2026-07-06:** Closed another non-destructive foreground recovery gap without repeating large downloads. A synthetic `UITEST_EXPORT_CANCEL_M9_6_*` history item points to a 4 KB app-private output file, and Computer Use in the visible API37 emulator clicked `导出`, opened the Android `Downloads` save picker, then canceled with the visible Android Back button. The app returned to History and displayed `未获得保存位置授权，导出已取消。请重新选择保存位置。` without crashing or leaking local paths. Cleanup uses `HistoryDao.deleteByTitlePrefix("UITEST_EXPORT_CANCEL_M9_6_")` and removes only the test output directory. Evidence is saved under `docs/qa/android-computer-use-20260706-m9-export-cancel/`. This is export-cancel recovery coverage only, not final T12 and not a replacement for the existing real export-write evidence.

**M9.7 result, 2026-07-06:** Closed the notification-allowed foreground slice with Computer Use in the visible API37 emulator. The first notification-shade check showed no YTDL notification because the app notification permission was disabled (`importance=NONE`), which was recorded as a permission-state finding rather than a notification failure. After restoring notification permission for the test environment, the system shade showed `YTDL 下载任务 · 下载完成`. A second real `视频+音频` task showed an expandable `YTDL 下载任务` notification with stage `正在下载视频` and action `取消`; tapping notification `取消` changed the notification to `已取消`, and the app returned to `下载已取消。`. App-private test output directories were cleaned. Evidence is saved under `docs/qa/android-computer-use-20260706-m9-notification/`. This closes the notification-allowed frontstage path; notification-denied final frontstage review and one coherent full-flow T12 remain open.

**M9.8 next slice, 2026-07-06:** Close the MVP1 independent-subtitle user-visible loop without attempting MVP2 subtitle embed/burn. Current ability-layer tests can download a subtitle file, and the format page can pass `selectedSubtitles`, but history/export still mainly track the media output. This slice should: (1) persist completed subtitle output references safely alongside the media output; (2) migrate the Room schema without losing existing history; (3) show history/queue metadata that distinguishes “媒体文件 + 独立字幕文件” from media-only output; (4) let the user export or share subtitle output when a completed task has subtitle files, while keeping `打开` focused on the media file; (5) keep cookies, URLs, local paths, query strings and raw subtitle contents out of history, logs, UI and errors; (6) add focused unit/migration/UI-binding tests and update `docs/qa/android-full-visual-test-plan.md` / `docs/qa/android-mvp-smoke.md`. Do not add FFmpeg, subtitle embedding, subtitle burning, three-in-one output, or a broad storage redesign in this slice. Final frontstage validation can use a synthetic history record with a real small subtitle file for UI/export affordance, and a separate real connected/ability test for subtitle download; do not repeat a large video download unless needed for T12.

**M9.8 result, 2026-07-06:** Closed the MVP1 optional independent-subtitle history/export loop. Completed history now stores media `outputUri` plus `subtitleOutputUris` as app-private relative references; Room migrated to v3 with 1->2 and 2->3 paths; queue/history metadata show “媒体文件 + 独立字幕文件” only when subtitle outputs exist; completed subtitle records expose “分享字幕” and “导出字幕” while media `打开/分享/导出` stay unchanged. Focused JVM tests, API37 Room migration connected tests, full `testDebugUnitTest`, and `assembleDebug` passed. Computer Use frontstage validation in the visible API37 emulator used a synthetic small app-private media+subtitle history record to open the History page, verify the subtitle actions, open the Android share sheet, open the Android save picker, and confirm no app-private path/cookies/header/query/raw subtitle leak was visible. Evidence is saved under `docs/qa/android-computer-use-20260706-m9-8-subtitle-output/`; the synthetic record and private test files were cleaned. This does not complete T12: one coherent full-flow MVP regression still remains. Real subtitle-success frontstage evidence is an optional subtitle专项，不是默认主路径的 T12 前置条件。

**M9.9 next slice, 2026-07-06:** Verify and, if needed, fix the optional real frontstage subtitle-download chain with the required URL `https://www.youtube.com/watch?v=tkxzMEfp49Q`. Use the visible API37 emulator and system soft keyboard URL entry. After analysis, the format page may allow subtitle selection only when the current analysis result actually contains subtitle files; if no subtitle files are available, the subtitle row must be non-selectable and no `字幕文件` queue stage may appear. When the user selects a supported subtitle option, choose a normal video+audio format, start the real download, and verify queue/history state shows MVP1 output as “媒体文件 + 独立字幕文件”. The expected optional-subtitle end state is one merged media file plus at least one independent subtitle file; do not add subtitle embed/burn or FFmpeg. The `字幕文件` queue stage is optional and only appears for tasks that selected subtitle output; media-only tasks must not show it. If the GUI cannot expose the subtitle selector only when analysis provides subtitles, cannot carry `selectedSubtitles` into the real request, loses subtitle outputs in history, or leaks private paths/sensitive strings, fix the smallest relevant slice before continuing. Save Computer Use evidence under a new `docs/qa/android-computer-use-20260706-m9-9-real-subtitle/` folder. This slice is still not the final T12 coherent full-flow gate.

**M9.9 partial result, 2026-07-06:** API37 foreground Computer Use reached the optional real subtitle chain and generated the merged media output, but the final `zh-Hans` subtitle download failed with YouTube `HTTP Error 429: Too Many Requests`. Do not treat this as final subtitle success or repeat the 355MB download immediately. The implementation now keeps the successful media output on subtitle failure, shows a subtitle-specific recovery message, allows failed-with-media history rows to open/share/export the media, and explicitly keeps `字幕文件` as an optional queue stage shown only when the task selected subtitles. JVM tests, full `testDebugUnitTest`, `assembleDebug`, and APK install passed. Real subtitle-success frontstage verification can be rerun later as an optional subtitle专项 after rate limiting clears, but it does not block MVP1 media主路径或 T12 默认无字幕全流程。

**M9.9 correction, 2026-07-07:** 用户再次明确：MVP1 不强制下载字幕；只有当前视频分析结果确实提供字幕文件时，用户才能选择下载独立字幕文件。无字幕或未选择字幕时，不下载字幕文件，也不显示 `字幕文件` 进度小项。代码已补 `subtitleSelectionUiState`：无分析显示“先分析”，无字幕显示“无可选”且不可点击，有字幕才显示“选择/取消”。相关 UI/下载测试、全量 `testDebugUnitTest`、`assembleDebug` 和最新 APK 安装启动检查已通过。轻量字幕探针仍返回 YouTube `HTTP Error 429: Too Many Requests`，但该结果只影响可选字幕专项，不阻塞默认无字幕主路径。

**M9.10 temporary test boundary, 2026-07-07:** 用户要求暂时不测试下载字幕文件，防止再次触发 YouTube 429。后续下载测试默认不选择字幕，不运行真实字幕下载、轻量字幕探针或前台字幕下载流程；T12 主路径只覆盖真实分析、视频/音频分离下载、原生合并、队列、历史、导出、通知、设置和失败恢复。字幕相关只保留“无字幕不可选、有字幕才可选但默认不选”的 UI/请求校验，以及既有历史能力证据。恢复字幕下载测试必须等用户明确同意。

**M9.11 smoke helper, 2026-07-09（历史记录，已由 2026-07-10 地址集替代）:** 按当时用户要求，后续真实测试地址曾切换为 `lcFR2mFSmSs`、`auNezUzwCZg` 和 Shorts `jWTrleK2_MU`；旧 `tkxzMEfp49Q` / `QBwpO9f0oAw` 只保留为历史证据。新增 `scripts/android_real_smoke.ps1` 作为辅助验证入口，默认只运行环境、单测、打包和无真实 YouTube 请求的 connected 安全集；真实分析/下载必须显式开关并遵守 10 分钟分析间隔、30 分钟下载间隔，字幕真实下载继续暂停。字幕暂停期间即使传入 `-RunRealSubtitleDownload` 也会被脚本拒绝，除非用户明确恢复后额外传入 `-AllowRealSubtitleDownload`。2026-07-09 默认脚本已通过；connected 设置页颜色测试的可见性假失败已修复为滚到 `ytdl-settings-appearance-summary` 后再断言；新主地址真实分析单项已通过并写入节流状态。

**M9.12 input-environment correction, 2026-07-09:** Computer Use remains available and can control the visible API37 emulator. A foreground reproduction showed that the matrix AVD setting `hw.keyboard=yes` can put Gboard into a physical-keyboard toolbar mode that blocks reliable long-URL soft-keyboard entry. The environment route is corrected to phone-like input: `scripts/android_env.ps1` now writes `hw.keyboard=no` for matrix AVDs while keeping `show_ime_with_hard_keyboard=1` and Gboard enabled on online emulators. Added `tests/test_android_env_script.py` to keep this contract. API37 was restarted with `hw.keyboard=no`; Computer Use then showed the full Gboard on-screen keyboard without the large `Emulator` floating input panel. This is input-environment progress only; it does not count as new YouTube GUI analysis or download acceptance.

**M9.13 foreground new-primary analysis, 2026-07-09:** After a fresh `node_repl` smoke, `sky.list_apps()` bootstrap, and Calculator `1+1=2` Computer Use check, API37 was cold-started from no running emulator, moved to the right side of the primary screen, refreshed with `android_env.ps1`, and reinstalled with the current debug APK. Clicking the URL field still initially showed the Gboard hardware-keyboard toolbar even with `hw.keyboard=no`; diagnostics showed the app `EditText` was served and `mInputShown=true`, while Gboard exposed a visible `Show on-screen keyboard` action. Choosing that visible action restored the full Gboard key area. The new primary video was then entered through visible Gboard key presses, without adb text injection, clipboard, hardware keys, or candidate selection. Because Gboard's first symbol-page `=\<` key toggles symbols rather than typing `=`, the optional `?si` share parameter was removed with visible backspace and the canonical URL `https://youtu.be/lcFR2mFSmSs` was analyzed. Foreground analysis succeeded with title `KISSING YOUR BEST FRIEND tiktok challenge ! Part 5 🔥`, duration `15:04`, and `自动（推荐） · 1920p WEBM 需原生合并`. Evidence is saved under `docs/qa/android-computer-use-20260709-new-url-analysis/`. This is foreground analysis evidence only: no download, no Shorts sample, no subtitle test, no notification-denied foreground review, and no final T12 pass.

**M9.14 notification-denied foreground review, 2026-07-09:** Continued in the same visible API37 emulator after re-running the required `node_repl` smoke, `sky.list_apps()` bootstrap, and Calculator `1+1=2` check. Auxiliary permission state showed `POST_NOTIFICATION: ignore` and `android.permission.POST_NOTIFICATIONS: granted=false`. Computer Use switched the app to Settings in the foreground and verified the user-visible `通知权限` row shows `未授权 · 下载仍在应用内显示进度` with a `请求` entry. No Android permission dialog was accepted or permission-grant path executed. Evidence is saved under `docs/qa/android-computer-use-20260709-notification-denied/`. This closes the notification-denied visible-state review, but final T12 still needs the current Shorts sample and release-gate summary; real subtitle download remains paused.

**M9.15 Shorts foreground sampling and native-merge compatibility fix, 2026-07-09:** Continued in the same visible API37 emulator after the required `node_repl` smoke and `sky.list_apps()` bootstrap; Calculator is no longer treated as a required Computer Use preflight. Computer Use entered `https://youtube.com/shorts/jWTrleK2_MU` through the visible Gboard keyboard without adb text injection, clipboard, hardware keys, candidates, or autocomplete. Real analysis succeeded with title `🚨THIS IS WHY The Celtics Won The Jaylen Brown-Paul George Trade #celtics #nba #chatsports`, duration `00:57`, and an initial `1920p MP4 需原生合并` auto summary. A no-subtitle short download was started and reached real queue progress, then failed with file processing error rather than 429. The app-private task directory showed MP4 video `137` plus WebM/Opus audio `251`, exposing that the automatic MP4 native-merge route could select an incompatible standalone audio stream. The fix restricts MP4 native-merge candidates to MP4/AVC video plus M4A/MP4A audio while leaving single-file, video-only, and audio-only routes unchanged; WebM-only high-resolution rows now explain `当前视频未提供可原生合并的 MP4 格式` instead of pretending the resolution is absent. A user foreground review then caught that vertical Shorts expose actual heights such as `1920p/1280p/854p/640p/426p/256p`, while the old format page only listed fixed landscape-style heights; the format page now merges standard heights with current-analysis heights so real Shorts rows appear as selectable options. After the 30-minute real-download spacing window, Computer Use started the no-subtitle Shorts download again; the visible queue completed `下载视频✓ / 下载音频✓ / 原生合并✓`, `14.2 MB / 14.2 MB`, and history recorded the completed `视频 137 + 音频 140` item. Auxiliary app-private inspection confirmed the new output is `merged-137-140.mp4`, while the old failed `251.webm` task remains only as historical evidence. Fresh checks passed: `FormatSelectionModelTest`, the focused GUI summary regression test, full `:app:testDebugUnitTest`, `:app:assembleDebug`, `tests/test_android_env_script.py`, and installing the rebuilt debug APK. Evidence is saved under `docs/qa/android-computer-use-20260709-shorts-sample/`. This closes the current Shorts sample, but final T12 still needs the new primary normal-video complete GUI download and release-gate summary; real subtitle download remains paused.

**M9.16 Gboard direct-popup correction, 2026-07-09:** User review correctly pointed out that a real-phone-like URL input path should show the bottom on-screen keyboard immediately after tapping the address field; needing an extra tap on Gboard's physical-keyboard toolbar is an environment gap, not a final acceptance path. Diagnostics showed the app URL `EditText` was already served and `mInputShown=true`, and the AVD matrix was already on `hw.keyboard=no`, but Android/Gboard still enumerated a physical keyboard and Gboard's own `Physical keyboard` preference had `Show on-screen keyboard` off with toolbar behavior enabled. The visible Gboard settings were corrected to `Write in text fields -> Use stylus to write in text fields = off`, `Physical keyboard -> Show on-screen keyboard = on`, and `Show toolbar = off`; after that, tapping the current APK URL field directly displayed the full bottom Gboard. `adb root` and `run-as com.google.android.inputmethod.latin` are unavailable on the Play production emulator image, so this Gboard private preference is recorded as a visible emulator setup fact rather than a scriptable `settings put` action. Evidence is saved under `docs/qa/android-computer-use-20260709-keyboard-direct-popup/`. Final T12 must continue from this corrected direct-popup state and must not count the extra-toolbar-tap path as realistic input acceptance.

**M9.17 primary normal-video full foreground download and release gate, 2026-07-09:** Continued in the visible API37 emulator after the required `node_repl` smoke and `sky.list_apps()` bootstrap. Computer Use clicked the URL field and verified that the full bottom Gboard appeared directly, then entered the full primary share URL `https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G` through visible Gboard key presses without adb text injection, clipboard, hardware keys, candidates, autocomplete, handwriting overlays, or a Gboard toolbar workaround. UIAutomator auxiliary evidence confirmed the exact URL in the input field. Foreground analysis succeeded with title `KISSING YOUR BEST FRIEND tiktok challenge ! Part 5 🔥`, duration `15:04`, and `自动（推荐） · 1080p MP4 需原生合并`. The format page confirmed compatible MP4 native-merge rows and correctly disabled WebM-only heights with `当前视频未提供可原生合并的 MP4 格式`; subtitles showed `当前视频未提供字幕` / `无可选` and were not selected. After the 30-minute real-download spacing window, Computer Use clicked `开始下载`. The queue progressed through video download, audio download, and native merge; completion showed `下载视频 ✓ / 下载音频 ✓ / 原生合并 ✓`, `101.5 MB / 101.5 MB`, and `100%`, with no subtitle stage. History recorded the completed `视频 137 + 音频 140` media item at `07/09 07:51` with `打开 / 分享 / 导出 / 删除`; opening the top history item launched the system video viewer and played the merged MP4. Auxiliary inspection found `files/gui-downloads/task-1783583408203-2/merged-137-140.mp4`, `106421453` bytes. logcat did not show 429, Too Many Requests, or download failure. Fresh closing checks passed: `tests/test_android_env_script.py` reported `1 passed`, `:app:testDebugUnitTest` was `BUILD SUCCESSFUL`, `:app:assembleDebug` was `BUILD SUCCESSFUL`, and `git diff --check` showed only line-ending warnings. Evidence is saved under `docs/qa/android-computer-use-20260709-primary-full-download/`, and `docs/qa/android-mvp-smoke.md` records the release-gate summary. This closes the API37 emulator foreground M9/T12 gate. Real subtitle download remains paused by user request, and M10 real-device validation plus Play signing/store materials remain later-stage work.

### Continuation Task M10: Xiaomi 14 Real-Device Validation

**Status:** 未开始；当前 ADB 只显示 API37 模拟器，用户确认暂时不会连接小米 14。该任务等 M9/T12 模拟器前台验收之后、推进到后续第 7 项时再执行。

**Purpose:** When the workflow reaches the later device/release-validation stage, validate on Xiaomi 14 or an equivalent `arm64-v8a` Android phone instead of doing formal Google Play store delivery work.

**Files:**
- Update: `docs/qa/android-mvp-smoke.md`
- Update: `docs/qa/android-full-visual-test-plan.md`
- Update only if real-device findings require code fixes: Android source/test files touched by the failing behavior.

**Steps:**
- [ ] Connect Xiaomi 14 or equivalent `arm64-v8a` phone with USB debugging enabled and verify `adb devices -l` shows a physical device model, not only `emulator-*`.
- [ ] Install the current `android/app/build/outputs/apk/debug/app-debug.apk` on the physical device.
- [ ] Launch the app and verify the five pages render without cutouts, clipping, bottom-nav overlap, or unreadable status/navigation bars.
- [ ] Run the current primary normal video URL `https://www.youtube.com/watch?v=PqQNXB6hhUs` through real GUI analysis, 1080p-or-best-supported video+audio format selection, real download, native merge, queue completion, and history landing.
- [ ] Verify device-specific behavior that the emulator cannot prove: notification visibility/permission behavior, background download survival, app-private output open/export, share sheet appearance without sending data, and storage permission denial recovery.
- [ ] 字幕下载测试当前暂停；不要选择字幕或触发字幕文件下载。只在用户明确恢复后，再验证 "merged video+audio file plus separate subtitle file"。
- [ ] Record device model, Android version, ABI, build fingerprint if available, screenshots, output file sizes, and observed gaps in `docs/qa/android-mvp-smoke.md`.
- [ ] Run `cd android; .\gradlew.bat :app:testDebugUnitTest` and `cd android; .\gradlew.bat :app:assembleDebug` after any code fix.
- [ ] Commit and push only after the real-device evidence or any fix is recorded.

**Acceptance:** M10 is accepted only when a real Xiaomi 14 or equivalent `arm64-v8a` phone runs the GUI flow and records device-specific evidence. Do not attempt this task until a phone is connected; continue the current M9/T12 emulator and GUI-verification work first.

### Continuation Task M11: Reference Visual Fidelity and Codex Appearance

**Status:** 进行中；M9/T12 已证明当前 GUI 可真实运行，M11 已完成五页基线/Codex 配色审计、底部导航与设置滚动缓冲、格式与队列空态密度、顶部挖孔安全区及状态/分辨率徽标的首轮修正。尚未完成真实下载进行中队列截图和完整五页前台复核，因此不能把 M11 写成通过。

**Purpose:** Bring the implemented five-page Compose UI closer to the confirmed Android GUI reference while preserving the real data bindings, queue/history behavior, privacy boundaries, and Play-safe wording already accepted by M9/T12.

**Files:**
- Modify as needed: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Modify as needed: `android/app/src/main/java/com/garyapp/ytdl/ui/theme/Theme.kt`
- Modify/add focused tests under `android/app/src/test/java/com/garyapp/ytdl/ui/`
- Update: `docs/qa/android-full-visual-test-plan.md`
- Update: `docs/qa/android-mvp-smoke.md`
- Create or update visual evidence under `docs/qa/android-visual-fidelity-*`

**Steps:**
- [ ] Create a focused visual-fidelity checklist from `docs/android-gui-reference-v3.png` covering five-page title hierarchy, card density, bottom navigation icon/pill behavior, format rows, queue grouping, history search/filter/actions, settings rows, safe areas, and scroll behavior.
- [ ] Capture or reuse foreground screenshots for all five current pages with the keyboard dismissed; compare them against the checklist and record exact gaps before implementation.
- [ ] Make only scoped UI refinements that preserve real state: do not add fake sample data, do not hide required privacy/legal text, keep reasons for formats actually supplied but unavailable on this device, and do not weaken download/queue/history functionality.
- [ ] Verify both `基准图配色` and `Codex 风格` remain selectable, persisted, and visibly affect five-page accents; Codex colors must be consistent with the existing Codex preset rather than a new unrelated palette.
- [ ] Add or adjust focused JVM/Compose/UI binding tests for any new stable visual contract that can be asserted without pixel snapshots.
- [ ] Use Computer Use on the visible API37 emulator for the foreground visual smoke after changes. The smoke must include all five tabs and at least one page where the keyboard is dismissed so bottom navigation is fully visible.
- [ ] Include at least one foreground queue-page capture while a real download task is actively running, not only an empty or completed queue. The capture must show real progress, speed or ETA when available, pause/cancel affordances, the bottom navigation, and safe-area behavior without overlap.
- [ ] Run `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`, `cd android; .\gradlew.bat :app:testDebugUnitTest`, and `cd android; .\gradlew.bat :app:assembleDebug`.
- [ ] Save screenshots/XML and document any remaining intentional deviations from the reference.

**Acceptance:** M11 is accepted only when the current five-page UI has a screenshot-backed visual audit against `docs/android-gui-reference-v3.png`, Codex/reference appearance settings are verified at the foreground UI level, at least one real in-progress queue-page screenshot proves progress/speed-or-ETA/cancel affordances remain visible, focused tests pass, and any remaining mismatch is explicitly documented rather than silently treated as complete.

#### M11 Current Execution Slice: Foreground Queue Evidence Recovery (2026-07-10)

**Goal:** Recover reliable Computer Use control of the existing API37 window, then produce one rate-limited, no-subtitle real-download queue capture without compromising the Gboard-only URL-entry requirement.

**Files:**
- Update: `docs/qa/android-mvp-smoke.md`
- Update: `docs/qa/android-full-visual-test-plan.md`
- Update: `docs/qa/android-visual-fidelity-20260709-m11/audit.md`
- Create: `docs/qa/android-visual-fidelity-20260709-m11/36-queue-real-progress-*.png` and matching XML only after the screenshot visibly shows an active task.
- Modify only if the captured foreground UI reveals a reproducible mismatch: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt` and its focused test under `android/app/src/test/java/com/garyapp/ytdl/ui/`.

- [x] Re-run the environment script and confirm one online API37 `hw.keyboard=no` emulator with Gboard is available.
- [x] Reconnect Computer Use with the required smoke and foreground-raise the emulator; frontally inspect Download, Format, Queue, History, and Settings, including the `基准图配色` / `Codex 风格` controls above bottom navigation.
- [ ] Before any URL input, recover a stable foreground window after any activation failure using Computer Use window discovery and visible `Raise`; do not use adb text injection, clipboard, hardware keyboard, or PowerShell/SendKeys as an alternate input route.
- [ ] Use the visible Gboard to enter the approved real URL exactly, observe the complete value in the field, analyze once, leave subtitles unselected, and start one video+audio download only after the configured real-download interval permits it.
- [ ] Capture an active Queue page only while it visibly shows the real title, non-placeholder progress, byte/speed-or-ETA data when supplied by the pipeline, cancel affordance, safe area, and bottom navigation. Reject empty, completed, or blocked captures.
- [ ] If a foreground screenshot exposes a new stable visual defect, write a failing focused Compose/JVM test first, implement the smallest `YtdlApp.kt` correction, then run focused test, related UI tests, full `:app:testDebugUnitTest`, and `:app:assembleDebug` sequentially.
- [ ] Record the exact foreground evidence and any remaining mismatch in all three QA documents. Do not mark M11 complete until the acceptance paragraph above is satisfied.

#### M11 Capacity and Test-Cleanup Correction (2026-07-10)

一次历史链接的前台真实视频+音频下载在 `原生合并` 后失败。辅助容量证据确认 API37 `/data` 当时仅余约 `215 MB`，不足以在保留约 `331.5 MB` 视频流和音频流的同时再写出合并 MP4。历史记录的前台删除确认虽然移除了 Room 记录，但失败任务的中间流仍滞留在 App 私有下载目录；该目录已清理并核验为空。

已修复并验证：合并器现在在写输出前检查输入流总大小加 `1 MiB` 缓冲所需的可用空间，并映射为可操作的存储不足提示；下载管线失败时清理未纳入最终输出的任务文件，同时保留已经完成的媒体输出。focused 回归、全量 `:app:testDebugUnitTest` 和 `:app:assembleDebug` 均已通过，当前 APK 已在可见 API37 模拟器前台确认空历史和空队列。

后续真实请求只使用本计划已列主/备用/Shorts 地址；旧 `tkxzMEfp49Q` 不得再用于新的真实验收。每次真实测试完成或失败后，必须前台删除对应历史记录并辅助核验 `files/gui-downloads` 没有遗留测试文件。该修复不替代 M11 的新地址前台真实下载、截图留存和五页视觉验收。

#### M11 Format-Availability Visibility Correction (2026-07-10)

用户将格式页口径收敛为“只显示当前视频提供的格式”。分析后保留 `自动（推荐）`，分辨率行只来自实际 `hasVideo` 格式高度；没有任何当前流的固定高度不再显示。真实存在但不兼容原生 MP4 合并的行仍显示并保留明确原因，避免把兼容性限制伪装成视频没有该高度；无分析时的禁用空态不变。回归、全量单测和 debug 打包已通过，前台复核等待用户恢复 Computer Use 后执行。

#### M11 Commit Audit Follow-up: Cancellation Cleanup (2026-07-10)

对 `93991c4` 的 fresh audit 发现取消分支会漏清已下载但尚未成为最终输出的中间流。已按 TDD 修复：取消分支复用失败分支的 `cleanupUntrackedTaskFiles`，并由取消视频流后的任务目录为空断言保护。修复不删除已完成媒体输出；focused 回归、全量单测和 debug 打包均通过。该行为仍待未来恢复 Computer Use 后随真实取消路径前台复核。

#### M11 Foreground Format and Storage-Recovery Slice (2026-07-10)

Computer Use 已在 API37 可见窗口重新执行新主地址的完整软键盘输入、真实分析和格式页复核。不存在的固定高度已隐藏，实际存在但不兼容原生 MP4 合并的高度仍显示原因。随后无字幕真实视频+音频任务在队列中显示视频/音频字节进度、取消动作和真实 `1080p`；合并前空间预检以前台中文存储不足提示终止任务。历史确认删除、流文件清理、空目录清理和最新版空历史/空队列均已复核。这个切片证明格式可见性和容量失败恢复的前台路径，但没有生成新的落盘截图审计包，也没有替代 M11 的完整五页视觉验收。

#### M11 Capacity-Probe False-Rejection Correction (2026-07-10)

对上一节的新主地址失败补充更正：当时 API37 `/data` 约有 `550 MB` 可用空间，下载输入约为 `87.4 MB + 13.9 MB`，因此该“存储空间不足”提示不是实际容量问题。`NativeMuxerMediaProcessor` 原先把不存在的 `merged-*.mp4` 传给 `usableSpace`；Android 可为不存在文件报告 `0`，造成假拒绝。

按 TDD 新增 `MediaProcessorContractTest.mergeMeasuresAvailableSpaceAtExistingOutputDirectory`，旧实现失败后，修复为创建并使用受控的现有输出目录做可用空间探测。全量 `:app:testDebugUnitTest`、`:app:assembleDebug` 均通过，debug APK 已安装并在可见 API37 模拟器前台启动。完整真实下载受 30 分钟节流约束尚未重跑，因此不能将本修复视作新的前台原生合并成功或 M11 完成；下次允许的真实主/备用地址下载必须复核该路径。

fresh audit 的 P2 指出上述目录目标单测未覆盖真实 muxer 成功路径。已把 API37 本地生成媒体 instrumentation 的输出改为原本不存在的受控子目录 `new-task/merged.mp4`，并断言目录创建、非空输出与一条视频轨/一条音频轨。`NativeMuxerMediaProcessorInstrumentedTest` 2 项通过，随后全量 `:app:testDebugUnitTest` 和 `:app:assembleDebug` 再次通过；该无网络设备测试关闭了该 P2，但不替代后续前台真实下载复验。

#### M11 Capacity-Probe Foreground Recheck and History Cleanup (2026-07-10)

在 30 分钟完整下载间隔后，Computer Use 用完整底部 Gboard 逐键输入新主分享地址，辅助 UIAutomator 只核验最终准确字段。真实分析后启动默认无字幕视频+音频下载；队列可见 `19.3 MB / 87.4 MB`、`7%`、取消动作和 `1080p`，视频/音频阶段完成后进入原生合并，最终显示三阶段均为勾选、`101.5 MB / 101.5 MB` 和绿色“完成”。这给出容量探测目录修正的真实前台成功证据。

本次完成记录同时暴露历史删除 chip 在窄卡内不显示。先让 `DownloadUiBridgeTest.historyActionsRenderAsIconTextChipsWithoutLosingCallbacks` 因缺少删除独立行而失败，再把删除置于主操作行下方，保留确认对话和原 test tag；focused、全量 `:app:testDebugUnitTest` 与 `:app:assembleDebug` 均通过。新版 APK 前台显示删除 chip，确认删除后历史为空；辅助只移除已确认的本次测试任务目录并核验 `files/gui-downloads` 为空。该修复和成功流程仍未形成新的落盘五页审计包，M11 不因此完成。

提交后 API37 `YtdlAppUiTest#historyDeleteRequiresConfirmationForInsertedTestRecord` 经 `connectedDebugAndroidTest` 通过，覆盖插入测试记录、取消删除后保留、再次确认后删除。该无网络 instrumentation 是删除可见性前台复核之外的运行时辅助证据。

After each task commit:

1. Open a fresh independent audit thread against the task commit and plan section.
2. Ask the audit thread for P0/P1/P2 findings only: spec gaps, security/privacy leaks, failing build/test risk, Play-policy mismatch, or GUI mismatch.
3. Read the audit result in the main session.
4. Fix P0/P1/P2 findings before moving to the next task.
5. Re-run the task's verification commands.

## Completion Gate

The API37 emulator GUI release gate for M9/T12 passed on 2026-07-09 with fresh Computer Use evidence. The broader Android MVP goal remains open until the post-M9 requirements are also resolved: M11 visual fidelity/Codex appearance audit, M10 real-device validation when a Xiaomi 14 or equivalent `arm64-v8a` phone is connected, and the still-unstarted Play signing, privacy-policy URL, Data safety, and store-material decisions. Passing unit tests alone is never enough for a user-visible Android acceptance claim.
