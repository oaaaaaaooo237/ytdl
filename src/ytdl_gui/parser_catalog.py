"""Catalog of stable Windows yt-dlp executables."""
import hashlib
import json
import re
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from time import monotonic
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from xml.etree import ElementTree

from ytdl_gui.update_manager import OFFICIAL_YTDLP_EXE_URL, probe_ytdlp_version


_RELEASES_PAGE = OFFICIAL_YTDLP_EXE_URL.split("/latest/", 1)[0]
RELEASE_DOWNLOAD_BASE = _RELEASES_PAGE + "/download"
_RELEASES_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases?per_page=20"
_VERSION = re.compile(r"[0-9]{1,4}(?:\.[0-9]{1,4}){2}")
_EXECUTABLE = "yt-dlp.exe"
_CHECKSUMS = "SHA2-256SUMS"


@dataclass(frozen=True)
class LocalParserVersion:
    path: Path
    version: str
    source: str
    available: bool


def parser_version_key(version: str) -> tuple[int, ...]:
    return tuple(map(int, version.split("."))) if _VERSION.fullmatch(version) else ()


def _versions_root(data_dir: Path) -> Path:
    return (data_dir / "tools" / "yt-dlp").resolve()


def _managed_path(root: Path, path: Path) -> bool:
    version = path.parent.name
    expected = root / version / _EXECUTABLE
    return bool(
        _VERSION.fullmatch(version)
        and path.absolute() == expected
        and path.resolve() == expected
    )


def installed_parser_versions(
    data_dir: Path,
    bundled_path: Path,
    active_path: Path,
    active_version: str,
    *,
    bundled_version: str | None = None,
) -> list[LocalParserVersion]:
    if bundled_version is None:
        bundled_version = active_version if bundled_path.resolve() == active_path.resolve() else ""
        if not bundled_version and bundled_path.is_file():
            try:
                bundled_version = probe_ytdlp_version(bundled_path)
            except (OSError, subprocess.SubprocessError, ValueError, IndexError):
                bundled_version = ""
    versions = [LocalParserVersion(bundled_path, bundled_version, "内置", bundled_path.is_file())]

    root = _versions_root(data_dir)
    paths = (
        path for path in root.glob(f"*/{_EXECUTABLE}")
        if _managed_path(root, path) and path.is_file() and path.stat().st_size
    )
    for path in sorted(paths, key=lambda path: parser_version_key(path.parent.name), reverse=True):
        versions.append(LocalParserVersion(path, path.parent.name, "已下载", True))

    if all(entry.path.resolve() != active_path.resolve() for entry in versions):
        versions.append(LocalParserVersion(active_path, active_version, "本机配置", active_path.is_file()))
    return versions


def _read_url(url: str, max_bytes: int, total_timeout: int, *, github_api: bool = False):
    headers = {"User-Agent": "CaptainFlash"}
    if github_api:
        headers["Accept"] = "application/vnd.github+json"
    started = monotonic()
    total = 0
    with urlopen(Request(url, headers=headers), timeout=8) as response:
        while True:
            chunk = response.read1(256 * 1024)
            if monotonic() - started > total_timeout:
                raise TimeoutError("解析器请求超时。")
            if not chunk:
                return
            total += len(chunk)
            if total > max_bytes:
                raise ValueError("解析器文件超出大小限制。")
            yield chunk


def _feed_versions() -> list[str]:
    feed = b"".join(_read_url(f"{_RELEASES_PAGE}.atom", 4 * 1024**2, 20))
    root = ElementTree.fromstring(feed)
    tag_prefix = f"{_RELEASES_PAGE}/tag/"
    versions = set()
    for link in root.findall("atom:entry/atom:link", {"atom": "http://www.w3.org/2005/Atom"}):
        url = link.get("href", "")
        if link.get("rel") == "alternate" and url.startswith(tag_prefix):
            version = url.removeprefix(tag_prefix)
            if _VERSION.fullmatch(version):
                versions.add(version)
    return sorted(versions, key=parser_version_key, reverse=True)


def fetch_parser_releases() -> list[str]:
    try:
        payload = json.loads(b"".join(_read_url(_RELEASES_URL, 4 * 1024**2, 20, github_api=True)))
    except HTTPError as error:
        if error.code not in (403, 429):
            raise
        return _feed_versions()

    if not isinstance(payload, list):
        raise ValueError("解析器版本列表无效。")

    versions = set()
    for release in payload:
        if not isinstance(release, dict) or release.get("draft") or release.get("prerelease"):
            continue
        version = release.get("tag_name")
        assets = release.get("assets")
        if not isinstance(version, str) or not _VERSION.fullmatch(version) or not isinstance(assets, list):
            continue
        base = f"{RELEASE_DOWNLOAD_BASE}/{version}"
        available = {
            asset.get("name") for asset in assets
            if isinstance(asset, dict) and asset.get("name") in (_EXECUTABLE, _CHECKSUMS)
            and asset.get("browser_download_url") == f"{base}/{asset['name']}"
        }
        if available == {_EXECUTABLE, _CHECKSUMS}:
            versions.add(version)
    return sorted(versions, key=parser_version_key, reverse=True)


def download_parser_release(data_dir: Path, version: str) -> Path:
    if not _VERSION.fullmatch(version):
        raise ValueError("解析器版本无效。")
    root = _versions_root(data_dir)
    target = root / version / _EXECUTABLE
    if not _managed_path(root, target):
        raise ValueError("解析器存储路径无效。")
    if target.exists():
        raise FileExistsError("此解析器版本已下载。")

    base = f"{RELEASE_DOWNLOAD_BASE}/{version}"
    checksums = b"".join(_read_url(f"{base}/{_CHECKSUMS}", 128 * 1024, 20)).decode("ascii")
    expected = re.search(r"^([0-9a-fA-F]{64}) [ *]yt-dlp\.exe\r?$", checksums, re.MULTILINE)
    if expected is None:
        raise ValueError("缺少解析器校验值。")

    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="download-", dir=root) as temp:
        staged = Path(temp) / _EXECUTABLE
        digest = hashlib.sha256()
        with staged.open("wb") as output:
            for chunk in _read_url(f"{base}/{_EXECUTABLE}", 96 * 1024**2, 180):
                output.write(chunk)
                digest.update(chunk)
        if not staged.stat().st_size or digest.hexdigest() != expected.group(1).lower():
            raise ValueError("解析器文件校验失败。")
        if probe_ytdlp_version(staged) != version:
            raise ValueError("解析器文件版本与选定版本不一致。")
        target.parent.mkdir(exist_ok=True)
        if not _managed_path(root, target):
            raise ValueError("解析器存储路径无效。")
        if target.exists():
            raise FileExistsError("此解析器版本已下载。")
        staged.rename(target)
    return target


def delete_parser_version(data_dir: Path, path: Path, active_path: Path) -> None:
    if not _managed_path(_versions_root(data_dir), path) or path.resolve() == active_path.resolve():
        raise ValueError("只能删除当前未使用的已下载解析器。")
    path.unlink()
