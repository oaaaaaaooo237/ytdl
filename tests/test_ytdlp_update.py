from pathlib import Path

import json
import socket
import subprocess

from ytdl_gui.update_manager import OFFICIAL_YTDLP_EXE_URL, UpdateManager, UpdateResult
from ytdl_gui.update_manager import probe_ytdlp_version
from ytdl_gui.ytdlp_runner import YtdlpCommandBuilder


def test_analysis_command_uses_timeout_cookies_and_no_playlist(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    cookies = tmp_path / "cookies.txt"
    builder = YtdlpCommandBuilder(exe)

    command = builder.analysis_command("https://www.youtube.com/watch?v=abc", cookies_path=cookies)

    assert command[:2] == [str(exe), "--dump-single-json"]
    assert "--no-playlist" in command
    assert "--cookies" in command
    assert str(cookies) in command


def test_playlist_probe_command_uses_flat_playlist_and_cookies(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    cookies = tmp_path / "cookies.txt"
    builder = YtdlpCommandBuilder(exe)

    command = builder.playlist_probe_command("https://www.youtube.com/playlist?list=abc", cookies_path=cookies)

    assert command[:2] == [str(exe), "--dump-single-json"]
    assert "--flat-playlist" in command
    assert "--cookies" in command
    assert str(cookies) in command


def test_download_command_uses_output_path_and_format(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    output = tmp_path / "%(title)s.%(ext)s"
    cookies = tmp_path / "cookies.txt"
    builder = YtdlpCommandBuilder(exe)

    command = builder.download_command("https://www.youtube.com/watch?v=abc", output, "22", cookies_path=cookies)

    assert "--newline" in command
    assert "--progress" in command
    assert "-f" in command
    assert "22" in command
    assert "-o" in command
    assert str(output) in command
    assert "--cookies" in command
    assert str(cookies) in command
    assert "--print" in command
    assert "after_move:filepath" in command


def test_download_command_can_write_subtitle_files(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    output = tmp_path / "%(title)s.%(ext)s"
    builder = YtdlpCommandBuilder(exe)

    command = builder.download_command(
        "https://www.youtube.com/watch?v=abc",
        output,
        "18",
        subtitle_action="file",
    )

    assert "--write-subs" in command
    assert "--write-auto-subs" in command
    assert "--sub-langs" in command
    assert "en.*" in command
    assert "all" not in command


def test_download_command_can_embed_subtitles_with_ffmpeg_location(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    output = tmp_path / "%(title)s.%(ext)s"
    ffmpeg = tmp_path / "ffmpeg.exe"
    builder = YtdlpCommandBuilder(exe)

    command = builder.download_command(
        "https://www.youtube.com/watch?v=abc",
        output,
        "18",
        subtitle_action="embed",
        ffmpeg_path=ffmpeg,
    )

    assert "--embed-subs" in command
    assert "--ffmpeg-location" in command
    assert str(ffmpeg) in command


def test_download_command_can_prepare_burn_subtitles_with_ffmpeg_location(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    output = tmp_path / "%(title)s.%(ext)s"
    ffmpeg = tmp_path / "ffmpeg.exe"
    builder = YtdlpCommandBuilder(exe)

    command = builder.download_command(
        "https://www.youtube.com/watch?v=abc",
        output,
        "18",
        subtitle_action="burn",
        ffmpeg_path=ffmpeg,
    )

    assert "--write-subs" in command
    assert "--write-auto-subs" in command
    assert "--sub-format" in command
    assert "srt/best" in command
    assert "en.*" in command
    assert "all" not in command
    assert "--convert-subs" in command
    assert "srt" in command
    assert "--embed-subs" not in command
    assert "--ffmpeg-location" in command
    assert str(ffmpeg) in command


def test_preview_url_command_uses_get_url_optional_format_and_cookies(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    cookies = tmp_path / "cookies.txt"
    builder = YtdlpCommandBuilder(exe)

    command = builder.preview_url_command("https://www.youtube.com/watch?v=abc", format_id="18", cookies_path=cookies)

    assert command[0] == str(exe)
    assert "-g" in command
    assert "-f" in command
    assert "18" in command
    assert "--cookies" in command
    assert str(cookies) in command


def test_probe_ytdlp_version_hides_child_console_window_on_windows(tmp_path: Path, monkeypatch):
    if not hasattr(subprocess, "CREATE_NO_WINDOW"):
        return
    captured_kwargs: dict = {}

    def fake_run(command, **kwargs):
        captured_kwargs.update(kwargs)
        return subprocess.CompletedProcess(command, 0, stdout="2026.03.17\n", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)

    assert probe_ytdlp_version(tmp_path / "yt-dlp.exe") == "2026.03.17"
    assert captured_kwargs["creationflags"] & subprocess.CREATE_NO_WINDOW


def test_update_switches_active_path_after_validation(tmp_path: Path):
    active = tmp_path / "active" / "yt-dlp.exe"
    active.parent.mkdir()
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        validated=True,
    )

    assert result == UpdateResult.UPDATED
    assert manager.state.active_version == "2026.05.30"
    assert manager.state.rollback_path == str(active)
    assert Path(manager.state.active_path).exists()


def test_update_busy_does_not_switch_active_path(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        tasks_running=True,
    )

    assert result == UpdateResult.BUSY
    assert manager.state.active_path == str(active)
    assert not manager.state_path.exists()


def test_update_rejects_missing_download(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(version="2026.05.30", downloaded_path=tmp_path / "missing.exe")

    assert result == UpdateResult.INVALID_DOWNLOAD
    assert manager.state.active_path == str(active)


def test_update_rejects_unvalidated_download_without_switching(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(version="2026.05.30", downloaded_path=_fake_exe(tmp_path / "new.exe"))

    assert result == UpdateResult.INVALID_DOWNLOAD
    assert manager.state.active_path == str(active)
    assert not manager.state_path.exists()


def test_update_rejects_download_when_validator_fails(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        validator=lambda _path, _version: False,
    )

    assert result == UpdateResult.INVALID_DOWNLOAD
    assert manager.state.active_path == str(active)


def test_update_rejects_unsafe_versions_without_copying_or_writing_state(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")

    for version in ["", "   ", "..", "..\\escape", "../escape", "2026.05.30:bad"]:
        manager = UpdateManager(data_dir=tmp_path, active_path=active)

        result = manager.accept_downloaded_update(
            version=version,
            downloaded_path=_fake_exe(tmp_path / f"new-{len(version)}.exe"),
            validated=True,
        )

        assert result == UpdateResult.INVALID_DOWNLOAD
        assert manager.state.active_path == str(active)
        assert not manager.state_path.exists()


def test_update_state_file_records_paths_version_and_source_url(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        validator=lambda _path, _version: True,
    )

    payload = json.loads(manager.state_path.read_text(encoding="utf-8"))
    assert result == UpdateResult.UPDATED
    assert payload["active_path"] == manager.state.active_path
    assert payload["active_version"] == "2026.05.30"
    assert payload["rollback_path"] == str(active)
    assert payload["source_url"] == OFFICIAL_YTDLP_EXE_URL


def test_rollback_restores_previous_active_path(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)
    manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        validated=True,
    )

    assert manager.rollback() is True

    assert manager.state.active_path == str(active)
    assert manager.state.rollback_path == ""


def test_rollback_fails_when_previous_path_is_missing(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)
    manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        validated=True,
    )
    active.unlink()

    assert manager.rollback() is False
    assert manager.state.active_path != str(active)


def test_rollback_fails_when_rollback_path_is_blank(tmp_path: Path):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")

    for rollback_path in ["", "  "]:
        manager = UpdateManager(data_dir=tmp_path, active_path=active)
        manager.state.rollback_path = rollback_path

        assert manager.rollback() is False
        assert manager.state.active_path == str(active)
        assert not manager.state_path.exists()


def test_update_manager_does_not_open_network_connections(tmp_path: Path, monkeypatch):
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")

    def fail_connect(*_args, **_kwargs):
        raise AssertionError("network access is not allowed in Task 4")

    monkeypatch.setattr(socket, "create_connection", fail_connect)
    manager = UpdateManager(data_dir=tmp_path, active_path=active)

    result = manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        validator=lambda _path, _version: True,
    )

    assert result == UpdateResult.UPDATED


def test_cookies_path_stays_in_command_args_not_update_state(tmp_path: Path):
    exe = tmp_path / "yt-dlp.exe"
    cookies = tmp_path / "secret" / "cookies.txt"
    cookies.parent.mkdir()
    builder = YtdlpCommandBuilder(exe)

    command = builder.analysis_command("https://www.youtube.com/watch?v=abc", cookies_path=cookies)
    active = tmp_path / "active.exe"
    active.write_text("old", encoding="utf-8")
    manager = UpdateManager(data_dir=tmp_path, active_path=active)
    manager.accept_downloaded_update(
        version="2026.05.30",
        downloaded_path=_fake_exe(tmp_path / "new.exe"),
        validated=True,
    )

    state_payload = manager.state_path.read_text(encoding="utf-8")
    assert str(cookies) in command
    assert str(cookies) not in state_payload


def _fake_exe(path: Path) -> Path:
    path.write_text("new", encoding="utf-8")
    return path
