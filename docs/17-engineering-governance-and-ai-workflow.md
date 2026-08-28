# RootTools 工程治理与 AI 协作规范

> 状态：Baseline v1
>
> 目标：让 RootTools 在持续增加 Root / Shizuku / 系统管理能力时，代码结构、测试、安全、多语言、提交和 AI 工作方式能够同步演进，而不是功能越多越难维护。

---

## 1. 本次工程审计结论

截至 2026-08-20，当前工程具有以下基础：

- Gradle 仅有 `:app` 一个模块；
- 主 Kotlin 代码约 65 个文件、约 9.7k 行；
- JVM unit test 10 个文件、约 565 行；
- 已有 RootShell、PrivilegeRouter、Controller / Repository、安全契约和真机验收文档；
- 已有产品路线图、领域设计、交付账本和 Shizuku / App Control 规划；
- Git 已初始化并连接 GitHub；
- `references/` 已加入忽略，不把调研工程混入产品源码。

这些说明项目**不是缺架构思想**，真正缺的是“工程治理层”。当前主要缺口如下。

### 1.1 P0 缺口

| 领域 | 当前状态 | 风险 |
|---|---|---|
| UI 分层 | `DashboardScreen.kt` 约 3460 行 | AI 并行修改冲突、回归范围过大、Feature 边界模糊 |
| ViewModel 分层 | `DashboardViewModel.kt` 约 880 行 | 状态与行为不断集中，后续难测、难拆 |
| 多语言 | `strings.xml` 仅 2 个字符串；大量 UI 文案硬编码 | 无法可靠中英文切换，AI 很容易继续复制硬编码 |
| AI 项目指令 | 原来没有项目级 `AGENTS.md` | 每次 AI 都要重新推断规范，容易产生不一致实现 |
| CI | 没有 GitHub Actions 质量门禁 | 本地能跑不等于提交可回归 |
| 格式/静态分析 | 只有 Android lint，没有统一 formatter / complexity gate | 风格漂移、超大函数/文件继续增长 |
| 覆盖率 | 有单测但无覆盖率基线 | “增加了测试”无法量化核心逻辑是否逐步变安全 |
| 依赖治理 | 依赖版本直接写在 `app/build.gradle.kts` | AI 升级依赖时容易产生版本漂移 |
| 提交规范 | Git 历史已有 `test/chore` 风格，但无正式规范 | scope、提交粒度和 release note 难自动化 |

### 1.2 P1 缺口

- 缺统一 Compose 组件库和 Design Token；
- 缺导航/Feature API 约束；
- 缺错误模型与 Action Result UI 规范；
- 缺 Accessibility 规则；
- 缺 ADR（Architecture Decision Record）机制；
- 缺依赖方向自动检查；
- 缺测试金字塔与 device matrix；
- 缺性能基线：启动、首屏、刷新、后台采样成本；
- 缺版本发布、Changelog、Release checklist；
- 缺安全审查清单：exported component、Intent 输入、root command injection、敏感日志、token。

---

## 2. 模块化：现在应该怎么做

### 2.1 结论

**需要模块化，但第一阶段应先做逻辑模块化，不应立即把 12+ 张工具卡片拆成 12+ 个 Gradle module。**

当前代码量不到 10k 行，真正的热点是巨型文件和 feature 边界没有落实。直接 Gradle 多模块化会新增：

- build.gradle 维护成本；
- API / implementation 暴露管理；
- Compose / Android resource 配置重复；
- 测试 fixture 和 dependency graph 复杂度；
- AI 为简单改动跨多个 module 修改的成本。

因此先解决“文件与依赖边界”，再解决“编译边界”。

### 2.2 Stage A：立即执行的逻辑模块化

目标结构：

