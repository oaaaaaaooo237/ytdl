import json
import shutil
import subprocess
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from uuid import uuid4

from PySide6.QtCore import QObject, Signal, Slot

from ytdl_gui.ffmpeg import FfmpegStatus, find_ffmpeg
from ytdl_gui.update_manager import UpdateOutcome, download_and_install_latest_ytdlp
from ytdl_gui.ytdlp_runner import AnalysisFailureKind, YtdlpCommandBuilder, categorize_analysis_error, parse_progress_line


@dataclass(frozen=True)
class AnalysisRequest:
    url: str
    ytdlp_path: Path
    cookies_path: Path | None
    timeout_seconds: int = 60


AnalysisRunner = Callable[..., subprocess.CompletedProcess[str]]
PopenFactory = Callable[..., subprocess.Popen]
FfmpegRunner = Callable[..., subprocess.CompletedProcess[str]]


def make_analysis_command(request: AnalysisRequest) -> list[str]:
    return YtdlpCommandBuilder(request.ytdlp_path).analysis_command(request.url, request.cookies_path)


@dataclass(frozen=True)
class PlaylistProbeRequest:
    url: str
    ytdlp_path: Path
    cookies_path: Path | None
    timeout_seconds: int = 60


def make_playlist_probe_command(request: PlaylistProbeRequest) -> list[str]:
    return YtdlpCommandBuilder(request.ytdlp_path).playlist_probe_command(request.url, request.cookies_path)


class AnalysisWorker(QObject):
    finished = Signal(dict)
    failed = Signal(str)
    canceled = Signal()

    def __init__(self, request: AnalysisRequest, runner: AnalysisRunner = subprocess.run):
        super().__init__()
        self.request = request
        self._runner = runner
        self._canceled = False

    def cancel(self) -> None:
        self._canceled = True

    @Slot()
    def run(self) -> None:
        if self._canceled:
            self.canceled.emit()
            return

        try:
            result = self._runner(
                make_analysis_command(self.request),
                capture_output=True,
                text=True,
                timeout=self.request.timeout_seconds,
                check=False,
            )
        except subprocess.TimeoutExpired:
            self.failed.emit(AnalysisFailureKind.NETWORK_TIMEOUT.value)
            return
        except subprocess.SubprocessError:
            self.failed.emit(AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value)
            return
        except OSError:
            self.failed.emit(AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value)
            return

        if self._canceled:
            self.canceled.emit()
            return

        if result.returncode != 0:
            self.failed.emit(categorize_analysis_error(result.stderr or "").value)
            return

        payload = self._parse_stdout(result.stdout)
        if payload is None:
            self.failed.emit(AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value)
            return

        self.finished.emit(payload)

    @staticmethod
    def _parse_stdout(stdout: str | None) -> dict[str, Any] | None:
        try:
            payload = json.loads(stdout or "")
        except json.JSONDecodeError:
            return None
        if not isinstance(payload, dict):
            return None
        return payload


class PlaylistProbeWorker(QObject):
    finished = Signal(dict)
    failed = Signal(str)

    def __init__(self, request: PlaylistProbeRequest, runner: AnalysisRunner = subprocess.run):
        super().__init__()
        self.request = request
        self._runner = runner

    @Slot()
    def run(self) -> None:
        try:
            result = self._runner(
                make_playlist_probe_command(self.request),
                capture_output=True,
                text=True,
                timeout=self.request.timeout_seconds,
                check=False,
            )
        except (subprocess.TimeoutExpired, subprocess.SubprocessError, OSError):
            self.failed.emit(AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value)
            return

        if result.returncode != 0:
            self.failed.emit(categorize_analysis_error(result.stderr or "").value)
            return

        payload = AnalysisWorker._parse_stdout(result.stdout)
        if payload is None:
            self.failed.emit(AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value)
            return

        self.finished.emit(payload)


@dataclass(frozen=True)
class DownloadRequest:
    url: str
    ytdlp_path: Path
    output_template: Path
    format_id: str
    cookies_path: Path | None = None
    subtitle_action: str = "none"
    ffmpeg_path: Path | None = None
    expected_title: str = ""


@dataclass(frozen=True)
class PreviewUrlRequest:
    url: str
    ytdlp_path: Path
    format_id: str | None = None
    cookies_path: Path | None = None
    timeout_seconds: int = 30


