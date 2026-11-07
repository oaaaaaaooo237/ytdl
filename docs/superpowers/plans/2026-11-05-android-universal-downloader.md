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

- [x] 测试先行：本地真实 HTTP 响应，文件名 header 优先/编码/路径去除；HTML 和错误响应拒绝；MIME/Content-Disposition 决定类型，未知长度与重定向不误报。
- [x] 定义普通数据 API（Android 无依赖，便于 JVM 测试）：`FileMetadata(sourceUrl, finalUrl, fileName, mimeType, totalBytes, etag, lastModified, kind, resumeSupport)`；`HttpFileProbe.probe(url)` 返回 `FileFound/NotFile/Rejected`；类型 `Video/Audio/Other`，未经 Range 验证为 Unknown。
- [x] `ResumableFileDownloader.download(metadata, partialFile, checkpointFile, shouldStop, onProgress)` 返回 `Completed/Stopped/RestartRequired/Failed`。不加密 checkpoint 只含请求元数据哈希和已提交字节，源 URL 留给加密请求存储。
- [x] TDD 覆盖 206、忽略范围200、身份变更、错误范围、416、截断、未知长度、空文件、暂停恢复、无身份、重定向、编码、路径、超时、资源释放、慢速首部停止、UTF8长文件名与创建partial后暂停竞态。
- [x] 主会话最终目标命令通过：47 测试、0失败、0错误、0跳过，BUILD SUCCESSFUL in 10s。此前全量541/0（最后6项增补由目标47验证，最终全量在Task2后再跑）。
- [x] 主会话 API/合同检查、独立规范与质量review通过。提交：e067f7e、1119ce7、01eefa5、0a1e06d。只证明独立核心，未证明Android GUI或完整任务接入。

## Task 2：普通文件接入现有任务、恢复与导出

文件：`DownloadRequest.kt`、`DownloadPipeline.kt`、`DownloadState.kt`、`PipelineCheckpoint.kt`、`ResumeRequestStore.kt`、`RetryDraftStore.kt`、`DownloadService.kt`、`DownloadFailureMessages.kt`、相关测试。按必要性增添专用 RawFilePipeline，避免把 HTTP 细节堆进媒体 pipeline。

- [x] 新增明确文件 route，持有 Task 1 元数据和用户文件名，不伪造 yt-dlp formatId。恢复载荷版本升级且可读取旧 v1/v2。媒体四 route 保持行为。
- [x] 测试完成约束：普通文件主输出、合法空文件、附件数；媒体非空约束不变。通用文件走 foreground/暂停取消/持久队列，复用路径和导出。
- [x] 变更源/不支持续传需要明确 UI 可恢复错误，新增重启确认 action 的请求参数只能由用户操作设置；没有自动破坏性回退。
- [x] 失败/暂停不能被既有 cleanup 删除 partial/checkpoint；取消清理只限当前 TaskWorkspace。重名自动编号和询问不可静默覆盖。
- [x] 顺序运行目标核心与旧 pipeline、resume、export 测试，再全量单测。

## Task 3a：统一任务模型、筛选及设置持久化

文件：新增 `ui/UnifiedTasks.kt` 与测试；按实际需要改 `HistoryItemEntity.java`、`QueueItemEntity.java`、Room provider/migration、`SettingsRepository.kt`、`AppSettings.kt`、`NotificationController.kt`、调度器/服务。

- [x] 对实时/待执行/恢复/历史建稳定 ID 联合视图，状态全覆盖、去重和升级后旧历史可见；输出类型 Video/Audio/Other，不靠文件标题猜测。
- [x] 先测 `matchesTaskFilter(task, statuses, types)`：状态多选 OR、类型多选 OR、跨组 AND、空组不限、全部重置。
- [x] 如果 schema 需要新增类型字段，只做非破坏迁移并测试旧数据库到新版本；禁止 destructive fallback。
- [x] 配置并发、冲突处理、完成/失败提醒持久化；字段有范围校验与旧偏好迁移，不提前暴露未接入的控件。
- [x] 目标单测、设置与数据库迁移测试顺序通过。规范和质量审阅后才进入 Task 3b。

## Task 3b：并发调度与通知实际行为

本项从原 Task 3 拆出，单独 fresh 实现子代理；避免同时改变数据模型和现有单任务服务的控制语义。

文件：`DownloadCoordinator.kt`、`DownloadService.kt`、`NotificationController.kt`、网络/恢复适配及相应测试。

