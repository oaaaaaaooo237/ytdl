# YTDL Android Google Play 版设计

日期：2026-06-19

## 状态

本设计记录用户确认的安卓版方向：需求核心沿用 Windows 版，但按 Google Play 上架要求重新收敛范围、权限、文案、存储和后台任务模型。

本设计不替代 Windows 版权威设计文档 `docs/superpowers/specs/2026-05-30-ytdl-gui-design.md`。Windows 版继续按原计划维护；Android 版后续应单独写实现计划，单独建工程/模块，单独验证和发布。

## 设计依据

本版按 Google Play 上架版设计，而不是旁加载版或全站点下载器。设计时参考以下官方或上游资料：

- Google Play 不当内容政策：https://support.google.com/googleplay/android-developer/answer/9878810
- Google Play 知识产权政策：https://support.google.com/googleplay/android-developer/answer/9888072
- Google Play 用户数据政策：https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play 全文件访问权限政策：https://support.google.com/googleplay/android-developer/answer/10467955
- Android 前台服务说明：https://developer.android.com/develop/background-work/services/fgs
- Android Storage Access Framework：https://developer.android.com/training/data-storage/shared/documents-files
- Chaquopy Android 文档：https://chaquo.com/chaquopy/doc/current/android.html
- FFmpegKit 退役公告：https://github.com/arthenica/ffmpeg-kit

## 产品定位

Google Play 版定位为：面向用户自有、已授权、公开许可内容的视频地址解析与下载管理工具。

不定位为：

- 任意网站视频下载器。
- YouTube 下载器。
- 成人网站下载器。
- 绕过网站权限、DRM 或版权限制的工具。

应用名、商店简介、截图、帮助文案和默认示例不得使用成人站点名称；不得承诺可下载受版权保护内容；不得鼓励用户下载未经授权的音视频。

## Play 版范围调整

Windows 版允许非 YouTube 站点走 `yt-dlp` best-effort 路径；Android Play 版需要更保守：

- 用户只能手动输入地址，不提供站点目录、热门站点、成人站点入口或推荐。
- 内置文案只描述“公开视频页面地址”或“已授权内容地址”。
- 下载前必须提示用户确认自己有权保存该内容。
- 默认阻止已知成人域名和明显不适合 Play 审核的站点；拦截时使用中性中文说明，不显示站点内容。
- 不提供 DRM 绕过、登录绕过、付费墙绕过、批量抓取站点内容等能力。
- 如未来需要成人站点或更宽站点范围，只能作为非 Play 分发 flavor 另行设计，不进入本设计。

## 核心用户流程

1. 用户打开应用，首页是下载页，不做营销落地页。
2. 用户粘贴一个或多个视频页面地址。
3. 应用先做本地 URL 风险检查；命中 Play 版禁用域名时停止分析并显示说明。
4. 用户点击分析，应用在后台调用解析引擎获取标题、时长、缩略图、格式、字幕和错误类别。
5. 分析成功后，用户选择保存位置、下载模式、分辨率、码率偏好、容器和字幕行为。
6. 用户点击开始下载前，应用显示一次简短授权确认：仅保存自己拥有权利或获得授权的内容。
7. 下载进入队列，并通过前台服务显示系统通知、进度、暂停/取消入口。
8. 下载完成后写入本地历史；用户可打开、分享或导出文件。

## 下载模式

保留 Windows 版的核心下载模式，但移动端文案更明确：

- 仅音频：提取或保存音频流；需要转换时标记需要 ffmpeg。
- 仅视频：保存视频流，不合并音频。
- 视频加音频：优先选择站点提供的单文件格式；高分辨率通常需要分别下载视频流和音频流，再用 ffmpeg 合并。
- 视频加音频加字幕：下载字幕文件、嵌入字幕或烧录字幕；嵌入和烧录必须依赖 ffmpeg，烧录需要明确提示耗时更长。

格式选择规则：

