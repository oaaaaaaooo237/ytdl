# Android 全真全量可视测试总账

日期：2026-06-20

## 目的

本文件用于约束 Android Play 版后续测试顺序，避免把单元测试、静态 GUI shell、instrumentation 后台检查或演示数据误当成真实功能验收。

权威输入：

- 需求与设计：`docs/superpowers/specs/2026-06-19-ytdl-android-play-design.md`
- 实现计划：`docs/superpowers/plans/2026-06-19-ytdl-android-play-mvp.md`
- GUI 基准图：`docs/android-gui-reference-v3.png`
- 固定真实测试地址：
  - 普通视频：`https://www.youtube.com/watch?v=tkxzMEfp49Q`
  - Shorts：`https://www.youtube.com/shorts/QBwpO9f0oAw`

## 硬性验收规则

1. 涉及 GUI、下载、打包、媒体处理、保存、导出、历史、通知的功能，不能只用单元测试或后台命令验收。
2. Android 功能验收必须使用 Computer Use 操作前台可见模拟器窗口并截图留证。UIAutomator 只能作为辅助定位/回归证据，不能记为通过。
3. `pytest`、Gradle unit test、instrumentation test 是前置证据，不等于用户可用性验收。
4. 下载进度必须来自真实 yt-dlp 下载事件或真实前台服务状态；静态百分比、演示队列、mock 进度不得算通过。
5. 当前 APK 安装到模拟器并重新操作后才算新鲜证据。代码变更后，受影响功能的旧截图和旧测试结论自动变为待重测。
6. 对 `tkxzMEfp49Q` 的后续测试顺序必须是：真实分析 -> 分离视频/音频下载能力 -> MediaProcessor 合并能力 -> 独立字幕文件能力 -> GUI 绑定 -> 前台可视下载/合并/队列/历史/导出/通知全流程。
7. 每项测试完成后必须记录命令、设备、时间、输出摘要、截图或文件证据、是否通过、遗留问题。
8. 前台可视测试输入 URL 时不得打开或使用模拟器软键盘、候选词栏或 Gboard 菜单；必须用 Computer Use 正常聚焦输入框后直接输入或通过可访问性写入。若正常输入失败，记录为测试阻断并修复，不得用软键盘绕过。
9. “通过验收”只用于 T12 全量 MVP 回归通过之后；T0-T11 只能标记为“已测/能力层通过/绑定层通过/待重测”，不能替代最终用户验收。
10. 最终验收必须是全面但不重复的前台可视测试：一个主路径覆盖五个页面和核心功能矩阵，另用 Shorts 做兼容抽样；不得用多轮相同按钮点击替代未覆盖功能。
11. 测试报告必须明确区分“能力层通过”“GUI 绑定通过”“全量可视验收通过”。任何一层未通过时，后续层只能记录为未开始或阻断，不能写成通过。

## 测试分层与去重原则

后续验收分三层记录，不能混淆：

1. 能力层测试：Gradle unit test 和 API37 instrumentation 用来证明核心能力存在，例如 yt-dlp 真实分析、指定 format id 下载、MediaProcessor 合并输出含音轨和视频轨。能力层可以用 adb/Gradle 执行，但不能替代用户可见验收。
2. GUI 绑定测试：证明界面选择、按钮、队列状态、历史状态与能力层结果相连。可以用 UIAutomator 做辅助回归，但最终用户路径必须由 Computer Use 前台操作。
3. 全真全量可视验收：只在能力层通过后执行一次完整主路径，不再重复跑无意义的同类流程。通过标准是用户在前台可见模拟器中完成输入、分析、选择高分辨率视频+音频、分离下载、合并、队列完成、历史查看、设置状态检查。

去重规则：

- 没有 MediaProcessor 合并能力前，不再反复测试旧的 ffmpeg 合并 GUI 标签。
- MVP1 不测试字幕嵌入/烧录；带字幕任务只验收“合并后的视频音频文件 + 独立字幕文件”。
- 已由 instrumentation 证明的能力，不用 Computer Use 重复检查内部文件字节；Computer Use 只检查用户能否触发并看懂流程。
- Shorts URL 只作为兼容性抽样，不重复覆盖普通视频已覆盖的每个按钮。
- 每次代码变更只重测受影响层级；最终 release gate 再跑全量。

## 全量可视验收覆盖矩阵

T12 只跑一条完整主路径，但必须覆盖下表；每个项目只在最合适的位置验证一次，避免重复耗时。

| 页面/能力 | T12 必测内容 | 不重复测试的内容 |
| --- | --- | --- |
| 下载页 | 输入真实 URL、分析、缩略图/标题/时长/格式摘要、保存位置、开始下载即时反馈 | 不重新证明 format id 下载字节数；该证据来自 T8C/T8D |
| 格式页 | 只显示/启用当前视频真实支持的格式，禁用不支持项并说明原因，应用高分辨率 `视频+音频` 选择 | 不逐个点击所有分辨率；只抽查支持项、禁用项和一个 merge-required 主路径 |
| 队列页 | 展示视频下载、音频下载、字幕下载、合并、完成/失败/取消状态，进度来自真实事件 | 不用静态样例队列；不重复内部 track 检查 |
| 历史页 | 完成后出现历史记录，打开/导出/删除至少覆盖一个成功项 | 不重复所有排序/筛选边角，除非本轮改动历史逻辑 |
| 设置页 | 保存位置、cookies 引用、解析器版本、原生媒体处理能力、MVP2 字幕烧录说明、通知权限、隐私/授权说明、外观配色状态 | 不读取或展示 cookies 内容 |
| 前台服务/通知 | 下载过程中用户可感知，通知拒绝时 app 内仍显示状态 | 不把系统通知截图作为唯一证据 |
| 隐私与失败 | 至少覆盖空 URL、非法 URL、字幕不可用或处理失败中的一个真实错误展示；日志/界面不泄露敏感内容 | 不在最终可视主路径里故意触发所有错误；其余由 T10/T11 测试覆盖 |
| Shorts 兼容 | 用 `https://www.youtube.com/shorts/QBwpO9f0oAw` 做分析和短下载抽样 | 不重复主路径的历史/设置/导出全套断言 |

