"""Probe and resume ordinary HTTP downloads independently of yt-dlp."""

from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
from dataclasses import dataclass, field
from email.utils import format_datetime, parsedate_to_datetime
from hashlib import sha256
import http.client
from http.cookiejar import DefaultCookiePolicy, MozillaCookieJar
import json
import os
from pathlib import Path
import re
from threading import Event, Lock
from time import monotonic, sleep
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlsplit
from urllib.request import AbstractHTTPHandler, HTTPCookieProcessor, HTTPRedirectHandler, HTTPSHandler, Request, build_opener
from uuid import uuid4

from PySide6.QtCore import QObject, Signal, Slot

from ytdl_gui.workers import DownloadRequest
from ytdl_gui.ytdlp_runner import ProgressEvent


_PARTIAL = 'direct-file.part'
_CHECKPOINT = 'direct-file.json'
_CHECKPOINT_TEMP = 'direct-file.json.tmp'
_RANGE_TEMP = 'direct-file.ranges.tmp'
_BLOCK = 256 * 1024
_PARALLEL_MIN_SIZE = 1024 * 1024
_TIMEOUT = 3
_MEDIA_TYPES = {
    'application/dash+xml', 'application/vnd.apple.mpegurl', 'application/x-mpegurl',
}
_CONNECTION_ERRORS = (URLError, TimeoutError, ConnectionError, http.client.HTTPException)


class DirectFileError(Exception):
    pass


class _RestartRequired(DirectFileError):
    pass


class _Skipped(DirectFileError):
    pass


class _Stopped(Exception):
    pass


@dataclass(frozen=True)
class DirectFileMetadata:
    filename: str
    content_type: str | None
    total_bytes: int | None
    kind: str
    resume_available: bool
    final_url: str = field(repr=False)


def sanitize_file_name(value: str) -> str:
    basename = value.replace('\\', '/').rsplit('/', 1)[-1]
    name = re.sub(r'[<>:"|?*\x00-\x1f\x7f-\x9f]', '_', basename).strip().rstrip('. ')
    if not name:
        return '下载文件'
    device = name.split('.', 1)[0].rstrip().upper()
    if re.fullmatch(r'CON|PRN|AUX|NUL|CONIN\$|CONOUT\$|(?:COM|LPT)[1-9¹²³]', device):
        name = '_' + name
    stem, extension = os.path.splitext(name)
    extension = _utf8_prefix(extension, 32).rstrip('. ')
    return _utf8_prefix(stem, 230 - len(extension.encode())).rstrip('. ') + extension


def _utf8_prefix(value: str, limit: int) -> str:
    return value.encode('utf-8', errors='replace')[:limit].decode('utf-8', errors='ignore')


def _filename(disposition: str, url: str) -> str:
    parameters = {}
    for match in re.finditer(r';\s*([^;=\s]+)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^;]*))', disposition):
        key, quoted, plain = match.groups()
        value = re.sub(r'\\(["\\])', r'\1', quoted) if quoted is not None else plain.strip()
        parameters.setdefault(key.lower(), value)
    charset, separator, value = parameters.get('filename*', '').partition("'")
    encoded = value.partition("'")
    if separator and encoded[1] and charset.lower() in {'utf-8', 'iso-8859-1'}:
        try:
            if not re.search(r'%(?![0-9a-fA-F]{2})', encoded[2]):
                return sanitize_file_name(unquote(encoded[2], encoding=charset, errors='strict'))
        except UnicodeError:
            pass
    path = urlsplit(url).path.rsplit('/', 1)[-1]
    return sanitize_file_name(parameters.get('filename') or unquote(path))


def _validate_url(url):
    parsed = urlsplit(url)
    if parsed.scheme not in {'http', 'https'} or not parsed.hostname or parsed.username is not None:
        raise DirectFileError('请输入有效的 HTTP 或 HTTPS 文件地址。')
    return parsed


class _FileRedirect(HTTPRedirectHandler):
    max_redirections = 5

    def __init__(self, offset, end, validator, final_key):
        self.offset = offset
        self.end = end
        self.validator = validator
        self.final_key = final_key

    def http_request(self, request):
        # Only the previously verified destination receives its file validator.
        if self.offset is not None and _url_key(request.full_url) == self.final_key:
            end = self.end if self.end is not None else ''
            request.add_unredirected_header('Range', f'bytes={self.offset}-{end}')
            request.add_unredirected_header('If-Range', self.validator)
        return request

    https_request = http_request

    def redirect_request(self, request, file, code, msg, headers, newurl):
        target = _validate_url(newurl)
        if request.type == 'https' and target.scheme != 'https':
            raise DirectFileError('服务器将 HTTPS 地址重定向到不安全的地址。')
        return super().redirect_request(request, file, code, msg, headers, newurl)


