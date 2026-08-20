# Android Root 工具行业能力地图与 RootTools 产品规划

## 1. 文档目的

本文件不是简单罗列“Root 后能跑哪些 shell 命令”，而是回答三个问题：

1. **2026 年一个成熟 Android Root 工具箱可以覆盖哪些产品领域？**
2. **哪些能力应该由 RootTools 自己做，哪些应该只做桥接或状态聚合？**
3. **RootTools 目前还缺什么，后续应该按什么顺序补齐？**

调研基线：**2026-08-20**。

本次优先采用项目官方仓库、官方文档和 Android 官方文档作为参考，核心样本包括：

- App Manager；
- Hail；
- Shizuku / Sui；
- Magisk；
- KernelSU；
- APatch；
- MMRL；
- AFWall+；
- Neo Backup；
- Advanced Charging Controller (ACC)；
- AdAway；
- Android Debug Bridge / package manager 官方能力。

---

## 2. 一句话结论

Root 权限本身只是一个**高权限执行基础设施**。一个成熟的 Root 工具产品通常会把它产品化成 12～18 个领域：

```text
Privilege / Root Runtime
Package & App Control
Startup / Background
Permission / AppOps / Privacy
Backup / Restore
Root Modules / Hooks
CPU / GPU / Scheduler
Memory / ZRAM / LMK / IO
Battery / Charging / Thermal
Network / Firewall / DNS / Hosts
Process / Service / Log / Trace
Storage / Root Files
ADB / Remote Control
Automation / Profiles
System Tweaks / Props / Overlay
Boot / Recovery / Partition
Security / Integrity Diagnostics
```

RootTools 已经覆盖其中相当大的一部分基础能力，但目前的主要缺口不是“再加 20 个小开关”，而是 5 个产品级领域尚未完整：

1. **App Control Center**；
2. **Backup & Recovery**；
3. **Per-app Firewall / Network Policy**；
4. **Multi-root Runtime Center（Magisk / KernelSU / APatch）**；
5. **Charge Controller / deeper battery policy**。

---

## 3. 行业样本与可以借鉴的能力

## 3.1 App Manager — 高级 Package / App Inspector

官方项目：

https://github.com/MuntashirAkon/AppManager

官方文档：

https://muntashir.dev/AppManager/en/

其公开功能覆盖：

- Activity / Receiver / Service / Provider；
- AppOps；
- permissions；
- signatures；
- shared libraries；
- tracker / library scan；
- manifest；
- app usage / data usage / storage；
- APK install/uninstall/share/backup；
- batch operations；
- single-click operations；
- profiles；
- debloater；
- logcat；
- file manager；
- code editor；
- root/ADB 下 permission / AppOps / process / net policy / battery optimization / freeze；
- root 下 component blocking / shared preferences / data backup / SSAID 等。

对 RootTools 的启发：

> 应用治理应该从“冻结几个 App”升级为 package-level control plane。

详细方案见 `15-app-control-center.md`。

---

## 3.2 Hail — Freeze 语义与多 Backend

官方项目：

https://github.com/aistra0528/Hail

Hail 明确区分：

- Force Stop；
- Disable；
- Hide；
- Suspend；
- system app uninstall/reinstall。

同时支持 Root、Shizuku(root/ADB)、Sui、Device Owner 等不同 working mode。

最重要的产品启发是：

> **“冻结”不是 Android framework 的单一原子能力。**

尤其 Suspend 主要限制用户交互，不保证停止后台运行，因此 RootTools 不能把所有这些状态都做成一个含糊的 Freeze toggle。

---

## 3.3 Shizuku / Sui — Framework 特权桥

官方项目：

https://github.com/RikkaApps/Shizuku

官方 API：

https://github.com/RikkaApps/Shizuku-API

Shizuku 的核心是让普通应用通过由 `app_process` 启动的高权限 Java 进程，以 ADB shell 或 root identity 调用 Android system APIs / Binder。

对 RootTools 的意义：

```text
RootShell
适合 Linux/root 世界：sysfs、/data/adb、adbd、root files

Shizuku / Sui
适合 Framework 世界：PackageManager、ActivityManager、AppOps、Binder services
```

因此 RootTools 正确方向不是“RootShell 全部替换成 Shizuku”，而是继续推进 `13-shizuku-sui-bridge.md` 的 typed Privilege Router。

