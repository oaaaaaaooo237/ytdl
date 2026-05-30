import pytest

from ytdl_gui.queue_manager import DownloadTask, QueueManager, TaskStatus


def test_queue_starts_up_to_concurrency():
    queue = QueueManager(max_concurrency=2)
    ids = [queue.add(DownloadTask(url=f"https://example.com/{index}", title=f"Item {index}")) for index in range(3)]

    started = queue.start_ready_tasks()

    assert started == ids[:2]
    assert queue.get(ids[0]).status == TaskStatus.RUNNING
    assert queue.get(ids[2]).status == TaskStatus.PENDING


def test_running_tasks_occupy_concurrency_slots():
    queue = QueueManager(max_concurrency=2)
    ids = [queue.add(DownloadTask(url=f"https://example.com/{index}", title=f"Item {index}")) for index in range(3)]
    queue.get(ids[0]).status = TaskStatus.RUNNING

    started = queue.start_ready_tasks()

    assert started == [ids[1]]
    assert queue.get(ids[2]).status == TaskStatus.PENDING


def test_legal_upper_bound_concurrency_starts_five_tasks():
    queue = QueueManager(max_concurrency=5)
    ids = [queue.add(DownloadTask(url=f"https://example.com/{index}", title=f"Item {index}")) for index in range(6)]

    started = queue.start_ready_tasks()

    assert started == ids[:5]
    assert [queue.get(task_id).status for task_id in ids[:5]] == [TaskStatus.RUNNING] * 5
    assert queue.get(ids[5]).status == TaskStatus.PENDING


def test_pending_pause_prevents_start():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))

    queue.pause(task_id)
    started = queue.start_ready_tasks()

    assert started == []
    assert queue.get(task_id).status == TaskStatus.PAUSED


def test_active_pause_becomes_cancel_retry_state():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    queue.start_ready_tasks()

    queue.pause(task_id)

    assert queue.get(task_id).status == TaskStatus.CANCELING_FOR_RETRY


def test_retry_failed_task_returns_to_pending():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    queue.mark_failed(task_id, "network")
    queue.get(task_id).progress = 45.0
    queue.get(task_id).speed = "1.2 MB/s"
    queue.get(task_id).eta = "00:12"

    queue.retry(task_id)

    assert queue.get(task_id).status == TaskStatus.PENDING
    assert queue.get(task_id).error == ""
    assert queue.get(task_id).progress == 0.0
    assert queue.get(task_id).speed == ""
    assert queue.get(task_id).eta == ""


def test_retry_canceled_task_returns_to_pending():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    queue.cancel(task_id)

    queue.retry(task_id)

    assert queue.get(task_id).status == TaskStatus.PENDING


def test_retry_rejects_running_or_canceling_task():
    queue = QueueManager(max_concurrency=2)
    running_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    canceling_id = queue.add(DownloadTask(url="https://example.com/b", title="B"))
    queue.start_ready_tasks()
    queue.pause(canceling_id)

    with pytest.raises(ValueError, match="只能重试"):
        queue.retry(running_id)
    with pytest.raises(ValueError, match="只能重试"):
        queue.retry(canceling_id)

    assert queue.get(running_id).status == TaskStatus.RUNNING
    assert queue.get(canceling_id).status == TaskStatus.CANCELING_FOR_RETRY


def test_resume_only_paused_task_returns_to_pending():
    queue = QueueManager(max_concurrency=1)
    paused_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    running_id = queue.add(DownloadTask(url="https://example.com/b", title="B"))
    queue.pause(paused_id)
    queue.get(running_id).status = TaskStatus.CANCELING_FOR_RETRY

    queue.resume(paused_id)
    queue.resume(running_id)

    assert queue.get(paused_id).status == TaskStatus.PENDING
    assert queue.get(running_id).status == TaskStatus.CANCELING_FOR_RETRY


def test_missing_task_raises_readable_chinese_error():
    queue = QueueManager()

    try:
        queue.get("missing")
    except KeyError as exc:
        assert "未找到任务" in str(exc)
        assert "missing" in str(exc)
    else:
        raise AssertionError("应当抛出 KeyError")


def test_invalid_concurrency_raises_chinese_error():
    try:
        QueueManager(max_concurrency=0)
    except ValueError as exc:
        assert "并发" in str(exc)
        assert "1 到 5" in str(exc)
    else:
        raise AssertionError("应当抛出 ValueError")


def test_invalid_concurrency_above_upper_bound_raises_chinese_error():
    with pytest.raises(ValueError, match="1 到 5"):
        QueueManager(max_concurrency=6)


def test_add_rejects_duplicate_task_id():
    queue = QueueManager()
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A", task_id="same-id"))

    with pytest.raises(ValueError, match="任务 ID 已存在"):
        queue.add(DownloadTask(url="https://example.com/b", title="B", task_id=task_id))


def test_mark_finished_and_cancel_update_task_status():
    queue = QueueManager(max_concurrency=1)
    finished_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    canceled_id = queue.add(DownloadTask(url="https://example.com/b", title="B"))
    queue.start_ready_tasks()

    queue.mark_finished(finished_id)
    queue.cancel(canceled_id)

    assert queue.get(finished_id).status == TaskStatus.FINISHED
    assert queue.get(canceled_id).status == TaskStatus.CANCELED


@pytest.mark.parametrize(
    "status",
    [TaskStatus.PAUSED, TaskStatus.FAILED, TaskStatus.CANCELED, TaskStatus.CANCELING_FOR_RETRY],
)
def test_mark_finished_rejects_non_running_tasks(status):
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    queue.get(task_id).status = status

    with pytest.raises(ValueError, match="只能将运行中的任务标记为完成"):
        queue.mark_finished(task_id)

    assert queue.get(task_id).status == status


def test_mark_failed_does_not_corrupt_finished_task():
    queue = QueueManager(max_concurrency=1)
    task_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    queue.start_ready_tasks()
    queue.mark_finished(task_id)

    with pytest.raises(ValueError, match="已完成"):
        queue.mark_failed(task_id, "late error")

    assert queue.get(task_id).status == TaskStatus.FINISHED
    assert queue.get(task_id).error == ""


def test_clear_completed_removes_finished_and_canceled_tasks_only():
    queue = QueueManager(max_concurrency=1)
    finished_id = queue.add(DownloadTask(url="https://example.com/a", title="A"))
    canceled_id = queue.add(DownloadTask(url="https://example.com/b", title="B"))
    failed_id = queue.add(DownloadTask(url="https://example.com/c", title="C"))
    pending_id = queue.add(DownloadTask(url="https://example.com/d", title="D"))
    queue.mark_failed(failed_id, "network")
    queue.get(finished_id).status = TaskStatus.RUNNING
    queue.mark_finished(finished_id)
    queue.cancel(canceled_id)

    removed = queue.clear_completed()

    assert removed == [finished_id, canceled_id]
    assert [task.task_id for task in queue.list()] == [failed_id, pending_id]
