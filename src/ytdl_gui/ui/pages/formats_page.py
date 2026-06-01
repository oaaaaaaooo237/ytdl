from PySide6.QtWidgets import QComboBox, QFrame, QGridLayout, QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget

from ytdl_gui.ui.widgets import PageHeader


class FormatsPage(QWidget):
    def __init__(self):
        super().__init__()
        self.resolution_combo = QComboBox()
        self.resolution_combo.addItems(["自动", "2160p", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p"])
        self.fps_combo = QComboBox()
        self.fps_combo.addItems(["自动", "60", "30", "24"])
        self.codec_combo = QComboBox()
        self.codec_combo.addItems(["自动", "H.264", "HEVC", "AV1", "VP9"])
        self.video_bitrate_combo = QComboBox()
        self.video_bitrate_combo.addItems(["自动", "高", "中", "低"])
        self.audio_bitrate_combo = QComboBox()
        self.audio_bitrate_combo.addItems(["自动", "320k", "256k", "192k", "128k"])
        self.container_combo = QComboBox()
        self.container_combo.addItems(["自动", "mp4", "mkv", "webm", "m4a", "mp3"])
        self.subtitle_combo = QComboBox()
        self.subtitle_combo.addItems(["不下载", "下载字幕文件", "嵌入", "烧录"])
        self.format_id_combo = QComboBox()
        self.format_id_combo.addItem("自动")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 18)
        layout.setSpacing(14)
        layout.addWidget(PageHeader("格式", "设置下载格式偏好；不做强制转码承诺。"))

        mode_row = QHBoxLayout()
        for index, text in enumerate(["视频+音频", "仅音频", "仅视频"]):
            button = QPushButton(text)
            button.setCheckable(True)
            button.setObjectName("segmentButton")
            button.setChecked(index == 0)
            mode_row.addWidget(button)
        layout.addLayout(mode_row)

        layout.addWidget(_section("视频选项", [
            ("分辨率", self.resolution_combo),
            ("帧率", self.fps_combo),
            ("视频编码", self.codec_combo),
            ("视频码率偏好", self.video_bitrate_combo),
        ]))
        layout.addWidget(_section("音频选项", [
            ("音频码率偏好", self.audio_bitrate_combo),
        ]))
        layout.addWidget(_section("容器与字幕", [
            ("容器", self.container_combo),
            ("字幕行为", self.subtitle_combo),
            ("实际格式", self.format_id_combo),
        ]))
        layout.addStretch()

    def load_available_formats(self, formats: list[dict], selected_format_id: str) -> None:
        self.format_id_combo.clear()
        for item in formats:
            format_id = str(item.get("format_id") or "")
            if format_id:
                self.format_id_combo.addItem(format_id)
        index = self.format_id_combo.findText(selected_format_id)
        if index >= 0:
            self.format_id_combo.setCurrentIndex(index)


def _section(title: str, rows: list[tuple[str, QWidget]]) -> QFrame:
    frame = QFrame()
    frame.setObjectName("formatSection")
    layout = QVBoxLayout(frame)
    layout.setContentsMargins(14, 12, 14, 12)
    layout.setSpacing(10)
    heading = QLabel(title)
    heading.setObjectName("sectionTitle")
    layout.addWidget(heading)
    grid = QGridLayout()
    grid.setHorizontalSpacing(12)
    grid.setVerticalSpacing(10)
    for row, (label_text, widget) in enumerate(rows):
        label = QLabel(label_text)
        label.setObjectName("optionLabel")
        grid.addWidget(label, row, 0)
        grid.addWidget(widget, row, 1)
    grid.setColumnStretch(1, 1)
    layout.addLayout(grid)
    return frame
