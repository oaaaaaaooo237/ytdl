from datetime import datetime
from html import escape

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QBrush
from PySide6.QtWidgets import (
    QAbstractItemView,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMenu,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QToolButton,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.ui.widgets import ElidedLabel, PageHeader, display_status


class HistoryPage(QWidget):
    history_action_requested = Signal(str, int)

    def __init__(self):
        super().__init__()
        self._records = []
        self.search = QLineEdit()
        self.search.setPlaceholderText("搜索历史")
        self.search.setAccessibleName("搜索历史")
        self.search.setClearButtonEnabled(True)
        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(["标题", "类型", "格式", "状态", "时间", "操作"])
        self.table.setShowGrid(False)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.table.verticalHeader().hide()
        self.table.verticalHeader().setDefaultSectionSize(54)
        header = self.table.horizontalHeader()
        header.setSectionResizeMode(0, QHeaderView.Stretch)
        for column in (1, 2, 4):
            self.table.hideColumn(column)
        header.setSectionResizeMode(3, QHeaderView.Fixed)
        header.setSectionResizeMode(5, QHeaderView.Fixed)
        self.table.setColumnWidth(3, 68)
        self.table.setColumnWidth(5, 162)
        self.empty = QLabel()
        self.empty.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.empty.setWordWrap(True)
        self.open_folder_button = QPushButton("打开下载文件夹")
        self.clear_button = QPushButton("清空历史")

        actions = QHBoxLayout()
        actions.addWidget(self.open_folder_button)
        actions.addWidget(self.clear_button)
        actions.addStretch()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("历史"))
        layout.addWidget(self.search)
        layout.addLayout(actions)
        layout.addWidget(self.table, 1)
        layout.addWidget(self.empty, 1)
        self.search.textChanged.connect(self.apply_filter)
        self.apply_filter()

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
                item = QTableWidgetItem(str(value))
                item.setToolTip(f"<p>{escape(str(value))}</p>")
                if column == 0:
                    item.setForeground(QBrush(Qt.GlobalColor.transparent))
                if column == 3:
                    font = item.font()
                    font.setBold(True)
                    item.setFont(font)
                self.table.setItem(row, column, item)
            self.table.setCellWidget(row, 0, self._summary_widget(record))
            self.table.setCellWidget(row, 5, self._action_widget(record_index))
        has_rows = bool(self.table.rowCount())
        self.table.setVisible(has_rows)
        self.empty.setVisible(not has_rows)
        self.empty.setText("没有匹配的下载记录。" if needle else "下载完成后，文件将在这里显示。")
        self.clear_button.setEnabled(bool(self._records))

    def _summary_widget(self, record) -> QWidget:
        widget = QWidget()
        widget.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        widget.setStyleSheet("background: transparent;")
        layout = QVBoxLayout(widget)
        layout.setContentsMargins(8, 0, 8, 0)
        layout.setSpacing(4)
        layout.setAlignment(Qt.AlignmentFlag.AlignVCenter)
        title_text = str(record.title)
        title = ElidedLabel(title_text)
        title.setStyleSheet("font-weight: 600;")
        title.setToolTip(f"<p>{escape(title_text)}</p>")
        time = str(record.created_at).replace("T", " ")
        try:
            time = datetime.fromisoformat(time).strftime("%m/%d %H:%M")
        except ValueError:
            pass
        values = dict.fromkeys((record.download_type, record.format_summary, time))
        meta = " · ".join(str(value) for value in values if value)
        summary = ElidedLabel(meta)
        summary.setObjectName("pageSubtitle")
        summary.setToolTip(f"<p>{escape(meta)}</p>")
        layout.addWidget(title)
        layout.addWidget(summary)
        return widget

    def _action_widget(self, record_index: int) -> QWidget:
        widget = QWidget()
        widget.setObjectName("historyRowActions")
        widget.setStyleSheet("#historyRowActions { background: transparent; }")
        layout = QHBoxLayout(widget)
        layout.setContentsMargins(4, 0, 4, 0)
        layout.setSpacing(5)
        for text, action in [("打开", "open_file"), ("分享", "share")]:
            button = QPushButton(text)
            button.setProperty("historyTextAction", True)
            button.setFixedSize(45, 30)
            if action == "share":
                button.setObjectName("historyShare")
            button.clicked.connect(
                lambda _checked=False, current_action=action, current_index=record_index: self.history_action_requested.emit(
                    current_action, current_index
                )
            )
            layout.addWidget(button)
        more = QToolButton(widget)
        more.setObjectName("historyMore")
        more.setText("更多")
        more.setFixedSize(54, 30)
        more.setPopupMode(QToolButton.ToolButtonPopupMode.InstantPopup)
        menu = QMenu(more)
        for text, name in [("打开目录", "open_folder"), ("重新下载", "redownload"), ("下载详情", "details"), ("删除记录", "delete")]:
            action = menu.addAction(text)
            action.setData(name)
        menu.triggered.connect(lambda action: self.history_action_requested.emit(action.data(), record_index))
        more.setMenu(menu)
        layout.addWidget(more)
        layout.addStretch()
        return widget