class DownloadWorker(QObject):
    progress = Signal(object)
    finished = Signal(str)
    failed = Signal(str)

    def __init__(
        self,
        request: DownloadRequest,
        popen_factory: PopenFactory | None = None,
        ffmpeg_runner: FfmpegRunner | None = None,
    ):
        super().__init__()
        self.request = request
        self._popen_factory = popen_factory or subprocess.Popen
        self._ffmpeg_runner = ffmpeg_runner or subprocess.run
        self._process = None
        self._canceled = False

    def cancel(self) -> None:
        self._canceled = True
        if self._process and self._process.poll() is None:
            self._process.terminate()

    @Slot()
    def run(self) -> None:
        before_files = _snapshot_output_files(self.request.output_template.parent)
        command = YtdlpCommandBuilder(self.request.ytdlp_path).download_command(
            self.request.url,
            self.request.output_template,
            self.request.format_id,
            self.request.cookies_path,
            subtitle_action=self.request.subtitle_action,
            ffmpeg_path=self.request.ffmpeg_path,
        )
        try:
            self._process = self._popen_factory(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
            )
        except OSError:
            self.failed.emit("启动 yt-dlp 下载失败，请检查 yt-dlp 路径。")
            return
        except subprocess.SubprocessError:
            self.failed.emit("启动 yt-dlp 下载失败，请稍后重试。")
            return

        output_path = ""
        stdout = getattr(self._process, "stdout", None)
        if stdout is not None:
            for line in stdout:
                if self._canceled:
                    break
                text = line.strip()
                event = parse_progress_line(text)
                self.progress.emit(event)
                if event.percent is None and _looks_like_output_path(text):
                    output_path = text

        return_code = self._process.wait()
        if self._canceled:
            self.failed.emit("下载已取消。")
        elif return_code == 0:
            resolved_output_path = _resolve_output_path(
                output_path,
                self.request.output_template,
                before_files,
                expected_title=self.request.expected_title,
            )
            if self.request.subtitle_action == "burn":
                burned_output_path = self._burn_subtitle(resolved_output_path, before_files)
                if burned_output_path:
                    self.finished.emit(burned_output_path)
                return
            self.finished.emit(resolved_output_path)
        else:
            self.failed.emit(f"yt-dlp 下载失败，退出码 {return_code}")

    def _burn_subtitle(self, output_path: str, before_files: set[Path]) -> str:
        media_path = Path(output_path)
        if not output_path or not media_path.exists():
            self.failed.emit(BURN_FAILURE_MESSAGE)
            return ""
        if self.request.ffmpeg_path is None:
            self.failed.emit("烧录字幕需要 ffmpeg；请先在设置中搜索或选择 ffmpeg.exe。")
            return ""
        subtitle_path = _find_subtitle_file(media_path, before_files)
        if subtitle_path is None:
            self.failed.emit("未找到可烧录的字幕文件；原始文件已保留，可改为下载字幕文件或嵌入字幕后重试。")
            return ""

        burned_output_path = _burned_output_path(media_path)
        safe_subtitle_path = _copy_subtitle_for_ffmpeg(subtitle_path)
        command = _burn_subtitle_command(Path(self.request.ffmpeg_path), media_path, safe_subtitle_path, burned_output_path)
        try:
            result = self._ffmpeg_runner(
                command,
                capture_output=True,
                text=True,
                check=False,
                cwd=str(safe_subtitle_path.parent),
            )
        except (OSError, subprocess.SubprocessError):
            self.failed.emit(BURN_FAILURE_MESSAGE)
            return ""
        finally:
            safe_subtitle_path.unlink(missing_ok=True)

        if result.returncode != 0 or not burned_output_path.exists():
            self.failed.emit(BURN_FAILURE_MESSAGE)
            return ""
        return str(burned_output_path)


def _snapshot_output_files(output_dir: Path) -> set[Path]:
    if not output_dir.exists():
        return set()
    return {path for path in output_dir.iterdir() if path.is_file()}


def _resolve_output_path(
    printed_path: str,
    output_template: Path,
    before_files: set[Path],
    expected_title: str = "",
) -> str:
    if printed_path and Path(printed_path).exists():
        return printed_path
    output_dir = output_template.parent
    if not output_dir.exists():
        return printed_path
    new_files = [path for path in output_dir.iterdir() if path.is_file() and path not in before_files]
    media_files = [path for path in new_files if path.suffix.casefold() not in {".vtt", ".srt", ".ass", ".json", ".part"}]
    candidates = media_files or new_files
    if not candidates:
        return printed_path
    if expected_title:
        matching_candidates = [path for path in candidates if _title_matches_path(expected_title, path)]
        if matching_candidates:
            return str(max(matching_candidates, key=lambda path: path.stat().st_mtime))
    return str(max(candidates, key=lambda path: path.stat().st_mtime))


def _title_matches_path(expected_title: str, path: Path) -> bool:
    expected = _path_match_text(expected_title)
    actual = _path_match_text(path.stem)
    if not expected or not actual:
        return False
    return expected in actual or actual in expected


