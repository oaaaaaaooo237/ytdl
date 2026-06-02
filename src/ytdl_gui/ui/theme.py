from PySide6.QtGui import QColor, QPalette

TEAL = "#0f8f8c"
TEAL_DARK = "#08716f"
TEAL_SOFT = "#d7f0ee"
SURFACE = "#ffffff"
BACKGROUND = "#f6f9fb"
NAV_BACKGROUND = "#ffffff"
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
        #titleBar, #bottomStatusBar {{
            background: {SURFACE};
            border-bottom: 1px solid #e1e7ec;
        }}
        #bottomStatusBar {{
            border-top: 1px solid #e1e7ec;
            border-bottom: 0;
        }}
        #appIcon {{
            background: {TEAL};
            color: white;
            border-radius: 6px;
            font-weight: 700;
        }}
        #appTitle {{
            background: transparent;
            font-size: 13px;
            font-weight: 700;
        }}
        #windowChromeButton {{
            border: 0;
            background: transparent;
            padding: 0;
            min-height: 0;
        }}
        #windowChromeButton:hover {{
            background: #eef3f6;
        }}
        #footerMuted {{
            color: {MUTED_TEXT};
            background: transparent;
            font-size: 11px;
        }}
        #footerStatusDot {{
            color: #15b34f;
            background: transparent;
            font-size: 12px;
        }}
        #footerButton {{
            font-size: 11px;
            padding: 5px 10px;
            min-height: 18px;
        }}
        #contentSurface {{
            background: {SURFACE};
            border: 1px solid #e3e8ec;
            border-radius: 8px;
        }}
        #pageTitle {{
            font-size: 22px;
            font-weight: 600;
            background: transparent;
        }}
        #pageSubtitle {{
            color: {MUTED_TEXT};
            background: transparent;
        }}
        QListWidget {{
            background: {NAV_BACKGROUND};
            border: 1px solid #e6ecf0;
            border-radius: 8px;
            padding: 12px 8px;
            outline: 0;
        }}
        QListWidget::item {{
            padding: 12px 8px;
            border-radius: 6px;
            margin: 4px 0;
        }}
        QListWidget::item:selected {{
            background: {TEAL_SOFT};
            color: #064e4b;
            font-weight: 600;
            border-left: 3px solid {TEAL};
        }}
        QListWidget#navRail {{
            padding: 8px 6px;
        }}
        QListWidget#navRail::item {{
            padding: 4px 3px;
            margin: 4px 0;
        }}
        #navItem, #navItemIcon, #navItemLabel {{
            background: transparent;
        }}
        #navItemLabel {{
            font-size: 12px;
        }}
        QPushButton {{
            border: 1px solid {BORDER};
            border-radius: 6px;
            padding: 8px 14px;
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
            padding: 8px;
            background: {SURFACE};
            selection-background-color: {TEAL};
        }}
        QTextEdit {{
            min-height: 34px;
        }}
        #sectionTitle {{
            font-size: 13px;
            font-weight: 700;
            background: transparent;
            margin-top: 4px;
        }}
        #videoCard, #optionsCard {{
            background: {SURFACE};
            border: 1px solid #dfe7ec;
            border-radius: 8px;
        }}
        #formatSection {{
            background: {SURFACE};
            border: 1px solid #dfe7ec;
            border-radius: 8px;
        }}
        #segmentButton {{
            min-height: 46px;
            border-radius: 7px;
            font-weight: 600;
        }}
        #segmentButton:checked {{
            background: {TEAL_SOFT};
            border-color: {TEAL};
            color: #064e4b;
        }}
        #thumbnailPlaceholder {{
            background: #e8eff5;
            border-radius: 6px;
            color: {MUTED_TEXT};
            font-weight: 600;
        }}
        #videoTitle {{
            font-size: 15px;
            font-weight: 700;
            background: transparent;
        }}
        #mutedLabel, #pathDisplay {{
            color: {MUTED_TEXT};
            background: transparent;
        }}
        #optionLabel {{
            color: {TEXT};
            background: transparent;
        }}
        #compactPreview {{
            border-top: 1px solid #edf1f3;
            margin-top: 8px;
            padding-top: 8px;
        }}
        #queueScroll {{
            border: 0;
            background: transparent;
        }}
        #queueScroll QWidget {{
            background: transparent;
        }}
        #queueCard {{
            background: {SURFACE};
            border: 1px solid #e2e8ee;
            border-radius: 8px;
        }}
        #queueThumb {{
            background: #dfeaf2;
            border-radius: 6px;
            color: {MUTED_TEXT};
            font-weight: 600;
        }}
        #queueTitle {{
            font-size: 13px;
            font-weight: 700;
            background: transparent;
        }}
        #toolbarButton {{
            min-width: 88px;
        }}
        #iconButton, QPushButton[queueAction="true"] {{
            min-width: 44px;
            max-width: 54px;
            padding: 6px;
        }}
        QProgressBar {{
            border: 0;
            border-radius: 3px;
            background: #ecf0f3;
            height: 7px;
            text-align: center;
        }}
        QProgressBar::chunk {{
            background: {TEAL};
            border-radius: 3px;
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
        #statusBadge {{
            color: #166534;
            background: #dcfce7;
            border-radius: 9px;
            padding: 2px 8px;
            font-size: 11px;
        }}
        QCheckBox {{
            background: transparent;
        }}
        QCheckBox#optionSwitch {{
            spacing: 10px;
        }}
        QCheckBox#optionSwitch::indicator {{
            width: 32px;
            height: 18px;
            border-radius: 9px;
            border: 1px solid #aab5bf;
            background: #aab5bf;
        }}
        QCheckBox#optionSwitch::indicator:checked {{
            border-color: {TEAL};
            background: {TEAL};
        }}
        """
    )
