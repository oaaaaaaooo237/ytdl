"""Client for the local aMule 3.2 REST API."""

from configparser import ConfigParser
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
import traceback
from urllib.error import HTTPError
from urllib.request import HTTPDefaultErrorHandler, HTTPErrorProcessor, HTTPHandler, OpenerDirector, Request

from ytdl_gui.process_tree import ManagedProcess


_FILE_LINK = re.compile(r"ed2k://\|file\|[^|\r\n]+\|[1-9][0-9]*\|([0-9a-f]{32})\|[^\r\n]*/", re.IGNORECASE)


def ed2k_file_hash(link: str) -> str | None:
    match = _FILE_LINK.fullmatch(link.strip())
    return match.group(1).lower() if match else None


class EmuleError(Exception):
    def __init__(self, message: str, *, code: str = "", status: int = 0):
        super().__init__(message)
        self.code = code
        self.status = status


class EmuleApi:
    def __init__(self, port: int, password: str, *, open_request=None):
        self._base_url = f"http://127.0.0.1:{port}/api/v1"
        if open_request is None:
            # The private API is plain loopback HTTP. The default opener eagerly creates an HTTPS context.
            opener = OpenerDirector()
            for handler in (HTTPHandler(), HTTPDefaultErrorHandler(), HTTPErrorProcessor()):
                opener.add_handler(handler)
            open_request = opener.open
        self._open = open_request
        self._password = password
        self._token = None

    def _request(self, method: str, route: str, payload: dict | None = None) -> dict:
        headers = {"Accept": "application/json"}
        body = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"
        request = Request(self._base_url + route, body, headers, method=method)
        try:
            with self._open(request, timeout=5) as response:
                result = response.read()
            return json.loads(result) if result else {}
        except HTTPError as error:
            with error:
                try:
                    code = json.load(error).get("error", {}).get("code", "")
                except (ValueError, AttributeError):
                    code = ""
            raise EmuleError(f"电驴引擎拒绝了请求（HTTP {error.code}）", code=code, status=error.code) from None
        except OSError:
            raise EmuleError("无法连接本机电驴引擎") from None
        except ValueError:
            raise EmuleError("本机电驴引擎返回了无效数据") from None

    def _login(self) -> None:
        session = self._request("POST", "/auth/login?include_token=true", {"password": self._password})
        self._token = session.get("token")
        if not self._token:
            raise EmuleError("电驴引擎登录响应缺少凭据")

    def call(self, method: str, route: str, payload: dict | None = None) -> dict:
        if not self._token:
            self._login()
        try:
            return self._request(method, route, payload)
        except EmuleError as error:
            if error.status != 401:
                raise
        self._token = None
        self._login()
        return self._request(method, route, payload)

    def search(self, query: str, network: str) -> int:
        return self.call("POST", "/search", {"query": query, "type": network})["search_id"]

    def results(self, search_id: int) -> dict:
        return self.call("GET", f"/search/{search_id}/results?limit=1000000000")

    def download_result(self, file_hash: str) -> None:
        if not re.fullmatch(r"[0-9a-fA-F]{32}", file_hash):
            raise EmuleError("电驴搜索结果的文件哈希无效")
        self.call("POST", f"/search/results/{file_hash}/download", {})


