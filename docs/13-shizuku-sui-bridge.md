# Shizuku / Sui 特权桥接规划

## 1. 当前结论

Root Tools **目前还没有真正对接 Shizuku / Sui**。

现状只是架构文档在“权限中心”里预留过 `SHIZUKU` Capability，但工程代码还没有形成可用链路：

- `ToolCapability` 目前只有 `ROOT / NOTIFICATION / MAGISK / VECTOR / NETWORK`；
- Gradle 尚未引入 `dev.rikka.shizuku:api` / `provider`；
- Manifest 尚未注册 `ShizukuProvider`；
- `DashboardUiState` 没有 Binder / Shizuku permission / backend UID 状态；
- 权限中心目前只处理 Root 与通知权限；
- 现有 Package / AppOps / Activity / Diagnostics 操作仍统一走 `RootShell`。

因此本模块不是“把 Shizuku 页面抄进 Root Tools”，而是要把 Shizuku / Sui 作为第二条**系统特权执行通道**接进现有 Controller 架构。

---

## 2. 为什么值得接

现有 `RootShell` 很适合：

- `/sys` CPU / thermal / block 节点；
- `/data/adb` Magisk / Vector 模块；
- adbd / init / root-only 文件；
- 需要真实 UID 0 Linux 权限的动作。

Shizuku / Sui 更适合 Android Framework 世界：

- PackageManager / ActivityManager / AppOps 等 Binder API；
- 组件 enable / disable；
- force-stop / package governance；
- framework service 诊断；
- 在没有传统 `su` 调用的情况下以 shell 或 root identity 执行 Java / Binder 逻辑。

两者应该互补，而不是互相替代。

```text
Root Tools UI / Tile / Automation
              │
              ▼
        Typed Controller
              │
       Privilege Router
        ┌─────┴──────────────┐
        │                    │
        ▼                    ▼
  RootShell Backend     Shizuku API Backend
  su / UID 0            Binder / UserService
        │                    │
        ▼                    ▼
 sysfs / files / adbd   Android framework services
 Magisk / modules       Package / AppOps / Activity
```

原则：**不能为了“支持 Shizuku”再造一套散落的命令执行体系。** UI、Quick Tile、Automation 继续只调用语义化 Controller。

---

## 3. 后端模型

### 3.1 Backend 类型

建议统一识别四种状态：

```kotlin
enum class PrivilegeBackendType {
    NONE,
    SHIZUKU_ADB,   // uid 2000
    SHIZUKU_ROOT,  // uid 0, Shizuku app root mode
    SUI_ROOT,      // uid 0, Magisk Sui
}
```

另外保留现有 `RootShell` 为独立能力，不把它伪装成 Shizuku backend。

状态模型建议：

```kotlin
data class ShizukuBridgeState(
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val backend: PrivilegeBackendType = PrivilegeBackendType.NONE,
    val uid: Int? = null,
    val serverVersion: Int? = null,
    val serverPatchVersion: Int? = null,
    val managerInstalled: Boolean = false,
    val suiAvailable: Boolean = false,
    val lastBinderDeathAt: Long? = null,
)
```

### 3.2 不按“有没有 Shizuku”粗暴判断功能

真正需要的是**语义 Capability**：

```text
ROOT_LINUX
ROOT_FS
SYSFS_WRITE
PACKAGE_CONTROL
COMPONENT_CONTROL
ACTIVITY_CONTROL
APP_OPS
FRAMEWORK_DIAGNOSTICS
MAGISK_CONTROL
ADBD_CONTROL
```

例如：

| 能力 | RootShell | Shizuku ADB | Shizuku Root / Sui |
|---|---:|---:|---:|
| CPU sysfs 写入 | ✅ | ❌ | ✅，但首版仍优先 RootShell |
| Magisk module | ✅ | ❌ | ✅，首版仍优先 RootShell |
| Package / Component | ✅ | ✅* | ✅ |
| force-stop | ✅ | ✅* | ✅ |
| AppOps / standby | ✅ | ✅* | ✅ |
| Android Binder service | 间接 shell | ✅ | ✅ |
| `/data/user/0/...` | ✅ | ❌ | ✅ |

`*` 代表具体能力仍受 Android 版本、shell permission 与 OEM 限制，必须运行时 probe，不能只根据 UID 猜测。

---

## 4. 工程接入方式

继续保持**单 `app` Gradle 模块**，不要为 Shizuku 再建 Android library module。

建议只增加现有 app 内部 package：

```text
com.arthur.roottools
├── privilege/
│   ├── ShizukuBridge.kt
│   ├── PrivilegeRouter.kt
│   ├── PrivilegeCapabilityResolver.kt
│   └── FrameworkServiceGateway.kt
├── model/
│   └── PrivilegeModels.kt
└── ui/
    └── existing Dashboard / Permission / ToolRegistry
```

### 4.1 Dependency

实施阶段采用当前 Maven Central 稳定 Shizuku API / provider，并统一使用一个版本常量：

