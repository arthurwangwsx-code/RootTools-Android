# Termux / Developer Runtime Bridge 调研与规划

## 1. 结论先行

Termux 非常适合接入 RootTools，但二者不应该互相替代：

```text
Termux
  = Linux userland / package manager / scripts / SSH / Python / Node / Git / long-running jobs

RootTools
  = Android privileged control plane / typed Controller / Root + Shizuku routing / audit / rollback / UI
```

最有价值的方向不是在 RootTools 里再做一套“Root 终端”，而是建立 **Developer Runtime Bridge**：

```text
Mac / AiBox / Agent
        │
        │ Tailscale / SSH / MCP（后续）
        ▼
      Termux
  Linux runtime / scripts
        │
        │ typed RootTools CLI / Android Intent
        ▼
     RootTools
 Privileged semantic actions
        │
        ├── RootShell
        ├── Shizuku / Sui
        └── Android Framework
```

这样可以把手机变成一个长期在线的 **Android 自动化执行节点 / 移动 DevBox**，同时继续保持 RootTools 的安全边界：
Agent 可以调用 `set_performance_mode`、`set_adb_transport`、`apply_app_profile`，但不直接获得一个无约束的 `su` shell。

---

## 2. 本次图片与真机证据

截图中的应用是 **Termux**。

截图同时证明了一个非常重要的权限事实：

```text
普通 Termux shell
  setprop service.adb.tcp.port 5555  -> permission denied
  stop adbd                           -> Must be root

执行 su 后
  setprop service.adb.tcp.port 5555
  stop adbd
  start adbd                          -> 成功
```

说明 Termux 本身只是普通 Android App userland；Root 权限来自 Magisk `su`，不是 Termux 自带系统权限。

### 2.1 2026-08-20 Samsung SM-S908E 实测

通过当前远程 ADB 对 Samsung 设备只读探测：

```text
package        com.termux
versionName    googleplay.2026.06.21
versionCode    141
targetSdk      37
installer      com.android.vending
files size     ~62 MB
```

当前设备安装的是 **Google Play 分支**，不是 F-Droid / GitHub stable 分支。

更关键的是：当前设备 Manifest / PackageManager 实测没有发现：

```text
com.termux.permission.RUN_COMMAND
com.termux.app.RunCommandService
com.termux.RUN_COMMAND
```

执行：

```text
cmd package query-services -a com.termux.RUN_COMMAND
```

结果为：

```text
No services found
```

因此：**当前 Samsung 上的 Google Play Termux 不能直接使用 stable Termux 的官方 `RUN_COMMAND` 外部调用协议。**

当前 Termux 只确认存在基础 `bash / pkg`；本次只读探测没有发现已安装的 `sshd / python / node`。

---

## 3. 上游官方能力调研

### 3.1 Stable Termux：官方支持第三方 App 调用命令

Termux stable 分支提供 `RunCommandService`：

```text
third-party Android app
        │
        │ com.termux.RUN_COMMAND
        ▼
RunCommandService
        │
        ├── command path
        ├── arguments
        ├── workdir
        ├── stdin
        ├── foreground/background
        └── PendingIntent result
```

第三方 App 要使用它，需要：

1. Manifest 声明 `com.termux.permission.RUN_COMMAND`；
2. 用户显式授予该 dangerous permission；
3. Termux `~/.termux/termux.properties` 中显式设置 `allow-external-apps=true`；
4. Android 11+ 满足 package visibility；
5. 需要返回结果时通过 `PendingIntent` 接收 stdout / stderr / exit code。

这意味着对于 F-Droid / GitHub stable Termux，RootTools 可以做真正的**双向原生 Bridge**，不需要 Accessibility 模拟点击。

### 3.2 Google Play Termux 是独立代码分支

Termux 官方 README 明确说明 Google Play 版本使用单独仓库，并因 Play policy 做了较大调整；功能与 F-Droid / GitHub stable 不完全一致。

历史公告也明确记录过 Play 分支移除了 `RUN_COMMAND`。当前 Samsung 2026.06.21 版本的 PackageManager 实测仍然没有该 service，因此 RootTools 必须**运行时 capability probe**，不能只按 `com.termux` 包名假设能力存在。

### 3.3 Termux:Tasker

Stable 生态提供 Termux:Tasker，让 Tasker 或其他 plugin host 执行 Termux 脚本。