```text
com.arthur.roottools
├── app
│   ├── RootToolsApp
│   ├── navigation
│   └── entrypoint
├── core
│   ├── common
│   ├── model
│   ├── privilege
│   ├── root
│   ├── safety
│   └── ui
│       ├── component
│       ├── theme
│       └── token
└── feature
    ├── dashboard
    ├── performance
    ├── adb
    ├── startup
    ├── apps
    ├── diagnostics
    ├── modules
    ├── actions
    ├── network
    ├── storage
    └── battery
```

第一轮不要求移动所有既有文件。优先做到：

1. `DashboardScreen.kt` 不再新增新的业务页面；
2. 按 Feature 抽离 Screen / Section / Dialog；
3. `DashboardViewModel` 抽离 Feature State 与 Coordinator；
4. 共用 UI 放到 `core/ui`；
5. 共享纯模型放 `core/model`；
6. 权限与特权路由继续保持唯一入口。

### 2.3 Stage B：有限 Gradle Core 模块

当满足以下任意两个条件时，开始 Gradle core extraction：

- 主代码超过约 15k～20k LOC；
- clean build / CI 反馈明显变慢；
- 两个以上 AI / 开发任务经常并行修改同一区域；
- 核心能力需要被多个 Feature 或未来 companion app 复用；
- 需要用 module visibility 强制依赖方向；
- core 已稳定且 2～3 个迭代没有频繁改 API。

推荐首批最多 3 个：

```text
:app
:core:model
:core:privilege
:core:designsystem
```

其中：

- `:core:model`：Android-free / 尽量 Kotlin-only 的模型、结果、policy；
- `:core:privilege`：RootShell、Shizuku/Sui、PrivilegeRouter、typed gateway；
- `:core:designsystem`：Theme、Token、复用 Compose 组件、共享 drawable/string（若必要）。

**暂时不要建立 `:core:utils` 大杂烩。** 公共代码必须按语义归属。

### 2.4 Stage C：Feature module 的准入条件

某个 Feature 满足下列任意一项再考虑独立 module：

- 3 个以上页面；
- 超过约 1200～1500 LOC 且仍持续增长；
- 有独立 data/controller/test 生命周期；
- 经常与其它 Feature 并行开发；
- 可以定义稳定的 navigation contract；
- 对其它 Feature 只暴露极少 API。

优先候选：

1. `apps` / App Control Center；
2. `adb`；
3. `diagnostics`。

不要按“一个 Repository 一个 module”“一张卡片一个 module”拆分。

### 2.5 导航边界

当前 `DashboardRoute` 使用本地 enum + `when` 管理约 16 个 route。这个方式在早期足够简单，但随着 Widget / Quick Tile / Intent deep link、App Control 子页面、详情页层级增加，会逐渐出现：

- back stack 只能手工维护；
- deep link 映射散落；
- Feature 自己无法声明 destination；
- `DashboardScreen.kt` 被迫继续知道所有页面。

2026-08-24 已接受迁移到 Navigation Compose + Material 3 Adaptive Navigation Suite。迁移仍不改变 Feature privileged 行为。目标是：

```text
App NavHost
  -> 5 top-level domain graphs
  -> feature destination contract
  -> feature route/screen
```

外部 Intent / Tile / Widget 只解析成 typed destination，不直接操作页面内部状态。

一级领域固定为 `Home / Apps / Device / Diagnostics / System`。Tab 切换使用 save/restore state 保留 multiple back stacks；紧凑手机使用 bottom navigation，展开窗口使用 navigation rail。完整产品契约见 `23-product-navigation-and-home.md`。

### 2.6 依赖装配 / DI

当前 `DashboardViewModel` 直接构造约 30 个 Repository / Store / Controller / Backend 依赖。继续增长后会导致：

- ViewModel 同时承担 composition root；
- 测试很难替换 shell / privilege backend；
- Feature 无法只持有自己的依赖；
- 生命周期和共享实例边界不明显。

第一阶段**不必立即引入 Hilt**。先建立显式 `AppContainer` / feature factory：

```text
Application
  -> AppContainer
      -> RootShell (process singleton)
      -> PrivilegeRouter
      -> AuditStore
      -> feature repositories/controllers
          -> Feature ViewModel Factory
```

