# Android 开发与测试环境

日期：2026-06-19

本文件记录 Android Play 版进入实现前的本机环境状态、缺口和建议布置方式。Android 产品需求与设计仍以 `docs/superpowers/specs/2026-06-19-ytdl-android-play-design.md` 为准，GUI 基准图以 `docs/android-gui-reference-v3.png` 为准。

## 当前目标

先准备 Android 开发和测试环境，不实现交互功能。

环境应支持后续完成：

- Kotlin + Jetpack Compose 应用壳。
- Chaquopy 调用 Python 版 `yt-dlp` 的验证。
- Room、前台服务、MediaStore/SAF、通知权限等 Android 能力测试。
- AAB 构建、模拟器 smoke、后续真实设备 smoke。

## 已检查到的本机环境

| 项目 | 当前状态 |
| --- | --- |
| JDK | 已有 JDK 17：`C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot` |
| Gradle | 已有 Gradle 8.10.2：`D:\DevTools\gradle-8.10.2\bin\gradle.bat` |
| Android Studio | 已安装：`D:\Softwares\Android\Android Studio` |
| Android SDK | 已安装：`D:\Softwares\Android\SDK` |
| SDK 环境变量 | `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 均指向 `D:\Softwares\Android\SDK` |
| adb | 已安装：`D:\Softwares\Android\SDK\platform-tools\adb.exe` |
| emulator | 已安装：`D:\Softwares\Android\SDK\emulator\emulator.exe`，但原 PATH 未包含该目录 |
| build-tools | 已有 34.0.0、35.0.0、36.1.0 |
| platforms | 已有 android-34、android-35、android-36 |
| system image | 已有 `system-images;android-36.1;google_apis_playstore;x86_64` |
| AVD | 已创建基线 AVD：`ytdl_api36_play_x86_64` |
| 真机 | 检查时 `adb devices` 未发现连接设备 |

## 当前缺口

1. 仓库内尚无 Android 工程骨架。
2. 基线 AVD 可被 `emulator -avd ytdl_api36_play_x86_64` 直接启动，WHPX 硬件加速检查通过；但 `emulator -list-avds` 和 `avdmanager list avd` 在当前命令行上下文没有正常列出 AVD，后续完整开机和安装测试要继续复查。
3. 设计要求后续 smoke 覆盖 Android 12、Android 14、Android 15；本机当前只有 Android 36.1 的 x86_64 Play 系统镜像，缺少 API 31、34、35 的系统镜像/AVD。
4. Gradle 缓存中尚未发现 Chaquopy Gradle 插件；首次创建 Android 工程并集成 Chaquopy 时需要联网解析依赖。
5. Gradle 缓存中尚未发现 Room runtime；首次实现 Room 时需要联网解析依赖。
6. 尚未配置 Android 工程的 Gradle wrapper；为了版本隔离，后续 Android 工程应自带 `gradlew`。
7. 尚未确认 Google Play 发布签名、隐私政策 URL、Data safety 填写口径和商店截图素材。

## 仓库内环境脚本

使用：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
```

脚本会在当前 PowerShell 进程内：

- 解析 Android SDK 位置。
- 设置 `ANDROID_HOME`、`ANDROID_SDK_ROOT`。
- 临时把 `platform-tools`、`emulator`、`cmdline-tools\latest\bin` 加入 PATH。
- 检查 `java`、`javac`、`gradle`、`adb`、`emulator`、`sdkmanager`、`avdmanager`。
- 列出已安装 SDK 包、已连接设备和 AVD。

创建当前已安装镜像可用的基线 AVD：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1 -CreateBaselineAvd
```

默认创建：

- AVD 名称：`ytdl_api36_play_x86_64`
- 系统镜像：`system-images;android-36.1;google_apis_playstore;x86_64`
- 设备配置：`medium_phone`

这个 AVD 只作为早期工程启动和 smoke 基线，不替代 Android 12/14/15 验收组合。

## 后续环境布置建议

第一步，先创建并验证基线 AVD：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1 -CreateBaselineAvd
```

当前已执行过该步骤；如本机 AVD 被删除，可用同一命令重建。

基线 AVD 启动探针已验证：

- `emulator -accel-check`：WHPX 可用。
- `emulator -avd ytdl_api36_play_x86_64 -no-window -no-audio -no-boot-anim -no-snapshot`：能找到 system image 并开始启动。
- 12 秒短探针内设备仍为 `offline`，尚未等待完整开机，因此不作为 connected test 通过证据。

第二步，创建 Android 工程骨架后，优先验证：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

第三步，补齐 Play 版验收用系统镜像：

- Android 12：API 31。
- Android 14：API 34。
- Android 15：API 35。

第四步，至少准备一台真实 Android 设备，优先覆盖 `arm64-v8a`，用于验证前台服务通知、后台下载、文件导出和移动网络提示。

## 阶段边界

当前环境准备阶段不做：

- Android UI 交互实现。
- `yt-dlp` 下载逻辑实现。
- ffmpeg 合并、烧录或转码能力。
- Play 版以外的旁加载 flavor。
- 成人站点或全站点下载支持。
