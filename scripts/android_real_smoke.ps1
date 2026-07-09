param(
  [switch]$RunPythonTests,
  [switch]$SkipUnitTests,
  [switch]$SkipAssemble,
  [switch]$SkipConnectedSafe,
  [switch]$RunRealAnalyze,
  [switch]$RunRealSingleDownload,
  [switch]$RunRealSplitDownload,
  [switch]$RunRealRequiredMerge,
  [switch]$RunRealShortsAnalyze,
  [switch]$RunRealSubtitleDownload,
  [switch]$ForceRealYoutube,
  [int]$AnalysisIntervalMinutes = 10,
  [int]$DownloadIntervalMinutes = 30
)

$ErrorActionPreference = "Stop"

function Resolve-RepoRoot {
  $root = & git rev-parse --show-toplevel
  if ($LASTEXITCODE -ne 0 -or !$root) {
    throw "Unable to resolve git repository root."
  }
  return $root.Trim()
}

function Invoke-Checked {
  param(
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$WorkingDirectory
  )

  Write-Host ""
  Write-Host "RUN $FilePath $($Arguments -join ' ')"
  Push-Location $WorkingDirectory
  try {
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
      throw "$FilePath failed with exit code $LASTEXITCODE."
    }
  } finally {
    Pop-Location
  }
}

function Read-State {
  param([string]$Path)
  if (!(Test-Path $Path)) {
    return [pscustomobject]@{}
  }

  $raw = Get-Content -Raw -Encoding UTF8 $Path
  if (!$raw.Trim()) {
    return [pscustomobject]@{}
  }

  return $raw | ConvertFrom-Json
}

