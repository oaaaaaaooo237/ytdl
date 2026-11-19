from PySide6.QtCore import QPoint, QRect, QSize, QSignalBlocker, Qt
from PySide6.QtGui import QColor, QIcon, QPainter, QPen, QPixmap, QPolygon
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QApplication,
    QStyle,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from ytdl_gui.paths import resource_root
from ytdl_gui.ui.player import PreviewPlayer
from ytdl_gui.ui.widgets import PageHeader


class DownloadPage(QWidget):
    def __init__(self):
        super().__init__()
        field_height = 48
        self.url_input = QTextEdit()
        self.url_input.setObjectName("urlInput")
        self.url_input.setPlaceholderText("输入或粘贴链接")
        self.url_input.setAccessibleName("下载地址或电驴关键词")
        self.url_input.setLineWrapMode(QTextEdit.LineWrapMode.NoWrap)
        self.url_input.setFixedHeight(field_height)
        self.url_input.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.url_input.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.analyze_button = QPushButton("分析")
        self.analyze_button.setObjectName("primaryButton")
        self.analyze_button.setProperty("addressAction", True)
        self.analyze_button.setFixedSize(76, field_height)
        self.paste_button = QPushButton()
        self.paste_button.setIcon(_clipboard_icon(self))
        self.paste_button.setToolTip("粘贴")
        self.paste_button.setAccessibleName("粘贴")
        self.paste_button.clicked.connect(self.paste_from_clipboard)
        self.emule_button = QPushButton()
        self.emule_button.setIcon(QIcon(str(resource_root() / "assets" / "emule.ico")))
        self.emule_button.setToolTip("电驴搜索")
        self.emule_button.setAccessibleName("电驴搜索")
        for button in (self.emule_button, self.paste_button):
            button.setProperty("addressIcon", True)
            button.setIconSize(QSize(32, 32))
            button.setFixedSize(field_height, field_height)
        self.save_folder_button = QPushButton("浏览")
        self.save_folder_button.setFixedHeight(field_height)
        self.start_button = QPushButton("开始下载")
        self.start_button.setObjectName("primaryButton")
        self.start_button.setProperty("largePrimary", True)
        self.start_button.setFixedHeight(46)
        self.status_label = QLabel("等待输入视频地址。")
        self.status_label.setObjectName("mutedLabel")
        self.title_label = QLabel("标题：未分析")
        self.duration_label = QLabel("时长：未分析")
        self.format_summary_label = QLabel("格式：未选择")
        self.save_folder_label = QLineEdit()
        self.save_folder_label.setObjectName("pathDisplay")
        self.save_folder_label.setReadOnly(True)
        self.save_folder_label.setFixedHeight(field_height)
        self.save_folder_label.setText("保存位置：未选择")
        self.free_space_label = QLabel("剩余空间：未检测")
        self.free_space_label.setFixedHeight(20)
        self._duration_badge_text = ""
        for dynamic_label in (
            self.status_label,
            self.title_label,
            self.duration_label,
            self.format_summary_label,
            self.free_space_label,
        ):
            dynamic_label.setWordWrap(True)
        self.thumbnail_label = QLabel("预览图")
        self.thumbnail_label.setObjectName("thumbnailPlaceholder")
        self.thumbnail_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.thumbnail_label.setFixedSize(160, 112)
        self.mode_combo = QComboBox()
        self.mode_combo.setObjectName("hiddenModeCombo")
        self.mode_combo.hide()
        self.audio_checkbox = QCheckBox("")
        self.audio_checkbox.setObjectName("optionSwitch")
        self.audio_checkbox.setChecked(True)
        self.video_checkbox = QCheckBox("")
        self.video_checkbox.setObjectName("optionSwitch")
        self.video_checkbox.setChecked(True)
        self.audio_option_icon = _OptionRowIcon(_standard_icon_pixmap(self, QStyle.StandardPixmap.SP_MediaVolume))
        self.audio_option_label = _OptionRowLabel("下载音频")
        self.video_option_icon = _OptionRowIcon(_standard_icon_pixmap(self, QStyle.StandardPixmap.SP_FileDialogDetailedView))
        self.video_option_label = _OptionRowLabel("下载视频")
        self.audio_quality_button = QPushButton("最佳质量")
        self.video_quality_button = QPushButton("最佳质量")
        self.format_settings_button = QPushButton("格式与字幕")
        self.audio_quality_button.setObjectName("qualityPill")
        self.video_quality_button.setObjectName("qualityPill")
        self.mode_combo.addItems(["音频+视频", "仅音频", "仅视频"])
        self.preview_checkbox = QCheckBox("")
        self.preview_checkbox.setObjectName("optionSwitch")
        self.preview_option_icon = _OptionRowIcon(_standard_icon_pixmap(self, QStyle.StandardPixmap.SP_MediaPlay))
        self.preview_option_label = _OptionRowLabel("下载时同步预览播放")
        self.preview_player = PreviewPlayer()
        self.preview_player.setObjectName("compactPreview")
        self.preview_player.setFixedHeight(96)
        self.preview_player.hide()
        self.audio_checkbox.toggled.connect(self._sync_mode_from_toggles)
        self.video_checkbox.toggled.connect(self._sync_mode_from_toggles)
        self.preview_checkbox.toggled.connect(self.preview_player.setVisible)
        self.mode_combo.currentIndexChanged.connect(self._sync_toggles_from_mode)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("新建下载", "识别链接后，选择下载设置。"))
        url_row = QHBoxLayout()
        url_row.setSpacing(8)
        url_row.addWidget(self.url_input, 1)
        url_row.addWidget(self.emule_button)
        url_row.addWidget(self.paste_button)
        url_row.addWidget(self.analyze_button)
        layout.addLayout(url_row)
        layout.addWidget(self.status_label)

        self.result_panel = QWidget()
        result_layout = QVBoxLayout(self.result_panel)
        result_layout.setContentsMargins(0, 0, 0, 0)
        result_layout.setSpacing(12)

        video_card = QFrame()
        video_card.setObjectName("videoCard")
        video_card.setMinimumHeight(140)
        video_layout = QHBoxLayout(video_card)
        video_layout.setContentsMargins(14, 14, 14, 14)
        video_layout.setSpacing(16)
        video_layout.addWidget(self.thumbnail_label, 0, Qt.AlignmentFlag.AlignTop)

        detail_layout = QVBoxLayout()
        detail_layout.setSpacing(7)
        self.title_label.setObjectName("videoTitle")
        self.title_label.setWordWrap(True)
        detail_layout.addWidget(self.title_label)
        detail_layout.addWidget(self.duration_label)
        detail_layout.addWidget(self.format_summary_label)
        detail_layout.addStretch()
        video_layout.addLayout(detail_layout, 1)
        result_layout.addWidget(video_card)
        result_layout.addWidget(self.preview_player)

        save_row = QHBoxLayout()
        save_row.setSpacing(8)
        self.free_space_label.setObjectName("pathDisplay")
        save_row.addWidget(self.save_folder_label, 1)
        save_row.addWidget(self.save_folder_button)
        self.options_card = QFrame()
        self.options_card.setObjectName("optionsCard")
        self.options_card.setFixedHeight(139)
        options_layout = QVBoxLayout(self.options_card)
        options_layout.setContentsMargins(14, 10, 14, 10)
        options_layout.setSpacing(0)
        options_layout.addLayout(
            _quality_toggle_row(
                self.audio_checkbox,
                self.audio_option_icon,
                self.audio_option_label,
                self.audio_quality_button,
            )
        )
        options_layout.addLayout(
            _quality_toggle_row(
                self.video_checkbox,
                self.video_option_icon,
                self.video_option_label,
                self.video_quality_button,
            )
        )
        options_layout.addLayout(_option_row("下载类型", self.mode_combo))
        options_layout.addLayout(_preview_toggle_row(self.preview_checkbox, self.preview_option_icon, self.preview_option_label))
        options_heading = QHBoxLayout()
        options_heading.addWidget(_SectionTitle("下载选项"), 1)
        options_heading.addWidget(self.format_settings_button)
        result_layout.addLayout(options_heading)
        result_layout.addWidget(self.options_card)
        result_layout.addWidget(_SectionTitle("保存位置"))
        result_layout.addLayout(save_row)
        result_layout.addWidget(self.free_space_label)

        layout.addWidget(self.result_panel)
        layout.addWidget(self.start_button)
        layout.addStretch()
        self.clear_analysis_result()

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

    def set_status(self, message: str, tooltip: str = "") -> None:
        self.status_label.setText(message)
        self.status_label.setToolTip(tooltip)

    def set_analysis_retry_available(self) -> None:
        self.analyze_button.setText("重试分析")

    def reset_analysis_action(self) -> None:
        self.analyze_button.setText("分析")

    def set_save_folder(self, path: str) -> None:
        self.save_folder_label.setText(path)
        self.save_folder_label.setCursorPosition(0)
        self.save_folder_label.setToolTip(path)

    def set_free_space(self, text: str) -> None:
        self.free_space_label.setText(text)

    def show_analysis_result(self, title: str, duration_text: str, format_summary: str) -> None:
        self._duration_badge_text = duration_text
        self.title_label.setText(f"标题：{title}")
        self.duration_label.setText(f"时长：{duration_text}")
        self.format_summary_label.setText(f"格式：{format_summary}")
        self.result_panel.show()
        self.start_button.show()

    def clear_analysis_result(self) -> None:
        self.result_panel.hide()
        self.start_button.setVisible(self.url_input.toPlainText().strip().lower().startswith("ed2k:"))
        self.thumbnail_label.setText("预览图")
        self.thumbnail_label.setProperty("hasPreviewOverlay", False)
        self.thumbnail_label.setProperty("durationBadge", "")

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


