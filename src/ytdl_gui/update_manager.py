import json
import shutil
import subprocess
import tempfile
from dataclasses import asdict, dataclass
from enum import Enum
from pathlib import Path
from typing import Callable
from urllib.request import urlretrieve

from ytdl_gui.subprocess_utils import hidden_window_kwargs


OFFICIAL_YTDLP_EXE_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
UNSAFE_VERSION_CHARS = set('<>:"|?*/\\')


class UpdateResult(str, Enum):
    UPDATED = "updated"
    INVALID_DOWNLOAD = "invalid_download"
    BUSY = "busy"


@dataclass
class UpdateState:
    active_path: str
    active_version: str = ""
    rollback_path: str = ""
    source_url: str = OFFICIAL_YTDLP_EXE_URL


@dataclass(frozen=True)
class UpdateOutcome:
    result: UpdateResult
    active_path: str = ""
    active_version: str = ""
    rollback_path: str = ""
    message: str = ""


def download_and_install_latest_ytdlp(data_dir: Path, active_path: Path, tasks_running: bool = False) -> UpdateOutcome:
    if tasks_running:
        return UpdateOutcome(UpdateResult.BUSY, active_path=str(active_path), message="有下载任务运行，稍后再更新。")

    data_dir.mkdir(parents=True, exist_ok=True)
    temp_dir = Path(tempfile.mkdtemp(prefix="ytdlp-update-", dir=str(data_dir)))
    downloaded_path = temp_dir / "yt-dlp.tmp.exe"
    try:
        urlretrieve(OFFICIAL_YTDLP_EXE_URL, downloaded_path)
        version = probe_ytdlp_version(downloaded_path)
        manager = UpdateManager(data_dir=data_dir, active_path=active_path)
        result = manager.accept_downloaded_update(version=version, downloaded_path=downloaded_path, validated=True)
        if result == UpdateResult.UPDATED:
            return UpdateOutcome(
                result=result,
                active_path=manager.state.active_path,
                active_version=manager.state.active_version,
                rollback_path=manager.state.rollback_path,
                message=f"已更新到 {manager.state.active_version}",
            )
        return UpdateOutcome(result=result, active_path=str(active_path), message="更新验证失败。")
    except (OSError, subprocess.SubprocessError, ValueError):
        return UpdateOutcome(UpdateResult.INVALID_DOWNLOAD, active_path=str(active_path), message="更新失败或下载文件无效。")
    finally:
        shutil.rmtree(temp_dir, ignore_errors=True)


def probe_ytdlp_version(executable: Path) -> str:
    result = subprocess.run(
        [str(executable), "--version"],
        capture_output=True,
        text=True,
        timeout=15,
        check=False,
        **hidden_window_kwargs(),
    )
    if result.returncode != 0:
        raise ValueError("yt-dlp version probe failed")
    version = (result.stdout or "").splitlines()[0].strip()
    if not version:
        raise ValueError("empty yt-dlp version")
    return version


class UpdateManager:
    def __init__(self, data_dir: Path, active_path: Path):
        self.data_dir = data_dir
        self.state_path = data_dir / "ytdlp-update-state.json"
        self.state = UpdateState(active_path=str(active_path))

    def accept_downloaded_update(
        self,
        version: str,
        downloaded_path: Path,
        tasks_running: bool = False,
        validator: Callable[[Path, str], bool] | None = None,
        validated: bool = False,
    ) -> UpdateResult:
        if tasks_running:
            return UpdateResult.BUSY
        if not self._version_is_safe(version):
            return UpdateResult.INVALID_DOWNLOAD
        if not self._download_is_valid(downloaded_path, version, validator, validated):
            return UpdateResult.INVALID_DOWNLOAD

        target_dir = self.data_dir / "tools" / "yt-dlp" / version
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / "yt-dlp.exe"
        if self._same_path(target, Path(self.state.active_path)):
            return UpdateResult.INVALID_DOWNLOAD

        shutil.copy2(downloaded_path, target)
        previous = self.state.active_path
        self.state = UpdateState(active_path=str(target), active_version=version, rollback_path=previous)
        self._save_state()
        return UpdateResult.UPDATED

    def rollback(self) -> bool:
        if not self.state.rollback_path.strip():
            return False
        rollback_path = Path(self.state.rollback_path)
        if not rollback_path.exists():
            return False
        self.state = UpdateState(active_path=str(rollback_path), active_version="", rollback_path="")
        self._save_state()
        return True

    def _download_is_valid(
        self,
        downloaded_path: Path,
        version: str,
        validator: Callable[[Path, str], bool] | None,
        validated: bool,
    ) -> bool:
        if not downloaded_path.exists() or not downloaded_path.is_file():
            return False
        if downloaded_path.stat().st_size <= 0:
            return False
        if validator:
            return bool(validator(downloaded_path, version))
        return validated

    @staticmethod
    def _version_is_safe(version: str) -> bool:
        if not version or version != version.strip():
            return False
        if ".." in version:
            return False
        return not any(char in UNSAFE_VERSION_CHARS for char in version)

    @staticmethod
    def _same_path(first: Path, second: Path) -> bool:
        try:
            return first.resolve() == second.resolve()
        except OSError:
            return first.absolute() == second.absolute()

    def _save_state(self) -> None:
        self.state_path.parent.mkdir(parents=True, exist_ok=True)
        self.state_path.write_text(json.dumps(asdict(self.state), ensure_ascii=False, indent=2), encoding="utf-8")
