# NFC Lab 技术方案与调研结论

## 1. 目标

本工程用于研究 Root Android 设备上的 NFC 能力边界，并建立可持续扩展的实验平台：

- 识别 NFC 标签和 Android 暴露的技术栈；
- 读取标准公开 NDEF 与协议元数据；
- 观察 Root 环境下 NFC Framework / Service / HAL / Routing 状态；
- 使用 Android 官方 HCE 构建**自有测试协议**，验证手机作为 ISO-DEP 卡与外部 reader 通信；
- 把“读取能力”“协议分析”“HCE 测试协议”和“Root 诊断”隔离，避免后续演进相互污染。

不把“能读取某张卡”等价为“能完整模拟该卡”。门禁卡经常依赖 UID、ISO 14443-3 层行为、私有认证或安全芯片密钥，这些不属于普通 Android HCE 的等价模拟范围。

## 2. 实机基线（2026-08-14）

ADB 对当前 Redmi K20 Pro (`raphael`) 的实测：

| 项目 | 结果 |
|---|---|
| 型号 | Xiaomi Mi 9T Pro / Redmi K20 Pro |
| Android | 11 / API 30 |
| Build | `V12.5.2.0.RFKMIXM` |
| Root | Magisk，`su -c id` => UID 0 |
| SELinux | Enforcing |
| Boot | Verified Boot orange / flash unlocked |
| NFC | 已开启 |
| Android features | NFC / HCE / HCE-F / eSE / UICC |
| NFC system libs | `libsn100nfc-nci.so`, `libsn100nfc_nci_jni.so` |
| HAL | `nqnfc_1_2_hal_service` |

结论：这台机器非常适合做 NFC Framework/HAL/HCE 研究，且具备普通应用 HCE 所需的 Android feature。

## 3. GitHub / AOSP 参考工程

这些仓库已克隆到根目录 `参考工程/`，并由 `.gitignore` 排除，不污染主工程版本控制。

### 3.1 `googlearchive/android-CardEmulation`

- 原 Google Android HCE 样例。
- 核心结构：AID 注册 + `HostApduService` + `processCommandApdu()`。
- 价值：最清晰地说明 Android HCE 的本质是 ISO-DEP/APDU 通道，不是任意射频卡复制。
- 仓库：https://github.com/googlearchive/android-CardEmulation

### 3.2 `AndroidCrypto/Android_HCE_Beginner_App`

- 同时包含 HCE 与 reader 侧教学代码。
- README 特别指出 Android HCE 暴露给 reader 的低层 Tag UID 不是一个可由普通应用固定控制的真实卡 UID。
- 价值：帮助区分“应用层持久 ID”与“射频层 UID”。
- 仓库：https://github.com/AndroidCrypto/Android_HCE_Beginner_App

### 3.3 `MichaelsPlayground/NfcHceNdefEmulator`

- 使用 HCE 实现 NFC Forum Type 4 NDEF Tag。
- 展示 SELECT NDEF application、Capability Container、SELECT NDEF file、READ BINARY 等 APDU 序列。
- 价值：证明标准 NDEF 模拟本质上仍构建于 ISO-DEP APDU 协议之上。
- 仓库：https://github.com/MichaelsPlayground/NfcHceNdefEmulator

### 3.4 `entur/android-nfc-lib`

- 对 Android internal/external NFC reader、tech wrapper、reader callbacks、HCE abstraction 做了较完整拆层。
- 价值：本工程后续扩展 USB/Bluetooth 外接 reader 时，可以参考其 wrapper/core/external 分层，而不是把所有协议塞到 Activity。
- 仓库：https://github.com/entur/android-nfc-lib

### 3.5 AOSP `packages/modules/Nfc`

- 使用 Android 官方当前 NFC 模块源码，重点关注：
  - `HostApduService`
  - `HostEmulationManager`
  - `AidRoutingManager`
  - `CardEmulationManager`
  - `RoutingOptionManager`
- 价值：Root 调研必须以系统实际 NFC stack 为准，而不是只看 SDK public API。
- 源码：https://android.googlesource.com/platform/packages/modules/Nfc

### 3.6 未直接集成的研究工具

NFCGate 的公开架构包含 capture / relay / replay / clone，能说明 Root/Xposed 环境下研究者如何扩展 Android NFC 观测面；但这些能力直接落地会越过本项目当前“授权实验与自有协议”的边界，因此只用于理解系统分层，不作为实现依赖或参考仓库拷入。

## 4. 为什么 Root 也不能自动“完整模拟任意门禁卡”

### 4.1 Android 官方 HCE 的边界

`HostApduService` 面向 ISO-DEP（ISO/IEC 14443-4）和 ISO/IEC 7816-4 APDU。外部 reader 通过 SELECT AID 把 APDU 路由到应用。

这意味着 HCE 很适合：

- 自定义会员卡/身份协议；
- 自有 reader + 自有 Android card protocol；
- NFC Forum Type 4 / NDEF；
- 某些本来就是 APDU/AID 模型的协议。

但很多门禁系统并不只依赖这一层。

### 4.2 典型差异

| 卡/协议特征 | 普通 Android HCE |
|---|---|
| ISO-DEP / APDU | 强支持 |
| 自定义 AID | 支持 |
| Type 4 NDEF | 可实现 |
| 固定模拟任意实体卡 UID | 通常不由普通 HCE 应用控制 |
| ISO 14443-3A 原始卡行为 | 不是 `HostApduService` 的抽象层 |
| MIFARE Classic Crypto1 密钥 | HCE 不会替你取得 |
| DESFire/安全卡内部长期密钥 | 密钥不可凭 Root 自动复制 |
| eSE/UICC 已安全下发凭证 | 由安全元件/系统 routing 管理，不等于普通 app 可导出 |

因此第一阶段的正确目标是**识别卡型与通信层级**，再判断是否落在 Android HCE 能表达的协议集合中。

## 5. Root 的实际价值

Root 的主要增益应放在“可观测性”和“实验控制面”，而不是默认绕过安全机制：

