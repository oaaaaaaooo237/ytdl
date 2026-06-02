# 测试证据边界

## 全真验收证据

- `scripts/real_download_matrix.ps1`：真实调用 `tools/yt-dlp.exe` 分析视频并分别执行音视频、仅音频、仅视频下载；音视频用真实预览 stream URL。每个 case 写入独立目录，不自动删除下载文件。
- `scripts/real_full_smoke.ps1`：真实调用 `tools/yt-dlp.exe` 分析视频、提取预览 stream URL、执行真实下载、写入真实历史记录。下载文件保留在本次运行目录，不自动删除。
- `scripts/real_url_smoke.ps1`：真实调用 `tools/yt-dlp.exe` 分析视频并执行真实下载，但不覆盖预览 stream。每次运行写入独立目录，不自动删除下载文件。
- `scripts/real_batch_smoke.ps1`：真实分析多个 URL，按配置并发数启动真实下载，验证队列、历史和实际下载文件。默认覆盖普通视频、Shorts 和短视频 3 个真实链接。
- `scripts/real_subtitle_smoke.ps1`：真实下载字幕文件，并使用项目内 `ffmpeg.exe` 执行字幕烧录。默认使用带自动字幕的 Shorts 链接。
- `scripts/package_win.ps1` 与 `scripts/smoke_packaged.ps1`：真实打包与打包产物存在性/ bundled `yt-dlp.exe --version` smoke。

## 非验收单元测试中的 mock/fake

这些测试只用于快速验证分支逻辑、错误处理和 UI wiring，不得作为“全真验收通过”的证据。

- `tests/test_workers.py`：用 `subprocess.CompletedProcess` 模拟分析/预览 worker 的成功、失败、超时、异常分支。
- `tests/test_download_progress.py`：用假 `analysis_runner` 与 `FakeProcess` 验证下载进度解析、状态更新和历史写入 wiring。
- `tests/test_preview_player.py`：用假 `preview_runner` 验证预览 UI 接线和失败不清空下载就绪状态；真实预览/下载验收由 `scripts/real_download_matrix.ps1` 和 `scripts/real_full_smoke.ps1` 覆盖。
- `tests/test_ffmpeg_detection.py`：用临时假 `ffmpeg.exe` 和 monkeypatch 验证搜索、去重、版本解析分支。
- `tests/test_ytdlp_update.py`：用假下载文件和禁止网络 monkeypatch 验证更新状态机、回滚和非阻塞安全规则。
- `tests/test_ui_service_wiring.py`：用假 `QApplication`/`MainWindow` 验证 app bootstrap 注入。
- `tests/test_queue_controls_gui.py`：用同步/收集式 worker runner 验证队列按钮 wiring，不执行真实下载。
- `scripts/render_qa_screenshots.py`：使用已保存的真实分析 metadata 中的格式列表，并覆盖为稳定视觉样本生成截图；不执行实时网络分析或下载，不作为全真验收证据。

## 报告规则

- 以后报告“全真通过”时，必须引用全真脚本或真实 GUI/打包运行证据。
- 使用 mock/fake 的结果只能称为“单元测试通过”或“UI wiring 测试通过”。
- 真实下载产生的文件不得自动删除；删除前先向用户说明路径和大小并等待确认。
- 当前主真实测试链接为 `https://www.youtube.com/watch?v=PqQNXB6hhUs`；批量真实测试还覆盖 `https://www.youtube.com/shorts/oXFad1nt6v0` 和 `https://www.youtube.com/watch?v=svoD582Pas4`。
