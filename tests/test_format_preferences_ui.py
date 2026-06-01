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
