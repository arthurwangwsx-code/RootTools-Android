# Environment Integrity Center — 环境完整性与设备可信诊断

## 1. 文档目的

本方案基于 2026-08-20 对 **Hunter 6.65 (`com.zhenxi.hunter`)** 的公开资料调研、Samsung SM-S908E 真机 APK 静态分析，以及 RootTools 当前工程结构，规划一个适合本项目的 **Environment Integrity Center / 环境完整性中心**。

目标不是复制 Hunter，也不是把 Root 设备简单判定成“风险设备”，而是回答四个更适合 RootTools 的问题：

1. 当前设备有哪些 **预期的 Root / 调试 / 自动化改动**？
2. RootTools 自己或当前 Android 运行环境是否出现 **非预期 Hook、注入、篡改或沙箱异常**？
3. Java / Framework / Root / Native / Attestation 多个视角观察到的设备信息是否一致？
4. 与用户确认过的“稳定基线”相比，本次启动后到底 **漂移了什么**？

核心产品定义：

> **Environment Integrity Center = 可解释的环境信号采集 + 多来源交叉验证 + 用户预期基线 + 差异诊断。**

它不应成为“Root = 红色”的风控页面。

---

## 2. 调研结论先行

### 2.1 Hunter 6.65 不是一个适合直接复制源码的开源基线

本次没有找到 Hunter 6.65 对应的官方公开源码仓库。

可以找到的是：

- 第三方 GitHub 检测工具集合中的历史 Hunter APK；
- 看雪等安全社区中的 Hunter 检测思路文章；
- 当前 Samsung 设备上已安装的 Hunter 6.65 APK；
- APK 自身携带的检测、课程和运行时资源。

因此本项目采用以下边界：

```text
可以借鉴：
检测领域划分 / 风险信号类型 / 多视角交叉验证 / UI 信息组织

不直接复用：
Hunter 反编译代码 / libhunter.so 实现 / 私有规则数据 / 加密资产
```

RootTools 应采用 clean-room 方式独立实现。

### 2.2 Hunter 的核心不是“查有没有 Magisk”

Hunter 6.65 已经明显超过基础 Root Detector，实际形成了多层环境检测体系：

```text
App self integrity
    +
Java / Native device fingerprint
    +
Root / Magisk / Zygisk / Xposed runtime
    +
Process memory / maps / linker integrity
    +
Sandbox / clone / emulator / cloud phone
    +
Boot / ROM / SELinux / attestation
    +
Risk package / automation environment
    +
Sensor / memory / user-action runtime signals
```

这也是截图中能直接看到 `DeviceBaseInfo`、`JavaDeviceFingerprint`，而不是只显示一个 Root 状态的原因。

### 2.3 RootTools 必须采用不同的风险模型

Hunter 面向“这个环境是否像正常消费设备”判断，因此 Root、解锁、Hook、自动化工具都可能被当成风险信号。

RootTools 的目标设备本身就是：

- 已 Root；
- Bootloader 可能已解锁；
- 可能存在 Magisk / KernelSU / APatch；
- 可能使用 Zygisk / LSPosed；
- 使用 Shizuku / Sui；
- 常开 ADB / Tailscale；
- 运行 Appium / uiautomator2 / scrcpy / 自动化工具。

如果直接复制传统风控模型，RootTools 自己会长期显示“高风险”，信息价值接近于零。

因此本项目必须把信号拆成：

```text
Expected Modification
用户明确知道并允许的修改

Unexpected Integrity Violation
不应发生的 Hook / 篡改 / 注入 / 二进制变化 / 基线漂移

Context Signal
VPN / ADB / 自动化 / Developer Options 等上下文信号

Unavailable
当前 Android / OEM / 权限下无法可靠判断
```

---

## 3. 本次 Hunter 6.65 真机分析证据

### 3.1 Samsung 设备上的实际包信息

通过当前已连接 Samsung SM-S908E 的 ADB 检查确认：

```text
package: com.zhenxi.hunter
versionName: 6.65
versionCode: 665
targetSdk: 36
minSdk: 28
ABI: arm64-v8a
```

APK 内包含一个体积较大的：

```text
lib/arm64-v8a/libhunter.so
```

说明核心检测中存在较重的 Native 层实现，而不是只有 Java / Kotlin API 检查。

### 3.2 APK 暴露出的主要检测能力

从 Manifest、资源、JNI 方法名和 Native 字符串可以确认以下能力域。

| 能力域 | 本次证据 | 可信度 |
|---|---|---:|
| APK 签名 / 自身完整性 | APK sign、CRC、integrity manifest、native strings | 高 |
| Root / su | root file / permission / mount / AVC log 等检测 | 高 |
| Magisk / Zygisk | `checkZygisk`、Magisk 路径、Zygisk 字符串 | 高 |
| KernelSU / APatch 等现代 Root | Manifest 查询包 + Native 侧 KernelSU side-channel 字符串 | 高 |
| Xposed / LSPosed / EdXposed | Manifest 风险包、module path / LSP mark | 高 |
| Frida / 注入 | Frida thread / pipe / mark / injection 相关逻辑 | 高 |
| Debug / Tracer | ptrace / TracerPid / hardware breakpoint | 高 |
| Maps / Linker / executable memory | `/proc/self/maps`、map_files、linker CRC、exec mapping | 高 |
| Inline Hook / GOT / code integrity | Native runtime strings + 公共检测目录描述 | 高 |
| Sandbox / Clone | private path / storage path coherence probe | 高 |
| Emulator / virtual device | goldfish / vbox / simulator mount markers | 高 |
| Cloud phone | charging / cloud service / cloud phone 检查流程 | 中高 |
| Custom ROM / boot state | custom ROM、boot verify、unlock、ROM match | 高 |
| SELinux | Java/Native SELinux status | 高 |
| Device fingerprint | Java / Native / CPU / Vulkan / GPU / thermal / sensor 等 | 高 |
| Fake location | FakeLocation / mock-location checks | 高 |
| VPN / proxy | `CheckVpn`、proxy 等 | 高 |
| Android Key Attestation | StrongBox / device ID attestation / cert chain | 高 |
| Risk package inventory | Manifest 中大量 root/hook/automation/faker package query | 高 |
| Sensor live monitor | 独立 sensor monitor layout | 高 |
| Process memory monitor | system/process memory、PSS/RSS/FD/heap | 高 |
| Touch / input behavior | User Action Track、IME audit、touch path | 高 |