只有当 Gradle feature module 增多、对象图显著复杂、需要 scoped binding / multibinding 时，再通过 ADR 决定是否引入 Hilt/Koin 等 DI 框架。不要为了“行业常见”提前增加框架成本。

---

## 3. 公共库应该怎么建

### 3.1 `core/common` 不是垃圾桶

允许进入公共层的代码必须满足：

- 至少有 2 个真实调用方；
- 语义稳定；
- 不依赖某个具体 Feature 的 UI 文案或业务状态；
- 能清晰命名；
- 有单测价值。

候选内容：

- typed result / error；
- time / size / frequency formatter；
- coroutine dispatchers abstraction（只有测试需要时）；
- parsing primitives；
- package/component validation；
- safe file / bounded history primitives；
- capability model。

不允许：

- `Utils.kt`；
- 各种 Feature 的 extension 全塞一起；
- 只被一个页面调用的包装函数；
- 将 shell command 以“通用 executor”名义重新暴露。

### 3.2 数据模型分类

```text
System DTO / raw parser result
        ↓
Domain Model
        ↓
UI State
```

三者不要混成一个 data class。

- raw result 允许贴近系统命令 / Binder；
- domain model 不暴露 shell 文本；
- UI state 可以包含 localized resource key / presentation state，但不反向进入 core。

---

## 4. 组件库 / Design System

### 4.1 为什么现在就要建

RootTools 后续会有大量“状态卡 + 指标 + 风险操作 + 权限状态 + 后端状态”。如果每个页面自行写 Material3 组合：

- UI 会越来越不一致；
- 风险操作的颜色/确认逻辑会漂移；
- AI 会复制旧页面的偶然实现；
- Accessibility 很难统一整改。

因此组件库应当**现在开始建立，但先以 package 形式存在**。

### 4.2 第一批通用组件

```text
RootToolsScaffold
ToolTopBar
ToolSectionCard
MetricTile
MetricRow
StatusChip
BackendBadge
CapabilityBadge
CapabilityGate
RiskBanner
RiskConfirmDialog
DangerActionButton
PrimaryActionButton
ActionResultBanner
LoadingState
EmptyState
ErrorState
CopyableValueRow
```

### 4.3 Design Token

至少统一：

- spacing：4 / 8 / 12 / 16 / 24 / 32；
- shape：small / card / dialog；
- semantic color：success / warning / danger / info / privileged；
- risk level：safe / caution / dangerous / destructive；
- typography mapping；
- card elevation / border；
- enabled / disabled / unavailable 状态透明度。

不要在业务 Composable 中重复散落 `Color(0xFF...)`、不同圆角值和同义状态色。

### 4.4 风险组件是业务安全的一部分

Root 工具的 Design System 不只是视觉复用。

例如 `RiskConfirmDialog` 应接受：

```kotlin
RiskConfirmSpec(
    level,
    title,
    impact,
    rollback,
    requiresExplicitConfirmation,
)
```

这样“清数据 / 禁用组件 / 关闭 ADB / 模块变更 / 重启”不会由每个页面自行决定提示强度。

---

## 5. 多语言规范

### 5.1 当前问题

当前 `app/src/main/res/values/strings.xml` 只有 2 个字符串，而 Compose 页面存在大量中文、英文和中英混排硬编码。

这会造成：

- 无法真正切换语言；
- 同一个概念出现多个翻译；
- AI 新增页面时继续复制硬编码；
- accessibility 文案和 UI 文案无法统一管理；
- 后续开源/分发时迁移成本迅速增加。

### 5.2 目标语言策略

建议：

```text
res/values/strings.xml           # 默认英文，必须完整
res/values-zh-rCN/strings.xml    # 简体中文
```

第二阶段再考虑繁中等其它语言。

原因：

