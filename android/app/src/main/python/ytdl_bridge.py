import base64
import copy
import hashlib
import io
import json
import os
import re
import socket
import threading
import time
import urllib.parse
import uuid
from collections import OrderedDict

import yt_dlp
from yt_dlp.networking.common import Response
from yt_dlp.networking.exceptions import HTTPError


_EPORNER_METADATA_HTTP_PREFIX = "http://www.eporner.com/xhr/video/"
_ANALYSIS_SNAPSHOTS = OrderedDict()
_ANALYSIS_SNAPSHOT_LOCK = threading.Lock()


def _upgrade_eporner_metadata_url(url):
    if isinstance(url, str) and url.startswith(_EPORNER_METADATA_HTTP_PREFIX):
        return "https://" + url[len("http://") :]
    return url


class AndroidYoutubeDL(yt_dlp.YoutubeDL):
    def __init__(self, *args, android_http_fallback=None, **kwargs):
        self._android_http_fallback = android_http_fallback
        self._android_metadata_origin = None
        super().__init__(*args, **kwargs)

    def _check_download_stop(self):
        # Reuse the progress callback solely as a stop probe before metadata I/O.
        for hook in self.params.get("progress_hooks") or ():
            hook({"status": "checking"})

    def urlopen(self, request):
        self._check_download_stop()
        if isinstance(request, str):
            request = _upgrade_eporner_metadata_url(request)
        else:
            original_url = getattr(request, "url", None)
            upgraded_url = _upgrade_eporner_metadata_url(original_url)
            if upgraded_url != original_url:
                request = request.copy()
                request.url = upgraded_url
        request_headers = (
            {} if isinstance(request, str) else dict(getattr(request, "headers", {}) or {})
        )
        native_response = (
            None if self.params.get("legacyserverconnect")
            else self._try_android_metadata_request(request, request_headers)
        )
        self._check_download_stop()
        if native_response is not None:
            return native_response
        try:
            response = super().urlopen(request)
            try:
                self._check_download_stop()
            except Exception:
                close = getattr(response, "close", None)
                if close:
                    close()
                raise
            return response
        except HTTPError as exc:
            self._check_download_stop()
            response = self._try_android_m3u8_precondition_retry(
                request,
                exc,
                request_headers,
            )
            self._check_download_stop()
            if response is None:
                raise
            exc.close()
            return response

    def _try_android_m3u8_precondition_retry(self, request, error, request_headers):
        url = request if isinstance(request, str) else getattr(request, "url", "")
        try:
            is_m3u8_request = (
                isinstance(url, str)
                and urllib.parse.urlsplit(url).path.lower().endswith(".m3u8")
            )
        except ValueError:
            is_m3u8_request = False
        if (
            not self._android_http_fallback
            or error.status != 412
            or not is_m3u8_request
        ):
            return None

        return self._try_android_metadata_request(
            request,
            request_headers,
            add_metadata_origin=True,
        )

    def _try_android_metadata_request(
        self,
        request,
        request_headers,
        add_metadata_origin=False,
    ):
        if not self._android_http_fallback:
            return None

        method = "GET" if isinstance(request, str) else str(getattr(request, "method", "GET") or "GET")
        data = None if isinstance(request, str) else getattr(request, "data", None)
        url = request if isinstance(request, str) else getattr(request, "url", "")
        headers = dict(self.params.get("http_headers") or {})
        headers.update(request_headers)
        if add_metadata_origin and self._android_metadata_origin:
            normalized_header_names = {str(name).lower() for name in headers}
            if "origin" not in normalized_header_names:
                headers["Origin"] = self._android_metadata_origin
            if "referer" not in normalized_header_names:
                headers["Referer"] = self._android_metadata_origin + "/"
        if (
            not isinstance(url, str)
            or not url.lower().startswith("https://")
            or method.upper() != "GET"
            or data is not None
            or any(str(name).lower() == "range" for name in headers)
        ):
            return None

        try:
            if not any(str(name).lower() == "cookie" for name in headers):
                cookie = self.cookiejar.get_cookie_header(url)
                if cookie:
                    headers["Cookie"] = cookie

            raw_result = self._android_http_fallback.fetch(
                url,
                json.dumps({str(name): str(value) for name, value in headers.items()}),
            )
            result = json.loads(str(raw_result))
            if not result.get("ok"):
                return None
            body = base64.b64decode(result["bodyBase64"], validate=True)
            status = int(result["status"])
            response_url = str(result["url"])
            response_headers = {
                str(name): str(value) for name, value in (result.get("headers") or {}).items()
            }
            content_type = next(
                (
                    value
                    for name, value in response_headers.items()
                    if name.lower() == "content-type"
                ),
                "",
            ).partition(";")[0].strip().lower()
            if content_type == "text/html":
                parsed_response_url = urllib.parse.urlsplit(response_url)
                if (
                    parsed_response_url.scheme.lower() == "https"
                    and parsed_response_url.hostname
                    and parsed_response_url.username is None
                    and parsed_response_url.password is None
                ):
                    self._android_metadata_origin = (
                        f"https://{parsed_response_url.netloc}"
                    )
            return Response(
                io.BytesIO(body),
                response_url,
                response_headers,
                status=status,
            )
        except Exception:
            return None


