# Android M11 视觉一致性审计

日期：2026-07-09

## 范围

本轮审计对应 `docs/superpowers/plans/2026-06-19-ytdl-android-play-mvp.md` 的 Continuation Task M11。目标是在不破坏真实分析、下载、队列、历史、隐私和 Play-safe 文案的前提下，将当前 Android 五页 GUI 继续贴近 `docs/android-gui-reference-v3.png`，并验证 `基准图配色` 与 `Codex 风格` 在前台截图层面真实生效。

本轮只采集当前 APK 的前台可见状态并记录差距；后续代码修复和复测另行补充。

## 证据

执行前已运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
```

结果摘要：API37 `emulator-5554` 在线，Gradle 9.4.1、JDK 17、Android SDK、Gboard 和矩阵 AVD `hw.keyboard=no` 均由脚本确认。

Computer Use 前置：

- `nodeRepl.write(JSON.stringify({ ok: true, cwd: nodeRepl.cwd }))` 成功。
- `sky.list_apps()` 成功并识别 `Android Emulator - ytdl_api37_play_x86_64:5554`。

当前 APK 前台截图：

- `01-download-reference-current.png`
- `02-format-reference-current.png`
- `03-queue-reference-current.png`
- `04-history-reference-current.png`
- `05-settings-reference-current.png`
- `11-settings-codex-selected-current.png`
- `12-download-codex-selected.png`
- `13-format-codex-selected.png`
- `14-queue-codex-selected.png`
- `15-history-codex-selected.png`
- `16-settings-codex-selected.png`

`06` 到 `10` 记录了第一次尝试点击 `Codex 风格` 但未真正切换的状态；原因是外观配色按钮被底部导航区域部分遮挡，需要先滚动设置页。

## Checklist

- 五页标题层级：下载、格式、队列、历史、设置均有可见标题和说明文案。
- 顶部安全区：当前依赖系统状态栏和内容顶部 padding；还没有主动复刻基准图的居中挖孔留白骨架。
- 卡片密度：下载页、历史页和设置页已有卡片结构；格式空态和队列空态明显比基准图稀疏。
- 底部导航：五个页面按钮可用，选中态可见；当前 icon 是文本符号，不是稳定图标资产，pill 只包 icon 区域。
- 格式页：无分析时只显示空态；真实分析后已有可用/不可用格式证据，但本轮空态截图和基准图的密度差距较大。
- 队列页：空态只显示等待卡；M11 仍要求后续至少补一张真实下载进行中队列截图。
- 历史页：搜索、筛选、打开/分享/导出/删除动作可见；右侧已有独立过滤 icon，历史动作已改为 icon+text chip。
- 设置页：设置行和隐私文案可见；外观配色区域在未滚动时被底部导航遮住，影响前台可点击性。
- Codex 配色：滚动后可选中，五页截图显示 accent 从基准红/绿/橙/紫/蓝切换到 Codex preset；但 palette 偏灰棕，需在后续审计中明确是否作为 Codex 风格的有意偏差。

## 当前差距

1. P1：设置页外观配色区域缺少足够底部留白，导致 `Codex 风格` 按钮首次处于底部导航遮挡区，前台点击容易落不到目标。
2. P1：底部导航仍使用文本符号作为 icon，选中 pill 范围也小于基准图；这会削弱五页底栏视觉一致性。
3. P1：队列页空态缺少基准图式分组密度；真实进行中队列截图仍未补齐，M11 不能据此关闭。
4. P2：格式页空态过空，和基准图的分辨率列表、紧凑设置行与底部 summary/button 结构差距明显。该差距不能通过 fake sample 解决，必须保留真实分析后才显示真实格式的原则。
5. P2：顶部安全区只依赖系统状态栏与固定内容 padding，未专门对齐基准图挖孔安全区骨架。

## 后续修复优先级

1. 先修设置页底部留白和外观配色可点击性，保证 M11 的基准/Codex 前台验证稳定。
2. 再修底部导航 icon/pill 结构和可测试契约，避免五页共同骨架偏离。
3. 再处理队列真实分组与格式/历史密度；真实进行中队列截图需要遵守 YouTube 节流，不能为了视觉重复大下载。

## 本轮修复后复核

代码修复：

- 设置页外观配色区域后增加额外滚动缓冲，避免 `基准图配色` / `Codex 风格` 按钮贴到底部导航遮挡区。
- 底部导航从文本符号切换为本地 `ImageVector` 图标，并将选中背景扩展到 icon+label 的 tab 区域。

测试：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.settingsAppearanceSectionKeepsExtraBottomScrollBuffer
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.bottomNavigationUsesVectorIconsInsteadOfTextSymbols
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
```

结果：以上 focused 测试、相关 UI 绑定测试、全量 debug 单测和 debug APK 打包均 `BUILD SUCCESSFUL`。

