from PySide6.QtWidgets import QHBoxLayout, QPushButton, QTableWidget, QTableWidgetItem, QVBoxLayout, QWidget
from PySide6.QtWidgets import QHeaderView

from ytdl_gui.ui.widgets import PageHeader


class QueuePage(QWidget):
    def __init__(self):
        super().__init__()
        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["标题", "状态", "进度", "速度", "剩余时间", "操作"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.pause_all_button = QPushButton("全部暂停")
        self.clear_completed_button = QPushButton("清除已完成")

        actions = QHBoxLayout()
        actions.addWidget(self.pause_all_button)
        actions.addWidget(self.clear_completed_button)
        actions.addStretch()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("队列", "查看待处理、下载中和已完成任务。"))
        layout.addLayout(actions)
        layout.addWidget(self.table, 1)

    def add_task(self, task_id: str, title: str, status: str = "等待中") -> None:
        row = self.table.rowCount()
        self.table.insertRow(row)
        self.table.setVerticalHeaderItem(row, QTableWidgetItem(task_id))
        values = [title, status, "0.0%", "", "", ""]
        for column, value in enumerate(values):
            self.table.setItem(row, column, QTableWidgetItem(value))

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

    def _row_for_task(self, task_id: str) -> int:
        for row in range(self.table.rowCount()):
            item = self.table.verticalHeaderItem(row)
            if item and item.text() == task_id:
                return row
        return -1
