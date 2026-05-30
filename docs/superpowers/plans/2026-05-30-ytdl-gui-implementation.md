# YTDL GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a polished Win11 x64 desktop GUI downloader that uses PySide6, bundled `yt-dlp.exe`, optional local ffmpeg, non-blocking updates, queue/history, cookies guidance, and best-effort preview playback.

**Architecture:** The application is split into testable core services and a PySide6 UI shell. Core services own paths, config, history, ffmpeg detection, `yt-dlp` command construction, format/subtitle selection, update safety, and queue scheduling; UI pages call those services through workers so network and subprocess work never blocks startup or the main window.

**Tech Stack:** Python 3.11+, PySide6, pytest, pytest-qt, PyInstaller, bundled `yt-dlp.exe`, optional user-provided `ffmpeg.exe`, JSON files in the user data directory.

---

## Audit Closure Gate

The design audit issues have been reviewed against `docs/superpowers/specs/2026-05-30-ytdl-gui-design.md` before writing this plan.

- P0 ffmpeg conflict: resolved by baseline/enhanced capability tiers.
- P0 built-in playback overpromise: resolved by treating playback as best-effort preview only.
- P0 yt-dlp update safety: resolved by official source, temp download, validation, atomic active-path switch, and rollback version.
- P0 license notices: resolved by explicit third-party notice requirements.
- P1 pause/resume ambiguity: resolved by pending pause and active cancel/retry continuation semantics.
- P1 subtitle scope: resolved by exact language tag matching and ffmpeg-gated embed/burn behavior.
- P1 format conflict rules: resolved by preference/fallback/actual selected format summary requirements.
- P1 cookies safety: resolved by sensitive-data warning, Netscape-format validation, path-only storage, and log redaction.
- P1 playlist expansion: resolved by explicit confirmation and 50-item expansion limit.
- P1 history privacy: resolved by delete/clear/refresh actions and sensitive-command exclusion.
- P1 non-YouTube boundary: resolved by YouTube core acceptance and other-site best-effort scope.
- P1 packaging acceptance: resolved by clean Win11 x64 smoke requirements.
- P2 proxy semantics: resolved by no first-version custom proxy UI and explicit risk note.
- P2 analysis behavior: resolved by 60-second analysis timeout, cancellation, retry, and categorized failures.
- P2 visual acceptance: resolved by a screenshot-based checklist tied to `docs/gui-reference.png`.

## Repository and File Structure

Create the Python application under `D:\garyapp\ytdl`:

- `pyproject.toml`: package metadata and pytest settings.
- `requirements.txt`: runtime dependencies.
- `requirements-dev.txt`: runtime plus test/build dependencies.
- `README.md`: local run, packaging, ffmpeg, cookies, and license guidance.
- `src/ytdl_gui/__init__.py`: package version.
- `src/ytdl_gui/main.py`: CLI entry point for the packaged app.
- `src/ytdl_gui/app.py`: QApplication bootstrap, theme loading, service wiring.
- `src/ytdl_gui/paths.py`: app data, bundled tool, updated tool, and resource paths.
- `src/ytdl_gui/models.py`: dataclasses/enums shared by services and UI.
- `src/ytdl_gui/config_store.py`: JSON config persistence.
- `src/ytdl_gui/history_store.py`: JSON history persistence and privacy-safe records.
- `src/ytdl_gui/cookies.py`: Netscape cookies validation and user-facing help text.
- `src/ytdl_gui/ffmpeg.py`: ffmpeg discovery, version probing, and help URLs.
- `src/ytdl_gui/ytdlp_runner.py`: subprocess wrapper for analysis, stream extraction, and download commands.
- `src/ytdl_gui/format_selector.py`: format preference and fallback logic.
- `src/ytdl_gui/subtitles.py`: subtitle language matching and ffmpeg dependency rules.
- `src/ytdl_gui/update_manager.py`: non-blocking update state transitions, temp download validation, active-path switch, rollback.
- `src/ytdl_gui/queue_manager.py`: in-memory queue scheduling, concurrency, cancel/retry state transitions.
- `src/ytdl_gui/workers.py`: Qt worker objects for analysis, update checks, downloads, and ffmpeg search.
- `src/ytdl_gui/ui/theme.py`: light Win11-style palette and spacing constants.
- `src/ytdl_gui/ui/main_window.py`: navigation shell and page wiring.
- `src/ytdl_gui/ui/pages/download_page.py`: URL input, analysis, save folder, basic options, start action.
- `src/ytdl_gui/ui/pages/formats_page.py`: resolution, FPS, codec, bitrate, container, subtitle controls.
- `src/ytdl_gui/ui/pages/queue_page.py`: queue table and task actions.
- `src/ytdl_gui/ui/pages/history_page.py`: search, re-download, open file/folder, delete, clear.
- `src/ytdl_gui/ui/pages/settings_page.py`: default folder, concurrency, cookies, ffmpeg, update preferences.
- `src/ytdl_gui/ui/pages/about_page.py`: versions, ffmpeg status, legal notice access.
- `src/ytdl_gui/ui/widgets.py`: reusable labeled controls, status badges, path row, error panel.
- `src/ytdl_gui/ui/player.py`: best-effort Qt multimedia preview wrapper.
- `tools/yt-dlp.exe`: bundled known-good executable.
- `licenses/THIRD_PARTY_NOTICES.txt`: bundled dependency notices.
- `packaging/ytdl_gui.spec`: PyInstaller spec.
- `scripts/fetch_ytdlp.ps1`: official-source helper to download and validate bundled `yt-dlp.exe`.
- `scripts/package_win.ps1`: build command for the packaged app.
- `scripts/smoke_packaged.ps1`: clean-machine style smoke checks.
- `tests/`: pytest coverage for core services plus GUI smoke tests.

## Task 0: Project Scaffold and Tooling

**Files:**
- Create: `pyproject.toml`
- Create: `requirements.txt`
- Create: `requirements-dev.txt`
- Create: `README.md`
- Create: `src/ytdl_gui/__init__.py`
- Create: `src/ytdl_gui/main.py`
- Create: `tests/conftest.py`

- [ ] **Step 1: Initialize git for implementation tracking**

Run:

```powershell
git init
git status --short
```

Expected: `git status --short` exits 0 and lists the existing `docs/` files as untracked.

- [ ] **Step 2: Create dependency files**

Create `requirements.txt`:

```txt
PySide6==6.8.1.1
```

Create `requirements-dev.txt`:

```txt
-r requirements.txt
pytest==8.3.4
pytest-qt==4.4.0
pyinstaller==6.11.1
```

- [ ] **Step 3: Create package metadata**

Create `pyproject.toml`:

```toml
[build-system]
requires = ["setuptools>=69"]
build-backend = "setuptools.build_meta"

[project]
name = "ytdl-gui"
version = "0.1.0"
description = "Win11 desktop GUI for YouTube-first yt-dlp downloads"
requires-python = ">=3.11"
dependencies = ["PySide6==6.8.1.1"]

[tool.setuptools.packages.find]
where = ["src"]

[tool.pytest.ini_options]
testpaths = ["tests"]
pythonpath = ["src"]
addopts = "-q"
```

- [ ] **Step 4: Create README**

Create `README.md`:

````markdown
# YTDL GUI

Win11 x64 desktop GUI for YouTube-first `yt-dlp` downloads.

## Local Run

```powershell
.\.venv\Scripts\python.exe -m ytdl_gui.main
```

## Scope

- Public YouTube videos are the first-version core target.
- Other `yt-dlp` supported sites are best-effort.
- DRM bypass and automatic browser cookie extraction are not included.

## cookies.txt

`cookies.txt` may contain sensitive login data. Export Netscape-format cookies with a browser extension or trusted tool, preferably only for the target site, then select that file in Settings.

## ffmpeg

ffmpeg is not bundled in the first version. Without ffmpeg, baseline operations still work where single-file formats are available. Merge, audio extraction, subtitle embed, burn-in, and transcode features require a local `ffmpeg.exe`.
````

- [ ] **Step 5: Create the package entry point**

Create `src/ytdl_gui/__init__.py`:

```python
__version__ = "0.1.0"
```

Create `src/ytdl_gui/main.py`:

```python
from ytdl_gui.app import run


def main() -> int:
    return run()


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 6: Create pytest base config**

Create `tests/conftest.py`:

```python
from pathlib import Path

import pytest


@pytest.fixture
def app_data_dir(tmp_path: Path) -> Path:
    path = tmp_path / "YTDLGui"
    path.mkdir()
    return path
```

- [ ] **Step 7: Install dependencies**

If `python` is not on PATH in this workspace, use:

```powershell
$PY = "C:\Users\garyr\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
& $PY -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

Expected: pip installs PySide6, pytest, pytest-qt, and PyInstaller without dependency conflicts.

- [ ] **Step 8: Commit scaffold**

Run:

```powershell
git add pyproject.toml requirements.txt requirements-dev.txt README.md src tests
git commit -m "chore: scaffold ytdl gui project"
```

Expected: commit succeeds with scaffold files tracked.

## Task 1: Paths, Config, History, and Cookie Safety

**Files:**
- Create: `src/ytdl_gui/paths.py`
- Create: `src/ytdl_gui/models.py`
- Create: `src/ytdl_gui/config_store.py`
- Create: `src/ytdl_gui/history_store.py`
- Create: `src/ytdl_gui/cookies.py`
- Create: `tests/test_config_history_cookies.py`

