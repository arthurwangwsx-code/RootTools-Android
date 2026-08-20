# App Control Center / 应用控制中心规划

## 1. 结论

当前 `RootTools` 的 `ToolId.APPS` 只承载“应用冻结”这一小部分能力，已经不足以覆盖 Root 设备最常见的应用治理场景。

本阶段建议**不新增 Gradle module、不新增第二张“应用管理”首页卡片**，而是把现有：

```text
应用冻结
```

升级为：

```text
应用控制
App Control Center
```

并把现有 `PackagePolicyController`、`StartupRepository`、`DiagnosticsRepository`、Shizuku / Sui 规划、
`RootActionAuditStore` 收敛到同一个应用治理领域。

用户提供的截图所展示的产品形态与开源项目 **App Manager** 的能力高度一致：应用列表、应用详情、Activity / Service /
Receiver / Provider、AppOps、权限、签名、共享库、批量操作、冻结、备份、预装应用治理等都属于成熟的“高级包管理器”范畴。
本项目可以参考这些**产品能力与交互模式**，但不复制其 GPL 代码、图标、数据库或实现细节。

该模块的目标不是变成“Root 文件管理器 + APK 逆向工具 + 终端”的大杂烩，而是成为 RootTools 中负责：

> **看清一个 App → 判断它为什么会运行 → 精确控制它 → 批量形成策略 → 可以恢复。**

---

## 2. 截图能力拆解

从截图可以拆出 5 个核心产品模式。

### 2.1 应用总览列表

每一行同时展示：

- 图标 / App 名称；
- package name；
- version name / version code；
- 首次安装 / 最近更新时间；
- UID；
- user app / system app；
- target SDK；
- 签名算法 / 签名摘要；
- tracker / bloatware / large heap / sensor 等标签；
- 快速搜索、过滤、排序。

这比 RootTools 当前“优先显示几个高价值对象”的模式更适合作为完整 App 管理入口。

### 2.2 App Detail 多维检查

截图中的横向 Tab 已经覆盖：

- 应用信息；
- Activity；
- Service；
- Broadcast Receiver；
- Content Provider；
- AppOps / 程序操作；
- Runtime Permission；
- 自定义权限；
- Features；
- Configuration；
- Signature；
- Shared Library；
- Overlay 等。

这说明“应用管理”不应该只理解成 freeze / uninstall，而应该是一个 package-level system inspector。

### 2.3 组件级启停

截图对 Activity / Service / Receiver 提供单组件开关，并标记 tracker、权限、启动模式等信息。

这对 RootTools 特别有价值，因为当前“启动治理”已经能回答“谁启动了”，但还不能非常精细地回答：

> “到底是这个 App 的哪个 Receiver / Service / Job 在拉起它，我是否能只关这个入口？”

### 2.4 AppOps / Permission 治理

截图同时展示：

- raw AppOps；
- allow / ignore / default 等 mode；
- manifest/runtime permission；
- dangerous / normal 等权限属性。

RootTools 需要保留“权限”和“AppOps”两个概念，不得把二者在 UI 上混为一个开关。

### 2.5 Batch / One-click / Profile

截图主列表支持多选，底部直接出现：

- 卸载；
- 冻结/解冻；
- 强行停止；
- 更多选项。

全局菜单还有：

- 一键操作；
- 应用使用情况；
- 正在运行的应用；
- 配置；
- 预装卸载器；
- 实验室。

这套设计背后的关键不是菜单形式，而是**把单 App 操作升级成策略和批处理**。

---

## 3. 本模块产品边界

### 3.1 必须做

1. 完整 App inventory；
2. App Detail inspector；
3. Activity / Service / Receiver / Provider 组件管理；
4. AppOps + Runtime Permission；
5. running / force-stop / freeze / enable / standby / background policy；
6. 关键路径、APK、split、ABI、签名、installer source 信息；
7. 多选批量操作；
8. Profile / Policy Set；
9. 导出 APK 与应用清单；
10. 所有写操作可审计、可验证、尽可能可回滚。

