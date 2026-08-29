# ADR-0004：收口 RootTools 相关工程与历史

- Status: accepted
- Date: 2026-08-29
- Owners: RootTools

## Context

`/Users/ai/project/mobile-android` 曾同时保留 RootTools 主仓、两个长期 worktree、三个独立 Root 工具工程和一个名称冲突的 Xposed 工程。它们分散使用不同 Gradle 入口、Root shell 实现、应用标识和 Git 状态，导致功能边界、提交历史和发布来源难以判断。

本次收口前的恢复点如下：

| 来源 | 恢复点 | 关系 |
|---|---|---|
| `RootTools/main` | `5ed5b33` | canonical 主线 |
| `feat/termux-runtime` | `cff048b` | 已被 `main` 完整包含，无独有提交 |
| `feat/shizuku-sui-bridge` | `c51d25e` | 已被 `main` 完整包含，无独有提交 |
| `Net-Tools/main` | `fbec3d2` | 独立 Root 网络检查历史 |
| `NFC-Tools/main` | `a2f5895` | 收口前建立的原始基线提交 |
| `Background-Server/main` | `f3cd30c` | 收口前建立的原始基线提交 |
| `root-tools/main` | `32923ba` | 收口前建立的 HyperOS Xposed 基线提交 |

`AdbTools` 实际提供剪贴板和加密载荷解包，不拥有 Root ADB 控制；`androidDemo` 是模板工程；`cfc-master` 是 CameraFileCopy 上游衍生工程。三者不进入 RootTools 收口范围。

## Decision Drivers

- 一个能力只保留一个正式真值源；
- 原始提交历史和未提交数据必须先有恢复点；
- 主产品最终只有一个 RootTools Android App；
- Root / Shizuku / Sui 写操作继续进入 typed Controller / `PrivilegeRouter`；
- NFC、网络检查、后台运行等大功能需要保留独立测试和依赖边界；
- Xposed 模块必须维持独立 APK/runtime 边界，不能伪装为普通 RootTools 页面；
- 迁移期间每个提交都应可审查、可回退，不使用 squash 丢失来源历史。

## Options

### Option A：继续维护多个同级仓库

- 优点：短期改动最少。
- 缺点：RootShell、设计系统、依赖版本、设备验证和发布入口继续分裂。
- 风险：同名目录和 worktree 再次被误认为独立产品。

### Option B：把所有代码直接复制进 `:app`

- 优点：表面上只剩一个模块。
- 缺点：丢失来源历史，巨型 `:app` 继续膨胀，原有独立测试和 native/runtime 边界消失。
- 风险：高权限实现互相绕过，无法分批验证。

### Option C：一个 canonical 仓库、一个主 App、少量有证据的模块

- 优点：历史完整；功能可以逐域迁移；最终发布和权限真值源唯一。
- 缺点：迁移期会短暂保留只读 source snapshot；Gradle 与 package 边界需要逐批归一化。
- 风险：若未在迁移完成后删除 snapshot，会重新形成双真值。

## Decision

选择 Option C。

最终仓库结构：

```text
RootTools/
├── app/                              # 唯一主 RootTools App
├── feature/
│   ├── network-inspection/           # 抓包、协议和 TLS 检查
│   ├── nfc/                          # Reader/HCE/OEM NFC 诊断
│   └── background-runtime/           # 长运行任务、功耗和 WireGuard server
├── companion/
│   └── hyperos-credential-fix/       # 独立 Xposed APK
├── ios/NFCProbe/                     # 独立 Apple 探针，不进入 Android APK
└── consolidation/sources/            # 迁移期只读来源，完成后删除
```

执行规则：

1. 来源仓库先建立清洁恢复提交；
2. 使用不 squash 的 subtree/merge 导入来源历史；
3. 每次只迁移一个功能域，先搬 characterization tests 和纯 policy/model；
4. Android Framework 操作改接 `PrivilegeRouter`，Linux/root-only 操作改接共享 `RootShell`；
5. UI 接入 RootTools canonical navigation 和 design system 后，才删除对应 source snapshot；
6. Termux/Shizuku worktree 在确认 `main` 包含全部提交后退役；
7. 最终源码树不得存在第二份可构建的同功能 App。

## Consequences

### Positive

- RootTools 成为唯一工程、历史、构建和发布入口；
- 专业功能保留独立测试/依赖边界，但共享权限、安全和 UI 契约；
- Xposed 与 iOS 探针保留真实平台边界；
- 原独立仓库历史可以通过 merge parent 和路径追踪继续审计。

### Negative / Trade-offs

- 迁移期间主仓会暂时增大；
- compileSdk、AGP、Kotlin、Compose、NDK 和 native bridge 必须逐项对齐；
- 真机验收需按 NFC、网络、后台/Xposed 分域执行，单一 `assembleDebug` 不能证明全部运行时能力。

## Validation / Revisit Trigger

完成条件：

- 所有来源恢复点都能从 RootTools Git 图到达；
- 两个历史 worktree 无独有提交并已退役；
- 主 App 能从 canonical navigation 到达已迁移功能；
- 同一系统能力只有一个 Repository/Controller/Router 真值源；
- `quality_guard.py`、JVM tests、lint、assemble 通过；
- NFC、网络检查、后台运行和 Xposed 分别有当前设备/运行时验证记录；
- `consolidation/sources/` 和原同级冗余目录完成删除或明确转为外部归档。

若模块只剩一个页面且没有独立依赖/测试生命周期，重新评估并折回 `:app` 的逻辑 feature package。
