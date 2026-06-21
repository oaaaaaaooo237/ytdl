# Android 开发与测试环境

日期：2026-06-19；状态复核：2026-06-21

本文件记录 Android Play 版本机环境状态。Android 产品需求与设计仍以 `docs/superpowers/specs/2026-06-19-ytdl-android-play-design.md` 为准，GUI 基准图以 `docs/android-gui-reference-v3.png` 为准。应用实现进度以 `docs/superpowers/plans/2026-06-19-ytdl-android-play-mvp.md` 和 `docs/qa/android-mvp-smoke.md` 为准；本文件不再作为功能完成度账本。

## 明确结论

本机 Android 开发和模拟器测试环境已准备好，可继续构建、安装和运行当前 Android 工程。

已实测通过：

- JDK 17、Android SDK、adb、emulator、sdkmanager、avdmanager 可用。
- Android 12/API 31、Android 15/API 35、Android 16/API 36.1、Android 17/API 37.0 的 x86_64 Google Play AVD 均能启动到 `sys.boot_completed=1`。
- API 37 AVD 可安装并启动临时探针 APK。
- 临时 Android 探针工程已成功构建：Gradle 9.4.1、AGP 9.2.1、`compileSdk = 37`、`targetSdk = 37`、Chaquopy 17.0.0、Room 2.8.4、Compose BOM 2026.06.00、activity-compose 1.13.0、Compose Compiler plugin 2.3.0。
- Chaquopy 构建已显式使用项目 venv Python：`D:\garyapp\ytdl\.venv\Scripts\python.exe`，版本为 Python 3.12.13。系统 PATH 中没有 `python`，后续 Android 工程不得依赖 PATH 自动发现 Python。

环境之外仍未完成，但边界明确：

- 尚未准备真实 `arm64-v8a` 手机；小米 14 或同级设备仍是第一阶段真机验收目标。
- 最终 Computer Use 前台可见模拟器全流程验收尚未通过；当前阻断见 `docs/qa/android-mvp-smoke.md`。
- 尚未确认 Google Play 发布签名、隐私政策 URL、Data safety 最终填写口径和商店截图素材。
- MVP2 若加入字幕嵌入、字幕烧录或三合一输出，仍需重新审计媒体处理链、许可证、ABI、16KB page size 和包体积。

## 已确认工具链

