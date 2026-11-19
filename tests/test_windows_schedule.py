import subprocess
from datetime import UTC, datetime
from pathlib import Path
from xml.etree import ElementTree

import pytest


def test_task_retains_absolute_time_and_quoted_paths(monkeypatch, tmp_path):
    from ytdl_gui.windows_schedule import _task_xml

    monkeypatch.setattr("ytdl_gui.windows_schedule.time.time", lambda: 1000)
    profile = tmp_path / "用户 & data"
    executable = tmp_path / "应用 & 安装" / "CaptainFlash.exe"
    task = ElementTree.fromstring(_task_xml("S-1-5-21-1", profile, executable, 2100.5))

    assert task.findtext(".//{*}StartBoundary") == datetime.fromtimestamp(2101, UTC).isoformat()
    assert task.findtext(".//{*}EndBoundary")
    assert task.findtext(".//{*}StartWhenAvailable") == "true"
    assert task.findtext(".//{*}UserId") == "S-1-5-21-1"
    assert task.findtext(".//{*}LogonType") == "InteractiveToken"
    assert task.findtext(".//{*}RunLevel") == "LeastPrivilege"
    assert task.findtext(".//{*}Command") == str(executable)
    assert task.findtext(".//{*}Arguments") == f'--scheduled --scheduled-profile "{profile}"'


def test_overdue_task_gets_a_future_trigger(monkeypatch, tmp_path):
    from ytdl_gui.windows_schedule import _task_xml

    monkeypatch.setattr("ytdl_gui.windows_schedule.time.time", lambda: 1000)
    task = ElementTree.fromstring(_task_xml("S-1-5-21-1", tmp_path, Path("CaptainFlash.exe"), 500))

    start = datetime.fromisoformat(task.findtext(".//{*}StartBoundary"))
    assert 1000 < start.timestamp() <= 1010


def test_rescheduling_overwrites_one_task_and_cancel_uses_same_identity(monkeypatch, tmp_path):
    from ytdl_gui import windows_schedule

    monkeypatch.setattr(windows_schedule.sys, "platform", "win32")
    monkeypatch.setattr(windows_schedule.sys, "frozen", True, raising=False)
    monkeypatch.setattr(windows_schedule, "_current_user_sid", lambda: "S-1-5-21-1")
    operations = []

    def run(*args):
        operations.append(args)
        if args[0] == "/Create":
            assert Path(args[args.index("/XML") + 1]).is_file()
            return subprocess.CompletedProcess(args, 0, b"", b"")
        return subprocess.CompletedProcess(args, 0x80070002, b"", b"")

    monkeypatch.setattr(windows_schedule, "_run_schtasks", run)
    windows_schedule.sync_windows_schedule(tmp_path / "A", 2000)
    windows_schedule.sync_windows_schedule(tmp_path / "A", 3000)
    windows_schedule.sync_windows_schedule(tmp_path / "B", 3000)
    windows_schedule.sync_windows_schedule(tmp_path / "A", None)

    task_names = [args[args.index("/TN") + 1] for args in operations]
    assert task_names[0] == task_names[1] == task_names[3]
    assert task_names[2] != task_names[0]
    assert all("/F" in args for args in operations)
    assert not any(Path(args[args.index("/XML") + 1]).exists() for args in operations[:3])


def test_windows_source_development_avoids_registering_the_python_executable(monkeypatch, tmp_path):
    from ytdl_gui import windows_schedule

    monkeypatch.setattr(windows_schedule.sys, "platform", "win32")
    monkeypatch.delattr(windows_schedule.sys, "frozen", raising=False)
    monkeypatch.setattr(windows_schedule, "_current_user_sid", lambda: pytest.fail("Queried Windows identity"))

    windows_schedule.sync_windows_schedule(tmp_path, 2000)


def test_missing_windows_system_root_reports_os_error(monkeypatch):
    from ytdl_gui.windows_schedule import _run_windows_tool

    monkeypatch.delenv("SystemRoot", raising=False)
    with pytest.raises(OSError):
        _run_windows_tool("schtasks.exe")


def test_windows_scheduler_timeout_reports_os_error(monkeypatch, tmp_path):
    from ytdl_gui.windows_schedule import _run_windows_tool

    def timed_out(command, **kwargs):
        raise subprocess.TimeoutExpired(command, kwargs["timeout"])

    monkeypatch.setenv("SystemRoot", str(tmp_path))
    monkeypatch.setattr(subprocess, "run", timed_out)
    with pytest.raises(OSError):
        _run_windows_tool("schtasks.exe", "/Query")
