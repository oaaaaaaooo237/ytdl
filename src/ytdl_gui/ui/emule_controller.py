from collections import deque
from datetime import datetime
from subprocess import SubprocessError

from PySide6.QtCore import QObject, QTimer, Signal, Slot

from ytdl_gui.emule import EmuleError, ed2k_file_hash
from ytdl_gui.history_store import HistoryRecord


class _EmuleWorker(QObject):
    finished = Signal(object)
    failed = Signal(str)

    def __init__(self, operation):
        super().__init__()
        self.operation = operation

    @Slot()
    def run(self):
        try:
            result = self.operation()
        except EmuleError as error:
            self.failed.emit(str(error))
        except (OSError, ValueError, KeyError, TypeError, SubprocessError):
            self.failed.emit("电驴请求失败，请重试")
        else:
            self.finished.emit(result)


class EmuleController(QObject):
    def __init__(self, window, engine):
        super().__init__(window)
        self.window = window
        self.engine = engine
        self.page = window.emule_page
        self.tasks = set()
        self.completion_ack = set()
        self.active = False
        self.busy = False
        self.pending = deque()
        self.stopping = False
        self.timer = QTimer(self)
        self.timer.setInterval(1500)
        self.timer.timeout.connect(self.poll)
        self.page.search_requested.connect(self.search)
        self.page.download_requested.connect(self.download_result)
        self.page.back_requested.connect(window.show_download_page)
        self.page.tabs.currentChanged.connect(self._check_selected_search)

    def _submit(self, operation, apply, failed, *, during_close=False) -> None:
        if self.busy:
            self.pending.append((operation, apply, failed))
            return
        self.busy = True
        worker = _EmuleWorker(operation)

        def complete(callback, result):
            def finish():
                self.busy = False
                if not self.window._closing or during_close:
                    callback(result)
                    if self.pending and not self.busy:
                        self._submit(*self.pending.popleft())
            self.window._run_on_ui_thread(finish)

        worker.finished.connect(lambda result: complete(apply, result))
        worker.failed.connect(lambda message: complete(failed, message))
        self.window._worker_runner(worker)

    def _begin(self):
        self.active = True

    def restore(self):
        if not self.window._closing:
            self._begin()
            self.poll()

    def open_from_address_box(self):
        address = self.window.download_page.url_input.toPlainText().strip()
        self.window.nav.setCurrentRow(0)
        self.window.download_stack.setCurrentWidget(self.page)
        if address and "://" not in address:
            self.search(address, self.page.network.currentData())
        else:
            self.page.query.setFocus()

    def search(self, query, network):
        self._begin()
        tab = self.page.add_search(query, network)

        def started(search_id):
            self.timer.start()
            if not tab.closed:
                tab.search_id = search_id

        def failed(message):
            if not tab.closed:
                tab.status.setText(message)

        self._submit(lambda: self.engine.search(query, network), started, failed)

    def poll(self):
        if self.window._closing or self.busy or self.pending:
            return
        tabs = []
        for index in range(self.page.tabs.count()):
            tab = self.page.tabs.widget(index)
            if tab.search_id is not None and not tab.settled:
                tabs.append((tab, tab.search_id))
        acknowledgements = tuple(self.completion_ack)

        def fetch():
            api = self.engine.start()
            status = api.call("GET", "/status")
            for file_hash in acknowledgements:
                try:
                    api.call("POST", "/downloads_clear_completed", {"hash": file_hash})
                except EmuleError as error:
                    if error.status not in (404, 409):
                        raise
            updates = []
            for tab, search_id in tabs:
                try:
                    updates.append((tab, api.results(search_id)))
                except EmuleError as error:
                    if error.status != 404:
                        raise
                    updates.append((tab, None))
            return updates, api.call("GET", "/downloads?status=all&limit=1000000000")["downloads"], status

        def failed(message):
            self.timer.stop()
            self.window.queue_page.show_notice(message)

        self._submit(fetch, self._update, failed)

    def _check_selected_search(self, index):
        tab = self.page.tabs.widget(index)
        if tab and tab.search_id is not None and not tab.expired:
            tab.settled = False

    def _update(self, snapshot):
        if self.active and not self.timer.isActive():
            self.timer.start()
        updates, downloads, status = snapshot
        connected = [label for key, label in (("ed2k", "eD2k"), ("kad", "Kad"))
                     if status.get(key, {}).get("state") == "connected"]
        self.window.settings_page.emule_status.setText("、".join(connected) if connected else "尚未连接")
        for tab, results in updates:
            if tab.closed:
                continue
            if results is None:
                tab.settled = True
                self.page.show_expired(tab)
            else:
                phase = results["progress"]["state"]
                self.page.show_results(tab, results["results"], phase)
                tab.settled = phase != "running"
        current = set()
        self.completion_ack.intersection_update(row["hash"] for row in downloads if row["status"] == "completed")
        for row in downloads:
            task_id = "ed2k:" + row["hash"]
            if row["status"] == "completed":
                self._record(task_id, row)
                continue
            current.add(task_id)
            if task_id not in self.tasks:
                self.window.queue_page.add_task(task_id, row["name"])
            speed = row["speed_bytes_per_second"]
            state = {
                "paused": "已暂停", "stopped": "已暂停", "hashing": "正在校验",
                "allocating": "正在分配空间", "completing": "正在完成",
                "insufficient_disk": "失败", "erroneous": "失败", "unknown": "失败",
            }.get(row["status"], "下载中" if speed else "等待来源")
            self.window.queue_page.update_task(
                task_id, state, row["progress"]["percent"], f"{speed / 1024:.1f} KB/s",
            )
        self.window.queue_page.remove_tasks(list(self.tasks - current))
        self.tasks = current

    def _record(self, task_id, row):
        store = self.window.history_store
        if not store or row["hash"] in self.completion_ack:
            return
        records = store.list()
        if any(record.task_id == task_id for record in records):
            self.completion_ack.add(row["hash"])
            return
        root = self.engine.incoming_dir.resolve()
        candidate = (root / row["name"]).resolve()
        if candidate.parent != root or not candidate.is_file():
            return
        size = candidate.stat().st_size
        if size != row.get("size_bytes", size):
            return
        store.add(HistoryRecord(row["name"], row["ed2k_link"], str(candidate), "电驴", "eD2k", "",
                                "finished", datetime.now().isoformat(timespec="seconds"), size, task_id=task_id))
        self.completion_ack.add(row["hash"])
        records = store.list()
        self.window.history_page.load_records(records)
        self.window.queue_page.load_history_records(records)

    def _queue(self, operation, failed=None):
        self._begin()

        def queued(_):
            self.timer.start()
            self.window.queue_page.show_notice("已加入电驴队列")
            self.window.show_tasks()
            self.poll()

        self._submit(operation, queued, failed or self._notice)

    def _notice(self, message):
        self.window._information_dialog(self.window, "电驴", message)

    def download_result(self, tab, item):
        search_id = tab.search_id

        def download():
            api = self.engine.start()
            if not any(row["hash"] == item["hash"] for row in api.results(search_id)["results"]):
                raise EmuleError("此结果已失效，请重新搜索。", status=404)
            api.download_result(item["hash"])

        def failed(message):
            if not tab.closed:
                tab.settled = False
            self._notice(message)

        self._queue(download, failed)

    def add_link(self, address):
        if not ed2k_file_hash(address):
            self._notice("请输入有效的 eD2k 文件链接。")
            return
        if not self.window._confirmation_dialog(self.window, "电驴下载", "电驴运行时可能向同网用户上传已下载片段及电驴保存目录内的文件。继续下载？"):
            return

        self._queue(lambda: self.engine.add_link(address))

    def action(self, task_id, action):
        if action == "cancel" and not self.window._confirmation_dialog(
            self.window, "取消电驴下载", "取消后将清除该任务的未完成文件。继续取消？",
        ):
            return
        route = "/downloads/" + task_id.removeprefix("ed2k:")
        method = "DELETE" if action == "cancel" else "PATCH"
        data = None if action == "cancel" else {"action": "resume" if action == "retry" else "pause"}
        self._submit(lambda: self.engine.start().call(method, route, data),
                     lambda _: self.poll(), self._notice)

    def pause_all(self):
        if not self.tasks:
            return
        hashes = [task.removeprefix("ed2k:") for task in self.tasks]
        self._submit(lambda: self.engine.start().call(
            "PATCH", "/downloads", {"hashes": hashes, "action": "pause"},
        ), lambda _: self.poll(), self._notice)

    def close(self) -> bool:
        self.timer.stop()
        self.pending.clear()
        if self.active and not self.stopping:
            if self.busy:
                return False
            self.stopping = True
            self._submit(self._stop_engine, self._store_completions, lambda _: None, during_close=True)
        return not self.active or not self.busy

    def _stop_engine(self):
        if hasattr(self.engine, "completed_before_stop"):
            return self.engine.completed_before_stop()
        self.engine.stop()
        return []

    def _store_completions(self, rows):
        for row in rows:
            self._record("ed2k:" + row["hash"], row)

    def shutdown_for_quit(self):
        if self.active:
            try:
                self._store_completions(self._stop_engine())
            except (EmuleError, OSError, SubprocessError):
                pass