修复后前台证据：

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
- `29-queue-badge-size-fix.png`
- `30-history-badge-size-fix.png`
- `31-history-status-resolution-paired-badges.jpg`
- `32-format-empty-state-density.jpg`
- `33-format-empty-state-summary-disabled.jpg`
- `34-history-status-resolution-badges.jpg`

观察：

- 五页底部导航已显示 vector 图标；选中态不再只是小字符背景。
- 设置页滚动后，外观配色卡、`基准图配色`、`Codex 风格` 和 `颜色方案` 行均能完整露出在底部导航上方，前台点击更稳定。
- 用户复核后再次微调历史/队列右侧徽标：状态徽标和分辨率徽标统一为 `64dp x 30dp` 最小尺寸并使用 `labelMedium`；历史页前台截图显示 `完成` 在右上角为绿色、`失败` 在右上角为红色，分辨率在右下角且与状态徽标尺寸一致。队列空态前台截图确认未误伤空态；真实进行中队列徽标仍待后续真实下载窗口截图。
- 本轮追加修复历史页搜索/动作控件：搜索框右侧新增圆形筛选 icon，历史行操作从裸文字改为 icon+text chip；并通过本地 instrumentation 种子插入一条完成、一条失败记录，前台 Computer Use 截图 `31-history-status-resolution-paired-badges.jpg` 确认 `失败` 红色、`完成` 绿色均在右上角，`1080p` / `720p` 分辨率均在右下角且尺寸一致。
- 本轮继续修复格式页无分析空态：不再只显示空卡，而是保留分辨率列表、帧率/编码/容器/字幕行、summary 和禁用的 `应用选择` 按钮；所有行明确标注需先分析或分析后显示，不伪造格式 id、大小、字幕或可下载能力。Computer Use 前台截图 `32-format-empty-state-density.jpg` / `33-format-empty-state-summary-disabled.jpg` 已确认空态密度和禁用 summary。
- 本轮继续按用户反馈微调下载状态徽标：队列完成态右上角从 `100%` 改为短状态 `完成`，失败为 `失败`；队列/历史共用右侧徽标列，状态在右上角、分辨率在右下角，二者固定同尺寸。`34-history-status-resolution-badges.jpg` 以前台历史种子记录再次确认 `失败` 红色 + `1080p`、`完成` 绿色 + `720p` 对齐。队列完成态样式已由 Compose bounds 单测覆盖；本轮用本机 HTTP 直链尝试生成队列任务，generic 直链可分析但没有可用下载格式，未形成新的队列终态前台截图。
- 当前仍未满足 M11：还缺真实下载进行中队列截图，队列页仍偏空态，部分历史缩略图在修复后截图中仍短暂显示占位渐变，需后续复核。

## 边界

- 本轮没有触发新的 YouTube 网络请求。
- 本轮没有声明 M11 通过。
- 真实字幕下载仍暂停。
- M10 真机验收、Play 签名、隐私政策 URL、Data safety 和商店素材仍未开始。

## 队列空态密度补强

本轮继续处理 M11 中“队列页空态缺少基准图式分组密度”的差距，不触发 YouTube 请求，不构造 sample 下载任务。

代码修复：

- 队列无真实任务时，在原等待卡下方新增禁用的 `任务阶段` 卡，显示 `下载视频` / `下载音频` / `原生合并` 准备骨架。
- 新增四个空态分组：`正在下载（0）`、`等待中（0）`、`已完成（0）`、`失败（0）`，只作为视觉分组提示，不复用真实队列 `QueueStageStrip` 或进度条。
- 新增 `输出信息` 卡，说明开始后才显示文件大小、速度和剩余时间；空态明确写出“不会显示假进度或占位百分比”。

TDD 过程：

- 先新增 `DownloadGuiBindingTest.emptyQueuePageKeepsReferenceDensityWithoutFakeProgress` 并确认红灯：缺少 `ytdl-queue-empty-steps-card`。
- 增加阶段卡后测试转绿。
- 按 fresh explorer 审计建议增强断言，要求四个空态分组 tag，并确认空态不出现 `ytdl-real-queue-card`、`ytdl-queue-cancel-action`、真实 `ytdl-queue-stage-strip` 或 `ytdl-queue-format-badge`；增强后再次红灯，随后补四个禁用分组转绿。

