# Android 通用下载器：保留与新增验收表

基线：8ce0852，工程版本 1.0.10。盘点来自当前源码，不将旧设计描述当成已实现功能。2026-11-05 Task 0 只读盘点已复核。

| 功能 | 拥有代码 | 主要回归测试 |
| --- | --- | --- |
| URL 软键盘、选择操作、改 URL 作废分析、异步过期响应抑制 | YtdlApp.kt / UrlInputField、analyzeUrl、analysisRequestRevision | UrlInputSelectionActionTest、YtdlAppDownloadFlowTest、DownloadGuiBindingTest |
| 媒体三模式、真实分辨率/编码/帧率/容器、精确 format ID、原生合并兼容性 | FormatSelection.kt、DownloadRequest.kt、MediaProcessor.kt | FormatSelectionModelTest、DownloadRequestRoutingTest、MediaProcessorContractTest |
| 任务等待队列、按 ID 取消、暂停/等待网络/继续、持久恢复 | DownloadCoordinator、DownloadService、ResumeTaskRepository、PipelineCheckpoint、TaskWorkspace | DownloadCoordinatorTest、DownloadResumeTest、DownloadServiceResumeTest、ResumeTaskRepositoryTest、TaskWorkspaceTest |
| 真实阶段与进度、未知长度、速度/ETA | DownloadState、queueProgressPresentation、QueueCard | DownloadStateTest、DownloadGuiBindingTest |
| 前台通知操作、拒绝通知后应用内进度、权限返回刷新 | NotificationController、YtdlApp 生命周期 | NotificationControllerTest、YtdlAppUiTest |
| 完成/失败/取消历史、加密重试草稿、重新分析与恢复格式 | DownloadHistoryRecorder、RetryDraftStore、retryHistoryItem | DownloadHistoryRecorderTest、RetryDraftStoreTest、RetryHistoryPersistenceTest、YtdlAppDownloadFlowTest |
| 历史搜索、类型筛选、缩略图、长标题与滚动列表 | filterHistoryItems、historyPageItems、HistoryCard | DownloadGuiBindingTest、YtdlAppUiTest |
| 打开/分享、FileProvider 与 SAF content URI、缺失文件中文错误 | outputTargetForHistoryUri、openHistoryItem、shareHistoryItem | DownloadUiBridgeTest、YtdlAppUiTest |
| 记录与文件删除分离、失败保留、保护共享/活动任务文件、清理载荷 | HistoryDeleteDialog、HistoryFileDeletion | HistoryDeleteDialogTest、HistoryFileDeletionTest、AndroidHistorySafDocumentsTest |
| 私有目录/SAF 保存、更改目录释放授权、完成自动导出与失败恢复 | SettingsRepository、ExportController、DownloadService.exportCompletedOutputs | SafTreeExportTest、SettingsRepositoryStorageTargetTest、DownloadServiceResumeTest |
| 分网站 Cookies/登录、域名匹配、清除与可读性、临时内容清理与脱敏 | SiteCookiesDialog、SiteLoginActivity、SiteLoginStore、TemporaryCookiesFile、SensitiveText | SiteCookiesDialogTest、SiteCookiesTest、SiteLoginStoreTest、TemporaryCookiesFileTest、HistoryPrivacyTest |
| 解析器启动/手动检查、下载/安装指定版本、切换/删除/回到内置、当前选择与运行版本 | ParserVersionManager、ParserVersionOperationCoordinator、YtdlApp 对话框 | ParserVersionManagerTest、ParserVersionOperationCoordinatorTest、ParserUpdateSettingsUiTest |
| 缓存统计、安全清理、下载中阻止清理、保护完成文件 | PrivateDownloadCache、DownloadCoordinator.runWhenIdle | PrivateDownloadCacheTest、SettingsCacheUiTest |
| 系统/浅/深模式、旧配色偏好读取、恢复默认保存目录、能力说明、实际开源清单 | SettingsRepository、Theme.kt、OpenSourceNoticesDialog | AppSettingsStorageTest、SettingsExplanationUiTest、DownloadGuiBindingTest |

## 已有与新增边界

- 当前源码实际已是下载/任务/设置三导航，不能依据旧计划把它改回五页。
- 字幕已有后端独立文件链路，但 GUI 当前明确传空字幕列表；本次按批准设计增加可用字幕选择，旧后端必须保留。旧“隐藏字幕”的测试应按新需求更新，而非删除下载/隐私断言。
- 现有“播放”是完成后 ACTION_VIEW 外部打开，没有内置播放器或网络播放缓冲；本次预览保留缩略图，不额外承诺新增播放器。
- 现有导出是完成后按保存目标自动 SAF 导出，没有独立的历史导出按钮；自动导出与打开/分享不能丢失。
- 现有调度为一个活动任务和顺序等待队列；若设置展示并发数量，必须实际接入安全调度，不能造一个没有作用的控件。
- 全状态联合筛选、HTTP 普通文件识别/命名/安全续传、主动剪贴板入口是新增，不能由旧媒体恢复测试替代验收。

## 本轮状态

Task 0：源码盘点已完成；基线全量单测运行中，尚未记录通过。
Task 1～6：未完成。目标保持完整，最终逐行补充实现提交、测试输出与前台 GUI 证据。
