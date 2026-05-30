# YTDL GUI Design

Date: 2026-05-30

## Status

This design is approved by the user as the basis for implementation and has been independently audited in a separate Codex thread.

Confirmed route:

- Build a Windows 11 x64 desktop GUI application.
- Use Python, PySide6, bundled `yt-dlp.exe`, and PyInstaller.
- Use the selected light Windows 11-style GUI reference in `docs/gui-reference.png`.
- Do not start implementation planning until this audited design has been reviewed.

Audit thread:

- `019e7946-1059-7c21-92b8-f420cba06e47`

Audit outcome:

- The route is feasible.
- The first-version acceptance boundary must distinguish baseline operation without ffmpeg from enhanced operation with ffmpeg.
- Built-in network playback must be treated as best-effort preview, not a core download blocker.
- Runtime `yt-dlp` updates must include source, validation, atomic switch, and rollback rules.
- Packaging must include third-party license notices.

## Product Goal

Create a polished desktop downloader for YouTube-first video workflows. The app should let the user paste one or more video page URLs, analyze available media formats, choose video/audio/subtitle options, download to a chosen folder, optionally preview playback inside the app during download, and manage download history.

The first version is YouTube-first but should not artificially block other websites that `yt-dlp` supports. It should not bypass DRM or site permissions. For videos that require login access, the app supports optional `cookies.txt` import.

Non-YouTube sites are best-effort in the first version. They are allowed through the same `yt-dlp` path, but they are not part of the core acceptance tests.

## Visual Direction

The GUI should follow `docs/gui-reference.png`:

- Light Windows 11-style utility app.
- Left navigation rail.
- White and light-gray surfaces.
- Teal primary action color.
- Clear workflow: URL input, analysis, options, queue, history, settings.
- Final UI labels should be Chinese, even though the reference mockup contains English labels.

Reference note: `docs/gui-reference.md`.

Visual acceptance checklist:

- Left navigation rail is visible on the main desktop layout.
- Main work area uses light white/gray surfaces with clear panel separation.
- Primary action buttons use a teal accent consistent with the selected reference.
- UI labels and user-facing messages are Chinese.
- Typography is clean and Windows-utility-like, without oversized landing-page hero text.
- The first implementation pass should save desktop screenshots for Download, Queue, History, and Settings pages and compare them against `docs/gui-reference.png` for layout direction.

## Technology Route

Use:

- GUI: PySide6.
- Download and analysis engine: bundled `yt-dlp.exe` invoked as a subprocess.
- Packaging: PyInstaller for Win11 x64.
- Media preview: PySide6 media playback for a network preview stream extracted by `yt-dlp`.
- Config and history: local files in the user's application data directory.
- External media tooling: ffmpeg is not bundled in the first version.

Why this route:

- It is the fastest route to a complete first version with good GUI quality.
- `yt-dlp.exe` can be updated independently at runtime.
- PyInstaller can package the GUI app and bundled tools into a Windows executable.
- Core behavior can be tested separately from the GUI.

## Application Architecture

Split the app into focused layers:

- UI layer: main window, navigation rail, download page, format page, queue page, history page, settings page, about page.
- Task layer: queue management, concurrent download scheduling, pause/resume/cancel, progress events.
- `yt-dlp` layer: analysis, format listing, download command construction, playback stream extraction, `yt-dlp` update checks.
- Config/history layer: default folder, concurrency, cookies path, ffmpeg path, update preferences, persistent download history.
- System detection layer: ffmpeg search, `yt-dlp` version checks, writable user-data path checks.

The UI must not block on network checks, update checks, or long-running analysis/download work.

## Main Navigation

Left navigation entries:

- Download: paste URLs, analyze videos, choose save location, choose basic audio/video/playback options, start tasks.
- Formats: choose resolution, FPS, codec, video bitrate preference, audio bitrate preference, container, subtitle behavior.
- Queue: show active and pending tasks, progress, speed, ETA, status, pause, resume, cancel, clear completed.
- History: search previous downloads, re-download, open file, open folder.
- Settings: default save folder, max concurrency, `cookies.txt`, ffmpeg path, `yt-dlp` update behavior.
- About: app version, `yt-dlp` version, ffmpeg status, help links and usage notes.

## Core User Flow

1. User pastes one or more URLs.
2. User clicks Analyze.
3. The app calls `yt-dlp.exe --dump-json` for each URL or a safe equivalent that returns metadata and formats.
4. The app displays title, thumbnail, duration, available formats, available subtitles, and warnings.
5. User selects save folder and download type:
   - Audio + video.
   - Audio only.
   - Video only.
6. User selects detailed format and subtitle options.
7. If "play while downloading" is enabled, the app obtains a suitable network preview stream and plays it in the built-in player.
8. User clicks Start Download.
9. The queue page shows status, progress, speed, ETA, and errors.
10. Finished tasks are written to persistent history.

