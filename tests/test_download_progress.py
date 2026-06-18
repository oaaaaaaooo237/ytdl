import os
import json
import subprocess
import threading
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtCore import QBuffer, QByteArray, QIODevice, QThread
from PySide6.QtGui import QColor, QImage

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.ui.main_window import MainWindow, _thumbnail_url
from ytdl_gui.workers import DownloadWorker
from ytdl_gui.ytdlp_runner import ProgressEvent, parse_progress_line, safe_command_summary


def test_parse_ytdlp_progress_line():
    line = "[download]  42.3% of 10.00MiB at 1.23MiB/s ETA 00:10"

    event = parse_progress_line(line)

    assert event == ProgressEvent(percent=42.3, speed="1.23MiB/s", eta="00:10", raw=line)


def test_parse_ytdlp_progress_line_without_eta():
    line = "[download] 100% of 49.55MiB in 00:00:10 at 4.91MiB/s"

    event = parse_progress_line(line)

    assert event == ProgressEvent(percent=100.0, speed="4.91MiB/s", eta="", raw=line)


def test_parse_ytdlp_fragment_progress_line():
    line = "[download]  12.3% of ~1.43GiB at 2.34MiB/s ETA 05:12 (frag 3/88)"

    event = parse_progress_line(line)

    assert event == ProgressEvent(percent=12.3, speed="2.34MiB/s", eta="05:12", raw=line)


def test_parse_non_progress_line_keeps_raw():
    line = "Deleting original file example.f248.mp4"

    event = parse_progress_line(line)

    assert event.percent is None
    assert event.raw == line


def test_safe_command_summary_redacts_cookie_path():
    command = ["yt-dlp.exe", "--cookies", "D:/secret/cookies.txt", "-f", "22", "https://example.com"]

    summary = safe_command_summary(command)

    assert "D:/secret" not in summary
    assert "--cookies <redacted>" in summary


def test_empty_url_and_download_without_analysis_show_chinese_status(qtbot):
    window = MainWindow(worker_runner=lambda worker: worker.run())
    qtbot.addWidget(window)

    window.download_page.analyze_button.click()
    assert window.download_page.status_label.text() == "请输入视频地址。"

    window.download_page.start_button.click()
    assert window.download_page.status_label.text() == "请先分析视频，再开始下载。"


