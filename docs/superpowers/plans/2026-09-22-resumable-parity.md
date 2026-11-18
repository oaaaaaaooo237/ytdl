# 两端续传与可选删除文件实施计划

> 使用 superpowers:subagent-driven-development。每个 Task 使用 fresh implementer；主会话按需求与安全/质量两个层次复查并验证后进入下一 Task。

目标：完成用户已授权的 Android 最新基线续传、删除文件复选框，并对齐 PC 的核心下载恢复行为。

基线：PC 在 D:/garyapp/ytdl，保留已有 v1.0.1 格式修复及删除文件未提交修改；Android 在 .qa-data/worktrees/android-unknown-single-file-1.0.3，从 014032a 的已审计 1.0.4 设计实施，不回退到根目录旧 1.0.2。已确认均可写。

Android 权威依据：docs/superpowers/specs/2026-08-03-android-resumable-download-1.0.4-design.md。用户本轮新增“勾选同时删除下载文件”覆盖旧设计中“不增加删除成品功能”一条；取消、普通缓存清理仍不得删除用户导出的文件。只允许明确勾选后按记录的精确 URI 删除普通文档，拒绝树根/目录/越界/链接，失败保留记录。

PC 保留现有 1—5 并发设置和 ffmpeg 能力，复用 yt-dlp 的 .part 续传；任务 ID 固定、恢复状态原子落盘，Windows DPAPI 加密 URL/任务请求，不保存 cookies 内容或临时媒体直链。恢复时仅加载暂停任务，不自动联网；点击继续才重新执行原页面和精确格式选择。取消与暂停分开，只有显式勾选才删除记录的成品。站点不支持 Range 时允许重下当前流并说明限制。

不扩展：Play 发布、后台自动开机下载、新下载协议、DRM 绕过、Android 原生网络栈在 PC 上的机械移植、三端 UI 重设计。

## Task 1：PC 可选文件删除收口
- 已分配 pc_delete_files。默认不勾选、取消无操作、明确文件归属、task_id 同源引用、失败保留记录。
- 验证：新增删除回归、全量 pytest、主会话代码复查。

## Task 2：Android 稳定任务、受控目录、加密恢复存储和数据库迁移
- 修改 DownloadRequest.kt 增加安全稳定 taskId（32 位小写 hex）及使用 cookies 标记；保持现有构造兼容。
- 新增 TaskFileStore：只接受 taskId 推导 gui-downloads/task-id；检查点使用相对路径/大小/格式 ID，禁止路径链接、父级和越界；原子写入 fsync。
- 新增加密恢复请求存储，复用 Keystore AES-GCM 能力，URL/标题/route/字幕/cookies 使用标记加密，不存临时 cookies 路径、请求头或媒体直链。
- QueueItemEntity/QueueDao/数据库迁移 3→4：taskId、队列顺序、阶段、暂停原因、进度字节、更新时间；不得加入原 URL。
- 测试先失败再实现：ID/路径/链接/相邻任务哨兵、损坏检查点、密文无明文秘密、迁移保留数据。

## Task 3：Android 下载管线阶段恢复与有界停止
- DownloadPipeline 使用同一 taskId 目录，明确 Pause/Offline/Cancel/Lifecycle 停止原因。
- 暂停/断网/生命周期停止保留 .part 和已校验完成阶段；取消先等下载退出再限定清理；阶段恢复跳过已完成视频/音频/合成，formatId 精确匹配。
- Python socket_timeout/retries 有界；用 progress_hook 和 bounded 请求退出；非 Range 来源重下当前流而非拼接，保留其它阶段。
- 聚焦测试：单文件、音视频分步、暂停无增长、取消安全、失败与暂停区分、字幕失败保持现有行为。

## Task 4：Android 持久队列、网络恢复、服务与前台交互
- DownloadCoordinator/Service 集成任务摘要及加密请求；状态先落盘再通知 UI。新建、暂停/继续、离线等待、队首规则及重启恢复按权威设计。
- ConnectivityManager 验证网络+短稳定窗口；403/404/410等网站错误不可无限等待。服务 onDestroy 只做生命周期停止并保留断点。
- UI/通知显示暂停、正在暂停、等待网络、继续、恢复失败；启动后台加载，不自动发请求；cookies 仅需要时重新读取现有受控来源。
- tests：队列顺序、存储/服务重建、离线自动继续、站点错误停止、任务精确格式不回退、Compose 控件。

