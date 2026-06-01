from collections.abc import Callable
from datetime import datetime
import os
from pathlib import Path
from uuid import uuid4

from PySide6.QtCore import Qt, QThread, QUrl
from PySide6.QtGui import QDesktopServices, QPixmap
from PySide6.QtNetwork import QNetworkAccessManager, QNetworkRequest
from PySide6.QtWidgets import QFileDialog, QFrame, QHBoxLayout, QLabel, QListWidget, QMessageBox, QPushButton, QStackedWidget, QVBoxLayout, QWidget

from ytdl_gui.config_store import AppConfig
from ytdl_gui.cookies import cookie_help_text, validate_netscape_cookies
from ytdl_gui.ffmpeg import FfmpegStatus, ffmpeg_help_url, find_ffmpeg
from ytdl_gui.format_selector import FormatPreference, choose_format
from ytdl_gui.history_store import HistoryRecord
from ytdl_gui.paths import bundled_ytdlp_path, resource_root
from ytdl_gui.subtitles import subtitle_action_requires_ffmpeg
from ytdl_gui.update_manager import UpdateOutcome, UpdateResult
from ytdl_gui.ui.pages.about_page import AboutPage
from ytdl_gui.ui.pages.download_page import DownloadPage
from ytdl_gui.ui.pages.formats_page import FormatsPage
from ytdl_gui.ui.pages.history_page import HistoryPage
from ytdl_gui.ui.pages.queue_page import QueuePage
from ytdl_gui.ui.pages.settings_page import SettingsPage
from ytdl_gui.workers import (
    AnalysisRequest,
    AnalysisWorker,
    DownloadRequest,
    DownloadWorker,
    FfmpegSearchWorker,
    PlaylistProbeRequest,
    PlaylistProbeWorker,
    PreviewUrlRequest,
    PreviewUrlWorker,
)
from ytdl_gui.workers import YtdlpUpdateRequest, YtdlpUpdateRunner, YtdlpUpdateWorker
from ytdl_gui.ytdlp_runner import ProgressEvent, extract_playlist_urls, playlist_limit_message


