# Root Tools 产品总规划

## 1. 产品定位

Root Tools 是个人 Android Root 设备的 **系统控制与诊断启动台**。主要服务四类场景：

1. **日常稳定使用**：既流畅又不过热，控制后台和功耗。
2. **远程设备控制**：5G / Wi-Fi 环境下通过 Tailscale + Root ADB 长期可达。
3. **自动化测试设备**：为 AiBox RootLab、Appium、MacroDroid、GKD 等提供可靠基础环境。
4. **Root 实验与诊断**：快速查看 CPU、内存、温度、进程、服务、模块和系统状态。

产品目标不是替代 Scene、ApexTuner、App Manager、Hail、Magisk、Vector 的全部能力，而是把个人最常用、最需要统一治理的能力收敛到一个可靠入口。

---

## 2. 功能地图

```text
Root Tools
├── Dashboard / 设备看板
│   ├── CPU
│   ├── Memory / ZRAM / PSI
│   ├── Thermal
│   ├── Battery
│   ├── Load / IO
│   └── Remote connectivity
│
├── Performance / 性能控制
│   ├── Auto
│   ├── Cool
│   ├── Performance
│   ├── Thermal stage
│   └── Policy history
│
├── Connectivity / 远程连接
│   ├── Root ADB TCP
│   ├── Android Native Wireless Debugging
│   ├── ADB endpoints / pairing / boot persistence
│   ├── Tailscale
│   ├── interfaces / routes
│   └── open ports
│
├── Startup / 启动治理
│   ├── BOOT_COMPLETED
│   ├── Services / Providers / Jobs
│   ├── Standby bucket
│   ├── AppOps
│   └── startup benchmark
│
├── Apps / 应用治理
│   ├── App inventory / search / filter
│   ├── App detail inspector
│   ├── Activity / Service / Receiver / Provider
│   ├── AppOps / Runtime Permissions
│   ├── Freeze / Enable / Force-stop
│   ├── Background / Standby / Battery policy
│   ├── Batch / Profile / Debloat
│   ├── APK / policy export
│   └── Tracker / library scanner
│
├── Diagnostics / 系统诊断
│   ├── Processes
│   ├── Root shell
│   ├── WakeLock
│   ├── Services
│   ├── Logcat
│   └── one-click snapshot
│
├── Integrity / 环境完整性
│   ├── App self integrity
│   ├── Root / Hook / runtime signals
│   ├── Boot / ROM / SELinux
│   ├── Device surface consistency
│   ├── Sandbox / virtualization
│   ├── Attestation
│   └── Expected baseline / drift
│
├── Modules / Root 模块
│   ├── Magisk modules
│   ├── Vector / Xposed modules
│   ├── Scope
│   └── reboot-required state
│
├── Privilege Bridge / 特权桥接
│   ├── Shizuku / Sui status
│   ├── Binder / permission / backend UID
│   ├── capability self-test
│   └── framework backend routing
│
└── Actions / 常用操作
    ├── restart adbd
    ├── restart SystemUI
    ├── reboot / recovery / bootloader
    ├── open developer settings
    ├── package actions
    └── quick tiles / automation intents
```

---

## 3. 一级导航与首页规划

2026-08-24 起，产品从“首页平铺所有工具卡片”收敛为 5 个长期稳定一级领域：

```text
首页 / 应用 / 设备 / 诊断 / 系统
```

详细导航、multiple back stacks、首页 Health Verdict / Attention / Timeline 契约见 `23-product-navigation-and-home.md`。

首页不再承担全量工具目录职责，而只保留：

1. 当前设备结论；
2. 4 个高频 Quick Actions；
3. 需要关注的问题；
4. 最近策略/Root 操作事件。

以下历史卡片规划继续作为 Feature 能力清单，不再代表首页最终视觉布局。

### 历史卡片规划

### P0：必须稳定可用

| 卡片 | 首页摘要 | 点击后 |
|---|---|---|
| 设备看板 | AP 温度、CPU、内存、Thermal | 完整实时监控 |
| 性能控制 | Auto/Cool/Performance + 热状态 | CPU 策略与频率详情 |
| Root ADB / ADB Control Center | Root TCP / Native Wireless + 推荐 endpoint | ADB transport、连接数据、配对、启动恢复、安全诊断 |
| 权限中心 | Root / 通知 / 必要权限 | 权限状态与申请 |

### P1：日常收益最高

| 卡片 | 首页摘要 | 点击后 |
|---|---|---|
| 启动治理 | 最近启动耗时、异常启动 App 数 | 开机事件、服务、Receiver 排名 |
| 应用控制 | Running / Frozen / Risk | Package、组件、AppOps、权限、运行态、批量策略 |
| 电池与温控 | 电量、充电、Skin、保护状态 | 电池保护、温控历史、充电状态 |
| 进程诊断 | Top CPU / 高耗电异常 | Process / root shell / wakelock |