def test_analysis_entry_uses_injected_runner_and_saves_metadata(qtbot, app_data_dir: Path):
    calls: list[list[str]] = []

    def runner(command, **kwargs):
        calls.append(command)
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=(
                '{"title": "Demo Video", "duration": 125, '
                '"formats": [{"format_id": "22", "height": 720, "ext": "mp4", '
                '"vcodec": "avc1", "acodec": "mp4a", "fps": 30}]}'
            ),
            stderr="",
        )

    config = ConfigStore(app_data_dir)
    config.save(AppConfig(active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        analysis_runner=runner,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    window.download_page.url_input.setPlainText("https://example.test/watch?v=1")
    window.download_page.analyze_button.click()

    assert calls
    assert Path(calls[0][0]) == Path("D:/tools/yt-dlp.exe")
    assert window.analyzed_metadata["title"] == "Demo Video"
    assert window.selected_format_id == "22"
    assert window.download_page.title_label.text() == "标题：Demo Video"
    assert window.download_page.duration_label.text() == "时长：02:05"
    assert window.download_page.format_summary_label.text() == "格式：720p mp4 avc1/mp4a 30fps"
    assert window.formats_page.format_id_combo.currentText() == "22"
    assert window.formats_page.format_id_combo.isHidden()
    assert window.formats_page.actual_format_label.text() == "720p mp4 avc1/mp4a 30fps"


def test_analysis_respects_audio_only_mode(qtbot, app_data_dir: Path):
    config = ConfigStore(app_data_dir)
    config.save(AppConfig(active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        worker_runner=lambda worker: worker.run(),
        analysis_runner=lambda command, **kwargs: subprocess.CompletedProcess(
            command,
            0,
            stdout=(
                '{"title": "Demo Video", "duration": 30, '
                '"formats": ['
                '{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},'
                '{"format_id": "139", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.5", "abr": 49},'
                '{"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129}'
                "]} "
            ),
            stderr="",
        ),
    )
    qtbot.addWidget(window)

    window.download_page.mode_combo.setCurrentText("仅音频")
    window.download_page.url_input.setPlainText("https://example.test/watch?v=1")
    window.download_page.analyze_button.click()

    assert window.selected_format_id == "140"
    assert window.download_page.format_summary_label.text() == "格式：音频 m4a mp4a.40.2 129kbps"


def test_ytdlp_analysis_failure_offers_retry_and_update_guidance(qtbot):
    window = MainWindow(worker_runner=lambda worker: worker.run())
    qtbot.addWidget(window)

    window.show_analysis_error("unknown_ytdlp_failure", "https://example.test/watch?v=1")

    assert window.download_page.analyze_button.text() == "重试分析"
    status = window.download_page.status_label.text()
    assert "检查更新" in status
    assert "重试分析" in status


def test_analysis_retry_label_returns_to_analyze_after_success(qtbot, app_data_dir: Path):
    def runner(command, **kwargs):
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=(
                '{"title": "Demo Video", "duration": 30, '
                '"formats": [{"format_id": "18", "height": 360, "ext": "mp4", '
                '"vcodec": "avc1", "acodec": "mp4a", "fps": 30}]}'
            ),
            stderr="",
        )

    config = ConfigStore(app_data_dir)
    config.save(AppConfig(active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        analysis_runner=runner,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)
    window.show_analysis_error("network_timeout", "https://example.test/watch?v=1")

    window.download_page.url_input.setPlainText("https://example.test/watch?v=1")
    window.download_page.analyze_button.click()

    assert window.selected_format_id == "18"
    assert window.download_page.analyze_button.text() == "分析"


def test_thumbnail_url_prefers_direct_thumbnail_then_highest_list_entry():
    assert _thumbnail_url({"thumbnail": "https://example.test/direct.jpg"}) == "https://example.test/direct.jpg"
    assert (
        _thumbnail_url({"thumbnails": [{"url": "https://example.test/small.jpg"}, {"url": "https://example.test/large.jpg"}]})
        == "https://example.test/large.jpg"
    )
    assert (
        _thumbnail_url(
            {
                "thumbnails": [
                    {"url": "https://example.test/small.jpg"},
                    {"url": "https://example.test/hq720.jpg"},
                    {"url": "https://example.test/maxresdefault.webp"},
                ]
            }
        )
        == "https://example.test/hq720.jpg"
    )


def test_thumbnail_loader_fetches_image_bytes_without_qt_network(qtbot):
    calls: list[str] = []
    image_bytes = _png_bytes()

    window = MainWindow(
        thumbnail_fetcher=lambda url, _headers=None: calls.append(url) or image_bytes,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "duration": 125,
            "thumbnail": "https://img.example.test/thumb.jpg",
            "formats": [{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}],
        },
    )

    pixmap = window.download_page.thumbnail_label.pixmap()
    assert calls == ["https://img.example.test/thumb.jpg"]
    assert pixmap is not None and not pixmap.isNull()
    assert window.download_page.thumbnail_label.property("hasPreviewOverlay") is True


def test_thumbnail_loader_uses_safe_metadata_headers_for_cdn_thumbnails(qtbot):
    calls: list[tuple[str, dict[str, str]]] = []
    image_bytes = _png_bytes()

    def fetcher(url: str, headers: dict[str, str] | None = None) -> bytes:
        calls.append((url, dict(headers or {})))
        return image_bytes

    window = MainWindow(
        thumbnail_fetcher=fetcher,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    window.apply_analysis_result(
        "https://www.pornhub.com/view_video.php?viewkey=abc",
        {
            "title": "Demo Video",
            "duration": 125,
            "thumbnail": "https://ci.phncdn.com/videos/thumb.jpg",
            "webpage_url": "https://www.pornhub.com/view_video.php?viewkey=abc",
            "http_headers": {
                "User-Agent": "yt-dlp-test-agent",
                "Referer": "https://www.pornhub.com/",
                "Accept-Language": "en-US,en;q=0.5",
                "Cookie": "session=secret",
                "Authorization": "Bearer secret",
            },
            "formats": [{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}],
        },
    )

    assert calls == [
        (
            "https://ci.phncdn.com/videos/thumb.jpg",
            {
                "User-Agent": "yt-dlp-test-agent",
                "Referer": "https://www.pornhub.com/",
                "Accept-Language": "en-US,en;q=0.5",
            },
        )
    ]
    pixmap = window.download_page.thumbnail_label.pixmap()
    assert pixmap is not None and not pixmap.isNull()


def test_thumbnail_loader_falls_back_to_analysis_url_as_referer(qtbot):
    calls: list[tuple[str, dict[str, str]]] = []
    image_bytes = _png_bytes()

    window = MainWindow(
        thumbnail_fetcher=lambda url, headers=None: calls.append((url, dict(headers or {}))) or image_bytes,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    window.apply_analysis_result(
        "https://www.pornhub.com/view_video.php?viewkey=abc",
        {
            "title": "Demo Video",
            "duration": 125,
            "thumbnail": "https://ci.phncdn.com/videos/thumb.jpg",
            "formats": [{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}],
        },
    )

    assert calls[0][1]["Referer"] == "https://www.pornhub.com/view_video.php?viewkey=abc"


def test_successful_single_url_analysis_opens_format_selection(qtbot, app_data_dir: Path):
    config = ConfigStore(app_data_dir)
    config.save(AppConfig(active_ytdlp_path="D:/tools/yt-dlp.exe"))
    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)
    window.download_page.url_input.setPlainText("https://example.test/watch?v=1")

    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "duration": 30,
            "formats": [{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}],
        },
    )

    assert window.nav.currentRow() == 1
    assert "格式" in window.download_page.status_label.text()


def test_quality_buttons_open_format_selection(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    window.download_page.audio_quality_button.click()
    assert window.nav.currentRow() == 1

    window.nav.setCurrentRow(0)
    window.download_page.video_quality_button.click()
    assert window.nav.currentRow() == 1


def test_start_download_announces_queued_task_and_opens_queue(qtbot, app_data_dir: Path):
    started_downloads: list[DownloadWorker] = []
    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    config = ConfigStore(app_data_dir)
    config.save(AppConfig(default_save_dir=str(download_dir), active_ytdlp_path="D:/tools/yt-dlp.exe"))

    def worker_runner(worker):
        if isinstance(worker, DownloadWorker):
            started_downloads.append(worker)
            return
        worker.run()

    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        worker_runner=worker_runner,
    )
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "duration": 125,
            "formats": [
                {
                    "format_id": "22",
                    "height": 720,
                    "ext": "mp4",
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                    "fps": 30,
                }
            ],
        },
    )

    window.download_page.start_button.click()

    assert len(started_downloads) == 1
    assert window.queue_page.table.rowCount() == 1
    assert window.nav.currentRow() == 2
    assert "已添加 1 个下载任务" in window.queue_page.notice_label.text()
    assert "正在队列页显示进度" in window.download_page.status_label.text()