### 3.3 Hunter 不是一次同步扫描

APK 主流程可以看出它把检测拆为：

- 主线程初始化；
- 多个异步检测线程；
- 延迟 sensor 检测；
- 延迟 root native 检测；
- APK sign / CRC；
- maps / linker / native runtime；
- sandbox；
- cloud phone；
- ROM / device unlock / attestation；
- fake location / Frida；
- 最终聚合风险项。

这对 RootTools 的启发不是“也开很多线程”，而是：

> 深检测必须有 Probe 调度、timeout、阶段状态和单项失败隔离，不能让一个昂贵探针阻塞整个页面。

### 3.4 截图中的 DeviceFingerprint 是“组合指纹”

用户截图里可以直接看到 Java 指纹由多个子项组成，包括：

```text
vulkan_fp
gpu_render
gpu_caps
gl_pixel_fp
battery_fp
thermal_zones
cpu_topology
audio_fp
sensor_list
...
```

这说明 Hunter 的重要思路是：

> 不只相信 `Build.MODEL` 或单个系统属性，而是对多个硬件 / 驱动 / Framework 表面生成独立观测，再检查它们是否一致。

RootTools 值得借鉴这个思路，但不应该生成可用于跨应用追踪用户的稳定设备 ID。

---

## 4. 对 Hunter 能力的取舍

## 4.1 应该直接吸收的产品思想

### A. 多来源交叉验证

例如：

```text
Build.VERSION / Build.FINGERPRINT
        ↕
getprop / property area
        ↕
root filesystem / boot props
        ↕
native system property
```

只要观察面之间出现异常差异，就比“字符串里有没有 magisk”更有诊断价值。

### B. App 自身完整性优先

对于 RootTools 来说，“设备已 Root”是预期状态；但：

- RootTools APK 被二次打包；
- 签名被替换；
- DEX / SO 在运行时与文件不一致；
- 自身方法被 Hook；
- Native 映射异常；

都应该是高价值告警。

### C. Risk finding 必须保留证据

不只显示：

```text
发现 Hook
```

而要显示：

```text
Finding: runtime.maps.exec_anonymous
Status: WARN
Confidence: Medium
Source: Native / procfs
Observed: executable anonymous mapping detected
Expected: no unknown executable anonymous mapping
Evidence: 3 mappings, 1 unattributed
```

### D. 快扫 / 深扫分离

环境检测适合用户主动触发，不适合首页 1～2 秒轮询。

---

## 4.2 不应该复制的部分

### A. “Root = 黑灰产”

不适合本项目。

### B. 所有自动化工具都当恶意风险

RootTools 本身就是自动化测试基础设施，Appium / uiautomator2 / scrcpy / Tailscale / ADB 应允许配置为 `EXPECTED`。

### C. 默认收集稳定硬件指纹

本项目只需要：

- 本机诊断；
- baseline diff；
- 短期 report correlation。

不需要生成跨安装、跨应用可稳定识别用户的全局 fingerprint。

### D. 把昂贵检测塞入启动路径

RootTools 已经有明确低开销原则，因此完整扫描必须 on-demand。

### E. 为“对抗检测”而实现隐藏

Environment Integrity Center 的职责是检测和解释自身环境，不负责修改系统以逃避第三方 App 检测。

---

## 5. RootTools 当前工程现状

## 5.1 已经存在的可复用基础设施

当前工程并不是从零开始。

### Device / Health

已有：

- `DeviceRepository`
- `DeviceHealthCollector`
- `DeviceSamplerService`
- CPU / Memory / Thermal / Battery / Storage 数据模型

可以直接提供：

- CPU topology 的一部分；
- thermal zones 的一部分；
- battery / charging 状态；
- build / device base 信息。

### Diagnostics

已有：

- `DiagnosticsRepository`
- top process；
- root shell；
- WakeLock；
- running service；
- root shell attribution。

它适合作为进程 / root runtime 的数据来源之一，不应该复制一套第二个 `ps/top` 采集器。

### Root Runtime / Module

已有：

- `ModuleCenterRepository`
- Magisk / Vector 状态；
- module inventory 基础；
- RootShell。

未来扩展 KernelSU / APatch 时，Environment Integrity Center 只消费统一 `RootRuntimeSnapshot`，不要自己硬编码各 Root 管理器逻辑。

### Network

已有：

- `NetworkRepository`
- interfaces；
- route / DNS；
- listening ports；
- Tailscale 状态。

VPN / proxy / suspicious listener 检测应优先复用这里。

### Privilege Bridge

当前工作区正在推进：

- `ShizukuBridge`
- `PrivilegeRouter`
- `ShizukuUserServiceClient`
- typed framework operation。

Integrity scan 大部分是只读，可以使用：

```text
Android API
  ↓
Shizuku/Sui read capability（必要时）
  ↓
RootShell cross-check
```

而不是新增一个任意 shell executor。

### Report / audit / tests

已有：

- `DiagnosticReportStore`
- `RootActionAuditStore`
- pure policy / parser JVM test 规范
- Samsung 真机验收原则。

Integrity Center 应直接沿用。

---