### 3.2 应该做，但不作为首版阻塞项

- tracker / library scanner；
- component blocking rule pack；
- 应用使用时长 / data usage；
- network policy / battery optimization；
- Debloater；
- APK + policy backup；
- Manifest / native library / shared library 浏览；
- foreground activity / running component 追踪；
- policy import / export；
- root-only IFW component blocking。

### 3.3 不直接塞进首版 App Control Center

下面这些能力属于“Root 应用可以做”，但产品边界应该拆出去：

- **完整 App Data Backup / Restore**：独立 Backup & Recovery 领域；
- **iptables / nftables Firewall**：独立 Network Policy / Firewall 领域；
- **Magisk / KernelSU / APatch module repository**：Root Runtime / Module Center；
- **CPU / GPU / Thermal**：Performance；
- **充电电流 / 电压 / 温度阈值控制**：Battery / Charge Controller；
- **任意 Root 文件编辑器 / 终端**：Lab，高风险且不是日常主路径；
- **Boot / partition flash**：不作为 RootTools 日常功能，风险过高。

---

## 4. 信息架构

## 4.1 首页卡片

现有：

```text
应用冻结
冻结 N · 受限 N
```

升级为：

```text
应用控制
Running 18 · Frozen 6 · Risk 4
```

Badge 只放一个最重要的状态：

- `RUNNING`
- `FROZEN`
- `RISK`
- `PROFILE`
- `SHIZUKU`

首页不展示完整 App 列表。

---

## 4.2 应用列表页

### 默认行信息

```text
[icon] 爱奇艺                        USER
       com.qiyi.video
       14.7.5 · SDK 33 · arm64/32
       Running · 72 trackers · 2 boot receivers
```

右侧只显示一个主要状态：

```text
RUNNING / FROZEN / RESTRICTED / SYSTEM
```

避免一行同时塞 8 个彩色 tag。

### 搜索字段

搜索默认包含：

- app label；
- package name；
- installer；
- UID；
- component class name（高级搜索）；
- permission / AppOp（高级搜索）；
- shared library / native library（高级搜索）。

### Filter

P0：

- User / System；
- Running；
- Frozen；
- Force-stopped；
- Boot capable；
- Background restricted；
- Low target SDK；
- Updated recently。

P1：

- Tracker detected；
- Debloat candidate；
- Backup exists；
- Has exported component；
- Has accessibility / notification listener / VPN / device admin 等特殊能力；
- Installed by selected installer；
- Large storage / high data usage / high screen time。

### Sort

- label；
- package；
- install / update time；
- target SDK；
- version；
- total size；
- data usage；
- screen time；
- running first；
- frozen first；
- boot receiver count；
- tracker count；
- last RootTools action。

---

## 4.3 多选模式

长按进入多选。

底部只保留 4 个高频动作：

```text
Force stop | Freeze/Enable | Profile | More
```

`More` 中包含：

- standby bucket；
- background AppOp；
- battery optimization；
- clear cache；
- export APK；
- export app list；
- uninstall user app；
- backup policy；
- component rule apply。

批量 destructive action 不能直接执行，必须进入 `Action Plan Preview`。

---

## 5. App Detail 页面

不建议照搬截图中十几个横向 Tab。手机宽度下会形成严重的 discoverability 问题。

RootTools 建议收敛成 8 个一级 Tab，每个 Tab 内再按 section 分组。

### 5.1 Overview

顶部 Summary：

- icon / label / package；
- version / versionCode；
- user/system；
- enabled / stopped / suspended / hidden；
- target / min SDK；
- UID / user ID；
- installer / initiating package；
- debuggable / testOnly；
- ABI；
- APK / split count；
- app data size；
- last update / first install；
- current backend：ROOT / SHIZUKU ROOT / SHIZUKU ADB。

