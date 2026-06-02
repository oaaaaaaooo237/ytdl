from PySide6.QtCore import QSize, Qt
from PySide6.QtGui import QPainter
from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QSizePolicy,
    QVBoxLayout,
    QWidget,
)


class PageHeader(QWidget):
    def __init__(self, title: str, subtitle: str = ""):
        super().__init__()
        self.title = QLabel(title)
        self.title.setObjectName("pageTitle")
        self.subtitle = QLabel(subtitle)
        self.subtitle.setObjectName("pageSubtitle")
        self.subtitle.setWordWrap(True)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 8)
        layout.setSpacing(4)
        layout.addWidget(self.title)
        if subtitle:
            layout.addWidget(self.subtitle)


class Surface(QFrame):
    def __init__(self):
        super().__init__()
        self.setObjectName("contentSurface")


class PathRow(QWidget):
    def __init__(self, placeholder: str, button_text: str):
        super().__init__()
        self.path_edit = QLineEdit()
        self.path_edit.setPlaceholderText(placeholder)
        self.button = QPushButton(button_text)

        layout = QHBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)
        layout.addWidget(self.path_edit, 1)
        layout.addWidget(self.button)


class ErrorPanel(QWidget):
    def __init__(self):
        super().__init__()
        self.label = QLabel("")
        self.label.setWordWrap(True)
        layout = QVBoxLayout(self)
        layout.addWidget(self.label)
        self.hide()

    def show_message(self, message: str) -> None:
        self.label.setText(message)
        self.show()


class ElidedLabel(QLabel):
    def __init__(self, text: str = ""):
        super().__init__(text)
        self.setWordWrap(False)
        self.setToolTip(text)
        self.setMaximumHeight(self.fontMetrics().height() + 4)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

    def setText(self, text: str) -> None:  # noqa: N802 - Qt API casing
        super().setText(text)
        self.setToolTip(text)

    def paintEvent(self, event) -> None:  # noqa: N802 - Qt API casing
        painter = QPainter(self)
        painter.setPen(self.palette().color(self.foregroundRole()))
        text = self.fontMetrics().elidedText(
            self.text(),
            Qt.TextElideMode.ElideRight,
            self.width(),
        )
        painter.drawText(self.rect(), self.alignment() | Qt.AlignmentFlag.AlignVCenter, text)

    def sizeHint(self) -> QSize:  # noqa: N802 - Qt API casing
        return QSize(80, self.fontMetrics().height() + 4)

    def minimumSizeHint(self) -> QSize:  # noqa: N802 - Qt API casing
        return QSize(0, self.fontMetrics().height() + 4)


def display_status(status: str) -> str:
    labels = {
        "finished": "已完成",
        "completed": "已完成",
        "failed": "失败",
        "canceled": "已取消",
        "cancelled": "已取消",
        "queued": "等待中",
        "pending": "等待中",
        "running": "下载中",
        "downloading": "下载中",
    }
    return labels.get(str(status).casefold(), str(status))
