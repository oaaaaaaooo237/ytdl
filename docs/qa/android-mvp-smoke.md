# Android MVP Smoke 证据账本

日期：2026-07-09

## 当前结论

Android Play MVP 的 API37 模拟器前台 M9/T12 验收已有新鲜通过证据；真机 M10、Play 签名/商店素材和正式上架交付仍未完成。

截至 2026-07-09（历史记录，已由 2026-07-10 地址集替代），真实测试地址曾切换为三条链接。当时主地址 `https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G` 已完成 Computer Use 前台可见完整 GUI 路径：点击地址框直接弹出完整 Gboard、逐键输入完整分享 URL、真实分析、格式页 MP4 原生合并兼容复核、无字幕下载、队列视频/音频/原生合并完成、历史落地和系统播放器打开。通知拒权设置页前台复核已完成。当时 Shorts 地址 `jWTrleK2_MU` 已完成前台软键盘输入、真实分析、格式页竖屏高度修复复核、无字幕短下载、原生合并、队列完成和历史落地。真实字幕下载按用户要求暂停，不再作为默认 T12 阻塞项；后续真机验收阶段尚未开始。当前输入环境已改为更拟真手机的 `hw.keyboard=no`，并已通过可见 Gboard 设置修正为点击 URL 输入框后直接从底部弹出完整键盘；最终前台验收不得再依赖先显示工具条、再点 `Show on-screen keyboard` 的路径。

2026-07-06 复查：Computer Use 已能激活 `Android Emulator - ytdl_api37_play_x86_64:5554` 并前台操作当前 APK；已用 Computer Use 点击并截图 `下载 -> 格式 -> 队列 -> 历史 -> 设置` 五页。早期为压制输入法曾使用 `Ctrl+V`、文本注入和硬件键事件；这些证据只保留为历史支持。最新测试口径改为完全拟真真机：点击 URL 输入框后允许并优先使用 Android 系统软键盘完成输入，测试重点改为确认 URL 未被候选词、自动补全、手写浮层或 Gboard 菜单改写，且流程可继续。

2026-07-10 测试地址替换：后续真实测试只使用普通视频主路径 `https://www.youtube.com/watch?v=PqQNXB6hhUs`、普通视频备用 `https://www.youtube.com/watch?v=svoD582Pas4`、Shorts 抽样 `https://www.youtube.com/shorts/oXFad1nt6v0`。问题 4 的单文件媒体专项另使用用户 2026-07-13 指定的两个 Eporner 地址：`https://www.eporner.com/video-cDSGZsgq7rb/transfixed-muscle-hunk-gets-buttfucked-by-two-horny-trans-girls-kasey-kei-and-bella-joie/?trx=1227735290aee694b81473a256bea12420712` 和 `https://www.eporner.com/video-Wyzh97cKNIY/bella-gets-told-do-and-spreads-em-bella-rolland-milan-ponjevic/`；它们不替换 YouTube 主路径，也不用于字幕测试。旧 `tkxzMEfp49Q` / `QBwpO9f0oAw` / `lcFR2mFSmSs` / `auNezUzwCZg` / `jWTrleK2_MU` 只保留为历史证据。为降低 429 风险，真实 connected 网络测试默认跳过，只有显式 `realYoutube=true` 才单项运行；真实字幕下载继续暂停，除非用户恢复并显式 `realYoutubeSubtitle=true`。真实分析/Shorts 抽样间隔至少 10 分钟，完整下载间隔至少 30 分钟；一旦出现 429，当天停止 YouTube 真实请求，改做单元、构建或非网络 UI 验证。

## 本轮已确认

- 当前分支：`feature/android-play-mvp-1`
- 当前远程同步提交：
  - `cf2d24a android: gate subtitle real smoke while paused`
  - `7d37978 Clean merged Android download intermediates`
  - `792c737 Fix Android URL input state reset`
  - `e1e0ac1 android: improve queue progress feedback`
  - `4609fb1 android: record api35 clean download evidence`
  - `667df01 android: harden foreground url input`
  - `85650be android: record foreground smoke evidence`
  - `3d8a455 android: confirm history deletes`
  - `b3ccea3 android: harden export file probe`
  - `41f4a1f android: clarify queue progress state`
  - `67cf325 android: harden queue progress and output storage`
  - `24c1287 android: add export listing probe`
  - `5ff7449 android: clarify staged progress and export names`
- API37 模拟器进程存在：`Android Emulator - ytdl_api37_play_x86_64:5554`
- ADB 设备在线：`emulator-5554 device product:sdk_gphone16k_x86_64`
- 用户确认可删除旧测试视频后，API37 `/sdcard/Download` 顶层两个旧 `.mp4` 测试导出已删除，空间从约 `230M` 恢复到约 `909M`；最新 `app-debug.apk` 已重新安装成功并启动到 `com.garyapp.ytdl/.MainActivity`。当前复查空间约 `1.1G`。

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

上述 2026-06-21 connected 真实 URL 覆盖是历史证据。2026-07-09 起，后续 connected 真实网络用例已改为默认跳过，必须按新地址集和 429 节流规则显式单项运行。

2026-07-09 测试地址替换验证：

- 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`，确认 API37 `emulator-5554` 在线、Gboard 软键盘环境可用。
- 已运行 `cd android; .\gradlew.bat :app:compileDebugAndroidTestKotlin`，通过。
- 已运行 `cd android; .\gradlew.bat :app:testDebugUnitTest`，通过。
- 已运行 `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest"`，在未传 `realYoutube=true` 时 4 个真实 YouTube 用例全部 `SKIPPED`，没有触发真实网络请求。
- 已运行 `cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.SubtitleDownloadInstrumentedTest"`，在未传 `realYoutubeSubtitle=true` 时字幕下载专项 `SKIPPED`，没有触发真实字幕下载。
- 新增 `scripts/android_real_smoke.ps1` 作为辅助 smoke 入口；默认模式会跑环境、Android 单测、debug 打包和 connected 安全集，但不会触发真实 YouTube 网络请求。
- 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\android_real_smoke.ps1`，结果通过：`android_env.ps1`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:connectedDebugAndroidTest` 均 `BUILD SUCCESSFUL`；真实 YouTube 相关用例按默认规则 `SKIPPED`，脚本输出 `No real YouTube opt-in step was run.`。
- 本轮发现并修复 connected 设置页外观测试的可见性假失败：`颜色方案`摘要行新增 `ytdl-settings-appearance-summary`，测试滚到摘要行后再断言颜色方案更新；已单独运行 `YtdlAppUiTest#settingsAppearanceColorPresetsAreVisibleAndSummaryUpdates`，结果 `BUILD SUCCESSFUL`。
- 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\android_real_smoke.ps1 -SkipUnitTests -SkipAssemble -SkipConnectedSafe -RunRealAnalyze`，对新主地址 `https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G` 完成一次 API37 connected 真实分析；输出 `YTDL_ANALYSIS_SMOKE sdk=37 device=sdk_gphone16k_x86_64 title=KISSING YOUR BEST FRIEND tiktok challenge ! Part 5 🔥 formats=27 highest=1920 subtitles=0`，测试 `BUILD SUCCESSFUL`。
- 该真实分析已写入本地节流状态 `.qa-real-smoke/android-real-youtube-state.json`：`lastAnalysisUtc=2026-07-09T02:32:15.0384254Z`。后续至少 10 分钟内不再跑分析/Shorts；完整下载仍按 30 分钟间隔推进。
- 已重新安装当前 `android/app/build/outputs/apk/debug/app-debug.apk` 到 API37，并启动到 `com.garyapp.ytdl/.MainActivity`；adb 辅助截图保存到 `docs/qa/android-computer-use-20260709-new-urls/00-adb-launch-current-apk.png`。这只证明当前 APK 可启动，不替代 Computer Use 前台可视验收。
- 已验证字幕暂停硬拦截：运行 `scripts/android_real_smoke.ps1 -SkipUnitTests -SkipAssemble -SkipConnectedSafe -RunRealSubtitleDownload` 会在真实网络请求前以 `Real subtitle download is paused` 拒绝；只有用户明确恢复字幕真实测试后，才允许额外传入 `-AllowRealSubtitleDownload`。

2026-07-09 前台输入环境纠偏：

- 本轮复现确认 Computer Use 可以控制 `Android Emulator - ytdl_api37_play_x86_64:5554`，问题不是 Computer Use 不可用。
- API37 AVD 原 `hw.keyboard=yes` 时，Gboard 会进入实体键盘工具栏/浮动面板状态，长 URL 前台软键盘输入不稳定。
- 临时改为 `hw.keyboard=no` 并重启 API37 后，Computer Use 点击 URL 输入框可显示完整 Gboard 软键盘，未再出现中间遮挡输入框的 `Emulator` 大浮层。
- `scripts/android_env.ps1` 已改为对矩阵 AVD 写入 `hw.keyboard=no`，并继续设置 `show_ime_with_hard_keyboard=1` 与 Gboard；新增 `tests/test_android_env_script.py` 防止脚本回退到硬件键盘模式。
- 已运行 `.\.venv\Scripts\python.exe -m pytest tests\test_android_env_script.py -q -o cache_dir=.qa-real-smoke\pytest-cache`，通过。
- 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`，输出四个矩阵 AVD 均为 `hardwareKeyboard=no`，API37 在线且 `SOFT_KEYBOARD emulator-5554 showImeWithHardKeyboard=1 ime=Gboard`。
- 同轮随后重试 Computer Use 时，`node_repl`/Computer Use 运行核返回 `Transport closed`，因此本轮没有继续形成新的前台 YouTube GUI 分析或下载验收证据；这只记录工具层边界，不代表应用前台流程已通过或失败。

2026-07-09 前台输入环境续跑：

- 本轮按用户指定顺序先验证 `node_repl` 最小 smoke 成功：`{"ok":true,"cwd":"D:\\garyapp\\ytdl"}`；随后 `setupComputerUseRuntime` 和 `sky.list_apps()` 成功，计算器前台 `1 + 1 = 2` 冒烟通过。
- 启动 Android 前确认没有现存 `qemu-system-x86_64`/`emulator` 进程且 `adb devices` 为空；随后启动 `ytdl_api37_play_x86_64`，等到 `emulator-5554 boot_completed=1`，并用 Computer Use 将窗口移动到主屏幕右侧、竖屏完整可见。
- 本轮重新运行 `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`，输出 API37 在线、`SOFT_KEYBOARD emulator-5554 showImeWithHardKeyboard=1 ime=Gboard`，四个矩阵 AVD 仍为 `hardwareKeyboard=no`；当前 `app-debug.apk` 安装返回 `Success`，可见前台打开下载页。
- 点击 URL 输入框后，API37 仍先显示 Gboard 硬件键盘工具条而非完整键盘。辅助诊断显示 served view 是 `app:id/ytdl_url_input`、`mInputShown=true`、`hw.keyboard=no` 已生效，但系统仍枚举 `AT Translated Set 2 keyboard`；Gboard 可见菜单中存在 `Show on-screen keyboard (Alt+K)`。点击该可见菜单项后，完整 Gboard 按键区出现。
- URL 输入使用 Computer Use 点击可见 Gboard 按键完成，未使用 adb、剪贴板、硬件键或候选词。由于 Gboard 第一符号页的 `=\<` 是符号页切换键而不是等号键，本轮用可见退格删除分享参数，最终以前台输入的 canonical 地址 `https://youtu.be/lcFR2mFSmSs` 继续；UIAutomator 辅助树确认输入框文本精确为该值。
- 点击 `分析` 后，Computer Use 前台可见结果显示真实缩略图、标题 `KISSING YOUR BEST FRIEND tiktok challenge ! Part 5 🔥`、时长 `15:04`，格式摘要 `自动（推荐） · 1920p WEBM 需原生合并`；没有触发下载，没有选择字幕。
- 证据保存于 `docs/qa/android-computer-use-20260709-new-url-analysis/`：
  - `01-analysis-result.png` / `01-analysis-result.xml`
  - `02-soft-keyboard-url.png` / `02-soft-keyboard-url.xml`
- 本轮真实分析已把本地节流状态 `.qa-real-smoke/android-real-youtube-state.json` 更新为 `lastAnalysisUtc=2026-07-09T05:14:22.5937299Z`。该目录被 `.gitignore` 忽略，只作本机频率控制。
- 边界：这次补齐的是新主地址 Computer Use 前台输入和真实 GUI 分析证据，不是完整 GUI 下载、Shorts 抽样、通知拒权前台复核或最终 T12 通过。Gboard 菜单只用于恢复完整软键盘显示，没有用于替代文本输入；后续最终验收仍应尽量从完整键盘直接输入原始分享 URL，或明确记录 canonical URL 等价边界。

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

1. 除非相关代码、测试地址或输入环境继续变化，不需要重复跑 API37 模拟器 M9/T12 完整主路径；后续应保留当前证据并进入真机/发布前事项。
2. 系统软键盘拟真输入后续不能回退到后台写入、硬件键、剪贴板、候选词、自动补全、手写浮层或 Gboard 菜单作为验收。
3. 真实字幕下载继续暂停；恢复字幕下载测试必须等用户明确同意。
4. 等后续推进到真机阶段且小米 14 或同级 `arm64-v8a` 设备已连接时，再做 M10 真机验收。
5. Play 签名、隐私政策 URL、Data safety 和商店素材仍是后续发布前事项，不属于本轮 API37 模拟器前台验收完成的证据范围。

## 2026-07-06 队列进度修正

- 当前 Android 下载编排仍是串行：视频流 -> 音频流 -> 原生合并；不是并行下载。只有本任务确实选择了字幕文件时，队列才追加“字幕文件”阶段；无字幕任务不显示这一项。
- 每个真实下载任务写入 App 私有 `gui-downloads/task-时间戳-序号` 子目录，App 私有输出不会互相覆盖。导出建议文件名规则为：`清理后的标题-yyyyMMdd-HHmmss.ext`；其中标题会替换 `\ / : * ? " < > |` 和换行制表符、截断到 72 个字符，时间使用设备本地完成时间，扩展名沿用实际输出文件。使用系统保存器导出时，App 不默认覆盖已有文件；同名冲突由系统保存器提示用户或追加序号。
- 队列卡片已区分“当前阶段真实百分比”和“当前阶段进行中但百分比未知”：有真实百分比时显示确定进度；没有可靠百分比时显示进行中进度条，避免卡在 `0%` 造成误解。右侧百分比继续显示整个下载大项的估算进度。

本轮新鲜验证：

```powershell
cd android
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.activeQueueStageWithoutReliablePercentUsesIndeterminateProgress
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebug
```

结果：三项均 `BUILD SUCCESSFUL`。该代码修正小节当时未完成 Computer Use 前台点击验收；随后 2026-07-06 前台复测见下文。

## 2026-07-06 URL 输入浮层收敛

