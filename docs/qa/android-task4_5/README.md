# Android Task 4.5 GUI 可视验证记录

日期：2026-06-20

## 验证范围

本记录对应 Android Play MVP Task 4.5：将占位页替换为接近 `docs/android-gui-reference-v3.png` 的五页 GUI shell，并验证它不是单页占位。

## 前台可见操作

- 设备：`ytdl_api37_play_x86_64`，`emulator-5554`。
- 应用：`com.garyapp.ytdl/.MainActivity`。
- 操作方式：先通过 `adb install -r android\app\build\outputs\apk\debug\app-debug.apk` 安装当前 debug APK，再通过 `adb shell am start -n com.garyapp.ytdl/.MainActivity` 启动。
- 前台验证：使用 Computer Use 激活可见 Android Emulator 窗口后，手动式坐标点击底部 5 个页面按钮：下载、格式、队列、历史、设置。
- 滚动验证：在格式页滚动到“实际下载 / 应用选择”区域；在队列页滚动到失败任务卡片。
- 截图方式：每次前台点击或滚动后，使用 `adb shell screencap -p` 保存设备截图，再 `adb pull` 到本目录；所有截图均重新检查 PNG 文件头。

## 截图证据

- `01-download.png`：下载页，包含地址输入、分析、预览卡片、保存位置、下载模式、授权确认、开始下载。
- `02-formats.png`：格式页首屏，包含下载模式、分辨率、合并标记和格式选项入口。
- `02-formats-scrolled.png`：格式页滚动后，包含“实际下载：720p MP4 单文件”和“应用选择”。
- `03-queue.png`：队列页首屏，包含下载进行中、真实进度样式、速度、预计剩余、暂停和取消。
- `03-queue-scrolled.png`：队列页滚动后，包含等待、完成和失败任务卡片。
- `04-history.png`：历史页，包含搜索、筛选、打开、分享、删除和本地记录卡片。
- `05-settings.png`：设置页，包含保存位置、Cookies 文件、解析器版本、ffmpeg 能力、通知、隐私、网站提示和外观颜色。

## 自动化支撑验证

- 新增 `YtdlAppUiTest` 使用 UIAutomator 运行真实 Activity，验证五页切换、格式页滚动和队列页滚动；不再依赖只读静态标签 Map。
- API37 上 `androidx.compose.ui:ui-test-junit4` 触发 Espresso `InputManager.getInstance` 兼容问题，因此本任务改用官方 Google Maven 当前 release `androidx.test.uiautomator:uiautomator:2.4.0-rc01`。
- `.\gradlew.bat :app:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:assembleDebug`：`BUILD SUCCESSFUL`。
- `.\gradlew.bat :app:connectedDebugAndroidTest`：API37 模拟器运行 3 个测试，`BUILD SUCCESSFUL`。
