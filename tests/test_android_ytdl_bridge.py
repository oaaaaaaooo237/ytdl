import base64
import copy
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
    def __init__(self, params=None, *_args, **_kwargs):
        self.params = params or {}
        self.cookiejar = EmptyCookieJar()

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
    def __init__(self, params=None, *_args, **_kwargs):
        self.params = params or {}
        self.cookiejar = EmptyCookieJar()

    def urlopen(self, _request):
        raise FakeHTTPError(404)


class PreconditionErrorYoutubeDL(ErroringYoutubeDL):
    def urlopen(self, _request):
        raise FakeHTTPError(412)


class MutatingPreconditionErrorYoutubeDL(ErroringYoutubeDL):
    def urlopen(self, request):
        request.headers.clear()
        raise FakeHTTPError(412)


class MetadataThenPreconditionYoutubeDL(ErroringYoutubeDL):
    def urlopen(self, request):
        if request.url.endswith(".m3u8"):
            request.headers.clear()
            raise FakeHTTPError(412)
        raise FakeHTTPError(410)


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


class RejectingFallback:
    def __init__(self):
        self.calls = []

    def fetch(self, url, headers_json):
        self.calls.append((url, json.loads(headers_json)))
        return json.dumps({"ok": False})


class RejectFirstM3u8Fallback(RecordingFallback):
    def fetch(self, url, headers_json):
        headers = json.loads(headers_json)
        matching_calls = [call for call in self.calls if call[0] == url]
        if url.split("?", 1)[0].lower().endswith(".m3u8") and not matching_calls:
            self.calls.append((url, headers))
            return json.dumps({"ok": False})
        return super().fetch(url, headers_json)


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


def test_native_metadata_success_avoids_python_http_410():
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


def test_cookie_header_failure_falls_back_to_original_python_error():
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


def test_eporner_http_upgrade_uses_native_metadata_on_rewritten_https_url():
    bridge = load_android_ytdl_bridge(ErroringYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)
    request = CopyableRequest(
        "http://www.eporner.com/xhr/video/5czAhpxw6bT?hash=test&device=generic"
    )

    response = downloader.urlopen(request)

    assert response.read() == b"<html>native</html>"
    assert fallback.calls[0][0].startswith(
        "https://www.eporner.com/xhr/video/5czAhpxw6bT"
    )
    assert request.url.startswith("http://")


def test_native_rejection_then_python_404_does_not_retry_native():
    bridge = load_android_ytdl_bridge(NotFoundYoutubeDL)
    fallback = RejectingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    try:
        downloader.urlopen(CopyableRequest("https://example.com/page"))
    except FakeHTTPError as exc:
        assert exc.status == 404
    else:
        raise AssertionError("Python HTTP 404 was unexpectedly replaced")

    assert fallback.calls == [("https://example.com/page", {})]


def test_android_metadata_https_get_uses_native_before_python():
    bridge = load_android_ytdl_bridge(RecordingYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)
    url = "https://noodlemagazine.com/watch/-207085431_456241493"

    response = downloader.urlopen(CopyableRequest(url, headers={"User-Agent": "agent"}))

    assert response.read() == b"<html>native</html>"
    assert fallback.calls == [(url, {"User-Agent": "agent"})]


def test_android_metadata_falls_back_to_python_when_native_rejects():
    bridge = load_android_ytdl_bridge(RecordingYoutubeDL)
    fallback = RejectingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)
    request = CopyableRequest("https://example.com/page")

    response = downloader.urlopen(request)

    assert response is request
    assert fallback.calls == [("https://example.com/page", {})]