class MainWindow(QWidget):
    def __init__(
        self,
        config_store=None,
        history_store=None,
        ffmpeg_finder: Callable[[], FfmpegStatus] = find_ffmpeg,
        external_url_opener: Callable[[str], object] | None = None,
        information_dialog: Callable[[QWidget, str, str], object] | None = None,
        ffmpeg_file_picker: Callable[[], str] | None = None,
        default_folder_picker: Callable[[], str] | None = None,
        cookies_file_picker: Callable[[], str] | None = None,
        analysis_runner=None,
        download_popen_factory=None,
        preview_runner=None,
        playlist_probe_runner=None,
        ytdlp_update_runner: YtdlpUpdateRunner | None = None,
        worker_runner: Callable[[object], object] | None = None,
        save_folder_picker: Callable[[], str] | None = None,
        confirmation_dialog: Callable[[QWidget, str, str], bool] | None = None,
        ):
        super().__init__()
        self.setWindowTitle("视频地址提取器")
        self.resize(590, 883)
        self.setMinimumSize(488, 760)
        self.config_store = config_store
        self.history_store = history_store
        self.analyzed_url = ""
        self.analyzed_metadata: dict = {}
        self.analyzed_results: dict[str, dict] = {}
        self.selected_format_id = ""
        self.selected_format_summary = ""
        self.save_folder_path = ""
        self._download_workers_by_task: dict[str, DownloadWorker] = {}
        self._download_requests_by_task: dict[str, DownloadRequest] = {}
        self._download_context_by_task: dict[str, dict[str, object]] = {}
        self._download_task_states: dict[str, str] = {}
        self._pending_download_task_ids: list[str] = []
        self._pending_download_status_by_task: dict[str, str] = {}
        self._threads: list[QThread] = []
        self._workers: list[object] = []
        self._ffmpeg_finder = ffmpeg_finder
        self._external_url_opener = external_url_opener or self._open_external_url
        self._information_dialog = information_dialog or QMessageBox.information
        self._ffmpeg_file_picker = ffmpeg_file_picker or self._pick_ffmpeg_file
        self._default_folder_picker = default_folder_picker or self._pick_default_folder
        self._cookies_file_picker = cookies_file_picker or self._pick_cookies_file
        self._analysis_runner = analysis_runner
        self._download_popen_factory = download_popen_factory
        self._preview_runner = preview_runner
        self._playlist_probe_runner = playlist_probe_runner
        self._ytdlp_update_runner = ytdlp_update_runner
        self._worker_runner = worker_runner or self._run_worker_in_thread
        self._save_folder_picker = save_folder_picker
        self._confirmation_dialog = confirmation_dialog or self._ask_confirmation
        self._network_manager = QNetworkAccessManager(self)

        self.nav = QListWidget()
        self.nav.setFixedWidth(80)
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

        self.title_bar = self._build_title_bar()
        self.bottom_status_bar = self._build_bottom_status_bar()

        body_layout = QHBoxLayout()
        body_layout.setContentsMargins(0, 0, 0, 0)
        body_layout.setSpacing(0)
        body_layout.addWidget(self.nav)
        body_layout.addWidget(self.stack, 1)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        layout.addWidget(self.title_bar)
        layout.addLayout(body_layout, 1)
        layout.addWidget(self.bottom_status_bar)

        if self.config_store:
            self.settings_page.load_config(self.config_store.load())
        if self.history_store:
            records = self.history_store.list()
            self.history_page.load_records(records)
            self.queue_page.load_history_records(records)

        self.connect_settings_actions()
        self.connect_download_actions()
        self.connect_history_actions()
        self.connect_queue_actions()
        self.connect_update_actions()
        self.connect_format_actions()
        self.connect_about_actions()

    def _build_title_bar(self) -> QFrame:
        frame = QFrame()
        frame.setObjectName("titleBar")
        frame.setFixedHeight(48)
        layout = QHBoxLayout(frame)
        layout.setContentsMargins(14, 0, 14, 0)
        layout.setSpacing(8)

        self.app_icon_label = QLabel("↓")
        self.app_icon_label.setObjectName("appIcon")
        self.app_icon_label.setFixedSize(22, 22)
        self.app_icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.app_title_label = QLabel("视频地址提取器")
        self.app_title_label.setObjectName("appTitle")

        layout.addWidget(self.app_icon_label)
        layout.addWidget(self.app_title_label)
        layout.addStretch()
        for text in ("－", "□", "×"):
            button = QPushButton(text)
            button.setObjectName("windowChromeButton")
            button.setFixedSize(32, 28)
            layout.addWidget(button)
        return frame

    def _build_bottom_status_bar(self) -> QFrame:
        frame = QFrame()
        frame.setObjectName("bottomStatusBar")
        frame.setFixedHeight(44)
        layout = QHBoxLayout(frame)
        layout.setContentsMargins(14, 0, 14, 0)
        layout.setSpacing(10)

        self.ytdlp_footer_label = QLabel(self._footer_ytdlp_text())
        self.ytdlp_footer_label.setObjectName("footerMuted")
        self.footer_status_dot = QLabel("●")
        self.footer_status_dot.setObjectName("footerStatusDot")
        self.footer_status_label = QLabel("正常")
        self.footer_status_label.setObjectName("footerMuted")
        self.footer_update_button = QPushButton("检查更新")
        self.footer_update_button.setObjectName("footerButton")

        layout.addWidget(self.ytdlp_footer_label)
        layout.addSpacing(18)
        layout.addWidget(self.footer_status_dot)
        layout.addWidget(self.footer_status_label)
        layout.addStretch()
        layout.addWidget(self.footer_update_button)
        return frame

    def _footer_ytdlp_text(self) -> str:
        if self.config_store:
            version = self.config_store.load().active_ytdlp_version
            if version:
                return f"yt-dlp {version}"
        return "yt-dlp 待检测"

    def connect_settings_actions(self) -> None:
        self.settings_page.choose_default_folder_button.clicked.connect(self.choose_default_folder)
        self.settings_page.choose_cookies_button.clicked.connect(self.choose_cookies_file)
        self.settings_page.clear_cookies_button.clicked.connect(self.clear_cookies_file)
        self.settings_page.cookies_help_button.clicked.connect(self.show_cookies_help)
        self.settings_page.find_ffmpeg_button.clicked.connect(self.search_ffmpeg)
        self.settings_page.choose_ffmpeg_button.clicked.connect(self.choose_ffmpeg)
        self.settings_page.ffmpeg_download_button.clicked.connect(self.open_ffmpeg_download)
        self.settings_page.concurrency.valueChanged.connect(lambda _value: self._save_settings_config())
        self.settings_page.update_on_start.toggled.connect(lambda _checked: self._save_settings_config())

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
        self.download_page.set_status("正在后台搜索本机 ffmpeg...")
        worker = FfmpegSearchWorker(self._ffmpeg_finder)
        worker.finished.connect(self.apply_ffmpeg_search_result)
        self._worker_runner(worker)

    def apply_ffmpeg_search_result(self, status: FfmpegStatus) -> None:
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

    @staticmethod
    def _ask_confirmation(parent: QWidget, title: str, text: str) -> bool:
        return (
            QMessageBox.question(
                parent,
                title,
                text,
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                QMessageBox.StandardButton.No,
            )
            == QMessageBox.StandardButton.Yes
        )

    def _pick_ffmpeg_file(self) -> str:
        path, _selected_filter = QFileDialog.getOpenFileName(
            self,
            "选择 ffmpeg.exe",
            "",
            "ffmpeg.exe (ffmpeg.exe);;可执行文件 (*.exe)",
        )
        return path

    def _pick_default_folder(self) -> str:
        return QFileDialog.getExistingDirectory(self, "选择默认保存位置")

    def _pick_cookies_file(self) -> str:
        path, _selected_filter = QFileDialog.getOpenFileName(
            self,
            "选择 cookies.txt",
            "",
            "cookies.txt (cookies.txt);;文本文件 (*.txt)",
        )
        return path

    def choose_default_folder(self) -> None:
        selected_path = self._default_folder_picker()
        if not selected_path:
            return
        self.settings_page.set_default_folder(selected_path)
        self._save_settings_config()

    def choose_cookies_file(self) -> None:
        selected_path = self._cookies_file_picker()
        if not selected_path:
            return
        result = validate_netscape_cookies(Path(selected_path))
        if not result.ok:
            self._information_dialog(self, "cookies.txt 无效", result.message)
            return
        self.settings_page.set_cookies_path(selected_path)
        self._save_settings_config()
        self._information_dialog(self, "cookies.txt 已设置", "已保存 cookies.txt 文件路径。")

    def clear_cookies_file(self) -> None:
        self.settings_page.set_cookies_path("")
        self._save_settings_config()

    def connect_download_actions(self) -> None:
        self.download_page.analyze_button.clicked.connect(self.start_analysis)
        self.download_page.save_folder_button.clicked.connect(self.choose_save_folder)
        self.download_page.start_button.clicked.connect(self.start_download)

    def connect_history_actions(self) -> None:
        self.history_page.clear_button.clicked.connect(self.clear_history)
        self.history_page.open_folder_button.clicked.connect(self.open_download_folder)
        self.history_page.history_action_requested.connect(self.handle_history_action)

    def connect_queue_actions(self) -> None:
        self.queue_page.task_action_requested.connect(self.handle_queue_action)
        self.queue_page.pause_all_button.clicked.connect(self.pause_all_tasks)
        self.queue_page.clear_completed_button.clicked.connect(self.clear_completed_queue_tasks)

    def connect_update_actions(self) -> None:
        self.footer_update_button.clicked.connect(lambda: self.check_ytdlp_updates(silent=False))

    def connect_format_actions(self) -> None:
        self.formats_page.subtitle_combo.currentTextChanged.connect(self.validate_subtitle_option)

    def connect_about_actions(self) -> None:
        self.about_page.legal_button.clicked.connect(self.open_legal_notices)

    def open_legal_notices(self) -> None:
        notice_path = resource_root() / "licenses" / "THIRD_PARTY_NOTICES.txt"
        if not notice_path.exists():
            self._information_dialog(self, "未找到第三方许可", "第三方许可文件未包含在当前程序目录中。")
            return
        self._external_url_opener(QUrl.fromLocalFile(str(notice_path)).toString())

    def validate_subtitle_option(self, text: str) -> None:
        if not subtitle_action_requires_ffmpeg(_subtitle_action_value(text)):
            return
        if self._has_ffmpeg_configured():
            return
        self.about_page.show_ffmpeg_missing_baseline()
        self.download_page.set_status("嵌入或烧录字幕需要 ffmpeg；请在设置中搜索或选择 ffmpeg.exe。")

    def maybe_startup_update_check(self) -> None:
        if not self.config_store:
            return
        if not self.config_store.load().check_ytdlp_updates_on_startup:
            return
        self.check_ytdlp_updates(silent=True)

    def check_ytdlp_updates(self, silent: bool = False) -> None:
        if not self.config_store:
            if not silent:
                self.footer_status_label.setText("未配置")
            return
        self.footer_status_label.setText("检查中")
        request = YtdlpUpdateRequest(
            data_dir=self.config_store.path.parent,
            active_path=self._active_ytdlp_path(),
            tasks_running=self._has_running_download_tasks(),
        )
        worker = YtdlpUpdateWorker(request, runner=self._ytdlp_update_runner)
        worker.finished.connect(lambda outcome, quiet=silent: self.apply_ytdlp_update_outcome(outcome, quiet))
        worker.failed.connect(lambda message, quiet=silent: self.show_ytdlp_update_failure(message, quiet))
        self._worker_runner(worker)

    def apply_ytdlp_update_outcome(self, outcome: UpdateOutcome, silent: bool = False) -> None:
        if outcome.result == UpdateResult.UPDATED:
            self._save_ytdlp_update_config(outcome)
            self.ytdlp_footer_label.setText(f"yt-dlp {outcome.active_version}")
            self.about_page.set_ytdlp_status(f"yt-dlp {outcome.active_version}")
            self.footer_status_label.setText("已更新")
            if not silent:
                self.download_page.set_status(outcome.message or "yt-dlp 更新完成。")
            return
        if outcome.result == UpdateResult.BUSY:
            self.footer_status_label.setText("稍后更新")
            if not silent:
                self.download_page.set_status(outcome.message or "下载任务运行中，稍后再更新 yt-dlp。")
            return
        self.footer_status_label.setText("无需更新" if silent else "更新失败")
        if not silent:
            self.download_page.set_status(outcome.message or "yt-dlp 更新失败或验证未通过。")

    def show_ytdlp_update_failure(self, message: str, silent: bool = False) -> None:
        self.footer_status_label.setText("更新失败")
        if not silent:
            self.download_page.set_status(message)

    def _save_ytdlp_update_config(self, outcome: UpdateOutcome) -> None:
        if not self.config_store:
            return
        current = self.config_store.load()
        self.config_store.save(
            AppConfig(
                default_save_dir=current.default_save_dir,
                cookies_path=current.cookies_path,
                ffmpeg_path=current.ffmpeg_path,
                max_concurrency=current.max_concurrency,
                check_ytdlp_updates_on_startup=current.check_ytdlp_updates_on_startup,
                active_ytdlp_path=outcome.active_path,
                active_ytdlp_version=outcome.active_version,
                rollback_ytdlp_path=outcome.rollback_path,
            )
        )

    def _has_running_download_tasks(self) -> bool:
        return any(state in {"running", "pause_requested", "cancel_requested"} for state in self._download_task_states.values())

    def _has_ffmpeg_configured(self) -> bool:
        return self._configured_ffmpeg_path() is not None

    def _configured_ffmpeg_path(self) -> Path | None:
        path = self.settings_page.ffmpeg_path.text().strip()
        if not path and self.config_store:
            path = self.config_store.load().ffmpeg_path.strip()
        return Path(path) if path else None

    def start_analysis(self) -> None:
        urls = self._all_urls()
        if not urls:
            self.download_page.set_status("请输入视频地址。")
            return

        self.analyzed_results.clear()
        if len(urls) == 1:
            self.download_page.set_status("正在后台分析视频地址...")
        else:
            self.download_page.set_status(f"正在后台分析 {len(urls)} 个视频地址...")
        for url in urls:
            self._start_analysis_for_url(url)

    def _start_analysis_for_url(self, url: str) -> None:
        request = AnalysisRequest(
            url=url,
            ytdlp_path=self._active_ytdlp_path(),
            cookies_path=self._cookies_path(),
        )
        if self._analysis_runner is None:
            worker = AnalysisWorker(request)
        else:
            worker = AnalysisWorker(request, runner=self._analysis_runner)
        worker.finished.connect(lambda payload, analyzed_url=url: self.apply_analysis_result(analyzed_url, payload))
        worker.failed.connect(lambda message, failed_url=url: self.show_analysis_error(message, failed_url))
        worker.canceled.connect(lambda: self.download_page.set_status("分析已取消。"))
        self._worker_runner(worker)

    def apply_analysis_result(self, url: str, metadata: dict) -> None:
        self.analyzed_url = url
        self.analyzed_metadata = metadata
        self.analyzed_results[url] = metadata
        formats = metadata.get("formats") if isinstance(metadata.get("formats"), list) else []
        try:
            choice = choose_format(formats, self._format_preference())
        except ValueError as exc:
            self.selected_format_id = ""
            self.selected_format_summary = ""
            self.download_page.set_status(f"分析完成，但没有可直接下载的格式：{exc}")
            return

        self.selected_format_id = choice.format_id
        self.selected_format_summary = choice.actual_summary
        title = str(metadata.get("title") or "未命名视频")
        self.formats_page.load_available_formats(formats, choice.format_id)
        self.download_page.show_analysis_result(title, _duration_text(metadata.get("duration")), choice.actual_summary)
        self._load_thumbnail(_thumbnail_url(metadata))
        self.download_page.set_status(_analysis_success_status(choice.relaxed))

    def show_analysis_error(self, message: str, url: str | None = None) -> None:
        if message == "playlist_confirmation_needed" and url:
            self.start_playlist_probe(url)
            return
        messages = {
            "login_required": "视频可能需要登录或年龄验证。请在设置中选择 cookies.txt 后重试。",
            "unavailable": "视频不可用、私有或已删除。",
            "network_timeout": "分析超时，请检查网络后重试。",
            "unsupported_url": "当前地址暂不受支持。",
            "playlist_confirmation_needed": "检测到播放列表，请先确认需要展开的项目数量。",
            "unknown_ytdlp_failure": "yt-dlp 分析失败，可尝试更新 yt-dlp 后重试。",
            "canceled": "分析已取消。",
        }
        self.download_page.set_status(messages.get(message, "分析失败，请重试。"))

    def start_playlist_probe(self, url: str) -> None:
        self.download_page.set_status("检测到播放列表，正在后台读取条目...")
        request = PlaylistProbeRequest(
            url=url,
            ytdlp_path=self._active_ytdlp_path(),
            cookies_path=self._cookies_path(),
        )
        if self._playlist_probe_runner is None:
            worker = PlaylistProbeWorker(request)
        else:
            worker = PlaylistProbeWorker(request, runner=self._playlist_probe_runner)
        worker.finished.connect(lambda payload, playlist_url=url: self.apply_playlist_probe_result(playlist_url, payload))
        worker.failed.connect(lambda _message: self.download_page.set_status("播放列表读取失败，请检查地址或稍后重试。"))
        self._worker_runner(worker)

    def apply_playlist_probe_result(self, _url: str, payload: dict) -> None:
        expansion = extract_playlist_urls(payload)
        if not expansion.urls:
            self.download_page.set_status("播放列表为空或无法读取条目。")
            return

        limit_text = ""
        if expansion.skipped_count:
            limit_text = "\n" + playlist_limit_message(len(expansion.urls), expansion.skipped_count)
        confirmed = self._confirmation_dialog(
            self,
            "确认展开播放列表",
            f"检测到播放列表，共 {expansion.total_count} 项。是否展开前 {len(expansion.urls)} 项并开始分析？{limit_text}",
        )
        if not confirmed:
            self.download_page.set_status("已取消播放列表展开。")
            return

        self.download_page.url_input.setPlainText("\n".join(expansion.urls))
        self.analyzed_results.clear()
        self.download_page.set_status(f"已展开 {len(expansion.urls)} 项，正在后台分析...")
        for entry_url in expansion.urls:
            self._start_analysis_for_url(entry_url)

    def choose_save_folder(self) -> None:
        if self._save_folder_picker is not None:
            selected = self._save_folder_picker()
        else:
            selected = self.download_page.choose_folder()
        if not selected:
            return
        self.save_folder_path = selected
        self.download_page.set_save_folder(selected)

    def clear_history(self) -> None:
        if not self.history_store:
            self.history_page.load_records([])
            return
        self.history_store.clear()
        self.history_page.load_records([])
        self.queue_page.load_history_records([])

    def open_download_folder(self) -> None:
        folder = self.settings_page.default_folder.text().strip()
        if not folder and self.config_store:
            folder = self.config_store.load().default_save_dir.strip()
        if folder:
            self._external_url_opener(QUrl.fromLocalFile(folder).toString())
        else:
            self._information_dialog(self, "未设置保存位置", "请先在设置中选择默认保存位置。")

    def handle_history_action(self, action: str, record_index: int) -> None:
        records = self.history_store.list() if self.history_store else list(self.history_page._records)
        if record_index < 0 or record_index >= len(records):
            return
        record = records[record_index]
        output_path = Path(record.output_path)
        if action == "open_file":
            if not output_path.exists():
                if self._confirmation_dialog(
                    self,
                    "文件不存在",
                    "下载文件已移动或删除。是否打开最后记录的保存目录？",
                ):
                    self._external_url_opener(QUrl.fromLocalFile(str(output_path.parent)).toString())
                return
            self._external_url_opener(QUrl.fromLocalFile(str(output_path)).toString())
            return
        if action == "open_folder":
            self._external_url_opener(QUrl.fromLocalFile(str(output_path.parent)).toString())
            return
        if action == "redownload":
            self.nav.setCurrentRow(0)
            self.download_page.url_input.setPlainText(record.url)
            self.start_analysis()
            return
        if action == "delete":
            self.delete_history_record(record_index)

    def delete_history_record(self, record_index: int) -> None:
        if not self.history_store:
            records = list(self.history_page._records)
            if 0 <= record_index < len(records):
                del records[record_index]
            self.history_page.load_records(records)
            return
        self.history_store.delete(record_index)
        records = self.history_store.list()
        self.history_page.load_records(records)
        self.queue_page.load_history_records(records)

    def start_download(self) -> None:
        analyzed_items = list(self.analyzed_results.items())
        if not analyzed_items and self.analyzed_url and self.analyzed_metadata:
            analyzed_items = [(self.analyzed_url, self.analyzed_metadata)]
        if not analyzed_items:
            self.download_page.set_status("请先分析视频，再开始下载。")
            return

        if not self._ensure_output_dir_available():
            return

        started = 0
        for url, metadata in analyzed_items:
            if self._start_download_for_analysis(url, metadata, allow_preview=started == 0):
                started += 1

    def _start_download_for_analysis(self, url: str, metadata: dict, allow_preview: bool) -> bool:
        formats = metadata.get("formats") if isinstance(metadata.get("formats"), list) else []
        try:
            choice = choose_format(formats, self._format_preference())
        except ValueError as exc:
            self.download_page.set_status(f"没有可直接下载的格式：{exc}")
            return False
        selected_format = self.formats_page.format_id_combo.currentText().strip()
        if len(self.analyzed_results) <= 1 and selected_format and selected_format != "自动":
            choice_format_id = selected_format
        else:
            choice_format_id = choice.format_id
        subtitle_action = _subtitle_action_value(self.formats_page.subtitle_combo.currentText())
        if subtitle_action == "burn":
            self.download_page.set_status("烧录字幕尚未实现；请选择下载字幕文件或嵌入字幕，避免假烧录。")
            return False
        ffmpeg_path = self._configured_ffmpeg_path()
        if subtitle_action_requires_ffmpeg(subtitle_action) and ffmpeg_path is None:
            self.about_page.show_ffmpeg_missing_baseline()
            self.download_page.set_status("嵌入字幕需要 ffmpeg；请先在设置中搜索或选择 ffmpeg.exe。")
            return False
        self.selected_format_id = choice_format_id
        self.selected_format_summary = choice.actual_summary
        task_id = uuid4().hex
        title = str(metadata.get("title") or "未命名视频")
        self.queue_page.add_task(task_id, title, "等待中")
        request = DownloadRequest(
            url=url,
            ytdlp_path=self._active_ytdlp_path(),
            output_template=self._output_template(),
            format_id=choice_format_id,
            cookies_path=self._cookies_path(),
            subtitle_action=subtitle_action,
            ffmpeg_path=ffmpeg_path,
        )
        self._download_requests_by_task[task_id] = request
        self._download_context_by_task[task_id] = {
            "title": title,
            "url": url,
            "format_id": choice_format_id,
            "format_summary": choice.actual_summary,
            "download_type": self.download_page.mode_combo.currentText(),
            "subtitle_behavior": self.formats_page.subtitle_combo.currentText(),
            "preview_on_start": allow_preview and self.download_page.preview_checkbox.isChecked(),
        }
        self._queue_download_task(task_id, "下载已开始，进度见队列。")
        return True

    def _queue_download_task(self, task_id: str, status_text: str) -> None:
        self._download_task_states[task_id] = "pending"
        if task_id not in self._pending_download_task_ids:
            self._pending_download_task_ids.append(task_id)
        self._pending_download_status_by_task[task_id] = status_text
        self._start_ready_download_workers()

    def _start_ready_download_workers(self) -> None:
        while self._active_download_count() < self._max_concurrency() and self._pending_download_task_ids:
            task_id = self._pending_download_task_ids.pop(0)
            if self._download_task_states.get(task_id) != "pending":
                continue
            request = self._download_requests_by_task.get(task_id)
            if request is None:
                continue
            context = self._download_context_by_task.get(task_id, {})
            if context.pop("preview_on_start", False):
                self.start_preview(str(context.get("url") or request.url), str(context.get("format_id") or request.format_id))
            status_text = self._pending_download_status_by_task.pop(task_id, "下载已开始，进度见队列。")
            self._start_download_worker(task_id, request, status_text)

    def _active_download_count(self) -> int:
        return sum(
            1
            for state in self._download_task_states.values()
            if state in {"running", "pause_requested", "cancel_requested"}
        )

    def _max_concurrency(self) -> int:
        if self.config_store:
            value = self.config_store.load().max_concurrency
        else:
            value = self.settings_page.concurrency.value()
        return max(1, min(5, int(value)))

    def start_preview(self, url: str | None = None, format_id: str | None = None) -> None:
        request = PreviewUrlRequest(
            url=url or self.analyzed_url,
            ytdlp_path=self._active_ytdlp_path(),
            format_id=format_id or self.selected_format_id,
            cookies_path=self._cookies_path(),
        )
        if self._preview_runner is None:
            worker = PreviewUrlWorker(request)
        else:
            worker = PreviewUrlWorker(request, runner=self._preview_runner)
        worker.finished.connect(self.download_page.preview_player.load_url)
        worker.failed.connect(lambda _message: self.download_page.preview_player.show_unavailable())
        self._worker_runner(worker)

    def _start_download_worker(self, task_id: str, request: DownloadRequest, status_text: str) -> None:
        self._download_task_states[task_id] = "running"
        self.queue_page.update_task(task_id, status="下载中", progress=0.0, speed="", eta="")
        self.download_page.set_status(status_text)
        worker = DownloadWorker(
            request,
            popen_factory=self._download_popen_factory,
        )
        self._download_workers_by_task[task_id] = worker
        worker.progress.connect(lambda event, task=task_id: self.update_download_progress(task, event))
        worker.finished.connect(lambda task=task_id: self.finish_download(task))
        worker.failed.connect(lambda message, task=task_id: self.fail_download(task, message))
        self._worker_runner(worker)

    def update_download_progress(self, task_id: str, event: ProgressEvent) -> None:
        if event.percent is None:
            return
        self.queue_page.update_task(task_id, status="下载中", progress=event.percent, speed=event.speed, eta=event.eta)

    def finish_download(self, task_id: str) -> None:
        self._download_workers_by_task.pop(task_id, None)
        self._download_task_states[task_id] = "finished"
        self.queue_page.update_task(task_id, status="已完成", progress=100.0, speed="", eta="00:00")
        self.download_page.set_status("下载完成。")
        self.record_finished_download(task_id)
        self._start_ready_download_workers()

    def fail_download(self, task_id: str, message: str) -> None:
        self._download_workers_by_task.pop(task_id, None)
        state = self._download_task_states.get(task_id, "")
        if state == "cancel_requested":
            self._download_task_states[task_id] = "canceled"
            self.queue_page.update_task(task_id, status="已取消")
            self.download_page.set_status("下载已取消。")
            self._start_ready_download_workers()
            return
        if state == "pause_requested":
            self._download_task_states[task_id] = "paused"
            self.queue_page.update_task(task_id, status="已暂停")
            self.download_page.set_status("下载已暂停，可继续。")
            self._start_ready_download_workers()
            return
        self._download_task_states[task_id] = "failed"
        safe_message = "下载失败，请检查地址、网络或 yt-dlp 状态后重试。"
        self.queue_page.update_task(task_id, status="失败")
        self.download_page.set_status(safe_message if "cookies" in message.lower() else message)
        self._start_ready_download_workers()

    def handle_queue_action(self, task_id: str, action: str) -> None:
        if action == "cancel":
            self.cancel_queue_task(task_id)
        elif action == "pause":
            self.pause_queue_task(task_id)
        elif action == "retry":
            self.retry_queue_task(task_id)

    def cancel_queue_task(self, task_id: str) -> None:
        if self._download_task_states.get(task_id) == "pending":
            self._remove_pending_download_task(task_id)
            self._download_task_states[task_id] = "canceled"
            self.queue_page.update_task(task_id, status="已取消")
            self.download_page.set_status("下载已取消。")
            return
        self._download_task_states[task_id] = "cancel_requested"
        worker = self._download_workers_by_task.get(task_id)
        if worker:
            worker.cancel()
        self.queue_page.update_task(task_id, status="已取消")
        self.download_page.set_status("下载已取消。")

    def pause_queue_task(self, task_id: str) -> None:
        if self._download_task_states.get(task_id) == "pending":
            self._remove_pending_download_task(task_id)
            self._download_task_states[task_id] = "paused"
            self.queue_page.update_task(task_id, status="已暂停")
            self.download_page.set_status("下载已暂停，可继续。")
            return
        self._download_task_states[task_id] = "pause_requested"
        worker = self._download_workers_by_task.get(task_id)
        if worker:
            worker.cancel()
        self.queue_page.update_task(task_id, status="已暂停")
        self.download_page.set_status("下载已暂停，可继续。")

    def retry_queue_task(self, task_id: str) -> None:
        state = self._download_task_states.get(task_id, "")
        if state not in {"paused", "canceled", "failed", "pause_requested", "cancel_requested"}:
            self.download_page.set_status("只能重试已暂停、已取消或失败的任务。")
            return
        request = self._download_requests_by_task.get(task_id)
        if request is None:
            return
        self.queue_page.update_task(task_id, status="等待中", progress=0.0, speed="", eta="")
        self._queue_download_task(task_id, "已重新开始下载。")

    def pause_all_tasks(self) -> None:
        for row in range(self.queue_page.table.rowCount()):
            task_item = self.queue_page.table.verticalHeaderItem(row)
            if task_item:
                self.pause_queue_task(task_item.text())

    def clear_completed_queue_tasks(self) -> None:
        task_ids = self.queue_page.completed_task_ids()
        self.queue_page.remove_tasks(task_ids)
        for task_id in task_ids:
            self._download_workers_by_task.pop(task_id, None)
            self._download_requests_by_task.pop(task_id, None)
            self._download_context_by_task.pop(task_id, None)
            self._download_task_states.pop(task_id, None)
            self._pending_download_status_by_task.pop(task_id, None)
            self._remove_pending_download_task(task_id)

    def _remove_pending_download_task(self, task_id: str) -> None:
        self._pending_download_task_ids = [pending_id for pending_id in self._pending_download_task_ids if pending_id != task_id]

    def record_finished_download(self, task_id: str | None = None) -> None:
        if not self.history_store:
            return
        context = self._download_context_by_task.get(task_id or "", {})
        record = HistoryRecord(
            title=str(context.get("title") or self.analyzed_metadata.get("title") or "未命名视频"),
            url=str(context.get("url") or self.analyzed_url),
            output_path=str(self._output_template()),
            download_type=str(context.get("download_type") or self.download_page.mode_combo.currentText()),
            format_summary=str(context.get("format_summary") or self.selected_format_summary),
            subtitle_behavior=str(context.get("subtitle_behavior") or self.formats_page.subtitle_combo.currentText()),
            status="finished",
            created_at=datetime.now().isoformat(timespec="seconds"),
        )
        self.history_store.add(record)
        records = self.history_store.list()
        self.history_page.load_records(records)
        self.queue_page.load_history_records(records)

    def _first_url(self) -> str:
        urls = self._all_urls()
        return urls[0] if urls else ""

    def _all_urls(self) -> list[str]:
        urls: list[str] = []
        for line in self.download_page.url_input.toPlainText().splitlines():
            text = line.strip()
            if text:
                urls.append(text)
        return urls

    def _active_ytdlp_path(self) -> Path:
        if self.config_store:
            config = self.config_store.load()
            if config.active_ytdlp_path:
                return Path(config.active_ytdlp_path)
        return bundled_ytdlp_path()

    def _cookies_path(self) -> Path | None:
        if self.config_store:
            value = self.config_store.load().cookies_path.strip()
        else:
            value = self.settings_page.cookies_path.text().strip()
        return Path(value) if value else None

    def _output_template(self) -> Path:
        return self._output_dir() / "%(title)s.%(ext)s"

    def _output_dir(self) -> Path:
        folder = self.save_folder_path or self.settings_page.default_folder.text().strip()
        if not folder and self.config_store:
            folder = self.config_store.load().default_save_dir.strip()
        return Path(folder) if folder else Path.home() / "Downloads"

    def _ensure_output_dir_available(self) -> bool:
        output_dir = self._output_dir()
        if not output_dir.exists() or not output_dir.is_dir() or not os.access(output_dir, os.W_OK):
            self.download_page.set_status("保存位置不可用，请重新选择保存文件夹。")
            self._information_dialog(self, "保存位置不可用", "保存文件夹不存在或不可写，请重新选择保存位置。")
            return False
        return True

    def _download_mode(self) -> str:
        index = self.download_page.mode_combo.currentIndex()
        if index == 1:
            return "audio_only"
        if index == 2:
            return "video_only"
        return "audio_video"

    def _format_preference(self) -> FormatPreference:
        return FormatPreference(
            resolution=_resolution_value(self.formats_page.resolution_combo.currentText()),
            container=_auto_empty(self.formats_page.container_combo.currentText()),
            fps=_integer_text(self.formats_page.fps_combo.currentText()),
            codec=_codec_value(self.formats_page.codec_combo.currentText()),
            video_bitrate=_video_bitrate_value(self.formats_page.video_bitrate_combo.currentText()),
            audio_bitrate=_audio_bitrate_value(self.formats_page.audio_bitrate_combo.currentText()),
            download_mode=self._download_mode(),
        )

    def _run_worker_in_thread(self, worker: object) -> None:
        thread = QThread(self)
        self._threads.append(thread)
        self._workers.append(worker)
        worker.moveToThread(thread)
        thread.started.connect(worker.run)

        def finish_thread(*_args) -> None:
            thread.quit()

        for signal_name in ("finished", "failed", "canceled"):
            signal = getattr(worker, signal_name, None)
            if signal is not None:
                signal.connect(finish_thread)
        thread.finished.connect(worker.deleteLater)
        thread.finished.connect(thread.deleteLater)
        thread.finished.connect(lambda: self._forget_thread(thread))
        thread.finished.connect(lambda: self._forget_worker(worker))
        thread.start()

    def _forget_thread(self, thread: QThread) -> None:
        if thread in self._threads:
            self._threads.remove(thread)

    def _forget_worker(self, worker: object) -> None:
        if worker in self._workers:
            self._workers.remove(worker)

    def _load_thumbnail(self, url: str) -> None:
        if not url:
            return
        reply = self._network_manager.get(QNetworkRequest(QUrl(url)))
        reply.finished.connect(lambda reply=reply: self._apply_thumbnail_reply(reply))

    def _apply_thumbnail_reply(self, reply) -> None:
        try:
            data = bytes(reply.readAll())
            pixmap = QPixmap()
            if pixmap.loadFromData(data):
                self.download_page.set_thumbnail(pixmap)
        finally:
            reply.deleteLater()


