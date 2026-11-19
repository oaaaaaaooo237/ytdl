from http.cookiejar import MozillaCookieJar
import sys

import pytest
from PySide6.QtNetwork import QNetworkCookie

from ytdl_gui.site_login import SiteLoginStore, website_url


def cookie(header: bytes):
    return QNetworkCookie.parseCookies(header)[0]


@pytest.mark.parametrize("value", ["file:///C:/Windows", "javascript:alert(1)",
                                  "https://name:secret@example.test", "https://"])
def test_website_address_requires_http_without_embedded_credentials(value):
    assert website_url(value).isEmpty()


@pytest.mark.skipif(sys.platform != "win32", reason="Windows DPAPI")
def test_saved_sites_are_encrypted_scoped_and_clear_independently(tmp_path):
    store = SiteLoginStore(tmp_path)
    media_url = "https://video.example.test/watch"
    other_url = "https://example.net/"
    assert store.save(media_url, [
        cookie(b"session=example-secret; Domain=video.example.test; Path=/; Secure; HttpOnly"),
        cookie(b"parent=example-parent; Domain=.example.test; Path=/video; Secure"),
        cookie(b"host=not-a-parent-cookie; Domain=example.test; Path=/"),
        cookie(b"third-party=not-shared; Domain=example.net; Path=/"),
        cookie(b"unrelated=not-shared; Domain=.notexample.test; Path=/"),
    ])
    assert store.save(other_url, [cookie(b"other=example-other; Domain=example.net; Path=/")])
    assert b"example-secret" not in next(tmp_path.glob("*.bin")).read_bytes()

    reopened = SiteLoginStore(tmp_path)
    assert reopened.sites() == ("example.net", "video.example.test")
    assert reopened.netscape_cookies("https://example.test/") is None
    assert reopened.netscape_cookies("https://child.video.example.test/") is None
    assert reopened.netscape_cookies("http://video.example.test/") is None
    assert reopened.netscape_cookies("https://user@video.example.test/") is None
    cookie_file = tmp_path / "fixture.cookies"
    cookie_file.write_bytes(reopened.netscape_cookies(media_url))
    jar = MozillaCookieJar(str(cookie_file))
    jar.load(ignore_discard=True, ignore_expires=True)
    assert {(c.name, c.path, c.domain_specified) for c in jar} == {
        ("session", "/", False), ("parent", "/video", True),
    }

    assert reopened.clear(media_url)
    assert reopened.netscape_cookies(media_url) is None
    assert reopened.netscape_cookies(other_url)
    assert reopened.clear("example.net")
    assert reopened.sites() == ()


@pytest.mark.skipif(sys.platform != "win32", reason="Windows DPAPI")
def test_empty_or_expired_cookies_do_not_replace_saved_login(tmp_path):
    store = SiteLoginStore(tmp_path)
    url = "https://example.test/"
    assert store.save(url, [cookie(b"a=b; Domain=example.test; Path=/")])
    assert not store.save(url, [cookie(b"a=b; Domain=example.test; Path=/; Expires=Wed, 01 Jan 2003 00:00:00 GMT")])
    assert [bytes(c.name()) for c in store.saved_cookies(url)] == [b"a"]
