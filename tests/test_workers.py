import subprocess
from pathlib import Path

from ytdl_gui.ytdlp_runner import AnalysisFailureKind
from ytdl_gui.workers import AnalysisRequest, AnalysisWorker, PreviewUrlRequest, PreviewUrlWorker, make_analysis_command


def make_request(tmp_path: Path, cookies_path: Path | None = None) -> AnalysisRequest:
    return AnalysisRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        cookies_path=cookies_path,
        timeout_seconds=60,
    )


def collect_worker_signals(worker: AnalysisWorker) -> dict[str, list[object]]:
    events: dict[str, list[object]] = {"finished": [], "failed": [], "canceled": []}
    worker.finished.connect(lambda payload: events["finished"].append(payload))
    worker.failed.connect(lambda message: events["failed"].append(message))
    worker.canceled.connect(lambda: events["canceled"].append(True))
    return events


def collect_preview_signals(worker: PreviewUrlWorker) -> dict[str, list[str]]:
    events: dict[str, list[str]] = {"finished": [], "failed": []}
    worker.finished.connect(lambda url: events["finished"].append(url))
    worker.failed.connect(lambda message: events["failed"].append(message))
    return events


def test_make_analysis_command_keeps_timeout_and_url(tmp_path: Path):
    request = make_request(tmp_path)

    command = make_analysis_command(request)

    assert str(request.ytdlp_path) == command[0]
    assert "--dump-single-json" in command
    assert request.timeout_seconds == 60
    assert request.url == command[-1]


def test_make_analysis_command_passes_cookie_path_only_in_argv(tmp_path: Path):
    cookies_path = tmp_path / "secret" / "cookies.txt"
    request = make_request(tmp_path, cookies_path=cookies_path)

    command = make_analysis_command(request)

    assert "--cookies" in command
    assert str(cookies_path) in command


def test_analysis_worker_emits_finished_dict_on_success(tmp_path: Path):
    def runner(command, **kwargs):
        return subprocess.CompletedProcess(command, 0, stdout='{"title": "Demo"}', stderr="")

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)

    worker.run()

    assert events["finished"] == [{"title": "Demo"}]
    assert events["failed"] == []
    assert events["canceled"] == []


def test_analysis_worker_timeout_emits_network_timeout(tmp_path: Path):
    def runner(command, **kwargs):
        raise subprocess.TimeoutExpired(command, kwargs["timeout"])

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)

    worker.run()

    assert events["failed"] == [AnalysisFailureKind.NETWORK_TIMEOUT.value]
    assert events["finished"] == []
    assert events["canceled"] == []


def test_analysis_worker_os_error_emits_unknown_failure(tmp_path: Path):
    def runner(command, **kwargs):
        raise OSError("cannot start process D:/secret/cookies.txt")

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)

    worker.run()

    assert events["failed"] == [AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value]
    assert events["finished"] == []
    assert events["canceled"] == []


def test_analysis_worker_subprocess_error_emits_unknown_failure(tmp_path: Path):
    def runner(command, **kwargs):
        raise subprocess.SubprocessError("subprocess failed D:/secret/cookies.txt")

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)

    worker.run()

    assert events["failed"] == [AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value]
    assert events["finished"] == []
    assert events["canceled"] == []


def test_analysis_worker_nonzero_stderr_is_categorized(tmp_path: Path):
    def runner(command, **kwargs):
        return subprocess.CompletedProcess(command, 1, stdout="", stderr="ERROR: Sign in to confirm your age")

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)

    worker.run()

    assert events["failed"] == [AnalysisFailureKind.LOGIN_REQUIRED.value]
    assert events["finished"] == []


def test_analysis_worker_pre_cancel_skips_runner(tmp_path: Path):
    calls: list[list[str]] = []

    def runner(command, **kwargs):
        calls.append(command)
        return subprocess.CompletedProcess(command, 0, stdout='{"title": "Demo"}', stderr="")

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)
    worker.cancel()

    worker.run()

    assert calls == []
    assert events["canceled"] == [True]
    assert events["finished"] == []
    assert events["failed"] == []


def test_analysis_worker_cancel_after_runner_returns_emits_canceled(tmp_path: Path):
    worker: AnalysisWorker

    def runner(command, **kwargs):
        worker.cancel()
        return subprocess.CompletedProcess(command, 0, stdout='{"title": "Demo"}', stderr="")

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)

    worker.run()

    assert events["canceled"] == [True]
    assert events["finished"] == []
    assert events["failed"] == []


def test_analysis_worker_invalid_json_emits_unknown_failure(tmp_path: Path):
    def runner(command, **kwargs):
        return subprocess.CompletedProcess(command, 0, stdout="not json", stderr="")

    worker = AnalysisWorker(make_request(tmp_path), runner=runner)
    events = collect_worker_signals(worker)

    worker.run()

    assert events["failed"] == [AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE.value]
    assert events["finished"] == []
    assert events["canceled"] == []


def test_preview_url_worker_emits_first_stream_url_and_passes_cookie_path(tmp_path: Path):
    calls: list[list[str]] = []
    cookies_path = tmp_path / "cookies.txt"

    def runner(command, **kwargs):
        calls.append(command)
        return subprocess.CompletedProcess(command, 0, stdout="https://media.example/stream.mp4\n", stderr="")

    request = PreviewUrlRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        format_id="18",
        cookies_path=cookies_path,
    )
    worker = PreviewUrlWorker(request, runner=runner)
    events = collect_preview_signals(worker)

    worker.run()

    assert events["finished"] == ["https://media.example/stream.mp4"]
    assert events["failed"] == []
    assert "-g" in calls[0]
    assert calls[0][calls[0].index("-f") + 1] == "18"
    assert str(cookies_path) in calls[0]


def test_preview_url_worker_reports_unavailable_without_sensitive_details(tmp_path: Path):
    def runner(command, **kwargs):
        return subprocess.CompletedProcess(command, 1, stdout="", stderr="D:/secret/cookies.txt failed")

    request = PreviewUrlRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        format_id="18",
        cookies_path=tmp_path / "secret" / "cookies.txt",
    )
    worker = PreviewUrlWorker(request, runner=runner)
    events = collect_preview_signals(worker)

    worker.run()

    assert events["finished"] == []
    assert events["failed"] == ["预览不可用"]