首屏快捷动作：

```text
Launch | Force stop | Freeze/Enable | More
```

`More`：

- uninstall；
- clear cache；
- clear data；
- export APK；
- share package info；
- open system app info；
- open Play / selected store；
- add to profile。

### 5.2 Components

统一容纳：

- Activities；
- Services；
- Broadcast Receivers；
- Content Providers。

顶部二级筛选：

```text
All | Activity | Service | Receiver | Provider
```

每个 component 展示：

- class name；
- exported；
- enabled state；
- required permission；
- process；
- directBootAware；
- foreground service type（如适用）；
- intent filter 摘要；
- tracker match；
- running state（Service 可用时）；
- BOOT / package changed / connectivity 等关键 Receiver tag。

动作：

- launch Activity；
- create shortcut（P2）；
- start/stop Service 仅在明确支持时；
- disable / restore component；
- block / unblock（高级 IFW 模式，P3）。

### 5.3 Ops

这一页只处理 **AppOps / Special Access**。

默认不是展示全部 raw op，而是分组：

- Background；
- Location；
- Sensors；
- Notifications；
- Clipboard；
- Media / Audio；
- Overlay / Install packages；
- VPN / accessibility 相关状态；
- Raw AppOps（Expert）。

每项展示：

- op name / op id；
- current mode；
- package / UID scope；
- last access / reject time（可获取时）；
- 当前 backend 是否有修改能力。

不允许对所有 raw op 提供无差别 toggle。

### 5.4 Permissions

分开显示：

- Requested permissions；
- Runtime permissions；
- Declared custom permissions；
- Permission groups；
- Special platform privileges（只读或跳系统设置）。

对于 runtime permission：

- granted / denied；
- flags；
- one-time / auto-reset 等信息（可获取时）；
- grant / revoke 仅在 capability probe 明确支持时出现。

### 5.5 Runtime

复用现有 Diagnostics / Startup 能力：

- current processes；
- process PID / UID / RSS / CPU；
- running services；
- recent process starts；
- boot receiver count；
- startup reasons；
- wake locks（可归属时）；
- jobs / alarms 摘要；
- standby bucket；
- battery optimization；
- background AppOps；
- force-stopped state。

这里是“为什么它自己又起来了”的核心页面。

### 5.6 Storage

- base APK path；
- split APK paths；
- `/data/user/<userId>/<package>`；
- `/data/user_de/<userId>/<package>`；
- external app dirs；
- cache / code cache；
- app storage usage；
- native library dirs；
- OBB / media 路径（如存在）。

P0 只做路径与大小查看 + `Export APK`。

P2 才考虑：

- cache explorer；
- shared prefs read-only viewer；
- database read-only viewer。

编辑器全部放 Expert/Lab，不在默认页直接暴露。

### 5.7 Code & Signing

- manifest summary；
- requested features；
- config changes；
- shared libraries；
- native `.so`；
- ABI / ELF bitness；
- signing certificate SHA-256；
- signer history；
- signing scheme 能力（可解析时）；
- overlay target / static shared library 信息；
- debuggable / profileable；
- target / min SDK；
- installer source。

P2：

- raw manifest viewer；
- native library export；
- APK file tree；
- version-to-version manifest / permission / component diff。

### 5.8 Policy & Backup

集中展示 RootTools 对该 App 做过的治理：

- current profile；
- freeze policy；
- standby bucket；
- background AppOp；
- component rules；
- network policy；
- battery optimization；
- RootTools audit history；
- exported APK snapshots；
- policy backup；
- full app-data backup availability（若未来 Backup Center 接入）。

---

## 6. Profile / 一键操作设计

这是本模块比“简单包管理器”更值得做的地方。

## 6.1 Profile 模型

```kotlin
data class AppPolicyProfile(
    val id: String,
    val name: String,
    val packages: Set<String>,
    val desiredState: Map<String, AppDesiredState>,
)
```