class _LazyHTTPSHandler(HTTPSHandler):
    def __init__(self):
        AbstractHTTPHandler.__init__(self)

    def https_open(self, request):
        # Frozen Windows builds can lack TLS assets even when the URL is HTTP.
        return self.do_open(http.client.HTTPSConnection, request)


def _open(url, offset=None, validator=None, final_key=None, cookies_path=None, *, end=None):
    _validate_url(url)
    request = Request(url, headers={'Accept-Encoding': 'identity'})
    handlers = [_LazyHTTPSHandler(), _FileRedirect(offset, end, validator, final_key)]
    if cookies_path:
        jar = MozillaCookieJar(str(cookies_path), policy=DefaultCookiePolicy(
            strict_ns_domain=DefaultCookiePolicy.DomainStrictNonDomain,
        ))
        jar.load(ignore_discard=True, ignore_expires=True)
        for cookie in jar:
            if cookie.expires == 0:
                cookie.expires = None
                cookie.discard = True
        jar.clear_expired_cookies()
        handlers.append(HTTPCookieProcessor(jar))
    opener = build_opener(*handlers)
    return opener.open(request, timeout=_TIMEOUT)


def _identity_encoding(headers) -> bool:
    values = headers.get_all('Content-Encoding', [])
    return not values or len(values) == 1 and values[0].strip().lower() == 'identity'


def _length(headers) -> int | None:
    values = headers.get_all('Content-Length', [])
    encodings = headers.get_all('Transfer-Encoding', [])
    if encodings and (values or len(encodings) != 1 or encodings[0].lower() != 'chunked'):
        raise DirectFileError('服务器返回的文件传输格式无效。')
    if not values:
        return None
    if len(values) != 1 or not re.fullmatch(r'[0-9]{1,19}', values[0].strip()):
        raise DirectFileError('服务器返回的文件长度无效。')
    return int(values[0])


def _http_date(value):
    if not value:
        return None
    try:
        date = parsedate_to_datetime(value)
        return date if format_datetime(date, usegmt=True) == value else None
    except (ValueError, TypeError, OverflowError):
        return None


def _validator(headers) -> tuple[str, str] | None:
    tags = headers.get_all('ETag', [])
    if len(tags) == 1 and re.fullmatch(r'"[!#-~\x80-\xff]*"', tags[0].strip()):
        return 'etag', tags[0].strip()
    modified = headers.get('Last-Modified', '')
    date = _http_date(headers.get('Date'))
    modified_date = _http_date(modified)
    if date is not None and modified_date is not None and (date - modified_date).total_seconds() >= 60:
        return 'date', modified
    return None


def _content_type(headers) -> str | None:
    value = headers.get('Content-Type', '').partition(';')[0].strip().lower()
    return value or None


def _read_block(response, size, check_stop=lambda: None) -> bytes:
    content = bytearray()
    while len(content) < size:
        check_stop()
        chunk = response.read1(size - len(content))
        if not chunk:
            break
        content.extend(chunk)
    return bytes(content)


def _web_page(content_type, prefix=b'') -> bool:
    if content_type in {'text/html', 'application/xhtml+xml'}:
        return True
    text = prefix.decode('utf-8', errors='replace').lstrip('\ufeff \r\n\t').lower()
    return text.startswith(('<!doctype html', '<html', '<head', '<body'))


def _kind(content_type, filename, prefix) -> str:
    extension = Path(filename).suffix.lower()
    if extension == ".torrent" or content_type in {"application/x-bittorrent", "application/bittorrent"}:
        return "torrent"
    if (content_type in _MEDIA_TYPES or extension in {'.m3u8', '.mpd'} or
            prefix.lstrip().startswith(b'#EXTM3U')):
        return 'stream'
    if content_type and content_type.startswith(('audio/', 'video/')):
        return content_type.partition('/')[0]
    if content_type in {None, 'application/octet-stream'}:
        if extension in {'.mp4', '.mkv', '.webm', '.mov', '.avi', '.wmv', '.m4v', '.mpeg', '.mpg', '.ts'}:
            return 'video'
        if extension in {'.mp3', '.m4a', '.flac', '.wav', '.ogg', '.aac', '.opus', '.wma'}:
            return 'audio'
    return 'file'


