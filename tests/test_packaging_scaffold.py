from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def test_fetch_ytdlp_uses_official_source_and_temp_validation():
    script = read_text("scripts/fetch_ytdlp.ps1")

    assert "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe" in script
    assert "yt-dlp.tmp.exe" in script
    assert "--version" in script
    assert "Move-Item" in script
    assert "cookies" not in script.lower()


def test_pyinstaller_spec_bundles_required_assets():
    spec = read_text("packaging/ytdl_gui.spec")

    assert 'str(root / "src" / "ytdl_gui" / "main.py")' in spec
    assert "tools" in spec
    assert "yt-dlp.exe" in spec
    assert '".venv"' in spec
    assert "ffmpeg.exe" in spec
    assert '"tools/ffmpeg/bin"' in spec
    assert "licenses" in spec
    assert "THIRD_PARTY_NOTICES.txt" in spec
    assert "docs" in spec
    assert "gui-reference.png" in spec
    assert "PySide6.QtMultimedia" in spec


def test_package_scripts_and_readme_document_packaging_flow():
    package_script = read_text("scripts/package_win.ps1")
    smoke_script = read_text("scripts/smoke_packaged.ps1")
    readme = read_text("README.md")

    assert "PyInstaller" in package_script
    assert "$LASTEXITCODE" in package_script
    assert "tools\\yt-dlp.exe" in package_script
    assert ".venv\\tools\\ffmpeg\\bin\\ffmpeg.exe" in package_script
    assert "dist\\YTDL-GUI\\YTDL-GUI.exe" in smoke_script
    assert "_internal\\tools\\yt-dlp.exe" in smoke_script
    assert "_internal\\tools\\ffmpeg\\bin\\ffmpeg.exe" in smoke_script
    assert "--version" in smoke_script
    assert "scripts\\fetch_ytdlp.ps1" in readme
    assert ".venv\\tools\\ffmpeg\\bin\\ffmpeg.exe" in readme
    assert "THIRD_PARTY_NOTICES.txt" in readme
    assert "ffmpeg" in readme


def test_third_party_notices_cover_bundled_components():
    notices = read_text("licenses/THIRD_PARTY_NOTICES.txt")

    assert "Python" in notices
    assert "PySide6" in notices
    assert "PyInstaller" in notices
    assert "yt-dlp" in notices
    assert "ffmpeg executable" in notices
    assert "https://ffmpeg.org/" in notices