1. 查看 `dumpsys nfc` 的 HCE service / AID cache / routing table。
2. 查看 NFC HAL service、系统属性、vendor library。
3. 对比 app 层 `PackageManager` feature 与系统实际 stack。
4. 后续可建立独立 `root-bridge` 模块，对允许的诊断命令做白名单化封装。
5. 可为系统版本/ROM 差异采集基线，判断 MIUI、AOSP、第三方 ROM 对 HCE/routing 的差异。

本版本没有修改 `/system`、vendor 配置、NFC firmware、SELinux policy 或系统 routing policy。

## 6. 工程架构

```text
app / Compose UI
├── Reader / Inspector
│   ├── Android NfcAdapter.ReaderMode
│   ├── Tag tech capability inspector
│   └── public NDEF reader
├── HCE Lab
│   ├── LabHostApduService
│   └── LabApduProtocol (pure protocol core)
├── Root Diagnostics
│   └── su -> dumpsys/getprop/service
└── Local Storage
    ├── scan metadata history
    └── HCE lab payload
```

关键隔离：

- **扫描结果不会自动成为 HCE payload**。
- HCE 使用固定的本应用 proprietary AID `F001020304050607`。
- `LabApduProtocol` 是纯 Kotlin 协议层，可单测，不依赖 Android Service 生命周期。
- Root 目前只在 `RootNfcDiagnostics` 中使用，避免 Root 能力扩散到业务 UI。

## 7. UI 方案

采用官方 Jetpack Compose + Material 3：

- 顶部展示设备能力；
- “读取 / 检查”与“HCE 测试卡”显式模式切换；
- Reader 页面直接展示 UID、tech list、ATQA/SAK、ISO-DEP 能力、NDEF；
- HCE 页面展示测试 AID 与可编辑 payload；
- Root 页面执行并折叠展示系统诊断；
- 最近扫描记录保存在 app private storage。

模式切换是必要的，因为 Android `enableReaderMode()` 会让 NFC controller 只处于 reader/writer 模式，从而关闭当前设备上的 card emulation。

## 8. 技术栈

基于 2026-08 的稳定方案：

- Android Gradle Plugin 9.3.0
- Gradle 9.5
- AGP 9 built-in Kotlin + Kotlin/Compose plugin 2.4.10
- compileSdk 37.1 / targetSdk 37 (Android 17)
- Android SDK Build Tools 37.0.0
- Jetpack Compose BOM 2026.06.00
- Material 3
- Activity Compose 1.13.0
- Lifecycle 2.11.0
- Java/JVM 17 target

当前 Mac 已补装 Android SDK Platform 37.1 与 Build Tools 37.0.0。`compileSdkMinor = 1` 用来明确选择 37.1；`targetSdk` 仍按 Android 的 major API 级别设置为 37。

## 9. 权限策略

“最大权限”按**对 NFC 研究真正有用的权限面**实现，而不是无差别申请 Android 危险权限：

- Manifest: `android.permission.NFC`
- NFC/HCE/HCE-F uses-feature 声明
- HCE service: `android.permission.BIND_NFC_SERVICE`
- Root: 通过 Magisk `su` 单独授权 UID 0 诊断进程
- 本地数据：app private storage，不申请全盘文件权限

Android 并不存在一个“Root Manifest permission”；Root 是由设备上的 `su` 管理器在运行时授予。

## 10. 下一阶段验证

### P0：当前工程

- 编译 Debug APK；
- 安装 Redmi K20 Pro；
- 验证 NFC Reader Mode 能收到标签；
- 验证 Root 诊断能通过 Magisk UID 0；
- 验证 HCE service 被 `dumpsys nfc` 注册并出现测试 AID。

### P1：双设备 HCE E2E

- 用第二台 NFC Android 设备做 reader；
- SELECT `F001020304050607`；
- 发送 `80CA000000`；
- 验证返回 UI 配置的 test payload + `9000`。

### P2：卡型能力矩阵

对自有测试卡逐张记录：

- NFC-A/B/F/V
- MIFARE Classic/Ultralight
- ISO-DEP
- NDEF
- ATQA/SAK / historical bytes
- 是否属于 HCE 可表达的 APDU 协议

### 自动化扫描接口

为避免依赖人工 UI 操作，`MainActivity` 支持 ADB extras：

- `automation_mode=reader`
- `session_id=<id>`
- `timeout_ms=<1s..30s>`

自动化启动后会：

1. 切换到 Reader Mode；
2. 主动重新 arm 一次 Reader Mode，促使已贴近的静态卡重新走 RF discovery；
3. 把状态写入 `files/scan_status.json`；
4. 发现 Tag 后把完整公开扫描信息写入 `files/last_scan.json`；
5. 超时仍无 Tag activation 时明确记录 `noTagActivation=true`，而不是把“无卡”误报成解析失败。

这套接口用于自动化诊断 Reader/HCE/射频发现链路，不包含受保护扇区认证、密钥恢复或门禁凭证重放。

### P3：Root stack observability

- 把 MIUI 当前 NFC service/HAL/routing 基线结构化；
- 对比 AOSP/第三方 ROM；
- 评估是否需要独立 `root-bridge` module 与可测试 command adapter。

## 11. 结论

这台 rooted Redmi K20 Pro 非常适合建立 Android NFC 研究平台，并且**确定支持 Android HCE**。但“完整读取门禁卡并保存后模拟”不能作为一个对所有卡型成立的统一能力：是否可模拟取决于卡片工作在哪一层、是否包含不可导出的秘密、reader 是否依赖固定低层 UID/时序/射频行为等。

工程因此选择先把可观测性、卡型识别、标准读取和自有 HCE 协议做扎实，再针对已获授权的具体测试卡判断其协议是否落在 HCE 可表达范围内。

## 12. 2026-08-14 实机验收记录

### 12.1 已通过

