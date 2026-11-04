# Android 1.0.7 文件选择入口

## 变更

cookies 文件仍通过系统 ACTION_OPEN_DOCUMENT 选择并持久保存所选 URI 的读权限。Android 8 及以上指定初始位置为共享内部存储根目录，避免系统记忆的 Download 子目录成为默认入口。请求显示 DocumentsUI 存储设备导航，保留全部文件类型以及取消选择行为。Android 7 使用系统目录导航。

可从系统导航切换其提供的内部存储、外置存储或文档提供方。Android/data、Android/obb 和其他应用私有目录仍遵守系统访问规则。

## 验证与范围

- CookiesDocumentPickerTest 2 项通过，覆盖 API35 初始根目录、可打开文件、任意文件类型、Documents 文件返回与取消，以及 API24 兼容路径。
- SmokeUnitTest 8 项通过，版本为 1.0.7 (8)。
- assembleDebug 成功，aapt2 实读 1.0.7 / versionCode 8，apksigner verify 成功。
- 产物 dist/android/1.0.7/ytdl-android-1.0.7-debug.apk，SHA256 为 67904A2245B41DED302EEF77B4607F75CD20D4106C7BC9ECD4D7F5B7E61AC3BB。
- 本轮启动唯一 API37 模拟器后，Computer Use 两次返回未获准访问 qemu-system-x86_64。用户确认开启任意应用后重试仍被拒绝，因此尚无新版前台目录导航与实际选择的验收结论。
- 测试后通过 adb emu kill 关闭本轮模拟器，并清理 adb / Gradle 测试进程。