def probe_direct_file(url: str, cookies_path: Path | None = None) -> DirectFileMetadata | None:
    # GET lets us distinguish a binary response from a mislabeled login page.
    try:
        with _open(url, cookies_path=cookies_path) as response:
            if response.status != 200 or not _identity_encoding(response.headers):
                return None
            mime = _content_type(response.headers)
            disposition = response.headers.get('Content-Disposition', '')
            if _web_page(mime) or not (mime or disposition):
                return None
            prefix = _read_block(response, 512)
            if _web_page(mime, prefix):
                return None
            filename = _filename(disposition, response.url)
            return DirectFileMetadata(filename, mime, _length(response.headers),
                                      _kind(mime, filename, prefix),
                                      _validator(response.headers) is not None, response.url)
    except (OSError, ValueError, http.client.HTTPException, DirectFileError):
        return None


class DirectFileProbeWorker(QObject):
    finished = Signal(object)
    failed = Signal(str)

    def __init__(self, url: str, cookies_path=None):
        super().__init__()
        self.url = url
        self.cookies_path = cookies_path

    @Slot()
    def run(self):
        try:
            self.finished.emit(probe_direct_file(self.url, self.cookies_path))
        except Exception:
            self.failed.emit('无法读取文件信息。')


def _present(path):
    return os.path.lexists(path)


def _task_paths(workspace):
    root = Path(workspace)
    parent = root.absolute()
    if parent.resolve() != parent or (parent.exists() and not parent.is_dir()):
        raise DirectFileError('文件下载的临时目录不安全。')
    paths = tuple(root / name for name in (_PARTIAL, _CHECKPOINT, _CHECKPOINT_TEMP, _RANGE_TEMP))
    if any(path.is_symlink() or (path.exists() and not path.is_file()) for path in paths):
        raise DirectFileError('文件下载的临时文件不安全。')
    return paths


def reset_direct_file_partial(temp_dir: Path) -> None:
    for path in _task_paths(temp_dir):
        path.unlink(missing_ok=True)


def _url_key(url):
    return sha256(url.encode()).hexdigest()


def _checkpoint_write(path, temporary, state):
    try:
        with temporary.open('x', encoding='utf-8') as output:
            json.dump(state, output)
            output.flush()
            os.fsync(output.fileno())
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def _checkpoint_read(path, partial, url):
    if not (_present(path) or _present(partial)):
        return None
    try:
        state = json.loads(path.read_text(encoding='utf-8'))
        downloaded = state['downloaded']
        total = state['total']
        complete = state['complete']
        if (state['source'] == _url_key(url) and type(downloaded) is int and downloaded >= 0 and
                (total is None or type(total) is int and downloaded <= total) and
                type(complete) is bool and (not complete or downloaded == total) and
                partial.stat().st_size == downloaded):
            return state
    except (OSError, ValueError, TypeError, KeyError):
        pass
    raise _RestartRequired('文件恢复记录已变化。请确认从头下载。')


