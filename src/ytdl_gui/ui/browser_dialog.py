"""Embedded browsing and explicit site-session handoff for downloads."""

from PySide6.QtCore import QUrl, Signal
from PySide6.QtNetwork import QNetworkCookie
from PySide6.QtWebEngineCore import QWebEnginePage, QWebEngineProfile, QWebEngineSettings
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWidgets import (
    QDialog, QHBoxLayout, QLabel, QLineEdit, QPushButton, QVBoxLayout,
)

from ytdl_gui.site_login import SiteLoginStore, cookie_for_host, website_host, website_url


def _encoded(url: QUrl) -> str:
    return bytes(url.toEncoded()).decode("ascii")


class _BrowserPage(QWebEnginePage):
    def acceptNavigationRequest(self, url, navigation_type, is_main_frame):
        if is_main_frame and url.scheme() != "about":
            return not website_url(_encoded(url)).isEmpty()
        return super().acceptNavigationRequest(url, navigation_type, is_main_frame)

    def javaScriptConsoleMessage(self, level, message, line_number, source_id):
        # Third-party page logs can contain login information.
        pass


class _BrowserView(QWebEngineView):
    link_selected = Signal(QUrl)

    def contextMenuEvent(self, event):
        request = self.lastContextMenuRequest()
        menu = self.createStandardContextMenu()
        if request is not None:
            url = website_url(_encoded(request.linkUrl()))
            if not url.isEmpty():
                action = menu.addAction("将此链接带入下载页")
                action.triggered.connect(lambda: self.link_selected.emit(url))
        menu.exec(event.globalPos())
        menu.deleteLater()