通过 T12 前，以下能力必须已经有新鲜证据：T1 构建与单元测试、T2 真实分析、T8C 指定格式分离下载、T8D required URL 核心合并、T8E 独立字幕文件输出、T6/T7 GUI 绑定、T9/T10/T11 历史/隐私/失败恢复。

## 推荐测试顺序

### T0 环境与设备前置

目的：确认本轮 Android SDK、JDK、Gradle、AVD、adb 是可用状态。

执行时机：每次继续 Android 开发、启动模拟器、跑 connected test 前。

命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
```

通过标准：

- JDK 17、Android SDK、Gradle 9.4.1、adb、emulator 可定位。
- 至少 `ytdl_api37_play_x86_64` 已连接为 `device`。
- 不用 `emulator -list-avds` 为空来判断无模拟器。

当前状态：已测。

本轮证据：2026-06-20 已运行环境脚本，API37 emulator-5554 处于 `device`，Gradle 9.4.1、adb 36.0.2 可用。

### T1 构建与非 GUI 单元测试

目的：确认基础代码、Room、策略、格式映射、隐私过滤、UI 文案模型没有编译或单元层错误。

执行时机：每次实现变更后；前台 GUI 测试前。

命令：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

通过标准：

- 两条命令都 `BUILD SUCCESSFUL`。
- 无新增失败测试。

当前状态：已测。

本轮证据：2026-06-20 已运行 `:app:testDebugUnitTest` 和 `:app:assembleDebug`，均 `BUILD SUCCESSFUL`。2026-06-21 审计修复后再次顺序运行 `:app:testDebugUnitTest` 和 `:app:assembleDebug`，均 `BUILD SUCCESSFUL`。

### T2 真实 URL 分析核心测试

目的：证明 `tkxzMEfp49Q` 能通过 Android APK 内置 Chaquopy + yt-dlp 真实分析，不只是桌面脚本或假数据。

执行时机：Task 4 及每次改动 `YtdlpBridge`、Python bridge、yt-dlp 版本、URL 策略后。

命令：

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#analyzesRequiredYoutubeSmokeUrl"
```

通过标准：

- 测试在 API37 设备上通过。
- 输出 `YTDL_ANALYSIS_SMOKE`，包含非空标题、formats 数量大于 0。
- 不保存 cookies 内容、不输出敏感请求头。

当前状态：已测。

本轮证据：2026-06-20 已在 API37 模拟器运行分析 instrumentation；输出 `YTDL_ANALYSIS_SMOKE sdk=37 device=sdk_gphone16k_x86_64 title=Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season formats=27 highest=1080 subtitles=0`。

### T3 真实单文件下载核心测试

目的：先在 GUI 绑定前证明 Android APK 内部可以真实下载文件，并捕获真实进度事件。

执行时机：Task 5 开始阶段，必须早于 GUI 下载按钮验收。

命令：

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#downloadsRequiredYoutubeSmokeUrlWithRealProgress"
```

通过标准：

- 使用 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。
- 输出文件位于 app 私有 cache/files 目录。
- 文件存在且大小大于 0。
- 输出 `YTDL_DOWNLOAD_PROGRESS` 多次，至少包含下载中进度和完成进度。
- 输出 `YTDL_DOWNLOAD_SMOKE`，记录文件名、字节数、progressEvents。
- 测试结束后记录下载文件位置；app 私有缓存可由测试自行清理。

当前状态：已测。

当前证据：

- 已先写 failing test，并确认缺少 `downloadSingleFile` 等真实下载 API 时测试失败；这证明此前没有真实下载链路。
- 2026-06-20 已用 API37 模拟器运行真实下载 instrumentation：
  - 命令：`.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#downloadsRequiredYoutubeSmokeUrlWithRealProgress"`
  - 测试结果：`tests="1" failures="0" errors="0" skipped="0"`
  - 进度证据：logcat 从 `percent=0.002298180478447062 downloaded=1024 total=44556988` 到 `status=finished percent=100.0 downloaded=44556988 total=44556988`
  - 下载证据：`YTDL_DOWNLOAD_SMOKE sdk=37 device=sdk_gphone16k_x86_64 file=download-tkxzMEfp49Q.mp4 bytes=44556988 progressEvents=69`
  - 证据文件：`android/app/build/outputs/androidTest-results/connected/debug/ytdl_api37_play_x86_64(AVD) - 17/logcat-com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest-downloadsRequiredYoutubeSmokeUrlWithRealProgress.txt`
- 2026-06-20 已重新运行全量 `:app:connectedDebugAndroidTest`：
  - XML 结果：`tests="4" failures="0" errors="0" skipped="0"`
  - 覆盖：真实分析、真实下载、五页 UIAutomator 导航、队列滚动。

### T4 前台可见 GUI 分析流程

目的：证明用户在可见模拟器里输入 URL 后，GUI 真实触发分析并显示结果。

执行时机：T1、T2 通过后；下载按钮接入前也可以单独验收。

操作步骤：

1. 安装当前 debug APK。
2. 前台打开 `ytdl_api37_play_x86_64` 模拟器窗口。
3. 用 Computer Use 点击下载页 URL 输入框。
4. 输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。
5. 点击 `分析`。
6. 等待真实分析完成。
7. 截图记录标题、时长、格式摘要、缩略图状态和错误提示状态。

通过标准：

- 可见窗口内完成操作，不只用 adb。
- 分析按钮不会卡死 UI。
- 标题、时长、格式摘要来自真实分析结果。
- 当分析结果包含 `thumbnailUrl` 时，页面必须显示真实缩略图。
- 缩略图加载失败时必须明确显示失败/占位，不影响下载，但不能把占位图当成缩略图通过。

当前状态：需按新规则重测。

本轮辅助证据：2026-06-20 已用前台 UIAutomator 流程输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`，点击 `分析`，页面显示真实标题 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`、真实缩略图、时长 `08:02`、当时旧合并标签和 `分析完成，可以开始下载。`

