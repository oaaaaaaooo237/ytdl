# 视频地址提取器

[![Release](https://img.shields.io/badge/release-v0.2.2--thumbnail-149A9A)](https://github.com/oaaaaaaooo237/ytdl/releases/tag/v0.2.2-thumbnail)
![Windows](https://img.shields.io/badge/Windows-11%20x64-2563EB)
![yt-dlp](https://img.shields.io/badge/engine-yt--dlp-3B82F6)
![ffmpeg](https://img.shields.io/badge/media-ffmpeg-16A34A)
![License](https://img.shields.io/badge/license-MIT-111827)

一个 Windows 11 x64 桌面工具，用来输入视频播放地址，分析可用格式，并按用户选择下载音频、视频或音视频合并文件。

## 特色

| 重点 | 说明 |
| --- | --- |
| 格式分析 | 显示标题、时长、缩略图和当前视频真实可用格式。 |
| 灵活下载 | 支持“音频+视频”“仅音频”“仅视频”三种模式。 |
| 高画质合并 | 高分辨率音视频分离格式可用内置 `ffmpeg` 合并。 |
| 字幕处理 | 支持字幕下载、嵌入和烧录。 |
| 队列历史 | 支持下载队列、历史记录和保存位置选择。 |
| 登录辅助 | 可手动选择 `cookies.txt` 文件路径，不保存 cookies 内容。 |

## 运行

已编译版本：下载 Release 压缩包后，运行 `YTDL-GUI.exe`。

源码版本：

```powershell
.\.venv\Scripts\python.exe -m ytdl_gui.main
```

## 边界

- 不绕过 DRM、网站权限或用户未授权的访问限制。
- 只保存用户选择的 `cookies.txt` 文件路径，不保存 cookies 内容。
- 日志、历史、错误提示和界面不应泄露 cookies 内容。
- 非 YouTube 网站按 `yt-dlp` 支持情况 best-effort 处理，不保证全部可用。

## 许可证

MIT License。
