# Android MVP Smoke 证据账本

日期：2026-06-21

## 当前结论

Android Play MVP 尚未通过最终验收。

截至 2026-07-05，Computer Use 已恢复，并已在 API37 前台可见模拟器窗口完成普通 YouTube 链接、Shorts 链接和一次冷启动串联流程的真实运行验证；截图级视觉密度审计也已完成一轮修复。队列页取消和系统通知栏取消均已补强到 API37 connected 真实链路，但本轮 Computer Use 对模拟器窗口再次出现激活失败，历史删除、真实 cookies 文件选择和外部导出写出仍未完成，所以不能写成“全量可视验收通过”；后续真机验收阶段也尚未开始。

## 本轮已确认

- 当前分支：`feature/android-play-mvp-1`
- 当前远程同步提交：
  - `2e067b6 android: document MVP status and licenses`
  - `3687292 android: show privacy and authorization boundaries`
  - `b4a6a85 android: bind notification permission status`
  - `4d68196 android: clarify export progress state`
  - `f5f12db android: show real queue thumbnails`
  - `ca042d8 android: fix gui audit gaps`
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

## 2026-06-21 历史阻断记录

以下是 2026-06-21 的历史阻断记录，已被 2026-07-05 的 Computer Use 前台复测取代；保留本段仅用于解释早期为什么没有把 T12 计为通过。

2026-06-21 复核结果：

- `nodeRepl.write("ok")` 可执行。
- Computer Use 可初始化并执行 `sky.list_apps()`。
- Computer Use 可枚举 `Android Emulator - ytdl_api37_play_x86_64:5554` 并被动截图。
- ADB 已确认 `com.garyapp.ytdl/.MainActivity` 是 `topResumedActivity` 和 `mCurrentFocus`。
- ADB `screencap` 显示 App 下载页真实可见。
- Computer Use 对模拟器窗口的被动截图仍显示旧壁纸/锁屏帧，和 ADB 真实画面不一致。
- Computer Use 的 `activate_window`、`click`、`perform_secondary_action("Raise")` 在模拟器窗口和普通窗口（便笺）上均返回 `failed to activate captured window`。

因此当时不能用 Computer Use 完成：