截图：`docs/qa/android-visible-real-flow/03-analysis.png`

Shorts 辅助证据：2026-06-20 已用前台 UIAutomator 流程输入 `https://www.youtube.com/shorts/QBwpO9f0oAw`，点击 `分析`，页面显示真实标题片段 `Luka and Jalen`、真实缩略图、时长 `00:14`、当时旧合并标签和 `分析完成，可以开始下载。`

截图：`docs/qa/android-visible-real-flow/shorts-03-analysis.png`

Shorts Computer Use 历史证据：2026-06-20 已用 Computer Use 在前台可见 Android Emulator 窗口内点击输入框、使用模拟器软键盘候选输入、点击 `分析`，页面显示真实标题片段 `Luka and Jalen`、真实缩略图、时长 `00:14`、当时旧合并标签。由于输入方式不符合 2026-06-20 新增的禁用软键盘规则，本证据只能证明分析结果展示，不计入最终前台输入验收。

截图：`docs/qa/android-visible-real-flow/cu-shorts-analysis-complete.png`

普通视频 Computer Use 历史证据：2026-06-20 已用 Computer Use 在前台可见 Android Emulator 窗口内清空输入框、使用模拟器软键盘候选输入 `https://youtu.be/tkxzMEfp49Q`、点击 `分析`，页面显示真实标题 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`、真实缩略图、时长 `08:02`、当时旧合并标签，并显示 `分析完成` 状态。由于输入方式不符合 2026-06-20 新增的禁用软键盘规则，本证据只能证明分析结果展示，不计入最终前台输入验收。

截图：`docs/qa/android-visible-real-flow/cu-normal-analysis-complete.png`

### T5 前台可见 GUI 单文件下载与真实进度

目的：证明用户点击开始下载后，队列页显示真实任务，进度条随真实下载事件变化。本项只覆盖站点直接提供的单文件音视频下载；它不能代表高分辨率音视频分离合并已经通过。

执行时机：T3 通过后；GUI 下载按钮接入真实下载 worker 后。当前阶段不再优先单独验收本项，避免在高分辨率合并能力缺失时重复跑低价值单文件路径；它并入 M6/M7 后作为队列/进度绑定的辅助覆盖。

操作步骤：

1. 按 T4 完成真实分析。
2. 选择一个当前视频真实支持的格式。
3. 点击 `开始下载`。
4. 确认授权提示。
5. 自动或手动进入 `队列` 页。
6. 前台观察进度从非完成状态逐步变化到完成。
7. 截图保存开始、中途、完成三个状态。

通过标准：

- 点击开始下载后有明确反馈，例如任务已加入队列。
- 队列进度不是静态演示值。
- 进度、速度、ETA 与真实下载事件同步更新。
- 下载完成后文件存在，状态变为完成。
- 应用不闪退、不弹出空白窗口、不被下载任务卡死。
- 如果下载页或格式页显示“需原生合并”，必须另走 T8 合并测试，不能用本项代替。

当前状态：UIAutomator/instrumentation 辅助回归已测；前台 Computer Use 可视验收仍待重测。

2026-06-21 本轮辅助证据：

- RED/覆盖缺口：修改前运行 `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"` 为 `BUILD SUCCESSFUL`，但现有真实 URL GUI 流程只覆盖分析和“应用格式选择”，没有点击 `开始下载`、队列完成或历史卡片断言。
- 修复后同一目标类在 API37 `ytdl_api37_play_x86_64(AVD) - 17` 上通过，XML 为 `tests="4" failures="0" errors="0" skipped="0"`，总耗时 `565.938s`；普通视频 `downloadPageRunsRealAnalyzeAndDownloadFlow` 耗时 `362.633s`。
- 普通视频使用 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 完成真实分析、选择 `1080p MP4 需原生合并`、点击 `开始下载`、进入队列页、等待真实任务从下载进入完成态，并截图显示 `下载完成`、`100%`、`339.2 MB / 339.2 MB`、`merged-299-140.mp4`。
- 新截图保存在模拟器 `/sdcard/Download/ytdl-visible-real-flow/`：`04-download-started.png`、`05-queue-active.png`、`06-queue-complete.png`、`07-history-real-card.png`。
- Shorts `https://www.youtube.com/shorts/QBwpO9f0oAw` 在同一目标类中只保留分析和格式辅助，不重复完整下载+历史长流程。
- 测试清理使用 `DownloadCoordinator.cancelActive()`、`DownloadCoordinator.resetForTests()` 和 Room 历史逐条删除；清理会循环删除历史残留，避免旧记录误满足本轮断言；不使用 `pm clear`，避免杀掉 instrumentation 进程；不使用 mock 下载、假进度或演示队列。
- 审计后补强断言：队列完成后直接查询 Room 最新历史，要求最新记录为本轮 `开始下载` 之后写入、状态 `completed`、输出 URI 为 `app-private://outputs/task-.../merged-...`，且格式摘要包含视频和音频。
- 审计修复后复核：重新运行 `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"`，API37 `ytdl_api37_play_x86_64(AVD) - 17` 上 4 项测试、0 失败，`BUILD SUCCESSFUL in 9m 29s`。
- 本项仍是 instrumentation/UIAutomator 辅助证据，不替代 Computer Use 在前台可见模拟器窗口中的最终验收。

历史辅助证据：2026-06-20 曾用前台 UIAutomator 流程点击 `开始下载`，切换到队列页，真实任务进度从下载中变化到完成；该证据发生在 M6 前台服务接线前，只能说明旧单文件路径曾可运行，不能作为当前高分辨率视频流 + 音频流合并或当前前台服务验收依据。

截图：

- 下载中：`docs/qa/android-visible-real-flow/04-queue-progress.png`，显示 `45%`、`19.4 MB / 42.5 MB`。
- 下载完成：`docs/qa/android-visible-real-flow/05-queue-complete.png`，显示 `100%`、`42.5 MB / 42.5 MB`、`download-tkxzMEfp49Q.mp4`。