RootTools 不需要优先走 Tasker plugin protocol，因为官方 `RUN_COMMAND` 已经是更直接的 App-to-App 接口；Tasker adapter 可以保留为兼容/自动化生态入口。

### 3.4 Termux:API

Termux:API 的定位相反：

```text
Termux script
    ↓
Termux:API
    ↓
Android API
```

它适合让 shell / Python 脚本访问通知、传感器、剪贴板、Wi-Fi、TTS 等 Android 能力。

但 RootTools 已经直接运行在 Android App 层，并拥有 Root / Shizuku Controller，因此不应把 RootTools 的 Android Framework 能力再绕到 Termux:API。

### 3.5 Termux:Boot / termux-services

Termux:Boot 能在开机后运行脚本；`termux-services` 使用 runit 管理常驻服务。

这非常适合后续承载：

- `sshd`；
- 本地 MCP relay；
- Python / Node worker；
- repo sync worker；
- 非特权数据处理任务。

但 RootTools 自己的 ADB / CPU / App policy boot restore 仍由 RootTools 管，不迁移给 Termux，避免出现两个系统状态真值源。

### 3.6 Termux:Widget

Termux:Widget 可以运行 `~/.shortcuts/` / `~/.shortcuts/tasks` 中的脚本。

RootTools 已经有自己的 Quick Tile / Widget / Action Router，所以不需要复制 Widget UI；更有价值的是让 Termux Widget 中的脚本可以调用 RootTools typed CLI。

---

## 4. RootTools 当前已经具备的接入基础

RootTools 不是从零开始。

### 4.1 Termux -> RootTools 已经基本可行

当前 `ActionRouterReceiver`：

- `android:exported="true"`；
- 要求显式 component；
- 校验 RootTools 本机随机 token；
- 只接受固定 semantic command；
- 不接受 arbitrary shell。

现有 command：

```text
SET_MODE
SET_ADB
SET_NATIVE_ADB
FREEZE
UNFREEZE
RUN_DIAGNOSTIC
```

因此 Termux 里实际上可以和 ADB CLI 一样，通过 `am broadcast` 调 RootTools。

目标应该把这一能力产品化为：

```text
roottools status
roottools performance auto
roottools performance cool
roottools adb root-tcp on
roottools adb wireless on
roottools app freeze <package>
roottools app enable <package>
roottools diagnose
```

CLI 只是参数解析器，最终仍然调用现有 Action Router / Controller。

### 4.2 RootTools -> Termux 当前需要分 Variant

```text
F-Droid / GitHub stable
  → Official RUN_COMMAND adapter

Google Play（当前 Samsung）
  → RUN_COMMAND unavailable
  → 先做 reverse bridge
  → 后续可选 SSH adapter / SAF provisioning
```

### 4.3 当前架构与 Termux Bridge 天然兼容

RootTools 已有：

- `RootShell`；
- `PrivilegeRouter`；
- `RootActionAuditStore`；
- `ActionRouterReceiver`；
- Tool Registry；
- ADB / Tailscale；
- diagnostics export。

Termux 集成应该成为 **integration adapter**，不是第四套 privilege backend。

---

## 5. 产品定位：Developer Runtime Center

建议后续新增一张一级卡片：

```text
开发运行时
Termux · Play 2026.06
Reverse bridge ready · Direct API unavailable
```

英文可命名：

```text
Developer Runtime
```

不建议直接叫“Termux 管理”，因为后续这个领域可能还会容纳：

- Termux；
- local SSH runtime；
- MCP relay；
- script jobs；
- Android automation CLI。

### 5.1 页面 Section

#### A. Runtime Overview

展示：

```text
Termux                    Installed
Variant                   Google Play
Version                   googleplay.2026.06.21
Prefix                    Ready
Root in Termux            Available / Unknown
RUN_COMMAND               Unsupported
RootTools CLI             Not installed
SSH server                Not installed / Off / Listening
Python                    Not installed
Node.js                   Not installed
Git                       Not installed
```

这里所有状态都必须 runtime probe，不按包名猜测。

#### B. Bridge Setup

根据 Variant 给出不同路径。

Stable Termux：

```text
[Grant RUN_COMMAND]
[Check allow-external-apps]
[Test Bridge]
[Install RootTools CLI]
```

