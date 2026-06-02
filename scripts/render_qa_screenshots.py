from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

os.environ.setdefault("QT_SCALE_FACTOR", "1")
os.environ.setdefault("QT_ENABLE_HIGHDPI_SCALING", "0")

from PySide6.QtCore import QEventLoop, QTimer
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import QApplication

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryRecord, HistoryStore
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.theme import apply_light_theme


QA_SCREENSHOT_SIZES = {
    "download": (590, 883),
    "formats": (488, 883),
    "queue": (535, 883),
    "history": (590, 883),
    "settings": (590, 883),
    "about": (590, 883),
}


def render_screenshots(metadata_path: Path, output_dir: Path, data_dir: Path) -> None:
    metadata = _read_metadata(metadata_path)
    output_dir.mkdir(parents=True, exist_ok=True)
    data_dir.mkdir(parents=True, exist_ok=True)

    config = ConfigStore(data_dir)
    config.save(
        AppConfig(
            default_save_dir=str(data_dir / "downloads"),
            ffmpeg_path="D:/ffmpeg/bin/ffmpeg.exe",
            active_ytdlp_version="2026.03.17",
        )
    )
    history = HistoryStore(data_dir)
    history.clear()
    for title, download_type, summary, created_at in [
        (metadata.get("title") or "测试视频", "音频+视频", "360p mp4", "2026-06-01T21:44:42"),
        ("仅音频测试", "仅音频", "音频 m4a 129kbps", "2026-06-01T21:45:20"),
        ("仅视频测试", "仅视频", "144p mp4 无音频", "2026-06-01T21:46:10"),
        ("历史样例", "音频+视频", "720p mp4", "2026-05-31T13:30:00"),
    ]:
        history.add(
            HistoryRecord(
                title,
                "https://www.youtube.com/watch?v=PqQNXB6hhUs",
                str(data_dir / "downloads" / "sample.mp4"),
                download_type,
                summary,
                "不下载",
                "finished",
                created_at,
            )
        )

    app = QApplication.instance() or QApplication(sys.argv)
    apply_light_theme(app)
    window = MainWindow(config_store=config, history_store=history, worker_runner=lambda worker: worker.run())
    window.download_page.mode_combo.setCurrentIndex(0)
    window.apply_analysis_result("https://www.youtube.com/watch?v=PqQNXB6hhUs", metadata)
    thumbnail = QPixmap("docs/qa/assets/PqQNXB6hhUs-thumbnail.jpg")
    if not thumbnail.isNull():
        window.download_page.set_thumbnail(thumbnail)
    for index, percent in enumerate([68.0, 32.0, 100.0]):
        task_id = f"qa-task-{index}"
        status = "下载中" if percent < 100 else "已完成"
        window.queue_page.add_task(
            task_id,
            metadata.get("title") or "测试视频",
            status,
            thumbnail=thumbnail if not thumbnail.isNull() else None,
        )
        window.queue_page.update_task(
            task_id,
            status=status,
            progress=percent,
            speed="2.00MiB/s",
            eta="00:18" if percent < 100 else "00:00",
        )
    window.about_page.set_ytdlp_status("2026.03.17")
    window.about_page.set_ffmpeg_status("已配置：D:/ffmpeg/bin/ffmpeg.exe")
    window.show()
    app.processEvents()
    loop = QEventLoop()
    QTimer.singleShot(2000, loop.quit)
    loop.exec()

    for index, name in enumerate(["download", "formats", "queue", "history", "settings", "about"]):
        window.nav.setCurrentRow(index)
        width, height = QA_SCREENSHOT_SIZES[name]
        window.resize(width, height)
        app.processEvents()
        window.grab().save(str(output_dir / f"{index + 1}-{name}.png"))


def _read_metadata(path: Path) -> dict:
    for encoding in ("utf-8-sig", "utf-8", "utf-16"):
        try:
            return json.loads(path.read_text(encoding=encoding))
        except (UnicodeError, json.JSONDecodeError):
            continue
    raise ValueError(f"无法读取视频分析 JSON：{path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Render deterministic GUI QA screenshots.")
    parser.add_argument("--metadata", type=Path, default=Path("docs/qa/real-analysis.json"))
    parser.add_argument("--output-dir", type=Path, default=Path("docs/qa/screenshots"))
    parser.add_argument("--data-dir", type=Path, default=Path(".qa-data"))
    args = parser.parse_args()
    render_screenshots(args.metadata, args.output_dir, args.data_dir)
    print(args.output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
