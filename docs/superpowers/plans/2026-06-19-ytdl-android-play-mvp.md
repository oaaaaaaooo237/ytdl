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
- [ ] Configure plugins: `com.android.application` 9.2.1, `org.jetbrains.kotlin.android` 2.3.0, `org.jetbrains.kotlin.plugin.compose` 2.3.0, `com.chaquo.python` 17.0.0.
- [ ] Configure Android: namespace `com.garyapp.ytdl`, `compileSdk = 37`, `targetSdk = 37`, `minSdk = 24`, app id `com.garyapp.ytdl`.
- [ ] Configure Chaquopy with `buildPython("D:/garyapp/ytdl/.venv/Scripts/python.exe")`.
- [ ] Implement a real `MainActivity` and Compose shell with bottom navigation labels `下载`, `格式`, `队列`, `历史`, `设置`.
- [ ] Add a unit test asserting the five navigation labels remain present in the app model.
- [ ] Verify:
  - `.\gradlew.bat :app:testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
  - Install and launch on `ytdl_api37_play_x86_64` using `adb install` and `adb shell am start`.
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

### Task 6: Android ffmpeg MediaProcessor

**Purpose:** Provide first-phase real media operations: audio/video merge, subtitle embed, and subtitle burn on Android.

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/MediaProcessor.kt`
- Create: `android/app/src/main/java/com/garyapp/ytdl/media/FfmpegBinary.kt`
- Create: `android/app/src/main/assets/licenses/ffmpeg.md`
- Create: `android/app/src/test/java/com/garyapp/ytdl/media/MediaProcessorCommandTest.kt`
- Create or add after chosen binary source: `android/app/src/main/jniLibs/x86_64/ffmpeg`
- Create or add after chosen binary source: `android/app/src/main/jniLibs/arm64-v8a/ffmpeg`

**Steps:**
- [ ] Select an auditable Android ffmpeg binary/library route with license text recorded in `ffmpeg.md`.
- [ ] Package only `x86_64` and `arm64-v8a` first-phase ABIs.
- [ ] Implement command building for merge, subtitle embed, and subtitle burn with safe paths.
- [ ] Add tests asserting command arguments and output paths are quoted/safe.
- [ ] Verify `.\gradlew.bat :app:testDebugUnitTest` and `.\gradlew.bat :app:assembleDebug`.
- [ ] Real check on API37: execute ffmpeg `-version`, then run at least one short merge or subtitle operation.
- [ ] Commit with message `android: add ffmpeg media processor`.

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

After each task commit:

1. Open a fresh independent audit thread against the task commit and plan section.
2. Ask the audit thread for P0/P1/P2 findings only: spec gaps, security/privacy leaks, failing build/test risk, Play-policy mismatch, or GUI mismatch.
3. Read the audit result in the main session.
4. Fix P0/P1/P2 findings before moving to the next task.
5. Re-run the task's verification commands.

## Completion Gate

The Android MVP is not complete until Task 9 passes with fresh evidence. Passing unit tests alone is not enough; the final gate requires real emulator GUI operation with `https://www.youtube.com/watch?v=tkxzMEfp49Q`.