Shorts 辅助证据：2026-06-20 已用前台 UIAutomator 流程对 `https://www.youtube.com/shorts/QBwpO9f0oAw` 点击 `开始下载`，切换到队列页，真实任务进度从下载中变化到完成。

截图：

- 下载中：`docs/qa/android-visible-real-flow/shorts-04-queue-progress.png`。
- 下载完成：`docs/qa/android-visible-real-flow/shorts-05-queue-complete.png`。

Shorts Computer Use 历史证据：2026-06-20 已用 Computer Use 在前台可见 Android Emulator 窗口内点击 `开始下载`，页面显示 `真实下载已开始，已加入队列`；随后点击底部 `队列`，真实任务显示 `Luka and Jalen`、`100%`、`download-QBwpO9f0oAw.mp4`。由于该流程的 URL 输入前置步骤使用了模拟器软键盘，本证据只能作为下载状态展示参考，需重测完整前台路径。

截图：`docs/qa/android-visible-real-flow/cu-shorts-queue-complete.png`

普通视频 Computer Use 历史证据：2026-06-20 已用 Computer Use 在前台可见 Android Emulator 窗口内点击 `开始下载`，页面显示 `真实下载已开始，已加入队列`；随后点击底部 `队列`，真实任务进度从 `39%`、`16.9 MB / 42.5 MB` 变化到 `100%`、`42.5 MB / 42.5 MB`，文件名为 `download-tkxzMEfp49Q.mp4`。由于该流程的 URL 输入前置步骤使用了模拟器软键盘，本证据只能作为下载状态展示参考，需重测完整前台路径。

截图：`docs/qa/android-visible-real-flow/cu-normal-queue-complete.png`

### T6 格式页真实可选项测试

目的：证明格式页只允许选择当前分析结果真实支持的格式，不支持的格式置灰或隐藏，并说明原因。

执行时机：T4 通过后；格式选择与下载参数绑定后。

操作步骤：

1. 使用 `tkxzMEfp49Q` 完成分析。
2. 打开 `格式` 页。
3. 检查分辨率、帧率、编码、音频码率、容器、字幕行为。
4. 选择支持格式并应用。
5. 回到下载页检查实际下载摘要。

通过标准：

- 支持项可选，不支持项不可选或置灰。
- 高分辨率音视频分离时明确标记需要原生合并。
- 实际下载摘要与用户选择一致。
- 选择“视频+音频”且目标分辨率只有分离视频流时，必须把下载任务路由到 T8 的原生合并链路。

当前状态：M6 核心/声明层已实现，前台可见通知验收未完成。

M6 本轮证据（核心/单元/构建层，不是 GUI 验收）：

- 2026-06-20 已新增下载编排核心：`DownloadRequest`、`DownloadPipeline`、`DownloadState`、`DownloadService`、`NotificationController`。
- 路由单元测试覆盖：direct progressive media、video-only、audio-only、merge-required、独立字幕文件、无字幕选择、取消、失败；direct progressive media 会调用明确 `formatId + media role` 下载，不会调用旧 `downloadSingleFile()`；merge-required 会调用明确 video/audio format 下载和 `MediaProcessor.mergeVideoAndAudio()`。
- 状态单元测试覆盖：`analyzing`、`waiting`、`downloading video`、`downloading audio`、`downloading subtitles`、`merging`、`exporting`、`completed`、`failed`、`canceled`；选中字幕时，缺少字幕输出不能进入 `completed`。
- Manifest 已声明 foreground service、`dataSync` 前台服务类型和通知权限；通知权限被拒时，核心进度仍由 app 内 `DownloadTaskState` 表达，不依赖系统通知可见性。当前 `DownloadService` 已通过 `DownloadCoordinator` 承载 `DownloadPipeline`，并向通知和 app 内监听者发布真实阶段/进度。
- 命令证据：`cd android; .\gradlew.bat :app:testDebugUnitTest` 为 `BUILD SUCCESSFUL`；`cd android; .\gradlew.bat :app:assembleDebug` 为 `BUILD SUCCESSFUL`。

剩余可视验收：

- 尚未在前台可见 Android 模拟器中下拉/观察通知、拒绝通知权限后验证 app 内队列状态；这仍属于 M9 前台可视验收。
- GUI `开始下载` 按钮已通过 `DownloadCoordinator.startForegroundDownload()` 接入真实前台服务，不再调用旧 `downloadSingleFile()`；五页完整绑定、历史/导出和取消按钮仍留给 M7/M9 前台验证。

### T7 前台服务与通知测试

目的：证明下载是 Android 用户可感知的前台任务。

执行时机：真实下载 worker 接入前台服务后。

操作步骤：

1. 开始真实下载。
2. 下拉通知栏或查看系统通知。
3. 切后台后继续观察通知和 app 队列状态。
4. 测试取消操作。

通过标准：

- 有前台服务通知。
- Android 13+ 通知权限被拒时，app 内仍显示状态。
- 取消后状态区分为取消，不误报未知失败。

当前状态：未完成。

### T8 MediaProcessor 与字幕文件测试

目的：证明 MVP1 最核心需求“分离视频流 + 音频流 -> 合并输出文件”真实可用，并在 GUI 绑定前补齐独立字幕文件输出。

执行时机：现在优先执行。没有 T8B/T8C/T8D 通过前，不再把高分辨率 `视频+音频` GUI 流程标记为可验收；没有 T8E 通过前，不再把带字幕任务标记为可验收。

#### T8A MediaProcessor 合同与路线

测试项：

- 文档 `docs/android-media-processor.md` 记录当前事实：MVP1 使用原生 MediaMuxer，FFmpeg 字幕嵌入/烧录属于 MVP2。
- 代码定义 `MediaMergeRequest`、`MediaProcessingResult`、`MediaProcessor`。
- 单元测试证明：原生 muxer 只承诺兼容流的音视频合并，不承诺字幕嵌入/烧录。

通过标准：

- `:app:testDebugUnitTest` 通过。
- `:app:assembleDebug` 通过。
- 文档明确原生 MediaMuxer 与独立字幕文件的 MVP1 边界。