## 5.2 当前结构上的限制

### A. 目前没有 Native / NDK 基础设施

当前 `app/build.gradle.kts` 只有 Kotlin / Compose / Shizuku 等依赖，没有 `externalNativeBuild` / CMake / NDK 模块。

因此 Hunter 中以下能力不应该作为第一阶段：

- inline hook 指令级检查；
- GOT / PLT integrity；
- linker internals；
- Native hardware breakpoint；
- Native property area consistency；
- ELF runtime/file section checksum。

这些属于 **P2 Native Integrity**。

### B. UI 文件已经过大

当前：

```text
DashboardScreen.kt     > 3300 lines
DashboardViewModel.kt   > 800 lines
```

Environment Integrity Center 不建议继续把所有 UI 和状态都塞进这两个文件。

但仍然遵循“单 app module”，只做 package-level 拆分：

```text
ui/integrity/
model/integrity...
data/integrity...
```

**不新增 Gradle module。**

### C. `ToolCapability` 只有强制能力，没有 optional capability 语义

Integrity Center 必须支持：

```text
无 Root：做普通 app / framework 自检
有 Shizuku ADB：增加 framework cross-check
有 Root：增加 proc/sys/fs 深检查
有 Native bridge：增加 runtime integrity
```

因此这个卡片不能简单声明 `requiredCapabilities = ROOT`。

建议：

```text
ToolDefinition.requiredCapabilities = emptySet()
```

详情页自己显示当前可用检测层级。

后续如果 ToolRegistry 需要表达 optional capability，再扩展：

```text
preferredCapabilities
```

而不是为了这个 Feature 把 Root 变成强制入口。

---

## 6. 产品形态

## 6.1 一级入口

建议新增：

```text
ToolId.INTEGRITY
标题：环境完整性
分类：ToolCategory.DIAGNOSTICS
```

不建议塞进现有 `DIAGNOSTICS / 进程诊断`。

原因：

- 现有 Diagnostics 是运行时性能 / process/service 诊断；
- Integrity 是安全信号 / 基线 / 多源一致性；
- 深扫生命周期、报告结构和风险模型都不同。

## 6.2 首页卡片

示例：

```text
环境完整性

EXPECTED ROOT · HOOK CLEAN
Boot unlocked · 1 drift

Last scan 12 min ago
```

首页只显示缓存结果，不启动深检测。

建议摘要字段：

- 最大非预期 finding 等级；
- expected modification 数；
- unexpected drift 数；
- 上次扫描时间。

## 6.3 详情页信息架构

建议 7 个 section，而不是几百条检查平铺：

```text
Overview
Runtime Integrity
Boot & OS
Root & Framework
Device Surface
Automation & Network
Attestation & Reports
```

### Overview

- Integrity Summary；
- Current profile；
- Last scan；
- Fast / Deep Scan；
- expected / warn / critical 聚合；
- 与上次 baseline 的 diff。

### Runtime Integrity

- App signing cert；
- installed APK split 信息；
- build-time manifest/hash；
- debugger / tracer；
- loaded native libraries；
- `/proc/self/maps` 摘要；
- suspicious executable mappings；
- P2 Native code / linker integrity。

### Boot & OS

- Android / API / Security Patch；
- build fingerprint；
- verified boot state；
- vbmeta state；
- bootloader lock；
- SELinux；
- system/vendor/product property consistency；
- custom ROM / OEM mismatch evidence。

### Root & Framework

- root availability；
- root runtime provider；
- Magisk / KernelSU / APatch；
- Zygisk；
- LSPosed / Xposed / Vector；
- Shizuku / Sui；
- root mounts / overlayfs；
- modules relevant to current app scope。

### Device Surface

- CPU topology；
- thermal zones；
- GPU renderer / Vulkan capabilities；
- sensor inventory；
- audio feature surface；
- battery / power supply descriptors；
- Java / Root / Native observation consistency。

### Automation & Network

- ADB；
- Developer Options；
- accessibility automation；
- uiautomator / Appium / scrcpy；
- overlay / notification listener 等 sensitive access；
- VPN / Tailscale；
- proxy；
- mock location；
- suspicious local ports / processes。

这些项默认是 **Context**，不能一刀切判成攻击。

### Attestation & Reports

- Android Keystore capability；
- TEE / StrongBox capability；
- Key Attestation result；
- Play Integrity（可选，后置）；
- export report；
- compare baseline。

---

## 7. Finding / Risk 数据模型

不要只保存一个 `riskLevel`。

建议模型：

```kotlin
enum class IntegrityCategory {
    APP_INTEGRITY,
    RUNTIME,
    BOOT_OS,
    ROOT_RUNTIME,
    VIRTUALIZATION,
    DEVICE_SURFACE,
    AUTOMATION,
    NETWORK_LOCATION,
    ATTESTATION,
}

enum class IntegrityDisposition {
    PASS,
    EXPECTED,
    INFO,
    WARN,
    CRITICAL,
    UNAVAILABLE,
}

enum class IntegrityConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

enum class IntegritySource {
    ANDROID_API,
    SHIZUKU,
    ROOT,
    PROCFS,
    SYSFS,
    NATIVE,
    ATTESTATION,
    BASELINE,
}

data class IntegrityFinding(
    val id: String,
    val category: IntegrityCategory,
    val disposition: IntegrityDisposition,
    val confidence: IntegrityConfidence,
    val sources: Set<IntegritySource>,
    val title: String,
    val summary: String,
    val observed: String? = null,
    val expected: String? = null,
    val evidence: List<String> = emptyList(),
    val remediationHint: String? = null,
)
```

### 为什么不建议只做 0～100 分

一个数字会隐藏：

- Root 是用户预期；
- Hook 是非预期；
- Tailscale 是正常；
- Attestation failure 在 unlocked bootloader 上可能是预期；
- 某个 OEM 不暴露节点只是 unavailable。

