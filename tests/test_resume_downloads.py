import importlib
import json
import os
import subprocess
import sys
import time
from pathlib import Path

import pytest

from ytdl_gui.workers import DownloadRequest, DownloadWorker
from ytdl_gui.ytdlp_runner import YtdlpCommandBuilder


def store_module():
    assert importlib.util.find_spec('ytdl_gui.queue_store'), '持久队列存储尚未实现'
    return importlib.import_module('ytdl_gui.queue_store')


def request(tmp_path):
    return DownloadRequest('https://example.test/page?secret=token', Path('yt-dlp.exe'),
                           tmp_path / 'original' / '%(title)s.%(ext)s', '137+140',
                           cookies_path=tmp_path / 'private-cookies.txt', subtitle_action='file')


def test_dpapi_store_roundtrip_excludes_cookie_path_and_preserves_exact_request(tmp_path):
    module = store_module()
    store = module.QueueStore(tmp_path)
    task_id = 'a' * 32
    store.save({task_id: (request(tmp_path), {'title': 'Private title'}, 'running')})
    raw = store.path.read_bytes()
    for secret in [b'example.test', b'token', b'Private title', b'private-cookies']:
        assert secret not in raw
    restored = module.QueueStore(tmp_path).load()
    item = restored[task_id]
    assert item['state'] == 'paused'
    assert item['request'].format_id == '137+140'
    assert item['request'].output_template == request(tmp_path).output_template
    assert item['request'].subtitle_action == 'file'
    assert item['request'].cookies_path is None
    assert item['request'].uses_cookies is True


def test_atomic_save_failure_preserves_previous_queue(tmp_path, monkeypatch):
    module = store_module()
    store = module.QueueStore(tmp_path)
    store.save({'a' * 32: (request(tmp_path), {}, 'paused')})
    before = store.path.read_bytes()
    monkeypatch.setattr(module.os, 'replace', lambda *args: (_ for _ in ()).throw(OSError('disk failure')))
    with pytest.raises(OSError):
        store.save({})
    assert store.path.read_bytes() == before


def test_terminal_cancel_does_not_restore_and_bad_ids_rejected(tmp_path):
    store = store_module().QueueStore(tmp_path)
    store.save({'a' * 32: (request(tmp_path), {}, 'cancel_requested')})
    assert store.load() == {}
    with pytest.raises(ValueError):
        store.save({'../escape': (request(tmp_path), {}, 'paused')})


def test_resume_command_keeps_home_temp_separate_and_retry_bounded(tmp_path):
    assert 'temp_dir' in __import__('inspect').signature(YtdlpCommandBuilder.download_command).parameters
    command = YtdlpCommandBuilder(Path('yt-dlp.exe')).download_command(
        'https://example.test/page', tmp_path / 'out' / '%(title)s.%(ext)s', '137+140',
        temp_dir=tmp_path / 'tasks' / ('a' * 32))
    assert command[command.index('-o') + 1] == '%(title)s.%(ext)s'
    assert 'home:' + str(tmp_path / 'out') in command
    assert 'temp:' + str(tmp_path / 'tasks' / ('a' * 32)) in command
    assert '--continue' in command and '--part' in command
    assert command[command.index('--socket-timeout') + 1] == '15'
    assert command[command.index('--retries') + 1] == '2'


def test_stop_before_run_never_spawns(tmp_path):
    calls = []
    worker = DownloadWorker(request(tmp_path), popen_factory=lambda *args, **kwargs: calls.append(args))
    assert hasattr(worker, 'stop'), '需明确停止原因'
    stopped = []
    worker.stopped.connect(stopped.append)
    worker.stop('pause')
    worker.run()
    assert calls == []
    assert stopped == ['pause']


@pytest.mark.parametrize('error,network', [
    ('ERROR: HTTP Error 403: Forbidden', False), ('ERROR: HTTP Error 404: Not Found', False),
    ('ERROR: Requested format is not available', False), ('ERROR: Connection reset by peer', True),
    ('ERROR: [Errno 11001] getaddrinfo failed', True), ('ERROR: The read operation timed out', True),
])
def test_network_errors_are_distinct_from_site_errors(error, network):
    module = importlib.import_module('ytdl_gui.workers')
    assert hasattr(module, 'is_network_download_error')
    assert module.is_network_download_error(error) is network


