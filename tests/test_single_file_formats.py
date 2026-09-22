import pytest

from ytdl_gui.format_selector import FormatPreference, available_resolution_values, choose_format
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ytdlp_runner import YtdlpCommandBuilder


def single_file(height=720, **fields):
    return {
        "format_id": f"{height}p",
        "height": height,
        "ext": "mp4",
        "url": f"https://media.example.test/{height}.mp4",
        "protocol": "https",
        **fields,
    }


@pytest.mark.parametrize("codecs", [{}, {"vcodec": "av1"}, {"acodec": "mp4a"}, {"vcodec": None, "acodec": None}])
def test_single_file_with_unknown_codecs_remains_selectable(codecs):
    formats = [single_file(240, **codecs), single_file(720, **codecs)]
    choice = choose_format(formats, FormatPreference())
    assert choice.format_id == "720p"
    assert choice.requires_ffmpeg_merge is False
    assert "单文件" in choice.actual_summary
    assert "未知" in choice.actual_summary
    assert available_resolution_values(formats) == {240, 720}


def test_explicit_av1_preference_preserves_exact_format_id():
    formats = [single_file(), single_file(format_id="av1-720p", vcodec="av1")]
    choice = choose_format(formats, FormatPreference(codec="av1"))
    assert choice.format_id == "av1-720p"
    assert "av1" in choice.actual_summary


@pytest.mark.parametrize("fields", [
    {"vcodec": "none"}, {"acodec": "none"}, {"ext": "mhtml"},
    {"url": ""}, {"url": "file:///tmp/video.mp4"},
    {"url": "https://"}, {"url": "https://[broken"},
    {"format_id": ""}, {"format_id": "   "}, {"format_id": None},
])
def test_missing_codecs_do_not_turn_non_video_or_incomplete_entries_into_single_files(fields):
    with pytest.raises(ValueError):
        choose_format([single_file(**fields)], FormatPreference())


@pytest.mark.parametrize("mode", ["audio_only", "video_only"])
def test_unknown_single_file_is_not_claimed_to_be_an_isolated_stream(mode):
    with pytest.raises(ValueError):
        choose_format([single_file()], FormatPreference(download_mode=mode))


@pytest.mark.parametrize("height", [None, 0, "", "unknown"])
@pytest.mark.parametrize("codecs", [{}, {"vcodec": "av1"}, {"vcodec": "av1", "acodec": "aac"}])
def test_original_single_file_without_height_is_selectable_without_invented_quality(height, codecs):
    formats = [single_file(height, format_id="original", **codecs)]
    choice = choose_format(formats, FormatPreference())
    assert choice.format_id == "original"
    assert choice.requires_ffmpeg_merge is False
    assert "原始格式" in choice.actual_summary
    assert "单文件" in choice.actual_summary
    assert "未知" in choice.actual_summary
    assert "Nonep" not in choice.actual_summary
    assert available_resolution_values(formats) == set()


@pytest.mark.parametrize("fields", [
    {"format_id": ""}, {"format_id": "   "}, {"ext": "m4a"},
    {"ext": "mhtml"}, {"url": "https://"}, {"url": "file:///video.mp4"},
    {"vcodec": "none"}, {"acodec": "none"},
])
def test_unknown_height_requires_a_downloadable_video_file(fields):
    with pytest.raises(ValueError):
        choose_format([single_file(None, **fields)], FormatPreference())


@pytest.mark.parametrize("mode", ["audio_only", "video_only"])
def test_original_single_file_never_becomes_an_isolated_stream(mode):
    with pytest.raises(ValueError):
        choose_format([single_file(None)], FormatPreference(download_mode=mode))


def test_known_resolution_candidates_remain_preferred_over_original_fallback():
    choice = choose_format(
        [single_file(None, format_id="original"), single_file(720)],
        FormatPreference(resolution=144),
    )
    assert choice.format_id == "720p"


def test_original_single_file_does_not_merge_with_separate_audio():
    choice = choose_format([
        single_file(None, format_id="original"),
        {"format_id": "audio", "vcodec": "none", "acodec": "aac", "ext": "m4a"},
    ], FormatPreference())
    assert choice.format_id == "original"
    assert choice.requires_ffmpeg_merge is False


def test_gui_original_single_file_selection_creates_download_with_exact_id(qtbot, tmp_path, monkeypatch):
    window = MainWindow()
    qtbot.addWidget(window)
    url = "https://example.test/original"
    metadata = {"title": "Original file", "formats": [single_file(None, format_id="original-exact")]}
    window.apply_analysis_result(url, metadata)
    assert window.selected_format_id == "original-exact"
    assert window.formats_page.resolution_combo.currentText() == "自动"
    assert window.formats_page.resolution_buttons[0].isEnabled()
    assert all(not button.isEnabled() for button in window.formats_page.resolution_buttons[1:])
    window.formats_page.apply_button.click()
    assert "原始格式" in window.download_page.format_summary_label.text()
    assert "未知" in window.download_page.format_summary_label.text()
    assert window.formats_page.merge_hint_label.isHidden()
    requests = []
    monkeypatch.setattr(window, "_start_download_worker", lambda task_id, request, status: requests.append(request))
    monkeypatch.setattr(window, "_active_ytdlp_path", lambda: tmp_path / "yt-dlp.exe")
    window.download_page.preview_checkbox.setChecked(False)
    assert window._start_download_for_analysis(url, metadata, allow_preview=False)
    assert len(requests) == 1
    assert requests[0].format_id == "original-exact"
    command = YtdlpCommandBuilder(requests[0].ytdlp_path).download_command(
        url, tmp_path / "video.mp4", requests[0].format_id,
    )
    assert command[command.index("-f") + 1] == "original-exact"


def test_gui_analysis_selects_unknown_single_file_and_passes_exact_id_to_download(qtbot, tmp_path):
    window = MainWindow()
    qtbot.addWidget(window)
    window.apply_analysis_result("https://example.test/video", {
        "title": "Single-file compatibility test",
        "formats": [single_file(240), single_file(720, format_id="av1-720p", vcodec="av1")],
    })
    assert window.selected_format_id == "av1-720p"
    assert "没有可直接下载" not in window.download_page.status_label.text()
    assert "单文件" in window.download_page.format_summary_label.text()
    window.formats_page.resolution_combo.setCurrentText("240p")
    window.apply_format_selection()
    assert window.selected_format_id == "240p"
    command = YtdlpCommandBuilder(tmp_path / "yt-dlp.exe").download_command(
        "https://example.test/video", tmp_path / "video.mp4", window.selected_format_id,
    )
    assert command[command.index("-f") + 1] == "240p"