当前 Play Termux：

```text
Direct RUN_COMMAND        Unsupported by installed build
Termux -> RootTools       Available
Local SSH bridge          Optional
Switch stable Termux      Guide only
```

RootTools **不能用 root 偷偷修改** `allow-external-apps=true`；这是用户明确授予第三方 App 执行 Termux command 的信任边界。

#### C. RootTools CLI

RootTools 生成/维护一个轻量 CLI wrapper。

第一阶段命令：

```text
roottools status
roottools mode auto|cool|performance
roottools adb root on
roottools adb wireless on|off
roottools diagnose
roottools app freeze <package>
roottools app enable <package>
```

第二阶段：

```text
roottools app profile apply <id>
roottools backup run <profile>
roottools network profile apply <id>
roottools integrity snapshot
```

不要增加：

```text
roottools shell "..."
roottools su "..."
```

#### D. Managed Tasks

RootTools 允许注册 **typed Termux Task**，而不是 arbitrary command：

```kotlin
data class TermuxTaskDefinition(
    val id: String,
    val executable: String,
    val argumentSchema: List<ArgumentSpec>,
    val workDirPolicy: WorkDirPolicy,
    val networkPolicy: NetworkPolicy,
    val timeoutSeconds: Int,
    val outputLimitBytes: Int,
    val risk: RiskLevel,
)
```

第一批适合的任务：

- `termux-info`；
- package inventory；
- Git repository status；
- start / stop approved service；
- run a RootTools-managed diagnostic parser；
- compress diagnostic bundle；
- checksum / archive；
- backup artifact encryption / upload adapter（后续）。

#### E. Services

目标管理：

```text
sshd
agent-relay
mcp-relay
repo-sync
custom approved service
```

每个服务显示：

- installed；
- enabled；
- running；
- PID；
- bind address；
- port；
- latest exit；
- recent log；
- boot policy。

默认禁止新服务监听 `0.0.0.0`。

优先：

```text
127.0.0.1
    或
Tailscale interface only
```

#### F. Jobs & Logs

展示：

- execution id；
- task id；
- source：UI / Agent / Termux CLI / Automation；
- start / finish；
- exit code；
- truncated stdout / stderr；
- 是否触发 RootTools privileged action；
- audit id。

Termux 原始命令文本默认不进入 Root action audit；RootTools 只记录 semantic task id。

---

## 6. 双向 Bridge 设计

## 6.1 Direction A：Termux -> RootTools

这是当前设备最应该先落地的一条链路。

```text
Termux shell / Python / Node
       │
       │ roottools CLI
       ▼
Android am broadcast
       │
       ▼
RootTools Action Router
       │
       ▼
Typed Controller
```

### 当前 API 的问题

目前只有一个全局 automation token。

随着 Termux / MacroDroid / Agent 都进入后，这个 token 粒度太粗：

```text
一个 token 泄漏
    → 所有 automation action 都一起暴露
```

建议升级为：

```text
AutomationClient
├── clientId
├── displayName
├── tokenHash
├── scopes
├── createdAt
├── lastUsedAt
└── revoked
```

Termux 默认 scopes：

```text
READ_STATUS
RUN_DIAGNOSTIC
SET_PERFORMANCE
SET_ADB_ENABLE
APP_POLICY
```

仍然不允许：

- remote ADB disable；
- reboot / recovery；
- arbitrary root shell；
- arbitrary package uninstall；
- partition / boot image write。

### Result contract

现有 Broadcast API 是 fire-and-forget。

Termux CLI 需要结构化返回：

```json
{
  "requestId": "...",
  "action": "SET_MODE",
  "success": true,
  "backend": "root",
  "message": "Performance mode set to COOL",
  "auditId": "..."
}
```

第一阶段可使用 explicit result broadcast + requestId；
后续如果 Agent/MCP 需要更稳定的请求响应，再设计 Binder / local RPC，并单独写 ADR。

---

## 6.2 Direction B：RootTools -> Stable Termux

使用官方 `RunCommandService`，不要自己模拟键盘输入 Termux UI。

建议接口：

```kotlin
interface TermuxCommandBackend {
    suspend fun probe(): TermuxBackendState
    suspend fun execute(task: TermuxTaskRequest): TermuxTaskResult
}
```

实现：

```text
OfficialRunCommandBackend
```

