# Android 1.0.8 网站 cookies 与 App 内登录

## 实现

- 设置的 Cookies 文件入口改为按网站管理。提供 X、YouTube 空配置入口，用户可填写名称和域名添加网站。
- 支持选择文件、登录、清除、检查文件可读性。未配置网站默认匿名。旧版全局引用仍展示为“旧版通用”，未匹配网站时沿用，用户可主动清除。
- 域名经过规范化，X/Twitter 和 YouTube/youtu.be 使用对应规范化域名。子域名按点边界匹配，优先最长域名；例如 x.com.evil.example 不会命中 x.com。
- 新下载任务固定入队时选择的文件引用，暂停和重启恢复不因修改设置而换成其他文件。恢复加密载荷升级 v2 并兼容 v1。
- App 内登录页面由用户操作 HTTPS WebView，明确点击“保存登录状态”后，仅读取所配置域名的 cookies。长期记录采用现有 Android Keystore / AES-GCM 加密设施，放在 noBackup 私有目录。设置只保存内部引用，任务运行时解密物化到私有临时文件，用后删除。
- 清除网站登录会移除该网站的加密记录。导入文件不删除用户源文件。退出登录页清理 App 自身 WebView cookies、缓存和 WebStorage。其他浏览器的私有 cookies 不在此流程的读取范围。
- 已有匿名下载网站无须重新创建 cookies。部分网站可能拒绝 WebView 登录，仍可使用用户主动导出的文件。

## 验证

- Android testDebugUnitTest 全量 499 项通过，0 failures / errors / skipped。
- 新增验证覆盖域名边界和别名、子域优先级、旧版引用兼容、重启恢复网站映射、清除隔离、排队后设置变更、恢复载荷 v1 兼容、加密记录与注入内容拒绝。
- Compose 测试覆盖添加网站、选择文件回调、登录回调、单网站清除、YouTube 默认匿名文案。
- assembleDebug 成功，aapt2 实读 1.0.8 / versionCode 9，apksigner verify 成功。
- 本轮 API37 模拟器窗口被 Computer Use 拒绝访问，返回未获准使用 qemu-system-x86_64，因此本轮只有单元/Compose 测试及构建证据。真实网站 App 内登录、保存后下载，以及系统文件选择器前台操作尚待用户参与复核。不得将这些自动测试表述为真实登录或 GUI 验收通过。
- 测试结束关闭本轮模拟器、adb 和临时 Gradle 进程。
