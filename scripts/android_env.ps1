param(
  [string]$SdkRoot = "",
  [string]$AvdName = "ytdl_api36_play_x86_64",
  [string]$AvdPackage = "system-images;android-36.1;google_apis_playstore;x86_64",
  [string]$AvdDevice = "medium_phone",
  [switch]$CreateBaselineAvd
)

$ErrorActionPreference = "Stop"

function Resolve-AndroidSdkRoot {
  param([string]$Requested)

  $candidates = @(
    $Requested,
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    "D:\Softwares\Android\SDK",
    "$env:LOCALAPPDATA\Android\Sdk"
  ) | Where-Object { $_ -and (Test-Path $_) }

  if (!$candidates) {
    throw "Android SDK not found. Set ANDROID_SDK_ROOT or install Android SDK."
  }

  return (Resolve-Path $candidates[0]).Path
}

function Require-Path {
  param([string]$Path, [string]$Name)
  if (!(Test-Path $Path)) {
    throw "$Name not found: $Path"
  }
}

function Invoke-Tool {
  param([string]$FilePath, [string[]]$Arguments)
  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "$FilePath failed with exit code $LASTEXITCODE."
  }
}

$resolvedSdkRoot = Resolve-AndroidSdkRoot -Requested $SdkRoot
$env:ANDROID_HOME = $resolvedSdkRoot
$env:ANDROID_SDK_ROOT = $resolvedSdkRoot

$platformTools = Join-Path $resolvedSdkRoot "platform-tools"
$emulatorDir = Join-Path $resolvedSdkRoot "emulator"
$cmdlineTools = Join-Path $resolvedSdkRoot "cmdline-tools\latest\bin"

Require-Path $platformTools "Android platform-tools"
Require-Path $emulatorDir "Android emulator tools"
Require-Path $cmdlineTools "Android command-line tools"

$pathEntries = @($platformTools, $emulatorDir, $cmdlineTools)
$existingPath = $env:Path -split ";"
$env:Path = (($pathEntries + $existingPath) | Where-Object { $_ } | Select-Object -Unique) -join ";"
$avdHome = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { Join-Path $env:USERPROFILE ".android\avd" }

$java = Get-Command java -ErrorAction SilentlyContinue
$javac = Get-Command javac -ErrorAction SilentlyContinue
$gradle = Get-Command gradle -ErrorAction SilentlyContinue
$adb = Get-Command adb -ErrorAction SilentlyContinue
$emulator = Get-Command emulator -ErrorAction SilentlyContinue
$sdkmanager = Get-Command sdkmanager -ErrorAction SilentlyContinue
$avdmanager = Get-Command avdmanager -ErrorAction SilentlyContinue

Write-Host "ANDROID_SDK_ROOT=$resolvedSdkRoot"
Write-Host "ANDROID_AVD_HOME=$avdHome"
Write-Host "java=$($java.Source)"
Write-Host "javac=$($javac.Source)"
Write-Host "gradle=$($gradle.Source)"
Write-Host "adb=$($adb.Source)"
Write-Host "emulator=$($emulator.Source)"
Write-Host "sdkmanager=$($sdkmanager.Source)"
Write-Host "avdmanager=$($avdmanager.Source)"

if (!$java -or !$javac) {
  throw "JDK is missing from PATH. Install JDK 17 and set JAVA_HOME."
}
if (!$gradle) {
  throw "Gradle is missing from PATH. Install Gradle or add a Gradle wrapper to the Android project."
}
if (!$adb -or !$emulator -or !$sdkmanager -or !$avdmanager) {
  throw "Android SDK tools are missing from PATH after environment setup."
}

Write-Host ""
Write-Host "Java version:"
Invoke-Tool $java.Source @("-version")

Write-Host ""
Write-Host "Gradle version:"
Invoke-Tool $gradle.Source @("-v")

Write-Host ""
Write-Host "ADB version and connected devices:"
Invoke-Tool $adb.Source @("version")
Invoke-Tool $adb.Source @("devices", "-l")

Write-Host ""
Write-Host "Installed Android SDK packages:"
Invoke-Tool $sdkmanager.Source @("--list_installed")

Write-Host ""
Write-Host "Android Virtual Devices:"
$avdOutput = & $avdmanager.Source list avd
$avdOutput | Out-Host
$emulatorAvds = & $emulator.Source -list-avds
if ($emulatorAvds) {
  Write-Host "Emulator AVD names:"
  $emulatorAvds | Out-Host
}
$avdIni = Join-Path $avdHome "$AvdName.ini"
$hasAvd = (($avdOutput | Select-String -SimpleMatch "Name: $AvdName") -ne $null) -or
  (($emulatorAvds | Select-String -SimpleMatch $AvdName) -ne $null) -or
  (Test-Path $avdIni)

if ($CreateBaselineAvd -and !$hasAvd) {
  Write-Host "Creating baseline AVD: $AvdName"
  "no" | & $avdmanager.Source create avd --force --name $AvdName --package $AvdPackage --device $AvdDevice
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create baseline AVD $AvdName."
  }
  $hasAvd = $true
}

if ($hasAvd) {
  Write-Host "Baseline AVD ready: $AvdName"
  if (!(($emulatorAvds | Select-String -SimpleMatch $AvdName) -ne $null)) {
    Write-Host "Warning: AVD ini exists, but emulator -list-avds did not report it."
  }
} else {
  Write-Host "Baseline AVD missing: $AvdName"
  Write-Host "Run: powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1 -CreateBaselineAvd"
}
