from enum import Enum


PLAYLIST_EXPANSION_LIMIT = 50


class AnalysisFailureKind(str, Enum):
    LOGIN_REQUIRED = "login_required"
    UNAVAILABLE = "unavailable"
    NETWORK_TIMEOUT = "network_timeout"
    UNSUPPORTED_URL = "unsupported_url"
    PLAYLIST_CONFIRMATION_NEEDED = "playlist_confirmation_needed"
    UNKNOWN_YTDLP_FAILURE = "unknown_ytdlp_failure"
    CANCELED = "canceled"


def categorize_analysis_error(stderr: str) -> AnalysisFailureKind:
    text = stderr.lower()
    if "cancel" in text or "canceled" in text or "cancelled" in text:
        return AnalysisFailureKind.CANCELED
    if "playlist" in text and ("confirm" in text or "confirmation" in text or "entries" in text or "items" in text):
        return AnalysisFailureKind.PLAYLIST_CONFIRMATION_NEEDED
    if "sign in" in text or "login" in text or "age" in text:
        return AnalysisFailureKind.LOGIN_REQUIRED
    if "unavailable" in text or "private" in text or "removed" in text:
        return AnalysisFailureKind.UNAVAILABLE
    if "timed out" in text or "timeout" in text:
        return AnalysisFailureKind.NETWORK_TIMEOUT
    if "unsupported url" in text:
        return AnalysisFailureKind.UNSUPPORTED_URL
    return AnalysisFailureKind.UNKNOWN_YTDLP_FAILURE


def playlist_limit_message(accepted: int, skipped: int) -> str:
    return f"播放列表最多展开 {PLAYLIST_EXPANSION_LIMIT} 项；已加入 {accepted} 项，跳过 {skipped} 项。"