- `./gradlew testDebugUnitTest assembleDebug`：**BUILD SUCCESSFUL**。
- `./gradlew lintDebug`：**BUILD SUCCESSFUL**。
- `git diff --check`：通过。
- Debug APK 已安装到 `raphael`，包名 `com.arthur.nfclab`，版本 `0.1.0`。
- Android 11 真机可正常冷启动，Compose UI hierarchy 可读取，未观察到 `FATAL EXCEPTION`。
- Manifest `android.permission.NFC` 已被系统授予。
- `dumpsys nfc` 已识别 `LabHostApduService`，AID `F001020304050607` 已进入 host routing table。
- 默认 Reader Mode 实测：`mEnableReader=true`、`mEnableHostRouting=false`。
- 切换 HCE Mode 实测：`mEnableReader=false`、`mEnableHostRouting=true`。
- UI 状态与系统 NFC service 状态一致，不是仅在 Compose 层切换标签。
- 参考仓库均位于 `参考工程/`，并确认被根目录 `.gitignore` 排除。

### 12.2 尚需实体介质完成的验证

当前会话没有把一张获授权的实体 NFC 门禁/测试卡贴到手机，因此以下项目不能伪造为“已验证”：

- 实体卡 Reader callback 是否触发；
- 该具体卡的 Tech List / ATQA / SAK / ISO-DEP / NDEF 结果；
- 该具体卡是否属于普通 Android HCE 可表达的协议层；
- 两台手机近场贴合后的真实 APDU E2E 往返。

这些能力代码已经存在，但必须以真实 NFC 射频交互作为验收条件。

### 12.3 Magisk 25.2 注意事项

设备当前 Magisk daemon 版本为 `25.2`（version code `25200`）。ADB 已反复验证 `su -c id` 可以获得 `uid=0(root)`。

第一次从新安装的 NFC Lab 发起 `su` 请求时，Magisk 的授权 UI 对 UIAutomator 不暴露可点击节点，并且该次请求后 Magisk daemon 曾停止响应；设备重启后 Root 已恢复正常。为了避免用坐标盲点授权或直接改写 `/data/adb/magisk.db` 带来额外风险，本次没有强行注入 Superuser policy。

因此当前状态应准确表述为：

- **设备 Root：已验证可用**；
- **应用 Root 诊断实现：已完成**；
- **应用级 Magisk 永久授权：尚未自动注入**。

后续若继续做 Root 深层 NFC stack 研究，优先建议先处理 Magisk 管理器/daemon 的版本与授权稳定性，再开展 vendor HAL/NCI 观测，避免把旧 Root 管理层异常误判成 NFC Framework 问题。

## 13. 非 Root 三星设备对照实验

### 13.1 设备

- Samsung SM-S908E（Galaxy S22 Ultra）
- Android 14 / API 34
- 未 Root（ADB shell 为 `uid=2000(shell)`）
- 系统 feature 同样声明 NFC / HCE / HCE-F / eSE / UICC

### 13.2 自动扫描结果

将最新 NFC Lab Debug APK 安装到三星后，通过同一套 ADB automation reader session 扫描用户贴在背面的卡，首次 session 即检测成功，并在自动重臂后再次重复检测到相同 UID。

机器可读结果：

```text
UID / Tag ID: 107662CC
Technologies: MifareClassic, NdefFormatable, NfcA
ATQA: 0400
SAK: 0x08
MIFARE Classic type: Classic
Size: 1024 bytes
Sectors: 16
Blocks: 64
NDEF records: none
```

因此该卡可明确识别为 **MIFARE Classic 1K / NFC-A**。

### 13.3 对 Root 需求的结论

这个实验直接证明：

- **普通 NFC Tag discovery 不需要 Root**；
- UID、ATQA、SAK、Android Tech List、MIFARE Classic 型号/容量等公开元数据可以由标准 Android Reader Mode 读取；
- Root 的价值主要在 system service / HAL / NCI / vendor stack 可观测性，而不是基础读卡能力。

三星结果也验证了 NFC Lab 的 Reader callback 与机器可读 automation scan 链路本身是正常的。红米先前的 `noTagActivation=true` 不能再解释为应用 Reader 实现错误，应优先考虑：

1. 红米背后的卡与三星这张不是同一类型；
2. 红米 NFC 线圈与卡片位置没有有效耦合；
3. 红米设备/ROM 在当前 RF 条件下未完成该标签的 discovery。

### 13.4 MIFARE Classic 的后续研究边界

这张卡属于 MIFARE Classic 1K。其 1 KB EEPROM 按 16 个 sector / 64 个 block 组织，受 sector key 认证保护的数据不能仅靠普通 Android Reader Mode 自动读取。

当前 NFC Lab 有意停在：

- 卡型识别；
- UID / ATQA / SAK；
- sector/block topology；
- 公开 NDEF / capability metadata。

不会自动执行未知门禁卡的密钥恢复、受保护 sector 导出或真实门禁凭证克隆。后续如果要继续研究，应使用**自有测试卡和已知测试密钥**搭建 MIFARE Classic auth/read/write 实验链路，并与 HCE 模拟能力分开评估，因为 Android HCE 并不能原生等价模拟 MIFARE Classic 的 ISO 14443-3A + Crypto1 卡行为。

## 13. 实体门禁卡自动扫描实验（2026-08-14 21:47-21:49）

用户已将一张待研究的门禁卡贴在 Redmi K20 Pro 背面。本轮不依赖人工 UI 操作，使用新增的 ADB 自动扫描协议完成。

### 13.1 自动化步骤

每轮扫描自动执行：

1. `svc nfc disable`
2. `svc nfc enable`
3. 冷启动 NFC Lab
4. 通过 Intent extra 强制进入 Reader Mode
5. Reader Mode 自动 re-arm 一次
6. 读取 `files/scan_status.json`
7. 同时检查 Android `NfcService`、NXP HAL/NCI 原始帧和应用 `NfcLabScan` 日志

随后又独立重复了 3 轮 RF cycle，以排除一次性状态异常。

### 13.2 四轮结果

首次 10 秒 session：

```json
{
  "status": "timeout",
  "noTagActivation": true
}
```

之后 3 个独立 5 秒 session 均得到相同结果：

- `rf-cycle-1`: timeout / noTagActivation
- `rf-cycle-2`: timeout / noTagActivation
- `rf-cycle-3`: timeout / noTagActivation

应用侧没有 `tagDiscovered`，`last_scan.json` 没有生成。

### 13.3 系统层交叉验证

每轮均确认：

```text
mState=on
mEnableReader=true
mEnableHostRouting=false
mTechMask=47
```