- [ ] **Step 1: Write failing tests**

Create `tests/test_config_history_cookies.py`:

```python
from pathlib import Path

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.cookies import cookie_help_text, validate_netscape_cookies
from ytdl_gui.history_store import HistoryRecord, HistoryStore


def test_config_roundtrip_stores_cookie_path_only(app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    config = AppConfig(default_save_dir="D:/Downloads", cookies_path="D:/secrets/cookies.txt", max_concurrency=3)
    store.save(config)

    loaded = store.load()

    assert loaded.default_save_dir == "D:/Downloads"
    assert loaded.cookies_path == "D:/secrets/cookies.txt"
    assert loaded.max_concurrency == 3
    assert "cookies.txt" in store.path.read_text(encoding="utf-8")
    assert "SID=" not in store.path.read_text(encoding="utf-8")


def test_history_redacts_sensitive_values(app_data_dir: Path):
    store = HistoryStore(app_data_dir)
    record = HistoryRecord(
        title="Example",
        url="https://www.youtube.com/watch?v=abc",
        output_path="D:/Downloads/example.mp4",
        download_type="audio_video",
        format_summary="1080p mp4 + m4a",
        subtitle_behavior="none",
        status="finished",
        created_at="2026-05-30T22:00:00",
    )
    store.add(record)

    payload = store.path.read_text(encoding="utf-8")

    assert "Example" in payload
    assert "--cookies" not in payload
    assert "SID=" not in payload


def test_cookie_validation_accepts_netscape_header(tmp_path: Path):
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tFALSE\t0\tNAME\tVALUE\n", encoding="utf-8")

    result = validate_netscape_cookies(cookie_file)

    assert result.ok is True
    assert result.message == "cookies.txt 格式看起来有效"


def test_cookie_validation_rejects_random_text(tmp_path: Path):
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text("not cookies", encoding="utf-8")

    result = validate_netscape_cookies(cookie_file)

    assert result.ok is False
    assert "Netscape" in result.message


def test_cookie_help_mentions_sensitive_data():
    text = cookie_help_text()

    assert "敏感登录数据" in text
    assert "Netscape" in text
    assert "浏览器扩展" in text
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_config_history_cookies.py -q
```

Expected: import errors for `ytdl_gui.config_store`, `ytdl_gui.history_store`, and `ytdl_gui.cookies`.

- [ ] **Step 3: Implement models and storage**

Create `src/ytdl_gui/models.py`:

```python
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class ValidationResult:
    ok: bool
    message: str


@dataclass
class Serializable:
    def to_dict(self) -> dict:
        return asdict(self)
```

Create `src/ytdl_gui/paths.py`:

```python
import os
import sys
from pathlib import Path


APP_NAME = "YTDLGui"


def app_data_dir() -> Path:
    base = os.environ.get("APPDATA")
    if base:
        path = Path(base) / APP_NAME
    else:
        path = Path.home() / f".{APP_NAME.lower()}"
    path.mkdir(parents=True, exist_ok=True)
    return path


def resource_root() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys._MEIPASS)  # type: ignore[attr-defined]
    return Path(__file__).resolve().parents[2]


def bundled_ytdlp_path() -> Path:
    return resource_root() / "tools" / "yt-dlp.exe"


def updated_ytdlp_path(data_dir: Path | None = None) -> Path:
    root = data_dir or app_data_dir()
    return root / "tools" / "yt-dlp.exe"
```

Create `src/ytdl_gui/config_store.py`:

```python
import json
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass
class AppConfig:
    default_save_dir: str = ""
    cookies_path: str = ""
    ffmpeg_path: str = ""
    max_concurrency: int = 2
    check_ytdlp_updates_on_startup: bool = True
    active_ytdlp_path: str = ""
    active_ytdlp_version: str = ""
    rollback_ytdlp_path: str = ""


class ConfigStore:
    def __init__(self, data_dir: Path):
        self.path = data_dir / "config.json"

    def load(self) -> AppConfig:
        if not self.path.exists():
            return AppConfig()
        payload = json.loads(self.path.read_text(encoding="utf-8"))
        return AppConfig(**{**asdict(AppConfig()), **payload})

    def save(self, config: AppConfig) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(asdict(config), ensure_ascii=False, indent=2), encoding="utf-8")
```

Create `src/ytdl_gui/history_store.py`:

```python
import json
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass
class HistoryRecord:
    title: str
    url: str
    output_path: str
    download_type: str
    format_summary: str
    subtitle_behavior: str
    status: str
    created_at: str


class HistoryStore:
    def __init__(self, data_dir: Path):
        self.path = data_dir / "history.json"

    def list(self) -> list[HistoryRecord]:
        if not self.path.exists():
            return []
        rows = json.loads(self.path.read_text(encoding="utf-8"))
        return [HistoryRecord(**row) for row in rows]

    def add(self, record: HistoryRecord) -> None:
        rows = self.list()
        rows.append(record)
        self._save_all(rows)

    def delete(self, index: int) -> None:
        rows = self.list()
        del rows[index]
        self._save_all(rows)

    def clear(self) -> None:
        self._save_all([])

    def _save_all(self, rows: list[HistoryRecord]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        safe_rows = [asdict(row) for row in rows]
        self.path.write_text(json.dumps(safe_rows, ensure_ascii=False, indent=2), encoding="utf-8")
```

Create `src/ytdl_gui/cookies.py`:

```python
from pathlib import Path

from ytdl_gui.models import ValidationResult


def validate_netscape_cookies(path: Path) -> ValidationResult:
    if not path.exists() or not path.is_file():
        return ValidationResult(False, "未找到 cookies.txt 文件")
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = [line for line in text.splitlines() if line.strip()]
    if not lines or "Netscape HTTP Cookie File" not in lines[0]:
        return ValidationResult(False, "请选择 Netscape 格式的 cookies.txt 文件")
    cookie_rows = [line for line in lines if not line.startswith("#")]
    if not cookie_rows:
        return ValidationResult(False, "cookies.txt 中没有可用的 cookie 记录")
    for row in cookie_rows:
        if len(row.split("\t")) < 7:
            return ValidationResult(False, "cookies.txt 行格式不完整，请重新导出 Netscape 格式文件")
    return ValidationResult(True, "cookies.txt 格式看起来有效")


def cookie_help_text() -> str:
    return (
        "cookies.txt 是敏感登录数据。请优先使用浏览器扩展只导出目标网站的 Netscape 格式 cookies，"
        "导出后在设置页选择该文件。本程序只保存文件路径，不显示、不复制、不写入日志中的 cookies 内容。"
    )
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_config_history_cookies.py -q
```

Expected: `5 passed`.

- [ ] **Step 5: Commit storage layer**

Run:

```powershell
git add src/ytdl_gui tests/test_config_history_cookies.py
git commit -m "feat: add config history and cookie safety"
```

Expected: commit succeeds.

## Task 2: ffmpeg Detection and Capability Tiers

**Files:**
- Create: `src/ytdl_gui/ffmpeg.py`
- Create: `tests/test_ffmpeg_detection.py`

- [ ] **Step 1: Write failing tests**

Create `tests/test_ffmpeg_detection.py`:

```python
from pathlib import Path

from ytdl_gui.ffmpeg import FfmpegStatus, find_ffmpeg, ffmpeg_help_url, media_capability_tier


def test_missing_ffmpeg_is_baseline_only(tmp_path: Path):
    status = find_ffmpeg(search_paths=[tmp_path], env_path="")

    assert status.found is False
    assert media_capability_tier(status) == "baseline"
    assert "ffmpeg.org" in ffmpeg_help_url()


def test_configured_ffmpeg_path_wins(tmp_path: Path):
    exe = tmp_path / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(configured_path=exe, search_paths=[], env_path="")

    assert status == FfmpegStatus(found=True, path=exe, version="未检测版本")
    assert media_capability_tier(status) == "enhanced"


def test_adjacent_bin_search(tmp_path: Path):
    exe = tmp_path / "ffmpeg" / "bin" / "ffmpeg.exe"
    exe.parent.mkdir(parents=True)
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(search_paths=[tmp_path], env_path="")

    assert status.found is True
    assert status.path == exe
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_ffmpeg_detection.py -q
```

Expected: import error for `ytdl_gui.ffmpeg`.

- [ ] **Step 3: Implement ffmpeg detection**

Create `src/ytdl_gui/ffmpeg.py`:

```python
import os
import subprocess
import json
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class FfmpegStatus:
    found: bool
    path: Path | None
    version: str


def ffmpeg_help_url() -> str:
    return "https://ffmpeg.org/download.html"


def media_capability_tier(status: FfmpegStatus) -> str:
    return "enhanced" if status.found else "baseline"


def find_ffmpeg(
    configured_path: Path | None = None,
    search_paths: list[Path] | None = None,
    env_path: str | None = None,
) -> FfmpegStatus:
    candidates: list[Path] = []
    if configured_path:
        candidates.append(configured_path)
    for root in search_paths or []:
        candidates.extend([root / "ffmpeg.exe", root / "ffmpeg" / "bin" / "ffmpeg.exe"])
    for segment in (env_path if env_path is not None else os.environ.get("PATH", "")).split(os.pathsep):
        if segment:
            candidates.append(Path(segment) / "ffmpeg.exe")

    for candidate in candidates:
        if candidate.exists() and candidate.is_file():
            return FfmpegStatus(True, candidate, _probe_version(candidate))
    return FfmpegStatus(False, None, "")


def _probe_version(path: Path) -> str:
    try:
        result = subprocess.run([str(path), "-version"], capture_output=True, text=True, timeout=5, check=False)
    except OSError:
        return "未检测版本"
    except subprocess.TimeoutExpired:
        return "未检测版本"
    first_line = result.stdout.splitlines()[0] if result.stdout.splitlines() else ""
    return first_line or "未检测版本"
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_ffmpeg_detection.py -q
```