当前状态：已完成合同层。

本轮证据：

- 已先写 `MediaProcessorContractTest`，未写生产代码前运行 `cd android; .\gradlew.bat :app:testDebugUnitTest`，失败于缺少 `NativeMuxerMediaProcessor`、`MediaMergeRequest`、`MediaOutputContainer` 等合同类型。
- 已新增 `MediaMergeRequest`、`MediaProcessingResult`、`MediaProcessor` 和 `NativeMuxerMediaProcessor` 合同实现。
- 单元测试覆盖：video/audio 输入必须 distinct 且可读，outputFile 必须在受控输出根目录内且不能等于输入文件，原生 muxer 不支持字幕嵌入/烧录。
- 历史说明：T8A 本身只建立合同层；真实媒体合并已在 T8B/T8D 用 API37 instrumentation 证明输出 MP4 同时含视频轨和音频轨。

#### T8B 原生音视频合并能力

测试项：

- 在 API37 instrumentation 中准备或生成一个视频-only 文件和一个音频-only 文件。
- 调用 `NativeMuxerMediaProcessor.mergeVideoAndAudio()`。
- 用 `MediaExtractor` 检查输出文件。

通过标准：

- 输出 MP4 文件存在且大于 0。
- `MediaExtractor` 检出 1 条视频轨和 1 条音频轨。
- 失败时保留输入文件并输出可读错误。

当前状态：已测。

本轮证据：

- RED：首次运行 `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.media.NativeMuxerMediaProcessorInstrumentedTest"` 时，两个测试均失败在 `原生 MediaMuxer 真实合并将在 M2/T8B 实现。`
- GREEN：实现 `NativeMuxerMediaProcessor` 后，同一命令在 API37 `ytdl_api37_play_x86_64(AVD) - 17` 上通过，`tests=2 failures=0 errors=0 skipped=0`。
- 轨道证据：`NATIVE_MUXER_T8B sdk=37 device=sdk_gphone16k_x86_64 videoInput=4583 audioInput=8497 output=/data/data/com.garyapp.ytdl/cache/native-muxer-t8b/merge-success/outputs/merged.mp4 bytes=9848 videoTracks=1 audioTracks=1`。
- 辅助验证：`cd android; .\gradlew.bat :app:testDebugUnitTest` 通过；`cd android; .\gradlew.bat :app:assembleDebug` 通过。
- 注意：本项使用运行时生成的最小 video-only/audio-only fixture 证明 MediaProcessor 可合并；真实 YouTube 分离流下载和合并仍由 T8C/T8D 覆盖。

#### T8C yt-dlp 指定格式分离下载

测试项：

- 使用 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。
- 从真实 `VideoAnalysis.formats` 选择一个 video-only format id 和一个 audio-only format id。
- 用 Android APK 内置 Chaquopy/yt-dlp 分别下载两个明确 format id。

通过标准：

- 两个输出文件均在 app 私有目录存在且大于 0。
- 记录实际 video format id、audio format id、文件路径、字节数。
- Python bridge 不得用 `18/worst` 替代指定 format id。

当前状态：已测。

本轮证据：

- RED：先新增 `SplitDownloadRequestTest` 和 `downloadsRequiredYoutubeSmokeUrlSplitFormats`，未写生产代码前运行 `cd android; .\gradlew.bat :app:testDebugUnitTest`，失败于缺少 `DownloadFormatRole`、`DownloadResult.role` 和 `downloadFormat()`。
- GREEN：新增 Python `download_format(url, output_dir, format_id, role, cookies_path, progress_listener)`，Kotlin `downloadFormat()` 强类型 wrapper 和 split role/result 解析后，`cd android; .\gradlew.bat :app:testDebugUnitTest` 通过，`SplitDownloadRequestTest` 为 `tests=4 failures=0 errors=0 skipped=0`。
- 构建验证：`cd android; .\gradlew.bat :app:assembleDebug` 为 `BUILD SUCCESSFUL`。
- API37 真实下载验证：`cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#downloadsRequiredYoutubeSmokeUrlSplitFormats"` 为 `BUILD SUCCESSFUL`；XML 结果 `tests="1" failures="0" errors="0" skipped="0"`，最近时间戳 `2026-06-20T08:37:30`。
- 真实下载证据：`YTDL_SPLIT_DOWNLOAD_SMOKE sdk=37 videoFormat=394 audioFormat=139 videoBytes=5426536 audioBytes=2942838 videoPath=/data/user/0/com.garyapp.ytdl/cache/split-download-smoke/download-tkxzMEfp49Q-394-video.mp4 audioPath=/data/user/0/com.garyapp.ytdl/cache/split-download-smoke/download-tkxzMEfp49Q-139-audio.m4a`。
- 回归保护：`formatId` 必须是单个明确格式编号，`394/18/worst`、`best`、`worst`、`bestvideo`、`bestaudio`、`worstvideo`、`worstaudio`、`bv`、`ba`、`wv`、`wa`、`all`、`mergeall` 等 selector/fallback 表达式会在调用 Python 前被拒绝，避免重新引入 360p 或 worst 兜底。
- 回归保护：成功下载 JSON 必须包含非空输出路径和正字节数；Python bridge 返回成功前也会确认输出文件存在且大小大于 0。
- 回归保护：split 下载文件定位只接受当前视频 id + format id + role 的输出文件，不再从下载目录捡其他视频的同格式旧文件。
- 说明：本项只证明 Android APK 内置 Chaquopy/yt-dlp 可按明确 format id 下载 video-only 与 audio-only 两个分离文件；仍不是 GUI 验收，也不包含合并、字幕文件、历史或通知。

#### T8D required URL 核心合并 smoke

测试项：

- API37 instrumentation 完成：真实分析 -> 选择分离视频/音频 -> 下载两个文件 -> MediaProcessor 合并 -> 检查输出音视频轨。

通过标准：

- 合并输出必须有视频轨和音频轨，且不能只是 360p 单文件兜底。
- 证据写入 `docs/android-media-processor.md`，包含命令、设备、时间、format id、输出路径、字节数、track 检查结果。

