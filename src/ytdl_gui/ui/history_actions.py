from __future__ import annotations

import shutil
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from PySide6.QtCore import QMimeData, Qt, QThread, QUrl
from PySide6.QtWidgets import (
    QApplication,
    QDialog,
    QDialogButtonBox,
    QFileDialog,
    QLabel,
    QListWidget,
    QListWidgetItem,
    QPushButton,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.history_store import HistoryRecord
from ytdl_gui.ui.widgets import ElidedLabel, display_status, human_file_size


@dataclass(frozen=True)
class HistoryArtifact:
    path: Path
    label: str


def _existing_file(value: str | Path) -> Path | None:
    if not value:
        return None
    try:
        path = Path(value)
        if path.is_absolute() and not path.is_symlink() and path.is_file():
            return path
    except (OSError, ValueError, TypeError):
        pass
    return None


def history_artifacts(record: HistoryRecord) -> list[HistoryArtifact]:
    auxiliary = record.auxiliary_paths
    if not isinstance(auxiliary, (list, tuple)):
        auxiliary = ()
    candidates = [
        (record.output_path, "下载文件"),
        *((value, "附加文件") for value in auxiliary),
        (record.torrent_source_path, "种子文件"),
    ]
    artifacts = {}
    for value, label in candidates:
        path = _existing_file(value)
        if path is not None:
            artifacts.setdefault(path, HistoryArtifact(path, label))
    return list(artifacts.values())


def history_source_link(record: HistoryRecord) -> str:
    if not isinstance(record.url, str):
        return ""
    source = record.url.strip()
    if "[已移除敏感内容]" in source or any(char in source for char in "\r\n"):
        return ""
    try:
        parsed = urlsplit(source)
    except ValueError:
        return ""
    if parsed.scheme in {"http", "https"} and parsed.hostname and not parsed.username and not parsed.password:
        return source
    if source.lower().startswith(("magnet:?xt=", "ed2k://|file|")):
        return source
    return ""


class HistoryExportError(Exception):
    def __init__(self, message: str, copied: int = 0):
        super().__init__(message)
        self.copied = copied


def export_history_files(paths: tuple[Path, ...], directory: Path) -> int:
    if not directory.is_dir():
        raise HistoryExportError("导出目录不可用，请重新选择。")
    destinations = set()
    for path in paths:
        if _existing_file(path) is None:
            raise HistoryExportError(f"文件已移动或删除：{path.name}")
        target = directory / path.name
        if target in destinations or target.exists() or target.is_symlink():
            raise HistoryExportError(f"已有同名文件：{path.name}。请选择其他目录，或逐个导出。")
        destinations.add(target)

    for copied, path in enumerate(paths):
        target = directory / path.name
        created = False
        try:
            with path.open("rb") as source, target.open("xb") as destination:
                created = True
                shutil.copyfileobj(source, destination, length=1024 * 1024)
        except FileExistsError as exc:
            raise HistoryExportError(f"已有同名文件：{path.name}。请选择其他目录。", copied) from exc
        except OSError as exc:
            message = f"无法导出：{path.name}。请检查文件是否可读、导出目录是否可写和剩余空间。"
            if created:
                try:
                    target.unlink()
                except OSError:
                    message += f"请检查导出目录中的未完成文件 {target.name}。"
            raise HistoryExportError(message, copied) from exc
    return len(paths)


class _HistoryExport(QThread):
    def __init__(self, paths: tuple[Path, ...], directory: Path, parent: QWidget):
        super().__init__(parent)
        self.paths = paths
        self.directory = directory
        self.message = ""

    def run(self) -> None:
        try:
            count = export_history_files(self.paths, self.directory)
        except HistoryExportError as exc:
            prefix = f"已导出 {exc.copied} 个文件。\n" if exc.copied else ""
            self.message = prefix + str(exc)
        except OSError:
            self.message = "无法访问导出目录，请重新选择。"
        else:
            self.message = f"已导出 {count} 个文件。"


class HistoryShareDialog(QDialog):
    def __init__(self, record: HistoryRecord, parent: QWidget | None = None):
        super().__init__(parent)
        self.setWindowTitle("分享与导出")
        self.resize(490, 325)
        self._source = history_source_link(record)
        self._export: _HistoryExport | None = None
        self._files = QListWidget()
        self._files.setAccessibleName("可分享文件")
        for artifact in history_artifacts(record):
            item = QListWidgetItem(f"{artifact.label} · {artifact.path.name}")
            item.setData(Qt.ItemDataRole.UserRole, str(artifact.path))
            item.setCheckState(Qt.CheckState.Unchecked if artifact.label == "种子文件" else Qt.CheckState.Checked)
            self._files.addItem(item)

        self._message = QLabel()
        self._message.setObjectName("historyShareFeedback")
        self._message.setTextFormat(Qt.TextFormat.PlainText)
        self._message.setWordWrap(True)
        if not self._files.count():
            self._message.setText("没有可用的本地文件。" if self._source else "此记录没有可用的本地文件或来源链接。")
            self._files.hide()
        else:
            self._message.setText("勾选要复制或导出的文件。")

        self._copy_source = QPushButton("复制来源链接")
        self._copy_source.setObjectName("historyCopySource")
        self._copy_source.clicked.connect(self._copy_link)
        self._copy_files = QPushButton("复制文件")
        self._copy_files.setObjectName("historyCopyFiles")
        self._copy_files.clicked.connect(self._copy_selected_files)
        self._save_files = QPushButton("导出文件")
        self._save_files.setObjectName("historyExport")
        self._save_files.clicked.connect(self._export_selected_files)
        self._close = QPushButton("关闭")
        self._close.setDefault(True)
        self._close.clicked.connect(self.reject)

        buttons = QDialogButtonBox()
        for button in (self._copy_source, self._copy_files, self._save_files):
            button.setAutoDefault(False)
            buttons.addButton(button, QDialogButtonBox.ButtonRole.ActionRole)
        buttons.addButton(self._close, QDialogButtonBox.ButtonRole.RejectRole)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(10)
        title = ElidedLabel(str(record.title))
        title.setStyleSheet("font-weight: 600;")
        title.setToolTip("")
        layout.addWidget(title)
        if self._files.count():
            layout.addWidget(self._files, 1)
        layout.addWidget(self._message)
        layout.addWidget(buttons)
        self._files.itemChanged.connect(self._sync_actions)
        self._sync_actions()

    def _selected_paths(self) -> tuple[Path, ...]:
        items = (self._files.item(row) for row in range(self._files.count()))
        return tuple(Path(item.data(Qt.ItemDataRole.UserRole)) for item in items if item.checkState() == Qt.CheckState.Checked)

    def _sync_actions(self) -> None:
        idle = self._export is None
        files_selected = bool(self._selected_paths())
        self._files.setEnabled(idle)
        self._copy_source.setEnabled(idle and bool(self._source))
        self._copy_files.setEnabled(idle and files_selected)
        self._save_files.setEnabled(idle and files_selected)
        self._close.setEnabled(idle)

    def reject(self) -> None:
        if self._export is None:
            super().reject()

    def _copy_link(self) -> None:
        QApplication.clipboard().setText(self._source)
        self._message.setText("已复制来源链接。")

    def _copy_selected_files(self) -> None:
        selected = self._selected_paths()
        if any(_existing_file(path) is None for path in selected):
            self._message.setText("选中的文件已移动或删除，请重新打开分享窗口。")
            return
        mime = QMimeData()
        mime.setUrls([QUrl.fromLocalFile(str(path)) for path in selected])
        mime.setData('application/x-qt-windows-mime;value="Preferred DropEffect"', b"\x01\x00\x00\x00")
        QApplication.clipboard().setMimeData(mime)
        self._message.setText(f"已复制 {len(selected)} 个文件，可在文件资源管理器或支持粘贴附件的应用中粘贴。")

    def _export_selected_files(self) -> None:
        destination = QFileDialog.getExistingDirectory(self, "选择导出目录")
        if not destination:
            return
        self._export = _HistoryExport(self._selected_paths(), Path(destination), self)
        self._export.finished.connect(self._export_finished)
        self._message.setText("正在导出文件…")
        self._sync_actions()
        self._export.start()

    def _export_finished(self) -> None:
        export = self._export
        self._export = None
        self._message.setText(export.message)
        export.deleteLater()
        self._sync_actions()


def show_history_share(parent: QWidget, record: HistoryRecord) -> None:
    dialog = HistoryShareDialog(record, parent)
    dialog.exec()
    dialog.deleteLater()


def show_history_details(parent: QWidget, record: HistoryRecord) -> None:
    dialog = QDialog(parent)
    dialog.setWindowTitle("下载详情")
    dialog.resize(460, 300)
    layout = QVBoxLayout(dialog)
    layout.setContentsMargins(18, 16, 18, 16)
    layout.setSpacing(8)
    lines = [
        record.title,
        f"{display_status(record.status)} · {record.created_at}",
        " · ".join(value for value in (record.download_type, record.format_summary, record.subtitle_behavior) if value),
        human_file_size(record.file_size_bytes) if record.file_size_bytes else "",
        record.output_path,
    ]
    for text in filter(None, lines):
        label = QLabel(text)
        label.setTextFormat(Qt.TextFormat.PlainText)
        label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        label.setWordWrap(True)
        layout.addWidget(label)
    layout.addStretch()
    close = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
    close.button(QDialogButtonBox.StandardButton.Close).setText("关闭")
    close.rejected.connect(dialog.reject)
    layout.addWidget(close)
    dialog.exec()
    dialog.deleteLater()
