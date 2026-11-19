from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import ssl
from threading import Barrier, BrokenBarrierError, Thread
from time import monotonic

import pytest

from ytdl_gui import direct_file
from ytdl_gui.workers import DownloadRequest


BODY = b"ordinary file\x00" * 20_000


@pytest.fixture
def source():
    class Handler(BaseHTTPRequestHandler):
        tag = '"same"'
        disposition = "attachment; filename=plain.zip; filename*=UTF-8''%E6%B5%8B%E8%AF%95+01.zip"
        content_type = 'application/zip'
        alternate_body = None
        wrong_range = False
        cut_body = False
        range_redirect = False
        refuse_ranges = False
        range_barrier = None
        ranges = []
        received_cookies = []

        def do_GET(self):
            self.received_cookies.append(self.headers.get("Cookie"))
            self.ranges.append((self.path, self.headers.get('Range'), self.headers.get('If-Range')))
            if self.path == '/secure-redirect':
                self.send_response(302)
                self.send_header('Location', f'https://127.0.0.1:{self.server.server_port}/a.zip')
                self.end_headers()
                return
            if self.path == '/redirect' or (self.path == '/a.zip' and self.range_redirect):
                self.send_response(302)
                self.send_header('Location', '/a.zip' if self.path == '/redirect' else '/other.zip')
                self.end_headers()
                return
            payload = self.alternate_body if self.alternate_body is not None else BODY
            end = len(payload) - 1
            if self.headers.get('Range') and not self.refuse_ranges:
                first, last = self.headers['Range'].partition('=')[2].split('-')
                start = int(first)
                if last:
                    end = min(end, int(last))
                if self.range_barrier is not None:
                    try:
                        self.range_barrier.wait(timeout=2)
                    except BrokenBarrierError:
                        self.send_error(503)
                        return
                self.send_response(206)
                range_start = start - 1 if self.wrong_range else start
                self.send_header('Content-Range', f'bytes {range_start}-{end}/{len(payload)}')
            else:
                start = 0
                self.send_response(200)
            self.send_header('Content-Length', str(end - start + 1))
            self.send_header('Content-Type', self.content_type)
            if self.disposition is not None:
                self.send_header('Content-Disposition', self.disposition)
            if self.tag is not None:
                self.send_header('ETag', self.tag)
            self.end_headers()
            try:
                chunk = payload[start:end + 1]
                self.wfile.write(chunk[:1200] if self.cut_body else chunk)
            except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError):
                pass

        def log_message(self, *args):
            pass

    with ThreadingHTTPServer(('127.0.0.1', 0), Handler) as server:
        thread = Thread(target=server.serve_forever, kwargs={'poll_interval': 0.01}, daemon=True)
        thread.start()
        try:
            yield f'http://127.0.0.1:{server.server_port}', Handler
        finally:
            server.shutdown()
            thread.join()


def request_for(tmp_path, url, name='archive.zip'):
    return DownloadRequest(url=url, ytdlp_path=Path('yt-dlp.exe'), output_template=tmp_path / 'output' / name,
                           format_id='', temp_dir=tmp_path / 'task')


def test_http_seed_is_detected_and_cached(source, tmp_path):
    from ytdl_gui.torrent import TorrentRequest, TorrentWorker

    base, handler = source
    handler.content_type = "application/x-bittorrent"
    handler.disposition = "attachment; filename=sample.torrent"
    handler.alternate_body = b"d4:infodee"
    url = base + "/sample.torrent"
    assert direct_file.probe_direct_file(url).kind == "torrent"
    worker = TorrentWorker(TorrentRequest(url, tmp_path, tmp_path))
    assert worker._metadata(None, tmp_path) == handler.alternate_body
    assert (tmp_path / "source.torrent").read_bytes() == handler.alternate_body


