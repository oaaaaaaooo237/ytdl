from pathlib import Path

from ytdl_gui.config_store import AppConfig, ConfigStore
from ytdl_gui.cookies import cookie_help_text, validate_netscape_cookies
from ytdl_gui.history_store import HistoryRecord, HistoryStore


def test_config_roundtrip_stores_cookie_path_only(app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    config = AppConfig(default_save_dir="D:/Downloads", cookies_path="D:/secrets/cookies.txt", max_concurrency=3)
    store.save(config)

    loaded = store.load()

    payload = store.path.read_text(encoding="utf-8")
    assert loaded.default_save_dir == "D:/Downloads"
    assert loaded.cookies_path == "D:/secrets/cookies.txt"
    assert loaded.max_concurrency == 3
    assert "cookies.txt" in payload
    assert "SID=" not in payload


def test_config_load_ignores_corrupt_json(app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.path.write_text("{not json", encoding="utf-8")

    assert store.load() == AppConfig()


def test_config_load_ignores_wrong_type_and_unknown_fields(app_data_dir: Path):
    store = ConfigStore(app_data_dir)
    store.path.write_text('["not", "a", "config"]', encoding="utf-8")

    assert store.load() == AppConfig()

    store.path.write_text('{"default_save_dir": "D:/Downloads", "future_field": "ignored"}', encoding="utf-8")

    loaded = store.load()

    assert loaded.default_save_dir == "D:/Downloads"
    assert not hasattr(loaded, "future_field")


def test_history_redacts_sensitive_values(app_data_dir: Path):
    store = HistoryStore(app_data_dir)
    record = HistoryRecord(
        title="Example --cookies D:/secrets/cookies.txt SID=abc123",
        url="https://www.youtube.com/watch?v=abc",
        output_path="D:/Downloads/example.mp4",
        download_type="audio_video",
        format_summary="1080p mp4 + m4a",
        subtitle_behavior="none",
        status="finished",
        created_at="2026-05-30T22:00:00",
    )
    store.add(record)

    payload = store.path.read_text(encoding="utf-8")

    assert "Example" in payload
    assert "--cookies" not in payload
    assert "SID=" not in payload


def test_history_redacts_full_sensitive_headers(app_data_dir: Path):
    store = HistoryStore(app_data_dir)
    record = HistoryRecord(
        title="Header Example",
        url="https://www.youtube.com/watch?v=abc",
        output_path="D:/Downloads/example.mp4",
        download_type="audio_video",
        format_summary="Cookie: SID=abc; HSID=def; SSID=ghi\nAuthorization: Bearer secret-token",
        subtitle_behavior="none",
        status="finished",
        created_at="2026-05-30T22:00:00",
    )
    store.add(record)

    payload = store.path.read_text(encoding="utf-8")

    assert "Header Example" in payload
    assert "Cookie:" not in payload
    assert "Authorization:" not in payload
    assert "SID=abc" not in payload
    assert "HSID=def" not in payload
    assert "SSID=ghi" not in payload
    assert "Bearer secret-token" not in payload
    assert "已移除敏感内容" in payload


def test_history_load_ignores_corrupt_json(app_data_dir: Path):
    store = HistoryStore(app_data_dir)
    store.path.write_text("{not json", encoding="utf-8")

    assert store.list() == []


def test_history_load_ignores_wrong_type_unknown_fields_and_malformed_rows(app_data_dir: Path):
    store = HistoryStore(app_data_dir)
    store.path.write_text('{"not": "history"}', encoding="utf-8")

    assert store.list() == []

    store.path.write_text(
        """[
          "bad row",
          {
            "title": "Example",
            "url": "https://www.youtube.com/watch?v=abc",
            "output_path": "D:/Downloads/example.mp4",
            "download_type": "audio_video",
            "format_summary": "1080p mp4 + m4a",
            "subtitle_behavior": "none",
            "status": "finished",
            "created_at": "2026-05-30T22:00:00",
            "future_field": "ignored"
          }
        ]""",
        encoding="utf-8",
    )

    records = store.list()

    assert len(records) == 1
    assert records[0].title == "Example"
    assert not hasattr(records[0], "future_field")


def test_history_load_sanitizes_existing_sensitive_values(app_data_dir: Path):
    store = HistoryStore(app_data_dir)
    store.path.write_text(
        """[
          {
            "title": "Existing --cookies D:/secrets/cookies.txt SID=abc",
            "url": "https://www.youtube.com/watch?v=abc",
            "output_path": "D:/Downloads/example.mp4",
            "download_type": "audio_video",
            "format_summary": "Cookie: SID=abc; HSID=def; SSID=ghi\\nAuthorization: Bearer secret-token token=zzz",
            "subtitle_behavior": "none",
            "status": "finished",
            "created_at": "2026-05-30T22:00:00"
          }
        ]""",
        encoding="utf-8",
    )

    record = store.list()[0]
    combined = " ".join([record.title, record.format_summary])

    assert "--cookies" not in combined
    assert "D:/secrets" not in combined
    assert "SID=" not in combined
    assert "HSID=" not in combined
    assert "SSID=" not in combined
    assert "token=" not in combined
    assert "Authorization:" not in combined
    assert "Cookie:" not in combined
    assert "Bearer secret-token" not in combined
    assert "已移除敏感内容" in combined


def test_cookie_validation_accepts_netscape_header(tmp_path: Path):
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tFALSE\t0\tNAME\tVALUE\n", encoding="utf-8")

    result = validate_netscape_cookies(cookie_file)

    assert result.ok is True
    assert result.message == "cookies.txt 格式看起来有效"


def test_cookie_validation_rejects_random_text(tmp_path: Path):
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text("not cookies", encoding="utf-8")

    result = validate_netscape_cookies(cookie_file)

    assert result.ok is False
    assert "Netscape" in result.message


def test_cookie_validation_error_does_not_echo_secret_like_content(tmp_path: Path):
    cookie_file = tmp_path / "cookies.txt"
    cookie_file.write_text("SID=abc123\nAuthorization: Bearer secret-token", encoding="utf-8")

    result = validate_netscape_cookies(cookie_file)

    assert result.ok is False
    assert "SID=abc123" not in result.message
    assert "secret-token" not in result.message


def test_cookie_help_mentions_sensitive_data():
    text = cookie_help_text()

    assert "敏感登录数据" in text
    assert "登录目标站点" in text
    assert "打开目标站点页面" in text
    assert "Netscape" in text
    assert "浏览器扩展" in text or "可信工具" in text
