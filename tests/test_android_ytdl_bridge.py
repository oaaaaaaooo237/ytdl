import importlib.util
import sys
import types
from pathlib import Path


def load_android_ytdl_bridge(youtube_dl_class=object):
    bridge_path = (
        Path(__file__).resolve().parents[1]
        / "android"
        / "app"
        / "src"
        / "main"
        / "python"
        / "ytdl_bridge.py"
    )
    spec = importlib.util.spec_from_file_location("android_ytdl_bridge", bridge_path)
    module = importlib.util.module_from_spec(spec)
    previous_yt_dlp = sys.modules.get("yt_dlp")
    sys.modules["yt_dlp"] = types.SimpleNamespace(YoutubeDL=youtube_dl_class)
    try:
        spec.loader.exec_module(module)
    finally:
        if previous_yt_dlp is None:
            sys.modules.pop("yt_dlp", None)
        else:
            sys.modules["yt_dlp"] = previous_yt_dlp
    return module


class RecordingYoutubeDL:
    def urlopen(self, request):
        return request


class CopyableRequest:
    def __init__(self, url):
        self.url = url

    def copy(self):
        return CopyableRequest(self.url)


def test_eporner_metadata_request_uses_https_without_mutating_original_request():
    bridge = load_android_ytdl_bridge(RecordingYoutubeDL)
    downloader = bridge.AndroidYoutubeDL()
    request = CopyableRequest(
        "http://www.eporner.com/xhr/video/5czAhpxw6bT?hash=test&device=generic"
    )

    rewritten = downloader.urlopen(request)

    assert rewritten.url == (
        "https://www.eporner.com/xhr/video/5czAhpxw6bT?hash=test&device=generic"
    )
    assert request.url.startswith("http://")


def test_remote_end_closed_during_analysis_is_reported_as_network_failure():
    bridge = load_android_ytdl_bridge()
    error = RuntimeError("Remote end closed connection without response")

    assert bridge._error_category(error) == "network"
    assert bridge._safe_error_message(error) == "网络连接失败，请稍后重试。"


def test_split_download_file_lookup_does_not_pick_stale_file_for_other_video(tmp_path):
    bridge = load_android_ytdl_bridge()
    stale_file = tmp_path / "download-otherVideo-394-video.mp4"
    stale_file.write_bytes(b"stale video")

    missing_current_file = tmp_path / "download-tkxzMEfp49Q-394-video.mp4"
    info = {
        "id": "tkxzMEfp49Q",
        "requested_downloads": [{"filepath": str(missing_current_file)}],
    }

    assert bridge._find_downloaded_file(info, str(tmp_path), "394", "video") == ""


def test_split_download_file_lookup_accepts_current_video_format_role_file(tmp_path):
    bridge = load_android_ytdl_bridge()
    current_file = tmp_path / "download-tkxzMEfp49Q-394-video.mp4"
    current_file.write_bytes(b"fresh video")

    info = {"id": "tkxzMEfp49Q", "requested_downloads": []}

    assert bridge._find_downloaded_file(info, str(tmp_path), "394", "video") == str(current_file.resolve())


def test_explicit_format_id_rejects_ytdlp_selector_aliases():
    bridge = load_android_ytdl_bridge()

    for selector in [
        "best",
        "worst",
        "bestvideo",
        "bestaudio",
        "worstvideo",
        "worstaudio",
        "bv",
        "ba",
        "wv",
        "wa",
        "b",
        "w",
        "all",
        "mergeall",
        "bv.2",
    ]:
        assert not bridge._is_explicit_format_id(selector), selector

    assert bridge._is_explicit_format_id("394")


def test_safe_format_preserves_unknown_codecs_without_inventing_none():
    bridge = load_android_ytdl_bridge()

    missing = bridge._safe_format({"format_id": "single", "ext": "mp4", "height": 1080})
    blank = bridge._safe_format(
        {
            "format_id": "blank",
            "ext": "mp4",
            "height": 720,
            "vcodec": "",
            "acodec": "   ",
        }
    )
    explicit_none = bridge._safe_format(
        {
            "format_id": "video-only",
            "ext": "mp4",
            "height": 1080,
            "vcodec": "avc1.640028",
            "acodec": "none",
        }
    )

    assert missing["vcodec"] is None
    assert missing["acodec"] is None
    assert blank["vcodec"] is None
    assert blank["acodec"] is None
    assert explicit_none["vcodec"] == "avc1.640028"
    assert explicit_none["acodec"] == "none"
