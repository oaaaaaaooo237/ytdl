import sys

from PySide6.QtWidgets import QApplication

from ytdl_gui.config_store import ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.paths import app_data_dir
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.theme import apply_light_theme


def run() -> int:
    app = QApplication.instance() or QApplication(sys.argv)
    apply_light_theme(app)
    data_dir = app_data_dir()
    window = MainWindow(config_store=ConfigStore(data_dir), history_store=HistoryStore(data_dir))
    window.show()
    return app.exec()