- 现场根因：API35 的 Gboard `Use stylus to write in text fields` 打开时，Computer Use 文本注入会触发手写输入浮层；`show_ime_with_hard_keyboard=0` 只能关闭普通软键盘，不能单独关闭该 Gboard 浮层。
- 代码修正：下载页 URL 输入框改为受控原生输入控件，并在 Android 14+ 对该输入控件关闭 `autoHandwriting`；保留原 `ytdl-url-input` 测试标签，并补充原生资源 id 兼容 UIAutomator。该代码修正降低输入法接管风险，但本轮证据不证明它可单独压制 Gboard 手写浮层。
- 测试环境：API35 已关闭 `show_ime_with_hard_keyboard` 和 Gboard `Write in text fields -> Use stylus to write in text fields`。
- Computer Use 前台可见复测：在上述测试环境和当前 APK 下，于 `Android Emulator - ytdl_api35_play_x86_64:5556` 点击 URL 输入框，输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`，屏幕未出现 Android 软键盘、候选栏或 Gboard 浮动工具条；仅出现 Android 粘贴提示 toast。toast 消失后的证据图：`docs/qa/android-computer-use-20260706/url-input-clean-api35.png`。

新鲜验证：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.urlInputDisablesAutoHandwritingOnAndroid14AndNewer
.\gradlew.bat :app:assembleDebug
```

结果：两项均 `BUILD SUCCESSFUL`。该小节只证明“测试环境配置 + 当前 APK”的 URL 输入浮层阻断已收敛，不等于完整下载链路通过。

## 2026-07-06 API35 历史输入真实合并下载抽样

本轮在 `Android Emulator - ytdl_api35_play_x86_64:5556` 上继续用 Computer Use 前台可见操作真实链接 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。

已观察到：

- URL 输入：点击 URL 输入框并写入测试地址，未出现 Android 软键盘、候选栏或 Gboard 浮动工具条。
- 真实分析：标题显示 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`，缩略图、时长 `08:02`、格式摘要可见。
- 下载配置：默认 `视频+音频`，格式摘要为 `自动（推荐） · 1080p MP4 需原生合并`。
- 真实下载：勾选授权确认后点击 `开始下载`，下载页显示 `正在下载视频...`，随后显示 `下载完成：merged-299-140.mp4`。
- 历史落库：历史页显示完成记录，入口包含 `打开 / 分享 / 导出 / 删除`；证据图：`docs/qa/android-computer-use-20260706/full-flow-history-api35.png`。

真实输出文件位于 API35 app 私有目录：

```text
files/gui-downloads/task-1783279356106-1/download-tkxzMEfp49Q-140-audio.m4a  7,806,830 bytes
files/gui-downloads/task-1783279356106-1/download-tkxzMEfp49Q-299-video.mp4  347,653,714 bytes
files/gui-downloads/task-1783279356106-1/merged-299-140.mp4                 355,645,249 bytes
```

边界：为了让底部导航完整可见，本轮中途调整并重新拉起过模拟器窗口；因此“下载完成后队列页完成态”未在同一运行时会话中完成前台证据采集。重启后队列页显示运行时空队列，历史页保留完成记录。下一轮需要在窗口完整可见状态下从下载开始前直接采集队列页进行中和完成态。

## 2026-07-06 Computer Use 五页前台导航复核

本轮重新用 Computer Use 激活 `Android Emulator - ytdl_api37_play_x86_64:5554`，前台点击底部五个页面按钮并逐页截图：

- `下载`：下载页显示已分析链接、真实预览图、标题/时长/格式摘要、保存位置、下载模式、授权确认和导出完成提示。
- `格式`：当前分析结果下显示 `视频+音频 / 仅音频 / 仅视频`，并按真实支持情况显示可选分辨率、灰显分辨率和 `需原生合并` 标签。
- `队列`：显示完成后的真实任务卡片、阶段条、绿色对勾和整体 `100%`。
- `历史`：显示真实完成记录，以及 `打开 / 分享 / 导出 / 删除` 入口。
- `设置`：显示默认保存位置、Cookies 文件、解析器版本、媒体处理能力、通知权限、隐私与授权说明、地址校验提示、外观与颜色。

截图证据已落盘：

- `docs/qa/android-computer-use-20260706/download.png`
- `docs/qa/android-computer-use-20260706/format.png`
- `docs/qa/android-computer-use-20260706/queue.png`
- `docs/qa/android-computer-use-20260706/history.png`
- `docs/qa/android-computer-use-20260706/settings.png`

边界：这次是前台可见 Computer Use 操作，截图文件是点击后同步拉取当前模拟器画面形成的辅助留痕；该项仅证明五页导航和页面状态可见，不替代最终 T12 系统软键盘拟真 URL 输入和全功能验收。

## 2026-07-06 Computer Use 真实下载、导出和打开复测

本轮继续用 Computer Use 在前台可见 API37 模拟器中跑真实链接 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。

已观察到：

- URL 进入输入框后点击 `分析`，页面显示真实缩略图、标题 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`、时长 `08:02`，格式摘要为 `自动（推荐） · 1080p MP4 需原生合并`。
- 点击授权确认和 `开始下载` 后，下载页立即显示 `正在下载视频...` 和 `下载中`，不是无反馈。
- 队列页显示真实任务、真实缩略图、阶段条 `下载视频 / 下载音频 / 原生合并`。
- 队列进度不是从 0 直接跳到 100：前台观察到整体进度约 `2%`、`8%`、`31%`，视频阶段字节数从约 `20.9 MB / 331.5 MB` 推进到约 `313.8 MB / 331.5 MB`。
- 视频阶段结束后，`下载视频` 显示绿色对勾，当前阶段切到 `下载音频`，整体进度显示约 `66%`。
- 任务完成后，阶段条显示 `下载视频✓ / 下载音频✓ / 原生合并✓`，右侧 `100%`，输出文件为 `merged-299-140.mp4`，合并媒体大小约 `339.2 MB`。
- 历史页出现完成记录，显示 `打开 / 分享 / 导出 / 删除`。
- 点击 `导出` 打开系统保存器，默认文件名为视频标题加完成时间，不再只是 `merged-299-140.mp4`；点击 `SAVE` 后返回历史页。
- 辅助核对 `/sdcard/Download` 已产生导出文件：`Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season-20260705-175152.mp4`，大小约 `339M`。
- 点击历史 `打开` 调起系统播放器，等待后看到篮球视频画面，证明合并文件可播放。

本轮产生的测试文件：

- App 私有目录：`files/gui-downloads/task-1783273701697-1/download-tkxzMEfp49Q-299-video.mp4`，约 `332M`。
- App 私有目录：`files/gui-downloads/task-1783273701697-1/download-tkxzMEfp49Q-140-audio.m4a`，约 `7.4M`。
- App 私有目录：`files/gui-downloads/task-1783273701697-1/merged-299-140.mp4`，约 `339M`。
- 外部下载目录：`/sdcard/Download/Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season-20260705-175152.mp4`，约 `339M`。

边界：

- 本轮 URL 输入先尝试 `Ctrl+V`，未落入输入框；随后用 Computer Use 直接文本输入成功，但 Android 输入法浮层短暂出现。它证明真实功能链路可运行；按 2026-07-06 最新口径，后续需要用当前 APK、系统软键盘和前台可见操作重测输入链路。
- 本轮未点击 `删除` 的确认删除按钮；后续 M9.4 已在用户明确授权后只确认删除测试历史记录。
- 本轮未选择 `cookies.txt`；后续 M9.4 已用合成测试 `cookies.txt` 完成系统文件选择器和“仅保存引用”前台复核。

## 2026-07-06 外部导出识别辅助测试收敛

本轮没有重复下载或导出 355MB 主视频；只修正外部导出写出后的自动识别辅助逻辑。旧识别方式按 `ytdl-export-*` 前缀找变体，目录里已有同前缀副本时可能把“已写出”误判为未识别。现在测试侧支持按精确文件名识别当前导出文件，目录里存在同前缀 `(1)` 副本时仍能找到期望文件。

本轮验证使用 5 字节小文件写入 `/sdcard/Download`，只证明“系统下载目录 listing 识别函数可靠”，不把它写成真实下载或最终导出 GUI 验收。

```powershell
cd android
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.storage.ExternalDownloadProbeInstrumentedTest"
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebug
```

结果：三项均 `BUILD SUCCESSFUL`；connected 探针 4/4 通过。该项仍是 adb/UIAutomator 辅助证据，不替代最终 Computer Use 前台可见全流程验收。

## 2026-07-06 历史删除确认收敛

本轮修复历史页删除行为：点击历史记录的 `删除` 不再直接删除 Room 记录，而是先显示确认对话框；取消后记录必须保留，确认后才执行删除。

测试策略边界：

- 单元层新增保护：`YtdlApp` 必须存在 `pendingDeleteHistoryItem`、`AlertDialog`、`ytdl-history-delete-dialog`、`ytdl-history-delete-confirm` 和 `ytdl-history-delete-cancel`。
- connected 辅助测试新增 `YtdlAppUiTest#historyDeleteRequiresConfirmationForInsertedTestRecord`：只插入一条 `UITEST_DELETE_CONFIRM_*` 测试历史，并按该记录 id 的按钮 tag 定位删除入口，验证点击删除后不会直接删除、取消后保留、确认后只删除该测试记录。
- connected 测试启动前不再调用 `clearHistoryRows()`，避免清空真实历史记录；新增单元测试保护不再出现遍历删除所有历史行的危险清理模式。
- Computer Use 前台可见验证先完成“历史页测试记录可见 -> 点击删除弹出确认框 -> 点击取消后记录保留”；随后在用户明确同意删除 `UITEST_FOREGROUND_DELETE_M9_4` 后，确认删除该测试记录并验证历史页回到空状态。该路径只删除测试历史记录，不删除媒体文件；本项仍不写成最终 T12 通过。
- 本轮临时插入的 `UITEST_VISIBLE_DELETE_*` 前台测试记录已通过精确前缀清理，未触碰真实下载历史。

本轮新鲜验证：

```powershell
cd android
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests "com.garyapp.ytdl.ui.DownloadUiBridgeTest.connectedUiTestDoesNotClearAllHistoryRowsBeforeLaunch" --tests "com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyDeleteRequiresUserConfirmationDialog"
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#historyDeleteRequiresConfirmationForInsertedTestRecord"
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebug
```

结果：

- 目标单测通过。
- API37 connected 辅助测试 1/1 通过。
- 全量 `:app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL`。
- 前台可见 Computer Use 已确认弹窗、取消保留和用户授权后的测试记录确认删除。

## 2026-07-06 M9.4 前台隐私动作复核

本轮使用单个前台可见 API37 模拟器窗口，并通过 Computer Use 操作真实 GUI。

已完成：

- 历史页测试记录 `UITEST_FOREGROUND_DELETE_M9_4` 可见。
- 点击 `删除` 后出现 `确认删除历史记录` 对话框，文案说明不会删除已保存的媒体文件。
- 点击 `取消` 后测试记录仍保留。
- 在用户明确同意后，确认删除该测试记录；历史页显示 `暂无真实历史记录`。
- 设置页点击 `Cookies 文件` 选择入口，系统文件选择器打开。
- 选择合成测试文件 `cookies.txt` 后，设置页只显示 `cookies.txt · 仅保存引用`，未显示 cookies 内容。

证据：

- `docs/qa/android-computer-use-20260706-m9-privacy-actions/history-delete-cancel-record-remains.png`
- `docs/qa/android-computer-use-20260706-m9-privacy-actions/history-delete-dialog-visible.png`
- `docs/qa/android-computer-use-20260706-m9-privacy-actions/history-delete-confirm-record-removed.png`
- `docs/qa/android-computer-use-20260706-m9-privacy-actions/settings-cookies-reference-only.png`
- 同目录保存了对应 `*.xml` 界面树。

新鲜验证：

```powershell
cd android
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebug
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#seedForegroundDeleteRecordWhenExplicitlyRequested"
```

结果：三项均 `BUILD SUCCESSFUL`。connected 检查以无参数方式运行种子夹具，确认默认不会插入测试历史记录，同时覆盖 androidTest 编译运行。

边界：cookies 文件为本轮合成测试文件，仅用于验证系统文件选择器和“只保存引用”的 UI 行为；未读取、显示、记录或持久化 cookies 内容。M9.4 补齐历史确认删除和 cookies 选择前台路径，但最终 T12 仍需更多失败恢复路径和一次完整全量前台回归。

## 2026-07-06 M9.5 缺失输出恢复和未知进度反馈

本轮没有重复下载或导出 339M/355M 大文件；只覆盖一个非破坏性失败恢复路径，并修正队列未知百分比阶段的视觉反馈。

修复内容：

- 历史页现在会渲染运行提示卡。历史记录的 app-private 输出文件缺失时，点击 `打开`、`分享`、`导出` 都会在历史页可见显示：`历史记录对应的本地文件不存在或为空，请重新下载或删除该记录。`
- 缺失输出提示改为动作无关，不再在 `打开` 或 `分享` 时显示“不能导出”，也不暴露本地路径或 cookies 文件名。
- 队列页当前阶段没有可靠百分比时，进度条改为动态进行中条；右侧百分比仍表示整个下载大项的估算进度。

前台可见验证：

- 设备：`Android Emulator - ytdl_api37_play_x86_64:5554`。
- 数据：只插入一条合成历史记录 `UITEST_MISSING_OUTPUT_M9_5_*`，指向不存在的 app-private 输出文件。
- 操作：用 Computer Use 在前台可见历史页依次点击 `打开`、`分享`、`导出`。
- 结果：三次操作均停留在历史页，并显示上述中文恢复提示；没有打开空白窗口、系统分享器或保存器，也没有崩溃。
- 清理：前台验证后用精确前缀清理 `UITEST_MISSING_OUTPUT_M9_5_*` 测试历史记录，未触碰真实下载历史。

证据：

- `docs/qa/android-computer-use-20260706-m9-missing-output/01-history-missing-output-recovery.png`
- `docs/qa/android-computer-use-20260706-m9-missing-output/01-history-missing-output-recovery.xml`

新鲜验证：

```powershell
cd android
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyPageRendersRuntimeRecoveryMessage --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.missingHistoryOutputMessageIsActionNeutralAndPathFree --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.indeterminateQueueProgressUsesAnimatedInProgressBar --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.activeQueueStageWithoutReliablePercentUsesIndeterminateProgress
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#historyMissingOutputActionsShowVisibleRecovery"
```

结果：两项均 `BUILD SUCCESSFUL`。另外，前台 seed 和 cleanup 夹具均已通过。该小节仍是失败恢复和 UI 反馈修复，不等于最终 T12。

## 2026-07-06 M9.6 导出取消恢复

本轮不重复下载或导出大文件，只使用 4 KB app-private 测试输出验证系统保存器取消路径。

前台可见验证：

- 设备：`Android Emulator - ytdl_api37_play_x86_64:5554`。
- 数据：显式 seed 夹具创建 `files/gui-downloads/task-export-cancel-m9-6/export-cancel-test.mp4`，大小 4096 bytes，并插入一条 `UITEST_EXPORT_CANCEL_M9_6_*` 合成完成历史记录。
- 操作：用 Computer Use 在前台可见历史页点击该记录的 `导出`，系统 `Downloads` 保存器打开后，用模拟器可见 Android 返回键取消。
- 结果：应用返回历史页，并显示 `未获得保存位置授权，导出已取消。请重新选择保存位置。`；没有写出测试媒体文件，没有崩溃。
- 清理：显式 cleanup 夹具用 `HistoryDao.deleteByTitlePrefix("UITEST_EXPORT_CANCEL_M9_6_")` 精确清理测试历史，并删除 `task-export-cancel-m9-6` 测试目录。