## Scope Choices

### Supported sites

The app is YouTube-first. Other `yt-dlp`-supported URLs are allowed, but UI copy and primary testing focus on YouTube.

Acceptance boundary:

- Core acceptance covers normal public YouTube video URLs.
- Other supported sites are best-effort.
- Non-YouTube failures should use generic Chinese error guidance and include log details, but they do not block first-version acceptance.

### Login and cookies

Default behavior handles public videos only.

The app supports optional `cookies.txt` import:

- Settings page lets the user select a `cookies.txt` file.
- Settings page lets the user clear the cookies setting.
- Error panels for login, age-restricted, private, or permission-restricted videos should point users to the cookies help.
- The app passes the file path to `yt-dlp`.
- The app does not show cookies file contents in the UI.

The help text should explain:

- Install a browser extension or tool that can export Netscape-format cookies.
- Log in to the target site in the browser.
- Open the target site page.
- Export `cookies.txt`.
- Select that file in the app settings.

Cookie safety requirements:

- Warn that `cookies.txt` is sensitive login data.
- Recommend exporting only cookies for the target site when the export tool supports that.
- Validate that the selected file appears to be Netscape-format cookies before using it.
- Store only the file path in config.
- Do not copy cookies into app history, logs, crash reports, or visible UI.
- Do not print the cookies file contents in error details.

### Network/proxy settings

The first version does not include custom proxy settings. It uses the system network environment.

## Download Options

Supported download modes:

- Audio + video.
- Audio only.
- Video only.

Format controls:

- Resolution options: Auto, 2160p, 1440p, 1080p, 720p, 480p.
- FPS preference.
- Codec preference.
- Video bitrate preference.
- Audio bitrate preference.
- Container preference such as MP4 or MKV.

Bitrate behavior:

- First version treats bitrate as a selection preference among available formats.
- It does not treat bitrate as exact forced transcoding by default.
- When a requested operation requires transcoding, the UI must show that ffmpeg is required and that processing may take longer.

Selection and fallback rules:

- Resolution, FPS, codec, bitrate, and container are preferences unless the UI explicitly marks a control as strict.
- If no exact match exists, the app should choose the closest valid `yt-dlp` format and display the actual selected format before download.
- If a user's requested combination cannot be satisfied, the app should explain which preference was relaxed.
- The final queue/history item must store the actual output format summary, not only the requested preferences.
- Auto should be the recommended fallback for unsupported combinations.

Playlist and batch URL behavior:

- Multiple pasted URLs are supported.
- A single URL that expands to a playlist, channel, or collection must not silently create a large queue.
- The first version should detect playlist-like results and ask the user before expanding them.
- The UI should show the estimated number of items before expansion when `yt-dlp` can report it.
- The first version should limit playlist/channel expansion to 50 items per user confirmation and tell the user when additional items are skipped.

Analysis behavior:

- Analysis runs in a background worker and never blocks the main window.
- Each URL analysis has a default 60-second timeout.
- Users can cancel pending or running analysis jobs from the queue/status area.
- Failed analysis should be categorized when possible as login required, unavailable or private video, network timeout, unsupported URL, playlist confirmation needed, or unknown `yt-dlp` failure.
- Retry analysis is available after network timeout, unknown `yt-dlp` failure, cookies configuration, or `yt-dlp` update.
- If cancellation happens, the UI should show a canceled state rather than an error state.

## Queue and Concurrency

The app supports batch URLs and concurrent downloads.

Defaults:

- Multiple pasted links are accepted.
- Tasks enter a queue.
- Default maximum concurrent downloads is 2.
- User can configure maximum concurrency from 1 to 5.

Queue actions:

- Pause.
- Resume.
- Cancel.
- Retry failed task.
- Clear completed tasks.

Pause and resume semantics:

- Pause for pending tasks means the task remains in the queue but is not started.
- Pause for an active download is not promised as a true process-level pause in the first version.
- For active downloads, the first version may support cancel plus retry/resume using `yt-dlp` continuation behavior when available.
- The UI label should avoid implying guaranteed byte-perfect pause/resume for active downloads unless that behavior is implemented and verified.

Progress display:

- Percent.
- Download speed.
- ETA.
- Current status.
- Error summary for failed tasks.

## Persistent History

The app stores download history persistently.

History records include:

- Title.
- Original URL.
- Output path.
- Download type.
- Format summary.
- Subtitle behavior.
- Status.
- Date/time.

History actions:

- Search.
- Re-download.
- Open file.
- Open containing folder.
- Delete a single history item.
- Clear all history.
- Refresh file existence state.

History privacy and lifecycle:

- History should not store cookies, tokens, authorization headers, or raw command lines containing sensitive values.
- If the output file has been moved or deleted, opening it should show a clear Chinese message and offer to open the last known folder.
- Logs and history should use safe summaries rather than full sensitive command lines.

## Subtitles

First version supports subtitle download, embedding, and burn-in.

Language priority:

1. English.
2. Chinese.

Language matching:

- English should match common tags such as `en`, `en-US`, and `en-GB`.
- Chinese should match common tags such as `zh`, `zh-CN`, `zh-TW`, `zh-Hans`, and `zh-Hant`.
- The app should show the actual subtitle language selected.

Subtitle source preference:

- Prefer human-provided subtitles.
- Allow automatic subtitles when human subtitles are unavailable or selected by the user.

Subtitle actions:

- Download subtitle file only.
- Embed subtitle into the output container.
- Burn subtitle into the video image.

ffmpeg dependency:

- Downloading `.srt` or `.vtt` files does not require ffmpeg.
- Embedding subtitles requires ffmpeg.
- Burning subtitles requires ffmpeg and may take significantly longer.
- If ffmpeg is missing, the UI should disable or warn for embed/burn options while keeping subtitle-file download available.
- Burn-in failure should not delete the original downloaded media and should offer a fallback to subtitle-file download or embedding when possible.

## Built-In Playback During Download

The user selected built-in playback of a network stream.

Acceptance boundary:

- Built-in playback is a best-effort preview feature in the first version.
- Download correctness must not depend on playback success.
- Preview failure does not block first-version acceptance if the UI reports the failure clearly and download still works.

Behavior:

- The app does not try to play a partially written local download file.
- The app asks `yt-dlp` for a suitable playable network stream.
- The built-in player plays that stream as a preview while the actual download task runs separately.
- Playback failure must not cancel the download.
- Download failure should update task status and playback status clearly.
- If a video only exposes separated audio/video streams or streams needing unsupported headers/cookies, the app may show "preview unavailable" while keeping download options available.

First-version playback scope:

- Play.
- Pause.
- Volume.
- Loading/error status.

Out of first-version playback scope:

- Advanced playlist playback.
- Playback speed controls.
- Frame-accurate subtitle playback inside the preview player.

## ffmpeg Detection and Guidance

ffmpeg is not bundled in the first version.

Capability tiers:

- Baseline without ffmpeg: analyze videos, show formats, download single-file formats where available, download subtitle files, manage queue/history/settings.
- Enhanced with ffmpeg: merge separate audio/video streams, extract or convert audio, embed subtitles, burn subtitles, transcode when explicitly requested.
- If ffmpeg is missing, the UI must not promise enhanced operations as available.
- Acceptance on a clean Win11 x64 machine without ffmpeg is limited to the baseline tier.
- Full media-processing acceptance requires ffmpeg to be detected or configured.

The app should support:

- Automatic local search.
- User-specified local `ffmpeg.exe` path.
- Help action that opens the official ffmpeg download page.
- Help action that copies recommended installation instructions.

Search locations:

- `PATH`.
- Program directory adjacent paths such as `ffmpeg/bin/ffmpeg.exe`.
- User data directory paths such as `ffmpeg/bin/ffmpeg.exe`.
- Previously configured user path.

UI states:

- If found, show ffmpeg version and path.
- If missing, allow non-ffmpeg operations.
- If missing, clearly mark merge, embed, burn-in, extract, and transcode operations as requiring ffmpeg.
- If a high-quality YouTube format requires separate video and audio streams, the UI should explain that merging requires ffmpeg and offer install/configure actions.

## yt-dlp Packaging and Update Behavior

Package a known-good `yt-dlp.exe` with the app.

Runtime priority:

1. Updated `yt-dlp.exe` in the user data directory.
2. Bundled `yt-dlp.exe` from the packaged app.

Startup update behavior:

- Main window opens immediately.
- Background worker checks for updates.
- Startup must not block on network or update checks.
- If a new version is available, prompt the user before updating.
- Network failure, timeout, or update failure should not block use of the bundled version.

Update safety requirements:

- Use only official `yt-dlp` release sources for automatic update checks.
- Default to the stable release channel.
- If future UI exposes nightly or master channels, label them as advanced and riskier.
- Download an update to a temporary path first.
- Verify the downloaded executable can report a version before switching to it.
- Switch active `yt-dlp.exe` atomically by updating the configured active path, not by overwriting a running executable.
- Keep the previous working version for rollback.
- Do not replace or delete the active executable while analysis or download tasks are running.
- Record active version, source path, update time, and rollback version in settings or logs.

Failure-time update behavior:

- If analysis or download fails in a way that may be caused by an outdated `yt-dlp`, show an action to try updating.
- After successful update, allow re-analysis or retry.

Settings:

- "Check for yt-dlp updates on startup" toggle.
- Manual "Check for updates" action.
- Current active `yt-dlp` version and source path.