@pytest.mark.skipif(sys.platform != 'win32', reason='Windows JobObject')
def test_process_tree_stops_and_cannot_write_after_stop(tmp_path):
    assert importlib.util.find_spec('ytdl_gui.process_tree'), '需要受控进程树'
    from ytdl_gui.process_tree import ManagedProcess
    target = tmp_path / 'writer.txt'
    child = "import time; f=open(" + repr(str(target)) + ", 'ab', buffering=0);\nwhile True: f.write(b'x'); time.sleep(.03)"
    parent = 'import subprocess,sys,time; subprocess.Popen([sys.executable,"-c",' + repr(child) + ']); time.sleep(60)'
    process = ManagedProcess([sys.executable, '-c', parent])
    try:
        deadline = time.monotonic() + 5
        while not target.exists() and time.monotonic() < deadline:
            time.sleep(.03)
        assert target.exists()
        process.terminate()
        process.wait(timeout=5)
        size = target.stat().st_size
        time.sleep(.3)
        assert target.stat().st_size == size
    finally:
        process.close()


def window_fixture(qtbot, tmp_path, store=None):
    from ytdl_gui.config_store import AppConfig, ConfigStore
    from ytdl_gui.ui.main_window import MainWindow
    from ytdl_gui.queue_store import QueueRecoveryWorker, QueueStore
    started = []
    config = ConfigStore(tmp_path)
    config.save(AppConfig(default_save_dir=str(tmp_path), max_concurrency=1))
    def run(worker):
        if isinstance(worker, QueueRecoveryWorker):
            worker.run()
        else:
            started.append(worker)
    window = MainWindow(config_store=config, queue_store=store or QueueStore(tmp_path), worker_runner=run)
    qtbot.addWidget(window)
    window.apply_analysis_result('https://example.test/watch', {'title': 'Original', 'formats': [
        {'format_id': '22', 'height': 720, 'ext': 'mp4', 'vcodec': 'avc1', 'acodec': 'mp4a', 'fps': 30}]})
    return window, started


def test_pause_holds_slot_until_worker_stopped_and_rejects_early_resume(qtbot, tmp_path):
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    window.start_download()
    task = next(iter(window._download_task_states))
    window.pause_queue_task(task)
    assert window.queue_page.table.item(0, 1).text() == '正在暂停'
    window.retry_queue_task(task)
    assert len(started) == 1
    started[0].stopped.emit('pause')
    assert window._download_task_states[task] == 'paused'
    assert len(started) == 2
    window.retry_queue_task(task)
    assert len(started) == 2  # other task still occupies the single slot


def test_new_window_recovers_paused_without_spawning_and_cancel_never_revives(qtbot, tmp_path):
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    recovered, new_started = window_fixture(qtbot, tmp_path)
    assert new_started == []
    assert recovered._download_task_states[task] == 'paused'
    original = window._download_requests_by_task[task]
    recovered.save_folder_path = str(tmp_path / 'other')
    recovered.retry_queue_task(task)
    assert new_started[0].request.output_template == original.output_template
    assert new_started[0].request.format_id == original.format_id
    recovered.cancel_queue_task(task)
    assert store_module().QueueStore(tmp_path).load() == {}
    new_started[0].stopped.emit('cancel')
    assert recovered._download_task_states[task] == 'canceled'


def test_failed_save_prevents_download(qtbot, tmp_path, monkeypatch):
    store = store_module().QueueStore(tmp_path)
    window, started = window_fixture(qtbot, tmp_path, store)
    monkeypatch.setattr(store, 'save', lambda *args: (_ for _ in ()).throw(OSError('disk full')))
    window.start_download()
    assert started == []
    assert '保存失败' in window.download_page.status_label.text()


def test_network_return_only_restarts_waiting_tasks(qtbot, tmp_path):
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    started[0].network_failed.emit()
    assert window._download_task_states[task] == 'waiting_network'
    window.network_changed(False)
    window.network_changed(True)
    assert len(started) == 2
    started[1].failed.emit('网站拒绝 403')
    window.network_changed(False)
    window.network_changed(True)
    assert len(started) == 2


