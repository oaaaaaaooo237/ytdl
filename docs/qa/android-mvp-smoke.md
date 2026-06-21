# Android MVP Smoke 证据账本

日期：2026-06-21

## 当前结论

Android Play MVP 尚未通过最终验收。

已具备的证据属于命令层、能力层、绑定层和 API37 辅助联动测试；最终要求的 Computer Use 前台可见模拟器全功能流程仍未通过，因此不能写成“全量可视验收通过”。

## 本轮已确认

- 当前分支：`feature/android-play-mvp-1`
- 当前远程同步提交：
  - `2e067b6 android: document MVP status and licenses`
  - `3687292 android: show privacy and authorization boundaries`
  - `b4a6a85 android: bind notification permission status`
  - `4d68196 android: clarify export progress state`
  - `f5f12db android: show real queue thumbnails`
- API37 模拟器进程存在：`Android Emulator - ytdl_api37_play_x86_64:5554`
- ADB 设备在线：`emulator-5554 device product:sdk_gphone16k_x86_64`

## 最近验证命令

2026-06-21 已顺序运行并通过：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest --tests com.garyapp.ytdl.SmokeUnitTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

2026-06-21 20:15 复跑结果：

- `.\gradlew.bat :app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"`：`BUILD SUCCESSFUL`，API37 上 4/4 tests passed，0 failed，0 skipped，报告生成时间 2026-06-21 20:15:05。

其中 `YtdlAppUiTest` 属于 UIAutomator/instrumentation 辅助联动证据，不是最终 Computer Use 前台可视验收。本轮该测试覆盖：

- 五个底部页面导航和关键页面节点。
- `https://www.youtube.com/watch?v=tkxzMEfp49Q` 的真实分析、1080p 格式选择、开始下载、队列完成、历史记录写入和 app-private 合并媒体 URI 检查。
- `https://www.youtube.com/shorts/QBwpO9f0oAw` 的真实分析预览和格式应用抽样。

## 已完成的能力/绑定层重点

- `yt-dlp` 真实分析和明确 format id 下载能力。
- Android 原生 `MediaExtractor + MediaMuxer` 合并分离视频流和音频流。
- 独立字幕文件下载能力；MVP1 不做字幕嵌入或烧录。
- 前台服务下载状态、真实队列状态、通知取消和应用内进度。
- 格式页只基于当前分析结果展示/启用可用格式，避免旧 360p 摘要和 fallback。
- 历史记录、打开/分享/导出/删除入口绑定到 app-private 输出定位。
- cookies 只保存引用；任务临时文件终态清理；设置、历史、日志和错误信息不写入 cookies 内容。
- 设置页展示真实通知权限状态、媒体处理边界、隐私与授权说明、Data safety/隐私政策/第三方许可证草案。

## 当前阻断

Computer Use 前台可见模拟器流程仍未通过，但阻断状态已从旧的启动失败变为输入/激活失败。

2026-06-21 复核结果：

- `nodeRepl.write("ok")` 可执行。
- Computer Use 可初始化并执行 `sky.list_apps()`。
- Computer Use 可枚举 `Android Emulator - ytdl_api37_play_x86_64:5554` 并被动截图。
- ADB 已确认 `com.garyapp.ytdl/.MainActivity` 是 `topResumedActivity` 和 `mCurrentFocus`。
- ADB `screencap` 显示 App 下载页真实可见。
- Computer Use 对模拟器窗口的被动截图仍显示旧壁纸/锁屏帧，和 ADB 真实画面不一致。
- Computer Use 的 `activate_window`、`click`、`perform_secondary_action("Raise")` 在模拟器窗口和普通窗口（便笺）上均返回 `failed to activate captured window`。

因此当前不能用 Computer Use 完成：

- 前台可见输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`
- 分析并确认标题、时长、缩略图和真实格式行
- 选择高分辨率视频+音频合并格式
- 开始真实下载并观察队列进度
- 检查历史、打开/分享/导出/删除
- 检查设置页通知、cookies、隐私与授权说明
- 触发一个真实失败恢复路径
- 用 Shorts 链接做兼容抽样

同日辅助检查：已重新安装当前 `app-debug.apk` 并启动到 API37 模拟器前台，用 ADB 截图巡检下载页；该检查确认 GUI 不再是壳层占位页，但它不是 Computer Use 前台可视全流程验收，不能替代 T12。

## 下一步

1. 等 Computer Use 输入/激活能力恢复后，继续使用当前 `ytdl_api37_play_x86_64` 和当前 APK。
2. 用 Computer Use 在前台可见模拟器窗口执行完整 T12，不使用模拟器软键盘。
3. 将截图、输出文件位置、测试时间、通过/失败结果追加到本文件。
4. 只有 T12 通过后，才能把 Android MVP 标记为可验收。

## 2026-06-21 继续修复：UI 审计问题收敛

记录时间：2026-06-21 20:55:20

本轮按 fresh subagent 只读审计结果修复了 1 个 P1 和 3 个 P2：

- P1：下载页“我确认有权保存该内容”从固定勾选改为真实用户确认门闩；未完成分析或未勾选时不能开始下载。
- P2：下载模式卡片从静态展示改为可点击，点击后按当前分析结果切换 视频+音频、仅音频、仅视频 选择。
- P2：历史页搜索框和 全部/视频/音频 筛选从静态控件改为本地历史列表过滤。
- P2：空队列不再显示队列滚动指示器；只有真实任务存在时才显示。
- 外观配色补充修复：ResolutionRow、SettingLineCard、历史筛选行、下载预览状态文字不再硬绑定基准图默认配色，改为读取 LocalYtdlAppPalette，避免 Codex 风格 只局部生效。

本轮新鲜验证：

`powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest --warning-mode all
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
`

结果：

- DownloadUiBridgeTest：通过。
- :app:testDebugUnitTest：BUILD SUCCESSFUL。
- :app:assembleDebug：BUILD SUCCESSFUL。
- YtdlAppUiTest：API37 上 4/4 tests passed，0 failed，0 skipped，覆盖五页导航、必测 URL https://www.youtube.com/watch?v=tkxzMEfp49Q 的真实分析、1080p 格式选择、授权确认、真实下载、队列完成、历史记录写入，以及 Shorts 分析抽样。
- Gradle 弃用警告来源已用 --warning-mode all 复核：来自 Chaquopy 17.0.0 插件声明内部依赖时使用 Gradle 10 将移除的 multi-string notation，不是本轮业务代码或项目 Gradle 脚本新增问题。

辅助 UI 证据：

- docs/qa/android-current-visual-20260621-auditfix/ 保存本轮安装当前 APK 后的五页 adb 截图和 UIAutomator XML。
- 该目录证明：下载页有真实授权 checkbox 和三种模式卡 testTag；历史页有真实搜索/筛选 testTag；空队列不再出现 ytdl-queue-scroll-indicator；设置页滚动后可见 基准图配色 与 Codex 风格。

Computer Use 最新复核：

- 本轮再次尝试 Computer Use 时，mcp__node_repl.js 连最小
odeRepl.write(...) 都失败，错误为 windows sandbox failed: helper_unknown_error: apply deny-read ACLs。
- 因此当前阻断比上一条记录中的“窗口激活失败”更早：Computer Use 入口本身没有稳定启动。
- 本轮所有 adb 截图、UIAutomator XML、connected test 都只能算辅助证据，不能替代 M9/T12 要求的 Computer Use 前台可见全功能验收。