- Root / Android 技术术语和开源生态英文更稳定；
- 默认资源必须完整，英文作为 fallback 更适合未知 locale；
- 中文仍作为重点维护语言。

### 5.3 AI 强制规则

新增功能：**禁止新增用户可见 hard-coded string。**

需要资源化的内容包括：

- 标题、按钮、说明；
- Toast / Snackbar；
- Dialog；
- Empty / Error / Loading；
- contentDescription；
- permission explanation；
- 风险提示；
- action result。

不翻译：

- package name；
- shell / adb 命令；
- Android property；
- Magisk / Shizuku / Sui / AppOps 等品牌或技术标识；
- 协议字段。

### 5.4 迁移策略

不要一次改完 160+ 处旧文案。

```text
新功能：100% resource 化
旧功能：每次修改该 Feature 时顺手完成该 Feature resource 化
最后：跑 hardcoded-string audit 清零
```

---

## 6. Git 与提交规范

采用 Conventional Commits 风格：

```text
<type>(<scope>): <summary>
```

推荐 type：

- `feat`
- `fix`
- `refactor`
- `perf`
- `test`
- `docs`
- `build`
- `ci`
- `chore`

推荐 scope：

- `adb`
- `apps`
- `battery`
- `dashboard`
- `diagnostics`
- `modules`
- `network`
- `performance`
- `privilege`
- `root`
- `startup`
- `storage`
- `ui`
- `docs`

### 6.1 AI 提交规则

1. 开始任务先 `git status`；
2. 不修改无关 dirty files；
3. 一个提交对应一个完整意图；
4. 不把大规模格式化和 Feature 逻辑混一起；
5. 不用 `git add .` 盲目提交整个工作区；
6. commit 前列出将被提交的文件；
7. commit 前至少跑目标测试；
8. 高风险核心逻辑还必须跑 lint + assemble；
9. push 前确认 branch 和 remote；
10. 除非任务明确要求，不自动 commit / push。

### 6.2 推荐提交拆分

```text
docs(...)     契约 / 方案
test(...)     pure policy 契约测试
feat(...)     backend / controller
feat(ui)      UI 集成
docs(...)     validation / ledger
```

不是每个任务都必须 5 个提交，但高风险能力不要塞成一个无法审查的大提交。

---

## 7. 代码质量门禁

### 7.1 现有最低门禁

日常开发先跑快速反馈，不要每次修改都生成 APK：

```bash
bash scripts/verify-fast.sh
# 单个 JVM 契约：
bash scripts/verify-fast.sh com.arthur.roottools.feature.assistant.policy.AssistantSelectionPolicyTest
```

只有准备真机安装时再执行：

```bash
./gradlew :app:assembleDebug
```

`./gradlew` 是 RootTools 的统一构建入口，负责固定 Gradle 版本、发现 JDK/Android SDK，并使用 checkout 级构建锁阻止同一工程重复并发编译。不要从 AI workflow 中绕过它直接调用其它工程的 wrapper 或裸 `gradle`。

当前 RootTools 使用 Kotlin in-process compilation，避免多个 Android 工程在 AI 并行开发时争用同一全局 Kotlin Compile Daemon。机器整体有多个重型 build 时仍应由 workspace 调度层避免同时启动 Android/Rust 全量编译；项目内 Gradle 参数不能替代跨项目资源调度。

`./gradlew` 同时执行 host-load preflight：1 分钟 load average 默认超过逻辑 CPU 数 2 倍时拒绝启动 build，可通过 `ROOTTOOLS_MAX_LOAD_PER_CORE` 调整。AI 不应在这种状态下反复重试或绕过锁；应先结束自己重复的 build，或由 workspace 调度层等待重型任务槽位。`ROOTTOOLS_FORCE_BUILD=1` 只用于明确需要立即抢占构建的人工/诊断场景。

交付前最低门禁：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

完整验证：

```bash
bash scripts/build.sh
```

### 7.2 建议补充

按顺序引入，不一次堆所有工具：

#### P0