- [x] 并发偏好真正控制同时运行的任务数；可用范围 1～3，默认值与批准设置一致。每任务独立执行者、取消令牌、进度/状态与工作区，不把原全局 currentState 当多任务事实来源。
- [x] 增加任务ID映射的活动状态与监听，保留必要的旧API兼容。按ID暂停/取消/继续不得误操作别的任务；手动暂停不被网络恢复覆盖，队列顺序稳定。
- [x] 降低并发不取消正在运行的任务，提高并发可启动新的等待任务；服务销毁时所有执行者安全停下，不能在仍写文件时删除工作区。
- [x] 前台服务通知持续有效，多任务状态不相互覆盖；完成/失败提醒按持久偏好生效，不能关闭必要的正在下载通知。
- [x] TDD 覆盖并发上限、独立取消/暂停、网络恢复、设置变更、不同任务终态与通知、销毁恢复。顺序目标单测与全量回归，通过两阶段review才进入UI任务。

## Task 4：A 浅深色主题与三页下载 GUI

文件：`ui/theme/Theme.kt`、`ui/YtdlApp.kt`，允许抽取 `DownloadPage.kt`/`FileDownloadSection.kt` 与测试。视觉权威为 selected-a 的浅深色图片与 HTML，保留既有拥有状态的入口而非另造 mock UI。

- [x] 主题加入 Morandi 浅深色，正确迁移默认偏好，手动与系统模式都生效；按钮、标签、输入直角，3 导航。
- [x] URL 剪贴板仅点击读取。异步 Probe/yt-dlp 识别、过期结果抑制、改 URL 清空旧结果；只有对应结果显示选项。
- [x] 文件名编辑、扩展名行内提示和提交确认、保存位置及冲突策略接入真实文件 route。
- [x] 格式页并入下载的展开区/子页，保留完整质量/codec/字幕/实际格式/预览缓冲等原有功能；媒体模式不混入文件字段。
- [x] 有意义的 GUI 测试覆盖3导航和互斥展示，避免只对文本标签作“验收”。目标测试与 assemble 辅助检查，最终前台验收留到 Task 6。

## Task 5：全部任务和可操作的分类设置 GUI

文件：`YtdlApp.kt`、按需拆分 `UnifiedTasksPage.kt`/`CategorizedSettingsPage.kt`，相关 UI 测试。

- [ ] 默认所有状态，横向单行筛选、摘要、空结果清除、真实进度；每状态操作可用；历史原搜索/打开/分享/导出/删除/重试迁移到任务中，不丢记录。
- [ ] 按最新要求合并为六类设置，解析器置于下载行为首项。六类页面和 Cookies 可读、解析器更新/回退、目录失效恢复、开源清单已有前台证据；账号限定内容与实际撤销目录授权仍待验。
- [ ] 外观选项保留，缓存/记录清理明确作用范围与风险确认，不做空按钮。与 Task 3 配置实际值绑定。
- [ ] 回归原功能盘点中的 GUI 功能，旧五页断言应更新为新需求但不删掉原行为断言。

## Task 6：完整回归、前台验收、APK

- [x] 按用户前台反馈调整格式页和任务页，底部导航选中时整块着色、文字颜色不变（`c76b50d`）。
- [x] 接通并发 1–5 滚动选择、等待上限、提交后定位任务、断点续传和自动字幕（`f4d1f53`）。该提交 Android 单测 614 项通过。
- [x] 前台链接输入按用户最新要求使用复制粘贴，核对完整地址后复测普通文件和此前提供的 X 地址。

- [x] 顺序执行目标单测→全量单测→assembleDebug（必要时 release 构建，不更换用户未知签名）。版本 1.1.0 / 12。
- [x] 接管现有 API37 模拟器，验收使用单个可见窗口。
- [ ] Computer Use 系统软键盘输入真实测试 URL；真实 HTTP 下载与暂停续传、源变化确认、媒体下载回归、任务过滤/状态动作、导出打开、浅深色手选/系统、设置全类操作截图记录。合成可控 HTTP fixture 为真实字节传输，不能伪造进度。
- [ ] 对旧 APK 升级安装，确认历史/配置/恢复信息保留。主机没有可接真机则如实记录仅模拟器，不将商店/真机扩展为当前用户未要求的新门槛。
- [ ] APK 复制到 `artifacts/android/CaptainFlash-1.1.0-debug.apk`，记录 SHA256、ABI、包名版本、验证矩阵与剩余项。有关键项未通过不得完成目标。
- [ ] 独立最终 review，必要修复复测后集成回 `D:/garyapp/ytdl`；保留初始未跟踪附件与设计稿。用户拿到可安装 APK 和准确验收结论。

## 用户批准的后台可靠性补充（2026-11-05）

- 后台服务超时保存恢复点并停止服务，避免系统超时异常；测试活动任务和等待任务。
- 下载行为增加后台电池限制状态和系统设置入口；检查锁屏、断网、进程结束和目录授权失效。
- 统一任务进度条与百分比，继续显示实际传输阶段。
- 链接识别增加取消入口，取消后停止等待并丢弃过期结果。
- 首次下载明确选择保存位置并持久记住选择。
- 同时完成用户已指定的等待队列默认 5、上限 50、右侧原位选择数量、手机 Cookies 导出指引和“返回设置”文案。
- 最终验收使用同一 APK，核对全部页面浅深主题与真实操作，并记录未通过项。