## Task 5：Android 历史删除文件复选框
- 删除弹窗默认未勾选、每次重置；后台按数据库记录取精确私有/SAF输出集合，先验证全部目标并保护其他引用。
- 私有成品只删已记录普通文件，不猜目录；临时工作目录只由 taskId 受控删除。SAF 只删明确记录文档，拒绝目录和树根，无权限/失败保留记录。
- DownloadCoordinator.runWhenIdle 防止与下载/导出并发；删除成功后再删重试草稿和历史。测试覆盖勾选/不勾选/取消/缺失/失败/共享/链接/外部URI。

## Task 6：PC 持久暂停继续与断网/重启恢复
- 新增最小队列状态存储和 DPAPI 受保护请求；稳定 task_id，原子写入；不保存 cookies 内容/临时直链。
- MainWindow/DownloadWorker 对暂停、取消、等待网络、退出分流；有界进程终止后才确认暂停；保留 .part；恢复原输出模板和精确 format_id。
- 启动后台恢复暂停队列；继续时重新执行原页面解析；网络恢复自动继续等待网络项，站点错误有限重试，保留并发设置。
- tests：进程重建、取消不复活、暂停释放槽位、站点错误不会无限等待、共享目录不误删、原子保存失败。

## Task 7：PC 未知原始单文件能力对齐和版本
- 对齐 Android 1.0.3：明确视频容器与可用格式ID/地址、缺少分辨率/编码时显示原始单文件，不虚构清晰度，不参加合并。
- 本次最终版本：Android 1.0.4，PC 1.1.0；更新相应版本测试。发布物本地生成，不默认发布 GitHub。

## Task 8：审查、前台验收、构建与交付
- 顺序 Android 验证：环境探针→目标 JVM→全量 JVM→assembleDebug→androidTest 构建/必要仪器测试。严禁同工程并发 Gradle。
- 使用合成可公开下载测试视频和受控支持 Range 的 HTTP 服务；直接 URL 前台输入遵守 Android 可见软键盘规则。后台种测试文件只作准备，操作流程用 Computer Use 完成。
- 两端前台验证：默认保留文件、取消、勾选删除+哨兵保留、暂停≥10秒不增长、继续、断网/联网、应用重启恢复、单文件和分离流阶段恢复。
- 进行独立需求与安全审查；只有已验证的功能才称通过。记录证据与剩余限制，构建完整 PC ZIP 和 Android 调试签名 APK，不将测试APK作为交付物。


## 执行记录与已验证接口

- Task 1 PC 删除功能：需求与安全复查通过，Windows 句柄删除替代路径 unlink；真实 junction 竞态、文件占用及损坏记录测试通过。目标 60 / 全量 333 通过，前台验收待最终阶段。
- Android 1.0.3 新鲜基线：391 JVM 测试零失败；Gradle 9.4.1 成功。Gradle 10 兼容警告来自 Chaquopy 17 内部 multi-string 依赖声明，本轮不升级依赖。
- 本地合成 47,122,232 字节 MP4 + DASH 测试资源已生成。Range 服务端口 8765，Android 地址使用 10.0.2.2。
- 已实测 yt-dlp -P home:<成品目录> -P temp:<任务目录> -o <相对文件名模板> 可将临时文件隔离于任务目录，再移动成品。-o 不得使用绝对路径，否则 -P 被忽略（官方文档）。
- 外部 yt-dlp 真实停止/继续：任务树停止后 .part=519607 字节，10 秒稳定；恢复请求 bytes=519607-，成品 SHA256=bfd8bb862f1c2158102cd4c686e29460484ee62c75e9b22da95e40659d52df59。此为引擎验证，不冒充应用续传已完成。
- PC Task 6 应使用受控 taskId 临时目录+相对输出模板/home成品路径，记录请求中保留原始稳定模板；停止必须有界并确保子进程树退出，不能仅 terminate PyInstaller 父进程即显示暂停。

- 不支持 Range 的来源也已实测：服务器对 Range 返回 200，yt-dlp 输出 Unable to resume 后重下当前文件，最终哈希仍一致；应用需提示重新开始，不能承诺所有来源按字节续传。
- PC Qt 网络探针：PySide6 6.8.1.1 的 networklistmanager backend 正常加载，reachability=Online，transport=WiFi。Qt 官方说明该状态为系统提示而非绝对可达性；仅明确 Disconnected 应阻止公网自动重试，Unknown/Local/Site 不直接等同所有来源不可访问。结合实际下载错误判断，网站拒绝不归为断网。