def test_offline_stop_does_not_hide_permanent_http_error(tmp_path):
    worker = None
    class Process:
        @property
        def stdout(self):
            yield 'ERROR: HTTP Error 403: Forbidden\n'
            worker.stop('offline')
        def poll(self):
            return None
        def terminate(self):
            pass
        def wait(self):
            return 1
    worker = DownloadWorker(request(tmp_path), popen_factory=lambda *a, **k: Process())
    failed, stopped = [], []
    worker.failed.connect(failed.append)
    worker.stopped.connect(stopped.append)
    worker.run()
    assert failed and stopped == []


@pytest.mark.skipif(sys.platform != 'win32', reason='Windows JobObject')
def test_owner_crash_kills_descendant_writer(tmp_path):
    target = tmp_path / 'crash-writer.txt'
    child = "import time; f=open(" + repr(str(target)) + ", 'ab', buffering=0);\nwhile True: f.write(b'x'); time.sleep(.02)"
    owner_script = ('from ytdl_gui.process_tree import ManagedProcess; import sys,time; '
                    'p=ManagedProcess([sys.executable,"-c",' + repr(child) + ']); time.sleep(60)')
    owner = subprocess.Popen([sys.executable, '-c', owner_script], creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        deadline = time.monotonic() + 5
        while not target.exists() and time.monotonic() < deadline:
            time.sleep(.03)
        assert target.exists()
        owner.kill()
        owner.wait(timeout=5)
        time.sleep(.2)
        size = target.stat().st_size
        time.sleep(.3)
        assert target.stat().st_size == size
    finally:
        if owner.poll() is None:
            owner.kill()
        owner.wait(timeout=5)


def test_resume_reloads_current_cookies_only_when_originally_used(qtbot, tmp_path):
    from dataclasses import replace
    from ytdl_gui.config_store import AppConfig
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    window.pause_queue_task(task)
    started[0].stopped.emit('pause')
    window._download_requests_by_task[task] = replace(window._download_requests_by_task[task], uses_cookies=True)
    window.retry_queue_task(task)
    assert len(started) == 1
    assert 'cookies.txt' in window.download_page.status_label.text()
    cookies = tmp_path / 'current.txt'
    cookies.write_text('# Netscape HTTP Cookie File\n.example.test\tTRUE\t/\tFALSE\t0\tsession\tprivate\n')
    window.config_store.save(AppConfig(default_save_dir=str(tmp_path), cookies_path=str(cookies)))
    window.retry_queue_task(task)
    assert started[-1].request.cookies_path == cookies


def test_close_waits_for_qthread_and_stops_active_download(qtbot, tmp_path):
    import threading
    from ytdl_gui.ui.main_window import MainWindow
    entered, exit_process = threading.Event(), threading.Event()
    class Process:
        @property
        def stdout(self):
            entered.set()
            exit_process.wait(3)
            return iter([])
        def poll(self):
            return None
        def terminate(self):
            exit_process.set()
        def wait(self):
            return 1
    window = MainWindow(download_popen_factory=lambda *a, **k: Process())
    qtbot.addWidget(window)
    task = 'a' * 32
    window.queue_page.add_task(task, 'Close test')
    window._download_requests_by_task[task] = request(tmp_path)
    window._start_download_worker(task, request(tmp_path), 'test')
    qtbot.waitUntil(entered.is_set)
    window.close()
    qtbot.waitUntil(lambda: not window._threads, timeout=5000)
    assert window._download_task_states[task] == 'paused'
    assert exit_process.is_set()


def test_burn_resume_finds_already_downloaded_matching_subtitles(tmp_path):
    from ytdl_gui.workers import _find_subtitle_file
    media, subtitle = tmp_path / 'Movie.mp4', tmp_path / 'Movie.en.srt'
    media.write_bytes(b'media')
    subtitle.write_text('subtitle')
    assert _find_subtitle_file(media, {media, subtitle}) == subtitle


def test_about_to_quit_stops_and_joins_workers(qtbot, tmp_path):
    import threading
    from ytdl_gui.ui.main_window import MainWindow
    entered, stopped = threading.Event(), threading.Event()
    class Process:
        @property
        def stdout(self):
            entered.set()
            stopped.wait(3)
            return iter([])
        def poll(self):
            return None
        def terminate(self):
            stopped.set()
        def wait(self):
            return 1
    window = MainWindow(download_popen_factory=lambda *a, **k: Process())
    qtbot.addWidget(window)
    task = 'a' * 32
    window.queue_page.add_task(task, 'Quit test')
    window._download_requests_by_task[task] = request(tmp_path)
    window._start_download_worker(task, request(tmp_path), 'test')
    qtbot.waitUntil(entered.is_set)
    assert hasattr(window, 'shutdown_for_quit')
    window.shutdown_for_quit()
    assert stopped.is_set()
    assert all(not thread.isRunning() for thread in window._threads)


def test_save_failure_at_start_remains_visible_and_does_not_launch(qtbot, tmp_path, monkeypatch):
    store = store_module().QueueStore(tmp_path)
    window, started = window_fixture(qtbot, tmp_path, store)
    original_save, count = store.save, 0
    def fail_second(tasks):
        nonlocal count
        count += 1
        if count == 2:
            raise OSError('disk full')
        original_save(tasks)
    monkeypatch.setattr(store, 'save', fail_second)
    window.start_download()
    assert started == []
    assert '保存失败' in window.download_page.status_label.text()


def test_corrupt_store_blocks_new_downloads_without_overwriting(qtbot, tmp_path):
    store = store_module().QueueStore(tmp_path)
    store.path.write_bytes(b'corrupt')
    window, started = window_fixture(qtbot, tmp_path, store)
    window.start_download()
    assert started == []
    assert store.path.read_bytes() == b'corrupt'


def test_failed_task_restores_as_failed_with_error_and_no_network_retry(qtbot, tmp_path):
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    started[0].failed.emit('网站拒绝访问或所选格式不可用，请检查权限与格式后重试。')
    restored, new_started = window_fixture(qtbot, tmp_path)
    assert restored._download_task_states[task] == 'failed'
    assert restored.queue_page.table.item(0, 1).text() == '失败'
    assert '网站拒绝' in restored.queue_page.notice_label.text()
    restored.network_changed(False)
    restored.network_changed(True)
    assert new_started == []


def test_retry_failed_save_preserves_paused_ui_and_continue_button(qtbot, tmp_path, monkeypatch):
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    window.pause_queue_task(task)
    started[0].stopped.emit('pause')
    monkeypatch.setattr(window.queue_store, 'save', lambda *_: (_ for _ in ()).throw(OSError('disk full')))
    window.retry_queue_task(task)
    assert window._download_task_states[task] == 'paused'
    assert window.queue_page.table.item(0, 1).text() == '已暂停'
    assert not window.queue_page._task_cards[task]['retry'].isHidden()
    assert len(started) == 1


@pytest.mark.parametrize('failure_at', ['stdout', 'resolve', 'subtitle_copy'])
def test_worker_exception_stops_process_and_emits_one_terminal(tmp_path, monkeypatch, failure_at):
    from dataclasses import replace
    module = importlib.import_module('ytdl_gui.workers')
    stopped = []
    media = tmp_path / 'original' / 'Movie.mp4'
    media.parent.mkdir()
    media.write_bytes(b'media')
    subtitle = media.with_suffix('.en.srt')
    subtitle.write_text('subtitle')
    def io_error(*_args, **_kwargs):
        raise OSError('disk/read error secret URL')
    class Process:
        @property
        def stdout(self):
            if failure_at == 'stdout':
                io_error()
            return iter([str(media)])
        def poll(self):
            return None
        def terminate(self):
            stopped.append(True)
        def wait(self):
            return 0
    if failure_at == 'resolve':
        monkeypatch.setattr(module, '_resolve_output_path', io_error)
    if failure_at == 'subtitle_copy':
        monkeypatch.setattr(module, '_copy_subtitle_for_ffmpeg', io_error)
    req = replace(request(tmp_path), subtitle_action='burn' if failure_at == 'subtitle_copy' else 'none', ffmpeg_path=Path('ffmpeg.exe'))
    worker = DownloadWorker(req, popen_factory=lambda *a, **k: Process())
    events = []
    worker.finished.connect(lambda _value: events.append('finished'))
    worker.failed.connect(lambda message: events.append(('failed', message)))
    worker.stopped.connect(lambda _value: events.append('stopped'))
    worker.run()
    assert stopped
    assert len(events) == 1 and events[0][0] == 'failed'
    assert 'secret' not in events[0][1]


def test_quit_has_bounded_total_wait_and_emergency_process_exit(qtbot, monkeypatch):
    from ytdl_gui.ui.main_window import MainWindow
    waits, exits = [], []
    class StalledThread:
        def wait(self, milliseconds=None):
            waits.append(milliseconds)
            return False
    window = MainWindow()
    qtbot.addWidget(window)
    window._threads = [StalledThread()]
    monkeypatch.setattr(os, '_exit', lambda status: exits.append(status))
    window.shutdown_for_quit()
    window._threads = []
    assert waits and 0 < waits[0] <= 20000
    assert exits == [1]


def test_unconfirmed_cleanup_keeps_slot_and_does_not_start_next(qtbot, tmp_path):
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    window.start_download()
    task = next(iter(window._download_task_states))
    started[0].stop_failed.emit('未能确认停止')
    assert window._active_download_count() == 1
    assert len(started) == 1
    assert window._download_task_states[task] == 'stop_failed'


def test_progress_is_persisted_at_most_once_per_second(qtbot, tmp_path, monkeypatch):
    from ytdl_gui.ytdlp_runner import ProgressEvent
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    clock = [100.0]
    monkeypatch.setattr('ytdl_gui.ui.main_window.time.monotonic', lambda: clock[0])
    original_save, writes = window.queue_store.save, []
    def save(tasks):
        writes.append(clock[0])
        original_save(tasks)
    monkeypatch.setattr(window.queue_store, 'save', save)
    window.update_download_progress(task, ProgressEvent(10))
    clock[0] = 100.3
    window.update_download_progress(task, ProgressEvent(12))
    clock[0] = 101.1
    window.update_download_progress(task, ProgressEvent(20))
    assert writes == [100.0, 101.1]
    restored, _ = window_fixture(qtbot, tmp_path)
    assert restored.queue_page.table.item(0, 2).text() == '20.0%'


def test_progress_save_failure_does_not_stop_or_discard_previous_snapshot(qtbot, tmp_path, monkeypatch):
    from ytdl_gui.ytdlp_runner import ProgressEvent
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    before = window.queue_store.path.read_bytes()
    monkeypatch.setattr(window.queue_store, 'save', lambda *_: (_ for _ in ()).throw(OSError('disk full')))
    window.update_download_progress(task, ProgressEvent(30))
    assert window._download_task_states[task] == 'running'
    assert started[0]._stop_reason == ''
    assert window.queue_store.path.read_bytes() == before
    assert '进度保存失败' in window.download_page.status_label.text()


def test_canceled_task_never_restores_after_stop_failure(qtbot, tmp_path):
    window, started = window_fixture(qtbot, tmp_path)
    window.start_download()
    task = next(iter(window._download_task_states))
    window.cancel_queue_task(task)
    assert window.queue_store.load() == {}
    started[0].stop_failed.emit('未能确认停止')
    assert window._active_download_count() == 1
    assert window.queue_page.table.item(0, 1).text() == '停止未确认'
    assert window.queue_store.load() == {}
    restored, new_started = window_fixture(qtbot, tmp_path)
    assert task not in restored._download_task_states
    assert new_started == []


def test_burn_transition_cleanup_timeout_is_stop_failed_not_download_failed(tmp_path, monkeypatch):
    from dataclasses import replace
    module = importlib.import_module('ytdl_gui.workers')
    media = tmp_path / 'Movie.mp4'
    media.write_bytes(b'media')
    media.with_suffix('.en.srt').write_text('subtitle')
    created, closed = [], []
    class Process:
        def __init__(self, *_args, **_kwargs):
            created.append(self)
            self.stdout = iter([str(media)])
        def poll(self):
            return 0
        def terminate(self):
            pass
        def wait(self, timeout=None):
            if timeout is not None:
                raise subprocess.TimeoutExpired('process tree', timeout)
            return 0
        def close(self):
            closed.append(True)
    monkeypatch.setattr(module, 'ManagedProcess', Process)
    worker = DownloadWorker(replace(request(tmp_path), subtitle_action='burn', ffmpeg_path=Path('ffmpeg.exe')))
    events = []
    worker.finished.connect(lambda _: events.append('finished'))
    worker.failed.connect(lambda _: events.append('failed'))
    worker.stop_failed.connect(lambda _: events.append('stop_failed'))
    worker.run()
    assert events == ['stop_failed']
    assert len(created) == 1  # never starts ffmpeg while the old tree is unconfirmed
    assert closed == [True]
