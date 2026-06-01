from PySide6.QtWidgets import QCheckBox, QFormLayout, QGroupBox, QLineEdit, QPushButton, QSpinBox, QVBoxLayout, QWidget

from ytdl_gui.ui.widgets import PageHeader


class SettingsPage(QWidget):
    def __init__(self):
        super().__init__()
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
        self.concurrency.setValue(2)
        self.update_on_start = QCheckBox("启动后后台检查 yt-dlp 更新")
        self.update_on_start.setChecked(True)
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

        paths_group = QGroupBox("路径")
        paths = QFormLayout(paths_group)
        paths.addRow("默认保存位置", self.default_folder)
        paths.addRow("", self.choose_default_folder_button)
        paths.addRow("cookies.txt", self.cookies_path)
        cookie_actions = QVBoxLayout()
        cookie_actions.addWidget(self.choose_cookies_button)
        cookie_actions.addWidget(self.clear_cookies_button)
        cookie_actions.addWidget(self.cookies_help_button)
        paths.addRow("", cookie_actions)
        paths.addRow("ffmpeg.exe", self.ffmpeg_path)

        ffmpeg_actions = QVBoxLayout()
        ffmpeg_actions.addWidget(self.find_ffmpeg_button)
        ffmpeg_actions.addWidget(self.choose_ffmpeg_button)
        ffmpeg_actions.addWidget(self.ffmpeg_download_button)
        paths.addRow("", ffmpeg_actions)

        behavior_group = QGroupBox("行为")
        behavior = QFormLayout(behavior_group)
        behavior.addRow("并发下载数", self.concurrency)
        behavior.addRow("", self.update_on_start)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 22, 24, 22)
        layout.setSpacing(12)
        layout.addWidget(PageHeader("设置", "配置保存位置、cookies、ffmpeg 与启动检查。"))
        layout.addWidget(paths_group)
        layout.addWidget(behavior_group)
        layout.addStretch()

    def load_config(self, config) -> None:
        self.default_folder.setText(config.default_save_dir)
        self.cookies_path.setText(config.cookies_path)
        self.ffmpeg_path.setText(config.ffmpeg_path)
        self.concurrency.setValue(config.max_concurrency)
        self.update_on_start.setChecked(config.check_ytdlp_updates_on_startup)

    def set_ffmpeg_path(self, path: str) -> None:
        self.ffmpeg_path.setText(path)

    def set_default_folder(self, path: str) -> None:
        self.default_folder.setText(path)

    def set_cookies_path(self, path: str) -> None:
        self.cookies_path.setText(path)