- `.editorconfig`；
- Kotlin formatter；
- Android lint 作为 CI required check；
- GitHub Actions PR verify；
- `gradle/libs.versions.toml` Version Catalog。

#### P1

- detekt 或同等级 complexity / code-smell 检查；
- Kover/Jacoco coverage；
- dependency update bot；
- dependency vulnerability review；
- debug / release build artifact smoke check。

### 7.3 覆盖率策略

不建议一开始要求整个 Android UI 80%。

更合理：

- `policy` / parser / validator：80%+ line coverage 作为目标；
- privilege routing pure logic：90%+；
- Controller：关键 branch 覆盖；
- Compose：优先语义/交互测试，不追求无意义行覆盖；
- Repository + shell parser：fixture-based tests。

覆盖率门槛只允许逐步提高，不因新增功能随意下调。

---

## 8. CI 规划

GitHub PR 建议至少有：

```text
verify-fast
  - formatter/check
  - unit tests
  - lintDebug
  - assembleDebug

verify-release
  - assembleRelease
  - optional coverage
```

未来可加入：

- connected Android test；
- Macrobenchmark / Baseline Profile；
- Samsung 真机是本地/私有 device gate，不应把 Root 设备接到公开 CI runner。

---

## 9. AI Skills 体系

项目应把 AI 从“自由发挥”改成“按任务类型执行固定工作流”。建议建立以下 8 类 Skill。

### 9.1 `research`

适用：竞品、Root 能力、AOSP/OEM、开源工程调研。

输入：问题、目标 Feature、已知参考工程。

必须输出：

- 当前代码证据；
- 官方 / 开源来源；
- facts / inference / assumptions 分开；
- 能力矩阵；
- RootTools 已有 vs 缺失；
- 风险；
- 推荐优先级；
- 不建议照搬的设计。

### 9.2 `feature-design`

适用：开发前方案。

必须输出：

- user story；
- in/out scope；
- current state；
- data flow；
- capability；
- permission / backend；
- domain model；
- controller / repository API；
- UI state；
- rollback / safety；
- test matrix；
- implementation phases；
- acceptance criteria。

### 9.3 `implementation`

固定顺序：

```text
contract
-> pure policy + tests
-> parser / adapter
-> controller
-> audit
-> ViewModel
-> UI
-> docs
-> verification
```

禁止“先把 UI 点通，再把 root 命令塞进去”。

### 9.4 `refactor`

目标：只改变结构，不改变业务语义。

规则：

- refactor 前先补 characterization test；
- 小批移动；
- 每批可编译；
- 不顺手加 Feature；
- 不在同一提交做全仓 formatter；
- 记录 API / package relocation。

### 9.5 `review`

检查顺序：

1. privileged safety；
2. architecture boundary；
3. input validation；
4. rollback/audit；
5. duplicate truth source；
6. concurrency/lifecycle；
7. i18n/accessibility；
8. tests；
9. performance；
10. docs。

输出按 P0/P1/P2 severity，不只给风格意见。

### 9.6 `documentation`

原则：更新 canonical doc，不制造第二份真值。

文档类型：

- research；
- feature design；
- architecture；
- ADR；
- testing standard；
- delivery ledger；
- validation report。

### 9.7 `device-validation`

固定顺序：

```text
adb identity / device check
-> read-only capability probe
-> install/update
-> launch
-> read-only UI/data validation
-> controlled mutation
-> verify expected system state
-> rollback
-> collect log / screenshot / result
```

任何 reboot / ADB-off / module mutation 必须提前评估远程失联风险。

### 9.8 `release`

检查：

- versionCode / versionName；
- changelog；
- unit / lint / debug / release；
- artifact；
- migration / compatibility；
- known issues；
- rollback；
- Git tag / release（仅明确要求时）。

---

## 10. 文档模板

本次新增：

```text
docs/templates/research-plan.md
docs/templates/feature-design.md
docs/templates/adr.md
docs/templates/validation-report.md
```