`AppDesiredState` 只保存**语义状态**，不保存 shell 命令：

```text
enabled
frozen
standbyBucket
backgroundMode
batteryOptimization
componentRules
networkPolicy
```

### 内置模板

第一版只提供模板，不自动执行：

- `Daily`：日常使用；
- `Test Device`：保活自动化 / Appium / AiBox；
- `Battery Saver`：限制低频 App；
- `Clean Boot`：尽量减少非核心 App 启动；
- `Travel`：限制后台与移动数据。

### Profile 应用流程

```text
选择 Profile
→ 读取当前状态
→ 生成 Diff
→ 标记 Protected / Unsupported / Risky
→ 用户确认
→ 分批执行
→ 每步 verify
→ 失败停止或继续（默认停止）
→ 生成结果与 rollback plan
```

不能用 `forEach { rootShell.execute(...) }` 直接批量写。

---

## 7. Component Manager 设计

## 7.1 两种实现层级

### Level 1 — Framework component state

优先通过 Shizuku / Sui PackageManager Binder；RootShell 作为 fallback。

适合：

- enable / disable component；
- restore default state；
- 查看 component metadata。

优点：

- typed；
- 快；
- 容易验证状态；
- 非 Root + Shizuku ADB 也可能部分可用。

### Level 2 — Root IFW blocking

App Manager 文档说明其 root component blocking 主要使用 Intent Firewall；这类方式相比普通 PackageManager disable 更难被 App 自己恢复，但：

- OEM / Android 版本差异更大；
- system package 误配风险更高；
- 规则写入需要 root 文件能力；
- 回滚和兼容验证必须非常严格。

因此 RootTools 把 IFW 放到 P3 Expert，不作为组件管理首发能力。

## 7.2 Protected Component Policy

默认禁止批量修改：

- `android`；
- SystemUI；
- Settings；
- Package Installer；
- Phone / Telephony 核心组件；
- Launcher 当前主入口；
- Root manager；
- Shizuku / Sui；
- RootTools 自身；
- 当前远程连接基础设施。

对 system app component 进行写操作时增加：

```text
System Critical Risk
```

并要求进入 Expert Mode。

---

## 8. Freeze / Disable / Hide / Suspend 的语义

行业工具容易把这些全部叫“冻结”，但实际效果不同。

RootTools UI 必须区分：

| 动作 | 语义 | 是否阻止后台 | UI 默认暴露 |
|---|---|---:|---:|
| Force Stop | 立即停止，后续可再次被显式启动 | 临时 | ✅ |
| Disable | package/component disabled | 强 | ✅，作为 Freeze 默认实现 |
| Suspend | 禁止用户交互、通知等 | 不保证阻止后台 | P2 |
| Hide | 对 package manager 隐藏 | OEM/权限差异大 | Expert |
| Uninstall for user | 当前 user 移除 system app | 强，可 `install-existing` 恢复 | Debloater |

Hail 的官方说明同样强调 Suspend 并不等于后台停止，因此 RootTools 不把 Suspend 做成“省电冻结”的默认方案。

---

## 9. AppOps / Permission 架构

### 9.1 Gateway

```kotlin
interface AppOpsGateway {
    suspend fun list(packageName: String, userId: Int): List<AppOpRecord>
    suspend fun setMode(request: SetAppOpRequest): ActionResult
}

interface PermissionGateway {
    suspend fun list(packageName: String, userId: Int): PermissionSnapshot
    suspend fun grantRuntime(request: RuntimePermissionRequest): ActionResult
    suspend fun revokeRuntime(request: RuntimePermissionRequest): ActionResult
}
```

Router：

```text
Shizuku/Sui Binder
    ↓ preferred
Root framework shell fallback
    ↓
Unsupported
```

### 9.2 原则

