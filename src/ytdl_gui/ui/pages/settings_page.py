from PySide6.QtCore import QEvent, Qt
from PySide6.QtWidgets import (
    QAbstractSpinBox, QCheckBox, QComboBox, QFormLayout, QGroupBox, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QScrollArea, QSpinBox, QStackedWidget, QVBoxLayout, QWidget,
)

from ytdl_gui import __version__
from ytdl_gui.ui.icons import line_icon
from ytdl_gui.ui.pages.emule_search_page import NETWORKS
from ytdl_gui.ui.widgets import ElidedLabel, PageHeader, Surface


class SettingsPage(QWidget):
    def __init__(self):
        super().__init__()
        self._saved_site_count = 0
        self.default_folder = QLineEdit()
        self.default_folder.setPlaceholderText("选择默认保存位置")
        self.choose_default_folder_button = QPushButton("选择默认保存位置")
        self.cookies_path = QLineEdit()
        self.cookies_path.setPlaceholderText("选择 cookies.txt 文件路径")
        self.choose_cookies_button = QPushButton("选择 cookies.txt")
        self.clear_cookies_button = QPushButton("清除 cookies")
        self.ffmpeg_path = QLineEdit()
        self.ffmpeg_path.setPlaceholderText("选择 ffmpeg.exe 文件路径")
        self.concurrency = QSpinBox()
        self.concurrency.setRange(1, 5)
        self.concurrency.setButtonSymbols(QAbstractSpinBox.ButtonSymbols.PlusMinus)
        self.concurrency.setMaximumWidth(140)
        self.concurrency.setValue(2)
        self.http_connections = QSpinBox()
        self.http_connections.setRange(1, 16)
        self.http_connections.setValue(4)
        self.http_limit = QSpinBox()
        self.http_limit.setRange(0, 50_000)
        self.http_limit.setSpecialValueText("不限速")
        self.http_limit.setSuffix(" KiB/s")
        self.http_limit.setSingleStep(128)
        self.waiting_limit = QSpinBox()
        self.waiting_limit.setRange(0, 50)
        self.waiting_limit.setValue(5)
        for spin in (self.http_connections, self.http_limit, self.waiting_limit):
            spin.setButtonSymbols(QAbstractSpinBox.ButtonSymbols.PlusMinus)
            spin.setMaximumWidth(140)
        self.resume_downloads = QCheckBox("断点续传")
        self.resume_downloads.setChecked(True)
        self.detected_subtitles = QCheckBox("默认下载识别到的字幕")
        self.update_on_start = QCheckBox("启动后后台检查 yt-dlp 更新")
        self.update_on_start.setChecked(True)
        self.appearance = QComboBox()
        for label, mode in (("浅色", "light"), ("深色", "dark"), ("跟随系统", "system")):
            self.appearance.addItem(label, mode)
        self.color_preset = QComboBox()
        self.color_preset.addItem("莫兰迪", "morandi")
        self.color_preset.addItem("Codex 风格", "codex")
        self.jump_to_tasks = QCheckBox("添加下载后显示任务页")
        self.notify_completed = QCheckBox("下载完成提醒")
        self.notify_failed = QCheckBox("下载失败提醒")
        for option in (self.jump_to_tasks, self.notify_completed, self.notify_failed):
            option.setChecked(True)
        self.file_conflict = QComboBox()
        for label, value in (("自动改名", "rename"), ("覆盖", "overwrite"), ("跳过", "skip")):
            self.file_conflict.addItem(label, value)
        self.manage_site_login = QPushButton("管理站点登录")
        self.manage_parsers = QPushButton("管理解析器版本")
        self.reset_save_folder = QPushButton("恢复默认目录")
        self.clean_task_cache = QPushButton("清理闲置缓存")
        self.clear_records = QPushButton("清空历史记录")
        self.find_ffmpeg_button = QPushButton("搜索 ffmpeg")
        self.choose_ffmpeg_button = QPushButton("选择 ffmpeg.exe")
        self.ffmpeg_download_button = QPushButton("打开 ffmpeg 官网下载页")
        self.cookies_help_button = QPushButton("如何获取 cookies.txt")
        self.find_ffmpeg_button.setToolTip("手动搜索本机 PATH 和常见目录中的 ffmpeg.exe")
        self.choose_ffmpeg_button.setToolTip("选择已经下载到本机的 ffmpeg.exe")
        self.ffmpeg_download_button.setToolTip("打开 ffmpeg 官方下载页面")
        self.cookies_help_button.setToolTip("查看导出 Netscape 格式 cookies.txt 的说明")
        self.choose_default_folder_button.setToolTip("选择默认下载保存目录")
        self.choose_cookies_button.setToolTip("选择 Netscape 格式 cookies.txt 文件")
        self.clear_cookies_button.setToolTip("清除 cookies.txt 路径设置")

        storage_group = QGroupBox("存储与记录")
        storage = QFormLayout(storage_group)
        storage.addRow("默认保存位置", self.default_folder)
        storage.addRow("", self.choose_default_folder_button)
        storage.addRow(self.reset_save_folder)
        storage.addRow(self.clean_task_cache)
        storage.addRow(self.clear_records)

        login_group = QGroupBox("网站与登录")
        login = QFormLayout(login_group)
        login.addRow("cookies.txt", self.cookies_path)
        cookie_actions = QHBoxLayout()
        cookie_actions.addWidget(self.choose_cookies_button)
        cookie_actions.addWidget(self.clear_cookies_button)
        login.addRow("", cookie_actions)
        login.addRow(self.cookies_help_button)
        login.addRow(self.manage_site_login)

        components_group = QGroupBox("组件与更新")
        components = QFormLayout(components_group)
        components.addRow("ffmpeg.exe", self.ffmpeg_path)
        ffmpeg_actions = QHBoxLayout()
        ffmpeg_actions.addWidget(self.find_ffmpeg_button)
        ffmpeg_actions.addWidget(self.choose_ffmpeg_button)
        components.addRow("", ffmpeg_actions)
        components.addRow(self.ffmpeg_download_button)
        components.addRow(self.update_on_start)
        components.addRow(self.manage_parsers)

        behavior_group = QGroupBox("下载行为")
        behavior = QFormLayout(behavior_group)
        behavior.addRow("并发下载数", self.concurrency)
        behavior.addRow("等待任务上限", self.waiting_limit)
        behavior.addRow("单文件最大 HTTP 连接数", self.http_connections)
        behavior.addRow("HTTP 下载总限速", self.http_limit)
        behavior.addRow("文件重名", self.file_conflict)
        behavior.addRow(self.resume_downloads)
        behavior.addRow(self.detected_subtitles)
        behavior.addRow(self.jump_to_tasks)

        appearance_group = QGroupBox("外观")
        appearance = QFormLayout(appearance_group)
        appearance.addRow("显示模式", self.appearance)
        appearance.addRow("配色", self.color_preset)
        notice_group = QGroupBox("通知")
        notices = QFormLayout(notice_group)
        notices.addRow(self.notify_completed)
        notices.addRow(self.notify_failed)

        emule_group = QGroupBox("电驴")
        emule_settings = QFormLayout(emule_group)
        self.emule_status = QLabel("尚未连接")
        emule_settings.addRow("连接状态", self.emule_status)
        self.emule_network = QComboBox()
        for label, value in NETWORKS:
            self.emule_network.addItem(label, value)
        self.emule_upload = QSpinBox()
        self.emule_download = QSpinBox()
        for limit in (self.emule_upload, self.emule_download):
            limit.setRange(0, 1000000)
            limit.setSpecialValueText("不限速")
            limit.setSuffix(" KiB/s")
            limit.setButtonSymbols(QAbstractSpinBox.ButtonSymbols.PlusMinus)
            limit.setMaximumWidth(180)
        self.emule_directory = QLineEdit()
        self.emule_directory.setPlaceholderText("应用专用目录")
        self.emule_directory.setReadOnly(True)
        self.choose_emule_directory = QPushButton("选择保存位置")
        emule_settings.addRow("默认搜索网络", self.emule_network)
        emule_settings.addRow("上传限速", self.emule_upload)
        emule_settings.addRow("下载限速", self.emule_download)
        emule_settings.addRow("保存位置", self.emule_directory)
        emule_settings.addRow("", self.choose_emule_directory)
        hint = QLabel("文件保存到所选位置的「电驴下载」子目录。电驴运行时可能共享该目录内的文件。限速和目录在引擎下次启动时生效。")
        hint.setWordWrap(True)
        emule_settings.addRow(hint)

        self._stack = QStackedWidget()
        shell = QVBoxLayout(self)
        shell.setContentsMargins(0, 0, 0, 0)
        shell.addWidget(self._stack)
        self._detail_views = {}
        self._summaries = {}
        self._row_icons = {}
        self.section_buttons = {}

        overview = QWidget()
        layout = QVBoxLayout(overview)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(10)
        layout.addWidget(PageHeader("设置", "管理下载、连接和程序组件。"))
        self._overview = self._scroll(overview)
        self._stack.addWidget(self._overview)
        for heading, options in (
            ("偏好", (("appearance", "外观", appearance_group), ("behavior", "下载行为", behavior_group),
                      ("notice", "通知", notice_group), ("storage", "存储与记录", storage_group))),
            ("连接与登录", (("login", "网站与登录", login_group), ("emule", "电驴", emule_group))),
            ("程序信息", (("components", "组件与更新", components_group), ("about", "关于与许可", None))),
        ):
            title = QLabel(heading)
            title.setObjectName("settingsSectionLabel")
            layout.addWidget(title)
            surface = Surface()
            rows = QVBoxLayout(surface)
            rows.setContentsMargins(12, 3, 12, 3)
            rows.setSpacing(0)
            for index, (key, label, group) in enumerate(options):
                rows.addWidget(self._overview_row(key, label, index == len(options) - 1))
                if group is not None:
                    group.layout().setRowWrapPolicy(QFormLayout.RowWrapPolicy.WrapLongRows)
                    self._add_detail(key, label, group)
            layout.addWidget(surface)
        layout.addStretch()
        self.concurrency.valueChanged.connect(self._refresh_summaries)
        self.default_folder.textChanged.connect(self._refresh_summaries)
        self.cookies_path.textChanged.connect(self._refresh_summaries)
        self.ffmpeg_path.textChanged.connect(self._refresh_summaries)
        self.update_on_start.toggled.connect(self._refresh_summaries)
        self.emule_network.currentIndexChanged.connect(self._refresh_summaries)
        self.appearance.currentIndexChanged.connect(self._refresh_summaries)
        self.color_preset.currentIndexChanged.connect(self._refresh_summaries)
        self.file_conflict.currentIndexChanged.connect(self._refresh_summaries)
        self.notify_completed.toggled.connect(self._refresh_summaries)
        self.notify_failed.toggled.connect(self._refresh_summaries)
        self._refresh_summaries()

    def _overview_row(self, key, title, last):
        button = QPushButton()
        button.setObjectName("settingsRow")
        button.setAccessibleName(title)
        button.setProperty("lastRow", last)
        button.setFixedHeight(60)
        button.setCursor(Qt.CursorShape.PointingHandCursor)
        row = QHBoxLayout(button)
        row.setContentsMargins(0, 8, 3, 8)
        row.setSpacing(12)
        icon = QLabel()
        icon.setObjectName("settingsRowIcon")
        icon.setAlignment(Qt.AlignmentFlag.AlignCenter)
        icon.setFixedSize(36, 36)
        icon.setPixmap(line_icon(key, self.palette().windowText().color()))
        row.addWidget(icon)
        text = QVBoxLayout()
        text.setSpacing(3)
        title_label = QLabel(title)
        title_label.setObjectName("settingsRowTitle")
        summary = ElidedLabel()
        summary.setObjectName("settingsRowSummary")
        text.addWidget(title_label)
        text.addWidget(summary)
        row.addLayout(text, 1)
        chevron = QLabel()
        chevron.setObjectName("settingsRowChevron")
        chevron.setPixmap(line_icon("next", self.palette().windowText().color()))
        row.addWidget(chevron)
        for label in (icon, title_label, summary, chevron):
            label.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        self._summaries[key] = summary
        self._row_icons[key] = icon, chevron
        self.section_buttons[key] = button
        button.clicked.connect(lambda: self._stack.setCurrentWidget(self._detail_views[key]))
        return button

    def _add_detail(self, key, title, content):
        detail = QWidget()
        layout = QVBoxLayout(detail)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        line = QHBoxLayout()
        line.addWidget(PageHeader(title), 1)
        back = QPushButton("返回设置")
        back.clicked.connect(self.show_overview)
        line.addWidget(back)
        layout.addLayout(line)
        if isinstance(content, QGroupBox):
            content.setTitle("")
        layout.addWidget(content)
        layout.addStretch()
        view = self._scroll(detail)
        self._detail_views[key] = view
        self._stack.addWidget(view)

    @staticmethod
    def _scroll(content):
        area = QScrollArea()
        area.setObjectName("settingsScroll")
        area.setWidgetResizable(True)
        area.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        area.setWidget(content)
        return area

    def set_about_page(self, content):
        self._add_detail("about", "关于与许可", content)

    def show_overview(self):
        self._stack.setCurrentWidget(self._overview)

    def set_emule_status(self, text):
        self.emule_status.setText(text)
        self._refresh_summaries()

    def set_saved_sites(self, count):
        self._saved_site_count = count
        self._refresh_summaries()

    def _refresh_summaries(self):
        reminders = int(self.notify_completed.isChecked()) + int(self.notify_failed.isChecked())
        values = {
            "appearance": f"{self.appearance.currentText()} · {self.color_preset.currentText()}",
            "behavior": f"同时下载 {self.concurrency.value()} 项 · 重名{self.file_conflict.currentText()}",
            "notice": ("完成与失败提醒" if reminders == 2 else
                       "下载完成提醒" if self.notify_completed.isChecked() else
                       "下载失败提醒" if reminders else "提醒已关闭"),
            "storage": self.default_folder.text().strip() or "系统下载文件夹",
            "login": (f"已保存 {self._saved_site_count} 个站点会话" if self._saved_site_count else
                      "已选择 cookies.txt" if self.cookies_path.text().strip() else "管理站点会话与 cookies.txt"),
            "emule": f"{self.emule_status.text()} · {self.emule_network.currentText()}",
            "components": ("已配置 ffmpeg" if self.ffmpeg_path.text().strip() else "管理 ffmpeg")
            + (" · 启动时检查更新" if self.update_on_start.isChecked() else " · 手动检查更新"),
            "about": f"v{__version__} · 第三方许可",
        }
        for key, value in values.items():
            self._summaries[key].setText(value)
            self.section_buttons[key].setAccessibleDescription(value)

    def changeEvent(self, event):
        super().changeEvent(event)
        if event.type() == QEvent.Type.PaletteChange and hasattr(self, "_row_icons"):
            color = self.palette().windowText().color()
            for key, (icon, chevron) in self._row_icons.items():
                icon.setPixmap(line_icon(key, color))
                chevron.setPixmap(line_icon("next", color))

    def load_config(self, config) -> None:
        self.default_folder.setText(config.default_save_dir)
        self.cookies_path.setText(config.cookies_path)
        self.ffmpeg_path.setText(config.ffmpeg_path)
        self.concurrency.setValue(config.max_concurrency)
        self.http_connections.setValue(config.http_max_connections)
        self.http_limit.setValue(config.http_limit_kib)
        self.waiting_limit.setValue(config.max_waiting_downloads)
        self.resume_downloads.setChecked(config.resume_downloads)
        self.detected_subtitles.setChecked(config.download_detected_subtitles)
        self.update_on_start.setChecked(config.check_ytdlp_updates_on_startup)
        self.emule_network.setCurrentIndex(max(0, self.emule_network.findData(config.emule_network)))
        self.emule_upload.setValue(config.emule_upload)
        self.emule_download.setValue(config.emule_download)
        self.emule_directory.setText(config.emule_directory)
        self.appearance.setCurrentIndex(max(0, self.appearance.findData(config.theme_mode)))
        self.color_preset.setCurrentIndex(max(0, self.color_preset.findData(config.color_preset)))
        self.file_conflict.setCurrentIndex(max(0, self.file_conflict.findData(config.file_conflict)))
        self.jump_to_tasks.setChecked(config.jump_to_tasks)
        self.notify_completed.setChecked(config.notify_completed)
        self.notify_failed.setChecked(config.notify_failed)

    def set_ffmpeg_path(self, path: str) -> None:
        self.ffmpeg_path.setText(path)

    def set_default_folder(self, path: str) -> None:
        self.default_folder.setText(path)

    def set_cookies_path(self, path: str) -> None:
        self.cookies_path.setText(path)