当前状态：已测/能力层通过；这不是 GUI 通过，也不是全量 MVP 验收。

本轮证据：2026-06-20T09:00:14 API37 instrumentation 已完成 required URL 核心合并 smoke。命令为 `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.media.RequiredUrlMergeInstrumentedTest"`，结果 `BUILD SUCCESSFUL`，XML 为 `tests=1 failures=0 errors=0 skipped=0`。设备 `sdk_gphone16k_x86_64`，SDK 37。选择 video-only format `160`（`videoCodec=avc1.4d400c`，`videoBytes=6694014`）和 audio-only format `139`（`audioCodec=mp4a.40.5`，`audioBytes=2942838`），输出 `/data/data/com.garyapp.ytdl/cache/required-url-merge-t8d/outputs/tkxzMEfp49Q-merged.mp4`，`mergedBytes=9691149`，`MediaExtractor` 检查 `videoTracks=1`、`audioTracks=1`。

TDD 记录：新增 `RequiredUrlMergeInstrumentedTest` 后第一次运行即通过；这是把既有 T8B/T8C 能力串起来的集成验证，未产生新的生产代码 RED，本项未修改生产代码。

#### T8E 独立字幕文件输出

测试项：

- 从真实分析结果读取字幕/自动字幕可用性。
- 用户选择字幕语言/格式后，真实下载独立字幕文件。
- 字幕下载结果暴露 `path/bytes/language/ext/source/title`，供同一任务的后续队列、历史和导出绑定。
- 历史和导出路径最终需要显示/处理两个输出：合并媒体文件和字幕文件。

通过标准：

- 字幕输出文件存在且大于 0，记录语言、扩展名、来源类型和路径。
- 合并后视频音频文件仍由 T8D 证明含 1 条视频轨和 1 条音频轨。
- 没有可用字幕时，状态必须明确显示“不下载字幕/无可用字幕”，不能误报完成。
- 不声称字幕嵌入、字幕烧录或三合一输出。

当前状态：已测/能力层通过；这不是 GUI、队列、历史、导出或最终 MVP 可视验收。

本轮证据：

- RED：先新增 `SubtitleDownloadRequestTest`，未写生产代码前运行 `cd android; .\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.core.ytdlp.SubtitleDownloadRequestTest`，失败于缺少 `SubtitleSource`、`parseSubtitleDownloadJson()`、`downloadSubtitle()` 和字幕 source 字段。
- GREEN：新增普通/自动字幕解析、字幕下载请求验证、字幕下载结果解析和 Python `download_subtitle()` 后，同一单测类通过。
- API37 真实字幕验证：`cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.SubtitleDownloadInstrumentedTest"` 为 `BUILD SUCCESSFUL`；XML 结果 `tests=1 failures=0 errors=0 skipped=0`，时间戳 `2026-06-20T12:44:32`。
- 真实下载证据：`YTDL_SUBTITLE_DOWNLOAD_SMOKE sdk=37 device=sdk_gphone16k_x86_64 language=en ext=vtt source=automatic bytes=61664 path=/data/user/0/com.garyapp.ytdl/cache/subtitle-download-smoke-0/download-tkxzMEfp49Q-subtitle-automatic.en.vtt`。
- 回归保护：字幕语言和扩展名必须是单个明确值，selector/fallback 表达式会在调用 Python 前被拒绝；失败摘要会过滤敏感 URL query、cookies 路径、Authorization 和 Cookie。
- 调试记录：第一次 instrumentation 选中了自动字幕翻译语言 `aa` 并遇到 YouTube 429；测试候选选择已改为优先稳定语言和格式，单个候选失败时记录安全摘要并尝试下一个，最终以 `tkxzMEfp49Q` 的自动英文 `vtt` 字幕通过。
- 说明：本项只证明能力层独立字幕文件输出；队列、历史、导出绑定仍在 M6/M8，MVP1 不做字幕嵌入、字幕烧录或三合一输出。

### T9 历史、保存、导出测试

目的：证明下载结果能被用户找到、导出、分享或重新下载。

执行时机：真实下载和 Room 历史绑定后。

操作步骤：

1. 完成一次真实下载。
2. 打开 `历史` 页。
3. 查看记录。
4. 测试打开、分享/导出、删除记录。

通过标准：

- 历史记录包含标题、时间、格式摘要、状态和输出 URI。
- 不保存 cookies 内容、Authorization、完整敏感 URL 查询串或原始命令行。
- 导出使用系统文件选择器或 MediaStore，不申请全文件访问权限。

当前状态：M8 单元层已实现/待前台联动验证。

M8 本轮证据：

