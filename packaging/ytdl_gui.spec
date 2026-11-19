# -*- mode: python ; coding: utf-8 -*-

import os
from pathlib import Path


root = Path(SPECPATH).parent
ffmpeg_exe = Path(os.environ.get("YTDL_PACKAGE_ENV", root / ".venv")) / "tools" / "ffmpeg" / "bin" / "ffmpeg.exe"

a = Analysis(
    [str(root / "src" / "ytdl_gui" / "main.py")],
    pathex=[str(root / "src")],
    binaries=[
        (str(root / "tools" / "yt-dlp.exe"), "tools"),
        (str(ffmpeg_exe), "tools/ffmpeg/bin"),
    ],
    datas=[
        (str(root / "assets" / "app-icon.ico"), "assets"),
        (str(root / "assets" / "emule.ico"), "assets"),
        (str(root / "licenses" / "THIRD_PARTY_NOTICES.txt"), "licenses"),
        (str(root / "licenses" / "eMule-GPL-2.0.txt"), "licenses"),
        (str(root / "docs" / "gui-reference.png"), "docs"),
        (str(root / "tools" / "amule"), "tools/amule"),
        (str(root / "tools" / "aria2"), "tools/aria2"),
    ],
    hiddenimports=["PySide6.QtMultimedia", "PySide6.QtWebEngineWidgets", "PySide6.QtWebEngineCore"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
# Qt uses the ICU implementation supplied by Windows 11. A different ICU
# discovered through the build host's PATH must not shadow System32.
a.binaries = [
    (name, source, kind)
    for name, source, kind in a.binaries
    if not (
        Path(name).parent == Path(".")
        and Path(name).suffix.lower() == ".dll"
        and Path(name).name.lower().startswith(("icuuc", "icuin", "icudt"))
    )
]
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="CaptainFlash",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    icon=str(root / "assets" / "app-icon.ico"),
    manifest=str(root / "packaging" / "windows-app.manifest"),
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="CaptainFlash",
)