class ProgressListenerException(Exception):
    pass


def _uses_compatible_tls(url):
    try:
        parsed = urllib.parse.urlsplit(url)
        host = parsed.hostname or ""
        return parsed.scheme.lower() in ("http", "https") and (
            host == "pornhub.com" or host.endswith(".pornhub.com")
        )
    except ValueError:
        return False


def _downloader_for_url(url, options, android_http_fallback):
    compatible = _uses_compatible_tls(url)
    # This extractor needs the same TLS profile for the page, formats and media.
    if compatible:
        options = dict(options, legacyserverconnect=True)
    downloader = AndroidYoutubeDL(options, android_http_fallback=android_http_fallback)
    if compatible:
        downloader._android_metadata_origin = "https://www.pornhub.com"
    return downloader


def _cookies_digest(path):
    if not path:
        return None
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for block in iter(lambda: source.read(65536), b""):
            digest.update(block)
    return digest.digest()


def _remember_analysis(url, info, downloader, cookies_digest):
    key = uuid.uuid4().hex
    entry = {
        "url": url,
        "created": time.monotonic(),
        "cookies_digest": cookies_digest,
        "info": downloader.sanitize_info(info, remove_private_keys=True),
        "cookies": [copy.copy(cookie) for cookie in downloader.cookiejar],
    }
    with _ANALYSIS_SNAPSHOT_LOCK:
        _ANALYSIS_SNAPSHOTS[key] = entry
        while len(_ANALYSIS_SNAPSHOTS) > 16:
            _ANALYSIS_SNAPSHOTS.popitem(last=False)
    return key


def _cached_analysis(key, url, cookies_path):
    with _ANALYSIS_SNAPSHOT_LOCK:
        entry = _ANALYSIS_SNAPSHOTS.get(key)
        if entry is None or entry["url"] != url:
            return None
        if time.monotonic() - entry["created"] > 900:
            _ANALYSIS_SNAPSHOTS.pop(key, None)
            return None
        _ANALYSIS_SNAPSHOTS.move_to_end(key)
    try:
        if entry["cookies_digest"] != _cookies_digest(cookies_path):
            return None
    except OSError:
        return None
    return copy.deepcopy(entry)


def analyze(url, cookies_path=None, android_http_fallback=None):
    options = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "noplaylist": True,
        "extract_flat": False,
    }
    if cookies_path:
        options["cookiefile"] = cookies_path

    try:
        cookies_digest = _cookies_digest(cookies_path) if _uses_compatible_tls(url) else None
        with _downloader_for_url(url, options, android_http_fallback) as ydl:
            info = ydl.extract_info(url, download=False)
            analysis_key = (
                _remember_analysis(url, info, ydl, cookies_digest)
                if _uses_compatible_tls(url) and info.get("formats") else None
            )
        result = _to_result(info)
        if analysis_key:
            result["analysisKey"] = analysis_key
        return json.dumps(result, ensure_ascii=False)
    except Exception as exc:  # yt-dlp has multiple extractor/downloader exception types.
        return json.dumps(
            {
                "ok": False,
                "errorCategory": _error_category(exc),
                "errorMessage": _safe_error_message(exc),
            },
            ensure_ascii=False,
        )


