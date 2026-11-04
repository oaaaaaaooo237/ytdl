# CaptainFlash 1.0.10

按用户要求暂停三版设计稿的实施，本版仅调整名称、版本显示和非操作性声明，保留既有页面布局及下载、格式、任务、历史、网站 cookies、解析器、外观与通知功能。

## 变更

- 系统应用名称、首页标题、前台下载通知名称统一为 CaptainFlash，applicationId 保持 com.garyapp.ytdl 以保留升级数据。
- 首页标题右侧以 labelSmall 显示实际 BuildConfig 版本 v1.0.10，versionCode 11。
- 删除首页授权勾选和对应下载门控，分析完成后可直接开始或加入队列；删除重复授权文案。
- 删除设置的隐私与授权说明、地址校验说明；简化网站管理的重复存储说明、历史空态数据库实现文字，以及“真实分析/真实任务”等无操作价值的限定词。
- 保留登录限制、网络/解析错误、格式兼容、清理与删除后果等决策信息。设置最后增加可打开、滚动、选择复制的开源组件与许可证来源信息。

## 验证

- Android 全量 500 项单测通过；末轮仅精简分析/格式/队列提示后，重新运行 FormatSelectionModelTest 和 DownloadGuiBindingTest（74 项）及 assembleDebug，通过。
- 可见 API37 模拟器通过 Computer Use 核对首页 CaptainFlash 右侧小号 v1.0.10、授权勾选消失、设置无目标声明、末尾开源声明可打开。
- 使用 Gboard 前台逐键输入 NBA 帖子 2116379527416799363，分析后无需额外勾选，直接点击开始下载。任务 task-b591c2cd84b54e4586a59dc483b514a5 最终显示视频＋音频完成。
- 直接复制 App 生成的 MP4，核验一条 1920×1080 H.264 视频轨和一条 AAC 音轨，9.130667 秒，12748155 字节，完整解码通过。末轮文案包重新安装并核对空态首页。
- aapt2 实读 application-label=CaptainFlash、versionName=1.0.10、versionCode=11；apksigner verify 通过。
- 发布文件 dist/android/1.0.10/CaptainFlash-1.0.10-debug.apk，SHA256 305C69D0F112F1B33631AE219B4210D16AED7B6A88765A5FDC7A77A58A495AA0。
- 按用户要求逐项删除 dist/android 中旧 1.0.5～1.0.9 APK 及其空目录，仅保留新的 1.0.10 APK。Git 历史与标签保留。
- 测试后关闭模拟器、adb 与构建进程，并核对残留。没有实施三版设计稿中的布局调整。
