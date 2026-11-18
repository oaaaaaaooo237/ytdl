import io
import json
import subprocess
import sys
from pathlib import Path

from ytdl_gui.workers import AnalysisRequest, AnalysisWorker, DownloadRequest, DownloadWorker
from ytdl_gui.ytdlp_runner import YtdlpCommandBuilder, categorize_analysis_error


URL = 'https://cn.pornhub.com/view_video.php?viewkey=test'


def analysis(tmp_path, cookies=None):
    payloads = []
    worker = AnalysisWorker(AnalysisRequest(URL, Path('yt-dlp.exe'), cookies),
                            runner=lambda *a, **k: subprocess.CompletedProcess([], 0, json.dumps({
                                'id': 'test', 'formats': [{'format_id': '480', 'url': 'https://media.test/private'}]
                            }), ''))
    worker.finished.connect(payloads.append)
    worker.run()
    return payloads[0]


def test_site_profile_is_consistent_and_host_scoped():
    builder = YtdlpCommandBuilder(Path('yt-dlp.exe'))
    for url, expected in [(URL, True), ('https://pornhub.com.evil.test/x', False),
                          ('https://youtube.com/x', False)]:
        for command in [builder.analysis_command(url), builder.preview_url_command(url),
                        builder.download_command(url, Path('out.mp4'), '480')]:
            assert ('--legacy-server-connect' in command) is expected


def test_download_uses_private_stdin_snapshot_and_reserves_output(tmp_path):
    payload = analysis(tmp_path)
    captured = {}
    writer = io.StringIO()

    class Process:
        stdin = writer
        stdout = ['__YTDL_OUTPUT__' + json.dumps(str(tmp_path / 'out.mp4')) + '\n']
        def wait(self): return 0
        def terminate(self): pass

    def popen(command, **kwargs):
        captured['command'], captured['kwargs'] = command, kwargs
        return Process()

    # Capture the pipe before close, like the receiving child process.
    class Pipe(io.StringIO):
        def close(self): captured['json'] = self.getvalue(); super().close()
    Process.stdin = Pipe()
    request = DownloadRequest(URL, Path('yt-dlp.exe'), tmp_path / 'out.mp4', '480', analysis_info=payload)
    worker = DownloadWorker(request, popen_factory=popen)
    reserved = []
    worker.output_reserved.connect(reserved.append)
    worker.run()
    assert captured['command'][-2:] == ['--load-info-json', '-']
    assert URL not in captured['command']
    assert 'media.test/private' not in repr(captured['command'])
    assert captured['kwargs']['stdin'] == subprocess.PIPE
    assert json.loads(captured['json'])['formats'][0]['format_id'] == '480'
    assert not any(key.startswith('__ytdl') for key in json.loads(captured['json']))
    assert reserved == [str(tmp_path / 'out.mp4')]


def test_changed_cookie_or_expired_snapshot_reparses(tmp_path):
    from ytdl_gui.workers import reusable_analysis
    cookie = tmp_path / 'cookies.txt'
    cookie.write_text('original')
    payload = analysis(tmp_path, cookie)
    assert reusable_analysis(payload, URL, cookie) is not None
    assert reusable_analysis(payload, URL + 'other', cookie) is None
    expired = dict(payload, __ytdl_snapshot_created=0)
    assert reusable_analysis(expired, URL, cookie) is None
    cookie.write_text('changed')
    assert reusable_analysis(payload, URL, cookie) is None


def test_cookie_change_during_analysis_does_not_bind_new_cookie_to_old_result(tmp_path):
    cookie = tmp_path / 'cookies.txt'
    cookie.write_text('original')
    captured = []
    def runner(*args, **kwargs):
        cookie.write_text('changed')
        return subprocess.CompletedProcess([], 0, '{"formats":[{"format_id":"480"}]}', '')
    worker = AnalysisWorker(AnalysisRequest(URL, Path('yt-dlp.exe'), cookie), runner=runner)
    worker.finished.connect(captured.append)
    worker.run()
    assert '__ytdl_snapshot_created' not in captured[0]


def test_snapshot_is_not_in_restored_queue(tmp_path):
    from ytdl_gui.queue_store import QueueStore, _protect
    store = QueueStore(tmp_path)
    request = DownloadRequest(URL, Path('yt-dlp.exe'), tmp_path / 'out.mp4', '480',
                              analysis_info=analysis(tmp_path))
    store.save({'a' * 32: (request, {}, 'queued')})
    assert b'media.test' not in _protect(store.path.read_bytes(), decrypt=True)
    assert store.load()['a' * 32]['request'].analysis_info is None


def test_webpage_error_is_not_misreported_as_login():
    assert categorize_analysis_error('Unable to download webpage: Connection reset by peer').value != 'login_required'
    assert categorize_analysis_error('No video formats found').value == 'no_formats'


def test_managed_process_can_receive_info_over_stdin():
    from ytdl_gui.process_tree import ManagedProcess
    process = ManagedProcess([sys.executable, '-c', 'import sys; print(sys.stdin.read())'], stdin=subprocess.PIPE)
    try:
        process.stdin.write('{"hello":"world"}')
        process.stdin.close()
        assert json.loads(process.stdout.read()) == {'hello': 'world'}
        assert process.wait(timeout=5) == 0
    finally:
        process.close()


def test_analysis_callbacks_start_thumbnail_on_ui_and_release_threads(qtbot):
    from ytdl_gui.ui.main_window import MainWindow
    observed = []
    class Window(MainWindow):
        def apply_analysis_result(self, url, payload):
            observed.append(self._is_ui_thread())
            super().apply_analysis_result(url, payload)
    payload = {'title': 'Demo', 'thumbnail': 'https://example.test/thumb.jpg', 'formats': [
        {'format_id': '480', 'height': 480, 'ext': 'mp4', 'vcodec': 'avc1', 'acodec': 'mp4a'}]}
    window = Window(analysis_runner=lambda *a, **k: subprocess.CompletedProcess([], 0, json.dumps(payload), ''),
                    thumbnail_fetcher=lambda *a: b'')
    qtbot.addWidget(window)
    window._start_analysis_for_url('https://example.test/video')
    qtbot.waitUntil(lambda: bool(observed), timeout=3000)
    qtbot.waitUntil(lambda: all(not thread.isRunning() for thread in window._threads), timeout=3000)
    assert observed == [True]
    qtbot.waitUntil(lambda: not window._threads and not window._workers, timeout=3000)
