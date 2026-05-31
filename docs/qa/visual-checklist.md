# Visual QA Checklist

- Download page screenshot shows left navigation, URL input, options, teal primary button, preview area, and Chinese labels.
- Formats page screenshot shows resolution, container, bitrate, actual format, and subtitle controls without text overlap.
- Queue page screenshot shows title, status, progress, speed, ETA, and actions.
- History page screenshot shows search and history table.
- Settings page screenshot shows save folder, cookies.txt, ffmpeg, concurrency, and update controls.
- About page screenshot shows app version, yt-dlp status, ffmpeg state, and third-party license entry.
- UI follows `docs/gui-reference.png`: light Win11 surfaces, teal primary action, compact utility layout, and left navigation.

## Current QA Evidence

- Full pytest: pass.
- Compile check: pass.
- Packaged build: `dist/YTDL-GUI/YTDL-GUI.exe` generated.
- Packaged smoke: bundled `yt-dlp.exe --version` prints `2026.03.17`.
- Packaged executable startup smoke: `YTDL-GUI.exe` starts, window title is `视频地址提取器`, process responds.
- Real URL analysis smoke uses `https://www.youtube.com/watch?v=KYDPpt3eqaQ`.
- Real GUI-logic download smoke completed in audio-only mode, selected format `140`, wrote history, and downloaded `23,382,701` bytes.
- Current screenshots are saved under `docs/qa/screenshots/`.

## Visual Status

- Current UI has been moved toward the selected reference: compact left rail, light Win11 surfaces, teal primary actions, staged download flow, format segments, and queue cards.
- Pixel-perfect equality with `docs/gui-reference.png` remains unproven and is not claimed here.
