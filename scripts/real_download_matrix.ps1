$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$Ytdlp = Join-Path $Root "tools\yt-dlp.exe"
$Url = if ($args.Count -gt 0) { $args[0] } else { "https://www.youtube.com/watch?v=KYDPpt3eqaQ" }
$RunId = Get-Date -Format "yyyyMMdd-HHmmss"
$DataDir = Join-Path $Root ".qa-real-smoke\matrix\$RunId"

if (!(Test-Path $Python)) {
  throw "Missing .venv Python: $Python"
}
if (!(Test-Path $Ytdlp)) {
  throw "Missing bundled yt-dlp.exe: $Ytdlp"
}

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

$SmokeCode = @'
import os
from pathlib import Path
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from PySide6.QtWidgets import QApplication

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.history_store import HistoryStore
from ytdl_gui.ui.main_window import MainWindow
from ytdl_gui.ui.player import PreviewState

root = Path(os.environ["YTDL_GUI_ROOT"])
data_dir = Path(os.environ["YTDL_GUI_SMOKE_DATA"])
url = os.environ["YTDL_GUI_SMOKE_URL"]

cases = [
    {
        "name": "audio_video_360p",
        "mode_index": 0,
        "resolution": "360p",
        "container": "mp4",
        "expected_format": "18",
        "summary_contains": ["360p", "mp4", "/"],
        "preview": True,
    },
    {
        "name": "audio_only_128k",
        "mode_index": 1,
        "audio_bitrate": "128k",
        "container": "m4a",
        "expected_format": "140",
        "summary_contains": ["\u97f3\u9891", "129"],
        "preview": False,
    },
    {
        "name": "video_only_144p",
        "mode_index": 2,
        "resolution": "144p",
        "container": "mp4",
        "codec": "H.264",
        "expected_format": "160",
        "summary_contains": ["144p", "\u65e0\u97f3\u9891"],
        "preview": False,
    },
]

app = QApplication.instance() or QApplication(sys.argv)
results = []

for case in cases:
    case_data = data_dir / case["name"]
    download_dir = case_data / "downloads"
    download_dir.mkdir(parents=True, exist_ok=True)
    config = ConfigStore(case_data)
    config.save(
        AppConfig(
            active_ytdlp_path=str(root / "tools" / "yt-dlp.exe"),
            default_save_dir=str(download_dir),
        )
    )
    history = HistoryStore(case_data)
    history.clear()

    window = MainWindow(config_store=config, history_store=history, worker_runner=lambda worker: worker.run())
    window.download_page.mode_combo.setCurrentIndex(case["mode_index"])
    if "resolution" in case:
        window.formats_page.resolution_combo.setCurrentText(case["resolution"])
    if "container" in case:
        window.formats_page.container_combo.setCurrentText(case["container"])
    if "codec" in case:
        window.formats_page.codec_combo.setCurrentText(case["codec"])
    if "audio_bitrate" in case:
        window.formats_page.audio_bitrate_combo.setCurrentText(case["audio_bitrate"])
    window.download_page.preview_checkbox.setChecked(case["preview"])
    window.download_page.url_input.setPlainText(url)

    window.start_analysis()
    if window.selected_format_id != case["expected_format"]:
        raise SystemExit(f"{case['name']} selected {window.selected_format_id}, expected {case['expected_format']}")
    for text in case["summary_contains"]:
        if text not in window.selected_format_summary:
            raise SystemExit(f"{case['name']} summary missing {text}: {window.selected_format_summary}")

    window.start_download()
    records = history.list()
    files = [path for path in download_dir.iterdir() if path.is_file()]
    downloaded_bytes = sum(path.stat().st_size for path in files)
    queue_status = window.queue_page.table.item(0, 1).text()
    preview_source = window.download_page.preview_player.player.source().toString()
    preview_loaded = "yes" if preview_source else "no"

    if queue_status != "\u5df2\u5b8c\u6210":
        raise SystemExit(f"{case['name']} queue did not finish: {queue_status}")
    if len(records) != 1:
        raise SystemExit(f"{case['name']} history count {len(records)}")
    if "%(title)s" in records[0].output_path or not Path(records[0].output_path).exists():
        raise SystemExit(f"{case['name']} history output path is not an existing real file: {records[0].output_path}")
    if not files or downloaded_bytes <= 0:
        raise SystemExit(f"{case['name']} downloaded file missing")
    if case["preview"] and window.download_page.preview_player.state != PreviewState.LOADING:
        raise SystemExit(f"{case['name']} preview not loaded")

    results.append(
        {
            "name": case["name"],
            "format_id": window.selected_format_id,
            "summary": window.selected_format_summary,
            "queue_status": queue_status,
            "history_count": len(records),
            "history_output_path": records[0].output_path,
            "downloaded_files": len(files),
            "downloaded_bytes": downloaded_bytes,
            "download_dir": str(download_dir),
            "preview_loaded": preview_loaded,
            "preview_url_length": len(preview_source),
        }
    )

for result in results:
    print(
        "case={name}; format_id={format_id}; summary={summary}; queue_status={queue_status}; "
        "history_count={history_count}; history_output_path={history_output_path}; "
        "downloaded_files={downloaded_files}; downloaded_bytes={downloaded_bytes}; "
        "preview_loaded={preview_loaded}; preview_url_length={preview_url_length}; download_dir={download_dir}".format(**result)
    )
'@

$env:YTDL_GUI_ROOT = $Root
$env:YTDL_GUI_SMOKE_DATA = $DataDir
$env:YTDL_GUI_SMOKE_URL = $Url

$SmokeCode | & $Python -
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