- 前台可见输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`
- 分析并确认标题、时长、缩略图和真实格式行
- 选择高分辨率视频+音频合并格式
- 开始真实下载并观察队列进度
- 检查历史、打开/分享/导出/删除
- 检查设置页通知、cookies、隐私与授权说明
- 触发一个真实失败恢复路径
- 用 Shorts 链接做兼容抽样

同日辅助检查：已重新安装当前 `app-debug.apk` 并启动到 API37 模拟器前台，用 ADB 截图巡检下载页；该检查确认 GUI 不再是壳层占位页，但它不是 Computer Use 前台可视全流程验收，不能替代 T12。

## 当前下一步

1. 在不触发外部发送/破坏性操作的前提下继续验证系统通知栏取消、导出取消/写出和失败恢复等前台路径；队列页取消已有 connected 真实链路辅助证据，但仍需 Computer Use 能激活窗口后补前台点击证据。
2. 历史删除需要用户明确确认后才能执行；真实 cookies 选择需要用户提供测试用 `cookies.txt`。
3. 视觉密度截图审计已完成一轮；后续只在相关 GUI 代码继续变化后重采截图。
4. 等后续推进到真机阶段且小米 14 已连接时，再做小米 14 或同级 `arm64-v8a` 真机验收；当前不把真机验收作为 M9 模拟器前台验收的阻断。

## 2026-06-21 继续修复：UI 审计问题收敛

记录时间：2026-06-21 20:55:20

本轮按 fresh subagent 只读审计结果修复了 1 个 P1 和 3 个 P2：

- P1：下载页“我确认有权保存该内容”从固定勾选改为真实用户确认门闩；未完成分析或未勾选时不能开始下载。
- P2：下载模式卡片从静态展示改为可点击，点击后按当前分析结果切换 视频+音频、仅音频、仅视频 选择。
- P2：历史页搜索框和 全部/视频/音频 筛选从静态控件改为本地历史列表过滤。
- P2：空队列不再显示队列滚动指示器；只有真实任务存在时才显示。
- 外观配色补充修复：ResolutionRow、SettingLineCard、历史筛选行、下载预览状态文字不再硬绑定基准图默认配色，改为读取 LocalYtdlAppPalette，避免 Codex 风格 只局部生效。

本轮新鲜验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest --warning-mode all
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

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

- 本轮再次尝试 Computer Use 时，mcp__node_repl.js 连最小 `nodeRepl.write(...)` 都失败，错误为 windows sandbox failed: helper_unknown_error: apply deny-read ACLs。
- 因此当前阻断比上一条记录中的“窗口激活失败”更早：Computer Use 入口本身没有稳定启动。
- 本轮所有 adb 截图、UIAutomator XML、connected test 都只能算辅助证据，不能替代 M9/T12 要求的 Computer Use 前台可见全功能验收。

## 2026-06-21 外观颜色设置测试补强

本轮补强了设置页 `外观与颜色` 的真实 UIAutomator 覆盖：

- 在设置页滚动到 `外观与颜色` 区域。
- 验证 `基准图配色` 和 `Codex 风格` 两个颜色预设可见。
- 点击 `基准图配色` 后确认摘要为 `基准图配色 · 跟随系统`。
- 点击 `Codex 风格` 后确认摘要即时更新为 `Codex 风格 · 跟随系统`。

验证命令：

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：API37 上 `YtdlAppUiTest` 5/5 tests passed，0 failed，0 skipped；仍属于 UIAutomator/instrumentation 辅助联动证据，不替代 Computer Use 前台可见全功能验收。

## 2026-06-21 21:21 Computer Use 提权后复测

用户确认已将权限提到完全访问后，本轮重新按 Computer Use 技能流程测试：

- `mcp__node_repl.js_reset`：成功返回 `js kernel reset`。
- Computer Use bootstrap + `sky.list_apps()`：失败，错误为 `windows sandbox failed: helper_unknown_error: apply deny-read ACLs`，`node_repl kernel exited unexpectedly`。
- 重置后只执行最小 `nodeRepl.write(...)`：仍失败，错误同为 `windows sandbox failed: helper_unknown_error: apply deny-read ACLs`，说明当前阻断发生在 node_repl/Windows helper 启动层，而不是目标模拟器窗口选择、应用状态或脚本流程。

结论：本轮提权后 Computer Use 仍不可用；在该问题恢复前，不能宣称 Android MVP 通过前台可见全功能验收。

## 2026-06-21 21:36 阶段验证复跑

本轮继续推进前，按 Android/Gradle 不并行规则顺序复跑验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：

- 环境脚本：JDK 17、Android SDK、Gradle 9.4.1、ADB、emulator、API37 设备均可用。
- `:app:testDebugUnitTest`：BUILD SUCCESSFUL。
- `:app:assembleDebug`：BUILD SUCCESSFUL。
- `YtdlAppUiTest`：API37 `ytdl_api37_play_x86_64(AVD) - 17` 上 5/5 tests passed，0 failed，0 skipped；覆盖必测 URL 真实分析/格式选择/下载/队列历史路径、Shorts 分析抽样、设置页外观颜色预设可见和摘要更新；该项不是像素级配色验收。

边界：以上仍是单元测试、构建和 UIAutomator/instrumentation 证据；Computer Use 前台可见全功能验收仍因 `apply deny-read ACLs` 未完成。

## 2026-06-21 21:48 审计修复后复跑

根据 fresh 审计子会话反馈，本轮收敛了两个测试/文档边界问题：

- connected 测试名称和 smoke 文档改为描述“颜色预设可见和摘要更新”，不把该测试包装成像素级配色验收。
- `YtdlAppUiTest` 在切换到 `Codex 风格` 后通过 finally 点回 `基准图配色`，避免污染后续测试和截图基线。

复跑命令：

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：API37 `ytdl_api37_play_x86_64(AVD) - 17` 上 5/5 tests passed，0 failed，0 skipped；`BUILD SUCCESSFUL`。

## 2026-06-21 22:14 外观 palette 语义验证补强

本轮按 TDD 增加了不可见 Compose 语义钩子，避免只凭“颜色方案”摘要判断配色是否应用：

- RED：新增 `DownloadGuiBindingTest.appRootSemanticsExposeReferencePaletteAccent` 和 `appRootSemanticsExposeCodexPaletteAccent` 后，生产代码缺少 `YtdlColorPresetIdKey` / `YtdlSettingsAccentArgbKey`，`compileDebugUnitTestKotlin` 失败。
- GREEN：根屏幕语义暴露当前颜色 preset id 和 settings accent ARGB；`reference_v3` 断言 `#FF2E86DE`，`codex` 断言 `#FF2F6D80`。
- 该语义只用于测试，不增加用户可见调试文字。