function Save-State {
  param([string]$Path, [object]$State)
  $dir = Split-Path -Parent $Path
  if (!(Test-Path $dir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
  }
  $State | ConvertTo-Json -Depth 4 | Set-Content -Path $Path -Encoding UTF8
}

function Get-StateValue {
  param([object]$State, [string]$Name)
  $property = $State.PSObject.Properties[$Name]
  if ($property) {
    return [string]$property.Value
  }
  return ""
}

function Set-StateValue {
  param([object]$State, [string]$Name, [string]$Value)
  $property = $State.PSObject.Properties[$Name]
  if ($property) {
    $property.Value = $Value
  } else {
    $State | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
  }
}

function Assert-RealYoutubeInterval {
  param(
    [object]$State,
    [string]$PropertyName,
    [int]$MinimumMinutes,
    [switch]$Force
  )

  if ($Force) {
    return
  }

  $lastValue = Get-StateValue -State $State -Name $PropertyName
  if (!$lastValue) {
    return
  }

  $lastUtc = [DateTime]::Parse($lastValue).ToUniversalTime()
  $elapsed = [DateTime]::UtcNow - $lastUtc
  if ($elapsed.TotalMinutes -lt $MinimumMinutes) {
    $remaining = [Math]::Ceiling($MinimumMinutes - $elapsed.TotalMinutes)
    throw "Real YouTube throttle active for $PropertyName. Wait at least $remaining more minute(s), or pass -ForceRealYoutube if the user explicitly approves."
  }
}

function Mark-RealYoutubeRun {
  param([object]$State, [string]$PropertyName)
  Set-StateValue -State $State -Name $PropertyName -Value ([DateTime]::UtcNow.ToString("o"))
}

function Invoke-Gradle {
  param([string[]]$Arguments)
  Invoke-Checked -FilePath $GradlePath -Arguments $Arguments -WorkingDirectory $AndroidDir
}

$RepoRoot = Resolve-RepoRoot
$AndroidDir = Join-Path $RepoRoot "android"
$EnvScript = Join-Path $RepoRoot "scripts\android_env.ps1"
$GradlePath = Join-Path $AndroidDir "gradlew.bat"
$PythonPath = Join-Path $RepoRoot ".venv\Scripts\python.exe"
$StatePath = Join-Path $RepoRoot ".qa-real-smoke\android-real-youtube-state.json"

if (!(Test-Path $EnvScript)) {
  throw "Missing Android environment script: $EnvScript"
}
if (!(Test-Path $GradlePath)) {
  throw "Missing Android Gradle wrapper: $GradlePath"
}

Write-Host "Android real smoke helper"
Write-Host "Repo: $RepoRoot"
Write-Host "Boundary: this script is supporting evidence only. Final acceptance still requires foreground visible Computer Use GUI testing."
Write-Host "Default mode avoids real YouTube network requests. Real YouTube steps require explicit switches."

Invoke-Checked -FilePath "powershell" -Arguments @("-ExecutionPolicy", "Bypass", "-File", $EnvScript) -WorkingDirectory $RepoRoot

if ($RunPythonTests) {
  if (!(Test-Path $PythonPath)) {
    throw "Python venv not found: $PythonPath"
  }
  Invoke-Checked -FilePath $PythonPath -Arguments @("-m", "pytest") -WorkingDirectory $RepoRoot
}

if (!$SkipUnitTests) {
  Invoke-Gradle @(":app:testDebugUnitTest")
}

if (!$SkipAssemble) {
  Invoke-Gradle @(":app:assembleDebug")
}

if (!$SkipConnectedSafe) {
  Invoke-Gradle @(":app:connectedDebugAndroidTest")
}

$state = Read-State -Path $StatePath
$ranRealYoutube = $false

if ($RunRealAnalyze) {
  Assert-RealYoutubeInterval -State $state -PropertyName "lastAnalysisUtc" -MinimumMinutes $AnalysisIntervalMinutes -Force:$ForceRealYoutube
  Mark-RealYoutubeRun -State $state -PropertyName "lastAnalysisUtc"
  Save-State -Path $StatePath -State $state
  $ranRealYoutube = $true
  Invoke-Gradle @(
    ":app:connectedDebugAndroidTest",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#analyzesRequiredYoutubeSmokeUrl",
    "-Pandroid.testInstrumentationRunnerArguments.realYoutube=true"
  )
}

if ($RunRealShortsAnalyze) {
  Assert-RealYoutubeInterval -State $state -PropertyName "lastAnalysisUtc" -MinimumMinutes $AnalysisIntervalMinutes -Force:$ForceRealYoutube
  Mark-RealYoutubeRun -State $state -PropertyName "lastAnalysisUtc"
  Save-State -Path $StatePath -State $state
  $ranRealYoutube = $true
  Invoke-Gradle @(
    ":app:connectedDebugAndroidTest",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#analyzesRequiredYoutubeShortsSmokeUrl",
    "-Pandroid.testInstrumentationRunnerArguments.realYoutube=true"
  )
}

if ($RunRealSingleDownload) {
  Assert-RealYoutubeInterval -State $state -PropertyName "lastDownloadUtc" -MinimumMinutes $DownloadIntervalMinutes -Force:$ForceRealYoutube
  Mark-RealYoutubeRun -State $state -PropertyName "lastDownloadUtc"
  Save-State -Path $StatePath -State $state
  $ranRealYoutube = $true
  Invoke-Gradle @(
    ":app:connectedDebugAndroidTest",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#downloadsRequiredYoutubeSmokeUrlWithRealProgress",
    "-Pandroid.testInstrumentationRunnerArguments.realYoutube=true"
  )
}

if ($RunRealSplitDownload) {
  Assert-RealYoutubeInterval -State $state -PropertyName "lastDownloadUtc" -MinimumMinutes $DownloadIntervalMinutes -Force:$ForceRealYoutube
  Mark-RealYoutubeRun -State $state -PropertyName "lastDownloadUtc"
  Save-State -Path $StatePath -State $state
  $ranRealYoutube = $true
  Invoke-Gradle @(
    ":app:connectedDebugAndroidTest",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#downloadsRequiredYoutubeSmokeUrlSplitFormats",
    "-Pandroid.testInstrumentationRunnerArguments.realYoutube=true"
  )
}

if ($RunRealRequiredMerge) {
  Assert-RealYoutubeInterval -State $state -PropertyName "lastDownloadUtc" -MinimumMinutes $DownloadIntervalMinutes -Force:$ForceRealYoutube
  Mark-RealYoutubeRun -State $state -PropertyName "lastDownloadUtc"
  Save-State -Path $StatePath -State $state
  $ranRealYoutube = $true
  Invoke-Gradle @(
    ":app:connectedDebugAndroidTest",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.media.RequiredUrlMergeInstrumentedTest",
    "-Pandroid.testInstrumentationRunnerArguments.realYoutube=true"
  )
}

if ($RunRealSubtitleDownload) {
  Assert-RealYoutubeInterval -State $state -PropertyName "lastSubtitleUtc" -MinimumMinutes $DownloadIntervalMinutes -Force:$ForceRealYoutube
  Mark-RealYoutubeRun -State $state -PropertyName "lastSubtitleUtc"
  Save-State -Path $StatePath -State $state
  $ranRealYoutube = $true
  Invoke-Gradle @(
    ":app:connectedDebugAndroidTest",
    "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.SubtitleDownloadInstrumentedTest",
    "-Pandroid.testInstrumentationRunnerArguments.realYoutube=true",
    "-Pandroid.testInstrumentationRunnerArguments.realYoutubeSubtitle=true"
  )
}

if ($ranRealYoutube) {
  Save-State -Path $StatePath -State $state
  Write-Host "Updated real YouTube throttle state: $StatePath"
} else {
  Write-Host "No real YouTube opt-in step was run."
}

Write-Host "Android real smoke helper completed."
