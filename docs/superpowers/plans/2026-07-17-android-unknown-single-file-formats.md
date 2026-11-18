# Android Unknown Single-File Formats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Android 版把缺少分辨率和编码信息、但具有明确格式 ID 和视频容器的单文件结果显示为“原始格式”，并只按单文件路线下载。

**Architecture:** 保留 Python 桥接返回的空元数据，不在解析层猜测分辨率。Kotlin 映射层严格识别未知元数据视频容器，格式选择层把未知高度条目单独显示，下载请求继续复用现有 `DirectSingleFile` 路线。

**Tech Stack:** Kotlin 2.3.0、JUnit 4、Robolectric、Jetpack Compose 选择模型、Gradle 9.4.1。

---

### Task 1: 映射未知元数据视频容器

**Files:**
- Modify: `android/app/src/test/java/com/garyapp/ytdl/core/ytdlp/FormatMappingTest.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/YtdlpBridge.kt`

- [x] **Step 1: 写入失败测试**

在 `FormatMappingTest` 增加：

```kotlin
@Test
fun mapsUnknownMetadataVideoContainerAsOriginalSingleFile() {
    val format = YtdlpBridge.mapFormat(
        mapOf("format_id" to "video_url", "ext" to "mp4"),
    )

    assertEquals(null, format.height)
    assertEquals("原始格式", format.label)
    assertTrue(format.hasVideo)
    assertTrue(format.hasAudio)
    assertTrue(format.isSupported)
    assertFalse(format.mergeRequired)
}

@Test
fun unknownMetadataAudioContainerRemainsUnsupported() {
    val format = YtdlpBridge.mapFormat(
        mapOf("format_id" to "audio_url", "ext" to "mp3"),
    )

    assertFalse(format.isSupported)
    assertEquals("不支持", format.label)
}
```

- [x] **Step 2: 运行测试并确认按预期失败**

Run:

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests 'com.garyapp.ytdl.core.ytdlp.FormatMappingTest' --no-parallel
```

Expected: `mapsUnknownMetadataVideoContainerAsOriginalSingleFile` 因当前标签为“不支持”、`isSupported=false` 而失败。

- [x] **Step 3: 写入最小映射实现**

在 `YtdlpBridge.mapFormat` 中基于已解析字段计算：

```kotlin
val isUnknownMetadataSingleFile =
    id.isNotBlank() &&
        height == null &&
        vcodec == null &&
        acodec == null &&
        ext.lowercase() in UnknownMetadataVideoContainers
val isSupported = hasVideo && (height != null || isUnknownMetadataSingleFile)
val label = when {
    isUnknownMetadataSingleFile -> "原始格式"
    !isSupported -> "不支持"
    mergeRequired -> "${height}p 需合并音频"
    else -> "${height}p"
}
```

并在伴生对象中加入：

```kotlin
private val UnknownMetadataVideoContainers = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")
```

- [x] **Step 4: 运行映射测试确认通过**

Run Task 1 Step 2 command.

Expected: `FormatMappingTest` 全部通过。

- [x] **Step 5: 提交映射改动**

```powershell
git add android/app/src/main/java/com/garyapp/ytdl/core/ytdlp/YtdlpBridge.kt android/app/src/test/java/com/garyapp/ytdl/core/ytdlp/FormatMappingTest.kt
git commit -m "fix(android): accept unknown metadata video files"
```

### Task 2: 显示并选择原始格式

**Files:**
- Modify: `android/app/src/test/java/com/garyapp/ytdl/ui/FormatSelectionModelTest.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/FormatSelection.kt`

- [x] **Step 1: 写入失败测试**

新增未知高度单文件测试辅助对象，并验证：

```kotlin
@Test
fun unknownMetadataSingleFileShowsOneSelectedOriginalFormatRow() {
    val analysis = analysisWith(unknownMetadataSingleFileFormat("video_url"))
    val selection = defaultFormatSelection(analysis)
    val rows = buildFormatResolutionRows(analysis, selection)

    assertEquals(FormatMode.VideoOnly, selection.mode)
    assertEquals(listOf("原始格式"), rows.map { it.label })
    assertEquals("video_url", rows.single().videoFormatId)
    assertTrue(rows.single().selected)
    assertEquals("原始格式 MP4 单文件", rows.single().summary)
}

@Test
fun knownResolutionsRemainRecommendedWhenOriginalFormatAlsoExists() {
    val analysis = analysisWith(
        progressiveFormat(id = "18", height = 360),
        unknownMetadataSingleFileFormat("video_url"),
    )
    val rows = buildFormatResolutionRows(analysis, defaultFormatSelection(analysis))

    assertEquals(listOf("自动（推荐）", "360p", "原始格式"), rows.map { it.label })
    assertEquals("18", rows.single { it.selected }.videoFormatId)
    assertTrue(rows.none { it.label.contains("null") })
}
```

辅助对象：

```kotlin
private fun unknownMetadataSingleFileFormat(id: String) = VideoFormat(
    id = id,
    ext = "mp4",
    height = null,
    label = "原始格式",
    hasVideo = true,
    hasAudio = true,
    mergeRequired = false,
    isSupported = true,
    videoCodec = null,
    audioCodec = null,
)
```

- [x] **Step 2: 运行测试并确认失败**

Run:

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests 'com.garyapp.ytdl.ui.FormatSelectionModelTest' --no-parallel
```