验证命令：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：

- `:app:testDebugUnitTest`：BUILD SUCCESSFUL。
- `:app:assembleDebug`：BUILD SUCCESSFUL。
- `YtdlAppUiTest`：API37 `ytdl_api37_play_x86_64(AVD) - 17` 上 5/5 tests passed，0 failed，0 skipped；`BUILD SUCCESSFUL`。

边界：这补强了“Codex 风格/基准图配色是否真实进入 Compose palette”的测试证据；仍不是最终 Computer Use 前台可见全功能验收。

## 2026-07-05 五页导航 accent 配色约束补强

本轮继续补强“基准图配色 / Codex 风格”不只在设置摘要中存在，而是进入五个底部页面导航 accent token：

- RED：新增 `DownloadGuiBindingTest.navigationAccentsMatchReferenceAndCodexPalettes` 后，生产代码缺少 `ytdlNavigationAccentHexesForUiTest`，`compileDebugUnitTestKotlin` 失败，错误为 unresolved reference。
- GREEN：新增测试辅助函数从现有 `ytdlNavigationDestinations(ytdlAppPaletteForPreset(...))` 读取五个页面导航 accent，并断言：
  - `reference_v3`：下载 `#FFFF5B55`、格式 `#FF138F88`、队列 `#FFFF7A1A`、历史 `#FF7357C8`、设置 `#FF2E86DE`。
  - `codex`：下载 `#FF315C6B`、格式 `#FF7C705E`、队列 `#FFA26E35`、历史 `#FF5D5D79`、设置 `#FF2F6D80`。
- 该 helper 为 `internal` 测试约束，不增加用户可见调试文案，也不改变下载、媒体、Room 或 yt-dlp 行为。

验证命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.navigationAccentsMatchReferenceAndCodexPalettes
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：

- 环境脚本：JDK 17、Android SDK、Gradle 9.4.1、ADB、emulator 可用；首次检查时无在线设备，随后启动 `ytdl_api37_play_x86_64` 并等待到 `BOOTED emulator-5554`。
- 目标单测：通过。
- `:app:testDebugUnitTest`：BUILD SUCCESSFUL。
- `:app:assembleDebug`：BUILD SUCCESSFUL。
- `YtdlAppUiTest`：API37 `ytdl_api37_play_x86_64(AVD) - 17` 上 5/5 tests passed，0 failed，0 skipped；`BUILD SUCCESSFUL in 10m 11s`。

边界：本轮补强的是五页导航 accent token 的自动化约束，仍不是像素级截图 diff，也不是最终 Computer Use 前台可见全功能验收。最终 M9/T12 仍必须用 Computer Use 在前台可见模拟器窗口完成 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 的全流程。

## 2026-07-05 Computer Use 输入环境与真实下载复核

本轮复核前台可视测试前置时发现：矩阵 AVD 的 `config.ini` 均为 `hw.keyboard=no`，点击 URL 输入框会触发 Android 输入法/工具浮层，违反“不使用 Android 软键盘、候选词栏或 Gboard 菜单”的验收规则。

修复：

- `scripts/android_env.ps1` 现在会对已存在或新建的矩阵 AVD 自动写入 `hw.keyboard=yes`。
- `docs/android-dev-environment.md` 已记录硬件键盘是 Computer Use 前台输入测试前置。

