from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QAbstractItemView, QComboBox, QHeaderView, QHBoxLayout, QLabel, QLineEdit,
    QMessageBox, QPushButton, QTabWidget, QTableWidget, QTableWidgetItem,
    QVBoxLayout, QWidget,
)

from ytdl_gui.ui.widgets import human_file_size


NETWORKS = (("全部网络", "all"), ("eD2k 全局", "global"), ("Kad", "kad"))


class _SearchTab(QWidget):
    def __init__(self, query: str, network: str):
        super().__init__()
        self.query = query
        self.network = network
        self.search_id = None
        self.settled = False
        self.closed = False
        self.expired = False
        self.rows = []
        self.status = QLabel("正在连接电驴网络…")
        self.status.setTextFormat(Qt.TextFormat.PlainText)
        self.status.setWordWrap(True)
        self.retry = QPushButton("重新搜索")
        self.retry.hide()
        self.table = QTableWidget(0, 4)
        self.table.setHorizontalHeaderLabels(["文件名", "大小", "完整来源", "总来源"])
        self.table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        for column, width in ((1, 78), (2, 76), (3, 64)):
            self.table.horizontalHeader().setSectionResizeMode(column, QHeaderView.ResizeMode.Fixed)
            self.table.setColumnWidth(column, width)
        self.table.setWordWrap(False)
        self.table.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.table.verticalHeader().hide()
        layout = QVBoxLayout(self)
        status_row = QHBoxLayout()
        status_row.addWidget(self.status, 1)
        status_row.addWidget(self.retry)
        layout.addLayout(status_row)
        layout.addWidget(self.table, 1)


class EmuleSearchPage(QWidget):
    search_requested = Signal(str, str)
    download_requested = Signal(object, object)
    back_requested = Signal()

    def __init__(self):
        super().__init__()
        back = QPushButton("返回下载")
        back.clicked.connect(self.back_requested)
        title = QLabel("电驴搜索")
        title.setObjectName("sectionTitle")
        self.query = QLineEdit()
        self.query.setPlaceholderText("输入文件名或关键词")
        self.network = QComboBox()
        for label, value in NETWORKS:
            self.network.addItem(label, value)
        search = QPushButton("搜索")
        search.setObjectName("primaryButton")
        search.clicked.connect(self._submit)
        self.query.returnPressed.connect(self._submit)
        self.tabs = QTabWidget()
        self.tabs.setTabsClosable(True)
        self.tabs.setUsesScrollButtons(True)
        self.tabs.tabCloseRequested.connect(self._close_tab)
        header = QHBoxLayout()
        header.addWidget(title, 1)
        header.addWidget(back)
        form = QHBoxLayout()
        form.addWidget(self.query, 1)
        form.addWidget(self.network)
        form.addWidget(search)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(16, 20, 16, 22)
        layout.addLayout(header)
        layout.addLayout(form)
        layout.addWidget(self.tabs, 1)

    def _submit(self) -> None:
        query = self.query.text().strip()
        if query:
            self.search_requested.emit(query, self.network.currentData())

    def _close_tab(self, index: int) -> None:
        tab = self.tabs.widget(index)
        tab.closed = True
        self.tabs.removeTab(index)
        tab.deleteLater()

    def add_search(self, query: str, network: str) -> _SearchTab:
        self.query.setText(query)
        tab = _SearchTab(query, network)
        tab.retry.clicked.connect(lambda: self.search_requested.emit(query, network))
        tab.table.cellClicked.connect(lambda row, _column: self._offer_download(tab, row))
        index = self.tabs.addTab(tab, self._tab_label(query, 0))
        self.tabs.setTabToolTip(index, query)
        self.tabs.setCurrentIndex(index)
        return tab

    @staticmethod
    def _tab_label(query: str, count: int) -> str:
        prefix = query[:12] + ("…" if len(query) > 12 else "")
        return f"{prefix} ({count})"

    def show_results(self, tab: _SearchTab, results: list[dict], state: str) -> None:
        index = self.tabs.indexOf(tab)
        if index < 0:
            return
        current_row = tab.table.currentRow()
        selected = tab.rows[current_row]["hash"] if 0 <= current_row < len(tab.rows) else None
        ranked = sorted(results, key=lambda item: (
            -item["sources"]["complete"], -item["sources"]["total"], item["name"].casefold(), item["hash"],
        ))
        if tab.rows != ranked:
            tab.rows = ranked
            tab.table.setRowCount(len(ranked))
            for row, item in enumerate(ranked):
                size = human_file_size(item["size_bytes"])
                for col, value in enumerate((item["name"], size, item["sources"]["complete"], item["sources"]["total"])):
                    cell = QTableWidgetItem(str(value))
                    cell.setToolTip(str(value))
                    tab.table.setItem(row, col, cell)
                if item["hash"] == selected:
                    tab.table.selectRow(row)
        self.tabs.setTabText(index, self._tab_label(tab.query, len(ranked)))
        tab.status.setText("正在搜索…" if state == "running" else ("搜索完成" if ranked else "没有找到匹配文件"))

    def show_expired(self, tab: _SearchTab) -> None:
        tab.expired = True
        tab.status.setText("此搜索已过期。")
        tab.retry.show()

    def _offer_download(self, tab: _SearchTab, row: int) -> None:
        item = tab.rows[row]
        dialog = QMessageBox(self)
        dialog.setWindowTitle("电驴下载")
        dialog.setTextFormat(Qt.TextFormat.PlainText)
        dialog.setText(item["name"])
        dialog.setInformativeText(
            f"{item['size_bytes']:,} 字节 · 完整来源 {item['sources']['complete']}\n"
            "电驴运行时可能向同网用户上传已下载片段及电驴保存目录内的文件。"
        )
        button = dialog.addButton("下载", QMessageBox.ButtonRole.AcceptRole)
        dialog.addButton("取消", QMessageBox.ButtonRole.RejectRole)
        dialog.exec()
        if dialog.clickedButton() == button:
            self.download_requested.emit(tab, item)
