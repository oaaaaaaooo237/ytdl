from PySide6.QtWidgets import QHBoxLayout, QHeaderView, QLineEdit, QPushButton, QTableWidget, QVBoxLayout, QWidget

from ytdl_gui.ui.widgets import PageHeader


class HistoryPage(QWidget):
    def __init__(self):
        super().__init__()
        self.search = QLineEdit()
        self.search.setPlaceholderText("搜索历史")
        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["标题", "类型", "格式", "状态", "时间", "操作"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.open_folder_button = QPushButton("打开下载文件夹")
        self.clear_button = QPushButton("清空历史")

        actions = QHBoxLayout()
        actions.addWidget(self.open_folder_button)
        actions.addWidget(self.clear_button)
        actions.addStretch()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("历史", "搜索下载记录并打开本地保存位置。"))
        layout.addWidget(self.search)
        layout.addLayout(actions)
        layout.addWidget(self.table, 1)
