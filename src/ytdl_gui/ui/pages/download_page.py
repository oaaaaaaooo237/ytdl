from PySide6.QtCore import Qt
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QApplication,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.ui.player import PreviewPlayer


class DownloadPage(QWidget):
    def __init__(self):
        super().__init__()
        self.url_input = QTextEdit()
        self.url_input.setPlaceholderText("粘贴一个或多个视频播放地址")
        self.url_input.setFixedHeight(38)
        self.analyze_button = QPushButton("分析")
        self.analyze_button.setObjectName("primaryButton")
        self.analyze_button.setFixedWidth(110)
        self.analyze_button.setMinimumHeight(42)
        self.paste_button = QPushButton("粘贴")
        self.paste_button.setFixedWidth(72)
        self.paste_button.clicked.connect(self.paste_from_clipboard)
        self.save_folder_button = QPushButton("浏览")
        self.start_button = QPushButton("开始下载")
        self.start_button.setObjectName("primaryButton")
        self.start_button.setMinimumHeight(46)
        self.status_label = QLabel("等待输入视频地址。")
        self.status_label.setObjectName("mutedLabel")
        self.title_label = QLabel("标题：未分析")
        self.duration_label = QLabel("时长：未分析")
        self.format_summary_label = QLabel("格式：未选择")
        self.save_folder_label = QLabel("保存位置：未选择")
        for dynamic_label in (
            self.status_label,
            self.title_label,
            self.duration_label,
            self.format_summary_label,
            self.save_folder_label,
        ):
            dynamic_label.setWordWrap(True)
        self.thumbnail_label = QLabel("预览图")
        self.thumbnail_label.setObjectName("thumbnailPlaceholder")
        self.thumbnail_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.thumbnail_label.setFixedSize(188, 112)
        self.mode_combo = QComboBox()
        self.mode_combo.addItems(["音频+视频", "仅音频", "仅视频"])
        self.preview_checkbox = QCheckBox("下载时同步预览播放")
        self.preview_player = PreviewPlayer()
        self.preview_player.setObjectName("compactPreview")
        self.preview_player.setFixedHeight(96)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 18)
        layout.setSpacing(14)

        layout.addWidget(_SectionTitle("1. 输入视频地址"))
        url_row = QHBoxLayout()
        url_row.setSpacing(10)
        url_row.addWidget(self.url_input, 1)
        url_row.addWidget(self.paste_button)
        layout.addLayout(url_row)
        analyze_row = QHBoxLayout()
        analyze_row.addStretch()
        analyze_row.addWidget(self.analyze_button)
        layout.addLayout(analyze_row)

        video_card = QFrame()
        video_card.setObjectName("videoCard")
        video_layout = QHBoxLayout(video_card)
        video_layout.setContentsMargins(14, 14, 14, 14)
        video_layout.setSpacing(16)
        video_layout.addWidget(self.thumbnail_label)

        detail_layout = QVBoxLayout()
        detail_layout.setSpacing(7)
        self.title_label.setObjectName("videoTitle")
        self.title_label.setWordWrap(True)
        detail_layout.addWidget(self.title_label)
        detail_layout.addWidget(self.duration_label)
        detail_layout.addWidget(self.format_summary_label)
        detail_layout.addWidget(self.status_label)
        detail_layout.addStretch()
        video_layout.addLayout(detail_layout, 1)
        layout.addWidget(video_card)

        layout.addWidget(_SectionTitle("2. 保存位置"))
        save_row = QHBoxLayout()
        save_row.setSpacing(10)
        self.save_folder_label.setObjectName("pathDisplay")
        save_row.addWidget(self.save_folder_label, 1)
        save_row.addWidget(self.save_folder_button)
        layout.addLayout(save_row)

        layout.addWidget(_SectionTitle("3. 选项"))
        options_card = QFrame()
        options_card.setObjectName("optionsCard")
        options_layout = QVBoxLayout(options_card)
        options_layout.setContentsMargins(14, 10, 14, 10)
        options_layout.setSpacing(0)
        options_layout.addLayout(_option_row("下载类型", self.mode_combo))
        options_layout.addLayout(_option_row("预览播放", self.preview_checkbox))
        options_layout.addWidget(self.preview_player)
        layout.addWidget(options_card)

        layout.addStretch()
        layout.addWidget(self.start_button)

    def choose_folder(self) -> str:
        return QFileDialog.getExistingDirectory(self, "选择保存位置")

    def paste_from_clipboard(self) -> None:
        text = QApplication.clipboard().text().strip()
        if text:
            self.url_input.setPlainText(text)

    def set_status(self, message: str) -> None:
        self.status_label.setText(message)

    def set_save_folder(self, path: str) -> None:
        self.save_folder_label.setText(path)

    def show_analysis_result(self, title: str, duration_text: str, format_summary: str) -> None:
        self.title_label.setText(f"标题：{title}")
        self.duration_label.setText(f"时长：{duration_text}")
        self.format_summary_label.setText(f"格式：{format_summary}")

    def set_thumbnail(self, pixmap: QPixmap) -> None:
        if pixmap.isNull():
            return
        self.thumbnail_label.setPixmap(
            pixmap.scaled(
                self.thumbnail_label.size(),
                Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                Qt.TransformationMode.SmoothTransformation,
            )
        )


class _SectionTitle(QLabel):
    def __init__(self, text: str):
        super().__init__(text)
        self.setObjectName("sectionTitle")


def _option_row(label_text: str, widget: QWidget) -> QHBoxLayout:
    row = QHBoxLayout()
    row.setContentsMargins(0, 8, 0, 8)
    row.setSpacing(12)
    label = QLabel(label_text)
    label.setObjectName("optionLabel")
    label.setFixedWidth(88)
    row.addWidget(label)
    row.addWidget(widget, 1)
    return row
