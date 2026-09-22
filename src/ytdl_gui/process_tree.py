"""Windows processes enter a kill-on-close job before any application code runs."""
import ctypes
from ctypes import wintypes
import os
import subprocess
import threading
import time


class _Limits(ctypes.Structure):
    _fields_ = [('per_process', ctypes.c_longlong), ('per_job', ctypes.c_longlong),
                ('flags', wintypes.DWORD), ('min_ws', ctypes.c_size_t), ('max_ws', ctypes.c_size_t),
                ('active', wintypes.DWORD), ('affinity', ctypes.c_size_t),
                ('priority', wintypes.DWORD), ('scheduling', wintypes.DWORD)]


class _ExtendedLimits(ctypes.Structure):
    _fields_ = [('basic', _Limits), ('io', ctypes.c_ulonglong * 6),
                ('process_memory', ctypes.c_size_t), ('job_memory', ctypes.c_size_t),
                ('peak_process', ctypes.c_size_t), ('peak_job', ctypes.c_size_t)]


class _Accounting(ctypes.Structure):
    _fields_ = [('times', ctypes.c_longlong * 4), ('faults', wintypes.DWORD),
                ('total', wintypes.DWORD), ('active', wintypes.DWORD), ('terminated', wintypes.DWORD)]


class ManagedProcess:
    def __init__(self, command, cwd=None, **_kwargs):
        import _winapi
        import msvcrt
        self._api = _winapi
        self._lock = threading.Lock()
        self._kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        for name, args, result in [
            ('CreateJobObjectW', [ctypes.c_void_p, wintypes.LPCWSTR], wintypes.HANDLE),
            ('SetInformationJobObject', [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD], wintypes.BOOL),
            ('AssignProcessToJobObject', [wintypes.HANDLE, wintypes.HANDLE], wintypes.BOOL),
            ('TerminateJobObject', [wintypes.HANDLE, wintypes.UINT], wintypes.BOOL),
            ('QueryInformationJobObject', [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD, ctypes.c_void_p], wintypes.BOOL),
            ('ResumeThread', [wintypes.HANDLE], wintypes.DWORD),
            ('CloseHandle', [wintypes.HANDLE], wintypes.BOOL),
        ]:
            function = getattr(self._kernel, name)
            function.argtypes, function.restype = args, result
        self._job = self._kernel.CreateJobObjectW(None, None)
        if not self._job:
            raise ctypes.WinError(ctypes.get_last_error())
        self._handle = None
        self.stdout = None
        self.returncode = None
        read_fd, write_fd = os.pipe()
        null_fd = os.open(os.devnull, os.O_RDONLY)
        thread_handle = None
        try:
            limits = _ExtendedLimits()
            limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
            self._check(self._kernel.SetInformationJobObject(self._job, 9, ctypes.byref(limits), ctypes.sizeof(limits)))
            write_handle, null_handle = msvcrt.get_osfhandle(write_fd), msvcrt.get_osfhandle(null_fd)
            os.set_handle_inheritable(write_handle, True)
            os.set_handle_inheritable(null_handle, True)
            startup = subprocess.STARTUPINFO()
            startup.dwFlags = subprocess.STARTF_USESTDHANDLES
            startup.hStdInput, startup.hStdOutput, startup.hStdError = null_handle, write_handle, write_handle
            startup.lpAttributeList = {'handle_list': [null_handle, write_handle]}
            self._handle, thread_handle, self.pid, _tid = _winapi.CreateProcess(
                None, subprocess.list2cmdline(command), None, None, True,
                subprocess.CREATE_NO_WINDOW | 0x4, None, cwd, startup)
            self._check(self._kernel.AssignProcessToJobObject(self._job, self._handle))
            if self._kernel.ResumeThread(thread_handle) == 0xFFFFFFFF:
                raise ctypes.WinError(ctypes.get_last_error())
            self.stdout = os.fdopen(read_fd, 'r', encoding='utf-8', errors='replace')
            read_fd = None
        except BaseException:
            if self._handle:
                _winapi.TerminateProcess(self._handle, 1)
            self.close()
            raise
        finally:
            if thread_handle:
                _winapi.CloseHandle(thread_handle)
            if read_fd is not None:
                os.close(read_fd)
            os.close(write_fd)
            os.close(null_fd)

    @staticmethod
    def _check(ok):
        if not ok:
            raise ctypes.WinError(ctypes.get_last_error())

    def poll(self):
        if self.returncode is None and self._handle:
            if self._api.WaitForSingleObject(self._handle, 0) == 0:
                self.returncode = self._api.GetExitCodeProcess(self._handle)
        return self.returncode

    def terminate(self):
        with self._lock:
            if self._job:
                self._check(self._kernel.TerminateJobObject(self._job, 1))

    def wait(self, timeout=None):
        deadline = None if timeout is None else time.monotonic() + timeout
        while True:
            self.poll()
            accounting = _Accounting()
            self._check(self._kernel.QueryInformationJobObject(self._job, 1, ctypes.byref(accounting), ctypes.sizeof(accounting), None))
            if self.returncode is not None and accounting.active == 0:
                return self.returncode
            if deadline is not None and time.monotonic() >= deadline:
                raise subprocess.TimeoutExpired('下载进程树', timeout)
            time.sleep(.02)

    def close(self):
        with self._lock:
            if self._job:
                self._kernel.CloseHandle(self._job)
                self._job = None
            if self._handle:
                self._api.CloseHandle(self._handle)
                self._handle = None
        if self.stdout:
            self.stdout.close()
