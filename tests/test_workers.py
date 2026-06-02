import subprocess
from pathlib import Path

from ytdl_gui.ytdlp_runner import AnalysisFailureKind
from ytdl_gui.workers import (
    AnalysisRequest,
    AnalysisWorker,
    DownloadRequest,
    DownloadWorker,
    PlaylistProbeRequest,
    PlaylistProbeWorker,
    PreviewUrlRequest,
    PreviewUrlWorker,
    make_analysis_command,
)


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


def collect_playlist_probe_signals(worker: PlaylistProbeWorker) -> dict[str, list[object]]:
    events: dict[str, list[object]] = {"finished": [], "failed": []}
    worker.finished.connect(lambda payload: events["finished"].append(payload))
    worker.failed.connect(lambda message: events["failed"].append(message))
    return events


def collect_download_signals(worker: DownloadWorker) -> dict[str, list[str]]:
    events: dict[str, list[str]] = {"finished": [], "failed": []}
    worker.finished.connect(lambda output_path: events["finished"].append(output_path))
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


def test_playlist_probe_worker_emits_playlist_payload_and_uses_flat_playlist(tmp_path: Path):
    calls: list[list[str]] = []

    def runner(command, **kwargs):
        calls.append(command)
        return subprocess.CompletedProcess(
            command,
            0,
            stdout='{"entries": [{"url": "https://www.youtube.com/watch?v=a"}]}',
            stderr="",
        )

    request = PlaylistProbeRequest(
        url="https://www.youtube.com/playlist?list=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        cookies_path=tmp_path / "cookies.txt",
    )
    worker = PlaylistProbeWorker(request, runner=runner)
    events = collect_playlist_probe_signals(worker)

    worker.run()

    assert events["finished"] == [{"entries": [{"url": "https://www.youtube.com/watch?v=a"}]}]
    assert events["failed"] == []
    assert "--flat-playlist" in calls[0]
    assert str(request.cookies_path) in calls[0]


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


def test_download_worker_emits_actual_output_path_from_ytdlp_print(tmp_path: Path):
    output_path = tmp_path / "Demo Video.mp4"

    class FakeStdout:
        def __iter__(self):
            return iter(
                [
                    "[download]  50.0% of 10.00MiB at 1.00MiB/s ETA 00:05\n",
                    str(output_path) + "\n",
                ]
            )

    class FakeProcess:
        stdout = FakeStdout()

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    request = DownloadRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        output_template=tmp_path / "%(title)s.%(ext)s",
        format_id="18",
    )
    worker = DownloadWorker(request, popen_factory=lambda command, **kwargs: FakeProcess())
    events = collect_download_signals(worker)

    worker.run()

    assert events["finished"] == [str(output_path)]
    assert events["failed"] == []


def test_download_worker_falls_back_to_new_existing_file_when_printed_path_is_stale(tmp_path: Path):
    stale_path = tmp_path / "Demo Video.mp4"
    actual_path = tmp_path / "Demo ⧸ Video.mp4"

    class FakeStdout:
        def __iter__(self):
            return iter(
                [
                    "[download] 100.0% of 1.00MiB at 1.00MiB/s ETA 00:00\n",
                    str(stale_path) + "\n",
                ]
            )

    class FakeProcess:
        stdout = FakeStdout()

        def __init__(self):
            actual_path.write_bytes(b"media")

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    request = DownloadRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        output_template=tmp_path / "%(title)s.%(ext)s",
        format_id="18",
    )
    worker = DownloadWorker(request, popen_factory=lambda command, **kwargs: FakeProcess())
    events = collect_download_signals(worker)

    worker.run()

    assert events["finished"] == [str(actual_path)]
    assert events["failed"] == []
