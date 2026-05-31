from collections.abc import Callable

from PySide6.QtCore import QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import QFileDialog, QHBoxLayout, QListWidget, QMessageBox, QStackedWidget, QWidget

from ytdl_gui.config_store import AppConfig
from ytdl_gui.cookies import cookie_help_text
from ytdl_gui.ffmpeg import FfmpegStatus, ffmpeg_help_url, find_ffmpeg
from ytdl_gui.ui.pages.about_page import AboutPage
from ytdl_gui.ui.pages.download_page import DownloadPage
from ytdl_gui.ui.pages.formats_page import FormatsPage
from ytdl_gui.ui.pages.history_page import HistoryPage
from ytdl_gui.ui.pages.queue_page import QueuePage
from ytdl_gui.ui.pages.settings_page import SettingsPage


class MainWindow(QWidget):
    def __init__(
        self,
        config_store=None,
        history_store=None,
        ffmpeg_finder: Callable[[], FfmpegStatus] = find_ffmpeg,
        external_url_opener: Callable[[str], object] | None = None,
        information_dialog: Callable[[QWidget, str, str], object] | None = None,
        ffmpeg_file_picker: Callable[[], str] | None = None,
    ):
        super().__init__()
        self.setWindowTitle("视频地址提取器")
        self.resize(1120, 720)
        self.config_store = config_store
        self.history_store = history_store
        self._ffmpeg_finder = ffmpeg_finder
        self._external_url_opener = external_url_opener or self._open_external_url
        self._information_dialog = information_dialog or QMessageBox.information
        self._ffmpeg_file_picker = ffmpeg_file_picker or self._pick_ffmpeg_file

        self.nav = QListWidget()
        self.nav.setFixedWidth(168)
        self.nav.addItems(["下载", "格式", "队列", "历史", "设置", "关于"])

        self.stack = QStackedWidget()
        self.download_page = DownloadPage()
        self.formats_page = FormatsPage()
        self.queue_page = QueuePage()
        self.history_page = HistoryPage()
        self.settings_page = SettingsPage()
        self.about_page = AboutPage()

        pages = [
            self.download_page,
            self.formats_page,
            self.queue_page,
            self.history_page,
            self.settings_page,
            self.about_page,
        ]
        for page in pages:
            self.stack.addWidget(page)

        self.nav.currentRowChanged.connect(self.stack.setCurrentIndex)
        self.nav.setCurrentRow(0)

        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        layout.addWidget(self.nav)
        layout.addWidget(self.stack, 1)

        if self.config_store:
            self.settings_page.load_config(self.config_store.load())
        if self.history_store:
            self.history_page.load_records(self.history_store.list())

        self.connect_settings_actions()

    def connect_settings_actions(self) -> None:
        self.settings_page.cookies_help_button.clicked.connect(self.show_cookies_help)
        self.settings_page.find_ffmpeg_button.clicked.connect(self.search_ffmpeg)
        self.settings_page.choose_ffmpeg_button.clicked.connect(self.choose_ffmpeg)
        self.settings_page.ffmpeg_download_button.clicked.connect(self.open_ffmpeg_download)

    def show_cookies_help(self) -> None:
        self._information_dialog(self, "如何获取 cookies.txt", cookie_help_text())

    def open_ffmpeg_download(self) -> None:
        self._external_url_opener(ffmpeg_help_url())

    def choose_ffmpeg(self) -> None:
        selected_path = self._ffmpeg_file_picker()
        if not selected_path:
            return
        self._apply_ffmpeg_path(selected_path, f"已选择：{selected_path}")

    def search_ffmpeg(self) -> None:
        status = self._ffmpeg_finder()
        if status.found and status.path:
            self._apply_ffmpeg_path(str(status.path), f"已找到：{status.version}")
            return

        self.about_page.show_ffmpeg_missing_baseline()
        self._information_dialog(
            self,
            "未找到 ffmpeg",
            "未在 PATH 或常见目录中找到 ffmpeg.exe。基础下载功能仍可使用；如需合并、转码、嵌入字幕，请手动选择本机 ffmpeg.exe 或打开官网下载页。",
        )

    def _apply_ffmpeg_path(self, path: str, about_status: str) -> None:
        self.settings_page.set_ffmpeg_path(path)
        self.about_page.show_ffmpeg_found(about_status)
        self._save_settings_config()

    def _save_settings_config(self) -> None:
        if not self.config_store:
            return
        current = self.config_store.load()
        self.config_store.save(
            AppConfig(
                default_save_dir=self.settings_page.default_folder.text(),
                cookies_path=self.settings_page.cookies_path.text(),
                ffmpeg_path=self.settings_page.ffmpeg_path.text(),
                max_concurrency=self.settings_page.concurrency.value(),
                check_ytdlp_updates_on_startup=self.settings_page.update_on_start.isChecked(),
                active_ytdlp_path=current.active_ytdlp_path,
                active_ytdlp_version=current.active_ytdlp_version,
                rollback_ytdlp_path=current.rollback_ytdlp_path,
            )
        )

    @staticmethod
    def _open_external_url(url: str) -> bool:
        return QDesktopServices.openUrl(QUrl(url))

    def _pick_ffmpeg_file(self) -> str:
        path, _selected_filter = QFileDialog.getOpenFileName(
            self,
            "选择 ffmpeg.exe",
            "",
            "ffmpeg.exe (ffmpeg.exe);;可执行文件 (*.exe)",
        )
        return path
