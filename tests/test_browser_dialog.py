from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
import sys
from threading import Thread
from time import monotonic, sleep

import pytest

from PySide6.QtWidgets import QApplication, QPushButton

from ytdl_gui.site_login import SiteLoginStore
from ytdl_gui.ui.browser_dialog import BrowserDialog


def wait_until(app, predicate):
    deadline = monotonic() + 10
    while monotonic() < deadline:
        app.processEvents()
        if predicate():
            return
        sleep(0.01)
    pytest.fail("浏览器操作超时")


@pytest.mark.skipif(sys.platform != "win32" or os.environ.get("CAPTAINFLASH_BROWSER_TEST") != "1",
                    reason="Requires standalone native Windows Qt WebEngine runtime")
def test_browser_restores_own_session_and_hands_off_attachment(tmp_path):
    seen_cookies = []

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            if self.path == "/signed-in":
                self.send_header("Set-Cookie", "session=fixture; Path=/; HttpOnly")
            elif self.path == "/view":
                seen_cookies.append(self.headers.get("Cookie"))
            if self.path == "/file":
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Disposition", 'attachment; filename="fixture.bin"')
            else:
                self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(b"<a href='/file'>file</a>")

        def log_message(self, *_args):
            pass

    app = QApplication.instance() or QApplication([])
    with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        url = f"http://127.0.0.1:{server.server_port}"
        store = SiteLoginStore(tmp_path)
        first = BrowserDialog(store, f"{url}/signed-in")
        second = None
        try:
            save = first.findChild(QPushButton, "browserSaveSession")
            wait_until(app, save.isEnabled)
            save.click()
            assert store.sites() == ("127.0.0.1",)
            handed_off = []
            first.url_selected.connect(handed_off.append)
            first.findChild(QPushButton, "browserUsePage").click()
            assert handed_off == [f"{url}/signed-in"]
            app.processEvents()

            second = BrowserDialog(store, f"{url}/view")
            wait_until(app, lambda: seen_cookies == ["session=fixture"])
            use_page = second.findChild(QPushButton, "browserUsePage")
            wait_until(app, use_page.isEnabled)
            second.findChild(QPushButton, "browserClearSession").click()
            assert store.sites() == ()
            second.web_view.reload()
            wait_until(app, lambda: len(seen_cookies) == 2)
            assert seen_cookies[1] is None
            wait_until(app, use_page.isEnabled)
            handed_off.clear()
            second.url_selected.connect(handed_off.append)
            second.web_view.page().runJavaScript("document.querySelector('a').click()")
            file_button = second.findChild(QPushButton, "browserUseFileLink")
            wait_until(app, lambda: not file_button.isHidden())
            assert handed_off == []
            file_button.click()
            assert handed_off == [f"{url}/file"]
        finally:
            first.reject()
            if second is not None:
                second.reject()
            server.shutdown()
            thread.join()