def test_format_page_mode_buttons_sync_download_type(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    window.formats_page.mode_buttons[1].click()
    assert window.download_page.mode_combo.currentText() == "仅音频"
    assert window.download_page.audio_checkbox.isChecked()
    assert not window.download_page.video_checkbox.isChecked()

    window.formats_page.mode_buttons[0].click()
    assert window.download_page.mode_combo.currentText() == "音频+视频"
    assert window.download_page.audio_checkbox.isChecked()
    assert window.download_page.video_checkbox.isChecked()


def test_apply_format_selection_updates_download_page_summary(qtbot):
    window = MainWindow()
    qtbot.addWidget(window)

    window.formats_page.resolution_combo.setCurrentText("1080p")
    window.formats_page.audio_bitrate_combo.setCurrentText("192k")
    window.formats_page.subtitle_combo.setCurrentText("烧录")
    window.formats_page.apply_button.click()

    assert window.nav.currentRow() == 0
    assert window.download_page.video_quality_button.text() == "1080p"
    assert window.download_page.audio_quality_button.text() == "192k"
    assert "字幕：烧录" in window.download_page.status_label.text()
    assert "可以开始下载" in window.download_page.status_label.text()


def test_start_download_uses_injected_popen_updates_queue_and_history(qtbot, app_data_dir: Path):
    popen_calls: list[list[str]] = []

    class FakeStdout:
        def __iter__(self):
            return iter(
                [
                    "[download]  50.0% of 10.00MiB at 2.00MiB/s ETA 00:05\n",
                    "[download] 100.0% of 10.00MiB at 2.00MiB/s ETA 00:00\n",
                ]
            )

    class FakeProcess:
        stdout = FakeStdout()

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    def popen_factory(command, **kwargs):
        popen_calls.append(command)
        return FakeProcess()

    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    config = ConfigStore(app_data_dir)
    config.save(
        AppConfig(
            default_save_dir=str(download_dir),
            cookies_path="D:/secret/cookies.txt",
            active_ytdlp_path="D:/tools/yt-dlp.exe",
        )
    )
    history = HistoryStore(app_data_dir)
    window = MainWindow(
        config_store=config,
        history_store=history,
        download_popen_factory=popen_factory,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "duration": 125,
            "formats": [
                {
                    "format_id": "22",
                    "height": 720,
                    "ext": "mp4",
                    "vcodec": "avc1",
                    "acodec": "mp4a",
                    "fps": 30,
                }
            ],
        },
    )

    window.download_page.start_button.click()

    assert popen_calls
    assert "--cookies" in popen_calls[0]
    assert str(Path("D:/secret/cookies.txt")) in popen_calls[0]
    assert "D:/secret" not in window.download_page.status_label.text()
    assert window.queue_page.table.rowCount() == 1
    assert window.queue_page.table.item(0, 1).text() == "已完成"
    assert window.queue_page.table.item(0, 2).text() == "100.0%"
    records = history.list()
    assert len(records) == 1
    assert records[0].title == "Demo Video"
    assert records[0].format_summary == "720p mp4 avc1/mp4a 30fps"
    assert "cookies" not in history.path.read_text(encoding="utf-8").lower()


