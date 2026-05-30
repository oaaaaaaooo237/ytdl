from PySide6.QtWidgets import QHBoxLayout, QPushButton, QTableWidget, QVBoxLayout, QWidget
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