首页可以展示最大 severity，但详情必须保留 findings。

---

## 8. Expected Baseline — 这是 RootTools 与 Hunter 最大的差异

## 8.1 Baseline profile

建议允许用户保存：

```text
Samsung S908E · Daily Rooted
```

示例预期：

```text
Root                 EXPECTED
Bootloader unlocked  EXPECTED
Magisk               EXPECTED
Zygisk               EXPECTED
LSPosed installed    EXPECTED
Shizuku/Sui          EXPECTED
ADB enabled           EXPECTED
Tailscale VPN         EXPECTED
uiautomator2          EXPECTED on test profile
```

但以下默认仍不可自动变成 expected：

```text
RootTools signing certificate changed
RootTools APK content changed unexpectedly
RootTools runtime method/code changed
unknown executable memory mapping
unexpected injected native library
Java/root/native device identity cross-check mismatch
baseline root runtime provider changed unexpectedly
```

如果用户真的在调试 RootTools 自身，也必须显式创建 `Lab / Hooked` profile 才降级为 expected。

## 8.2 Drift 优先于绝对“干净”

对个人测试机更有价值的是：

```text
昨天正常
今天新增了一个 Zygisk module
今天 RootTools 进程出现未知 .so
今天 vbmeta property 与 baseline 不一致
今天 sensor / thermal topology 突然变化
```

所以详情页第一优先级应该显示：

```text
What changed since last trusted baseline?
```

而不是：

```text
Your device is rooted.
```

---

## 9. Probe 架构

## 9.1 Probe contract

所有检测项统一抽象：

```kotlin
interface IntegrityProbe {
    val id: String
    val mode: ScanMode
    val timeoutMs: Long

    suspend fun probe(context: IntegrityProbeContext): ProbeResult
}
```

其中：

```text
ScanMode.FAST
ScanMode.DEEP
ScanMode.NATIVE
ScanMode.REMOTE_ATTESTATION
```

### Probe 必须满足

1. 默认只读；
2. 独立 timeout；
3. 单项失败不终止整个 scan；
4. 输出结构化 evidence；
5. 不直接决定全局风险；
6. probe 和 risk policy 分离；
7. OEM 不支持时返回 `UNAVAILABLE`，不能返回 `WARN`。

## 9.2 数据流

```text
IntegrityScreen
      │
      ▼
IntegrityViewModel
      │
      ▼
IntegrityRepository
      │
      ├── FastProbeRunner
      ├── DeepProbeRunner
      ├── Existing Snapshot Adapters
      │     ├── DeviceRepository
      │     ├── DiagnosticsRepository
      │     ├── NetworkRepository
      │     ├── ModuleCenterRepository
      │     └── ShizukuBridge / PrivilegeRouter
      │
      ├── NativeIntegrityBridge      [P2]
      ├── AttestationVerifier        [P2]
      └── IntegrityBaselineStore
              │
              ▼
        IntegrityRiskEngine
              │
              ▼
        IntegritySnapshot
```

## 9.3 复用优先

禁止：

```text
IntegrityRepository 再执行一遍 top / dumpsys / network / thermal 全家桶
```

必须优先消费已有 snapshot，并只为缺失信号增加专用 probe。

---

## 10. P0 — Fast Integrity MVP

P0 先把 **不依赖 NDK、诊断价值高、可测试** 的能力做完整。

## 10.1 App self identity

检查：

- package name；
- versionCode / versionName；
- signing certificate SHA-256；
- installer source；
- base APK / split APK 路径；
- debug flag；
- debuggable；
- current process uid / selinux context；
- runtime package info 与 root shell `pm path / dumpsys package` 一致性。

### Build-time identity manifest

建议构建时生成：

```text
assets/roottools_integrity_manifest.json
```

只记录：

- app signing cert digest；
- version；
- selected packaged resource hashes；
- selected DEX / native library hashes（存在时）；
- schema version。

不要把 release keystore 或 secret 放入 APK。

## 10.2 Boot / verified state

通过 Android API + Root `getprop` 交叉检查：

- `ro.boot.verifiedbootstate`；
- `ro.boot.vbmeta.device_state`；
- `ro.boot.flash.locked`；
- `ro.build.type`；
- `ro.build.tags`；
- `ro.debuggable`；
- security patch；
- product / vendor / system fingerprint；
- SELinux enforcing/permissive。

对于 Rooted profile：

```text
unlocked != critical
```

它是 `EXPECTED` 或 `INFO`。

## 10.3 Root runtime inventory

先消费 Module / Root runtime 层：

- root available；
- provider：Magisk / KSU / APatch / unknown su；
- manager package；
- Zygisk；
- module count；
- LSPosed / Xposed / Vector 状态；
- known module scope 信息（可获得时）。

Root Runtime Center 完成后，这里只读统一 snapshot。

## 10.4 proc / runtime basic integrity

普通应用权限即可做一部分：

- `/proc/self/status`：TracerPid；
- `/proc/self/maps`：loaded maps；
- executable anonymous mapping count；
- deleted executable mapping；
- unexpected writable + executable mapping；
- known runtime library inventory。

P0 不直接声称“发现 Frida”。

规则应该是：

```text
known strong marker     -> high confidence
generic RWX mapping     -> medium/low confidence evidence
```

避免单个弱启发式导致红屏。

## 10.5 Environment context

读取但默认不判攻击：

- Developer Options；
- ADB；
- USB debugging / wireless debugging capability；
- VPN / Tailscale；
- global proxy；
- mock location configured；
- overlay permission；
- accessibility enabled services；
- notification listener；
- usage access。

这些信息与 Permissions / ADB / Network 页面互相链接。

## 10.6 Risk package taxonomy

