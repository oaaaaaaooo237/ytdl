import ctypes
import sys

from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QApplication, QMenu, QSystemTrayIcon

from ytdl_gui import __version__
from ytdl_gui.config_store import ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.paths import app_data_dir, app_icon_path
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.theme import apply_light_theme


APP_USER_MODEL_ID = f"oaaaaaaooo237.ytdl-gui.{__version__}"


def load_app_icon() -> QIcon:
    return QIcon(str(app_icon_path()))


def configure_application_icon(app, window) -> QSystemTrayIcon | None:
    icon = load_app_icon()
    if icon.isNull():
        return None

    app.setWindowIcon(icon)
    window.setWindowIcon(icon)

    if not QSystemTrayIcon.isSystemTrayAvailable():
        return None

    tray = QSystemTrayIcon(icon, window)
    tray.setToolTip("视频地址提取器")
    menu = QMenu(window)
    show_action = menu.addAction("显示主窗口")
    quit_action = menu.addAction("退出")
    show_action.triggered.connect(lambda: _restore_window(window))
    quit_action.triggered.connect(app.quit)
    tray.setContextMenu(menu)
    tray.activated.connect(lambda reason: _handle_tray_activation(reason, window))
    tray.show()
    window._tray_icon = tray
    window._tray_menu = menu
    return tray


def _handle_tray_activation(reason, window) -> None:
    if reason in {
        QSystemTrayIcon.ActivationReason.Trigger,
        QSystemTrayIcon.ActivationReason.DoubleClick,
    }:
        _restore_window(window)


def _restore_window(window) -> None:
    if hasattr(window, "showNormal"):
        window.showNormal()
    else:
        window.show()
    window.raise_()
    window.activateWindow()


def _set_windows_app_user_model_id() -> None:
    if sys.platform != "win32":
        return
    try:
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(APP_USER_MODEL_ID)
    except (AttributeError, OSError):
        return


def run() -> int:
    _set_windows_app_user_model_id()
    app = QApplication.instance() or QApplication(sys.argv)
    apply_light_theme(app)
    data_dir = app_data_dir()
    window = MainWindow(config_store=ConfigStore(data_dir), history_store=HistoryStore(data_dir))
    configure_application_icon(app, window)
    window.show()
    window.maybe_startup_update_check()
    return app.exec()