```kotlin
implementation("dev.rikka.shizuku:api:<version>")
implementation("dev.rikka.shizuku:provider:<version>")
```

首版只引入官方 API / Provider，不复制 Shizuku Manager 源码，也不引入第三方 wrapper。

### 4.2 Binder 生命周期

`ShizukuBridge` 负责：

1. 监听 Binder received；
2. 监听 Binder dead；
3. 查询本 App Shizuku permission；
4. 发起 permission request；
5. 读取 backend UID，区分 ADB / ROOT；
6. 检测 Sui；
7. 输出只读 `StateFlow<ShizukuBridgeState>`。

所有 Feature 不直接监听 Shizuku Binder，避免每个页面重复注册 listener。

### 4.3 Privilege Router

不要设计成：

```text
execute("任意 shell")
```

而是按 typed gateway：

```kotlin
interface PackageOpsGateway {
    suspend fun forceStop(packageName: String): ActionResult
    suspend fun setPackageEnabled(packageName: String, enabled: Boolean): ActionResult
    suspend fun setComponentEnabled(component: ComponentName, enabled: Boolean): ActionResult
}

interface AppOpsGateway { ... }
interface ActivityOpsGateway { ... }
```

Router 根据当前 capability 选择 Shizuku / Sui 或现有 Root 实现。

这样既能复用现有 Controller，又能避免以后出现三套 `pm` / `am` / `appops` 命令。

---

## 5. 产品页面规划

### 5.1 新增首页卡片：Shizuku / Sui

建议加入 `ToolRegistry`，分类放 `SYSTEM`，首页摘要只显示最关键的信息：

```text
Shizuku / Sui
SUI · ROOT · Ready
```

或：

```text
Shizuku / Sui
Shizuku · ADB · 授权待确认
```

Badge：

- `ROOT`
- `ADB`
- `SUI`
- `OFF`
- `AUTH`

### 5.2 详情页结构

#### A. 服务状态

- Shizuku / Sui 是否可用；
- Binder alive；
- 当前 UID：0 / 2000；
- 当前模式：Root / ADB / Sui；
- server API / patch version；
- Root Tools 是否已获得 Shizuku permission；
- 最近一次 Binder disconnect。

#### B. 快速动作

- 请求 Root Tools 的 Shizuku 权限；
- 打开 Shizuku；
- 打开 Android 无线调试设置；
- 打开开发者选项；
- 检测 / 刷新 Binder；
- 一键运行 Capability Self-test。

如果设备安装 Sui，再显示：

- `Sui available`；
- 打开 Sui 管理入口（系统支持时）；
- 当前实际使用的是 Shizuku Root 还是 Sui Root。

#### C. 能力矩阵

用列表明确告诉用户当前哪些模块正在使用什么 backend：

```text
应用冻结       Shizuku ROOT
组件管理       Shizuku ROOT
AppOps         Shizuku ROOT
性能控制       RootShell
Root ADB       RootShell
Magisk 模块    RootShell
系统诊断       Hybrid
```

这比只显示“Shizuku 正在运行”更有价值。

#### D. Self-test

只做固定测试，不允许输入任意命令：

- Binder ping；
- PackageManager read；
- ActivityManager read；
- AppOps read；
- UserService bind / unbind；
- backend latency；
- permission / UID consistency。

结果可以纳入一键诊断报告。

---

## 6. 可以在 Root Tools 增加的实际功能

### P0 — Shizuku 基础接入

- Shizuku / Sui 状态卡；
- permission request；
- Root / ADB / Sui mode 识别；
- Binder 生命周期；
- Capability self-test；
- 权限中心增加 Shizuku 行；
- 诊断报告增加 Shizuku backend 信息。

### P1 — 应用治理升级

这是最值得优先迁移的一组：

- Package enable / disable；
- force-stop；
- Standby bucket；
- AppOps；
- 组件级管理：Activity / Service / Receiver / Provider；
- 开机 Receiver 筛选；
- 单组件 disable / restore；
- 批量操作前后 diff；
- 所有修改进入 `RootActionAuditStore`。

其中“组件级管理”可以明显增强现在的“启动治理 / 应用冻结”，比单纯做一个 Shizuku 状态页更有实际价值。

### P1 — 权限 / AppOps 检查器

- 查看 App 关键 runtime permission；
- 查看 AppOps mode；
- 高风险权限筛选；
- 后台运行相关 AppOps；
- 悬浮窗 / 通知 / usage / exact alarm 等状态聚合；
- 支持的项目提供语义化修改入口。

不能执行的权限必须显示“当前 backend 无权限”，不能静默 fallback 成失败 shell。

### P2 — Framework Diagnostics

- package / user / activity / process service health；
- framework service 可达性；
- 当前前台 package / task 信息；
- package state diff；
- diagnostics snapshot 中标注数据来自 `ROOT_SHELL / SHIZUKU / SUI / ANDROID_API`。

### P2 — 非 Root 降级模式

未来 Root Tools 也可以在没有 Magisk root 的 Android 11+ 设备上通过 Shizuku Wireless Debugging 提供一部分能力：

