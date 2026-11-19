import json
from dataclasses import asdict, dataclass, fields
from pathlib import Path


@dataclass
class AppConfig:
    default_save_dir: str = ""
    cookies_path: str = ""
    ffmpeg_path: str = ""
    max_concurrency: int = 2
    check_ytdlp_updates_on_startup: bool = True
    active_ytdlp_path: str = ""
    active_ytdlp_version: str = ""
    rollback_ytdlp_path: str = ""
    emule_network: str = "all"
    emule_upload: int = 0
    emule_download: int = 0
    emule_directory: str = ""
    theme_mode: str = "light"
    color_preset: str = "morandi"
    jump_to_tasks: bool = True
    notify_completed: bool = True
    notify_failed: bool = True
    file_conflict: str = "rename"
    http_max_connections: int = 4
    http_limit_kib: int = 0
    max_waiting_downloads: int = 5
    resume_downloads: bool = True
    download_detected_subtitles: bool = False


class ConfigStore:
    def __init__(self, data_dir: Path):
        self.path = data_dir / "config.json"

    def load(self) -> AppConfig:
        if not self.path.exists():
            return AppConfig()
        try:
            payload = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError, UnicodeDecodeError):
            return AppConfig()
        if not isinstance(payload, dict):
            return AppConfig()

        allowed_fields = {field.name for field in fields(AppConfig)}
        clean_payload = {key: value for key, value in payload.items() if key in allowed_fields}
        return AppConfig(**{**asdict(AppConfig()), **clean_payload})

    def save(self, config: AppConfig) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(asdict(config), ensure_ascii=False, indent=2), encoding="utf-8")
