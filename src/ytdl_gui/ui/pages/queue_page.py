from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)
from PySide6.QtWidgets import QHeaderView

from ytdl_gui.ui.widgets import PageHeader, display_status


class QueuePage(QWidget):
    task_action_requested = Signal(str, str)

    def __init__(self):
        super().__init__()
        self._task_cards: dict[str, dict[str, object]] = {}
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
        self.card_layout.setSpacing(10)
        self.card_layout.addStretch()

        self.scroll_area = QScrollArea()
        self.scroll_area.setObjectName("queueScroll")
        self.scroll_area.setWidgetResizable(True)
        self.scroll_area.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll_area.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll_area.setWidget(self.card_host)

        actions = QHBoxLayout()
        actions.addWidget(self.pause_all_button)
        actions.addWidget(self.clear_completed_button)
        actions.addStretch()

        self.history_heading = QLabel("历史")
        self.history_heading.setObjectName("sectionTitle")
        self.recent_history_table = QTableWidget(0, 4)
        self.recent_history_table.setObjectName("queueHistoryTable")
        self.recent_history_table.setHorizontalHeaderLabels(["标题", "格式", "状态", "时间"])
        self.recent_history_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.recent_history_table.verticalHeader().hide()
        self.recent_history_table.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.recent_history_table.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.recent_history_table.setMinimumHeight(144)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("队列", "查看待处理、下载中和已完成任务。"))
        layout.addLayout(actions)
        layout.addWidget(self.scroll_area, 2)
        layout.addWidget(self.history_heading)
        layout.addWidget(self.recent_history_table, 1)
        layout.addWidget(self.table, 1)

    def add_task(self, task_id: str, title: str, status: str = "等待中", thumbnail: QPixmap | None = None) -> None:
        row = self.table.rowCount()
        self.table.insertRow(row)
        self.table.setVerticalHeaderItem(row, QTableWidgetItem(task_id))
        values = [title, status, "0.0%", "", "", ""]
        for column, value in enumerate(values):
            self.table.setItem(row, column, QTableWidgetItem(value))
        self._add_task_card(task_id, title, status, thumbnail)

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
        layout = QHBoxLayout(card)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(12)

        thumb = QLabel("视频")
        thumb.setObjectName("queueThumb")
        thumb.setFixedSize(84, 54)
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
        title_label = QLabel(title)
        title_label.setObjectName("queueTitle")
        title_label.setWordWrap(True)
        progress = QProgressBar()
        progress.setRange(0, 1000)
        progress.setValue(0)
        meta = QLabel(status)
        meta.setObjectName("mutedLabel")
        content.addWidget(title_label)
        content.addWidget(progress)
        content.addWidget(meta)

        pause = QPushButton("暂停")
        cancel = QPushButton("取消")
        retry = QPushButton("重试")
        pause.setObjectName(f"queue-pause-{task_id}")
        cancel.setObjectName(f"queue-cancel-{task_id}")
        retry.setObjectName(f"queue-retry-{task_id}")
        for button in (pause, cancel, retry):
            button.setProperty("queueAction", True)
            button.setFixedSize(58, 30)
        retry.setVisible(_should_show_retry(status))
        pause.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "pause"))
        cancel.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "cancel"))
        retry.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "retry"))
        actions = QVBoxLayout()
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

    def load_history_records(self, records) -> None:
        self.recent_history_table.setRowCount(0)
        for record in list(records)[-5:]:
            row = self.recent_history_table.rowCount()
            self.recent_history_table.insertRow(row)
            values = [
                record.title,
                record.format_summary,
                display_status(record.status),
                record.created_at,
            ]
            for column, value in enumerate(values):
                self.recent_history_table.setItem(row, column, QTableWidgetItem(str(value)))


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
