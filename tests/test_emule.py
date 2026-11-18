import io
import json
import ssl
from urllib.error import HTTPError

import pytest

from ytdl_gui.emule import EmuleApi, EmuleEngine, EmuleError, ed2k_file_hash


FILE_HASH = "0123456789abcdef0123456789abcdef"
LINK = f"ed2k://|file|测试%20文件.bin|1024|{FILE_HASH.upper()}|/"


def test_file_link_requires_positive_size_and_file_hash():
    assert ed2k_file_hash(LINK) == FILE_HASH
    assert ed2k_file_hash(LINK.replace("|1024|", "|0|")) is None
    assert ed2k_file_hash("ed2k://|server|127.0.0.1|4661|/") is None


def test_loopback_api_does_not_initialize_https(monkeypatch):
    def unavailable():
        raise FileNotFoundError("HTTPS runtime unavailable")

    monkeypatch.setattr(ssl, "_create_default_https_context", unavailable)
    EmuleApi(4713, "test-password")


def test_cold_search_waits_for_real_network_connections(monkeypatch, tmp_path):
    calls = []
    states = iter((
        {"ed2k": {"state": "connecting"}, "kad": {"state": "connecting"}},
        {"ed2k": {"state": "connected"}, "kad": {"state": "connecting"}},
        {"ed2k": {"state": "connected"}, "kad": {"state": "connected"}},
    ))

    class Api:
        def call(self, method, path):
            assert (method, path) == ("GET", "/status")
            calls.append("status")
            return next(states)

        def search(self, query, network):
            calls.append((query, network))
            return 42

    engine = EmuleEngine(tmp_path, tmp_path / "profile")
    monkeypatch.setattr(engine, "start", Api)
    monkeypatch.setattr(engine, "_connect", lambda *_: None)
    monkeypatch.setattr("ytdl_gui.emule.time.sleep", lambda _: None)
    assert engine.search("Debian ISO", "all") == 42
    assert calls == ["status", "status", "status", ("Debian ISO", "all")]


def test_search_and_download_use_correct_methods_and_search_id():
    seen = []
    responses = iter((
        {"token": "test-token"},
        {"search_id": 42},
        {"results": [], "progress": {"state": "running"}},
        {},
    ))

    def open_request(request, timeout):
        seen.append((request.method, request.selector, request.headers, request.data, timeout))
        return io.BytesIO(json.dumps(next(responses)).encode())

    api = EmuleApi(4713, "test-password", open_request=open_request)
    assert api.search("开源 文件", "all") == 42
    assert api.results(42)["progress"]["state"] == "running"
    api.download_result(FILE_HASH)
    assert [row[:2] for row in seen] == [
        ("POST", "/api/v1/auth/login?include_token=true"),
        ("POST", "/api/v1/search"),
        ("GET", "/api/v1/search/42/results?limit=1000000000"),
        ("POST", f"/api/v1/search/results/{FILE_HASH}/download"),
    ]
    assert json.loads(seen[1][3]) == {"query": "开源 文件", "type": "all"}
    assert json.loads(seen[3][3]) == {}
    assert seen[2][2]["Authorization"] == "Bearer test-token"
    assert seen[2][4] > 0


def test_disconnected_or_unreachable_api_preserves_the_error_without_credentials():
    responses = iter((b'{"token":"secret-token"}', b'{"error":{"code":"amuled_rejected"}}'))

    def open_request(request, timeout):
        if request.selector.startswith("/api/v1/auth"):
            return io.BytesIO(next(responses))
        raise HTTPError(request.full_url, 400, "Bad Request", {}, io.BytesIO(next(responses)))

    api = EmuleApi(4713, "secret-password", open_request=open_request)
    with pytest.raises(EmuleError) as caught:
        api.search("开源", "kad")
    assert caught.value.code == "amuled_rejected"
    assert caught.value.status == 400
    assert "secret" not in str(caught.value)


def test_engine_configuration_is_private_to_application_and_preserves_existing_values(tmp_path):
    profile = tmp_path / "profile"
    profile.mkdir()
    config = profile / "amule.conf"
    config.write_text("[eMule]\nMaxUpload=64\n", encoding="utf-8")
    engine = EmuleEngine(tmp_path / "bin", profile)
    engine._prepare_config(4712)

    output = config.read_text(encoding="utf-8")
    assert "MaxUpload=64" in output
    assert "ECAddress=127.0.0.1" in output
    assert "UDPEnable=1" in output
    assert f"IncomingDir={(profile / 'Incoming').as_posix()}" in output
    assert engine._ec_password not in output


def test_missing_engine_does_not_create_a_profile(tmp_path):
    profile = tmp_path / "profile"
    with pytest.raises(EmuleError, match="缺少"):
        EmuleEngine(tmp_path / "bin", profile).start()
    assert not profile.exists()

    def timed_out(request, timeout):
        raise TimeoutError("secret-password")

    with pytest.raises(EmuleError, match="连接") as caught:
        EmuleApi(4713, "secret-password", open_request=timed_out).search("开源", "kad")
    assert "secret" not in str(caught.value)
