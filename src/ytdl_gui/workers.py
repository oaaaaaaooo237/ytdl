import json
import subprocess
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any

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


def make_analysis_command(request: AnalysisRequest) -> list[str]:
    return YtdlpCommandBuilder(request.ytdlp_path).analysis_command(request.url, request.cookies_path)


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


@dataclass(frozen=True)
class DownloadRequest:
    url: str
    ytdlp_path: Path
    output_template: Path
    format_id: str
    cookies_path: Path | None = None
    subtitle_action: str = "none"
    ffmpeg_path: Path | None = None


@dataclass(frozen=True)
class PreviewUrlRequest:
    url: str
    ytdlp_path: Path
    format_id: str | None = None
    cookies_path: Path | None = None
    timeout_seconds: int = 30


class DownloadWorker(QObject):
    progress = Signal(object)
    finished = Signal()
    failed = Signal(str)

    def __init__(self, request: DownloadRequest, popen_factory: PopenFactory | None = None):
        super().__init__()
        self.request = request
        self._popen_factory = popen_factory or subprocess.Popen
        self._process = None
        self._canceled = False

    def cancel(self) -> None:
        self._canceled = True
        if self._process and self._process.poll() is None:
            self._process.terminate()

    @Slot()
    def run(self) -> None:
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

        stdout = getattr(self._process, "stdout", None)
        if stdout is not None:
            for line in stdout:
                if self._canceled:
                    break
                self.progress.emit(parse_progress_line(line.strip()))

        return_code = self._process.wait()
        if self._canceled:
            self.failed.emit("下载已取消。")
        elif return_code == 0:
            self.finished.emit()
        else:
            self.failed.emit(f"yt-dlp 下载失败，退出码 {return_code}")


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
