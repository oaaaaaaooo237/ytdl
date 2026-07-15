# Android 1.0.1 Task Title and Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 隐藏任务卡片中的输出文件名，让历史搜索只匹配视频标题，并用循环标题、同画风图标和一位小数数值展示下载进度。

**Architecture:** 历史映射继续保留内部输出 URI，只从可见 `meta` 中移除文件名；标题搜索只读取 `HistoryUiItem.title`。下载进度使用独立 `QueueProgressMeta` 模型提供速度、容量和无障碍说明，Compose 卡片用公共跑马灯标题组件和两组自绘矢量图标渲染，不改下载回调或数据库结构。

**Tech Stack:** Kotlin、Jetpack Compose、Compose Foundation `basicMarquee`、Robolectric Compose Test、Gradle 9.4.1、Android API 37。

---

### Task 1: 隐藏历史文件名并按标题搜索

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/DownloadUiBridge.kt`
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Test: `android/app/src/test/java/com/garyapp/ytdl/ui/DownloadUiBridgeTest.kt`

- [ ] **Step 1: 写历史映射和标题搜索的失败测试**

在 `DownloadUiBridgeTest` 中修改 `historyAudioFilterUsesHiddenFormatClassification`，要求第二行只保留类型和时间；扩展 `historySearchAndTypeFilterUseLoadedLocalItems`，证明标题可以命中，但 `meta` 中的文字不能命中：

```kotlin
assertTrue(item.meta.matches(Regex("仅音频 · \\d{2}/\\d{2} \\d{2}:\\d{2}")))
assertFalse(item.meta.contains("audio.m4a"))

assertEquals(
    listOf(video),
    filterHistoryItemsForUiTest(items, query = "航拍", selectedFilterIndex = 0),
)
assertEquals(
    emptyList<HistoryUiItem>(),
    filterHistoryItemsForUiTest(items, query = "720p", selectedFilterIndex = 0),
)
```

- [ ] **Step 2: 运行聚焦测试并确认 RED**

Run:

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests "com.garyapp.ytdl.ui.DownloadUiBridgeTest.historyAudioFilterUsesHiddenFormatClassification" --tests "com.garyapp.ytdl.ui.DownloadUiBridgeTest.historySearchAndTypeFilterUseLoadedLocalItems" --no-parallel
```

Expected: 当前 `meta` 仍包含 `audio.m4a`，并且搜索仍匹配 `meta` 中的 `720p`，测试失败。

- [ ] **Step 3: 完成最小实现**

把 `historyMeta` 中的文件名删除：

```kotlin
private fun historyMeta(row: HistoryItemEntity): String {
    val parts = listOfNotNull(
        historyTypeLabel(row.formatSummary.orEmpty()).takeIf { it.isNotBlank() },
        row.completedAt.takeIf { it > 0L }?.let { formatHistoryTime(it) },
        row.errorSummary?.takeIf { it.isNotBlank() && !it.contains("字幕") },
    )
    return redactHistoryUiText(parts.joinToString(" · ")).trim()
}
```

删除只服务于前台文件名的 `historyOutputFileName`、`truncateHistoryFileName` 以及由此变成无用的 `URI`、`URLDecoder` 导入。历史搜索改成只匹配标题：

```kotlin
val searchable = item.title.lowercase(Locale.ROOT)
val matchesQuery = normalizedQuery.isBlank() || searchable.contains(normalizedQuery)
```

类型筛选继续使用 `item.isAudioOnly`，不受标题搜索收紧影响。

- [ ] **Step 4: 运行聚焦测试并确认 GREEN**

Run: 与 Step 2 相同。

Expected: 两项测试通过，标题搜索和音视频分类保持正常。

- [ ] **Step 5: 提交历史文案与搜索改动**

```powershell
git add -- android/app/src/main/java/com/garyapp/ytdl/ui/DownloadUiBridge.kt android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt android/app/src/test/java/com/garyapp/ytdl/ui/DownloadUiBridgeTest.kt
git commit -m "fix(android): hide task filenames from history"
```