Hunter 6.65 Manifest 明确查询大量：

- Root manager；
- Xposed / LSPosed；
- Hide My Applist / root hiding；
- VirtualApp / sandbox；
- Auto.js / clicker / uiautomator；
- scrcpy / TotalControl；
- device faker；
- SuperSU / one-click-root 等。

RootTools 不应硬编码“包存在 = 恶意”。

建议分类：

```text
ROOT_RUNTIME
HOOK_FRAMEWORK
VIRTUALIZATION
AUTOMATION
REMOTE_CONTROL
DEVICE_SPOOFING
ROOT_HIDING
UNKNOWN_HIGH_PRIVILEGE
```

并把 App Control Center inventory 作为包信息真值源。

---

## 11. P1 — Device Surface & Sandbox Consistency

## 11.1 设备表面指纹

目的不是生成唯一 ID，而是生成 **可比较的局部 hash**。

建议：

```text
cpu_topology_hash
thermal_zone_shape_hash
gpu_renderer_hash
gpu_capability_hash
vulkan_capability_hash
sensor_shape_hash
audio_capability_hash
battery_supply_shape_hash
```

只存 hash + human readable summary。

不要默认保存：

- IMEI；
- IMSI；
- serial；
- Wi-Fi MAC；
- Android ID 原值；
- 可跨设备追踪的隐私标识。

## 11.2 Java / Root cross-check

P1 即使还没有 Native，也可以做：

```text
Android API
vs
RootShell / procfs / sysfs / getprop
```

典型 finding：

```text
build.model.framework != ro.product.model
sensor count changed after reboot
thermal zone topology differs from baseline
GPU renderer reports emulator-like surface but OEM props say Samsung
```

任何单项都不要自动判 critical，交给 RiskEngine 做多信号聚合。

## 11.3 Sandbox / clone consistency

Hunter 6.65 有明显的多路径 private/external storage coherence probe。

RootTools 可以独立实现一个更克制的版本：

### Read-only first

比较：

- `applicationInfo.dataDir`；
- `deviceProtectedDataDir`；
- `/proc/self/mountinfo`；
- `/data/user/0/<pkg>`；
- `/data/user_de/0/<pkg>`；
- storage canonical path；
- inode / device id；
- symlink / bind mount 差异。

### Sentinel probe 可选

如果需要写入验证：

1. 只写 RootTools 自己的 cache / noBackup / files；
2. 生成随机 nonce；
3. 多路径读取并比较 inode/content；
4. finally 清理；
5. 不写其它 App 私有目录；
6. 不需要 Root 时不要使用 Root；
7. 结果进入 scan evidence，不进入 `RootActionAuditStore` 的系统修改历史。

它属于自检临时文件，不是系统 state mutation。

---

## 12. P2 — Native Integrity

只有 P0/P1 稳定后再引入 NDK。

## 12.1 为什么需要 Native

Java 层很难可靠回答：

- 自己的 JNI function 是否被 inline hook；
- GOT / PLT 是否重定向；
- linker / libc 关键实现是否被替换；
- runtime mapped ELF 与磁盘 ELF 是否一致；
- executable code page 是否被 patch；
- NativeBridge / injected `.so` 来源是否异常。

## 12.2 建议 Native 范围

第一版只做诊断，不做 anti-analysis 对抗：

```text
NativeIntegrityBridge
├── procMapsSummary()
├── loadedElfSummary()
├── selfElfRuntimeFileDiff()
├── executableMappingSummary()
├── tracerSummary()
└── hookSurfaceSummary()
```

不建议第一版做：

- 主动隐藏 maps；
- 修改 linker；
- anti-ptrace 对抗；
- Dobby 自 Hook；
- 对抗 Frida 的破坏性措施。

RootTools 是诊断工具，不是加固壳。

## 12.3 Native finding 原则

必须区分：

```text
Observed anomaly
```

与：

```text
Attribution to Frida/LSPosed/Magisk
```

除非存在强 marker，否则不能把“未知映射”直接命名成某具体工具。

---

## 13. P2 — Attestation

## 13.1 Android Key Attestation

可以提供：

- hardware-backed Keystore capability；
- TEE / StrongBox；
- attestation certificate chain；
- verified boot fields；
- security level。

但正确的信任验证应在 **另一台受信任设备 / server** 完成，而不是在被检测的同一台手机上自证。

因此 RootTools 第一阶段只做：

```text
Local capability + raw parsed summary
```

后续可增加：

```text
Mac / trusted verifier
```

### 13.1.1 2026-08-20 KeyAttestation 专项落地范围

本轮根据 `vvb2060/KeyAttestation` 1.8.4 与 Android 官方 `android/keyattestation`
重新校准优先级：Attestation 不再等待整个 Native Integrity 完成后才开始，而是以**独立、只读、按需触发的
Integrity 子能力**先落地。这样不会把 NDK 或 Hunter 风格深检测带进当前工程，同时可以立即给 Samsung
Root 测试机提供硬件信任视角。

本轮实现边界：

```text
Environment Integrity
└── Hardware Attestation
    ├── Standard Android Key Attestation
    ├── StrongBox Attestation（设备支持时）
    ├── Certificate chain signature / validity verification
    ├── Google / Google RKP / Knox / AOSP / OEM / Unknown root classification
    ├── current Google trust-anchor compatibility（含 2026 新根）
    ├── Google attestation revocation status（按需联网，失败不阻断本地结果）
    ├── RootOfTrust
    │   ├── deviceLocked
    │   ├── verifiedBootState
    │   ├── verifiedBootKey digest
    │   └── verifiedBootHash digest
    ├── OS / Vendor / Boot patch level
    ├── RKP provisioning-info extension detection
    ├── root property / attested state cross-check
    └── PEM certificate-chain export
```

明确不进入本轮：

