from pathlib import Path

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QStyle,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)
from PySide6.QtWidgets import QHeaderView

from ytdl_gui.ui.widgets import ElidedLabel, display_status


class QueuePage(QWidget):
    task_action_requested = Signal(str, str)
    history_action_requested = Signal(str, int)

    def __init__(self):
        super().__init__()
        self._task_cards: dict[str, dict[str, object]] = {}
        self._recent_history_records = []
        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["标题", "状态", "进度", "速度", "剩余时间", "操作"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.table.hide()
        self.pause_all_button = QPushButton("全部暂停")
        self.clear_completed_button = QPushButton("清除已完成")
        self.pause_all_button.setObjectName("toolbarButton")
        self.clear_completed_button.setObjectName("toolbarButton")

        self.card_host = QWidget()
        self.card_layout = QVBoxLayout(self.card_host)
        self.card_layout.setContentsMargins(0, 0, 0, 0)
        self.card_layout.setSpacing(8)
        self.card_layout.addStretch()

        self.scroll_area = QScrollArea()
        self.scroll_area.setObjectName("queueScroll")
        self.scroll_area.setWidgetResizable(True)
        self.scroll_area.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll_area.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll_area.setMaximumHeight(300)
        self.scroll_area.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Maximum)
        self.scroll_area.setWidget(self.card_host)

        actions = QHBoxLayout()
        actions.setContentsMargins(0, 0, 0, 0)
        actions.setSpacing(8)
        actions.addWidget(self.pause_all_button)
        actions.addWidget(self.clear_completed_button)

        self.queue_heading = QLabel()
        self.queue_heading.setObjectName("queueHeaderTitle")
        self._update_queue_heading()
        self.history_heading = QLabel("历史")
        self.history_heading.setObjectName("sectionTitle")
        self.recent_history_table = QTableWidget(0, 7)
        self.recent_history_table.setObjectName("queueHistoryTable")
        self.recent_history_table.setHorizontalHeaderLabels(["标题", "格式", "大小", "状态", "时间", "", ""])
        history_header = self.recent_history_table.horizontalHeader()
        history_header.setSectionResizeMode(0, QHeaderView.Stretch)
        for column, width in {1: 56, 2: 50, 3: 56, 4: 62, 5: 26, 6: 26}.items():
            history_header.setSectionResizeMode(column, QHeaderView.Fixed)
            self.recent_history_table.setColumnWidth(column, width)
        self.recent_history_table.verticalHeader().hide()
        self.recent_history_table.verticalHeader().setDefaultSectionSize(48)
        self.recent_history_table.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.recent_history_table.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.recent_history_table.setMinimumHeight(144)
        self.open_downloads_button = QPushButton("打开下载文件夹")
        self.open_downloads_button.setObjectName("queueOpenDownloadsButton")
        self.open_downloads_button.setFixedWidth(150)
        self.open_downloads_button.setIcon(self.style().standardIcon(QStyle.StandardPixmap.SP_DirOpenIcon))
        self.open_downloads_button.setIconSize(QSize(15, 15))
        self.history_search = QLineEdit()
        self.history_search.setPlaceholderText("搜索历史...")
        self.history_search.setObjectName("queueHistorySearch")
        self.history_search.setFixedWidth(180)
        self.history_search.addAction(
            self.style().standardIcon(QStyle.StandardPixmap.SP_FileDialogContentsView),
            QLineEdit.ActionPosition.TrailingPosition,
        )
        self.history_search.textChanged.connect(lambda _text: self._render_recent_history())

        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 20, 16, 22)
        layout.setSpacing(10)
        header_row = QHBoxLayout()
        header_row.setContentsMargins(0, 0, 0, 0)
        header_row.setSpacing(12)
        header_row.addWidget(self.queue_heading, 1)
        header_row.addLayout(actions)
        layout.addLayout(header_row)
        layout.addWidget(self.scroll_area, 2)
        layout.addWidget(self.history_heading)
        layout.addWidget(self.recent_history_table, 1)
        history_footer = QHBoxLayout()
        history_footer.setContentsMargins(0, 0, 0, 0)
        history_footer.setSpacing(12)
        history_footer.addWidget(self.open_downloads_button)
        history_footer.addStretch()
        history_footer.addWidget(self.history_search)
        layout.addLayout(history_footer)
        layout.addWidget(self.table, 1)

    def add_task(self, task_id: str, title: str, status: str = "等待中", thumbnail: QPixmap | None = None) -> None:
        row = self.table.rowCount()
        self.table.insertRow(row)
        self.table.setVerticalHeaderItem(row, QTableWidgetItem(task_id))
        values = [title, status, "0.0%", "", "", ""]
        for column, value in enumerate(values):
            self.table.setItem(row, column, QTableWidgetItem(value))
        self._add_task_card(task_id, title, status, thumbnail)
        self._update_queue_heading()

    def update_task(self, task_id: str, status: str | None = None, progress=None, speed: str = "", eta: str = "") -> None:
        row = self._row_for_task(task_id)
        if row < 0:
            return
        if status is not None:
            self.table.setItem(row, 1, QTableWidgetItem(status))
        if progress is not None:
            self.table.setItem(row, 2, QTableWidgetItem(f"{progress:.1f}%"))
        if speed:
            self.table.setItem(row, 3, QTableWidgetItem(speed))
        if eta:
            self.table.setItem(row, 4, QTableWidgetItem(eta))
        self._update_task_card(task_id, status, progress, speed, eta)

    def _row_for_task(self, task_id: str) -> int:
        for row in range(self.table.rowCount()):
            item = self.table.verticalHeaderItem(row)
            if item and item.text() == task_id:
                return row
        return -1

    def _add_task_card(self, task_id: str, title: str, status: str, thumbnail: QPixmap | None = None) -> None:
        card = QFrame()
        card.setObjectName("queueCard")
        card.setFixedHeight(90)
        layout = QHBoxLayout(card)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(12)

        thumb = QLabel("视频")
        thumb.setObjectName("queueThumb")
        thumb.setFixedSize(80, 64)
        thumb.setAlignment(Qt.AlignmentFlag.AlignCenter)
        if thumbnail is not None and not thumbnail.isNull():
            thumb.setText("")
            thumb.setPixmap(
                thumbnail.scaled(
                    thumb.size(),
                    Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                    Qt.TransformationMode.SmoothTransformation,
                )
            )

        content = QVBoxLayout()
        title_label = ElidedLabel(title)
        title_label.setObjectName("queueTitle")
        progress = QProgressBar()
        progress.setRange(0, 1000)
        progress.setValue(0)
        meta = QLabel(status)
        meta.setObjectName("mutedLabel")
        content.addWidget(title_label)
        content.addWidget(progress)
        content.addWidget(meta)

        pause = _queue_action_button(
            self.style().standardIcon(QStyle.StandardPixmap.SP_MediaPause),
            "暂停",
        )
        cancel = _queue_action_button(
            self.style().standardIcon(QStyle.StandardPixmap.SP_DialogCloseButton),
            "取消",
        )
        retry = _queue_action_button(
            self.style().standardIcon(QStyle.StandardPixmap.SP_BrowserReload),
            "重试",
        )
        pause.setObjectName(f"queue-pause-{task_id}")
        cancel.setObjectName(f"queue-cancel-{task_id}")
        retry.setObjectName(f"queue-retry-{task_id}")
        for button in (pause, cancel, retry):
            button.setProperty("queueAction", True)
        retry.setVisible(_should_show_retry(status))
        pause.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "pause"))
        cancel.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "cancel"))
        retry.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "retry"))
        actions = QHBoxLayout()
        actions.setContentsMargins(0, 0, 0, 0)
        actions.setSpacing(6)
        actions.addWidget(pause)
        actions.addWidget(cancel)
        actions.addWidget(retry)

        layout.addWidget(thumb)
        layout.addLayout(content, 1)
        layout.addLayout(actions)
        self.card_layout.insertWidget(max(self.card_layout.count() - 1, 0), card)
        self._task_cards[task_id] = {
            "card": card,
            "title": title_label,
            "progress": progress,
            "meta": meta,
            "thumb": thumb,
            "pause": pause,
            "cancel": cancel,
            "retry": retry,
        }

    def _update_task_card(self, task_id: str, status: str | None, progress_value, speed: str, eta: str) -> None:
        card = self._task_cards.get(task_id)
        if not card:
            return
        progress = card["progress"]
        meta = card["meta"]
        if isinstance(progress, QProgressBar) and progress_value is not None:
            progress.setValue(int(float(progress_value) * 10))
        if isinstance(meta, QLabel):
            parts = [part for part in [status, speed, f"ETA {eta}" if eta else ""] if part]
            if parts:
                meta.setText(" · ".join(parts))
        retry = card.get("retry")
        if isinstance(retry, QPushButton) and status is not None:
            retry.setVisible(_should_show_retry(status))

    def completed_task_ids(self) -> list[str]:
        task_ids: list[str] = []
        for row in range(self.table.rowCount()):
            status_item = self.table.item(row, 1)
            task_item = self.table.verticalHeaderItem(row)
            if status_item and task_item and status_item.text() in {"已完成", "已取消"}:
                task_ids.append(task_item.text())
        return task_ids

    def remove_tasks(self, task_ids: list[str]) -> None:
        for task_id in task_ids:
            row = self._row_for_task(task_id)
            if row >= 0:
                self.table.removeRow(row)
            card = self._task_cards.pop(task_id, None)
            widget = card.get("card") if card else None
            if isinstance(widget, QWidget):
                self.card_layout.removeWidget(widget)
                widget.deleteLater()
        self._update_queue_heading()

    def _update_queue_heading(self) -> None:
        count = self.table.rowCount()
        self.queue_heading.setText(f"下载队列 ({count})")

    def load_history_records(self, records) -> None:
        all_records = list(records)
        start_index = max(len(all_records) - 5, 0)
        self._recent_history_records = list(enumerate(all_records))[start_index:]
        self._render_recent_history()

    def _render_recent_history(self) -> None:
        self.recent_history_table.setRowCount(0)
        query = self.history_search.text().strip().casefold()
        records = [
            (record_index, record)
            for record_index, record in self._recent_history_records
            if not query
            or query in record.title.casefold()
            or query in record.format_summary.casefold()
            or query in record.created_at.casefold()
        ]
        for record_index, record in records:
            row = self.recent_history_table.rowCount()
            self.recent_history_table.insertRow(row)
            self.recent_history_table.setRowHeight(row, 48)
            values = [
                record.title,
                record.format_summary,
                _format_history_size(record),
                display_status(record.status),
                record.created_at,
            ]
            for column, value in enumerate(values):
                self.recent_history_table.setItem(row, column, QTableWidgetItem(str(value)))
                if column == 0:
                    self.recent_history_table.setCellWidget(row, column, _history_title_cell(record))
                    continue
                if column == 3:
                    host = QWidget()
                    host_layout = QHBoxLayout(host)
                    host_layout.setContentsMargins(0, 0, 0, 0)
                    badge = QLabel(str(value))
                    badge.setObjectName("statusBadge")
                    badge.setAlignment(Qt.AlignmentFlag.AlignCenter)
                    badge.setMaximumHeight(22)
                    host_layout.addStretch()
                    host_layout.addWidget(badge)
                    host_layout.addStretch()
                    self.recent_history_table.setCellWidget(row, column, host)
                    continue
            self.recent_history_table.setCellWidget(
                row,
                5,
                _history_action_cell(
                    self.style().standardIcon(QStyle.StandardPixmap.SP_DirOpenIcon),
                    "queueHistoryOpenFolder",
                    "打开所在文件夹",
                    lambda _checked=False, index=record_index: self.history_action_requested.emit("open_folder", index),
                ),
            )
            self.recent_history_table.setCellWidget(
                row,
                6,
                _history_action_cell(
                    self.style().standardIcon(QStyle.StandardPixmap.SP_FileIcon),
                    "queueHistoryOpenFile",
                    "打开文件",
                    lambda _checked=False, index=record_index: self.history_action_requested.emit("open_file", index),
                ),
            )