## 2026-11-07 用户修订的地址入口布局

以用户手机截图反馈为依据，本轮 1.11.5 采用以下布局和交互要求。

- 第一行地址框占据可用宽度，右侧为 48 dp 的分析图标按钮。主动剪贴板操作收进地址框尾部。
- 第二行两个等宽、同高的文字按钮，分别为“导入地址列表”和“内置浏览器打开”。使用既有直角、莫兰迪配色和 Material 字体角色。文字按可用宽度换行，行高取两按钮的最大内容高度。
- 所有下载类型共用地址分析入口。首页不提供单独种子下载入口，输入网址、磁力链接或导入来源后按类型分流。
- 地址列表支持 UTF-8 TXT 和 Markdown，包括 Markdown 链接、自动链接、项目列表和编号列表，保留去重和大小上限。
- WebView 长按 HTTP/HTTPS 链接出现“下载”操作，选择后复制链接并回填主页，进入现有分析流程。响应声明下载的链接可直接进入同一流程。
- 验证包含 Markdown 解析单测、两个变体全量回归、手机窄屏和展开屏/大字号截图，以及可见模拟器中长按链接下载至完成。

## 2026-11-07 Torrent 升级关系修订

用户要求继承旧通用 APK 中已经配置的 X Cookies。历史通用 1.11.1（13）的 applicationId 为 com.garyapp.ytdl，证书 SHA-256 为 ad03d41cda8bbcf547e9aaceda77aaf6a38cb8d0b57163ec78ff72905a6caa0a。之前的 Torrent 包使用 .torrent 后缀而形成另一应用。1.11.6（18）移除这个后缀，Torrent 改为沿用原包名和原签名的可选升级版本，与标准版分别交付 APK。

Cookies 配置继续使用原 SharedPreferences 和内容 URI，应用内加密登录继续使用原包名对应的私有目录和 Android Keystore。迁移采用 Android 同应用覆盖升级，保留原用户数据和 URI 授权。此前手机导入来源记录为 Download/x-cookies.txt，源文件保留在用户手机。当前仅有模拟器连接，真机 Cookies 实际可读性尚待设备接入或用户安装后检查。

## 2026-11-07 1.11.7 用户问题修复计划

1. 解析器更新持久化。启动时恢复已保存选择，仅在已选择下载版本时异步初始化 Python，并读取实际导入版本；更新检查采用启动配置版本。选择使用同步提交确保成功反馈对应落盘结果。设备验证必须在已有下载版本的冷进程运行，断言选择、实际模块版本及重建后的管理器选择一致。
2. Torrent 纳入通用下载协议。新增持久化 Torrent 路由，复用 DownloadCoordinator、DownloadService、Room 队列、检查点、暂停/继续/取消、历史、通知和多文件导出。首页以常规结果区呈现种子及保存位置。旧独立进度弹窗和独立前台服务移除，旧已保存待恢复项和成品迁入统一任务。兼容旧恢复/重试数据。
3. 普通文件连接上限。设置中提供1/2/4/8/16，默认4，写入原设置仓库；实际文件管线采用所选上限，小文件和服务器约束仍自动减小。它与多任务并发为独立设置。
4. 回归。覆盖磁力文本/Markdown/分享入口、持久化请求及重试往返、多文件历史引用、暂停后恢复和原生种子文件字节一致性。
5. 焦点约束。用户明确选择“先做后台验证，前台稍后再测”。所有前台模拟器操作暂停；后台检查完成后仍须标注剩余真实GUI验收，待允许后继续用户提供链接、后台下载、页面切换及重启检查。

## 2026-11-07 完成任务复制来源与种子导出（1.11.8）

用户要求移除完成任务的成品导出入口，采用双方块复制图标，置于删除前的倒数第二位。媒体与普通文件复制原提交分析的地址，磁力任务复制完整磁力地址，显示“复制成功”。远程/本地 .torrent 来源则通过系统文件选择器导出真正的种子描述文件。

实现从既有加密重试记录恢复原始URL，普通文件不使用重定向后的CDN地址，媒体不使用下载成品地址。Torrent下载独立保留描述文件，避免成品移至授权目录、任务工作区清理后无法导出。兼容旧任务工作区描述文件及旧磁力记录。若旧记录没有来源数据则明确提示不可用。所有操作复用任务图标和现有系统导出流程，新增复制成功反馈。

后台验证包含来源选择与兼容回归、Compose按钮点击/剪贴板/成功提示，以及Android设备中种子描述字节和FileProvider读取一致性。前台验收仍遵守用户“稍后再测”的要求。