Expected: 当前实现产生重复自动行或 `nullp` 行，断言失败。

- [x] **Step 3: 最小调整视频格式行构造**

在 `videoDownloadRows` 中把选择分成已知高度和未知高度两组：

```kotlin
val choices = analysis.formats
    .filter { it.isSupported && it.hasVideo }
    .sortedByDescending { it.height ?: 0 }
    .map { FormatChoice(video = it, audio = null, mergeRequired = false) }
val knownChoices = choices.filter { it.video.height != null }
val unknownChoices = choices.filter { it.video.height == null }
val knownRows = rowsFromVideoChoices(knownChoices, selection)
val explicitUnknown = selection.selectedVideoFormatId?.let { formatId ->
    unknownChoices.firstOrNull { it.video.id == formatId }
}
val unknownChoice = explicitUnknown ?: unknownChoices.maxByOrNull { it.video.filesizeBytes ?: 0L }
val unknownRow = unknownChoice?.let { choice ->
    rowFromChoice(
        height = null,
        label = "原始格式",
        choice = choice,
        selected = explicitUnknown != null ||
            (knownRows.isEmpty() && selection.selectedVideoFormatId == null && selection.selectedHeight == null),
    )
}
return knownRows + listOfNotNull(unknownRow)
```

- [x] **Step 4: 运行格式选择测试确认通过**

Run Task 2 Step 2 command.

Expected: `FormatSelectionModelTest` 全部通过。

- [x] **Step 5: 提交选择模型改动**

```powershell
git add android/app/src/main/java/com/garyapp/ytdl/ui/FormatSelection.kt android/app/src/test/java/com/garyapp/ytdl/ui/FormatSelectionModelTest.kt
git commit -m "fix(android): show original single-file format"
```

### Task 3: 锁定单文件下载路线并更新版本

**Files:**
- Modify: `android/app/src/test/java/com/garyapp/ytdl/download/DownloadRequestRoutingTest.kt`
- Modify: `android/app/build.gradle.kts`

- [x] **Step 1: 写入未知高度路由测试**

```kotlin
@Test
fun unknownMetadataVideoUsesDirectSingleFileRoute() {
    val format = VideoFormat(
        id = "video_url",
        ext = "mp4",
        height = null,
        label = "原始格式",
        hasVideo = true,
        hasAudio = true,
        mergeRequired = false,
        isSupported = true,
        videoCodec = null,
        audioCodec = null,
    )
    val request = DownloadRequest.fromAnalysis(
        url = TestUrl,
        analysis = analysisWith(format),
        selection = FormatSelection(
            mode = FormatMode.VideoOnly,
            selectedHeight = null,
            selectedVideoFormatId = "video_url",
        ),
    ).getOrThrow()

    assertEquals(DownloadRoute.DirectSingleFile("video_url"), request.route)
    assertFalse(request.route is DownloadRoute.MergeRequired)
}
```

- [x] **Step 2: 运行路由测试**

Run:

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests 'com.garyapp.ytdl.download.DownloadRequestRoutingTest' --no-parallel
```

Expected: 路由测试通过，证明现有下载路线无需生产代码修改。

- [x] **Step 3: 更新独立版本号**

在 `android/app/build.gradle.kts` 中改为：

```kotlin
versionCode = 4
versionName = "1.0.3"
```

- [x] **Step 4: 提交路由测试和版本号**

```powershell
git add android/app/src/test/java/com/garyapp/ytdl/download/DownloadRequestRoutingTest.kt android/app/build.gradle.kts
git commit -m "test(android): lock original format download route"
```

### Task 4: 顺序验证、真实地址分析和审计

**Files:**
- Modify: `docs/qa/android-mvp-smoke.md`
- Modify: `docs/superpowers/plans/2026-07-14-android-parser-manager-and-network-fallback.md`

- [x] **Step 1: 运行聚焦测试**

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests 'com.garyapp.ytdl.core.ytdlp.FormatMappingTest' --tests 'com.garyapp.ytdl.ui.FormatSelectionModelTest' --tests 'com.garyapp.ytdl.download.DownloadRequestRoutingTest' --no-parallel
```

Expected: `BUILD SUCCESSFUL`。

- [x] **Step 2: 运行全量单元测试**

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --no-parallel
```

Expected: `BUILD SUCCESSFUL`。

- [x] **Step 3: 构建 debug APK**

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebug --no-parallel
```

Expected: `BUILD SUCCESSFUL`，APK 版本为 `1.0.3 (4)`。

- [x] **Step 4: 用真实地址做后台分析**

使用用户提供的地址，只验证分析结果包含一个 `video_url` MP4、映射结果为“原始格式”、默认选择为视频单文件；不得下载完整媒体，不输出 cookies、完整直链、IP 或原始异常。

- [x] **Step 5: 审计并更新 QA 记录**

检查本轮没有引入站点专用判断、重复网络逻辑、无用导入、临时探针或敏感信息泄露。记录锁屏期间只完成后台/自动化验证，前台可见“原始格式”和真实开始下载仍待解锁后验收。

- [x] **Step 6: 提交验证文档**

```powershell
git add docs/qa/android-mvp-smoke.md docs/superpowers/plans/2026-07-14-android-parser-manager-and-network-fallback.md
git commit -m "docs(android): record unknown format validation"
```
