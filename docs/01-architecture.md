# Root Tools 工程架构

## 1. 总体结构

工程继续保持 **单 `app` Gradle 模块**，通过 package 分层，而不是拆成大量 Android library module。

建议逐步演进为：

```text
com.arthur.roottools
├── app / navigation
├── core
│   ├── root
│   ├── shell
│   ├── privilege
│   ├── permissions
│   ├── sampling
│   ├── history
│   └── safety
├── feature
│   ├── dashboard
│   ├── performance
│   ├── adb
│   ├── startup
│   ├── apps
│   ├── diagnostics
│   ├── battery
│   ├── network
│   ├── modules
│   └── actions
└── ui
    ├── components
    └── theme
```

目前已有代码可以逐步迁移，不需要一次性重构。

---

## 2. 核心分层

### Collector

只读系统状态，不做系统修改。

示例：

- `CpuCollector`
- `MemoryCollector`
- `ThermalCollector`
- `BatteryCollector`
- `ProcessCollector`
- `NetworkCollector`
- `StartupCollector`

### Repository

将多个 Collector 数据组合成领域 Snapshot。

```text
Collectors
   ↓
Repository
   ↓
Feature ViewModel
   ↓
Compose UI
```

### Controller / Action

唯一允许修改系统状态的一层。

例如：

- `CpuPolicyController`
- `AdbController`
- `PackagePolicyController`
- `ModuleController`
- `SystemActionController`

禁止 UI、Tile、MacroDroid Receiver 直接执行散落的 root shell 命令。

---

## 3. Shell 设计

当前 `RootShell` 已收敛为轻量 Root Command Executor，并由 App 进程级共享的持久 `su` session 承载：

```text
RootShell
├── execute()
├── executeBatch()
├── process-wide serialized su session
├── command subshell + completion marker
├── timeout / cancellation → invalidate session
└── next command lazily recreates session
```

### Root session 生命周期

Magisk 的“已授予超级用户权限”提示与**新建 `su` 进程**绑定。Dashboard、CPU 策略、Quick Tile、Automation、
Module 等能力如果各自执行 `su -c`，会造成普通刷新甚至后台轮询都反复出现授权提示，也会产生没有必要的 root
进程创建成本。

因此从 0.2.x 起采用以下约束：

1. 同一 App 进程只维护一个共享 `su` session；
2. Repository / Controller / Service / Tile 都继续依赖 `RootShell`，不直接持有或新建 `su`；
3. 命令串行执行，每条命令放进独立 subshell，`exit/cd/临时变量` 不污染长期 session；
4. 命令失败只返回 exit code，不重建 session；只有 shell 退出、transport 异常、超时或 coroutine cancellation 才失效；
5. 写操作失败后**不自动重放**，避免部分成功时重复修改系统；
6. session 仅供 App 内部 typed Repository/Controller 使用，不暴露任意 shell API、socket 或外部 daemon；
7. App 进程死亡后 session 随进程消失；下一次真正需要 root 时再重新创建，因此 Magisk 提示正常情况下只在
   新进程首次取得 root 时出现一次，而不是每次采样出现。

`stream()` 当前不提供通用公开入口：现有 Logcat / diagnostics 都采用明确的按需快照，暂无必须保持长期 root stream 的产品能力。等真正出现实时日志 Feature 时再增加受生命周期管理的 stream API，避免提前引入新的常驻进程风险。

系统写操作统一进入 `RootActionAuditStore`，记录：

- 时间
- feature
- command/action 名称
- 修改前值
- 修改后值
- exit code
- 是否可回滚

当前已覆盖 CPU cap、Root ADB、Package/Standby/AppOps、Battery Protection、Magisk/Vector module、System Actions，以及 Automation / Quick Tile 入口。审计最多保留 200 条，不显示原始危险 shell。

UI 不展示原始危险命令，默认展示语义化动作。

### Shizuku / Sui 接入后的执行边界

Shizuku / Sui 不替换 `RootShell`，而是作为 Android Framework 操作的第二条特权通道：

```text
Typed Controller
      ↓
Privilege Router
  ├── Shizuku/Sui UserService: Package / Component / Activity / AppOps
  └── RootShell fallback: framework state operations only when needed

Root-only Controller
      ↓
RootShell: sysfs / root file / Magisk / adbd
```

Feature 不直接持有 Shizuku Binder；统一由 `ShizukuBridge` 管理 Binder lifecycle、permission 与 backend UID，`ShizukuUserServiceClient` 管理 typed UserService 生命周期，再由 `PrivilegeRouter` 按语义 capability 选路。禁止重新引入公开的“任意 shell executor”。

当前路由还有两个关键约束：

