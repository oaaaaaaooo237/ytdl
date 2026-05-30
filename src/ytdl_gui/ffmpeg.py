import os
import subprocess
from dataclasses import dataclass
from pathlib import Path


UNKNOWN_VERSION = "未检测版本"


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
    probe_timeout_seconds: float = 1.0,
) -> FfmpegStatus:
    candidates: list[Path] = []
    if configured_path:
        candidates.append(configured_path)
    for root in search_paths or []:
        candidates.extend([root / "ffmpeg.exe", root / "ffmpeg" / "bin" / "ffmpeg.exe"])
    for segment in (env_path if env_path is not None else os.environ.get("PATH", "")).split(os.pathsep):
        if segment:
            candidates.append(Path(segment) / "ffmpeg.exe")

    for candidate in _dedupe_candidates(candidates):
        if candidate.exists() and candidate.is_file():
            return FfmpegStatus(True, candidate, _probe_version(candidate, probe_timeout_seconds))
    return FfmpegStatus(False, None, "")


def _dedupe_candidates(candidates: list[Path]) -> list[Path]:
    unique: list[Path] = []
    seen: set[str] = set()
    for candidate in candidates:
        key = os.path.normcase(os.path.abspath(os.fspath(candidate)))
        if key in seen:
            continue
        seen.add(key)
        unique.append(candidate)
    return unique


def _probe_version(path: Path, timeout_seconds: float = 1.0) -> str:
    try:
        result = subprocess.run(
            [str(path), "-version"],
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
            check=False,
        )
    except OSError:
        return UNKNOWN_VERSION
    except subprocess.TimeoutExpired:
        return UNKNOWN_VERSION
    first_line = result.stdout.splitlines()[0] if result.stdout.splitlines() else ""
    return first_line or UNKNOWN_VERSION