- 已新增安全历史生成：完成、失败、取消的 `DownloadTaskState` 可生成 `HistoryItemEntity`，历史中不保存 cookies 内容、Authorization、原始命令行或敏感 URL query。
- 已新增生产链路终态写入：`DownloadService` 在 pipeline 返回完成、失败或取消终态后调用 `DownloadHistoryRecorder` 写入 Room；完成态必须有真实存在且非空的媒体输出文件。
- 已新增 App 私有输出发现和导出合同：`ExportController` 生成安全的 app-private 输出引用、`ACTION_CREATE_DOCUMENT` Intent、MediaStore metadata，并可把 App 私有输出复制到目标流；不伪造已导出成功。
- 已新增历史 UI 绑定：历史页从 Room 读取最近记录并渲染真实历史卡片；空库时才显示空状态。打开/分享/删除前台操作仍留到 M9/T12。
- 已新增完成态防误报：完成历史必须有真实存在且非空的媒体输出文件；缺失输出、空输出或不存在路径不能写为成功历史。
- 单元 RED/GREEN：先运行目标测试失败于缺少 M8 API；实现后 `HistoryPrivacyTest` 目标测试通过。
- 最终命令复核：2026-06-21 顺序运行目标 M8/URL/脱敏测试、全量 `:app:testDebugUnitTest` 和 `:app:assembleDebug`，均 `BUILD SUCCESSFUL`。
- 运行 smoke：2026-06-21 将 `app-debug.apk` 安装到 API37 `emulator-5554` 并启动 `com.garyapp.ytdl/.MainActivity`，`dumpsys activity` 显示 `topResumedActivity` 为本应用且 `visible=true`。该 smoke 只证明启动级运行，不替代 M9/T12 前台全流程验收。
- M9 前置审计修复：2026-06-21 针对 P1 审计意见修复重复下载历史串档风险和通知取消生命周期。下载 pipeline 现在为每个任务建立唯一 `task-*` 输出目录，`app-private://outputs/...` 保存 App 私有根目录下的相对路径，历史打开/分享/导出可重新定位到原始输出文件；`DownloadService` 收到通知取消 action 后会调用 `stopSelf(startId)`。新增 `repeatedMergeRunsCreateDistinctHistoryUrisThatResolveToOriginalFiles` 和取消分支源码约束测试。
- 复核命令：2026-06-21 顺序运行 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:connectedDebugAndroidTest`，均 `BUILD SUCCESSFUL`；connected 在 API37 `ytdl_api37_play_x86_64(AVD) - 17` 上完成 12 项测试、0 失败。
- 2026-06-21 `YtdlAppUiTest` 辅助联动回归已确认普通视频完整下载后历史页出现真实历史卡片：自动断言覆盖标题、`完成` 状态、最新 Room 历史记录、`app-private://outputs/task-.../merged-...` 输出 URI 和视频+音频格式摘要；截图观察显示卡片含 `打开`、`分享`、`导出`、`删除` 操作。该证据仍不替代前台 Computer Use 打开/分享/导出/删除验收。

剩余验收：真实历史页打开、导出、删除和系统导出 UI 留到 M9/T12。

### T10 cookies 与隐私边界测试

目的：证明 cookies 只保存引用，不保存内容，不泄露到 UI、日志、历史或错误信息。

执行时机：cookies 入口和下载任务临时 cookies 文件实现后。

测试项：

- 选择 cookies 文件。
- 设置页只显示文件引用或安全名称。
- 下载任务运行时临时物化 cookies 文件。
- 任务完成、失败或取消后删除临时文件。
- 错误信息、历史、日志不包含 cookies 内容。

通过标准：

- 单元测试和真实任务检查均通过。
- 无 cookies 内容出现在持久化数据或用户界面。

当前状态：M8 单元层已实现/待前台联动验证。

M8 本轮证据：`TemporaryCookiesFileTest` 覆盖 cookies 内容被拒绝作为引用、文件引用物化为 App 私有临时文件且不在 `toString()` 泄露内容，复制中途失败时清理半成品，UI 准备层会把设置引用物化为受管临时文件后再构造下载请求，pipeline 在完成、失败、取消终态删除临时 cookies 文件，清理失败时进入安全失败状态并保留托管路径以便重试，并拒绝把未托管的用户原始 cookies 路径直接传给下载引擎；`SensitiveTextTest` 和 `FormatMappingTest` 覆盖日志、错误文本、yt-dlp 失败摘要中的 cookies 命令参数、`--cookies=...`、`--cookies-from-browser`、Authorization、敏感 query 脱敏。

剩余验收：真实设置页 cookies 选择、真实下载任务和错误界面的前台可见检查留到 M9/T12。

### T11 失败场景与恢复测试

目的：证明用户遇到异常时能理解下一步，而不是看到空白或闪退。

执行时机：真实分析、下载、服务、历史均接入后。

测试项：

- 空 URL。
- 非 URL 文本。
- 非 http/https 地址。
- 网络中断或不可达。
- 需要授权访问。
- 磁盘/保存位置不可用。
- 字幕不可用或字幕下载失败。
- 下载取消。

通过标准：

- 错误中文可读。
- 不泄露敏感信息。
- 状态可恢复或可重试。

当前状态：M8 单元层已实现/待前台联动验证。

M8 本轮证据：`HistoryPrivacyTest` 覆盖空 URL、非法 URL、非 http/https 地址、网络失败、字幕不可用、处理失败、导出拒绝、取消、cookies 临时文件清理失败和历史写入失败的中文可读文案，且断言错误文本不泄露 cookies、Authorization、敏感 query 或 cookies 文件引用；完成态历史额外覆盖无媒体输出和媒体文件不存在时不能写为成功。

剩余验收：至少一个真实失败场景的前台可见恢复路径留到 M9/T12。

### T12 全量 MVP 回归

目的：在所有模块接通后，按用户路径完整跑一遍。

执行时机：Task 9 release gate。

命令和操作：

```powershell
.\.venv\Scripts\python.exe -m pytest
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

前台可见操作：

1. 启动模拟器。
2. 安装当前 APK。
3. 用 Computer Use 在前台可见模拟器窗口输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`，不得使用模拟器软键盘。
4. 分析。
5. 选择真实支持的高分辨率 `视频+音频` 选项，且该选项需要分离视频流和音频流。
6. 开始下载。
7. 观察队列真实显示视频流下载、音频流下载、字幕下载或明确跳过原因、MediaProcessor 合并、完成状态。
8. 完成后检查历史。
9. 导出或打开合并后的输出文件。
10. 检查设置页、cookies 边界、MediaProcessor/MVP2 字幕能力说明、通知权限和隐私说明。
11. 触发一个可恢复失败场景并确认中文错误可读且不泄露敏感信息。
12. 用 `https://www.youtube.com/shorts/QBwpO9f0oAw` 做一次 Shorts 分析和短下载抽样。

通过标准：

- 所有命令通过。
- 前台可见操作全流程通过。
- 证据写入 `docs/qa/android-mvp-smoke.md`。

当前状态：未完成。2026-06-21 命令层和 API37 connected 层已通过，但 Computer Use 前台可视操作入口仍因工具层错误阻断，不能记为 T12 通过。

本轮阻断证据：尝试通过 Computer Use 技能入口连接 Windows 自动化 helper 时，工具调用失败：`Mcp error: -32602: js: codex/sandbox-state-meta: missing field sandboxPolicy`。2026-06-21 再次按技能流程确认入口文件存在，连接轻量 `list_apps()` 失败；重置会话后重试仍为同一错误。因此本轮没有完成“前台可见模拟器窗口 + Computer Use 全流程”验收。

