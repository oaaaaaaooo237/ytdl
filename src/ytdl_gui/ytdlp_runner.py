from enum import Enum
from pathlib import Path
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
    for part in command:
        if skip_next:
            skip_next = False
            continue
        if part == "--cookies":
            safe.append("--cookies <redacted>")
            skip_next = True
            continue
        if part.startswith("--cookies="):
            safe.append("--cookies=<redacted>")
            continue
        safe.append(part)
    return " ".join(safe)


class YtdlpCommandBuilder:
    def __init__(self, executable: Path):
        self.executable = executable

    def analysis_command(self, url: str, cookies_path: Path | None = None) -> list[str]:
        command = [
            str(self.executable),
            "--dump-single-json",
            "--no-warnings",
            "--no-playlist",
        ]
        self._append_cookies(command, cookies_path)
        command.append(url)
        return command

    def playlist_probe_command(self, url: str, cookies_path: Path | None = None) -> list[str]:
        command = [
            str(self.executable),
            "--dump-single-json",
            "--flat-playlist",
        ]
        self._append_cookies(command, cookies_path)
        command.append(url)
        return command

    def download_command(
        self,
        url: str,
        output_template: Path,
        format_id: str,
        cookies_path: Path | None = None,
    ) -> list[str]:
        command = [
            str(self.executable),
            "--newline",
            "-f",
            format_id,
            "-o",
            str(output_template),
        ]
        self._append_cookies(command, cookies_path)
        command.append(url)
        return command

    def preview_url_command(
        self,
        url: str,
        format_id: str | None = None,
        cookies_path: Path | None = None,
    ) -> list[str]:
        command = [str(self.executable), "-g"]
        if format_id:
            command.extend(["-f", format_id])
        self._append_cookies(command, cookies_path)
        command.append(url)
        return command

    @staticmethod
    def _append_cookies(command: list[str], cookies_path: Path | None) -> None:
        if cookies_path:
            command.extend(["--cookies", str(cookies_path)])


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
    if "cancel" in text or "canceled" in text or "cancelled" in text:
        return AnalysisFailureKind.CANCELED
    if "playlist" in text and ("confirm" in text or "confirmation" in text or "entries" in text or "items" in text):
        return AnalysisFailureKind.PLAYLIST_CONFIRMATION_NEEDED
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
