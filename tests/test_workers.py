import subprocess
from pathlib import Path

import pytest

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


def test_analysis_workers_hide_child_console_windows_on_windows(tmp_path: Path):
    if not hasattr(subprocess, "CREATE_NO_WINDOW"):
        pytest.skip("CREATE_NO_WINDOW is only available on Windows")
    captured_kwargs: list[dict] = []

    def analysis_runner(command, **kwargs):
        captured_kwargs.append(kwargs)
        return subprocess.CompletedProcess(command, 0, stdout='{"title": "Demo"}', stderr="")

    def playlist_runner(command, **kwargs):
        captured_kwargs.append(kwargs)
        return subprocess.CompletedProcess(command, 0, stdout='{"entries": []}', stderr="")

    def preview_runner(command, **kwargs):
        captured_kwargs.append(kwargs)
        return subprocess.CompletedProcess(command, 0, stdout="https://media.example/stream.mp4\n", stderr="")

    AnalysisWorker(make_request(tmp_path), runner=analysis_runner).run()
    PlaylistProbeWorker(
        PlaylistProbeRequest(
            url="https://www.youtube.com/playlist?list=abc",
            ytdlp_path=tmp_path / "yt-dlp.exe",
            cookies_path=None,
        ),
        runner=playlist_runner,
    ).run()
    PreviewUrlWorker(
        PreviewUrlRequest(
            url="https://www.youtube.com/watch?v=abc",
            ytdlp_path=tmp_path / "yt-dlp.exe",
            format_id="18",
        ),
        runner=preview_runner,
    ).run()

    assert captured_kwargs
    assert all(kwargs["creationflags"] & subprocess.CREATE_NO_WINDOW for kwargs in captured_kwargs)


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


def test_download_worker_hides_child_console_window_on_windows(tmp_path: Path):
    if not hasattr(subprocess, "CREATE_NO_WINDOW"):
        pytest.skip("CREATE_NO_WINDOW is only available on Windows")
    captured_kwargs: dict = {}

    class FakeStdout:
        def __iter__(self):
            return iter(["[download] 100.0% of 1.00MiB at 1.00MiB/s ETA 00:00\n"])

    class FakeProcess:
        stdout = FakeStdout()

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    def popen_factory(command, **kwargs):
        captured_kwargs.update(kwargs)
        return FakeProcess()

    request = DownloadRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        output_template=tmp_path / "%(title)s.%(ext)s",
        format_id="18",
    )
    worker = DownloadWorker(request, popen_factory=popen_factory)

    worker.run()

    assert captured_kwargs["creationflags"] & subprocess.CREATE_NO_WINDOW


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


def test_download_worker_fallback_prefers_file_matching_expected_title(tmp_path: Path):
    expected_path = tmp_path / "Expected Video.m4a"
    other_path = tmp_path / "Other Video.m4a"

    class FakeStdout:
        def __iter__(self):
            return iter(["[download] 100.0% of 1.00MiB at 1.00MiB/s ETA 00:00\n"])

    class FakeProcess:
        stdout = FakeStdout()

        def __init__(self):
            expected_path.write_bytes(b"expected")
            other_path.write_bytes(b"other")

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            other_path.touch()
            return 0

    request = DownloadRequest(
        url="https://www.youtube.com/watch?v=expected",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        output_template=tmp_path / "%(title)s.%(ext)s",
        format_id="18",
        expected_title="Expected Video",
    )
    worker = DownloadWorker(request, popen_factory=lambda command, **kwargs: FakeProcess())
    events = collect_download_signals(worker)

    worker.run()

    assert events["finished"] == [str(expected_path)]
    assert events["failed"] == []


def test_download_worker_burns_downloaded_subtitle_to_new_output(tmp_path: Path):
    media_path = tmp_path / "Demo Video.mp4"
    subtitle_path = tmp_path / "Demo Video.en.srt"
    burned_path = tmp_path / "Demo Video.burned.mp4"
    ffmpeg_calls: list[list[str]] = []

    class FakeStdout:
        def __iter__(self):
            return iter(
                [
                    "[download] 100.0% of 1.00MiB at 1.00MiB/s ETA 00:00\n",
                    str(media_path) + "\n",
                ]
            )

    class FakeProcess:
        stdout = FakeStdout()

        def __init__(self):
            media_path.write_bytes(b"media")
            subtitle_path.write_text("1\n00:00:00,000 --> 00:00:01,000\nDemo\n", encoding="utf-8")

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    def ffmpeg_runner(command, **kwargs):
        ffmpeg_calls.append(command)
        burned_path.write_bytes(b"burned")
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

    request = DownloadRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        output_template=tmp_path / "%(title)s.%(ext)s",
        format_id="18",
        subtitle_action="burn",
        ffmpeg_path=tmp_path / "ffmpeg.exe",
    )
    worker = DownloadWorker(
        request,
        popen_factory=lambda command, **kwargs: FakeProcess(),
        ffmpeg_runner=ffmpeg_runner,
    )
    events = collect_download_signals(worker)

    worker.run()

    assert events["finished"] == [str(burned_path)]
    assert events["failed"] == []
    assert ffmpeg_calls
    assert str(media_path) in ffmpeg_calls[0]
    assert str(burned_path) in ffmpeg_calls[0]
    assert "-vf" in ffmpeg_calls[0]
    assert media_path.exists()
    assert subtitle_path.exists()


def test_download_worker_preserves_original_media_when_burn_fails(tmp_path: Path):
    media_path = tmp_path / "Demo Video.mp4"
    subtitle_path = tmp_path / "Demo Video.en.srt"

    class FakeStdout:
        def __iter__(self):
            return iter([str(media_path) + "\n"])

    class FakeProcess:
        stdout = FakeStdout()

        def __init__(self):
            media_path.write_bytes(b"media")
            subtitle_path.write_text("1\n00:00:00,000 --> 00:00:01,000\nDemo\n", encoding="utf-8")

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    def ffmpeg_runner(command, **kwargs):
        return subprocess.CompletedProcess(command, 1, stdout="", stderr="burn failed")

    request = DownloadRequest(
        url="https://www.youtube.com/watch?v=abc",
        ytdlp_path=tmp_path / "yt-dlp.exe",
        output_template=tmp_path / "%(title)s.%(ext)s",
        format_id="18",
        subtitle_action="burn",
        ffmpeg_path=tmp_path / "ffmpeg.exe",
    )
    worker = DownloadWorker(
        request,
        popen_factory=lambda command, **kwargs: FakeProcess(),
        ffmpeg_runner=ffmpeg_runner,
    )
    events = collect_download_signals(worker)

    worker.run()

    assert events["finished"] == []
    assert events["failed"] == ["字幕烧录失败，原始文件已保留；可改为下载字幕文件或嵌入字幕后重试。"]
    assert media_path.exists()