---

## 3.4 Magisk — Systemless Root / Module / Zygisk

官方项目：

https://github.com/topjohnwu/Magisk

Magisk 官方明确提供：

- MagiskSU；
- Magisk Modules；
- MagiskBoot；
- Zygisk。

RootTools 当前 Module Center 只偏向 Magisk / Vector 状态，后续应把“Root 模块”升级为更高一级的：

```text
Root Runtime Center
```

而不是把 Magisk 当成 Android Root 的唯一实现。

---

## 3.5 KernelSU / APatch — Kernel Root 时代

KernelSU：

https://github.com/tiann/KernelSU

APatch：

https://github.com/bmax121/APatch

两者都说明现代 Android Root 生态已经不只是 Magisk：

- KernelSU 在 kernel 层提供 root access，并有 module / metamodule 系统；
- APatch 基于 KernelPatch，也提供 kernel-oriented root 方案。

因此 RootTools 未来所有 root capability 都应基于：

```text
Capability probe
```

而不是：

```text
if (magiskInstalled) rootAvailable = true
```

---

## 3.6 MMRL — Multi-root Module Ecosystem

官方项目：

https://github.com/MMRLApp/MMRL

MMRL 官方说明支持：

- Magisk；
- KernelSU；
- APatch；
- module repository；
- bulk install；
- module dependency；
- module file transparency。

RootTools 不一定要完整复制一个“模块商店”，但 Module Center 应该至少做到：

- root runtime detection；
- installed module inventory；
- enable/disable；
- module metadata；
- service / action / web UI discovery（能力支持时）；
- reboot-required；
- module source / update status；
- module files / risk summary。

Repository discovery 可以后置。

---

## 3.7 AFWall+ — Root Firewall

官方项目：

https://github.com/ukanth/afwall

AFWall+ 代表了 Root 环境中另一类非常高价值能力：**基于 iptables 的 per-app firewall**。

公开能力包括：

- per-app allow/block；
- Wi-Fi / mobile / VPN / tethering 等不同 network type；
- IPv4 / IPv6；
- profiles；
- logging；
- boot protection；
- automation integration。

RootTools 当前“网络诊断”主要回答：

> 现在走什么网络、route/DNS/port 是什么？

但还没有回答：

> 哪个 App 能不能通过移动数据 / Wi-Fi / VPN / LAN 发包？

这是一个明显的 P1 产品缺口。

---

## 3.8 Neo Backup — Root App Data Backup

官方项目：

https://github.com/NeoApplications/Neo-Backup

Neo Backup 公开能力包括：

- 单 App APK + data backup；
- 单个与 batch restore；
- system app restore；
- schedule；
- custom app lists；
- encryption。

对 RootTools 的启发：

Root 设备最实际的长期价值之一不是“调频”，而是：

> **设备坏掉、刷机、清数据、换 ROM 后，能恢复关键 App 和配置。**

因此 Backup & Recovery 应成为 RootTools 后续一级领域，而不是附属于“保存 APK”的小按钮。

---

## 3.9 ACC — Charging Controller

官方项目：

https://github.com/VR-25/acc

ACC 通过 root/systemless 方式对充电进行更细控制，核心包括：

- charging current；
- temperature；
- voltage；
- charging switch / stop-resume threshold；
- device-specific power-supply backend。

RootTools 当前 Battery 页面已经有 Samsung 80% Battery Protection，但这仍属于系统已有能力的控制。

行业更深一层是：

```text
Charge Controller
```

它必须做强 capability probe 和机型适配，不适合直接泛化写 sysfs。

---

## 3.10 AdAway — Hosts / DNS / Privacy Network Control

官方项目：

https://github.com/AdAway/AdAway

AdAway 代表 Root 网络控制中的 hosts 路径，同时也提供 non-root local VPN 路径。

RootTools 可以借鉴的不是“再做一个广告拦截器”，而是把 network control 分层：

```text
L3/L4: Firewall / UID policy
Name resolution: DNS / hosts
App framework: netpolicy / background data
```

不同层解决的问题不同，不应该只用一个“禁联网”开关概括。

---

## 3.11 Android 官方 ADB / Package Manager

官方文档：

https://developer.android.com/tools/adb

Android 官方 `adb shell` 自身就提供 package manager、activity manager 等大量系统操作入口。

