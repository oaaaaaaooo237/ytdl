from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def test_android_env_prefers_phone_like_soft_keyboard_avds():
    script = read_text("scripts/android_env.ps1")

    assert "hw.keyboard=no" in script
    assert "hw.keyboard=yes" not in script
    assert "show_ime_with_hard_keyboard 1" in script
