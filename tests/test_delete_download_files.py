import os
import subprocess
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import pytest
from PySide6.QtCore import QTimer
from PySide6.QtWidgets import QApplication, QCheckBox, QMessageBox

from ytdl_gui.history_store import HistoryRecord, HistoryStore
from ytdl_gui.ui.main_window import MainWindow


def record(path, size=0):
    return HistoryRecord("测试", "url", str(path), "video", "mp4", "none", "finished", "today", size)


def window_with_record(qtbot, tmp_path, choice=True):
    path = tmp_path / "video.mp4"
    path.write_bytes(b"video")
    store = HistoryStore(tmp_path / "data")
    store.add(record(path, 5))
    window = MainWindow(history_store=store)
    qtbot.addWidget(window)
    window._confirm_record_deletion = lambda *_: choice
    messages = []
    window._information_dialog = lambda _parent, title, text: messages.append(text)
    return window, store, path, messages


@pytest.mark.parametrize("choice", [None, False, True])
def test_history_delete_respects_choice(qtbot, tmp_path, choice):
    window, store, path, messages = window_with_record(qtbot, tmp_path, choice)
    sentinel = tmp_path / "video.en.srt"
    sentinel.write_text("keep")
    window.delete_history_record(0)
    assert bool(store.list()) == (choice is None)
    assert path.exists() == (choice is not True)
    assert sentinel.read_text() == "keep"
    assert not messages


def test_confirmation_is_unchecked_every_time_and_cancel_returns_none(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)
    assert hasattr(window, "_confirm_record_deletion")
    seen = []
    def dismiss():
        dialog = QApplication.activeModalWidget()
        checkbox = dialog.findChild(QCheckBox)
        seen.append((checkbox.text(), checkbox.isChecked(), dialog.text()))
        checkbox.setChecked(True)
        dialog.reject()
    for _ in range(2):
        QTimer.singleShot(0, dismiss)
        assert window._confirm_record_deletion("删除历史", 1) is None
    assert all(text == "同时删除下载文件" and not checked and "字幕" in description
               for text, checked, description in seen)