Expected: `3 passed`.

- [ ] **Step 5: Commit ffmpeg detection**

Run:

```powershell
git add src/ytdl_gui/ffmpeg.py tests/test_ffmpeg_detection.py
git commit -m "feat: detect ffmpeg capability tier"
```

Expected: commit succeeds.

## Task 3: Format, Subtitle, and Analysis Rules

**Files:**
- Create: `src/ytdl_gui/format_selector.py`
- Create: `src/ytdl_gui/subtitles.py`
- Create: `src/ytdl_gui/ytdlp_runner.py`
- Create: `tests/test_format_subtitle_analysis.py`

- [ ] **Step 1: Write failing tests**

Create `tests/test_format_subtitle_analysis.py`:

```python
from ytdl_gui.format_selector import FormatPreference, choose_format
from ytdl_gui.subtitles import choose_subtitle_language, subtitle_action_requires_ffmpeg
from ytdl_gui.ytdlp_runner import AnalysisFailureKind, categorize_analysis_error, playlist_limit_message


def test_choose_format_relaxes_resolution_and_reports_actual():
    formats = [
        {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
        {"format_id": "22", "height": 720, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
    ]

    result = choose_format(formats, FormatPreference(resolution=1080, container="mp4"))

    assert result.format_id == "22"
    assert result.actual_summary == "720p mp4 avc1/mp4a 30fps"
    assert result.relaxed == ["resolution"]


def test_auto_format_picks_highest_single_file():
    formats = [
        {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
        {"format_id": "22", "height": 720, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
    ]

    result = choose_format(formats, FormatPreference(resolution=None, container="mp4"))

    assert result.format_id == "22"
    assert result.relaxed == []


def test_subtitle_language_priority_matches_common_tags():
    subtitles = {"zh-Hant": [{}], "en-US": [{}]}

    assert choose_subtitle_language(subtitles, ["en", "zh"]) == "en-US"
    assert choose_subtitle_language(subtitles, ["zh"]) == "zh-Hant"


def test_subtitle_ffmpeg_requirements():
    assert subtitle_action_requires_ffmpeg("file") is False
    assert subtitle_action_requires_ffmpeg("embed") is True
    assert subtitle_action_requires_ffmpeg("burn") is True


def test_analysis_error_categories():
    assert categorize_analysis_error("Sign in to confirm your age") == AnalysisFailureKind.LOGIN_REQUIRED
    assert categorize_analysis_error("This video is unavailable") == AnalysisFailureKind.UNAVAILABLE
    assert categorize_analysis_error("timed out") == AnalysisFailureKind.NETWORK_TIMEOUT
    assert categorize_analysis_error("Unsupported URL") == AnalysisFailureKind.UNSUPPORTED_URL
    assert categorize_analysis_error("traceback") == AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE


def test_playlist_limit_message_mentions_limit():
    assert "50" in playlist_limit_message(accepted=50, skipped=12)
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_format_subtitle_analysis.py -q
```

Expected: import errors for `format_selector`, `subtitles`, and `ytdlp_runner`.

- [ ] **Step 3: Implement format selection**

Create `src/ytdl_gui/format_selector.py`:

```python
from dataclasses import dataclass, field


@dataclass(frozen=True)
class FormatPreference:
    resolution: int | None = None
    container: str = "mp4"
    fps: int | None = None
    codec: str | None = None
    video_bitrate: int | None = None
    audio_bitrate: int | None = None


@dataclass(frozen=True)
class FormatChoice:
    format_id: str
    actual_summary: str
    relaxed: list[str] = field(default_factory=list)


def choose_format(formats: list[dict], preference: FormatPreference) -> FormatChoice:
    single_file = [item for item in formats if item.get("vcodec") != "none" and item.get("acodec") != "none"]
    container_matches = [item for item in single_file if item.get("ext") == preference.container]
    candidates = container_matches or single_file
    if not candidates:
        raise ValueError("没有可用的单文件格式")

    relaxed: list[str] = []
    if preference.resolution is not None:
        under_or_equal = [item for item in candidates if (item.get("height") or 0) <= preference.resolution]
        if under_or_equal:
            candidates = under_or_equal
    selected = max(candidates, key=lambda item: (item.get("height") or 0, item.get("fps") or 0))

    if preference.resolution is not None and selected.get("height") != preference.resolution:
        relaxed.append("resolution")
    if selected.get("ext") != preference.container:
        relaxed.append("container")

    return FormatChoice(str(selected["format_id"]), _summary(selected), relaxed)


def _summary(item: dict) -> str:
    height = item.get("height") or "unknown"
    ext = item.get("ext") or "unknown"
    vcodec = item.get("vcodec") or "unknown"
    acodec = item.get("acodec") or "unknown"
    fps = item.get("fps") or "unknown"
    return f"{height}p {ext} {vcodec}/{acodec} {fps}fps"
```

- [ ] **Step 4: Implement subtitle and analysis helpers**

Create `src/ytdl_gui/subtitles.py`:

```python
LANGUAGE_ALIASES = {
    "en": ("en", "en-US", "en-GB"),
    "zh": ("zh", "zh-CN", "zh-TW", "zh-Hans", "zh-Hant"),
}


def choose_subtitle_language(subtitles: dict[str, list], priority: list[str]) -> str:
    for desired in priority:
        for tag in LANGUAGE_ALIASES.get(desired, (desired,)):
            if tag in subtitles:
                return tag
    return ""


def subtitle_action_requires_ffmpeg(action: str) -> bool:
    return action in {"embed", "burn"}
```

Create `src/ytdl_gui/ytdlp_runner.py`:

```python
from enum import Enum


PLAYLIST_EXPANSION_LIMIT = 50


class AnalysisFailureKind(str, Enum):
    LOGIN_REQUIRED = "login_required"
    UNAVAILABLE = "unavailable"
    NETWORK_TIMEOUT = "network_timeout"
    UNSUPPORTED_URL = "unsupported_url"
    PLAYLIST_CONFIRMATION_NEEDED = "playlist_confirmation_needed"
    UNKNOWN_YTDLP_FAILURE = "unknown_ytdlp_failure"
    CANCELED = "canceled"


def categorize_analysis_error(stderr: str) -> AnalysisFailureKind:
    text = stderr.lower()
    if "sign in" in text or "login" in text or "age" in text:
        return AnalysisFailureKind.LOGIN_REQUIRED
    if "unavailable" in text or "private" in text or "removed" in text:
        return AnalysisFailureKind.UNAVAILABLE
    if "timed out" in text or "timeout" in text:
        return AnalysisFailureKind.NETWORK_TIMEOUT
    if "unsupported url" in text:
        return AnalysisFailureKind.UNSUPPORTED_URL
    return AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE


def playlist_limit_message(accepted: int, skipped: int) -> str:
    return f"播放列表最多展开 {PLAYLIST_EXPANSION_LIMIT} 项；已加入 {accepted} 项，跳过 {skipped} 项。"
```

- [ ] **Step 5: Run tests to verify pass**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_format_subtitle_analysis.py -q
```

Expected: `6 passed`.

- [ ] **Step 6: Commit selection rules**

Run:

```powershell
git add src/ytdl_gui/format_selector.py src/ytdl_gui/subtitles.py src/ytdl_gui/ytdlp_runner.py tests/test_format_subtitle_analysis.py
git commit -m "feat: add media selection and analysis rules"
```

Expected: commit succeeds.

## Task 4: yt-dlp Commands and Update Safety

**Files:**
- Modify: `src/ytdl_gui/ytdlp_runner.py`
- Create: `src/ytdl_gui/update_manager.py`
- Create: `tests/test_ytdlp_update.py`

- [ ] **Step 1: Write failing tests**

Create `tests/test_ytdlp_update.py`:

```python
from pathlib import Path

from ytdl_gui.update_manager import UpdateManager, UpdateResult
from ytdl_gui.ytdlp_runner import YtdlpCommandBuilder