### P2：Root 深度管理

| 卡片 | 首页摘要 | 点击后 |
|---|---|---|
| Root 模块 | Magisk / Vector 模块数 | 模块 enable/disable、scope、需重启 |
| 网络诊断 | 5G/Wi-Fi/Tailscale | route、DNS、端口、延迟 |
| 存储与 IO | 可用空间 / IO pressure | 文件系统、IO、缓存、存储健康 |
| 常用操作 | 收藏动作数量 | SystemUI、adbd、reboot 等动作 |
| Shizuku / Sui | ROOT / ADB / SUI / OFF | Binder、权限、Backend、Capability 自检 |
| 环境完整性 | Expected Root / Hook / Drift | 自身完整性、Root/Hook、Boot/ROM、设备表面、Sandbox、可信基线 |

---

## 4. 设备看板设计

首页看板只显示 5 个最重要指标：

```text
AP Temperature
Skin Temperature
CPU load / top cluster frequency
Memory available
Thermal status
```

详情页分为：

### CPU

- 每簇 current / min / max / hardware max
- 每簇 utilization
- governor
- cpuset / uclamp 摘要
- 最近 5 / 30 分钟频率分布
- Top CPU processes

### Memory

- MemTotal / MemAvailable
- Cached / Anon / Slab
- ZRAM total / used / compressed ratio
- Swap usage
- memory PSI `some/full`
- Top RSS / PSS processes
- LMKD / memory pressure 状态

### Thermal

- AP / Skin / Battery / USB / PATHM
- Android Thermal Status
- 最近 30 分钟温度曲线
- 热状态变化时间线
- 哪个进程在升温期间占 CPU

### System load

- load average
- CPU idle ratio
- IO PSI
- process count
- uptime

---

## 5. 常见操作卡片

建议把常用动作分成不同风险等级。

### 低风险：单击执行

- 打开开发者选项
- 打开 Magisk
- 打开 Vector
- 打开 Hail
- 复制 Tailscale ADB 命令
- 刷新设备状态
- restart adbd

### 中风险：需要二次确认

- restart SystemUI
- force-stop 某 App
- enable / disable package
- 清理异常后台 Service
- 切换 CPU Performance 模式

### 高风险：明确确认 + 倒计时

- reboot
- reboot recovery
- reboot bootloader/download mode
- 修改长期启动配置
- 修改 Magisk module enabled state

高风险操作不得出现在首页一击即执行的位置。

---

## 6. 性能与采样预算

Root Tools 本身必须满足：

- 首页后台采样：30 秒一次
- 看板前台实时页：2 秒一次，可配置 1 / 2 / 5 秒
- 实时页退到后台后自动回 30 秒
- 默认不常驻 `top` / `dumpsys` 循环
- 进程列表只在用户打开时读取
- Logcat 默认不持续抓取
- 历史数据按环形缓存保存，默认只保留 24 小时轻量指标

特别禁止出现 ApexTuner 本次类似的高频 shell busy-loop。

---

## 7. 优先级

### Milestone A — 工具箱骨架

- [x] 卡片式首页
- [x] 权限中心
- [x] 性能控制
- [x] Root ADB
- [x] Quick Settings Tile
- [x] 工具卡片统一注册模型

### Milestone B — 设备看板

- [x] CPU Monitor
- [x] Memory / ZRAM Monitor
- [x] Thermal / Battery Monitor
- [x] 轻量时间序列
- [x] Dashboard 卡片与详情页

### Milestone C — 启动与后台治理

- [x] Startup trace
- [x] App startup ranking
- [x] Freeze / Enable
- [x] Standby bucket / AppOps
- [x] 自动化基础设施白名单

### Milestone D — Root 诊断中心

- [x] Top processes
- [x] WakeLocks
- [x] Running services
- [x] root shell 归属分析
- [x] 一键诊断快照

### Milestone E — 模块与自动化

- [x] Magisk / Vector module center
- [x] Quick action favorites
- [x] MacroDroid Intent API
- [x] ADB CLI API（显式 Broadcast，由 `adb shell am broadcast -n ...` 调用）
- [x] 导出诊断报告

### Milestone F — 网络诊断

- [x] interfaces / transports / Tailscale
- [x] routes / DNS
- [x] listening TCP ports
- [x] manual one-shot ping

### Milestone G — 存储与 IO

- [x] filesystem capacity
- [x] IO PSI
- [x] physical block statistics
- [x] Storage 卡片与详情页