def test_android_metadata_keeps_unsafe_requests_on_python_network():
    bridge = load_android_ytdl_bridge(RecordingYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    for request in (
        CopyableRequest("http://example.com/page"),
        CopyableRequest("https://example.com/page", method="POST", data=b"x"),
        CopyableRequest(
            "https://example.com/page",
            headers={"Range": "bytes=0-9"},
        ),
    ):
        assert downloader.urlopen(request) is request

    assert fallback.calls == []


def test_http_412_m3u8_retries_native_metadata_with_source_headers():
    bridge = load_android_ytdl_bridge(PreconditionErrorYoutubeDL)
    fallback = RejectFirstM3u8Fallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    response = downloader.urlopen(
        CopyableRequest(
            "https://cdn.example.com/path/master.m3u8?token=short-lived",
            headers={"Origin": "https://example.com", "Referer": "https://example.com/"},
        )
    )

    assert response.read() == b"<html>native</html>"
    assert len(fallback.calls) == 2


def test_http_412_m3u8_uses_headers_captured_before_python_handler_mutation():
    bridge = load_android_ytdl_bridge(MutatingPreconditionErrorYoutubeDL)
    fallback = RejectFirstM3u8Fallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    downloader.urlopen(
        CopyableRequest(
            "https://cdn.example.com/master.m3u8",
            headers={"Origin": "https://example.com", "Referer": "https://example.com/"},
        )
    )

    assert fallback.calls == [
        (
            "https://cdn.example.com/master.m3u8",
            {"Origin": "https://example.com", "Referer": "https://example.com/"},
        ),
        (
            "https://cdn.example.com/master.m3u8",
            {"Origin": "https://example.com", "Referer": "https://example.com/"},
        ),
    ]


def test_http_412_m3u8_uses_root_origin_from_successful_metadata_fallback():
    bridge = load_android_ytdl_bridge(MetadataThenPreconditionYoutubeDL)
    fallback = RejectFirstM3u8Fallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    downloader.urlopen(CopyableRequest("https://www.example.com/watch"))
    downloader.urlopen(CopyableRequest("https://cdn.example.net/master.m3u8"))

    assert fallback.calls[2] == (
        "https://cdn.example.net/master.m3u8",
        {
            "Origin": "https://www.example.com",
            "Referer": "https://www.example.com/",
        },
    )


def test_http_412_m3u8_does_not_invent_source_without_metadata_fallback():
    bridge = load_android_ytdl_bridge(PreconditionErrorYoutubeDL)
    fallback = RejectFirstM3u8Fallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    downloader.urlopen(CopyableRequest("https://cdn.example.net/master.m3u8"))

    assert fallback.calls == [
        ("https://cdn.example.net/master.m3u8", {}),
        ("https://cdn.example.net/master.m3u8", {}),
    ]


def test_http_412_non_m3u8_does_not_use_android_metadata_fallback():
    bridge = load_android_ytdl_bridge(PreconditionErrorYoutubeDL)
    fallback = RejectingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    try:
        downloader.urlopen(CopyableRequest("https://example.com/page"))
    except FakeHTTPError as exc:
        assert exc.status == 412
    else:
        raise AssertionError("non-m3u8 HTTP 412 unexpectedly used Android fallback")

    assert fallback.calls == [("https://example.com/page", {})]


def test_http_412_m3u8_fallback_rejects_http_post_and_range():
    bridge = load_android_ytdl_bridge(PreconditionErrorYoutubeDL)
    fallback = RecordingFallback()
    downloader = bridge.AndroidYoutubeDL(android_http_fallback=fallback)

    for request in (
        CopyableRequest("http://cdn.example.com/master.m3u8"),
        CopyableRequest("https://cdn.example.com/master.m3u8", method="POST", data=b"x"),
        CopyableRequest(
            "https://cdn.example.com/master.m3u8",
            headers={"Range": "bytes=0-9"},
        ),
    ):
        try:
            downloader.urlopen(request)
        except FakeHTTPError as exc:
            assert exc.status == 412
        else:
            raise AssertionError("unsafe HTTP 412 request unexpectedly used Android fallback")

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

def test_download_options_have_bounded_network_waits(tmp_path):
    captured = []

    class BoundedProbe(RecordingYoutubeDL):
        def __init__(self, params=None, **kwargs):
            super().__init__(params)
            captured.append(params)
        def __enter__(self):
            return self
        def __exit__(self, *args):
            pass
        def extract_info(self, *args, **kwargs):
            raise TimeoutError("bounded probe")

    bridge = load_android_ytdl_bridge(BoundedProbe)
    bridge.download_format("https://example.test/watch", str(tmp_path), "18", "media")
    bridge.download_subtitle("https://example.test/watch", str(tmp_path), "en", "vtt", "manual")
    assert len(captured) == 2
    for options in captured:
        assert options["socket_timeout"] == 10
        assert options["retries"] == 1
        assert options["fragment_retries"] == 1
        assert options["extractor_retries"] == 1
        assert options["continuedl"] is True
        assert options["nopart"] is False

def test_stop_during_metadata_fallback_does_not_start_python_retry():
    network_calls = []
    stopped = False

    class NetworkProbe(RecordingYoutubeDL):
        def urlopen(self, request):
            network_calls.append(request)
            return request

    class StopFallback:
        def fetch(self, url, headers):
            nonlocal stopped
            stopped = True
            return json.dumps({"ok": False})

    def hook(event):
        if stopped:
            raise RuntimeError("stopped")

    bridge = load_android_ytdl_bridge(NetworkProbe)
    ydl = bridge.AndroidYoutubeDL({"progress_hooks": [hook]}, android_http_fallback=StopFallback())
    import pytest
    with pytest.raises(RuntimeError, match="stopped"):
        ydl.urlopen("https://example.test/page")
    assert network_calls == []


def test_http_access_and_missing_resource_errors_never_wait_for_network():
    bridge = load_android_ytdl_bridge()
    for message, expected in [
        ("HTTP Error 401: Unauthorized", "permission"),
        ("HTTP Error 403: Forbidden", "permission"),
        ("network request failed: HTTP Error 403: Forbidden", "permission"),
        ("HTTP Error 404: Not Found", "unsupported"),
        ("network request failed: HTTP Error 410: Gone", "unsupported"),
        ("HTTPError: 403", "permission"),
        ("network request failed: Forbidden", "permission"),
    ]:
        assert bridge._error_category(RuntimeError(message)) == expected, message


def test_connection_reset_refused_and_dns_failures_are_network_errors():
    import socket

    bridge = load_android_ytdl_bridge()
    for error in [
        ConnectionResetError(104, "Connection reset by peer"),
        ConnectionRefusedError(111, "Connection refused"),
        socket.gaierror(-2, "Name or service not known"),
        RuntimeError("Unable to download webpage: Connection reset by peer"),
        RuntimeError("Unable to download webpage: Connection refused"),
        RuntimeError("Name or service not known"),
        RuntimeError("Temporary failure in name resolution"),
        RuntimeError("getaddrinfo failed"),
        RuntimeError("No route to host"),
        RuntimeError("Unable to download https://example.test/role: Connection reset by peer"),
        RuntimeError("Unable to download https://example.test/forbidden: Connection reset by peer"),
    ]:
        assert bridge._error_category(error) == "network", str(error)


def test_tls_connection_eof_is_a_retryable_network_failure():
    import ssl

    bridge = load_android_ytdl_bridge()
    errors = [
        ssl.SSLEOFError(8, "EOF occurred in violation of protocol"),
        RuntimeError(
            "ERROR: [generic] sample-20s: Unable to download webpage: "
            "[SSL: UNEXPECTED_EOF_WHILE_READING] EOF occurred in violation of protocol (_ssl.c:1010)"
        ),
    ]
    for error in errors:
        assert bridge._error_category(error) == "network"
        assert bridge._safe_error_message(error) == "网络连接失败，请稍后重试。"


def test_format_selection_failure_is_not_a_network_outage():
    bridge = load_android_ytdl_bridge()
    assert bridge._error_category(RuntimeError("Requested format is not available")) == "unsupported"
    assert bridge._error_category(RuntimeError("format id network is invalid")) == "unsupported"
    assert bridge._error_category(FileNotFoundError("output file missing")) == "unknown"


def test_empty_site_formats_report_the_site_response_instead_of_file_processing():
    bridge = load_android_ytdl_bridge()
    error = RuntimeError("ERROR: [PornHub] sample: No video formats found!")
    assert bridge._error_category(error) == "unsupported"
    assert bridge._safe_error_message(error) == "网站本次未返回可下载的视频格式，请稍后重新分析。"


def test_http_server_response_is_not_reported_as_a_disconnected_network():
    bridge = load_android_ytdl_bridge()
    assert bridge._error_category(RuntimeError("network request failed: HTTP Error 503")) == "unknown"


def test_pornhub_analysis_and_download_use_the_same_verified_tls_profile(tmp_path):
    sessions = []
    output = tmp_path / "download-test-240p-media.mp4"

    class ProfileYoutubeDL:
        def __init__(self, params):
            self.params = params
            self.cookiejar = []
            sessions.append(dict(params))

        @staticmethod
        def sanitize_info(info, remove_private_keys=False):
            return copy.deepcopy(info)

        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

        def extract_info(self, url, download):
            assert self.params.get("legacyserverconnect") is True
            assert not self.params.get("nocheckcertificate", False)
            if download:
                assert self.params["format"] == "240p"
                output.write_bytes(b"downloaded media")
            return {"id": "test", "title": "test", "format_id": "240p", "filepath": str(output),
                    "formats": [{"format_id": "240p", "height": 240, "ext": "mp4"}]}

    bridge = load_android_ytdl_bridge(ProfileYoutubeDL)
    url = "https://cn.pornhub.com/view_video.php?viewkey=test"
    cookies = tmp_path / "chosen-cookies.txt"
    cookies.write_text("# Netscape HTTP Cookie File\n", encoding="utf-8")
    analysis = json.loads(bridge.analyze(url, cookies_path=str(cookies)))
    assert analysis["ok"] is True
    selected = analysis["formats"][0]["format_id"]
    result = json.loads(bridge.download_format(url, str(tmp_path), selected, "media", cookies_path=str(cookies)))
    assert result["ok"] is True
    assert result["formatId"] == selected
    assert len(sessions) == 2
    assert all(options["cookiefile"] == str(cookies) for options in sessions)


def test_site_tls_profile_does_not_change_other_sites_or_lookalike_hosts():
    sessions = []

    class ProfileYoutubeDL:
        def __init__(self, params):
            self.params = params

        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

        def extract_info(self, url, download):
            sessions.append((url, self.params.get("legacyserverconnect", False)))
            return {"title": "test", "formats": []}

    bridge = load_android_ytdl_bridge(ProfileYoutubeDL)
    for url in (
        "https://www.pornhub.com/view_video.php?viewkey=test",
        "https://pornhub.com/view_video.php?viewkey=test",
        "https://pornhub.com.evil.test/page",
        "https://notpornhub.com/page",
        "https://www.youtube.com/watch?v=test",
    ):
        assert json.loads(bridge.analyze(url))["ok"] is True
    assert [enabled for _, enabled in sessions] == [True, True, False, False, False]


def test_download_uses_the_analyzed_snapshot_without_requesting_the_page_again(tmp_path):
    calls = []
    output = tmp_path / "download-test-240p-media.mp4"

    class SnapshotYoutubeDL:
        def __init__(self, params):
            self.params = params
            self.cookiejar = types.SimpleNamespace(set_cookie=lambda cookie: None)

        def __enter__(self):
            return self

        def __exit__(self, *args):
            pass

        @staticmethod
        def sanitize_info(info, remove_private_keys=False):
            return copy.deepcopy(info)

        def extract_info(self, url, download):
            calls.append("page")
            self.cookiejar = []
            assert download is False, "下载不应第二次请求网页"
            return {"id": "test", "title": "test", "formats": [{"format_id": "240p", "height": 240, "ext": "mp4"}]}

        def process_ie_result(self, info, download):
            calls.append("snapshot")
            assert download is True
            assert self.params["format"] == info["formats"][0]["format_id"] == "240p"
            output.write_bytes(b"downloaded media")
            return {"id": "test", "title": "test", "format_id": "240p", "filepath": str(output)}

    bridge = load_android_ytdl_bridge(SnapshotYoutubeDL)
    url = "https://cn.pornhub.com/view_video.php?viewkey=test"
    analysis = json.loads(bridge.analyze(url))
    assert analysis.get("analysisKey")
    result = json.loads(bridge.download_format(url, str(tmp_path), "240p", "media", analysis_key=analysis["analysisKey"]))
    assert result["ok"] is True
    assert calls == ["page", "snapshot"]
    other = json.loads(bridge.download_format(
        "https://cn.pornhub.com/view_video.php?viewkey=other", str(tmp_path), "240p", "media",
        analysis_key=analysis["analysisKey"]))
    assert other["ok"] is False
    assert calls[-1] == "page"
    changed_cookies = tmp_path / "changed-cookies.txt"
    changed_cookies.write_text("# Netscape HTTP Cookie File\n", encoding="utf-8")
    changed = json.loads(bridge.download_format(url, str(tmp_path), "240p", "media",
        cookies_path=str(changed_cookies), analysis_key=analysis["analysisKey"]))
    assert changed["ok"] is False
    assert calls.count("snapshot") == 1
