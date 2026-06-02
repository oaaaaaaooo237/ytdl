# Function Test Matrix

Primary test URL: `https://www.youtube.com/watch?v=PqQNXB6hhUs`

Batch test URLs:

- `https://www.youtube.com/watch?v=PqQNXB6hhUs`
- `https://www.youtube.com/shorts/oXFad1nt6v0`
- `https://www.youtube.com/watch?v=svoD582Pas4`

| Area | Requirement | Evidence | Status |
| --- | --- | --- | --- |
| Analysis | Analyze real video URL without blocking startup | `scripts/real_download_matrix.ps1` analyzed the primary test URL in three modes; selected formats `18`, `251`, `160` | Pass |
| Download modes | Real download for audio+video, audio-only, and video-only | `scripts/real_download_matrix.ps1`: `audio_video_360p` 37,641,530 bytes; `audio_only_128k` 8,670,609 bytes; `video_only_144p` 7,350,423 bytes | Pass |
| Output path validation | Missing or unwritable save folder is rejected before starting download worker | `tests/test_download_progress.py::test_start_download_rejects_missing_output_folder_before_starting_worker` | Pass |
| Format preferences | Resolution, container, codec, and audio bitrate controls affect selected format | `tests/test_format_preferences_ui.py`; real matrix selected `18` for 360p mp4, `251` for 128k-near audio, `160` for 144p H.264 video-only | Pass |
| Format fallback explanation | Relaxed format preferences are shown in Chinese before download | `tests/test_format_preferences_ui.py::test_analysis_status_explains_relaxed_format_preferences` | Pass |
| Analysis failure recovery | yt-dlp analysis failures clearly offer retry and update guidance, then reset after a successful retry | `tests/test_download_progress.py::test_ytdlp_analysis_failure_offers_retry_and_update_guidance`; `tests/test_download_progress.py::test_analysis_retry_label_returns_to_analyze_after_success` | Pass |
| Queue | Progress reaches completed state | `scripts/real_download_matrix.ps1`, all matrix cases `queue_status=已完成` | Pass |
| Queue thumbnails | Queue cards can show the analyzed video thumbnail instead of a placeholder | `tests/test_queue_controls_gui.py::test_queue_card_can_render_thumbnail`; `tests/test_queue_controls_gui.py::test_main_window_adds_analysis_thumbnail_to_queue_card`; refreshed `docs/qa/screenshots/3-queue.png` | Pass |
| History | Finished download writes history | `scripts/real_download_matrix.ps1`, all matrix cases `history_count=1` | Pass |
| History output path | Finished download history records the real file emitted by yt-dlp, not the output template | `tests/test_download_progress.py::test_finished_download_history_uses_actual_output_path`; `tests/test_workers.py::test_download_worker_emits_actual_output_path_from_ytdlp_print`; `scripts/real_url_smoke.ps1` printed an existing `history_output_path` under `.qa-real-smoke/basic/20260603-001115` | Pass |
| Batch real URLs | Multiple pasted URLs can be analyzed, queued with concurrency 2, downloaded, and recorded in history | `scripts/real_batch_smoke.ps1` latest run under `.qa-real-smoke/batch/20260603-021014`: 3 real URLs, `queue_rows=3`, `queue_statuses=已完成,已完成,已完成`, `history_count=3`, `downloaded_files=3`, `downloaded_bytes=11,007,992` | Pass |
| History missing files | Opening a moved/deleted history file shows Chinese confirmation and offers last known folder | `tests/test_ui_service_wiring.py::test_history_open_missing_file_offers_last_known_folder` | Pass |
| Packaging | Build Win11 x64 dist folder | `scripts/package_win.ps1` | Pass |
| Packaged smoke | Bundled yt-dlp and bundled ffmpeg report versions | `scripts/smoke_packaged.ps1`, yt-dlp `2026.03.17`, ffmpeg `8.1-essentials_build-www.gyan.dev` | Pass |
| Packaged startup | Exe opens responsive window | Manual process smoke, title `视频地址提取器` | Pass |
| Visual diff | Strict pixel comparison with reference crops | `scripts/visual_compare.py`, `docs/qa/visual-diff.json`; latest exact: Download `1.2145%`, Formats `1.7816%`, Queue `1.5525%` | Not pass |
| Navigation visual rail | Left navigation uses visible icon+Chinese-label widgets without ellipsis, duplicate native text/icons, overlap, or scrollbar artifacts | `tests/test_gui_smoke.py::test_navigation_matches_reference_icon_rail`; `tests/test_gui_smoke.py::test_navigation_custom_widgets_avoid_native_text_duplication`; `tests/test_gui_smoke.py::test_navigation_icon_and_label_do_not_overlap`; refreshed `docs/qa/screenshots/1-download.png` | Pass |
| Cookies settings | Select and validate cookies.txt from GUI | `tests/test_settings_actions.py` covers choose/validate/clear and path-only storage | Pass |
| History actions | Search/open folder/clear from GUI | `tests/test_ui_service_wiring.py` covers search/open/clear; history write covered by real smoke | Pass |
| Preview | Play-while-downloading obtains stream and failure does not block download | `scripts/real_download_matrix.ps1`, `audio_video_360p` loaded a real preview URL while real download completed | Pass |
| Subtitles | Real subtitle-file download and ffmpeg burn-in preserve source files and record burned output | `scripts/real_subtitle_smoke.ps1` with `https://www.youtube.com/shorts/oXFad1nt6v0`: subtitle file case `subtitle_files=2`; burn case output `.burned.mp4`, `media_files=2`, `downloaded_bytes=10,634,514` | Pass |
| Queue controls | Pause/resume/cancel/retry from GUI | `tests/test_queue_controls_gui.py` covers task action buttons, cancel, and retry restart; core queue manager covers pause/resume states | Pass |
| Playlist protection | Playlist-like analysis failures must probe entries, ask before expanding, and limit to 50 items | `tests/test_ui_service_wiring.py::test_playlist_analysis_failure_probes_and_confirms_before_expanding`; `tests/test_format_subtitle_analysis.py::test_extract_playlist_urls_limits_to_50_and_reports_skipped` | Pass |
| Legal notice | About page third-party license entry opens bundled notice file | `tests/test_ui_service_wiring.py::test_about_legal_button_opens_third_party_notice_file` | Pass |