1. AppOps `allow` 不等于 runtime permission `granted`；
2. `normal` permission 不展示误导性的 revoke 开关；
3. signature / privileged permission 默认只读；
4. “当前 backend 无法修改”要直接显示原因；
5. 不提供“Grant all permissions”按钮；
6. 每次写入保存 before / after mode。

---

## 10. Tracker / Library Scanner

这是截图中很显眼的能力，但不应第一阶段就把数据库复制进来。

建议拆两层。

### 10.1 Library Inspector

不依赖 tracker 数据库即可做：

- native libraries；
- shared libraries；
- manifest components；
- package names / class prefix；
- SDK / dependency fingerprint（能够可靠识别时）。

### 10.2 Tracker Rule Pack

独立版本化：

```text
tracker-rules.json
├── id
├── packagePrefixes
├── componentPatterns
├── category
├── source
├── license
├── lastUpdated
└── confidence
```

要求：

- 数据来源许可证可独立审计；
- 不复制 App Manager 内置 tracker 数据；
- 规则可更新但默认不后台频繁联网；
- 每个命中展示 confidence；
- 默认“报告”，不自动 block；
- block tracker component 必须走 Component Controller + Audit。

---

## 11. Debloater / 预装应用治理

第一阶段不碰 `/system` 动态分区，不物理删除系统 APK。

只提供：

1. `disable-user`；
2. `uninstall --user <id>`（capability 支持时）；
3. `install-existing` restore；
4. export APK before action；
5. dependency / protected package preflight；
6. debloat profile；
7. rollback。

标记：

```text
Safe-ish
Needs review
Critical / blocked
```

“Bloatware” 第一版只允许：

- 用户自己标记；
- RootTools 本地规则；
- OEM profile；

不因为某个未知数据库说是 bloatware 就直接允许批量删除。

---

## 12. APK / Backup 范围

### P0：Export APK

支持：

- base.apk；
- split APKs；
- version metadata；
- signing SHA-256；
- package metadata；
- 导出成一个 RootTools snapshot 目录或 zip。

### P1：Policy Backup

备份 RootTools 自己掌握的状态：

- enabled/frozen；
- standby bucket；
- AppOps；
- runtime permission（只备份可恢复项）；
- battery optimization；
- component rules；
- network policy；
- profile membership。

### P2：Full App Data Backup

不在本模块直接实现，先由 `16-industry-root-capability-map.md` 中的 Backup & Recovery 领域单独规划。

原因：完整 app data restore 涉及：

- 多 user；
- CE / DE data；
- owner / mode；
- SELinux context；
- KeyStore；
- account / service state；
- external data；
- system app；
- version / signature compatibility。

这不是“tar 一下 `/data/data`”可以安全完成的功能。

---

## 13. 数据模型

建议新增但仍放在单 `app` module 内。

```text
model/
├── AppInventoryModels.kt
├── AppDetailModels.kt
├── AppComponentModels.kt
├── AppPermissionModels.kt
├── AppPolicyModels.kt
└── AppBackupModels.kt

data/
├── AppInventoryRepository.kt
├── AppDetailRepository.kt
├── AppComponentRepository.kt
├── AppUsageRepository.kt
└── AppExportStore.kt

policy/
├── PackagePolicyController.kt      # existing, extend
├── ComponentPolicyController.kt
├── AppOpsPolicyController.kt
├── PermissionPolicyController.kt
├── AppProfileController.kt
└── DebloatController.kt
```

不要创建：

```text
:feature-app-manager
:feature-component-manager
:feature-permission-manager
```

当前工程规模没有收益。

---

## 14. Snapshot 模型

```kotlin
data class AppInventoryRecord(
    val packageName: String,
    val label: String,
    val uid: Int,
    val userId: Int,
    val isSystem: Boolean,
    val enabledState: PackageEnabledState,
    val isRunning: Boolean,
    val isStopped: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int,
    val installerPackage: String?,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val bootReceiverCount: Int,
    val trackerCount: Int?,
    val riskFlags: Set<AppRiskFlag>,
)
```

