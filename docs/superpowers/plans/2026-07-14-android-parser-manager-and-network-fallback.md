# Android Parser Manager and Metadata Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复深色主题地址框可读性和 Android Pornhub `HTTP 410` 解析失败，并把启动更新提示升级为可下载、选择和删除 `yt-dlp` 版本的完整管理功能。

**Architecture:** `yt-dlp` 仍是唯一解析器和默认网络路径。Android 原生 HTTPS 仅在 Python 请求收到 410 后，对受限的 HTTPS GET 元数据请求重试，并把有限大小的文本响应包装回 yt-dlp；解析器版本从官方 PyPI 下载 wheel、校验 SHA-256 后存入 App 私有目录，选择后在下一次启动复制到运行时缓存并加入 Python `sys.path`，APK 内置版本始终兜底。

**Tech Stack:** Kotlin 2.3、Compose Material 3、Chaquopy 17、Python 3.12、yt-dlp wheel、Robolectric、Android instrumentation、API37 Play AVD。

## Global Constraints

- 默认中文界面和错误提示。
- 不提交 `AGENTS.md`、附件目录或 `.qa-data`。
- Android 原生网络回退不得处理登录、授权、DRM、`401/403/451`、POST、Range 或媒体大文件。
- 原生回退只允许 HTTPS GET，只有 Python 首次返回 410 才触发一次；响应体上限 4 MiB，内容类型限网页、JSON、XML 和 M3U8 文本。
- Eporner 的精确 HTTP 元数据升级规则保持独立，不泛化为全站协议替换。
- 解析器只从 `https://pypi.org/pypi/yt-dlp/json` 元数据和 `https://files.pythonhosted.org/` wheel 下载；版本、文件名和 SHA-256 必须全部校验。
- 下载版本保存在 App 私有目录；内置版本不可删除。选择和删除后的版本切换在重启应用后生效。
- 当前进程从缓存中的运行时 wheel 副本加载，删除已下载版本不得破坏正在运行的 Python 模块。
- Gradle 任务顺序执行；真实字幕下载保持暂停。

---

### Task 1: 地址输入框对比度

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Test: `android/app/src/test/java/com/garyapp/ytdl/ui/DownloadGuiBindingTest.kt`

**Interfaces:**
- Produces: 浅色 URL 输入面固定使用 `#181B17` 正文和 `#5E625C` 提示文字，不随深色主题变成浅色。

- [x] 增加深色主题下读取原生 `EditText.currentTextColor` 和 `hintTextColors` 的失败测试。
- [x] 确认旧实现因白底浅色文字失败。
- [x] 将 URL 输入正文、提示和前导图标绑定到浅色输入面专用颜色。
- [x] 运行聚焦测试并确认通过。
- [x] 在最终可见模拟器中切换深色主题，目测 URL 和提示文字清晰可读。

