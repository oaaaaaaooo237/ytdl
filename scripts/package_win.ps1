$ErrorActionPreference = "Stop"

if (!(Test-Path ".\.venv\Scripts\python.exe")) {
  throw "Missing .venv. Create it and install requirements-dev.txt first."
}

if (!(Test-Path ".\tools\yt-dlp.exe")) {
  throw "Missing tools\yt-dlp.exe. Run scripts\fetch_ytdlp.ps1 first."
}

.\.venv\Scripts\python.exe -m PyInstaller .\packaging\ytdl_gui.spec --noconfirm --clean
if ($LASTEXITCODE -ne 0) {
  throw "PyInstaller packaging failed with exit code $LASTEXITCODE."
}
Write-Host "Packaged dist\YTDL-GUI\YTDL-GUI.exe"
