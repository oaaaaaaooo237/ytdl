import math
from dataclasses import dataclass, field


@dataclass(frozen=True)
class FormatPreference:
    resolution: int | None = None
    container: str = "mp4"
    fps: int | None = None
    codec: str | None = None
    video_bitrate: int | None = None
    audio_bitrate: int | None = None
    download_mode: str = "audio_video"


@dataclass(frozen=True)
class FormatChoice:
    format_id: str
    actual_summary: str
    relaxed: list[str] = field(default_factory=list)


def choose_format(formats: list[dict], preference: FormatPreference) -> FormatChoice:
    candidates = [item for item in formats if _matches_download_mode(item, preference.download_mode)]
    if not candidates:
        if preference.download_mode == "audio_only":
            raise ValueError("没有可用的音频格式")
        if preference.download_mode == "video_only":
            raise ValueError("没有可用的视频格式")
        raise ValueError("没有可用的单文件格式")

    selected = min(candidates, key=lambda item: _preference_penalty(item, preference))
    relaxed = _relaxed_preferences(selected, preference)
    format_id = selected.get("format_id")
    if not format_id:
        raise ValueError("所选格式缺少 format_id，无法创建下载任务")

    return FormatChoice(str(format_id), _summary(selected), relaxed)


def _preference_penalty(item: dict, preference: FormatPreference) -> tuple:
    height = _number(item.get("height"))
    fps = _number(item.get("fps"))
    vbr = _number(item.get("vbr") or item.get("tbr"))
    abr = _number(item.get("abr"))

    resolution_penalty = abs(height - preference.resolution) if preference.resolution is not None else -height
    fps_penalty = abs(fps - preference.fps) if preference.fps is not None else -fps
    codec_penalty = 0 if _codec_matches(item, preference.codec) else 1
    video_bitrate_penalty = abs(vbr - preference.video_bitrate) if preference.video_bitrate is not None else 0
    audio_bitrate_penalty = abs(abr - preference.audio_bitrate) if preference.audio_bitrate is not None else 0
    container_penalty = 0 if _normalized(item.get("ext")) == _normalized(preference.container) else 1

    if preference.download_mode == "audio_only":
        return (
            audio_bitrate_penalty if preference.audio_bitrate is not None else -abr,
            container_penalty,
            -abr,
        )

    return (
        resolution_penalty,
        fps_penalty,
        codec_penalty,
        video_bitrate_penalty,
        audio_bitrate_penalty,
        container_penalty,
        -height,
        -fps,
    )


def _relaxed_preferences(item: dict, preference: FormatPreference) -> list[str]:
    relaxed: list[str] = []
    if preference.resolution is not None and _number(item.get("height")) != preference.resolution:
        relaxed.append("resolution")
    if preference.fps is not None and _number(item.get("fps")) != preference.fps:
        relaxed.append("fps")
    if preference.codec is not None and not _codec_matches(item, preference.codec):
        relaxed.append("codec")
    if preference.video_bitrate is not None and _number(item.get("vbr") or item.get("tbr")) != preference.video_bitrate:
        relaxed.append("video_bitrate")
    if preference.audio_bitrate is not None and _number(item.get("abr")) != preference.audio_bitrate:
        relaxed.append("audio_bitrate")
    if preference.container and _normalized(item.get("ext")) != _normalized(preference.container):
        relaxed.append("container")
    return relaxed


def _codec_matches(item: dict, codec: str | None) -> bool:
    if not codec:
        return True
    desired = _normalized(codec)
    codecs = (_normalized(item.get("vcodec")), _normalized(item.get("acodec")))
    return any(desired in candidate or candidate in desired for candidate in codecs if candidate)


def _has_audio_video_codecs(item: dict) -> bool:
    vcodec = _normalized(item.get("vcodec"))
    acodec = _normalized(item.get("acodec"))
    return bool(vcodec and acodec and vcodec != "none" and acodec != "none")


def _has_audio_only_codecs(item: dict) -> bool:
    vcodec = _normalized(item.get("vcodec"))
    acodec = _normalized(item.get("acodec"))
    return bool(acodec and acodec != "none" and (not vcodec or vcodec == "none"))


def _has_video_only_codecs(item: dict) -> bool:
    vcodec = _normalized(item.get("vcodec"))
    acodec = _normalized(item.get("acodec"))
    return bool(vcodec and vcodec != "none" and (not acodec or acodec == "none"))


def _matches_download_mode(item: dict, mode: str) -> bool:
    if mode == "audio_only":
        return _has_audio_only_codecs(item)
    if mode == "video_only":
        return _has_video_only_codecs(item)
    return _has_audio_video_codecs(item)


def _number(value: object) -> int:
    if isinstance(value, int | float):
        return int(value) if math.isfinite(value) else 0
    if isinstance(value, str):
        try:
            number = float(value)
        except ValueError:
            return 0
        return int(number) if math.isfinite(number) else 0
    return 0


def _normalized(value: object) -> str:
    return str(value or "").casefold()


def _summary(item: dict) -> str:
    vcodec = item.get("vcodec") or "unknown"
    acodec = item.get("acodec") or "unknown"
    ext = item.get("ext") or "unknown"
    if _normalized(vcodec) == "none" and _normalized(acodec) != "none":
        abr = item.get("abr") or item.get("tbr") or "unknown"
        return f"音频 {ext} {acodec} {abr}kbps"

    height = item.get("height") or "unknown"
    fps = item.get("fps") or "unknown"
    if _normalized(acodec) == "none" and _normalized(vcodec) != "none":
        return f"{height}p {ext} {vcodec}/无音频 {fps}fps"
    return f"{height}p {ext} {vcodec}/{acodec} {fps}fps"