- 只展示当前分析结果真实支持的分辨率、帧率、编码、音频码率和容器。
- 不支持的格式不允许选择；如保留可见状态，必须置灰并说明原因。
- “自动”是推荐默认值。
- 下载前显示“将实际下载/合并的格式摘要”，不要只显示用户偏好。

## Android UI 结构

移动端不复制 Windows 左侧导航栏，采用底部导航：

- 下载：地址输入、分析结果、保存位置、核心选项、开始下载。
- 格式：分辨率、帧率、编码、音频码率、容器、字幕行为。
- 队列：正在下载、等待、暂停、失败、完成；展示真实进度、速度、ETA。
- 历史：搜索、打开文件、分享、重新下载、删除记录。
- 设置：默认保存位置、并发数、cookies 文件、解析器更新、ffmpeg 状态、隐私和法律说明。

视觉方向：

- 使用 Jetpack Compose 和 Material 3。
- 保持 Windows 版浅色、干净、工具型的气质，但按手机操作优化。
- 首页优先完成真实操作，不做宣传首屏。
- 重要按钮靠近拇指区；复杂格式设置放到单独页面或底部抽屉。
- 错误提示必须给出下一步操作，不只显示原始日志。

## 技术路线

推荐路线：

- Android 原生外壳：Kotlin、Jetpack Compose、ViewModel、Room、Foreground Service。
- 解析与下载核心：通过 Chaquopy 集成 Python 版 `yt-dlp`，复用其模块能力，而不是打包 Windows `.exe`。
- 任务调度：前台服务负责长时间下载；UI 通过状态流观察队列。
- 本地数据库：Room 保存队列、历史、设置摘要和可恢复状态。
- 文件保存：默认保存到 app 私有目录；导出/另存使用 MediaStore 或 Storage Access Framework。
- ffmpeg：按 Android ABI 打包经过许可审查的 ffmpeg 可执行文件或库；不要依赖已退役的 FFmpegKit 作为长期基础。

不采用：

- PyInstaller、Windows `yt-dlp.exe`、Windows `ffmpeg.exe`。
- Android `MANAGE_EXTERNAL_STORAGE` 全文件访问权限。
- 启动时联网更新或阻塞式工具检查。
- 后台静默下载，必须有用户可见通知。

## yt-dlp 更新策略

Android Play 版不能按桌面方式随意下载并替换本机可执行文件。

第一版策略：

- 应用内置一个经过测试的 Python 版 `yt-dlp`。
- 启动不检查更新，不阻塞主界面。
- 设置页可手动检查解析器更新状态，但默认只提示“当前内置版本”。
- 如果要支持运行时更新，只允许更新 app 私有目录中的 Python 包资源，并必须验证版本、来源、回滚和失败保护。
- ffmpeg 更新只能跟随应用版本发布，不做运行时替换。

## 存储和权限

权限原则：

- 不申请 `MANAGE_EXTERNAL_STORAGE`。
- 不读取全盘文件。
- 不扫描用户媒体库。
- 只访问用户明确选择的保存目录、cookies 文件或导出目标。

保存策略：

- 默认下载到 app 私有目录。
- 用户可通过系统文件选择器选择导出位置。
- 需要长期写入用户选择目录时，使用 Storage Access Framework 保存 URI 授权。
- 历史记录只保存输出 URI、标题、时间、格式摘要和状态，不保存敏感请求头或 cookies 内容。

## cookies 与登录内容

Play 版第一版仍支持用户手动选择 cookies 文件，但必须弱化入口、强化安全提示：

- 只通过系统文件选择器选择 cookies 文件。
- 只保存 URI 或路径引用，不复制 cookies 内容。
- 不在配置、历史、日志、错误信息、界面中展示 cookies 内容。
- 不提供自动读取浏览器 cookies、Root 读取、WebView 偷取 cookies 等能力。
- 使用前显示醒目的敏感数据说明：cookies 可能代表登录状态，用户应只导入自己账号和自己授权内容所需的数据。
- 对需要登录、年龄限制、私有或权限受限内容，只给出“需要授权访问”的通用说明，不承诺一定可下载。