### Milestone H — 电池与温控

- [x] 独立 Battery / Thermal 页面
- [x] Samsung 80% Battery Protection
- [x] recent thermal range
- [x] Performance policy relation

### Milestone J — Shizuku / Sui 特权桥接

- [x] Shizuku API / provider 接入
- [x] Binder lifecycle + permission + UID/mode detection
- [x] Sui detection
- [x] Shizuku / Sui 独立卡片与权限中心状态
- [x] Capability self-test（只读）
- [x] Package / Activity / AppOps typed gateway
- [x] `PackagePolicyController` 接入 Privilege Router
- [x] Component Manager（Activity / Service / Receiver / Provider）
- [x] Non-root Framework catalog + `FRAMEWORK_PRIVILEGE` 降级路径
- [x] JVM routing / validator / package / component / self-test protocol 覆盖
- [ ] Shizuku Root Samsung typed UserService + component write/rollback 最终验收
- [ ] Sui Root 可选验收
- [ ] 非 Root + Shizuku ADB 真机专项验收

详细方案见 [13-shizuku-sui-bridge.md](./13-shizuku-sui-bridge.md)。

### Milestone K — ADB Control Center

- [x] 参考 WADBS 完成产品能力拆解与许可证边界确认
- [x] 明确 Root TCP 5555 / Android Native Wireless / USB Debugging 三类状态分离
- [ ] `AdbSnapshot / AdbRepository / AdbController` 收口
- [ ] Root TCP + Native Wireless 双 transport 详情页
- [ ] Tailscale / LAN / Native TLS 多 endpoint 与 Copy / Share
- [ ] Native Wireless capability probe + 系统 pairing 快捷入口
- [ ] Boot Policy + reboot 后 Root TCP 自动恢复
- [ ] Tailscale + Root ADB post-boot 真实远程重连
- [x] Root Tailscale 产品/安全边界：Android VPN 槽位留给 Hiddify，Tailscale 改走 Root `tailscale0`
- [x] Root Tailscale typed Controller / 状态探针 / 固定版本哈希安装器 / 独立详情页
- [ ] Root Tailscale + Hiddify 同时在线真机验收
- [ ] Root Tailscale + Root ADB reboot 后真实远程重连验收
- [ ] Launcher Widget（事件驱动，不做 1 秒 Root polling）
- [ ] Samsung SM-S908E 完整真机验收

详细方案见 [04-adb-network.md](./04-adb-network.md) 与 [28-root-tailscale-coexistence.md](./28-root-tailscale-coexistence.md)。

### Milestone L — App Control Center

截图与行业调研确认：现有 `ToolId.APPS` 不应继续停留在“应用冻结”层级，而应升级成完整 App Control Center。

- [x] 完成截图能力拆解：应用列表、详情、组件、AppOps、权限、共享库、批量操作、Debloat
- [x] 对照 App Manager / Hail / Shizuku 等官方资料完成产品边界调研
- [x] 明确不新增 Gradle module，继续复用现有 `PackagePolicyController / StartupRepository / DiagnosticsRepository`
- [x] 明确 Shizuku/Sui 负责 framework typed operations，RootShell 负责 root FS / IFW 等 root-only 能力
- [ ] `ToolId.APPS` 文案升级为“应用控制”
- [ ] App inventory + search/filter/sort
- [ ] App Detail：Overview / Components / Ops / Permissions / Runtime / Storage / Code & Signing / Policy & Backup
- [ ] Activity / Service / Receiver / Provider 组件管理
- [ ] AppOps / Runtime Permission typed gateway
- [ ] Batch `ActionPlan` + diff preview + verify + rollback metadata
- [ ] Profile / 一键策略
- [ ] APK export + policy backup
- [ ] Debloater：disable-user / uninstall-for-user / install-existing restore
- [ ] tracker / library scanner（完成 license review 后）
- [ ] Samsung SM-S908E 10 个代表 App 的读写回滚验收

详细方案见 [15-app-control-center.md](./15-app-control-center.md)。

### Milestone M — Root 工具行业缺口扩展

行业调研后确认后续最值得补齐的 4 个一级方向：

1. **Backup & Recovery**：APK、policy、app private data、module/config、加密与 restore verify；
2. **Firewall & App Network**：per-app Wi-Fi/mobile/VPN/LAN、netfilter、profile、日志、boot restore；
3. **Root Runtime Center**：Magisk / KernelSU / APatch 统一探测与 module provider；
4. **Charge Controller**：在 Battery/Thermal 上增加 device-specific charging capability probe 与安全控制。

