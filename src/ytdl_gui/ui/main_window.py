from collections.abc import Callable
from datetime import datetime
from dataclasses import replace
import os
import copy
import time
from pathlib import Path
from uuid import uuid4

from PySide6.QtCore import QObject, QSize, Qt, QThread, QTimer, QUrl, Signal, Slot
from PySide6.QtGui import QDesktopServices, QPixmap
from PySide6.QtWidgets import (
    QCheckBox,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QListView,
    QListWidget,
    QListWidgetItem,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QStackedWidget,
    QStyle,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.config_store import AppConfig
from ytdl_gui.cookies import cookie_help_text, validate_netscape_cookies
from ytdl_gui.ffmpeg import FfmpegStatus, ffmpeg_help_url, find_ffmpeg
from ytdl_gui.file_deletion import delete_recorded_files
from ytdl_gui.format_selector import FormatPreference, available_resolution_values, choose_format
from ytdl_gui.history_store import HistoryRecord
from ytdl_gui.paths import bundled_ytdlp_path, local_ffmpeg_candidates, resource_root
from ytdl_gui.queue_store import QueueRecoveryWorker
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
    ThumbnailWorker,
    fetch_thumbnail_bytes,
)
from ytdl_gui.workers import YtdlpUpdateRequest, YtdlpUpdateRunner, YtdlpUpdateWorker
from ytdl_gui.ytdlp_runner import ProgressEvent, extract_playlist_urls, playlist_limit_message


class _UiCallDispatcher(QObject):
    call_requested = Signal(object)

    def __init__(self, parent: QObject):
        super().__init__(parent)
        self.call_requested.connect(self._run, Qt.ConnectionType.QueuedConnection)

    @Slot(object)
    def _run(self, callback) -> None:
        callback()