def test_finished_download_history_uses_actual_output_path(qtbot, app_data_dir: Path):
    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    output_file = download_dir / "Demo Video.mp4"
    output_file.write_bytes(b"x" * 1536)

    class FakeStdout:
        def __iter__(self):
            return iter(
                [
                    "[download] 100.0% of 1.00MiB at 1.00MiB/s ETA 00:00\n",
                    f"{output_file}\n",
                ]
            )

    class FakeProcess:
        stdout = FakeStdout()

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    config = ConfigStore(app_data_dir)
    config.save(AppConfig(default_save_dir=str(download_dir), active_ytdlp_path="D:/tools/yt-dlp.exe"))
    history = HistoryStore(app_data_dir)
    window = MainWindow(
        config_store=config,
        history_store=history,
        download_popen_factory=lambda command, **kwargs: FakeProcess(),
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}
            ],
        },
    )

    window.download_page.start_button.click()

    records = history.list()
    assert records[0].output_path == str(output_file)
    assert records[0].file_size_bytes == 1536
    assert "%(title)s" not in records[0].output_path


def test_finish_download_survives_history_write_failure(qtbot, app_data_dir: Path):
    class FailingHistoryStore:
        path = app_data_dir / "history.json"

        def add(self, record):
            raise OSError("disk full")

        def list(self):
            return []

    output_file = app_data_dir / "downloads" / "Demo Video.mp4"
    output_file.parent.mkdir()
    output_file.write_bytes(b"x")
    window = MainWindow(history_store=FailingHistoryStore())
    qtbot.addWidget(window)
    task_id = "task-history-failure"
    window.queue_page.add_task(task_id, "Demo Video")
    window._download_context_by_task[task_id] = {
        "title": "Demo Video",
        "url": "https://example.test/watch?v=1",
        "output_path": str(output_file),
    }

    window.finish_download(task_id, str(output_file))
    qtbot.wait(20)

    assert window._download_task_states[task_id] == "finished"
    assert window.queue_page.table.item(0, 2).text() == "100.0%"
    assert "history" not in window.download_page.status_label.text().lower()


