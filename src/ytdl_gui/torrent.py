"""Run each Windows BitTorrent download in a private, local aria2 process."""

import base64
from dataclasses import dataclass
import hashlib
import http.client
import json
import os
from pathlib import Path, PureWindowsPath
import re
import secrets
import shutil
import socket
import subprocess
import threading
import time
from urllib.parse import parse_qsl, urlsplit

from PySide6.QtCore import QObject, Signal, Slot

from ytdl_gui.direct_file import DirectFileError, _open

from ytdl_gui.paths import resource_root
from ytdl_gui.process_tree import ManagedProcess


_MAX_TORRENT_BYTES = 16 * 1024 * 1024
_POLL_SECONDS = 0.4
_STATUS_FIELDS = ['status', 'downloadSpeed', 'files', 'errorCode']


class TorrentError(Exception):
    pass


class _TransferInterrupted(Exception):
    pass


@dataclass(frozen=True)
class TorrentFile:
    index: int
    path: str
    length: int


@dataclass(frozen=True)
class TorrentRequest:
    source: str
    output_dir: Path
    state_dir: Path
    selected_files: tuple[int, ...] = ()
    aria2_path: Path | None = None


def find_aria2() -> Path | None:
    bundled = resource_root() / 'tools' / 'aria2' / 'aria2c.exe'
    if bundled.is_file():
        return bundled
    on_path = shutil.which('aria2c')
    return Path(on_path).resolve() if on_path else None


def torrent_partial_dir(output_dir: Path, state_dir: Path) -> Path:
    key = hashlib.sha256(os.fsencode(state_dir.resolve())).hexdigest()[:24]
    return output_dir.resolve() / f'.captainflash-torrent-{key}'


def retained_torrent_stage(state_dir: Path) -> Path | None:
    try:
        destination = Path(json.loads((state_dir / "stage.json").read_bytes()))
        stage = torrent_partial_dir(destination, state_dir)
        owner = stage / ".captainflash-owner"
        if stage.is_dir() and stage.resolve() == stage and not owner.is_symlink():
            if owner.read_text(encoding="utf-8") == state_dir.name:
                return stage
    except (OSError, ValueError, TypeError):
        return None
    return None


def _btih(magnet: str) -> str:
    hashes = set()
    for key, value in parse_qsl(urlsplit(magnet).query):
        if key != 'xt' or not value.lower().startswith('urn:btih:'):
            continue
        raw = value[9:]
        if re.fullmatch(r'[0-9a-fA-F]{40}', raw):
            hashes.add(raw.lower())
        elif re.fullmatch(r'[A-Z2-7a-z]{32}', raw):
            hashes.add(base64.b32decode(raw.upper()).hex())
    if len(hashes) != 1:
        raise TorrentError('磁力链接需要包含一个有效的 BitTorrent v1 文件哈希。')
    return hashes.pop()


def _valid_path_component(name: str) -> bool:
    return not (
        name.endswith(('.', ' ')) or name.casefold().startswith('.captainflash-torrent-')
        or PureWindowsPath(name).is_reserved() or re.search(r'[<>:"\\|?*\x00-\x1f]', name)
    )


def _files_for_picker(entries: list[dict], stage: Path) -> tuple[TorrentFile, ...]:
    files = []
    seen = set()
    for entry in entries:
        path = Path(entry['path'])
        try:
            relative = path.relative_to(stage)
        except ValueError:
            raise TorrentError('种子包含超出下载目录的文件路径。') from None
        if not relative.parts or not all(_valid_path_component(part) for part in relative.parts):
            raise TorrentError('种子包含 Windows 不支持的文件名。')
        if path.resolve() != path:
            raise TorrentError('种子的下载路径包含重定向链接。')
        key = relative.as_posix().casefold()
        if key in seen:
            raise TorrentError('种子包含重复的文件路径。')
        seen.add(key)
        files.append(TorrentFile(int(entry['index']), relative.as_posix(), int(entry['length'])))
    if not files or len({Path(file.path).parts[0].casefold() for file in files}) != 1:
        raise TorrentError('种子没有有效的文件目录。')
    if any(parent.as_posix().casefold() in seen for file in files for parent in Path(file.path).parents):
        raise TorrentError('种子中有同时用作文件和目录的路径。')
    return tuple(files)


