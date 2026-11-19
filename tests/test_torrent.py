import base64
from contextlib import contextmanager
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import socket
import struct
from threading import Event, Thread, Timer
from urllib.parse import parse_qs, urlencode, urlsplit

import pytest
from PySide6.QtCore import Qt

from ytdl_gui.torrent import (
    TorrentError, TorrentRequest, TorrentWorker, _Aria2, _btih, _files_for_picker, _progress,
    find_aria2, torrent_partial_dir,
)


def test_magnet_hash_normalizes_the_two_btih_encodings():
    digest = bytes(range(20))
    for encoded in (digest.hex().upper(), base64.b32encode(digest).decode()):
        assert _btih(f'magnet:?dn=Fixture&xt=urn%3Abtih%3A{encoded}') == digest.hex()
    with pytest.raises(TorrentError):
        _btih('magnet:?xt=urn:btmh:122000')


def test_picker_returns_relative_paths_and_rejects_paths_outside_download(tmp_path):
    stage = tmp_path / 'pending'
    file = {'index': '1', 'length': '42', 'path': (stage / 'Test' / 'one.bin').as_posix()}
    (picked,) = _files_for_picker([file], stage)
    assert (picked.index, Path(picked.path), picked.length) == (1, Path('Test/one.bin'), 42)
    for outside in (tmp_path / 'outside.bin', stage / 'Test:stream', stage / 'NUL.txt'):
        with pytest.raises(TorrentError):
            _files_for_picker([file | {'path': outside.as_posix()}], stage)


def test_progress_uses_only_selected_files():
    status = {
        'downloadSpeed': '25',
        'files': [
            {'length': '100', 'completedLength': '50', 'selected': 'true'},
            {'length': '500', 'completedLength': '200', 'selected': 'false'},
        ],
    }
    progress = _progress(status)
    assert progress['percent'] == 50
    assert progress['total_bytes'] == 100
    assert progress['downloaded_bytes'] == 50
    assert progress['eta_seconds'] == 2


def _bencode(value):
    if isinstance(value, bytes):
        return str(len(value)).encode() + b':' + value
    if isinstance(value, int):
        return f'i{value}e'.encode()
    if isinstance(value, list):
        return b'l' + b''.join(map(_bencode, value)) + b'e'
    return b'd' + b''.join(_bencode(key) + _bencode(value[key]) for key in sorted(value)) + b'e'


@contextmanager
def _local_tracker():
    ready = Event()
    publish_peers = Event()
    publish_peers.set()
    seed = []

    class Tracker(BaseHTTPRequestHandler):
        def do_GET(self):
            fields = parse_qs(urlsplit(self.path).query)
            if not seed and fields.get('left') == ['0']:
                seed.append(int(fields['port'][0]))
                ready.set()
            peers = socket.inet_aton('127.0.0.1') + struct.pack('!H', seed[0]) if seed and publish_peers.is_set() else b''
            payload = _bencode({b'interval': 2, b'peers': peers})
            self.send_response(200)
            self.send_header('Content-Length', str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_):
            pass

    with ThreadingHTTPServer(('127.0.0.1', 0), Tracker) as server:
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield f'http://127.0.0.1:{server.server_port}/announce', ready, publish_peers
        finally:
            server.shutdown()
            thread.join()


@contextmanager
def _seed_torrent(tmp_path, binary):
    piece_length = 64 * 1024
    # The files share a piece. Only the selected payload should be published.
    payloads = {b'first.bin': b'F' * (8 * 1024 * 1024 + 23), b'second.bin': b'S' * (192 * 1024)}
    stream = b''.join(payloads.values())
    info = {b'name': b'Fixture', b'piece length': piece_length, b'private': 1,
            b'files': [{b'path': [name], b'length': len(data)} for name, data in payloads.items()],
            b'pieces': b''.join(hashlib.sha1(stream[offset:offset + piece_length]).digest()
                               for offset in range(0, len(stream), piece_length))}
    seed_directory = tmp_path / 'seed'
    seed_directory.mkdir()
    (seed_directory / 'Fixture').mkdir()
    for name, data in payloads.items():
        (seed_directory / 'Fixture' / name.decode()).write_bytes(data)
    with _local_tracker() as (announce, ready, publish_peers):
        torrent = _bencode({b'info': info, b'announce': announce.encode()})
        seeder = _Aria2(binary, seed_directory)
        try:
            seeder.wait_ready(lambda: None)
            seeder.call('changeGlobalOption', {'max-overall-upload-limit': '768K'})
            seed_id = seeder.call('addTorrent', base64.b64encode(torrent).decode(), [], {
                'dir': seed_directory.as_posix(), 'seed-time': '60', 'seed-ratio': '0', 'check-integrity': 'true',
            })
            assert ready.wait(10), seeder.call('tellStatus', seed_id)
            torrent_path = tmp_path / 'test.torrent'
            torrent_path.write_bytes(torrent)
            magnet = 'magnet:?' + urlencode({'xt': 'urn:btih:' + hashlib.sha1(_bencode(info)).hexdigest(), 'tr': announce})
            yield torrent_path, magnet, payloads, publish_peers
        finally:
            seeder.close()


