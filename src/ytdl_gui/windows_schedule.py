import hashlib
import math
import os
import re
import subprocess
import sys
import tempfile
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path
from xml.sax.saxutils import escape

from ytdl_gui.subprocess_utils import hidden_window_kwargs


def _run_windows_tool(tool: str, *args: str) -> subprocess.CompletedProcess[bytes]:
    system_root = Path(os.environ.get("SystemRoot", ""))
    if not system_root.is_absolute():
        raise OSError("无法定位 Windows 系统目录")
    command = system_root / "System32" / tool
    try:
        return subprocess.run(
            [str(command), *args],
            stdin=subprocess.DEVNULL,
            capture_output=True,
            timeout=8,
            **hidden_window_kwargs(),
        )
    except subprocess.TimeoutExpired as exc:
        raise OSError(f"等待 {tool} 响应超时") from exc


def _current_user_sid() -> str:
    result = _run_windows_tool("whoami.exe", "/User", "/FO", "CSV", "/NH")
    sid = re.search(rb',"(S-1-[0-9-]+)"', result.stdout)
    if result.returncode or sid is None:
        raise OSError("无法获取当前 Windows 用户标识")
    return sid[1].decode("ascii")


def _run_schtasks(*args: str) -> subprocess.CompletedProcess[bytes]:
    return _run_windows_tool("schtasks.exe", *args, "/HRESULT")


def _task_xml(sid: str, profile: Path, executable: Path, next_run_at: float) -> str:
    # A short lead time keeps newly registered overdue triggers from being missed.
    start = datetime.fromtimestamp(math.ceil(max(next_run_at, time.time() + 5)), UTC)
    # The end boundary enables StartWhenAvailable for a one-time trigger.
    end = start + timedelta(days=365)
    args = subprocess.list2cmdline(["--scheduled", "--scheduled-profile", str(profile)])
    return f"""<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <Triggers>
    <TimeTrigger>
      <StartBoundary>{start.isoformat()}</StartBoundary>
      <EndBoundary>{end.isoformat()}</EndBoundary>
    </TimeTrigger>
  </Triggers>
  <Principals>
    <Principal>
      <UserId>{sid}</UserId>
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <StartWhenAvailable>true</StartWhenAvailable>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
  </Settings>
  <Actions>
    <Exec>
      <Command>{escape(str(executable))}</Command>
      <Arguments>{escape(args)}</Arguments>
    </Exec>
  </Actions>
</Task>
"""


def _task_name(sid: str, profile: Path) -> str:
    identity = f"{sid}\0{os.path.normcase(str(profile))}".encode("utf-8")
    return "\\CaptainFlash-Scheduled-" + hashlib.sha256(identity).hexdigest()[:24]


def sync_windows_schedule(profile: Path, next_run_at: float | None) -> None:
    """Register the profile's next Unix timestamp, or remove its wakeup."""
    if sys.platform != "win32" or not getattr(sys, "frozen", False):
        return

    profile = profile.resolve()
    sid = _current_user_sid()
    task = _task_name(sid, profile)
    if next_run_at is None:
        result = _run_schtasks("/Delete", "/TN", task, "/F")
        code = result.returncode & 0xFFFFFFFF
        if code not in {0, 0x80070002}:  # ERROR_FILE_NOT_FOUND
            raise OSError(f"无法删除 Windows 计划任务（0x{code:08X}）")
        return

    with tempfile.TemporaryDirectory(prefix="captainflash-schedule-") as directory:
        definition = Path(directory) / "task.xml"
        xml = _task_xml(sid, profile, Path(sys.executable).resolve(), next_run_at)
        definition.write_text(xml, encoding="utf-16")
        result = _run_schtasks("/Create", "/TN", task, "/XML", str(definition), "/F")
    if result.returncode:
        code = result.returncode & 0xFFFFFFFF
        raise OSError(f"无法注册 Windows 计划任务（0x{code:08X}）")