## Packaging and Distribution

Target:

- Windows 11 x64.
- Directly runnable executable or stable `dist` folder.

Packaging includes:

- Python runtime.
- PySide6 dependencies.
- App resources.
- Bundled `yt-dlp.exe`.

Packaging excludes:

- ffmpeg binaries in the first version.

License and notices:

- Distribution must include third-party license notices for bundled components.
- Notices must cover at least Python runtime, PySide6/Qt, PyInstaller-related bootloader/runtime notices where applicable, and bundled `yt-dlp.exe`.
- The app should include a visible About/Legal entry that opens the notice file.
- Packaging should preserve license files required by dependencies.

Packaging priorities:

1. Reliable startup and operation.
2. Clear file layout.
3. Then smaller package size.

Clean-machine packaging acceptance:

- Test the packaged app on Win11 x64 without system Python installed.
- Confirm PySide6 UI starts.
- Confirm Qt multimedia component loads or fails gracefully.
- Confirm bundled `yt-dlp.exe` runs.
- Confirm user-data paths are writable.
- Confirm ffmpeg-missing state is shown correctly.

## Error Handling

Errors should be shown in Chinese with practical next actions.

Important cases:

- Video requires login: suggest `cookies.txt`.
- ffmpeg missing: explain which features require ffmpeg and offer search/select/download actions.
- `yt-dlp` analysis failed: show error summary and offer update/retry.
- Network failed: show retry option.
- Output path unavailable: ask user to choose another folder.
- Unsupported stream selection: suggest Auto or a different resolution/container.
- Playlist expansion limit reached: explain how many items were accepted and how many were skipped.
- Preview unavailable: explain that download can continue without preview.

Errors should not expose raw logs as the only explanation. Raw logs can be available in an expandable details panel or log file.

## Data Storage

Use the user's application data directory for mutable state:

- Config.
- History.
- Logs.
- Updated `yt-dlp.exe`.
- Cached thumbnails if needed.

The packaged application directory should be treated as read-only.

## Testing and Acceptance

Core logic should be testable without opening the GUI:

- `yt-dlp` command construction.
- Format selection mapping.
- Subtitle option mapping.
- ffmpeg detection.
- cookies path config.
- queue concurrency scheduling.
- history read/write.
- update behavior state transitions.
- playlist expansion confirmation and limit behavior.
- history deletion and file-existence refresh.

GUI smoke checks:

- Main window opens.
- Navigation pages switch.
- URL input accepts one or more links.
- Save folder picker opens.
- Settings controls render.
- Queue task rows render.
- Error panels show Chinese messages.
- ffmpeg-missing state disables or explains enhanced operations.
- Preview failure is shown without canceling a download task.
- Legal/third-party notice entry is reachable.

Acceptance criteria:

- App starts on Win11 x64.
- UI matches the selected light reference direction.
- One or more links can be analyzed.
- Download type, resolution, bitrate preference, subtitle behavior, and save folder can be selected.
- Queue supports concurrent downloads with default concurrency 2 and configurable range 1-5.
- History is persistent and searchable.
- cookies file can be configured and explained.
- ffmpeg can be searched, selected, or explained via official download guidance.
- `yt-dlp` update checks are non-blocking.
- A packaged executable or `dist` folder can be produced for testing.
- Baseline acceptance passes without ffmpeg installed.
- Enhanced media-processing acceptance passes when ffmpeg is configured.
- Built-in playback is accepted as best-effort preview and is not required for all analyzed videos.
- Third-party license notice is included in the package.

## Known Risks

- Some sites or videos may not expose a single network stream suitable for preview playback. The app should handle preview failure separately from download failure.
- YouTube format availability changes frequently. Runtime `yt-dlp` updates reduce this risk but do not remove it.
- Subtitle burn-in can be slow and format-sensitive.
- ffmpeg absence limits merging, embedding, burn-in, extraction, and transcoding.
- PyInstaller packages with PySide6 can be large.
- Antivirus false positives may occur with PyInstaller-packaged executables and bundled downloader binaries.
- Enterprise network/proxy environments may not behave like ordinary system network access because the first version has no custom proxy UI.

## Out of Scope for First Version

- DRM bypass.
- Built-in proxy settings.
- Browser cookie extraction without user-provided `cookies.txt`.
- Full media-player feature set.
- Automatic ffmpeg bundling.
- Guaranteed preview playback for every video.
- True active-download pause/resume unless explicitly implemented and verified.
- Cloud sync.
- Mobile version.
- Browser extension.

## Next Process Steps

1. Run a design self-review on this document.
2. Open a separate design-audit thread for independent review.
3. Incorporate any audit findings that the user accepts.
4. Ask the user to review the final spec.
5. Only after user approval, invoke `superpowers:writing-plans`.