只允许来自 `TermuxTaskRegistry` 的 task。

结果：

- stdout；
- stderr；
- exit code；
- timeout；
- Termux error code；
- duration。

不要把 `RUN_COMMAND` 封装成 RootTools 对外公开的 arbitrary command endpoint。

---

## 6.3 Direction C：RootTools -> Play Termux fallback

当前 Samsung 无官方 `RUN_COMMAND`。

候选方案按推荐度：

### 方案 1：只做 reverse bridge（P0，推荐）

RootTools 不主动执行 Termux command；Termux 脚本主动调用 RootTools。

优点：

- 无额外依赖；
- 当前设备马上可用；
- 安全边界最清晰。

### 方案 2：Local SSH adapter（P2，可选）

Termux 安装 OpenSSH 并启动 `sshd`：

```text
RootTools
   │ SSH key auth
   ▼
127.0.0.1:<port>
   │
   ▼
Termux sshd
```

优点：

- 不依赖 `RUN_COMMAND`；
- 和未来远程 DevBox 能力一致。

缺点：

- RootTools 需要 SSH client dependency；
- key lifecycle / host key / timeout / output stream 都要管理；
- 不能为了 bridge 默认开启公网监听。

因此只作为 Play branch optional adapter，不应成为 P0 blocker。

### 方案 3：直接 root 进入 Termux UID 执行 binary（不推荐）

理论上 root 可以访问 Termux private data，但 Android exec / SELinux / linker / app UID / environment 都有差异。

RootTools 如果依赖：

```text
su → Termux private files → hand-crafted env → execute
```

会形成非常脆弱的私有实现，同时绕开 Termux 自己的 App trust model。

本项目不采用这条路线作为正式能力。

### 方案 4：Accessibility 模拟终端输入（拒绝）

不可测试、不可靠、会污染用户会话，也完全没有必要。

---

## 7. 文件与脚本交换

Termux Bridge 不只有 command execution。

建议独立建模：

```text
TermuxFileBridge
```

优先方式：

1. Stable RUN_COMMAND 用 stdin 写入 RootTools-managed script；
2. SAF / DocumentsProvider 由用户授予目录 URI；
3. shared storage 只用于明确需要跨 App 分享的 artifact；
4. 不使用 root 直接改 Termux `$HOME` 作为常规同步协议。

目标目录建议：

```text
~/roottools/
├── bin/
│   └── roottools
├── tasks/
├── jobs/
├── cache/
└── exports/
```

脚本由 RootTools 管理时保存：

- script id；
- version；
- SHA-256；
- required packages；
- capability scopes；
- source。

更新前显示 diff / version，不静默覆盖用户自定义脚本。

---

## 8. 最值得想象的能力空间

## 8.1 手机成为远程 Android DevBox

```text
Mac / iPhone / AiBox
       │
    Tailscale
       │
       ▼
Termux SSH / MCP relay
       │
       ├── Git / Python / Node / scripts
       │
       └── RootTools typed actions
```

人在外面没有电脑时，可以：

- 查看设备健康状态；
- 开 Root ADB；
- 调整性能模式；
- 应用 App profile；
- 导出诊断；
- 执行备份；
- 拉取 Git 仓库里的自动化脚本；
- 运行轻量 Python / Node worker；
- 将结果通过 Tailscale / SSH / MCP 返回。

## 8.2 RootTools 成为 Android Privileged API Server

Termux / Agent 不再自己维护：

```text
setprop
pm
cmd appops
settings
dumpsys
sysfs
```

而是只调用：

```text
roottools status
roottools mode cool
roottools app profile apply test-device
roottools adb root on
roottools diagnose
```

底层 Android / Samsung / Shizuku / Magisk 差异全部由 RootTools 处理。

## 8.3 Device Lab Agent

未来多台测试机时：

```text
Agent Orchestrator
   ├── Samsung #1 / Termux relay / RootTools
   ├── Xiaomi  #2 / Termux relay / RootTools
   └── Redmi   #3 / Termux relay / RootTools
```

Agent 对所有 Android 设备使用同一 typed schema，不关心每台机的 root 实现细节。

## 8.4 Backup pipeline

未来 Backup & Recovery 可以非常自然地分工：

