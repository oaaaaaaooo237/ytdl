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

## 重启宿主后的前台复核

用户明确授权 Android Emulator 持久应用访问后，重启宿主再次测试，Computer Use 已能操作可见 API37 窗口。安装并启动交付的 1.0.8 (9) 后完成以下实际流程。

- 设置 → Cookies 文件 → 按网站管理，X 和 YouTube 均显示“未配置 · 匿名访问”。
- X → 选择文件，系统选择器直接显示内部存储根目录。进入 Documents 选择 site-picker-test.txt（仅包含 Netscape 注释头，没有登录凭证），返回后只有 X 显示此文件，YouTube 继续匿名。
- 点击“检查可读性”，界面显示文件可读取，并说明登录有效性需实际请求验证。清除 X 后恢复匿名。
- 使用 Gboard 在前台逐键输入网站名称 test 和域名 example.com，点击添加网站，滚动列表出现 test / example.com，其他站点仍匿名。
- X → 登录，App 内 HTTPS WebView 加载 x.com，点击 Sign in 后出现邮箱/用户名登录表单。没有遇到 WebView 拒绝提示。
- 实际账号认证与保存已交由用户操作，尚待保存后下载的端到端复核，不能仅凭表单打开宣称登录后下载通过。

## 1.0.9 登录误保存修复

用户实际尝试 Google 联合登录后出现白屏并点击保存。对 App 私有加密记录执行一次只检查 cookie 名称的诊断，确认记录仅包含游客标记，不含 auth_token。诊断不输出任何 cookie 值；临时诊断测试源文件在定位后删除。

- X App 内登录保存现要求非空 auth_token 和 ct0；游客 cookies 或未完成登录不再保存为登录状态。提示用户完成网站自身账号登录与必要验证。
- 登录页增加 Google 等联合登录的限制说明。Google 官方 OAuth 政策要求使用安全浏览器，禁止在开发者控制的嵌入式用户代理中进行 Google OAuth。参考 https://developers.google.com/identity/protocols/oauth2/policies#use-secure-browsers。
- 1.0.9 (10) 全量 500 项单测通过。assembleDebug、aapt2 版本核验、apksigner 签名校验通过。
- APK 为 dist/android/1.0.9/ytdl-android-1.0.9-debug.apk，SHA256 为 1AE1FF64518230F66A1C371D3E15E8F339767D74694CA571DF1778F59990C7F6。
- 前台在未登录 X 页面点击保存，页面没有作为成功返回；取消后原文件导入配置仍保留，未被游客数据覆盖。
- 用户明确授权使用手机上的 Download/x-cookies.txt，临时复制到模拟器并通过系统选择器绑定至 X，YouTube 继续显示匿名。用 Gboard 重新逐键输入原帖 2109772550783856786，实际分析成功并以视频＋音频模式启动 task-0451475ba24b488eb70914875044e053，视频和音频由同一任务顺序下载。
- 本次不证明 Google 联合登录成功；文件导入与 App 内登录是两种不同来源。前台新增网站、文件选择、清除、可读性检查已验证。
- 最终前台历史出现一条“视频＋音频 · 完成 · 1066p”记录。直接复制 App 生成文件到 .qa-data/X-site-cookies-1.0.9.mp4，核验 480×1066 H.264、AAC、81.269841 秒、9396242 字节，完整解码通过。任务最终只有一个 MP4，.parts 已清理。SHA256 为 cfc51076f42468b0b1b0ba2be3d52de7f455ac00ed9b5b4ca0c910633058e970。
- 验收结束移除模拟器导入副本与对应引用、无敏感信息的 Documents 测试文件和自定义 test/example.com 配置，卸载临时诊断 test APK。保留用户真机 Download/x-cookies.txt 原文件；关闭模拟器、adb 和构建测试进程。