复核命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
```

结果：四个矩阵 AVD 均输出 `hardwareKeyboard=yes`；额外检查 `hw.keyboard=yes` 全部通过。

前台真实流程复核：

- API37 模拟器重启后，`adb shell dumpsys input_method` 显示 `mInputShown=false`。
- 初次捕获到锁屏时，Computer Use 点击会被窗口激活状态影响；解锁并回到应用后，Computer Use 可以前台点击、输入和切页。
- `type_text` 会在该模拟器上走剪贴板式输入并触发 Gboard 浮层；该输入尝试不计入验收。随后改用 Computer Use 硬件按键逐字输入，`?` 必须用 `Shift_L+slash`，成功输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。
- 前台点击“分析”成功，识别标题 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`，预览图显示，时长 `08:02`，格式摘要显示 `自动（推荐） · 1080p MP4 需原生合并`。
- 勾选“我确认有权保存该内容”后前台点击“开始下载”，队列页真实进度从 `8% / 29.1 MB` 到 `30% / 101.7 MB`、`67% / 224.2 MB`，随后进入 `正在合并...`，最终显示 `100%`。
- 历史页显示同一任务为 `完成`，文件名 `merged-299-140.mp4`。

输出文件证据：

```text
cache/gui-downloads/task-1783236126094-1/merged-299-140.mp4 355645249 bytes
cache/gui-downloads/task-1783236126094-1/download-tkxzMEfp49Q-299-video.mp4 347653714 bytes
cache/gui-downloads/task-1783236126094-1/download-tkxzMEfp49Q-140-audio.m4a 7806830 bytes
```

补充输入复测：

- 使用 `adb shell ime disable` 禁用 API37 测试 AVD 上的 Gboard 和语音输入法后，`adb shell dumpsys input_method` 显示 `mInputShown=false`。
- 在前台可见模拟器窗口中仅使用 Computer Use 硬件按键重新输入同一 URL，并点击“分析”，公开视频解析再次成功。
- 复测期间没有弹出完整 Android 软键盘、候选词栏或 Gboard 菜单；但输入框左侧仍出现一个系统折叠小浮动按钮，后续最终 T12 前仍需决定是否接受该测试环境表现，或继续压制该浮动按钮。

当前边界：本轮已证明真实分析、真实分离流下载、原生合并、队列进度和历史落地在前台可见模拟器中跑通；但最终 T12 仍需从空白页面开始完整跑一次，包括格式选择、下载、队列、历史、设置，并确认输入过程不触发不可接受的软键盘/Gboard UI。

## 2026-07-05 15:50 Computer Use 恢复后前台复测

用户确认 Computer Use 可用后，本轮重新按前台可见窗口复测 API37 模拟器，不使用后台脚本替代 GUI 操作。

复测状态：

- `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`：JDK 17、Android SDK、Gradle 9.4.1、ADB、emulator 均可用；`emulator-5554` 在线；四个矩阵 AVD 均为 `hardwareKeyboard=yes`。
- Computer Use 成功连接 `Android Emulator - ytdl_api37_play_x86_64:5554` 前台窗口。
- 以前台可见 GUI 勾选“我确认有权保存该内容”，点击“开始下载”。
- 队列页真实进度连续变化：`7% / 22.3 MB`、`31% / 105.1 MB`、`43% / 144.0 MB`、`53% / 177.1 MB`、`64% / 213.6 MB`、`88% / 292.0 MB`、`99% / 329.3 MB`。
- 视频流完成后进入 `正在合并...`，随后显示 `下载完成`、`100%`，文件名 `merged-299-140.mp4`。
- 历史页顶部出现同一任务，状态 `完成`，时间 `07/05 07:48`。

本轮输出文件证据：

```text
cache/gui-downloads/task-1783237568987-2/download-tkxzMEfp49Q-140-audio.m4a 7806830 bytes
cache/gui-downloads/task-1783237568987-2/download-tkxzMEfp49Q-299-video.mp4 347653714 bytes
cache/gui-downloads/task-1783237568987-2/merged-299-140.mp4 355645249 bytes
```

结论：Computer Use 当前可用；真实分析后的视频+音频分离下载、原生合并、队列进度、历史落地链路已在前台可见模拟器窗口复测通过。最终 M9/T12 仍需补齐从空白输入开始的五页全功能可视验收。