class BrowserDialog(QDialog):
    url_selected = Signal(str)
    sessions_changed = Signal()

    def __init__(self, store: SiteLoginStore, initial_url: str = "", parent=None):
        super().__init__(parent)
        self._store = store
        self._cookies = {}
        self._opened_sites = set()
        self._download_url = QUrl()
        self.setWindowTitle("内置浏览器")
        self.resize(1000, 690)
        self.setMinimumSize(620, 440)

        self.web_view = _BrowserView(self)
        self._profile = QWebEngineProfile(self)
        self._page = _BrowserPage(self._profile, self.web_view)
        self._page.settings().setUnknownUrlSchemePolicy(
            QWebEngineSettings.UnknownUrlSchemePolicy.DisallowUnknownUrlSchemes)
        self._page.permissionRequested.connect(lambda permission: permission.deny())
        self._page.newWindowRequested.connect(self._open_requested_window)
        self.web_view.setPage(self._page)
        self.web_view.link_selected.connect(self._select_url)

        self._cookie_store = self._profile.cookieStore()
        self._cookie_store.cookieAdded.connect(self._track_cookie)
        self._cookie_store.cookieRemoved.connect(self._forget_cookie)
        self._profile.downloadRequested.connect(self._capture_download)

        self.address = QLineEdit(placeholderText="输入网站地址")
        self.address.setAccessibleName("网站地址")
        self.address.returnPressed.connect(self._navigate_address)
        controls = QHBoxLayout()
        for title, callback in (("后退", self.web_view.back), ("前进", self.web_view.forward),
                                ("刷新", self.web_view.reload)):
            controls.addWidget(self._button(title, callback))
        controls.addWidget(self.address, 1)
        controls.addWidget(self._button("打开", self._navigate_address))

        self._status = QLabel("在网站页面登录，再保存当前站点的会话。")
        self._status.setWordWrap(True)
        self._status.setObjectName("footerMuted")
        actions = QHBoxLayout()
        self._save = self._button("保存站点会话", self._save_session, "browserSaveSession")
        self._use_page = self._button("将当前地址带入下载页", self._select_page, "browserUsePage")
        self._clear = self._button("清除站点会话", self._clear_session, "browserClearSession")
        self._use_file = self._button("将文件链接带入下载页", self._select_download, "browserUseFileLink")
        self._use_file.hide()
        actions.addWidget(self._save)
        actions.addWidget(self._clear)
        actions.addStretch(1)
        actions.addWidget(self._use_file)
        actions.addWidget(self._use_page)

        layout = QVBoxLayout(self)
        layout.addLayout(controls)
        layout.addWidget(self.web_view, 1)
        layout.addWidget(self._status)
        layout.addLayout(actions)
        self._set_page_actions(False)
        self.web_view.urlChanged.connect(self._display_address)
        self.web_view.loadStarted.connect(self._start_loading)
        self.web_view.loadFinished.connect(self._finish_loading)

        url = website_url(initial_url)
        if not url.isEmpty():
            self._open_url(url)
        else:
            self.address.setFocus()

    def _button(self, title, callback, name=""):
        button = QPushButton(title)
        button.setObjectName(name)
        button.setAutoDefault(False)
        button.clicked.connect(callback)
        return button

    def _navigate_address(self):
        value = self.address.text().strip()
        url = website_url(value)
        if url.isEmpty() and "//" not in value:
            url = website_url(f"https://{value}")
        if url.isEmpty():
            self._status.setText("请输入有效的 HTTP 或 HTTPS 网站地址。")
            return
        self._open_url(url)

    def _open_url(self, url):
        origin_key = (url.scheme(), website_host(url))
        if origin_key not in self._opened_sites:
            self._opened_sites.add(origin_key)
            try:
                for saved in self._store.saved_cookies(_encoded(url)):
                    cookie = QNetworkCookie(saved)
                    if not cookie.domain().startswith("."):
                        cookie.setDomain("")
                    self._cookie_store.setCookie(cookie, url)
            except (OSError, ValueError):
                self._status.setText("读取网站会话失败，请在网站页面重新登录。")
        self.web_view.setUrl(url)

    def _open_requested_window(self, request):
        url = website_url(_encoded(request.requestedUrl()))
        if request.isUserInitiated() and not url.isEmpty():
            self._open_url(url)

    def _display_address(self, url):
        self.address.setText(_encoded(website_url(_encoded(url))))
        self.address.setCursorPosition(0)

    def _set_page_actions(self, enabled):
        for button in (self._use_page, self._save, self._clear):
            button.setEnabled(enabled)

    def _start_loading(self):
        self._set_page_actions(False)
        self._use_file.hide()

    def _finish_loading(self, success):
        self._set_page_actions(success and not website_url(_encoded(self.web_view.url())).isEmpty())
        if not success and self._use_file.isHidden():
            self._status.setText("网页加载未完成。请检查地址或重试。")

    @staticmethod
    def _cookie_key(cookie):
        return cookie.domain(), cookie.path(), bytes(cookie.name())

    def _track_cookie(self, cookie):
        self._cookies[self._cookie_key(cookie)] = QNetworkCookie(cookie)

    def _forget_cookie(self, cookie):
        self._cookies.pop(self._cookie_key(cookie), None)

    def _save_session(self):
        try:
            saved = self._store.save(_encoded(self.web_view.url()), list(self._cookies.values()))
        except (OSError, ValueError):
            self._status.setText("保存网站会话失败。")
            return
        if saved:
            self._status.setText("已保存当前站点会话。")
            self.sessions_changed.emit()
        else:
            self._status.setText("当前站点没有可保存的会话，请先在网站页面登录。")

    def _clear_session(self):
        url = self.web_view.url()
        host = website_host(url)
        try:
            self._store.clear(_encoded(url))
        except (OSError, ValueError):
            self._status.setText("清除网站会话失败。")
            return
        for key, cookie in tuple(self._cookies.items()):
            if cookie_for_host(cookie, host):
                self._cookie_store.deleteCookie(cookie, url)
                self._cookies.pop(key, None)
        self._status.setText("已清除保存的站点会话。")
        self.sessions_changed.emit()

    def _capture_download(self, request):
        self._download_url = website_url(_encoded(request.url()))
        request.cancel()
        self._use_file.setVisible(not self._download_url.isEmpty())
        if self._download_url.isEmpty():
            self._status.setText("此文件没有可带入下载页的 HTTP 或 HTTPS 地址。")
            return
        self._status.setText("已发现网页提供的文件链接。")

    def _select_page(self):
        self._select_url(self.web_view.url())

    def _select_download(self):
        self._select_url(self._download_url)

    def _select_url(self, url):
        target = website_url(_encoded(url))
        if not target.isEmpty():
            self.url_selected.emit(_encoded(target))
            self.accept()

    def done(self, result):
        if self._profile is not None:
            self._page.deleteLater()
            self._profile.deleteLater()
            self._profile = None
            self._cookies.clear()
        super().done(result)
