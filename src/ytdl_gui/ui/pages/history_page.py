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

from ytdl_gui.ui.widgets import PageHeader


class HistoryPage(QWidget):
    def __init__(self):
        super().__init__()
        self._records = []
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
        self.search.textChanged.connect(self.apply_filter)

    def load_records(self, records) -> None:
        self._records = list(records)
        self.apply_filter(self.search.text())

    def apply_filter(self, text: str = "") -> None:
        needle = text.casefold().strip()
        self.table.setRowCount(0)
        for record in self._records:
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
                record.status,
                record.created_at,
                "打开",
            ]
            for column, value in enumerate(values):
                self.table.setItem(row, column, QTableWidgetItem(str(value)))