追加页面覆盖：

- 格式页前台可见：`视频+音频` 模式下，2160p/1440p 灰显并标注 `当前视频未提供`；1080p 选中后摘要显示 `实际下载：1080p MP4 需原生合并`；应用选择后下载页摘要同步为 `1080p MP4 需原生合并`。
- 设置页前台可见：包含默认保存位置、Cookies 文件、解析器版本、媒体处理能力、通知权限、隐私与授权说明、地址校验提示、外观与颜色；地址校验提示仅说明空地址、非法地址和非 http/https，不包含域名屏蔽。
- Shorts 链接 `https://www.youtube.com/shorts/QBwpO9f0oAw` 从空白输入框开始，仅用 Computer Use 硬件按键输入；禁用 Gboard 后未出现完整软键盘、候选栏或 Gboard 工具浮条。
- Shorts 分析成功：标题 `Luka and Jalen 🤝`，时长 `00:14`，预览图显示，格式摘要 `自动（推荐） · 1280p MP4 需原生合并`。
- Shorts 真实下载完成：队列页显示 `4.1 MB / 4.1 MB`、`100%`、`merged-136-140.mp4`；历史页顶部显示同一任务 `完成`，时间 `07/05 07:55`。

Shorts 输出文件证据：

```text
cache/gui-downloads/task-1783238122848-3/download-QBwpO9f0oAw-136-video.mp4 4107901 bytes
cache/gui-downloads/task-1783238122848-3/download-QBwpO9f0oAw-140-audio.m4a 231677 bytes
cache/gui-downloads/task-1783238122848-3/merged-136-140.mp4 4344747 bytes
```

剩余边界：本轮已经覆盖下载、格式、队列、历史、设置五页，并覆盖普通 YouTube 链接与 Shorts 链接；后续最终验收仍建议补一次应用冷启动后的完整串联录像式流程，避免当前历史状态和已分析状态对体验判断产生影响。

## 2026-07-05 16:20 冷启动前台串联复测

用户澄清：后续第 7 项到达时只做小米 14 真机验收，不做 Google Play 商店交付；当前不连接小米 14，继续按顺序推进 M9/T12 模拟器前台验收。

本轮环境和安装：

- `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`：JDK 17、Android SDK、Gradle 9.4.1、ADB、emulator 可用；矩阵 AVD `hardwareKeyboard=yes`。
- 启动 API37 `ytdl_api37_play_x86_64`，等待到 `sys.boot_completed=1`。
- `cd android; .\gradlew.bat :app:assembleDebug`：`BUILD SUCCESSFUL`。
- `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`：`Success`。
- `adb shell monkey -p com.garyapp.ytdl 1` 启动到 `com.garyapp.ytdl/.MainActivity`。

Computer Use 冷启动前台流程：

- 冷启动下载页可见：空输入框、预览占位、保存位置、下载模式、授权确认和禁用的开始按钮。
- 空 URL 点击分析后可见失败提示：`请先输入公开视频页面地址。`
- 初次输入时发现系统 `mInputShown=true`，不计入验收；随后禁用 Gboard 和语音输入法，确认 `mInputShown=false` 且 `ime list -s` 为空。
- 在干净输入状态下仅用 Computer Use 硬件按键重新输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`，未出现软键盘、候选栏或 Gboard 工具浮层。
- 分析成功：标题 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`，预览图可见，时长 `08:02`，摘要 `自动（推荐） · 1080p MP4 需原生合并`。
- 格式页可见：2160p/1440p 灰显并显示 `当前视频未提供`；1080p 可选并显示 `需原生合并`；应用后下载页摘要同步为 `1080p MP4 需原生合并`。
- 授权确认勾选后启动下载；队列页进入真实任务，进度连续变化：`23% / 77.6 MB`、`30% / 101.0 MB`、`35% / 116.9 MB`、`45% / 150.4 MB`、`52% / 175.7 MB`、`63% / 208.9 MB`、`83% / 277.6 MB`、`94% / 313.3 MB`。
- 视频流完成后进入音频下载 `100% / 7.4 MB`，随后进入 `正在合并...`，最终显示 `下载完成`、`100%`、`merged-299-140.mp4`。
- 历史页顶部出现本轮记录，状态 `完成`，时间 `07/05 08:16`，打开/分享/导出/删除入口可见。
- 历史 `打开`：能调起系统视频查看器并播放本地合并文件。
- 历史 `分享`：能打开系统分享面板，显示 `Sharing 1 file` 和 `merged-299-140.mp4`；未选择任何分享目标，未发送文件。
- 历史 `导出`：能打开系统保存界面，预填文件名 `merged-299-140.mp4`；未点击 SAVE，未写出外部文件。
- 历史 `删除`：按钮可见；因删除本地记录属于破坏性 UI 操作，本轮未执行删除。
- 设置页可见 Cookies 文件、解析器版本、媒体处理能力、通知权限、隐私与授权说明、地址校验提示和外观与颜色。
- Cookies 选择入口能打开系统文件管理器；本轮未选择 cookies 文件，因为用户未提供测试用 `cookies.txt`。