def _progress(status: dict) -> dict:
    files = [entry for entry in status.get('files', []) if entry.get('selected') == 'true']
    total = sum(int(entry['length']) for entry in files)
    downloaded = sum(int(entry['completedLength']) for entry in files)
    speed = int(status.get('downloadSpeed', 0))
    completed = status.get('status') == 'complete'
    eta = max(0, (total - downloaded + speed - 1) // speed) if speed else (0 if completed else None)
    return {
        'downloaded_bytes': downloaded,
        'total_bytes': total,
        'speed_bytes_per_second': speed,
        'percent': 100 if completed else min(99.9, downloaded * 100 / total) if total else 0,
        'eta_seconds': eta,
        'speed': f'{speed / 1024:.1f} KiB/s' if speed else '',
        'eta': f'{eta // 3600:02}:{eta % 3600 // 60:02}:{eta % 60:02}' if eta is not None else '',
    }


class _Aria2:
    def __init__(self, binary: Path, state_dir: Path):
        self._closed = False
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            self._port = listener.getsockname()[1]
        self._secret = secrets.token_hex(24)
        command = [
            str(binary.resolve()), '--no-conf=true', '--no-netrc=true', '--enable-rpc=true',
            '--rpc-listen-all=false', '--rpc-allow-origin-all=false', f'--rpc-listen-port={self._port}',
            f'--rpc-secret={self._secret}', '--rpc-save-upload-metadata=false', '--rpc-max-request-size=32M',
            '--listen-port=6881-6999', '--dht-listen-port=6881-6999', '--enable-dht6=true',
            '--dht-file-path=dht.dat', '--dht-file-path6=dht6.dat',
            '--auto-save-interval=1', '--seed-time=0', '--file-allocation=none', '--disk-cache=0',
            '--auto-file-renaming=false', '--allow-overwrite=false', '--summary-interval=0',
        ]
        if os.name == 'nt':
            self._process = ManagedProcess(command, cwd=str(state_dir), stdout=subprocess.DEVNULL)
        else:
            self._process = subprocess.Popen(command, cwd=state_dir, stdin=subprocess.DEVNULL,
                                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def call(self, method: str, *params):
        client = http.client.HTTPConnection('127.0.0.1', self._port, timeout=2)
        try:
            body = json.dumps({'jsonrpc': '2.0', 'id': 'captainflash', 'method': f'aria2.{method}',
                               'params': [f'token:{self._secret}', *params]})
            client.request('POST', '/jsonrpc', body, {'Content-Type': 'application/json'})
            result = json.load(client.getresponse())
            if result.get('id') != 'captainflash' or 'result' not in result:
                raise TorrentError('Torrent 引擎拒绝了请求。请检查种子文件、文件选择和保存目录。')
            return result['result']
        except (OSError, ValueError, http.client.HTTPException) as error:
            raise TorrentError('无法连接本机 Torrent 引擎。') from error
        finally:
            client.close()

    def wait_ready(self, interrupted) -> None:
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline and self._process.poll() is None:
            interrupted()
            try:
                if 'BitTorrent' in self.call('getVersion').get('enabledFeatures', []):
                    return
                break
            except TorrentError:
                time.sleep(0.1)
        raise TorrentError('无法启动支持 BitTorrent 的本机 aria2 引擎。')

    def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        try:
            if self._process.poll() is None:
                try:
                    # Forced shutdown skips network cleanup and still saves the piece journal.
                    self.call('forceShutdown')
                    self._process.wait(timeout=4)
                except (TorrentError, subprocess.TimeoutExpired):
                    self._process.terminate()
                    self._process.wait(timeout=2)
        finally:
            if isinstance(self._process, ManagedProcess):
                self._process.close()


class TorrentWorker(QObject):
    files_ready = Signal(object)
    artifacts_ready = Signal(object)
    progress = Signal(dict)
    finished = Signal(str)
    failed = Signal(str)
    stopped = Signal(str)

    def __init__(self, request: TorrentRequest):
        super().__init__()
        self.request = request
        self._selection = tuple(request.selected_files)
        self._selection_ready = threading.Event()
        self._stop = threading.Event()
        self._stop_reason = ''
        self._stop_lock = threading.Lock()

    def select_files(self, indices) -> None:
        self._selection = tuple(indices)
        self._selection_ready.set()

    def pause(self) -> None:
        self.stop('pause')

    def cancel(self) -> None:
        self.stop('cancel')

    def stop(self, reason: str) -> None:
        with self._stop_lock:
            if self._stop_reason != 'cancel':
                self._stop_reason = reason
            self._stop.set()
            self._selection_ready.set()

    def _check_stop(self) -> None:
        if self._stop.is_set():
            raise _TransferInterrupted

    def _wait(self, rpc: _Aria2, gid: str, *, report: bool = False, timeout: int = 0) -> None:
        deadline = time.monotonic() + timeout if timeout else None
        while True:
            self._check_stop()
            status = rpc.call('tellStatus', gid, _STATUS_FIELDS)
            state = status['status']
            if state in {'error', 'removed'}:
                code = status.get('errorCode', '')
                suffix = f'（aria2 错误 {code}）' if str(code).isdigit() else ''
                raise TorrentError(f'Torrent 下载失败{suffix}。')
            if report:
                self.progress.emit(_progress(status))
            if state == 'complete':
                return
            if deadline is not None and time.monotonic() >= deadline:
                raise TorrentError('获取磁力链接文件列表超时。请检查网络和可连接的做种者。')
            self._stop.wait(_POLL_SECONDS)

    def _metadata(self, rpc: _Aria2, state_dir: Path) -> bytes:
        source = self.request.source.strip()
        if source.lower().startswith('magnet:'):
            digest = _btih(source)
            cached = state_dir / f'{digest}.torrent'
            if not cached.is_file():
                fetch_dir = state_dir / 'metadata'
                fetch_dir.mkdir(exist_ok=True)
                staging = fetch_dir / cached.name
                staging.unlink(missing_ok=True)
                gid = rpc.call('addUri', ['magnet:' + source[7:]], {
                    'dir': fetch_dir.as_posix(), 'bt-metadata-only': 'true', 'bt-save-metadata': 'true',
                })
                self._wait(rpc, gid, timeout=120)
                if not staging.is_file():
                    raise TorrentError('Torrent 引擎未保存磁力链接的文件列表。')
                self._read_torrent(staging)
                staging.replace(cached)
        else:
            cached = state_dir / 'source.torrent'
            if not cached.is_file():
                if source.lower().startswith(("http://", "https://")):
                    with _open(source) as response:
                        data = response.read(_MAX_TORRENT_BYTES + 1)
                    if not 0 < len(data) <= _MAX_TORRENT_BYTES or not data.startswith(b"d"):
                        raise TorrentError("下载的种子文件无效或超过 16 MiB。")
                else:
                    original = Path(source)
                    if original.suffix.lower() != '.torrent' or not original.is_file():
                        raise TorrentError('请选择存在的 .torrent 文件或输入有效的磁力链接。')
                    data = self._read_torrent(original)
                self._write_atomic(cached, data)
        return self._read_torrent(cached)

    @staticmethod
    def _write_atomic(path: Path, data: bytes) -> None:
        staged = path.with_suffix('.tmp')
        staged.write_bytes(data)
        staged.replace(path)

    @staticmethod
    def _read_torrent(path: Path) -> bytes:
        with path.open('rb') as file:
            data = file.read(_MAX_TORRENT_BYTES + 1)
        if not 0 < len(data) <= _MAX_TORRENT_BYTES:
            raise TorrentError('种子文件为空或超过 16 MiB。')
        return data

    def _run_download(self, rpc: _Aria2, state_dir: Path, output: Path):
        torrent = self._metadata(rpc, state_dir)
        self._check_stop()
        stage = torrent_partial_dir(output, state_dir)
        if stage.resolve() != stage:
            raise TorrentError('Torrent 的暂存目录包含重定向链接。')
        stage.mkdir(exist_ok=True)
        owner = stage / ".captainflash-owner"
        if owner.is_symlink() or (owner.exists() and owner.read_text(encoding="utf-8") != state_dir.name):
            raise TorrentError("Torrent 的暂存目录不属于当前任务。")
        owner.write_text(state_dir.name, encoding="utf-8")
        self._write_atomic(state_dir / "stage.json", json.dumps(output.as_posix()).encode())
        gid = rpc.call('addTorrent', base64.b64encode(torrent).decode('ascii'), [], {
            'dir': stage.as_posix(), 'pause': 'true', 'bt-remove-unselected-file': 'true',
            'check-integrity': 'true', 'bt-hash-check-seed': 'false',
        })
        files = _files_for_picker(rpc.call('getFiles', gid), stage)
        root_name = Path(files[0].path).parts[0]
        destination = output / root_name
        local_root = stage / root_name
        ready = state_dir / 'ready.json'
        if ready.is_file() and not local_root.exists() and destination.exists():
            if json.loads(ready.read_bytes()) == root_name:
                owner.unlink(missing_ok=True)
                if not any(stage.iterdir()):
                    stage.rmdir()
                return destination, tuple(str(output / file.path) for file in files
                                          if file.index in self._selection and (output / file.path).is_file())
        if destination.exists() or destination.is_symlink():
            raise TorrentError('保存目录已有同名文件或文件夹。请选择其他目录。')
        if not self._selection:
            self.files_ready.emit(files)
            self._selection_ready.wait()
        self._check_stop()
        valid = {file.index for file in files}
        selected = set(self._selection)
        if not selected or not selected <= valid:
            raise TorrentError('请选择种子中的至少一个有效文件。')
        selection = ','.join(map(str, sorted(selected)))
        rpc.call('changeOption', gid, {'select-file': selection})
        rpc.call('unpause', gid)
        self._wait(rpc, gid, report=True)
        rpc.close()
        for file in files:
            if file.index in selected and not file.length:
                empty_file = stage / file.path
                empty_file.parent.mkdir(parents=True, exist_ok=True)
                empty_file.touch(exist_ok=True)
        self._write_atomic(ready, json.dumps(root_name, ensure_ascii=False).encode('utf-8'))
        # Selective completion can retain the swarm's progress journal for the other files.
        stage.joinpath(root_name + '.aria2').unlink(missing_ok=True)
        local_root.rename(destination)
        owner.unlink(missing_ok=True)
        stage.rmdir()
        return destination, tuple(str(output / file.path) for file in files if file.index in selected)

    @Slot()
    def run(self) -> None:
        try:
            self._check_stop()
            binary = self.request.aria2_path or find_aria2()
            if binary is None or not binary.is_file():
                raise TorrentError('缺少本机 Torrent 引擎 aria2。请安装包含该组件的 Windows 版本。')
            state_dir = self.request.state_dir.resolve()
            output = self.request.output_dir.resolve()
            state_dir.mkdir(parents=True, exist_ok=True)
            output.mkdir(parents=True, exist_ok=True)
            rpc = _Aria2(binary, state_dir)
            try:
                rpc.wait_ready(self._check_stop)
                destination, artifacts = self._run_download(rpc, state_dir, output)
            finally:
                rpc.close()
        except _TransferInterrupted:
            self.stopped.emit(self._stop_reason)
        except TorrentError as error:
            self.failed.emit(str(error))
        except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError, DirectFileError):
            self.failed.emit('Torrent 任务无法读写文件或保存下载状态。请检查种子、保存目录和可用空间。')
        else:
            self.artifacts_ready.emit(artifacts)
            self.finished.emit(str(destination))
