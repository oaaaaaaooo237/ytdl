$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$Ytdlp = Join-Path $Root "tools\yt-dlp.exe"
$Urls = if ($args.Count -gt 0) {
  $args
} else {
  @(
    "https://www.youtube.com/watch?v=PqQNXB6hhUs",
    "https://www.youtube.com/shorts/oXFad1nt6v0",
    "https://www.youtube.com/watch?v=svoD582Pas4"
  )
}
$RunId = Get-Date -Format "yyyyMMdd-HHmmss"
$DataDir = Join-Path $Root ".qa-real-smoke\batch\$RunId"
$DownloadDir = Join-Path $DataDir "downloads"

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
import time

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
urls = [url for url in os.environ["YTDL_GUI_SMOKE_URLS"].splitlines() if url.strip()]

if len(urls) < 2:
    raise SystemExit("batch smoke requires at least two URLs")

config = ConfigStore(data_dir)
config.save(
    AppConfig(
        active_ytdlp_path=str(root / "tools" / "yt-dlp.exe"),
        default_save_dir=str(download_dir),
        max_concurrency=2,
    )
)
history = HistoryStore(data_dir)
history.clear()

app = QApplication.instance() or QApplication(sys.argv)
window = MainWindow(config_store=config, history_store=history)
window.download_page.mode_combo.setCurrentIndex(1)
window.download_page.preview_checkbox.setChecked(False)
window.download_page.url_input.setPlainText("\n".join(urls))


def pump_until(predicate, timeout_seconds: int, label: str) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        app.processEvents()
        if predicate():
            return
        time.sleep(0.05)
    raise SystemExit(f"timeout waiting for {label}")


window.start_analysis()
pump_until(lambda: len(window.analyzed_results) == len(urls), 180, "batch analysis")

if set(window.analyzed_results) != set(urls):
    raise SystemExit(f"analyzed URL mismatch: {list(window.analyzed_results)}")

window.start_download()
running_count = sum(1 for state in window._download_task_states.values() if state == "running")
if running_count != min(2, len(urls)):
    raise SystemExit(f"expected two concurrent running downloads, got {running_count}: {window._download_task_states}")

pump_until(lambda: len(history.list()) == len(urls), 600, "batch downloads")
app.processEvents()

records = history.list()
files = [path for path in download_dir.iterdir() if path.is_file()]
queue_statuses = [window.queue_page.table.item(row, 1).text() for row in range(window.queue_page.table.rowCount())]

if window.queue_page.table.rowCount() != len(urls):
    raise SystemExit(f"queue row count {window.queue_page.table.rowCount()}, expected {len(urls)}")
if any(status != "\u5df2\u5b8c\u6210" for status in queue_statuses):
    raise SystemExit(f"batch queue not finished: {queue_statuses}")
if len(records) != len(urls):
    raise SystemExit(f"history count {len(records)}, expected {len(urls)}")
if {record.url for record in records} != set(urls):
    raise SystemExit(f"history URL mismatch: {[record.url for record in records]}")
if len(files) < len(urls):
    raise SystemExit(f"downloaded files {len(files)}, expected at least {len(urls)}")

def path_match_text(value):
    return "".join(character.casefold() for character in value if character.isalnum())

for record in records:
    output_path = Path(record.output_path)
    if "%(title)s" in record.output_path or not output_path.exists():
        raise SystemExit(f"history output path is not an existing real file: {record.output_path}")
    if record.file_size_bytes != output_path.stat().st_size:
        raise SystemExit(
            f"history file size mismatch: title={record.title}; "
            f"record={record.file_size_bytes}; actual={output_path.stat().st_size}"
        )
    expected_title = path_match_text(record.title)
    actual_stem = path_match_text(output_path.stem)
    if expected_title and actual_stem and expected_title not in actual_stem and actual_stem not in expected_title:
        raise SystemExit(f"history output path does not match record title: title={record.title}; output={record.output_path}")

print(f"url_count={len(urls)}")
print(f"queue_rows={window.queue_page.table.rowCount()}")
print(f"queue_statuses={','.join(queue_statuses)}")
print(f"history_count={len(records)}")
print(f"downloaded_files={len(files)}")
print(f"downloaded_bytes={sum(path.stat().st_size for path in files)}")
print(f"download_dir={download_dir}")
for index, record in enumerate(records, start=1):
    print(f"record_{index}_url={record.url}")
    print(f"record_{index}_format={record.format_summary}")
    print(f"record_{index}_output_path={record.output_path}")
    print(f"record_{index}_file_size_bytes={record.file_size_bytes}")
'@

$env:YTDL_GUI_ROOT = $Root
$env:YTDL_GUI_SMOKE_DATA = $DataDir
$env:YTDL_GUI_SMOKE_DOWNLOADS = $DownloadDir
$env:YTDL_GUI_SMOKE_URLS = ($Urls -join "`n")

$SmokeCode | & $Python -
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
