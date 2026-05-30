from PySide6.QtWidgets import QComboBox, QFormLayout, QGroupBox, QVBoxLayout, QWidget

from ytdl_gui.ui.widgets import PageHeader


class FormatsPage(QWidget):
    def __init__(self):
        super().__init__()
        self.resolution_combo = QComboBox()
        self.resolution_combo.addItems(["自动", "2160p", "1440p", "1080p", "720p", "480p"])
        self.fps_combo = QComboBox()
        self.fps_combo.addItems(["自动", "60", "30", "24"])
        self.codec_combo = QComboBox()
        self.codec_combo.addItems(["自动", "H.264", "HEVC", "AV1", "VP9"])
        self.video_bitrate_combo = QComboBox()
        self.video_bitrate_combo.addItems(["自动", "高", "中", "低"])
        self.audio_bitrate_combo = QComboBox()
        self.audio_bitrate_combo.addItems(["自动", "320k", "256k", "192k", "128k"])
        self.container_combo = QComboBox()
        self.container_combo.addItems(["自动", "mp4", "mkv", "webm", "mp3"])
        self.subtitle_combo = QComboBox()
        self.subtitle_combo.addItems(["不下载", "下载字幕文件", "嵌入", "烧录"])

        group = QGroupBox("格式偏好")
        form = QFormLayout(group)
        form.addRow("分辨率", self.resolution_combo)
        form.addRow("帧率", self.fps_combo)
        form.addRow("视频编码", self.codec_combo)
        form.addRow("视频码率偏好", self.video_bitrate_combo)
        form.addRow("音频码率偏好", self.audio_bitrate_combo)
        form.addRow("容器", self.container_combo)
        form.addRow("字幕行为", self.subtitle_combo)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("格式", "设置下载格式偏好；不做强制转码承诺。"))
        layout.addWidget(group)
        layout.addStretch()