NXP HAL 反复输出 `Polling Loop Started`，说明 NFC controller 确实在主动轮询。

AOSP `nci_defs.h` 中：

- `NCI_MSG_RF_DISCOVER = 3`
- `NCI_MSG_RF_INTF_ACTIVATED = 5`
- `NCI_MSG_RF_EE_DISCOVERY_REQ = 10`

对应检查当前 NXP `NxpNciR` 原始接收帧：

```text
RF_DISCOVER_NTF (0x6103): 0
RF_INTF_ACTIVATED_NTF (0x6105): 0
RF_EE_DISCOVERY_REQ_NTF (0x610A): 有记录
```

其中 `0x610A` 来自 NFCEE / Secure Element 路由，不表示外部实体卡被发现。

因此本轮失败点确定发生在 **Android Tag 对象产生之前的 RF discovery 层**，不是 UID/NDEF/MIFARE 解析器、应用权限、Reader callback 或 UI 问题。

### 13.4 当前最可能的原因

按当前证据排序：

1. 门禁卡没有处于 K20 Pro NFC 天线的有效耦合区域；
2. 该门禁卡不是 13.56 MHz NFC，而是 125 kHz 等 LF RFID；
3. 极少数不属于 Android/NFC controller 当前 polling technology 集合的私有射频协议。

K20 Pro 的拆机资料显示，其 NFC 天线不像很多手机那样紧邻摄像头，而是位于**电池上方/中部电池区域上方的盖板区域**。因此测试该机时不能仅凭“卡已经贴在手机背面”推断已经进入 NFC coil 的有效耦合区。

### 13.5 能力边界

Root、NCI 日志和 vendor HAL 可以把“为什么没读到”定位到 RF discovery 层，但软件无法让 13.56 MHz NFC 天线接收 125 kHz LF RFID，也无法通过 ADB 改变实体卡和手机线圈的物理相对位置。

如果后续确认门禁卡是 125 kHz，应把架构扩展为 **外接 LF RFID reader -> Android USB/Bluetooth bridge -> NFC Lab protocol inspector**，而不是继续修改 Android NFC Framework。

## 14. Redmi K20 Pro NFC 质检（2026-08-14 22:04-22:15）

在交叉测试已经确认一张 MIFARE Classic 1K 卡可被 Samsung S22 Ultra 稳定读取、但放到 Redmi K20 Pro 后完全没有 RF activation 的前提下，进一步对 Redmi 做系统设置、MIUI 厂商自检、厂商贴卡测试和内核 IRQ 四层质检。

### 14.1 MIUI 设置页：NFC 确认已开启

通过 `android.settings.NFC_SETTINGS` 打开 MIUI “连接与共享”页面后下滑到 NFC 区域，UIAutomator 实际读取到：

```text
title: NFC
summary: 允许手机在接触其他设备时交换数据
android:id/switch_widget checked=true
```

同页还存在：

- 安全模块位置：当前使用 SIM 卡钱包；
- 非接触付款；
- 一键修复 NFC：用于修复开卡、充值、刷卡或读卡失败等问题。

Android Framework 同时确认：

```text
Service nfc: found
mState=on
mScreenState=ON_UNLOCKED
```

因此当前外部卡读取失败不是“系统 NFC 开关关闭”造成的。

### 14.2 MIUI 自动 NFC/eSE 自检：通过

反编译系统自带 `/system/app/Cit/Cit.apk` 后定位到：

- `com.miui.cit.connect.AutoNfcCheckActivity`
- `com.miui.cit.connect.CitNfcCheckActivity`
- `com.miui.cit.connect.CitNfcToolActivity`
- `com.miui.cit.connect.CitResetSEActivity`

`AutoNfcCheckActivity` 的源码明确把 `raphael` 列入 PN10X/NXP 支持产品列表，并通过 `INfcAdapterExtras` / eSE logical channel 读取 CPLC。

实际启动厂商自动测试后日志为：

```text
AutoNfcCheckActivity: Open logical channel successfully
AutoNfcCheckActivity: Getting CPLC
AutoNfcCheckActivity: ese_cplc resutl:9F7F2A479007644701F356060090841455173002864810000000510000044D3B031952800100000000003545529000
AutoNfcCheckActivity: auto test nfc check sucess
```

这证明以下内部链路是工作的：

```text
Android Framework
  -> NXP NFC service / HAL
  -> NFC controller
  -> eSE / internal wired interface
```

因此不能把当前问题概括为“NFC 芯片完全损坏”。

### 14.3 MIUI 厂商实体贴卡测试：失败

随后直接启动 Xiaomi 自带的 `CitNfcCheckActivity`。该 Activity 源码使用系统 `NfcAdapter.enableForegroundDispatch()`，监听 `NfcA / NfcB / NfcF / NfcV`，收到 `TECH_DISCOVERED` 后会：

- 记录 `NFC test PASS`；
- 列出 Tag technologies；
- 启用“通过”按钮。

实际测试时，已在 Samsung 上验证可读的 MIFARE Classic 1K 卡保持贴在 Redmi 背部。点击“初始化 NFC 测试”后等待约 8 秒，结果：

```text
UI: 把卡片放在手机背面
pass_bt: enabled=false
```

日志只有：

```text
CitNfcCheckActivity: **** click nfc test init button ****
CitNfcCheckActivity: **** in initNfc func ****
CitNfcCheckActivity: ** check nfcAdapter != null,will enableForegroundDispatch ***
```

没有：

- `onNewIntent`
- `TECH_DISCOVERED`
- `NFC test PASS`
- 任意 Tag technology

这意味着 **Xiaomi 自己的厂商读卡代码同样无法发现该已知正常的 13.56 MHz NFC 卡**。因此 NFC Lab 的实现基本可以从故障原因中排除。

### 14.4 内核驱动 / IRQ 质检

内核已经注册 NXP NQ/NCI 相关节点：

```text
/sys/class/nqx/nq-nci
/sys/bus/i2c/drivers/nq-nci
/sys/firmware/devicetree/base/soc/i2c@a84000/nq@28
```

中断资源：

