from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QFileDialog,
    QFormLayout,
    QGroupBox,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.ui.player import PreviewPlayer
from ytdl_gui.ui.widgets import PageHeader


class DownloadPage(QWidget):
    def __init__(self):
        super().__init__()
        self.url_input = QTextEdit()
        self.url_input.setPlaceholderText("粘贴一个或多个视频播放地址")
        self.analyze_button = QPushButton("分析")
        self.save_folder_button = QPushButton("选择保存位置")
        self.start_button = QPushButton("开始下载")
        self.start_button.setObjectName("primaryButton")
        self.status_label = QLabel("等待输入视频地址。")
        self.title_label = QLabel("标题：未分析")
        self.duration_label = QLabel("时长：未分析")
        self.format_summary_label = QLabel("格式：未选择")
        self.save_folder_label = QLabel("保存位置：未选择")
        self.mode_combo = QComboBox()
        self.mode_combo.addItems(["音频+视频", "仅音频", "仅视频"])
        self.preview_checkbox = QCheckBox("下载时同步预览播放")
        self.preview_player = PreviewPlayer()

        url_group = QGroupBox("视频地址")
        url_layout = QVBoxLayout(url_group)
        url_layout.addWidget(self.url_input)

        options_group = QGroupBox("下载选项")
        form = QFormLayout(options_group)
        form.addRow("下载类型", self.mode_combo)
        form.addRow("", self.preview_checkbox)

        preview_group = QGroupBox("预览")
        preview_layout = QVBoxLayout(preview_group)
        preview_layout.addWidget(self.preview_player)

        actions = QHBoxLayout()
        actions.addWidget(self.analyze_button)
        actions.addWidget(self.save_folder_button)
        actions.addStretch()
        actions.addWidget(self.start_button)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("下载", "粘贴播放地址，先分析格式，再加入下载队列。"))
        layout.addWidget(url_group)
        layout.addWidget(options_group)
        layout.addWidget(preview_group)
        layout.addLayout(actions)
        layout.addWidget(self.status_label)
        layout.addWidget(self.title_label)
        layout.addWidget(self.duration_label)
        layout.addWidget(self.format_summary_label)
        layout.addWidget(self.save_folder_label)
        layout.addStretch()

    def choose_folder(self) -> str:
        return QFileDialog.getExistingDirectory(self, "选择保存位置")

    def set_status(self, message: str) -> None:
        self.status_label.setText(message)

    def set_save_folder(self, path: str) -> None:
        self.save_folder_label.setText(f"保存位置：{path}")

    def show_analysis_result(self, title: str, duration_text: str, format_summary: str) -> None:
        self.title_label.setText(f"标题：{title}")
        self.duration_label.setText(f"时长：{duration_text}")
        self.format_summary_label.setText(f"格式：{format_summary}")
