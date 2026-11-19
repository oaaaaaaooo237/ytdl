from pathlib import Path

from ytdl_gui.queue_store import QueueRecoveryWorker, QueueStore
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.workers import DownloadRequest, DownloadWorker


def test_scheduled_task_persists_and_runs_when_due(qtbot, tmp_path, monkeypatch):
    clock = [1000]
    monkeypatch.setattr("ytdl_gui.ui.main_window.time.time", lambda: clock[0])
    store = QueueStore(tmp_path)
    task_id = "a" * 32
    request = DownloadRequest(
        "https://example.test/sample.mp4", Path("yt-dlp.exe"),
        tmp_path / "sample.mp4", "best", expected_title="样本",
    )
    store.save({task_id: (request, {"scheduled_at": 2000}, "scheduled")})
    workers = []

    def run_worker(worker):
        if isinstance(worker, QueueRecoveryWorker):
            worker.run()
        else:
            workers.append(worker)

    window = MainWindow(queue_store=store, worker_runner=run_worker)
    qtbot.addWidget(window)
    assert window._download_task_states[task_id] == "scheduled"
    assert workers == []

    clock[0] = 2001
    window._run_scheduled_tasks()

    assert window._download_task_states[task_id] == "running"
    assert len(workers) == 1 and isinstance(workers[0], DownloadWorker)
    assert workers[0].request.ytdlp_path == window._active_ytdlp_path()
