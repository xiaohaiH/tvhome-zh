# Android 4.4 / YunOS 旧系统兼容版

## 安装哪个文件

使用仓库根目录的 **`tvhome-zh-kitkat.apk`**，版本为 `1.6.1-kitkat.2`（versionCode 12）。
本版纠正了首个兼容试装包自动导入普通应用的行为，恢复原项目的“默认只显示电视应用”策略。

| 项目 | 兼容版 |
| --- | --- |
| 包名 | `com.gathin.tvhome` |
| 最低系统 | Android 4.4 / API 19 |
| 签名 | v1（KitKat 必需）+ v2 |
| 字节码 | 单个 `classes.dex`，DEX 035 |
| CPU 架构 | 无原生 `.so`，不限定 ARM64 |
| 编译/目标 SDK | compileSdk 35 / targetSdk 34；不是最低安装版本 |

目标设备是用户提供的 `CVTE_MSD338_512M_PB801` / YunOS 2.1.0，按其 Android 4.4.4 兼容能力适配。
**2026-10-30 用户反馈：真实电视上点图标后立刻闪退，尚未显示桌面。原因尚未确认，当前包不能视为已兼容该电视。**
无法获取系统日志时，请使用另行提供的 [无 ADB 启动诊断包](startup-diagnostics.md)，通过屏幕报告拍照反馈。
厂商固件的默认桌面、遥控器及信号源行为仍未验证。

根目录原有两个 APK 未覆盖：`tvhome-zh.apk` 仍是最低 Android 8.0 的旧包，`tvhome-zh-debug.apk` 仍是最低 Android 5.0 的原包。不要拿它们测试 KitKat 适配。

## 不连接 ADB 的安装步骤

1. 把 `tvhome-zh-kitkat.apk` 复制到电视能读取的 U 盘，用电视文件管理器打开安装。按系统提示允许该来源安装应用即可。
2. 安装后先从原桌面的应用列表打开 **tvhome**，测试遥控器和应用启动。
3. **暂时不要禁用、卸载原桌面，也不要执行仓库的一键替换/禁用脚本。** 确认稳定后，再通过系统允许的方式选择默认桌面。
4. 低内存电视建议先使用纯色背景、关闭每日壁纸和天气，并在桌面设置中启用“减少动画”；稳定后按需打开功能。

文件完整性可在 Windows 上检查：

```powershell
Get-FileHash .\tvhome-zh-kitkat.apk -Algorithm SHA256
```

将结果与 `tvhome-zh-kitkat.apk.sha256` 比较。若仍出现“解析程序包出现问题”，优先核对复制后的文件是否完整、安装的文件名是否正确。截图的通用提示本身不能区分所有厂商限制，需要后续具体错误或安装日志。

### 签名与覆盖安装

本次构建使用**本机 Gradle 的 debug 密钥**（通常位于 `~/.android/debug.keystore`），不是上游作者的私钥。Release 构建进行了 R8 压缩，但沿用本地 debug 签名，适合本次侧载测试，不是商店发布签名。

- 如果此前一直安装失败，通常没有旧包需要处理，直接安装兼容版即可。
- 如果同包名的上游版本已经安装成功，本包不能直接覆盖不同签名的旧包。应先备份设置，并通过系统界面卸载旧版再安装；**卸载会清除应用数据**。操作前保留可用的原厂桌面。
- 后续构建请保留同一份本地 keystore；更换密钥也会导致无法覆盖升级。不要把私钥提交到仓库。

## 实际适配内容

不只是修改 `minSdk`：

- KitKat 使用 Holo 全屏主题；API 21+ 保留 Material 主题。
- 矢量图在构建时生成 mdpi/hdpi/xhdpi/xxhdpi PNG，供旧版 `ImageView` 和文字按钮加载。
- KitKat 的图标 tint 使用普通灰色值；API 21+ 保留聚焦时切换颜色的 ColorStateList。旧 `ImageView` 把状态颜色当整数读取会直接崩溃。
- 卡片圆角改用 API 19 可用的 `Canvas.clipPath`；光圈使用旧版 `drawRoundRect(RectF, ...)`。
- PopupWindow 阴影仅在 API 21+ 启用，旧系统仍保留弹窗内容和遥控器操作。
- 网络状态改用生命周期内注册/注销的广播与 `NetworkInfo`，不会在 KitKat 启动时创建不存在的 `NetworkCallback`，也不会把旧系统正常连接一律显示为离线。
- **保持纯净电视桌面的默认逻辑**：所有系统版本都只自动枚举 `LEANBACK_LAUNCHER` 电视应用；即使没有找到电视应用，也不会自动导入普通 `LAUNCHER` 应用。普通应用仍可通过应用库手动添加、移除，常用栏也只列出电视应用与已手动添加的应用。
- `LEANBACK_LAUNCHER` 是可在 API 19 查询的 Intent 字符串，不需要调用新版框架接口。另保留 tvhome 自己的普通启动入口，方便从旧电视原桌面打开；这不改变其他应用的筛选规则。
- 标准 TV Input Framework 调用隔离在 `TvInputCatalog` 中，只在 API 21+ 执行。