def _should_show_retry(status: str) -> bool:
    return str(status).casefold() in {
        "failed",
        "canceled",
        "cancelled",
        "paused",
        "失败",
        "已取消",
        "已暂停",
    }


def _format_history_size(record) -> str:
    stored_size = getattr(record, "file_size_bytes", 0)
    try:
        size = int(stored_size)
    except (TypeError, ValueError):
        size = 0
    if size <= 0:
        size = _path_file_size(getattr(record, "output_path", ""))
    return _human_file_size(size) if size > 0 else "-"


def _path_file_size(path: str) -> int:
    try:
        candidate = Path(path)
        if not candidate.is_file():
            return 0
        return candidate.stat().st_size
    except OSError:
        return 0


def _human_file_size(size: int) -> str:
    gigabyte = 1024 * 1024 * 1024
    megabyte = 1024 * 1024
    if size >= gigabyte:
        return f"{size / gigabyte:.2f} GB"
    if size >= megabyte:
        return f"{size / megabyte:.1f} MB"
    return f"{max(size, 0) / 1024:.1f} KB"


def _history_title_cell(record) -> QWidget:
    host = QWidget()
    host.setObjectName("queueHistoryTitleCell")
    layout = QHBoxLayout(host)
    layout.setContentsMargins(4, 4, 2, 4)
    layout.setSpacing(6)

    thumb = QLabel()
    thumb.setObjectName("queueHistoryThumb")
    thumb.setFixedSize(34, 34)
    thumb.setAlignment(Qt.AlignmentFlag.AlignCenter)
    thumbnail = _history_thumbnail(record, thumb.size())
    if thumbnail is not None:
        thumb.setPixmap(thumbnail)
    else:
        thumb.setText("▶")

    title = QLabel(str(record.title))
    title.setObjectName("queueHistoryTitle")
    title.setWordWrap(True)
    title.setMaximumHeight(40)

    layout.addWidget(thumb)
    layout.addWidget(title, 1)
    return host


