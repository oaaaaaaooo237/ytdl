# Android MediaProcessor 路线记录

日期：2026-06-20

## 当前结论

后续开发顺序调整为先实现媒体能力，再绑定 GUI 流程。

第一步不再继续反复测试旧的 ffmpeg 合并界面标签，而是先实现真实可运行的 `MediaProcessor`：

1. 先用 Android 原生 `MediaExtractor + MediaMuxer` 实现兼容流的音视频合并。
2. 用 yt-dlp 下载明确的 video-only format id 和 audio-only format id。
3. 在 API37 模拟器中把两个文件合并成一个 MP4，并用 `MediaExtractor` 证明输出文件同时包含视频轨和音频轨。
4. 再把这个能力接入 GUI 的下载、队列、历史和最终 Computer Use 前台可视验收。
5. MVP1 带字幕任务输出合并后的视频音频文件和独立字幕文件；字幕嵌入、字幕烧录和三合一输出进入 MVP2。

## M1/T8A 合同状态

当前已建立第一层媒体处理合同：

- `MediaMergeRequest` 描述 video-only 输入、audio-only 输入、受控输出文件、输出容器和预期 format id。
- `MediaProcessingResult` 记录输出文件、写入字节数、视频轨数量、音频轨数量和处理器名称。
- `MediaProcessor.mergeVideoAndAudio()` 返回 `Result<MediaProcessingResult>`，调用方必须处理失败。
- `NativeMuxerMediaProcessor` 已在 M2/T8B 实现真实 `MediaExtractor + MediaMuxer` 样本复制；M1/T8A 保留为合同和边界说明。
- `NativeMuxerMediaProcessor` 明确 `supportsSubtitleEmbed=false`、`supportsSubtitleBurn=false`；字幕嵌入和字幕烧录属于 MVP2，MVP1 只保存独立字幕文件。

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

- 独立字幕文件已能通过 yt-dlp bridge 输出到 app 私有目录；队列、历史和导出绑定仍在 M6/M8。

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

- 独立字幕文件下载能力已在 M5/T8E 补齐；后续还要把该结果接入真实下载编排、队列、历史和导出。

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

## M5/T8E 独立字幕文件输出状态

当前已实现 MVP1 独立字幕文件输出能力：

- `VideoAnalysis.subtitles` 会同时包含普通字幕和自动字幕，并用 `SubtitleSource.Manual` / `SubtitleSource.Automatic` 区分来源。
- Kotlin bridge 新增 `downloadSubtitle(url, outputDirectory, language, ext, source, cookiesPath, listener)`，调用方必须显式选择分析结果中的语言、扩展名和来源类型。
- 字幕语言和扩展名只接受单个明确值；`all`、`best`、`en/zh-Hans`、`vtt/srt`、`vtt,ttml`、`en.*` 等 selector/fallback 表达式会在调用 Python 前被拒绝。
- Python bridge 新增 `download_subtitle()`，使用 yt-dlp 的 `skip_download`、`writesubtitles` / `writeautomaticsub`、`subtitleslangs` 和 `subtitlesformat` 只下载独立字幕文件，不下载视频，不嵌入字幕，不烧录字幕。
- 成功结果返回 `outputPath`、`bytesWritten`、`language`、`ext`、`source` 和 `title`，供后续同一下载任务的队列、历史和导出绑定。
- 失败信息会转为安全中文摘要，不记录或返回 cookies 内容、Authorization、Cookie 或敏感 URL query。

本轮 API37 instrumentation 证据：

```text
命令：cd android; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.garyapp.ytdl.core.ytdlp.SubtitleDownloadInstrumentedTest"
结果：BUILD SUCCESSFUL，tests=1 failures=0 errors=0 skipped=0，时间戳 2026-06-20T12:44:32
设备：sdk_gphone16k_x86_64，SDK 37
证据：YTDL_SUBTITLE_DOWNLOAD_SMOKE sdk=37 device=sdk_gphone16k_x86_64 language=en ext=vtt source=automatic bytes=61664 path=/data/user/0/com.garyapp.ytdl/cache/subtitle-download-smoke-0/download-tkxzMEfp49Q-subtitle-automatic.en.vtt
报告：android/app/build/outputs/androidTest-results/connected/debug/TEST-ytdl_api37_play_x86_64(AVD) - 17-_app-.xml
Logcat：android/app/build/outputs/androidTest-results/connected/debug/ytdl_api37_play_x86_64(AVD) - 17/logcat-com.garyapp.ytdl.core.ytdlp.SubtitleDownloadInstrumentedTest-analyzesAndDownloadsOneAvailableSubtitleOrReportsNone.txt
```