这说明 RootTools 的价值不是“把 adb 命令做成按钮”，而是：

- capability probing；
- typed controller；
- state model；
- safety；
- diff；
- verification；
- rollback；
- automation API。

---

## 4. Root 能力的完整技术分层

从底层权限来看，可以分成 6 层。

## 4.1 Linux / Kernel Layer

Root 可以接触：

- `/proc`；
- `/sys`；
- cgroup / cpuset / scheduler；
- block device；
- kernel logs；
- netfilter / iptables；
- power supply nodes；
- thermal nodes；
- filesystem ownership / mode；
- mount namespace；
- device nodes。

对应产品：

- CPU / thermal / memory / IO；
- firewall；
- charging；
- root file access；
- low-level diagnostics。

## 4.2 Android Framework Layer

通过 root shell / Shizuku / Binder 可以进一步操作：

- PackageManager；
- ActivityManager；
- AppOps；
- UserManager；
- JobScheduler；
- DeviceIdle；
- NetworkPolicy；
- Notification / usage / special access state；
- package/component state。

对应产品：

- App Control Center；
- startup/background；
- permission/AppOps；
- package lifecycle；
- app network policy。

## 4.3 Root Runtime Layer

Magisk / KernelSU / APatch 提供：

- root grant；
- module；
- systemless modification；
- boot scripts；
- Zygote/kernel hooking（实现不同）。

对应产品：

- Root Runtime Center；
- Module Center；
- module state / scope / risk；
- reboot-required management。

## 4.4 App Private Data Layer

Root 可以访问普通应用不能访问的：

- `/data/user/...`；
- `/data/user_de/...`；
- app private files；
- shared prefs；
- database；
- private cache；
- app-specific config。

对应产品：

- full app backup；
- forensic / inspector；
- prefs/database viewer。

这也是最容易造成数据损坏的领域之一。

## 4.5 Boot / Partition Layer

Root 工具还可能触及：

- boot image；
- init boot；
- recovery；
- vbmeta；
- dynamic partitions；
- slot；
- flashing。

这属于高危维护工具领域，并不意味着应该进入日常 RootTools。

## 4.6 Automation Layer

Root 使很多动作可以真正自动化：

- boot profile；
- time/profile based app policies；
- network policy profile；
- performance profile；
- battery profile；
- remote ADB recovery；
- scheduled backup；
- state-triggered diagnostics。

RootTools 当前已有 Intent API / Quick Tile，这一层可以成为所有领域的统一上层入口。

---

## 5. 行业功能地图

| 领域 | 典型能力 | 参考项目 | RootTools 当前状态 | 建议 |
|---|---|---|---|---|
| Root Runtime | su、root manager、root type | Magisk/KSU/APatch | Magisk 偏重 | P1 扩成 multi-root |
| App Control | package/detail/component/AppOps | App Manager/Hail | 冻结基础版 | **P0 优先** |
| Startup | receiver/service/job/start trace | App Manager + system tools | 已有 | 合并进 App Control runtime |
| Permissions | runtime/AppOps/special access | App Manager | 部分 | P0/P1 |
| Backup | APK + private data + policy | Neo Backup/App Manager | 仅报告/导出 | **P1 新领域** |
| Module | enable/disable/repo/dependency | Magisk/MMRL/KSU/APatch | 基础已做 | P1 扩展 |
| CPU/Scheduler | freq/governor/cpuset/uclamp | kernel tools | 已做较多 | 继续稳定性 |
| Memory/IO | ZRAM/LMK/PSI/block IO | system/root tools | 已做 | 保持观测优先 |
| Battery/Thermal | charge limit/current/temp/voltage | ACC | 80% protection + observe | P2 扩展 |
| Firewall | UID/network type/firewall profiles | AFWall+ | 仅 net diagnosis | **P1 新领域** |
| DNS/Hosts | hosts / DNS override | AdAway | 无 | P2 optional |
| Process/Logs | process/service/wakelock/logcat | App Manager/system | 已做 | 可增加 trace |
| Storage | root FS / app private storage | App Manager/root explorer | capacity/IO only | P2 read-only inspector |
| ADB Remote | TCP/native wireless/recovery | Android ADB | 规划较完整 | K milestone |
| Automation | intents/profiles/tiles/schedules | AFWall/Hail/Tasker ecosystem | 已有基础 | 扩 Profile |
| System Tweaks | props/overlay/settings/init | root tools | actions 少量 | Lab only |
| Boot/Flash | boot image/partition/recovery | Magisk/KSU/APatch | 禁止 | 保持外部工具 |
| Privacy scanner | trackers/libraries/components | App Manager | 无 | P2/P3 |