def _clipboard_icon(widget: QWidget) -> QIcon:
    image = QPixmap(64, 64)
    image.fill(Qt.GlobalColor.transparent)
    painter = QPainter(image)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    painter.scale(64 / 24, 64 / 24)
    pen = QPen(widget.palette().buttonText().color())
    pen.setWidthF(1.5)
    pen.setCapStyle(Qt.PenCapStyle.RoundCap)
    pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
    painter.setPen(pen)
    painter.drawRoundedRect(5, 4, 14, 17, 2, 2)
    painter.fillRect(8, 2, 8, 5, widget.palette().button())
    painter.drawRoundedRect(8, 2, 8, 5, 1, 1)
    painter.drawLine(8, 11, 16, 11)
    painter.drawLine(8, 15, 16, 15)
    painter.end()
    return QIcon(image)


class _SectionTitle(QLabel):
    def __init__(self, text: str):
        super().__init__(text)
        self.setObjectName("sectionTitle")
        self.setFixedHeight(20)


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


class _OptionRowIcon(QLabel):
    def __init__(self, pixmap: QPixmap):
        super().__init__("")
        self.setObjectName("optionRowIcon")
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setFixedSize(22, 22)
        self.setPixmap(pixmap)


class _OptionRowLabel(QLabel):
    def __init__(self, text: str):
        super().__init__(text)
        self.setObjectName("optionLabel")


def _quality_toggle_row(toggle: QCheckBox, icon: QLabel, label: QLabel, quality_button: QPushButton) -> QHBoxLayout:
    row = QHBoxLayout()
    row.setContentsMargins(0, 8, 0, 8)
    row.setSpacing(12)
    row.addWidget(toggle)
    row.addWidget(icon)
    row.addWidget(label, 1)
    row.addWidget(quality_button)
    return row


def _preview_toggle_row(toggle: QCheckBox, icon: QLabel, label: QLabel) -> QHBoxLayout:
    row = QHBoxLayout()
    row.setContentsMargins(0, 8, 0, 8)
    row.setSpacing(12)
    row.addWidget(toggle)
    row.addWidget(icon)
    row.addWidget(label, 1)
    row.addStretch()
    return row


def _standard_icon_pixmap(widget: QWidget, standard_icon: QStyle.StandardPixmap) -> QPixmap:
    return widget.style().standardIcon(standard_icon).pixmap(QSize(18, 18))
