LANGUAGE_ALIASES = {
    "en": ("en", "en-US", "en-GB"),
    "zh": ("zh", "zh-CN", "zh-TW", "zh-Hans", "zh-Hant"),
}


def choose_subtitle_language(subtitles: dict[str, list], priority: list[str]) -> str:
    actual_tags = {tag.casefold(): tag for tag in subtitles}
    for desired in priority:
        for tag in LANGUAGE_ALIASES.get(desired, (desired,)):
            actual_tag = actual_tags.get(tag.casefold())
            if actual_tag:
                return actual_tag
    return ""


def subtitle_action_requires_ffmpeg(action: str) -> bool:
    return action in {"embed", "burn"}