### 保留的限制

- **HDMI/AV 信号源**：KitKat 不提供 API 21 的标准输入枚举；兼容版尝试已有厂商 provider/已配置的原生信号源入口，没有可用入口时提示无法列出。未猜测或硬编码 CVTE 私有接口。先保留电视原有的信号源遥控键，默认不接管它。
- **农历**：沿用原逻辑，仅 API 24+ 使用系统 ICU；KitKat 不显示农历。
- **天气、必应壁纸**：旧系统的 TLS、根证书和网络服务兼容性尚未真机验证。失败不等于桌面无法运行；没有关闭证书校验或改用不安全连接。
- **默认桌面、Home/设置键、屏保**：仍受 YunOS 的厂商实现影响。JVM 测试不等同于这些功能已经在电视上验证。

## 本地重新构建（不安装到任何设备）

需要完整 JDK 17+、Android SDK Platform 35，以及 Gradle 请求的 Build Tools 34.0.0。缺少依赖时需要联网下载；这不需要连接电视。

先设置 `JAVA_HOME` 为自己的 JDK 或 Android Studio 的 `jbr` 路径，`ANDROID_HOME` 为 SDK 路径。Windows PowerShell 示例：

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
# $env:JAVA_HOME = "你的 Android Studio 安装目录\jbr"

python -m unittest discover -s tools/tests -v
cd launcher
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
Copy-Item .\app\build\outputs\apk\release\app-release.apk ..\tvhome-zh-kitkat.apk
```

macOS/Linux 使用相同的 Gradle 任务，将 `gradlew.bat` 替换为 `./gradlew`。这些命令**只编译和测试，不会安装、修改默认桌面或连接电视/手机**。

用 SDK Build Tools 中的工具核验最终 APK（工具目录需在 PATH 中）：

```text
aapt dump badging tvhome-zh-kitkat.apk
apksigner verify --verbose --min-sdk-version 19 tvhome-zh-kitkat.apk
```

应分别看到 `sdkVersion:'19'` 和 `Verified using v1 scheme (JAR signing): true`。重新构建后请重新生成 SHA-256，原校验文件不会自动更新。

## 已执行的验证与未验证部分

- 7 项 Python 构建/资源契约检查通过。
- 36 项 Robolectric 测试通过，覆盖 API 19、21、28 的 Activity 生命周期、网络标签、弹窗/屏保布局、圆角与光圈绘制调用、矢量图回退，以及电视应用默认筛选、普通应用手动添加/移除、两类应用的启动入口和常用栏筛选。
- 新增筛选回归用例先在旧兼容逻辑下复现了 5 个 API 19 失败；恢复默认逻辑后全部通过。
- Android Lint：0 错误，未发现 `NewApi` / `InlinedApi` 问题；仍有其他提示（布局、翻译、弃用接口等），不是零警告。
- Release APK 已检查最低 API、普通启动入口、v1/v2 签名、ZIP 完整性、单 DEX 035、PNG 资源及无原生库。
- 独立代码审查未发现阻塞试装的问题；有真实端口的 TV Input Framework、YunOS 固件行为和真实 GPU 绘制仍需设备验证。

### 到电视后逐项检查

- [ ] U 盘安装成功，原厂应用列表可以打开 tvhome。
- [ ] 首次启动、退出后重进、待机恢复不闪退。
- [ ] 遥控器上下左右、确认、长按移动、返回均正常。
- [ ] 默认只显示电视应用，不自动填充普通应用；没有电视应用时也不自动放宽筛选。
- [ ] 通过应用库手动添加普通应用后，能显示、打开、加入常用栏；移除后不再自动出现。
- [ ] 桌面设置、应用库、快捷设置正常，图标可见。
- [ ] Wi-Fi/有线状态与实际一致，断网和恢复后能更新。
- [ ] 本地壁纸可选择；天气/网络壁纸按需测试。
- [ ] 电视自身的信号源键、Home 键和默认桌面选择符合预期。

前几项没有确认前，不要移除原厂桌面。