def _history_thumbnail(record, size: QSize) -> QPixmap | None:
    thumbnail_path = getattr(record, "thumbnail_path", "")
    if not thumbnail_path:
        return None
    pixmap = QPixmap(str(thumbnail_path))
    if pixmap.isNull():
        return None
    return pixmap.scaled(
        size,
        Qt.AspectRatioMode.KeepAspectRatioByExpanding,
        Qt.TransformationMode.SmoothTransformation,
    )


def _history_action_cell(icon, object_name: str, tooltip: str, callback) -> QWidget:
    host = QWidget()
    host.setObjectName("queueHistoryActionCell")
    layout = QHBoxLayout(host)
    layout.setContentsMargins(0, 0, 0, 0)
    button = QPushButton()
    button.setObjectName(object_name)
    button.setProperty("queueHistoryAction", True)
    button.setFixedSize(26, 26)
    button.setIcon(icon)
    button.setIconSize(QSize(14, 14))
    button.setToolTip(tooltip)
    button.clicked.connect(callback)
    layout.addWidget(button, 0, Qt.AlignmentFlag.AlignCenter)
    return host


def _queue_action_button(icon, tooltip: str) -> QPushButton:
    button = QPushButton()
    button.setFixedSize(36, 36)
    button.setIcon(icon)
    button.setIconSize(QSize(16, 16))
    button.setToolTip(tooltip)
    return button