- KeyBox 导入；
- 修改 / 伪造 attestation；
- 修改 RKP server hostname；
- 设备 ID / IMEI / Serial attestation；
- Play Integrity token / verdict；
- 在 RootTools 内建立“本机自证 = 远端可信”的错误安全结论。

原因：前三项属于高风险或对抗性能力；设备 ID attestation 需要更高系统权限且会扩大隐私面；Play Integrity
需要 Google Play / Cloud / backend 产品条件。RootTools 本轮目标是**诊断与证据导出**。

实现仍保持单 `app` Gradle module，通过 package 分层：

```text
model/IntegrityModels.kt
data/AttestationParser.kt
data/AttestationChainVerifier.kt
data/GoogleAttestationStatusClient.kt
data/DeviceIntegrityRepository.kt
ui/integrity/DeviceIntegrityScreen.kt
```

核心安全规则：

1. Attestation 只在用户进入页面 / 点击刷新时运行，不进入 Dashboard sampler、Service 或 boot receiver；
2. 每次生成临时 Keystore key，读取证书链后立即删除 alias；
3. StrongBox 不支持属于 `UNAVAILABLE/INFO`，不是风险；
4. Root / Bootloader unlocked 在 Root 测试机默认属于 `EXPECTED`，不能直接升级为 critical；
5. 只有 challenge mismatch、证书链签名失败、官方 revocation 命中、强信号互相矛盾等才提高告警等级；
6. 在线根 / revocation 获取失败时保留完整本地结果，并明确显示 `online verification unavailable`；
7. 证书导出只包含公开证书，不导出任何 Android Keystore 私钥；
8. 本机解析结果是诊断信息；真正的信任决策仍推荐把导出的链交给 Mac / server verifier。

## 13.2 Play Integrity

Play Integrity 更适合：

- 已通过 Google Play 分发的 app；
- 有 Cloud project / backend；
- 需要 Google 的 app/device/account verdict。

RootTools 当前是个人 sideload / root 工具，因此不应该让 P0 依赖 Play Integrity。

后续如果上架或有 backend，可以作为：

```text
External Trust Verdict
```

而不是本地 risk engine 的唯一真值源。

Rooted / unlocked 设备得到 degraded verdict 在本产品里可能只是 expected context。

---

## 14. Risk Engine

## 14.1 不允许 Probe 自己决定最终红黄绿

流程：

```text
ProbeResult
   ↓
SignalNormalizer
   ↓
BaselineMatcher
   ↓
IntegrityRiskEngine
   ↓
IntegrityFinding
```

## 14.2 建议规则

### CRITICAL

只给强证据，例如：

- RootTools signing cert 与 build manifest 不一致；
- RootTools packaged code/hash 明确不一致且不是当前合法版本；
- Native runtime/file code mismatch 有强证据；
- 明确发现非预期 injected library / code patch；
- 多源强一致性验证表明运行环境被替换。

### WARN

例如：

- unknown executable mapping；
- TracerPid 非 0；
- root provider / module inventory 相比 trusted baseline 突变；
- build/property cross-check mismatch；
- sandbox path incoherence；
- 未知 high-privilege automation service。

### EXPECTED

例如：

- Magisk installed；
- Bootloader unlocked；
- LSPosed installed；
- Shizuku root；
- Tailscale VPN；
- ADB enabled；

前提是当前 profile 已明确允许。

### INFO

例如：

- Developer Options enabled；
- StrongBox unavailable on unsupported device；
-某 probe 只能得到低置信度结果。

### UNAVAILABLE

例如：

- OEM 未暴露节点；
- Android 版本不支持；
- Shizuku permission denied；
- no Root；
- Key Attestation unsupported。

**UNAVAILABLE 不等于 RISK。**

---

## 15. Scan Mode 与性能预算

## 15.1 Fast Scan

目标：用户打开页面后快速得到基本结果。

包含：

- self package/signing；
- build / boot properties；
- SELinux；
- root runtime snapshot；
- TracerPid；
- basic maps；
- ADB / VPN / proxy / automation context；
- baseline diff。

目标耗时：

```text
P50 < 800 ms
P95 < 2 s
```

这是设计预算，不是先写死 SLA。

## 15.2 Deep Scan

包含：

- full proc/maps analysis；
- root mount cross-check；
- package taxonomy；
- device surface hash；
- sandbox coherence；
- deeper ROM / runtime checks；
- optional short sensor sampling。

目标：

```text
常规 3～8 s
单 probe timeout 1～3 s
总 timeout 15 s
```

## 15.3 Native / Attestation

必须用户主动触发，独立显示进度。

首页、后台 sampler、Quick Tile 都禁止定时跑 full integrity scan。

---

## 16. UI 设计

## 16.1 Summary header

不要照搬 Hunter 的整屏红色。

建议：

```text
Environment Integrity

1 Warning
7 Expected Changes
23 Checks Passed

Profile: S908E · Daily Rooted
Last scan: 00:41

[Fast Scan] [Deep Scan]
```

只有真正 `CRITICAL` finding 才出现明显红色强调。

## 16.2 Finding card

每项显示：

```text
Runtime Maps
WARN · Medium confidence

Found 1 unattributed executable mapping.
Observed after reboot, not present in trusted baseline.

Sources: PROCFS + ROOT
[Evidence] [Compare] [Mark expected...]
```

`Mark expected` 对自签名/自代码完整性等关键项默认禁用。

## 16.3 Baseline diff

单独 section：

```text
Changed since baseline
+ LSPosed module: xxx
+ listening local port: 27183
~ thermal zone count: 41 -> 42
- package: old automation helper
```

这会比传统“风险列表”更适合用户自己的长期 Root 测试机。

---

## 17. Report 与隐私

## 17.1 Human-readable report

复用 `DiagnosticReportStore`，新增：

