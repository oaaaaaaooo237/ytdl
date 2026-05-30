from PySide6.QtWidgets import QLabel, QPushButton, QVBoxLayout, QWidget

from ytdl_gui import __version__
from ytdl_gui.ui.widgets import PageHeader


class AboutPage(QWidget):
    def __init__(self):
        super().__init__()
        self.version_label = QLabel(f"YTDL GUI {__version__}")
        self.ytdlp_label = QLabel("yt-dlp 状态：待检测")
        self.ffmpeg_label = QLabel("ffmpeg 状态：待检测")
        self.legal_button = QPushButton("第三方许可")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("关于", "查看组件状态和第三方许可信息。"))
        layout.addWidget(self.version_label)
        layout.addWidget(self.ytdlp_label)
        layout.addWidget(self.ffmpeg_label)
        layout.addWidget(self.legal_button)
        layout.addStretch()

    def load_status(self, ytdlp_status: str = "待检测", ffmpeg_status: str = "待检测") -> None:
        self.set_ytdlp_status(ytdlp_status)
        self.set_ffmpeg_status(ffmpeg_status)

    def set_ytdlp_status(self, status: str) -> None:
        self.ytdlp_label.setText(f"yt-dlp 状态：{status}")

    def set_ffmpeg_status(self, status: str) -> None:
        self.ffmpeg_label.setText(f"ffmpeg 状态：{status}")