class EmuleEngine:
    """Own a private aMule profile and its two local processes."""

    def __init__(self, binary_dir: Path, profile: Path):
        self.binary_dir = binary_dir
        self.profile = profile.resolve()
        self._ec_password = secrets.token_hex(24)
        self._daemon = None
        self._http = None
        self._api = None
        self._flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        self._incoming = self.profile / "Incoming"
        self.upload_limit = None
        self.download_limit = None

    @property
    def incoming_dir(self) -> Path:
        return self._incoming

    def configure(self, directory: str, upload: int, download: int) -> None:
        if self._api is None:
            self._incoming = Path(directory).resolve() / "电驴下载" if directory else self.profile / "Incoming"
            self.upload_limit = upload
            self.download_limit = download

    def _prepare_config(self, ec_port: int) -> None:
        self.profile.mkdir(parents=True, exist_ok=True)
        if self.incoming_dir.resolve() != self.incoming_dir:
            raise EmuleError("电驴保存目录不能是目录链接")
        self.incoming_dir.mkdir(parents=True, exist_ok=True)
        (self.profile / "Temp").mkdir(exist_ok=True)
        config_path = self.profile / "amule.conf"
        config = ConfigParser(interpolation=None, delimiters=("=",))
        config.optionxform = str
        if config_path.exists():
            config.read(config_path, encoding="utf-8-sig")
        if not config.has_section("eMule"):
            config.add_section("eMule")
        config["eMule"].update({
            "IncomingDir": self.incoming_dir.as_posix(),
            "TempDir": (self.profile / "Temp").as_posix(),
            "UDPEnable": "1",
            "UPnPEnabled": "0",
        })
        if self.upload_limit is not None:
            config["eMule"]["MaxUpload"] = str(self.upload_limit)
        if self.download_limit is not None:
            config["eMule"]["MaxDownload"] = str(self.download_limit)
        config["ExternalConnect"] = {
            "AcceptExternalConnections": "1",
            "ECAddress": "127.0.0.1",
            "ECPort": str(ec_port),
            "ECPassword": hashlib.md5(self._ec_password.encode()).hexdigest(),
        }
        staged = config_path.with_suffix(".new")
        with staged.open("w", encoding="utf-8") as stream:
            config.write(stream, space_around_delimiters=False)
        staged.replace(config_path)

    def _local_ports(self) -> tuple[int, int]:
        with socket.socket() as ec, socket.socket() as http:
            ec.bind(("127.0.0.1", 0))
            http.bind(("127.0.0.1", 0))
            return ec.getsockname()[1], http.getsockname()[1]

    def _command(self, name: str) -> str:
        return str(self.binary_dir / (name + ".exe"))

    def _run(self, arguments, **options) -> None:
        subprocess.run(arguments, check=True, timeout=8, creationflags=self._flags,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, **options)

    def _spawn(self, arguments):
        if os.name == "nt":
            return ManagedProcess(arguments, stdout=subprocess.DEVNULL)
        return subprocess.Popen(arguments, creationflags=self._flags, stdin=subprocess.DEVNULL,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def start(self) -> EmuleApi:
        if self._api and self._daemon.poll() is None and self._http.poll() is None:
            return self._api
        for name in ("amuled", "amuleapi", "amulecmd"):
            if not Path(self._command(name)).is_file():
                raise EmuleError("缺少本机电驴引擎组件")
        self.stop()
        ec_port, http_port = self._local_ports()
        admin = secrets.token_hex(24)
        directory = f"--config-dir={self.profile}"
        api = [self._command("amuleapi"), directory]
        try:
            self._prepare_config(ec_port)
            self._run([*api, "--set-admin-pass-stdin"], input=admin + "\n", text=True)
            self._run([*api, "--set-ec-pass-stdin"], input=self._ec_password + "\n", text=True)
            self._run([self._command("amulecmd"), f"--config-file={self.profile / 'remote.conf'}",
                       f"--create-config-from={self.profile / 'amule.conf'}"])
            self._daemon = self._spawn([self._command("amuled"), directory])
            self._http = self._spawn([*api, f"--port={ec_port}", f"--http-port={http_port}", "--bind=127.0.0.1"])
            client = EmuleApi(http_port, admin)
            deadline = time.monotonic() + 12
            while self._daemon.poll() is None and self._http.poll() is None:
                try:
                    if not client.call("GET", "/status").get("ec_connected"):
                        raise EmuleError("本机电驴引擎尚未就绪", status=503)
                except EmuleError as error:
                    if time.monotonic() >= deadline or error.status not in (0, 503):
                        raise
                    time.sleep(0.25)
                else:
                    self._api = client
                    return client
            raise EmuleError("本机电驴引擎提前退出")
        except (OSError, subprocess.SubprocessError, ValueError):
            try:
                (self.profile / "startup-error.log").write_text(traceback.format_exc(), encoding="utf-8")
            except OSError:
                pass
            self.stop()
            raise EmuleError("无法初始化本机电驴引擎") from None
        except EmuleError:
            self.stop()
            raise

    def stop(self) -> None:
        try:
            if self._daemon and self._daemon.poll() is None:
                self._run([self._command("amulecmd"), f"--config-file={self.profile / 'remote.conf'}",
                           "--command=Shutdown"])
                self._daemon.wait(timeout=6)
        except (OSError, subprocess.SubprocessError):
            pass
        finally:
            for process in (self._http, self._daemon):
                if isinstance(process, ManagedProcess):
                    process.close()
                elif process and process.poll() is None:
                    process.terminate()
                    process.wait(timeout=5)
            self._api = self._daemon = self._http = None

    def completed_before_stop(self) -> list[dict]:
        try:
            if self._api:
                return self._api.call("GET", "/downloads?status=completed&limit=1000000000")["downloads"]
            return []
        finally:
            self.stop()

    def _connect(self, api: EmuleApi, network: str) -> None:
        if network in ("global", "all") and not api.call("GET", "/servers")["servers"]:
            try:
                api.call("POST", "/servers_update", {"url": "https://upd.emule-security.org/server.met"})
            except EmuleError as error:
                if error.status != 409:
                    raise
        if network in ("kad", "all") and not (self.profile / "nodes.dat").is_file():
            try:
                api.call("POST", "/kad/update", {"url": "https://upd.emule-security.org/nodes.dat"})
            except EmuleError as error:
                if error.status != 409 and network == "kad":
                    raise
        api.call("POST", "/networks/connect", {"network": {"global": "ed2k", "all": "both", "kad": "kad"}[network]})

    def add_link(self, address: str) -> None:
        api = self.start()
        try:
            self._connect(api, "all")
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                status = api.call("GET", "/status")
                if any(status.get(network, {}).get("state") == "connected" for network in ("ed2k", "kad")):
                    break
                time.sleep(0.5)
        except EmuleError:
            # Keep offline links queued; the daemon will look up sources when reconnecting.
            pass
        result = api.call("POST", "/downloads", {"links": [address]})["results"][0]
        if not result["ok"]:
            raise EmuleError("无法加入该电驴链接，文件可能已在队列中。")

    def search(self, query: str, network: str) -> int:
        api = self.start()
        self._connect(api, network)
        wanted = {"global": {"ed2k"}, "kad": {"kad"}, "all": {"ed2k", "kad"}}[network]
        deadline = time.monotonic() + 17
        while True:
            status = api.call("GET", "/status")
            ready = {name for name in wanted if status.get(name, {}).get("state") == "connected"}
            if ready == wanted:
                break
            if time.monotonic() >= deadline:
                if not ready:
                    raise EmuleError("电驴网络尚未连接，请稍后重新搜索")
                break
            time.sleep(0.5)
        return api.search(query, network)