---

## 6. RootTools 应该形成的产品域

基于现有工程，不建议无限增加首页卡片。建议最终收敛为 8 个一级产品域，每个域内部再有工具卡或详情入口。

## Domain A — Device Health & Performance

包含：

- Dashboard；
- CPU；
- Memory / ZRAM / PSI；
- Thermal；
- Battery；
- Storage / IO；
- Performance Policy。

当前成熟度：**高**。

重点不是继续加 sysfs 开关，而是：

- 更稳；
- 更低采样开销；
- 可解释；
- 设备 capability probe。

---

## Domain B — App Control Center

包含：

- App inventory；
- component；
- AppOps；
- permission；
- runtime；
- startup；
- freeze；
- profile；
- debloat；
- APK export；
- tracker scanner。

当前成熟度：**低到中**。

优先级：**最高新增领域**。

详细见 `15-app-control-center.md`。

---

## Domain C — Connectivity & Firewall

包含：

- Root ADB；
- Native Wireless；
- Tailscale；
- interfaces / route / DNS；
- listening ports；
- per-app firewall；
- per-network profile；
- netpolicy；
- optional hosts/DNS policy。

当前成熟度：

- connectivity diagnostics：高；
- policy/firewall：低。

建议新增 `Firewall & App Network`，但可以先作为 Network detail 的第二个入口，不急于增加首页卡片。

---

## Domain D — Backup & Recovery

建议未来独立首页卡片。

包含：

- device config snapshot；
- app APK export；
- app policy backup；
- app private data backup；
- module list backup；
- RootTools config backup；
- scheduled backup；
- encrypted backup；
- restore verification；
- disaster recovery checklist。

这是长期 Root 设备非常高价值的能力。

---

## Domain E — Root Runtime & Modules

把现在的“Root 模块”升级为：

```text
Root Runtime
```

包含：

- current root implementation；
- Magisk / KernelSU / APatch detection；
- su health；
- root grant state（可获取时）；
- module inventory；
- module enable/disable；
- Zygisk / LSPosed / Sui / related runtime status；
- module WebUI / action discovery；
- reboot required；
- module update / source metadata；
- boot module failure safe-mode hints。

RootTools 不需要取代每个 root manager 的 installer / patcher。

---

## Domain F — Diagnostics & Observability

包含：

- process；
- service；
- wakelock；
- logcat；
- kernel / dmesg（受支持时）；
- ANR / crash summary；
- package runtime；
- one-click snapshot；
- lightweight trace；
- before/after comparison。

当前成熟度：中高。

后续重点：

- package attribution；
- timeline；
- export；
- crash / ANR correlation。

---

## Domain G — Battery & Charge

包含：

- battery health；
- temperature；
- Samsung battery protection；
- charge current/voltage read；
- charge switch capability probe；
- charging threshold；
- temperature limit；
- charging profile；
- overnight mode。

这里必须遵循：

```text
read first
→ detect device backend
→ vendor-specific adapter
→ conservative write
→ immediate verify
→ thermal safety override
```

不能设计一个通用“写 `/sys/class/power_supply/...`”页面让用户自己试。

---

## Domain H — Automation & Lab

### Automation

- Quick Tile；
- explicit Intent API；
- saved profile；
- schedule；
- boot policy；
- external Agent / MCP integration；
- dry-run / result report。

### Lab

只放低频、高风险、高专业度功能：

- raw system property inspector；
- root file read-only browser；
- manifest / prefs / DB inspector；
- IFW；
- init / overlay inspection；
- experimental Binder service probe。

Lab 不应该默认出现在首页第一屏。

---

## 7. 不建议成为 RootTools 核心能力的领域

## 7.1 Root Installer / Boot Image Patcher

Magisk、KernelSU、APatch 自己已经承担：

- boot image patch；
- installation；
- update；
- root manager specific migration。

RootTools 最多做：

- detect；
- status；
- deep link；
- backup current metadata；
- diagnostics。

不自己做 boot image patcher。

