$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Python = Join-Path $Root ".venv\Scripts\python.exe"
$Ytdlp = Join-Path $Root "tools\yt-dlp.exe"
$Ffmpeg = Join-Path $Root ".venv\tools\ffmpeg\bin\ffmpeg.exe"
$Url = if ($args.Count -gt 0) { $args[0] } else { "https://www.youtube.com/shorts/oXFad1nt6v0" }
$RunId = Get-Date -Format "yyyyMMdd-HHmmss"
$DataDir = Join-Path $Root ".qa-real-smoke\subtitles\$RunId"

if (!(Test-Path $Python)) {
  throw "Missing .venv Python: $Python"
}
if (!(Test-Path $Ytdlp)) {
  throw "Missing bundled yt-dlp.exe: $Ytdlp"
}
if (!(Test-Path $Ffmpeg)) {
  throw "Missing project ffmpeg.exe: $Ffmpeg"
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

root = Path(os.environ["YTDL_GUI_ROOT"])
data_dir = Path(os.environ["YTDL_GUI_SMOKE_DATA"])
url = os.environ["YTDL_GUI_SMOKE_URL"]
ffmpeg_path = root / ".venv" / "tools" / "ffmpeg" / "bin" / "ffmpeg.exe"

cases = [
    ("subtitle_file", "\u4e0b\u8f7d\u5b57\u5e55\u6587\u4ef6"),
    ("subtitle_burn", "\u70e7\u5f55"),
]

app = QApplication.instance() or QApplication(sys.argv)
results = []

for case_name, subtitle_text in cases:
    case_dir = data_dir / case_name
    download_dir = case_dir / "downloads"
    download_dir.mkdir(parents=True, exist_ok=True)
    config = ConfigStore(case_dir)
    config.save(
        AppConfig(
            active_ytdlp_path=str(root / "tools" / "yt-dlp.exe"),
            default_save_dir=str(download_dir),
            ffmpeg_path=str(ffmpeg_path),
            max_concurrency=1,
        )
    )
    history = HistoryStore(case_dir)
    history.clear()

    window = MainWindow(config_store=config, history_store=history, worker_runner=lambda worker: worker.run())
    window.download_page.mode_combo.setCurrentIndex(0)
    window.formats_page.resolution_combo.setCurrentText("360p")
    window.formats_page.container_combo.setCurrentText("mp4")
    window.formats_page.subtitle_combo.setCurrentText(subtitle_text)
    window.download_page.url_input.setPlainText(url)

    window.start_analysis()
    if not window.selected_format_id:
        raise SystemExit(f"{case_name} analysis did not select a format")

    window.start_download()

    records = history.list()
    files = [path for path in download_dir.iterdir() if path.is_file()]
    subtitle_files = [path for path in files if path.suffix.casefold() in {".srt", ".vtt", ".ass"}]
    media_files = [path for path in files if path.suffix.casefold() in {".mp4", ".mkv", ".webm", ".m4a"}]
    queue_status = window.queue_page.table.item(0, 1).text()

    if queue_status != "\u5df2\u5b8c\u6210":
        raise SystemExit(f"{case_name} queue did not finish: {queue_status}")
    if len(records) != 1:
        raise SystemExit(f"{case_name} history count {len(records)}")
    if not Path(records[0].output_path).exists():
        raise SystemExit(f"{case_name} history output path missing: {records[0].output_path}")
    if not subtitle_files:
        raise SystemExit(f"{case_name} did not download subtitle files")
    if not media_files:
        raise SystemExit(f"{case_name} did not download media files")
    if case_name == "subtitle_burn":
        output_path = Path(records[0].output_path)
        if ".burned" not in output_path.stem:
            raise SystemExit(f"{case_name} history did not point to burned output: {output_path}")

    results.append(
        {
            "case": case_name,
            "format_id": window.selected_format_id,
            "summary": window.selected_format_summary,
            "queue_status": queue_status,
            "history_output_path": records[0].output_path,
            "subtitle_files": len(subtitle_files),
            "media_files": len(media_files),
            "downloaded_bytes": sum(path.stat().st_size for path in files),
            "download_dir": str(download_dir),
        }
    )

for result in results:
    print(
        "case={case}; format_id={format_id}; summary={summary}; queue_status={queue_status}; "
        "history_output_path={history_output_path}; subtitle_files={subtitle_files}; "
        "media_files={media_files}; downloaded_bytes={downloaded_bytes}; download_dir={download_dir}".format(**result)
    )
'@

$env:YTDL_GUI_ROOT = $Root
$env:YTDL_GUI_SMOKE_DATA = $DataDir
$env:YTDL_GUI_SMOKE_URL = $Url

$SmokeCode | & $Python -
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
