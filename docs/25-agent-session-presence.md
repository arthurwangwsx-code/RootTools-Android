# Agent Session Presence / 后台 Agent 可见性

## 1. Problem / User Story

- 后台影子屏可以在物理屏熄灭时继续运行 App，但用户需要知道 Agent 大概在做什么；
- 不能为了可见性持续抢占主屏，也不能让高帧率 Preview 带来额外发热；
- Samsung / Pixel / Xiaomi 等设备必须先共享一套通用会话状态，再叠加 OEM 顶部通知能力；
- Xiaomi Focus Notification / HyperIsland 作为后续适配层，不进入通用业务逻辑。

## 2. Product Surfaces

```text
AgentSessionState (single source of truth)
  ├─ Standard ongoing notification      # 所有 Android 设备
  ├─ Agent overlay window               # SYSTEM_ALERT_WINDOW 已授权时
  ├─ Agent session detail screen        # 完整状态 / Preview / 接管
  └─ OEM top-surface adapter            # Xiaomi Focus / HyperIsland 后续接入
```

### Notification

- Running / Paused 使用低打扰持续通知；
- Waiting for user 使用高重要度提醒；
- 点击进入 Agent Session 详情；
- 支持 Pause/Resume、Stop、显示悬浮窗；
- Android 14+ 使用 `specialUse` FGS，并声明具体用途。

### Overlay

- 默认收起为 56dp 胶囊 / 浮球；
- 点击展开为轻量任务卡片；
- 展开态展示任务标题、当前步骤、可选 Shadow Preview、Pause/Resume、Hide、Stop、Details；
- 收起或隐藏时不抓 Preview；展开并 Running 时最多约 0.5fps 按需刷新；
- Overlay 权限不足时自动降级到通知，不阻塞任务执行。

### Detail Screen

- 展示当前任务、状态、目标 App、步骤、进度；
- 展示 Overlay 权限状态并跳转标准系统授权页；
- 展示按需 Shadow Preview；
- 提供 Pause / Resume / Stop / Show Overlay；
- 无活动任务时允许启动一个观察会话，用于验证 presence surface。

## 3. Domain Contract

`AgentSessionState` 是唯一状态源：

- `taskId`
- `title`
- `targetPackage / targetLabel`
- `currentStep`
- `status`
- `progressCurrent / progressTotal`
- `overlayMode`
- `startedAtEpochMs / updatedAtEpochMs`

UI、Notification、Overlay、OEM adapter 不拥有独立业务状态。

## 4. Performance Contract

- Collapsed overlay：0 fps Preview；
- Hidden overlay：0 fps Preview；
- Expanded + Running：最多每 2 秒请求一次 Preview；
- Preview 继续复用 Shadow Display 420px JPEG，不启用 MediaProjection；
- Overlay Service 本身不轮询设备健康指标；
- Session 状态更新通过 StateFlow 推送。

## 5. Permission / Safety

- `SYSTEM_ALERT_WINDOW` 必须通过标准 Settings 授权页获得，不使用 Root 静默授予；
- Overlay 不允许执行任意 shell；所有任务控制仍走 typed Controller / Session Manager；
- Notification action 只允许 Pause/Resume/Stop/Show overlay；
- Stop 只停止 Agent Session / presence surface，不隐式关闭 ADB / Wi-Fi；
- Preview 遵守既有 Shadow Display secure-content 边界。

## 6. OEM Strategy

### Samsung / Generic Android

```text
AgentSessionState
  -> Standard ongoing notification
  -> TYPE_APPLICATION_OVERLAY (when user granted)
```

### Xiaomi HyperOS 2 / 3

后续在同一状态源上增加：

```text
AgentSessionState
  -> Xiaomi Focus Notification adapter (OS2)
  -> Xiaomi HyperIsland adapter (OS3)
```

OEM adapter 失败必须回退标准 Notification，不影响 Overlay / Task execution。

## 7. UX Contract

### Presence hierarchy

同一个后台任务只拥有一份 `AgentSessionState`，但可以有三种信息密度：

1. **System surface**：标准持续通知；后续 Xiaomi adapter 可映射到 Focus Notification / HyperIsland。
2. **Overlay surface**：默认 56dp 左右的收起浮球；用户点击后展开成轻量任务卡片。
3. **Detail surface**：完整任务详情、Preview、权限状态、暂停/继续/停止与诊断信息。

三层 UI 不重复维护任务状态，也不把内部推理链展示给用户。用户看到的是可解释的动作摘要，例如：

```text
淘宝新手机调研
正在读取搜索结果 · 8 / 15
```

而不是内部 prompt、模型推理或任意 shell。

### Overlay states

```text
HIDDEN
  └─ user show -> COLLAPSED

COLLAPSED
  ├─ tap -> EXPANDED
  └─ hide -> HIDDEN

EXPANDED
  ├─ collapse -> COLLAPSED
  ├─ hide -> HIDDEN
  └─ details -> Agent Session screen
```

- `COLLAPSED`：只显示任务标识 / 状态点，不请求 Preview；
- `EXPANDED`：显示标题、当前步骤、Preview、Pause/Resume、Hide、Stop、Details；
- `WAITING_USER`：视觉上提升为 attention 状态，但不能越过系统安全页或自动确认登录/支付/生物识别；
- `COMPLETED / FAILED`：通知允许短时保留结果入口，Overlay 默认自动收起或消失。