1. Shizuku/Sui Ready 时先执行 Binder route，成功时**不 probe `su`**；仅在 Binder route 失败/不可用后才探测 Root fallback，避免无意义 Magisk 授权提示。
2. UserService AIDL 只包含固定语义方法；package/component/AppOp 等动态参数都通过 `PrivilegeInputValidator`，不接受外部 shell command 文本。

组件写操作额外经过 `ComponentSafetyPolicy`，Package 写操作经过 `PackageMutationPolicy`；这些 Android-free 规则由 JVM unit test 覆盖。详细设计见 `13-shizuku-sui-bridge.md` 与 `14-core-logic-testing-standard.md`。

---

## 4. 工具卡片注册模型

首页已经从手写 `listOf(ToolboxCard(...))` 收敛为统一注册模型：

```kotlin
data class ToolDefinition(
    val id: ToolId,
    val title: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val accent: Color,
    val statusProvider: ToolStatusProvider,
    val destination: Destination,
    val requiredCapabilities: Set<Capability>,
)
```

能力声明示例：

```text
performance:
  ROOT
  CPU_SYSFS

adb:
  ROOT
  ADBD

dashboard:
  ROOT_OPTIONAL

module-center:
  ROOT
  MAGISK
```

这样权限中心可以自动计算“当前有哪些卡片不可用”。

### 2026-08-19 实施拆解（Milestone I）

当前卡片数量已经超过 10 个，本阶段正式把首页从手写 `listOf(ToolboxCard(...))` 收敛为统一注册表。

静态注册信息：

- `ToolId`
- `ToolCategory`
- title
- icon / accent
- required capabilities
- implemented

动态信息不放进注册表，由 `ToolStatusResolver` 根据 ViewModel State 计算：

- subtitle
- badge
- enabled

这样 Dashboard 的 AP 温度、Startup 的 App 数、Network 的 Tailscale 状态都不会污染静态工具定义。

第一版 Capability：

- `ROOT`
- `NOTIFICATION`
- `MAGISK`
- `VECTOR`
- `NETWORK`

新增工具的最低流程：

```text
ToolRegistry 注册
→ Route 映射
→ Feature Screen
→ Controller/Repository
→ docs + ledger
```

---

## 5. 采样架构

建议只有一个 `DeviceSamplerService`，而不是 CPU、Memory、Thermal 各起一个 Service。

```text
DeviceSamplerService
├── thermal  5~30s
├── cpu      5~30s
├── memory   5~30s
├── battery  30s
└── network  30s
        ↓
Shared StateFlow<DeviceHealthSnapshot>
        ↓
Dashboard / Performance / Notification / Tile
```

采样频率：

- App 首页：30 秒
- Dashboard / Battery 详情：1 / 2 / 5 秒可选，默认 2 秒
- Top Process / PSS：最低 10 秒，不随详情基础采样频率提高
- 用户离开详情页：恢复 30 秒

禁止多个页面重复执行 `dumpsys thermalservice`。

---

## 6. 历史数据

第一阶段不用 Room 大库，先用轻量环形缓存：

```text
timestamp
apTemp
skinTemp
batteryTemp
thermalStatus
cpuLoad
memoryAvailable
swapUsed
```

当前实现：

- 详情实时环形缓存：最多 900 点
- 24h 轻量持久历史：5 分钟最多 1 点，最多 288 点
- 不保存进程/PSS/WakeLock 等重数据
- 暂不做 7 天历史，避免为个人工具箱引入数据库与额外 IO

当前 24h 轻量历史不需要 Room；只有未来出现多天查询、索引或复杂聚合时才重新评估数据库。

---

## 7. 权限中心

Capability 建议定义：

- `ROOT`
- `POST_NOTIFICATIONS`
- `USAGE_STATS`
- `ACCESSIBILITY`（如果以后真的需要）
- `NOTIFICATION_LISTENER`（仅相关工具）
- `SHIZUKU`
- `MAGISK_AVAILABLE`
- `VECTOR_AVAILABLE`
- `TAILSCALE_AVAILABLE`

规则：

1. 首次启动自动请求最低必要权限。
2. 非当前功能需要的权限不提前申请。
3. Android / Magisk 必须用户确认的权限不绕过。
4. 权限失败不会导致整个工具箱不可打开。

---

## 8. Quick Settings / Automation API

所有快速入口调用统一 Controller：

```text
Quick Tile ─┐
MacroDroid ─┼→ RootTools Action Router → Controller
ADB Intent ─┤
UI ─────────┘
```

当前已经提供受控、显式组件 Intent：

```text
com.arthur.roottools.action.SET_PERFORMANCE_MODE
com.arthur.roottools.action.SET_ADB
com.arthur.roottools.action.RUN_DIAGNOSTIC
com.arthur.roottools.action.FREEZE_APP
```

Automation Receiver 必须显式指定 component，并校验应用私有随机 token；不接受任意 shell 字符串。涉及危险动作仍保留 UI 二次确认边界。