### Task 2: Android 原生元数据 410 回退

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/AndroidMetadataHttpFallback.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/YtdlpBridge.kt`
- Modify: `android/app/src/main/python/ytdl_bridge.py`
- Test: `android/app/src/test/java/com/garyapp/ytdl/core/ytdlp/AndroidMetadataHttpFallbackTest.kt`
- Test: `tests/test_android_ytdl_bridge.py`
- Test: `android/app/src/androidTest/java/com/garyapp/ytdl/core/ytdlp/PornhubAnalysisInstrumentedTest.kt`

**Interfaces:**
- Produces: `AndroidMetadataHttpFallback.fetch(url: String, headersJson: String): String`，返回不含敏感日志的 JSON 响应。
- Consumes: Python `AndroidYoutubeDL` 在原请求抛出 `HTTPError(status=410)` 后调用该接口；成功时构造 yt-dlp `Response`，失败时重新抛出原异常。

- [x] 为 HTTPS GET、410 单次触发、POST/Range/非 HTTPS 拒绝、4 MiB 上限和内容类型白名单编写失败测试。
- [x] 实现 Kotlin 原生 HTTPS 获取器，保留安全请求头和 cookies，不转发 Host、Content-Length、Accept-Encoding。
- [x] 为 Python 410 回退、非 410 不回退、Eporner 规则不回退编写失败测试。
- [x] 在 `AndroidYoutubeDL.urlopen` 中实现一次性受限回退，并将回调注入分析及各下载入口。
- [x] 删除本轮临时异常/IP/UA 诊断函数和诊断断言，只保留默认跳过的真实 Pornhub 成功回归。
- [x] 在干净 API37 上运行给定 Pornhub URL 的真实分析测试，断言标题和格式非空，不下载媒体。

### Task 3: yt-dlp 解析器版本管理（方案 A）

**Files:**
- Create: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/ParserVersionManager.kt`
- Create: `android/app/src/test/java/com/garyapp/ytdl/core/ytdlp/ParserVersionManagerTest.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/ParserUpdateChecker.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/MainActivity.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Modify: `android/app/src/test/java/com/garyapp/ytdl/ui/ParserUpdateSettingsUiTest.kt`

**Interfaces:**
- Produces: `ParserVersionManager.listVersions()`、`downloadLatest(expectedVersion)`、`select(version)`、`delete(version)`、`prepareSelectedRuntimeWheel(cacheDir)`。
- Consumes: PyPI JSON 的最新版、`py3-none-any.whl` 下载 URL 和 SHA-256；`MainActivity` 在首次 import `yt_dlp` 前插入运行时 wheel 路径。

- [x] 编写 PyPI 元数据解析、官方主机限制、SHA 匹配、校验失败清理、选择、删除和全部删除后重新下载测试。
- [x] 实现私有版本目录、`.part` 原子落盘、SHA sidecar、版本列表和内置版本兜底。
- [x] 编写运行时副本测试：选择版本复制到 cache，源 wheel 删除后当前副本仍存在，下次启动回退内置。
- [x] 在 `MainActivity` 启动 Python 后、加载桥接模块前配置所选 wheel；无效选择自动回退内置。
- [x] 更新启动弹窗为“稍后 / 更新”，更新按钮直接下载、校验并选择最新版，完成后提示重启生效。
- [x] 将设置页解析器对话框改为版本列表：内置版本可选不可删；已下载版本可使用、删除；列表为空仍显示“下载最新版”。
- [x] 确认清理下载缓存不进入解析器版本目录或运行时缓存目录。

### Task 4: 审计和验收

**Files:**
- Modify: `docs/qa/android-mvp-smoke.md`
- Modify: `docs/superpowers/plans/2026-06-19-ytdl-android-play-mvp.md`

**Interfaces:**
- Produces: 新行为、测试结果和未完成真机边界的持久记录。

- [x] 运行 Python 桥接测试。
- [x] 顺序运行受影响 Android 单测、全量 `:app:testDebugUnitTest --no-parallel` 和 `:app:assembleDebug --no-parallel`。
- [x] 检查临时诊断函数、发布页动作、旧“不可内部更新”文案、无用声明和重复下载逻辑均已移除。
- [x] 使用唯一可见 API37 模拟器和 Computer Use：验证深色输入框、启动“更新”、版本选择/删除/重新下载，以及给定 Pornhub URL 的真实分析；不下载媒体、不测试字幕。
- [x] 删除本轮模拟器测试数据和 App 私有临时文件，保留已下载解析器版本仅在验证其持久化确有需要时。
- [x] 更新 QA 与主计划，明确模拟器通过不替代尚未执行的小米 14 最终真机验收。

## 完成记录（2026-07-15）

- 深色主题前台复核通过：浅色地址输入面使用深色正文和提示文字，边框及图标清晰可见。
- Pornhub 真实分析 instrumentation 仅执行一次并通过；Computer Use 又在可见 API37 模拟器中用 Gboard 逐键输入并精确核对给定 URL，真实分析显示标题、`08:39` 时长和 `1920p MP4 H.264` 格式。本轮没有下载媒体或测试字幕。
- 解析器方案 A 已完成：启动提示为“稍后 / 更新”；设置页支持官方 PyPI 下载、SHA-256 校验、列表、选择、删除和删光后重新下载；内置 `2026.3.17` 不可删除。删除测试版后重新下载 `2026.7.4`，重启应用显示“当前选择”和“本进程实际版本”均为 `2026.7.4`。
- 需求审查和代码质量审查均已闭环，最终复审为 `CLEAN`。Python `12/12`、受影响 Android `114/114`、全量 JVM `368/368` 通过；`:app:assembleDebug` 和 `:app:assembleDebugAndroidTest` 成功。
- 已停止误建缓存对应的 Gradle/Kotlin 后台进程并删除 `.qa-data/gradle-home`，释放约 1.18 GB；未清空 `.qa-data/temp` 或历史 QA 证据。模拟器最终保留已验证可启动的 `2026.7.4` 私有解析器版本，不保留媒体下载产物。
- 该完成记录只关闭本计划的 API37 模拟器范围，不替代主计划中的小米 14 最终真机验收；D2/M10 仍未完成，Play 发布准备仍按用户要求暂停。

## 1.0.1 发布前复核（2026-07-15）

- 在 1.0.1 最终 APK 的可见 API37 模拟器中再次完成删除和恢复：用户在删除确认框出现后明确授权，下载版 `2026.7.4` 删除成功，列表只剩内置 `2026.3.17`；随后“下载最新版”从官方源重新下载、校验并选择 `2026.7.4`，界面提示重启后生效。
- 本次复核确认“本进程仍可继续使用运行副本”和“本地下载版已删除”互不混淆。重新下载期间按钮会暂时禁用，约 8 秒后出现“解析器更新完成”，不是界面卡死。
- 1.0.1 对下载任务页和文件名的改动没有改写 Pornhub 410 兼容逻辑，也没有合并 Eporner 的精确协议修正规则；临时网络探针、原始异常、IP、完整地址和 cookies 内容仍不会进入前台或日志。
- 问题 8 仅调整前台页面组织：独立“格式”页并入下载页，底部为“下载 / 任务 / 设置”三项。Pornhub 原生 HTTPS 元数据回退、Eporner 精确协议修正规则和解析器版本管理没有改动；可见模拟器只用既有历史地址重新分析，没有再次下载媒体。
