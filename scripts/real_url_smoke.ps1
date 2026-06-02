$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$Ytdlp = Join-Path $Root "tools\yt-dlp.exe"
$RunId = Get-Date -Format "yyyyMMdd-HHmmss"
$DataDir = Join-Path $Root ".qa-real-smoke\basic\$RunId"
$DownloadDir = Join-Path $DataDir "downloads"
$Url = if ($args.Count -gt 0) { $args[0] } else { "https://www.youtube.com/watch?v=KYDPpt3eqaQ" }

if (!(Test-Path $Python)) {
  throw "Missing .venv Python: $Python"
}
if (!(Test-Path $Ytdlp)) {
  throw "Missing bundled yt-dlp.exe: $Ytdlp"
}

New-Item -ItemType Directory -Force -Path $DataDir, $DownloadDir | Out-Null

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

root = Path(os.environ["YTDL_GUI_ROOT"])
data_dir = Path(os.environ["YTDL_GUI_SMOKE_DATA"])
download_dir = Path(os.environ["YTDL_GUI_SMOKE_DOWNLOADS"])
url = os.environ["YTDL_GUI_SMOKE_URL"]

config = ConfigStore(data_dir)
config.save(
    AppConfig(
        active_ytdlp_path=str(root / "tools" / "yt-dlp.exe"),
        default_save_dir=str(download_dir),
    )
)
history = HistoryStore(data_dir)
history.clear()

app = QApplication.instance() or QApplication(sys.argv)
window = MainWindow(config_store=config, history_store=history, worker_runner=lambda worker: worker.run())
window.download_page.mode_combo.setCurrentIndex(1)
window.download_page.url_input.setPlainText(url)

window.start_analysis()
if not window.selected_format_id:
    raise SystemExit("analysis did not select a format")
if window.selected_format_id not in {"139", "140", "249", "251"}:
    raise SystemExit(f"audio-only smoke selected unexpected format {window.selected_format_id}")

window.start_download()
records = history.list()
files = [path for path in download_dir.iterdir() if path.is_file()]

print(f"title={window.analyzed_metadata.get('title')}")
print(f"format_id={window.selected_format_id}")
print(f"format_summary={window.selected_format_summary}")
print(f"status={window.download_page.status_label.text()}")
print(f"queue_status={window.queue_page.table.item(0, 1).text()}")
print(f"history_count={len(records)}")
print(f"history_output_path={records[0].output_path if records else ''}")
print(f"downloaded_files={len(files)}")
print(f"downloaded_bytes={sum(path.stat().st_size for path in files)}")
print(f"download_dir={download_dir}")

if window.queue_page.table.item(0, 1).text() != "\u5df2\u5b8c\u6210":
    raise SystemExit("queue did not finish")
if len(records) != 1:
    raise SystemExit("history was not written")
if "%(title)s" in records[0].output_path or not Path(records[0].output_path).exists():
    raise SystemExit(f"history output path is not an existing real file: {records[0].output_path}")
if not files:
    raise SystemExit("downloaded file missing")
'@

$env:YTDL_GUI_ROOT = $Root
$env:YTDL_GUI_SMOKE_DATA = $DataDir
$env:YTDL_GUI_SMOKE_DOWNLOADS = $DownloadDir
$env:YTDL_GUI_SMOKE_URL = $Url

$SmokeCode | & $Python -
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
