from PySide6.QtGui import QColor, QPalette

TEAL = "#0f8f8c"
TEAL_DARK = "#08716f"
TEAL_SOFT = "#d7f0ee"
SURFACE = "#ffffff"
BACKGROUND = "#f5f7f8"
NAV_BACKGROUND = "#eef2f3"
TEXT = "#1f2933"
MUTED_TEXT = "#667085"
BORDER = "#d8dee4"


def apply_light_theme(app) -> None:
    palette = QPalette()
    palette.setColor(QPalette.Window, QColor(BACKGROUND))
    palette.setColor(QPalette.Base, QColor(SURFACE))
    palette.setColor(QPalette.AlternateBase, QColor("#f8fafb"))
    palette.setColor(QPalette.Button, QColor(SURFACE))
    palette.setColor(QPalette.Text, QColor(TEXT))
    palette.setColor(QPalette.WindowText, QColor(TEXT))
    palette.setColor(QPalette.ButtonText, QColor(TEXT))
    palette.setColor(QPalette.Highlight, QColor(TEAL))
    palette.setColor(QPalette.HighlightedText, QColor("#ffffff"))
    app.setPalette(palette)
    app.setStyleSheet(
        f"""
        QWidget {{
            font-family: 'Microsoft YaHei UI', 'Segoe UI';
            font-size: 13px;
            color: {TEXT};
            background: {BACKGROUND};
        }}
        #contentSurface {{
            background: {SURFACE};
            border: 1px solid #e3e8ec;
            border-radius: 8px;
        }}
        #pageTitle {{
            font-size: 20px;
            font-weight: 600;
            background: transparent;
        }}
        #pageSubtitle {{
            color: {MUTED_TEXT};
            background: transparent;
        }}
        QListWidget {{
            background: {NAV_BACKGROUND};
            border: 0;
            padding: 10px;
            outline: 0;
        }}
        QListWidget::item {{
            padding: 11px 12px;
            border-radius: 6px;
            margin: 2px 0;
        }}
        QListWidget::item:selected {{
            background: {TEAL_SOFT};
            color: #064e4b;
            font-weight: 600;
        }}
        QPushButton {{
            border: 1px solid {BORDER};
            border-radius: 6px;
            padding: 7px 12px;
            background: {SURFACE};
            min-height: 20px;
        }}
        QPushButton:hover {{
            border-color: #b8c1c8;
            background: #f9fbfb;
        }}
        QPushButton#primaryButton {{
            background: {TEAL};
            color: white;
            border-color: {TEAL};
            font-weight: 600;
        }}
        QPushButton#primaryButton:hover {{
            background: {TEAL_DARK};
            border-color: {TEAL_DARK};
        }}
        QLineEdit, QTextEdit, QComboBox, QSpinBox {{
            border: 1px solid {BORDER};
            border-radius: 6px;
            padding: 7px;
            background: {SURFACE};
            selection-background-color: {TEAL};
        }}
        QTextEdit {{
            min-height: 118px;
        }}
        QGroupBox {{
            border: 1px solid #dde3e7;
            border-radius: 8px;
            margin-top: 12px;
            padding: 12px;
            background: {SURFACE};
            font-weight: 600;
        }}
        QGroupBox::title {{
            subcontrol-origin: margin;
            left: 12px;
            padding: 0 4px;
        }}
        QTableWidget {{
            background: {SURFACE};
            border: 1px solid #dde3e7;
            border-radius: 6px;
            gridline-color: #edf1f3;
        }}
        QHeaderView::section {{
            background: #f3f6f7;
            border: 0;
            border-bottom: 1px solid #dde3e7;
            padding: 7px;
            font-weight: 600;
        }}
        QCheckBox {{
            background: transparent;
        }}
        """
    )