### Task 2: 建立一位小数下载进度模型

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Test: `android/app/src/test/java/com/garyapp/ytdl/ui/DownloadUiBridgeTest.kt`

- [ ] **Step 1: 写进度格式的失败测试**

把既有 `queueCardMetaForUiTest` 断言改成结构化结果，并增加单位边界：

```kotlin
assertEquals(
    QueueProgressMeta(
        speed = "400.0 B/s",
        transferred = "300.0 B/1000.0 B",
        contentDescription = "下载速度 400.0 B/s，已下载 300.0 B，总容量 1000.0 B",
    ),
    queueCardMetaForUiTest(updated),
)
assertEquals("1.0 KB", formatProgressBytesForUiTest(1024.0))
assertEquals("1.0 MB", formatProgressBytesForUiTest(1024.0 * 1024.0))
assertEquals("1.0 GB", formatProgressBytesForUiTest(1024.0 * 1024.0 * 1024.0))
```

再加入缺少数据的断言：

```kotlin
assertEquals("--", empty.speed)
assertEquals("0.0 B/--", empty.transferred)
assertFalse(empty.speed.contains("速度"))
assertFalse(empty.transferred.contains("已下载"))
assertFalse(empty.transferred.contains("总计"))
assertFalse(empty.transferred.contains("未知"))
```

- [ ] **Step 2: 运行聚焦测试并确认 RED**

Run:

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests "com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueProgressUpdatesSpeedAndDownloadedBytesWhileKeepingStageTotalStable" --tests "com.garyapp.ytdl.ui.DownloadUiBridgeTest.queueProgressUsesOneDecimalUnitsWithoutVisibleLabels" --no-parallel
```

Expected: `QueueProgressMeta` 和 `formatProgressBytesForUiTest` 尚不存在，测试编译失败。

- [ ] **Step 3: 实现独立进度模型和格式化函数**

在 `YtdlApp.kt` 中加入：

```kotlin
internal data class QueueProgressMeta(
    val speed: String,
    val transferred: String,
    val contentDescription: String,
)

private fun formatProgressBytes(bytes: Double): String {
    val safeBytes = bytes.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val units = listOf("B", "KB", "MB", "GB")
    var value = safeBytes
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}

internal fun formatProgressBytesForUiTest(bytes: Double): String = formatProgressBytes(bytes)
```

把 `queueCardMeta` 改成：

```kotlin
private fun queueCardMeta(state: RuntimeDownloadState): QueueProgressMeta {
    val speed = state.speedBytesPerSecond
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { "${formatProgressBytes(it)}/s" }
        ?: "--"
    val downloaded = formatProgressBytes((state.downloadedBytes ?: 0L).toDouble())
    val total = state.totalBytes?.let { formatProgressBytes(it.toDouble()) } ?: "--"
    return QueueProgressMeta(
        speed = speed,
        transferred = "$downloaded/$total",
        contentDescription = "下载速度 $speed，已下载 $downloaded，总容量 $total",
    )
}
```

保留原有 `formatBytes(Long)` 给设置页缓存统计使用，不改变其输出。

- [ ] **Step 4: 运行聚焦测试并确认 GREEN**

Run: 与 Step 2 相同。

Expected: 一位小数、四级单位和未知值断言全部通过。

- [ ] **Step 5: 提交进度模型**

```powershell
git add -- android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt android/app/src/test/java/com/garyapp/ytdl/ui/DownloadUiBridgeTest.kt
git commit -m "feat(android): format compact download progress"
```

### Task 3: 渲染循环标题和同画风进度图标

**Files:**
- Modify: `android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt`
- Test: `android/app/src/test/java/com/garyapp/ytdl/ui/DownloadGuiBindingTest.kt`

- [ ] **Step 1: 写 Compose 失败测试**

渲染一个正在下载的长标题任务、一个等待任务和一条长标题历史，断言三类标题都带跑马灯语义，当前任务显示两个图标：

```kotlin
composeRule.onNodeWithTag("ytdl-current-task-title")
    .assert(SemanticsMatcher.expectValue(YtdlTaskTitleMarqueeKey, true))
