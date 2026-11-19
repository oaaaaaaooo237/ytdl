import ctypes
from pathlib import Path
import sys
from PySide6.QtCore import QCoreApplication, QLockFile, QTimer, Qt

from PySide6.QtGui import QIcon
from PySide6.QtWidgets import QApplication, QMenu, QSystemTrayIcon, QMessageBox

from ytdl_gui import APP_DISPLAY_NAME, __version__
from ytdl_gui.config_store import ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.paths import app_data_dir, app_icon_path
from ytdl_gui.queue_store import QueueStore
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
    tray.setToolTip(APP_DISPLAY_NAME)
    menu = QMenu(window)
    show_action = menu.addAction("显示主窗口")
    quit_action = menu.addAction("退出")
    show_action.triggered.connect(lambda: _restore_window(window))
    quit_action.triggered.connect(window.close)
    tray.setContextMenu(menu)
    tray.activated.connect(lambda reason: _handle_tray_activation(reason, window))
    tray.messageClicked.connect(window.open_notification_task)
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
    scheduled_start = sys.argv[1:3] == ["--scheduled", "--scheduled-profile"] and len(sys.argv) == 4
    _set_windows_app_user_model_id()
    if QCoreApplication.instance() is None:
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_ShareOpenGLContexts)
    app = QApplication.instance() or QApplication(sys.argv)
    app.setApplicationDisplayName(APP_DISPLAY_NAME)
    apply_light_theme(app)
    data_dir = Path(sys.argv[3]) if scheduled_start else app_data_dir()
    if scheduled_start and not data_dir.is_absolute():
        return 1
    data_dir.mkdir(parents=True, exist_ok=True)
    profile_lock = QLockFile(str(data_dir / 'profile.lock'))
    profile_lock.setStaleLockTime(0)
    if not profile_lock.tryLock(0):
        if scheduled_start:
            return 0
        QMessageBox.information(None, '程序已运行', '同一用户数据目录已有运行中的实例，请使用原窗口。')
        return 1
    window = MainWindow(config_store=ConfigStore(data_dir), history_store=HistoryStore(data_dir),
                        queue_store=QueueStore(data_dir))
    window.watch_network()
    app.aboutToQuit.connect(window.shutdown_for_quit)
    configure_application_icon(app, window)
    if scheduled_start:
        window.showMinimized()
        timer = QTimer(window)

        def finish_background_schedule():
            if not window.isMinimized():
                timer.stop()
            elif window.background_schedule_finished():
                timer.stop()
                window.close()

        timer.timeout.connect(finish_background_schedule)
        timer.start(2000)
    else:
        window.show()
        window.maybe_startup_update_check()
    return app.exec()
