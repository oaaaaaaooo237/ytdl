# Android Play Data safety 草案

日期：2026-06-20

## 当前实现口径

- 开发者服务器：当前 Android MVP 不接入自有服务器，不向开发者服务器收集或上传用户数据。
- 视频地址：用户输入的视频页面地址只用于本机分析和下载请求；本地历史不保存原始 URL 或敏感 query。
- cookies：设置只保存用户选择的 cookies 文件 URI/路径引用，不保存 cookies 内容。下载任务运行时会在 App 私有缓存中创建一次性临时 cookies 文件，任务完成、失败或取消后删除。
- 输出文件：默认保存到 App 私有目录；当前控制器层已支持系统 `ACTION_CREATE_DOCUMENT` / MediaStore 元数据和 App 私有输出复制到目标流，不申请全文件访问权限。前台导出入口和系统导出 UI 留到 M9/T12 验证。
- 本地历史：仅保存标题、安全来源摘要、格式摘要、状态、输出引用和已脱敏错误摘要；不保存 cookies 内容、Authorization、原始命令行或敏感 query。
- 日志和错误：面向用户和持久化的错误文本通过脱敏处理，不展示 cookies、Authorization、bearer token 或 cookies 文件路径。

## Play Console 填写建议

- Data collected：无开发者收集的数据。
- Data shared：无开发者共享的数据。用户主动下载时，目标网站会按其服务规则接收视频 URL 请求和用户提供的 cookies 授权信息。
- Data processed ephemerally：cookies 临时副本仅用于单次任务，终态后删除。
- Data encrypted in transit：App 不传输到自有服务器；访问公开视频站点时使用用户提供的 URL 协议，推荐 HTTPS。
- User deletion：清除 App 数据或卸载 App 会移除本地设置、历史和私有输出；后续发布前仍需用前台 UI 验证历史删除和导出入口。

## 权限说明

- `INTERNET`：分析和下载用户输入的视频页面。
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC`：下载期间保持前台可感知任务。
- `POST_NOTIFICATIONS`：Android 13+ 下载状态通知；拒绝通知时，App 内队列状态仍应可见。
