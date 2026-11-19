from PySide6.QtGui import QColor, QFont, QPalette

TEAL = "#536F73"
TEAL_DARK = "#455F62"
TEAL_SOFT = "#E0E7E4"
ROSE = "#8B5860"
ROSE_DARK = "#794B53"
ROSE_SOFT = "#F0E2E2"
SURFACE = "#FCFAF6"
BACKGROUND = "#F3F0EB"
NAV_BACKGROUND = SURFACE
TEXT = "#323F40"
MUTED_TEXT = "#637071"
BORDER = "#D8D9D1"


def apply_light_theme(app) -> None:
    app.setFont(QFont("Segoe UI", 10))
    palette = QPalette()
    palette.setColor(QPalette.Window, QColor(BACKGROUND))
    palette.setColor(QPalette.Base, QColor(SURFACE))
    palette.setColor(QPalette.AlternateBase, QColor(BACKGROUND))
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
            font-family: 'Segoe UI', 'Microsoft YaHei UI', 'Microsoft YaHei';
            font-size: 10pt;
            color: {TEXT};
            background: {BACKGROUND};
        }}
        #titleBar, #bottomStatusBar {{
            background: {SURFACE};
            border-bottom: 1px solid {BORDER};
        }}
        #bottomStatusBar {{
            border-top: 1px solid {BORDER};
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
        #appVersion, #footerMuted {{
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
            border: 1px solid {BORDER};
            border-radius: 8px;
            padding: 12px 8px;
            outline: 0;
        }}
        QListWidget::item {{
            padding: 12px 8px;
            border-radius: 0;
            margin: 4px 0;
        }}
        QListWidget::item:selected {{
            background: {ROSE_SOFT};
            color: {ROSE_DARK};
            font-weight: 600;
            border-left: 2px solid {ROSE};
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
            border-radius: 0;
            padding: 8px 14px;
            background: {SURFACE};
            min-height: 20px;
        }}
        QPushButton:hover {{
            border-color: {TEAL};
            background: {TEAL_SOFT};
        }}
        QPushButton[addressIcon="true"] {{
            padding: 0;
        }}
        QTabBar::tab {{
            border: 1px solid {BORDER};
            border-radius: 0;
            padding: 8px 14px;
            background: {SURFACE};
        }}
        QTabBar::tab:selected {{
            background: {TEAL_SOFT};
            color: {TEAL_DARK};
        }}
        QPushButton#primaryButton {{
            background: {ROSE};
            color: #FFFDF7;
            border-color: {ROSE};
            font-weight: 600;
        }}
        QPushButton#primaryButton:hover {{
            background: {ROSE_DARK};
            border-color: {ROSE_DARK};
        }}
        QPushButton#primaryButton[addressAction="true"] {{
            background: {TEAL};
            border-color: {TEAL};
            padding: 0 4px;
        }}
        QPushButton#primaryButton[addressAction="true"]:hover {{
            background: {TEAL_DARK};
            border-color: {TEAL_DARK};
        }}
        QPushButton[largePrimary="true"] {{
            min-height: 28px;
        }}
        QLineEdit, QTextEdit, QComboBox, QSpinBox {{
            border: 1px solid {BORDER};
            border-radius: 0;
            padding: 8px;
            background: {SURFACE};
            selection-background-color: {TEAL};
        }}
        QTextEdit {{
            min-height: 34px;
        }}
        QTextEdit#urlInput {{
            min-height: 30px;
        }}
        QSpinBox {{
            min-height: 20px;
            padding: 5px 8px;
        }}
        #sectionTitle {{
            font-size: 13px;
            font-weight: 700;
            background: transparent;
            margin-top: 4px;
        }}
        #videoCard, #optionsCard {{
            background: {SURFACE};
            border: 1px solid {BORDER};
            border-radius: 8px;
        }}
        #videoCard QLabel, QGroupBox QLabel {{
            background: transparent;
        }}
        #videoCard #thumbnailPlaceholder {{
            background: {TEAL_SOFT};
        }}
        #formatSection {{
            background: {SURFACE};
            border: 1px solid {BORDER};
            border-radius: 8px;
        }}
        #segmentButton {{
            min-height: 46px;
            border-radius: 0;
            font-weight: 600;
        }}
        #segmentButton:checked {{
            background: {TEAL_SOFT};
            border-color: {TEAL};
            color: {TEAL_DARK};
        }}
        #thumbnailPlaceholder {{
            background: {TEAL_SOFT};
            border-radius: 6px;
            color: {MUTED_TEXT};
            font-weight: 600;
        }}
        #videoTitle {{
            font-size: 15px;
            font-weight: 700;
            background: transparent;
        }}
        #mutedLabel, QLabel#pathDisplay {{
            color: {MUTED_TEXT};
            background: transparent;
        }}
        QLineEdit#pathDisplay {{
            color: {TEXT};
            background: {SURFACE};
            border: 1px solid {BORDER};
            border-radius: 0;
            padding: 8px;
        }}
        #optionLabel {{
            color: {TEXT};
            background: transparent;
        }}
        #actualFormatLabel {{
            color: #31445b;
            background: #f7fafc;
            border: 1px solid #d8e1e8;
            border-radius: 6px;
            padding: 7px 9px;
        }}
        #mergeHintLabel {{
            color: #455a73;
            background: #eef7f6;
            border: 1px solid #c8e7e3;
            border-radius: 6px;
            padding: 7px 9px;
        }}
        QRadioButton:disabled, QLabel#resolutionSizeLabel:disabled {{
            color: #98a2b3;
        }}
        QRadioButton::indicator:disabled {{
            border: 1px solid #cbd5df;
            background: #edf2f6;
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
            background: transparent;
            border: 0;
            border-bottom: 1px solid {BORDER};
        }}
        #queueThumb {{
            background: {TEAL_SOFT};
            border-radius: 6px;
            color: {MUTED_TEXT};
            font-weight: 600;
        }}
        #queueTitle {{
            font-size: 13px;
            font-weight: 700;
            background: transparent;
        }}
        #queueHeaderTitle {{
            font-size: 14px;
            font-weight: 700;
            background: transparent;
        }}
        #queueNotice {{
            color: {TEAL_DARK};
            background: {TEAL_SOFT};
            border: 1px solid {BORDER};
            border-radius: 6px;
            padding: 6px 10px;
            font-size: 12px;
        }}
        #queueHistoryTitleCell, #queueHistoryActionCell {{
            background: transparent;
        }}
        #queueHistoryThumb {{
            background: {TEAL_SOFT};
            border-radius: 4px;
            color: #ffffff;
            font-weight: 700;
        }}
        #queueHistoryTitle {{
            font-size: 11px;
            background: transparent;
        }}
        QPushButton[queueHistoryAction="true"] {{
            min-width: 0;
            padding: 0;
            border-radius: 0;
        }}
        #toolbarButton {{
            min-width: 88px;
        }}
        #iconButton {{
            min-width: 36px;
            max-width: 36px;
            min-height: 36px;
            max-height: 36px;
            padding: 0;
        }}
        QPushButton[queueAction="true"] {{
            min-width: 34px;
            max-width: 34px;
            min-height: 34px;
            max-height: 34px;
            padding: 0;
        }}
        QProgressBar, #queueScroll QProgressBar {{
            border: 0;
            border-radius: 3px;
            background: {TEAL_SOFT};
            height: 7px;
            text-align: center;
        }}
        QProgressBar::chunk {{
            background: {TEAL};
            border-radius: 3px;
        }}
        QGroupBox {{
            border: 1px solid {BORDER};
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
            border: 1px solid {BORDER};
            border-radius: 0;
            gridline-color: {BORDER};
        }}
        QHeaderView::section {{
            background: {TEAL_SOFT};
            border: 0;
            border-bottom: 1px solid {BORDER};
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
            border-radius: 0;
            border: 1px solid {BORDER};
            background: {TEAL_SOFT};
        }}
        QCheckBox#optionSwitch::indicator:checked {{
            border-color: {TEAL};
            background: {TEAL};
        }}
        """
    )