NAV_ITEMS = [
    ("下载", QStyle.StandardPixmap.SP_ArrowDown),
    ("格式", QStyle.StandardPixmap.SP_FileDialogDetailedView),
    ("队列", QStyle.StandardPixmap.SP_FileDialogListView),
    ("历史", QStyle.StandardPixmap.SP_BrowserReload),
    ("设置", QStyle.StandardPixmap.SP_ComputerIcon),
    ("关于", QStyle.StandardPixmap.SP_MessageBoxInformation),
]


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
        thumbnail_fetcher=None,
        worker_runner: Callable[[object], object] | None = None,
        save_folder_picker: Callable[[], str] | None = None,
        confirmation_dialog: Callable[[QWidget, str, str], bool] | None = None,
        queue_store=None,
        ):
        super().__init__()
        self._ui_dispatcher = _UiCallDispatcher(self)
        self.setWindowTitle("视频地址提取器")
        self.resize(590, 883)
        self.setMinimumSize(488, 760)
        self.config_store = config_store
        self.history_store = history_store
        self.queue_store = queue_store
        self._queue_ready = queue_store is None
        self._queue_save_failed = False
        self._progress_saved_at = 0.0
        self._closing = False
        self._shutdown_deadline = None
        self._network_available = True
        self._network_info = None
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
        self._thumbnail_fetcher = thumbnail_fetcher or fetch_thumbnail_bytes
        self._worker_runner = worker_runner or self._run_worker_in_thread
        self._save_folder_picker = save_folder_picker
        self._confirmation_dialog = confirmation_dialog or self._ask_confirmation

        self.nav = self._build_navigation()

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
            self.stack.addWidget(self._scroll_page(page))

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
        if self.queue_store is not None:
            self.download_page.start_button.setEnabled(False)
            recovery = QueueRecoveryWorker(self.queue_store)
            recovery.finished.connect(self._restore_queue)
            recovery.failed.connect(self._queue_recovery_failed)
            self._worker_runner(recovery)

    @Slot(dict)
    def _restore_queue(self, items):
        errors = []
        for task_id, item in items.items():
            request = item['request']
            self._download_requests_by_task[task_id] = request
            self._download_context_by_task[task_id] = {**item['context'], 'url': request.url,
                                                       'format_id': request.format_id}
            state = item['state']
            self._download_task_states[task_id] = state
            self.queue_page.add_task(task_id, request.expected_title or '恢复任务', '失败' if state == 'failed' else '已暂停')
            self.queue_page.update_task(task_id, progress=item['context'].get('progress', 0))
            if state == 'failed':
                error = item['context'].get('error') or '上次下载失败，请检查网站权限和所选格式后重试。'
                self.queue_page._task_cards[task_id]['meta'].setToolTip(error)
                errors.append(error)
        self._queue_ready = True
        self.download_page.start_button.setEnabled(True)
        if items:
            self.queue_page.show_notice('已恢复暂停任务。点击继续将使用原格式和保存位置；不支持续传的网站会重新下载当前文件。')
        if errors:
            self.queue_page.show_notice('已恢复失败任务：' + '；'.join(dict.fromkeys(errors)))

    @Slot(str)
    def _queue_recovery_failed(self, message):
        self.download_page.set_status(message)
        self.queue_page.show_notice(message)

    def _save_queue(self) -> bool:
        if self.queue_store is None:
            return True
        if not self._queue_ready:
            return False
        try:
            self.queue_store.save({task: (request, self._download_context_by_task.get(task, {}),
                                           self._download_task_states.get(task, 'paused'))
                                   for task, request in self._download_requests_by_task.items()})
        except (OSError, ValueError, TypeError):
            self._queue_save_failed = True
            self.download_page.set_status('恢复队列保存失败，未启动新下载；请检查磁盘与数据目录。')
            return False
        self._queue_save_failed = False
        return True

    def _set_download_state(self, task_id, state) -> bool:
        previous = self._download_task_states.get(task_id)
        self._download_task_states[task_id] = state
        if self._save_queue():
            return True
        if previous is None:
            self._download_task_states.pop(task_id, None)
        else:
            self._download_task_states[task_id] = previous
        return False

    def watch_network(self):
        from PySide6.QtNetwork import QNetworkInformation
        if QNetworkInformation.loadDefaultBackend():
            self._network_info = QNetworkInformation.instance()
            self._network_info.reachabilityChanged.connect(self._reachability_changed)
            self._network_available = self._network_info.reachability() != QNetworkInformation.Reachability.Disconnected

    def _reachability_changed(self, reachability):
        from PySide6.QtNetwork import QNetworkInformation
        if reachability == QNetworkInformation.Reachability.Disconnected:
            self.network_changed(False)
        else:
            # Ignore short connectivity flaps; this timer performs no network I/O.
            QTimer.singleShot(1500, self._network_stable)

    def _network_stable(self):
        from PySide6.QtNetwork import QNetworkInformation
        if self._network_info.reachability() != QNetworkInformation.Reachability.Disconnected:
            self.network_changed(True)

    def network_changed(self, available):
        self._network_available = available
        if self._closing:
            return
        for task_id, state in list(self._download_task_states.items()):
            if not available and state == 'running':
                self._stop_queue_task(task_id, 'offline')
            elif not available and state == 'pending':
                if self._set_download_state(task_id, 'waiting_network'):
                    self._remove_pending_download_task(task_id)
                    self.queue_page.update_task(task_id, status='等待网络')
            elif available and state == 'waiting_network':
                self.retry_queue_task(task_id)

    def closeEvent(self, event):
        if not self._threads:
            event.accept()
            return
        event.ignore()
        if not self._closing:
            self._closing = True
            self._shutdown_deadline = time.monotonic() + 20
            self.setEnabled(False)
            for task in list(self._download_workers_by_task):
                self._stop_queue_task(task, 'exit')
        if self._shutdown_deadline is not None and time.monotonic() >= self._shutdown_deadline:
            # Last resort only after an explicit exit request. OS teardown
            # closes every non-inheritable KILL_ON_JOB_CLOSE handle.
            os._exit(1)
        self.download_page.set_status('正在停止后台任务，完成后退出…')
        QTimer.singleShot(100, self.close)

    @Slot()
    def shutdown_for_quit(self):
        # Normal window/tray exit uses closeEvent's nonblocking drain. This
        # fallback also covers an external QApplication.quit/OS shutdown.
        self._closing = True
        for task in list(self._download_workers_by_task):
            self._stop_queue_task(task, 'exit')
        deadline = time.monotonic() + 20
        for thread in list(self._threads):
            if not thread.wait(max(1, int((deadline - time.monotonic()) * 1000))):
                os._exit(1)
                return

    def _scroll_page(self, page: QWidget) -> QScrollArea:
        scroll = QScrollArea()
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        scroll.setMinimumSize(0, 0)
        scroll.setWidget(page)
        return scroll

    def _build_navigation(self) -> QListWidget:
        nav = QListWidget()
        nav.setObjectName("navRail")
        nav.setFixedWidth(82)
        nav.setViewMode(QListView.ViewMode.ListMode)
        nav.setMovement(QListView.Movement.Static)
        nav.setFlow(QListView.Flow.TopToBottom)
        nav.setWrapping(False)
        nav.setResizeMode(QListView.ResizeMode.Adjust)
        nav.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        nav.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        nav.setIconSize(QSize(20, 20))
        nav.setGridSize(QSize(70, 72))
        nav.setUniformItemSizes(True)
        for text, standard_icon in NAV_ITEMS:
            icon = self.style().standardIcon(standard_icon)
            item = QListWidgetItem("")
            item.setData(Qt.ItemDataRole.UserRole, text)
            item.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            item.setSizeHint(QSize(70, 72))
            nav.addItem(item)
            nav.setItemWidget(item, self._build_navigation_item_widget(text, icon))
        return nav

    def _build_navigation_item_widget(self, text: str, icon) -> QWidget:
        widget = QWidget()
        widget.setObjectName("navItem")
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(0, 7, 0, 7)
        layout.setSpacing(3)

        icon_label = QLabel()
        icon_label.setObjectName("navItemIcon")
        icon_label.setFixedSize(24, 24)
        icon_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        icon_label.setPixmap(icon.pixmap(QSize(20, 20)))

        text_label = QLabel(text)
        text_label.setObjectName("navItemLabel")
        text_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        text_label.setMinimumWidth(text_label.fontMetrics().horizontalAdvance(text))

        layout.addWidget(icon_label, 0, Qt.AlignmentFlag.AlignHCenter)
        layout.addWidget(text_label, 0, Qt.AlignmentFlag.AlignHCenter)
        return widget

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
        self.download_page.audio_quality_button.clicked.connect(self.open_format_selection)
        self.download_page.video_quality_button.clicked.connect(self.open_format_selection)

    def open_format_selection(self) -> None:
        self.nav.setCurrentRow(1)

    def connect_history_actions(self) -> None:
        self.history_page.clear_button.clicked.connect(self.clear_history)
        self.history_page.open_folder_button.clicked.connect(self.open_download_folder)
        self.queue_page.open_downloads_button.clicked.connect(self.open_download_folder)
        self.queue_page.history_action_requested.connect(self.handle_history_action)
        self.history_page.history_action_requested.connect(self.handle_history_action)

    def connect_queue_actions(self) -> None:
        self.queue_page.task_action_requested.connect(self.handle_queue_action)
        self.queue_page.pause_all_button.clicked.connect(self.pause_all_tasks)
        self.queue_page.clear_completed_button.clicked.connect(self.clear_completed_queue_tasks)

    def connect_update_actions(self) -> None:
        self.footer_update_button.clicked.connect(lambda: self.check_ytdlp_updates(silent=False))

    def connect_format_actions(self) -> None:
        self.formats_page.subtitle_combo.currentTextChanged.connect(self.validate_subtitle_option)
        for index, button in enumerate(self.formats_page.mode_buttons):
            button.clicked.connect(lambda _checked=False, current=index: self.set_download_mode(current))
        self.download_page.mode_combo.currentIndexChanged.connect(self.formats_page.set_mode_index)
        self.formats_page.apply_button.clicked.connect(self.apply_format_selection)
        self.formats_page.reset_button.clicked.connect(self.reset_format_selection)

    def set_download_mode(self, index: int) -> None:
        self.download_page.mode_combo.setCurrentIndex(index)
        self.formats_page.set_mode_index(index)
        self._refresh_current_format_controls(update_download_summary=True)

    def apply_format_selection(self) -> None:
        video_quality = self._format_summary_label(self.formats_page.resolution_combo.currentText())
        audio_quality = self._format_summary_label(self.formats_page.audio_bitrate_combo.currentText())
        subtitle_behavior = self.formats_page.subtitle_combo.currentText()
        actual_summary = ""
        relaxed_preferences: list[str] = []
        formats = self.analyzed_metadata.get("formats") if isinstance(self.analyzed_metadata.get("formats"), list) else []
        if formats:
            try:
                choice = choose_format(formats, self._format_preference())
            except ValueError as exc:
                self.download_page.set_status(f"没有可直接下载的格式：{exc}")
                self.nav.setCurrentRow(0)
                return
            self.selected_format_id = choice.format_id
            self.selected_format_summary = choice.actual_summary
            actual_summary = choice.actual_summary
            relaxed_preferences = choice.relaxed
            self.formats_page.load_available_formats(formats, choice.format_id, choice.actual_summary)
            self.formats_page.set_merge_hint_visible(choice.requires_ffmpeg_merge)
            self._apply_available_resolution_controls(formats, choice.actual_summary)
            self.download_page.format_summary_label.setText(f"格式：{choice.actual_summary}")
            video_quality = _video_quality_from_summary(choice.actual_summary, fallback=video_quality)
            audio_quality = _audio_quality_from_summary(choice.actual_summary, fallback=audio_quality)
        else:
            self.formats_page.set_merge_hint_visible(False)
        self.download_page.video_quality_button.setText(video_quality)
        self.download_page.audio_quality_button.setText(audio_quality)
        status = f"格式选择已应用：{self.download_page.mode_combo.currentText()}，字幕：{subtitle_behavior}。"
        tooltip = (
            f"格式选择已应用：{self.download_page.mode_combo.currentText()}，"
            f"视频：{video_quality}，音频：{audio_quality}，字幕：{subtitle_behavior}。"
        )
        if relaxed_preferences:
            status += _relaxed_format_message(relaxed_preferences)
            tooltip += _relaxed_format_message(relaxed_preferences)
        if formats and choice.requires_ffmpeg_merge:
            merge_message = _ffmpeg_merge_message()
            status += merge_message
            tooltip += merge_message
        if actual_summary:
            tooltip += f"实际格式：{actual_summary}。"
        status += "可以开始下载。"
        tooltip += "可以开始下载。"
        self.download_page.set_status(status, tooltip=tooltip)
        self.nav.setCurrentRow(0)

    def reset_format_selection(self) -> None:
        self.set_download_mode(0)
        self.formats_page.resolution_combo.setCurrentText("自动")
        self.formats_page.fps_combo.setCurrentText("自动")
        self.formats_page.codec_combo.setCurrentText("自动")
        self.formats_page.video_bitrate_combo.setCurrentText("自动")
        self.formats_page.audio_bitrate_combo.setCurrentText("自动")
        self.formats_page.container_combo.setCurrentText("自动")
        self.formats_page.subtitle_combo.setCurrentText("不下载")
        self.formats_page.format_id_combo.setCurrentText("自动")
        self.formats_page.set_actual_format_summary("自动")
        self.apply_format_selection()

    @staticmethod
    def _format_summary_label(value: str) -> str:
        return "最佳质量" if value.strip() == "自动" else value.strip()

    def _refresh_current_format_controls(self, update_download_summary: bool = False) -> None:
        formats = self.analyzed_metadata.get("formats") if isinstance(self.analyzed_metadata.get("formats"), list) else []
        if not formats:
            return
        try:
            choice = choose_format(formats, self._format_preference())
        except ValueError:
            self.formats_page.set_merge_hint_visible(False)
            return
        self.selected_format_id = choice.format_id
        self.selected_format_summary = choice.actual_summary
        self.formats_page.load_available_formats(formats, choice.format_id, choice.actual_summary)
        self.formats_page.set_merge_hint_visible(choice.requires_ffmpeg_merge)
        self._apply_available_resolution_controls(formats, choice.actual_summary)
        if update_download_summary:
            self.download_page.format_summary_label.setText(f"格式：{choice.actual_summary}")

    def _apply_available_resolution_controls(self, formats: list[dict], actual_summary: str) -> None:
        values = available_resolution_values(formats, self._format_preference().download_mode)
        selected = _resolution_from_summary(actual_summary) or "自动"
        self.formats_page.set_available_resolutions(values, selected)

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
        return self._active_download_count() > 0

    def _has_ffmpeg_configured(self) -> bool:
        return self._configured_ffmpeg_path() is not None

    def _configured_ffmpeg_path(self) -> Path | None:
        path = self.settings_page.ffmpeg_path.text().strip()
        if not path and self.config_store:
            path = self.config_store.load().ffmpeg_path.strip()
        if path:
            configured = Path(path)
            if configured.exists() and configured.is_file():
                return configured
        for candidate in local_ffmpeg_candidates(self.config_store.path.parent if self.config_store else None):
            if candidate.exists() and candidate.is_file():
                return candidate
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
        worker.finished.connect(lambda payload, analyzed_url=url: self._run_on_ui_thread(
            lambda: self.apply_analysis_result(analyzed_url, payload)))
        worker.failed.connect(lambda message, failed_url=url: self._run_on_ui_thread(
            lambda: self.show_analysis_error(message, failed_url)))
        worker.canceled.connect(lambda: self._run_on_ui_thread(lambda: self.download_page.set_status("分析已取消。")))
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
            self.formats_page.set_merge_hint_visible(False)
            self.download_page.set_status(f"分析完成，但没有可直接下载的格式：{exc}")
            return

        self.selected_format_id = choice.format_id
        self.selected_format_summary = choice.actual_summary
        title = str(metadata.get("title") or "未命名视频")
        self.formats_page.load_available_formats(formats, choice.format_id, choice.actual_summary)
        self.formats_page.set_merge_hint_visible(choice.requires_ffmpeg_merge)
        self._apply_available_resolution_controls(formats, choice.actual_summary)
        self.download_page.show_analysis_result(title, _duration_text(metadata.get("duration")), choice.actual_summary)
        self._load_thumbnail(_thumbnail_url(metadata), _thumbnail_headers(metadata, url))
        self.download_page.reset_analysis_action()
        self.download_page.set_status(_analysis_success_status(choice.relaxed, choice.requires_ffmpeg_merge))
        if len(self._all_urls()) <= 1:
            self.open_format_selection()

    def show_analysis_error(self, message: str, url: str | None = None) -> None:
        if message == "playlist_confirmation_needed" and url:
            self.start_playlist_probe(url)
            return
        messages = {
            "no_formats": "网站本次未返回可下载的视频格式，请稍后重新分析。",
            "login_required": "视频可能需要登录或年龄验证。请在设置中选择 cookies.txt 后重试。",
            "unavailable": "视频不可用、私有或已删除。",
            "network_timeout": "分析超时，请检查网络后重试。",
            "unsupported_url": "当前地址暂不受支持。",
            "playlist_confirmation_needed": "检测到播放列表，请先确认需要展开的项目数量。",
            "unknown_ytdlp_failure": "yt-dlp 分析失败，可点击右下角“检查更新”，更新后重试分析。",
            "canceled": "分析已取消。",
        }
        if message in {"login_required", "network_timeout", "unknown_ytdlp_failure"}:
            self.download_page.set_analysis_retry_available()
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
        records = self._history_records()
        self._delete_history_records(records, list(range(len(records))), "清空历史")

    def _history_records(self) -> list[HistoryRecord]:
        return self.history_store.list() if self.history_store else list(self.history_page._records)

    def _confirm_record_deletion(self, title: str, count: int) -> bool | None:
        dialog = QMessageBox(self)
        dialog.setWindowTitle(title)
        dialog.setIcon(QMessageBox.Icon.Warning)
        dialog.setText(f"确认移除 {count} 条记录？\n默认保留下载文件。勾选后永久删除已记录的主文件，并移除同一任务的历史记录；独立字幕、未完成的分片及其他副本保留。")
        checkbox = QCheckBox("同时删除下载文件", dialog)
        checkbox.setChecked(False)
        dialog.setCheckBox(checkbox)
        confirm = dialog.addButton("确认删除", QMessageBox.ButtonRole.AcceptRole)
        cancel = dialog.addButton("取消", QMessageBox.ButtonRole.RejectRole)
        dialog.setDefaultButton(cancel)
        dialog.setEscapeButton(cancel)
        dialog.exec()
        return checkbox.isChecked() if dialog.clickedButton() == confirm else None

    def _remove_history_indices(self, records: list[HistoryRecord], indices: list[int]) -> None:
        for index in sorted(indices, reverse=True):
            if self.history_store:
                self.history_store.delete(index)
            del records[index]
        self.history_page.load_records(records)
        self.queue_page.load_history_records(records)

    def _delete_files(self, outputs: list[tuple[str, int]], protected_paths: list[str]) -> dict[int, str]:
        protected = list(protected_paths)
        unresolved = []
        active = set(self._download_workers_by_task) | {task for task, state in self._download_task_states.items()
                                                      if state in {'running', 'pause_requested', 'cancel_requested', 'offline_requested', 'exit_requested', 'stop_failed'}}
        for task in active:
            output = self._download_context_by_task.get(task, {}).get('output_path')
            if output:
                protected.append(str(output))
            else:
                request = self._download_requests_by_task.get(task)
                try:
                    unresolved.append(request.output_template.parent.resolve() if request else None)
                except (OSError, ValueError, RuntimeError, TypeError):
                    unresolved.append(None)
        failures, eligible, positions = {}, [], []
        for index, output in enumerate(outputs):
            path = output[0]
            try:
                in_active_folder = isinstance(path, str) and path and any(
                    folder is None or Path(path).resolve().is_relative_to(folder) for folder in unresolved)
            except (OSError, ValueError, RuntimeError, TypeError):
                failures[index] = '文件路径安全检查失败，已保留文件和记录。'
                continue
            if in_active_folder:
                failures[index] = '当前任务尚未确定保存文件名，请稍后再删除此目录中的文件。'
            elif path == '':
                continue
            else:
                positions.append(index)
                eligible.append(output)
        failures.update({positions[index]: reason for index, reason in delete_recorded_files(eligible, protected).items()})
        return failures

    def _show_deletion_failures(self, failures: dict[int, str]) -> None:
        if failures:
            self._information_dialog(self, "部分记录未删除", f"有 {len(failures)} 条记录已保留：\n" + "\n".join(dict.fromkeys(failures.values())))

    def _delete_history_records(self, records: list[HistoryRecord], indices: list[int], title: str) -> None:
        if not indices:
            return
        choice = self._confirm_record_deletion(title, len(indices))
        if choice is None:
            return
        if records != self._history_records():
            self._information_dialog(self, "记录已更新", "历史记录已变化，请重新选择后删除。")
            return
        failures = {}
        if choice:
            selected = [records[index] for index in indices]
            owned_tasks = {record.task_id for record in selected if record.task_id}
            protected = [record.output_path for index, record in enumerate(records) if index not in indices]
            protected.extend(str(context.get("output_path", "")) for task, context in self._download_context_by_task.items()
                             if task not in owned_tasks or self._download_task_states.get(task) != "finished")
            failures = self._delete_files([(record.output_path, record.file_size_bytes) for record in selected], protected)
        self._remove_history_indices(records, [index for position, index in enumerate(indices) if position not in failures])
        self._show_deletion_failures(failures)

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
        if action == "delete":
            self.delete_history_record(record_index)
            return
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
    def delete_history_record(self, record_index: int) -> None:
        records = self._history_records()
        if 0 <= record_index < len(records):
            self._delete_history_records(records, [record_index], "删除历史记录")

    def start_download(self) -> None:
        if not self._queue_ready or self._closing:
            self.download_page.set_status('恢复队列尚未就绪，暂不能启动下载。')
            return
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
            if self._queue_save_failed:
                self.queue_page.show_notice('恢复队列保存失败，任务尚未启动；请检查磁盘后继续。')
                return
        if started:
            message = f"已添加 {started} 个下载任务，正在队列页显示进度。"
            self.queue_page.show_notice(message)
            self.download_page.set_status(message)
            self.nav.setCurrentRow(2)

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
        ffmpeg_path = self._configured_ffmpeg_path()
        if choice.requires_ffmpeg_merge and ffmpeg_path is None:
            self.about_page.show_ffmpeg_missing_baseline()
            self.download_page.set_status("此分辨率需要 ffmpeg 合并音频和视频；请先在设置中搜索或选择 ffmpeg.exe。")
            return False
        if subtitle_action_requires_ffmpeg(subtitle_action) and ffmpeg_path is None:
            self.about_page.show_ffmpeg_missing_baseline()
            self.download_page.set_status("嵌入或烧录字幕需要 ffmpeg；请先在设置中搜索或选择 ffmpeg.exe。")
            return False
        self.selected_format_id = choice_format_id
        self.selected_format_summary = choice.actual_summary
        task_id = uuid4().hex
        title = str(metadata.get("title") or "未命名视频")
        thumbnail = self.download_page.thumbnail_label.pixmap()
        if thumbnail is not None and thumbnail.isNull():
            thumbnail = None
        self.queue_page.add_task(task_id, title, "等待中", thumbnail=thumbnail)
        request = DownloadRequest(
            url=url,
            ytdlp_path=self._active_ytdlp_path(),
            output_template=self._output_template(),
            format_id=choice_format_id,
            cookies_path=self._cookies_path(),
            subtitle_action=subtitle_action,
            ffmpeg_path=ffmpeg_path,
            expected_title=title,
            uses_cookies=self._cookies_path() is not None,
            temp_dir=self.queue_store.task_dir(task_id) if self.queue_store else None,
            analysis_info=copy.deepcopy(metadata) if '__ytdl_snapshot_created' in metadata else None,
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
        if not self._queue_download_task(task_id, "下载已开始，进度见队列。"):
            self.queue_page.remove_tasks([task_id])
            self._download_requests_by_task.pop(task_id, None)
            self._download_context_by_task.pop(task_id, None)
            self._download_task_states.pop(task_id, None)
            return False
        return True

    def _queue_download_task(self, task_id: str, status_text: str) -> bool:
        state = 'pending' if self._network_available else 'waiting_network'
        if not self._set_download_state(task_id, state):
            return False
        if state == 'waiting_network':
            self.queue_page.update_task(task_id, status='等待网络')
            return True
        if task_id not in self._pending_download_task_ids:
            self._pending_download_task_ids.append(task_id)
        self._pending_download_status_by_task[task_id] = status_text
        self._start_ready_download_workers()
        return True

    def _start_ready_download_workers(self) -> None:
        if self._closing or not self._queue_ready or not self._network_available:
            return
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
            if state in {"running", "pause_requested", "cancel_requested", "offline_requested", "exit_requested", "stop_failed"}
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
        if not self._set_download_state(task_id, 'running'):
            self._download_task_states[task_id] = 'paused'
            self.queue_page.update_task(task_id, status='已暂停')
            return
        self.queue_page.update_task(task_id, status="下载中", speed="", eta="")
        self._download_context_by_task.setdefault(task_id, {}).pop('output_path', None)
        self.download_page.set_status(status_text)
        worker = DownloadWorker(
            request,
            popen_factory=self._download_popen_factory,
        )
        self._download_workers_by_task[task_id] = worker
        worker.progress.connect(lambda event, task=task_id: self.update_download_progress(task, event))
        worker.output_reserved.connect(lambda path, task=task_id: self.reserve_download_output(task, path))
        worker.finished.connect(lambda output_path, task=task_id: self.finish_download(task, output_path))
        worker.failed.connect(lambda message, task=task_id: self.fail_download(task, message))
        worker.stopped.connect(lambda reason, task=task_id: self.download_stopped(task, reason))
        worker.network_failed.connect(lambda task=task_id: self.download_stopped(task, 'offline'))
        worker.stop_failed.connect(lambda message, task=task_id: self.download_stop_failed(task, message))
        self._worker_runner(worker)

    def _is_ui_thread(self) -> bool:
        return QThread.currentThread() == self.thread()

    def reserve_download_output(self, task_id: str, path: str) -> None:
        if not self._is_ui_thread():
            self._run_on_ui_thread(lambda: self.reserve_download_output(task_id, path))
            return
        self._download_context_by_task.setdefault(task_id, {})['output_path'] = path

    def _run_on_ui_thread(self, callback) -> None:
        if self._is_ui_thread():
            callback()
            return
        self._ui_dispatcher.call_requested.emit(callback)

    def update_download_progress(self, task_id: str, event: ProgressEvent) -> None:
        if not self._is_ui_thread():
            self._run_on_ui_thread(lambda task_id=task_id, event=event: self.update_download_progress(task_id, event))
            return
        if event.percent is None:
            if 'Unable to resume' in event.raw:
                self.queue_page.show_notice('此来源不支持续传，正在重新下载当前文件；其他已完成文件保留。')
            return
        if self._download_task_states.get(task_id) != 'running':
            return
        self._download_context_by_task.setdefault(task_id, {})['progress'] = event.percent
        self.queue_page.update_task(task_id, status="下载中", progress=event.percent, speed=event.speed, eta=event.eta)
        now = time.monotonic()
        if self.queue_store is not None and now - self._progress_saved_at >= 1:
            self._progress_saved_at = now
            if not self._save_queue():
                message = '进度保存失败，下载仍在继续；已保留上次恢复快照，请检查磁盘。'
                self.download_page.set_status(message)
                self.queue_page.show_notice(message)

    def finish_download(self, task_id: str, output_path: str = "") -> None:
        if not self._is_ui_thread():
            self._run_on_ui_thread(lambda task_id=task_id, output_path=output_path: self.finish_download(task_id, output_path))
            return
        if self._download_task_states.get(task_id) in {'cancel_requested', 'canceled'}:
            self.download_stopped(task_id, 'cancel')
            return
        self._download_workers_by_task.pop(task_id, None)
        self._download_task_states[task_id] = "finished"
        if task_id in self._download_requests_by_task:
            self._download_requests_by_task[task_id] = replace(self._download_requests_by_task[task_id], analysis_info=None)
        saved = self._save_queue()
        if output_path:
            self._download_context_by_task.setdefault(task_id, {})["output_path"] = output_path
        self.queue_page.update_task(task_id, status="已完成", progress=100.0, speed="", eta="00:00")
        self.download_page.set_status("下载完成。")
        if not saved:
            self.download_page.set_status('下载完成，但恢复队列保存失败；重启后可能仍显示暂停任务。')
        try:
            self.record_finished_download(task_id)
        except Exception:
            self.download_page.set_status("下载完成，但历史记录写入失败。")
        self._start_ready_download_workers()

    def fail_download(self, task_id: str, message: str) -> None:
        if not self._is_ui_thread():
            self._run_on_ui_thread(lambda task_id=task_id, message=message: self.fail_download(task_id, message))
            return
        self._download_workers_by_task.pop(task_id, None)
        state = self._download_task_states.get(task_id, "")
        if state in {"cancel_requested", "canceled"}:
            self.download_stopped(task_id, 'cancel')
            return
        if state == "pause_requested":
            self._download_task_states[task_id] = "paused"
            self.queue_page.update_task(task_id, status="已暂停")
            self.download_page.set_status("下载已暂停，可继续。")
            self._start_ready_download_workers()
            return
        self._download_task_states[task_id] = "failed"
        safe_message = "下载失败，请检查地址、网络或 yt-dlp 状态后重试。"
        shown_message = safe_message if "cookies" in message.lower() else message
        self._download_context_by_task.setdefault(task_id, {})['error'] = shown_message
        saved = self._save_queue()
        self.queue_page.update_task(task_id, status="失败")
        if saved:
            self.download_page.set_status(shown_message)
        self._start_ready_download_workers()

    def download_stopped(self, task_id, reason):
        if not self._is_ui_thread():
            self._run_on_ui_thread(lambda: self.download_stopped(task_id, reason))
            return
        previous = self._download_task_states.get(task_id)
        if previous in {'cancel_requested', 'canceled'}:
            reason = 'cancel'
        elif previous in {'pause_requested', 'exit_requested'}:
            reason = 'pause'
        self._download_workers_by_task.pop(task_id, None)
        state, label = {'cancel': ('canceled', '已取消'), 'offline': ('waiting_network', '等待网络')}.get(reason, ('paused', '已暂停'))
        self._download_task_states[task_id] = state
        saved = self._save_queue()
        if state == 'canceled':
            self._remove_canceled_task_card(task_id)
        else:
            self.queue_page.update_task(task_id, status=label)
        if saved:
            self.download_page.set_status({'paused': '下载已暂停，可继续。', 'canceled': '下载已取消。',
                                           'waiting_network': '连接已中断，等待网络恢复；也可点击继续重试。'}[state])
        # Only restart after a real network transition. A timeout with an online
        # OS hint must not loop forever against the same inaccessible website.
        if reason == 'offline' and previous == 'offline_requested' and self._network_available:
            self.retry_queue_task(task_id)
        self._start_ready_download_workers()

    def download_stop_failed(self, task_id, message):
        if not self._is_ui_thread():
            self._run_on_ui_thread(lambda: self.download_stop_failed(task_id, message))
            return
        # Keep the durable cancellation tombstone while retaining its slot.
        if self._download_task_states.get(task_id) != 'cancel_requested':
            self._download_task_states[task_id] = 'stop_failed'
        self._save_queue()
        self.queue_page.update_task(task_id, status='停止未确认')
        self.download_page.set_status(message)

    def handle_queue_action(self, task_id: str, action: str) -> None:
        if action == "cancel":
            self.cancel_queue_task(task_id)
        elif action == "pause":
            self.pause_queue_task(task_id)
        elif action == "retry":
            self.retry_queue_task(task_id)

    def cancel_queue_task(self, task_id: str) -> None:
        self._stop_queue_task(task_id, 'cancel')

    def pause_queue_task(self, task_id: str) -> None:
        self._stop_queue_task(task_id, 'pause')

    def _stop_queue_task(self, task_id, reason):
        state = self._download_task_states.get(task_id)
        if state is None or state in {'finished', 'canceled', 'cancel_requested', 'stop_failed'}:
            return
        worker = self._download_workers_by_task.get(task_id)
        next_state = reason + '_requested' if worker else ('canceled' if reason == 'cancel' else 'paused')
        if not self._set_download_state(task_id, next_state):
            # A failing disk must not prevent shutdown, but cancellation must
            # not be claimed durable until its tombstone has been saved.
            if worker:
                self._download_task_states[task_id] = 'pause_requested'
                worker.stop('pause')
            return
        self._remove_pending_download_task(task_id)
        if worker:
            label = {'pause': '正在暂停', 'cancel': '正在取消', 'offline': '正在等待网络', 'exit': '正在暂停'}[reason]
            self.queue_page.update_task(task_id, status=label)
            self.download_page.set_status(label + '，正在停止下载进程…')
            worker.stop(reason)
            QTimer.singleShot(5000, lambda task=task_id: self._show_stop_delay(task))
        else:
            if reason == 'cancel':
                self._remove_canceled_task_card(task_id)
            else:
                self.queue_page.update_task(task_id, status='已暂停')
            self.download_page.set_status('下载已取消。' if reason == 'cancel' else '下载已暂停，可继续。')

    def _remove_canceled_task_card(self, task_id):
        # Retain the canceled state as a tombstone for late worker signals.
        self.queue_page.remove_tasks([task_id])
        self._download_requests_by_task.pop(task_id, None)
        self._download_context_by_task.pop(task_id, None)
        self._pending_download_status_by_task.pop(task_id, None)
        self._remove_pending_download_task(task_id)
        self.queue_page.show_notice('任务已取消并移出队列，已下载文件保留。')

    def _show_stop_delay(self, task_id):
        if task_id in self._download_workers_by_task and self._download_task_states.get(task_id, '').endswith('_requested'):
            self.download_page.set_status('下载进程尚未完全退出，仍保留任务占用；请等待停止完成。')

    def retry_queue_task(self, task_id: str) -> None:
        state = self._download_task_states.get(task_id, "")
        if state not in {"paused", "canceled", "failed", "waiting_network"} or task_id in self._download_workers_by_task:
            self.download_page.set_status("只能重试已暂停、已取消或失败的任务。")
            return
        request = self._download_requests_by_task.get(task_id)
        if request is None:
            return
        uses_cookies = request.uses_cookies or request.cookies_path is not None
        cookies = self._cookies_path() if uses_cookies else None
        if uses_cookies and (cookies is None or not validate_netscape_cookies(cookies).ok):
            self.download_page.set_status('此任务需要 cookies.txt，请在设置中重新选择有效文件后继续。')
            return
        if not request.output_template.parent.is_dir():
            self.download_page.set_status('原保存位置不可用，请恢复该目录后继续。')
            return
        self._download_requests_by_task[task_id] = replace(request, cookies_path=cookies,
            uses_cookies=uses_cookies, ytdlp_path=self._active_ytdlp_path(), ffmpeg_path=self._configured_ffmpeg_path())
        self.queue_page.update_task(task_id, status="等待中", speed="", eta="")
        if not self._queue_download_task(task_id, "已重新开始下载。"):
            self._download_requests_by_task[task_id] = request
            self.queue_page.update_task(task_id, status={'paused': '已暂停', 'failed': '失败',
                                                         'canceled': '已取消', 'waiting_network': '等待网络'}[state])

    def pause_all_tasks(self) -> None:
        for row in range(self.queue_page.table.rowCount()):
            task_item = self.queue_page.table.verticalHeaderItem(row)
            if task_item:
                self.pause_queue_task(task_item.text())

    def clear_completed_queue_tasks(self) -> None:
        task_ids = [task for task in self.queue_page.completed_task_ids()
                    if self._download_task_states.get(task) in {"finished", "canceled"}]
        if not task_ids:
            return
        choice = self._confirm_record_deletion("清除已完成任务", len(task_ids))
        if choice is None:
            return
        # A modal dialog may process worker signals or retries; recheck states.
        task_ids = [task for task in task_ids if self._download_task_states.get(task) in {"finished", "canceled"}]
        failures = {}
        if choice:
            records = self._history_records()
            outputs = []
            for task in task_ids:
                context = self._download_context_by_task.get(task, {})
                matching = [record for record in records if record.task_id == task]
                sizes = {record.file_size_bytes for record in matching if record.file_size_bytes}
                outputs.append((str(context.get("output_path", "")), next(iter(sizes), 0)))
            protected = [record.output_path for record in records if record.task_id not in task_ids]
            protected.extend(str(context.get("output_path", "")) for task, context in self._download_context_by_task.items() if task not in task_ids)
            # Never discard a history path which differs from its queue output.
            mapping_failures = {}
            for index, task in enumerate(task_ids):
                matching = [record for record in records if record.task_id == task]
                if (any(record.output_path != outputs[index][0] for record in matching)
                        or len({record.file_size_bytes for record in matching if record.file_size_bytes}) > 1):
                    mapping_failures[index] = "任务与历史记录中的文件信息不一致，已保留文件和记录。"
                    protected.append(outputs[index][0])
                    protected.extend(record.output_path for record in matching)
            failures = self._delete_files(outputs, protected)
            failures.update(mapping_failures)
            task_ids = [task for index, task in enumerate(task_ids) if index not in failures]
            self._remove_history_indices(records, [index for index, record in enumerate(records) if record.task_id in task_ids])
        self.queue_page.remove_tasks(task_ids)
        for task_id in task_ids:
            self._download_workers_by_task.pop(task_id, None)
            self._download_requests_by_task.pop(task_id, None)
            self._download_context_by_task.pop(task_id, None)
            self._download_task_states.pop(task_id, None)
            self._pending_download_status_by_task.pop(task_id, None)
            self._remove_pending_download_task(task_id)
        self._show_deletion_failures(failures)
        self._save_queue()

    def _remove_pending_download_task(self, task_id: str) -> None:
        self._pending_download_task_ids = [pending_id for pending_id in self._pending_download_task_ids if pending_id != task_id]

    def record_finished_download(self, task_id: str | None = None) -> None:
        if not self.history_store:
            return
        context = self._download_context_by_task.get(task_id or "", {})
        output_path = str(context.get("output_path") or self._output_template())
        record = HistoryRecord(
            title=str(context.get("title") or self.analyzed_metadata.get("title") or "未命名视频"),
            url=str(context.get("url") or self.analyzed_url),
            output_path=output_path,
            download_type=str(context.get("download_type") or self.download_page.mode_combo.currentText()),
            format_summary=str(context.get("format_summary") or self.selected_format_summary),
            subtitle_behavior=str(context.get("subtitle_behavior") or self.formats_page.subtitle_combo.currentText()),
            status="finished",
            created_at=datetime.now().isoformat(timespec="seconds"),
            file_size_bytes=_file_size_bytes(output_path),
            task_id=task_id or "",
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

        for signal_name in ("finished", "failed", "canceled", "stopped", "network_failed", "stop_failed"):
            signal = getattr(worker, signal_name, None)
            if signal is not None:
                signal.connect(thread.quit, Qt.ConnectionType.DirectConnection)
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

    def _load_thumbnail(self, url: str, headers: dict[str, str] | None = None) -> None:
        if not url:
            return
        worker = ThumbnailWorker(url, headers, self._thumbnail_fetcher)
        worker.finished.connect(self._apply_thumbnail_data)
        worker.failed.connect(lambda: self.download_page.thumbnail_label.setText("预览图"))
        self._worker_runner(worker)

    def _apply_thumbnail_data(self, data: bytes) -> None:
        pixmap = QPixmap()
        if pixmap.loadFromData(data):
            self.download_page.set_thumbnail(pixmap)


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


def _analysis_success_status(relaxed_preferences: list[str], requires_ffmpeg_merge: bool = False) -> str:
    relaxed_message = _relaxed_format_message(relaxed_preferences)
    merge_message = _ffmpeg_merge_message() if requires_ffmpeg_merge else ""
    if relaxed_message:
        return f"分析完成，{relaxed_message}{merge_message}请在格式页确认分辨率、码率后开始下载。"
    return f"分析完成，{merge_message}请在格式页确认分辨率、码率后开始下载。"


def _relaxed_format_message(relaxed_preferences: list[str]) -> str:
    labels = [_RELAXED_PREFERENCE_LABELS.get(key, key) for key in relaxed_preferences]
    return f"已放宽：{'、'.join(labels)}。" if labels else ""


def _ffmpeg_merge_message() -> str:
    return "将使用 ffmpeg 合并音频和视频。"


def _video_quality_from_summary(summary: str, fallback: str) -> str:
    resolution = _resolution_from_summary(summary)
    return resolution or fallback


def _resolution_from_summary(summary: str) -> str:
    first_token = summary.split(" ", 1)[0].strip()
    if first_token.endswith("p") and first_token[:-1].isdigit():
        return first_token
    return ""


def _audio_quality_from_summary(summary: str, fallback: str) -> str:
    parts = summary.split()
    if len(parts) >= 4 and parts[-1].endswith("kbps"):
        return parts[-1].removesuffix("bps")
    return fallback


def _thumbnail_url(metadata: dict) -> str:
    thumbnail = metadata.get("thumbnail")
    if isinstance(thumbnail, str) and thumbnail:
        return thumbnail
    thumbnails = metadata.get("thumbnails")
    if isinstance(thumbnails, list):
        for item in reversed(thumbnails):
            if isinstance(item, dict) and isinstance(item.get("url"), str):
                url = item["url"]
                if not url.casefold().split("?", 1)[0].endswith(".webp"):
                    return url
        for item in reversed(thumbnails):
            if isinstance(item, dict) and isinstance(item.get("url"), str):
                return item["url"]
    return ""


SAFE_METADATA_THUMBNAIL_HEADERS = {
    "accept": "Accept",
    "accept-language": "Accept-Language",
    "referer": "Referer",
    "user-agent": "User-Agent",
}


def _thumbnail_headers(metadata: dict, analyzed_url: str) -> dict[str, str]:
    headers: dict[str, str] = {}
    metadata_headers = metadata.get("http_headers")
    if isinstance(metadata_headers, dict):
        for name, value in metadata_headers.items():
            canonical_name = SAFE_METADATA_THUMBNAIL_HEADERS.get(str(name).casefold())
            if canonical_name and isinstance(value, str) and value:
                headers[canonical_name] = value

    if not headers.get("Referer"):
        webpage_url = metadata.get("webpage_url")
        if isinstance(webpage_url, str) and webpage_url:
            headers["Referer"] = webpage_url
        elif analyzed_url:
            headers["Referer"] = analyzed_url
    return headers


def _file_size_bytes(path: str) -> int:
    try:
        candidate = Path(path)
        if not candidate.is_file():
            return 0
        return candidate.stat().st_size
    except OSError:
        return 0


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