def download_single_file(
    url,
    output_dir,
    cookies_path=None,
    progress_listener=None,
    android_http_fallback=None,
):
    os.makedirs(output_dir, exist_ok=True)

    def progress_hook(event):
        _emit_progress(progress_listener, event)

    options = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "format": "18/worst[ext=mp4]/worst",
        "outtmpl": os.path.join(output_dir, "download-%(id)s.%(ext)s"),
        "progress_hooks": [progress_hook],
        "socket_timeout": 10,
        "retries": 1,
        "fragment_retries": 1,
        "extractor_retries": 1,
        "continuedl": True,
        "nopart": False,
    }
    if cookies_path:
        options["cookiefile"] = cookies_path

    try:
        with _downloader_for_url(url, options, android_http_fallback) as ydl:
            info = ydl.extract_info(url, download=True)
        output_path = _find_downloaded_file(info, output_dir)
        bytes_written = _downloaded_file_size(output_path)
        if bytes_written <= 0:
            raise RuntimeError("download output file was not created")
        return json.dumps(
            {
                "ok": True,
                "title": info.get("title") or "",
                "formatId": str(info.get("format_id") or ""),
                "outputPath": output_path,
                "bytesWritten": bytes_written,
            },
            ensure_ascii=False,
        )
    except Exception as exc:  # yt-dlp has multiple extractor/downloader exception types.
        return json.dumps(
            {
                "ok": False,
                "errorCategory": _error_category(exc),
                "errorMessage": _safe_error_message(exc),
            },
            ensure_ascii=False,
        )


def download_format(
    url,
    output_dir,
    format_id,
    role,
    cookies_path=None,
    progress_listener=None,
    android_http_fallback=None,
    analysis_key=None,
):
    try:
        normalized_format_id = str(format_id or "").strip()
        normalized_role = str(role or "").strip()
        if not _is_explicit_format_id(normalized_format_id):
            raise ValueError("format id must be a single explicit id")
        if normalized_role not in ("media", "video", "audio"):
            raise ValueError("role must be media, video or audio")

        os.makedirs(output_dir, exist_ok=True)

        def progress_hook(event):
            _emit_progress(progress_listener, event)

        options = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "format": normalized_format_id,
            "outtmpl": os.path.join(
                output_dir,
                f"download-%(id)s-%(format_id)s-{normalized_role}.%(ext)s",
            ),
            "progress_hooks": [progress_hook],
            "socket_timeout": 10,
            "retries": 1,
            "fragment_retries": 1,
            "extractor_retries": 1,
            "continuedl": True,
            "nopart": False,
        }
        if cookies_path:
            options["cookiefile"] = cookies_path

        with _downloader_for_url(url, options, android_http_fallback) as ydl:
            snapshot = _cached_analysis(analysis_key, url, cookies_path) if analysis_key else None
            if snapshot is None:
                info = ydl.extract_info(url, download=True)
            else:
                for cookie in snapshot["cookies"]:
                    ydl.cookiejar.set_cookie(cookie)
                info = ydl.process_ie_result(snapshot["info"], download=True)
        output_path = _find_downloaded_file(info, output_dir, normalized_format_id, normalized_role)
        bytes_written = _downloaded_file_size(output_path)
        if bytes_written <= 0:
            raise RuntimeError("download output file was not created")
        return json.dumps(
            {
                "ok": True,
                "title": info.get("title") or "",
                "formatId": str(info.get("format_id") or normalized_format_id),
                "role": normalized_role,
                "outputPath": output_path,
                "bytesWritten": bytes_written,
            },
            ensure_ascii=False,
        )
    except Exception as exc:  # yt-dlp has multiple extractor/downloader exception types.
        return json.dumps(
            {
                "ok": False,
                "errorCategory": _error_category(exc),
                "errorMessage": _safe_error_message(exc),
            },
            ensure_ascii=False,
        )


