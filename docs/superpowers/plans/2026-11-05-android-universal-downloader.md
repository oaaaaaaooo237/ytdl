# CaptainFlash APK 重构执行计划

> 执行方式：superpowers:subagent-driven-development。每个 Task 一个 fresh 实现子代理，主会话先做规范 review 与验证，再进入下一项；规范通过后另做代码质量审阅。禁止同工程并行 Gradle。

**依据：** 用户已批准 `2026-11-05-android-universal-downloader-design.md` 与 A 浅深色设计。旧媒体功能保留，新增通用文件下载/安全续传。工作树 `D:/garyapp/ytdl-apk-revision`，分支 `codex/android-universal-downloader`；最终将已验证提交集成回原工作区，保留原未跟踪附件。

**验证命令前缀：** PowerShell 设置 `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'`，cwd 为本工作树 `android`。使用 `./gradlew.bat`，SDK 环境已重新运行 `scripts/android_env.ps1` 检查。

## Task 0：现有功能盘点与基线

- [x] 只读建立功能→拥有代码→测试映射，特别是 UI 重新布局容易丢的媒体功能。见 `docs/qa/android-universal-downloader-retention.md`。
- [x] `./gradlew.bat :app:testDebugUnitTest --console=plain`：500 项全过，无跳过，BUILD SUCCESSFUL in 2m 50s。
- [x] 记录新旧权威关系；原始图片不改。源码实际已为三导航，不因旧文档倒退为五入口。

## Task 1：独立 HTTP 文件识别与安全续传核心

文件：新增 `core/files/FileMetadata.kt`、`core/files/HttpFileProbe.kt`、`core/files/ResumableFileDownloader.kt` 和对应 `src/test/.../core/files/*Test.kt`。不改 UI、Room、DownloadRoute 或现有 pipeline，先独立验证网络传输合同。

- [ ] 测试先行：本地真实 HTTP 响应，文件名 header 优先/编码/路径去除；HTML 和错误响应拒绝；MIME/Content-Disposition 决定类型，未知长度与重定向不误报。
- [ ] 定义普通数据 API（Android 无依赖，便于 JVM 测试）：`FileMetadata(sourceUrl, finalUrl, fileName, mimeType, totalBytes, etag, lastModified, kind, resumeSupport)`；`HttpFileProbe.probe(url): Result<FileMetadata>`；类型 `Video/Audio/Other`。HTML 探测返回不属于文件的结果供调用方回退原 yt-dlp，不吞掉权限/传输错误。
- [ ] `ResumableFileDownloader.download(metadata, partialFile, checkpointFile, shouldStop, onProgress)` 返回完成文件或明确暂停/重启确认结果。恢复信息可与临时文件同目录，敏感 URL 不包含在不加密 checkpoint（调用方已有加密请求）；ETag/长度/偏移校验匹配后才能追加。
- [ ] TDD 测试覆盖 206 正常续传、忽略 Range 的 200、源变更、错误 Content-Range、416 完成/拒绝、截断、未知长度、暂停后恢复和缺少身份信息；不覆盖旧字节，不静默从头下载。连接超时、重定向上限、identity 编码、关闭流、路径约束必须具体验证。
- [ ] 目标测试先失败后通过：`./gradlew.bat :app:testDebugUnitTest --tests 'com.garyapp.ytdl.core.files.*' --console=plain`。
- [ ] 主会话审查 API 与合同；规范通过后代码质量审阅；记录提交。

## Task 2：普通文件接入现有任务、恢复与导出

文件：`DownloadRequest.kt`、`DownloadPipeline.kt`、`DownloadState.kt`、`PipelineCheckpoint.kt`、`ResumeRequestStore.kt`、`RetryDraftStore.kt`、`DownloadService.kt`、`DownloadFailureMessages.kt`、相关测试。按必要性增添专用 RawFilePipeline，避免把 HTTP 细节堆进媒体 pipeline。

- [ ] 新增明确文件 route，持有 Task 1 元数据和用户文件名，不伪造 yt-dlp formatId。恢复载荷版本升级且可读取旧 v1/v2。媒体四 route 保持行为。
- [ ] 测试完成约束：普通文件主输出、合法空文件、附件数；媒体非空约束不变。通用文件走 foreground/暂停取消/持久队列，复用路径和导出。
- [ ] 变更源/不支持续传需要明确 UI 可恢复错误，新增重启确认 action 的请求参数只能由用户操作设置；没有自动破坏性回退。
- [ ] 失败/暂停不能被既有 cleanup 删除 partial/checkpoint；取消清理只限当前 TaskWorkspace。重名自动编号和询问不可静默覆盖。
- [ ] 顺序运行目标核心与旧 pipeline、resume、export 测试，再全量单测。