## 7.2 Partition Flasher

日常工具箱不应该把：

- boot；
- recovery；
- vbmeta；
- super；
- vendor_boot；
- init_boot；

做成“一键刷写”。

风险收益比不成立。

## 7.3 Generic Root Terminal

RootTools 已有 typed Controller 架构，不应该为了“功能全”反过来把任意 root terminal 当主要产品入口。

高级用户真需要 terminal 可以使用专门终端工具。

## 7.4 默认自动修改 SELinux / Thermal

RootTools 应该可以**诊断** SELinux / thermal state，但不应该把：

- permanently permissive；
- disable thermal；
- remove all vendor limits；

做成普通模式功能。

---

## 8. 新增产品优先级

## P0 — 先补“应用控制”

原因：

- 与用户日常“装很多 App、控制自启、后台、权限、组件”高度匹配；
- 与现有 Startup / Diagnostics / PackagePolicy / Shizuku 复用最高；
- 不需要立刻碰 kernel / partition 高风险区域；
- UI 价值非常明显。

任务：见 `15-app-control-center.md`。

---

## P1 — Backup & Recovery

建议单独输出后续文档：

```text
17-backup-recovery.md
```

第一阶段：

- RootTools config；
- package/app list；
- APK snapshot；
- policy backup；
- Magisk/KSU/APatch module inventory；
- recovery index。

第二阶段才做完整 app private data。

---

## P1 — Firewall & App Network

建议后续：

```text
18-firewall-network-policy.md
```

范围：

- Android NetworkPolicy；
- per-app mobile/Wi-Fi/VPN/LAN；
- root netfilter backend；
- boot restore；
- profiles；
- logs；
- automation API。

先 probe Samsung kernel / iptables/nft behavior，再决定实现方式。

---

## P1 — Root Runtime Center

建议扩展现有 `07-root-module-center.md`，而不是创建多个 root manager 卡片。

任务：

- Magisk / KernelSU / APatch probe；
- generic `RootRuntimeSnapshot`；
- module provider interface；
- current implementation / version；
- module enable/disable adapter；
- safe reboot-required state；
- Zygisk / LSPosed / Sui state aggregation。

---

## P2 — Charge Controller

建议扩展 `11-battery-thermal.md`。

第一步只读：

- charging current；
- voltage；
- power supply topology；
- available charge switches；
- vendor nodes。

第二步才有 typed controller。

---

## P2 — Tracker / Privacy Scanner

属于 App Control Center 的高级能力。

必须先处理：

- signature data source；
- license；
- update strategy；
- false positives；
- scan cache；
- block behavior。

---

## P3 — Root File / System Inspector

目标不是替代完整 root file manager，而是为了诊断：

- app data path；
- module files；
- props；
- init fragments；
- overlay；
- configs。

首版 read-only。

---

## 9. 首页信息架构建议

当前 RootTools 已有 13 张卡片，继续一项功能一张卡会越来越难维护。

短期不重构首页，但新增能力遵循：

### 保持一级卡片

- 设备看板；
- 性能控制；
- Root ADB；
- 应用控制；
- 进程诊断；
- Root Runtime；
- 电池与温控；
- Backup & Recovery（未来）。

### 合并为详情子入口

- Startup → App Control / Runtime；
- Firewall → Network；
- Storage → Dashboard/Diagnostics 或保留当前卡片；
- Shizuku/Sui → 权限/Root Runtime + 独立诊断入口；
- 常用操作 → 首页 Quick Actions 区域。

这属于未来导航收口，不要求在 App Control Center 开发前先重构。

---

## 10. 通用 Controller 规则

行业功能变多以后，最危险的问题是“每个页面自己写 root shell”。

RootTools 必须保持：

```text
UI / Tile / Automation / Agent
              ↓
         Semantic Action
              ↓
          Controller
              ↓
       Capability Router
        ┌─────┼─────────┐
        ↓     ↓         ↓
    Android  Shizuku   RootShell
      API    / Sui
```

禁止出现：

```text
FirewallScreen -> su iptables
AppScreen -> su pm
BackupScreen -> su tar
BatteryScreen -> su echo > sysfs
```

而应出现：

```text
FirewallController
PackagePolicyController
BackupController
ChargePolicyController
```

