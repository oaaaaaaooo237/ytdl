"""Browser cookies saved explicitly by the current Windows user."""

import base64
import json
import os
from pathlib import Path
from uuid import uuid4

from PySide6.QtCore import QDateTime, QUrl
from PySide6.QtNetwork import QNetworkCookie


def website_url(value: str) -> QUrl:
    url = QUrl(value.strip(), QUrl.ParsingMode.StrictMode)
    if url.isValid() and url.scheme() in {"https", "http"} and url.host() and not url.userInfo():
        return url
    return QUrl()


def website_host(url: QUrl) -> str:
    return url.host(QUrl.ComponentFormattingOption.EncodeUnicode).lower().rstrip(".")


def cookie_for_host(cookie: QNetworkCookie, host: str) -> bool:
    domain = cookie.domain().lower().rstrip(".")
    return bool(host and domain and (domain == host or (domain.startswith(".") and host.endswith(domain))))


def _usable_cookie(cookie: QNetworkCookie, url: QUrl) -> bool:
    return (
        cookie_for_host(cookie, website_host(url))
        and (not cookie.isSecure() or url.scheme() == "https")
        and (cookie.isSessionCookie() or cookie.expirationDate() > QDateTime.currentDateTimeUtc())
    )


def _protect(data: bytes, decrypt: bool = False) -> bytes:
    from ytdl_gui.queue_store import _protect as dpapi

    try:
        return dpapi(data, decrypt)
    except OSError as exc:
        raise OSError("无法读取或保护当前 Windows 用户的网站会话。") from exc


class SiteLoginStore:
    def __init__(self, data_dir: Path):
        self.path = Path(data_dir) / "site-logins.bin"

    def sites(self) -> tuple[str, ...]:
        return tuple(sorted(self._read()))

    def save(self, url: str, cookies: list[QNetworkCookie]) -> bool:
        target = website_url(url)
        records = [base64.b64encode(bytes(cookie.toRawForm())).decode("ascii")
                   for cookie in cookies if _usable_cookie(cookie, target)]
        if not records:
            return False
        saved = self._read()
        saved[website_host(target)] = records
        self._write(saved)
        return True

    def clear(self, url_or_host: str) -> bool:
        if "://" not in url_or_host:
            if url_or_host.count(":") > 1 and not url_or_host.startswith("["):
                url_or_host = f"[{url_or_host}]"
            url_or_host = f"https://{url_or_host}"
        host = website_host(website_url(url_or_host))
        saved = self._read()
        if host not in saved:
            return False
        del saved[host]
        self._write(saved)
        return True

    def saved_cookies(self, url: str) -> list[QNetworkCookie]:
        target = website_url(url)
        if target.isEmpty():
            return []
        cookies = []
        for raw in self._read().get(website_host(target), []):
            for cookie in QNetworkCookie.parseCookies(base64.b64decode(raw, validate=True)):
                if _usable_cookie(cookie, target):
                    cookies.append(cookie)
        return cookies

    def netscape_cookies(self, url: str) -> bytes | None:
        rows = []
        for cookie in self.saved_cookies(url):
            domain = cookie.domain()
            host_scope = "TRUE" if domain.startswith(".") else "FALSE"
            name = bytes(cookie.name())
            value = bytes(cookie.value())
            if cookie.isHttpOnly():
                domain = f"#HttpOnly_{domain}"
            expires = 0 if cookie.isSessionCookie() else cookie.expirationDate().toSecsSinceEpoch()
            fields = (domain.encode("utf-8"), host_scope.encode("ascii"),
                      (cookie.path() or "/").encode("utf-8"),
                      b"TRUE" if cookie.isSecure() else b"FALSE",
                      str(expires).encode("ascii"), name, value)
            if name and not any(separator in field for field in fields for separator in (b"\t", b"\r", b"\n", b"\x00")):
                rows.append(b"\t".join(fields) + b"\n")
        return b"# Netscape HTTP Cookie File\n" + b"".join(rows) if rows else None

    def _read(self) -> dict[str, list[str]]:
        try:
            encrypted = self.path.read_bytes()
        except FileNotFoundError:
            return {}
        try:
            document = json.loads(_protect(encrypted, decrypt=True))
            sites = document["sites"]
            if document["version"] == 1 and isinstance(sites, dict) and all(
                isinstance(records, list) and all(isinstance(raw, str) for raw in records)
                for records in sites.values()
            ):
                return sites
        except (KeyError, TypeError, ValueError):
            pass
        raise ValueError("网站会话文件格式无效。")

    def _write(self, sites: dict[str, list[str]]) -> None:
        if not sites:
            self.path.unlink(missing_ok=True)
            return
        encoded = json.dumps({"version": 1, "sites": sites}).encode("utf-8")
        encrypted = _protect(encoded)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(f".{uuid4().hex}.tmp")
        try:
            with temporary.open("xb") as output:
                output.write(encrypted)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, self.path)
        finally:
            temporary.unlink(missing_ok=True)
