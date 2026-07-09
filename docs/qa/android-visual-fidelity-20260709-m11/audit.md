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
- 历史页：搜索、筛选、打开/分享/导出/删除动作可见；缺少基准图右侧过滤 icon，动作仍是文本工具条。
- 设置页：设置行和隐私文案可见；外观配色区域在未滚动时被底部导航遮住，影响前台可点击性。
- Codex 配色：滚动后可选中，五页截图显示 accent 从基准红/绿/橙/紫/蓝切换到 Codex preset；但 palette 偏灰棕，需在后续审计中明确是否作为 Codex 风格的有意偏差。

## 当前差距

1. P1：设置页外观配色区域缺少足够底部留白，导致 `Codex 风格` 按钮首次处于底部导航遮挡区，前台点击容易落不到目标。
2. P1：底部导航仍使用文本符号作为 icon，选中 pill 范围也小于基准图；这会削弱五页底栏视觉一致性。
3. P1：队列页空态缺少基准图式分组密度；真实进行中队列截图仍未补齐，M11 不能据此关闭。
4. P2：格式页空态过空，和基准图的分辨率列表、紧凑设置行与底部 summary/button 结构差距明显。该差距不能通过 fake sample 解决，必须保留真实分析后才显示真实格式的原则。
5. P2：历史页筛选缺少右侧 filter affordance，列表动作仍是文字按钮，和基准图 icon+text 工具感不同。
6. P2：顶部安全区只依赖系统状态栏与固定内容 padding，未专门对齐基准图挖孔安全区骨架。

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

观察：

- 五页底部导航已显示 vector 图标；选中态不再只是小字符背景。
- 设置页滚动后，外观配色卡、`基准图配色`、`Codex 风格` 和 `颜色方案` 行均能完整露出在底部导航上方，前台点击更稳定。
- 用户复核后再次微调历史/队列右侧徽标：状态徽标和分辨率徽标统一为 `64dp x 30dp` 最小尺寸并使用 `labelMedium`；历史页前台截图显示 `完成` 在右上角为绿色、`失败` 在右上角为红色，分辨率在右下角且与状态徽标尺寸一致。队列空态前台截图确认未误伤空态；真实进行中队列徽标仍待后续真实下载窗口截图。
- 当前仍未满足 M11：还缺真实下载进行中队列截图，队列页仍偏空态，格式页空态过稀，历史页动作仍偏文字工具条，部分历史缩略图在修复后截图中仍短暂显示占位渐变，需后续复核。

## 边界

- 本轮没有触发新的 YouTube 网络请求。
- 本轮没有声明 M11 通过。
- 真实字幕下载仍暂停。
- M10 真机验收、Play 签名、隐私政策 URL、Data safety 和商店素材仍未开始。