核心逻辑与 Backend fallback 的具体测试契约统一遵循 `14-core-logic-testing-standard.md`，避免每个新 Root 领域自行定义一套安全规则。

---

## 11. Capability 模型需要扩展

现有 capability 粒度还不够。

建议从卡片级：

```text
ROOT
MAGISK
NETWORK
SHIZUKU
```

演进为语义级：

```text
ROOT_LINUX
ROOT_FS
SYSFS_READ
SYSFS_WRITE
PACKAGE_READ
PACKAGE_CONTROL
COMPONENT_CONTROL
APP_OPS_READ
APP_OPS_WRITE
PERMISSION_CONTROL
USAGE_STATS
PROCESS_READ
FRAMEWORK_DIAGNOSTICS
NETFILTER
NETWORK_POLICY
APP_DATA_READ
APP_DATA_WRITE
MODULE_MAGISK
MODULE_KERNELSU
MODULE_APATCH
CHARGE_CONTROL
BOOT_IMAGE_ACCESS
```

Feature 只问“我需要什么能力”，不问“设备是不是 Magisk”。

---

## 12. Root Runtime 抽象

建议未来模型：

```kotlin
enum class RootImplementation {
    NONE,
    MAGISK,
    KERNEL_SU,
    APATCH,
    OTHER,
}

data class RootRuntimeSnapshot(
    val rootAvailable: Boolean,
    val implementation: RootImplementation,
    val versionName: String?,
    val versionCode: Long?,
    val suUid: Int?,
    val moduleSupport: Boolean,
    val zygiskLikeRuntime: String?,
    val safeModeAvailable: Boolean?,
)
```

RootShell 继续只是“执行能力”，RootRuntime 是“环境描述”。

---

## 13. Backup 的产品原则

行业工具证明 Root backup 很有价值，但也非常容易产生“备份成功，恢复失败”的假安全感。

RootTools 后续 Backup 必须做到：

### Backup Manifest

每份备份记录：

- package；
- version；
- signing digest；
- Android version；
- userId；
- root runtime；
- selected data scopes；
- compression；
- encryption；
- file count / size；
- checksum；
- createdAt。

### Restore Preflight

检查：

- signature compatibility；
- package installed state；
- version compatibility；
- userId；
- available storage；
- CE/DE unlock state；
- SELinux restore capability；
- backup integrity。

### Restore Verify

恢复后不能只检查 exit code，要检查：

- package launches；
- data dir owner；
- expected files；
- selected policy；
- crash / ANR；
- relevant service state。

---

## 14. Firewall 的产品原则

Root firewall 不能只做一个 `iptables -A OUTPUT ...` demo。

要处理：

- UID mapping；
- app reinstall UID change；
- multi-user UID；
- Wi-Fi / cellular / VPN / tethering；
- IPv4 / IPv6；
- rule ordering；
- boot race；
- VPN coexistence；
- DNS leak；
- rule persistence；
- logging；
- rollback；
- failsafe。

第一版应该先做：

```text
Capability probe + dry-run compiler + read current rules
```

再开放写入。

---

## 15. Charge Controller 的产品原则

不同 OEM 的 power_supply / charging switch 完全不统一。

因此接口不能是：

```kotlin
fun writeSysfs(path: String, value: String)
```

而应是：

```kotlin
interface ChargeBackend {
    suspend fun probe(): ChargeCapabilities
    suspend fun readState(): ChargeState
    suspend fun setChargeLimit(percent: Int): ActionResult
    suspend fun setCurrentLimit(milliAmp: Int): ActionResult
    suspend fun restoreDefaults(): ActionResult
}
```

Samsung adapter 可以单独实现，但仍位于单 app module 内。

---

## 16. 高风险功能的统一安全策略

所有新领域继续使用 4 级风险：

### R0 Read-only

直接执行。

### R1 Reversible

单次确认，保存 before state。

### R2 System-impacting

明确 preview + verify + rollback。

### R3 Device/data critical

二次确认；默认不支持 batch；某些能力永久 out-of-scope。

典型 R3：

- clear app data；
- app private data overwrite；
- partition write；
- boot image write；
- disable critical system component；
- disable thermal safety；
- raw system file edit。

---

## 17. RootTools 与行业工具的关系

产品目标不是：

```text
把 App Manager + Hail + Magisk + AFWall + Neo Backup + ACC 全抄一遍
```

