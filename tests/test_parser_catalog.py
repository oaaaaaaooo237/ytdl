import hashlib
import io
import json
from pathlib import Path

import pytest

from ytdl_gui import parser_catalog as catalog
from ytdl_gui.ui.parser_dialog import ParserVersionDialog


def _exe(path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"yt-dlp")
    return path


def test_catalog_lists_bundled_downloaded_and_current_external_parser(tmp_path, monkeypatch):
    bundled = _exe(tmp_path / "dist" / "yt-dlp.exe")
    downloaded = _exe(tmp_path / "data" / "tools" / "yt-dlp" / "2026.04.30" / "yt-dlp.exe")
    external = _exe(tmp_path / "external" / "yt-dlp.exe")
    _exe(downloaded.parent.parent / "unrelated" / "yt-dlp.exe")
    monkeypatch.setattr(catalog, "probe_ytdlp_version", lambda path: "2026.04.01")

    versions = catalog.installed_parser_versions(tmp_path / "data", bundled, external, "2026.04.29")

    assert [(entry.path, entry.version, entry.source) for entry in versions] == [
        (bundled, "2026.04.01", "内置"),
        (downloaded, "2026.04.30", "已下载"),
        (external, "2026.04.29", "本机配置"),
    ]


def test_release_catalog_requires_official_assets_and_sorts_stable_versions(monkeypatch):
    def release(version, *, prerelease=False, forged=False):
        base = catalog.RELEASE_DOWNLOAD_BASE + "/" + version
        if forged:
            base = "https://example.invalid/" + version
        return {
            "tag_name": version, "draft": False, "prerelease": prerelease,
            "assets": [
                {"name": name, "browser_download_url": f"{base}/{name}"}
                for name in ("yt-dlp.exe", "SHA2-256SUMS")
            ],
        }

    payload = [
        release("2026.4.2"), release("2026.04.30"),
        release("2026.05.15", prerelease=True), release("2026.04.25", forged=True),
        release("../unsafe"),
    ]
    monkeypatch.setattr(catalog, "urlopen", lambda *_args, **_kwargs: io.BytesIO(json.dumps(payload).encode()))

    assert catalog.fetch_parser_releases() == ["2026.04.30", "2026.4.2"]


def test_install_validates_download_and_never_changes_active_configuration(tmp_path, monkeypatch):
    payload = b"official yt-dlp"
    checksum = f"{hashlib.sha256(payload).hexdigest()} *yt-dlp.exe\n".encode()
    config = tmp_path / "config.json"
    config.write_text('{"active_ytdlp_version": "2026.04.01"}', encoding="utf-8")
    monkeypatch.setattr(
        catalog, "urlopen", lambda request, **_kwargs: io.BytesIO(
            checksum if request.full_url.endswith("/SHA2-256SUMS") else payload
        )
    )
    monkeypatch.setattr(catalog, "probe_ytdlp_version", lambda _path: "2026.04.30")

    installed = catalog.download_parser_release(tmp_path, "2026.04.30")

    assert installed == tmp_path / "tools" / "yt-dlp" / "2026.04.30" / "yt-dlp.exe"
    assert installed.read_bytes() == payload
    assert "2026.04.01" in config.read_text(encoding="utf-8")
    assert not (tmp_path / "ytdlp-update-state.json").exists()
    with pytest.raises(FileExistsError):
        catalog.download_parser_release(tmp_path, "2026.04.30")


def test_bad_checksum_is_rejected_before_executable_version_probe(tmp_path, monkeypatch):
    checksum = f"{'0' * 64}  yt-dlp.exe\n".encode()
    monkeypatch.setattr(
        catalog, "urlopen", lambda request, **_kwargs: io.BytesIO(
            checksum if request.full_url.endswith("/SHA2-256SUMS") else b"invalid"
        )
    )

    def unexpected_probe(_path):
        pytest.fail("failed checksum must prevent execution")

    monkeypatch.setattr(catalog, "probe_ytdlp_version", unexpected_probe)

    with pytest.raises(ValueError, match="校验"):
        catalog.download_parser_release(tmp_path, "2026.04.30")

    assert not list(tmp_path.rglob("yt-dlp.exe"))


def test_delete_rejects_current_or_external_parser(tmp_path):
    bundled = _exe(tmp_path / "dist" / "yt-dlp.exe")
    managed = _exe(tmp_path / "tools" / "yt-dlp" / "2026.04.30" / "yt-dlp.exe")

    with pytest.raises(ValueError):
        catalog.delete_parser_version(tmp_path, managed, managed)
    with pytest.raises(ValueError):
        catalog.delete_parser_version(tmp_path, bundled, managed)
    assert catalog.delete_parser_version(tmp_path, managed, bundled) is None
    assert bundled.exists()
    assert not managed.exists()


def test_dialog_checks_and_downloads_without_applying_until_requested(qtbot, tmp_path, monkeypatch):
    bundled = _exe(tmp_path / "dist" / "yt-dlp.exe")
    data_dir = tmp_path / "data"
    downloaded = data_dir / "tools" / "yt-dlp" / "2026.04.30" / "yt-dlp.exe"
    from ytdl_gui.ui import parser_dialog

    actions = []
    current = (bundled, "2026.04.01")
    monkeypatch.setattr(catalog, "probe_ytdlp_version", lambda _: current[1])
    monkeypatch.setattr(parser_dialog, "fetch_parser_releases", lambda: ["2026.04.30"])
    monkeypatch.setattr(parser_dialog, "download_parser_release", lambda *_: _exe(downloaded))

    def apply(path, version):
        nonlocal current
        current = path, version
        actions.append(current)

    dialog = ParserVersionDialog(
        data_dir, bundled, get_active=lambda: current, apply_version=apply, can_change=lambda: True
    )
    qtbot.addWidget(dialog)
    qtbot.waitUntil(dialog.check_button.isEnabled)
    assert dialog.versions.count() == 1

    dialog.check_button.click()
    qtbot.waitUntil(lambda: dialog.versions.count() == 2)
    dialog.versions.setCurrentRow(1)
    assert dialog.download_button.isEnabled()
    dialog.download_button.click()
    qtbot.waitUntil(dialog.apply_button.isEnabled)
    assert downloaded.exists() and not actions

    dialog.apply_button.click()
    assert actions == [(downloaded, "2026.04.30")]
    assert not dialog.delete_button.isEnabled()


def test_release_catalog_uses_official_feed_on_rest_rate_limit(monkeypatch):
    from urllib.error import HTTPError

    feed = b"""<feed xmlns="http://www.w3.org/2005/Atom">
      <entry><link rel="alternate" href="https://github.com/yt-dlp/yt-dlp/releases/tag/2026.04.01"/></entry>
      <entry><link rel="alternate" href="https://github.com/yt-dlp/yt-dlp/releases/tag/2026.04.30"/></entry>
      <entry><link rel="alternate" href="https://github.com/yt-dlp/yt-dlp/releases/tag/2026.04.30.00123"/></entry>
      <entry><link rel="alternate" href="https://example.invalid/yt-dlp/yt-dlp/releases/tag/2026.05.01"/></entry>
    </feed>"""

    def open_release(request, **_kwargs):
        if request.full_url.startswith("https://api.github.com/"):
            raise HTTPError(request.full_url, 403, "rate limit exceeded", None, None)
        assert request.full_url == "https://github.com/yt-dlp/yt-dlp/releases.atom"
        return io.BytesIO(feed)

    monkeypatch.setattr(catalog, "urlopen", open_release)

    assert catalog.fetch_parser_releases() == ["2026.04.30", "2026.04.01"]