详情页：

```kotlin
data class AppDetailSnapshot(
    val summary: AppSummary,
    val runtime: AppRuntimeSnapshot,
    val components: AppComponentSnapshot,
    val permissions: PermissionSnapshot,
    val appOps: AppOpsSnapshot,
    val storage: AppStorageSnapshot,
    val signing: SigningSnapshot,
    val code: AppCodeSnapshot,
    val policy: AppPolicySnapshot,
    val sourceStatus: Map<AppDetailSection, DataSourceStatus>,
)
```

必须显式携带 source / capability status，避免某个 section 失败后 UI 假装“没有数据”。

---

## 15. 数据源与复用矩阵

| 信息/动作 | 首选数据源 | fallback | 复用现有能力 |
|---|---|---|---|
| App inventory | Android PackageManager | Shizuku PackageManager | 新增 repository |
| package enabled | Shizuku/Sui | RootShell `pm` | `PackagePolicyController` |
| force-stop | Shizuku/Sui ActivityManager | RootShell `am` | `PackagePolicyController` |
| standby bucket | Framework / Shizuku | RootShell `am standby` | existing |
| background AppOps | Shizuku AppOps | RootShell `appops` | existing |
| process/runtime | `/proc` + framework | root dumpsys | `DiagnosticsRepository` |
| startup reasons | event log | - | `StartupRepository` |
| components | PackageManager / Shizuku | package dump | new component repository |
| APK/data paths | PackageManager | Root FS | new detail repository |
| signing | PackageManager SigningInfo | APK parser | new detail repository |
| action audit | `RootActionAuditStore` | - | existing |
| privilege route | `PrivilegeRouter` | RootShell | `13-shizuku-sui-bridge.md` |

核心原则：**App Control Center 不再自己实现一份 startup / process / package shell。**

---

## 16. Action Plan / 事务模型

所有多步写操作走：

```text
Request
  ↓
Preflight
  ↓
Resolve capability/backend
  ↓
Read current state
  ↓
Generate ActionPlan + Risk
  ↓
Confirm
  ↓
Execute step
  ↓
Verify step
  ↓
Audit
  ↓
Rollback metadata
```

### ActionPlan 示例

```text
Profile: Clean Boot

WeChat
  standby active -> rare
  background RUN_ANY_IN_BACKGROUND allow -> ignore

iQIYI
  enabled -> disabled-user

Tailscale
  Protected -> SKIP
```

必须在执行前就告诉用户：

- 什么会改；
- 什么不会改；
- 什么无法改；
- 哪些可以恢复。

---

## 17. 风险等级

### R0 — Read-only

- metadata；
- components；
- permissions；
- signing；
- libs；
- usage；
- runtime；
- paths。

直接执行。

### R1 — Reversible

- force-stop；
- standby bucket；
- background AppOp；
- battery optimization；
- freeze/enable user app；
- component disable / restore。

单 App 可确认一次；Batch 必须 preview。

### R2 — Destructive but recoverable

- uninstall-for-user system app；
- clear cache；
- suspend / hide；
- IFW rule；
- large batch profile。

明确确认 + rollback 提示。

### R3 — Data destructive / critical

- clear data；
- uninstall user app；
- system critical component 修改；
- shared prefs/database write；
- system file edit。

二次确认，默认不提供 batch。

---

## 18. 性能预算

应用管理器很容易成为“扫描器自己拖慢系统”，需要明确预算。

### 列表首屏

首次只读取：

- label / icon；
- package；
- version；
- system/user；
- enabled；
- targetSdk；
- install/update time。

不要首屏对全部 300 个 App 同时跑：

- dumpsys package；
- appops；
- tracker class scan；
- APK zip scan；
- usage stats aggregation；
- native library ELF parse。

### Lazy enrichment

```text
Visible rows
→ boot count / runtime state
→ tracker count cache
→ size / usage
```