composeRule.onNodeWithTag("ytdl-pending-task-title-0")
    .assert(SemanticsMatcher.expectValue(YtdlTaskTitleMarqueeKey, true))
composeRule.onNodeWithTag("ytdl-history-title-301")
    .assert(SemanticsMatcher.expectValue(YtdlTaskTitleMarqueeKey, true))
composeRule.onNodeWithTag("ytdl-queue-download-speed-icon").assertExists()
composeRule.onNodeWithTag("ytdl-queue-storage-icon").assertExists()
composeRule.onNodeWithText("400.0 B/s").assertExists()
composeRule.onNodeWithText("300.0 B/1000.0 B").assertExists()
```

- [ ] **Step 2: 运行 Compose 测试并确认 RED**

Run:

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --tests "com.garyapp.ytdl.ui.DownloadGuiBindingTest.taskTitlesMarqueeAndProgressUsesMatchingIcons" --no-parallel
```

Expected: 标题标签、跑马灯语义和图标标签尚不存在，测试失败。

- [ ] **Step 3: 添加跑马灯标题组件**

在 `YtdlApp.kt` 中引入 `androidx.compose.foundation.basicMarquee`，定义测试语义并复用一个标题组件：

```kotlin
internal val YtdlTaskTitleMarqueeKey = SemanticsPropertyKey<Boolean>("YtdlTaskTitleMarquee")
private var SemanticsPropertyReceiver.ytdlTaskTitleMarquee by YtdlTaskTitleMarqueeKey

@Composable
private fun TaskCardTitle(
    text: String,
    tag: String,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(iterations = Int.MAX_VALUE)
            .semantics { ytdlTaskTitleMarquee = true }
            .testTag(tag),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
```

当前任务使用 `ytdl-current-task-title`，等待任务使用带索引的 `ytdl-pending-task-title-$index`，历史使用 `ytdl-history-title-${item.id}`。

- [ ] **Step 4: 添加同画风图标和进度行**

复用现有 `DownloadTabIcon` 作为下载速度图标，并用 `tabIcon` 绘制硬盘图标：

```kotlin
private val StorageCapacityIcon = tabIcon("StorageCapacity") {
    moveTo(4f, 5f)
    lineTo(20f, 5f)
    lineTo(22f, 11f)
    lineTo(22f, 19f)
    lineTo(2f, 19f)
    lineTo(2f, 11f)
    close()
    moveTo(5f, 14f)
    lineTo(19f, 14f)
    lineTo(19f, 17f)
    lineTo(5f, 17f)
    close()
}
```

新增 `QueueProgressMetaRow`，同一行显示两组 14dp 图标和文本：