证据：

- `docs/qa/android-computer-use-20260706-m9-export-cancel/01-history-export-cancel-visible.png`
- `docs/qa/android-computer-use-20260706-m9-export-cancel/01-history-export-cancel-visible.xml`

边界：这只证明“用户取消系统导出保存器”可以恢复并提示，不证明真实大文件导出写出；真实导出写出已有历史证据，最终 T12 仍需一次 coherent 全量主路径复核。

## 2026-07-06 M9.7 通知前台可视验收

本轮在 API37 可见模拟器窗口里继续 T7 通知路径，不把 connected/adb 作为最终通知验收。

关键过程：

- 初次下拉通知栏时未看到 YTDL 通知；辅助诊断显示 `com.garyapp.ytdl` 通知权限为 `importance=NONE`，所以该状态不能证明通知功能失败。
- 测试环境恢复 `POST_NOTIFICATIONS` 后，前台通知栏出现 `YTDL 下载任务 · 下载完成`。
- 重新从下载页开始一条真实 `视频+音频` 任务，下拉系统通知栏后看到 `YTDL 下载任务 · 正在下载视频`。
- 展开通知后可见阶段文案和 `取消` 操作；点击通知内 `取消` 后，通知变为 `已取消`，回到应用后下载页显示 `下载已取消。`。
- 本轮产生的 app 私有测试输出已清理：`files/gui-downloads` 已为空。

证据：

- `docs/qa/android-computer-use-20260706-m9-notification/01-notification-complete-visible.png`
- `docs/qa/android-computer-use-20260706-m9-notification/01-notification-complete-visible.xml`
- `docs/qa/android-computer-use-20260706-m9-notification/02-notification-running-expanded.png`
- `docs/qa/android-computer-use-20260706-m9-notification/03-notification-canceled.png`
- `docs/qa/android-computer-use-20260706-m9-notification/03-notification-canceled.xml`
- `docs/qa/android-computer-use-20260706-m9-notification/04-app-canceled-after-notification.png`
- `docs/qa/android-computer-use-20260706-m9-notification/04-app-canceled-after-notification.xml`

边界：这是通知允许状态下的前台可见通知链路验证；通知拒权状态下 app 内队列仍可见已有 connected 辅助证据。该小节仍不是最终 T12。

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

Computer Use 当时复核：

- 本轮再次尝试 Computer Use 时，mcp__node_repl.js 连最小 `nodeRepl.write(...)` 都失败，错误为 windows sandbox failed: helper_unknown_error: apply deny-read ACLs。
- 因此当时阻断比上一条记录中的“窗口激活失败”更早：Computer Use 入口本身没有稳定启动。
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

本轮按当时旧口径复核前台可视测试前置时发现：矩阵 AVD 的 `config.ini` 均为 `hw.keyboard=no`，点击 URL 输入框会触发 Android 输入法/工具浮层。该段保留为历史记录；2026-07-06 用户已把最终验收口径调整为必须使用系统软键盘拟真输入。

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
- 按当时旧口径，复测期间没有弹出完整 Android 软键盘、候选词栏或 Gboard 菜单，但输入框左侧仍出现一个系统折叠小浮动按钮。该输入证据已被 2026-07-06 最新系统软键盘拟真口径取代。

当前边界：本轮已证明真实分析、真实分离流下载、原生合并、队列进度和历史落地在前台可见模拟器中跑通；但最终 T12 仍需从空白页面开始完整跑一次，包括系统软键盘拟真输入、格式选择、下载、队列、历史和设置。

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
- 在当时压制输入法的测试环境下，仅用 Computer Use 硬件按键重新输入 `https://www.youtube.com/watch?v=tkxzMEfp49Q`，未出现软键盘、候选栏或 Gboard 工具浮层。按 2026-07-06 最新口径，该记录只作为历史支持证据，后续最终验收需使用系统软键盘拟真输入。
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

- 该小节记录时未执行历史删除，因为需要用户明确确认删除动作；后续 M9.4 已在用户明确授权后只删除测试历史记录。
- 该小节记录时未选择 cookies 文件；后续 M9.4 已用合成测试 `cookies.txt` 完成系统文件选择器和“仅保存引用”前台复核。
- 未做小米 14 真机验收；当前 ADB 只检测到模拟器，且用户确认电脑暂不连接小米 14。
- 外部导出写出、历史打开播放、测试历史确认删除和合成 cookies 文件选择已在 2026-07-06 补充前台证据；仍需继续做更多失败恢复和最终 Computer Use 全量前台复测等剩余 M9/T12 项；视觉密度审计见后续小节。

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
- 本轮没有重新执行破坏性历史删除、cookies 文件选择、外部导出写出、通知/取消前台路径；其中测试历史确认删除和合成 cookies 文件选择已在后续 M9.4 补齐。
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

根据前台观察反馈，本轮确认当前 MVP1 下载链路不是并行下载视频流和音频流：`DownloadPipeline` 对视频+音频合并任务按“下载视频 -> 下载音频 -> 原生合并”串行执行。队列页已改为显示分阶段状态，避免用户看到进度条从 `0%` 直接跳到 `100%` 或误把视频阶段进度理解为全任务进度；“字幕文件”只在该任务已选择字幕输出时追加显示。

本轮 UI 规则：

- 已完成阶段显示绿色 `✓`。
- 正在进行阶段使用灰色加粗和下划线。
- 阶段进度条对应当前阶段。
- 右侧百分比对应整个下载大项的估算进度。

同名输出处理也已收敛：App 私有下载目录继续使用每任务唯一目录避免内部串档；历史页导出时，系统保存对话框默认文件名改为 `清理后的标题-yyyyMMdd-HHmmss.ext`，避免多个导出任务都显示 `merged-299-140.mp4`。外部覆盖仍不作为默认行为；覆盖应由用户在系统保存器或后续明确选项中确认。

Computer Use 边界：本轮重新连接后可以枚举窗口并被动截图模拟器，但对窗口点击/按键仍报 `failed to activate captured window`，所以本节不是最终前台可视验收通过记录。进一步跨窗口 smoke 显示，不只是 Android Emulator，多个普通 Windows 窗口的 `activate_window` 也返回同一错误；当时阻断位于 Computer Use/Windows 窗口激活链路，而不是 App 代码或模拟器内页面。按 2026-07-06 最新口径，后续可视 URL 输入必须先由 Computer Use 正常聚焦输入框，并通过系统软键盘拟真输入；剪贴板、硬件键或可访问性写入只能作为环境诊断证据，不能替代最终验收。若 Computer Use 仍不能激活窗口，必须先修测试环境。

## 2026-07-05 通知拒权时 app 内进度补强

本轮新增 `YtdlAppUiTest.notificationPermissionDeniedStillShowsInAppProgress`，用真实链接 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 覆盖 Android 13+ 通知权限被拒绝后的应用内状态：

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#notificationPermissionDeniedStillShowsInAppProgress"
```

结果：API37 `ytdl_api37_play_x86_64(AVD) - 17` 上 1/1 通过，`BUILD SUCCESSFUL in 3m 25s`。

覆盖内容：

- 通过系统权限命令拒绝 `POST_NOTIFICATIONS` 后重启前台页面。
- 设置页显示 `未授权 · 下载仍在应用内显示进度` 和 `请求`。
- 启动真实 1080p 视频+音频下载后，队列页显示真实任务卡与阶段条。
- 阶段条包含 `下载视频`、`下载音频`、`原生合并`；未选择字幕时不显示 `字幕文件` 阶段。
- 从 app 内队列点击 `取消` 后，Room 最新历史进入 `canceled`，不允许写成 completed。
- 测试结束恢复通知权限，避免污染后续通知栏测试。

边界：这是 connected/UIAutomator 辅助证据，不替代最终 Computer Use 前台可见全量验收。拒绝通知权限时不要求通知抽屉显示 `YTDL 下载任务`；验收重点是 app 内队列状态仍可见、可理解、可取消。

## 2026-07-05 App 私有输出目录和取消触控补强

本轮全量 `YtdlAppUiTest` 初次复跑时发现真实大文件下载可能被 Android 清理：日志显示 App `cache/gui-downloads/...` 已用约 955MB、超过当前缓存配额后被 `installd` 清理，导致长视频完成链路超时。根因不是下载/合并能力失败，而是把用户下载输出放进了系统可主动回收的 cache 目录。

修复：

- GUI 下载输出根目录从 `context.cacheDir/gui-downloads` 迁到 `context.filesDir/gui-downloads`。
- FileProvider 路径从 `<cache-path>` 同步改为 `<files-path>`。
- 为迁移前已存在的历史记录保留只读 legacy cache fallback：新下载不再写 cache；旧 `app-private://outputs/...` 历史只有在旧 cache 文件仍存在时才可继续打开、分享或导出。
- 新增单元保护 `ytdlAppStoresLargeDownloadsOutsideCacheDirectory`，避免后续回退到 cache。
- 队列页 `取消` 从小文字点击区改为最小 `56dp x 36dp` 的触控目标，新增 `queueCancelActionUsesStableTouchTarget` 保护测试。

本轮顺序验证：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.data.HistoryPrivacyTest.appPrivateDiscoveryCanReadLegacyCacheHistoryWhenFilesRootMisses --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.ytdlAppStoresLargeDownloadsOutsideCacheDirectory --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueCancelActionUsesStableTouchTarget
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueCancelActionUsesStableTouchTarget
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#notificationPermissionDeniedStillShowsInAppProgress"
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：

- 取消触控保护测试先红后绿，最终通过。
- legacy cache 历史 fallback、filesDir 输出目录和取消触控目标测试通过。
- `:app:testDebugUnitTest`：`BUILD SUCCESSFUL in 18s`。
- `:app:assembleDebug`：`BUILD SUCCESSFUL in 4s`。
- 通知拒权目标 connected：API37 1/1 通过，`BUILD SUCCESSFUL in 3m 16s`。
- 全量 `YtdlAppUiTest`：API37 8/8 通过，`BUILD SUCCESSFUL in 15m 3s`。

Computer Use 边界：本轮 Computer Use 可以连接并被动截图 `Android Emulator - ytdl_api37_play_x86_64:5554`，但 `activate_window` 仍返回 `failed to activate captured window`。因此以上仍是单元、构建和 connected/UIAutomator 辅助证据，不能写成最终前台可视验收通过。按 2026-07-06 最新口径，后续可视输入 URL 时必须使用系统软键盘拟真输入，不能用后台写入替代。

## 2026-07-06 API35 前台可视短视频 smoke

本轮按用户反馈继续收敛队列页：视频+音频高分辨率下载当前不是并行链路，而是“下载视频 -> 下载音频 -> 原生合并”串行执行；队列页必须把基础阶段直接展示出来，若本任务选择了字幕文件，则追加显示“字幕文件”阶段。右侧百分比表达整个任务估算进度，下面进度条表达当前阶段进度。队列卡片不再显示内部 `merged-xxx.mp4` 文件名，改为说明 App 私有目录和导出建议名规则。

验证范围：

- 运行环境：前台可见 `Android Emulator - ytdl_api35_play_x86_64:5556`，Computer Use 操作真实 GUI。
- 测试地址：`https://youtu.be/QBwpO9f0oAw`。
- 操作覆盖：下载页输入 URL、分析、预览图和标题显示、授权确认、开始下载、切换队列、观察分阶段进度、等待原生合并完成。
- 输入边界：未打开 Android 软键盘、候选栏或 Gboard 菜单。

本轮观察到：

- 分析后显示标题 `Luka and Jalen 🤝`、时长 `00:14`，格式显示 `自动（推荐） · 1280p MP4 需原生合并`。
- 队列页下载中可见阶段条：`下载视频 ✓`、`下载音频`、`原生合并`；完成后三个阶段均显示绿色完成状态。
- 完成卡片显示文件大小、App 私有目录和导出建议名规则，没有暴露内部 `merged-...` 文件名。
- 同轮日志未发现 `AndroidRuntime`、`FATAL EXCEPTION`、`ComposeInternal`、`LayoutNode should be attached`、`Force finishing` 或进程退出。

辅助验证：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

结果均为 `BUILD SUCCESSFUL`。

残留问题：

- API35 日志仍有 `ForegroundServiceTypeLoggerModule ... does not have any types`；APK manifest 和 package granted permissions 已确认包含 `dataSync` 前台服务类型和权限，但系统日志告警未消失，不能写成已修复。
- 成功合并后仍可见 `MPEG4Writer: Stop() called but track is not started or stopped`，当前未发现它导致输出失败，但需要后续单独调查。
- 这是阶段 smoke，不等同于最终全功能全量验收；最终验收仍必须回到前台可见模拟器，按下载、格式、队列、历史、设置完整路径跑完。

## 2026-07-06 API35 格式保持与中间流清理复测

本轮在最新提交 `792c737` 和 `7d37978` 后，用 API35 前台可见模拟器补做非最终回归：

- 测试地址：`https://www.youtube.com/watch?v=tkxzMEfp49Q`。
- URL 输入：用桌面剪贴板和 `Ctrl+V` 粘贴到下载页输入框，未出现 Android 软键盘、候选栏或 Gboard 菜单。
- 真实分析：标题、预览图、时长和格式摘要正常显示。
- 格式页：选择 `1080p` 并应用后，下载页分析结果没有被 TextWatcher 清空，格式摘要保持为高分辨率视频+音频原生合并路径。
- 真实下载：启动后队列页能看到真实阶段进度，先出现低百分比下载状态，再进入完成态。
- 输出检查：最新任务目录只保留合并文件 `files/gui-downloads/task-1783286879264-1/merged-299-140.mp4`，约 `339M`；成功合并后的分离视频流和音频流中间文件已清理。

新鲜验证：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

结果：两项均 `BUILD SUCCESSFUL`。

边界：这是 API35 前台真实回归，不是最终 T12 API37 验收。随后用户确认可以删除旧测试视频，API37 `/sdcard/Download` 顶层两个旧 `.mp4` 测试导出已清理，空间恢复到约 `909M`；最新 `app-debug.apk` 已安装成功，包信息显示 `targetSdk=37`，并已启动到 `com.garyapp.ytdl/.MainActivity`。后续 API37 前台可视验收必须基于这个最新安装状态继续。

## 2026-07-06 API37 音频 403 重试与最新 APK 复测

用户确认可以删除旧测试视频后，本轮清理了 API37 外部下载目录中的旧 `.mp4` 测试导出，并删除旧 app-private 失败任务目录；`/sdcard/Download` 顶层不再保留旧测试视频，当前空间约 `1.1G`。最新 `app-debug.apk` 已重新安装成功。

根因记录：

