$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$version = '1.37.0'
$release = "https://github.com/aria2/aria2/releases/download/release-$version"
$cache = Join-Path $root ".qa-data\aria2-$version"
$install = Join-Path $root 'tools\aria2'
New-Item -ItemType Directory -Path $cache, $install -Force | Out-Null

function Get-Aria2Asset([string]$name, [string]$hash, [string]$baseUrl = $release) {
    $path = Join-Path $cache $name
    if (!(Test-Path -LiteralPath $path)) {
        Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/$name" -OutFile "$path.tmp"
        if ((Get-FileHash -LiteralPath "$path.tmp" -Algorithm SHA256).Hash -ne $hash) {
            throw "Invalid SHA256 for $name"
        }
        Move-Item -LiteralPath "$path.tmp" -Destination $path
    }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $hash) {
        throw "Invalid SHA256 for $name"
    }
    return $path
}

# The Windows ZIP checksum comes from Microsoft's aria2.aria2 WinGet manifest.
$build = "aria2-$version-win-64bit-build1"
$binary = Get-Aria2Asset "$build.zip" '67D015301EEF0B612191212D564C5BB0A14B5B9C4796B76454276A4D28D9B288'
$source = Get-Aria2Asset "aria2-$version.tar.xz" '60A420AD7085EB616CB6E2BDF0A7206D68FF3D37FB5A956DC44242EB2F79B66B'
$unpacked = Join-Path $cache 'package-bin'
Expand-Archive -LiteralPath $binary -DestinationPath $unpacked -Force
$portable = Join-Path $unpacked $build
foreach ($file in 'aria2c.exe', 'COPYING', 'LICENSE.OpenSSL', 'AUTHORS', 'README.mingw') {
    Copy-Item -LiteralPath (Join-Path $portable $file) -Destination $install -Force
}
Copy-Item -LiteralPath $source -Destination $install -Force

# Corresponding sources of the statically linked dependencies in Dockerfile.mingw.
$linkedSources = Join-Path $install 'sources'
New-Item -ItemType Directory -Path $linkedSources -Force | Out-Null
$archives = @(
    (Get-Aria2Asset 'gmp-6.3.0.tar.xz' 'A3C2B80201B89E68616F4AD30BC66AEE4927C3CE50E33929CA819D5C43538898' 'https://ftp.gnu.org/gnu/gmp'),
    (Get-Aria2Asset 'expat-2.5.0.tar.bz2' '6F0E6E01F7B30025FA05C85FDAD1E5D0EC7FD35D9F61B22F34998DE11969FF67' 'https://github.com/libexpat/libexpat/releases/download/R_2_5_0'),
    (Get-Aria2Asset 'sqlite-autoconf-3430100.tar.gz' '39116C94E76630F22D54CD82C3CEA308565F1715F716D1B2527F1C9C969BA4D9' 'https://www.sqlite.org/2023'),
    (Get-Aria2Asset 'zlib-1.3.tar.gz' 'FF0BA4C292013DBC27530B3A81E1F9A813CD39DE01CA5E0F8BF355702EFA593E' 'https://zlib.net/fossils'),
    (Get-Aria2Asset 'c-ares-1.19.1.tar.gz' '321700399B72ED0E037D0074C629E7741F6B2EC2DDA92956ABE3E9671D3E268E' 'https://github.com/c-ares/c-ares/releases/download/cares-1_19_1'),
    (Get-Aria2Asset 'libssh2-1.11.0.tar.bz2' 'E56D81F01F090D3C6EAF5F56C645610FD1A90D92819541D0079AE363D1EED11E' 'https://libssh2.org/download')
)
Copy-Item -LiteralPath $archives -Destination $linkedSources -Force
