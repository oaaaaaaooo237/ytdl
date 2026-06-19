param(
  [string]$SdkRoot = "",
  [string]$AvdDevice = "medium_phone",
  [switch]$CreateMatrixAvds
)

$ErrorActionPreference = "Stop"

$avdMatrix = @(
  @{ Name = "ytdl_api31_play_x86_64"; Package = "system-images;android-31;google_apis_playstore;x86_64"; Target = "android-31" },
  @{ Name = "ytdl_api35_play_x86_64"; Package = "system-images;android-35;google_apis_playstore;x86_64"; Target = "android-35" },
  @{ Name = "ytdl_api36_play_x86_64"; Package = "system-images;android-36.1;google_apis_playstore;x86_64"; Target = "android-36.1" },
  @{ Name = "ytdl_api37_play_x86_64"; Package = "system-images;android-37.0;google_apis_playstore_ps16k;x86_64"; Target = "android-37.0" }
)

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

function Resolve-Gradle {
  $candidates = @(
    "D:\DevTools\gradle-9.4.1\bin\gradle.bat",
    "D:\DevTools\gradle-8.11.1\bin\gradle.bat",
    "D:\DevTools\gradle-8.10.2\bin\gradle.bat"
  )

  foreach ($candidate in $candidates) {
    if (Test-Path $candidate) {
      return (Resolve-Path $candidate).Path
    }
  }

  $fromPath = Get-Command gradle -ErrorAction SilentlyContinue
  if ($fromPath) {
    return $fromPath.Source
  }

  return ""
}

$resolvedSdkRoot = Resolve-AndroidSdkRoot -Requested $SdkRoot
$env:ANDROID_HOME = $resolvedSdkRoot
$env:ANDROID_SDK_ROOT = $resolvedSdkRoot
$env:ANDROID_AVD_HOME = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { Join-Path $env:USERPROFILE ".android\avd" }

$platformTools = Join-Path $resolvedSdkRoot "platform-tools"
$emulatorDir = Join-Path $resolvedSdkRoot "emulator"
$cmdlineTools21 = Join-Path $resolvedSdkRoot "cmdline-tools\21.0\bin"
$cmdlineToolsLatest = Join-Path $resolvedSdkRoot "cmdline-tools\latest\bin"
$cmdlineTools = if (Test-Path $cmdlineTools21) { $cmdlineTools21 } else { $cmdlineToolsLatest }

Require-Path $platformTools "Android platform-tools"
Require-Path $emulatorDir "Android emulator tools"
Require-Path $cmdlineTools "Android command-line tools"

$pathEntries = @($platformTools, $emulatorDir, $cmdlineTools)
$existingPath = $env:Path -split ";"
$env:Path = (($pathEntries + $existingPath) | Where-Object { $_ } | Select-Object -Unique) -join ";"

$java = Get-Command java -ErrorAction SilentlyContinue
$javac = Get-Command javac -ErrorAction SilentlyContinue
$gradlePath = Resolve-Gradle
$adb = Get-Command adb -ErrorAction SilentlyContinue
$emulator = Get-Command emulator -ErrorAction SilentlyContinue
$sdkmanager = Get-Command sdkmanager -ErrorAction SilentlyContinue
$avdmanager = Get-Command avdmanager -ErrorAction SilentlyContinue

Write-Host "ANDROID_SDK_ROOT=$resolvedSdkRoot"
Write-Host "ANDROID_AVD_HOME=$env:ANDROID_AVD_HOME"
Write-Host "java=$($java.Source)"
Write-Host "javac=$($javac.Source)"
Write-Host "gradle=$gradlePath"
Write-Host "adb=$($adb.Source)"
Write-Host "emulator=$($emulator.Source)"
Write-Host "sdkmanager=$($sdkmanager.Source)"
Write-Host "avdmanager=$($avdmanager.Source)"

if (!$java -or !$javac) {
  throw "JDK is missing from PATH. Install JDK 17 and set JAVA_HOME."
}
if (!$gradlePath) {
  throw "Gradle is missing. Install Gradle 9.4.1 or add a Gradle wrapper to the Android project."
}
if (!$adb -or !$emulator -or !$sdkmanager -or !$avdmanager) {
  throw "Android SDK tools are missing from PATH after environment setup."
}

Write-Host ""
Write-Host "Java version:"
Invoke-Tool $java.Source @("-version")

Write-Host ""
Write-Host "Gradle version:"
Invoke-Tool $gradlePath @("-v")

Write-Host ""
Write-Host "ADB version and connected devices:"
Invoke-Tool $adb.Source @("version")
Invoke-Tool $adb.Source @("devices", "-l")

Write-Host ""
Write-Host "Emulator acceleration:"
Invoke-Tool $emulator.Source @("-accel-check")

Write-Host ""
Write-Host "Installed Android SDK packages:"
Invoke-Tool $sdkmanager.Source @("--list_installed")

Write-Host ""
Write-Host "AVD matrix by ini files:"
foreach ($entry in $avdMatrix) {
  $ini = Join-Path $env:ANDROID_AVD_HOME "$($entry.Name).ini"
  if (Test-Path $ini) {
    Write-Host "READY $($entry.Name) target=$($entry.Target)"
  } else {
    Write-Host "MISSING $($entry.Name) target=$($entry.Target)"
  }
}

if ($CreateMatrixAvds) {
  foreach ($entry in $avdMatrix) {
    $ini = Join-Path $env:ANDROID_AVD_HOME "$($entry.Name).ini"
    if (Test-Path $ini) {
      Write-Host "AVD exists: $($entry.Name)"
      continue
    }

    Write-Host "Creating AVD: $($entry.Name)"
    "no" | & $avdmanager.Source create avd --name $entry.Name --package $entry.Package --device $AvdDevice
    if ($LASTEXITCODE -ne 0) {
      throw "Failed to create AVD $($entry.Name)."
    }
  }
}

Write-Host ""
Write-Host "Note: emulator -list-avds and avdmanager list avd may be empty in this environment. The matrix above is based on concrete .ini files and direct launch has been verified."
