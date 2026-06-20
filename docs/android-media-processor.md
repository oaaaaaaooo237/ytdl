# Android MediaProcessor 路线记录

日期：2026-06-20

## 当前结论

后续开发顺序调整为先实现媒体能力，再绑定 GUI 流程。

第一步不再继续反复测试 `需 ffmpeg 合并` 的界面标签，而是先实现真实可运行的 `MediaProcessor`：

1. 先用 Android 原生 `MediaExtractor + MediaMuxer` 实现兼容流的音视频合并。
2. 用 yt-dlp 下载明确的 video-only format id 和 audio-only format id。
3. 在 API37 模拟器中把两个文件合并成一个 MP4，并用 `MediaExtractor` 证明输出文件同时包含视频轨和音频轨。
4. 再把这个能力接入 GUI 的下载、队列、历史和最终 Computer Use 前台可视验收。
5. Android ffmpeg 仍然是第一阶段必做内容，用于字幕嵌入、字幕烧录和原生 muxer 不支持的复杂处理；不能因为先实现原生合并就删除 ffmpeg 目标。

## M1/T8A 合同状态

当前已建立第一层媒体处理合同：

- `MediaMergeRequest` 描述 video-only 输入、audio-only 输入、受控输出文件、输出容器和预期 format id。
- `MediaProcessingResult` 记录输出文件、写入字节数、视频轨数量、音频轨数量和处理器名称。
- `MediaProcessor.mergeVideoAndAudio()` 返回 `Result<MediaProcessingResult>`，调用方必须处理失败。
- `NativeMuxerMediaProcessor` 当前只承担合同校验和路线声明；真实 `MediaExtractor + MediaMuxer` 样本复制将在 M2/T8B 实现。
- `NativeMuxerMediaProcessor` 明确 `supportsSubtitleEmbed=false`、`supportsSubtitleBurn=false`；字幕嵌入和字幕烧录必须路由到后续 Android ffmpeg processor。

当前合同校验边界：

- video/audio 输入必须是两个不同的可读取文件。
- outputFile 不能覆盖任一输入文件。
- outputFile 必须位于构造处理器时指定的受控输出根目录内。
- 第一阶段原生 muxer 合同只允许 MP4 输出。
- 预期 video/audio format id 不能为空。

## M2/T8B 原生合并状态

当前已实现 `NativeMuxerMediaProcessor` 的第一阶段真实合并能力：

- 使用 Android `MediaExtractor` 读取视频输入文件中的第一条视频轨。
- 使用 Android `MediaExtractor` 读取音频输入文件中的第一条音频轨。
- 使用 Android `MediaMuxer` 把两条已编码轨道复制到新的 MP4 输出文件。
- 不使用 shell，不调用 ffmpeg，不做转码。
- 输出成功后返回 `MediaProcessingResult`，包含输出文件、字节数、视频轨数量、音频轨数量和 processor 名称。
- 如果输入缺少视频轨或音频轨，返回失败，不把输出文件标记为成功。

本轮 API37 instrumentation 证据：

```text
命令：cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.media.NativeMuxerMediaProcessorInstrumentedTest"
结果：BUILD SUCCESSFUL，tests=2 failures=0 errors=0 skipped=0
设备：sdk_gphone16k_x86_64，SDK 37
证据：NATIVE_MUXER_T8B sdk=37 device=sdk_gphone16k_x86_64 videoInput=4583 audioInput=8497 output=/data/data/com.garyapp.ytdl/cache/native-muxer-t8b/merge-success/outputs/merged.mp4 bytes=9848 videoTracks=1 audioTracks=1
报告：android/app/build/outputs/androidTest-results/connected/debug/TEST-ytdl_api37_play_x86_64(AVD) - 17-_app-.xml
Logcat：android/app/build/outputs/androidTest-results/connected/debug/ytdl_api37_play_x86_64(AVD) - 17/logcat-com.garyapp.ytdl.media.NativeMuxerMediaProcessorInstrumentedTest-mergesRuntimeGeneratedVideoOnlyAndAudioOnlyStreamsIntoMp4.txt
```

后续仍未完成：

- 还没有 Android ffmpeg 字幕嵌入/烧录能力；这是 M5/T8E。

## M3/T8C 指定格式分离下载状态

当前已实现 yt-dlp 明确 format id 的分离下载能力：

