$ErrorActionPreference = "Stop"

if (!(Test-Path ".\.venv\Scripts\python.exe")) {
  throw "Missing .venv. Create it and install requirements-dev.txt first."
}

if (!(Test-Path ".\tools\yt-dlp.exe")) {
  throw "Missing tools\yt-dlp.exe. Run scripts\fetch_ytdlp.ps1 first."
}

if (!(Test-Path ".\.venv\tools\ffmpeg\bin\ffmpeg.exe")) {
  throw "Missing .venv\tools\ffmpeg\bin\ffmpeg.exe. Put the project-local ffmpeg.exe there before packaging."
}

.\.venv\Scripts\python.exe -m PyInstaller .\packaging\ytdl_gui.spec --noconfirm --clean
if ($LASTEXITCODE -ne 0) {
  throw "PyInstaller packaging failed with exit code $LASTEXITCODE."
}
Write-Host "Packaged dist\YTDL-GUI\YTDL-GUI.exe"