```kotlin
@Composable
private fun QueueProgressMetaRow(meta: QueueProgressMeta) {
    val color = LocalYtdlAppPalette.current.softText
    Row(
        modifier = Modifier.semantics { contentDescription = meta.contentDescription },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(DownloadTabIcon, null, Modifier.size(14.dp).testTag("ytdl-queue-download-speed-icon"), color)
            Text(meta.speed, color = color, style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(StorageCapacityIcon, null, Modifier.size(14.dp).testTag("ytdl-queue-storage-icon"), color)
            Text(meta.transferred, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

`QueueCard` 接收可空 `progressMeta` 和普通 `supportingText`：

```kotlin
private fun QueueCard(
    title: String,
    titleTag: String,
    subtitle: String,
    progress: QueueProgressPresentation,
    status: String,
    progressMeta: QueueProgressMeta? = null,
    supportingText: String = "",
    formatBadge: String,
    codecBadge: String,
    stageItems: List<QueueStageItem>,
    accent: Color,
    modifier: Modifier = Modifier,
    thumbnailBitmap: Bitmap? = null,
    onCancel: (() -> Unit)? = null,
)
```

当前任务传入：

```kotlin
titleTag = "ytdl-current-task-title",
progressMeta = queueCardMeta(state),
```

等待任务传入：

```kotlin
titleTag = "ytdl-pending-task-title-$index",
supportingText = "前方 ${index + if (hasCurrentTask) 1 else 0} 个任务",
```

卡片只在 `progressMeta != null` 时渲染 `QueueProgressMetaRow`，只在 `supportingText.isNotBlank()` 时渲染普通提示，避免把等待提示伪装成下载容量。

- [ ] **Step 5: 运行 Compose 测试并确认 GREEN**

Run: 与 Step 2 相同。

Expected: 三类标题、两个图标和两组进度文本均可找到，状态徽标布局测试继续通过。

- [ ] **Step 6: 提交界面实现**

```powershell
git add -- android/app/src/main/java/com/garyapp/ytdl/ui/YtdlApp.kt android/app/src/test/java/com/garyapp/ytdl/ui/DownloadGuiBindingTest.kt
git commit -m "feat(android): marquee task titles and show progress icons"
```

### Task 4: 审计、完整验证、前台验收和交付

**Files:**
- Modify: `docs/qa/android-mvp-smoke.md`
- Modify: `docs/superpowers/plans/2026-07-15-android-task-title-and-progress-implementation.md`

- [ ] **Step 1: 测试前代码审计**

用 `rg` 检查并清理仅由本轮改动产生的无用声明：

```powershell
rg -n "historyOutputFileName|truncateHistoryFileName|速度 |已下载 |总计 |未知|ytdlTaskTitleMarquee|QueueProgressMeta" android/app/src/main android/app/src/test
git diff --check
git status --short
```

Expected: 运行时代码不再显示旧进度汉字或历史文件名；没有无用导入、重复图标、临时探针或空白错误；`.codex-remote-attachments/` 保持未跟踪。

- [ ] **Step 2: 顺序运行 Android 测试和构建**

所有 Gradle 命令使用固定版本、`--no-parallel` 和超时，逐条执行：

```powershell
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:testDebugUnitTest --no-parallel
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebug --no-parallel
D:\DevTools\gradle-9.4.1\bin\gradle.bat :app:assembleDebugAndroidTest --no-parallel
```

Expected: 单元测试、debug APK 和 androidTest APK 均构建成功。

- [ ] **Step 3: 前台可见 API 37 模拟器验收**

先检查在线设备和 `qemu-system-x86_64`，只启动一个 `ytdl_api37_play_x86_64` 可见窗口，并移动到主屏幕右侧完整可见。覆盖安装 debug APK，真实打开 App：

1. 在任务页确认历史卡片不显示文件名，搜索框输入标题片段可以找到对应记录。
2. 确认长标题单行从右向左循环，短标题不移动。
3. 在实际下载进行时确认进度行显示同画风下载图标和硬盘图标，格式为 `3.2 MB/s` 与 `126.4 MB/512.0 MB`，没有旧汉字且不与右侧徽标重叠。
4. 不通过 ADB、UIAutomator 或测试代码伪造前台展示；ADB 只用于安装和辅助读取状态。

- [ ] **Step 4: 更新 QA、归档唯一 APK 并安装到手机**

把本轮自动化与前台结果写入 `docs/qa/android-mvp-smoke.md`。复制 `android/app/build/outputs/apk/debug/app-debug.apk` 到 `dist/android/1.0.1-latest/ytdl-android-1.0.1-debug.apk`，重新生成 `SHA256SUMS.txt`，确认发布目录只有一个 APK。手机 `a73e29a3` 在线时使用 `adb -s a73e29a3 install -r` 覆盖安装并核对 `versionName=1.0.1`、`versionCode=2`。

- [ ] **Step 5: 提交、推送和核对远程**

只暂存本计划涉及的代码、测试和文档，排除 `AGENTS.md`、`.qa-data`、附件和 APK：

```powershell
git add -- docs/qa/android-mvp-smoke.md docs/superpowers/plans/2026-07-15-android-task-title-and-progress-implementation.md
git commit -m "docs(android): verify compact task progress"
git push origin feature/android-play-mvp-1
```

Expected: 本地 `HEAD` 与 `origin/feature/android-play-mvp-1` 一致，现有草稿 PR #1 保持打开。
