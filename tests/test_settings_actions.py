import os
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.cookies import cookie_help_text
from ytdl_gui.ffmpeg import FfmpegStatus, find_ffmpeg, ffmpeg_help_url
from ytdl_gui.update_manager import OFFICIAL_YTDLP_EXE_URL
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.workers import FfmpegSearchWorker


def test_official_ytdlp_update_source_is_github_release():
    assert OFFICIAL_YTDLP_EXE_URL == "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"


def test_settings_buttons_expose_help_actions(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.settings_page.cookies_help_button.text() == "如何获取 cookies.txt"
    assert window.settings_page.find_ffmpeg_button.text() == "搜索 ffmpeg"
    assert window.settings_page.choose_ffmpeg_button.text() == "选择 ffmpeg.exe"
    assert window.settings_page.ffmpeg_download_button.text() == "打开 ffmpeg 官网下载页"


def test_cookies_help_action_uses_shared_help_text(qtbot):
    messages: list[tuple[str, str]] = []
    window = MainWindow(information_dialog=lambda _parent, title, text: messages.append((title, text)))
    qtbot.addWidget(window)

    window.show_cookies_help()

    assert messages == [("如何获取 cookies.txt", cookie_help_text())]


def test_ffmpeg_download_action_uses_shared_url(qtbot):
    opened: list[str] = []
    window = MainWindow(external_url_opener=opened.append)
    qtbot.addWidget(window)

    window.open_ffmpeg_download()

    assert opened == [ffmpeg_help_url()]


def test_choose_ffmpeg_action_updates_settings_about_and_config(qtbot, app_data_dir: Path, tmp_path: Path):
    store = ConfigStore(app_data_dir)
    exe = tmp_path / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")
    window = MainWindow(config_store=store, ffmpeg_file_picker=lambda: str(exe))
    qtbot.addWidget(window)

    window.choose_ffmpeg()

    assert window.settings_page.ffmpeg_path.text() == str(exe)
    assert window.about_page.ffmpeg_label.text() == f"ffmpeg 状态：已选择：{exe}"
    assert store.load().ffmpeg_path == str(exe)


def test_ffmpeg_search_is_user_triggered_and_updates_status(qtbot):
    calls: list[bool] = []
    found = FfmpegStatus(True, Path("D:/ffmpeg/bin/ffmpeg.exe"), "ffmpeg version test")
    window = MainWindow(ffmpeg_finder=lambda: calls.append(True) or found)
    qtbot.addWidget(window)

    assert calls == []

    window.search_ffmpeg()

    assert calls == [True]
    assert window.settings_page.ffmpeg_path.text() == "D:\\ffmpeg\\bin\\ffmpeg.exe"
    assert window.about_page.ffmpeg_label.text() == "ffmpeg 状态：已找到：ffmpeg version test"


def test_ffmpeg_search_missing_shows_chinese_baseline_message(qtbot):
    messages: list[tuple[str, str]] = []
    window = MainWindow(
        ffmpeg_finder=lambda: FfmpegStatus(False, None, ""),
        information_dialog=lambda _parent, title, text: messages.append((title, text)),
    )
    qtbot.addWidget(window)

    window.search_ffmpeg()

    assert window.settings_page.ffmpeg_path.text() == ""
    assert window.about_page.ffmpeg_label.text() == "ffmpeg 状态：未找到，基础下载功能仍可使用"
    assert messages == [
        (
            "未找到 ffmpeg",
            "未在 PATH 或常见目录中找到 ffmpeg.exe。基础下载功能仍可使用；如需合并、转码、嵌入字幕，请手动选择本机 ffmpeg.exe 或打开官网下载页。",
        )
    ]


def test_ffmpeg_search_can_use_user_data_dir(tmp_path: Path):
    exe = tmp_path / "ffmpeg" / "bin" / "ffmpeg.exe"
    exe.parent.mkdir(parents=True)
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(search_paths=[tmp_path], env_path="")

    assert status.found is True
    assert status.path == exe


def test_ffmpeg_search_worker_emits_status(tmp_path: Path):
    status = FfmpegStatus(True, tmp_path / "ffmpeg.exe", "ffmpeg worker")
    worker = FfmpegSearchWorker(finder=lambda: status)
    emitted: list[FfmpegStatus] = []
    worker.finished.connect(emitted.append)

    worker.run()

    assert emitted == [status]
