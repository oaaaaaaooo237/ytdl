from PySide6.QtCore import Signal
from PySide6.QtWidgets import (
    QHBoxLayout,
    QHeaderView,
    QLineEdit,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.ui.widgets import PageHeader, display_status


class HistoryPage(QWidget):
    history_action_requested = Signal(str, int)

    def __init__(self):
        super().__init__()
        self._records = []
        self.search = QLineEdit()
        self.search.setPlaceholderText("搜索历史")
        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["标题", "类型", "格式", "状态", "时间", "操作"])
        header = self.table.horizontalHeader()
        for column in range(5):
            header.setSectionResizeMode(column, QHeaderView.Stretch)
        header.setSectionResizeMode(5, QHeaderView.Fixed)
        self.table.setColumnWidth(5, 224)
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
        self.search.textChanged.connect(self.apply_filter)

    def load_records(self, records) -> None:
        self._records = list(records)
        self.apply_filter(self.search.text())

    def apply_filter(self, text: str = "") -> None:
        needle = text.casefold().strip()
        self.table.setRowCount(0)
        for record_index, record in enumerate(self._records):
            searchable = " ".join(
                [
                    str(record.title),
                    str(record.url),
                    str(record.download_type),
                    str(record.format_summary),
                    str(record.status),
                    str(record.created_at),
                ]
            ).casefold()
            if needle and needle not in searchable:
                continue
            row = self.table.rowCount()
            self.table.insertRow(row)
            values = [
                record.title,
                record.download_type,
                record.format_summary,
                display_status(record.status),
                record.created_at,
            ]
            for column, value in enumerate(values):
                self.table.setItem(row, column, QTableWidgetItem(str(value)))
            self.table.setCellWidget(row, 5, self._action_widget(record_index))

    def _action_widget(self, record_index: int) -> QWidget:
        widget = QWidget()
        layout = QHBoxLayout(widget)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(4)
        for text, action in [("打开", "open_file"), ("目录", "open_folder"), ("重下", "redownload"), ("删除", "delete")]:
            button = QPushButton(text)
            button.setFixedWidth(52)
            button.clicked.connect(
                lambda _checked=False, current_action=action, current_index=record_index: self.history_action_requested.emit(
                    current_action, current_index
                )
            )
            layout.addWidget(button)
        layout.addStretch()
        return widget