- [x] 完成行业能力地图与产品边界
- [ ] `17-backup-recovery.md`
- [ ] `18-firewall-network-policy.md`
- [ ] 扩展 `07-root-module-center.md` 为 multi-root Runtime 方案
- [ ] 扩展 `11-battery-thermal.md` 的 Charge Controller 方案

行业调研与完整能力地图见 [16-industry-root-capability-map.md](./16-industry-root-capability-map.md)。

### Milestone N — Environment Integrity Center

基于 Hunter 6.65 真机分析确认：RootTools 需要一个面向 **个人 Root / 自动化测试设备** 的环境完整性能力，但不能沿用“Root = 黑灰产设备”的传统风控模型。

核心方向是：

```text
App self integrity
    +
Android / Root / Native 多视角交叉验证
    +
Expected Root / ADB / Automation baseline
    +
Unexpected drift / Hook / tamper finding
```

- [x] 完成 Hunter 6.65 Manifest / resources / JNI / Native 检测域调研
- [x] 明确 Hunter 6.65 未找到可直接复用的官方公开源码，采用 clean-room 独立实现
- [x] 明确 `EXPECTED / INFO / WARN / CRITICAL / UNAVAILABLE` 可解释 finding 模型
- [x] 明确不新增 Gradle module，复用 Device / Diagnostics / Network / Module / Shizuku / RootShell 真值源
- [ ] `ToolId.INTEGRITY` + 独立详情页
- [ ] Fast Integrity：self identity / boot / SELinux / root runtime / TracerPid / maps / environment context
- [ ] Trusted baseline + profile + drift diff
- [ ] Device Surface：CPU / thermal / GPU / Vulkan / sensor / audio / battery shape
- [ ] Sandbox / clone path consistency
- [ ] Native Integrity：ELF runtime/file、executable maps、limited hook-surface diagnostics
- [x] Android Key Attestation：TEE / StrongBox / certificate chain / RootOfTrust / Google roots+revocation / PEM export
- [ ] trusted off-device verifier（Mac / server 重新验证 certificate chain 与 challenge）
- [ ] Samsung SM-S908E rooted / LSPosed / emulator / non-root Shizuku 降级验收

详细方案见 [19-environment-integrity-center.md](./19-environment-integrity-center.md)。

### Milestone O — Developer Runtime / Termux Bridge

Termux 不作为第二套 RootTools，也不把 arbitrary `su` terminal 暴露给 Agent。它承担 Linux userland / package / script / SSH / Python / Node / job runtime，RootTools 继续承担 Android privileged typed control plane。

2026-08-20 Samsung SM-S908E 实测当前安装的是 `googleplay.2026.06.21`，PackageManager 没有 `com.termux.RUN_COMMAND` / `RunCommandService`，所以必须按运行时 capability 选择 bridge，而不能只判断 `com.termux` 包名。

- [x] 完成 Termux 官方 RUN_COMMAND / Tasker / API / Boot / Widget / services 能力调研
- [x] Samsung 真机确认当前 Google Play Termux 无 `RUN_COMMAND` service
- [x] 明确双层定位：Termux execution plane + RootTools privileged control plane
- [ ] `TermuxRuntimeSnapshot` / distribution + capability probe
- [ ] Developer Runtime 详情页
- [ ] Termux -> RootTools `roottools` typed CLI
- [ ] Automation credential 从单 token 演进为 client + scopes
- [ ] structured result callback / requestId
- [ ] Stable Termux `OfficialRunCommandBackend`
- [ ] Managed Termux Task Registry（不开放 arbitrary command）
- [ ] optional OpenSSH / Python / Node runtime bootstrap
- [ ] Play Termux local SSH fallback feasibility
- [ ] Remote MCP / Agent bridge ADR + Tailscale-only exposure
- [ ] Samsung Termux -> RootTools round-trip 真机验收

详细方案见 [20-termux-developer-runtime.md](./20-termux-developer-runtime.md)。

### 高风险动作暂缓

`reboot / recovery / bootloader` 保留在产品规划中，但当前版本**不提供可执行入口**。原因：当前 Root ADB TCP 5555 尚未证明重启后必然自动恢复，远程触发可能导致设备失联。必须先完成可靠的 post-boot reconnect，再开放这些动作。

---

## 8. 验收标准

每个新卡片至少满足：

1. 首页状态摘要准确。
2. 独立详情页可用。
3. Root 失败有明确提示。
4. 不修改与本功能无关的系统状态。
5. 所有修改可恢复。
6. 真机至少验证 Samsung SM-S908E。
7. 不引入持续高 CPU / 高 IO 监控。
8. 关键操作提供 Quick Tile / Intent / ADB 中至少一种快速入口（适用时）。
