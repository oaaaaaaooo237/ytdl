from PySide6.QtCore import Qt, Signal
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

from ytdl_gui.ui.widgets import PageHeader


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
        self.scroll_area.setWidget(self.card_host)

        actions = QHBoxLayout()
        actions.addWidget(self.pause_all_button)
        actions.addWidget(self.clear_completed_button)
        actions.addStretch()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("队列", "查看待处理、下载中和已完成任务。"))
        layout.addLayout(actions)
        layout.addWidget(self.scroll_area, 1)
        layout.addWidget(self.table, 1)

    def add_task(self, task_id: str, title: str, status: str = "等待中") -> None:
        row = self.table.rowCount()
        self.table.insertRow(row)
        self.table.setVerticalHeaderItem(row, QTableWidgetItem(task_id))
        values = [title, status, "0.0%", "", "", ""]
        for column, value in enumerate(values):
            self.table.setItem(row, column, QTableWidgetItem(value))
        self._add_task_card(task_id, title, status)

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

    def _add_task_card(self, task_id: str, title: str, status: str) -> None:
        card = QFrame()
        card.setObjectName("queueCard")
        layout = QHBoxLayout(card)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(12)

        thumb = QLabel("视频")
        thumb.setObjectName("queueThumb")
        thumb.setFixedSize(84, 54)
        thumb.setAlignment(Qt.AlignmentFlag.AlignCenter)

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
        pause.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "pause"))
        cancel.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "cancel"))
        retry.clicked.connect(lambda _checked=False, current=task_id: self.task_action_requested.emit(current, "retry"))
        actions = QHBoxLayout()
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
