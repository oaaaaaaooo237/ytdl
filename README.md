# YTDL GUI

Win11 x64 桌面 GUI 视频下载工具，第一版以公开 YouTube 视频为核心目标，其他 `yt-dlp` 支持的网站作为 best-effort。

## 本地运行

首次配置或重新创建 `.venv` 后，先把本项目以 editable 方式安装到虚拟环境：

```powershell
.\.venv\Scripts\python.exe -m pip install -e . --no-build-isolation
```

然后启动 GUI：

```powershell
.\.venv\Scripts\python.exe -m ytdl_gui.main
```

## 范围边界

- 不绕过 DRM 或网站权限。
- 需要登录的视频通过用户手动选择 Netscape 格式 `cookies.txt` 支持。
- 第一版不自动提取浏览器 cookies。

## cookies.txt

`cookies.txt` 可能包含敏感登录数据。请使用浏览器扩展或可信工具导出 Netscape 格式 cookies，最好只导出目标网站 cookies，然后在设置页选择该文件。本程序第一版只保存文件路径，不保存 cookies 内容。

## ffmpeg

第一版不内置 ffmpeg。没有 ffmpeg 时，仍应支持可用的基础下载能力；合并、音频提取、字幕嵌入、字幕烧录和转码需要用户本机配置 `ffmpeg.exe`。

## Packaging

Fetch the bundled `yt-dlp.exe` from the official release source:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\fetch_ytdlp.ps1
```

Build the Win11 x64 distribution folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package_win.ps1
```

Run packaged smoke checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke_packaged.ps1
```

The package includes `licenses\THIRD_PARTY_NOTICES.txt`. ffmpeg is user-provided and is not bundled.
