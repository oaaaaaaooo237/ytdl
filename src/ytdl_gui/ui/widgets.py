from PySide6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
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