def download_subtitle(
    url,
    output_dir,
    language,
    ext,
    source,
    cookies_path=None,
    progress_listener=None,
    android_http_fallback=None,
):
    try:
        normalized_language = str(language or "").strip()
        normalized_ext = str(ext or "").strip()
        normalized_source = str(source or "").strip()
        if not _is_explicit_subtitle_token(normalized_language):
            raise ValueError("subtitle language must be a single explicit value")
        if not _is_explicit_subtitle_token(normalized_ext):
            raise ValueError("subtitle extension must be a single explicit value")
        if normalized_source not in ("manual", "automatic"):
            raise ValueError("subtitle source must be manual or automatic")

        os.makedirs(output_dir, exist_ok=True)

        def progress_hook(event):
            _emit_progress(progress_listener, event)

        options = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "skip_download": True,
            "writesubtitles": normalized_source == "manual",
            "writeautomaticsub": normalized_source == "automatic",
            "subtitleslangs": [normalized_language],
            "subtitlesformat": normalized_ext,
            "outtmpl": os.path.join(output_dir, f"download-%(id)s-subtitle-{normalized_source}.%(ext)s"),
            "progress_hooks": [progress_hook],
            "socket_timeout": 10,
            "retries": 1,
            "fragment_retries": 1,
            "extractor_retries": 1,
            "continuedl": True,
            "nopart": False,
        }
        if cookies_path:
            options["cookiefile"] = cookies_path

        with AndroidYoutubeDL(
            {**options, "writesubtitles": False, "writeautomaticsub": False},
            android_http_fallback=android_http_fallback,
        ) as ydl:
            analysis_info = ydl.extract_info(url, download=False)
        if not _subtitle_available(analysis_info, normalized_language, normalized_ext, normalized_source):
            raise ValueError("requested subtitle is not available")

        with _downloader_for_url(url, options, android_http_fallback) as ydl:
            info = ydl.extract_info(url, download=True)
        output_path = _find_subtitle_file(info, output_dir, normalized_language, normalized_ext, normalized_source)
        bytes_written = _downloaded_file_size(output_path)
        if bytes_written <= 0:
            raise RuntimeError("subtitle output file was not created")
        return json.dumps(
            {
                "ok": True,
                "title": info.get("title") or "",
                "language": normalized_language,
                "ext": normalized_ext,
                "source": normalized_source,
                "outputPath": output_path,
                "bytesWritten": bytes_written,
            },
            ensure_ascii=False,
        )
    except Exception as exc:  # yt-dlp has multiple extractor/downloader exception types.
        return json.dumps(
            {
                "ok": False,
                "errorCategory": _error_category(exc),
                "errorMessage": _safe_error_message(exc),
            },
            ensure_ascii=False,
        )


def _to_result(info):
    return {
        "ok": True,
        "title": info.get("title") or "",
        "duration": int(info.get("duration") or 0),
        "thumbnail": info.get("thumbnail") or "",
        "formats": [_safe_format(item) for item in info.get("formats") or []],
        "subtitles": _safe_subtitles(info.get("subtitles") or {}),
        "automatic_captions": _safe_subtitles(info.get("automatic_captions") or {}),
    }


def _safe_format(item):
    acodec = _safe_codec(item.get("acodec"))
    if (acodec is None and item.get("vcodec") == "none" and item.get("ext") == "mp4"
            and item.get("protocol") in ("m3u8", "m3u8_native")):
        # X identifies these standalone audio playlists in the CDN path.
        playlist = urllib.parse.urlsplit(item.get("url") or "")
        if playlist.hostname == "video.twimg.com" and "/mp4a/" in playlist.path:
            acodec = "mp4a"
    return {
        "format_id": str(item.get("format_id") or ""),
        "ext": str(item.get("ext") or ""),
        "height": item.get("height"),
        "width": item.get("width"),
        "fps": item.get("fps"),
        "vcodec": _safe_codec(item.get("vcodec")),
        "acodec": acodec,
        "filesize": item.get("filesize") or item.get("filesize_approx"),
        "tbr": item.get("tbr"),
    }


