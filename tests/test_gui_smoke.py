import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.theme import apply_light_theme
from ytdl_gui.history_store import HistoryRecord
from PySide6.QtGui import QColor, QPixmap
from PySide6.QtWidgets import QApplication, QLabel, QListView
from PySide6.QtCore import QPoint, QSize, Qt


def combo_items(combo):
    return [combo.itemText(index) for index in range(combo.count())]


def table_headers(table):
    return [
        table.horizontalHeaderItem(index).text()
        for index in range(table.columnCount())
    ]


def label_texts(widget):
    return [label.text() for label in widget.findChildren(QLabel)]


def nav_labels(window):
    labels = []
    for index in range(window.nav.count()):
        item = window.nav.item(index)
        role_text = item.data(Qt.ItemDataRole.UserRole)
        if role_text:
            labels.append(role_text)
            continue
        widget = window.nav.itemWidget(item)
        label = widget.findChild(QLabel, "navItemLabel") if widget else None
        labels.append(label.text() if label else item.text())
    return labels


def test_main_window_has_chinese_navigation(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert nav_labels(window) == ["下载", "格式", "队列", "历史", "设置", "关于"]
    assert window.windowTitle() == "视频地址提取器"
    assert window.app_title_label.text() == "视频地址提取器"
    assert window.title_bar.objectName() == "titleBar"
    assert window.bottom_status_bar.objectName() == "bottomStatusBar"
    assert window.ytdlp_footer_label.text().startswith("yt-dlp")
    assert window.footer_update_button.text() == "检查更新"
    assert window.stack.count() == 6


def test_navigation_matches_reference_icon_rail(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    for index in range(window.nav.count()):
        item = window.nav.item(index)
        widget = window.nav.itemWidget(item)
        assert widget is not None, item.text()
        icon_label = widget.findChild(QLabel, "navItemIcon")
        assert icon_label is not None, item.text()
        assert icon_label.pixmap() is not None and not icon_label.pixmap().isNull()
    assert window.nav.iconSize().width() >= 18
    assert window.nav.gridSize().height() >= 68


def test_navigation_custom_widgets_avoid_native_icon_duplication(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    for index in range(window.nav.count()):
        assert window.nav.item(index).icon().isNull(), window.nav.item(index).text()


def test_navigation_icon_rail_avoids_scrollbar_artifacts(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    assert window.nav.viewMode() == QListView.ViewMode.ListMode
    assert window.nav.horizontalScrollBarPolicy() == Qt.ScrollBarPolicy.ScrollBarAlwaysOff
    assert not window.nav.horizontalScrollBar().isVisible()


def test_navigation_icon_labels_have_stable_width(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    widest_label = max(
        window.nav.fontMetrics().horizontalAdvance(window.nav.item(index).text())
        for index in range(window.nav.count())
    )
    assert window.nav.viewport().width() >= widest_label + window.nav.iconSize().width() + 24


def test_navigation_width_matches_reference_window(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.nav.width() == 82


def test_navigation_uses_full_chinese_label_widgets(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)

    for index in range(window.nav.count()):
        item = window.nav.item(index)
        widget = window.nav.itemWidget(item)
        assert widget is not None, item.text()
        label = widget.findChild(QLabel, "navItemLabel")
        assert label is not None, item.text()
        assert label.text() == nav_labels(window)[index]
        assert label.minimumWidth() >= window.nav.fontMetrics().horizontalAdvance(label.text())


def test_navigation_custom_widgets_avoid_native_text_duplication(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)

    for expected, index in zip(["下载", "格式", "队列", "历史", "设置", "关于"], range(window.nav.count())):
        item = window.nav.item(index)
        assert item.data(Qt.ItemDataRole.UserRole) == expected
        assert item.text() == ""


def test_navigation_icon_and_label_do_not_overlap(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    for index in range(window.nav.count()):
        widget = window.nav.itemWidget(window.nav.item(index))
        icon_label = widget.findChild(QLabel, "navItemIcon")
        text_label = widget.findChild(QLabel, "navItemLabel")
        assert icon_label.geometry().bottom() + 2 <= text_label.geometry().top(), window.nav.item(index).text()


def test_reference_screenshot_sizes_do_not_expand_to_page_minimums(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://www.youtube.com/watch?v=PqQNXB6hhUs",
        {
            "title": "紐約陣容剋制？馬刺天賦碾壓！",
            "duration": 540,
            "formats": [
                {"format_id": str(index), "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a.40.2", "fps": 30}
                for index in range(60)
            ],
        },
    )
    for index in range(3):
        window.queue_page.add_task(f"qa-task-{index}", "紐約陣容剋制？馬刺天賦碾壓！馬刺VS尼克，冠軍賽超強前瞻！")
    records = [
        HistoryRecord("測試標題", "https://example.com", "D:/x.mp4", "音频+视频", "360p mp4", "不下载", "finished", "2026-06-01T21:44:42")
        for _index in range(4)
    ]
    window.queue_page.load_history_records(records)
    expected_sizes = {
        0: QSize(590, 883),
        1: QSize(488, 883),
        2: QSize(535, 883),
    }

    window.show()
    for index, size in expected_sizes.items():
        window.nav.setCurrentRow(index)
        window.resize(size)
        qtbot.wait(50)
        assert window.size() == size


def test_stack_size_hint_fits_reference_body_height_after_qa_content(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://www.youtube.com/watch?v=PqQNXB6hhUs",
        {
            "title": "紐約陣容剋制？馬刺天賦碾壓！",
            "duration": 540,
            "formats": [
                {"format_id": str(index), "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a.40.2", "fps": 30}
                for index in range(60)
            ],
        },
    )
    for index in range(3):
        window.queue_page.add_task(f"qa-task-{index}", "紐約陣容剋制？馬刺天賦碾壓！馬刺VS尼克，冠軍賽超強前瞻！")

    assert window.stack.sizeHint().height() <= 791


def test_download_page_has_primary_controls(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.download_page.url_input.placeholderText() == "粘贴一个或多个视频播放地址"
    assert window.download_page.analyze_button.text() == "分析"
    assert window.download_page.paste_button.text() == "粘贴"
    assert window.download_page.save_folder_button.text() == "浏览"
    assert window.download_page.start_button.text() == "开始下载"
    assert window.download_page.free_space_label.text().startswith("剩余空间")
    assert window.download_page.start_button.objectName() == "primaryButton"
    assert window.download_page.title_label.wordWrap() is True
    assert window.download_page.format_summary_label.wordWrap() is True
    assert window.download_page.status_label.wordWrap() is True
    assert combo_items(window.download_page.mode_combo) == ["音频+视频", "仅音频", "仅视频"]
    assert window.download_page.preview_checkbox.text() == "下载时同步预览播放"


def test_download_thumbnail_uses_reference_visual_size(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.download_page.thumbnail_label.size() == QSize(198, 170)


def test_download_url_row_starts_near_reference_y(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    url_top = window.download_page.url_input.mapTo(window, QPoint(0, 0)).y()

    assert 106 <= url_top <= 114


def test_download_video_card_height_matches_reference(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    video_card = window.download_page.thumbnail_label.parentWidget()

    assert 190 <= video_card.height() <= 202


def test_download_options_and_start_button_match_reference_rows(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    options_top = window.download_page.options_card.mapTo(window, QPoint(0, 0)).y()
    start_top = window.download_page.start_button.mapTo(window, QPoint(0, 0)).y()

    assert 566 <= options_top <= 576
    assert 134 <= window.download_page.options_card.height() <= 145
    assert 732 <= start_top <= 744
    assert 42 <= window.download_page.start_button.height() <= 48


def test_download_section_titles_keep_compact_height(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    titles = [
        label
        for label in window.download_page.findChildren(QLabel)
        if label.text().startswith(("1.", "2.", "3."))
    ]

    assert titles
    assert all(title.maximumHeight() <= 24 for title in titles)


def test_download_free_space_row_keeps_compact_height(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.download_page.free_space_label.maximumHeight() <= 24


def test_download_url_input_height_matches_reference(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    assert 36 <= window.download_page.url_input.height() <= 42


def test_download_url_input_hides_scrollbar_chrome(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.download_page.url_input.verticalScrollBarPolicy() == Qt.ScrollBarPolicy.ScrollBarAlwaysOff
    assert window.download_page.url_input.horizontalScrollBarPolicy() == Qt.ScrollBarPolicy.ScrollBarAlwaysOff


def test_download_page_uses_reference_toggle_option_rows(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.download_page.mode_combo.isHidden()
    assert window.download_page.audio_checkbox.objectName() == "optionSwitch"
    assert window.download_page.video_checkbox.objectName() == "optionSwitch"
    assert window.download_page.preview_checkbox.objectName() == "optionSwitch"
    assert window.download_page.audio_checkbox.text() == "下载音频"
    assert window.download_page.video_checkbox.text() == "下载视频"
    assert window.download_page.audio_checkbox.isChecked()
    assert window.download_page.video_checkbox.isChecked()
    assert window.download_page.audio_quality_button.text() == "最佳质量"
    assert window.download_page.video_quality_button.text() == "最佳质量"


def test_download_preview_toggle_is_visible_at_reference_size(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    options_card = window.download_page.preview_checkbox.parentWidget()
    toggle_top = window.download_page.preview_checkbox.mapTo(options_card, QPoint(0, 0)).y()
    toggle_bottom = window.download_page.preview_checkbox.mapTo(
        options_card,
        QPoint(0, window.download_page.preview_checkbox.height()),
    ).y()

    assert window.download_page.preview_checkbox.isVisible()
    assert 0 <= toggle_top < toggle_bottom <= options_card.height()


def test_download_preview_controls_are_hidden_until_enabled(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.show()
    qtbot.wait(50)

    assert window.download_page.preview_player.isHidden()

    window.download_page.preview_checkbox.setChecked(True)
    qtbot.wait(50)

    assert window.download_page.preview_player.isVisible()


def test_download_start_button_stays_near_options_card(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.show()
    qtbot.wait(50)

    gap = window.download_page.start_button.geometry().top() - window.download_page.options_card.geometry().bottom()

    assert 18 <= gap <= 34


def test_download_thumbnail_renders_play_overlay_and_duration_badge(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    thumbnail = QPixmap(320, 180)
    thumbnail.fill(QColor("#336699"))

    window.download_page.show_analysis_result("Demo", "03:33", "720p mp4")
    window.download_page.set_thumbnail(thumbnail)

    assert window.download_page.thumbnail_label.property("hasPreviewOverlay") is True
    assert window.download_page.thumbnail_label.property("durationBadge") == "03:33"


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


def test_formats_page_uses_reference_resolution_rows(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.formats_page.resolution_combo.isHidden()
    assert len(window.formats_page.resolution_buttons) >= 6
    labels = [button.text() for button in window.formats_page.resolution_buttons]
    assert "1080p (FHD)" in labels
    assert "720p (HD)" in labels
    assert window.formats_page.reset_button.text() == "重置默认"
    assert window.formats_page.apply_button.text() == "应用选择"
    assert window.formats_page.resolution_buttons[0].isChecked()


def test_formats_apply_button_visible_without_scrolling_at_reference_size(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(488, 883)
    window.nav.setCurrentRow(1)
    window.show()
    qtbot.wait(50)

    button_bottom = window.formats_page.apply_button.mapTo(window, QPoint(0, window.formats_page.apply_button.height())).y()

    assert button_bottom <= window.bottom_status_bar.geometry().top() - 8


def test_queue_page_headers_and_actions(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert table_headers(window.queue_page.table) == ["标题", "状态", "进度", "速度", "剩余时间", "操作"]
    assert window.queue_page.pause_all_button.text() == "全部暂停"
    assert window.queue_page.clear_completed_button.text() == "清除已完成"
    assert window.queue_page.history_heading.text() == "历史"
    assert table_headers(window.queue_page.recent_history_table) == ["标题", "格式", "状态", "时间"]


def test_queue_history_status_uses_completed_badge(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.queue_page.load_history_records(
        [
            HistoryRecord(
                "測試標題",
                "https://example.com",
                "D:/x.mp4",
                "音频+视频",
                "360p mp4",
                "不下载",
                "finished",
                "2026-06-01T21:44:42",
            )
        ]
    )

    badge_host = window.queue_page.recent_history_table.cellWidget(0, 2)
    badge = badge_host.findChild(QLabel, "statusBadge") if badge_host else None

    assert isinstance(badge, QLabel)
    assert badge.text() == "已完成"
    assert badge.maximumHeight() <= 24


def test_queue_history_footer_exposes_folder_and_search_controls(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.queue_page.open_downloads_button.text() == "打开下载文件夹"
    assert window.queue_page.history_search.placeholderText() == "搜索历史..."
    assert window.queue_page.open_downloads_button.minimumWidth() >= 150
    assert window.queue_page.history_search.minimumWidth() >= 180


def test_queue_history_search_filters_recent_records(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.queue_page.load_history_records(
        [
            HistoryRecord("Alpha Video", "https://example.com/a", "D:/a.mp4", "音频+视频", "1080p MP4", "不下载", "finished", "2026-06-01T10:21:00"),
            HistoryRecord("Beta Clip", "https://example.com/b", "D:/b.mp4", "音频+视频", "720p MP4", "不下载", "finished", "2026-06-01T09:15:00"),
        ]
    )

    window.queue_page.history_search.setText("Beta")

    assert window.queue_page.recent_history_table.rowCount() == 1
    assert window.queue_page.recent_history_table.item(0, 0).text() == "Beta Clip"


def test_queue_toolbar_aligns_with_header_at_reference_size(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(535, 883)
    window.nav.setCurrentRow(2)
    window.show()
    qtbot.wait(50)

    title = next(label for label in window.queue_page.findChildren(QLabel) if label.text() == "队列")
    title_top = title.mapTo(window.queue_page, QPoint(0, 0)).y()
    button_top = window.queue_page.pause_all_button.mapTo(window.queue_page, QPoint(0, 0)).y()

    assert button_top <= title_top + 16


def test_queue_header_uses_compact_reference_layout(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    subtitles = [
        label.text()
        for label in window.queue_page.findChildren(QLabel)
        if label.text().startswith("查看")
    ]

    assert subtitles == []


def test_queue_cards_match_reference_top_and_height(qtbot):
    apply_light_theme(QApplication.instance())
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(535, 883)
    window.nav.setCurrentRow(2)
    for index in range(3):
        task_id = f"task-{index}"
        window.queue_page.add_task(task_id, "Rick Astley - Never Gonna Give You Up (Official Music Video)")
        window.queue_page.update_task(
            task_id,
            status="下载中" if index < 2 else "已完成",
            progress=[68.0, 32.0, 100.0][index],
            speed="2.00MiB/s",
            eta="00:18" if index < 2 else "00:00",
        )
    window.show()
    qtbot.wait(50)

    first_card = window.queue_page._task_cards["task-0"]["card"]
    first_card_top = first_card.mapTo(window, QPoint(0, 0)).y()

    assert 112 <= first_card_top <= 125
    assert 86 <= first_card.height() <= 94


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


def test_queue_card_title_is_single_line_with_full_text_tooltip(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    long_title = "紐約陣容剋制？馬刺天賦碾壓！馬刺VS尼克，冠軍賽超強前瞻！#victorwembanyama"

    window.queue_page.add_task("task-long-title", long_title)
    title_label = window.queue_page._task_cards["task-long-title"]["title"]

    assert title_label.toolTip() == long_title
    assert title_label.wordWrap() is False
    assert title_label.maximumHeight() <= title_label.fontMetrics().height() + 4


def test_queue_history_stays_close_below_task_cards(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(535, 883)
    window.nav.setCurrentRow(2)
    for index in range(3):
        window.queue_page.add_task(f"task-{index}", "紐約陣容剋制？馬刺天賦碾壓！馬刺VS尼克，冠軍賽超強前瞻！")
    window.show()
    qtbot.wait(50)

    last_card = window.queue_page._task_cards["task-2"]["card"]
    card_bottom = last_card.mapTo(window.queue_page, QPoint(0, last_card.height())).y()
    history_top = window.queue_page.history_heading.geometry().top()

    assert 0 <= history_top - card_bottom <= 48


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


def test_settings_action_buttons_fit_chinese_labels_at_default_width(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.resize(590, 883)
    window.nav.setCurrentRow(4)
    window.show()
    qtbot.wait(50)

    buttons = [
        window.settings_page.choose_cookies_button,
        window.settings_page.clear_cookies_button,
        window.settings_page.cookies_help_button,
        window.settings_page.find_ffmpeg_button,
        window.settings_page.choose_ffmpeg_button,
        window.settings_page.ffmpeg_download_button,
    ]

    for button in buttons:
        assert button.width() >= button.sizeHint().width(), button.text()


def test_about_page_status_controls(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert "YTDL GUI" in window.about_page.version_label.text()
    assert window.about_page.ytdlp_label.text() == "yt-dlp 状态：待检测"
    assert window.about_page.ffmpeg_label.text() == "ffmpeg 状态：待检测"
    assert window.about_page.legal_button.text() == "第三方许可"