def _duration_text(value: object) -> str:
    try:
        total = int(float(value))
    except (TypeError, ValueError):
        return "未知"
    minutes, seconds = divmod(max(total, 0), 60)
    hours, minutes = divmod(minutes, 60)
    if hours:
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"
    return f"{minutes:02d}:{seconds:02d}"


_RELAXED_PREFERENCE_LABELS = {
    "resolution": "分辨率",
    "fps": "帧率",
    "codec": "编码",
    "video_bitrate": "视频码率",
    "audio_bitrate": "音频码率",
    "container": "容器",
}


def _analysis_success_status(relaxed_preferences: list[str]) -> str:
    labels = [_RELAXED_PREFERENCE_LABELS.get(key, key) for key in relaxed_preferences]
    if labels:
        return f"分析完成，已放宽：{'、'.join(labels)}。可以开始下载。"
    return "分析完成，可以开始下载。"


def _thumbnail_url(metadata: dict) -> str:
    thumbnail = metadata.get("thumbnail")
    if isinstance(thumbnail, str) and thumbnail:
        return thumbnail
    thumbnails = metadata.get("thumbnails")
    if isinstance(thumbnails, list):
        for item in reversed(thumbnails):
            if isinstance(item, dict) and isinstance(item.get("url"), str):
                return item["url"]
    return ""


def _resolution_value(text: str) -> int | None:
    if text == "自动":
        return None
    return _integer_text(text.rstrip("p"))


def _integer_text(text: str) -> int | None:
    if text == "自动":
        return None
    try:
        return int(text.rstrip("k"))
    except ValueError:
        return None


def _auto_empty(text: str) -> str:
    return "" if text == "自动" else text


def _codec_value(text: str) -> str | None:
    if text == "自动":
        return None
    return {
        "H.264": "avc1",
        "HEVC": "hev",
        "AV1": "av01",
        "VP9": "vp9",
    }.get(text, text)


def _video_bitrate_value(text: str) -> int | None:
    return {
        "高": 5000,
        "中": 2500,
        "低": 800,
    }.get(text)


def _audio_bitrate_value(text: str) -> int | None:
    return _integer_text(text)


def _subtitle_action_value(text: str) -> str:
    return {
        "下载字幕文件": "file",
        "嵌入": "embed",
        "烧录": "burn",
    }.get(text, "none")