```text
RootTools
  privileged export / package policy / app data snapshot
       │
       ▼
Termux
  tar / compression / checksum / encryption / rsync / rclone
       │
       ▼
NAS / Mac / cloud
```

RootTools 不需要自己内置一整套 Linux backup 工具链。

## 8.5 Diagnostic pipeline

```text
RootTools snapshot
  → Termux Python parser
  → normalize JSON
  → archive
  → send to Agent / Mac
```

RootTools 保持只采集 Android privileged truth；数据加工交给通用 Linux runtime。

## 8.6 Script / Workflow Marketplace（远期）

不是允许任意插件直接 root，而是：

```text
Workflow Manifest
├── id / version
├── required Termux packages
├── inputs
├── RootTools scopes
├── script hash
└── outputs
```

工作流可以组合：

```text
Termux step
  + RootTools typed action
  + file artifact
  + condition
```

这会比在 RootTools APK 里不断硬编码新工具更有扩展性，同时仍然可以审计权限。

---

## 9. 安全模型

Termux 本质是可编程 Linux 环境，一旦再连接 RootTools，就必须明确权限边界。

### 9.1 必须保持的规则

1. RootTools 对外仍不提供 arbitrary root shell；
2. `RUN_COMMAND` 只由 RootTools 内部 typed Task Registry 使用；
3. `allow-external-apps` 必须由用户显式开启；
4. Termux client 使用独立 scoped token，不复用单一全局万能 token；
5. token 不进入 shell history / logcat / command label；
6. 高风险动作继续要求 RootTools UI confirmation；
7. SSH / MCP 默认 localhost 或 Tailscale-only；
8. 每个 managed script 有 hash / version；
9. stdout / stderr 有长度上限，敏感字段可 redact；
10. Termux task 和 RootTools privileged action 分开审计，并通过 requestId 关联。

### 9.2 Break-glass root terminal

用户当然仍可在 Termux 手工执行：

```text
su
```

这属于用户主动进入终端的 break-glass 能力，不应被 RootTools Agent API 自动化。

---

## 10. 工程设计

继续遵守单 `:app` Gradle module，不新增 `:termux-*` module。

建议 package：

```text
com.arthur.roottools
├── model/
│   └── TermuxModels.kt
├── data/
│   └── TermuxRuntimeRepository.kt
├── integration/termux/
│   ├── TermuxCapabilityProbe.kt
│   ├── TermuxCommandBackend.kt
│   ├── OfficialRunCommandBackend.kt
│   └── TermuxResultReceiver.kt
├── policy/
│   └── TermuxTaskController.kt
└── ui/
    └── DeveloperRuntimeScreen.kt
```

后续如果 SSH fallback 被证明有必要：

```text
integration/termux/LocalSshTermuxBackend.kt
```

不要把 SSH 抽成新的 Gradle module，除非未来多个 Feature 真的复用并产生独立测试/发布边界。

### 10.1 Domain model

```kotlin
enum class TermuxDistribution {
    FDROID,
    GITHUB,
    GOOGLE_PLAY,
    UNKNOWN,
}

enum class TermuxBridgeMode {
    OFFICIAL_RUN_COMMAND,
    REVERSE_INTENT_ONLY,
    LOCAL_SSH,
    UNAVAILABLE,
}

data class TermuxRuntimeSnapshot(
    val installed: Boolean,
    val versionName: String?,
    val distribution: TermuxDistribution,
    val runCommandServiceAvailable: Boolean,
    val runCommandPermissionDeclared: Boolean,
    val runCommandPermissionGranted: Boolean,
    val sshdInstalled: Boolean,
    val sshdListening: Boolean,
    val rootToolsCliInstalled: Boolean,
    val bridgeMode: TermuxBridgeMode,
)
```

关键点：`distribution` 只是解释信息，真正决定功能的是 `capability probe`。

---

## 11. 分阶段实施计划

## P0 — 当前 Samsung 立即可用

- [ ] `TermuxRuntimeSnapshot` + package/service capability probe；
- [ ] 正确识别当前 `googleplay.2026.06.21` 无 `RUN_COMMAND`；
- [ ] Developer Runtime 详情页；
- [ ] Termux -> RootTools CLI contract；
- [ ] Automation token 升级为 client + scope 模型设计；
- [ ] CLI 支持 status / mode / ADB / diagnostic / app freeze；
- [ ] 结构化 result callback；
- [ ] Samsung 真机从 Termux 调 RootTools round-trip 验收。

