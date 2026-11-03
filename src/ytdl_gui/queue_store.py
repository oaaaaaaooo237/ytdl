"""Atomic, current-user encrypted resumable queue. No extracted media URLs."""
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import re
from uuid import uuid4

from PySide6.QtCore import QObject, Signal, Slot

from ytdl_gui.workers import DownloadRequest


class _Blob(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]


def _protect(data: bytes, decrypt=False) -> bytes:
    buffer = ctypes.create_string_buffer(data)
    source = _Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    target = _Blob()
    crypt = ctypes.WinDLL('crypt32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    function = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
    function.argtypes = [ctypes.POINTER(_Blob), ctypes.c_void_p, ctypes.c_void_p,
                         ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(_Blob)]
    function.restype = wintypes.BOOL
    if not function(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(target)):
        raise OSError('无法读取或保护本用户的恢复队列。')
    try:
        return ctypes.string_at(target.data, target.size)
    finally:
        kernel.LocalFree(target.data)


class QueueStore:
    def __init__(self, data_dir: Path):
        self.path = Path(data_dir) / 'resume-queue.bin'
        self.tasks_root = Path(data_dir) / 'download-tasks'

    def task_dir(self, task_id):
        self._validate_id(task_id)
        path = self.tasks_root / task_id
        for candidate in [self.tasks_root, path]:
            if candidate.is_symlink() or candidate.is_junction():
                raise ValueError('任务临时目录不能是链接。')
        return path

    @staticmethod
    def _validate_id(task_id):
        if not isinstance(task_id, str) or not re.fullmatch('[0-9a-f]{32}', task_id):
            raise ValueError('任务 ID 无效。')

    def save(self, tasks):
        items = []
        for task_id, (request, context, state) in tasks.items():
            self._validate_id(task_id)
            if state in {'finished', 'canceled', 'cancel_requested'}:
                continue
            items.append({'task_id': task_id, 'state': state, 'url': request.url,
                          'output_template': str(request.output_template.absolute()),
                          'format_id': request.format_id, 'subtitle_action': request.subtitle_action,
                          'expected_title': request.expected_title,
                          'uses_cookies': request.uses_cookies or request.cookies_path is not None,
                          'context': {key: context[key] for key in
                                      ('title', 'format_summary', 'download_type', 'subtitle_behavior', 'progress', 'error')
                                      if key in context}})
        encrypted = _protect(json.dumps({'version': 1, 'tasks': items}, ensure_ascii=False).encode('utf-8'))
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix('.' + uuid4().hex + '.tmp')
        try:
            with temporary.open('xb') as output:
                output.write(encrypted)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, self.path)
        finally:
            temporary.unlink(missing_ok=True)

    def load(self):
        if not self.path.exists():
            return {}
        payload = json.loads(_protect(self.path.read_bytes(), decrypt=True))
        if payload.get('version') != 1 or not isinstance(payload.get('tasks'), list):
            raise ValueError('恢复队列版本无效。')
        restored = {}
        for item in payload['tasks']:
            task_id = item['task_id']
            self._validate_id(task_id)
            if task_id in restored:
                raise ValueError('恢复队列包含重复任务。')
            request = DownloadRequest(url=item['url'], ytdlp_path=Path('yt-dlp.exe'),
                                      output_template=Path(item['output_template']), format_id=item['format_id'],
                                      subtitle_action=item['subtitle_action'], expected_title=item['expected_title'],
                                      uses_cookies=item['uses_cookies'], temp_dir=self.task_dir(task_id))
            restored[task_id] = {'request': request, 'context': item['context'],
                                 'state': 'failed' if item['state'] == 'failed' else 'paused'}
        return restored


class QueueRecoveryWorker(QObject):
    finished = Signal(dict)
    failed = Signal(str)

    def __init__(self, store):
        super().__init__()
        self.store = store

    @Slot()
    def run(self):
        try:
            self.finished.emit(self.store.load())
        except (OSError, ValueError, KeyError, TypeError):
            self.failed.emit('恢复队列读取失败，原文件已保留；请检查本机用户与数据目录后重启。')