def _run_with_deadline(worker):
    timeout = Timer(45, lambda: worker.stop('test_timeout'))
    timeout.start()
    try:
        worker.run()
    finally:
        timeout.cancel()


@pytest.mark.skipif(os.environ.get('CAPTAINFLASH_TORRENT_INTEGRATION') != '1',
                    reason='Requires loopback access between local processes')
def test_real_torrent_and_magnet_selection_restart_and_publish(tmp_path):
    binary = find_aria2()
    if binary is None:
        pytest.skip('Run scripts/fetch_aria2.ps1 to install the local test engine.')
    direct = Qt.ConnectionType.DirectConnection
    with _seed_torrent(tmp_path, binary) as (torrent_path, magnet, payloads, publish_peers):
        request = TorrentRequest(str(torrent_path), tmp_path / 'from-file', tmp_path / 'state-file', (), binary)
        first = TorrentWorker(request)
        stopped, finished, errors, seen = [], [], [], []
        first.stopped.connect(stopped.append, direct)
        first.finished.connect(finished.append, direct)
        first.failed.connect(errors.append, direct)

        def choose(files):
            assert [file.path for file in files] == ['Fixture/first.bin', 'Fixture/second.bin']
            first.select_files([1])

        def pause_after_progress(update):
            if update['downloaded_bytes'] >= 128 * 1024:
                seen.append(update['downloaded_bytes'])
                first.stop('exit')

        first.files_ready.connect(choose, direct)
        first.progress.connect(pause_after_progress, direct)
        _run_with_deadline(first)
        assert stopped == ['exit'] and not errors and not finished and seen
        assert not (request.output_dir / 'Fixture').exists()
        assert list(torrent_partial_dir(request.output_dir, request.state_dir).rglob('*.aria2'))
        torrent_path.unlink()

        publish_peers.clear()
        resumed_bytes = []
        resumed = TorrentWorker(TorrentRequest(request.source, request.output_dir, request.state_dir, (1,), binary))

        def observe_checkpoint(update):
            if not resumed_bytes and update['downloaded_bytes']:
                resumed_bytes.append(update['downloaded_bytes'])
                publish_peers.set()

        resumed.progress.connect(observe_checkpoint, direct)
        resumed.finished.connect(finished.append, direct)
        resumed.failed.connect(errors.append, direct)
        _run_with_deadline(resumed)
        assert not errors and len(finished) == 1
        assert resumed_bytes[0] >= seen[0]
        assert (Path(finished[0]) / 'first.bin').read_bytes() == payloads[b'first.bin']
        assert not (Path(finished[0]) / 'second.bin').exists()

        magnet_state = tmp_path / '磁力状态'
        (magnet_state / 'metadata').mkdir(parents=True)
        (magnet_state / 'metadata' / f'{_btih(magnet)}.torrent').write_bytes(b'd')
        magnet_job = TorrentWorker(TorrentRequest(magnet, tmp_path / '磁力下载', magnet_state, (), binary))
        magnet_job.files_ready.connect(lambda files: magnet_job.select_files([files[1].index]), direct)
        magnet_job.finished.connect(finished.append, direct)
        magnet_job.failed.connect(errors.append, direct)
        _run_with_deadline(magnet_job)
        assert not errors and len(finished) == 2
        assert (Path(finished[1]) / 'second.bin').read_bytes() == payloads[b'second.bin']
        assert not (Path(finished[1]) / 'first.bin').exists()
        assert (magnet_state / f'{_btih(magnet)}.torrent').is_file()
