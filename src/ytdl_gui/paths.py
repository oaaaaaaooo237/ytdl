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