def test_finish_download_called_from_worker_thread_updates_ui_on_main_thread(qtbot, app_data_dir: Path):
    output_file = app_data_dir / "downloads" / "Demo Video.mp4"
    output_file.parent.mkdir()
    output_file.write_bytes(b"x")
    window = MainWindow()
    qtbot.addWidget(window)
    task_id = "task-thread-finish"
    window.queue_page.add_task(task_id, "Demo Video")
    update_threads: list[bool] = []
    original_update_task = window.queue_page.update_task

    def capture_update_task(*args, **kwargs):
        update_threads.append(QThread.currentThread() == window.thread())
        original_update_task(*args, **kwargs)

    window.queue_page.update_task = capture_update_task
    worker_thread = threading.Thread(target=lambda: window.finish_download(task_id, str(output_file)))

    worker_thread.start()
    worker_thread.join(timeout=2)

    assert not worker_thread.is_alive()
    qtbot.waitUntil(lambda: window._download_task_states.get(task_id) == "finished", timeout=1000)
    assert update_threads
    assert all(update_threads)


def test_start_download_rejects_missing_output_folder_before_starting_worker(qtbot, app_data_dir: Path):
    missing_folder = app_data_dir / "missing-downloads"
    messages: list[tuple[str, str]] = []

    def forbidden_popen(*_args, **_kwargs):
        raise AssertionError("download worker should not start when output folder is missing")

    window = MainWindow(
        config_store=ConfigStore(app_data_dir),
        history_store=HistoryStore(app_data_dir),
        download_popen_factory=forbidden_popen,
        information_dialog=lambda _parent, title, text: messages.append((title, text)),
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)
    window.save_folder_path = str(missing_folder)
    window.analyzed_results = {
        "https://example.test/watch?v=demo": {
            "title": "Demo",
            "formats": [{"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}],
        }
    }

    window.start_download()

    assert window.queue_page.table.rowCount() == 0
    assert messages == [("保存位置不可用", "保存文件夹不存在或不可写，请重新选择保存位置。")]
    assert "保存位置不可用" in window.download_page.status_label.text()


def test_start_download_rejects_split_audio_video_when_ffmpeg_missing(qtbot, app_data_dir: Path, monkeypatch):
    monkeypatch.setattr("ytdl_gui.ui.main_window.local_ffmpeg_candidates", lambda *args, **kwargs: [])
    started_downloads: list[DownloadWorker] = []
    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    config = ConfigStore(app_data_dir)
    config.save(AppConfig(default_save_dir=str(download_dir), active_ytdlp_path="D:/tools/yt-dlp.exe", ffmpeg_path=""))

    def worker_runner(worker):
        if isinstance(worker, DownloadWorker):
            started_downloads.append(worker)
            return
        worker.run()

    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        worker_runner=worker_runner,
    )
    qtbot.addWidget(window)
    window.formats_page.resolution_combo.setCurrentText("1080p")
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30},
                {"format_id": "299", "height": 1080, "ext": "mp4", "vcodec": "avc1.64002a", "acodec": "none", "fps": 60},
                {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129},
            ],
        },
    )

    window.start_download()

    assert started_downloads == []
    assert window.queue_page.table.rowCount() == 0
    assert "需要 ffmpeg 合并音频和视频" in window.download_page.status_label.text()


def test_batch_urls_analyze_and_download_each_url(qtbot, app_data_dir: Path):
    analysis_urls: list[str] = []
    popen_calls: list[list[str]] = []

    def analysis_runner(command, **kwargs):
        url = command[-1]
        analysis_urls.append(url)
        suffix = url.rsplit("=", 1)[-1]
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=json.dumps(
                {
                    "title": f"Demo {suffix}",
                    "duration": 60,
                    "formats": [
                        {
                            "format_id": "18",
                            "height": 360,
                            "ext": "mp4",
                            "vcodec": "avc1",
                            "acodec": "mp4a",
                            "fps": 30,
                        }
                    ],
                }
            ),
            stderr="",
        )

    class FakeStdout:
        def __iter__(self):
            return iter(["[download] 100.0% of 1.00MiB at 1.00MiB/s ETA 00:00\n"])

    class FakeProcess:
        stdout = FakeStdout()

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    def popen_factory(command, **kwargs):
        popen_calls.append(command)
        return FakeProcess()

    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    config = ConfigStore(app_data_dir)
    config.save(AppConfig(default_save_dir=str(download_dir), active_ytdlp_path="D:/tools/yt-dlp.exe"))
    history = HistoryStore(app_data_dir)
    window = MainWindow(
        config_store=config,
        history_store=history,
        analysis_runner=analysis_runner,
        download_popen_factory=popen_factory,
        worker_runner=lambda worker: worker.run(),
    )
    qtbot.addWidget(window)

    urls = ["https://example.test/watch?v=a", "https://example.test/watch?v=b"]
    window.download_page.url_input.setPlainText("\n".join(urls))
    window.download_page.analyze_button.click()
    window.download_page.start_button.click()

    assert analysis_urls == urls
    assert list(window.analyzed_results) == urls
    assert [call[-1] for call in popen_calls] == urls
    assert window.queue_page.table.rowCount() == 2
    records = history.list()
    assert [record.url for record in records] == urls
    assert [record.title for record in records] == ["Demo a", "Demo b"]


