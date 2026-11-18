# Android 导出写出证据

日期：2026-07-05

结论：本轮不把 `historyExportWritesMergedOutputThroughSystemPicker` 作为自动化通过项。connected 测试里的外部文件识别逻辑长时间等待且不可靠，需要另行用小文件验证识别函数。

已观察到的事实：

- 系统下载目录存在 `/sdcard/Download/ytdl-export-1783250902008.mp4`。
- 文件大小为 `355645249` bytes，说明系统保存位置已产生非空导出文件。
- 这只能作为“应用曾写出外部文件”的证据，不能替代最终前台可见 Computer Use 验收。

后续策略：

- 不再反复下载或导出同一个 355 MB 视频来验证识别逻辑。
- 若继续做导出自动化，先用小而可控的测试文件验证 `/sdcard/Download/ytdl-export-*.mp4` 列表和大小解析。
- 最终 GUI 验收仍必须回到前台可见 Android 模拟器窗口真实操作。

2026-07-05 补充：已新增测试侧小探针 `ExternalDownloadProbeInstrumentedTest`，不跑真实大视频，不调用系统保存 UI，只在 `/sdcard/Download` 创建 5 字节唯一前缀测试文件，验证 `ls -l` 输出可被解析为唯一文件名和大小，并验证多个同名前缀变体时不会误判成功。本项只修复 connected 测试识别基础，不能替代前台可见导出验收。
