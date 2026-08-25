# 产品导航与首页信息架构

## 1. Problem / User Story

- 用户场景：RootTools 已经从早期 10 余张工具卡片扩展为应用治理、设备控制、诊断、系统与开发者运行时等多个长期领域。日常使用需要在任意页面 1～2 次点击触达高频能力，而不是反复“返回首页 → 找卡片 → 再进入”。
- 现有问题：`DashboardRoute` 仍使用本地 enum + `when` 管理所有页面，返回行为基本回到 HOME；首页把工具按两列卡片平铺，一级领域、当前设备结论、需要关注的问题和最近事件没有形成稳定层级。
- 成功标准：建立 5 个稳定一级 Tab、每个 Tab 保留独立导航状态、首页回答“设备现在怎么样”、领域页回答“这一类能力可以做什么”，并保留现有 Feature 的 privileged 行为与真值源。

## 2. Scope

### In scope

- 5 个一级导航领域：`首页 / 应用 / 设备 / 诊断 / 系统`。
- 手机紧凑窗口使用 Bottom Navigation；较宽窗口通过 Material 3 Adaptive 自动切换为 Navigation Rail。
- 使用 Navigation Compose 管理 Feature destination、返回栈、启动 deep-link 路由与状态恢复。
- 首页重构为：Health Verdict、Quick Actions、Attention、Recent Activity、Pinned/Domain entry。
- `应用 / 设备 / 诊断 / 系统` 各自提供轻量 landing page，而不是把现有完整 Feature 再复制一份。
- 现有工具映射到固定领域，Feature Screen 继续复用现有 Repository / Controller / ViewModel 真值源。
- 导航/首页纯决策逻辑建立 JVM tests。

### Out of scope

- 不修改 CPU、ADB、Module、Package、AppOps、Shizuku 等 privileged 语义。
- 不因为导航重构引入第二套 Repository / Controller。
- 本阶段不全面重写 App Control / Developer Runtime 内部信息架构；只建立新的一级入口和导航边界。
- 不实现用户可编辑的 Tab 数量；一级领域必须长期稳定。

## 3. Current State

### 已有真值源

- `DashboardViewModel` / `DashboardUiState`：首页与多个 Feature 当前共享状态。
- `ToolRegistry`：工具标题、图标、能力要求、分类的单一注册表。
- `DeviceHealthSnapshot` / `DeviceSnapshot`：CPU、内存、温度、电池等实时摘要。
- `CpuPolicyEventStore` + `RootActionAuditStore`：最近性能策略与特权操作事件。
- 各独立 Feature Screen：Performance、Shadow Display、Integrity、Developer Runtime、Ad Governance、Health Dashboard 等。

### 当前 UI 债务

- `DashboardScreen.kt` 同时承担首页、导航和多个 legacy screen，超过 2800 行。
- `DashboardRoute` 通过 `rememberSaveable<ToolboxRoute>` 保存单一 route；没有真正 back stack。
- 所有工具 `chunked(2)` 平铺到首页，`ToolCategory` 没有形成实际产品层级。
- 首页 hero 的状态文案与诊断数据耦合不足，不能可靠表达“需要关注什么”。
- 各页面返回行为直接回 HOME，跨领域切换效率低。

## 4. Information Architecture

```text
RootTools
│
├── 首页 Home
│   ├── Health Verdict
│   ├── Quick Actions
│   ├── Attention
│   └── Recent Activity
│
├── 应用 Apps
│   ├── App Control Center
│   ├── Startup Governance
│   ├── Ad Governance
│   ├── Permissions
│   ├── Components
│   └── AppOps
│
├── 设备 Device
│   ├── Performance
│   ├── Battery / Thermal
│   ├── Root ADB
│   ├── Shadow Display
│   ├── Network
│   └── Storage / IO
│
├── 诊断 Diagnostics
│   ├── Health Dashboard
│   ├── Process / Root Shell / WakeLock
│   └── Environment Integrity
│
└── 系统 System
    ├── Root Modules
    ├── Shizuku / Sui
    ├── Common Actions / Automation
    └── Developer Runtime
```