- Task 2 已完成并通过主会话及独立需求/安全复查：40 suites / 408 JVM tests / failures=0 / errors=0 / skipped=0，新增17项。主会话读取XML确认408；Gradle已释放。
- Task 2 API：DownloadRequest新增taskId(32hex)、usesCookies、storageTarget；TaskWorkspace(rootDirectory)以directory/saveCheckpoint/loadCheckpoint/deleteTask提供受控访问；TaskCheckpoint包含phase/stopReason/进度和CompletedTaskFile(role,formatId,relativePath,sizeBytes)。跳阶段的role/formatId与原route核对属于Task3硬门槛。
- FileResumeRequestStore.fromContext使用noBackup/resume-requests+Keystore AES-GCM，保留原StorageTarget；不保存临时cookies路径/thumbnail/请求头。跨实例原子写锁、字符串上限、任务ID调包检查已覆盖。
- Room升4，MIGRATION_3_4保留原数据；旧queue新生成taskId、queueOrder=id，唯一索引，DAO新增按ID/非终态顺序查询。旧queue没有恢复密文，Task4应保留记录并明确提示恢复不可用，不自动猜测URL/路径。
- Task 3 已派发 android_resume_pipeline，仅实现管线/checkpoint/有界停止和最小枚举映射；后续Service/Coordinator/UI仍待Task4。

- 最终整合：Android 在最新独立工作树实现和验证后，将其有效源码/测试/文档变化整合回当前项目，与本轮PC改动一起复核；不得把工作树旧PC文件覆盖当前PC修复，也不得复制build/.gradle缓存。必要本地提交/合并只含本轮拥有的修改，不包含.codex-remote-attachments，不默认push或GitHub发布。


### Task 4 接入时已识别的关键边界

- Service 改为至多一个 drain worker，完成/暂停后再claim下一条，不把所有请求预先塞进Executor，否则继续任务无法插回等待队首。
- onDestroy只发LifecycleEnd，不能cancelActive删断点；旧Service完成回调不可清掉新Service通知或干扰新下载。线程未退出时不释放占用；停止超时显示受控错误，不声称已暂停。
- 恢复时显式区分手动暂停与重启后等待任务；启动不分析/下载，只由继续动作启动服务。损坏/旧版无密文记录保持可见并可移除，不伪造DownloadRequest URL。
- 从恢复请求重建cookies必须使用当前设置中已有的受控来源，且只在usesCookies=true时生成新临时副本；缺失授权保留任务等待用户处理。
- 用request.storageTarget捕获原保存位置；续传不随全局设置变化而改变导出目标。
- 暂停期间PrivateDownloadCache必须按Room非终态taskId保护整个目录，不能只靠尚不存在的outputUri，否则缓存清理会删除.part。
- 完成导出后的私有目录清理使用TaskWorkspace.deleteTask(request.taskId)，不能重新以结果携带路径推导待删目录或deleteRecursively。
- checkpoint.phase表示当前下载/合成阶段；UI暂停状态来自stopReason。重启展示Paused保留已下载字节，不把DownloadingVideo等旧运行状态当作正在运行，也不在Resuming时清空进度。

- Task 3管线已完成并经需求/安全独审通过：41 suites / 426 JVM测试零失败，Python277测试（bridge22）通过。新增17项恢复回归，包括真实等10秒文件不增长。四种stopReason、角色/原formatID核对、hardlink标题发布、checkpoint保护、metadata总deadline与checking停止hook已实现。
- Task4必须承接：第三方Python协议/NativeMuxer不能立即退出时，停止超时仅告警并保留槽位与文件，不能伪报Paused/另起下载线程。暂停不写终态历史；恢复重建cancellation及临时cookies，使用同taskId；Completed仅表示私有成品，Service仍负责导出。

- Task4当前实现者 android_resume_runtime，拟定 ResumeTaskRepository(create/saveState/recover/completeTerminal)、ResumeTaskItem(taskId,title,state,nullable request,recoveryError)；Coordinator addSavedListener/restore/resume/cancelSaved/pauseActive。PrivateDownloadCache按非终态taskId保护整个目录。Task5后续只删除历史明确文件，不能直接清非终态workspace。

