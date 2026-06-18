import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from ytdl_gui.ui.main_window import MainWindow


def test_format_page_exposes_lower_smoke_resolutions(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    assert window.formats_page.resolution_combo.findText("360p") >= 0
    assert window.formats_page.resolution_combo.findText("240p") >= 0
    assert window.formats_page.resolution_combo.findText("144p") >= 0
    assert window.formats_page.container_combo.findText("m4a") >= 0


def test_resolution_preference_selects_matching_audio_video_format(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.formats_page.resolution_combo.setCurrentText("360p")
    window.formats_page.container_combo.setCurrentText("mp4")

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
                {"format_id": "22", "height": 720, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
            ],
        },
    )

    assert window.selected_format_id == "18"
    assert window.download_page.format_summary_label.text() == "格式：360p mp4 avc1/mp4a 30fps"


def test_analysis_status_explains_relaxed_format_preferences(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.formats_page.resolution_combo.setCurrentText("1080p")
    window.formats_page.fps_combo.setCurrentText("60")
    window.formats_page.codec_combo.setCurrentText("H.264")

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "22", "height": 720, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
            ],
        },
    )

    status = window.download_page.status_label.text()
    assert "已放宽" in status
    assert "分辨率" in status
    assert "帧率" in status


def test_apply_format_selection_refreshes_actual_format_summary(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.formats_page.resolution_combo.setCurrentText("360p")
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
                {"format_id": "137", "height": 1080, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
            ],
        },
    )
    assert window.download_page.format_summary_label.text() == "格式：360p mp4 avc1/mp4a 30fps"

    window.formats_page.resolution_combo.setCurrentText("1080p")
    window.formats_page.apply_button.click()

    assert window.selected_format_id == "137"
    assert window.download_page.format_summary_label.text() == "格式：1080p mp4 avc1/mp4a 30fps"
    assert window.formats_page.format_id_combo.currentText() == "137"
    assert window.formats_page.format_id_combo.isHidden()
    assert window.formats_page.actual_format_label.text() == "1080p mp4 avc1/mp4a 30fps"
    assert len(window.download_page.status_label.text()) <= 40
    assert "1080p mp4 avc1/mp4a 30fps" in window.download_page.status_label.toolTip()


def test_apply_format_selection_explains_when_resolution_is_relaxed(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
            ],
        },
    )

    window.formats_page.resolution_combo.setCurrentText("1080p")
    window.formats_page.apply_button.click()

    status = window.download_page.status_label.text()
    assert window.selected_format_id == "18"
    assert window.download_page.video_quality_button.text() == "360p"
    assert window.download_page.format_summary_label.text() == "格式：360p mp4 avc1/mp4a 30fps"
    assert "已放宽" in status
    assert "分辨率" in status
    assert len(status) <= 60
    assert "360p mp4 avc1/mp4a 30fps" in window.download_page.status_label.toolTip()


def test_analysis_disables_unavailable_audio_video_resolutions(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.formats_page.resolution_combo.setCurrentText("1080p")

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
                {"format_id": "137", "height": 1080, "ext": "mp4", "vcodec": "avc1", "acodec": "none", "fps": 30},
            ],
        },
    )

    assert window.formats_page.resolution_combo.currentText() == "360p"
    assert _resolution_button(window, "360p").isEnabled()
    assert not _resolution_button(window, "1080p").isEnabled()
    assert _resolution_button(window, "1080p").property("availableResolution") is False
    assert _resolution_button(window, "1080p").toolTip() == "该视频在当前下载类型下没有此分辨率"
    assert _resolution_size_label(window, "1080p").property("availableResolution") is False
    assert window.download_page.video_quality_button.text() in {"最佳质量", "360p"}


def test_audio_video_mode_enables_high_resolution_when_audio_can_be_merged(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.formats_page.resolution_combo.setCurrentText("1080p")

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "fps": 30},
                {"format_id": "299", "height": 1080, "ext": "mp4", "vcodec": "avc1.64002a", "acodec": "none", "fps": 60},
                {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129},
            ],
        },
    )

    assert _resolution_button(window, "1080p").isEnabled()
    assert window.formats_page.resolution_combo.currentText() == "1080p"
    assert window.selected_format_id == "299+140"
    assert window.formats_page.format_id_combo.currentText() == "299+140"
    assert window.formats_page.format_id_combo.isHidden()
    assert window.formats_page.actual_format_label.text() == (
        "1080p mp4 avc1.64002a + 音频 m4a mp4a.40.2 129kbps 60fps"
    )
    assert "299" not in window.formats_page.actual_format_label.text()
    assert "140" not in window.formats_page.actual_format_label.text()
    assert not window.formats_page.merge_hint_label.isHidden()
    assert "ffmpeg 合并" in window.formats_page.merge_hint_label.text()
    assert "ffmpeg 合并" in window.download_page.status_label.text()
    assert "1080p mp4 avc1.64002a + 音频 m4a mp4a.40.2 129kbps 60fps" in window.download_page.format_summary_label.text()


def test_video_only_mode_enables_video_only_resolutions(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
                {"format_id": "137", "height": 1080, "ext": "mp4", "vcodec": "avc1", "acodec": "none", "fps": 30},
            ],
        },
    )

    window.formats_page.mode_buttons[2].click()

    assert _resolution_button(window, "1080p").isEnabled()
    assert window.formats_page.resolution_combo.currentText() == "1080p"


def test_audio_bitrate_preference_selects_matching_audio_only_format(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.download_page.mode_combo.setCurrentText("仅音频")
    window.formats_page.audio_bitrate_combo.setCurrentText("128k")

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "139", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.5", "abr": 49},
                {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129},
            ],
        },
    )

    assert window.selected_format_id == "140"
    assert window.download_page.format_summary_label.text() == "格式：音频 m4a mp4a.40.2 129kbps"


def test_video_only_resolution_and_codec_preferences_select_matching_format(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    window.download_page.mode_combo.setCurrentText("仅视频")
    window.formats_page.resolution_combo.setCurrentText("144p")
    window.formats_page.codec_combo.setCurrentText("H.264")
    window.formats_page.container_combo.setCurrentText("mp4")

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "160", "height": 144, "ext": "mp4", "vcodec": "avc1.4d400c", "acodec": "none", "fps": 30},
                {"format_id": "278", "height": 144, "ext": "webm", "vcodec": "vp9", "acodec": "none", "fps": 30},
                {"format_id": "134", "height": 360, "ext": "mp4", "vcodec": "avc1.4d401e", "acodec": "none", "fps": 30},
            ],
        },
    )

    assert window.selected_format_id == "160"
    assert window.download_page.format_summary_label.text() == "格式：144p mp4 avc1.4d400c/无音频 30fps"


def _resolution_button(window: MainWindow, value: str):
    for button in window.formats_page.resolution_buttons:
        if button.property("resolutionValue") == value:
            return button
    raise AssertionError(f"missing resolution button {value}")


def _resolution_size_label(window: MainWindow, value: str):
    return window.formats_page.resolution_size_labels[value]