### 详情页

按 Tab lazy load；离开页面取消未完成任务。

### Scanner

- 只在用户触发或后台充电条件下运行；
- 支持增量：package version 未变化不重扫；
- scan cache 以 `package + versionCode + signingDigest` 为 key。

---

## 19. UI 原则

截图的信息密度很高，适合专家，但 RootTools 不应原样复刻。

建议：

1. 默认模式：语义化、低噪音；
2. Expert mode：显示 raw op id、component fully-qualified class、path、signature digest；
3. 危险项永远不靠颜色区分，必须有文字；
4. 长 package/class name 可复制；
5. 列表单行尽量不超过 4 行；
6. 横向 Tab 最多 8 个一级组；
7. Filter 使用可保存 preset；
8. 多选批量操作使用底部 action bar；
9. 每次写操作完成后立即刷新对应 section，而不是整页重扫。

---

## 20. 实施阶段

所有阶段必须遵循 `14-core-logic-testing-standard.md`：先定义 contract / pure policy / validator 与 JVM 单元测试，再接 privileged adapter、Controller 和 UI；install/uninstall、clear data 等非幂等动作禁止无条件自动 fallback。

## Phase A0 — Inventory Foundation

- [x] `ToolId.APPS` 文案从“应用冻结”升级为“应用控制”；
- [x] `AppControlRepository` + `AppInventorySnapshot`；
- [x] user / system / enabled / running / frozen 状态；
- [x] 搜索；
- [x] P0 filter/sort；
- [x] Compose lazy list；
- [x] App Detail route；
- [x] 复用现有 `PackagePolicyController`，不破坏 freeze / enable / force-stop / standby / background 入口。
- [x] Inventory 首屏只读取列表必要元数据；installer / signing / path / AppOps 等按 Detail/Tab 懒加载，避免 300+ package 的 Binder/shell storm；

### 完成定义

- 300+ packages 列表可流畅滚动；
- 首屏不产生 root shell storm；
- package metadata 与 `pm list packages / dumpsys package` 抽样一致。

## Phase A1 — Detail Inspector

- [x] Overview；
- [x] APK / split / data / device-protected / credential-protected path；
- [x] target / min / compile SDK；
- [x] installer；
- [x] signing SHA-256；
- [x] native library path + shared library summary；
- [x] system/user/debuggable/persistent/largeHeap/backup/cleartext flags；
- [x] `sourceStatus`；
- [x] export package diagnostic JSON/Markdown。

## Phase A2 — Component Manager

- [x] Activities / Services / Receivers / Providers；
- [x] component search/filter；
- [x] exported / permission / boot / foreground-service / direct-boot tags；
- [x] launch Activity；
- [x] component enable / disable；
- [x] `ComponentPolicyController`；
- [x] protected component gate；
- [x] before/after verify；
- [ ] Samsung 真机抽样验证。

依赖：`13-shizuku-sui-bridge.md` Phase S3/S4。

## Phase A3 — Permission / AppOps

- [x] requested/runtime permission read；
- [x] P0 AppOps raw result + semantic mode；
- [x] grant/revoke dangerous runtime permission；
- [x] set AppOp mode（typed whitelist）；
- [x] special access deep links；
- [x] capability probe；
- [x] AppOps / Component audit + rollback metadata。

## Phase A4 — Runtime / Background

- [ ] process / service 聚合；
- [ ] startup reason；
- [ ] BOOT receiver 关联；
- [x] force-stop；
- [x] standby bucket；
- [ ] battery optimization；
- [x] background AppOps；
- [ ] 与 `05-startup-background.md` UI 合并入口，避免两套治理页面重复。

## Phase A5 — Batch / Profile

- [ ] multi-select；
- [ ] `ActionPlan`；
- [ ] diff preview；
- [ ] transaction id；
- [ ] batch verify；
- [ ] rollback metadata；
- [ ] AppPolicyProfile；
- [ ] built-in templates；
- [ ] import/export profile；
- [ ] Automation API 只调用 profile id，不接受 shell。