```text
IRQ 225 / GPIO 47 / Edge / nq-nci
```

在重新进入 NFC Lab Reader Mode 时，`nq-nci` IRQ 总计数从此前约 508 增长到 517，说明主控与 NFC controller 在模式切换/配置过程中确实产生硬件中断和通信。

随后保持：

```text
mState=on
mTechMask=47
mEnableReader=true
mEnableHostRouting=false
```

并让已知正常的 MIFARE Classic 卡持续贴着，8 秒自动扫描期间：

```text
T+2s IRQ total: 517
T+6s IRQ total: 517
END  IRQ total: 517
scan result: timeout / noTagActivation=true
```

也就是说控制器初始化/配置能产生 IRQ，但外部卡存在期间没有新的卡场响应进入 NQ/NCI 中断链路。这与此前 `RF_DISCOVER_NTF=0`、`RF_INTF_ACTIVATED_NTF=0` 完全一致。

### 14.5 NFC 单音产测工具

MIUI CIT 还包含 `CitNfcToolActivity`，其源码显示这是一个 NFC RF single-tone 工程测试：

```text
start: pnx --mcc 8
       pnx --frf 1

stop:  pnx --mcc 8
       pnx --frf 0
```

该工具会直接改变 NFC controller 的 RF test mode。当前用户版 ROM 中未找到可直接 shell 调用的 `pnx` 可执行文件；同时没有外部 RF field meter 时，即使强行进入 13.56 MHz 单音模式，也无法可靠判断天线实际辐射强度。因此本轮没有执行该产测命令，避免为了无判据的测试改变控制器工程状态。

### 14.6 质检结论与维修优先级

当前证据矩阵：

| 层级 | 结果 |
|---|---|
| MIUI NFC 设置开关 | ✅ 已开启 |
| Android NFC service | ✅ `mState=on` |
| NXP HAL / polling stack | ✅ 正常工作 |
| eSE / logical channel / CPLC | ✅ MIUI 自动自检 PASS |
| NQ/NCI kernel driver + IRQ | ✅ 已注册且控制器通信有 IRQ |
| NFC Lab 读取已知正常 MIFARE 卡 | ❌ 无 RF activation |
| MIUI CIT 读取同一张卡 | ❌ 无 `TECH_DISCOVERED` |
| NCI `RF_DISCOVER_NTF / RF_INTF_ACTIVATED_NTF` | ❌ 0 次 |
| Reader Mode 下外部卡导致的新 `nq-nci` IRQ | ❌ 未观察到 |

因此当前最合理的定位是：

> **NFC controller / 软件栈 / eSE 基本正常，但“外部 13.56 MHz RF 天线耦合路径”高度可疑。**

硬件检查优先级建议：

1. 确认 Redmi K20 Pro NFC 天线线圈的真实位置，并把已知正常卡覆盖整个有效线圈区域重新扫；
2. 检查后盖/中框上的 NFC 天线线圈是否为原装或是否曾更换；
3. 检查 NFC antenna spring contact / pogo contact 是否压接正常、氧化、变形或缺失；
4. 检查后盖拆修后是否有绝缘胶、磁吸附件、金属片影响耦合；
5. 若触点和线圈正常，再考虑 NFC RF front-end / matching network 的板级故障。

在进行上述物理检查前，不建议继续修改 NFC Lab Reader 代码，因为应用层和 Xiaomi 厂商代码已经得到相同失败结果。

## 16. Root Native Bridge 与 SELinux（2026-08-15）

### 16.1 为什么 Root 后仍显示 SELinux Enforcing

Android 权限不是只有 Unix UID/GID 一层。当前 Samsung S22 Ultra 实测：

```text
普通 ADB shell:  uid=2000, context=u:r:shell:s0
Magisk su:       uid=0,    context=u:r:magisk:s0
NFC Framework:   uid=nfc,  context=u:r:nfc:s0
NFC HAL:         uid=nfc,  context=u:r:hal_nfc_default:s0
SELinux:         Enforcing
```

因此 `uid=0(root)` 表示 Unix discretionary access control 层取得最高 UID，但 SELinux mandatory access control 仍继续按 `source domain -> target type -> class -> permission` 决策。某个 Root 进程如果不具备对应 allow rule，仍可能收到 AVC denial。

这也是为什么项目不把“Root”简单定义成“所有 NFC 底层接口都自动可访问”。

### 16.2 已落地 Native Bridge

工程新增：

```text
app/src/main/cpp/nfc_root_bridge.c
app/src/main/java/com/arthur/nfclab/root/RootNativeBridge.kt
```

Gradle 使用 NDK `28.2.13676358` 的 `aarch64-linux-android26-clang` 构建 PIE executable，并作为生成 assets 打进 APK。APP 运行时：

```text
Compose / Kotlin
   -> ProcessBuilder("su", "-c", ...)
   -> copy helper to /data/local/tmp
   -> chmod 0700
   -> native helper status
   -> JSON
   -> Root Diagnostics UI / automation result
```

### 16.3 Samsung 实机 Native helper 结果

在 rooted Samsung S22 Ultra 上实际运行 native helper 得到：

```json
{
  "bridgeVersion": "1",
  "uid": 0,
  "euid": 0,
  "gid": 0,
  "selinuxContext": "u:r:magisk:s0",
  "selinuxEnforcing": true,
  "nfcInitialized": "true",
  "nfcFirmware": "NXP 1.1.32",
  "nfcPort": "I2C",
  "nfcInterrupt": "... pn547 ..."
}
```

同时白名单路径 probe 显示：

- `/sys/fs/selinux/enforce`：在当前 Root domain 下可访问；
- `/proc/interrupts`：可访问；
- `/sys/class/nfc`：存在；
- `/vendor/etc/libnfc-nxp.conf`：可读、不可写。

注意：`/sys/fs/selinux/enforce` 显示可写，仅描述当前 domain 的访问面。项目不会通过该接口把系统切成 Permissive；关闭 SELinux 并不能给 NFC controller 增加不存在的 card-emulation protocol。

### 16.4 Native Bridge 的边界设计

当前 helper 只开放：

- `status`
- `version`