本轮输出文件证据：

```text
cache/gui-downloads/task-1783239204052-1/download-tkxzMEfp49Q-140-audio.m4a 7806830 bytes
cache/gui-downloads/task-1783239204052-1/download-tkxzMEfp49Q-299-video.mp4 347653714 bytes
cache/gui-downloads/task-1783239204052-1/merged-299-140.mp4 355645249 bytes
```

剩余边界：

- 未执行历史删除，因为需要用户明确确认删除动作。
- 未选择真实 cookies 文件，因为当前没有用户提供的测试 `cookies.txt`。
- 未做小米 14 真机验收；当前 ADB 只检测到模拟器，且用户确认电脑暂不连接小米 14。
- 仍需继续做外部导出写出、真实 cookies 文件选择、删除确认和最终 Computer Use 全量前台复测等剩余 M9/T12 项；视觉密度审计见后续小节。

## 2026-07-05 视觉密度修复与截图审计

本轮继续按顺序处理 Android GUI 与基准图的视觉差距，没有开始小米 14 真机验收。

修复内容：

- 增加项目内紧凑 Typography，降低页标题、正文、标签等默认字号密度。
- 收紧底部导航、卡片、分段按钮、列表项、队列/历史缩略图和页面间距。
- 下载页常态消息不再长期占据首屏提示卡；空 URL、失败、下载入队、导出/cookies/通知等需要用户知道的反馈仍显示。

新鲜验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：

- 环境脚本：JDK 17、Android SDK、Gradle 9.4.1、ADB、emulator 可用；API37 模拟器在线。
- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`。
- `YtdlAppUiTest`：API37 `ytdl_api37_play_x86_64(AVD) - 17` 上 5/5 tests passed，0 failed，0 skipped；`BUILD SUCCESSFUL in 9m 17s`。

静态视觉证据：

- 已安装当前 debug APK 到 API37 模拟器，并采集五页截图：`docs/qa/android-visual-audit-20260705-compactfix/`。
- `download.png` 显示下载页首屏不再被常态消息卡挤压。
- `history.png` 与 `settings.png` 相比修复前信息密度更接近基准图。

边界：

- 本轮 ADB 截图只算静态视觉证据，不替代 Computer Use 前台全功能验收。
- 本轮没有重新执行破坏性历史删除、真实 cookies 文件选择、外部导出写出、通知/取消前台路径。
- 小米 14 真机验收仍留到后续第 7 项且设备连接后执行。

## 2026-07-05 队列取消路径补强

本轮根据 fresh 审计结果修复了一个取消 race：用户在任务刚入队、前台服务尚未 attach 取消令牌时点击取消，旧逻辑可能丢失取消请求。现在 `DownloadCoordinator.cancelActive()` 会记住非终态任务的取消请求，并在服务 attach `MutableDownloadCancellation` 时立即传递。

新增验证：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadCoordinatorTest.cancelBeforeServiceAttachesCancellationIsRemembered
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.queueRuntimeMessagesOnlyShowUserActionFeedback
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#downloadPageCanCancelRunningForegroundTask"
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：

- race 单测先红后绿，证明早取消请求已被保留。
- 全量 `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`。
- API37 connected 真实取消测试：`BUILD SUCCESSFUL`，流程为真实分析 `https://www.youtube.com/watch?v=tkxzMEfp49Q`、应用 1080p 视频+音频选择、启动前台下载、进入队列后立即点击取消、UI 进入 `最近任务已取消`，Room 最新历史必须为 `canceled`，不能为空通过或写成 completed。
- API37 connected 全量 `YtdlAppUiTest`：6/6 tests passed，`BUILD SUCCESSFUL in 11m 10s`。

