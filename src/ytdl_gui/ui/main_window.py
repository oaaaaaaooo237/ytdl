from PySide6.QtWidgets import QHBoxLayout, QListWidget, QStackedWidget, QWidget

from ytdl_gui.ui.pages.about_page import AboutPage
from ytdl_gui.ui.pages.download_page import DownloadPage
from ytdl_gui.ui.pages.formats_page import FormatsPage
from ytdl_gui.ui.pages.history_page import HistoryPage
from ytdl_gui.ui.pages.queue_page import QueuePage
from ytdl_gui.ui.pages.settings_page import SettingsPage


class MainWindow(QWidget):
    def __init__(self, config_store=None, history_store=None):
        super().__init__()
        self.setWindowTitle("视频地址提取器")
        self.resize(1120, 720)
        self.config_store = config_store
        self.history_store = history_store

        self.nav = QListWidget()
        self.nav.setFixedWidth(168)
        self.nav.addItems(["下载", "格式", "队列", "历史", "设置", "关于"])

        self.stack = QStackedWidget()
        self.download_page = DownloadPage()
        self.formats_page = FormatsPage()
        self.queue_page = QueuePage()
        self.history_page = HistoryPage()
        self.settings_page = SettingsPage()
        self.about_page = AboutPage()

        pages = [
            self.download_page,
            self.formats_page,
            self.queue_page,
            self.history_page,
            self.settings_page,
            self.about_page,
        ]
        for page in pages:
            self.stack.addWidget(page)

        self.nav.currentRowChanged.connect(self.stack.setCurrentIndex)
        self.nav.setCurrentRow(0)

        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)
        layout.addWidget(self.nav)
        layout.addWidget(self.stack, 1)

        if self.config_store:
            self.settings_page.load_config(self.config_store.load())
        if self.history_store:
            self.history_page.load_records(self.history_store.list())