2026-06-21 继续复核：再次重置连接上下文后执行轻量 `list_apps()`，仍返回同一 `sandboxPolicy` 工具层错误。作为辅助视觉巡检，已用 adb 安装当前 APK、前台启动 API37 模拟器并截图查看下载、格式、队列、历史、设置五页空态；五页布局和关键文案可见，队列页有可滚动区域，设置页明确“仅校验空地址、非法地址和非 http/https”。该辅助巡检只用于发现明显 GUI 偏差，不计入 T12 通过证据。

## 当前总状态

| 测试项 | 状态 | 说明 |
| --- | --- | --- |
| T0 环境与设备前置 | 已测 | 本轮已跑 `android_env.ps1` |
| T1 构建与单元测试 | 已测 | `testDebugUnitTest`、`assembleDebug` 已通过 |
| T2 真实 URL 分析核心 | 已测 | API37 真实分析 `tkxzMEfp49Q`，formats=27，highest=1080 |
| T3 真实单文件下载核心 | 已测 | API37 真实下载 `download-tkxzMEfp49Q.mp4`，44,556,988 字节，69 个进度事件 |
| T4 前台 GUI 分析 | 需按新规则重测 | 旧 Computer Use 证据使用了模拟器软键盘输入，只能作为展示参考 |
| T5 前台 GUI 单文件下载进度 | instrumentation 辅助已测/待前台重测 | 2026-06-21 `YtdlAppUiTest` 已让普通视频完成真实 `1080p` 分离下载、原生合并、队列完成和历史卡片；Shorts 只做分析/格式辅助；仍不是 Computer Use 前台验收 |
| T6 格式真实可选项 | 绑定层通过/待前台重测 | M7 已用单元测试和审计覆盖当前分析结果格式绑定、不可用项原因、模式卡选中态、实际帧率/编码/容器摘要、旧摘要/旧 format id 防复用；仍需前台可见 GUI 重测 |
| T7 前台服务通知 | 绑定层补强/待前台可视验收 | M6 已完成 foreground service 声明、通知控制器、`DownloadService` 承载 pipeline 和核心状态模型；M9 前置补上队列页真实取消入口和通知取消 action；尚未做前台可见通知/拒权 GUI 验收 |
| T8A MediaProcessor 合同与路线 | 已完成合同层 | 已定义合同、校验边界和原生 muxer 职责；真实合并在 T8B |
| T8B 原生音视频合并能力 | 已测 | API37 instrumentation 已证明输出 MP4 含 1 条视频轨和 1 条音频轨 |
| T8C yt-dlp 指定格式分离下载 | 已测 | API37 已真实下载 video-only format 394 与 audio-only format 139 两个文件 |
| T8D required URL 核心合并 smoke | 已测/能力层通过 | API37 已用 `tkxzMEfp49Q` 完成分析、format 160/139 分离下载、原生合并和 track 检查；非 GUI/MVP 验收 |
| T8E 独立字幕文件输出 | 能力层通过/GUI 绑定补强 | API37 已下载 `tkxzMEfp49Q` 自动英文 `vtt` 字幕到 app 私有 cache；M9 前置补上格式页字幕开关和下载请求 `selectedSubtitles` 传递；真实前台字幕下载仍待 T12 |
| T9 历史保存导出 | 绑定层补强/instrumentation 辅助已测/待前台验证 | 2026-06-21 `YtdlAppUiTest` 已确认完成下载后历史页出现真实完成卡片和操作入口；真实打开、分享、导出、删除的前台操作仍留到 T12 |
| T10 cookies 隐私边界 | M8 单元层已实现/待前台验证 | cookies 只保存引用、临时文件终态删除、复制失败清理、拒绝原始 cookies 路径、设置/历史/日志/错误脱敏已覆盖；真实设置页选择和任务联动留到 M9/T12 |
| T11 失败恢复 | M8 单元层已实现/待前台验证 | 失败文案和脱敏已覆盖；真实前台失败恢复路径留到 M9/T12 |
| T12 全量 MVP 回归 | 未完成 | `testDebugUnitTest`、`assembleDebug`、`connectedDebugAndroidTest` 已通过；Computer Use 入口仍被 `sandboxPolicy` 工具层错误阻断，不能算前台可视验收通过 |

## 下一步推进顺序

1. M4/T8D：已完成能力层核心分析、分离下载、合并 smoke，并检查输出音视频轨。
2. M5/T8E：已完成独立字幕文件能力层输出，队列/历史/导出绑定留给后续任务。
3. M6：已完成核心下载编排、前台服务声明、通知控制器、`DownloadService` 承载 pipeline 和真实队列状态模型；GUI `开始下载` 已接入前台服务以移除旧 `18/worst` fallback。前台可见通知、队列交互、取消按钮、历史/导出仍需 M7/M9 验证。
4. M7：绑定层通过/待前台重测；测试口径为 `DownloadGuiBindingTest` 覆盖当前 `VideoAnalysis` 格式选择、下载摘要防旧值、队列阶段文案、设置页 parser/media 标签、开始下载即时状态、模式卡真实选中态、格式详情真实摘要和无输出完成态防误报，并已通过审计。该状态不代表前台可见模拟器全流程通过。
5. M8：已完成单元层实现并通过 2026-06-21 全量 `testDebugUnitTest` / `assembleDebug` 复核，APK 已在 API37 上安装并启动到 `MainActivity`；前台可见历史、导出、cookies 和失败恢复留到 M9/T12。
6. M9 前置：已补外观配色设置持久化与即时应用、队列/通知取消、历史打开/分享/导出/删除、字幕独立文件选择绑定、重复下载唯一输出和历史相对 URI；全量单测、构建、connected 已通过，仍需恢复 Computer Use 前台可见模拟器验证。
7. M9/T12：Computer Use 前台可见全真全量验收；这是唯一最终通过口径。
