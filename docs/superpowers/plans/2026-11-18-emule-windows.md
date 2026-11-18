# Windows 电驴实现计划

**目标**　Windows 便携包可在本机搜索 eD2k/Kad 并直接下载，队列、历史和设置接入现有界面。

**基线**　`codex/emule-windows`，设计见 `docs/superpowers/specs/2026-11-18-emule-windows-design.md`。逐项先验证失败情况，再实现和复验。Android 在 Windows 验收后推进。

## 1. 本机引擎

- [x] 校验 aMule 3.2.0 官方便携包，确认 `amuled` 和 `amuleapi` 的隔离配置、启动、登录和关闭。
- [x] 验证网络连接与搜索 ID 生命周期，并以可合法分发的文件验证真实传输。区分本机接口成功、公共网络可用和文件具备来源。
- [x] 在 `tests/test_emule.py` 先覆盖 eD2k 链接、API 查询、状态错误与超时。在 `src/ytdl_gui/emule.py` 实现必要的本机接口与进程管理。

## 2. 下载入口与搜索页

- [x] 在 `tests/test_emule_ui.py` 覆盖驴头入口、单次搜索单页签、结果数、来源排序、切换保留选中项。另以 Qt 测试驱动实际搜索和下载确认。
- [x] 在 `src/ytdl_gui/ui/pages/emule_search_page.py` 实现搜索页。在 `download_page.py` 和 `main_window.py` 接入驴头、直接粘贴 eD2k 链接以及后台请求。回归既有链接分析。

## 3. 队列、历史与设置

- [x] 核对队列状态显示、暂停、继续、取消、断开状态、断点保存和完成历史去重。
- [x] 在 `main_window.py` 与 `ui/pages/queue_page.py` 接入现有队列展示和任务操作，使用 `history_store.py` 的现有存储能力。完成前核对文件存在及所属目录。
- [x] 在 `ui/pages/settings_page.py` 与 `config_store.py` 加入电驴设置，覆盖旧配置读取、连接状态、默认搜索网络、限速和专用保存目录。

## 4. 打包与可见验收

- [x] 在 `packaging/ytdl_gui.spec` 和 `scripts/package_win.ps1` 加入固定版本引擎包，随包提供许可证、第三方声明和对应源码。
- [ ] 跑全量 pytest，验证从未安装 aMule 的 Windows 环境启动成品包。用可见窗口完成搜索、切换页签、点击下载、真实传输、暂停和重启恢复，最后从历史定位本机文件。
- [ ] 记录测试、成品版本、校验值和未覆盖项。Windows 验收后再创建 Android 本机引擎移植与验收计划。