而是：

```text
统一状态 / Controller / Audit / Automation / UX
          +
把个人高频场景做深
```

RootTools 最有价值的差异化应该是：

### 17.1 一个设备真值源

CPU、App、ADB、Network、Battery 不各自重复 shell。

### 17.2 一个 Action Router

UI / Tile / Agent / Automation 都调用同一语义动作。

### 17.3 一个 Audit / Rollback 体系

所有 Root 修改可追踪。

### 17.4 Profile 跨领域编排

例如：

```text
Test Device Profile
├── Performance = Auto
├── Appium = enabled + keep alive
├── iQIYI/Facebook = frozen
├── Firewall = allow Wi-Fi only for test apps
├── ADB = Root TCP on
└── Battery = protection on
```

这类跨领域 Profile 是单一工具难以提供的能力。

### 17.5 Agent / MCP 可调用

后续 AiBox / Agent 不需要拿 root shell，而是调用：

```text
get_app_state
apply_app_profile
set_performance_mode
set_adb_transport
run_diagnostic
backup_app
```

这会比“给 Agent 一个 `su` terminal”安全得多。

---

## 18. 推荐未来 Roadmap

```text
Now
├── J Shizuku / Sui
├── K ADB Control Center
└── L App Control Center

Next P1
├── M Root Runtime Center
├── N Backup & Recovery Foundation
└── O Firewall & App Network

P2
├── Charge Controller
├── Tracker / Privacy Scanner
├── Module repository metadata
└── Root File / System Inspector (read-only)

P3 / Lab
├── IFW advanced blocking
├── prefs/db inspector
├── system tweak inspector
└── device-specific experimental adapters
```

---

## 19. 本轮具体决策

本次不直接开发所有行业能力，先形成产品和工程边界：

- [x] 将截图能力归类为 `App Control Center`，不再视为“应用冻结增强”；
- [x] 输出 `15-app-control-center.md` 详细设计；
- [x] 完成 Root 行业能力分层；
- [x] 明确 P0/P1/P2/P3；
- [x] 明确 Backup / Firewall / Root Runtime / Charge 是主要缺口；
- [x] 保持单 `app` Gradle module；
- [x] 不复制 GPL 项目实现；
- [x] 不把 partition flash / generic root terminal 纳入日常核心能力；
- [ ] App Control Center 代码实施；
- [ ] Backup & Recovery 独立详细方案；
- [ ] Firewall & App Network 独立详细方案；
- [ ] Root Runtime Center 详细迁移方案；
- [ ] Charge Controller capability probe 方案。

---

## 20. 研究来源

### Package / App Governance

- App Manager: https://github.com/MuntashirAkon/AppManager
- App Manager Docs: https://muntashir.dev/AppManager/en/
- Hail: https://github.com/aistra0528/Hail
- Android ADB: https://developer.android.com/tools/adb

### Privilege / Root Runtime

- Shizuku: https://github.com/RikkaApps/Shizuku
- Shizuku API: https://github.com/RikkaApps/Shizuku-API
- Magisk: https://github.com/topjohnwu/Magisk
- KernelSU: https://github.com/tiann/KernelSU
- APatch: https://github.com/bmax121/APatch

### Modules

- MMRL: https://github.com/MMRLApp/MMRL

### Network

- AFWall+: https://github.com/ukanth/afwall
- AdAway: https://github.com/AdAway/AdAway

### Backup

- Neo Backup: https://github.com/NeoApplications/Neo-Backup

### Battery / Charging

- Advanced Charging Controller: https://github.com/VR-25/acc

---

## 21. License / Reference Boundary

本次调研的多个参考项目使用 GPL-3.0 / GPL-3.0-or-later / AGPL 等 copyleft license。

RootTools 当前采用以下原则：

1. 可以参考公开产品功能、Android 系统能力和交互问题；
2. 不复制 competitor source file；
3. 不复制图标、截图素材、内置 rule database；
4. 不把 GPL Java/Kotlin 实现“改几个类名”后放入工程；
5. 若未来确实需要复用代码，必须单独做 license review；
6. 第三方 tracker / bloatware / module metadata 同样必须记录 source + license；
7. Android 官方 API / shell 能力应优先自行做 typed adapter。

这允许 RootTools 吸收成熟工具的产品经验，同时保持自己的架构和维护边界。
