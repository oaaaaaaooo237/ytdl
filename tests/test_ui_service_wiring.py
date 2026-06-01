import os
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import subprocess

from PySide6.QtWidgets import QPushButton

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryRecord, HistoryStore
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.update_manager import UpdateOutcome, UpdateResult


def test_settings_page_loads_config_and_displays_cookie_path_only(qtbot, app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.save(
        AppConfig(
            default_save_dir="D:/Downloads",
            cookies_path="D:/secrets/cookies.txt",
            ffmpeg_path="D:/ffmpeg/bin/ffmpeg.exe",
            max_concurrency=4,
            check_ytdlp_updates_on_startup=False,
        )
    )

    window = MainWindow(config_store=store, history_store=HistoryStore(app_data_dir))
    qtbot.addWidget(window)

    assert window.config_store is store
    assert window.settings_page.default_folder.text() == "D:/Downloads"
    assert window.settings_page.cookies_path.text() == "D:/secrets/cookies.txt"
    assert "SID=" not in window.settings_page.cookies_path.text()
    assert window.settings_page.ffmpeg_path.text() == "D:/ffmpeg/bin/ffmpeg.exe"
    assert window.settings_page.concurrency.value() == 4
    assert window.settings_page.update_on_start.isChecked() is False


def test_history_page_renders_history_without_sensitive_fields(qtbot, app_data_dir: Path):
    history = HistoryStore(app_data_dir)
    history.add(
        HistoryRecord(
            "Title",
            "https://example.test/watch?v=1",
            "D:/file.mp4",
            "audio_video",
            "720p mp4",
            "none",
            "finished",
            "2026-05-30T22:00:00",
        )
    )

    window = MainWindow(config_store=ConfigStore(app_data_dir), history_store=history)
    qtbot.addWidget(window)

    assert window.history_store is history
    assert window.history_page.table.rowCount() == 1
    assert window.queue_page.recent_history_table.rowCount() == 1
    assert window.queue_page.recent_history_table.item(0, 0).text() == "Title"
    assert window.queue_page.recent_history_table.item(0, 2).text() == "已完成"
    values = [window.history_page.table.item(0, column).text() for column in range(5)]
    assert values == ["Title", "audio_video", "720p mp4", "已完成", "2026-05-30T22:00:00"]
    assert history_action_button(window, 0, "打开").text() == "打开"
    visible_text = " ".join(values)
    assert "cookies" not in visible_text.lower()
    assert "token" not in visible_text.lower()
    assert "--cookies" not in visible_text


def history_action_button(window: MainWindow, row: int, text: str) -> QPushButton:
    widget = window.history_page.table.cellWidget(row, 5)
    assert widget is not None
    for button in widget.findChildren(QPushButton):
        if button.text() == text:
            return button
    raise AssertionError(f"missing history action button: {text}")


def test_history_row_actions_open_redownload_and_delete(qtbot, app_data_dir: Path):
    output_path = app_data_dir / "downloads" / "demo.mp4"
    output_path.parent.mkdir()
    output_path.write_text("fake", encoding="utf-8")
    history = HistoryStore(app_data_dir)
    history.add(
        HistoryRecord(
            "Demo",
            "https://example.test/watch?v=demo",
            str(output_path),
            "音频+视频",
            "360p mp4",
            "不下载",
            "finished",
            "2026-06-01T22:00:00",
        )
    )
    opened: list[str] = []
    analysis_urls: list[str] = []

    def analysis_runner(command, **kwargs):
        analysis_urls.append(command[-1])
        return subprocess.CompletedProcess(
            command,
            0,
            stdout='{"title": "Demo", "formats": [{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}]}',
            stderr="",
        )

    window = MainWindow(
        config_store=ConfigStore(app_data_dir),
        history_store=history,
        external_url_opener=opened.append,
        analysis_runner=analysis_runner,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    history_action_button(window, 0, "打开").click()
    history_action_button(window, 0, "目录").click()
    history_action_button(window, 0, "重下").click()
    history_action_button(window, 0, "删除").click()

    assert opened[0] == output_path.as_uri()
    assert opened[1] == output_path.parent.as_uri()
    assert analysis_urls == ["https://example.test/watch?v=demo"]
    assert window.download_page.url_input.toPlainText() == "https://example.test/watch?v=demo"
    assert history.list() == []
    assert window.history_page.table.rowCount() == 0


def test_history_page_load_records_clears_existing_rows(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    first = HistoryRecord("Old", "url", "D:/old.mp4", "video", "480p", "none", "finished", "2026-05-30T20:00:00")
    second = HistoryRecord("New", "url", "D:/new.mp4", "audio", "m4a", "none", "failed", "2026-05-30T21:00:00")

    window.history_page.load_records([first])
    window.history_page.load_records([second])

    assert window.history_page.table.rowCount() == 1
    assert window.history_page.table.item(0, 0).text() == "New"
    assert window.history_page.table.item(0, 3).text() == "失败"


def test_history_search_filters_visible_records(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    first = HistoryRecord("Subaru review", "url", "D:/car.m4a", "audio", "m4a", "none", "finished", "2026-05-30T20:00:00")
    second = HistoryRecord("Music video", "url", "D:/music.mp4", "video", "720p", "none", "finished", "2026-05-30T21:00:00")

    window.history_page.load_records([first, second])
    window.history_page.search.setText("subaru")

    assert window.history_page.table.rowCount() == 1
    assert window.history_page.table.item(0, 0).text() == "Subaru review"


def test_clear_history_action_updates_store_and_table(qtbot, app_data_dir: Path):
    history = HistoryStore(app_data_dir)
    history.add(HistoryRecord("Title", "url", "D:/file.mp4", "video", "720p", "none", "finished", "2026-05-30T20:00:00"))
    window = MainWindow(config_store=ConfigStore(app_data_dir), history_store=history)
    qtbot.addWidget(window)

    window.clear_history()

    assert history.list() == []
    assert window.history_page.table.rowCount() == 0


def test_open_download_folder_uses_configured_folder(qtbot, app_data_dir: Path):
    opened: list[str] = []
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(default_save_dir="D:/Downloads"))
    window = MainWindow(config_store=store, external_url_opener=opened.append)
    qtbot.addWidget(window)

    window.open_download_folder()

    assert opened == ["file:///D:/Downloads"]


def test_main_window_can_construct_without_stores(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.config_store is None
    assert window.history_store is None
    assert window.settings_page.concurrency.value() == 2
    assert window.history_page.table.rowCount() == 0


def test_about_page_status_loaders(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    window.about_page.load_status(ytdlp_status="yt-dlp 2026.05.30", ffmpeg_status="ffmpeg 未配置")
    assert window.about_page.ytdlp_label.text() == "yt-dlp 状态：yt-dlp 2026.05.30"
    assert window.about_page.ffmpeg_label.text() == "ffmpeg 状态：ffmpeg 未配置"

    window.about_page.set_ytdlp_status("待检测")
    window.about_page.set_ffmpeg_status("已配置")
    assert window.about_page.ytdlp_label.text() == "yt-dlp 状态：待检测"
    assert window.about_page.ffmpeg_label.text() == "ffmpeg 状态：已配置"


def test_app_run_injects_config_and_history_stores(monkeypatch, app_data_dir: Path):
    import ytdl_gui.app as app_module

    created = {}

    class FakeApplication:
        def __init__(self, argv):
            self.argv = argv

        @staticmethod
        def instance():
            return None

        def exec(self):
            return 0

    class FakeWindow:
        def __init__(self, config_store=None, history_store=None):
            created["config_store"] = config_store
            created["history_store"] = history_store

        def show(self):
            created["shown"] = True

        def maybe_startup_update_check(self):
            created["startup_update_checked"] = True

    monkeypatch.setattr(app_module, "QApplication", FakeApplication)
    monkeypatch.setattr(app_module, "MainWindow", FakeWindow)
    monkeypatch.setattr(app_module, "apply_light_theme", lambda app: created.setdefault("theme_applied", True))
    monkeypatch.setattr(app_module, "app_data_dir", lambda: app_data_dir)

    assert app_module.run() == 0
    assert created["theme_applied"] is True
    assert created["shown"] is True
    assert created["startup_update_checked"] is True
    assert isinstance(created["config_store"], ConfigStore)
    assert isinstance(created["history_store"], HistoryStore)
    assert created["config_store"].path == app_data_dir / "config.json"
    assert created["history_store"].path == app_data_dir / "history.json"


def test_footer_update_button_runs_update_worker_and_updates_config(qtbot, app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(active_ytdlp_path="D:/old/yt-dlp.exe", active_ytdlp_version="2026.03.17"))

    def update_runner(request):
        return UpdateOutcome(
            result=UpdateResult.UPDATED,
            active_path="D:/new/yt-dlp.exe",
            active_version="2026.06.01",
            rollback_path=str(request.active_path),
            message="已更新到 2026.06.01",
        )

    window = MainWindow(
        config_store=store,
        history_store=HistoryStore(app_data_dir),
        ytdlp_update_runner=update_runner,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    window.footer_update_button.click()

    config = store.load()
    assert config.active_ytdlp_path == "D:/new/yt-dlp.exe"
    assert config.active_ytdlp_version == "2026.06.01"
    assert Path(config.rollback_ytdlp_path) == Path("D:/old/yt-dlp.exe")
    assert window.ytdlp_footer_label.text() == "yt-dlp 2026.06.01"
    assert window.footer_status_label.text() == "已更新"


def test_startup_update_check_schedules_worker_without_running_inline(qtbot, app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(check_ytdlp_updates_on_startup=True))
    scheduled_workers: list[object] = []

    window = MainWindow(
        config_store=store,
        history_store=HistoryStore(app_data_dir),
        ytdlp_update_runner=lambda request: UpdateOutcome(result=UpdateResult.INVALID_DOWNLOAD, message="not run"),
        worker_runner=scheduled_workers.append,
    )
    qtbot.addWidget(window)

    window.maybe_startup_update_check()

    assert len(scheduled_workers) == 1
    assert window.footer_status_label.text() == "检查中"