- Python bridge 新增 `download_format(url, output_dir, format_id, role, cookies_path, progress_listener)`。
- `download_format()` 的 yt-dlp `format` 只使用调用方传入的明确 `format_id`，不使用 `18/worst` 兜底。
- `format_id` 必须是单个明确格式编号，不能包含 `/`、`+`、`,`、筛选器或其他 yt-dlp selector/fallback 表达式；`best`、`worst`、`bestvideo`、`bestaudio`、`worstvideo`、`worstaudio`、`bv`、`ba`、`wv`、`wa`、`all`、`mergeall` 等 selector alias 也会被拒绝。
- 输出文件名包含视频 id、format id 和 role，避免 video/audio 下载互相覆盖。
- 返回成功前会确认输出文件存在且大小大于 0；Kotlin JSON parser 也拒绝空路径或 0 字节的成功结果。
- split 下载文件定位只接受当前视频 id、format id 和 role 匹配的输出文件，避免在 candidate path 缺失时捡到其他视频的旧文件。
- Kotlin bridge 新增 `downloadFormat()`，使用 `DownloadFormatRole.Video` / `DownloadFormatRole.Audio` 约束 role。
- 单元测试覆盖 split 下载 JSON 解析、空 format id 拒绝、未知 role 拒绝。

本轮 API37 instrumentation 证据：

```text
命令：cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest#downloadsRequiredYoutubeSmokeUrlSplitFormats"
结果：BUILD SUCCESSFUL，tests=1 failures=0 errors=0 skipped=0，最近时间戳 2026-06-20T08:37:30
设备：sdk_gphone16k_x86_64，SDK 37
证据：YTDL_SPLIT_DOWNLOAD_SMOKE sdk=37 videoFormat=394 audioFormat=139 videoBytes=5426536 audioBytes=2942838 videoPath=/data/user/0/com.garyapp.ytdl/cache/split-download-smoke/download-tkxzMEfp49Q-394-video.mp4 audioPath=/data/user/0/com.garyapp.ytdl/cache/split-download-smoke/download-tkxzMEfp49Q-139-audio.m4a
报告：android/app/build/outputs/androidTest-results/connected/debug/TEST-ytdl_api37_play_x86_64(AVD) - 17-_app-.xml
Logcat：android/app/build/outputs/androidTest-results/connected/debug/ytdl_api37_play_x86_64(AVD) - 17/logcat-com.garyapp.ytdl.core.ytdlp.YtdlpBridgeInstrumentedTest-downloadsRequiredYoutubeSmokeUrlSplitFormats.txt
```

后续仍未完成：

- 还没有 Android ffmpeg 字幕嵌入/烧录能力；这是 M5/T8E。

## M4/T8D required URL 核心合并 smoke 状态

当前已完成 required URL 的能力层 smoke；这不是 GUI 通过，也不是全量 MVP 验收：

- 新增 `android/app/src/androidTest/java/com/garyapp/ytdl/media/RequiredUrlMergeInstrumentedTest.kt`。
- API37 instrumentation 对 `https://www.youtube.com/watch?v=tkxzMEfp49Q` 执行真实 `YtdlpBridge().analyze()`。
- 从 `VideoAnalysis.formats` 选择明确分离流：video-only format `160`，`videoCodec=avc1.4d400c`；audio-only format `139`，`audioCodec=mp4a.40.5`。
- 使用 `downloadFormat()` 分别下载 video/audio 到 app 私有 cache 目录，不使用 mock 下载，不使用 `360p/18/worst` 兜底。
- 使用 `NativeMuxerMediaProcessor(outputRoot).mergeVideoAndAudio()` 合并为 MP4。
- 使用 `MediaExtractor` 检查合并输出，结果为 `videoTracks=1`、`audioTracks=1`。
- TDD 记录：新增 instrumentation 后第一次运行即通过；这是集成验证，未产生新的生产代码 RED，本任务未修改生产代码。

本轮 API37 instrumentation 证据：