### 一级 Tab 原则

1. 一级导航只放长期稳定的领域，不放某个当前高频 Feature。
2. Tab 数固定为 5，避免随着功能增长不断挤压底栏。
3. 具体 Feature 由领域 landing page 和首页 Quick Actions 触达。
4. Feature 属于一个 canonical domain；可以从首页快捷跳转，但不复制状态或实现。

## 5. Navigation Architecture

```text
MainActivity
  -> RootToolsAppShell
      -> NavigationSuiteScaffold
          -> NavHost
              -> home graph
              -> apps graph
              -> device graph
              -> diagnostics graph
              -> system graph
```

### Multiple back stacks

切换一级 Tab 时：

```text
navigate(tab.route) {
  popUpTo(graph.startDestination) { saveState = true }
  launchSingleTop = true
  restoreState = true
}
```

验收行为：

```text
应用 -> App Control -> detail
诊断 -> Integrity
切回应用
=> 保留应用 graph 的 navigation/saveable state，不重新回首页。
```

### Deep link / external entry

现有 `MainActivity.EXTRA_OPEN_SCREEN` 继续只解析为 typed destination：

- `adb` -> Device / ADB
- `integrity` -> Diagnostics / Integrity

Tile / Widget / Intent 不直接修改内部 Composable state。

## 6. Home Product Contract

### 6.1 Health Verdict

首页第一屏必须是动态结论，而不是固定“设备状态良好”。优先级：

1. `Critical`：已知 abnormal root shell / 明确严重异常；
2. `Warning`：Thermal Moderate/Severe、Skin 明显偏热、CPU 持续高负载；
3. `Setup`：Root/关键能力未就绪；
4. `Good`：当前实时指标无明显异常。

Verdict 只根据已采集事实判断；没有做过深度诊断时不得声称“系统不存在任何问题”。

### 6.2 Quick Actions

首页固定 4 个高频触达：

- Performance / Cool
- Root ADB
- App Control
- Diagnostics

Quick Action 默认只导航，不把危险写操作放在首页。

### 6.3 Attention

只展示需要用户理解/处理的问题：

- abnormal root shell；
- Moderate/Severe thermal；
- 当前 CPU 使用率明显异常；
- Root 不可用；
- 其它已有状态中可以可靠判断的高优先级异常。

无问题时显示明确 Empty State。

### 6.4 Recent Activity

合并：

- CPU policy events；
- Root action audit records。

按 timestamp 排序，首页只展示最近 3～5 条；详情仍由原 Feature 维护。

## 7. Domain Landing Pages

Landing page 不是第二套 Dashboard。统一结构：

1. 领域标题 + 一句话状态；
2. 高频入口 2～4 个；
3. 其它工具列表；
4. capability unavailable 时给出可解释状态；
5. 不执行隐藏后台扫描。

页面应复用 `ToolRegistry` 和 `DashboardUiState` 生成摘要，禁止复制 Feature data collector。

## 8. Visual / Interaction System

### Navigation

- Compact phone：Material 3 bottom navigation。
- Expanded width：Material 3 adaptive Navigation Rail。
- 当前 Tab 使用明确 selected indicator；Diagnostics 可在真实 attention > 0 时显示 badge。

### Cards

- 首页 hero：一张主状态卡，不重复大量工程指标。
- Quick Actions：紧凑、可扫视，强调“动作/目的地”而不是技术说明。
- Domain Tool Row：图标 + 标题 + 1 行状态 + trailing chevron/badge。
- 高级技术字段留在 Feature Detail，不在一级 landing 暴露 policy id / raw sysfs。

### Feedback

- 导航操作不显示 loading。
- Feature 自己管理 loading / action / error。
- Bottom navigation 切换不触发额外 root collector。

## 9. Capability / Permission

本 Feature 自身不新增 privileged capability。