不接受任意 shell、任意文件路径、任意 ioctl 或任意 raw device write。这样能把 Root 能力收敛成一个可审计的 NFC 系统研究接口，而不是把 APP 变成通用权限绕过工具。

后续若遇到确切 AVC denial，可按以下流程扩展：

1. 记录具体 source domain / target type / class / permission；
2. 判断是否属于 NFC 诊断或自有测试协议必需能力；
3. 优先通过现有 Samsung/NXP Binder/HAL API 访问；
4. 只有在确有必要时再增加一个明确的 native bridge command；
5. 每个 command 固定参数和目标资源，不开放任意透传。

### 16.5 与 MIFARE Classic 模拟的关系

Native/root bridge 能解决的是“APP 无法直接调用某个系统/NFC 底层接口”的权限和进程边界问题，但不能解决“控制器固件没有实现目标协议”的问题。

当前 Samsung/NXP 配置已经确认：

```text
MIFARE_READER_ENABLE=1
HOST_LISTEN_TECH_MASK=0x07
```

其中 Host Listen 的文档注释明确对应：

- ISO-DEP Tech A；
- ISO-DEP Tech B；
- NFC-F / T3T。

未发现 Host-side MIFARE Classic / Crypto1 listen-mode 实现。Samsung `com.samsung.android.nfc.t4t.jar` 也只实现 Type-4 NDEF read/write/lock/clear。

因此 native helper 可以继续深入 NCI/vendor 能力探测，但 **Root + native binary 不会自动让 S22 Ultra 变成 MIFARE Classic 1K PICC**。要做到协议等价模拟，仍需要 NFCC/firmware 本身存在对应 listen/card-emulation protocol，或使用专门的外部 NFC/RFID 硬件研究平台。

## 15. Root Samsung S22 Ultra 的 MIFARE Classic 模拟能力复测（2026-08-15）

Samsung S22 Ultra (`SM-S908E`, `b0q`) 完成 Root 后，重新对同一张门禁卡和 Samsung/NXP NFC 栈做实机检查，目标是回答：Root 是否能让该手机完整模拟已识别的 MIFARE Classic 1K 卡。

### 15.1 Root 与 NFC 基线

实测：

```text
Android: 14 / API 34
su -c id -> uid=0(root), context=u:r:magisk:s0
SELinux: Enforcing
Verified Boot: orange
Bootloader: unlocked
NFC: on
NXP firmware: 1.1.32
```

设备声明：

- `android.hardware.nfc`
- `android.hardware.nfc.hce`
- `android.hardware.nfc.hcef`
- `android.hardware.nfc.ese`
- `android.hardware.nfc.uicc`
- `com.nxp.mifare`
- `com.samsung.android.nfc.t4temul`

### 15.2 同一卡重新读取：成功

重新安装当前 NFC Lab，并用 ADB 自动 reader session 读取当前贴在 Samsung 背后的实体卡，结果：

```text
UID: 107662CC
Tech: NfcA / MifareClassic / NdefFormatable
ATQA: 0400
SAK: 0x08
MIFARE Classic: 1K
16 sectors / 64 blocks / 1024 bytes
```

与 Root 前识别结果一致，因此 Root 没有改变实体卡的协议类型。

### 15.3 Root 后自定义 HCE：成功

NFC Lab 新增 `automation_mode=hce`，可无 UI 切换到测试 HCE。

实测状态：

```json
{
  "status": "hce_ready",
  "supportsHce": true,
  "aid": "F001020304050607"
}
```

系统同时显示：

```text
mEnableReader=false
mEnableHostRouting=true
```

`dumpsys nfc` 中确认：

```text
ComponentInfo{com.arthur.nfclab/...LabHostApduService}
AID: F001020304050607
*DEFAULT* ...LabHostApduService
```

因此 Samsung 的普通 ISO-DEP/APDU Host Card Emulation 链路本身工作正常。

### 15.4 Samsung T4T 隐藏扩展

系统存在：

```text
/system/framework/com.samsung.android.nfc.t4t.jar
feature: com.samsung.android.nfc.t4temul
```

反编译隐藏框架后，`T4tAdapter` 只暴露：

- `writeT4tData(fileId, data)`
- `readT4tData(fileId)`
- `lockT4tNdefData()`
- `isLockedT4tNdefData()`
- `clearT4tNdefData()`
- `hasT4tCeFeature()`

并使用 Type-4 NDEF File ID `E104`。这是一条 **Type 4 / NDEF card-emulation** 能力，不是 MIFARE Classic 仿真接口。

### 15.5 NXP vendor 配置：MIFARE 是 Reader，Host Listen 不包含 Classic

Samsung 当前 `/vendor/etc/libnfc-nxp.conf` 的关键配置：

```text
MIFARE_READER_ENABLE=0x01
LEGACY_MIFARE_READER=0
```

说明 NXP MIFARE 支持首先是 reader 能力。

Proprietary protocol map 中存在：

```text
byte[5] NCI_PROTOCOL_MIFARE
NFA_PROPRIETARY_CFG={05, FF, FF, 06, 81, 80, 70, FF, FF}
```

但 Host Listen 配置明确只有：

```text
0x01 = ISO-DEP Tech A
0x02 = ISO-DEP Tech B
0x04 = T3T / NFC-F
HOST_LISTEN_TECH_MASK=0x07
```

没有把 MIFARE Classic protocol 作为 Host Listen/Card Emulation 协议暴露给 Android 主机。

配置里的：

```text
DEFAULT_MIFARE_CLT_ROUTE=0x01
```

属于 off-host / Secure Element technology routing 语义，不能解释为“普通或 Root Android App 可以任意实现 MIFARE Classic PICC”。

### 15.6 最终判断

对当前这张 `UID=107662CC` 的 MIFARE Classic 1K 卡：

| 能力 | Root Samsung S22 Ultra |
|---|---|
| 读取 UID / ATQA / SAK / 卡型 | ✅ 已实测 |
| 普通 ISO-DEP HCE | ✅ 已实测 |
| Samsung Type-4 NDEF emulation | ✅ 系统扩展存在 |
| Host 侧 MIFARE Classic protocol listen | ❌ 当前 vendor 配置未暴露 |
| 用 `HostApduService` 等价模拟该 MIFARE Classic 1K | ❌ 协议层不匹配 |
| Root 后自动获得完整 MIFARE Classic card emulation | ❌ 实测/配置均不支持该结论 |

