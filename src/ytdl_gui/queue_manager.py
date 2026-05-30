from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from uuid import uuid4


class TaskStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    PAUSED = "paused"
    CANCELING_FOR_RETRY = "canceling_for_retry"
    FINISHED = "finished"
    FAILED = "failed"
    CANCELED = "canceled"


@dataclass
class DownloadTask:
    url: str
    title: str
    task_id: str = field(default_factory=lambda: uuid4().hex)
    status: TaskStatus = TaskStatus.PENDING
    progress: float = 0.0
    speed: str = ""
    eta: str = ""
    error: str = ""


class QueueManager:
    def __init__(self, max_concurrency: int = 2):
        if max_concurrency < 1 or max_concurrency > 5:
            raise ValueError("最大并发数必须在 1 到 5 之间")
        self.max_concurrency = max_concurrency
        self._tasks: list[DownloadTask] = []

    def add(self, task: DownloadTask) -> str:
        if any(existing.task_id == task.task_id for existing in self._tasks):
            raise ValueError(f"任务 ID 已存在：{task.task_id}")
        self._tasks.append(task)
        return task.task_id

    def get(self, task_id: str) -> DownloadTask:
        for task in self._tasks:
            if task.task_id == task_id:
                return task
        raise KeyError(f"未找到任务：{task_id}")

    def list(self) -> list[DownloadTask]:
        return list(self._tasks)

    def start_ready_tasks(self) -> list[str]:
        running = sum(1 for task in self._tasks if task.status == TaskStatus.RUNNING)
        slots = self.max_concurrency - running
        started: list[str] = []
        for task in self._tasks:
            if slots <= 0:
                break
            if task.status == TaskStatus.PENDING:
                task.status = TaskStatus.RUNNING
                started.append(task.task_id)
                slots -= 1
        return started

    def pause(self, task_id: str) -> None:
        task = self.get(task_id)
        if task.status == TaskStatus.PENDING:
            task.status = TaskStatus.PAUSED
        elif task.status == TaskStatus.RUNNING:
            task.status = TaskStatus.CANCELING_FOR_RETRY

    def resume(self, task_id: str) -> None:
        task = self.get(task_id)
        if task.status == TaskStatus.PAUSED:
            task.status = TaskStatus.PENDING

    def mark_failed(self, task_id: str, error: str) -> None:
        task = self.get(task_id)
        if task.status == TaskStatus.FINISHED:
            raise ValueError(f"任务已完成，不能标记为失败：{task_id}")
        task.status = TaskStatus.FAILED
        task.error = error

    def mark_finished(self, task_id: str) -> None:
        task = self.get(task_id)
        if task.status != TaskStatus.RUNNING:
            raise ValueError(f"只能将运行中的任务标记为完成：{task_id}")
        task.status = TaskStatus.FINISHED

    def cancel(self, task_id: str) -> None:
        task = self.get(task_id)
        task.status = TaskStatus.CANCELED

    def retry(self, task_id: str) -> None:
        task = self.get(task_id)
        if task.status not in {TaskStatus.FAILED, TaskStatus.CANCELED}:
            raise ValueError(f"只能重试失败或已取消的任务：{task_id}")
        task.status = TaskStatus.PENDING
        task.error = ""
        task.progress = 0.0
        task.speed = ""
        task.eta = ""

    def clear_completed(self) -> list[str]:
        completed_statuses = {TaskStatus.FINISHED, TaskStatus.CANCELED}
        removed = [task.task_id for task in self._tasks if task.status in completed_statuses]
        self._tasks = [task for task in self._tasks if task.status not in completed_statuses]
        return removed