## Task 3：统一任务模型、筛选及设置实际行为

文件：新增 `ui/UnifiedTasks.kt` 与测试；按实际需要改 `HistoryItemEntity.java`、`QueueItemEntity.java`、Room provider/migration、`SettingsRepository.kt`、`AppSettings.kt`、`NotificationController.kt`、调度器/服务。

- [ ] 对实时/待执行/恢复/历史建稳定 ID 联合视图，状态全覆盖、去重和升级后旧历史可见；输出类型 Video/Audio/Other，不靠文件标题猜测。
- [ ] 先测 `matchesTaskFilter(task, statuses, types)`：状态多选 OR、类型多选 OR、跨组 AND、空组不限、全部重置。
- [ ] 如果 schema 需要新增类型字段，只做非破坏迁移并测试旧数据库到新版本；禁止 destructive fallback。
- [ ] 配置并发、冲突处理、完成/失败提醒持久化并接入实际调度/通知，前台服务通知不被偏好禁用。核心暂停/恢复用户意图不可被并发引入回归。
- [ ] 目标单测、设置迁移测试、调度与通知测试顺序通过。

## Task 4：A 浅深色主题与三页下载 GUI

文件：`ui/theme/Theme.kt`、`ui/YtdlApp.kt`，允许抽取 `DownloadPage.kt`/`FileDownloadSection.kt` 与测试。视觉权威为 selected-a 的浅深色图片与 HTML，保留既有拥有状态的入口而非另造 mock UI。

- [ ] 主题加入 Morandi 浅深色，正确迁移默认偏好，手动与系统模式都生效；按钮、标签、输入直角，3 导航。
- [ ] URL 剪贴板仅点击读取。异步 Probe/yt-dlp 识别、过期结果抑制、改 URL 清空旧结果；只有对应结果显示选项。
- [ ] 文件名编辑、扩展名行内提示和提交确认、保存位置及冲突策略接入真实文件 route。
- [ ] 格式页并入下载的展开区/子页，保留完整质量/codec/字幕/实际格式/预览缓冲等原有功能；媒体模式不混入文件字段。
- [ ] 有意义的 GUI 测试覆盖3导航和互斥展示，避免只对文本标签作“验收”。目标测试与 assemble 辅助检查，最终前台验收留到 Task 6。

## Task 5：全部任务和可操作的分类设置 GUI

文件：`YtdlApp.kt`、按需拆分 `UnifiedTasksPage.kt`/`CategorizedSettingsPage.kt`，相关 UI 测试。

- [ ] 默认所有状态，横向单行筛选、摘要、空结果清除、真实进度；每状态操作可用；历史原搜索/打开/分享/导出/删除/重试迁移到任务中，不丢记录。
- [ ] 七类设置均进入有内容且真正接线的详情。保留网站 cookies/登录管理、解析器检查/更新/版本/回退、存储权限处理和许可。
- [ ] 外观选项保留，缓存/记录清理明确作用范围与风险确认，不做空按钮。与 Task 3 配置实际值绑定。
- [ ] 回归原功能盘点中的 GUI 功能，旧五页断言应更新为新需求但不删掉原行为断言。

## Task 6：完整回归、前台验收、APK

- [ ] 顺序执行目标单测→全量单测→assembleDebug（必要时 release 构建，不更换用户未知签名）。版本 1.1.0 / 12。
- [ ] 检查既有 qemu/emulator 进程与 adb 设备；仅接管/启动一个可见 API37 模拟器，立即定位到主屏右侧且完整可见。
- [ ] Computer Use 系统软键盘输入真实测试 URL；真实 HTTP 下载与暂停续传、源变化确认、媒体下载回归、任务过滤/状态动作、导出打开、浅深色手选/系统、设置全类操作截图记录。合成可控 HTTP fixture 为真实字节传输，不能伪造进度。
- [ ] 对旧 APK 升级安装，确认历史/配置/恢复信息保留。主机没有可接真机则如实记录仅模拟器，不将商店/真机扩展为当前用户未要求的新门槛。
- [ ] APK 复制到 `artifacts/android/CaptainFlash-1.1.0-debug.apk`，记录 SHA256、ABI、包名版本、验证矩阵与剩余项。有关键项未通过不得完成目标。
- [ ] 独立最终 review，必要修复复测后集成回 `D:/garyapp/ytdl`；保留初始未跟踪附件与设计稿。用户拿到可安装 APK 和准确验收结论。
