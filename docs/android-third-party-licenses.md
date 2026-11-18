# Android 第三方许可证草案

日期：2026-06-21

本文件记录 Android Play MVP 当前已接入或随 APK 运行时使用的主要第三方组件和许可证口径。它是发布前许可证说明的工作稿；正式 AAB 发布前仍需要从最终打包产物生成完整 notice 清单并核对所有传递依赖。

## 当前运行时组件

| 组件 | 当前版本/来源 | 用途 | 许可证口径 | 本轮证据 |
| --- | --- | --- | --- | --- |
| YTDL App 自有代码 | 仓库 `LICENSE` | Android 应用外壳和业务代码 | MIT License | `LICENSE` |
| yt-dlp | `yt-dlp==2026.3.17`，Chaquopy pip 安装 | 视频分析、格式枚举、下载、字幕文件下载 | wheel 内 `licenses/LICENSE` 为 public-domain/Unlicense 风格文本 | `android/app/build/python/pip/debug/common/yt_dlp-2026.3.17.dist-info/licenses/LICENSE` |
| Chaquopy | `com.chaquo.python` 17.0.0 | Android 内嵌 Python 与 pip 包打包 | MIT License | 本地 Maven POM `com.chaquo.python:gradle:17.0.0` |
| Kotlin Standard Library | `org.jetbrains.kotlin:kotlin-stdlib:2.3.0` | Kotlin 运行时 | Apache-2.0 | 本地 Maven POM |
| AndroidX Activity/Core/Lifecycle/Room | Activity Compose 1.13.0、Core 1.18.0、Lifecycle 2.9.4、Room 2.8.4 等 | Activity、生命周期、数据库、Android 基础库 | Apache License 2.0 | `gradle :app:dependencies` 与本地 Maven POM |
| Jetpack Compose | Compose BOM 2026.06.00；Compose UI 1.11.3；Material3 1.4.0 | 五页 GUI 和 Material 组件 | Apache License 2.0 | `gradle :app:dependencies` 与本地 Maven POM |

## 测试期依赖

以下依赖只用于单元测试或 instrumentation，不应随正式 APK 作为运行时能力展示，但正式 notice 生成时仍应以最终构建产物为准核对：

- JUnit 4.13.2
- Robolectric 4.16
- AndroidX Test / UIAutomator / Compose UI Test
- Room Testing

## 明确未打包内容

- Android MVP1 不打包 FFmpeg、FFmpegKit、mobile-ffmpeg 或其他 native FFmpeg AAR。
- Android MVP1 不运行时下载或替换 yt-dlp 代码。
- Windows 版 bundled `ffmpeg.exe`、`yt-dlp.exe` 不适用于 Android 包体。

## 发布前必须复核

1. 用 release/AAB 的最终依赖树重新生成完整第三方 notice，不只依赖 debug 构建。
2. 核对 Chaquopy 打包的 Python 运行时、标准库和任何 native 库的许可证文件是否需要在应用内或随包展示。
3. 核对 yt-dlp wheel 的许可证文件随最终 APK/AAB 保留或可在应用内打开。
4. 若 MVP2 引入 FFmpeg 或等价媒体处理链，必须重新审计 LGPL/GPL/nonfree、ABI、16KB page size、包体积和源码/notice 要求。