def test_missing_file_allows_history_removal(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    path.unlink()
    window.delete_history_record(0)
    assert not store.list()
    assert not messages


@pytest.mark.parametrize("unsafe", ["directory", "relative", "template", "replaced"])
def test_unsafe_path_retains_record(qtbot, tmp_path, unsafe):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    if unsafe == "replaced":
        path.write_bytes(b"replaced file")
    else:
        output = {"directory": tmp_path, "relative": "video.mp4", "template": tmp_path / "%(title)s.mp4"}[unsafe]
        store.clear()
        store.add(record(output))
    window.delete_history_record(0)
    assert store.list()
    assert path.exists()
    assert messages


def test_other_history_reference_protects_file(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.add(record(path))
    window.delete_history_record(0)
    assert len(store.list()) == 2
    assert path.exists()
    assert messages


def test_running_download_protects_file(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    window._download_task_states["active"] = "running"
    window.delete_history_record(0)
    assert store.list() and path.exists() and messages


def test_unlink_failure_retains_record(qtbot, tmp_path, monkeypatch):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    def denied(_path, *args, **kwargs):
        raise PermissionError("in use")
    monkeypatch.setattr("ytdl_gui.file_deletion._delete_file_by_handle", denied)
    window.delete_history_record(0)
    assert store.list() and path.exists() and messages


def test_clear_history_removes_only_successful_records(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.add(record(tmp_path))
    window.clear_history()
    assert not path.exists()
    assert [row.output_path for row in store.list()] == [str(tmp_path)]
    assert messages


def test_parent_junction_retains_record(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    actual = tmp_path / "actual"
    actual.mkdir()
    target = actual / "file.mp4"
    target.write_bytes(b"keep")
    link = tmp_path / "junction"
    if os.name == "nt":
        subprocess.run(["cmd", "/c", "mklink", "/J", str(link), str(actual)], check=True, capture_output=True)
    else:
        link.symlink_to(actual, target_is_directory=True)
    try:
        store.clear()
        store.add(record(link / "file.mp4"))
        window.delete_history_record(0)
        assert store.list() and target.exists() and messages
    finally:
        if os.name == "nt":
            link.rmdir()
        else:
            link.unlink()


@pytest.mark.parametrize("choice", [None, False, True])
def test_clear_completed_queue_respects_choice(qtbot, tmp_path, choice):
    window, store, path, messages = window_with_record(qtbot, tmp_path, choice)
    store.clear()
    window.queue_page.add_task("done", "测试", status="已完成")
    window._download_task_states["done"] = "finished"
    window._download_context_by_task["done"] = {"output_path": str(path)}
    window.clear_completed_queue_tasks()
    assert ("done" in window._download_task_states) == (choice is None)
    assert path.exists() == (choice is not True)
    assert not messages


def test_queue_history_reference_protects_file(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    window.queue_page.add_task("done", "测试", status="已完成")
    window._download_task_states["done"] = "finished"
    window._download_context_by_task["done"] = {"output_path": str(path)}
    window.clear_completed_queue_tasks()
    assert "done" in window._download_task_states
    assert path.exists() and messages


def test_completed_task_own_history_is_removed_with_file(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.clear()
    owned = record(path, 5)
    owned.task_id = "done"
    store.add(owned)
    window.queue_page.add_task("done", "测试", status="已完成")
    window._download_task_states["done"] = "finished"
    window._download_context_by_task["done"] = {"output_path": str(path)}
    window.clear_completed_queue_tasks()
    assert not store.list()
    assert not path.exists()
    assert not window._download_task_states
    assert not messages


@pytest.mark.parametrize("choice", [None, False])
def test_clear_history_respects_choice(qtbot, tmp_path, choice):
    window, store, path, messages = window_with_record(qtbot, tmp_path, choice)
    window.clear_history()
    assert bool(store.list()) == (choice is None)
    assert path.exists() and not messages


def test_history_finished_task_reference_is_same_owner(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.clear()
    owned = record(path, 5)
    owned.task_id = "done"
    store.add(owned)
    window._download_task_states["done"] = "finished"
    window._download_context_by_task["done"] = {"output_path": str(path)}
    window.delete_history_record(0)
    assert not path.exists() and not store.list() and not messages


def test_history_other_queue_reference_is_protected(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    window._download_task_states["other"] = "paused"
    window._download_context_by_task["other"] = {"output_path": str(path)}
    window.delete_history_record(0)
    assert path.exists() and store.list() and messages


def test_file_symlink_retains_record(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    link = tmp_path / "linked.mp4"
    link.symlink_to(path)
    store.clear()
    store.add(record(link))
    window.delete_history_record(0)
    assert link.is_symlink() and path.exists() and store.list() and messages


def test_missing_child_under_junction_retains_record(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    link = tmp_path / "linked"
    link.symlink_to(tmp_path, target_is_directory=True)
    store.clear()
    store.add(record(link / "missing.mp4"))
    window.delete_history_record(0)
    assert store.list() and messages


def test_clear_history_shared_size_mismatch_retains_all_references(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.add(record(path, 99))
    window.clear_history()
    assert path.exists() and len(store.list()) == 2 and messages


def test_clear_history_duplicate_outputs_delete_once(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.add(record(path, 5))
    window.clear_history()
    assert not path.exists() and not store.list() and not messages


def test_clear_completed_preserves_active_cancel_request(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path, False)
    window.queue_page.add_task("cancel", "测试", status="已取消")
    window._download_task_states["cancel"] = "cancel_requested"
    window._download_context_by_task["cancel"] = {"output_path": str(path)}
    window.clear_completed_queue_tasks()
    assert "cancel" in window._download_task_states
    assert window.queue_page.table.rowCount() == 1
    assert path.exists()


def test_history_changes_during_confirmation_retains_records(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    def confirm(*_):
        store.add(record(tmp_path / "another.mp4"))
        return True
    window._confirm_record_deletion = confirm
    window.delete_history_record(0)
    assert path.exists() and len(store.list()) == 2 and messages


def test_queue_failure_retains_context_and_request(qtbot, tmp_path, monkeypatch):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.clear()
    window.queue_page.add_task("done", "测试", status="已完成")
    window._download_task_states["done"] = "finished"
    window._download_context_by_task["done"] = {"output_path": str(path)}
    window._download_requests_by_task["done"] = object()
    monkeypatch.setattr("ytdl_gui.file_deletion._delete_file_by_handle", lambda *_: (_ for _ in ()).throw(PermissionError()))
    window.clear_completed_queue_tasks()
    assert path.exists() and messages
    assert "done" in window._download_task_states
    assert "done" in window._download_context_by_task
    assert "done" in window._download_requests_by_task
    assert window.queue_page.table.rowCount() == 1


def test_same_task_conflicting_history_sizes_retain_file(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.clear()
    for size in (5, 99):
        owned = record(path, size)
        owned.task_id = "done"
        store.add(owned)
    window.queue_page.add_task("done", "测试", status="已完成")
    window._download_task_states["done"] = "finished"
    window._download_context_by_task["done"] = {"output_path": str(path)}
    window.clear_completed_queue_tasks()
    assert path.exists() and len(store.list()) == 2 and messages


def test_clear_history_invalid_link_reference_protects_target(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    link = tmp_path / "link.mp4"
    link.symlink_to(path)
    store.add(record(link))
    window.clear_history()
    assert path.exists() and len(store.list()) == 2 and messages


def test_canceled_task_without_recorded_output_clears_only_record(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    partial = tmp_path / "video.mp4.part"
    partial.write_bytes(b"partial")
    store.clear()
    window.queue_page.add_task("canceled", "测试", status="已取消")
    window._download_task_states["canceled"] = "canceled"
    window.clear_completed_queue_tasks()
    assert not window._download_task_states
    assert not window.queue_page.table.rowCount()
    assert path.exists() and partial.read_bytes() == b"partial" and not messages


def test_empty_history_output_clears_record_without_searching_files(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.clear()
    store.add(record(""))
    window.delete_history_record(0)
    assert not store.list() and path.exists() and not messages


def test_empty_queue_output_with_recorded_history_path_retains_both(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.clear()
    owned = record(path, 5)
    owned.task_id = "done"
    store.add(owned)
    window.queue_page.add_task("done", "测试", status="已完成")
    window._download_task_states["done"] = "finished"
    window.clear_completed_queue_tasks()
    assert path.exists() and store.list() and window._download_task_states and messages


def test_unresolvable_path_returns_failure_without_deleting(tmp_path, monkeypatch):
    from ytdl_gui.file_deletion import delete_recorded_files

    path = tmp_path / "file.mp4"
    path.write_bytes(b"keep")
    def unresolved(*_args, **_kwargs):
        raise RuntimeError("link loop")
    monkeypatch.setattr(Path, "resolve", unresolved)
    failures = delete_recorded_files([(str(path), 4)], [])
    assert 0 in failures and path.exists()


@pytest.mark.parametrize("checked", [False, True])
def test_confirmation_accept_returns_checkbox_value(qtbot, checked):
    window = MainWindow()
    qtbot.addWidget(window)
    def accept():
        dialog = QApplication.activeModalWidget()
        dialog.findChild(QCheckBox).setChecked(checked)
        next(button for button in dialog.buttons() if button.text() == "确认删除").click()
    QTimer.singleShot(0, accept)
    assert window._confirm_record_deletion("删除历史", 1) is checked


def test_finished_history_persists_task_identity(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    store.clear()
    window._download_context_by_task["owned"] = {"output_path": str(path)}
    window.record_finished_download("owned")
    assert store.list()[0].task_id == "owned"
    assert store.list()[0].file_size_bytes == 5


def test_none_output_path_is_a_safe_failure(tmp_path):
    from ytdl_gui.file_deletion import delete_recorded_files

    path = tmp_path / "keep.mp4"
    path.write_bytes(b"keep")
    failures = delete_recorded_files([(None, 0), (str(path), 4)], [])
    assert 0 in failures and path.exists()


@pytest.mark.skipif(os.name != "nt", reason="Windows junction race")
def test_junction_swap_after_final_path_check_does_not_delete_other_file(tmp_path, monkeypatch):
    import ytdl_gui.file_deletion as deletion

    folder = tmp_path / "recorded"
    saved = tmp_path / "original"
    other = tmp_path / "unrelated"
    folder.mkdir()
    other.mkdir()
    output = folder / "same.mp4"
    sentinel = other / "same.mp4"
    output.write_bytes(b"file")
    sentinel.write_bytes(b"keep")
    checked_file = deletion._checked_file
    checks = 0
    def swap_after_check(path_text):
        nonlocal checks
        result = checked_file(path_text)
        checks += 1
        if checks == 2:
            folder.rename(saved)
            subprocess.run(["cmd", "/c", "mklink", "/J", str(folder), str(other)], check=True, capture_output=True)
        return result
    monkeypatch.setattr(deletion, "_checked_file", swap_after_check)
    try:
        failures = deletion.delete_recorded_files([(str(output), 4)], [])
        assert sentinel.read_bytes() == b"keep"
        assert (saved / "same.mp4").read_bytes() == b"file"
        assert 0 in failures
    finally:
        if saved.exists():
            folder.rmdir()


@pytest.mark.skipif(os.name != "nt", reason="Windows file handle")
def test_junction_swap_after_handle_open_cannot_redirect_deletion(tmp_path, monkeypatch):
    import ytdl_gui.file_deletion as deletion

    folder = tmp_path / "recorded"
    saved = tmp_path / "original"
    other = tmp_path / "unrelated"
    folder.mkdir()
    other.mkdir()
    output = folder / "same.mp4"
    sentinel = other / "same.mp4"
    output.write_bytes(b"file")
    sentinel.write_bytes(b"keep")
    original_fstat = os.fstat
    swaps = []
    def swap_after_open(descriptor):
        info = original_fstat(descriptor)
        try:
            folder.rename(saved)
        except PermissionError:
            # Exclusive sharing may also prevent the ancestor rename itself.
            swaps.append("blocked")
            raise
        subprocess.run(["cmd", "/c", "mklink", "/J", str(folder), str(other)], check=True, capture_output=True)
        swaps.append("swapped")
        return info
    monkeypatch.setattr(os, "fstat", swap_after_open)
    try:
        failures = deletion.delete_recorded_files([(str(output), 4)], [])
        assert sentinel.read_bytes() == b"keep"
        assert len(swaps) == 1
        if swaps == ["swapped"]:
            assert not (saved / "same.mp4").exists() and not failures
        else:
            assert output.read_bytes() == b"file" and 0 in failures
    finally:
        if swaps == ["swapped"]:
            folder.rmdir()


@pytest.mark.skipif(os.name != "nt", reason="Windows file sharing")
def test_open_file_blocks_deletion_and_allows_retry_after_close(tmp_path):
    from ytdl_gui.file_deletion import delete_recorded_files

    path = tmp_path / "file.mp4"
    path.write_bytes(b"keep")
    with path.open("rb"):
        assert 0 in delete_recorded_files([(str(path), 4)], [])
        assert path.read_bytes() == b"keep"
    assert not delete_recorded_files([(str(path), 4)], [])
    assert not path.exists()


def test_none_path_history_delete_action_does_not_crash(qtbot, tmp_path):
    window, store, path, messages = window_with_record(qtbot, tmp_path)
    damaged = record(path)
    damaged.output_path = None
    store.clear()
    store.add(damaged)
    window.handle_history_action("delete", 0)
    assert store.list() and path.exists() and messages
