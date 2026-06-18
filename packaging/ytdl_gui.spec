# -*- mode: python ; coding: utf-8 -*-

from pathlib import Path


root = Path(SPECPATH).parent
ffmpeg_exe = root / ".venv" / "tools" / "ffmpeg" / "bin" / "ffmpeg.exe"

a = Analysis(
    [str(root / "src" / "ytdl_gui" / "main.py")],
    pathex=[str(root / "src")],
    binaries=[
        (str(root / "tools" / "yt-dlp.exe"), "tools"),
        (str(ffmpeg_exe), "tools/ffmpeg/bin"),
    ],
    datas=[
        (str(root / "licenses" / "THIRD_PARTY_NOTICES.txt"), "licenses"),
        (str(root / "docs" / "gui-reference.png"), "docs"),
    ],
    hiddenimports=["PySide6.QtMultimedia"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="YTDL-GUI",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    manifest=str(root / "packaging" / "windows-app.manifest"),
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="YTDL-GUI",
)