注意：本项是能力层通过，不是 GUI、队列、历史、导出或最终 MVP 可视验收通过。

## 已确认事实

- Android 官方 `MediaMuxer` 可用于把 elementary streams 封装成 MP4/WebM/3GP；这适合先做“已编码视频流 + 已编码音频流”的合并。
- `MediaMuxer` 不是字幕烧录工具，也不是通用转码/滤镜工具；这些能力进入 MVP2。
- MVP1 不需要 Android FFmpeg 二进制；Windows `ffmpeg.exe` 仍不能用于 Android。

## MVP1 / MVP2 媒体路线

2026-06-20 用户确认新的分层：MVP1 先以 Android 原生 `MediaMuxer` 为主；带字幕任务生成独立字幕文件和合并后的视频音频文件。字幕嵌入、字幕烧录、视频+音频+字幕三者合一输出作为 MVP2 标准。

采用路线：

- MVP1：高分辨率分离视频流 + 音频流的兼容 MP4 合并，继续使用已实测的 Android 原生 `MediaExtractor + MediaMuxer` 快速路径。
- MVP1：字幕只作为独立文件下载、保存、历史关联和导出，不嵌入容器，不烧录到画面。
- MVP2：字幕嵌入、字幕烧录、滤镜、非兼容容器/编码和原生 muxer 不能处理的场景，再设计可审计 FFmpeg 或等价处理链。

已拒绝路线：

- FFmpegKit / arthenica / mobile-ffmpeg：官方项目已退役，不作为长期默认基础。
- `com.bihe0832.android:lib-ffmpeg-mobile-aaf`：AAR 来源、源码配置、许可证覆盖、全 ABI/16KB/AAB 证据不足，不进入主线依赖；MVP1 也不需要该依赖。
- `io.github.pao11:ffmpeg-android`：本地探针发现缺少 `x86_64`，不能满足 API37 模拟器验收。
- Bytedeco / JavaCV FFmpeg：维护活跃，但包体积、许可证组合和 API 控制成本过高，不作为当前 MVP1 路线。

MVP2 FFmpeg 路线以后重新立项；不得把未审计 AAR 或退役 FFmpegKit 当作捷径。

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

### MVP2 FFmpeg 后续阶段

目标：

- 可审计 FFmpeg 或等价媒体处理链。
- 提供 `ffmpeg -version` 或等价 JNI bridge 能力探针。
- 支持字幕嵌入。
- 支持字幕烧录。
- 覆盖原生 muxer 不支持的媒体处理场景。

准入要求：

- 明确源码来源、签名/hash 和构建方式。
- 明确 LGPL/GPL/nonfree 许可证边界。
- 明确 ABI：至少 `x86_64` 用于模拟器，`arm64-v8a` 用于手机。
- 明确包体积影响、16KB page-size 证据和 Play 分发风险。

## 后续任务映射

- M1/T8A：定义合同、模型和文档边界。
- M2/T8B：实现并测试原生合并处理器。
- M3/T8C：实现 yt-dlp 指定 format id 的分离下载。
- M4/T8D：已用 `tkxzMEfp49Q` 完成核心分析、分离下载、合并 smoke，并由 `MediaExtractor` 证明输出含 1 条视频轨和 1 条音频轨。
- M5/T8E：已补 MVP1 独立字幕文件下载结果模型和能力层 smoke；历史关联和导出留给 M8，`MediaMuxer` 仍是兼容分离音视频合并的优先快速路径。
- M6：实现下载编排、前台服务、通知和真实队列状态，禁止 360p 单文件假回退。
- M7：把真实合并、字幕文件和队列状态接入五页 GUI，确保格式选择、队列和状态文案都来自真实能力。
- M8：补历史、导出、cookies 隐私和失败恢复。
- M9/T12：Computer Use 前台可见全功能全流程验收，这是唯一最终验收口径。