辅助截图：

- `docs/qa/android-cancel-20260705/08-queue-canceled.png`

Computer Use 边界：

- 本轮 Computer Use 可以枚举 `Android Emulator - ytdl_api37_play_x86_64:5554`，但窗口捕获显示黑屏，实际点击失败：`failed to activate captured window`。
- ADB 截图确认 App 前台画面正常，但 ADB 截图和 connected/UIAutomator 仍只能作为辅助证据，不能替代最终 Computer Use 前台可视验收。
- 系统通知栏里的通知 action 取消仍未完成 Computer Use 前台可视验收。

## 2026-07-05 系统通知栏取消路径补强

本轮新增 `YtdlAppUiTest.notificationActionCanCancelRunningForegroundTask`，用真实链接 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 启动 1080p 视频+音频前台下载，打开系统通知栏，展开 `YTDL 下载任务` 通知后点击 `取消` action，并断言 Room 最新历史进入 `canceled`。

验证过程：

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#notificationActionCanCancelRunningForegroundTask"
```

结果：

- RED：首次测试能看到 `YTDL 下载任务`，但未展开通知时找不到 `取消` action，证明测试确实覆盖通知栏交互细节。
- GREEN：改为真实用户动作“展开通知后点击取消”后，API37 connected 测试 1/1 通过；P3 稳定性修正后复跑为 `BUILD SUCCESSFUL in 1m 14s`。
- 同轮全量 `YtdlAppUiTest`：API37 7/7 tests passed；P3 稳定性修正后复跑为 `BUILD SUCCESSFUL in 11m 37s`。
- 辅助截图已保存到 `docs/qa/android-notification-cancel-20260705/`。

边界：这是 connected/UIAutomator 辅助证据，不替代最终 Computer Use 前台可见全量验收；通知权限拒绝路径仍需后续前台检查。

## 2026-07-05 阶段化进度和导出命名收敛

根据前台观察反馈，本轮确认当前 MVP1 下载链路不是并行下载视频流和音频流：`DownloadPipeline` 对视频+音频合并任务按“下载视频 -> 下载音频 -> 原生合并”串行执行。队列页已改为显示分阶段状态，避免用户看到进度条从 `0%` 直接跳到 `100%` 或误把视频阶段进度理解为全任务进度。

本轮 UI 规则：

- 已完成阶段显示绿色 `✓`。
- 正在进行阶段使用灰色加粗和下划线。
- 阶段进度条对应当前阶段。
- 右侧百分比对应整个下载大项的估算进度。

同名输出处理也已收敛：App 私有下载目录继续使用每任务唯一目录避免内部串档；历史页导出时，系统保存对话框默认文件名改为“视频标题 + 完成时间 + 扩展名”，避免多个导出任务都显示 `merged-299-140.mp4`。外部覆盖仍不作为默认行为；覆盖应由用户在系统保存器或后续明确选项中确认。

Computer Use 边界：本轮重新连接后可以枚举窗口并被动截图模拟器，但对窗口点击/按键仍报 `failed to activate captured window`，所以本节不是最终前台可视验收通过记录。进一步跨窗口 smoke 显示，不只是 Android Emulator，多个普通 Windows 窗口的 `activate_window` 也返回同一错误；当前阻断位于 Computer Use/Windows 窗口激活链路，而不是 App 代码或模拟器内页面。后续可视 URL 输入必须先由 Computer Use 正常聚焦输入框；优先使用 `type_text` 或可访问性写入，也允许在不触发软键盘/Gboard 浮层的前提下用 `Ctrl+V` 粘贴，不能再把逐字慢速输入作为常规测试方式。若 Computer Use 仍不能激活窗口，必须先修测试环境。