```text
可用：Package / Component / 部分 AppOps / force-stop / framework diagnostics
不可用：CPU sysfs / Magisk modules / root-only files / Root ADB 控制
```

这不是当前 Samsung root 主路径的前置条件，但架构上现在就应该允许这种降级，避免以后重构。

---

## 7. 不建议直接复制 Shizuku Manager 的功能

截图中的 Shizuku 首页还包含：

- 已授权应用列表；
- 在终端中使用 Shizuku；
- Root / Wireless Debugging 启动帮助；
- Shizuku 自身启动与重启。

Root Tools 的边界建议如下：

### 可以做

- 展示 **Root Tools 自己**是否获得 Shizuku permission；
- 打开 Shizuku Manager；
- 打开 Wireless Debugging / Developer Options；
- 展示当前 Shizuku mode / UID / Binder 状态；
- 提供固定 Capability self-test；
- 可选提供 `rish` 使用说明或复制入口。

### 第一版不要做

- 自己维护“其它 App 的 Shizuku 授权列表”；
- 模仿 Shizuku Manager 修改其它 App 的授权数据库；
- 内置无约束 root / rish 终端；
- 自己重新实现 Wireless Debugging pairing 协议；
- 为 Shizuku 再跑一个长期轮询 daemon。

Shizuku Manager 负责“Shizuku 自己的管理”，Root Tools 负责“消费 Shizuku API 并把系统能力产品化”。

---

## 8. 与现有模块的对接矩阵

| Root Tools 模块 | 首版策略 | 后续方向 |
|---|---|---|
| 权限中心 | 增加 Shizuku / Sui 状态与授权 | Capability 自动解释 |
| 应用冻结 | 保持 RootShell 可用 | 优先 Shizuku Binder，Root fallback |
| 启动治理 | 现有分析保留 | 增加 component / receiver 控制 |
| 进程诊断 | 保持 Root 数据源 | framework 数据走 Shizuku hybrid |
| 常用操作 | 不变 | Android framework action 可迁移 Shizuku |
| 性能控制 | RootShell | 继续 RootShell，避免无收益迁移 |
| Root ADB | RootShell | 继续 RootShell |
| Root 模块 | RootShell | 继续 RootShell |
| 网络诊断 | Hybrid | 必要时补 Binder service 数据 |
| 电池温控 | Root + Android API | Shizuku 只做补充，不做核心 backend |

---

## 9. 实施顺序

### Phase S1 — Bridge Foundation

- [ ] 引入 Shizuku API / provider；
- [ ] `ShizukuBridgeState`；
- [ ] Binder received / dead listener；
- [ ] permission request；
- [ ] UID / backend mode detection；
- [ ] Sui detection；
- [ ] `ToolCapability.SHIZUKU`；
- [ ] 权限中心显示状态。

### Phase S2 — 独立卡片

- [ ] `ToolId.SHIZUKU`；
- [ ] 首页动态摘要；
- [ ] Shizuku / Sui detail page；
- [ ] settings / manager quick open；
- [ ] Capability self-test；
- [ ] diagnostics export。

### Phase S3 — App Governance Migration

- [ ] `PrivilegeRouter`；
- [ ] Package typed gateway；
- [ ] Activity typed gateway；
- [ ] AppOps typed gateway；
- [ ] 现有 `PackagePolicyController` 接入 router；
- [ ] Root fallback；
- [ ] 单元测试覆盖 backend route decision。

### Phase S4 — Component Manager

- [ ] Activities / Services / Receivers / Providers 列表；
- [ ] BOOT receiver / foreground service / exported component 筛选；
- [ ] component enable / disable；
- [ ] before / after diff；
- [ ] protected package / protected component 白名单；
- [ ] Samsung 真机验收。

### Phase S5 — Non-root Degrade

- [ ] 在 Root 不可用、Shizuku ADB 可用时首页不整体报错；
- [ ] ToolRegistry 按 Capability 灰显真正不可用功能；
- [ ] App Governance 使用 shell identity；
- [ ] Root-only 页面明确解释为什么不可用。

---

## 10. 验收标准

1. Shizuku 未安装时 Root Tools 原有 Root 功能完全不受影响；
2. Shizuku 已运行但未授权时，只提示授权，不 crash；
3. Shizuku Binder 死亡后 UI 能在数秒内变成 OFF / reconnecting，不继续执行旧 binder；
4. UID=2000 与 UID=0 必须正确区分，不能把 ADB 权限显示成 ROOT；
5. Shizuku / Sui 操作仍经过现有 Controller + Audit，不从 UI 直接调用；
6. Package 操作在 Shizuku 可用时走 Shizuku，失败时根据策略安全 fallback；
7. sysfs / Magisk / adbd 等 root-only 功能不为了“统一”而强行迁移；
8. 不新增常驻高频服务，不引入 Binder polling busy-loop；
9. Samsung SM-S908E 上完成 Shizuku Root 模式真机验证；
10. 后续若安装 Sui，使用同一 API 链路完成 Sui Root 验证。