def test_file_probe_applies_cookies_only_to_matching_hosts(source, tmp_path):
    base, handler = source
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text(
        "# Netscape HTTP Cookie File\n127.0.0.1\tFALSE\t/\tFALSE\t0\tfixture\tvalid\n",
        encoding="utf-8",
    )
    assert direct_file.probe_direct_file(base + "/a.zip", cookie_file) is not None
    assert direct_file.probe_direct_file(base.replace("127.0.0.1", "localhost") + "/a.zip", cookie_file) is not None
    assert handler.received_cookies == ["fixture=valid", None]


def run_worker(request, *, policy='rename', pause=False, pause_at=0, **options):
    worker = direct_file.DirectFileWorker(
        request, conflict_policy=policy, report_special_results=True, **options,
    )
    terminal = []
    for event in ('finished', 'failed', 'stopped', 'network_failed', 'skipped', 'restart_required'):
        getattr(worker, event).connect(lambda *args, event=event: terminal.append((event, *args)))
    if pause:
        worker.progress.connect(
            lambda event: worker.stop('pause') if event.percent and pause_at < event.percent < 100 else None,
        )
    worker.run()
    assert len(terminal) == 1
    return terminal[0]


def test_probe_redirect_content_disposition_and_size(source):
    url, handler = source
    info = direct_file.probe_direct_file(url + '/redirect')
    assert info.filename == '测试+01.zip'
    assert info.content_type == 'application/zip'
    assert info.total_bytes == len(BODY)
    assert info.kind == 'file'
    assert info.resume_available
    handler.disposition = None
    assert direct_file.probe_direct_file(url + '/redirect').filename == 'a.zip'


def test_probe_excludes_web_pages_and_identifies_media(source):
    url, handler = source
    handler.alternate_body = b' \xef\xbb\xbf<!DOCTYPE HTML><html>Login</html>'
    assert direct_file.probe_direct_file(url + '/a.zip') is None
    handler.alternate_body = None
    handler.content_type = 'application/octet-stream'
    handler.disposition = None
    assert direct_file.probe_direct_file(url + '/file.mp4').kind == 'video'
    handler.content_type = 'application/vnd.apple.mpegurl'
    assert direct_file.probe_direct_file(url + '/stream').kind == 'stream'


def test_probe_decodes_quoted_filename_with_delimiter_and_escape(source):
    url, handler = source
    handler.disposition = r'attachment; note="client; filename=ignored"; filename="report;\"one\".txt"'
    assert direct_file.probe_direct_file(url + '/a.zip').filename == 'report;_one_.txt'


def test_tls_initialization_is_deferred_until_https_redirect(source, monkeypatch):
    url, _handler = source
    attempted = []

    def unavailable_context(*args, **kwargs):
        attempted.append(True)
        raise FileNotFoundError

    monkeypatch.setattr(ssl, '_create_default_https_context', unavailable_context)
    assert direct_file.probe_direct_file(url + '/a.zip') is not None
    assert attempted == []
    assert direct_file.probe_direct_file(url + '/secure-redirect') is None
    assert attempted == [True]


@pytest.mark.parametrize(('raw', 'safe'), [
    ('C:\\folder\\CON.txt', '_CON.txt'),
    ('../../NUL. ', '_NUL'),
    ('AUX .foo', '_AUX.foo'),
    ('../foo?.zip', 'foo_.zip'),
    ('..', '下载文件'),
])
def test_safe_windows_name(raw, safe):
    assert direct_file.sanitize_file_name(raw) == safe


@pytest.mark.parametrize(('policy', 'event', 'new_name'), [
    ('rename', 'finished', 'archive (1).zip'),
    ('skip', 'skipped', None),
    ('overwrite', 'finished', 'archive.zip'),
])
def test_conflict_policy(source, tmp_path, policy, event, new_name):
    url, handler = source
    request = request_for(tmp_path, url + '/a.zip')
    target = request.output_template
    target.parent.mkdir()
    target.write_bytes(b'existing')
    result = run_worker(request, policy=policy)
    assert result[0] == event
    if new_name:
        new_path = target.with_name(new_name)
        assert result[1] == str(new_path)
        assert new_path.read_bytes() == BODY
    if policy != 'overwrite':
        assert target.read_bytes() == b'existing'


