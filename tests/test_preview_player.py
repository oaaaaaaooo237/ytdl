import os
import subprocess
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.player import PreviewPlayer, PreviewState, preview_failure_message


def test_preview_failure_message_keeps_download_available():
    message = preview_failure_message(PreviewState.UNAVAILABLE)

    assert "预览不可用" in message
    assert "下载仍可继续" in message


def test_preview_player_exposes_basic_controls(qtbot):
    player = PreviewPlayer()
    qtbot.addWidget(player)

    assert player.play_button.text() == "播放"
    assert player.pause_button.text() == "暂停"
    assert player.volume.minimum() == 0
    assert player.volume.maximum() == 100
    assert "预览" in player.status.text()


def test_preview_failure_does_not_clear_download_readiness(qtbot):
    window = MainWindow(worker_runner=lambda worker: worker.run())
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

    window.download_page.preview_player.show_unavailable()

    assert window.selected_format_id == "22"
    assert window.download_page.start_button.isEnabled()
    assert window.download_page.status_label.text() == "分析完成，请在格式页确认分辨率、码率后开始下载。"
    assert "预览不可用" in window.download_page.preview_player.status.text()
    assert "下载仍可继续" in window.download_page.preview_player.status.text()


def _preview_window(qtbot, app_data_dir: Path, preview_runner):
    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(default_save_dir=str(download_dir), active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=store,
        history_store=HistoryStore(app_data_dir),
        preview_runner=preview_runner,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {
                    "format_id": "18",
                    "height": 360,
                    "ext": "mp4",
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                    "fps": 30,
                }
            ],
        },
    )
    window.download_page.preview_checkbox.setChecked(True)
    return window


def test_start_preview_extracts_stream_url(qtbot, app_data_dir: Path):
    calls: list[list[str]] = []

    def preview_runner(command, **kwargs):
        calls.append(command)
        return subprocess.CompletedProcess(command, 0, stdout="https://media.example/preview.mp4\n", stderr="")

    window = _preview_window(qtbot, app_data_dir, preview_runner)

    window.start_preview()

    assert calls
    assert window.download_page.preview_player.state == PreviewState.LOADING
    assert window.download_page.preview_player.status.text() == "正在加载预览..."


def test_preview_stream_failure_does_not_clear_download_readiness(qtbot, app_data_dir: Path):
    def preview_runner(command, **kwargs):
        return subprocess.CompletedProcess(command, 1, stdout="", stderr="preview unavailable")

    window = _preview_window(qtbot, app_data_dir, preview_runner)

    window.start_preview()

    assert window.selected_format_id == "18"
    assert window.download_page.start_button.isEnabled()
    assert window.download_page.status_label.text() == "分析完成，请在格式页确认分辨率、码率后开始下载。"
    assert window.download_page.preview_player.state == PreviewState.UNAVAILABLE
    assert "预览不可用" in window.download_page.preview_player.status.text()
