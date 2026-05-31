# Function Test Matrix

Test URL: `https://www.youtube.com/watch?v=KYDPpt3eqaQ`

| Area | Requirement | Evidence | Status |
| --- | --- | --- | --- |
| Analysis | Analyze real video URL without blocking startup | `scripts/real_url_smoke.ps1` selects metadata and format | Pass |
| Download | Download selected audio-only format from real URL | `scripts/real_url_smoke.ps1`, `format_id=140`, `downloaded_bytes=23382701` | Pass |
| Queue | Progress reaches completed state | `scripts/real_url_smoke.ps1`, `queue_status=已完成` | Pass |
| History | Finished download writes history | `scripts/real_url_smoke.ps1`, `history_count=1` | Pass |
| Packaging | Build Win11 x64 dist folder | `scripts/package_win.ps1` | Pass |
| Packaged smoke | Bundled yt-dlp reports version | `scripts/smoke_packaged.ps1`, `2026.03.17` | Pass |
| Packaged startup | Exe opens responsive window | Manual process smoke, title `视频地址提取器` | Pass |
| Visual diff | Strict pixel comparison with reference crops | `scripts/visual_compare.py`, `docs/qa/visual-diff.json` | Not pass |
| Cookies settings | Select and validate cookies.txt from GUI | `tests/test_settings_actions.py` covers choose/validate/clear and path-only storage | Pass |
| History actions | Search/open folder/clear from GUI | `tests/test_ui_service_wiring.py` covers search/open/clear; history write covered by real smoke | Pass |
| Preview | Play-while-downloading obtains stream and failure does not block download | Preview failure covered; stream integration missing | Not complete |
| Queue controls | Pause/resume/cancel/retry from GUI | Core queue manager covered; GUI controls incomplete | Not complete |
