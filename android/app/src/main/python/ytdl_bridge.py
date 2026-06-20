import json
import os
import re

import yt_dlp


def analyze(url, cookies_path=None):
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
        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(url, download=False)
        return json.dumps(_to_result(info), ensure_ascii=False)
    except Exception as exc:  # yt-dlp has multiple extractor/downloader exception types.
        return json.dumps(
            {
                "ok": False,
                "errorCategory": _error_category(exc),
                "errorMessage": _safe_error_message(exc),
            },
            ensure_ascii=False,
        )


def download_single_file(url, output_dir, cookies_path=None, progress_listener=None):
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
        "continuedl": True,
        "nopart": False,
    }
    if cookies_path:
        options["cookiefile"] = cookies_path

    try:
        with yt_dlp.YoutubeDL(options) as ydl:
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


def download_format(url, output_dir, format_id, role, cookies_path=None, progress_listener=None):
    try:
        normalized_format_id = str(format_id or "").strip()
        normalized_role = str(role or "").strip()
        if not _is_explicit_format_id(normalized_format_id):
            raise ValueError("format id must be a single explicit id")
        if normalized_role not in ("video", "audio"):
            raise ValueError("role must be video or audio")

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
            "continuedl": True,
            "nopart": False,
        }
        if cookies_path:
            options["cookiefile"] = cookies_path

        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(url, download=True)
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


def _to_result(info):
    return {
        "ok": True,
        "title": info.get("title") or "",
        "duration": int(info.get("duration") or 0),
        "thumbnail": info.get("thumbnail") or "",
        "formats": [_safe_format(item) for item in info.get("formats") or []],
        "subtitles": _safe_subtitles(info.get("subtitles") or {}),
    }


def _safe_format(item):
    return {
        "format_id": str(item.get("format_id") or ""),
        "ext": str(item.get("ext") or ""),
        "height": item.get("height"),
        "width": item.get("width"),
        "fps": item.get("fps"),
        "vcodec": str(item.get("vcodec") or "none"),
        "acodec": str(item.get("acodec") or "none"),
        "filesize": item.get("filesize") or item.get("filesize_approx"),
        "tbr": item.get("tbr"),
    }


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
    except Exception:
        pass


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
    if "network" in text or "timed out" in text or "timeout" in text:
        return "network"
    if "unsupported" in text or "no suitable extractor" in text:
        return "unsupported"
    if "private" in text or "sign in" in text or "login" in text or "permission" in text:
        return "permission"
    if "format id" in text or "role" in text:
        return "unsupported"
    if "extractor" in text or "parser" in text or "unable to extract" in text:
        return "parser"
    return "unknown"


def _safe_error_message(exc):
    category = _error_category(exc)
    if category == "network":
        return "网络连接失败，请稍后重试。"
    if category == "unsupported":
        return "暂不支持分析这个地址。"
    if category == "permission":
        return "需要授权访问，请确认账号权限或 cookies 文件。"
    if category == "parser":
        return "解析失败，请通过应用更新获取新版解析器后重试。"
    return "分析失败，请检查地址或稍后重试。"