def _safe_codec(value):
    normalized = str(value).strip() if value is not None else ""
    return normalized or None


def _safe_subtitles(subtitles):
    safe = {}
    for language, entries in subtitles.items():
        safe[str(language)] = [{"ext": str(entry.get("ext") or "")} for entry in entries or []]
    return safe


def _emit_progress(progress_listener, event):
    if not progress_listener:
        return

    status = str(event.get("status") or "")
    downloaded = _as_int(event.get("downloaded_bytes"))
    total = _as_int(event.get("total_bytes") or event.get("total_bytes_estimate"))
    speed = _as_float(event.get("speed"))
    eta = _as_int(event.get("eta"))
    filename = event.get("filename") or event.get("tmpfilename") or ""
    percent = _progress_percent(event, downloaded, total)

    try:
        progress_listener.onProgress(status, percent, downloaded, total, speed, eta, str(filename))
    except BaseException as exc:
        detail = f"{exc.__class__.__name__}: {exc}"
        raise ProgressListenerException(f"progress listener exception: {detail}") from exc


def _is_explicit_format_id(value):
    selector_aliases = {
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
    }
    lowered = value.lower() if value else ""
    return bool(
        value
        and re.fullmatch(r"[A-Za-z0-9._-]+", value)
        and not any(lowered == alias or lowered.startswith(f"{alias}.") for alias in selector_aliases)
    )


def _is_explicit_subtitle_token(value):
    selector_aliases = {
        "best",
        "worst",
        "all",
    }
    lowered = value.lower() if value else ""
    return bool(
        value
        and re.fullmatch(r"[A-Za-z0-9._-]+", value)
        and not any(lowered == alias or lowered.startswith(f"{alias}.") for alias in selector_aliases)
    )


def _subtitle_available(info, language, ext, source):
    if not isinstance(info, dict):
        return False
    key = "subtitles" if source == "manual" else "automatic_captions"
    subtitles = info.get(key) or {}
    entries = subtitles.get(language) or []
    return any(str(entry.get("ext") or "").lower() == ext.lower() for entry in entries or [])


def _progress_percent(event, downloaded, total):
    status = str(event.get("status") or "")
    if status == "finished":
        return 100.0
    if total and downloaded is not None:
        return max(0.0, min(99.9, downloaded * 100.0 / total))

    raw_percent = str(event.get("_percent_str") or "").strip()
    match = re.search(r"([0-9]+(?:\.[0-9]+)?)", raw_percent)
    if match:
        return max(0.0, min(99.9, float(match.group(1))))
    return None


def _find_downloaded_file(info, output_dir, format_id=None, role=None):
    for candidate in _candidate_paths(info):
        if candidate and os.path.isfile(candidate) and _matches_split_download_name(candidate, info, format_id, role):
            return os.path.abspath(candidate)

    newest = None
    newest_mtime = -1
    for root, _, files in os.walk(output_dir):
        for name in files:
            if name.endswith(".part"):
                continue
            path = os.path.join(root, name)
            if not _matches_split_download_name(path, info, format_id, role):
                continue
            mtime = os.path.getmtime(path)
            if mtime > newest_mtime:
                newest = path
                newest_mtime = mtime
    return os.path.abspath(newest) if newest else ""


def _find_subtitle_file(info, output_dir, language, ext, source):
    for candidate in _candidate_paths(info):
        if candidate and os.path.isfile(candidate) and _matches_subtitle_download_name(candidate, info, language, ext, source):
            return os.path.abspath(candidate)

    newest = None
    newest_mtime = -1
    for root, _, files in os.walk(output_dir):
        for name in files:
            if name.endswith(".part"):
                continue
            path = os.path.join(root, name)
            if not _matches_subtitle_download_name(path, info, language, ext, source):
                continue
            mtime = os.path.getmtime(path)
            if mtime > newest_mtime:
                newest = path
                newest_mtime = mtime
    return os.path.abspath(newest) if newest else ""


