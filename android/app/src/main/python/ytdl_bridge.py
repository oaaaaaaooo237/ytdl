import json

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


def _error_category(exc):
    text = str(exc).lower()
    if "network" in text or "timed out" in text or "timeout" in text:
        return "network"
    if "unsupported" in text or "no suitable extractor" in text:
        return "unsupported"
    if "private" in text or "sign in" in text or "login" in text or "permission" in text:
        return "permission"
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