- 安装空间紧张和下载失败是两件事：旧测试视频导致安装最新 APK 一度空间不足；真实下载失败发生在安装后的音频流下载阶段。
- 失败复现：同一地址 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 的 1080p 视频流 `299` 已下载完成，随后音频流 `140` 报 `HTTP Error 403: Forbidden`；任务目录只留下 `download-tkxzMEfp49Q-299-video.mp4`。
- 对照验证：Windows 侧 `tools\yt-dlp.exe 2026.03.17 -f 140` 可下载同一音频格式，说明格式本身可用，Android 侧需要把 403 归类为可重试网络失败，而不是直接把整个任务判为合并/文件处理失败。

修复内容：

- Python 桥接层把 `HTTP Error 403` / `Forbidden` 归类为 `network`。
- Android 下载编排对单个 format 下载段做最多一次网络失败重试；已下载成功的视频段不重复下载，只重试失败的音频段。
- 新增单元测试覆盖“视频成功、音频首次网络失败、仅音频重试一次、随后合并成功”。

新鲜验证：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadRequestRoutingTest.mergeRequiredRouteRetriesOnlyFailedFormatPartOnceForNetworkFailure
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadRequestRoutingTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

结果：四项均 `BUILD SUCCESSFUL`。

API37 前台可见复测：

- Computer Use 激活 `Android Emulator - ytdl_api37_play_x86_64:5554`，最新 APK 已安装并启动。
- `Ctrl+V` 和直接文本注入在 API37 上会触发 Gboard；随后为排查功能链路，曾用前台硬件键事件输入 URL。按 2026-07-06 最新拟真软键盘口径，该输入方式只作为历史支持证据，不计入最终输入验收。
- 真实分析成功，格式摘要为 `1080p MP4 需原生合并`。
- 队列页观察到真实阶段进度：约 `4%`、`15%`、`31%`，随后视频和音频阶段均变为完成，进入 `66%` 原生合并，最终 `100%`。
- 历史页显示最新完成记录，上一条保留为本轮修复前的失败记录，便于追溯。
- 最新输出目录只保留 `files/gui-downloads/task-1783289403701-1/merged-299-140.mp4`，大小约 `339M`；中间视频流和音频流文件已清理。

边界：这次已经证明最新 APK 可以安装，且 API37 前台真实 1080p 视频+音频下载、音频段重试、原生合并和历史落库可用；但它仍不是最终 T12 全功能通过。该小节记录时确认删除、cookies 文件选择和更多失败恢复前台路径尚未完成；其中确认删除和 cookies 文件选择已在后续 M9.4 前台复核补齐。

## 2026-07-06 M9.1 非破坏性失败恢复与输入边界

本轮按 M9.1 只补非破坏性路径，不执行历史确认删除，不要求真实 cookies 文件。

代码/测试层：

- 新增单元测试覆盖下载页 runtime message 必须显示空 URL、非法 URL、非 http/https URL 的中文错误提示。
- 同一测试断言错误提示不泄露 `token=secret`、host 或敏感输入片段。
- 按 2026-07-06 最新拟真输入口径，URL 输入框不再因模拟器/设备存在硬件键盘而压制系统软键盘；点击/聚焦输入框会主动请求系统输入法。单元测试已改为覆盖 `QWERTY`、`12KEY`、`NOKEYS`、`UNDEFINED` 配置下都允许软键盘弹出，并检查输入框包含主动请求系统输入法的代码路径。
- 失败文案链路继续通过 `UrlPolicy` 和下载页 runtime message 呈现安全错误。

前台可见验证：

- 设备：`Android Emulator - ytdl_api37_play_x86_64:5554`。
- 环境：API37 AVD 当前 `hw.keyboard=yes`；已设置 `show_ime_with_hard_keyboard=1`，Gboard 启用。
- 操作：安装最新 `app-debug.apk` 后启动 App，用 Computer Use 点击 URL 输入框，Android 系统软键盘真实弹出；随后只点击软键盘键位输入非法文本 `avx`，没有选择候选词、自动补全、手写浮层或 Gboard 菜单，再点击 `分析`。
- 结果：下载页显示 `分析失败：仅支持 http 或 https 开头的公开视频地址。`，`开始下载` 保持禁用；软键盘未遮挡错误提示。
- 队列页复核：显示 `暂无真实下载任务` / `尚未开始真实下载`，说明失败没有误加入下载队列。
- 截图证据：
  - `docs/qa/android-computer-use-20260706-m9-failure/04-soft-keyboard-invalid-url-error.png`
  - `docs/qa/android-computer-use-20260706-m9-failure/05-soft-keyboard-no-queue-task.png`

输入边界：

- 旧的硬件键输入 `ftp://x.y` 证据已降级为历史支持，不再作为最终输入验收。
- 本轮已按最新口径完成软键盘拟真输入：系统键盘可见、文本真实进入 URL 输入框、未选择候选词、错误提示可见、队列无真实任务。
- 小屏键位坐标较密，本轮原计划输入 `abc`，实际点击得到 `avx`；该文本仍是有效的非法 URL 测试输入，且未通过候选词改写。

新鲜验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:testDebugUnitTest --tests "com.garyapp.ytdl.ui.DownloadGuiBindingTest"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

结果：均 `BUILD SUCCESSFUL`。Gradle 10 兼容性提示来自既有 Chaquopy 依赖声明，不是本轮改动引入。

边界：M9.1 补齐了一个可恢复失败场景；确认删除和 cookies 文件选择已在后续 M9.4 补齐。最终 T12 仍需更多失败恢复路径，以及完整主路径的一次全量前台复测。

## 2026-07-06 M9.2 系统软键盘主路径分析和格式页确认

本轮按最新真机拟真口径继续补 T12 输入链路，但不重复执行长下载，不执行破坏性历史删除，也不要求真实 cookies 文件。

前台可见验证：

- 设备：`Android Emulator - ytdl_api37_play_x86_64:5554`。
- 环境：API37 AVD 当前 `hw.keyboard=yes`；`android_env.ps1` 已确认 `showImeWithHardKeyboard=1` 且输入法为 Gboard。
- 安装：当前 `app-debug.apk` 已重新安装并启动到 `com.garyapp.ytdl/.MainActivity`。
- 输入：用 Computer Use 点击 URL 输入框，Android 系统软键盘真实弹出；随后只点击软键盘键位输入完整 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。系统文本树确认输入框完整文本为该 URL。
- 输入边界：输入过程中没有选择候选词、自动补全、手写浮层或 Gboard 菜单。重新聚焦时候选栏显示建议词，但未被点击，系统文本树仍显示完整 URL 未被改写。
- 分析：点击 `分析` 后真实分析成功，下载页显示真实预览图、标题 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`、时长 `08:02`，格式摘要为 `自动（推荐） · 1080p MP4 需原生合并`。
- 格式页：前台切换到 `格式` 页后，2160p/1440p 按当前分析结果灰显并显示不可用原因，1080p/720p/480p/360p/240p 等按真实格式可见，1080p 标记为 `需原生合并`。

截图和文本树证据：

- `docs/qa/android-computer-use-20260706-m9-required-url/01-soft-keyboard-url-visible.png`
- `docs/qa/android-computer-use-20260706-m9-required-url/01-soft-keyboard-url-visible.xml`
- `docs/qa/android-computer-use-20260706-m9-required-url/02-analysis-result.png`
- `docs/qa/android-computer-use-20260706-m9-required-url/02-analysis-result.xml`
- `docs/qa/android-computer-use-20260706-m9-required-url/03-format-options.png`
- `docs/qa/android-computer-use-20260706-m9-required-url/03-format-options.xml`

新鲜验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
cd android
.\gradlew.bat :app:assembleDebug
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
```

结果：环境复查通过，`assembleDebug` 为 `BUILD SUCCESSFUL`，APK 安装返回 `Success`。本节只证明系统软键盘拟真输入后的真实分析和格式页联动，不计为完整 T12 下载验收；下载、队列、历史、设置和更多失败恢复仍需继续。该小节记录时确认删除和 cookies 文件选择仍未完成，二者已在后续 M9.4 补齐。

## 2026-07-06 M9.3 系统软键盘主路径下载、队列和历史复核

本轮继续使用 API37 前台可见模拟器窗口和 Computer Use。系统软键盘输入验收口径沿用 M9.2 已保存证据：M9.2 已证明 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 通过可见 Gboard 逐键输入且未被候选词、自动补全、手写浮层或 Gboard 菜单改写。M9.3 的独立保存证据从队列生命周期开始，不把本轮未落盘的输入过程另算为截图/XML 证据。

已观察到：

- 真实分析和下载承接 M9.2 的同一测试地址：标题为 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`，时长 `08:02`，下载页显示真实预览图和 `自动（推荐） · 1080p MP4 需原生合并`。
- 当前高分辨率 `视频+音频` 不是并行下载，而是串行执行：`下载视频 -> 下载音频 -> 原生合并`。
- 队列页真实进度不是从 `0%` 直接跳到 `100%`：前台截图记录了视频阶段约 `18.0 MB / 331.5 MB`、`156.7 MB / 331.5 MB`、`293.0 MB / 331.5 MB` 的推进。
- 保存下来的 M9.3 截图/XML 未单独覆盖音频下载中或原生合并进行中的瞬间；它们覆盖视频阶段推进和最终完成态。完成态显示 `下载视频✓ / 下载音频✓ / 原生合并✓`、`100%`。
- 完成卡片显示文件大小、App 私有目录和导出建议名规则。App 私有目录内最新合并文件为 `files/gui-downloads/task-1783294386576-1/merged-299-140.mp4`，大小约 `339M`。
- 历史页出现最新完成记录，包含 `打开 / 分享 / 导出 / 删除` 操作；设置页继续显示 cookies 只保存引用、媒体处理边界、通知权限和隐私说明。

截图和文本树证据：

- `docs/qa/android-computer-use-20260706-m9-download-mainpath/01-queue-video-progress.png`
- `docs/qa/android-computer-use-20260706-m9-download-mainpath/02-queue-video-progress-later.png`
- `docs/qa/android-computer-use-20260706-m9-download-mainpath/03-queue-video-progress-near-complete.png`
- `docs/qa/android-computer-use-20260706-m9-download-mainpath/04-queue-native-merge.png`
- `docs/qa/android-computer-use-20260706-m9-download-mainpath/05-queue-complete.png`
- `docs/qa/android-computer-use-20260706-m9-download-mainpath/06-history-complete.png`
- `docs/qa/android-computer-use-20260706-m9-download-mainpath/07-settings-boundaries.png`

边界：本节是系统软键盘拟真口径下的真实主路径阶段 smoke，仍不写成最终 T12 通过。该小节记录时确认删除和 cookies 文件选择仍未完成；二者已在后续 M9.4 用测试历史记录和合成 `cookies.txt` 补齐。更多失败恢复路径仍需补齐。历史页当前完成记录缩略图仍呈现为卡片占位图，后续若按设计图要求还原历史缩略图，需要单独修复和复测。

## 2026-07-06 历史页真实缩略图修复

本轮修复历史页完成记录只能显示固定渐变占位的问题：`VideoAnalysis.thumbnailUrl` 会随 `DownloadRequest` 进入终态历史记录，Room 从版本 1 迁移到 2 时新增 `thumbnailUrl` 字段；历史 UI 模型读取该安全缩略图引用后，历史卡片尝试加载真实图片，失败时继续回退到占位图。

安全边界：

- 只保存分析结果里的 http/https 缩略图 URL。
- 写入历史前会去除 query 和 fragment，并拒绝含 userinfo 或疑似 token/secret/signature/auth 主机名/路径片段的缩略图 URL，避免把令牌类信息带入历史。
- 不保存 Cookie、Authorization 或请求头。
- 旧历史记录可通过 Room 1 -> 2 迁移继续保留，新增缩略图字段为空时仍显示占位图。
- 历史卡片缩略图加载使用 64 条 LRU 缓存、有界线程池、采样解码和线程安全取消标记，不为每张卡片创建无限制原始线程。

新鲜验证：

```powershell
cd android
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyModelAndCardSupportRealThumbnails
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyModelAndCardSupportRealThumbnails --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyRowsPassThumbnailUrlToUiModel --tests com.garyapp.ytdl.download.DownloadHistoryRecorderTest.recordsSafeThumbnailUrlForCompletedHistory
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadHistoryRecorderTest.rejectsSensitiveThumbnailUrlPartsBeforeHistoryPersistence --tests com.garyapp.ytdl.download.DownloadHistoryRecorderTest.rejectsSensitiveThumbnailUrlHostBeforeHistoryPersistence --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyThumbnailLoadingUsesBoundedCacheInsteadOfPerCardRawThreads
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.data.YtdlDatabaseMigrationTest#migration1To2AddsThumbnailUrlWithoutDroppingHistoryRows"
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#historyCardLoadsThumbnailForInsertedTestRecord"
```

结果：上述验证均已跑通到 `BUILD SUCCESSFUL`。迁移测试创建旧版 SQLite 表后，由 Room 通过 `MIGRATION_1_2` 打开到 v2，并用 DAO 读取确认旧历史保留且 `thumbnailUrl` 为空。Connected 缩略图辅助测试只插入并清理一条 `UITEST_THUMBNAIL_*` 测试历史记录，不触碰真实下载历史。

截图证据：

- `docs/qa/android-history-thumbnail-20260706/11-history-thumbnail.png`
- `docs/qa/android-history-thumbnail-20260706/migration-connected-result.xml`
- `docs/qa/android-history-thumbnail-20260706/thumbnail-connected-result.xml`

边界：这是历史缩略图链路的辅助验证，不等于最终 T12 全功能前台验收通过。Computer Use 已确认当前只保留一个 API37 模拟器窗口；模拟器宿主窗口已恢复竖屏并移动到主屏幕可见区域。最终可视验收仍必须以可见窗口真实操作为准，不能用后台 connected 测试替代。

## 2026-07-06 M9.8 独立字幕文件用户可见闭环

本轮按 MVP1 边界补齐“合并后的视频音频文件 + 可选独立字幕文件”的历史/导出闭环，不涉及 FFmpeg、字幕嵌入、字幕烧录或三合一输出。默认主路径不下载字幕；只有当前视频分析结果确实提供字幕文件，且用户选择字幕时，才下载独立字幕文件。

代码与模型变化：

- Room 数据库升到 v3，`history_items` 新增 `subtitleOutputUris` 字段；`MIGRATION_2_3` 只追加列，旧历史保留。
- 完成任务写入历史时，媒体输出仍保存到 `outputUri`；字幕输出只保存为 `app-private://outputs/...` 安全引用列表，不保存本机绝对路径、cookies、敏感 URL query 或字幕文件内容。
- 历史页有字幕输出时，元信息显示“媒体文件 + 独立字幕文件”，并新增“分享字幕”“导出字幕”入口；无字幕记录仍只显示媒体动作，不出现字幕操作。
- 队列完成态有字幕输出时显示“媒体文件 + 独立字幕文件”，不把内部合并文件名或字幕文件名作为主要完成文案。

