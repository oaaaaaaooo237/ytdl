from PySide6.QtCore import QPoint, QRect, QSignalBlocker, Qt
from PySide6.QtGui import QColor, QPainter, QPixmap, QPolygon
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
        self.url_input.setObjectName("urlInput")
        self.url_input.setPlaceholderText("粘贴一个或多个视频播放地址")
        self.url_input.setFixedHeight(38)
        self.url_input.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.url_input.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
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
        self._duration_badge_text = ""
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
        self.thumbnail_label.setFixedSize(198, 170)
        self.mode_combo = QComboBox()
        self.mode_combo.setObjectName("hiddenModeCombo")
        self.mode_combo.hide()
        self.audio_checkbox = QCheckBox("下载音频")
        self.audio_checkbox.setObjectName("optionSwitch")
        self.audio_checkbox.setChecked(True)
        self.video_checkbox = QCheckBox("下载视频")
        self.video_checkbox.setObjectName("optionSwitch")
        self.video_checkbox.setChecked(True)
        self.audio_quality_button = QPushButton("最佳质量")
        self.video_quality_button = QPushButton("最佳质量")
        self.audio_quality_button.setObjectName("qualityPill")
        self.video_quality_button.setObjectName("qualityPill")
        self.mode_combo.addItems(["音频+视频", "仅音频", "仅视频"])
        self.preview_checkbox = QCheckBox("下载时同步预览播放")
        self.preview_checkbox.setObjectName("optionSwitch")
        self.preview_player = PreviewPlayer()
        self.preview_player.setObjectName("compactPreview")
        self.preview_player.setFixedHeight(96)
        self.preview_player.hide()
        self.audio_checkbox.toggled.connect(self._sync_mode_from_toggles)
        self.video_checkbox.toggled.connect(self._sync_mode_from_toggles)
        self.preview_checkbox.toggled.connect(self.preview_player.setVisible)
        self.mode_combo.currentIndexChanged.connect(self._sync_toggles_from_mode)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 48, 24, 18)
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
        video_card.setFixedHeight(185)
        video_layout = QHBoxLayout(video_card)
        video_layout.setContentsMargins(14, 14, 14, 0)
        video_layout.setSpacing(16)
        video_layout.addWidget(self.thumbnail_label, 0, Qt.AlignmentFlag.AlignTop)

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
        self.options_card = QFrame()
        self.options_card.setObjectName("optionsCard")
        options_layout = QVBoxLayout(self.options_card)
        options_layout.setContentsMargins(14, 10, 14, 10)
        options_layout.setSpacing(0)
        options_layout.addLayout(_quality_toggle_row(self.audio_checkbox, self.audio_quality_button))
        options_layout.addLayout(_quality_toggle_row(self.video_checkbox, self.video_quality_button))
        options_layout.addLayout(_option_row("下载类型", self.mode_combo))
        options_layout.addLayout(_option_row("预览播放", self.preview_checkbox))
        options_layout.addWidget(self.preview_player)
        layout.addWidget(self.options_card)

        layout.addSpacing(8)
        layout.addWidget(self.start_button)

    def choose_folder(self) -> str:
        return QFileDialog.getExistingDirectory(self, "选择保存位置")

    def _sync_mode_from_toggles(self) -> None:
        audio = self.audio_checkbox.isChecked()
        video = self.video_checkbox.isChecked()
        if not audio and not video:
            sender = self.sender()
            restore = self.video_checkbox if sender is self.audio_checkbox else self.audio_checkbox
            with QSignalBlocker(restore):
                restore.setChecked(True)
            audio = self.audio_checkbox.isChecked()
            video = self.video_checkbox.isChecked()
        index = 0 if audio and video else 1 if audio else 2
        with QSignalBlocker(self.mode_combo):
            self.mode_combo.setCurrentIndex(index)

    def _sync_toggles_from_mode(self, index: int) -> None:
        with QSignalBlocker(self.audio_checkbox), QSignalBlocker(self.video_checkbox):
            self.audio_checkbox.setChecked(index in {0, 1})
            self.video_checkbox.setChecked(index in {0, 2})

    def paste_from_clipboard(self) -> None:
        text = QApplication.clipboard().text().strip()
        if text:
            self.url_input.setPlainText(text)

    def set_status(self, message: str) -> None:
        self.status_label.setText(message)

    def set_analysis_retry_available(self) -> None:
        self.analyze_button.setText("重试分析")

    def reset_analysis_action(self) -> None:
        self.analyze_button.setText("分析")

    def set_save_folder(self, path: str) -> None:
        self.save_folder_label.setText(path)

    def show_analysis_result(self, title: str, duration_text: str, format_summary: str) -> None:
        self._duration_badge_text = duration_text
        self.title_label.setText(f"标题：{title}")
        self.duration_label.setText(f"时长：{duration_text}")
        self.format_summary_label.setText(f"格式：{format_summary}")

    def set_thumbnail(self, pixmap: QPixmap) -> None:
        if pixmap.isNull():
            return
        composed = pixmap.scaled(
            self.thumbnail_label.size(),
            Qt.AspectRatioMode.KeepAspectRatioByExpanding,
            Qt.TransformationMode.SmoothTransformation,
        )
        composed = self._thumbnail_with_overlay(composed)
        self.thumbnail_label.setPixmap(composed)
        self.thumbnail_label.setProperty("hasPreviewOverlay", True)
        self.thumbnail_label.setProperty("durationBadge", self._duration_badge_text)

    def _thumbnail_with_overlay(self, pixmap: QPixmap) -> QPixmap:
        composed = QPixmap(pixmap)
        painter = QPainter(composed)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)

        center = composed.rect().center()
        radius = 28
        painter.setBrush(QColor(0, 0, 0, 130))
        painter.setPen(Qt.PenStyle.NoPen)
        painter.drawEllipse(center, radius, radius)

        painter.setBrush(QColor("#ffffff"))
        painter.drawPolygon(
            QPolygon(
                [
                    QPoint(center.x() - 8, center.y() - 13),
                    QPoint(center.x() - 8, center.y() + 13),
                    QPoint(center.x() + 14, center.y()),
                ]
            )
        )

        if self._duration_badge_text:
            badge_width = max(42, painter.fontMetrics().horizontalAdvance(self._duration_badge_text) + 12)
            badge = QRect(
                composed.width() - badge_width - 7,
                composed.height() - 27,
                badge_width,
                20,
            )
            painter.setBrush(QColor(0, 0, 0, 185))
            painter.drawRoundedRect(badge, 4, 4)
            painter.setPen(QColor("#ffffff"))
            painter.drawText(badge, Qt.AlignmentFlag.AlignCenter, self._duration_badge_text)

        painter.end()
        return composed


class _SectionTitle(QLabel):
    def __init__(self, text: str):
        super().__init__(text)
        self.setObjectName("sectionTitle")


def _option_row(label_text: str, widget: QWidget) -> QHBoxLayout:
    row = QHBoxLayout()
    if widget.objectName() == "hiddenModeCombo":
        row.setContentsMargins(0, 0, 0, 0)
        row.setSpacing(0)
        return row
    row.setContentsMargins(0, 8, 0, 8)
    row.setSpacing(12)
    label = QLabel(label_text)
    label.setObjectName("optionLabel")
    label.setFixedWidth(88)
    row.addWidget(label)
    row.addWidget(widget, 1)
    return row


def _quality_toggle_row(toggle: QCheckBox, quality_button: QPushButton) -> QHBoxLayout:
    row = QHBoxLayout()
    row.setContentsMargins(0, 8, 0, 8)
    row.setSpacing(12)
    row.addWidget(toggle, 1)
    row.addWidget(quality_button)
    return row