```text
RootTools Integrity Report
Device summary
Scan capabilities
Expected changes
Unexpected findings
Baseline diff
Probe unavailable list
Evidence summary
```

## 17.2 JSON report

未来给 Agent / MCP / ADB 自动化消费：

```json
{
  "schemaVersion": 1,
  "profile": "s908e-daily-rooted",
  "maxDisposition": "WARN",
  "findings": [],
  "capabilities": {},
  "baseline": {}
}
```

## 17.3 默认脱敏

默认不导出：

- IMEI / IMSI；
- phone number；
- serial；
- MAC 原值；
- Android ID 原值；
-完整 attestation certificate；
- clipboard content；
-用户输入内容。

Hunter 有 clipboard / input / touch 等运行时观察能力，但 RootTools 没有必要默认采集这些敏感数据。

---

## 18. Automation / ADB API

等 UI 和 repository 稳定后，可以增加 typed action：

```text
INTEGRITY_FAST_SCAN
INTEGRITY_DEEP_SCAN
INTEGRITY_EXPORT_LAST_REPORT
```

不接受：

```text
custom shell
custom probe command
arbitrary path
```

返回：

```text
scanId
status
maxDisposition
expectedCount
warnCount
criticalCount
reportPath/token
```

这符合现有 `ActionRouterReceiver` 的 typed semantic action 原则。

---

## 19. 代码落点建议

继续单 app module：

```text
app/src/main/java/com/arthur/roottools/
├── model/
│   └── IntegrityModels.kt
├── data/
│   ├── IntegrityRepository.kt
│   ├── IntegrityBaselineStore.kt
│   └── integrity/
│       ├── AppIdentityProbe.kt
│       ├── BootIntegrityProbe.kt
│       ├── ProcRuntimeProbe.kt
│       ├── RootRuntimeProbe.kt
│       ├── EnvironmentContextProbe.kt
│       ├── DeviceSurfaceProbe.kt
│       └── SandboxCoherenceProbe.kt
├── policy/
│   ├── IntegrityRiskEngine.kt
│   └── IntegrityBaselineMatcher.kt
├── nativeintegrity/                # P2，仍属于 app module
│   └── NativeIntegrityBridge.kt
└── ui/integrity/
    ├── IntegrityScreen.kt
    └── IntegrityUiModels.kt
```

原则：

- 不建 `:integrity` Gradle module；
- 不在 `DashboardScreen.kt` 再堆几百行；
- Controller 仅用于 remediation 写动作，scan 自身走 Repository/Probe；
- 所有 policy 为 Android-free pure Kotlin。

---

## 20. 单元测试规划

必须遵循 `14-core-logic-testing-standard.md`。

## 20.1 Risk Engine

新增：

```text
IntegrityRiskEngineTest
```

覆盖：

1. Root detected + baseline expected -> EXPECTED；
2. Root detected + clean profile -> WARN；
3. signing mismatch -> CRITICAL；
4. unknown RWX only -> WARN/low confidence，而不是 CRITICAL；
5. unavailable attestation -> UNAVAILABLE；
6. Tailscale + expected profile -> EXPECTED；
7.多个弱信号不会错误升级为强 attribution；
8. strong runtime code mismatch -> CRITICAL。

## 20.2 Baseline Matcher

新增：

```text
IntegrityBaselineMatcherTest
```

覆盖：

- exact match；
- added / removed signal；
- expected provider version change；
- critical invariant cannot auto-allow；
- schema migration；
- corrupt baseline fallback。

## 20.3 Parser / normalizer

至少包括：

```text
BootPropertyParserTest
ProcMapsParserTest
IntegritySignalNormalizerTest
```

使用固定 fixture，不依赖真机才能跑核心 policy tests。

## 20.4 Native test

P2 Native 需要：

- JVM 只测 Kotlin adapter；
- instrumentation / Samsung 真机测 JNI；
- synthetic fixture 或 test-only library 制造可控 mapping；
- 不通过真实恶意注入来作为唯一测试方式。

---

## 21. Samsung SM-S908E 验收矩阵

当前截图设备本身就是最重要的首个验收目标。

### Profile A — Daily Rooted

预期：

- Root -> EXPECTED；
- unlocked boot -> EXPECTED；
- Magisk/Zygisk -> EXPECTED（若当前实际使用）；
- Shizuku/Sui -> EXPECTED；
- Tailscale/ADB -> EXPECTED；
- RootTools signing/self-integrity -> PASS；
- no unexplained runtime injection -> PASS。

### Profile B — Rooted + LSPosed scope off

验证：

- LSPosed installed 可被识别；
- RootTools 未被 scope 时，不应误报自身 hooked；
- framework presence 与 runtime injection 分开。

### Profile C — Controlled hook lab

只在实验 profile 下，通过自建 test hook / instrumentation 制造一个可控 finding：

- 检测到 runtime change；
- finding 有证据；
-默认 profile 为 WARN/CRITICAL；
- Lab profile 可显式 expected。

### Profile D — Emulator

验证：

- emulator signals；
- no physical-only API crash；
- unsupported hardware probe 返回 UNAVAILABLE。

### Profile E — Non-root / Shizuku ADB

验证：

-页面仍可打开；
-普通 API + Shizuku framework checks 正常；
-Root-only probe 显示 unavailable；
-不会整页显示“Root required”。

---

## 22. 与截图中“黑灰产设备”结果的关系

Hunter 截图当前显示：

```text
黑灰产设备, 当前程序已经被Hook&修改！
```

这个结论说明 **Hunter 自己的 risk engine 认为存在 Hook/修改证据**，但仅凭截图不能可靠断言具体是哪一个规则触发。

原因是 Hunter 同时检查：

