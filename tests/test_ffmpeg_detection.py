import os
import subprocess
from pathlib import Path

import ytdl_gui.ffmpeg as ffmpeg_module
from ytdl_gui.ffmpeg import FfmpegStatus, ffmpeg_help_url, find_ffmpeg, media_capability_tier


def test_missing_ffmpeg_is_baseline_only(tmp_path: Path):
    status = find_ffmpeg(search_paths=[tmp_path], env_path="")

    assert status.found is False
    assert media_capability_tier(status) == "baseline"
    assert "ffmpeg.org" in ffmpeg_help_url()


def test_configured_ffmpeg_path_wins(tmp_path: Path):
    exe = tmp_path / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(configured_path=exe, search_paths=[], env_path="")

    assert status == FfmpegStatus(found=True, path=exe, version="未检测版本")
    assert media_capability_tier(status) == "enhanced"


def test_adjacent_bin_search(tmp_path: Path):
    exe = tmp_path / "ffmpeg" / "bin" / "ffmpeg.exe"
    exe.parent.mkdir(parents=True)
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(search_paths=[tmp_path], env_path="")

    assert status.found is True
    assert status.path == exe


def test_path_search_finds_ffmpeg_exe(tmp_path: Path):
    exe = tmp_path / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(search_paths=[], env_path=str(tmp_path))

    assert status.found is True
    assert status.path == exe
    assert status.version == "未检测版本"


def test_configured_path_must_be_a_file(tmp_path: Path):
    configured_dir = tmp_path / "ffmpeg.exe"
    configured_dir.mkdir()

    status = find_ffmpeg(configured_path=configured_dir, search_paths=[], env_path="")

    assert status == FfmpegStatus(found=False, path=None, version="")


def test_version_probe_uses_stdout_first_line(tmp_path: Path, monkeypatch):
    exe = tmp_path / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")

    def fake_run(command, **kwargs):
        assert command == [str(exe), "-version"]
        assert kwargs["timeout"] == 1.0
        return subprocess.CompletedProcess(command, 0, stdout="ffmpeg version 6.1\nbuilt with gcc", stderr="")

    monkeypatch.setattr(subprocess, "run", fake_run)

    status = find_ffmpeg(configured_path=exe, search_paths=[], env_path="")

    assert status == FfmpegStatus(found=True, path=exe, version="ffmpeg version 6.1")


def test_version_probe_timeout_falls_back_to_unknown(tmp_path: Path, monkeypatch):
    exe = tmp_path / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")

    def fake_run(command, **kwargs):
        raise subprocess.TimeoutExpired(command, timeout=kwargs["timeout"])

    monkeypatch.setattr(subprocess, "run", fake_run)

    status = find_ffmpeg(configured_path=exe, search_paths=[], env_path="")

    assert status == FfmpegStatus(found=True, path=exe, version="未检测版本")


def test_duplicate_candidates_are_probed_once(tmp_path: Path, monkeypatch):
    exe = tmp_path / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")
    probed: list[Path] = []

    def fake_probe(path: Path, timeout_seconds: float):
        probed.append(path)
        return "ffmpeg version test"

    monkeypatch.setattr(ffmpeg_module, "_probe_version", fake_probe)

    status = find_ffmpeg(configured_path=exe, search_paths=[tmp_path], env_path=str(tmp_path))

    assert status == FfmpegStatus(found=True, path=exe, version="ffmpeg version test")
    assert probed == [exe]


def test_path_search_parses_multiple_segments(tmp_path: Path):
    first = tmp_path / "first"
    second = tmp_path / "second"
    first.mkdir()
    second.mkdir()
    exe = second / "ffmpeg.exe"
    exe.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(search_paths=[], env_path=f"{first}{os.pathsep}{second}")

    assert status.found is True
    assert status.path == exe


def test_configured_path_wins_over_path(tmp_path: Path):
    configured = tmp_path / "configured" / "ffmpeg.exe"
    path_candidate = tmp_path / "path" / "ffmpeg.exe"
    configured.parent.mkdir()
    path_candidate.parent.mkdir()
    configured.write_text("fake", encoding="utf-8")
    path_candidate.write_text("fake", encoding="utf-8")

    status = find_ffmpeg(configured_path=configured, search_paths=[], env_path=str(path_candidate.parent))

    assert status.path == configured
