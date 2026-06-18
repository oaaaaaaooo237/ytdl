# YTDL Android Google Play 版设计审计

日期：2026-06-19

审计对象：`docs/superpowers/specs/2026-06-19-ytdl-android-play-design.md`

## 审计依据

- Google Play 不当内容政策：https://support.google.com/googleplay/android-developer/answer/9878810
- Google Play 知识产权政策：https://support.google.com/googleplay/android-developer/answer/9888072
- Google Play 用户数据政策：https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play 全文件访问权限政策：https://support.google.com/googleplay/android-developer/answer/10467955
- Android 前台服务说明：https://developer.android.com/develop/background-work/services/fgs
- Android Storage Access Framework：https://developer.android.com/training/data-storage/shared/documents-files
- Chaquopy Android 文档：https://chaquo.com/chaquopy/doc/current/android.html
- FFmpegKit 退役公告：https://github.com/arthenica/ffmpeg-kit

## 第一轮审计

结论：设计方向正确，但尚未达到可直接写实现计划的标准。主要问题是若干 Play 合规和 Android 实现边界仍不够明确。

### 阻塞问题

1. `yt-dlp` 更新策略仍保留“运行时更新 Python 包资源”的口子。Play 上架版第一阶段应更保守，解析器代码更新只随应用版本发布；运行时最多检查版本状态和提示升级应用。
2. cookies 设计写了“只保存 URI 或路径引用、不复制 cookies 内容”，但 Chaquopy/`yt-dlp` 通常需要文件路径。需要明确：持久化只保存 URI；运行时可在 app 私有临时目录短暂物化 cookies 文件，任务结束后删除，且不得进入日志、历史、备份或 UI。
3. ffmpeg 设计前后不一致：技术路线说打包 Android ABI ffmpeg，MVP 又说只做检测和能力说明。第一阶段应明确不内置 ffmpeg 合并能力，只保留接口和缺失态；内置 ffmpeg 必须作为第二阶段独立法律/体积/ABI 审计任务。
4. 存储策略不够 Android 化。Android 11+ 对 SAF 目录选择有根目录、Download、Android/data、Android/obb 等限制；“选择保存位置”需要拆成 app 私有目录、MediaStore Downloads、ACTION_CREATE_DOCUMENT/OPEN_DOCUMENT_TREE 的不同路径。
5. 前台服务要求不够具体。需要明确 foreground service type、通知权限、取消/失败行为、真实进度来源，以及 Android 13+ 通知权限被拒绝时的降级说明。
6. Play 合规交付物不足。需要在设计中列出 Data safety 数据表、隐私政策、权限清单、商店文案/截图审查、成人域名拦截、知识产权声明等可验收项。
7. MVP 范围含混：核心流程支持多个 URL，但第一阶段 MVP 又写单任务下载。需要用阶段表明确第一阶段只实现单 URL/单活动任务，批量队列作为后续。
8. Chaquopy 平台约束未写入验收。需要明确 minSdk 至少 24，首版 ABI 只支持 `arm64-v8a` 和 `x86_64`，并把包体积作为验收风险。

### 非阻塞改进

1. URL 风险拦截应写成静态本地域名策略，避免运行时远程规则更新带来额外合规面。
2. 历史记录应明确不保存完整敏感 URL 查询串、请求头、cookies、Authorization。
3. 缩略图安全请求头规则应与 Windows 版一致，继续只允许 User-Agent、Accept、Accept-Language、Referer。

## 修订要求

- 调整设计文档，关闭上述 8 个阻塞问题。
- 修订后进行第二轮审计。
- 第二轮若无 P0/P1 阻塞，再判定可进入 Android 实现计划阶段。

## 修订记录

已修订设计文档：`docs/superpowers/specs/2026-06-19-ytdl-android-play-design.md`

修订内容：

- 关闭第一阶段 `yt-dlp` 运行时热更新口子，改为只能随应用版本发布。
- 明确 cookies 持久配置只保存 URI/路径引用；如 `yt-dlp` 需要文件路径，只允许任务运行时临时物化到 app 私有临时文件并删除。
- 明确第一阶段不内置 ffmpeg 合并能力，只保留接口、能力缺失态和第二阶段审计门槛。
- 将保存位置拆为 app 私有目录、MediaStore、`ACTION_CREATE_DOCUMENT`、SAF URI 授权，并写明 Android 11+ SAF 受限目录。
- 补充前台服务类型、Android 13+ 通知权限、真实进度、失败/取消/网络断开/权限失效状态区分。
- 补充 Data safety 初稿、隐私政策、权限声明、商店截图和 Play 预发布报告验收。
- 将第一阶段 MVP 收敛为单 URL、单活动下载任务，不做批量、播放列表或并发下载。
- 写入 Chaquopy 平台约束：`minSdk` 至少 24，首版 ABI 为 `arm64-v8a` 和 `x86_64`。

## 第二轮审计

结论：第一轮 8 个阻塞问题均已关闭，设计已达到可进入实现计划编写的标准。

核对结果：

- 未发现未完成占位词。
- Play 版第一阶段不再承诺运行时替换或热更新 `yt-dlp` 代码。
- ffmpeg 第一阶段边界一致：只做接口和能力状态，不承诺合并/烧录执行。
- cookies 规则可实现且不违反敏感数据边界：持久保存 URI，运行时短暂临时文件，任务结束删除。
- 存储、通知、前台服务、Data safety、权限声明、Play listing 和真实设备 smoke 均有明确验收项。
- 第一阶段 MVP 范围已经收敛为单 URL、单活动任务，不与批量/并发路线冲突。

剩余非阻塞事项：

- 实现计划需要把隐私政策 URL 和 Play Console Data safety 表述作为专门任务。
- 实现计划需要把 Android 工程结构、Chaquopy 验证、前台服务真实下载、MediaStore/SAF 导出作为前四个技术风险优先任务。
