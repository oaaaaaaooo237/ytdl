<div align="center">
  <img src="assets/app-icon.png" width="96" alt="视频地址提取器图标" />

  <h1>视频地址提取器</h1>

  <p>
    一个面向 Windows 11 x64 的图形化视频下载工具。输入播放地址，分析可用格式，按需下载音频、视频或音视频合并文件。
  </p>

  <p>
    <a href="https://github.com/oaaaaaaooo237/ytdl/releases/tag/win-v1.0.0">
      <img alt="Release" src="https://img.shields.io/badge/Windows-v1.0.0-149A9A" />
    </a>
    <img alt="Windows" src="https://img.shields.io/badge/Windows-11%20x64-2563EB" />
    <img alt="yt-dlp" src="https://img.shields.io/badge/engine-yt--dlp-3B82F6" />
    <img alt="ffmpeg" src="https://img.shields.io/badge/media-ffmpeg-16A34A" />
    <img alt="License" src="https://img.shields.io/badge/license-MIT-111827" />
  </p>

  <p>
    <a href="https://github.com/oaaaaaaooo237/ytdl/releases">下载 Release</a>
    ·
    <a href="docs/superpowers/specs/2026-05-30-ytdl-gui-design.md">查看设计</a>
  </p>
</div>

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
