$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$cache = Join-Path $root ".qa-data\amule-3.2.0"
$install = Join-Path $root "tools\amule"
$release = "https://github.com/amule-org/amule/releases/download/3.2.0"
New-Item -ItemType Directory -Path $cache, $install -Force | Out-Null

function Get-AmuleAsset([string]$name, [string]$hash) {
    $path = Join-Path $cache $name
    if (!(Test-Path -LiteralPath $path)) {
        Invoke-WebRequest -Uri "$release/$name" -OutFile "$path.tmp"
        Move-Item -LiteralPath "$path.tmp" -Destination $path
    }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $hash) {
        throw "Invalid SHA256 for $name"
    }
    return $path
}

$binary = Get-AmuleAsset "aMule-3.2.0-Windows-x64.zip" "6826fb9c3a63394c5ba111647ab397025cc8e44034772263aab18bae50f4ab32"
$source = Get-AmuleAsset "aMule-3.2.0-src.tar.gz" "9c060a79fe5444b2700b4e3c8c6162032cd56dd4a242d384d7ff95d977c288d1"
$unpacked = Join-Path $cache "package-bin"
Expand-Archive -LiteralPath $binary -DestinationPath $unpacked -Force
$portable = Join-Path $unpacked "amule-portable-x64"
foreach ($directory in "bin", "licenses", "share") {
    Copy-Item -LiteralPath (Join-Path $portable $directory) -Destination $install -Recurse -Force
}
Copy-Item -LiteralPath $source -Destination $install -Force