TDD 与验证：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadHistoryRecorderTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.data.YtdlDatabaseMigrationTest"
```

RED 结果：新增测试先失败于缺少 `subtitleOutputUris` / `MIGRATION_2_3`，以及队列完成态没有“媒体文件 + 独立字幕文件”元信息。GREEN 后上述两条命令均为 `BUILD SUCCESSFUL`；迁移测试在 API37 connected 环境覆盖 v1 -> v3 与 v2 -> v3。

前台可视复核：

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb install -r android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -e class com.garyapp.ytdl.ui.YtdlAppUiTest#seedForegroundSubtitleOutputRecordWhenExplicitlyRequested -e seedForegroundSubtitleOutput true com.garyapp.ytdl.test/androidx.test.runner.AndroidJUnitRunner
```

结果：在前台可见 API37 模拟器窗口中，用 Computer Use 打开历史页后，合成小型 app-private 历史记录显示“媒体文件 + 独立字幕文件”，并显示“分享字幕”“导出字幕”。点击“分享字幕”打开 Android 系统分享面板，只显示安全文件名 `subtitle-output.zh-Hans.vtt`，未显示 app 私有路径、cookies、Authorization、Bearer、URL query 或字幕内容。点击“导出字幕”打开 Android 系统保存器，底部显示安全建议文件名和 `SAVE` 按钮；取消后回到历史页并显示“未获得保存位置授权，导出已取消。请重新选择保存位置。”恢复提示。复核结束后已运行 `cleanupForegroundSubtitleOutputRecordsWhenExplicitlyRequested` 清理测试历史和私有测试文件夹。

截图证据：

- `docs/qa/android-computer-use-20260706-m9-8-subtitle-output/m9-8-subtitle-history-card.png`
- `docs/qa/android-computer-use-20260706-m9-8-subtitle-output/m9-8-subtitle-export-picker.png`

剩余边界：本节仍不是最终 T12。前台复核使用的是合成小型历史记录，证明历史页字幕入口、分享面板和保存器链路；真实带字幕下载从格式页选择字幕后观察队列、历史和字幕动作属于后续可选字幕专项，不是默认无字幕主路径的 T12 前置条件。最终仍需一次 coherent 全量主路径复核。

## 2026-07-06 M9.9 可选真实字幕链路限流与恢复策略修正

本轮用 API37 前台可见模拟器继续可选真实字幕链路复核。测试地址仍为 `https://www.youtube.com/watch?v=tkxzMEfp49Q`。前台操作已完成真实分析、格式页选择字幕、启动真实 `1080p` 视频+音频任务，并在队列页观察到真实视频阶段字节进度推进。任务实际生成了 app-private 媒体输出：

- 大小：`355645249` bytes

失败根因不是媒体下载或原生合并，而是最后下载 `zh-Hans` 自动字幕时，yt-dlp 收到 `HTTP Error 429: Too Many Requests`。这说明本轮真实字幕专项被 YouTube 字幕接口限流；不应继续反复下载 355MB 大文件来证明同一个问题。

本轮修正：

- 队列阶段规则明确为：基础阶段按实际下载路线显示；只有当前任务选择了字幕输出时才追加 `字幕文件` 阶段，未选择字幕的任务不显示这一项。
- 字幕选择规则明确为：只有当前分析结果提供字幕文件时，格式页字幕行才可选择；无分析或当前视频未提供字幕时显示不可选状态。
- 字幕下载失败时，失败态保留已经成功生成的媒体输出，不再让用户误以为整个媒体文件丢失。
- 字幕阶段失败文案改为 `所选字幕 zh-Hans 不可用，请取消字幕或重新分析后再试。`，不再笼统显示“当前地址或格式暂不支持”。
- 历史页对“失败但已有安全 app-private 媒体输出”的记录开放 `打开 / 分享 / 导出 / 删除`，但仍显示失败状态和字幕失败原因。
- 历史/队列元信息继续显示 `媒体文件`，不外显内部 `merged-299-140.mp4` 这类实现文件名。

新鲜验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadRequestRoutingTest.subtitleDownloadFailurePreservesCompletedMediaOutputAndNamesSubtitleProblem --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.failedSubtitleRecordWithMediaOutputKeepsMediaActionsOnly
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadRequestRoutingTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

结果：以上 JVM 测试、全量单测、debug 打包和 APK 安装均已通过。旧的 355MB 测试输出已按用户授权清理，避免污染后续安装空间。

截图证据：

- `docs/qa/android-computer-use-20260706-m9-9-real-subtitle/m9-9-real-subtitle-video-stage.png`
- `docs/qa/android-computer-use-20260706-m9-9-real-subtitle/m9-9-real-subtitle-audio-stage.png`
- `docs/qa/android-computer-use-20260706-m9-9-real-subtitle/m9-9-real-subtitle-subtitle-stage.png`
- `docs/qa/android-computer-use-20260706-m9-9-real-subtitle/m9-9-real-subtitle-failure.png`

边界：本节不是最终 T12，也不是“真实字幕下载成功”结论。它证明了真实媒体下载和合并已经完成、字幕失败根因为外部 429 限流，并完成了字幕失败后的媒体保留与用户提示修复。429 只影响可选字幕专项，不阻塞 MVP1 默认无字幕主路径；等限流消退后，可再用 Computer Use 在前台可见窗口复核真实字幕成功链路，确认历史页出现“媒体文件 + 独立字幕文件”和字幕导出/分享入口。

### 2026-07-07 字幕选择口径补充

用户补充澄清：MVP1 不强制下载字幕；只有当前视频本身提供字幕文件时，用户才应该能选择下载独立字幕文件。无字幕或未选择字幕时，不下载字幕文件，也不显示 `字幕文件` 进度小项。

本轮修正：

- 新增 `subtitleSelectionUiState`，把字幕行拆成明确 UI 状态：无分析为“先分析”、无字幕为“无可选”且不可点击、有字幕才显示“选择/取消”。
- 格式页字幕行接入该状态，无字幕时视觉上置灰且 `clickable` 禁用。
- 请求层原有校验继续保留：`selectedSubtitles` 必须来自当前分析结果。

新鲜验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.subtitleToggleIsAvailableOnlyWhenCurrentAnalysisProvidesSubtitles
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest --tests com.garyapp.ytdl.download.DownloadRequestRoutingTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

结果：以上相关测试、全量单测、debug 打包和最新 APK 安装均通过。Computer Use 已激活前台可见 API37 模拟器窗口并确认最新 APK 启动到下载页；该检查只证明安装启动状态，不等于最终 T12 全量验收。

429 复核：用 `--skip-download --write-auto-subs --sub-langs zh-Hans` 做轻量字幕探针，未下载视频，仍返回 `HTTP Error 429: Too Many Requests`；探针目录为空，没有留下字幕文件。该 429 只影响可选字幕专项，不阻塞默认无字幕主路径。

### 2026-07-07 暂停字幕下载测试

用户要求：暂时不测试下载字幕文件，避免继续触发 YouTube 429。

执行边界：

- 后续下载测试默认不选择字幕，不运行真实字幕下载、轻量字幕探针或前台字幕下载流程。
- T12 主路径继续覆盖真实分析、视频/音频分离下载、原生合并、队列、历史、导出、通知、设置和失败恢复。
- 字幕相关只保留 UI/请求校验：无字幕时不可选择；有字幕时才可选择，但当前默认不选择。
- 恢复字幕下载测试必须等用户明确同意。

### 2026-07-09 API37 无字幕主路径前台复核

本轮在左侧完整可见的 `Android Emulator - ytdl_api37_play_x86_64:5554` 中继续使用 Computer Use 前台操作当前 APK。模拟器状态为 `1080x2400 ROTATION_0`，URL 输入使用完整 Gboard 软键盘逐键输入，未使用 adb/剪贴板/硬件键写入。

测试地址：`https://www.youtube.com/watch?v=tkxzMEfp49Q`。

已观察到：

- 完整软键盘输入后，辅助 UI 树确认输入框真实内容为完整 URL。
- 真实分析成功，显示真实缩略图、标题 `Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season`、时长 `08:02` 和 `1080p MP4 需原生合并`。
- 点击开始下载后，队列页真实进度从视频阶段逐步推进：`33.2 MB / 331.5 MB`、`111.6 MB / 331.5 MB`、`215.7 MB / 331.5 MB`，不是 0 直接跳 100。
- 视频阶段完成后切到 `下载音频`；最终队列页显示 `下载视频✓ / 下载音频✓ / 原生合并✓`、`100%`、约 `339.2 MB / 339.2 MB`。
- 本轮未选择字幕；队列页未显示 `字幕文件` 阶段，符合“只有选择字幕时才显示字幕阶段”的当前口径。
- 历史页出现最新完成记录，显示真实缩略图、`视频299 + 音频140`、约 `339.2 MB`，并提供 `打开 / 分享 / 导出 / 删除`。
- 点击历史页 `打开` 后，系统播放器可播放合并后视频。
- 设置页可见默认保存位置、cookies 只保存引用、解析器版本、原生媒体处理能力、通知权限、隐私与授权说明、地址校验提示和外观颜色设置。
- 格式页复核显示 `2160p/1440p` 为“当前视频未提供”，`1080p/720p/480p/240p` 标记“需原生合并”，`360p` 标记“单文件”；字幕行显示“有 1099 个字幕可选；当前不下载”，未进入字幕选择。

证据目录：

- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/01-format-supported-options.png`
- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/01-format-supported-options.xml`
- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/02-queue-complete-nosubtitle.png`
- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/02-queue-complete-nosubtitle.xml`
- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/03-history-completed-record.png`
- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/03-history-completed-record.xml`
- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/04-settings-boundaries.png`
- `docs/qa/android-computer-use-20260709-mainpath-nosubtitle/04-settings-boundaries.xml`

边界：

- 本节证明当前 APK 的默认无字幕主路径已经用 Computer Use 前台可见方式跑通并补齐格式页、队列完成态、历史和设置证据。
- 真实字幕下载仍按用户要求暂停，不作为本轮阻塞项；恢复必须等用户明确同意。
- T12 仍需在最终 release gate 前汇总一次完整清单，确认 Shorts 兼容抽样、通知拒权前台复核、失败恢复覆盖和构建测试均为新鲜证据后，才能写成最终通过。

### 2026-07-09 通知拒权前台复核

本轮继续使用右侧完整可见的 `Android Emulator - ytdl_api37_play_x86_64:5554` 和 Computer Use。执行前已按要求完成 `node_repl` 最小 smoke、`sky.list_apps()` bootstrap，并确认计算器前台仍显示 `1 + 1 = 2`。

前置状态和前台观察：

- 辅助命令确认当前 App 通知权限为拒绝态：`adb shell appops get com.garyapp.ytdl POST_NOTIFICATION` 输出 `POST_NOTIFICATION: ignore`，`dumpsys package` 显示 `android.permission.POST_NOTIFICATIONS: granted=false`。
- Computer Use 在可见模拟器窗口中从下载页切到 `设置` 页；设置页 `通知权限` 行显示 `未授权 · 下载仍在应用内显示进度`，右侧提供 `请求` 入口。
- 本轮没有点击系统权限弹窗，也没有把通知权限改为允许；这是拒权可见状态和降级文案复核，不是通知允许路径或通知取消路径的重复测试。
- 证据保存于 `docs/qa/android-computer-use-20260709-notification-denied/`：
  - `01-settings-notification-denied.png`
  - `01-settings-notification-denied.xml`

边界：本节补齐通知拒权设置页前台复核；在本节完成时，T12 仍待新主地址完整 GUI 下载和 release gate 汇总。后续已在 2026-07-09 新主地址完整 GUI 下载小节补齐。

### 2026-07-09 Shorts 前台抽样与格式兼容修复

本轮继续使用右侧完整可见的 `Android Emulator - ytdl_api37_play_x86_64:5554` 和 Computer Use。执行前重新完成 `node_repl` 最小 smoke 与 `sky.list_apps()` bootstrap；计算器不再作为每次 Computer Use 前的必需步骤。

前台过程：

- 使用完整可见 Gboard 逐键输入 canonical Shorts 地址 `https://youtube.com/shorts/jWTrleK2_MU`，未使用 adb 文本注入、剪贴板、硬件键、候选词或自动补全替代输入；辅助 UI 树确认输入框内容为该完整 URL。
- 真实分析成功：标题为 `🚨THIS IS WHY The Celtics Won The Jaylen Brown-Paul George Trade #celtics #nba #chatsports`，时长 `00:57`，摘要最初显示 `自动（推荐） · 1920p MP4 需原生合并`。
- 本轮未选择字幕。点击开始下载后，队列页观察到真实视频阶段进度，例如 `4.0 MB / 13.3 MB`、`9%`。
- 随后任务失败，队列页显示 `下载失败：文件处理失败，请重试或选择其他格式。`。logcat 未见 429；app-private 任务目录显示已下载 `download-jWTrleK2_MU-137-video.mp4` 和 `download-jWTrleK2_MU-251-audio.webm`。

根因与修复：

- 根因是自动 `视频+音频` 路线为 MP4 原生合并选择了 MP4/AVC 视频 `137`，但同时选择了 WebM/Opus 音频 `251`；当前 MVP1 原生合并输出使用 Android `MediaMuxer` 写 MP4，不能把 WebM/Opus 音频直接封装进 MP4。
- `FormatSelection` 现在只把 MP4/AVC 视频和 M4A/MP4A 音频纳入 MP4 原生合并候选；单文件格式、纯视频和纯音频路线不受影响。
- 只提供 WebM/VP9 的高分辨率不再被描述成“当前视频未提供”，而是显示 `当前视频未提供可原生合并的 MP4 格式`。
- 用户前台复核指出 Shorts 竖屏视频的真实高度包含 `1920p/1280p/854p/640p/426p/256p` 等非固定横屏档位；格式页现在会合并固定常用分辨率和当前分析实际高度，避免 `自动（推荐）` 可用但下面每个固定行都像“未提供”。
- `DownloadGuiBindingTest` 的格式摘要测试数据也改为真实可合并的 MP4/AVC + M4A 组合，避免测试继续固化 WebM 合并假设。

新鲜验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.FormatSelectionModelTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.formatSettingSummariesComeFromSelectedFormats
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
.\.venv\Scripts\python.exe -m pytest tests\test_android_env_script.py -q -o cache_dir=.qa-real-smoke\pytest-cache
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

结果：以上格式选择模型测试、GUI 摘要回归、全量 debug 单测、debug 打包、环境脚本测试和修复后 APK 安装均已通过。

证据保存于 `docs/qa/android-computer-use-20260709-shorts-sample/`：

- `01-shorts-analysis.png`
- `01-shorts-analysis.xml`
- `02-shorts-queue-progress.png`
- `02-shorts-queue-progress.xml`
- `03-shorts-download-failed.png`
- `03-shorts-download-failed.xml`
- `04-shorts-fixed-url-entered.png`
- `04-shorts-fixed-url-entered.xml`
- `05-shorts-fixed-analysis.png`
- `05-shorts-fixed-analysis.xml`
- `06-shorts-fixed-format-rows.png`
- `06-shorts-fixed-format-rows.xml`
- `07-shorts-dynamic-format-rows.png`
- `07-shorts-dynamic-format-rows.xml`

修复后前台复核：