AI 后续输出方案时优先套模板，减少每次文档结构漂移。

---

## 11. 安全与权限规范还需补齐的工程项

RootTools 属于高权限应用，除了普通 Android 工程规范，还需要额外建立：

### P0

- exported component inventory；
- external Intent schema 与输入验证；
- token / local secret 生命周期；
- privileged action risk level；
- audit record schema；
- shell interpolation review；
- package / component protected target policy。

### P1

- threat model 文档；
- backup / restore 的敏感数据分类；
- debug log 脱敏；
- crash report 是否允许带 package / command / device info；
- root backend compatibility matrix；
- Android API / Samsung One UI compatibility matrix。

---

## 12. 性能规范

Root 工具非常容易“为了监控性能而消耗性能”。工程标准应明确：

- 后台无必要不 polling；
- 页面不可见时降采样；
- shell command 批处理优先于大量进程创建；
- 不允许 Compose 高频状态导致昂贵 repository 重跑；
- Top process / dumpsys 维持低频；
- UI 列表使用稳定 key；
- 大量 package/component 数据避免一次性主线程转换；
- 后续建立 startup + scrolling benchmark；
- 稳定后引入 Baseline Profile。

---

## 13. ADR 机制

以下决定必须写 ADR，而不是只留在聊天：

- 是否进入 Gradle 多模块；
- 是否引入 Room；
- Privilege backend 路由重大变化；
- 是否开放 destructive automation；
- 多 Root runtime 统一接口；
- 是否建立远程 daemon / MCP；
- 是否改变默认 ADB exposure；
- 是否引入新的长期后台 Service。

ADR 状态：`proposed / accepted / superseded / rejected`。

---

## 14. 分阶段落地计划

### P0：先把 AI 和工程入口标准化

- [x] 建立根目录 `AGENTS.md`；
- [x] 建立工程治理文档；
- [x] 建立 research / design / ADR / validation 模板；
- [ ] 新增 `.editorconfig`；
- [ ] 建立 formatter；
- [ ] 建立 GitHub Actions fast verify；
- [ ] 建立 Version Catalog；
- [ ] 把“新增 UI 不得硬编码文案”加入自动检查；
- [ ] 从新增 Feature 开始强制资源化。

### P1：收敛当前结构债务

- [ ] 拆 `DashboardScreen.kt`；
- [ ] 拆 `DashboardViewModel.kt`；
- [ ] 建 `AppContainer`，把对象装配从 ViewModel 移出；
- [ ] 用 typed destination + Navigation Compose 收敛多页面导航；
- [ ] 建 `core/ui/component`；
- [ ] 建统一 ActionResult / Error presentation；
- [ ] Feature package 化；
- [ ] 建 parser fixture test 体系；
- [ ] coverage baseline；
- [ ] security review checklist。

### P2：模块化与性能工程

- [ ] 根据 LOC/build/concurrency 指标做第一次 module ADR；
- [ ] 必要时抽 `:core:model`；
- [ ] 必要时抽 `:core:privilege`；
- [ ] 必要时抽 `:core:designsystem`；
- [ ] Macrobenchmark；
- [ ] Baseline Profile；
- [ ] dependency graph enforcement。

### P3：成熟工程治理

- [ ] Release automation；
- [ ] Changelog automation；
- [ ] API / compatibility matrix；
- [ ] 多设备验证矩阵；
- [ ] AI skill files 自动路由；
- [ ] 关键 Feature 的端到端设备验收脚本。

---

## 15. 是否要马上拆 Gradle module：最终判断

当前答案是：**不马上大拆。**

正确顺序：

```text
3460 行巨型 Screen 拆开
-> Feature package 边界建立
-> 公共组件与 core API 稳定
-> CI / tests 能保护重构
-> 观察构建与并行开发成本
-> 再用 ADR 决定 Gradle module
```

这样既避免“单模块最终变泥球”，也避免为了架构美观过早拆出几十个 module。

