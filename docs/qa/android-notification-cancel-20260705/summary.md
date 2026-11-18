# Android 通知栏取消辅助证据

日期：2026-07-05

本轮补强 `YtdlAppUiTest.notificationActionCanCancelRunningForegroundTask`，覆盖真实下载任务的系统通知栏取消路径：

1. 授予 Android 13+ 通知权限。
2. 用真实链接 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 分析视频。
3. 选择 1080p 视频+音频，启动真实前台下载。
4. 打开系统通知栏，确认 `YTDL 下载任务` 正在下载。
5. 展开该通知后点击 `取消` action。
6. 断言 Room 最新历史进入 `canceled`，且不能误写成 `completed`。

验证记录：

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest#notificationActionCanCancelRunningForegroundTask"
```

结果：`BUILD SUCCESSFUL`，1/1 通过；P3 稳定性修正后复跑为 `BUILD SUCCESSFUL in 1m 14s`。

同轮全量回归：

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.ui.YtdlAppUiTest"
```

结果：单元测试和 debug 构建均为 `BUILD SUCCESSFUL`；API37 `YtdlAppUiTest` 7/7 通过，P3 稳定性修正后复跑为 `BUILD SUCCESSFUL in 11m 37s`。

截图：

- `09-notification-before-cancel.png`：通知栏中可见 `YTDL 下载任务`。
- `09-notification-expanded.png`：展开通知后可见取消 action。
- `09-notification-canceled.png`：取消后回到应用状态。

边界：这是 API37 connected/UIAutomator 辅助证据，不替代最终 Computer Use 前台可见全量验收。
