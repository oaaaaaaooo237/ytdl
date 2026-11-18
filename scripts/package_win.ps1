param([string]$PythonEnvironment = ".\.venv")

$ErrorActionPreference = "Stop"
$packageEnv = (Resolve-Path -LiteralPath $PythonEnvironment).Path
$python = Join-Path $packageEnv "Scripts\python.exe"

if (!(Test-Path -LiteralPath $python)) {
  throw "Missing .venv. Create it and install requirements-dev.txt first."
}

if (!(Test-Path ".\tools\yt-dlp.exe")) {
  throw "Missing tools\yt-dlp.exe. Run scripts\fetch_ytdlp.ps1 first."
}

if (!(Test-Path -LiteralPath (Join-Path $packageEnv "tools\ffmpeg\bin\ffmpeg.exe"))) {
  throw "Missing .venv\tools\ffmpeg\bin\ffmpeg.exe. Put the project-local ffmpeg.exe there before packaging."
}

& "$PSScriptRoot\fetch_amule.ps1"
$previousPackageEnv = $env:YTDL_PACKAGE_ENV
$env:YTDL_PACKAGE_ENV = $packageEnv
try {
  & $python -m PyInstaller .\packaging\ytdl_gui.spec --noconfirm --clean
} finally {
  $env:YTDL_PACKAGE_ENV = $previousPackageEnv
}
if ($LASTEXITCODE -ne 0) {
  throw "PyInstaller packaging failed with exit code $LASTEXITCODE."
}
Write-Host "Packaged dist\YTDL-GUI\YTDL-GUI.exe"
