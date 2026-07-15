# Android 取消路径证据 - 2026-07-05

本目录保存 M9/T12 取消路径补强后的辅助截图证据。

## 证据

- `08-queue-canceled.png`：API37 connected 真实下载流程中，启动 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 下载后在队列页点击取消，最终 UI 显示 `最近任务已取消`、`下载已取消。` 和真实任务卡 `已取消`。

## 边界

- 该截图来自 API37 connected/UIAutomator 流程，是真实 APK、真实分析、真实下载管线和真实取消状态的辅助证据。
- 本轮 Computer Use 能枚举模拟器窗口，但无法激活该窗口，点击返回 `failed to activate captured window`；因此本目录不能替代最终 Computer Use 前台可见验收。