新鲜验证：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.emptyQueuePageKeepsReferenceDensityWithoutFakeProgress
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
```

结果：环境脚本、队列空态 focused 测试、两个相关 UI 测试类、全量 debug 单测和 debug APK 打包均通过。APK 已重新安装并启动到可见 API37 模拟器作为辅助运行证据。

Computer Use 边界：本轮按要求重新执行 `nodeRepl.write(JSON.stringify({ ok: true, cwd: nodeRepl.cwd }))` 和 `sky.list_apps()`，均成功并识别 `Android Emulator - ytdl_api37_play_x86_64:5554`；但 Windows 仍显示“是否允许网络访问此应用？”安全提示，且 `PickerHost` 透明层拦截模拟器底部点击。按安全规则未点击该系统安全提示，因此本轮没有新增队列空态前台截图，不能把该切片写成新的前台可见通过，也不能声明 M11 通过。M11 仍缺真实进行中队列截图和安全提示解除后的五页前台复核。

## 顶部安全区与暂停点

本轮继续按基准图补齐顶部挖孔安全区：所有页面内容列表开头增加独立的 `16dp` 顶部安全区和居中的 `7dp` 灰色挖孔标记，位于页面标题之前。新增 `DownloadGuiBindingTest.appScreensStartWithDedicatedPunchHoleSafeArea` 保护节点存在、顺序、尺寸、居中和标题位于安全区下方。

新鲜验证：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest.appScreensStartWithDedicatedPunchHoleSafeArea
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.garyapp.ytdl.ui.DownloadGuiBindingTest --tests com.garyapp.ytdl.ui.DownloadUiBridgeTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:testDebugUnitTest
D:\DevTools\gradle-9.4.1\bin\gradle.bat -p android :app:assembleDebug
```

结果：以上 focused 测试、相关 UI 测试组、全量 debug 单测和 debug APK 打包均 `BUILD SUCCESSFUL`。当前 APK 已安装并启动到 API37 模拟器，Computer Use 重新执行 `nodeRepl.write(JSON.stringify({ ok: true, cwd: nodeRepl.cwd }))` 和 `sky.list_apps()` 成功；前台可见截图 `35-top-safe-area-punch-hole.png` 显示顶部居中挖孔标记已出现。

暂停点后的处理：队列/历史下载框右侧徽标继续共用同尺寸与同位置规则；完成保持绿色、失败保持红色，状态在右上角、分辨率在右下角。本轮移除了按旧格式 ID 推断分辨率的回退逻辑，徽标只从实际 `formatSummary` 提取；例如短视频摘要为 `720p` 时显示 `720p`，旧记录只有 `视频 137 + 音频 140` 时留空而不臆测为 `1080p`。新增 `DownloadUiBridgeTest.resolutionBadgesUseRecordedSummaryInsteadOfGuessingFromLegacyFormatIds` 先验证旧行为失败，再验证修复后通过。

新鲜验证：focused 回归测试、`DownloadGuiBindingTest` 与 `DownloadUiBridgeTest`、全量 `:app:testDebugUnitTest` 和 `:app:assembleDebug` 均通过；APK 已安装到 API37 模拟器。Computer Use 已完成连接和可见模拟器窗口快照，但 Windows 拒绝激活该窗口，截图只见 Android 桌面，未能进入 YTDL 页面。因此本轮没有新增前台 GUI 验收，也不能声明 M11 通过。后续仍需真实进行中队列截图和完整五页前台复核。

## 2026-07-10 前台恢复复测

本轮先运行环境脚本，确认 API37 `emulator-5554` 在线，AVD 为 `hw.keyboard=no` 且 Gboard 的 `showImeWithHardKeyboard=1` 已开启。按要求重新运行 `nodeRepl.write(JSON.stringify({ ok: true, cwd: nodeRepl.cwd }))` 与 `sky.list_apps()`；Computer Use 通过模拟器窗口的 `Raise` 动作恢复了一次可见前台控制。

这一次前台可见观察已完成下载、格式、队列、历史、设置五页切换；格式空态、队列空态、历史空态、底栏选中态和顶部挖孔标记均可见。设置页滚动后，`跟随系统` / `浅色` / `深色` 以及 `基准图配色` / `Codex 风格` 均完整位于底部导航上方。该观察未保存新的可接受证据截图，故只作为恢复诊断，不能替代 M11 截图审计。

随后点击地址框后，完整 Gboard 在约两秒内从底部弹出，符合拟真键盘前置；但逐键输入过程中 Windows 再次拒绝 Computer Use 的窗口激活。恢复后，辅助 `adb` 状态确认 `com.garyapp.ytdl/.MainActivity` 仍为 `topResumedActivity`，而 Computer Use 连续两次捕获到黑色 Android 桌面/天气画面，无法可靠看到应用。没有使用 adb、剪贴板、硬件键或脚本替代 URL 输入；没有点击分析、没有发起任何 YouTube 请求，也没有开始下载。模拟器保持运行，M11 仍未通过。

进一步诊断：辅助 `uiautomator` 树仍完整返回 YTDL 下载页、地址框、下载模式卡、授权 checkbox 和五个底栏节点；以二进制安全方式取得的设备帧缓冲也显示 YTDL 与完整 Gboard，表明应用未退出且软键盘仍在前台。Computer Use 的 `sky.list_windows()` 只公开一个 QEMU 外层窗口，没有可单独选择的 Android 子窗口；该唯一窗口的 Computer Use 截图却持续显示黑色桌面/天气。因此根因收敛为 QEMU 外层窗口的 Computer Use 画面捕获/激活失配，而非应用、软键盘或活动栈失效。设备帧缓冲和 UIAutomator 只作辅助诊断，不能替代前台验收；在该失配恢复前不得继续发送业务触摸或键盘输入。
