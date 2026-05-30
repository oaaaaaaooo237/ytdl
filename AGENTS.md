# 项目规则

- 默认使用中文与用户沟通；用户可见的界面文案、错误提示、帮助说明也默认使用中文。
- 权威设计文档：`docs/superpowers/specs/2026-05-30-ytdl-gui-design.md`。
- 权威实现计划：`docs/superpowers/plans/2026-05-30-ytdl-gui-implementation.md`。
- 后续开发必须按实现计划逐项推进；不要绕开计划直接扩展功能或改设计。
- GUI 视觉方向以 `docs/gui-reference.png` 为准。
- 如果发现设计、计划、实现之间冲突，先暂停并指出冲突，不要自行扩大范围。
- cookies 第一版只保存用户选择的 `cookies.txt` 文件路径，不复制或内置保存 cookies 文件内容；日志、历史、错误信息和界面不得泄露 cookies 内容。
- 启动流程不能被网络请求、yt-dlp 更新、视频分析、下载任务或本机工具搜索卡住。
- 不要绕过 DRM、网站权限或用户未授权的访问限制。
- 核心逻辑必须有测试；GUI 和打包必须有 smoke check。
- 没有新鲜验证输出时，不要声称任务完成、测试通过或打包成功。
