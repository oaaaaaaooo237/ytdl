$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Tools = Join-Path $Root "tools"
$Target = Join-Path $Tools "yt-dlp.exe"
$Temp = Join-Path $Tools "yt-dlp.tmp.exe"
$Url = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"

New-Item -ItemType Directory -Force -Path $Tools | Out-Null
if (Test-Path $Temp) {
  Remove-Item -LiteralPath $Temp -Force
}

try {
  Invoke-WebRequest -Uri $Url -OutFile $Temp
  $Version = & $Temp --version
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($Version)) {
    throw "yt-dlp version validation failed."
  }

  Move-Item -LiteralPath $Temp -Destination $Target -Force
  Write-Host "yt-dlp version: $Version"
  Write-Host "Saved $Target"
}
catch {
  if (Test-Path $Temp) {
    Remove-Item -LiteralPath $Temp -Force
  }
  throw
}
