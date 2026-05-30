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
        self.mode_combo = QComboBox()
        self.mode_combo.addItems(["音频+视频", "仅音频", "仅视频"])
        self.preview_checkbox = QCheckBox("下载时同步预览播放")

        url_group = QGroupBox("视频地址")
        url_layout = QVBoxLayout(url_group)
        url_layout.addWidget(self.url_input)

        options_group = QGroupBox("下载选项")
        form = QFormLayout(options_group)
        form.addRow("下载类型", self.mode_combo)
        form.addRow("", self.preview_checkbox)

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
        layout.addLayout(actions)
        layout.addWidget(QLabel("当前版本仅呈现界面壳；分析和下载将在后续任务接入。"))
        layout.addStretch()

    def choose_folder(self) -> str:
        return QFileDialog.getExistingDirectory(self, "选择保存位置")
