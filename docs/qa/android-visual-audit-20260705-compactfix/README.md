# Android 视觉密度修复截图证据

时间：2026-07-05

范围：对照 `docs/android-gui-reference-v3.png`，修复 Android 五页 GUI 的字号、底栏、卡片和列表密度偏大的问题。

本轮改动：

- 增加项目内紧凑 Typography，避免继续使用 Material 3 默认较大字号。
- 收紧页面内边距、底部导航、卡片、分段按钮、列表项和历史/队列缩略图尺寸。
- 下载页常态消息 `等待输入公开视频页面地址。` 和 `分析完成，可以开始下载。` 不再占用首屏提示卡；错误、下载入队、导出、cookies、通知等用户需要看到的反馈仍显示。

截图：

- `download.png`
- `format.png`
- `queue.png`
- `history.png`
- `settings.png`

验证：

- `cd android; .\gradlew.bat :app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `cd android; .\gradlew.bat :app:assembleDebug`：`BUILD SUCCESSFUL`。
- `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"`：API37 上 5/5 tests passed，`BUILD SUCCESSFUL`。
- 已安装当前 debug APK 到 `ytdl_api37_play_x86_64` 并通过 ADB 截图复核五页静态视觉。

边界：

- 本目录截图是静态视觉证据，不替代 Computer Use 前台全功能验收。
- `format.png` 是未分析前的空态格式页；真实分析后的格式页前台可视证据仍以 `docs/qa/android-mvp-smoke.md` 中 2026-07-05 Computer Use 记录为准。
- 历史删除、真实 cookies 文件选择、外部导出写出、通知/取消前台路径仍按 M9/T12 剩余项推进。
