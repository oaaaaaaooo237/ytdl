import os
import subprocess
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.ui.main_window import MainWindow
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


def test_empty_url_and_download_without_analysis_show_chinese_status(qtbot):
    window = MainWindow(worker_runner=lambda worker: worker.run())
    qtbot.addWidget(window)

    window.download_page.analyze_button.click()
    assert window.download_page.status_label.text() == "请输入视频地址。"

    window.download_page.start_button.click()
    assert window.download_page.status_label.text() == "请先分析视频，再开始下载。"


def test_analysis_entry_uses_injected_runner_and_saves_metadata(qtbot, app_data_dir: Path):
    calls: list[list[str]] = []

    def runner(command, **kwargs):
        calls.append(command)
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=(
                '{"title": "Demo Video", "duration": 125, '
                '"formats": [{"format_id": "22", "height": 720, "ext": "mp4", '
                '"vcodec": "avc1", "acodec": "mp4a", "fps": 30}]}'
            ),
            stderr="",
        )

    config = ConfigStore(app_data_dir)
    config.save(AppConfig(active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        analysis_runner=runner,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    window.download_page.url_input.setPlainText("https://example.test/watch?v=1")
    window.download_page.analyze_button.click()

    assert calls
    assert Path(calls[0][0]) == Path("D:/tools/yt-dlp.exe")
    assert window.analyzed_metadata["title"] == "Demo Video"
    assert window.selected_format_id == "22"
    assert window.download_page.title_label.text() == "标题：Demo Video"
    assert window.download_page.duration_label.text() == "时长：02:05"
    assert window.download_page.format_summary_label.text() == "格式：720p mp4 avc1/mp4a 30fps"
    assert window.formats_page.format_id_combo.currentText() == "22"


def test_analysis_respects_audio_only_mode(qtbot, app_data_dir: Path):
    config = ConfigStore(app_data_dir)
    config.save(AppConfig(active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        worker_runner=lambda worker: worker.run(),
        analysis_runner=lambda command, **kwargs: subprocess.CompletedProcess(
            command,
            0,
            stdout=(
                '{"title": "Demo Video", "duration": 30, '
                '"formats": ['
                '{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},'
                '{"format_id": "139", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.5", "abr": 49},'
                '{"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129}'
                "]} "
            ),
            stderr="",
        ),
    )
    qtbot.addWidget(window)

    window.download_page.mode_combo.setCurrentText("仅音频")
    window.download_page.url_input.setPlainText("https://example.test/watch?v=1")
    window.download_page.analyze_button.click()

    assert window.selected_format_id == "140"
    assert window.download_page.format_summary_label.text() == "格式：音频 m4a mp4a.40.2 129kbps"


def test_start_download_uses_injected_popen_updates_queue_and_history(qtbot, app_data_dir: Path):
    popen_calls: list[list[str]] = []

    class FakeStdout:
        def __iter__(self):
            return iter(
                [
                    "[download]  50.0% of 10.00MiB at 2.00MiB/s ETA 00:05\n",
                    "[download] 100.0% of 10.00MiB at 2.00MiB/s ETA 00:00\n",
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

    def popen_factory(command, **kwargs):
        popen_calls.append(command)
        return FakeProcess()

    config = ConfigStore(app_data_dir)
    config.save(
        AppConfig(
            default_save_dir=str(app_data_dir / "downloads"),
            cookies_path="D:/secret/cookies.txt",
            active_ytdlp_path="D:/tools/yt-dlp.exe",
        )
    )
    history = HistoryStore(app_data_dir)
    window = MainWindow(
        config_store=config,
        history_store=history,
        download_popen_factory=popen_factory,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "duration": 125,
            "formats": [
                {
                    "format_id": "22",
                    "height": 720,
                    "ext": "mp4",
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                    "fps": 30,
                }
            ],
        },
    )

    window.download_page.start_button.click()

    assert popen_calls
    assert "--cookies" in popen_calls[0]
    assert str(Path("D:/secret/cookies.txt")) in popen_calls[0]
    assert "D:/secret" not in window.download_page.status_label.text()
    assert window.queue_page.table.rowCount() == 1
    assert window.queue_page.table.item(0, 1).text() == "已完成"
    assert window.queue_page.table.item(0, 2).text() == "100.0%"
    records = history.list()
    assert len(records) == 1
    assert records[0].title == "Demo Video"
    assert records[0].format_summary == "720p mp4 avc1/mp4a 30fps"
    assert "cookies" not in history.path.read_text(encoding="utf-8").lower()
