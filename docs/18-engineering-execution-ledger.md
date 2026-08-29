# RootTools 工程治理实施账本

> 用途：跟踪 `17-engineering-governance-and-ai-workflow.md` 从“规范”变成“工程事实”的进度。

## Phase A — Repository baseline

- [x] 根目录 `AGENTS.md`：AI 工程契约
- [x] `.editorconfig`：基础格式约束
- [x] `scripts/quality_guard.py`：legacy debt / i18n 增量门禁
- [x] EntryPoint composition-root gate：UI / Tile / Widget / Receiver / Service 禁止重新直接构造 `RootShell`
- [x] GitHub Actions：PR / main 的 unit test + lint + assemble gate
- [x] Conventional Commit executable guard + versioned `commit-msg` hook
- [x] 标准 research / design / implementation / refactor / review / documentation / device validation / release skills
- [x] 标准 research / feature / ADR / validation 文档模板

### 当前冻结基线

| 指标 | 2026-08-20 baseline | 策略 |
|---|---:|---|
| `DashboardScreen.kt` | 2888 LOC | ceiling 2950；设备看板已迁出，继续拆 Performance / ADB / Diagnostics 等页面 |
| `DashboardViewModel.kt` | 996 LOC | ceiling 1060，优先抽 Feature state / coordinator |
| `AppControlCenterScreen.kt` | 908 LOC | ceiling 950；Runtime/Storage/Code/Policy 已拆出，继续拆 Inventory / Detail / Components / Permissions |
| Compose hard-coded UI strings | 160（当前 guard 统计口径） | global ceiling 160；仅 legacy `DashboardScreen.kt` 允许债务，其它新 UI 文件必须为 0 |

> ceiling 比当前值保留少量并行开发缓冲。不能为了让 CI 通过而随意抬高；需要抬高时必须 ADR 说明原因。

## Phase B — i18n and design system

- [x] 默认英文 `values/strings.xml` 已开始作为 fallback
- [x] `values-zh-rCN/strings.xml` 已建立
- [x] i18n CI gate：默认资源禁止中文；所有可翻译默认 key 必须存在 `zh-rCN`
- [x] `core/ui/token` 建立 spacing / radius / status / risk token
- [x] `core/ui/component` 建立第一批 Section / Metric / Status / Error / Risk 组件
- [ ] 首页 / Dashboard 旧 UI 文案迁移完成
- [x] Environment Integrity 页面文案资源化完成；旧 Device Integrity 页面已删除
- [ ] legacy hard-coded UI string debt 清零
- [ ] Accessibility content description 审查完成

## Phase C — composition root and feature state

- [x] 建立 `RootToolsApp + AppContainer`
- [x] `RootShell / PrivilegeRouter / AuditStore` 生命周期由 composition root 明确管理
- [x] `DashboardViewModel` 不再负责依赖构造
- [x] Tile / Widget / Boot / Automation / CPU Service 统一接入 composition root
- [ ] ADB state / actions 抽为 Feature coordinator/ViewModel
- [ ] App Control state / actions 抽为 Feature coordinator/ViewModel
- [ ] Integrity state / actions 抽为 Feature coordinator/ViewModel
- [ ] Dashboard 只聚合首页摘要，不再持有所有 Feature 完整状态

## Phase D — screen extraction

- [x] Dashboard feature screen：独立 `HealthDashboardUiState` + `feature/dashboard/ui`，0 直接 UI literal
- [x] Dashboard route/card registry 与 presentation formatter 从巨型 Screen 抽离
- [ ] Performance feature screen
- [ ] ADB feature screen
- [ ] Startup feature screen
- [ ] Diagnostics feature screen
- [ ] Module feature screen
- [ ] Network / Storage / Battery feature screens
- [ ] `DashboardScreen.kt` 降到 900 LOC 以下

## Phase E — dependency and quality maturity

- [x] Gradle Version Catalog
- [x] formatter / static analysis staging ADR：当前使用 EditorConfig + Android Lint + custom guards；legacy 拆分收口后再全量接 formatter/detekt
- [x] Kover 0.9.9 coverage report integrated for Android JVM host tests
- [x] core coverage non-regression baseline：Root session / privilege validator+router / mutation policies / thermal+CPU ownership / integrity matcher+risk engine
- [x] dependency-direction check：禁止 `feature/A` 直接 import `feature/B` 的实现
- [x] dependency-direction check：Feature 禁止反向依赖 legacy UI；Core 禁止反向依赖 Feature/UI；Integrity 仅冻结当前 2 条 legacy data 过渡依赖
- [x] security review automation：exported component / explicit Intent / automation credential+scope / arbitrary shell/process / sensitive backup exclusion
- [ ] performance baseline：startup / first frame / dashboard sampling

## Gradle module decision gate

当前仍保持单 `:app` module。只有满足 `17-engineering-governance-and-ai-workflow.md` 的 extraction 条件后，才评估：

```text
:core:model
:core:privilege
:core:designsystem
```

Feature module 不按卡片数量创建。

## Phase F — RootTools 工程收口

- [x] RootTools 主工作树 WIP 建立原子恢复提交 `5ed5b33`
- [x] NFC Tools 建立原始基线提交 `a2f5895`
- [x] Background Server 建立原始基线提交 `f3cd30c`
- [x] HyperOS Credential Fix 建立原始基线提交 `32923ba`
- [x] 确认 Termux / Shizuku 分支已被 `main` 完整包含且无独有提交
- [ ] 不 squash 导入 Net / NFC / Background / HyperOS 来源历史
- [ ] 迁移并归一化 network-inspection 功能
- [ ] 迁移并归一化 NFC 功能及 iOS probe 路径
- [ ] 迁移并归一化 background-runtime / WireGuard 功能
- [ ] 建立 HyperOS Credential Fix companion module
- [ ] 删除迁移期 source snapshot，退役同级 worktree/旧工程目录
- [ ] 完成统一构建、静态检查和分域真机验证

收口契约与完成条件见 `adr/0004-consolidate-roottools-projects.md`。