因此 Root 对 Samsung 的价值主要是：

- 更深入查看/调试 NXP NCI、routing、vendor config；
- 访问隐藏 Samsung/NXP 系统能力；
- 研究 eSE/off-host 技术路由。

但它**不会把 HostApduService 或 Samsung T4T 变成 MIFARE Classic 1K 仿真器**。若要研究 MIFARE Classic 的完整 PICC 行为，需要专门支持该协议的安全元件/硬件或实验卡平台，而不是仅靠 Android Root。

## 16. MIFARE Classic 的 Secure Element 路线

### 16.1 Host HCE 不是唯一可能路线

对当前 Samsung S22 Ultra 的本机实测仍然成立：Android Host HCE / `HostApduService` 的 host listen 路径是 ISO-DEP A/B 与 NFC-F/T3T，不存在 MIFARE Classic Host Listen。Samsung 的隐藏 `t4t` 框架同样是 Type 4 NDEF，不是 Classic。

但 NXP 官方存在 **MIFARE4Mobile (M4M)** 体系。其 Secure Element Platform API / OTA / TSM 模型可以在合适的 eSE/UICC 上管理 MIFARE 产品资源；NXP 的认证列表中包含支持 MIFARE Classic 1K/4K 的 embedded Secure Element 产品。

因此能力矩阵应修正为：

```text
Android Host HCE
  └─ MIFARE Classic full emulation: 当前 Samsung 栈未发现支持

Secure Element / Off-host
  └─ MIFARE4Mobile-capable SE: 行业/产品层面存在可行实现
       ├─ 需要相容的 SE 产品/固件
       ├─ 需要 M4M / Java Card 组件
       ├─ 需要 OEM / TSM / issuer provisioning 权限
       └─ 不能由普通 Root App 自动假定可部署
```

### 16.2 当前 Samsung 的已知证据

Samsung/NXP vendor 配置：

- `MIFARE_READER_ENABLE=0x01`：MIFARE Reader 支持；
- `DEFAULT_MIFARE_CLT_ROUTE`：off-host technology routing 配置；
- eSE / UICC 均由 NFC service 暴露；
- Host-side Classic emulation API 未发现；
- Samsung JNI 存在 `NativeExtFieldDetect::startCardEmulation()`，但现有符号/配置只能证明存在 card-emulation 辅助入口，不能证明它实现 MIFARE Classic CRYPTO1 PICC。

### 16.3 下一阶段的安全验证方式

后续研究必须把“协议能力”与“真实门禁凭证”隔离：

1. 只读识别 Samsung eSE 产品和已安装服务/框架；
2. 检查设备是否存在 MIFARE4Mobile / MIFARE Java Card 运行组件或 OEM provisioning 通路；
3. 若存在，只使用合成测试 UID、测试 sector 数据和测试密钥验证 Classic PICC 能力；
4. 不向该测试路径导入真实门禁卡的密钥、受保护 sector 或认证状态。

这可以回答“这台手机能否从 Secure Element 层模拟 MIFARE Classic”这个技术问题，同时避免把研究工具变成真实门禁凭证克隆工具。

## 17. iPhone 真实 RF 对端：NFC Probe（2026-08-15）

为避免只能从 Samsung 自身 `dumpsys nfc` / HCE routing 推断“模拟是否成功”，工程新增独立 iOS 真机验证端：

```text
ios/NFCProbe
```

其目标不是实现 MIFARE Classic Crypto1，而是把 iPhone 作为外部 13.56 MHz Reader，验证 Samsung 是否真的在 RF 空口表现为期望的 NFC tag/card-emulation protocol。

### 17.1 Core NFC 验证链路

```text
Samsung S22 Ultra
  Root / NFC Lab / HCE or synthetic CE
            │
            │ 13.56 MHz RF
            ▼
iPhone Core NFC
  NFCTagReaderSession(.iso14443)
            │
            ├─ NFCMiFareTag
            │    ├─ identifier
            │    ├─ mifareFamily
            │    └─ historicalBytes
            │
            └─ NFCISO7816Tag
                 ├─ identifier
                 ├─ initialSelectedAID
                 ├─ historicalBytes
                 └─ APDU 80 CA 00 00 00
```

NFC Lab 的测试 AID 为：

```text
F001020304050607
```

iOS `Info.plist` 通过 `com.apple.developer.nfc.readersession.iso7816.select-identifiers` 只声明该自有测试 AID；检测到 ISO7816 tag 后，Probe 会连接并发送测试 `GET DATA` APDU，成功时记录 payload、SW1/SW2 和完整 JSON。

### 17.2 当前实现

- SwiftUI 单页 Probe UI；
- ISO14443 扫描；
- MIFARE-compatible tag family / identifier 观测；
- ISO7816 AID/APDU 自动验证；
- 最近结果 JSON 复制；
- XcodeGen 工程描述；
- `scripts/ios-probe.sh` 自动环境检查、构建、安装、启动。

模拟器纯编译已在 Xcode 26.2 / iOS 26.2 SDK 下实测：

```text
./scripts/ios-probe.sh build-sim
BUILD SUCCEEDED
```

Core NFC 本身只能在 iPhone 真机验证。

### 17.3 当前真机安装前置状态

Mac 钥匙串存在 Apple Development / Distribution identities，并缓存了历史 provisioning profiles；但是当前 Xcode Account 列表为空，而且已有 wildcard Development profile 没有 NFC Tag Reading entitlement。因此不能用旧 wildcard profile 正确签名 NFC Probe。

另外，当前 CoreDevice 记录中的两台物理 iPhone 都是 `unavailable`，虽然 Developer Mode 已开启。

因此第一次安装前需要满足：

1. Xcode → Settings → Accounts 登录 Apple Developer Program 账号，让 automatic signing 能创建带 NFC capability 的 profile；
2. 解锁 iPhone，让 `devicectl` 状态变成 `connected`；
3. 执行：