## Phase A6 — Export / Debloater

- [ ] export base + splits；
- [ ] policy backup；
- [ ] app list export；
- [ ] system app uninstall-for-user；
- [ ] `install-existing` restore；
- [ ] debloat risk classification；
- [ ] export-before-debloat；
- [ ] Samsung One UI safe-list。

## Phase A7 — Tracker / Library Scanner

- [ ] scanner cache；
- [ ] library fingerprint；
- [ ] tracker rule format；
- [ ] tracker data source/license review；
- [ ] tracker match detail；
- [ ] report-only default；
- [ ] component block integration；
- [ ] scan performance benchmark。

## Phase A8 — Expert Root Inspector

- [ ] raw manifest；
- [ ] APK file tree；
- [ ] shared prefs read-only；
- [ ] DB read-only；
- [ ] IFW proof-of-concept；
- [ ] IFW rollback / reboot behavior test；
- [ ] advanced component rule import/export；
- [ ] no generic root terminal dependency。

---

## 21. 测试矩阵

### 21.1 Package 类型

- 普通 user app；
- split APK；
- system app；
- updated system app；
- disabled-user app；
- uninstall-for-user system app；
- work profile / secondary user（后续有设备时）；
- debuggable APK；
- accessibility / notification listener / VPN app；
- Xposed/Zygisk related app；
- target SDK 很低的 legacy app。

### 21.2 Backend

- RootShell only；
- Shizuku Root；
- Sui Root；
- Shizuku ADB；
- none / permission denied。

### 21.3 写操作

每个 Controller 至少验证：

```text
read before
→ execute
→ read after
→ process/app behavior
→ rollback
→ read restored
```

### 21.4 Samsung SM-S908E 真机重点

- One UI system package protected list；
- freeze / enable 后 Launcher 与 package state；
- component disable 后 App 是否自行恢复；
- BOOT receiver 与 Startup trace 对应；
- Shizuku Root 与 RootShell 结果一致；
- AppOps mode 与系统行为一致；
- 100/300 package list 性能；
- tracker scan 不导致明显发热；
- multi-select 20 个 App 不产生 `su` toast storm。

---

## 22. 验收标准

1. `ToolId.APPS` 从单一“冻结工具”升级为完整 App Control Center；
2. 普通模式不暴露 raw shell；
3. 所有 package/component/AppOps 写操作经过 typed Controller；
4. Shizuku 可用时 framework 类操作优先 Shizuku，RootShell 是 fallback；
5. 所有写操作带 before / after / verify；
6. Batch 在执行前必须有 diff preview；
7. protected system package 不允许普通模式修改；
8. 清数据/卸载等 R3 操作永不一键批量执行；
9. 列表首屏不对所有 App 做 heavy scan；
10. App Detail 各 Tab lazy load；
11. component / permission / AppOps / signing 抽样结果与系统命令一致；
12. Samsung SM-S908E 完成至少 10 个代表 App 的读写回滚验收；
13. 任何 tracker 数据源进入工程前完成 license review；
14. 不复制 App Manager / Hail 等项目的 GPL 实现代码或资源；
15. 与 Startup / Diagnostics / Shizuku 复用同一真值源，不出现重复实现。

---

## 23. 行业参考

调研基线：2026-08-20。

- App Manager: https://github.com/MuntashirAkon/AppManager
- App Manager 官方文档: https://muntashir.dev/AppManager/en/
- Hail: https://github.com/aistra0528/Hail
- Shizuku: https://github.com/RikkaApps/Shizuku
- Shizuku API: https://github.com/RikkaApps/Shizuku-API
- Android ADB / package manager: https://developer.android.com/tools/adb

更完整的 Root 工具行业能力地图与 RootTools 缺口见：

`16-industry-root-capability-map.md`。
