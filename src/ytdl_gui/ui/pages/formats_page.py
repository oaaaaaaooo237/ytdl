from PySide6.QtWidgets import (
    QButtonGroup,
    QComboBox,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QRadioButton,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.ui.widgets import PageHeader


class FormatsPage(QWidget):
    def __init__(self):
        super().__init__()
        self.resolution_combo = QComboBox()
        self.resolution_combo.setObjectName("hiddenResolutionCombo")
        self.resolution_combo.hide()
        self.resolution_buttons: list[QRadioButton] = []
        self.resolution_size_labels: dict[str, QLabel] = {}
        self.resolution_button_group = QButtonGroup(self)
        self.resolution_button_group.setExclusive(True)
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
        self.format_id_combo.setObjectName("hiddenFormatIdCombo")
        self.format_id_combo.hide()
        self.actual_format_label = QLabel("自动")
        self.actual_format_label.setObjectName("actualFormatLabel")
        self.actual_format_label.setWordWrap(True)
        self.actual_format_label.setMinimumHeight(32)
        self.merge_hint_label = QLabel("当前选择会分别下载视频流和音频流，并使用 ffmpeg 合并。")
        self.merge_hint_label.setObjectName("mergeHintLabel")
        self.merge_hint_label.setWordWrap(True)
        self.merge_hint_label.hide()
        self.reset_button = QPushButton("重置默认")
        self.apply_button = QPushButton("应用选择")
        self.apply_button.setObjectName("primaryButton")
        self.mode_buttons: list[QPushButton] = []
        self.mode_button_group = QButtonGroup(self)
        self.mode_button_group.setExclusive(True)
        for combo in (
            self.fps_combo,
            self.codec_combo,
            self.video_bitrate_combo,
            self.audio_bitrate_combo,
            self.container_combo,
            self.subtitle_combo,
            self.format_id_combo,
        ):
            combo.setFixedHeight(32)
        self.format_id_combo.addItem("自动")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(22, 14, 24, 8)
        layout.setSpacing(8)
        layout.addWidget(PageHeader("格式", "设置下载格式偏好；不做强制转码承诺。"))

        mode_row = QHBoxLayout()
        for index, text in enumerate(["视频+音频", "仅音频", "仅视频"]):
            button = QPushButton(text)
            button.setCheckable(True)
            button.setObjectName("segmentButton")
            button.setChecked(index == 0)
            self.mode_buttons.append(button)
            self.mode_button_group.addButton(button, index)
            button.setFixedHeight(42)
            mode_row.addWidget(button)
        layout.addLayout(mode_row)
        layout.addWidget(self._resolution_section())
        self.resolution_combo.currentTextChanged.connect(self._sync_resolution_buttons)
        self.resolution_combo.setCurrentText("自动")
        self._sync_resolution_buttons(self.resolution_combo.currentText())

        layout.addWidget(_section("音频选项", [
            ("音频码率偏好", self.audio_bitrate_combo),
        ]))
        layout.addWidget(_section("容器与字幕", [
            ("容器", self.container_combo),
            ("字幕行为", self.subtitle_combo),
            ("实际格式", self.actual_format_label),
        ]))
        layout.addWidget(self.merge_hint_label)
        action_row = QHBoxLayout()
        action_row.addWidget(self.reset_button)
        action_row.addStretch()
        action_row.addWidget(self.apply_button)
        layout.addLayout(action_row)
        layout.addStretch()

    def _resolution_section(self) -> QFrame:
        frame = QFrame()
        frame.setObjectName("formatSection")
        layout = QVBoxLayout(frame)
        layout.setContentsMargins(10, 8, 10, 8)
        layout.setSpacing(5)

        heading = QLabel("视频选项")
        heading.setObjectName("sectionTitle")
        layout.addWidget(heading)

        grid = QGridLayout()
        grid.setHorizontalSpacing(12)
        grid.setVerticalSpacing(2)
        for column, text in enumerate(["分辨率", "画面尺寸"]):
            label = QLabel(text)
            label.setObjectName("mutedLabel")
            grid.addWidget(label, 0, column)

        rows = [
            ("自动", "", "自动"),
            ("2160p (4K)", "3840x2160", "2160p"),
            ("1440p (2K)", "2560x1440", "1440p"),
            ("1080p (FHD)", "1920x1080", "1080p"),
            ("720p (HD)", "1280x720", "720p"),
            ("480p", "854x480", "480p"),
            ("360p", "640x360", "360p"),
            ("240p", "426x240", "240p"),
        ]
        for row_index, (label_text, size_text, value) in enumerate(rows, start=1):
            button = QRadioButton(label_text)
            button.setProperty("resolutionValue", value)
            button.clicked.connect(lambda _checked=False, current=value: self._set_resolution_from_button(current))
            self.resolution_buttons.append(button)
            self.resolution_button_group.addButton(button)
            grid.addWidget(button, row_index, 0)
            size_label = QLabel(size_text)
            size_label.setObjectName("resolutionSizeLabel")
            self.resolution_size_labels[value] = size_label
            grid.addWidget(size_label, row_index, 1)

        grid.setColumnStretch(0, 1)
        grid.setColumnStretch(1, 1)
        layout.addLayout(grid)

        detail_grid = QGridLayout()
        detail_grid.setHorizontalSpacing(12)
        detail_grid.setVerticalSpacing(4)
        for row, (label_text, widget) in enumerate(
            [
                ("帧率", self.fps_combo),
                ("视频编码", self.codec_combo),
                ("视频码率偏好", self.video_bitrate_combo),
            ]
        ):
            label = QLabel(label_text)
            label.setObjectName("optionLabel")
            detail_grid.addWidget(label, row, 0)
            detail_grid.addWidget(widget, row, 1)
        detail_grid.setColumnStretch(1, 1)
        layout.addLayout(detail_grid)
        return frame

    def _set_resolution_from_button(self, value: str) -> None:
        self.resolution_combo.setCurrentText(value)

    def _sync_resolution_buttons(self, value: str) -> None:
        for button in self.resolution_buttons:
            if button.property("resolutionValue") == value:
                button.setChecked(True)
                return

    def set_mode_index(self, index: int) -> None:
        if 0 <= index < len(self.mode_buttons):
            self.mode_buttons[index].setChecked(True)

    def set_available_resolutions(self, resolution_values: set[int], selected_value: str = "自动") -> None:
        available = {f"{value}p" for value in resolution_values}
        fallback = _highest_resolution_text(resolution_values)
        if selected_value != "自动" and selected_value not in available:
            selected_value = fallback
        for button in self.resolution_buttons:
            value = str(button.property("resolutionValue") or "")
            enabled = value == "自动" or value in available
            button.setEnabled(enabled)
            button.setToolTip("" if enabled else "该视频在当前下载类型下没有此分辨率")
            button.setProperty("availableResolution", enabled)
            button.setStyleSheet("" if enabled else "color: #98a2b3;")
            label = self.resolution_size_labels.get(value)
            if label is not None:
                label.setEnabled(enabled)
                label.setProperty("availableResolution", enabled)
                label.setStyleSheet("" if enabled else "color: #98a2b3;")
        self.resolution_combo.setCurrentText(selected_value)

    def load_available_formats(self, formats: list[dict], selected_format_id: str, actual_summary: str = "") -> None:
        self.format_id_combo.clear()
        self.format_id_combo.addItem("自动")
        for item in formats:
            format_id = str(item.get("format_id") or "")
            if format_id:
                self.format_id_combo.addItem(format_id)
        index = self.format_id_combo.findText(selected_format_id)
        if index < 0 and selected_format_id and selected_format_id != "自动":
            self.format_id_combo.addItem(selected_format_id)
            index = self.format_id_combo.findText(selected_format_id)
        if index >= 0:
            self.format_id_combo.setCurrentIndex(index)
        self.set_actual_format_summary(actual_summary or selected_format_id or "自动")

    def set_merge_hint_visible(self, visible: bool) -> None:
        self.merge_hint_label.setVisible(visible)

    def set_actual_format_summary(self, text: str) -> None:
        self.actual_format_label.setText(text or "自动")


def _section(title: str, rows: list[tuple[str, QWidget]]) -> QFrame:
    frame = QFrame()
    frame.setObjectName("formatSection")
    layout = QVBoxLayout(frame)
    layout.setContentsMargins(10, 8, 10, 8)
    layout.setSpacing(4)
    heading = QLabel(title)
    heading.setObjectName("sectionTitle")
    layout.addWidget(heading)
    grid = QGridLayout()
    grid.setHorizontalSpacing(12)
    grid.setVerticalSpacing(4)
    visible_rows = [
        (label_text, widget)
        for label_text, widget in rows
        if widget.objectName() not in {"hiddenResolutionCombo", "hiddenFormatIdCombo"}
    ]
    for row, (label_text, widget) in enumerate(visible_rows):
        label = QLabel(label_text)
        label.setObjectName("optionLabel")
        grid.addWidget(label, row, 0)
        grid.addWidget(widget, row, 1)
    grid.setColumnStretch(1, 1)
    layout.addLayout(grid)
    return frame


def _highest_resolution_text(values: set[int]) -> str:
    return f"{max(values)}p" if values else "自动"
