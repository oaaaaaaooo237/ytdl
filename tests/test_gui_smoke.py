import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ytdl_gui.ui.main_window import MainWindow
from PySide6.QtWidgets import QLabel
from PySide6.QtCore import QPoint


def combo_items(combo):
    return [combo.itemText(index) for index in range(combo.count())]


def table_headers(table):
    return [
        table.horizontalHeaderItem(index).text()
        for index in range(table.columnCount())
    ]


def label_texts(widget):
    return [label.text() for label in widget.findChildren(QLabel)]


def test_main_window_has_chinese_navigation(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    nav_text = [window.nav.item(index).text() for index in range(window.nav.count())]

    assert nav_text == ["下载", "格式", "队列", "历史", "设置", "关于"]
    assert window.windowTitle() == "视频地址提取器"
    assert window.app_title_label.text() == "视频地址提取器"
    assert window.title_bar.objectName() == "titleBar"
    assert window.bottom_status_bar.objectName() == "bottomStatusBar"
    assert window.ytdlp_footer_label.text().startswith("yt-dlp")
    assert window.footer_update_button.text() == "检查更新"
    assert window.stack.count() == 6


def test_download_page_has_primary_controls(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.download_page.url_input.placeholderText() == "粘贴一个或多个视频播放地址"
    assert window.download_page.analyze_button.text() == "分析"
    assert window.download_page.paste_button.text() == "粘贴"
    assert window.download_page.save_folder_button.text() == "浏览"
    assert window.download_page.start_button.text() == "开始下载"
    assert window.download_page.start_button.objectName() == "primaryButton"
    assert window.download_page.title_label.wordWrap() is True
    assert window.download_page.format_summary_label.wordWrap() is True
    assert window.download_page.status_label.wordWrap() is True
    assert combo_items(window.download_page.mode_combo) == ["音频+视频", "仅音频", "仅视频"]
    assert window.download_page.preview_checkbox.text() == "下载时同步预览播放"


def test_formats_page_exposes_format_preferences(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert combo_items(window.formats_page.resolution_combo) == [
        "自动",
        "2160p",
        "1440p",
        "1080p",
        "720p",
        "480p",
        "360p",
        "240p",
        "144p",
    ]
    assert "Auto" not in combo_items(window.formats_page.resolution_combo)
    assert "Auto" not in combo_items(window.formats_page.fps_combo)
    assert "Auto" not in combo_items(window.formats_page.codec_combo)
    assert "Auto" not in combo_items(window.formats_page.video_bitrate_combo)
    assert "Auto" not in combo_items(window.formats_page.audio_bitrate_combo)
    assert "Auto" not in combo_items(window.formats_page.container_combo)
    assert "m4a" in combo_items(window.formats_page.container_combo)
    labels = label_texts(window.formats_page)
    assert "视频编码" in labels
    assert "codec" not in labels
    assert window.formats_page.fps_combo.count() > 1
    assert window.formats_page.codec_combo.count() > 1
    assert window.formats_page.video_bitrate_combo.count() > 1
    assert window.formats_page.audio_bitrate_combo.count() > 1
    assert combo_items(window.formats_page.subtitle_combo) == [
        "不下载",
        "下载字幕文件",
        "嵌入",
        "烧录",
    ]


def test_queue_page_headers_and_actions(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert table_headers(window.queue_page.table) == ["标题", "状态", "进度", "速度", "剩余时间", "操作"]
    assert window.queue_page.pause_all_button.text() == "全部暂停"
    assert window.queue_page.clear_completed_button.text() == "清除已完成"
    assert window.queue_page.history_heading.text() == "历史"
    assert table_headers(window.queue_page.recent_history_table) == ["标题", "格式", "状态", "时间"]


def test_queue_card_actions_fit_compact_width(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(535, 883)
    window.nav.setCurrentRow(2)
    window.queue_page.add_task("task-1", "The Harsh Truth As A Subaru Owner / 2026 BRZ tS Review")
    window.show()
    qtbot.wait(50)

    card = window.queue_page._task_cards["task-1"]["card"]
    cancel_button = window.queue_page._task_cards["task-1"]["cancel"]
    retry_button = window.queue_page._task_cards["task-1"]["retry"]
    viewport = window.queue_page.scroll_area.viewport()
    button_right = cancel_button.mapTo(viewport, QPoint(cancel_button.width(), 0)).x()

    assert button_right <= viewport.width()
    assert retry_button.isHidden()


def test_settings_page_has_ffmpeg_and_cookies_controls(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.settings_page.default_folder.placeholderText() == "选择默认保存位置"
    assert window.settings_page.cookies_path.placeholderText() == "选择 cookies.txt 文件路径"
    assert window.settings_page.ffmpeg_path.placeholderText() == "选择 ffmpeg.exe 文件路径"
    assert window.settings_page.concurrency.minimum() == 1
    assert window.settings_page.concurrency.maximum() == 5
    assert window.settings_page.update_on_start.text() == "启动后后台检查 yt-dlp 更新"
    assert window.settings_page.find_ffmpeg_button.text() == "搜索 ffmpeg"
    assert window.settings_page.choose_ffmpeg_button.text() == "选择 ffmpeg.exe"
    assert window.settings_page.ffmpeg_download_button.text() == "打开 ffmpeg 官网下载页"
    assert window.settings_page.cookies_help_button.text() == "如何获取 cookies.txt"


def test_about_page_status_controls(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert "YTDL GUI" in window.about_page.version_label.text()
    assert window.about_page.ytdlp_label.text() == "yt-dlp 状态：待检测"
    assert window.about_page.ffmpeg_label.text() == "ffmpeg 状态：待检测"
    assert window.about_page.legal_button.text() == "第三方许可"