### Notification semantics

- `RUNNING / PAUSED`：低打扰 ongoing channel；
- `WAITING_USER`：高重要度 attention channel；
- 点击通知始终进入 `agent-session` detail，而不是直接打开目标 App；
- notification action 只允许 `Pause/Resume` 与 `Stop`；
- Overlay 未授权、被 OEM 临时隐藏或创建失败时，Notification 是必达 fallback。

## 8. Lifecycle / Recovery Contract

```text
Agent task starts
  -> AgentSessionManager writes persisted state
  -> AgentSessionService starts foreground
  -> notification becomes visible
  -> overlay shown only if user granted SYSTEM_ALERT_WINDOW and mode != HIDDEN

process recreated / app upgraded
  -> persisted session state restored
  -> active RUNNING/PAUSED/WAITING_USER session rebinds presence service

session stopped
  -> overlay removed
  -> AgentSessionService stopped
  -> active notification cancelled
  -> Shadow Display remains independent unless task policy explicitly owns it
```

Presence 生命周期和 Shadow Display 生命周期必须解耦。停止“任务可见性”不能顺手关闭 ADB、网络或其它不属于该 session 的基础设施。

## 9. OEM / Security Behavior

- Samsung / Android 在敏感系统页面可能使用 `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`；RootTools 必须接受系统强制隐藏，不能通过 Root/LSPosed 绕过；
- Overlay 位置必须使用当前 logical display bounds clamp，不能假设 WindowManager physical frame 与 ADB input 坐标一致；
- Shadow Display active probe 必须兼容 OEM `cmd display` 输出差异，不在 UI 层写厂商分支；
- Xiaomi Focus / HyperIsland adapter 只消费 `AgentSessionState`，失败必须回退标准 Notification；
- Android Bubble / PiP 不是主实现。Bubble 只在未来确有 conversation 语义时考虑；PiP 只适合“纯画面观察”扩展，不承担任务控制。

## 10. Samsung Acceptance Matrix

设备：SM-S908E / Android 14 / One UI 6.1 / rooted。

- [x] APK 安装成功，原 Root / ADB 功能无回归；
- [x] 无 Overlay 权限时，Agent Session 仍能通过 ongoing notification 工作；
- [x] 标准系统 Overlay 授权入口可达；使用 One UI「悬浮窗」设置页手动授权，没有 Root/AppOps 静默放行；
- [x] 授权后 collapsed overlay 可显示并展开；WindowManager 为 `TYPE_APPLICATION_OVERLAY`，收起约 58dp，展开约 320dp；位置会按屏幕边界 clamp；
- [x] expanded overlay 能显示 current step；Shadow Display Preview 低频采集已生成约 32KB JPEG；敏感系统权限页会由 One UI 正确强制隐藏 non-system overlay；
- [x] collapsed / hidden 状态不持续抓 Preview；expanded + Running 才启动 2 秒 Preview loop；收起态 RootTools CPU 真机抽样 0.0%；
- [ ] notification content PendingIntent 在 Activity 已存在时仍能通过 `onNewIntent` 重新消费 `open_screen=agent-session`；代码修复已编译并通过 security guard，待覆盖安装后的三星回归；
- [ ] Pause / Resume 在 detail / persistent state / notification 三处同步；
- [ ] Stop 后 AgentSessionService / active NotificationRecord / overlay 均消失，同时不误杀独立 Shadow Display；
- [ ] App 进程被系统回收 / APK 覆盖安装后，持久 AgentSessionState 与 presence surface 的恢复策略真机验证；
- [ ] 息屏 / 锁屏后 ongoing notification/session 保持，Overlay 不唤醒物理屏；
- [ ] 最新 Agent Session 变更完成完整 `testDebugUnitTest` / `lintDebug` / `assembleDebug` / security guard；Quality guard 只允许既有历史债；
- [ ] 验收结束完成 Shadow Display / AgentSessionService / Notification / Overlay 清理并确认 CPU 稳态。

### Samsung compatibility note

One UI 6.1 / Android 14 returns full `Display id N:` lines from `cmd display get-displays`, while
HyperOS 2 also supports the `-i` ids-only form. Shadow display status therefore parses the full
form first and keeps the ids-only command as a fallback instead of assuming one OEM CLI shape.

One UI also applies `HIDE_NON_SYSTEM_OVERLAY_WINDOWS` to sensitive system UI. During Chrome's
default-browser role prompt the Agent overlay remains created but WindowManager reports
`mForceHideNonSystemOverlayWindow=true` / `mPolicyVisibility=false`. RootTools intentionally does
not bypass this anti-tapjacking behavior; the overlay resumes only after the sensitive window is no
longer foreground.

The Samsung test device reports 1440×3088 physical pixels with a 1080×2316 override size. ADB
`input` coordinates therefore use the 1080×2316 logical space even though WindowManager frames may
be printed in the physical coordinate space.

Collapsed preview policy was also measured rather than inferred: while a RUNNING observer session
remained `COLLAPSED`, `/data/local/tmp/roottools-shadow/preview.jpg` kept the exact same size and
nanosecond modification timestamp across a 6-second window, and RootTools CPU sampled at 0.0%.