P0 不要求用户更换 Termux 版本。

## P1 — Stable Termux 双向原生 Bridge

- [ ] `OfficialRunCommandBackend`；
- [ ] runtime permission flow；
- [ ] `allow-external-apps` setup 检查与说明；
- [ ] PendingIntent result receiver；
- [ ] `TermuxTaskRegistry`；
- [ ] 安装 / 更新 `~/roottools/bin/roottools`；
- [ ] task timeout / output limit / audit；
- [ ] F-Droid / GitHub Termux 真机验证。

## P2 — Runtime bootstrap / services

- [ ] package inventory；
- [ ] optional environment preset：Git / OpenSSH / Python / Node；
- [ ] service status / start / stop；
- [ ] sshd key / listener safety；
- [ ] Play Termux Local SSH fallback feasibility；
- [ ] managed script hash / version / update。

## P3 — Remote DevBox / MCP

必须先写 ADR：`是否建立远程 daemon / MCP`。

- [ ] local MCP relay；
- [ ] Tailscale-only remote exposure；
- [ ] Agent scopes；
- [ ] streaming job result；
- [ ] device identity / pairing；
- [ ] rate limit / revoke / audit；
- [ ] AiBox / Mac round-trip。

## P4 — Cross-feature workflows

- [ ] Backup pipeline；
- [ ] App profile orchestration；
- [ ] diagnostic post-processing；
- [ ] multi-device Device Lab Agent；
- [ ] signed workflow manifest / script registry。

---

## 12. 测试计划

### JVM

- distribution detection；
- capability -> bridge mode routing；
- task allowlist；
- argument validation；
- token scope decision；
- result parser；
- timeout / truncation；
- hostile path / argument rejection。

### Android integration

- package absent；
- Play Termux installed but no RunCommand service；
- stable Termux installed but permission denied；
- service available + permission granted；
- PendingIntent callback；
- process death during task；
- Termux force-stopped；
- RootTools token revoked。

### Samsung SM-S908E

当前 Play 分支：

1. Runtime page 准确显示 `Direct API unsupported`；
2. Termux CLI -> RootTools status；
3. Termux CLI -> Performance Cool -> Auto；
4. Termux CLI -> Root ADB ensure on；
5. diagnostic result round-trip；
6. invalid token / invalid command 不执行；
7. 后台无高频 polling；
8. RootTools / Termux 均退后台后 CPU 不异常。

后续 stable 分支：

1. `RUN_COMMAND` permission 用户授权；
2. `allow-external-apps=true` 用户显式配置；
3. RootTools -> `termux-info`；
4. stdout / stderr / exitCode 回传；
5. timeout；
6. Termux task 与 Root action audit 关联。

---

## 13. 不做什么

本 Feature 明确不做：

- RootTools 内置完整 terminal emulator；
- WebView terminal；
- arbitrary remote `su` API；
- Accessibility 自动敲 Termux；
- 通过 root 偷改 Termux private config 以绕过用户授权；
- 默认安装并公网开放 sshd；
- 默认常驻 Python / Node / MCP daemon；
- 将 RootTools Controller 逻辑复制成 Termux shell script。

---

## 14. 推荐优先级

优先级判断：**值得做，而且建议排在 Agent / MCP 之前。**

原因不是“Termux 本身功能多”，而是它能给 RootTools 提供现成的 Linux 执行平面：

```text
RootTools = 安全、可解释的 Android 特权控制面
Termux    = 通用、可扩展的 Linux 执行面
```

先把这两层的本地 typed bridge 建好，后面 AiBox、MCP、远程 Agent、备份、脚本工作流都会自然复用同一个基础设施。

---

## 15. 上游参考资料

优先使用官方/上游资料：

- Termux `RUN_COMMAND` Intent：<https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent>
- Termux stable App：<https://github.com/termux/termux-app>
- Termux Google Play 分支说明：<https://github.com/termux-play-store>
- Termux:Tasker：<https://github.com/termux/termux-tasker>
- Termux:API：<https://github.com/termux/termux-api>
- Termux:Widget：<https://github.com/termux/termux-widget>
- Termux:Boot：<https://github.com/termux/termux-boot>
- termux-services：<https://github.com/termux/termux-services>

