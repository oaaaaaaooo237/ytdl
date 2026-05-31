from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from PySide6.QtCore import QEventLoop, QTimer
from PySide6.QtWidgets import QApplication

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryRecord, HistoryStore
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.theme import apply_light_theme


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
    history.add(
        HistoryRecord(
            metadata.get("title") or "测试视频",
            "https://www.youtube.com/watch?v=KYDPpt3eqaQ",
            str(data_dir / "downloads" / "sample.m4a"),
            "仅音频",
            "音频 m4a mp4a.40.2 129.477kbps",
            "不下载",
            "finished",
            "2026-05-31T13:30:00",
        )
    )

    app = QApplication.instance() or QApplication(sys.argv)
    apply_light_theme(app)
    window = MainWindow(config_store=config, history_store=history, worker_runner=lambda worker: worker.run())
    window.download_page.mode_combo.setCurrentIndex(1)
    window.apply_analysis_result("https://www.youtube.com/watch?v=KYDPpt3eqaQ", metadata)
    for index, percent in enumerate([68.0, 32.0, 100.0]):
        task_id = f"qa-task-{index}"
        status = "下载中" if percent < 100 else "已完成"
        window.queue_page.add_task(task_id, metadata.get("title") or "测试视频", status)
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