| 项目 | 实测状态 |
| --- | --- |
| JDK | `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot` |
| Gradle 9.4.1 | `D:\DevTools\gradle-9.4.1\bin\gradle.bat`，Android 工程应以此版本生成/提交 Gradle Wrapper |
| Gradle 8.11.1 | `D:\DevTools\gradle-8.11.1\bin\gradle.bat`，已安装但不作为 Android 17 最终口径 |
| Gradle 8.10.2 | `D:\DevTools\gradle-8.10.2\bin\gradle.bat`，已确认不足以运行 AGP 8.9.3+ |
| Android Studio | `D:\Softwares\Android\Android Studio` |
| Android SDK | `D:\Softwares\Android\SDK` |
| SDK 环境变量 | `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 指向 `D:\Softwares\Android\SDK` |
| adb | `D:\Softwares\Android\SDK\platform-tools\adb.exe`，版本 36.0.2 |
| emulator | `D:\Softwares\Android\SDK\emulator\emulator.exe`，版本 36.6.11 |
| cmdline-tools | 优先使用 `D:\Softwares\Android\SDK\cmdline-tools\21.0\bin` |
| WHPX | `emulator -accel-check` 返回 WHPX 可用 |
| Python for Chaquopy | `D:\garyapp\ytdl\.venv\Scripts\python.exe`，Python 3.12.13 |

## 已安装 SDK 包

关键已安装包：

- `build-tools;37.0.0`
- `platforms;android-31`
- `platforms;android-35`
- `platforms;android-36`
- `platforms;android-37.0`
- `platforms;android-CinnamonBun`
- `system-images;android-31;google_apis_playstore;x86_64`
- `system-images;android-35;google_apis_playstore;x86_64`
- `system-images;android-36.1;google_apis_playstore;x86_64`
- `system-images;android-37.0;google_apis_playstore_ps16k;x86_64`

- `cmdline-tools;21.0`

`cmdline-tools;latest` 仍保留 20.0，但脚本已优先使用独立安装的 21.0。不要再用运行中的 `latest` 目录自更新自己。

## 已创建并实测的 AVD

| AVD | Target | 验证结果 |
| --- | --- | --- |
| `ytdl_api31_play_x86_64` | `android-31` | 已启动到 `sys.boot_completed=1` |
| `ytdl_api35_play_x86_64` | `android-35` | 已启动到 `sys.boot_completed=1` |
| `ytdl_api36_play_x86_64` | `android-36.1` | 已启动到 `sys.boot_completed=1` |
| `ytdl_api37_play_x86_64` | `android-37.0` | 已启动到 `sys.boot_completed=1`，已安装并启动探针 APK |

注意：本机 `emulator -list-avds` 和 `avdmanager list avd` 仍可能不列出这些 AVD，但直接用 `emulator -avd <name>` 启动已实测可用。后续不得仅凭 list 命令为空判断 AVD 不存在；应检查 `C:\Users\garyr\.android\avd\<name>.ini` 并直接启动验证。

## Android 17 构建口径

固定采用：

- Gradle Wrapper：9.4.1
- Android Gradle Plugin：9.2.1
- `compileSdk = 37`
- `targetSdk = 37`
- `minSdk = 24`
- Chaquopy：17.0.0
- Chaquopy Python：3.12，`buildPython("D:/garyapp/ytdl/.venv/Scripts/python.exe")`
- Room：2.8.4
- Compose BOM：2026.06.00
- activity-compose：1.13.0
- Compose Compiler Gradle plugin：2.3.0
- `gradle.properties` 至少包含：
  - `android.useAndroidX=true`
  - `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`
  - `org.gradle.parallel=false`
  - `org.gradle.workers.max=2`

已排除的错误路线：

- Gradle 8.10.2：AGP 8.9.3 要求 Gradle 8.11.1 以上，不能用。
- AGP 8.9.3 + `compileSdk = 37`：会查找 `platforms;android-37`，但本机官方 SDK 安装的是 `platforms;android-37.0`。
- AGP 8.9.3 + `compileSdkPreview = "CinnamonBun"`：能识别预览平台，但 Room/AndroidX AAR metadata 不把它视作满足 `minCompileSdk >= 34` 的数值 SDK。
- AGP 9.4.0-alpha01：要求 Gradle 9.5.0，且超出 Chaquopy 17.0.0 文档支持的 AGP 9.2.x 范围，不作为当前路线。

## 验证证据

本轮实测命令和结果：

- `powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1`：JDK、Gradle、Android SDK 工具、已安装包、设备检查完成。
- `sdkmanager --install ...`：补齐 API31、API35、API37 平台/系统镜像、build-tools 37、emulator 36.6.11、Gradle 相关依赖缓存。
- `emulator -accel-check`：WHPX 可用。
- `emulator -avd ytdl_api31_play_x86_64`、`ytdl_api35_play_x86_64`、`ytdl_api36_play_x86_64`、`ytdl_api37_play_x86_64`：均启动到 `sys.boot_completed=1`。
- `D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebug`：临时探针工程最终 `BUILD SUCCESSFUL`。
- API37 AVD 安装并启动探针 APK：`adb install` 返回 `Success`，`am start` 后 `topResumedActivity=... com.example.ytdlprobe/.MainActivity`。

临时探针工程位于 `.qa-android-env/gradle-probe`，该目录已被 `.gitignore` 忽略，仅作本机环境验证。

## 仓库内环境脚本

使用：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1
```

创建或复查矩阵 AVD：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\android_env.ps1 -CreateMatrixAvds
```

脚本会：

- 解析 Android SDK 位置。
- 设置 `ANDROID_HOME`、`ANDROID_SDK_ROOT`、`ANDROID_AVD_HOME`。
- 临时把 `platform-tools`、`emulator`、`cmdline-tools\21.0\bin` 加入 PATH；如果 21.0 不存在，才回退到 `cmdline-tools\latest\bin`。
- 优先识别 `D:\DevTools\gradle-9.4.1\bin\gradle.bat`。
- 检查 `java`、`javac`、Gradle、`adb`、`emulator`、`sdkmanager`、`avdmanager`。
- 列出关键 SDK 包和 AVD `.ini` 文件。
- 创建 API31/API35/API36/API37 四个矩阵 AVD。

## 下一步开发前置条件

正式 Android 工程已经创建，后续仍必须使用 Gradle Wrapper/固定 Gradle 9.4.1 和上述 Gradle/AGP/Chaquopy/Room/Compose 版本，不再使用系统 Gradle 8.10.2。

第一阶段实现开始后，构建验收命令至少包括：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

真实设备验收仍需要小米 14 或同级 `arm64-v8a` 手机。模拟器矩阵已经准备好，不能替代真机通知、后台下载、文件导出和原生媒体合并性能验证；MVP2 若加入 FFmpeg 或等价字幕处理链，还需要另做真机性能、许可证、ABI 和包体积验证。
