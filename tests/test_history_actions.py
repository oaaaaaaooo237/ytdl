import os
from dataclasses import replace
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import pytest
from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication, QFileDialog, QLabel, QListWidget, QPushButton, QToolButton

from ytdl_gui.history_store import HistoryRecord
from ytdl_gui.ui import history_actions
from ytdl_gui.ui.pages.history_page import HistoryPage


def make_record(output_path: str, **kwargs) -> HistoryRecord:
    return HistoryRecord(
        "演示", "https://example.test/watch?v=1", output_path, "视频", "720p", "",
        "finished", "2026-11-19T08:00:00", **kwargs,
    )


def test_share_lists_only_explicit_available_files(tmp_path):
    movie = tmp_path / "演示.mp4"
    subtitle = tmp_path / "演示.zh.srt"
    other_subtitle = tmp_path / "演示.en.srt"
    torrent = tmp_path / "source.torrent"
    for path in (movie, subtitle, other_subtitle, torrent):
        path.touch()
    record = make_record(
        str(movie),
        auxiliary_paths=[str(movie), str(subtitle), str(tmp_path / "missing.srt"), str(tmp_path)],
        torrent_source_path=str(torrent),
    )

    assert [item.path for item in history_actions.history_artifacts(record)] == [movie, subtitle, torrent]
    assert [item.path for item in history_actions.history_artifacts(replace(record, output_path=""))] == [
        movie, subtitle, torrent,
    ]


def test_export_checks_conflicts_before_copying_and_preserves_files(tmp_path):
    source = tmp_path / "原文件"
    destination = tmp_path / "导出目录"
    source.mkdir()
    destination.mkdir()
    video = source / "视频.mp4"
    subtitle = source / "视频.zh.srt"
    torrent = source / "来源.torrent"
    inputs = (video, subtitle, torrent)
    for path in inputs:
        path.write_bytes(path.suffix.encode())
    collision = destination / subtitle.name
    collision.write_bytes(b"existing")

    with pytest.raises(history_actions.HistoryExportError, match="已有同名文件"):
        history_actions.export_history_files(inputs, destination)
    assert collision.read_bytes() == b"existing"
    assert not (destination / video.name).exists()

    collision.unlink()
    assert history_actions.export_history_files(inputs, destination) == 3
    assert [(destination / path.name).read_bytes() for path in inputs] == [path.read_bytes() for path in inputs]


def test_copy_share_url_and_native_file_list(qtbot, tmp_path):
    path = tmp_path / "演示.mp4"
    path.touch()
    dialog = history_actions.HistoryShareDialog(make_record(str(path)))
    qtbot.addWidget(dialog)
    clipboard = QApplication.clipboard()

    try:
        dialog.findChild(QPushButton, "historyCopyFiles").click()
        assert [Path(url.toLocalFile()) for url in clipboard.mimeData().urls()] == [path]
        dialog.findChild(QPushButton, "historyCopySource").click()
        assert clipboard.text() == "https://example.test/watch?v=1"
    finally:
        clipboard.clear()


def test_share_dialog_exports_the_checked_files_in_background(qtbot, tmp_path, monkeypatch):
    path = tmp_path / "演示.mp4"
    path.write_bytes(b"contents")
    subtitle = tmp_path / "演示.zh.srt"
    subtitle.touch()
    directory = tmp_path / "导出"
    directory.mkdir()
    monkeypatch.setattr(QFileDialog, "getExistingDirectory", lambda *_: str(directory))
    dialog = history_actions.HistoryShareDialog(make_record(str(path), auxiliary_paths=(str(subtitle),)))
    qtbot.addWidget(dialog)
    feedback = dialog.findChild(QLabel, "historyShareFeedback")
    dialog.findChild(QListWidget).item(1).setCheckState(Qt.CheckState.Unchecked)

    dialog.findChild(QPushButton, "historyExport").click()
    qtbot.waitUntil(lambda: feedback.text() == "已导出 1 个文件。")

    assert (directory / path.name).read_bytes() == b"contents"
    assert not (directory / subtitle.name).exists()


def test_local_torrent_source_disables_copy_link(qtbot, tmp_path):
    torrent = tmp_path / "原始文件.torrent"
    torrent.touch()
    record = replace(make_record(""), url=str(torrent), torrent_source_path=str(torrent))
    dialog = history_actions.HistoryShareDialog(record)
    qtbot.addWidget(dialog)

    assert not dialog.findChild(QPushButton, "historyCopySource").isEnabled()
    copy_files = dialog.findChild(QPushButton, "historyCopyFiles")
    assert not copy_files.isEnabled()
    dialog.findChild(QListWidget).item(0).setCheckState(Qt.CheckState.Checked)
    assert copy_files.isEnabled()


def test_history_share_and_details_emit_original_index_after_filtering(qtbot):
    page = HistoryPage()
    qtbot.addWidget(page)
    actions = []
    page.history_action_requested.connect(lambda action, index: actions.append((action, index)))
    page.load_records([replace(make_record(""), title="其他"), make_record("")])
    page.search.setText("演示")

    buttons = page.table.cellWidget(0, 5)
    buttons.findChild(QPushButton, "historyShare").click()
    next(action for action in buttons.findChild(QToolButton).menu().actions() if action.data() == "details").trigger()

    assert actions == [("share", 1), ("details", 1)]
