# 真机启动闪退：无 ADB 诊断包

## 当前状态

用户反馈 `1.6.1-kitkat.2` 在真实电视上点图标后立刻退出，看不到桌面，且无法取得系统日志。
**闪退原因尚未确认；下面的包用于收集证据，不是已经修好的版本。**

- 文件：[`tvhome-zh-kitkat-diagnostic.apk`](../tvhome-zh-kitkat-diagnostic.apk)
- 应用名：**TVHome 启动诊断**
- 包名：`com.gathin.tvhome.diagnostic`（与原版并存，不覆盖原版及其数据）
- 版本：`1.6.1-kitkat.2-diagnostic.1`
- minSdk 19；v1 + v2 签名；506,817 字节
- SHA-256：`e44def9649d3f3fec346f2db25cb0e282570914cb417b8639eb3c034fc82da07`

## 在电视上怎么操作

1. 将此 **diagnostic** APK 复制到 U 盘，用电视原有文件管理器安装。不用卸载已有版本。
2. 从原桌面打开 **TVHome 启动诊断**。应先出现一张纯文字诊断页，**先拍一张包含 Android/API、型号信息的照片**；内容不全时用遥控器上下滚动。
3. 选择 **启动桌面测试**，让它尝试打开真正的桌面。
4. 如果又闪退，重新打开 **TVHome 启动诊断**（不要打开旧的 tvhome）。如果已经回到诊断页，可选择 **刷新报告**。
5. 拍下 **最后到达的步骤**、**捕获的异常**，以及后面的 `Caused by:` 部分。可上下滚动分几张拍。
6. 在拍完前，**不要再次按“启动桌面测试”**，它会清除上一次报告。不要清除应用数据或卸载诊断包。

如果第 2 步连纯文字诊断页都出现不了，请明确反馈 **“诊断页也闪退”**。这与“诊断页正常、点测试后闪退”是两类不同线索。

如果测试桌面能打开，也请反馈，不要把它当作确认修复：诊断包有独立的数据目录、不同包名、额外报告进程和启动标记，运行条件与原包并不完全相同。

## 安全和限制

- **不接管 HOME**，没有 HOME intent-filter，不修改系统默认桌面；诊断版的无障碍/屏保服务也禁用。请保留原厂桌面，不执行替换/禁用脚本。
- 桌面的应用筛选仍是电视应用默认显示，普通应用通过应用库手动添加。
- 诊断页使用系统 Holo 主题、系统字体和基础控件，不依赖桌面布局、自定义字体或 AndroidX 界面。
- 真正的桌面在独立的 `:desktop` 进程/任务中运行，诊断页不直接加载桌面类。桌面进程失败后可重新查看已保存的记录。
- 报告只保存在本应用的内部文件目录，没有额外权限或上传功能；记录设备/系统/版本/堆大小、启动步骤及能捕获的异常。照片分享前可自行检查异常文本是否包含不想分享的信息。
- 记录 Java 异常后仍交给系统原有异常处理器终止进程，不强行吞掉异常继续运行，也不自动重启循环。
- 原生崩溃、系统杀进程、内存耗尽，以及 Application 启动前的失败不保证能留下堆栈。最后步骤只说明“到达过哪里”，并不证明那个步骤本身就是故障点。`M30` 表示 onResume 代码完成，不表示第一帧已成功绘制。
- 诊断包尚未在该电视执行；静态检查和 JVM 测试不是 YunOS 真机验证。

## 本地验证与复建

```text
python -m unittest discover -s tools/tests -v
cd launcher
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintDiagnostic :app:assembleDiagnostic
```

Windows 可用 `gradlew.bat`；JDK/SDK 配置与 [KitKat 兼容说明](kitkat-compatibility.md) 相同。

已通过 12 个 Python 合约、60 个 Robolectric 用例（SDK 19/21/28）；debug/diagnostic Lint 0 errors、无 NewApi/InlinedApi，仍有其他警告。诊断版保留 release 的 R8 优化和资源缩减，仅保留应用类/方法名及行号信息以方便解释照片中的堆栈。

实际 APK 已检查：独立包名、诊断页启动入口、没有 HOME、桌面进程/任务隔离、API 19 v1/v2 签名、ZIP CRC、单 DEX 035、图标 PNG 回退、无原生库。

日志及独立审查在忽略目录 `launcher/app/build/reports/kitkat/diagnostics-*`；此包的 R8 mapping 另留为 `diagnostics-1-mapping.txt`。

原有 `tvhome-zh.apk`、`tvhome-zh-debug.apk`、`tvhome-zh-kitkat.apk` 和 `device-info.apk` 均未修改，没有向任何连接设备安装应用。