- 重新安装修复后的 `app-debug.apk` 后，Computer Use 再次通过可见 Gboard 输入 `https://youtube.com/shorts/jWTrleK2_MU` 并完成真实分析。
- 格式页现在显示 Shorts 真实高度行：`1920p`、`1280p`、`854p`、`640p`、`426p`、`256p` 等；`1920p`、`1280p`、`854p` 等可见为“需原生合并”，不再被固定横屏列表吞掉。
- 等待 30 分钟下载间隔后，前台启动无字幕短下载。队列显示视频阶段真实进度 `10.4 MB / 13.3 MB`，随后完成 `下载视频✓ / 下载音频✓ / 原生合并✓`、`14.2 MB / 14.2 MB`、`100%`。
- 辅助 app-private 目录确认新任务输出为 `files/gui-downloads/task-1783581536428-1/merged-137-140.mp4`；旧失败任务仍保留 `download-jWTrleK2_MU-137-video.mp4` + `download-jWTrleK2_MU-251-audio.webm` 作为对照。
- 历史页顶部显示新完成记录，元信息为 `视频 137 + 音频 140`，并提供 `打开 / 分享 / 导出 / 删除`；旧 `视频 137 + 音频 251` 失败记录留在下方。

新增修复后证据：

- `08-shorts-fixed-queue-progress.png`
- `08-shorts-fixed-queue-progress.xml`
- `09-shorts-fixed-queue-complete.png`
- `09-shorts-fixed-queue-complete.xml`
- `10-shorts-fixed-history-complete.png`
- `10-shorts-fixed-history-complete.xml`

边界：本节已补齐当前 Shorts 样本的前台可见无字幕成功下载和历史落地证据。为控制真实 YouTube 请求频率，本轮已把本地节流状态更新为 `lastAnalysisUtc=2026-07-09T07:10:15.7532328Z`、`lastDownloadUtc=2026-07-09T07:19:45.8766903Z`。真实字幕下载仍按用户要求暂停；在本节完成时，最终 T12 仍待新主地址完整 GUI 下载和 release gate 汇总，后续已在 2026-07-09 新主地址完整 GUI 下载小节补齐。

### 2026-07-09 Gboard 直接弹出环境修正

用户前台观察指出：点击 URL 地址框后，拟真手机输入应直接从底部弹出完整软键盘；如果只出现 Gboard 顶部工具条，还需要再点菜单，这不能算最终拟真输入通过。

本轮诊断结果：

- Android 应用侧已经正常请求输入法：辅助诊断显示 URL 输入框是 served view，`mInputShown=true`。
- AVD 配置已是 `hw.keyboard=no`，`show_ime_with_hard_keyboard=1` 也已写入；但系统仍枚举 `AT Translated Set 2 keyboard`，Gboard 自身仍可能按物理键盘偏好显示工具条。
- 在可见 Gboard 设置中确认并修正：`Write in text fields -> Use stylus to write in text fields = off`、`Physical keyboard -> Show on-screen keyboard = on`、`Show toolbar = off`。
- 修正后回到当前 APK 下载页，隐藏键盘后单击 URL 输入框，完整 Gboard 按键区直接从底部弹出，不再需要点击 `Show on-screen keyboard`。
- `adb root` 返回 `adbd cannot run as root in production builds`，`adb shell run-as com.google.android.inputmethod.latin ...` 返回 `package not debuggable`；因此 Gboard 私有偏好不能用稳定公开的 `settings put` 写入，`scripts/android_env.ps1` 只负责可脚本化的系统层检查和矩阵 AVD `hw.keyboard=no` 契约。

证据保存于 `docs/qa/android-computer-use-20260709-keyboard-direct-popup/`：

- `01-direct-gboard-popup.png`
- `01-direct-gboard-popup.xml`

边界：本节修正的是输入环境，不是新的 YouTube 下载验收。后续新主地址完整 GUI 下载必须从“点击地址框后直接出现完整 Gboard”这个环境状态继续，不再把先点 Gboard 工具条作为最终验收路径。

### 2026-07-09 新主地址完整 GUI 下载与 M9/T12 release gate

本轮继续使用右侧完整可见的 `Android Emulator - ytdl_api37_play_x86_64:5554` 和 Computer Use。执行前按要求完成 `node_repl` 最小 smoke 与 `sky.list_apps()` bootstrap；未再把计算器作为必需前置步骤。输入环境已先通过上一节 Gboard 直接弹出验证。

前台过程：

- 点击 URL 输入框后，完整 Gboard 直接从底部弹出；通过可见 Gboard 逐键输入完整分享地址 `https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G`，未使用 adb 文本注入、剪贴板、硬件键、候选词、自动补全或 Gboard 工具条替代输入。辅助 UI 树确认输入框文本精确为该完整 URL。
- 点击 `分析` 后，Computer Use 前台可见真实缩略图、标题 `KISSING YOUR BEST FRIEND tiktok challenge ! Part 5 🔥`、时长 `15:04`，格式摘要为 `自动（推荐） · 1080p MP4 需原生合并`。
- 格式页显示 `1080p/720p/480p/240p/144p` 为可用的 MP4 原生合并路线，`360p` 为单文件；`1920p/1280p/854p/640p/426p/256p` 等 WebM-only 高度显示 `当前视频未提供可原生合并的 MP4 格式`，不再被误认为可 MP4 原生合并。
- 字幕行显示 `当前视频未提供字幕` / `无可选`，本轮没有选择字幕，队列中也没有出现 `字幕文件` 阶段。
- 等待完整下载间隔超过 30 分钟后，前台点击 `开始下载`。下载页显示 `正在下载视频...`；队列页随后观察到真实任务从视频下载推进到音频下载和原生合并。任务很快切换阶段，保存的中途证据覆盖音频/合并阶段，队列完成态显示 `下载视频 ✓ / 下载音频 ✓ / 原生合并 ✓`、`101.5 MB / 101.5 MB`、`100%`。
- 历史页顶部出现完成记录，显示真实缩略图、`视频 137 + 音频 140 · youtube · 07/09 07:51 · 媒体文件`，并提供 `打开 / 分享 / 导出 / 删除`。
- 点击历史页顶部记录的 `打开` 后，系统视频查看器可播放合并后的 MP4；随后用可见返回箭头回到应用历史页。没有执行删除，没有发送分享，没有写出外部导出文件。

辅助证据：

- App-private 输出文件：`files/gui-downloads/task-1783583408203-2/merged-137-140.mp4`。
- 输出大小：`106421453` bytes。
- logcat 未发现 `429`、`Too Many Requests` 或下载失败；只见模拟器系统噪声和服务停止计时。
- 本地节流状态已更新为 `lastAnalysisUtc=2026-07-09T07:35:53.3385695Z`、`lastDownloadUtc=2026-07-09T07:50:37.8777089Z`。
- 收尾新鲜验证：`.\.venv\Scripts\python.exe -m pytest tests\test_android_env_script.py -q -o cache_dir=.qa-real-smoke\pytest-cache` 输出 `1 passed`；`D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest` 为 `BUILD SUCCESSFUL`；`D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug` 为 `BUILD SUCCESSFUL`；`git diff --check` 仅有换行风格 warning，没有实际空白错误。

证据保存于 `docs/qa/android-computer-use-20260709-primary-full-download/`：

- `01-primary-url-entered.png` / `01-primary-url-entered.xml`
- `02-primary-analysis-result.png` / `02-primary-analysis-result.xml`
- `03-primary-format-rows.png` / `03-primary-format-rows.xml`
- `04-primary-format-subtitle-state.png` / `04-primary-format-subtitle-state.xml`
- `05-primary-download-started.png` / `05-primary-download-started.xml`
- `06-primary-queue-inflight-progress.png` / `06-primary-queue-inflight-progress.xml`
- `07-primary-queue-merge-progress.png` / `07-primary-queue-merge-progress.xml`
- `08-primary-queue-complete.png` / `08-primary-queue-complete.xml`
- `09-primary-history-complete.png` / `09-primary-history-complete.xml`
- `10-primary-open-player.png` / `10-primary-open-player.xml`

Release gate 结论：当前 API37 模拟器前台 M9/T12 验收已补齐新主地址完整默认无字幕路径、当前 Shorts 样本、通知拒权可见状态、Gboard 直接弹出输入环境、格式兼容修复证据、队列/历史/打开链路，以及本轮新鲜单元测试、debug 打包和环境脚本验证。真实字幕下载仍按用户要求暂停；M10 真机验收、Play 签名、隐私政策 URL、Data safety 和商店素材仍是后续阶段，不属于本轮模拟器前台通过结论。

### 2026-07-09 历史页隐藏内部格式编号

用户复核指出：历史页 `视频 137 + 音频 140` 里的数字是 YouTube/yt-dlp 内部格式编号，只对调试和 QA 有意义，不应作为普通用户默认可见文案。
用户进一步确认：下载队列和历史卡片应在每行下载框右下角显示分辨率；历史卡片右上角保留状态徽标，`完成` 为绿色、`失败` 为红色，状态徽标和分辨率徽标大小一致并上下对齐。

本轮修正：

- 新建下载请求会保存用户可读的格式摘要，例如 `1080p MP4 需原生合并`；历史落库默认使用该摘要，不再直接拼接内部 format id。
- 已经写入数据库的旧历史记录在 UI 层兼容清洗：`视频 137 + 音频 140`、`视频 137 + 音频 251` 等旧摘要在历史页正文显示为 `视频+音频 · 原生合并`，内部编号仍保留在下载路线、输出文件名、测试断言和 QA 排障证据里。
- 审计补丁覆盖旧多字幕摘要：`视频 299 + 音频 140 + 字幕 en.vtt, zh-Hans.vtt` 也会清洗为用户可读正文并推断 `1080p`，不会把 `299/140` 泄露到历史卡片正文。
- 历史卡片新增独立 `formatBadge`：分辨率不再混在正文元信息里，而是显示在每张历史卡片右下角。旧 format id 仅作兼容推断，例如 `137/299` 映射为 `1080p`，`136` 映射为 `720p`；因此不是写死 `1080p`。
- 历史卡片状态徽标与分辨率徽标同尺寸上下对齐：`完成` 在右上角显示绿色，`失败` 在右上角显示固定红色，分辨率在右下角显示；失败红色不再依赖 `downloadAccent`，避免 Codex 配色下变成蓝灰/浅蓝。
- 队列真实任务卡也接入 `formatBadge`，从当前 `activeRequest.formatSummary` 或路线 format id 推断分辨率；音频-only 不显示分辨率。队列卡片右侧同样采用上状态、下分辨率的同尺寸徽标结构。当前没有新真实下载进行中，本轮不额外触发 YouTube 下载请求，队列右下角徽标以单元/源码测试覆盖，后续真实下载前台验收继续补截图。
- 该修正不触发新的 YouTube 网络请求，不改变原生合并路线，也不修改历史输出文件。

新鲜验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadHistoryRecorderTest.defaultHistoryFormatSummaryUsesUserReadableFormatDetails
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.download.DownloadHistoryRecorderTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyMetaHidesLegacyInternalFormatIds --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyFailureBadgeUsesRedAcrossColorPresets --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyModelAndCardSupportRealThumbnails --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueCardSourceRendersDedicatedFormatBadge --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueFormatBadgeUsesActiveRequestResolutionInsteadOfHardcodedValue
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
```

结果：以上 focused 测试、相关测试组、全量 debug 单测和 debug 打包均 `BUILD SUCCESSFUL`。已重新安装当前 `app-debug.apk`，并按项目要求先完成 `node_repl` 最小 smoke 与 `sky.list_apps()` bootstrap，再用 Computer Use 在可见 API37 模拟器窗口打开历史页复核。前台历史页显示三条旧记录正文均为 `视频+音频 · 原生合并 · youtube · ...`，未再显示 `137`、`140` 或 `251`；每张历史卡片右上角显示状态徽标，右下角显示 `1080p` 分辨率徽标，其中失败记录的状态徽标为红色。测试另覆盖旧 Shorts `视频 136 + 音频 140` 会显示 `720p`，防止回退为写死 `1080p`。

证据保存于 `docs/qa/android-computer-use-20260709-format-id-label/`：

- `01-history-format-summary.png`
- `01-history-format-summary.xml`
- `02-history-status-resolution-badges.png`
- `02-history-status-resolution-badges.xml`

用户随后复核指出状态徽标不能因新增分辨率徽标而缩小；右侧应形成上方状态、下方分辨率的对角信息组。代码已统一历史/队列右侧 `CardPillBadge` 最小尺寸为 `64dp x 30dp` 并使用 `labelMedium`，失败状态统一使用固定红色而不是 Codex/主题下载强调色。新鲜验证为：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueCardSourceRendersDedicatedFormatBadge
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
```

结果均为 `BUILD SUCCESSFUL`。当前 APK 已重新安装，Computer Use 前台复核历史页：`完成`/`失败` 状态徽标保持右上角且不再缩小，`完成` 为绿色、`失败` 为红色，分辨率徽标在右下角并与状态徽标同尺寸。未触发新的 YouTube 网络请求；队列页当前无真实运行任务，右侧徽标的真实进行中前台截图仍留到后续 M11 下载窗口补齐。截图追加保存于 `docs/qa/android-visual-fidelity-20260709-m11/`：

- `29-queue-badge-size-fix.png`
- `30-history-badge-size-fix.png`

用户继续要求历史/下载状态信息更稳定对应：状态徽标保持右上角，`完成` 为绿色、`失败` 为红色；分辨率保持右下角，和状态徽标同尺寸，形成上下对齐的信息组。本轮追加修复历史页控件密度：搜索框右侧新增圆形筛选 icon，历史行操作从裸文字改为 icon+text chip。为避免重复触发 YouTube 请求，本轮用显式 instrumentation 参数插入两条本地安全测试记录（一条完成 `720p`、一条失败 `1080p`），再通过 Computer Use 在可见 API37 模拟器前台打开历史页复核。