def test_analysis_command_uses_timeout_cookies_and_no_playlist(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    cookies = tmp_path / "cookies.txt"
    builder = YtdlpCommandBuilder(exe)

    command = builder.analysis_command("https://www.youtube.com/watch?v=abc", cookies_path=cookies)

    assert command[:2] == [str(exe), "--dump-single-json"]
    assert "--no-playlist" in command
    assert "--cookies" in command
    assert str(cookies) in command


def test_download_command_uses_output_path_and_format(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    output = tmp_path / "%(title)s.%(ext)s"
    builder = YtdlpCommandBuilder(exe)

    command = builder.download_command("https://www.youtube.com/watch?v=abc", output, "22")

    assert "-f" in command
    assert "22" in command
    assert "-o" in command
    assert str(output) in command


def test_update_switches_active_path_after_validation(tmp_path: Path):
    active = tmp_path / "active" / "yt-dlp.exe"
    active.parent.mkdir()
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(version="2026.05.30", downloaded_path=_fake_exe(tmp_path / "new.exe"))

    assert result == UpdateResult.UPDATED
    assert manager.state.active_version == "2026.05.30"
    assert manager.state.rollback_path == str(active)
    assert Path(manager.state.active_path).exists()


def test_update_rejects_missing_download(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(version="2026.05.30", downloaded_path=tmp_path / "missing.exe")

    assert result == UpdateResult.INVALID_DOWNLOAD
    assert manager.state.active_path == str(active)


def _fake_exe(path: Path) -> Path:
    path.write_text("new", encoding="utf-8")
    return path
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_ytdlp_update.py -q
```

Expected: import or attribute errors for `YtdlpCommandBuilder` and `UpdateManager`.

- [ ] **Step 3: Extend yt-dlp command builder**

Add this to `src/ytdl_gui/ytdlp_runner.py`:

```python
from pathlib import Path


class YtdlpCommandBuilder:
    def __init__(self, executable: Path):
        self.executable = executable

    def analysis_command(self, url: str, cookies_path: Path | None = None) -> list[str]:
        command = [str(self.executable), "--dump-single-json", "--no-warnings", "--no-playlist", url]
        if cookies_path:
            command[1:1] = ["--cookies", str(cookies_path)]
        return command

    def playlist_probe_command(self, url: str, cookies_path: Path | None = None) -> list[str]:
        command = [str(self.executable), "--dump-single-json", "--flat-playlist", url]
        if cookies_path:
            command[1:1] = ["--cookies", str(cookies_path)]
        return command

    def download_command(self, url: str, output_template: Path, format_id: str, cookies_path: Path | None = None) -> list[str]:
        command = [str(self.executable), "--newline", "-f", format_id, "-o", str(output_template), url]
        if cookies_path:
            command[1:1] = ["--cookies", str(cookies_path)]
        return command

    def preview_url_command(self, url: str, format_id: str | None = None, cookies_path: Path | None = None) -> list[str]:
        command = [str(self.executable), "-g"]
        if format_id:
            command.extend(["-f", format_id])
        if cookies_path:
            command.extend(["--cookies", str(cookies_path)])
        command.append(url)
        return command
```

- [ ] **Step 4: Implement update manager state transitions**

Create `src/ytdl_gui/update_manager.py`:

```python
import json
import shutil
from dataclasses import asdict, dataclass
from enum import Enum
from pathlib import Path


OFFICIAL_YTDLP_EXE_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"


class UpdateResult(str, Enum):
    UPDATED = "updated"
    INVALID_DOWNLOAD = "invalid_download"
    BUSY = "busy"


@dataclass
class UpdateState:
    active_path: str
    active_version: str = ""
    rollback_path: str = ""
    source_url: str = OFFICIAL_YTDLP_EXE_URL


class UpdateManager:
    def __init__(self, data_dir: Path, active_path: Path):
        self.data_dir = data_dir
        self.state_path = data_dir / "ytdlp-update-state.json"
        self.state = UpdateState(active_path=str(active_path))

    def accept_downloaded_update(self, version: str, downloaded_path: Path, tasks_running: bool = False) -> UpdateResult:
        if tasks_running:
            return UpdateResult.BUSY
        if not downloaded_path.exists() or not downloaded_path.is_file():
            return UpdateResult.INVALID_DOWNLOAD
        target_dir = self.data_dir / "tools" / "yt-dlp" / version
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / "yt-dlp.exe"
        shutil.copy2(downloaded_path, target)
        previous = self.state.active_path
        self.state = UpdateState(active_path=str(target), active_version=version, rollback_path=previous)
        self._save_state()
        return UpdateResult.UPDATED

    def rollback(self) -> bool:
        rollback_path = Path(self.state.rollback_path)
        if not rollback_path.exists():
            return False
        self.state = UpdateState(active_path=str(rollback_path), active_version="", rollback_path="")
        self._save_state()
        return True

    def _save_state(self) -> None:
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        self.state_path.write_text(json.dumps(asdict(self.state), ensure_ascii=False, indent=2), encoding="utf-8")
```

- [ ] **Step 5: Run tests to verify pass**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_ytdlp_update.py -q
```

Expected: `4 passed`.

- [ ] **Step 6: Commit yt-dlp command and update safety**

Run:

```powershell
git add src/ytdl_gui/ytdlp_runner.py src/ytdl_gui/update_manager.py tests/test_ytdlp_update.py
git commit -m "feat: add ytdlp commands and update state"
```

Expected: commit succeeds.

## Task 5: Queue Scheduling and Task States

**Files:**
- Create: `src/ytdl_gui/queue_manager.py`
- Create: `tests/test_queue_manager.py`

- [ ] **Step 1: Write failing tests**

Create `tests/test_queue_manager.py`:

```python
from ytdl_gui.queue_manager import DownloadTask, QueueManager, TaskStatus


def test_queue_starts_up_to_concurrency():
    queue = QueueManager(max_concurrency=2)
    ids = [queue.add(DownloadTask(url=f"https://example.com/{index}", title=f"Item {index}")) for index in range(3)]

    started = queue.start_ready_tasks()

    assert started == ids[:2]
    assert queue.get(ids[0]).status == TaskStatus.RUNNING
    assert queue.get(ids[2]).status == TaskStatus.PENDING


def test_pending_pause_prevents_start():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))

    queue.pause(task_id)
    started = queue.start_ready_tasks()

    assert started == []
    assert queue.get(task_id).status == TaskStatus.PAUSED


def test_active_pause_becomes_cancel_retry_state():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    queue.start_ready_tasks()

    queue.pause(task_id)

    assert queue.get(task_id).status == TaskStatus.CANCELING_FOR_RETRY


def test_retry_failed_task_returns_to_pending():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    queue.mark_failed(task_id, "network")

    queue.retry(task_id)

    assert queue.get(task_id).status == TaskStatus.PENDING
    assert queue.get(task_id).error == ""
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_queue_manager.py -q
```

Expected: import error for `ytdl_gui.queue_manager`.

- [ ] **Step 3: Implement queue manager**

Create `src/ytdl_gui/queue_manager.py`:

```python
from dataclasses import dataclass, field
from enum import Enum
from uuid import uuid4


class TaskStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    PAUSED = "paused"
    CANCELING_FOR_RETRY = "canceling_for_retry"
    FINISHED = "finished"
    FAILED = "failed"
    CANCELED = "canceled"


@dataclass
class DownloadTask:
    url: str
    title: str
    task_id: str = field(default_factory=lambda: uuid4().hex)
    status: TaskStatus = TaskStatus.PENDING
    progress: float = 0.0
    speed: str = ""
    eta: str = ""
    error: str = ""


class QueueManager:
    def __init__(self, max_concurrency: int = 2):
        if max_concurrency < 1 or max_concurrency > 5:
            raise ValueError("max_concurrency must be between 1 and 5")
        self.max_concurrency = max_concurrency
        self._tasks: list[DownloadTask] = []

    def add(self, task: DownloadTask) -> str:
        self._tasks.append(task)
        return task.task_id

    def get(self, task_id: str) -> DownloadTask:
        return next(task for task in self._tasks if task.task_id == task_id)

    def start_ready_tasks(self) -> list[str]:
        running = sum(1 for task in self._tasks if task.status == TaskStatus.RUNNING)
        slots = self.max_concurrency - running
        started: list[str] = []
        for task in self._tasks:
            if slots <= 0:
                break
            if task.status == TaskStatus.PENDING:
                task.status = TaskStatus.RUNNING
                started.append(task.task_id)
                slots -= 1
        return started

    def pause(self, task_id: str) -> None:
        task = self.get(task_id)
        if task.status == TaskStatus.PENDING:
            task.status = TaskStatus.PAUSED
        elif task.status == TaskStatus.RUNNING:
            task.status = TaskStatus.CANCELING_FOR_RETRY

    def resume(self, task_id: str) -> None:
        task = self.get(task_id)
        if task.status == TaskStatus.PAUSED:
            task.status = TaskStatus.PENDING

    def mark_failed(self, task_id: str, error: str) -> None:
        task = self.get(task_id)
        task.status = TaskStatus.FAILED
        task.error = error

    def retry(self, task_id: str) -> None:
        task = self.get(task_id)
        task.status = TaskStatus.PENDING
        task.error = ""
        task.progress = 0.0
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_queue_manager.py -q
```

Expected: `4 passed`.

- [ ] **Step 5: Commit queue manager**

Run:

```powershell
git add src/ytdl_gui/queue_manager.py tests/test_queue_manager.py
git commit -m "feat: add download queue scheduler"
```

Expected: commit succeeds.

## Task 6: PySide6 Application Shell and Visual Direction

**Files:**
- Create: `src/ytdl_gui/app.py`
- Create: `src/ytdl_gui/ui/theme.py`
- Create: `src/ytdl_gui/ui/main_window.py`
- Create: `src/ytdl_gui/ui/pages/download_page.py`
- Create: `src/ytdl_gui/ui/pages/formats_page.py`
- Create: `src/ytdl_gui/ui/pages/queue_page.py`
- Create: `src/ytdl_gui/ui/pages/history_page.py`
- Create: `src/ytdl_gui/ui/pages/settings_page.py`
- Create: `src/ytdl_gui/ui/pages/about_page.py`
- Create: `src/ytdl_gui/ui/widgets.py`
- Create: `tests/test_gui_smoke.py`

- [ ] **Step 1: Write failing GUI smoke tests**

Create `tests/test_gui_smoke.py`:

```python
from ytdl_gui.ui.main_window import MainWindow


def test_main_window_has_chinese_navigation(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    nav_text = [window.nav.item(index).text() for index in range(window.nav.count())]

    assert nav_text == ["下载", "格式", "队列", "历史", "设置", "关于"]


def test_download_page_has_primary_controls(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.download_page.url_input.placeholderText() == "粘贴一个或多个视频播放地址"
    assert window.download_page.analyze_button.text() == "分析"
    assert window.download_page.start_button.text() == "开始下载"
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_gui_smoke.py -q
```

Expected: import error for `ytdl_gui.ui.main_window`.

- [ ] **Step 3: Create UI package folders**

Create these empty package files:

```txt
src/ytdl_gui/ui/__init__.py
src/ytdl_gui/ui/pages/__init__.py
```

- [ ] **Step 4: Implement theme and widgets**

Create `src/ytdl_gui/ui/theme.py`:

```python
from PySide6.QtGui import QColor, QPalette


TEAL = "#0f8f8c"
SURFACE = "#ffffff"
BACKGROUND = "#f5f7f8"
TEXT = "#1f2933"


def apply_light_theme(app) -> None:
    palette = QPalette()
    palette.setColor(QPalette.Window, QColor(BACKGROUND))
    palette.setColor(QPalette.Base, QColor(SURFACE))
    palette.setColor(QPalette.Button, QColor(SURFACE))
    palette.setColor(QPalette.Text, QColor(TEXT))
    palette.setColor(QPalette.ButtonText, QColor(TEXT))
    app.setPalette(palette)
    app.setStyleSheet(
        f"""
        QWidget {{ font-family: 'Microsoft YaHei UI'; font-size: 13px; color: {TEXT}; }}
        QListWidget {{ background: #eef2f3; border: 0; padding: 8px; }}
        QListWidget::item {{ padding: 10px 12px; border-radius: 6px; }}
        QListWidget::item:selected {{ background: #d7f0ee; color: #064e4b; }}
        QPushButton {{ border: 1px solid #d8dee4; border-radius: 6px; padding: 7px 12px; background: #ffffff; }}
        QPushButton#primaryButton {{ background: {TEAL}; color: white; border-color: {TEAL}; font-weight: 600; }}
        QLineEdit, QTextEdit, QComboBox, QSpinBox {{ border: 1px solid #d8dee4; border-radius: 6px; padding: 7px; background: #ffffff; }}
        QGroupBox {{ border: 1px solid #dde3e7; border-radius: 8px; margin-top: 12px; padding: 12px; background: #ffffff; }}
        QGroupBox::title {{ subcontrol-origin: margin; left: 12px; padding: 0 4px; }}
        """
    )
```

Create `src/ytdl_gui/ui/widgets.py`:

```python
from PySide6.QtWidgets import QLabel, QVBoxLayout, QWidget


class ErrorPanel(QWidget):
    def __init__(self):
        super().__init__()
        self.label = QLabel("")
        self.label.setWordWrap(True)
        layout = QVBoxLayout(self)
        layout.addWidget(self.label)
        self.hide()

    def show_message(self, message: str) -> None:
        self.label.setText(message)
        self.show()
```

- [ ] **Step 5: Implement page widgets**

Create `src/ytdl_gui/ui/pages/download_page.py`:

```python
from PySide6.QtWidgets import QCheckBox, QComboBox, QFileDialog, QHBoxLayout, QLabel, QPushButton, QTextEdit, QVBoxLayout, QWidget


class DownloadPage(QWidget):
    def __init__(self):
        super().__init__()
        self.url_input = QTextEdit()
        self.url_input.setPlaceholderText("粘贴一个或多个视频播放地址")
        self.analyze_button = QPushButton("分析")
        self.start_button = QPushButton("开始下载")
        self.start_button.setObjectName("primaryButton")
        self.save_folder_button = QPushButton("选择保存位置")
        self.mode_combo = QComboBox()
        self.mode_combo.addItems(["音频+视频", "仅音频", "仅视频"])
        self.preview_checkbox = QCheckBox("下载时同步预览播放")

        top = QHBoxLayout()
        top.addWidget(self.analyze_button)
        top.addWidget(self.save_folder_button)
        top.addWidget(self.start_button)

        layout = QVBoxLayout(self)
        layout.addWidget(QLabel("视频地址"))
        layout.addWidget(self.url_input)
        layout.addWidget(QLabel("下载类型"))
        layout.addWidget(self.mode_combo)
        layout.addWidget(self.preview_checkbox)
        layout.addLayout(top)
        layout.addStretch()

    def choose_folder(self) -> str:
        return QFileDialog.getExistingDirectory(self, "选择保存位置")
```

Create `src/ytdl_gui/ui/pages/formats_page.py`:

```python
from PySide6.QtWidgets import QCheckBox, QComboBox, QFormLayout, QWidget


class FormatsPage(QWidget):
    def __init__(self):
        super().__init__()
        self.resolution = QComboBox()
        self.resolution.addItems(["自动", "2160p", "1440p", "1080p", "720p", "480p"])
        self.container = QComboBox()
        self.container.addItems(["mp4", "mkv"])
        self.audio_bitrate = QComboBox()
        self.audio_bitrate.addItems(["自动", "320k", "256k", "192k", "128k"])
        self.subtitle = QComboBox()
        self.subtitle.addItems(["不下载字幕", "下载字幕文件", "嵌入字幕", "烧录字幕"])
        self.strict = QCheckBox("严格匹配格式")

        layout = QFormLayout(self)
        layout.addRow("分辨率", self.resolution)
        layout.addRow("容器", self.container)
        layout.addRow("音频码率偏好", self.audio_bitrate)
        layout.addRow("字幕", self.subtitle)
        layout.addRow("", self.strict)
```

Create `src/ytdl_gui/ui/pages/queue_page.py`:

```python
from PySide6.QtWidgets import QPushButton, QTableWidget, QVBoxLayout, QWidget


class QueuePage(QWidget):
    def __init__(self):
        super().__init__()
        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["标题", "状态", "进度", "速度", "剩余时间", "操作"])
        self.clear_completed_button = QPushButton("清除已完成")
        layout = QVBoxLayout(self)
        layout.addWidget(self.table)
        layout.addWidget(self.clear_completed_button)
```

Create `src/ytdl_gui/ui/pages/history_page.py`:

```python
from PySide6.QtWidgets import QLineEdit, QPushButton, QTableWidget, QVBoxLayout, QWidget


class HistoryPage(QWidget):
    def __init__(self):
        super().__init__()
        self.search = QLineEdit()
        self.search.setPlaceholderText("搜索历史")
        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["标题", "类型", "格式", "状态", "时间", "操作"])
        self.clear_button = QPushButton("清空历史")
        layout = QVBoxLayout(self)
        layout.addWidget(self.search)
        layout.addWidget(self.table)
        layout.addWidget(self.clear_button)
```

Create `src/ytdl_gui/ui/pages/settings_page.py`:

```python
from PySide6.QtWidgets import QCheckBox, QFormLayout, QLineEdit, QPushButton, QSpinBox, QWidget


class SettingsPage(QWidget):
    def __init__(self):
        super().__init__()
        self.default_folder = QLineEdit()
        self.cookies_path = QLineEdit()
        self.ffmpeg_path = QLineEdit()
        self.concurrency = QSpinBox()
        self.concurrency.setRange(1, 5)
        self.concurrency.setValue(2)
        self.update_on_start = QCheckBox("启动时后台检查 yt-dlp 更新")
        self.update_on_start.setChecked(True)
        self.find_ffmpeg_button = QPushButton("搜索 ffmpeg")
        self.choose_ffmpeg_button = QPushButton("选择 ffmpeg.exe")
        self.ffmpeg_download_button = QPushButton("打开官网下载页")
        self.cookies_help_button = QPushButton("如何获取 cookies.txt")

        layout = QFormLayout(self)
        layout.addRow("默认保存位置", self.default_folder)
        layout.addRow("cookies.txt", self.cookies_path)
        layout.addRow("", self.cookies_help_button)
        layout.addRow("ffmpeg.exe", self.ffmpeg_path)
        layout.addRow("", self.find_ffmpeg_button)
        layout.addRow("", self.choose_ffmpeg_button)
        layout.addRow("", self.ffmpeg_download_button)
        layout.addRow("并发下载数", self.concurrency)
        layout.addRow("", self.update_on_start)
```

Create `src/ytdl_gui/ui/pages/about_page.py`:

```python
from PySide6.QtWidgets import QLabel, QPushButton, QVBoxLayout, QWidget


class AboutPage(QWidget):
    def __init__(self):
        super().__init__()
        self.version_label = QLabel("YTDL GUI 0.1.0")
        self.ytdlp_label = QLabel("yt-dlp：待检测")
        self.ffmpeg_label = QLabel("ffmpeg：待检测")
        self.legal_button = QPushButton("第三方许可")
        layout = QVBoxLayout(self)
        layout.addWidget(self.version_label)
        layout.addWidget(self.ytdlp_label)
        layout.addWidget(self.ffmpeg_label)
        layout.addWidget(self.legal_button)
        layout.addStretch()
```

- [ ] **Step 6: Implement main window and app bootstrap**

Create `src/ytdl_gui/ui/main_window.py`:

```python
from PySide6.QtWidgets import QHBoxLayout, QListWidget, QStackedWidget, QWidget

from ytdl_gui.ui.pages.about_page import AboutPage
from ytdl_gui.ui.pages.download_page import DownloadPage
from ytdl_gui.ui.pages.formats_page import FormatsPage
from ytdl_gui.ui.pages.history_page import HistoryPage
from ytdl_gui.ui.pages.queue_page import QueuePage
from ytdl_gui.ui.pages.settings_page import SettingsPage


class MainWindow(QWidget):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("视频地址提取器")
        self.resize(1120, 720)

        self.nav = QListWidget()
        self.nav.setFixedWidth(160)
        self.nav.addItems(["下载", "格式", "队列", "历史", "设置", "关于"])

        self.stack = QStackedWidget()
        self.download_page = DownloadPage()
        self.formats_page = FormatsPage()
        self.queue_page = QueuePage()
        self.history_page = HistoryPage()
        self.settings_page = SettingsPage()
        self.about_page = AboutPage()
        for page in [self.download_page, self.formats_page, self.queue_page, self.history_page, self.settings_page, self.about_page]:
            self.stack.addWidget(page)

        self.nav.currentRowChanged.connect(self.stack.setCurrentIndex)
        self.nav.setCurrentRow(0)

        layout = QHBoxLayout(self)
        layout.addWidget(self.nav)
        layout.addWidget(self.stack, 1)
```

Create `src/ytdl_gui/app.py`:

```python
import sys

from PySide6.QtWidgets import QApplication

from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.theme import apply_light_theme


def run() -> int:
    app = QApplication(sys.argv)
    apply_light_theme(app)
    window = MainWindow()
    window.show()
    return app.exec()
```

- [ ] **Step 7: Run GUI smoke tests**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_gui_smoke.py -q
```

Expected: `2 passed`.

- [ ] **Step 8: Commit GUI shell**

Run:

```powershell
git add src/ytdl_gui/ui src/ytdl_gui/app.py tests/test_gui_smoke.py
git commit -m "feat: add pySide6 gui shell"
```

Expected: commit succeeds.

## Task 7: Workers for Non-Blocking Analysis, Updates, and ffmpeg Search

**Files:**
- Create: `src/ytdl_gui/workers.py`
- Create: `tests/test_workers.py`

- [ ] **Step 1: Write failing worker tests**

Create `tests/test_workers.py`:

```python
from pathlib import Path

from ytdl_gui.workers import AnalysisRequest, make_analysis_command


def test_make_analysis_command_keeps_timeout_and_url(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    request = AnalysisRequest(url="https://www.youtube.com/watch?v=abc", ytdlp_path=exe, cookies_path=None, timeout_seconds=60)

    command = make_analysis_command(request)

    assert str(exe) == command[0]
    assert "--dump-single-json" in command
    assert request.timeout_seconds == 60
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_workers.py -q
```

Expected: import error for `ytdl_gui.workers`.

- [ ] **Step 3: Implement worker request objects and command helper**

Create `src/ytdl_gui/workers.py`:

```python
from dataclasses import dataclass
from pathlib import Path

from PySide6.QtCore import QObject, Signal, Slot

from ytdl_gui.ytdlp_runner import AnalysisFailureKind, YtdlpCommandBuilder, categorize_analysis_error


@dataclass(frozen=True)
class AnalysisRequest:
    url: str
    ytdlp_path: Path
    cookies_path: Path | None
    timeout_seconds: int = 60


def make_analysis_command(request: AnalysisRequest) -> list[str]:
    return YtdlpCommandBuilder(request.ytdlp_path).analysis_command(request.url, request.cookies_path)


class AnalysisWorker(QObject):
    finished = Signal(dict)
    failed = Signal(str)
    canceled = Signal()

    def __init__(self, request: AnalysisRequest):
        super().__init__()
        self.request = request
        self._canceled = False

    def cancel(self) -> None:
        self._canceled = True

    @Slot()
    def run(self) -> None:
        if self._canceled:
            self.canceled.emit()
            return
        command = make_analysis_command(self.request)
        try:
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=self.request.timeout_seconds,
                check=False,
            )
        except subprocess.TimeoutExpired:
            self.failed.emit(AnalysisFailureKind.NETWORK_TIMEOUT.value)
            return
        if self._canceled:
            self.canceled.emit()
            return
        if result.returncode != 0:
            self.failed.emit(categorize_analysis_error(result.stderr).value)
            return
        self.finished.emit(json.loads(result.stdout))
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_workers.py -q
```

Expected: `1 passed`.

- [ ] **Step 5: Commit worker foundation**

Run:

```powershell
git add src/ytdl_gui/workers.py tests/test_workers.py
git commit -m "feat: add nonblocking worker foundation"
```

Expected: commit succeeds.

## Task 8: Wire UI to Core Services

**Files:**
- Modify: `src/ytdl_gui/app.py`
- Modify: `src/ytdl_gui/ui/main_window.py`
- Modify: `src/ytdl_gui/ui/pages/download_page.py`
- Modify: `src/ytdl_gui/ui/pages/settings_page.py`
- Modify: `src/ytdl_gui/ui/pages/history_page.py`
- Modify: `src/ytdl_gui/ui/pages/about_page.py`
- Create: `tests/test_ui_service_wiring.py`

- [ ] **Step 1: Write failing service wiring tests**

Create `tests/test_ui_service_wiring.py`:

```python
from pathlib import Path

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryRecord, HistoryStore
from ytdl_gui.ui.main_window import MainWindow


def test_settings_page_loads_config(qtbot, app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(default_save_dir="D:/Downloads", ffmpeg_path="D:/ffmpeg/bin/ffmpeg.exe", max_concurrency=4))

    window = MainWindow(config_store=store, history_store=HistoryStore(app_data_dir))
    qtbot.addWidget(window)

    assert window.settings_page.default_folder.text() == "D:/Downloads"
    assert window.settings_page.ffmpeg_path.text() == "D:/ffmpeg/bin/ffmpeg.exe"
    assert window.settings_page.concurrency.value() == 4


def test_history_page_renders_history(qtbot, app_data_dir: Path):
    history = HistoryStore(app_data_dir)
    history.add(HistoryRecord("Title", "url", "D:/file.mp4", "audio_video", "720p mp4", "none", "finished", "2026-05-30T22:00:00"))

    window = MainWindow(config_store=ConfigStore(app_data_dir), history_store=history)
    qtbot.addWidget(window)

    assert window.history_page.table.rowCount() == 1
    assert window.history_page.table.item(0, 0).text() == "Title"
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_ui_service_wiring.py -q
```

Expected: `MainWindow.__init__` does not accept `config_store` and `history_store`.

- [ ] **Step 3: Add service injection and page loaders**

Modify `src/ytdl_gui/ui/main_window.py` so the constructor accepts optional stores:

```python
class MainWindow(QWidget):
    def __init__(self, config_store=None, history_store=None):
        super().__init__()
        self.setWindowTitle("视频地址提取器")
        self.resize(1120, 720)
        self.config_store = config_store
        self.history_store = history_store

        self.nav = QListWidget()
        self.nav.setFixedWidth(160)
        self.nav.addItems(["下载", "格式", "队列", "历史", "设置", "关于"])

        self.stack = QStackedWidget()
        self.download_page = DownloadPage()
        self.formats_page = FormatsPage()
        self.queue_page = QueuePage()
        self.history_page = HistoryPage()
        self.settings_page = SettingsPage()
        self.about_page = AboutPage()
        for page in [self.download_page, self.formats_page, self.queue_page, self.history_page, self.settings_page, self.about_page]:
            self.stack.addWidget(page)

        self.nav.currentRowChanged.connect(self.stack.setCurrentIndex)
        self.nav.setCurrentRow(0)

        layout = QHBoxLayout(self)
        layout.addWidget(self.nav)
        layout.addWidget(self.stack, 1)

        if self.config_store:
            self.settings_page.load_config(self.config_store.load())
        if self.history_store:
            self.history_page.load_records(self.history_store.list())
```

Add to `src/ytdl_gui/ui/pages/settings_page.py`:

```python
    def load_config(self, config) -> None:
        self.default_folder.setText(config.default_save_dir)
        self.cookies_path.setText(config.cookies_path)
        self.ffmpeg_path.setText(config.ffmpeg_path)
        self.concurrency.setValue(config.max_concurrency)
        self.update_on_start.setChecked(config.check_ytdlp_updates_on_startup)
```

Modify the import in `src/ytdl_gui/ui/pages/history_page.py`:

```python
from PySide6.QtWidgets import QLineEdit, QPushButton, QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget
```

Add this method inside `HistoryPage`:

```python
    def load_records(self, records) -> None:
        self.table.setRowCount(0)
        for record in records:
            row = self.table.rowCount()
            self.table.insertRow(row)
            for column, value in enumerate([record.title, record.download_type, record.format_summary, record.status, record.created_at, "打开"]):
                self.table.setItem(row, column, QTableWidgetItem(str(value)))
```

Modify `src/ytdl_gui/app.py`:

```python
from ytdl_gui.config_store import ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.paths import app_data_dir


def run() -> int:
    app = QApplication(sys.argv)
    apply_light_theme(app)
    data_dir = app_data_dir()
    window = MainWindow(config_store=ConfigStore(data_dir), history_store=HistoryStore(data_dir))
    window.show()
    return app.exec()
```

- [ ] **Step 4: Run wiring tests**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_ui_service_wiring.py -q
```

Expected: `2 passed`.

- [ ] **Step 5: Commit UI service wiring**

Run:

```powershell
git add src/ytdl_gui tests/test_ui_service_wiring.py
git commit -m "feat: wire gui pages to local stores"
```

Expected: commit succeeds.

## Task 9: Settings Actions, ffmpeg Search, and Update Prompts

**Files:**
- Modify: `src/ytdl_gui/workers.py`
- Modify: `src/ytdl_gui/ui/main_window.py`
- Modify: `src/ytdl_gui/ui/pages/settings_page.py`
- Modify: `src/ytdl_gui/ui/pages/about_page.py`
- Create: `tests/test_settings_actions.py`

- [ ] **Step 1: Write failing settings action tests**

Create `tests/test_settings_actions.py`:

```python
from pathlib import Path

from ytdl_gui.ffmpeg import find_ffmpeg
from ytdl_gui.update_manager import OFFICIAL_YTDLP_EXE_URL
from ytdl_gui.ui.main_window import MainWindow


def test_official_ytdlp_update_source_is_github_release():
    assert OFFICIAL_YTDLP_EXE_URL == "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"


def test_settings_buttons_expose_help_actions(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.settings_page.cookies_help_button.text() == "如何获取 cookies.txt"
    assert window.settings_page.find_ffmpeg_button.text() == "搜索 ffmpeg"
    assert window.settings_page.ffmpeg_download_button.text() == "打开官网下载页"


def test_ffmpeg_search_can_use_user_data_dir(tmp_path: Path):
    exe = tmp_path / "ffmpeg" / "bin" / "ffmpeg.exe"
    exe.parent.mkdir(parents=True)
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(search_paths=[tmp_path], env_path="")

    assert status.found is True
    assert status.path == exe
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_settings_actions.py -q
```

Expected: the official source and ffmpeg search tests pass, while UI action wiring is incomplete.

- [ ] **Step 3: Add settings action methods**

Add these methods to `src/ytdl_gui/ui/main_window.py`:

```python
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import QMessageBox
from PySide6.QtCore import QUrl

from ytdl_gui.cookies import cookie_help_text
from ytdl_gui.ffmpeg import ffmpeg_help_url, find_ffmpeg

    def connect_settings_actions(self) -> None:
        self.settings_page.cookies_help_button.clicked.connect(self.show_cookies_help)
        self.settings_page.ffmpeg_download_button.clicked.connect(self.open_ffmpeg_download)
        self.settings_page.find_ffmpeg_button.clicked.connect(self.search_ffmpeg)

    def show_cookies_help(self) -> None:
        QMessageBox.information(self, "如何获取 cookies.txt", cookie_help_text())

    def open_ffmpeg_download(self) -> None:
        QDesktopServices.openUrl(QUrl(ffmpeg_help_url()))

    def search_ffmpeg(self) -> None:
        status = find_ffmpeg()
        if status.found and status.path:
            self.settings_page.ffmpeg_path.setText(str(status.path))
            self.about_page.ffmpeg_label.setText(f"ffmpeg：{status.version}")
        else:
            QMessageBox.information(self, "未找到 ffmpeg", "未在 PATH 或常见目录中找到 ffmpeg.exe。可以手动选择本机路径，或打开官网下载页。")
```

Call `self.connect_settings_actions()` at the end of `MainWindow.__init__` after loading config and history.

- [ ] **Step 4: Run settings tests**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_settings_actions.py -q
```

Expected: `3 passed`.

- [ ] **Step 5: Commit settings actions**

Run:

```powershell
git add src/ytdl_gui tests/test_settings_actions.py
git commit -m "feat: add settings help and detection actions"
```

Expected: commit succeeds.

## Task 10: Download Execution, Progress, and History Integration

**Files:**
- Modify: `src/ytdl_gui/ytdlp_runner.py`
- Modify: `src/ytdl_gui/workers.py`
- Modify: `src/ytdl_gui/ui/main_window.py`
- Modify: `src/ytdl_gui/ui/pages/queue_page.py`
- Create: `tests/test_download_progress.py`

- [ ] **Step 1: Write failing download progress tests**

Create `tests/test_download_progress.py`:

```python
from ytdl_gui.ytdlp_runner import ProgressEvent, parse_progress_line, safe_command_summary


def test_parse_ytdlp_progress_line():
    line = "[download]  42.3% of 10.00MiB at 1.23MiB/s ETA 00:10"

    event = parse_progress_line(line)

    assert event == ProgressEvent(percent=42.3, speed="1.23MiB/s", eta="00:10", raw=line)


def test_parse_non_progress_line_keeps_raw():
    line = "Deleting original file example.f248.mp4"

    event = parse_progress_line(line)

    assert event.percent is None
    assert event.raw == line


def test_safe_command_summary_redacts_cookie_path():
    command = ["yt-dlp.exe", "--cookies", "D:/secret/cookies.txt", "-f", "22", "https://example.com"]

    summary = safe_command_summary(command)

    assert "D:/secret" not in summary
    assert "--cookies <redacted>" in summary
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_download_progress.py -q
```

Expected: import errors for `ProgressEvent`, `parse_progress_line`, and `safe_command_summary`.

- [ ] **Step 3: Add progress parsing and redacted command summaries**

Add this to `src/ytdl_gui/ytdlp_runner.py`:

```python
import re
from dataclasses import dataclass


@dataclass(frozen=True)
class ProgressEvent:
    percent: float | None
    speed: str = ""
    eta: str = ""
    raw: str = ""


PROGRESS_PATTERN = re.compile(r"\[download\]\s+(?P<percent>\d+(?:\.\d+)?)%.*? at (?P<speed>\S+) ETA (?P<eta>\S+)")


def parse_progress_line(line: str) -> ProgressEvent:
    match = PROGRESS_PATTERN.search(line)
    if not match:
        return ProgressEvent(percent=None, raw=line)
    return ProgressEvent(
        percent=float(match.group("percent")),
        speed=match.group("speed"),
        eta=match.group("eta"),
        raw=line,
    )


def safe_command_summary(command: list[str]) -> str:
    safe: list[str] = []
    skip_next = False
    for index, part in enumerate(command):
        if skip_next:
            skip_next = False
            continue
        if part == "--cookies":
            safe.append("--cookies <redacted>")
            skip_next = True
        else:
            safe.append(part)
    return " ".join(safe)
```

- [ ] **Step 4: Add download worker request object**

Add this to `src/ytdl_gui/workers.py`:

```python
@dataclass(frozen=True)
class DownloadRequest:
    url: str
    ytdlp_path: Path
    output_template: Path
    format_id: str
    cookies_path: Path | None = None


class DownloadWorker(QObject):
    progress = Signal(object)
    finished = Signal()
    failed = Signal(str)

    def __init__(self, request: DownloadRequest):
        super().__init__()
        self.request = request
        self._process: subprocess.Popen | None = None

    def cancel(self) -> None:
        if self._process and self._process.poll() is None:
            self._process.terminate()

    @Slot()
    def run(self) -> None:
        from ytdl_gui.ytdlp_runner import parse_progress_line

        command = YtdlpCommandBuilder(self.request.ytdlp_path).download_command(
            self.request.url,
            self.request.output_template,
            self.request.format_id,
            self.request.cookies_path,
        )
        self._process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        assert self._process.stdout is not None
        for line in self._process.stdout:
            self.progress.emit(parse_progress_line(line.strip()))
        return_code = self._process.wait()
        if return_code == 0:
            self.finished.emit()
        else:
            self.failed.emit(f"yt-dlp 下载失败，退出码 {return_code}")
```

- [ ] **Step 5: Run download progress tests**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_download_progress.py -q
```

Expected: `3 passed`.

- [ ] **Step 6: Wire finished downloads to history**

Add a method to `src/ytdl_gui/ui/main_window.py`:

```python
from datetime import datetime
from ytdl_gui.history_store import HistoryRecord

    def record_finished_download(self, title: str, url: str, output_path: str, download_type: str, format_summary: str, subtitle_behavior: str) -> None:
        if not self.history_store:
            return
        self.history_store.add(
            HistoryRecord(
                title=title,
                url=url,
                output_path=output_path,
                download_type=download_type,
                format_summary=format_summary,
                subtitle_behavior=subtitle_behavior,
                status="finished",
                created_at=datetime.now().isoformat(timespec="seconds"),
            )
        )
        self.history_page.load_records(self.history_store.list())
```

- [ ] **Step 7: Commit download execution foundation**

Run:

```powershell
git add src/ytdl_gui tests/test_download_progress.py
git commit -m "feat: add download progress and history integration"
```

Expected: commit succeeds.

## Task 11: Preview Player and Failure Boundaries

**Files:**
- Create: `src/ytdl_gui/ui/player.py`
- Modify: `src/ytdl_gui/ui/pages/download_page.py`
- Create: `tests/test_preview_player.py`

- [ ] **Step 1: Write failing preview tests**

Create `tests/test_preview_player.py`:

```python
from ytdl_gui.ui.player import PreviewState, preview_failure_message


def test_preview_failure_message_keeps_download_available():
    message = preview_failure_message(PreviewState.UNAVAILABLE)

    assert "预览不可用" in message
    assert "下载仍可继续" in message
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_preview_player.py -q
```

Expected: import error for `ytdl_gui.ui.player`.

- [ ] **Step 3: Implement best-effort preview wrapper**

Create `src/ytdl_gui/ui/player.py`:

```python
from enum import Enum

from PySide6.QtMultimedia import QAudioOutput, QMediaPlayer
from PySide6.QtWidgets import QLabel, QPushButton, QSlider, QVBoxLayout, QWidget
from PySide6.QtCore import Qt, QUrl


class PreviewState(str, Enum):
    IDLE = "idle"
    LOADING = "loading"
    PLAYING = "playing"
    UNAVAILABLE = "unavailable"
    ERROR = "error"


def preview_failure_message(state: PreviewState) -> str:
    if state == PreviewState.UNAVAILABLE:
        return "预览不可用，下载仍可继续。"
    return "预览播放失败，下载任务不会因此取消。"


class PreviewPlayer(QWidget):
    def __init__(self):
        super().__init__()
        self.player = QMediaPlayer(self)
        self.audio = QAudioOutput(self)
        self.player.setAudioOutput(self.audio)
        self.status = QLabel("预览未开始")
        self.play_button = QPushButton("播放")
        self.pause_button = QPushButton("暂停")
        self.volume = QSlider(Qt.Horizontal)
        self.volume.setRange(0, 100)
        self.volume.setValue(60)
        self.volume.valueChanged.connect(lambda value: self.audio.setVolume(value / 100))
        layout = QVBoxLayout(self)
        layout.addWidget(self.status)
        layout.addWidget(self.play_button)
        layout.addWidget(self.pause_button)
        layout.addWidget(self.volume)

    def load_url(self, url: str) -> None:
        self.status.setText("正在加载预览")
        self.player.setSource(QUrl(url))

    def show_unavailable(self) -> None:
        self.status.setText(preview_failure_message(PreviewState.UNAVAILABLE))
```

- [ ] **Step 4: Run preview tests**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_preview_player.py -q
```

Expected: `1 passed`.

- [ ] **Step 5: Commit preview boundary**

Run:

```powershell
git add src/ytdl_gui/ui/player.py src/ytdl_gui/ui/pages/download_page.py tests/test_preview_player.py
git commit -m "feat: add best effort preview player"
```

Expected: commit succeeds.

## Task 12: Packaging, yt-dlp Bundle, and Legal Notices

**Files:**
- Create: `scripts/fetch_ytdlp.ps1`
- Create: `scripts/package_win.ps1`
- Create: `scripts/smoke_packaged.ps1`
- Create: `packaging/ytdl_gui.spec`
- Create: `licenses/THIRD_PARTY_NOTICES.txt`
- Modify: `README.md`

- [ ] **Step 1: Create official yt-dlp fetch helper**

Create `scripts/fetch_ytdlp.ps1`:

```powershell
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Tools = Join-Path $Root "tools"
$Target = Join-Path $Tools "yt-dlp.exe"
$Temp = Join-Path $Tools "yt-dlp.tmp.exe"
$Url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
New-Item -ItemType Directory -Force -Path $Tools | Out-Null
Invoke-WebRequest -Uri $Url -OutFile $Temp
& $Temp --version | Out-Host
Move-Item -Force $Temp $Target
Write-Host "Saved $Target"
```

- [ ] **Step 2: Create PyInstaller spec**

Create `packaging/ytdl_gui.spec`:

```python
# -*- mode: python ; coding: utf-8 -*-

from pathlib import Path

root = Path.cwd()

a = Analysis(
    ["src/ytdl_gui/main.py"],
    pathex=[str(root / "src")],
    binaries=[(str(root / "tools" / "yt-dlp.exe"), "tools")],
    datas=[
        (str(root / "licenses" / "THIRD_PARTY_NOTICES.txt"), "licenses"),
        (str(root / "docs" / "gui-reference.png"), "docs"),
    ],
    hiddenimports=["PySide6.QtMultimedia"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="YTDL-GUI",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="YTDL-GUI",
)
```

- [ ] **Step 3: Create package script**

Create `scripts/package_win.ps1`:

```powershell
$ErrorActionPreference = "Stop"
if (!(Test-Path ".\.venv\Scripts\python.exe")) {
  throw "Missing .venv. Create it and install requirements-dev.txt first."
}
if (!(Test-Path ".\tools\yt-dlp.exe")) {
  throw "Missing tools\yt-dlp.exe. Run scripts\fetch_ytdlp.ps1 first."
}
.\.venv\Scripts\python.exe -m PyInstaller .\packaging\ytdl_gui.spec --noconfirm --clean
Write-Host "Packaged dist\YTDL-GUI\YTDL-GUI.exe"
```

Create `scripts/smoke_packaged.ps1`:

```powershell
$ErrorActionPreference = "Stop"
$Exe = ".\dist\YTDL-GUI\YTDL-GUI.exe"
if (!(Test-Path $Exe)) {
  throw "Packaged exe not found: $Exe"
}
$Ytdlp = ".\dist\YTDL-GUI\tools\yt-dlp.exe"
if (!(Test-Path $Ytdlp)) {
  throw "Bundled yt-dlp.exe not found: $Ytdlp"
}
& $Ytdlp --version | Out-Host
Write-Host "Packaged smoke inputs exist. Start the GUI manually for visual smoke."
```

- [ ] **Step 4: Create third-party notice file**

Create `licenses/THIRD_PARTY_NOTICES.txt`:

```txt
YTDL GUI third-party notices

This distribution includes or is designed to bundle the following third-party components:

Python runtime
License: Python Software Foundation License
Source: https://www.python.org/

PySide6 and Qt
License: LGPL/commercial terms depending on distribution package
Source: https://doc.qt.io/qtforpython-6/

PyInstaller bootloader/runtime
License: GPL with bootloader exception
Source: https://pyinstaller.org/

yt-dlp executable
License: Unlicense
Source: https://github.com/yt-dlp/yt-dlp

ffmpeg is not bundled in the first version. If users configure a local ffmpeg.exe, that binary remains user-provided.
```

- [ ] **Step 5: Update README with packaging commands**

Append this section to `README.md`:

````markdown
## Packaging

Fetch the bundled `yt-dlp.exe` from the official release source:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\fetch_ytdlp.ps1
```

Build the Win11 x64 distribution folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package_win.ps1
```

Run packaged smoke checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke_packaged.ps1
```

The package includes `licenses\THIRD_PARTY_NOTICES.txt`. ffmpeg is user-provided and is not bundled.
````

- [ ] **Step 6: Run packaging preflight**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
powershell -ExecutionPolicy Bypass -File .\scripts\package_win.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\smoke_packaged.ps1
```

Expected: tests pass, PyInstaller creates `dist\YTDL-GUI\YTDL-GUI.exe`, bundled `yt-dlp.exe --version` prints a version.

- [ ] **Step 7: Commit packaging**

Run:

```powershell
git add tools scripts packaging licenses README.md
git commit -m "build: add windows packaging"
```

Expected: commit succeeds.

## Task 13: End-to-End Verification and Visual QA

**Files:**
- Create: `docs/qa/visual-checklist.md`

- [ ] **Step 1: Create visual checklist**

Create `docs/qa/visual-checklist.md`:

```markdown
# Visual QA Checklist

- Download page screenshot shows left navigation, URL input, options, teal primary button, and Chinese labels.
- Formats page screenshot shows resolution, container, bitrate, and subtitle controls without text overlap.
- Queue page screenshot shows title, status, progress, speed, ETA, and actions.
- History page screenshot shows search and history table.
- Settings page screenshot shows save folder, cookies.txt, ffmpeg, concurrency, and update controls.
- About page screenshot shows app version, yt-dlp version, ffmpeg state, and third-party license entry.
- The UI follows `docs/gui-reference.png`: light Win11 surfaces, teal primary action, compact utility layout.
```

- [ ] **Step 2: Run full core tests**

Run:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Expected: all tests pass.

- [ ] **Step 3: Run packaged smoke**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package_win.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\smoke_packaged.ps1
```

Expected: package exists and bundled `yt-dlp.exe` reports a version.

- [ ] **Step 4: Manual Win11 GUI smoke**

Run:

```powershell
Start-Process ".\dist\YTDL-GUI\YTDL-GUI.exe"
```

Expected manual checks:

- Main window opens immediately before update/network checks finish.
- Navigation switches between Download, Formats, Queue, History, Settings, and About.
- Settings can show ffmpeg missing state without blocking baseline operations.
- cookies help text explains how to export Netscape-format `cookies.txt` and warns it is sensitive.
- Legal notice opens or is reachable from About.
- Preview unavailable messages do not cancel queued downloads.

- [ ] **Step 5: Commit QA docs**

Run:

```powershell
git add docs/qa
git commit -m "docs: add qa checklist"
```

Expected: commit succeeds.

---

## Execution Notes

- Use `superpowers:subagent-driven-development` for implementation if parallel review capacity is available.
- Use `superpowers:executing-plans` for inline implementation if the user wants this same session to execute the plan.
- Before claiming completion of any task, run the task's verification commands and read the output.
- Network actions such as dependency install and `yt-dlp.exe` download may require explicit approval in this Codex environment.
- The first packaged build is accepted at baseline without ffmpeg and enhanced only after a valid `ffmpeg.exe` is detected or configured.
