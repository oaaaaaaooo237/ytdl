# Android X 音视频自动合并验收

2026-11-04，API37 Play x86_64 可见模拟器，debug 1.0.5 (6)。

## 修复

X 的独立 MP4 HLS 音频缺少 acodec 字段，原格式选择器将其排除，导致默认退为仅视频。桥接层仅在音频轨、MP4、HLS、video.twimg.com 主机及 /mp4a/ 路径均匹配，且原编码缺失时恢复 MP4 audio 编码标识。已有编码与其他网站保持原有映射。随后复用现有分离下载和 Android MediaExtractor / MediaMuxer 自动合并流程。

X 返回帖子但未提供视频的报错现在独立分类，界面引导用户核对原帖与 cookies，不再归为通用网络错误。

## 前台真实流程

两条链接均通过 Computer Use 点击地址框，使用完整 Gboard 逐键输入并核对 URL。选择“视频＋音频”，确认保存权后只点击一次开始下载。队列显示同一任务的下载视频、下载音频、原生合并三个阶段，历史最终显示“视频＋音频”“完成”。

- NBA 帖子 2116379527416799363，无 cookies。1080p 默认合并任务 task-7db0108cc41142b0b2210e6099146dc3。
- 第二条帖子 2109772550783856786，使用用户授权的手机 Chrome X 会话。1066p 合并任务 task-58e36d7a93a8499c8d701ed489ba096f。

## 成品核验

直接复制模拟器任务目录中 App 生成的 MP4 到 .qa-data，未在电脑重新合成。两者均含一条 H.264 视频轨与一条 AAC 音轨，ffmpeg 完整解码成功。

| 成品 | 视频尺寸 | 容器时长 | 文件大小 |
| --- | --- | --- | --- |
| NBA-app-automerged.mp4 | 1920×1080 | 9.130667 秒 | 12748155 字节 |
| X-app-automerged-1066p.mp4 | 480×1066 | 81.269841 秒 | 9396242 字节 |

第二条任务最终媒体文件计数为 1，.parts 已删除。其成品 SHA256 为 591e4faa9f195d26e91b915c286fc9ad7224aa0323aaa1577b8e18e7deccbf71。

## 自动验证与交付

- tests/test_android_ytdl_bridge.py，33 项通过，含缺失编码、显式编码保留、其他主机及 query 伪匹配回归。
- Android testDebugUnitTest，488 项通过，无失败、错误或跳过。将既有版本 smoke 的旧 1.0.4 (5) 断言与仓库已配置的 1.0.5 (6) 同步。
- assembleDebug 构建成功。APK SHA256 为 6135A6FCE6F323E00258E1BF8A22ED5FD7FF9EB87433187A786CBEA672224571。
- 测试结束删除模拟器 Downloads 中的临时 X cookies 文件及其设置引用。凭证值未进入日志或验收文档。

本次证明 API37 模拟器的 X 原生自动合并流程。需要登录的内容仍由用户提供有访问权的 cookies；真机全量验收及 Play 发布不在本次结论内。

## 1.0.6 版本交付

功能前台验收完成后，将本次修复单独标记为 1.0.6 (versionCode 7)，同步版本 smoke 测试。上述 1.0.5 前台与全量测试记录为同一功能代码的验证依据。

- 新版本执行 SmokeUnitTest 和 assembleDebug，BUILD SUCCESSFUL。Python 桥接回归再次执行，33 项通过。
- 交付文件为 dist/android/1.0.6/ytdl-android-1.0.6-debug.apk。aapt2 实读 versionName=1.0.6、versionCode=7，apksigner verify 成功。
- 新 APK SHA256 为 A9B9548A10A653D687A14BFC0390C76D96523F46AFFBFF2955CDA8D859D3AA83。
- 使用 --no-daemon 构建，交付后关闭本轮启动的 adb 服务并复核测试进程。版本与验证记录一并纳入本次 Git 提交。