追加验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historySearchHeaderUsesRightSideFilterIconAffordance --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyActionsRenderAsIconTextChipsWithoutLosingCallbacks
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.settingsPrivacyLegalTextStatesConcreteBoundariesWithoutSecrets
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebugAndroidTest
D:\Softwares\Android\SDK\platform-tools\adb.exe shell am instrument -w -e class 'com.garyapp.ytdl.ui.YtdlAppUiTest#seedForegroundHistoryBadgeVisualRecordsWhenExplicitlyRequested' -e seedForegroundHistoryBadgeVisual true com.garyapp.ytdl.test/androidx.test.runner.AndroidJUnitRunner
D:\Softwares\Android\SDK\platform-tools\adb.exe shell am instrument -w -e class 'com.garyapp.ytdl.ui.YtdlAppUiTest#cleanupForegroundHistoryBadgeVisualRecordsWhenExplicitlyRequested' -e cleanupForegroundHistoryBadgeVisual true com.garyapp.ytdl.test/androidx.test.runner.AndroidJUnitRunner
```

结果：focused 测试、相关测试组、全量 debug 单测、debug APK 打包、androidTest APK 打包、显式本地种子 instrumentation 和清理 instrumentation 均通过。Computer Use 前台复核截图追加保存于 `docs/qa/android-visual-fidelity-20260709-m11/`：

- `31-history-status-resolution-paired-badges.jpg`

前台观察：历史页搜索框右侧已有圆形筛选 icon；失败记录右上为红色 `失败`，右下为 `1080p`；完成记录右上为绿色 `完成`，右下为 `720p`；打开/分享/导出/删除动作显示为带小图标的 chip。未触发新的 YouTube 网络请求；真实进行中队列右侧徽标截图仍留到后续下载窗口补齐。

### 2026-07-09 M11 视觉一致性审计与首轮修复

本轮开始执行 Continuation Task M11，不触发新的 YouTube 网络请求。已基于 `docs/android-gui-reference-v3.png` 创建截图审计目录：

```text
docs/qa/android-visual-fidelity-20260709-m11/
```

已完成：

- 用 Computer Use 在前台可见 API37 模拟器中采集当前 APK 的五页 `基准图配色` 截图。
- 滚动设置页后切换 `Codex 风格`，采集五页 Codex 配色截图。
- 写入 `docs/qa/android-visual-fidelity-20260709-m11/audit.md`，列出 M11 checklist、当前差距和后续优先级。
- 修复设置页外观配色区域底部留白，避免 `基准图配色` / `Codex 风格` 按钮贴近底部导航遮挡区。
- 底部导航从文本符号改为本地 vector 图标，选中态背景扩展到 icon+label 的 tab 区域。

新鲜验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.settingsAppearanceSectionKeepsExtraBottomScrollBuffer
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.bottomNavigationUsesVectorIconsInsteadOfTextSymbols
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
```

结果：以上 focused 测试、相关 UI 绑定测试、全量 debug 单测和 debug 打包均 `BUILD SUCCESSFUL`。已重新安装当前 `app-debug.apk` 并用 Computer Use 前台复核，修复后截图保存为：

- `17-download-after-nav-fix.png`
- `18-format-after-nav-fix.png`
- `19-queue-after-nav-fix.png`
- `20-history-after-nav-fix.png`
- `21-settings-after-nav-fix.png`
- `22-settings-appearance-buffer-after-nav-fix.png`
- `23-download-reference-after-nav-fix.png`
- `24-format-reference-after-nav-fix.png`
- `25-queue-reference-after-nav-fix.png`
- `26-history-reference-after-nav-fix.png`
- `27-settings-reference-after-nav-fix.png`
- `28-settings-reference-appearance-buffer-after-nav-fix.png`

边界：M11 尚未通过。仍缺真实下载进行中队列截图，队列/格式/历史页与基准图仍有密度和动作样式差距，Codex 配色还需要在后续截图审计中明确剩余偏差。M10 真机验收、Play 签名、隐私政策 URL、Data safety 和商店素材仍未开始。

### 2026-07-09 M11 格式空态和状态/分辨率徽标二次修正

本轮继续处理用户对下载状态徽标和格式页空态的反馈，不触发新的 YouTube 请求，不测试字幕下载。

代码变化：

- 格式页无分析时保留更接近基准图的信息密度：显示禁用的分辨率列表、帧率/视频编码/容器格式/字幕行、summary 和禁用的 `应用选择` 按钮；文案明确为“请先分析视频”或“分析后显示”，不伪造真实格式。
- 队列完成态右上角状态从 `100%` 改为 `完成`，失败态仍为 `失败`；完成用绿色，失败用固定红色。
- 队列和历史共用同一个右侧徽标列：状态在右上角，分辨率在右下角，两个 `CardPillBadge` 固定同尺寸，避免新增分辨率后状态徽标缩小或错位。

新鲜验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.terminalQueueCardPinsStatusAboveResolutionWithMatchedBadgeSize --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueTerminalStatusBadgeUsesCompactStateTextAndOutcomeColor --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.formatPageWithoutAnalysisKeepsDisabledReferenceStructure
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#seedForegroundHistoryBadgeVisualRecordsWhenExplicitlyRequested" "-Pandroid.testInstrumentationRunnerArguments.seedForegroundHistoryBadgeVisual=true"
D:\Softwares\Android\SDK\platform-tools\adb.exe shell am instrument -w -e class com.garyapp.ytdl.ui.YtdlAppUiTest#seedForegroundHistoryBadgeVisualRecordsWhenExplicitlyRequested -e seedForegroundHistoryBadgeVisual true com.garyapp.ytdl.test/androidx.test.runner.AndroidJUnitRunner
D:\Softwares\Android\SDK\platform-tools\adb.exe shell am instrument -w -e class com.garyapp.ytdl.ui.YtdlAppUiTest#cleanupForegroundHistoryBadgeVisualRecordsWhenExplicitlyRequested -e cleanupForegroundHistoryBadgeVisual true com.garyapp.ytdl.test/androidx.test.runner.AndroidJUnitRunner
```

结果：focused 测试、两个相关测试类、全量 debug 单测、debug APK 打包均 `BUILD SUCCESSFUL`。显式本地历史徽标种子和清理 instrumentation 均 `OK (1 test)`；第一次通过 Gradle 跑 connected 种子成功后，Gradle 清理了 app 包，因此又手动安装 app/test APK 并用 `adb shell am instrument` 重新种子，供前台可见复核使用。

Computer Use 前台证据：

- `docs/qa/android-visual-fidelity-20260709-m11/32-format-empty-state-density.jpg`
- `docs/qa/android-visual-fidelity-20260709-m11/33-format-empty-state-summary-disabled.jpg`
- `docs/qa/android-visual-fidelity-20260709-m11/34-history-status-resolution-badges.jpg`

观察：格式页空态已有禁用结构和明确 summary；历史页种子记录中，失败记录右上红色 `失败`、右下 `1080p`，完成记录右上绿色 `完成`、右下 `720p`，尺寸和左右边缘对齐。队列终态徽标由 Compose bounds 单测覆盖；本轮尝试用本机 HTTP + adb reverse 生成不触发外网的队列任务，generic `.webm` 直链可分析但提示“请选择可用格式”，无法自然进入队列终态，因此没有把本轮记为新的队列前台终态通过。

边界：M11 仍未通过。还缺真实下载进行中队列截图和后续五页视觉复核；本轮 URL 输入使用过 adb 辅助，仅服务于本机直链队列尝试，不计入系统软键盘 URL 输入验收。

### 2026-07-09 M11 队列空态密度补强

本轮继续推进 M11 队列页视觉一致性，不触发新的 YouTube 请求，不测试字幕下载，不构造假下载任务。

代码变化：

- 队列页无真实任务时，在原等待卡下方新增禁用的 `任务阶段` 骨架，显示 `下载视频` / `下载音频` / `原生合并`。
- 新增空态分组 `正在下载（0）`、`等待中（0）`、`已完成（0）`、`失败（0）`，用于接近基准图队列页的分组密度；这些分组不复用真实 `QueueStageStrip`、不显示进度条、不显示取消动作、不显示分辨率徽标。
- 新增 `输出信息` 卡，说明真实任务开始后才显示文件大小、速度和剩余时间；空态明确提示不会显示假进度或占位百分比。

TDD 与验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.emptyQueuePageKeepsReferenceDensityWithoutFakeProgress
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
```

结果：队列空态 focused 测试先按 TDD 红灯后转绿；环境脚本、相关 UI 测试组、全量 debug 单测和 debug 打包均通过。当前 debug APK 已重新安装并启动到 API37 模拟器作为辅助运行证据。

Computer Use 边界：本轮重新执行 `nodeRepl.write(JSON.stringify({ ok: true, cwd: nodeRepl.cwd }))` 和 `sky.list_apps()`，均成功；Computer Use 可看到 `Android Emulator - ytdl_api37_play_x86_64:5554`。但 Windows 仍弹出“是否允许网络访问此应用？”安全提示，`PickerHost` 透明层拦截模拟器底部点击。按安全边界未点击该系统提示，因此本轮没有新增队列空态前台截图，不能算新的 Android 前台 GUI 验收通过。M11 仍未通过：仍缺真实进行中队列截图，以及安全提示解除后的五页前台复核。

### 2026-07-09 M11 顶部安全区暂停点

顶部挖孔安全区已补到五页公共内容开头，新增单测覆盖安全区/挖孔节点、尺寸、居中和标题顺序；focused 测试、相关 UI 测试组、全量 debug 单测和 debug 打包均通过。Computer Use 已重新完成 node_repl smoke 与 `sky.list_apps()`，并在可见 API37 模拟器截图 `docs/qa/android-visual-fidelity-20260709-m11/35-top-safe-area-punch-hole.png` 中确认顶部居中挖孔标记出现。

用户随后要求继续调整下载队列/历史右侧徽标：状态与分辨率同尺寸，完成绿色、失败红色，状态右上角、分辨率右下角，且分辨率不能写死为 `1080p`。本轮已移除按格式 ID 猜测分辨率的回退；队列和历史徽标只读取实际 `formatSummary`，短视频摘要为 `720p` 时显示 `720p`，仅含旧格式 ID 的历史记录不展示猜测值。focused 回归、相关 UI 测试、全量 debug 单测和 debug APK 打包均通过；Computer Use 因 Windows 拒绝激活模拟器窗口而未形成新的 YTDL 前台页面证据。M11 仍未通过。

### 2026-07-10 M11 前台恢复诊断

环境脚本确认 API37 `emulator-5554` 在线，`hw.keyboard=no` 与 Gboard 软键盘前置正确。Computer Use 首先通过可见 `Raise` 动作恢复前台控制，并前台切换下载、格式、队列、历史、设置五页；设置页滚动后 `基准图配色` / `Codex 风格` 选择器完整可见。随后点击地址框，完整 Gboard 从底部弹出；因窗口激活再次被 Windows 拒绝，逐键 URL 输入未完成。辅助 `topResumedActivity` 仍为 YTDL，但 Computer Use 捕获画面变为黑色桌面/天气，无法作为应用前台证据。未使用 adb/剪贴板/硬件键补写 URL，未点击分析，未触发 YouTube 请求或字幕下载。M11 的真实进行中队列截图与完整可保存的五页前台复核仍待恢复后补齐。

补充根因证据：辅助 UIAutomator 树和设备帧缓冲仍显示 YTDL 下载页与完整 Gboard，证明 Activity/应用渲染未退出；`sky.list_windows()` 只提供一个 QEMU 外层窗口，而其 Computer Use 捕获持续显示黑色桌面/天气。该窗口捕获失配被视为前台验收阻断，UIAutomator/adb 只用于说明原因，不用于替代前台输入或验收。

### 2026-07-10 M11 真实队列、容量不足和测试文件清理

本轮 Computer Use 恢复到可见 API37 模拟器后，用系统软键盘完成了一次历史链接的公开视频分析和视频+音频下载，未选择字幕。前台队列先后可见真实下载进度（`4.0 MB / 331.5 MB` 至 `326.6 MB / 331.5 MB`）以及 `原生合并` 阶段，右上角状态与右下角实际 `1080p` 徽标位置正确。合并随后失败，前台队列和历史都显示红色 `失败` 徽标与“文件处理失败，请重试或选择其他格式。”

辅助容量核验确认失败根因：模拟器 `/data` 仅余约 `215 MB`，而视频流约 `331.5 MB`，原生合并还需要写出新的 MP4。该故障不是格式编号或分辨率显示问题。删除历史记录的前台确认流程成功，但随后发现失败合并的两条中间流仍残留在 App 私有下载目录，属于清理缺陷；已删除本次遗留目录并确认可用空间回升至约 `554 MB`、下载目录为空。

修复：`NativeMuxerMediaProcessor` 现在会在创建合并输出前要求“视频流大小 + 音频流大小 + 1 MiB”可用空间，不足时输出“设备存储空间不足，请清理空间后重试。”；`DownloadPipeline` 在失败分支清理未纳入最终输出的任务文件，保留已完成媒体文件以兼容字幕后续失败。新增容量不足、失败合并清理和用户提示映射单测；相关 focused 测试、全量 `:app:testDebugUnitTest` 与 `:app:assembleDebug` 均通过，debug APK 已重新安装，并由 Computer Use 前台确认历史和队列均为空。

测试地址与清理纪律：后续真实测试只使用当前主地址 `https://www.youtube.com/watch?v=PqQNXB6hhUs`、备用 `https://www.youtube.com/watch?v=svoD582Pas4` 和短视频 `https://www.youtube.com/shorts/oXFad1nt6v0`；旧链接只保留历史证据。每次真实测试收尾都要在前台删除对应历史记录，并辅助核验 App 私有下载目录无遗留测试文件。未因本轮再发起新地址下载，因此这不是新的 M11 最终验收通过。

### 2026-07-10 M11 格式行只显示当前视频提供的高度

用户确认格式页不再把固定分辨率表与分析结果混排。已有分析结果时，格式列表固定保留 `自动（推荐）`，其余只显示当前视频真实视频流提供的高度，并按高度降序排列；完全不存在的 `2160p`、`1440p`、`720p` 等高度不再显示“当前视频未提供”。实际存在但不适用于 Android 原生 MP4 合并的格式仍保留在列表中，继续显示具体不兼容原因。无分析空态仍维持禁用结构，不伪造可下载格式。

TDD：`FormatSelectionModelTest.missingResolutionIsHiddenInsteadOfShownAsUnavailable` 和对应 GUI 绑定断言先按旧固定高度行为失败，随后仅收紧 `resolutionHeightsFor` 为真实 `hasVideo` 高度后转绿。`FormatSelectionModelTest`、`DownloadGuiBindingTest`、全量 `:app:testDebugUnitTest` 和 `:app:assembleDebug` 均通过。用户此前以 `Esc` 停止 Computer Use，因此本轮尚未安装并以前台可见模拟器复核这项新布局，不能作为新的 M11 前台验收。

### 2026-07-10 提交审计：取消任务的中间流清理

对提交 `93991c4` 的独立只读审计发现一个 P1：视频流下载成功后、进入下一阶段前若用户取消，`DownloadPipeline` 的取消分支此前没有执行失败分支已有的未跟踪文件清理，可能留下 App 私有中间视频流。已新增 `DownloadRequestRoutingTest.cancellationStopsBeforeNextRouteAndDoesNotComplete` 的目录为空断言，先复现红灯，再在 `DownloadPipelineCanceledException` 分支调用同一受控清理方法。该清理仍只删除未纳入最终输出的文件，不删除已经完成的媒体输出。focused 回归、全量 `:app:testDebugUnitTest` 和 `:app:assembleDebug` 均通过。

### 2026-07-10 M11 前台格式过滤与容量失败恢复复核（历史记录，旧地址证据）

API37 可见模拟器已安装当时 APK。Computer Use 通过完整底部 Gboard 逐键输入历史主分享地址 `https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G`；辅助 UIAutomator 只用于确认完整字段值。真实分析显示标题 `KISSING YOUR BEST FRIEND tiktok challenge ! Part 5 🔥`、时长 `15:04` 和 `1080p MP4 需原生合并`。