def _path_match_text(value: str) -> str:
    return "".join(character.casefold() for character in value if character.isalnum())


SUBTITLE_SUFFIXES = {".ass", ".srt", ".vtt"}
BURN_FAILURE_MESSAGE = "字幕烧录失败，原始文件已保留；可改为下载字幕文件或嵌入字幕后重试。"


def _find_subtitle_file(media_path: Path, before_files: set[Path]) -> Path | None:
    output_dir = media_path.parent
    if not output_dir.exists():
        return None
    subtitle_files = [
        path
        for path in output_dir.iterdir()
        if path.is_file() and path not in before_files and path.suffix.casefold() in SUBTITLE_SUFFIXES
    ]
    preferred = [path for path in subtitle_files if path.stem == media_path.stem or path.stem.startswith(media_path.stem + ".")]
    candidates = preferred or subtitle_files
    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime)


def _burned_output_path(media_path: Path) -> Path:
    candidate = media_path.with_name(f"{media_path.stem}.burned.mp4")
    if not candidate.exists():
        return candidate
    index = 2
    while True:
        candidate = media_path.with_name(f"{media_path.stem}.burned-{index}.mp4")
        if not candidate.exists():
            return candidate
        index += 1


def _copy_subtitle_for_ffmpeg(subtitle_path: Path) -> Path:
    safe_path = subtitle_path.with_name(f".ytdl-burn-{uuid4().hex}{subtitle_path.suffix.casefold()}")
    shutil.copy2(subtitle_path, safe_path)
    return safe_path


def _burn_subtitle_command(ffmpeg_path: Path, media_path: Path, subtitle_path: Path, output_path: Path) -> list[str]:
    return [
        str(ffmpeg_path),
        "-y",
        "-i",
        str(media_path),
        "-vf",
        f"subtitles={subtitle_path.name}",
        "-c:a",
        "copy",
        str(output_path),
    ]


def _looks_like_output_path(text: str) -> bool:
    if not text:
        return False
    lower = text.casefold()
    if "cookies" in lower or lower.startswith("["):
        return False
    return bool(Path(text).suffix)


class PreviewUrlWorker(QObject):
    finished = Signal(str)
    failed = Signal(str)

    def __init__(self, request: PreviewUrlRequest, runner: AnalysisRunner = subprocess.run):
        super().__init__()
        self.request = request
        self._runner = runner

    @Slot()
    def run(self) -> None:
        command = YtdlpCommandBuilder(self.request.ytdlp_path).preview_url_command(
            self.request.url,
            self.request.format_id,
            self.request.cookies_path,
        )
        try:
            result = self._runner(
                command,
                capture_output=True,
                text=True,
                timeout=self.request.timeout_seconds,
                check=False,
            )
        except (OSError, subprocess.SubprocessError, subprocess.TimeoutExpired):
            self.failed.emit("预览不可用")
            return

        if result.returncode != 0:
            self.failed.emit("预览不可用")
            return

        preview_url = (result.stdout or "").splitlines()[0].strip() if result.stdout else ""
        if preview_url:
            self.finished.emit(preview_url)
        else:
            self.failed.emit("预览不可用")


FfmpegFinder = Callable[[], FfmpegStatus]


class FfmpegSearchWorker(QObject):
    finished = Signal(object)

    def __init__(self, finder: FfmpegFinder = find_ffmpeg):
        super().__init__()
        self._finder = finder

    @Slot()
    def run(self) -> None:
        self.finished.emit(self._finder())


@dataclass(frozen=True)
class YtdlpUpdateRequest:
    data_dir: Path
    active_path: Path
    tasks_running: bool = False


YtdlpUpdateRunner = Callable[[YtdlpUpdateRequest], UpdateOutcome]


class YtdlpUpdateWorker(QObject):
    finished = Signal(object)
    failed = Signal(str)

    def __init__(
        self,
        request: YtdlpUpdateRequest,
        runner: YtdlpUpdateRunner | None = None,
    ):
        super().__init__()
        self.request = request
        self._runner = runner or _default_ytdlp_update_runner

    @Slot()
    def run(self) -> None:
        try:
            self.finished.emit(self._runner(self.request))
        except (OSError, subprocess.SubprocessError, ValueError):
            self.failed.emit("yt-dlp 更新失败。")


def _default_ytdlp_update_runner(request: YtdlpUpdateRequest) -> UpdateOutcome:
    return download_and_install_latest_ytdlp(
        data_dir=request.data_dir,
        active_path=request.active_path,
        tasks_running=request.tasks_running,
    )
