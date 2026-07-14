import base64
import importlib.util
import io
import json
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
    module_names = (
        "yt_dlp",
        "yt_dlp.networking",
        "yt_dlp.networking.common",
        "yt_dlp.networking.exceptions",
    )
    previous_modules = {name: sys.modules.get(name) for name in module_names}
    yt_dlp_module = types.ModuleType("yt_dlp")
    yt_dlp_module.__path__ = []
    yt_dlp_module.YoutubeDL = youtube_dl_class
    networking_module = types.ModuleType("yt_dlp.networking")
    networking_module.__path__ = []
    common_module = types.ModuleType("yt_dlp.networking.common")
    common_module.Response = FakeResponse
    exceptions_module = types.ModuleType("yt_dlp.networking.exceptions")
    exceptions_module.HTTPError = FakeHTTPError
    sys.modules.update(
        {
            "yt_dlp": yt_dlp_module,
            "yt_dlp.networking": networking_module,
            "yt_dlp.networking.common": common_module,
            "yt_dlp.networking.exceptions": exceptions_module,
        }
    )
    try:
        spec.loader.exec_module(module)
    finally:
        for name, previous in previous_modules.items():
            if previous is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous
    return module


class FakeResponse:
    def __init__(self, fp, url, headers, status=200, reason=None):
        self.fp = fp
        self.url = url
        self.headers = headers
        self.status = status
        self.reason = reason or "OK"

    def read(self):
        return self.fp.read()


class FakeHTTPError(Exception):
    def __init__(self, status):
        super().__init__(f"HTTP Error {status}")
        self.status = status

    def close(self):
        return None


class RecordingYoutubeDL:
    def urlopen(self, request):
        return request


class CopyableRequest:
    def __init__(self, url, method="GET", data=None, headers=None):
        self.url = url
        self.method = method
        self.data = data
        self.headers = headers or {}

    def copy(self):
        return CopyableRequest(self.url, self.method, self.data, self.headers.copy())


class EmptyCookieJar:
    def get_cookie_header(self, _url):
        return None


class RaisingCookieJar:
    def get_cookie_header(self, _url):
        raise RuntimeError("cookie header unavailable")


class ErroringYoutubeDL:
    def __init__(self, params=None, *_args, **_kwargs):
        self.params = params or {}
        self.cookiejar = EmptyCookieJar()

    def urlopen(self, _request):
        raise FakeHTTPError(410)


class CookieErrorYoutubeDL(ErroringYoutubeDL):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.cookiejar = RaisingCookieJar()


class NotFoundYoutubeDL:
    def __init__(self, *_args, **_kwargs):
        self.cookiejar = EmptyCookieJar()

    def urlopen(self, _request):
        raise FakeHTTPError(404)


class RecordingFallback:
    def __init__(self):
        self.calls = []

    def fetch(self, url, headers_json):
        self.calls.append((url, json.loads(headers_json)))
        return json.dumps(
            {
                "ok": True,
                "status": 200,
                "url": url,
                "headers": {"Content-Type": "text/html"},
                "bodyBase64": base64.b64encode(b"<html>native</html>").decode("ascii"),
            }
        )


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


def test_http_410_https_get_uses_android_metadata_fallback_once():
    bridge = load_android_ytdl_bridge(ErroringYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    response = downloader.urlopen(
        CopyableRequest("https://example.com/page", headers={"User-Agent": "agent"})
    )

    assert response.read() == b"<html>native</html>"
    assert fallback.calls == [("https://example.com/page", {"User-Agent": "agent"})]


def test_android_metadata_fallback_merges_global_headers_with_request_overrides():
    bridge = load_android_ytdl_bridge(ErroringYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(
        {
            "http_headers": {
                "User-Agent": "global-agent",
                "Accept-Language": "global-language",
            }
        },
        android_http_fallback=fallback,
    )

    downloader.urlopen(
        CopyableRequest(
            "https://example.com/page",
            headers={"Accept-Language": "request-language"},
        )
    )

    assert fallback.calls == [
        (
            "https://example.com/page",
            {
                "User-Agent": "global-agent",
                "Accept-Language": "request-language",
            },
        )
    ]


def test_cookie_header_failure_rethrows_original_http_410_without_using_fallback():
    bridge = load_android_ytdl_bridge(CookieErrorYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    try:
        downloader.urlopen(CopyableRequest("https://example.com/page"))
    except FakeHTTPError as exc:
        assert exc.status == 410
    else:
        raise AssertionError("cookie header failure replaced the original HTTP 410")

    assert fallback.calls == []


def test_eporner_http_upgrade_410_does_not_use_android_metadata_fallback():
    bridge = load_android_ytdl_bridge(ErroringYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)
    request = CopyableRequest(
        "http://www.eporner.com/xhr/video/5czAhpxw6bT?hash=test&device=generic"
    )

    try:
        downloader.urlopen(request)
    except FakeHTTPError as exc:
        assert exc.status == 410
    else:
        raise AssertionError("Eporner upgrade unexpectedly used Android fallback")

    assert fallback.calls == []
    assert request.url.startswith("http://")


def test_non_410_https_error_does_not_use_android_metadata_fallback():
    bridge = load_android_ytdl_bridge(NotFoundYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    try:
        downloader.urlopen(CopyableRequest("https://example.com/page"))
    except FakeHTTPError as exc:
        assert exc.status == 404
    else:
        raise AssertionError("non-410 error unexpectedly used Android fallback")

    assert fallback.calls == []


def test_android_metadata_fallback_rejects_http_post_range_and_non_410():
    bridge = load_android_ytdl_bridge(ErroringYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    for request in (
        CopyableRequest("http://example.com/page"),
        CopyableRequest("https://example.com/page", method="POST", data=b"x"),
        CopyableRequest("https://example.com/page", headers={"Range": "bytes=0-9"}),
    ):
        try:
            downloader.urlopen(request)
        except FakeHTTPError as exc:
            assert exc.status == 410
        else:
            raise AssertionError("unsafe request unexpectedly used fallback")

    assert fallback.calls == []


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
