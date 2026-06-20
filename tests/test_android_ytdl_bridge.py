import importlib.util
import sys
import types
from pathlib import Path


def load_android_ytdl_bridge():
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
    sys.modules.setdefault("yt_dlp", types.SimpleNamespace(YoutubeDL=object))
    spec.loader.exec_module(module)
    return module


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
