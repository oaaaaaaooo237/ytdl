"""Local and available yt-dlp versions for Windows settings."""
from pathlib import Path
from typing import Callable

from PySide6.QtCore import QObject, QRunnable, QThreadPool, Qt, Signal, Slot
from PySide6.QtWidgets import (
    QDialog, QHBoxLayout, QLabel, QListWidget, QListWidgetItem, QPushButton, QVBoxLayout,
)

from ytdl_gui.parser_catalog import (
    LocalParserVersion, delete_parser_version, download_parser_release, fetch_parser_releases,
    installed_parser_versions, parser_version_key,
)


class _OperationSignals(QObject):
    completed = Signal(object, object)


class _Operation(QRunnable):
    def __init__(self, action):
        super().__init__()
        self.action = action
        self.signals = _OperationSignals()

    def run(self):
        try:
            result = self.action()
        except Exception as error:
            self.signals.completed.emit(None, error)
        else:
            self.signals.completed.emit(result, None)


class ParserVersionDialog(QDialog):
    def __init__(
        self,
        data_dir: Path,
        bundled_path: Path,
        *,
        get_active: Callable[[], tuple[Path, str]],
        apply_version: Callable[[Path, str], None],
        can_change: Callable[[], bool],
        parent=None,
    ):
        super().__init__(parent)
        self._data_dir = data_dir
        self._bundled_path = bundled_path
        self._get_active = get_active
        self._apply_version = apply_version
        self._can_change = can_change
        self._local: list[LocalParserVersion] = []
        self._releases: list[str] = []
        self._operation = None
        self._on_success = None
        self._error_message = ""

        self.setWindowTitle("解析器版本")
        self.resize(620, 410)
        self.setMinimumSize(540, 320)

        self.versions = QListWidget()
        self.versions.setAccessibleName("解析器版本列表")
        self.status = QLabel()
        self.status.setWordWrap(True)
        self.status.setTextFormat(Qt.TextFormat.PlainText)
        self.status.setObjectName("footerMuted")
        self.check_button = self._button("检查更新", self._check)
        self.download_button = self._button("下载选中版本", self._download)
        self.apply_button = self._button("应用", self._apply)
        self.delete_button = self._button("删除", self._delete)

        actions = QHBoxLayout()
        actions.addWidget(self.check_button)
        actions.addWidget(self.download_button)
        actions.addWidget(self.apply_button)
        actions.addWidget(self.delete_button)
        actions.addStretch(1)
        actions.addWidget(self._button("关闭", self.reject))

        layout = QVBoxLayout(self)
        layout.addWidget(self.versions, 1)
        layout.addWidget(self.status)
        layout.addLayout(actions)
        self.versions.currentItemChanged.connect(self._update_actions)

        active_path, active_version = get_active()
        self._start(
            lambda: installed_parser_versions(data_dir, bundled_path, active_path, active_version),
            self._show_local,
            "正在读取解析器版本…",
            "读取解析器版本失败。",
        )

    @staticmethod
    def _button(title, callback):
        button = QPushButton(title)
        button.setAutoDefault(False)
        button.clicked.connect(callback)
        return button

    def _start(self, action, on_success, status, error_message):
        self._operation = _Operation(action)
        self._on_success = on_success
        self._error_message = error_message
        self.status.setText(status)
        self._update_actions()
        self._operation.signals.completed.connect(self._finish, Qt.ConnectionType.QueuedConnection)
        QThreadPool.globalInstance().start(self._operation)

    @Slot(object, object)
    def _finish(self, result, error):
        self._operation = None
        if error is None:
            self._on_success(result)
        else:
            detail = f" {error}" if type(error) is ValueError else ""
            self.status.setText(self._error_message + detail)
        self._update_actions()

    def _show_local(self, entries):
        self._local = entries
        self._populate()
        self.status.setText("检查更新可查看 GitHub 上的 yt-dlp 稳定版本。")

    def _populate(self, preferred_path: Path | None = None):
        active_path = self._get_active()[0].resolve()
        target = (preferred_path or active_path).resolve()
        self.versions.clear()
        chosen_row = 0
        local_versions = set()
        for entry in self._local:
            suffix = " • 当前使用" if entry.path.resolve() == active_path else ""
            if entry.available:
                local_versions.add(entry.version)
            else:
                suffix += " • 文件缺失"
            label = f"{entry.version or '版本未知'}  ·  {entry.source}{suffix}"
            item = QListWidgetItem(label)
            item.setData(Qt.ItemDataRole.UserRole, entry)
            self.versions.addItem(item)
            if entry.path.resolve() == target:
                chosen_row = self.versions.count() - 1
        for version in self._releases:
            if version not in local_versions:
                item = QListWidgetItem(f"{version}  ·  GitHub 稳定版")
                item.setData(Qt.ItemDataRole.UserRole, version)
                self.versions.addItem(item)
        self.versions.setCurrentRow(chosen_row)

    def _selected(self):
        item = self.versions.currentItem()
        return item.data(Qt.ItemDataRole.UserRole) if item is not None else None

    def _update_actions(self, *_args):
        ready = self._operation is None
        selected = self._selected()
        local = isinstance(selected, LocalParserVersion) and selected.available
        inactive = local and selected.path.resolve() != self._get_active()[0].resolve()
        self.check_button.setEnabled(ready)
        self.download_button.setEnabled(ready and isinstance(selected, str))
        self.apply_button.setEnabled(ready and inactive)
        self.delete_button.setEnabled(ready and inactive and selected.source == "已下载")

    def _check(self):
        self._start(fetch_parser_releases, self._show_releases, "正在检查更新…", "检查解析器更新失败。")

    def _show_releases(self, versions):
        self._releases = versions
        self._populate()
        if not versions:
            self.status.setText("GitHub 暂无可下载的稳定版。")
            return
        active_path, active_version = self._get_active()
        active_version = active_version or next(
            (entry.version for entry in self._local if entry.path.resolve() == active_path.resolve()),
            "",
        )
        latest = versions[0]
        if parser_version_key(active_version) == parser_version_key(latest):
            message = f"当前使用最新稳定版 {latest}。"
        else:
            message = f"最新稳定版为 {latest}。"
        self.status.setText(message)

    def _download(self):
        version = self._selected()
        if not isinstance(version, str):
            return
        data_dir = self._data_dir
        self._start(
            lambda: download_parser_release(data_dir, version),
            self._show_download,
            f"正在下载 {version}…",
            f"下载解析器 {version} 失败。",
        )

    def _show_download(self, path):
        version = path.parent.name
        self._local.append(LocalParserVersion(path, version, "已下载", True))
        self._populate(preferred_path=path)
        self.status.setText(f"已下载 {version}。点击“应用”即可切换。")

    def _apply(self):
        selected = self._selected()
        if not isinstance(selected, LocalParserVersion) or not selected.path.is_file():
            self.status.setText("解析器文件缺失，请选择其他版本。")
            return
        if not self._can_change():
            self.status.setText("请在当前任务结束后切换解析器。")
            return
        try:
            self._apply_version(selected.path, selected.version)
        except (OSError, ValueError):
            self.status.setText("保存解析器设置失败。")
            return
        self._populate()
        self.status.setText(f"当前使用 {selected.version or selected.source} 解析器。")

    def _delete(self):
        selected = self._selected()
        if not isinstance(selected, LocalParserVersion) or selected.source != "已下载":
            return
        if not self._can_change():
            self.status.setText("请在当前任务结束后删除解析器。")
            return
        try:
            delete_parser_version(self._data_dir, selected.path, self._get_active()[0])
        except (OSError, ValueError):
            self.status.setText("删除解析器失败。")
            return
        self._local.remove(selected)
        self._populate()
        self.status.setText(f"已删除 {selected.version}。")