def test_stop_then_resume_sends_validated_range(source, tmp_path):
    url, handler = source
    request = request_for(tmp_path, url + '/a.zip')
    assert run_worker(request, pause=True) == ('stopped', 'pause')
    assert not request.output_template.exists()
    assert run_worker(request)[0] == 'finished'
    assert request.output_template.read_bytes() == BODY
    assert handler.ranges[-1][1].startswith('bytes=')
    assert handler.ranges[-1][2] == '"same"'


def test_resume_range_is_only_sent_to_bound_destination(source, tmp_path):
    url, handler = source
    request = request_for(tmp_path, url + '/redirect')
    assert run_worker(request, pause=True) == ('stopped', 'pause')
    handler.range_redirect = True
    assert run_worker(request)[0] == 'restart_required'
    assert handler.ranges[-3] == ('/redirect', None, None)
    assert handler.ranges[-2][0] == '/a.zip'
    assert handler.ranges[-2][1].startswith('bytes=')
    assert handler.ranges[-2][2] == '"same"'
    assert handler.ranges[-1] == ('/other.zip', None, None)


def test_resume_disabled_restarts_without_a_range(source, tmp_path):
    url, handler = source
    request = request_for(tmp_path, url + '/a.zip')
    assert run_worker(request, pause=True) == ('stopped', 'pause')
    assert run_worker(request, pause=True, resume_transfer=False) == ('stopped', 'pause')
    assert not (request.temp_dir / 'direct-file.part').exists()
    assert run_worker(request, resume_transfer=False)[0] == 'finished'
    assert all(entry[1:] == (None, None) for entry in handler.ranges)
    assert request.output_template.read_bytes() == BODY


@pytest.mark.parametrize('mode', ['valid', 'wrong-range', 'refused', 'no-validator', 'weak-validator'])
def test_parallel_ranges_require_verified_representation(source, tmp_path, mode):
    url, handler = source
    request = request_for(tmp_path, url + '/a.zip')
    handler.alternate_body = bytes(range(251)) * 8_000 + b'end'
    handler.wrong_range = mode == 'wrong-range'
    handler.refuse_ranges = mode == 'refused'
    if mode == 'valid':
        handler.range_barrier = Barrier(3)
    elif mode == 'no-validator':
        handler.tag = None
    elif mode == 'weak-validator':
        handler.tag = 'W/"same"'
    assert run_worker(request, max_connections=3)[0] == 'finished'
    assert request.output_template.read_bytes() == handler.alternate_body
    ranges = {(header, validator) for _, header, validator in handler.ranges if header}
    if mode == 'valid':
        total = len(handler.alternate_body)
        expected = {f'bytes={total * index // 3}-{total * (index + 1) // 3 - 1}' for index in range(3)}
        assert ranges == {(header, '"same"') for header in expected}
        assert len(handler.ranges) == 4
    elif mode in {'no-validator', 'weak-validator'}:
        assert not ranges
    else:
        assert ranges
        assert handler.ranges[-1][1:] == (None, None)


def test_parallel_pause_keeps_only_contiguous_bytes_for_resume(source, tmp_path):
    url, handler = source
    request = request_for(tmp_path, url + '/a.zip')
    body = handler.alternate_body = bytes(range(251)) * 8_000
    assert run_worker(request, max_connections=3, rate_limit_kib=1024,
                      pause=True, pause_at=70) == ('stopped', 'pause')
    partial = (request.temp_dir / 'direct-file.part').read_bytes()
    assert 0 < len(partial) < len(body)
    assert partial == body[:len(partial)]
    assert run_worker(request, max_connections=3)[0] == 'finished'
    assert handler.ranges[-1] == ('/a.zip', f'bytes={len(partial)}-', '"same"')
    assert request.output_template.read_bytes() == body


