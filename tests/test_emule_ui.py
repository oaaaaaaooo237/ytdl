from ytdl_gui.ui.pages.emule_search_page import EmuleSearchPage
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.config_store import ConfigStore
from ytdl_gui.history_store import HistoryStore


def test_queries_keep_independent_results_and_count(qtbot):
    page = EmuleSearchPage()
    qtbot.addWidget(page)
    first = page.add_search("这是一个超过页签显示长度的测试关键词", "global")
    second = page.add_search("另一组", "kad")
    assert page.tabs.count() == 2
    page.show_results(first, [
        {"name": "first", "hash": "a", "size_bytes": 10, "sources": {"complete": 1, "total": 8}},
        {"name": "top", "hash": "b", "size_bytes": 20, "sources": {"complete": 4, "total": 5}},
    ], "finished")
    page.show_results(second, [], "finished")
    page.tabs.setCurrentWidget(first)
    assert "… (2)" in page.tabs.tabText(0)
    assert first.table.item(0, 0).text() == "top"
    first.table.selectRow(0)
    page.tabs.setCurrentWidget(second)
    page.tabs.setCurrentWidget(first)
    assert first.table.currentRow() == 0
    assert second.table.rowCount() == 0


def test_donkey_button_submits_address_box_text(qtbot):
    class Engine:
        def search(self, query, network):
            requests.append((query, network))
            return 15

        def stop(self):
            pass

    requests = []
    window = MainWindow(emule_engine=Engine(), worker_runner=lambda worker: worker.run())
    qtbot.addWidget(window)
    window.download_page.url_input.setPlainText("Debian ISO")
    window.download_page.emule_button.click()
    assert requests == [("Debian ISO", "all")]
    assert window.stack.currentWidget() is window.emule_page
    assert window.emule_page.tabs.currentWidget().search_id == 15


def test_emule_settings_store_independently_from_existing_settings(qtbot, tmp_path):
    store = ConfigStore(tmp_path)
    window = MainWindow(config_store=store)
    qtbot.addWidget(window)
    page = window.settings_page
    page.emule_network.setCurrentIndex(page.emule_network.findData("kad"))
    page.emule_upload.setValue(72)
    page.emule_download.setValue(2048)
    page.concurrency.setValue(3)
    saved = store.load()
    assert saved.emule_network == "kad"
    assert saved.emule_upload == 72
    assert saved.emule_download == 2048
    assert saved.max_concurrency == 3


def test_direct_ed2k_link_and_queue_actions(qtbot):
    calls = []
    file_hash = "0123456789abcdef0123456789abcdef"
    link = f"ed2k://|file|fixture.bin|3|{file_hash}|/"

    class Engine:
        def start(self):
            return self

        def add_link(self, address):
            self.call("POST", "/downloads", {"links": [address]})

        def call(self, method, route, payload=None):
            calls.append((method, route, payload))
            if route == "/status":
                return {}
            if route == "/downloads?status=all&limit=1000000000":
                return {"downloads": [{
                    "hash": file_hash, "name": "fixture.bin", "status": "paused",
                    "speed_bytes_per_second": 0, "progress": {"percent": 1.0},
                }]}
            return {"results": [{"ok": True}]}

        def stop(self):
            pass

    window = MainWindow(emule_engine=Engine(), worker_runner=lambda worker: worker.run(),
                        confirmation_dialog=lambda *_args: True)
    qtbot.addWidget(window)
    window.download_page.url_input.setPlainText(link)
    window.download_page.start_button.click()
    assert ("POST", "/downloads", {"links": [link]}) in calls
    assert window.queue_page.table.item(0, 1).text() == "已暂停"
    window.handle_queue_action("ed2k:" + file_hash, "retry")
    window.handle_queue_action("ed2k:" + file_hash, "cancel")
    assert ("PATCH", "/downloads/" + file_hash, {"action": "resume"}) in calls
    assert ("DELETE", "/downloads/" + file_hash, None) in calls


def test_completed_file_enters_history_once(qtbot, tmp_path):
    class Engine:
        incoming_dir = tmp_path

    store = HistoryStore(tmp_path)
    window = MainWindow(history_store=store, emule_engine=Engine())
    qtbot.addWidget(window)
    payload = {"hash": "document", "name": "file.bin", "status": "completed", "ed2k_link": "ed2k://file"}
    window.emule._update(([], [payload], {}))
    assert store.list() == []
    (tmp_path / "file.bin").write_bytes(b"abc")
    window.emule._update(([], [payload], {}))
    window.emule._update(([], [payload], {}))
    assert [record.file_size_bytes for record in store.list()] == [3]
    store.clear()
    window.emule._update(([], [payload], {}))
    assert store.list() == []


def test_queue_action_waits_for_pending_search_request(qtbot):
    calls = []
    workers = []

    class Engine:
        def search(self, query, network):
            calls.append("search")
            return 7

        def start(self):
            return self

        def call(self, method, route, body):
            calls.append(body["action"])

    window = MainWindow(emule_engine=Engine(), worker_runner=workers.append)
    qtbot.addWidget(window)
    window.emule.search("Debian", "all")
    window.handle_queue_action("ed2k:" + "a" * 32, "pause")
    assert len(workers) == 1
    workers.pop(0).run()
    workers.pop(0).run()
    assert calls == ["search", "pause"]
    window.emule.active = False
