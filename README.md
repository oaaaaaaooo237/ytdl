# YTDL GUI

一个面向 Windows 11 x64 的简洁视频下载 GUI。程序基于 `yt-dlp`，第一阶段以 YouTube 公开视频为核心目标，其他 `yt-dlp` 支持的网站按 best-effort 处理。

当前里程碑版本：`v0.2.1-icon`

## 主要功能

- 输入一个或多个视频播放地址并分析可用格式。
- 选择音频+视频、仅音频、仅视频下载模式。
- 按视频实际支持情况选择分辨率、帧率、编码、容器和码率偏好。
- 高分辨率视频+音频会通过项目内置 `ffmpeg` 合并。
- 支持字幕下载、嵌入和烧录。
- 支持下载队列、历史记录、保存位置选择。
- 支持用户手动选择 `cookies.txt` 文件路径。
- 启动时不阻塞网络分析、下载或 yt-dlp 更新检查。
- 打包后可在 Win11 x64 环境直接运行。

## 安全边界

- 不绕过 DRM、网站权限或用户未授权的访问限制。
- 程序只保存用户选择的 `cookies.txt` 文件路径，不保存 cookies 文件内容。
- 日志、历史、错误提示和界面不应泄露 cookies 内容。
- 需要登录权限的视频，应由用户自行导出并选择 Netscape 格式 `cookies.txt`。

## 本地运行

首次配置或重建 `.venv` 后，先安装项目：

```powershell
.\.venv\Scripts\python.exe -m pip install -e . --no-build-isolation
```

启动 GUI：

```powershell
.\.venv\Scripts\python.exe -m ytdl_gui.main
```

## ffmpeg

项目优先使用本地虚拟环境中的 ffmpeg：

```powershell
.\.venv\tools\ffmpeg\bin\ffmpeg.exe
```

打包产物会携带该 ffmpeg。用户也可以在设置页手动选择其他有效的 `ffmpeg.exe`。

## 打包

获取项目内置 `yt-dlp.exe`：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\fetch_ytdlp.ps1
```

构建 Win11 x64 发行目录：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package_win.ps1
```

打包结果：

```text
dist\YTDL-GUI\YTDL-GUI.exe
```

## 测试

运行自动化测试：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

检查打包产物：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke_packaged.ps1
```

使用真实视频地址做 smoke check：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\real_url_smoke.ps1 "https://www.youtube.com/watch?v=sp5EP_xbnc4"
```

真实测试下载文件会写入 `.qa-real-smoke`，该目录不应提交到 Git。

## 许可证

本项目使用 MIT License，详见 `LICENSE`。

打包目录包含第三方组件说明：`licenses\THIRD_PARTY_NOTICES.txt`。