- APK / CRC / signature；
- maps / linker / runtime memory；
- Zygisk / LSPosed / Frida；
- sandbox / ROM / root；
- Java / Native fingerprint；
- attestation 等。

RootTools 不能复刻这种“只有最终红屏、触发原因不透明”的体验。

正确目标应该是：

```text
Summary:
1 unexpected runtime integrity finding

Cause:
runtime.maps.library_added

Evidence:
libxxx.so newly mapped after baseline

Context:
Zygisk installed (expected)
LSPosed installed (expected)

Conclusion:
Root runtime exists, but the specific runtime drift is not part of current baseline.
```

---

## 23. 分阶段推进计划

## Phase I0 — Schema / Contract

- [ ] `ToolId.INTEGRITY`
- [ ] `IntegrityModels`
- [ ] `IntegrityProbe` contract
- [ ] `IntegrityRiskEngine`
- [ ] `IntegrityBaselineMatcher`
- [ ] pure unit tests
- [ ] 详情页 skeleton

完成标准：还没做深检测，也能用 fixture 驱动完整 UI / policy。

## Phase I1 — Fast Integrity

- [ ] self package / signing
- [ ] boot / build / SELinux
- [ ] root runtime snapshot
- [ ] TracerPid / basic maps
- [ ] ADB / VPN / proxy / automation context
- [ ] fast scan orchestrator
- [ ] last scan cache
- [ ] human report

完成标准：Samsung 上 2 秒级得到可解释 snapshot。

## Phase I2 — Baseline & Drift

- [ ] trusted baseline store
- [ ] profile editor
- [ ] expected modification policy
- [ ] immutable critical invariants
- [ ] baseline diff UI
- [ ] baseline migration tests

完成标准：Root/ADB/Tailscale 不再造成无意义红屏，非预期变化可以突出。

## Phase I3 — Device Surface / Sandbox

- [ ] CPU topology hash
- [ ] thermal topology hash
- [ ] GPU / Vulkan capability summary
- [ ] sensor shape hash
- [ ] audio/battery surface
- [ ] Java vs Root cross-check
- [ ] sandbox path consistency
- [ ] package taxonomy integration

完成标准：具备 Hunter 截图中类似组合指纹的诊断能力，但不产生跨应用追踪 ID。

## Phase I4 — Native Integrity

- [ ] NDK / CMake 最小接入
- [ ] JNI bridge tests
- [ ] runtime/file ELF consistency
- [ ] executable mapping analysis
- [ ] limited hook-surface diagnostics
- [ ] native findings explainability

完成标准：能够检测 RootTools 自身非预期 native runtime patch，并保持低误报。

## Phase I5 — Attestation / External Trust

- [x] Android Key Attestation capability（Standard + StrongBox）
- [x] cert chain / RootOfTrust / patch level parser + local signature / validity verification
- [x] Google trust anchors（含 2026 新根）+ online revocation refresh + offline fallback
- [x] PEM evidence export + Samsung SM-S908E rooted 真机验收
- [ ] trusted off-device verifier implementation
- [ ] Play Integrity feasibility（上架/Cloud project 后）

完成标准：本地完整性与外部信任 verdict 完全分层。

---

## 24. 优先级建议

### 现在最值得做

```text
I0 -> I1 -> I2
```

原因：

- 与现有 Kotlin / RootShell / Shizuku 架构兼容；
- 可以快速给当前 Samsung Root 测试机带来价值；
- 不需要先引入 NDK；
- 可以建立正确的 baseline 风险模型；
- 后续任何更深 Native 检测都有稳定承载层。

### 第二阶段

```text
I3
```

它提供最接近截图中“设备指纹”价值的能力。

### 最后再做

```text
I4 / I5
```

Native Integrity 和 Attestation 技术价值高，但工程成本、OEM 差异和维护成本也最高。

---

## 25. Definition of Done

Environment Integrity Center 不能以“页面做出来”作为完成。

至少满足：

1. RootTools 在 rooted Samsung 上不会因为 `root=true` 直接显示 critical；
2. 每个 finding 都能解释 source / observed / expected / confidence；
3. Fast scan 不持续占 CPU / IO；
4. Deep scan 不进入后台轮询；
5. unavailable 不等于 risk；
6. APK signing / self-integrity 有强 invariant；
7. baseline corrupt / missing 可安全恢复；
8. pure RiskEngine / BaselineMatcher 有 JVM tests；
9.所有 Root / Shizuku 读取有 backend attribution；
10. report 默认脱敏；
11. Samsung SM-S908E 完成 Profile A/B 验收；
12. emulator 与 non-root + Shizuku ADB 至少验证降级路径；
13. P2 Native 不复制 Hunter 私有实现；
14. 不新增 Gradle module；
15. 不引入任意 shell / arbitrary probe API。

---

## 26. 参考调研

公开资料用于确认能力领域与 Android 官方安全模型，具体实现仍需 RootTools 独立设计：

- AndroidEnvDetection / MagiskDetection 公共检测工具集合：历史 Hunter 能力索引；
- 看雪安全社区：《聊聊大厂设备指纹其二 & Hunter 环境检测思路详解》；
- Android Developers：Android Key Attestation；
- Android Developers：Play Integrity API / integrity verdicts；
- 当前 Samsung SM-S908E 上安装的 Hunter 6.65 APK 静态分析。

Hunter APK、Native binary 和其私有资产仅用于能力边界研究，不作为 RootTools 源代码来源。

---

## 27. 最终产品方向

RootTools 不应该变成另一个“过不了就整屏红色”的 Hunter clone。

更适合本项目的终态是：

```text
Environment Integrity Center

Know what is modified.
Know what is expected.
Know what changed.
Know why a finding was raised.
```

对一台长期 Root、跑自动化、需要远程可达的个人测试机，这比单纯判断“有没有 Root”更有长期价值。