```text
命令：cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.media.RequiredUrlMergeInstrumentedTest"
结果：BUILD SUCCESSFUL，tests=1 failures=0 errors=0 skipped=0，时间戳 2026-06-20T09:00:14
设备：sdk_gphone16k_x86_64，SDK 37
证据：YTDL_REQUIRED_MERGE_SMOKE sdk=37 device=sdk_gphone16k_x86_64 title="Jalen Brunson 'Captain Clutch' Moments in Knicks Championship Season" videoFormat=160 videoCodec=avc1.4d400c audioFormat=139 audioCodec=mp4a.40.5 videoBytes=6694014 audioBytes=2942838 videoPath=/data/user/0/com.garyapp.ytdl/cache/required-url-merge-t8d/downloads/download-tkxzMEfp49Q-160-video.mp4 audioPath=/data/user/0/com.garyapp.ytdl/cache/required-url-merge-t8d/downloads/download-tkxzMEfp49Q-139-audio.m4a output=/data/data/com.garyapp.ytdl/cache/required-url-merge-t8d/outputs/tkxzMEfp49Q-merged.mp4 mergedBytes=9691149 videoTracks=1 audioTracks=1
报告：android/app/build/outputs/androidTest-results/connected/debug/TEST-ytdl_api37_play_x86_64(AVD) - 17-_app-.xml
Logcat：android/app/build/outputs/androidTest-results/connected/debug/ytdl_api37_play_x86_64(AVD) - 17/logcat-com.garyapp.ytdl.media.RequiredUrlMergeInstrumentedTest-analyzesDownloadsSplitStreamsAndMergesRequiredUrlIntoMp4.txt
```

## 已确认事实

- 当前仓库没有 Android ffmpeg 二进制文件。
- 当前 Android 工程没有 `android/app/src/main/jniLibs/x86_64/ffmpeg` 或 `android/app/src/main/jniLibs/arm64-v8a/ffmpeg`。
- 本机 `.venv` 和 Windows 打包目录里的 `ffmpeg.exe` 是 Windows 程序，不能用于 Android。
- 本机 Gradle 缓存未发现可直接复用的 `ffmpeg-kit`、`arthenica`、`javacpp/bytedeco ffmpeg` Android 产物。
- Android 官方 `MediaMuxer` 可用于把 elementary streams 封装成 MP4/WebM/3GP；这适合先做“已编码视频流 + 已编码音频流”的合并。
- `MediaMuxer` 不是字幕烧录工具，也不是通用转码/滤镜工具；字幕烧录仍需要 ffmpeg 或等价处理链。
- `ffmpeg-kit` 官方仓库已经归档并退休，不能把它当成长期稳定的默认路线；如果后续采用 fork 或自行打包，必须记录来源、许可证、ABI、包体积和维护风险。

## 路线边界

### 原生 MediaProcessor 第一阶段

目标：

- 支持 app 私有目录内的 video-only 文件 + audio-only 文件合并为 MP4。
- 合并不经过 shell 字符串拼接。
- 合并输出必须可被 `MediaExtractor` 检查出 1 条视频轨和 1 条音频轨。
- 失败时保留两个原始下载文件，错误信息中文可读。

非目标：

- 不承诺字幕烧录。
- 不承诺所有 codec/container 都能无转码合并。
- 不做 DRM 绕过，不做未授权内容访问。

### Android ffmpeg 后续阶段

目标：

- 提供 `ffmpeg -version` 或等价能力探针。
- 支持字幕嵌入。
- 支持字幕烧录。
- 覆盖原生 muxer 不支持的媒体处理场景。

准入要求：

- 明确二进制来源或构建方式。
- 明确 LGPL/GPL 许可证边界。
- 明确 ABI：至少 `x86_64` 用于模拟器，`arm64-v8a` 用于手机。
- 明确包体积影响和 Play 分发风险。

## 后续任务映射

- M1/T8A：定义合同、模型和文档边界。
- M2/T8B：实现并测试原生合并处理器。
- M3/T8C：实现 yt-dlp 指定 format id 的分离下载。
- M4/T8D：已用 `tkxzMEfp49Q` 完成核心分析、分离下载、合并 smoke，并由 `MediaExtractor` 证明输出含 1 条视频轨和 1 条音频轨。
- M5/T8E：补 Android ffmpeg 字幕嵌入/烧录能力。
- M6：实现下载编排、前台服务、通知和真实队列状态，禁止 360p 单文件假回退。
- M7：把真实合并与 ffmpeg 能力接入五页 GUI，确保格式选择、队列和状态文案都来自真实能力。
- M8：补历史、导出、cookies 隐私和失败恢复。
- M9/T12：Computer Use 前台可见全功能全流程验收，这是唯一最终验收口径。
