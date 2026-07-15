# Android 视觉审计修复前截图

时间：2026-07-05

用途：记录视觉密度修复前的五页截图，作为发现问题的证据。

截图：

- `download.png`
- `format.png`
- `queue.png`
- `history.png`
- `settings.png`

主要发现：

- 全局字号和字重偏大，页面信息密度低于 `docs/android-gui-reference-v3.png`。
- 下载页常态/导出类消息卡容易占据首屏空间。
- 历史页和设置页卡片、列表项、底栏都显得偏松。

后续修复证据见：`docs/qa/android-visual-audit-20260705-compactfix/`。

边界：本目录截图来自 ADB 静态截图，只用于视觉审计，不替代 Computer Use 前台全功能验收。
