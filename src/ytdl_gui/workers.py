import json
import subprocess
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PySide6.QtCore import QObject, Signal, Slot

from ytdl_gui.ytdlp_runner import AnalysisFailureKind, YtdlpCommandBuilder, categorize_analysis_error


@dataclass(frozen=True)
class AnalysisRequest:
    url: str
    ytdlp_path: Path
    cookies_path: Path | None
    timeout_seconds: int = 60


AnalysisRunner = Callable[..., subprocess.CompletedProcess[str]]


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
