import os
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtWidgets import QPushButton

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.pages.queue_page import QueuePage


def _button(page: QueuePage, name: str) -> QPushButton:
    button = page.findChild(QPushButton, name)
    assert button is not None
    return button


def test_queue_card_buttons_emit_task_actions(qtbot):
    page = QueuePage()
    qtbot.addWidget(page)
    events: list[tuple[str, str]] = []
    page.task_action_requested.connect(lambda task_id, action: events.append((task_id, action)))

    page.add_task("task-1", "Demo Video", "失败")

    assert _button(page, "queue-pause-task-1").text() == "暂停"
    assert _button(page, "queue-cancel-task-1").text() == "取消"
    assert _button(page, "queue-retry-task-1").text() == "重试"

    _button(page, "queue-pause-task-1").click()
    _button(page, "queue-cancel-task-1").click()
    _button(page, "queue-retry-task-1").click()

    assert events == [("task-1", "pause"), ("task-1", "cancel"), ("task-1", "retry")]


def test_main_window_cancel_and_retry_queue_actions_restart_worker(qtbot, app_data_dir: Path):
    started_workers: list[object] = []
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(default_save_dir=str(app_data_dir / "downloads"), active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=store,
        worker_runner=lambda worker: started_workers.append(worker),
    )
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
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
    task_id = window.queue_page.table.verticalHeaderItem(0).text()

    window.queue_page.task_action_requested.emit(task_id, "retry")
    assert len(started_workers) == 1
    assert window.download_page.status_label.text() == "只能重试已暂停、已取消或失败的任务。"

    window.queue_page.task_action_requested.emit(task_id, "cancel")
    assert window.queue_page.table.item(0, 1).text() == "已取消"
    assert window.download_page.status_label.text() == "下载已取消。"

    window.queue_page.task_action_requested.emit(task_id, "retry")
    assert len(started_workers) == 2
    assert window.queue_page.table.item(0, 1).text() == "下载中"
    assert window.download_page.status_label.text() == "已重新开始下载。"