def test_download_rate_limit_applies_to_total_task_bytes(source, tmp_path):
    url, handler = source
    request = request_for(tmp_path, url + '/a.zip')
    handler.alternate_body = BODY * 4
    started = monotonic()
    assert run_worker(request, max_connections=3, rate_limit_kib=1024)[0] == 'finished'
    assert monotonic() - started >= len(handler.alternate_body) / (1024 * 1024) * 0.92
    assert request.output_template.read_bytes() == handler.alternate_body


def test_shared_rate_limit_applies_to_concurrent_tasks(source, tmp_path):
    url, handler = source
    first = request_for(tmp_path, url + '/a.zip')
    second = replace(first, temp_dir=tmp_path / 'second', output_template=tmp_path / 'output' / 'second.zip')
    limit = direct_file.HttpTransferLimiter(512)
    started = monotonic()
    with ThreadPoolExecutor(max_workers=2) as executor:
        results = [executor.submit(run_worker, request, transfer_limiter=limit) for request in (first, second)]
        assert [result.result()[0] for result in results] == ['finished', 'finished']
    assert monotonic() - started >= len(BODY) * 2 / (512 * 1024) * 0.92
    assert first.output_template.read_bytes() == second.output_template.read_bytes() == BODY


@pytest.mark.parametrize('mode', ['bad-range', 'changed-tag', 'changed-url', 'tampered-length', 'no-validator'])
def test_unsafe_resumption_preserves_partial(source, tmp_path, mode):
    url, handler = source
    if mode == 'no-validator':
        handler.tag = None
    request = request_for(tmp_path, url + '/a.zip')
    assert run_worker(request, pause=True) == ('stopped', 'pause')
    partial = request.temp_dir / 'direct-file.part'
    if mode == 'bad-range':
        handler.wrong_range = True
    elif mode == 'changed-tag':
        handler.tag = '"new"'
    elif mode == 'changed-url':
        request = replace(request, url=url + '/another.zip')
    elif mode == 'tampered-length':
        partial.write_bytes(partial.read_bytes() + b'extra')
    current = partial.read_bytes()
    assert run_worker(request)[0] == 'restart_required'
    assert partial.read_bytes() == current
    assert not request.output_template.exists()
    direct_file.reset_direct_file_partial(request.temp_dir)
    assert not partial.exists()


def test_interruption_cannot_overwrite_existing_file(source, tmp_path):
    url, handler = source
    handler.cut_body = True
    request = request_for(tmp_path, url + '/a.zip')
    target = request.output_template
    target.parent.mkdir()
    target.write_bytes(b'existing')
    assert run_worker(request, policy='overwrite')[0] == 'network_failed'
    assert target.read_bytes() == b'existing'
    handler.cut_body = False
    assert run_worker(request, policy='overwrite')[0] == 'finished'
    assert target.read_bytes() == BODY


def test_transfer_rechecks_that_source_is_an_ordinary_file(source, tmp_path):
    url, handler = source
    request = request_for(tmp_path, url + '/a.zip')
    handler.content_type = 'video/mp4'
    assert run_worker(request)[0] == 'failed'
    assert not request.output_template.exists()


def test_concurrent_same_name_transfers_publish_distinct_files(source, tmp_path):
    url, _handler = source
    first = request_for(tmp_path, url + '/a.zip')
    second = replace(first, temp_dir=tmp_path / 'other_task')
    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(run_worker, (first, second)))
    assert {result[0] for result in results} == {'finished'}
    paths = {Path(result[1]) for result in results}
    assert {path.name for path in paths} == {'archive.zip', 'archive (1).zip'}
    assert all(path.read_bytes() == BODY for path in paths)
