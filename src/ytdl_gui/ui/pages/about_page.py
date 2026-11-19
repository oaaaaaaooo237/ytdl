from PySide6.QtWidgets import QLabel, QPushButton, QVBoxLayout, QWidget

from ytdl_gui import APP_DISPLAY_NAME, __display_version__
from ytdl_gui.ui.widgets import Surface


class AboutPage(QWidget):
    def __init__(self):
        super().__init__()
        self.version_label = QLabel(f"{APP_DISPLAY_NAME} {__display_version__}")
        self.ytdlp_label = QLabel("yt-dlp 状态：待检测")
        self.ffmpeg_label = QLabel("ffmpeg 状态：待检测")
        self.legal_button = QPushButton("第三方许可")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(12)
        details = Surface()
        info = QVBoxLayout(details)
        info.setContentsMargins(16, 16, 16, 16)
        info.setSpacing(12)
        self.version_label.setObjectName("sectionTitle")
        info.addWidget(self.version_label)
        info.addWidget(self.ytdlp_label)
        info.addWidget(self.ffmpeg_label)
        info.addWidget(self.legal_button)
        layout.addWidget(details)

    def load_status(self, ytdlp_status: str = "待检测", ffmpeg_status: str = "待检测") -> None:
        self.set_ytdlp_status(ytdlp_status)
        self.set_ffmpeg_status(ffmpeg_status)

    def set_ytdlp_status(self, status: str) -> None:
        self.ytdlp_label.setText(f"yt-dlp 状态：{status}")

    def set_ffmpeg_status(self, status: str) -> None:
        self.ffmpeg_label.setText(f"ffmpeg 状态：{status}")

    def show_ffmpeg_found(self, status: str) -> None:
        self.set_ffmpeg_status(status)

    def show_ffmpeg_missing_baseline(self) -> None:
        self.set_ffmpeg_status("未找到，基础下载功能仍可使用")