def test_batch_download_respects_configured_concurrency(qtbot, app_data_dir: Path):
    started_downloads: list[DownloadWorker] = []

    def analysis_runner(command, **kwargs):
        suffix = command[-1].rsplit("=", 1)[-1]
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=json.dumps(
                {
                    "title": f"Demo {suffix}",
                    "formats": [
                        {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}
                    ],
                }
            ),
            stderr="",
        )

    class FakeStdout:
        def __iter__(self):
            return iter(["[download] 100.0% of 1.00MiB at 1.00MiB/s ETA 00:00\n"])

    class FakeProcess:
        stdout = FakeStdout()

        def poll(self):
            return None

        def terminate(self):
            pass

        def wait(self):
            return 0

    def worker_runner(worker):
        if isinstance(worker, DownloadWorker):
            started_downloads.append(worker)
        else:
            worker.run()

    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    config = ConfigStore(app_data_dir)
    config.save(
        AppConfig(
            default_save_dir=str(download_dir),
            active_ytdlp_path="D:/tools/yt-dlp.exe",
            max_concurrency=1,
        )
    )
    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        analysis_runner=analysis_runner,
        download_popen_factory=lambda command, **kwargs: FakeProcess(),
        worker_runner=worker_runner,
    )
    qtbot.addWidget(window)

    urls = [f"https://example.test/watch?v={suffix}" for suffix in ("a", "b", "c")]
    window.download_page.url_input.setPlainText("\n".join(urls))
    window.download_page.analyze_button.click()
    window.download_page.start_button.click()

    assert len(started_downloads) == 1
    assert [window.queue_page.table.item(row, 1).text() for row in range(3)] == ["下载中", "等待中", "等待中"]

    started_downloads[0].run()

    assert len(started_downloads) == 2
    assert [window.queue_page.table.item(row, 1).text() for row in range(3)] == ["已完成", "下载中", "等待中"]


def test_start_download_with_burn_subtitle_starts_real_worker_when_ffmpeg_configured(qtbot, app_data_dir: Path):
    started_downloads: list[DownloadWorker] = []
    download_dir = app_data_dir / "downloads"
    download_dir.mkdir()
    config = ConfigStore(app_data_dir)
    config.save(
        AppConfig(
            default_save_dir=str(download_dir),
            active_ytdlp_path="D:/tools/yt-dlp.exe",
            ffmpeg_path="D:/ffmpeg/bin/ffmpeg.exe",
        )
    )

    def worker_runner(worker):
        if isinstance(worker, DownloadWorker):
            started_downloads.append(worker)
            return
        worker.run()

    window = MainWindow(
        config_store=config,
        history_store=HistoryStore(app_data_dir),
        worker_runner=worker_runner,
    )
    qtbot.addWidget(window)
    window.apply_analysis_result(
        "https://example.test/watch?v=1",
        {
            "title": "Demo Video",
            "formats": [
                {"format_id": "18", "height": 360, "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "fps": 30}
            ],
        },
    )
    window.formats_page.subtitle_combo.setCurrentText("烧录")

    window.download_page.start_button.click()

    assert len(started_downloads) == 1
    assert started_downloads[0].request.subtitle_action == "burn"
    assert started_downloads[0].request.ffmpeg_path is not None
    assert started_downloads[0].request.ffmpeg_path.name == "ffmpeg.exe"
    assert started_downloads[0].request.ffmpeg_path.exists()
    assert "尚未实现" not in window.download_page.status_label.text()


def _png_bytes() -> bytes:
    image = QImage(64, 36, QImage.Format.Format_RGB32)
    image.fill(QColor("#336699"))
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    assert image.save(buffer, "PNG")
    return bytes(data)