class HttpTransferLimiter:
    """Rate limit shared by the HTTP streams using the same instance."""

    def __init__(self, rate_limit_kib=0):
        self._lock = Lock()
        self._rate = 0
        self.set_limit(rate_limit_kib)

    def set_limit(self, rate_limit_kib):
        rate = max(0, rate_limit_kib) * 1024
        with self._lock:
            if rate != self._rate:
                self._rate = rate
                self._available = 0.0
                self._updated = monotonic()

    def block_size(self):
        with self._lock:
            return min(_BLOCK, max(512, self._rate // 8)) if self._rate else _BLOCK

    def consume(self, size, check_stop):
        while size > 0:
            check_stop()
            with self._lock:
                if not self._rate:
                    return
                now = monotonic()
                self._available = min(self._rate / 20, self._available + (now - self._updated) * self._rate)
                self._updated = now
                consumed = min(self._available, size)
                self._available -= consumed
                size -= consumed
                delay = min(0.05, size / self._rate)
            if delay:
                sleep(delay)


class DirectFileWorker(QObject):
    output_reserved = Signal(str)
    progress = Signal(object)
    finished = Signal(str)
    failed = Signal(str)
    stopped = Signal(str)
    network_failed = Signal()
    stop_failed = Signal(str)
    skipped = Signal(str)
    restart_required = Signal(str)

    def __init__(self, request: DownloadRequest, *, conflict_policy='rename', report_special_results=False,
                 max_connections=1, rate_limit_kib=0, resume_transfer=True, transfer_limiter=None):
        super().__init__()
        if conflict_policy not in {'rename', 'overwrite', 'skip'}:
            raise ValueError('文件重名处理方式无效。')
        self.request = request
        self.conflict_policy = conflict_policy
        self.report_special_results = report_special_results
        self.max_connections = max(1, min(16, max_connections))
        self.resume_transfer = resume_transfer
        self.transfer_limiter = transfer_limiter if transfer_limiter is not None else HttpTransferLimiter(rate_limit_kib)
        self._stop_reason = ''
        self._commit_lock = Lock()

    def cancel(self):
        self.stop('cancel')

    def stop(self, reason: str):
        if reason not in {'pause', 'cancel', 'offline', 'exit'}:
            raise ValueError('未知停止原因')
        with self._commit_lock:
            if self._stop_reason != 'cancel':
                self._stop_reason = reason

    def _check_stop(self):
        if self._stop_reason:
            raise _Stopped

    @Slot()
    def run(self):
        try:
            path = self._download()
        except Exception as error:
            if self._stop_reason:
                self.stopped.emit(self._stop_reason)
            elif self.report_special_results and isinstance(error, _Skipped):
                self.skipped.emit(str(error))
            elif self.report_special_results and isinstance(error, _RestartRequired):
                self.restart_required.emit(str(error))
            elif isinstance(error, DirectFileError):
                self.failed.emit(str(error))
            elif isinstance(error, _CONNECTION_ERRORS):
                self.network_failed.emit()
            else:
                self.failed.emit('文件下载处理失败，请检查磁盘和保存位置后重试。')
        else:
            self.finished.emit(str(path))

    def _download(self):
        self._check_stop()
        if self.request.temp_dir is None:
            raise DirectFileError('文件下载缺少任务临时目录。')
        target = self.request.output_template
        if sanitize_file_name(target.name) != target.name:
            raise DirectFileError('请输入有效的文件名。')
        self._check_conflict(target)
        partial, checkpoint, temporary, range_temp = _task_paths(self.request.temp_dir)
        partial.parent.mkdir(parents=True, exist_ok=True)
        temporary.unlink(missing_ok=True)
        range_temp.unlink(missing_ok=True)
        if not self.resume_transfer:
            reset_direct_file_partial(self.request.temp_dir)
        state = _checkpoint_read(checkpoint, partial, self.request.url)
        try:
            if not (state and state.get('complete')):
                self._receive(partial, checkpoint, temporary, range_temp, state)
            path = self._publish(partial, target)
        finally:
            if not self.resume_transfer:
                reset_direct_file_partial(self.request.temp_dir)
        for artifact in (partial, checkpoint):
            try:
                artifact.unlink(missing_ok=True)
            except OSError:
                # The published file is already complete.
                pass
        self.output_reserved.emit(str(path))
        return path

    def _receive(self, partial, checkpoint, temporary, range_temp, state, *, allow_parallel=True):
        self._check_stop()
        url = self.request.url
        downloaded = state['downloaded'] if state else 0
        validator = state.get('validator') if state else None
        if downloaded and not (isinstance(validator, list) and len(validator) == 2 and
                               isinstance(validator[1], str) and isinstance(state.get('final'), str)):
            raise _RestartRequired('源文件缺少可靠标识，无法续传。请确认从头下载。')
        try:
            response = _open(url, downloaded or None, validator[1] if downloaded else None,
                             state['final'] if downloaded else None, self.request.cookies_path)
        except HTTPError as error:
            with error:
                if downloaded and error.code == 416:
                    expected = f'bytes */{downloaded}'
                    if (state['total'] == downloaded and error.headers.get('Content-Range') == expected and
                            self._same_source(state, error) and _identity_encoding(error.headers)):
                        state['complete'] = True
                        _checkpoint_write(checkpoint, temporary, state)
                        return
                    raise _RestartRequired('服务器拒绝续传范围。请确认从头下载。') from None
                raise DirectFileError(f'服务器拒绝文件下载（HTTP {error.code}）。') from None
        with response:
            self._check_stop()
            total = self._response_total(response, state if downloaded else None)
            mime = _content_type(response.headers)
            if _web_page(mime):
                raise DirectFileError('链接返回网页或登录页面。')
            prefix = _read_block(response, 512, self._check_stop)
            self._check_stop()
            if not downloaded:
                if _web_page(mime, prefix):
                    raise DirectFileError('链接返回网页或登录页面。')
                disposition = response.headers.get('Content-Disposition', '')
                if not (mime or disposition):
                    raise DirectFileError('服务器未提供文件类型。')
                kind = _kind(mime, _filename(disposition, response.url), prefix)
                allowed = {"file", "audio", "video"} if self.request.kind == "direct_media" else {"file"}
                if kind not in allowed:
                    raise DirectFileError('此地址返回媒体，请重新分析并使用媒体下载。')
                current_validator = _validator(response.headers)
                state = dict(source=_url_key(url), final=_url_key(response.url),
                             validator=list(current_validator) if current_validator else None,
                             total=total, downloaded=0, complete=False)
                if (allow_parallel and self.max_connections > 1 and total is not None and
                        total >= _PARALLEL_MIN_SIZE and current_validator and current_validator[0] == 'etag'):
                    response.close()
                    try:
                        self._receive_ranges(partial, checkpoint, temporary, range_temp, state, prefix)
                    except (DirectFileError, *_CONNECTION_ERRORS):
                        return self._receive(partial, checkpoint, temporary, range_temp, None, allow_parallel=False)
                    return
            else:
                state['total'] = total
            mode = 'ab' if downloaded else ('wb' if partial.exists() else 'xb')
            initial_size = downloaded
            started = monotonic()
            last_update = 0.0
            with partial.open(mode) as output:
                chunk = prefix
                while True:
                    self._check_stop()
                    if chunk:
                        self.transfer_limiter.consume(len(chunk), self._check_stop)
                        downloaded += len(chunk)
                        if total is not None and downloaded > total:
                            raise DirectFileError('服务器返回的数据超过声明长度。')
                        output.write(chunk)
                        output.flush()
                        os.fsync(output.fileno())
                        state['downloaded'] = downloaded
                        _checkpoint_write(checkpoint, temporary, state)
                        if monotonic() - last_update >= 0.15:
                            self._report(downloaded, total, initial_size, started)
                            last_update = monotonic()
                    self._check_stop()
                    chunk = _read_block(response, self.transfer_limiter.block_size(), self._check_stop)
                    if not chunk:
                        break
            if total is not None and downloaded != total:
                raise http.client.IncompleteRead(b'', total - downloaded)
            state.update(total=downloaded, downloaded=downloaded, complete=True)
            _checkpoint_write(checkpoint, temporary, state)
            self._report(downloaded, downloaded, initial_size, started)

    def _receive_ranges(self, partial, checkpoint, temporary, range_temp, state, prefix):
        total = state['total']
        count = min(self.max_connections, total // _BLOCK)
        offsets = [total * index // count for index in range(count + 1)]
        sizes = [0] * count
        abort = Event()
        write_lock = Lock()
        started = monotonic()
        contiguous = 0
        try:
            with range_temp.open('xb') as output:
                with ThreadPoolExecutor(max_workers=count) as executor:
                    futures = {
                        executor.submit(self._receive_range, index, offsets, state, prefix,
                                        output, sizes, write_lock, abort)
                        for index in range(count)
                    }
                    pending = futures
                    try:
                        while pending:
                            self._check_stop()
                            done, pending = wait(pending, timeout=0.15, return_when=FIRST_COMPLETED)
                            for future in done:
                                future.result()
                            with write_lock:
                                downloaded = sum(sizes)
                            if downloaded < total:
                                self._report(downloaded, total, 0, started)
                    except _Stopped:
                        pass
                    finally:
                        abort.set()
                complete = True
                for future in futures:
                    try:
                        future.result()
                    except _Stopped:
                        complete = False
                # Only a contiguous prefix can become a serial checkpoint.
                for index, size in enumerate(sizes):
                    contiguous += size
                    if size != offsets[index + 1] - offsets[index]:
                        break
                if not complete:
                    contiguous = min(contiguous, total - 1)
                output.truncate(contiguous)
                output.flush()
                os.fsync(output.fileno())
            if contiguous and (self.resume_transfer or contiguous == total):
                os.replace(range_temp, partial)
                state.update(downloaded=contiguous, complete=contiguous == total)
                _checkpoint_write(checkpoint, temporary, state)
            self._check_stop()
            self._report(total, total, 0, started)
        finally:
            range_temp.unlink(missing_ok=True)

    def _receive_range(self, index, offsets, state, prefix, output, sizes, write_lock, abort):
        def check_stop():
            if abort.is_set():
                raise _Stopped
            self._check_stop()

        offset, end = offsets[index:index + 2]
        check_stop()
        with _open(self.request.url, offset, state['validator'][1], state['final'],
                   self.request.cookies_path, end=end - 1) as response:
            length = _length(response.headers)
            expected = f"bytes {offset}-{end - 1}/{state['total']}"
            if (response.status != 206 or not self._same_source(state, response) or
                    response.headers.get_all('Content-Range', []) != [expected] or
                    length not in {None, end - offset} or not _identity_encoding(response.headers) or
                    _web_page(_content_type(response.headers))):
                raise DirectFileError('服务器返回的并行分段范围无效。')
            while offset < end:
                chunk = _read_block(response, min(end - offset, self.transfer_limiter.block_size()), check_stop)
                if not chunk or (index == 0 and offset == 0 and not chunk.startswith(prefix)):
                    raise DirectFileError('服务器返回的并行分段内容无效。')
                self.transfer_limiter.consume(len(chunk), check_stop)
                with write_lock:
                    output.seek(offset)
                    output.write(chunk)
                    sizes[index] += len(chunk)
                offset += len(chunk)
            if _read_block(response, 1, check_stop):
                raise DirectFileError('服务器返回的分段数据超过声明长度。')

    @staticmethod
    def _same_source(state, response):
        validator = _validator(response.headers)
        return (validator is not None and list(validator) == state['validator'] and
                _url_key(response.geturl()) == state['final'])

    def _response_total(self, response, state):
        length = _length(response.headers)
        if not _identity_encoding(response.headers):
            raise DirectFileError('服务器返回了不支持的内容编码。')
        if state is None:
            if response.status != 200 or response.headers.get('Content-Range'):
                raise DirectFileError('服务器未返回完整文件。')
            return length
        if response.status != 206 or not self._same_source(state, response):
            raise _RestartRequired('服务器未接受续传或文件标识已变化。请确认从头下载。')
        values = response.headers.get_all('Content-Range', [])
        match = re.fullmatch(r'bytes ([0-9]{1,19})-([0-9]{1,19})/([0-9]{1,19})', values[0]) if len(values) == 1 else None
        if match is not None:
            start, end, total = map(int, match.groups())
            if (start == state['downloaded'] <= end == total - 1 and
                    state['total'] in {None, total} and length in {None, end - start + 1}):
                return total
        raise _RestartRequired('服务器返回的续传范围或文件长度无效。请确认从头下载。')

    def _report(self, downloaded, total, initial_size, started):
        elapsed = monotonic() - started
        speed = (downloaded - initial_size) / elapsed if elapsed else 0
        percent = downloaded / total * 100 if total else (100.0 if total == 0 else None)
        eta = ''
        if speed and total is not None:
            minutes, seconds = divmod(int((total - downloaded) / speed), 60)
            eta = f'{minutes:02}:{seconds:02}'
        self.progress.emit(ProgressEvent(percent, f'{speed / 1024:.0f} KiB/s' if speed else '', eta))

    def _check_conflict(self, target):
        if self.conflict_policy == 'skip' and _present(target):
            raise _Skipped('保存目录已有同名文件，已跳过。')

    def _publish(self, partial, target):
        target.parent.mkdir(parents=True, exist_ok=True)
        stage = target.parent / f'.ytdl-file-{uuid4().hex}.tmp'
        staged = False
        try:
            self._check_stop()
            self._check_conflict(target)
            try:
                os.link(partial, stage)
                staged = True
            except FileExistsError:
                raise
            except OSError:
                with partial.open('rb') as source, stage.open('xb') as output:
                    staged = True
                    while chunk := source.read(_BLOCK):
                        self._check_stop()
                        output.write(chunk)
                    output.flush()
                    os.fsync(output.fileno())
            with self._commit_lock:
                self._check_stop()
                if self.conflict_policy == 'overwrite':
                    os.replace(stage, target)
                    return target
                candidate = target
                index = 0
                while True:
                    try:
                        if os.name == 'nt':
                            stage.rename(candidate)
                        else:
                            os.link(stage, candidate)
                        return candidate
                    except FileExistsError:
                        self._check_conflict(candidate)
                    index += 1
                    candidate = target.with_name(f'{target.stem} ({index}){target.suffix}')
        finally:
            if staged:
                stage.unlink(missing_ok=True)
