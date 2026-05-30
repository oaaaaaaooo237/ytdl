from ytdl_gui.format_selector import FormatPreference, choose_format
from ytdl_gui.subtitles import choose_subtitle_language, subtitle_action_requires_ffmpeg
from ytdl_gui.ytdlp_runner import AnalysisFailureKind, categorize_analysis_error, playlist_limit_message


def test_choose_format_relaxes_resolution_and_reports_actual():
    formats = [
        {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
        {"format_id": "22", "height": 720, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
    ]

    result = choose_format(formats, FormatPreference(resolution=1080, container="mp4"))

    assert result.format_id == "22"
    assert result.actual_summary == "720p mp4 avc1/mp4a 30fps"
    assert result.relaxed == ["resolution"]


def test_auto_format_picks_highest_single_file():
    formats = [
        {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
        {"format_id": "22", "height": 720, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
    ]

    result = choose_format(formats, FormatPreference(resolution=None, container="mp4"))

    assert result.format_id == "22"
    assert result.relaxed == []


def test_choose_format_relaxes_container_when_no_container_match():
    formats = [
        {"format_id": "43", "height": 360, "ext": "webm", "vcodec": "vp9", "acodec": "opus", "fps": 30},
    ]

    result = choose_format(formats, FormatPreference(resolution=360, container="mp4"))

    assert result.format_id == "43"
    assert result.actual_summary == "360p webm vp9/opus 30fps"
    assert result.relaxed == ["container"]


def test_choose_format_relaxes_fps_codec_and_bitrate_preferences():
    formats = [
        {
            "format_id": "18",
            "height": 720,
            "ext": "mp4",
            "vcodec": "avc1",
            "acodec": "mp4a",
            "fps": 30,
            "vbr": 1500,
            "abr": 128,
        },
    ]

    result = choose_format(
        formats,
        FormatPreference(
            resolution=720,
            container="mp4",
            fps=60,
            codec="vp9",
            video_bitrate=3000,
            audio_bitrate=192,
        ),
    )

    assert result.format_id == "18"
    assert result.relaxed == ["fps", "codec", "video_bitrate", "audio_bitrate"]


def test_choose_format_parses_numeric_strings_for_preferences():
    formats = [
        {
            "format_id": "360",
            "height": "360",
            "ext": "mp4",
            "vcodec": "avc1",
            "acodec": "mp4a",
            "fps": "30",
            "vbr": "800",
            "abr": "96",
        },
        {
            "format_id": "1080",
            "height": "1080",
            "ext": "mp4",
            "vcodec": "avc1",
            "acodec": "mp4a",
            "fps": "60",
            "vbr": "3000",
            "abr": "192",
        },
    ]

    result = choose_format(
        formats,
        FormatPreference(resolution=1080, container="mp4", fps=60, video_bitrate=3000, audio_bitrate=192),
    )

    assert result.format_id == "1080"
    assert result.relaxed == []


def test_choose_format_treats_non_finite_numeric_strings_as_zero():
    formats = [
        {
            "format_id": "bad",
            "height": "inf",
            "ext": "mp4",
            "vcodec": "avc1",
            "acodec": "mp4a",
            "fps": "nan",
            "vbr": "-inf",
            "abr": "not-a-number",
        },
        {
            "format_id": "good",
            "height": "720",
            "ext": "mp4",
            "vcodec": "avc1",
            "acodec": "mp4a",
            "fps": "30",
            "vbr": "1500",
            "abr": "128",
        },
    ]

    result = choose_format(formats, FormatPreference(resolution=720, container="mp4"))

    assert result.format_id == "good"


def test_choose_format_reports_chinese_error_when_no_single_file_format():
    formats = [
        {"format_id": "137", "height": 1080, "ext": "mp4", "vcodec": "avc1", "acodec": "none", "fps": 30},
        {"format_id": "140", "height": None, "ext": "m4a", "vcodec": "none", "acodec": "mp4a"},
    ]

    try:
        choose_format(formats, FormatPreference())
    except ValueError as exc:
        message = str(exc)
    else:
        raise AssertionError("expected ValueError")

    assert message == "没有可用的单文件格式"
    assert "æ" not in message
    assert "�" not in message


def test_choose_format_rejects_formats_missing_codec_metadata():
    formats = [
        {"format_id": "missing-video", "height": 720, "ext": "mp4", "acodec": "mp4a", "fps": 30},
        {"format_id": "missing-audio", "height": 720, "ext": "mp4", "vcodec": "avc1", "fps": 30},
    ]

    try:
        choose_format(formats, FormatPreference())
    except ValueError as exc:
        message = str(exc)
    else:
        raise AssertionError("expected ValueError")

    assert message == "没有可用的单文件格式"


def test_choose_format_reports_chinese_error_when_format_id_missing():
    formats = [
        {"height": 720, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
    ]

    try:
        choose_format(formats, FormatPreference(resolution=720, container="mp4"))
    except ValueError as exc:
        message = str(exc)
    else:
        raise AssertionError("expected ValueError")

    assert message == "所选格式缺少 format_id，无法创建下载任务"
    assert "KeyError" not in message


def test_subtitle_language_priority_matches_common_tags():
    subtitles = {"zh-Hant": [{}], "en-US": [{}]}

    assert choose_subtitle_language(subtitles, ["en", "zh"]) == "en-US"
    assert choose_subtitle_language(subtitles, ["zh"]) == "zh-Hant"


def test_subtitle_language_returns_empty_when_no_fallback_matches():
    subtitles = {"ja": [{}], "fr": [{}]}

    assert choose_subtitle_language(subtitles, ["en", "zh"]) == ""


def test_subtitle_language_matches_simplified_chinese_tag():
    subtitles = {"zh-CN": [{}]}

    assert choose_subtitle_language(subtitles, ["zh"]) == "zh-CN"


def test_subtitle_language_matches_common_tags_case_insensitively():
    subtitles = {"en-us": [{}], "zh-hans": [{}]}

    assert choose_subtitle_language(subtitles, ["en"]) == "en-us"
    assert choose_subtitle_language(subtitles, ["zh"]) == "zh-hans"


def test_subtitle_ffmpeg_requirements():
    assert subtitle_action_requires_ffmpeg("file") is False
    assert subtitle_action_requires_ffmpeg("embed") is True
    assert subtitle_action_requires_ffmpeg("burn") is True


def test_analysis_error_categories():
    assert categorize_analysis_error("Sign in to confirm your age") == AnalysisFailureKind.LOGIN_REQUIRED
    assert categorize_analysis_error("This video is unavailable") == AnalysisFailureKind.UNAVAILABLE
    assert categorize_analysis_error("timed out") == AnalysisFailureKind.NETWORK_TIMEOUT
    assert categorize_analysis_error("Unsupported URL") == AnalysisFailureKind.UNSUPPORTED_URL
    assert categorize_analysis_error("traceback") == AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE


def test_analysis_error_categories_playlist_and_canceled():
    assert (
        categorize_analysis_error("playlist has 120 entries and needs confirmation")
        == AnalysisFailureKind.PLAYLIST_CONFIRMATION_NEEDED
    )
    assert categorize_analysis_error("analysis canceled by user") == AnalysisFailureKind.CANCELED


def test_playlist_limit_message_mentions_limit():
    assert "50" in playlist_limit_message(accepted=50, skipped=12)


def test_playlist_limit_message_is_readable_chinese():
    message = playlist_limit_message(accepted=50, skipped=12)

    assert message == "播放列表最多展开 50 项；已加入 50 项，跳过 12 项。"
    assert "鎾" not in message
    assert "�" not in message