| Capability | Backend | Permission | Fallback | Failure UI |
|---|---|---|---|---|
| Navigation | AndroidX Compose | none | current activity | destination unavailable |
| Home summary | existing state | existing backend | last known/empty | status chip / empty state |
| Feature actions | existing typed controller | unchanged | unchanged | existing Feature UI |

## 10. Safety / Rollback

- 风险等级：低。主要为 UI/navigation refactor。
- 不修改任何系统写操作的参数与默认值。
- 原 `DashboardRoute` 迁移期间保留 Feature Screen API；导航失败可回退到 top-level landing。
- ADB deep-link 不关闭/重启 ADB，只导航到 ADB screen。
- Bottom navigation 不允许承载“关闭 Root ADB”等可能导致远程失联的动作。

## 11. Test Matrix

### JVM

- 每个 Feature destination 映射到唯一 top-level Tab；
- `adb` / `integrity` external entry 映射正确；
- Health Verdict 优先级；
- Attention count 不把正常设备误报；
- Recent Activity 合并顺序。

### Compose / build

- 默认入口为 Home；
- 5 个 Tab 均可编译并进入对应 landing；
- Feature route API 保持原有参数；
- strings 默认语言与 `zh-rCN` 完整。

### Real device — Xiaomi 14 / HyperOS

- 5 Tab 底栏在 1200×2670 显示完整，无文字裁切；
- Home 第一屏无需滚动即可看到 verdict + quick actions + bottom navigation；
- 切换 5 Tab 无明显卡顿；
- ADB / Integrity 外部入口能定位到正确 Tab/Feature；
- 进入 Performance、ADB、App Control、Diagnostics、Modules 后返回逻辑正确；
- App 退后台时不因 navigation 新增持续 CPU/root shell；
- 生成至少 Home、Apps、Device、Diagnostics、System 五张真机截图用于视觉回归。

## 12. Implementation Plan

### P0 — Contract / shell

1. 建立本设计文档与 destination/domain mapping tests；
2. 引入 Navigation Compose 与 Material 3 Adaptive Navigation Suite；
3. 建立 `RootToolsAppShell` + 5 top-level graphs + multiple back stacks；
4. 保留现有 deep-link contract。

### P1 — Home / landing

1. `HomeHealthPolicy` + tests；
2. 新 Home：Verdict / Quick Actions / Attention / Timeline；
3. Apps / Device / Diagnostics / System landing pages；
4. 复用 `ToolRegistry`，移除首页全量两列卡片作为主导航。

### P2 — Migration / validation

1. 将 MainActivity 切到新 AppShell；
2. project quality gate；
3. Xiaomi 14 覆盖安装；
4. 截图 + UI hierarchy + cold navigation smoke test；
5. 写回 validation 与 delivery ledger。

## 13. Acceptance Criteria

- [x] App 有且只有 5 个稳定一级 Tab。
- [x] Bottom navigation / Navigation Rail 由 Material 3 Adaptive Navigation Suite 根据窗口形态适配；本轮 Xiaomi 14 验收 compact Bottom Navigation。
- [x] Navigation Compose 管理真实 back stack，不再通过单个 local enum 模拟全局导航。
- [x] 每个现有工具均可从且仅从一个 canonical domain landing 找到。
- [x] 首页固定文案“设备状态良好”被动态 verdict 取代，并补齐 Loading 状态避免空数据误报。
- [x] 首页提供 4 个高频 Quick Actions。
- [x] Attention 与 Recent Activity 可用且无额外 root polling。
- [x] ADB / Integrity external entry regression 通过。
- [x] Unit / lint / assemble 通过（既有 repo debt 单独记录）。
- [x] Xiaomi 14 安装验证通过并生成五个一级页面截图。

真机证据见 `docs/validation/navigation-2026-08-24.md`。

## 14. Open Questions

- App Control 内部 detail 是否在下一阶段继续拆成 Overview / Background / Permissions / Components / Runtime，由独立 Feature navigation graph 管理。
- Developer Runtime 1328 行 legacy screen 需要后续独立重构，不与本导航迁移混在同一批 privileged 行为变化中。
