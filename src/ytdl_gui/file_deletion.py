"""Delete only exact recorded output files, without following filesystem links."""

import os
import stat
from pathlib import Path


class FileDeletionError(ValueError):
    pass


def _file_identity(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns


def _delete_file_by_handle(path: Path, expected) -> None:
    if os.name != "nt":
        raise FileDeletionError("当前系统不支持安全文件删除，已保留文件和记录。")

    import ctypes
    from ctypes import wintypes
    import msvcrt

    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                  wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    kernel.CreateFileW.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.CloseHandle.restype = wintypes.BOOL
    kernel.SetFileInformationByHandle.argtypes = [wintypes.HANDLE, ctypes.c_int, wintypes.LPVOID, wintypes.DWORD]
    kernel.SetFileInformationByHandle.restype = wintypes.BOOL

    # DELETE | FILE_READ_ATTRIBUTES, no sharing: bind one file and exclude
    # concurrent writers/renames. OPEN_REPARSE_POINT never opens a link target.
    handle = kernel.CreateFileW(str(path), 0x00010080, 0, None, 3, 0x00200000, None)
    if handle == wintypes.HANDLE(-1).value:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        descriptor = msvcrt.open_osfhandle(handle, os.O_RDONLY)
    except BaseException:
        kernel.CloseHandle(handle)
        raise
    # open_osfhandle transfers ownership; os.close is the sole handle closer.
    try:
        actual = os.fstat(descriptor)
        if (not stat.S_ISREG(actual.st_mode)
                or getattr(actual, "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT
                or _file_identity(actual) != _file_identity(expected)):
            raise FileDeletionError("下载文件已发生变化，已保留文件和记录。")
        delete_file = ctypes.c_ubyte(1)  # FILE_DISPOSITION_INFO.DeleteFile
        if not kernel.SetFileInformationByHandle(handle, 4, ctypes.byref(delete_file), ctypes.sizeof(delete_file)):
            raise ctypes.WinError(ctypes.get_last_error())
    finally:
        os.close(descriptor)


def _checked_file(path_text: str):
    if not isinstance(path_text, str) or not path_text or "%(" in path_text or "\x00" in path_text:
        raise FileDeletionError("未记录明确的下载文件路径。")
    path = Path(path_text)
    if not path.is_absolute() or ".." in path.parts:
        raise FileDeletionError("下载文件路径不安全。")
    # Reject device paths and alternate data streams on Windows.
    if os.name == "nt" and (path_text.startswith(("\\\\?\\", "\\\\.\\")) or ":" in str(path)[len(path.drive):]):
        raise FileDeletionError("下载文件路径不安全。")
    file_stat = None
    for component in [*reversed(path.parents), path]:
        try:
            info = component.lstat()
        except FileNotFoundError:
            continue
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise FileDeletionError("下载文件或父目录是链接，已保留记录。")
        if component == path:
            if not stat.S_ISREG(info.st_mode):
                raise FileDeletionError("记录路径不是普通文件，已保留记录。")
            file_stat = info
        elif not stat.S_ISDIR(info.st_mode):
            raise FileDeletionError("下载文件父路径不是目录。")
    return path, file_stat


def delete_recorded_files(outputs: list[tuple[str, int]], protected_paths: list[str]) -> dict[int, str]:
    """Return per-output failures. Missing files count as success; no globbing."""
    failures = {}
    candidates = {}
    for index, (path_text, expected_size) in enumerate(outputs):
        if path_text == "":
            # No recorded main file: remove the record without searching for
            # partial downloads, subtitles, or other files with similar names.
            continue
        try:
            path, info = _checked_file(path_text)
            if info is not None and expected_size and info.st_size != expected_size:
                raise FileDeletionError("文件大小与下载记录不符，已保留文件和记录。")
            for protected in protected_paths:
                if not protected:
                    continue
                other = Path(protected)
                if path.resolve() == other.resolve() or (info is not None and other.exists() and path.samefile(other)):
                    raise FileDeletionError("文件仍被其他任务或历史记录引用，已保留文件和记录。")
            candidates[index] = (path, info, path.resolve())
        except (OSError, ValueError, RuntimeError, TypeError) as error:
            failures[index] = str(error) if isinstance(error, FileDeletionError) else "无法安全检查下载文件，已保留记录。"

    # If any selected record cannot validate a shared path, retain all references.
    blocked = set()
    for index in failures:
        try:
            blocked.add(Path(outputs[index][0]).resolve())
        except (OSError, ValueError, RuntimeError, TypeError):
            # An unresolvable failed reference cannot be ruled out as shared.
            return {i: "存在无法校验的文件引用，已保留文件和记录。" for i in range(len(outputs))}
    deleted = set()
    for index, (path, before, key) in candidates.items():
        if key in blocked:
            failures[index] = "同一文件的其他记录校验失败，已保留记录。"
            continue
        if key in deleted:
            continue
        try:
            _, current = _checked_file(str(path))
            if current is not None:
                if before is None or _file_identity(current) != _file_identity(before):
                    raise FileDeletionError("下载文件已发生变化，已保留记录。")
                _delete_file_by_handle(path, before)
            deleted.add(key)
        except (OSError, ValueError) as error:
            failures[index] = str(error) if isinstance(error, FileDeletionError) else "文件删除失败，可能仍被占用或没有权限；已保留记录。"
            blocked.add(key)
    return failures
