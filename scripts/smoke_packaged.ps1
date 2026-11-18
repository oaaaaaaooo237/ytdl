$ErrorActionPreference = "Stop"

$Exe = ".\dist\CaptainFlash\CaptainFlash.exe"
if (!(Test-Path $Exe)) {
  throw "Packaged exe not found: $Exe"
}

$YtdlpCandidates = @(
  ".\dist\CaptainFlash\tools\yt-dlp.exe",
  ".\dist\CaptainFlash\_internal\tools\yt-dlp.exe"
)
$Ytdlp = $YtdlpCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (!$Ytdlp) {
  throw "Bundled yt-dlp.exe not found in packaged tools directory."
}

& $Ytdlp --version | Out-Host

$FfmpegCandidates = @(
  ".\dist\CaptainFlash\tools\ffmpeg\bin\ffmpeg.exe",
  ".\dist\CaptainFlash\_internal\tools\ffmpeg\bin\ffmpeg.exe"
)
$Ffmpeg = $FfmpegCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (!$Ffmpeg) {
  throw "Bundled ffmpeg.exe not found in packaged tools directory."
}

& $Ffmpeg -version | Select-Object -First 1 | Out-Host
Write-Host "Packaged smoke inputs exist. Start the GUI manually for visual smoke."