前台格式页确认：没有实际流的 `2160p/1440p` 已隐藏；实际存在但非原生 MP4 合并兼容的 `1920p/1280p/854p/640p/426p/256p` 仍显示原因；`1080p/720p/480p/240p` 显示 `需原生合并`，`360p` 显示 `单文件`。随后以默认视频+音频、无字幕启动一条真实下载：队列可见视频阶段 `51.2 MB / 87.4 MB`、右下实际 `1080p` 和取消动作，随后可见音频阶段 `9.9 MB / 13.9 MB`。任务在合并前因容量预检进入失败，前台消息准确为“设备存储空间不足，请清理空间后重试”，并在队列/历史维持右上红色 `失败`、右下 `1080p`。

在前台历史确认删除后，辅助核验本次流文件均已清理；发现旧包只遗留空任务目录，已删除该空目录并为新包补充回归。新版随后重新安装，Computer Use 前台确认历史和队列均为空，辅助 `files/gui-downloads` 也为空。M11 仍未标记为通过：本次 Computer Use 画面为会话内可见证据，未新增落盘的完整截图审计包；五页截图级对照和真机/Play 后续项仍待完成。

### 2026-07-10 容量预检目标修正

上述新主地址任务失败后，辅助检查显示 API37 `/data` 约有 `550 MB` 可用空间，而本次视频、音频输入约为 `87.4 MB + 13.9 MB`。这说明前台的“存储空间不足”并非真实容量不足。根因是 `NativeMuxerMediaProcessor` 把尚未创建的 `merged-*.mp4` 输出文件传给 `usableSpace`；Android 对不存在文件可返回 `0`，从而产生假拒绝。

已新增 `MediaProcessorContractTest.mergeMeasuresAvailableSpaceAtExistingOutputDirectory`，先证明旧实现把未创建的输出文件作为探测目标；实现改为先取得并创建受控输出目录，再在该已存在目录上测量可用空间。`:app:testDebugUnitTest` 与 `:app:assembleDebug` 均已通过，debug APK 已重新安装到可见 API37 模拟器并能前台启动。因完整真实下载尚在 30 分钟节流窗口内，本条修复暂只有单测/打包/安装证据，不能把它写成新的前台合并成功或 M11 通过。

提交 `023539d` 的独立审计提出 P2：目录目标单测本身没有证明真实 muxer 合并。已将 API37 本地生成媒体 instrumentation 的成功输出改到原本不存在的 `new-task/merged.mp4`，断言目录创建、输出非空以及各一条视频轨和音频轨；`NativeMuxerMediaProcessorInstrumentedTest` 的 2 项均通过。随后全量 `:app:testDebugUnitTest` 与 `:app:assembleDebug` 再次通过。该本地设备测试不触发 YouTube 请求，也仍不替代节流窗口后的前台真实下载复验。

### 2026-07-10 容量修正前台复验与测试清理（历史记录，旧地址证据）

30 分钟节流窗口后，Computer Use 在可见 API37 模拟器通过完整底部 Gboard 逐键输入历史主分享地址 `https://youtu.be/lcFR2mFSmSs?si=FqJ3ZTdKRq6NAt6G`；辅助 UIAutomator 仅核对最终字段值。真实分析成功后，以默认“视频+音频”、未选择字幕启动下载。队列先显示视频阶段 `19.3 MB / 87.4 MB`、`7%`、取消动作和实际 `1080p`，随后完成视频/音频阶段并进入“原生合并”；最终前台显示 `下载视频 ✓ / 下载音频 ✓ / 原生合并 ✓`、`101.5 MB / 101.5 MB` 和绿色“完成”。这证明容量探测已不再对不存在输出文件作假拒绝。

历史页随后暴露完成记录的删除 chip 在窄卡内不可见。按 TDD 在 `DownloadUiBridgeTest.historyActionsRenderAsIconTextChipsWithoutLosingCallbacks` 加入可见删除行约束，旧布局红灯后，将删除 chip 置于主要操作行下方并保留原确认对话和测试 tag。focused、全量 `:app:testDebugUnitTest` 和 `:app:assembleDebug` 均通过。最新版 APK 的可见历史页显示“删除”，确认对话后历史为空；辅助仅删除已核验的 `files/gui-downloads/task-1783666522520-1` 测试目录并确认 `files/gui-downloads` 为空。此会话截图为前台可见证据但未另存为审计包，M11 仍未完成五页截图级审计。

提交后补跑 API37 `YtdlAppUiTest#historyDeleteRequiresConfirmationForInsertedTestRecord`，`connectedDebugAndroidTest` 成功：测试记录在取消后保留、再次确认后删除。该本地 connected 回归补足删除回调和确认流程的运行时辅助证据。

## 2026-07-10 后续执行边界

本文件此前的旧地址段落均为历史/证据记录；后续 Android 真实测试只按文首“2026-07-10 测试地址替换”中的三条完整 URL 执行。不要从历史段落复制地址发起新的分析或下载。

### 2026-07-10 当前主地址真实分析、下载完成与授权清理

- Computer Use 在可见 API37 模拟器中点击地址框后直接显示完整底部 Gboard，逐键输入当前主地址 `https://www.youtube.com/watch?v=PqQNXB6hhUs`；辅助 UIAutomator 树核验输入框完整值与该 URL 逐字一致，未使用剪贴板、候选词、自动补全或硬件键替代输入。
- 真实分析成功：前台出现真实缩略图、`09:00` 时长、标题和 `自动（推荐） · 1080p MP4 需原生合并` 摘要。格式页显示当前视频实际提供的 `1080p/720p/480p/360p/240p/144p`，字幕显示不可选；本轮未选择字幕。
- 按 30 分钟下载节流规则启动默认视频+音频下载。队列前台真实观察到视频阶段 `39.2 MB / 187.9 MB`、`6%`、取消动作和右下 `1080p`，随后观察到 `107.1 MB / 187.9 MB`、`19%`；最终完成态显示 `下载视频✓ / 下载音频✓ / 原生合并✓`、`196.3 MB / 196.3 MB`、绿色“完成”和右下 `1080p`。
- 历史页确认本轮完成记录后，按用户授权在前台删除 Room 历史记录；应用删除提示明确说明不会删除已保存媒体文件。随后按用户授权，仅对本轮已核验的目录执行辅助清理：删除前 `run-as com.garyapp.ytdl find files/gui-downloads -type f -o -type d` 列出 `files/gui-downloads/task-1783691348285-1/merged-137-140.mp4`，删除该精确任务目录后同一命令只剩 `files/gui-downloads` 根目录。重启应用后的队列显示 `正在下载(0)`、`等待中(0)`、`已完成(0)`、`失败(0)`，历史页为空；这属于运行态 UI 复核，不作为文件删除原因的证明。
- 本轮以 Computer Use 在会话内的可见操作和观察作为主证据。M11 后续只需继续处理实际可见的 UI 差异、外观设置联动和功能行为，不重复为留档发起真实下载。

### 2026-07-13 小米 14 真机验收暂停记录

- 真机：小米 14，序列号 `a73e29a3`，机型 `23127PN0CC`，代号 `houji`，Android API 36，ABI `arm64-v8a`。已安装 scrcpy 4.0，并通过 Computer Use 操作其可见 Windows 真机窗口。
- 权限前置：Computer Use 功能需要当前 Codex 任务启用“完全访问权限”。权限不足时记录为前台操控环境阻断，不得用 adb、UIAutomator、后台脚本或其他非前台方式替代 GUI 验收。
- 构建安装：新鲜 `:app:assembleDebug` 成功。第一次安装时设备中途断开，自动重连后再次执行安装成功，应用可在真机前台启动。
- 五页 GUI：Computer Use 已浏览下载、格式、队列、历史、设置五页；底部导航可用，页面可见内容未被底部导航遮挡。
- 通知权限：设置页初始显示未授权。Computer Use 点击请求后出现 HyperOS 系统通知权限弹窗，点击“始终允许”，返回应用后显示通知已允许。
- 地址与网络：首次分析失败时 VPN 未开启。用户开启 VPN，并在地址栏粘贴完整主地址 `https://www.youtube.com/watch?v=PqQNXB6hhUs`；Computer Use 核对前台可见地址后点击分析。该次 VPN 关闭导致的失败是网络环境问题，不记为应用失败恢复验收。
- 真实分析：VPN 开启后成功显示真实缩略图、`09:00` 时长、标题和默认摘要 `自动（推荐） · 1080p MP4 需原生合并`。格式页显示当前视频真实提供的 `1080p/720p/480p/360p/240p/144p`；`360p` 为单文件，其余可选项按实际能力显示原生合并；未选择或测试字幕。
- 真实下载：Computer Use 勾选授权并启动一次默认视频+音频下载。队列前台观察到真实任务、`66%`、视频和音频阶段完成、原生合并进行中及右下 `1080p`。打开通知栏后，任务在后台完成，通知显示 `YTDL 下载任务 / 下载完成`。用户已认可本次测试，不再为证明或留档重复真实下载。
- 问题 1：保存位置不可选。
- 问题 2：软件没有正式图标，通知栏仍显示默认占位图标。
- 问题 3：点击 `YTDL 下载任务 / 下载完成` 通知不能返回软件。
- 问题 4：当分析结果不提供可分离的独立音频流和视频流、只提供可直接下载的单文件视频时，`视频+音频` 选项目前仍可用，应改为灰色且不可选择；`视频下载` 应只显示当前结果中真实可下载的视频格式，选择后能够开始并完成下载，不能继续出现“所列格式均不可下载”。该问题只使用文首两条 Eporner 专项地址测试，并控制真实请求频率。
- 优化 1：地址栏已有文字时，长按应弹出可用的文字快捷菜单，至少包含全选、复制、粘贴和删除，并能对当前输入内容正确执行对应操作。
- 问题 6：电脑版启动后会自动比对最新 `yt-dlp`，有新版时提示下载；手机版目前没有该功能，点击设置页“解析器版本”也无反应。手机版应在启动后异步比对内置版本和最新版本，发现更新时提示下载或更新应用，且点击“解析器版本”可以主动重查；检查不得阻塞启动，也不得在运行时替换 Python 代码。
- 设置交互缺口：设置页“默认保存位置”“解析器版本”“媒体处理能力”“地址校验提示”目前均不能点击。修复后四项必须分别进入真实保存位置选择、解析器更新检查、媒体能力说明和地址校验说明，不能继续保留无响应箭头。
- 未完成：确认本次完成记录进入历史；打开合并视频；导出；分享；处理并复核问题 1-4、问题 6、优化 1 和四个设置入口；最后删除本次测试历史并精确清理 App 私有测试输出目录。当前测试历史和输出尚未清理，恢复时保留现有完成任务继续检查；除问题 4 的两个专项地址外，不发起新的真实下载。
- 当前结论：M10/D2 只完成了上述真机子路径，尚未整体通过。用户要求暂时停止测试并准备关机；本记录即恢复点。Play 发布准备已按用户指示暂时取消，不阻塞当前真机验收。

### 2026-07-13 后续前台验收拆分

- 第 5 步在今天执行：修复和自动化验证收口后，先安装最新 APK 到单一可见 API37 模拟器，用 Computer Use 完成前台验收。必测新行为为地址栏长按全选/复制/粘贴/删除、保存位置选择入口、解析器版本启动检查和手动重查、媒体处理能力说明、地址校验说明、缓存统计/确认清理、正式图标、通知点击返回，以及单文件媒体的模式灰显、真实格式选择和下载路由。Computer Use 前先运行规定的 `node_repl` 最小 smoke 和 `sky.list_apps()`；不要求计算器冒烟。缓存清理使用合成 App 私有测试文件；不为截图、XML或证明重复 YouTube 下载；问题 4 只使用两条 Eporner 专项地址并控制频率；不测试字幕。完成第 5 步后暂停。
- 第 6 步明天执行：在小米 14 上做最终 Computer Use 真机验收。保留并沿用已认可的历史真实下载，不重复主地址下载；复核新设置入口、长按菜单、通知返回、保存位置、缓存清理、单文件媒体、现有完成记录、打开、导出和分享。收尾时删除本次测试历史并精确清理 App 私有测试输出。该步完成前，M10/D2 仍为未通过。

### 2026-07-14 第 5 步 API37 模拟器验收完成

- 环境与入口：新鲜环境检查通过；全程只保留 `emulator-5554` 和一个可见 `ytdl_api37_play_x86_64` 窗口，`hw.keyboard=no`、`show_ime_with_hard_keyboard=1`、Gboard 正常。Computer Use 在 Windows 解锁后可激活模拟器；开始前按规定完成最小 smoke、`sky.list_apps()` 和前台窗口接管。最新 APK 安装成功，修复后 SHA-256 为 `15EB9FAE7722E28FE53E197C0C3407E5515642F078F4004A0BDDF0CBB5C0CC1A`。
- 首轮前台验收通过：启动器显示选定的正式图标；地址框直接弹出完整 Gboard，长按菜单的全选、复制、粘贴、删除均可执行；默认保存位置进入真实系统目录选择器；媒体处理能力和地址校验提示均打开说明对话框；23 B 合成 App 私有文件被正确统计、确认清理并立即归零；真实下载通知点击可返回同一 App 队列任务；单文件结果下“视频+音频”灰显，“视频下载”只显示真实单文件高度；所有 App 内操作由 Computer Use 完成，adb/UIAutomator 只辅助核对。
- 首轮发现并修复两项缺陷：其一，点击解析器版本行只触发行内重查，没有状态/版本对话框；其二，格式页明确勾选 `240p` 后仍按自动 `720p` 发起下载。修复加入解析器状态对话框、对话框内重新检查、UI revision 乱序拒绝、具体格式即时生效和真实 `YtdlApp` 请求闭环测试；格式页命令改为“返回下载页”。审计提出的 3 项 P2 已全部闭环，第二轮独立复审为 `CLEAN`。
- 自动化与构建：受影响测试 `112/112`、相关测试组 `179/179`、全量 debug 单测 `295/295` 通过；`:app:assembleDebug --no-parallel` 通过；`git diff --check` 通过。Gradle 任务均顺序运行，测试临时文件仅使用项目 `.qa-data`。
- 修复后前台复验：点击“解析器版本”打开对话框，显示内置 `yt-dlp 2026.3.17`、发现更新 `2026.7.4` 和可用的“重新检查”；检查中和完成状态在同一对话框更新。第二条 Eporner 地址只分析一次，完整 URL 经 Gboard 逐键输入并精确核对后才点击分析；格式页勾选具体 `240p`，底部返回下载页后摘要保持 `240p MP4 单文件`，唯一一次下载启动后队列显示 `240p / 83.8 MB`，未回退为 `720p/auto`，随后立即前台取消。
- 请求与清理：首轮两条专项地址共分析 2 次，第一条失败后未重试，第二条成功；首轮错误 `720p` 下载启动 1 次后取消。修复后仅对第二条再分析 1 次并启动 1 次 `240p` 下载后取消；两轮真实下载完成数均为 0，字幕请求/下载为 0。历史、地址分析态、终态通知和 `files/gui-downloads` 本轮文件均已清理；仅保留当前进程内存中的取消任务卡，不存在持久历史或私有文件残留。
- 结论：第 5 步 API37 可见模拟器 Computer Use 验收通过并完成清理，按用户要求在此暂停。第 6 步小米 14 真机最终验收仍留到明天；完成前不得宣称 M10/D2 真机验收通过。