def _matches_split_download_name(path, info, format_id, role):
    if not format_id and not role:
        return True
    if not format_id or not role or not isinstance(info, dict):
        return False
    video_id = str(info.get("id") or "")
    if not video_id:
        return False
    expected_prefix = f"download-{video_id}-{format_id}-{role}."
    return os.path.basename(path).startswith(expected_prefix)


def _matches_subtitle_download_name(path, info, language, ext, source):
    if not isinstance(info, dict):
        return False
    video_id = str(info.get("id") or "")
    if not video_id:
        return False
    name = os.path.basename(path)
    return name.startswith(f"download-{video_id}-subtitle-{source}.") and name.endswith(f".{language}.{ext}")


def _downloaded_file_size(path):
    if not path or not os.path.isfile(path):
        return 0
    return os.path.getsize(path)


def _candidate_paths(info):
    if not isinstance(info, dict):
        return []
    keys = ("filepath", "_filename", "filename")
    candidates = [info.get(key) for key in keys]
    for item in info.get("requested_downloads") or []:
        if isinstance(item, dict):
            candidates.extend(item.get(key) for key in keys)
    return candidates


def _as_int(value):
    try:
        if value is None:
            return None
        return int(value)
    except (TypeError, ValueError):
        return None


def _as_float(value):
    try:
        if value is None:
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def _error_category(exc):
    text = str(exc).lower()
    if "[twitter]" in text and "no video could be found in this tweet" in text:
        return "x_no_video"
    if "canceled" in text or "cancelled" in text or "取消" in text:
        return "canceled"
    # An HTTP response is a site response, not proof of lost connectivity.
    http_status = re.search(r"\bhttp\s*(?:(?:error|status)(?:\s+code)?)?\s*[:=]?\s*(\d{3})\b", text)
    if http_status:
        status = int(http_status.group(1))
        if status in (401, 403):
            return "permission"
        if status in (404, 410):
            return "unsupported"
        return "unknown"
    if (
        "no video formats found" in text
        or "format id" in text
        or "requested format is not available" in text
        or "role must be" in text
    ):
        return "unsupported"
    if (
        isinstance(exc, (ConnectionError, TimeoutError, socket.gaierror))
        or "timed out" in text
        or "timeout" in text
        or "remote end closed connection" in text
        or "unexpected_eof_while_reading" in text
        or "eof occurred in violation of protocol" in text
        or "connection reset" in text
        or "connection refused" in text
        or "connection aborted" in text
        or "name or service not known" in text
        or "name resolution" in text
        or "getaddrinfo failed" in text
        or "no route to host" in text
    ):
        return "network"
    if "unsupported" in text or "no suitable extractor" in text:
        return "unsupported"
    if "subtitle" in text or "subtitles" in text or "language" in text or "extension" in text:
        return "unsupported"
    if any(word in text for word in ("private", "sign in", "login", "permission", "forbidden", "unauthorized")):
        return "permission"
    if "extractor" in text or "parser" in text or "unable to extract" in text:
        return "parser"
    if "network" in text:
        return "network"
    return "unknown"


def _safe_error_message(exc):
    category = _error_category(exc)
    if category == "x_no_video":
        return "X 未返回这条帖子的视频。"
    if "no video formats found" in str(exc).lower():
        return "网站本次未返回可下载的视频格式，请稍后重新分析。"
    if category == "canceled":
        return "下载已取消。"
    if "subtitle" in str(exc).lower() or "subtitles" in str(exc).lower():
        return "没有可用的匹配字幕文件，请选择分析结果中的语言和格式。"
    if category == "network":
        return "网络连接失败，请稍后重试。"
    if category == "unsupported":
        return "暂不支持分析这个地址。"
    if category == "permission":
        return "需要授权访问，请确认账号权限或 cookies 文件。"
    if category == "parser":
        return "解析失败，请通过应用更新获取新版解析器后重试。"
    return "分析失败，请检查地址或稍后重试。"