## 后台任务与进度

下载属于用户可感知的长任务，必须使用前台服务：

- 下载开始后创建系统通知。
- 通知展示文件名、百分比、速度、ETA、暂停/取消操作。
- 进度必须来自真实下载事件，不用假进度。
- 应用被切到后台、锁屏或短暂重启后，应尽量保持队列状态可恢复。
- 用户取消任务时，保留可续传的临时文件由用户设置决定。

并发：

- 默认并发 1，移动网络和电量更保守。
- 设置允许 1 到 3。
- 移动数据网络下载大文件时给出提示。

## 预览图与媒体预览

移动端第一版只承诺缩略图预览，不承诺边下边播：

- 缩略图加载走后台请求，失败不影响下载。
- 缩略图请求只传递安全请求头，例如 User-Agent、Accept、Accept-Language、Referer。
- 不传递 Cookie、Authorization 或其他敏感头到 UI 日志。
- 边下载边播放作为后续增强功能，不进入第一版 Play 上架验收。

## 隐私、合规和文案

应用内必须有：

- 隐私政策入口。
- 下载授权确认文案。
- cookies 敏感数据说明。
- 第三方组件和许可证说明。
- 不绕过 DRM、权限或版权限制的说明。

商店 listing 约束：

- 不使用 YouTube、Pornhub 等第三方品牌作为主卖点或截图示例。
- 不展示成人内容、成人站点 URL 或相关引导。
- 不承诺下载版权内容。
- 截图使用自制公开测试页面或自有演示内容。

## 测试和验收

单元测试：

- URL 风险检查和禁用域名拦截。
- 格式选择映射。
- 下载命令/参数构造。
- cookies URI 保存和敏感信息过滤。
- 历史记录读写。
- 队列状态流和并发限制。
- 真实进度解析。

集成测试：

- Chaquopy 能导入并调用 `yt-dlp`。
- 前台服务可启动、更新通知、取消任务。
- app 私有目录保存成功。
- SAF 导出成功。
- ffmpeg 存在和缺失两种能力分级。

真实设备 smoke：

- Android 12、Android 14、Android 15 至少各一台或模拟器组合。
- Wi-Fi 和移动网络下载提示。
- 后台下载通知持续显示。
- 分析失败、缩略图失败、下载失败都有中文提示。
- 不申请 Play 高风险存储权限。

Play 上架验收：

- AAB 构建成功。
- Data safety 表述与实际数据处理一致。
- 隐私政策可公开访问。
- 权限清单无 `MANAGE_EXTERNAL_STORAGE`。
- 商店文案和截图无成人站点、无版权侵权暗示。
- 第三方许可证随包或应用内可访问。

## 第一阶段 MVP 边界

第一阶段只做 Play 可审的最小闭环：

- Kotlin + Compose 应用壳。
- 下载、格式、队列、历史、设置五个底部导航页面。
- Chaquopy 调用 `yt-dlp` 分析一个公开授权测试链接。
- 单任务真实下载到 app 私有目录。
- 前台服务通知真实进度。
- 历史记录写入 Room。
- 通过系统文件选择器导出文件。
- cookies 入口只保存用户选择的 URI，不读取或展示内容。
- 成人域名和明显不适合 Play 审核的域名先拦截。
- ffmpeg 仅做存在/缺失检测和能力说明；合并/烧录可以进入第二阶段。

## 暂不进入第一阶段

- 成人站点支持。
- Play 版以外的旁加载 flavor。
- 边下载边播放。
- 自动读取浏览器 cookies。
- 运行时替换 ffmpeg。
- 全文件访问权限。
- 多平台共享 UI。
- 复杂批量/播放列表扩展。

## 后续流程

1. 用户审阅本设计。
2. 如确认，单独写 Android Google Play 版实现计划。
3. 实现计划必须从 Android 工程结构、最小真实下载闭环和 Play 合规检查开始。
4. Windows 版 bugfix、版本号、标签和发布继续单独收尾，不与 Android 开发提交混在一起。
