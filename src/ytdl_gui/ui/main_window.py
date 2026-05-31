from collections.abc import Callable
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from PySide6.QtCore import QThread, QUrl
from PySide6.QtGui import QDesktopServices, QPixmap
from PySide6.QtNetwork import QNetworkAccessManager, QNetworkRequest
from PySide6.QtWidgets import QFileDialog, QHBoxLayout, QListWidget, QMessageBox, QStackedWidget, QWidget

from ytdl_gui.config_store import AppConfig
from ytdl_gui.cookies import cookie_help_text, validate_netscape_cookies
from ytdl_gui.ffmpeg import FfmpegStatus, ffmpeg_help_url, find_ffmpeg
from ytdl_gui.format_selector import FormatPreference, choose_format
from ytdl_gui.history_store import HistoryRecord
from ytdl_gui.paths import bundled_ytdlp_path
from ytdl_gui.ui.pages.about_page import AboutPage
from ytdl_gui.ui.pages.download_page import DownloadPage
from ytdl_gui.ui.pages.formats_page import FormatsPage
from ytdl_gui.ui.pages.history_page import HistoryPage
from ytdl_gui.ui.pages.queue_page import QueuePage
from ytdl_gui.ui.pages.settings_page import SettingsPage
from ytdl_gui.workers import AnalysisRequest, AnalysisWorker, DownloadRequest, DownloadWorker, PreviewUrlRequest, PreviewUrlWorker
from ytdl_gui.ytdlp_runner import ProgressEvent


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
        worker_runner: Callable[[object], object] | None = None,
        save_folder_picker: Callable[[], str] | None = None,
        ):
        super().__init__()
        self.setWindowTitle("视频地址提取器")
        self.resize(620, 860)
        self.setMinimumSize(620, 760)
        self.config_store = config_store
        self.history_store = history_store
        self.analyzed_url = ""
        self.analyzed_metadata: dict = {}
        self.selected_format_id = ""
        self.selected_format_summary = ""
        self.save_folder_path = ""
        self._download_workers_by_task: dict[str, DownloadWorker] = {}
        self._download_requests_by_task: dict[str, DownloadRequest] = {}
        self._download_task_states: dict[str, str] = {}
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
        self._worker_runner = worker_runner or self._run_worker_in_thread
        self._save_folder_picker = save_folder_picker
        self._network_manager = QNetworkAccessManager(self)

        self.nav = QListWidget()
        self.nav.setFixedWidth(92)
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
        self.connect_download_actions()
        self.connect_history_actions()
        self.connect_queue_actions()

    def connect_settings_actions(self) -> None:
        self.settings_page.choose_default_folder_button.clicked.connect(self.choose_default_folder)
        self.settings_page.choose_cookies_button.clicked.connect(self.choose_cookies_file)
        self.settings_page.clear_cookies_button.clicked.connect(self.clear_cookies_file)
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

    def connect_queue_actions(self) -> None:
        self.queue_page.task_action_requested.connect(self.handle_queue_action)
        self.queue_page.pause_all_button.clicked.connect(self.pause_all_tasks)
        self.queue_page.clear_completed_button.clicked.connect(self.clear_completed_queue_tasks)

    def start_analysis(self) -> None:
        url = self._first_url()
        if not url:
            self.download_page.set_status("请输入视频地址。")
            return

        self.download_page.set_status("正在后台分析视频地址...")
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
        worker.failed.connect(self.show_analysis_error)
        worker.canceled.connect(lambda: self.download_page.set_status("分析已取消。"))
        self._worker_runner(worker)

    def apply_analysis_result(self, url: str, metadata: dict) -> None:
        self.analyzed_url = url
        self.analyzed_metadata = metadata
        formats = metadata.get("formats") if isinstance(metadata.get("formats"), list) else []
        try:
            choice = choose_format(formats, FormatPreference(download_mode=self._download_mode()))
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
        self.download_page.set_status("分析完成，可以开始下载。")

    def show_analysis_error(self, message: str) -> None:
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

    def open_download_folder(self) -> None:
        folder = self.settings_page.default_folder.text().strip()
        if not folder and self.config_store:
            folder = self.config_store.load().default_save_dir.strip()
        if folder:
            self._external_url_opener(QUrl.fromLocalFile(folder).toString())
        else:
            self._information_dialog(self, "未设置保存位置", "请先在设置中选择默认保存位置。")

    def start_download(self) -> None:
        if not self.analyzed_url or not self.selected_format_id:
            self.download_page.set_status("请先分析视频，再开始下载。")
            return

        selected_format = self.formats_page.format_id_combo.currentText().strip()
        if selected_format and selected_format != "自动":
            self.selected_format_id = selected_format
        task_id = uuid4().hex
        title = str(self.analyzed_metadata.get("title") or "未命名视频")
        self.queue_page.add_task(task_id, title, "下载中")
        request = DownloadRequest(
            url=self.analyzed_url,
            ytdlp_path=self._active_ytdlp_path(),
            output_template=self._output_template(),
            format_id=self.selected_format_id,
            cookies_path=self._cookies_path(),
        )
        self._download_requests_by_task[task_id] = request
        if self.download_page.preview_checkbox.isChecked():
            self.start_preview()
        self._start_download_worker(task_id, request, "下载已开始，进度见队列。")

    def start_preview(self) -> None:
        request = PreviewUrlRequest(
            url=self.analyzed_url,
            ytdlp_path=self._active_ytdlp_path(),
            format_id=self.selected_format_id,
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
        self.record_finished_download()

    def fail_download(self, task_id: str, message: str) -> None:
        self._download_workers_by_task.pop(task_id, None)
        state = self._download_task_states.get(task_id, "")
        if state == "cancel_requested":
            self._download_task_states[task_id] = "canceled"
            self.queue_page.update_task(task_id, status="已取消")
            self.download_page.set_status("下载已取消。")
            return
        if state == "pause_requested":
            self._download_task_states[task_id] = "paused"
            self.queue_page.update_task(task_id, status="已暂停")
            self.download_page.set_status("下载已暂停，可继续。")
            return
        self._download_task_states[task_id] = "failed"
        safe_message = "下载失败，请检查地址、网络或 yt-dlp 状态后重试。"
        self.queue_page.update_task(task_id, status="失败")
        self.download_page.set_status(safe_message if "cookies" in message.lower() else message)

    def handle_queue_action(self, task_id: str, action: str) -> None:
        if action == "cancel":
            self.cancel_queue_task(task_id)
        elif action == "pause":
            self.pause_queue_task(task_id)
        elif action == "retry":
            self.retry_queue_task(task_id)

    def cancel_queue_task(self, task_id: str) -> None:
        self._download_task_states[task_id] = "cancel_requested"
        worker = self._download_workers_by_task.get(task_id)
        if worker:
            worker.cancel()
        self.queue_page.update_task(task_id, status="已取消")
        self.download_page.set_status("下载已取消。")

    def pause_queue_task(self, task_id: str) -> None:
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
        self._start_download_worker(task_id, request, "已重新开始下载。")

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
            self._download_task_states.pop(task_id, None)

    def record_finished_download(self) -> None:
        if not self.history_store:
            return
        record = HistoryRecord(
            title=str(self.analyzed_metadata.get("title") or "未命名视频"),
            url=self.analyzed_url,
            output_path=str(self._output_template()),
            download_type=self.download_page.mode_combo.currentText(),
            format_summary=self.selected_format_summary,
            subtitle_behavior=self.formats_page.subtitle_combo.currentText(),
            status="finished",
            created_at=datetime.now().isoformat(timespec="seconds"),
        )
        self.history_store.add(record)
        self.history_page.load_records(self.history_store.list())

    def _first_url(self) -> str:
        for line in self.download_page.url_input.toPlainText().splitlines():
            text = line.strip()
            if text:
                return text
        return ""

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
        folder = self.save_folder_path or self.settings_page.default_folder.text().strip()
        if not folder and self.config_store:
            folder = self.config_store.load().default_save_dir.strip()
        output_dir = Path(folder) if folder else Path.home() / "Downloads"
        return output_dir / "%(title)s.%(ext)s"

    def _download_mode(self) -> str:
        index = self.download_page.mode_combo.currentIndex()
        if index == 1:
            return "audio_only"
        if index == 2:
            return "video_only"
        return "audio_video"

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
