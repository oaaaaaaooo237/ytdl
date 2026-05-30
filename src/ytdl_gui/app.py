import sys

from PySide6.QtWidgets import QApplication

from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.theme import apply_light_theme


def run() -> int:
    app = QApplication.instance() or QApplication(sys.argv)
    apply_light_theme(app)
    window = MainWindow()
    window.show()
    return app.exec()
