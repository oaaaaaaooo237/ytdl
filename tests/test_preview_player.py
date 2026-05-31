import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

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
    assert window.download_page.status_label.text() == "分析完成，可以开始下载。"
    assert "预览不可用" in window.download_page.preview_player.status.text()
    assert "下载仍可继续" in window.download_page.preview_player.status.text()