```text
./scripts/ios-probe.sh doctor
./scripts/ios-probe.sh install
```

安装脚本不会选择 Simulator 作为 NFC 真机目标。

### 17.4 验收边界

iPhone 能证明：

- Samsung 是否真正产生 ISO14443 RF card response；
- iPhone 识别到的 tag family / identifier；
- ISO7816 AID 是否可 SELECT；
- NFC Lab 自有 APDU 是否在真实 RF 链路上成功。

Core NFC 不提供 MIFARE Classic Crypto1 sector authentication，所以最终 Classic/Crypto1 等价行为仍需要专用、授权的测试读卡环境；iOS Probe 不执行真实门禁凭证认证或克隆。

## 18. Xiaomi 14 官方实体门卡路径复核（2026-08-24）

在一台已 Root 的 Xiaomi 14 (`houji`) 上，对“小米钱包能够正常模拟实体门卡”这一事实做了系统层复核。为避免把用户的真实凭证写入工程，本节只记录协议与路由结构，不保存卡片名称、VC UID、CID 或受保护扇区内容。

### 18.1 实机基线

- Android 15 / API 35；
- Magisk `su` 可获得 UID 0，SELinux 仍为 Enforcing；
- 系统声明 NFC / HCE / HCE-F / eSE / UICC / `com.nxp.mifare`；
- 小米钱包/TSM 包为 `com.miui.tsmclient`；
- `dumpsys secure_element` 显示 `eSE1` 已连接；
- NFC service 的默认 route 为 secure element，小米钱包对应 `ESEWalletDummyService` 为 off-host eSE service。

### 18.2 NXP vendor routing 证据

当前 `/odm/etc/libnfc-nxp.conf` 的关键配置包括：

```text
MIFARE_READER_ENABLE=0x01
LEGACY_MIFARE_READER=0
DEFAULT_AID_ROUTE=0x01
DEFAULT_ISODEP_ROUTE=0x01
DEFAULT_MIFARE_CLT_ROUTE=0x01
DEFAULT_MIFARE_CLT_PWR_STATE=0x3B
HOST_LISTEN_TECH_MASK=0x07
```

配置注释把 `0x01` 定义为 eSE route；因此 `DEFAULT_MIFARE_CLT_ROUTE=0x01` 是这次判断的关键证据：MIFARE card technology 的默认 card-emulation 路由被送往 eSE，而不是 Android host process。

同时，`HOST_LISTEN_TECH_MASK=0x07` 仍对应 Android host 侧的 ISO-DEP A/B 与 NFC-F/T3T 能力。这两项并不冲突，因为它们描述的是两条不同的数据路径：

```text
Android Host HCE
  -> HostApduService / ISO-DEP APDU

Xiaomi official physical-door-card emulation
  -> NFCC technology routing
  -> eSE / off-host applet
  -> MIFARE-compatible card behavior
```

### 18.3 小米钱包数据与 NFC service 的交叉证据

对 `com.miui.tsmclient` 的只读分析确认：

- 官方实体门卡对象使用 `MIFARE_ENTRANCE` 类型；
- 当前设备存在 `mifare_card_type = NORMAL` 的门卡记录；
- 门卡元数据包含独立的 VC UID / CID，但这些值属于凭证标识，本工程不记录；
- `is_sector_overwritten` 字段存在，说明官方流程显式跟踪 sector 数据是否已经写入虚拟卡；
- `dumpsys nfc` 同时报告已 provision 的卡为 `TYPE:M1`，并区分 `ACTIVATED` / `DEACTIVATED` 状态。

这比“系统支持 `com.nxp.mifare` feature”强得多：它证明当前设备已经存在真实可工作的 **M1 off-host virtual card provisioning**。

### 18.4 官方 APK 的协议流程证据

对当前小米钱包 APK 的只读反编译显示，它的 MIFARE 门卡流程包含：

- 从实体 `Tag` 读取 UID / ATQA / SAK / 卡容量；
- 使用 Android `MifareClassic` 识别 sector 可访问性；
- 将卡片参数组织成 `CopyMifareCardRequest` / `MifareCardParam`；
- 通过 TSM `DOOR_CARD_ENROLL` 流程向安全元件 provision 虚拟卡；
- 对加密 sector 存在单独的验证与数据准备流程。

这说明“小米官方 NFC 可以模拟 M1 门卡”的关键不是突破 `HostApduService`，而是 **OEM 拥有 TSM + eSE applet + NFCC proprietary technology routing 的完整发行链路**。

继续追踪官方卡片切换代码还能看到另一个重要边界：钱包的 `activateCard` / `deactivateCard` 最终由 Secure Element terminal 打开 CRS（Card Resource Service）并执行卡状态切换，而不是启动一个普通 Android HCE service。也就是说，截图中“公司/小区”之间的激活状态，本质上是在 **eSE 内已 provision applet 之间切换 Card Emulation 状态**；手机上层 UI 只是发起管理动作。

### 18.5 对 Root 能力边界的修正

因此此前“Root 不能让 Host HCE 变成 MIFARE Classic PICC”的结论仍然成立，但需要补充：

1. **Host HCE 路线**：Root 并不会自动给 `HostApduService` 增加 MIFARE Classic/Crypto1 PICC 协议。
2. **OEM off-host 路线**：本机已经证明小米官方可以借助 eSE 实现 M1 虚拟门卡。
3. **Root 的增益**：可以观察 vendor routing、eSE/NFCEE 状态、官方 TSM 的数据模型与卡状态，帮助理解和验证系统；它并不自动授予第三方应用 OEM provisioning、TSM 服务端授权或 eSE applet 管理权限。

### 18.6 工程落地

`RootNfcDiagnostics` 新增小米 off-host MIFARE 只读探针，采集：

- 小米钱包包/版本存在性；
- eSE1 terminal 是否在线；
- NXP MIFARE / AID / ISO-DEP route；
- Host Listen mask；
- `dumpsys nfc` 已 provision 卡的 AID、状态与 `TYPE:M1`。

输出会对 `UID` / `CID` / VC credential identifiers 做脱敏，不读取数据库中的真实卡名，不导出密钥、受保护 sector 或 eSE 内容。