- Task4集成期间主会话独立修正Python错误分类：显式HTTP401/403为permission，404/410为unsupported，其他已收到HTTP响应为unknown（不当全局断网）；连接重置/拒绝/中断、DNS与超时为network。格式不可用不当断网，URL包含role/forbidden也不会掩盖真实连接错误。4项新增测试先失败后修复，Python全量281通过，bridge26通过。
- 本地QA服务当前PID=36328，命令为本仓库 .qa-data/delete-files-20260922/range_server.py，端口8765。qa.html和merge.html分别提供仅合成数据的QA resume/QA merge标题；rate.txt默认262144 B/s，可选audio-rate.txt专门降低DASH音频流速度。旧PC可视fixture进程44436/37468已关闭；新fixture配置目录改为pc-final，历史taskId为合法32hex。验收完成须只清理本次自建服务/进程。

- QA服务状态更新：Start-Process启动的实例之后已不在监听；脚本语法检查通过，已改为exec托管长进程，session_id=46655，监听PID=56052，端口8765，HTTP HEAD qa.html=200 text/html。以这条为准，早先PID记录已过期。
- Task4仍在收口与全量测试；只读复查者android_runtime_review，实施者android_resume_runtime。Task5 Android复选框、Task6 PC续传、Task7未知原始格式/版本、Task8前台与整合打包尚未完成，不得称两端功能已完成。

- Task4已最终收口并通过需求/安全复查：43 suites /452 JVM测试全部通过，主会话读取XML确认。停止优先级、早取消实际任务落盘、永久站点错误覆盖断网token、取消清理失败状态、导出断网自动继续均有回归。当前不等同前台GUI通过。
- Task5开始：Android删除确认默认不勾选，精确私有/SAF文件引用及共享/非终态任务保护；不得因此改变Task4的暂停/取消/恢复语义。

- Task5已完成并经需求/安全复查通过：46 suites /467 JVM tests，failures=0/errors=0；主会话重读XML确认。支持精确私有/SAF输出、tree-derived叶文档、旧私有目录、共享引用/非终态保护、默认不勾选及失败保留记录。API24宿主Robolectric的lstat不识别Windows符号链接，真实API24拒绝链接尚待设备验证。
- Task5后 assembleDebug + assembleDebugAndroidTest 构建成功（20秒），尚不等于前台通过。Task6由pc_resume_runtime实施，主会话准备Android前台验证。
- 后续主会话修复并独审：cancelSaved成功时更新匹配任务的通知，失败时只保留取消动作，避免旧继续操作残留；新增5项回归。原471项全量通过，新增ActionCancel服务回归单独通过。
- 前台API37发现SELinux拒绝硬链接，已将标题发布改为受控随机临时文件copy+fsync+rename，已有标题通过大小/SHA256校验，raw保留到标题checkpoint保存。新增复制中暂停和checkpoint写失败恢复测试；目标DownloadResumeTest通过。真实已下载任务安装修复后无需网络即完成入库，哈希与源一致。此记录替代执行记录中硬链接方案。
- 用户本轮明确要求地址直接复制粘贴，前台后续输入按该最新指令执行并观察确认完整URL。Android前台暂停30秒不增长、Range206、应用退出后恢复暂停/手动继续完成、删除取消/默认保留/勾选删除及邻近文件保护已验证，证据记入docs/qa/2026-09-22-resumable-parity.md。

## 本轮收口

- Task6最终5项复查发现均闭环，独立复审放行；368项当时全量通过，Windows真实worker以及打包GUI均有断点偏移+hash证据。
- Task7独审放行：PC未知分辨率原始单文件有效ID/容器/http(s)校验及精确下载，PC1.1.0；Android1.0.4/code5。目标129项独立通过。
- Task8已完成本地整合、主流程前台验收与交付包。整合后根目录Python403项、Android476项通过，PC ZIP和Android APK构建成功。Android额外修复DASH估计总量小于已下载量时显示错误的问题，两条回归覆盖。
- 详细前台、Range、重启、系统断网、原生合并、私有/SAF删除及限制见docs/qa/2026-09-22-resumable-parity.md。API24链接设备验证、arm64真机矩阵和PC真实网卡断开仍无本轮证据；不声称已完成这些矩阵测试。未push或发布远端。
