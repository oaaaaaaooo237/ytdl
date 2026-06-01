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
    assert window.settings_page.choose_default_folder_button.text() == "选择默认保存位置"
    assert window.settings_page.choose_cookies_button.text() == "选择 cookies.txt"
    assert window.settings_page.clear_cookies_button.text() == "清除 cookies"
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


def test_choose_default_folder_updates_config(qtbot, app_data_dir: Path, tmp_path: Path):
    store = ConfigStore(app_data_dir)
    folder = tmp_path / "downloads"
    folder.mkdir()
    window = MainWindow(config_store=store, default_folder_picker=lambda: str(folder))
    qtbot.addWidget(window)

    window.choose_default_folder()

    assert window.settings_page.default_folder.text() == str(folder)
    assert store.load().default_save_dir == str(folder)


def test_concurrency_and_startup_update_settings_save_immediately(qtbot, app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(max_concurrency=2, check_ytdlp_updates_on_startup=True))
    window = MainWindow(config_store=store)
    qtbot.addWidget(window)

    window.settings_page.concurrency.setValue(4)
    window.settings_page.update_on_start.setChecked(False)

    config = store.load()
    assert config.max_concurrency == 4
    assert config.check_ytdlp_updates_on_startup is False


def test_choose_valid_cookies_path_updates_config_without_content(qtbot, app_data_dir: Path, tmp_path: Path):
    store = ConfigStore(app_data_dir)
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tFALSE\t0\tSID\tSECRET\n", encoding="utf-8")
    messages: list[tuple[str, str]] = []
    window = MainWindow(
        config_store=store,
        cookies_file_picker=lambda: str(cookie_file),
        information_dialog=lambda _parent, title, text: messages.append((title, text)),
    )
    qtbot.addWidget(window)

    window.choose_cookies_file()

    assert window.settings_page.cookies_path.text() == str(cookie_file)
    assert store.load().cookies_path == str(cookie_file)
    assert "SECRET" not in store.path.read_text(encoding="utf-8")
    assert messages == [("cookies.txt 已设置", "已保存 cookies.txt 文件路径。")]


def test_choose_invalid_cookies_path_does_not_update_config(qtbot, app_data_dir: Path, tmp_path: Path):
    store = ConfigStore(app_data_dir)
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text("not cookies", encoding="utf-8")
    messages: list[tuple[str, str]] = []
    window = MainWindow(
        config_store=store,
        cookies_file_picker=lambda: str(cookie_file),
        information_dialog=lambda _parent, title, text: messages.append((title, text)),
    )
    qtbot.addWidget(window)

    window.choose_cookies_file()

    assert window.settings_page.cookies_path.text() == ""
    assert store.load().cookies_path == ""
    assert messages
    assert messages[0][0] == "cookies.txt 无效"


def test_clear_cookies_path_updates_config(qtbot, app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.save(AppConfig(cookies_path="D:/secret/cookies.txt"))
    window = MainWindow(config_store=store)
    qtbot.addWidget(window)

    window.clear_cookies_file()

    assert window.settings_page.cookies_path.text() == ""
    assert store.load().cookies_path == ""


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
