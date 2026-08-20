# Scheduled Actions / Power Automation 方案

> 状态：2026-08-20 方案完成，待实现。
>
> 目标：为 RootTools 增加可靠、可审计、可取消的定时动作能力，首批覆盖 **定时息屏 / 定时重启 / 定时关机**，并为后续 Agent / Termux Managed Task 条件触发预留统一调度层。

---

## 1. Problem / User Story

### 用户场景

RootTools 所在设备经常被用于长时间运行 Agent、自动化测试、Termux 任务或远程操作。用户需要：

1. Agent 夜间运行时，若任务已经完成，自动息屏，避免 OLED 长亮和额外耗电；
2. 指定时间自动重启，让测试机恢复到干净状态；
3. 编译、下载、备份或 Agent 工作结束后自动关机；
4. 能提前取消或延后高风险动作，避免误杀仍在运行的任务；
5. 定时动作即使 App 不在前台、设备处于 Doze，也能可靠触发；
6. 重启后能恢复仍有效的计划，但不能因为“补执行”造成重启循环或刚开机又关机。

### 成功标准

- 定时息屏在 Samsung 真机 Doze / 锁屏 / App 后台状态下可可靠触发；
- 正常重启、关机使用固定语义 Root action，不暴露任意 shell；
- timer / exact alarm / boot restore 只有一个真值源；
- 高风险动作创建、触发、取消、跳过、失败都有审计记录；
- 重启 / 关机不做无条件 retry，也不做多 backend 自动 fallback；
- 后续 Agent / Termux 只需要发语义事件，不需要自己维护 timer 或 root 命令；
- 不引入常驻轮询，不新增 Gradle module。

---

## 2. Feasibility Conclusion

结论：**三个能力都可以实现。**

| 能力 | 可行性 | 首选实现 | 风险 | 备注 |
|---|---|---|---|---|
| 定时息屏 | 高 | `AlarmManager` + typed `DEVICE_SLEEP` + `KEYCODE_SLEEP` | 中 | 可验证 `PowerManager.isInteractive == false` |
| 定时重启 | 高（Root） | `AlarmManager` + typed `DEVICE_REBOOT` + `/system/bin/reboot` | 高 | 触发后设备和远程连接会消失；禁止 retry/fallback |
| 定时关机 | 高（Root） | `AlarmManager` + typed `DEVICE_POWER_OFF` + `/system/bin/reboot -p` | 高 | 关机后 Android 自身无法再靠 AlarmManager 开机 |

当前 Samsung SM-S908E 的只读能力探测已经确认：

```text
model=SM-S908E
sdk=34
/system/bin/reboot exists
/system/bin/svc exists
/system/bin/input exists
su uid=0
/system/bin/reboot --help -> usage: reboot [-p] [rebootcommand]
```

因此当前测试机具备首版实现所需的 Root / power 基础能力。

### 一个重要边界

**“定时关机”不等于“定时开机”。**

设备完全关机后 Android framework、RootTools、AlarmManager 都不再运行。除非 OEM / PMIC / RTC 提供专门的 scheduled power-on 能力，否则 RootTools 不能承诺自动再次开机。

首版只做：

```text
screen off
normal reboot
power off
```

不把以下动作加入无人值守 schedule：

```text
recovery
bootloader
download mode
flash
module mutation + reboot chain
```

---

## 3. Current State

项目不是从零开始，已经有可以直接复用的基础设施。

### 3.1 已有 Controller / Root 真值源

已有：

- `RootShell`：App 进程级共享持久 root session；
- `SystemActionController`：固定语义系统动作；
- `RootActionAuditStore`：Root 写操作审计；
- `PrivilegeRouter`：Shizuku/Sui 与 RootShell 的语义路由；
- `ActionRouterReceiver`：token + explicit component 的 Automation API；
- `AdbBootReceiver`：已经使用 `AlarmManager` 做 boot restore retry；
- `RECEIVE_BOOT_COMPLETED`：Manifest 已声明。

### 3.2 已有安全契约

`docs/14-core-logic-testing-standard.md` 已明确：

- UI / Receiver 不能直接拼 privileged shell；
- destructive action 不允许无条件 retry / fallback；
- reboot 属于 destructive action；
- privileged mutation 必须有 typed Controller 与 audit。

因此本功能不能实现为：

```text
AlarmReceiver
  -> su -c "reboot"
```

而必须是：

```text
AlarmReceiver
  -> ScheduledActionController
  -> ActionExecutionGateway
  -> PowerActionController
  -> fixed PowerBackend
  -> RootShell / Shizuku semantic backend
```

### 3.3 当前高风险动作仍有产品门禁

`08-common-actions.md` / `00-product-roadmap.md` 当前仍规定：

> post-boot Tailscale + Root ADB 重连没有完成真机验收前，不开放远程 reboot 执行入口。

这个约束应继续保留。

因此建议：

- Scheduler 与息屏能力可以先落地；
- 重启 / 关机 Controller 可以完成并测试到“dispatch 前”；
- 真正开放无人值守重启，需要先通过 post-boot reconnect gate；
- 关机比重启风险更高，因为它天然不会自动回连，必须单独打开 `Unattended power off` 能力。

---

## 4. Scope

### P0 in scope

- 一次性倒计时：15 min / 30 min / 1 h / 自定义；
- 指定日期时间；
- daily / weekday 基础重复计划；
- 息屏；
- 正常重启；
- 关机；
- exact alarm capability；
- Doze 下触发；
- boot / app update 后重建 alarm；
- 暂停 / 恢复 / 编辑 / 删除；
- 下一次执行时间；
- 高风险倒计时通知 + Cancel / Snooze；
- 执行历史；
- destructive action boot-loop guard；
- missed schedule 策略。

### P1 in scope

- `Agent finished` / `Managed Task finished` 事件触发；
- `No active Agent` 条件；
- “Agent 完成后 N 分钟息屏”；
- “到点但 Agent 仍运行 -> 延后 N 分钟”；
- Automation / Termux scoped API 创建低风险 schedule；
- destructive schedule 独立 scope + 本机总开关。

### P2 candidates

- Charging / battery / thermal 条件；
- 网络断开 / Tailscale 在线条件；
- 一组动作组成 Profile，例如 `Agent Night Mode`；
- reboot 后继续 Agent workflow；
- Device Lab 多设备统一 schedule schema。

### Out of scope

- 任意 shell schedule；
- cron parser 直接暴露给外部 Agent；
- recovery / bootloader 定时重启；
- 绕过 exact alarm 特殊访问授权；
- 通过 root 修改 Android 电池优化白名单；
- 保证关机后的自动开机。

---

## 5. Capability / Permission

| Capability | Backend | Permission / prerequisite | Fallback | Failure UI |
|---|---|---|---|---|
| 普通近似 timer | Android `AlarmManager` | 无额外特殊权限 | `setAndAllowWhileIdle` | `近似执行` badge |
| 精确定时 | Android `AlarmManager` | Android 12+ `SCHEDULE_EXACT_ALARM` special access | 低风险可降级为近似；高风险不静默降级 | `需要允许“闹钟和提醒”` |
| 后台 / Doze 唤醒 | `RTC_WAKEUP` / `ELAPSED_REALTIME_WAKEUP` | alarm 已成功 armed | 无轮询 fallback | `未武装 / 系统限制` |
| 息屏 | Shizuku/Sui semantic method 或 Root | fixed `input keyevent KEYCODE_SLEEP` | Shizuku -> Root 可 fallback | `无法控制屏幕` |
| 重启 | Root power backend | Root ready + safety gate | **不 fallback** | `重启未派发` |
| 关机 | Root power backend | Root ready + explicit unattended power-off | **不 fallback** | `关机未派发` |
| boot 后恢复 | manifest receiver | `RECEIVE_BOOT_COMPLETED` | 无 | `计划等待重新武装` |
| pre-unlock 恢复 | device-protected store | `LOCKED_BOOT_COMPLETED` + directBootAware | 可在 USER_UNLOCKED 再补一次 | `等待解锁` |
| 高风险取消窗口 | short foreground countdown service | notification permission + exact alarm path | 无静默执行 fallback | `通知权限缺失，不能武装` |

### Exact alarm 选择

RootTools targetSdk 当前为 35。Android 14+ 对新安装应用默认不会自动授予 `SCHEDULE_EXACT_ALARM`。

应使用：

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

不建议使用：

```xml
USE_EXACT_ALARM
```

后者主要面向闹钟 / 日历类应用，并受发布政策限制。

UI 必须先检查：

```kotlin
alarmManager.canScheduleExactAlarms()
```

没有权限时：

- 息屏：允许用户选择“近似时间”；
- 重启 / 关机：默认不 arm 精确计划，不静默变成不精确；
- 提供系统“闹钟和提醒”特殊访问入口。

---

## 6. Architecture

```text
                         ┌────────────────────────┐
                         │ Scheduled Actions UI   │
                         └───────────┬────────────┘
                                     │
                                     ▼
                         ScheduledActionViewModel
                                     │
                       create/edit/arm/cancel/query
                                     │
                                     ▼
                         ScheduledActionController
                          │        │          │
                          │        │          └── Audit / History
                          │        │
                          │        └── ScheduledActionStore
                          │             device-protected
                          │
                          ▼
                         AlarmScheduler
                   AlarmManager + PendingIntent
                          │
                 exact / inexact / reschedule
                          │
                          ▼
                   ScheduledActionReceiver
                          │
                          ▼
                    ExecutionPreflight
                  ┌───────┴────────┐
                  │                │
             low/medium         destructive
                  │                │
                  │                ▼
                  │       PowerCountdownService
                  │          30s + cancel
                  │                │
                  └───────┬────────┘
                          ▼
                 ActionExecutionGateway
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
   PowerActionController        existing Controllers
            │
            ▼
      PowerBackendRouter
       │             │
       ▼             ▼
 Shizuku/Sui      RootPowerBackend
  DEVICE_SLEEP    sleep/reboot/poweroff
       │             │
       └──────┬──────┘
              ▼
      Android / Linux system
```

### 架构原则

1. `AlarmManager` 只负责“什么时候触发”，不负责“怎么 root”；
2. Receiver Intent 只携带 `scheduleId`，不携带 shell / action command；
3. Receiver 收到 `scheduleId` 后必须回 Store 读取可信 schedule；
4. 一次性任务在 destructive dispatch 前先原子标记为 consumed；
5. recurring schedule 在 destructive dispatch 前先计算并保存下一次 occurrence；
6. reboot / poweroff dispatch 后不等待传统“exit code = success”；
7. reboot / poweroff 不允许自动换 backend 再执行第二次。

---

## 7. Package / File Layout

不新增 Gradle module。

建议在现有 `:app` 中新增逻辑 feature：

```text
com.arthur.roottools
├── feature/actions/
│   ├── model/
│   │   └── ScheduledActionModels.kt
│   ├── data/
│   │   ├── ScheduledActionStore.kt
│   │   └── ScheduledActionHistoryStore.kt
│   ├── policy/
│   │   ├── SchedulePolicy.kt
│   │   ├── ScheduleOccurrenceCalculator.kt
│   │   └── DestructiveActionGuard.kt
│   ├── runtime/
│   │   ├── AlarmScheduler.kt
│   │   ├── ScheduledActionReceiver.kt
│   │   ├── ScheduleRestoreReceiver.kt
│   │   └── PowerCountdownService.kt
│   └── ui/
│       ├── ScheduledActionsScreen.kt
│       ├── ScheduledActionEditor.kt
│       └── ScheduledActionsViewModel.kt
├── policy/
│   ├── SystemActionController.kt        # existing
│   └── PowerActionController.kt         # new semantic power action controller
└── root/
    └── RootShell.kt                     # existing truth source
```

`DashboardScreen.kt` 已经是 legacy giant file，**本功能不得继续把完整 schedule UI 塞进去**。Dashboard 只增加导航入口，实际页面放独立文件。

---

## 8. Domain Model

### 8.1 Semantic action

```kotlin
enum class PowerActionId {
    DEVICE_SLEEP,
    DEVICE_REBOOT,
    DEVICE_POWER_OFF,
}
```

不使用：

```kotlin
command: String
shell: String
```

### 8.2 Trigger

```kotlin
sealed interface ScheduleTrigger {
    data class AfterDuration(val durationMs: Long) : ScheduleTrigger
    data class AtDateTime(val epochMillis: Long, val zoneId: String) : ScheduleTrigger
    data class Daily(val localTime: LocalTime, val zoneId: String) : ScheduleTrigger
    data class Weekdays(
        val days: Set<DayOfWeek>,
        val localTime: LocalTime,
        val zoneId: String,
    ) : ScheduleTrigger

    // P1
    data class AgentCompleted(
        val taskSelector: AgentTaskSelector,
        val delayMs: Long,
    ) : ScheduleTrigger
}
```

### 8.3 Schedule entity

```kotlin
data class ScheduledAction(
    val id: String,
    val action: PowerActionId,
    val trigger: ScheduleTrigger,
    val enabled: Boolean,
    val precision: SchedulePrecision,
    val missedPolicy: MissedSchedulePolicy,
    val guard: ExecutionGuard,
    val source: ScheduleSource,
    val createdAtMs: Long,
    val nextTriggerAtMs: Long?,
    val lastExecutionAtMs: Long?,
    val riskAckVersion: Int,
)
```

### 8.4 Precision

```kotlin
enum class SchedulePrecision {
    EXACT,
    APPROXIMATE,
}
```

### 8.5 Missed schedule

```kotlin
enum class MissedSchedulePolicy {
    SKIP,
    RUN_IF_WITHIN_GRACE,
}
```

默认值：

| Action | missed policy |
|---|---|
| DEVICE_SLEEP | `RUN_IF_WITHIN_GRACE(5 min)` 可选 |
| DEVICE_REBOOT | `SKIP` 强制 |
| DEVICE_POWER_OFF | `SKIP` 强制 |

**重启 / 关机永远不做 boot 后 catch-up。**

### 8.6 Execution guard

```kotlin
data class ExecutionGuard(
    val postponeWhenAgentActive: Boolean = true,
    val postponeMinutes: Int = 15,
    val requireExactAlarm: Boolean = false,
    val requireNotification: Boolean = false,
    val countdownSeconds: Int = 0,
    val requirePostBootReconnectReady: Boolean = false,
)
```

推荐默认：

```text
sleep:
  countdown = 0
  postponeWhenAgentActive = false

reboot:
  countdown = 30s
  notification required
  exact alarm required for time trigger
  postponeWhenAgentActive = true
  postBootReconnectReady = true

power off:
  countdown = 30s
  notification required
  exact alarm required for time trigger
  postponeWhenAgentActive = true
  explicit unattended-power-off switch required
```

---

## 9. Alarm Scheduling Strategy

### 9.1 `After 30 min`

用户语义是相对时间，优先：

```text
ELAPSED_REALTIME_WAKEUP
```

同时持久化 wall-clock deadline，便于 reboot 后重建剩余时间。

### 9.2 `Today 03:30`

用户语义是绝对本地时间：

```text
RTC_WAKEUP
```

### 9.3 Exact

```kotlin
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    triggerAtMillis,
    pendingIntent,
)
```

### 9.4 Approximate

```kotlin
alarmManager.setAndAllowWhileIdle(...)
```

或在允许窗口语义下使用：

```kotlin
alarmManager.setWindow(...)
```

### 9.5 为什么不用 WorkManager 当主 scheduler

本功能是用户明确设置的时间动作，而且可能需要在 Doze 下准时进入高风险倒计时。

WorkManager 更适合：

- 可延迟后台任务；
- 上传；
- 周期维护；
- 允许系统批处理的工作。

它不是本功能的 primary timing source。

因此：

```text
timing truth source = AlarmManager
long background work = WorkManager candidate
```

首版 power action 本身只需要几秒，不引入 WorkManager 依赖。

---

## 10. Alarm Identity / Security

PendingIntent：

```text
explicit component
+ internal receiver
+ immutable
+ stable requestCode
+ scheduleId only
```

示意：

```kotlin
Intent(context, ScheduledActionReceiver::class.java)
    .setAction(ACTION_FIRE_SCHEDULE)
    .putExtra(EXTRA_SCHEDULE_ID, schedule.id)
```

禁止：

```kotlin
.putExtra("command", "reboot")
.putExtra("shell", "...")
```

Receiver 必须：

```text
scheduleId
   -> trusted ScheduledActionStore
   -> enabled?
   -> occurrence still valid?
   -> policy preflight
   -> semantic action
```

这样即使 Intent 被重放，也不能把一个 sleep schedule 改造成 reboot。

---

## 11. Persistent Store / Direct Boot

Schedule 数据应放在：

```kotlin
context.createDeviceProtectedStorageContext()
```

原因：

- reboot 后可能在用户首次解锁前需要恢复 alarm；
- schedule 本身不包含密码、token、Agent secret；
- device-protected storage 更适合 boot scheduling metadata。

首版 schedule 数量很少，不需要为了这个功能引入 Room。

建议：

```text
DeviceProtected SharedPreferences / Atomic JSON
```

持久化内容：

- schedule definitions；
- next occurrence；
- armed state；
- last dispatch；
- destructive guard marker；
- last known boot id；
- execution history ring buffer。

Automation token / Agent credential **仍然不能迁入 device-protected store**。

---

## 12. Reschedule / Recovery

需要统一的 `ScheduleRestoreReceiver` 处理：

```text
LOCKED_BOOT_COMPLETED
BOOT_COMPLETED
USER_UNLOCKED
MY_PACKAGE_REPLACED
TIME_SET / TIME_CHANGED
TIMEZONE_CHANGED
ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
```

每次都调用同一个：

```kotlin
alarmScheduler.reconcileAll(reason)
```

禁止每个 Receiver 各写一套重建逻辑。

### Exact permission 被撤销

Android 会取消未来 exact alarms。

RootTools 本地 schedule 记录不能直接删除，而应该变成：

```text
WAITING_PERMISSION
```

UI 显示：

```text
计划仍保存，但当前没有武装
允许“闹钟和提醒”后会自动恢复
```

---

## 13. Power Action Execution

## 13.1 DEVICE_SLEEP

首选语义动作：

```text
input keyevent KEYCODE_SLEEP
```

不要使用：

```text
KEYCODE_POWER
```

因为 POWER 是 toggle：如果屏幕已经关闭，反而可能点亮设备。

执行流程：

```text
PowerManager.isInteractive
  false -> NO_OP_ALREADY_SLEEPING
  true
     -> semantic backend DEVICE_SLEEP
     -> wait <= 2s
     -> verify !PowerManager.isInteractive
```

Backend：

```text
Shizuku/Sui UserService fixed sleepDevice()
   -> if unavailable and Root ready
RootPowerBackend.sleepDevice()
```

这是确定目标状态、幂等的操作，允许受控 fallback。

### Device Admin 是否需要？

Android 的 `DevicePolicyManager.lockNow()` 可以立即锁屏，但要求 Device Admin / device owner 等权限。

RootTools 已经有 Root / Shizuku，因此首版**不为了息屏额外要求 Device Admin**。

如果后续明确需要“安全锁定并要求强认证”，可把 `LOCK_NOW` 作为另一个语义动作，而不是混在 `DEVICE_SLEEP` 中。

---

## 13.2 DEVICE_REBOOT

P0 Root backend 使用 Android 标准 reboot binary：

```text
/system/bin/reboot
```

AOSP 的 reboot 实现最终请求 Android init power control。

### 不使用普通 `RootShell.execute()` 的传统成功语义

AOSP `reboot` 正常路径会等待设备真正进入 reboot，所以“命令退出码”不是合适的 success signal。

建议新增专用 dispatch：

```text
RootPowerBackend.dispatchReboot()
```

语义结果：

```kotlin
SEALED / REJECTED / DISPATCHED
```

而不是：

```kotlin
exitCode == 0 -> reboot success
```

可以由固定模板后台派发，让共享 root shell 能快速返回“请求已派发”；之后设备消失是预期行为。

### 重启后验证

dispatch 前保存：

```text
executionId
scheduleId
bootIdBefore
dispatchAt
```

新 boot 后读取：

```text
/proc/sys/kernel/random/boot_id
```

若 boot id 已变化且 pending reboot marker 存在：

```text
DISPATCHED -> CONFIRMED_AFTER_BOOT
```

这比在 reboot 前伪造“成功”更准确。

---

## 13.3 DEVICE_POWER_OFF

Samsung / AOSP 当前支持：

```text
/system/bin/reboot -p
```

AOSP `reboot -p` 的语义是 shutdown / power off。

同样必须通过：

```text
PowerActionController
 -> RootPowerBackend.dispatchPowerOff()
```

不允许 Receiver 直接执行。

### Power off 的结果模型

设备关机后无法立刻回报。

因此只能记录：

```text
DISPATCHED
```

下次人工开机后可以将历史记录补充为：

```text
DEVICE_BOOTED_AFTER_POWER_OFF_DISPATCH
```

但不能把它解释成“RootTools 已证明硬件成功断电”。

---

## 14. Destructive Action Safety

重启 / 关机不能只依赖“用户几小时前点过保存”。

建议采用两阶段 arm：

```text
创建时：明确确认 + 5s enable countdown

执行时：exact alarm
   -> 30s foreground countdown
   -> notification
      [取消]
      [延后 15 分钟]
   -> timeout
   -> final preflight
   -> dispatch once
```

Android 官方允许 exact alarm 为用户请求的时间动作启动前台服务，因此不需要长期常驻 FGS。

### 14.1 创建时确认

重启：

```text
此计划会让设备断开连接并重新启动。
如果 Root ADB / Tailscale 没有自动恢复，你可能需要物理接触设备。
```

关机：

```text
此计划会完全关闭设备。
关机后 RootTools、Agent、ADB 和定时任务都不会继续运行，必须手工或硬件方式重新开机。
```

### 14.2 执行时 final preflight

高风险 action 触发前再次检查：

- schedule 仍 enabled；
- occurrence id 未消费；
- exact occurrence 未过期；
- root backend ready；
- 不处于 boot grace period；
- 没有同 schedule 正在执行；
- Agent guard 是否允许；
- reboot reconnect gate 是否满足；
- 用户是否在 countdown 中取消。

任何一个失败：

```text
SKIPPED / FAILED
```

**不要 fallback 到另一个 destructive backend。**

---

## 15. Boot-loop Guard

无人值守 reboot 最大风险不是“命令失败”，而是 schedule 恢复错误导致重启循环。

必须建立硬保护。

### Guard A — destructive schedule 不 catch-up

如果开机时发现：

```text
scheduledAt < now
```

重启 / 关机：

```text
mark MISSED
compute next future occurrence
never execute immediately
```

### Guard B — minimum boot age

默认：

```text
device uptime < 10 min
```

时禁止执行 recurring reboot / poweroff。

UI 可在后续允许高级用户改，但首版固定不可关闭。

### Guard C — consume before dispatch

一次性：

```text
ARMED
 -> atomically CONSUMED
 -> dispatch
```

重复：

```text
current occurrence consumed
 -> persist next future occurrence
 -> dispatch
```

### Guard D — execution idempotency

同一个：

```text
scheduleId + occurrenceAt
```

只能有一次 destructive dispatch。

---

## 16. Agent / Managed Task Integration

这是本功能最值得继续扩展的部分。

## 16.1 Agent 完成后息屏

```text
Agent task finished
  -> AgentTaskEvent(FINISHED)
  -> ScheduledActionController.onEvent(...)
  -> wait 2 min
  -> DEVICE_SLEEP
```

推荐预设：

```text
“Agent 完成后息屏”
delay: 2 min
cancel when another Agent starts: true
```

也就是说：Agent A 结束后开始 2 分钟 timer，如果这期间 Agent B 又开始，则取消本次 sleep occurrence。

## 16.2 到点但 Agent 还在跑

适用于 nightly reboot：

```text
04:00 scheduled reboot
  -> active Agent exists
  -> POSTPONED_AGENT_ACTIVE
  -> +15 min
  -> recheck
```

必须设置最大延后窗口，例如：

```text
max postpone = 2 h
```

超过窗口后默认：

```text
SKIP
```

而不是强制杀 Agent。

用户可以在高级选项明确改成：

```text
At deadline -> reboot even if Agent active
```

但这属于更高风险模式。

## 16.3 Task 成功后关机

```text
Managed Task COMPLETED(success=true)
  -> delay 60s
  -> power-off countdown
  -> DEVICE_POWER_OFF
```

失败时默认不关机，方便用户排查。

可以提供：

```text
[x] 仅成功时执行
[ ] 成功 / 失败都执行
```

## 16.4 Agent 权限边界

默认 external client scopes：

```text
schedule.read
schedule.sleep
```

不默认授予：

```text
schedule.reboot
schedule.poweroff
```

要让 Agent 创建 destructive schedule，需要同时满足：

```text
client scope granted
+ local Unattended Power Actions master switch
+ destructive schedule safety policy
```

Agent 永远不能发送：

```text
shell = "reboot -p"
```

---

## 17. UI / UX

### 17.1 信息架构

不建议首页再新增一个很大的卡片。

第一阶段继续复用“常用操作”一级入口，在详情页顶部增加：

```text
立即执行 | 计划任务 | 自动化 | 审计
```

实际 `计划任务` 页面独立实现，避免继续膨胀 `DashboardScreen.kt`。

如果后续 Agent / Profiles / 条件触发明显增多，再把一级名称升级为：

```text
动作与自动化
```

### 17.2 计划任务首页

顶部 Hero：

```text
计划任务
3 个已启用 · 下一项 01:45 息屏

精确定时     已允许
Root          Ready
重启回连     已验证 / 未验证
Agent Guard   开启
```

快捷模板：

```text
[15 分钟后息屏]
[30 分钟后息屏]
[1 小时后息屏]
[今晚定时重启]
[Agent 完成后息屏]
```

### 17.3 Schedule Card

示意：

```text
┌─────────────────────────────────┐
│ 🌙 息屏                  已武装  │
│ 今天 02:30 · 精确               │
│ 还有 1 小时 12 分               │
│ 来源：本机 UI                   │
│                                 │
│ [暂停]                  [···]   │
└─────────────────────────────────┘
```

重启：

```text
┌─────────────────────────────────┐
│ ↻ 重启                  高风险  │
│ 每天 04:00 · 精确               │
│ Agent 运行时延后 15 分钟        │
│ 回连保护：通过                  │
│                                 │
│ [暂停]                  [···]   │
└─────────────────────────────────┘
```

关机：

```text
┌─────────────────────────────────┐
│ ⏻ 关机                  高风险  │
│ Agent task #backup 成功后 1 min │
│ 执行前 30 秒通知                │
│                                 │
│ [暂停]                  [···]   │
└─────────────────────────────────┘
```

### 17.4 State chips

统一：

```text
ARMED            已武装
PAUSED           已暂停
WAITING_PERMISSION 等待权限
WAITING_AGENT    等待 Agent
POSTPONED        已延后
MISSED           已错过
BLOCKED_SAFETY   安全保护阻止
FAILED           执行失败
```

### 17.5 新建流程

Step 1 — 动作：

```text
○ 息屏
○ 重启
○ 关机
```

Step 2 — 触发：

```text
倒计时 | 指定时间 | 重复 | Agent
```

Step 3 — 条件：

重启 / 关机默认显示：

```text
[x] Agent 运行时延后
    延后 15 分钟
    最长 2 小时

[x] 执行前 30 秒允许取消
[x] 要求精确定时
```

Step 4 — 风险确认：

```text
即将武装“每天 04:00 自动重启”

设备会断开连接。
当前 post-boot reconnect：已验证 / 未验证

5 ... 4 ... 3 ... 2 ... 1
[武装计划]
```

### 17.6 触发时 notification

```text
RootTools
将在 30 秒后重启设备
计划：Nightly reboot · 04:00

[取消]   [延后 15 分钟]
```

如果设备正在使用：

- notification 仍是主入口；
- App 前台可以额外显示 countdown banner；
- 不使用 Toast 高频刷屏。

---

## 18. Execution History / Audit

现有 `RootActionAuditStore` 只表达“Root 写动作”，而 schedule 还需要表达调度生命周期。

建议增加独立 history：

```kotlin
enum class ScheduledExecutionStatus {
    ARMED,
    FIRED,
    COUNTDOWN,
    CANCELED,
    SNOOZED,
    SKIPPED,
    BLOCKED,
    DISPATCHED,
    CONFIRMED_AFTER_BOOT,
    FAILED,
}
```

每条记录：

```text
executionId
scheduleId
action
source
plannedAt
firedAt
status
backend
guard reason
bootId
detail
```

RootActionAudit：

```text
只在真正触发 privileged mutation 时记录
```

ScheduleHistory：

```text
记录 arm / cancel / skipped / postponed / dispatched 生命周期
```

二者通过：

```text
executionId / auditId
```

关联。

---

## 19. External Automation API

现有 `ActionRouterReceiver` 先不直接开放 destructive schedule。

P0 可增加只读与 sleep：

```text
SCHEDULE_SLEEP
CANCEL_SCHEDULE
LIST_SCHEDULES   # 如果 broadcast callback 协议完善后
```

但更推荐在 `20-termux-developer-runtime.md` 的 scoped client credential 落地后再开放完整 API：

```json
{
  "action": "schedule.create",
  "target": "DEVICE_SLEEP",
  "trigger": {
    "type": "AFTER_DURATION",
    "seconds": 600
  }
}
```

destructive example 只允许 typed schema：

```json
{
  "action": "schedule.create",
  "target": "DEVICE_REBOOT",
  "trigger": {
    "type": "AT_TIME",
    "epochMillis": 1787169600000
  },
  "guardProfile": "REMOTE_SAFE_REBOOT"
}
```

服务端仍需重新验证所有 policy，不能信任 client 传入 `guardProfile` 就跳过安全检查。

---

## 20. Failure Semantics

### Alarm 未武装

```text
WAITING_PERMISSION
```

### Root 在执行时丢失

```text
FAILED_ROOT_UNAVAILABLE
```

不 retry destructive action。

### Agent active

```text
POSTPONED_AGENT_ACTIVE
```

达到最大 postpone：

```text
SKIPPED_AGENT_ACTIVE
```

### 用户取消 countdown

```text
CANCELED_BY_USER
```

recurring schedule：

- 当前 occurrence canceled；
- schedule 本身仍 enabled；
- 自动计算下一 occurrence。

### App 被 force-stop

Android force-stop 会影响 alarm / receiver 可用性。

UI 应明确显示系统限制，不做隐藏自保或 root 自动解除 force-stop。

### exact permission 被撤销

- exact alarm 会被系统取消；
- schedule definition 保留；
- UI -> `WAITING_PERMISSION`；
- 授权广播到来后统一 reconcile。

---

## 21. Test Matrix

### Pure JVM

`ScheduleOccurrenceCalculatorTest`

- 30 min countdown；
- daily next occurrence；
- weekday selection；
- timezone change；
- DST forward / backward；
- exact time already missed；
- recurring destructive action 不 catch-up；
- boundary at midnight；
- disabled schedule 无 next occurrence。

`SchedulePolicyTest`

- sleep + exact permission absent -> approximate allowed；
- reboot + exact required but absent -> blocked；
- poweroff without explicit master switch -> blocked；
- destructive action during boot grace -> blocked；
- Agent active -> postpone；
- max postpone exceeded -> skip；
- invalid negative duration rejected；
- too-short destructive countdown rejected。

`DestructiveActionGuardTest`

- same occurrence replay -> rejected；
- boot id changed -> previous reboot can confirm；
- same boot repeated dispatch -> rejected；
- missed reboot -> skip；
- next occurrence persisted before dispatch。

### Android integration

- PendingIntent identity stable；
- scheduleId-only receiver contract；
- exact alarm permission request / revoke / grant；
- BOOT_COMPLETED reconcile；
- TIMEZONE_CHANGED reconcile；
- MY_PACKAGE_REPLACED reconcile；
- foreground countdown starts from exact alarm；
- notification Cancel；
- notification Snooze。

### Samsung SM-S908E real device

#### Sleep

- screen awake -> 2 min timer -> screen off；
- AOD / Doze 场景；
- already screen off -> no accidental wake；
- App background；
- App process killed then alarm fires；
- exact permission denied -> state degradation correct。

#### Reboot

前置必须先完成 `04-adb-network.md` 的 post-boot reconnect gate。

- local UI one-shot reboot；
- T-30 countdown notification；
- cancel -> 不 reboot；
- snooze -> 15 min 后重新 countdown；
- actual reboot -> boot id changes；
- Root TCP restore；
- Tailscale reconnect；
- RootTools schedule reconcile；
- no second reboot after boot；
- recurring next run = future date。

#### Power off

需要物理可接触设备时执行：

- countdown cancel；
- actual power off；
- 手工开机；
- schedule history 恢复；
- 不补执行 missed poweroff。

---

## 22. Implementation Plan

## Phase A — Scheduler Core

1. `ScheduledAction` domain model；
2. `ScheduleOccurrenceCalculator` + JVM tests；
3. device-protected `ScheduledActionStore`；
4. `AlarmScheduler`；
5. exact alarm capability / permission UI；
6. restore receiver；
7. history store。

验收：

```text
能可靠创建 / 取消 / reboot 后恢复一个无副作用 test schedule
```

## Phase B — DEVICE_SLEEP

1. `PowerActionController`；
2. Shizuku UserService `sleepDevice()` semantic method；
3. Root fallback；
4. `PowerManager.isInteractive` verify；
5. 15 / 30 / 60 min quick timer；
6. Samsung Doze 真机验收。

这是最适合第一批上线的能力。

## Phase C — Destructive Safety Runtime

1. `DestructiveActionGuard`；
2. boot id / occurrence idempotency；
3. `PowerCountdownService`；
4. notification Cancel / Snooze；
5. RootPowerBackend dispatch semantics；
6. no retry / no fallback contract tests。

## Phase D — Reboot

前置：

```text
Root ADB boot restore PASS
Tailscale reconnect PASS
```

然后：

1. local scheduled reboot；
2. boot-id confirmation；
3. recurring reboot；
4. Samsung real-device reboot validation。

## Phase E — Power Off

1. explicit `Unattended power off` master switch；
2. power-off schedule；
3. physical-device validation；
4. shutdown history semantics。

## Phase F — Agent Events

等待 Managed Task / Agent runtime 有统一 lifecycle 后：

1. `AgentTaskEventSource`；
2. Agent finished trigger；
3. no-active-agent guard；
4. cancel sleep timer when a new Agent starts；
5. Agent scope model；
6. preset `Agent Night Mode`。

---

## 23. Recommended Product Presets

### Preset A — Agent 完成后息屏

```text
Trigger: all selected Agent tasks finished
Delay: 2 min
Action: DEVICE_SLEEP
Cancel when new task starts: yes
```

### Preset B — 夜间安全重启

```text
Trigger: daily 04:00
Action: DEVICE_REBOOT
Exact: yes
If Agent active: postpone 15 min
Max postpone: 2 h
After max: skip
Countdown: 30s
Post-boot reconnect required: yes
```

### Preset C — 工作完成后关机

```text
Trigger: selected Managed Task success
Delay: 1 min
Action: DEVICE_POWER_OFF
Only on success: yes
Countdown: 30s
Local unattended power-off switch: required
```

### Preset D — 临时睡眠计时器

```text
Trigger: after 15 / 30 / 60 min
Action: DEVICE_SLEEP
Approximate allowed: yes
```

---

## 24. Acceptance Criteria

- [ ] 不新增 Gradle module；
- [ ] `DashboardScreen.kt` 不继续承载完整 schedule UI；
- [ ] Receiver 不接受任意 shell / command；
- [ ] alarm Intent 只携带 trusted schedule id；
- [ ] exact alarm permission 有独立 capability state；
- [ ] exact permission 被撤销时不会伪装成 armed；
- [ ] schedule definitions 可在 reboot / package update 后恢复；
- [ ] destructive schedule 永不 catch-up；
- [ ] boot grace 可阻止刚启动就 reboot/poweroff；
- [ ] same occurrence 不会 dispatch 两次；
- [ ] sleep 使用 `KEYCODE_SLEEP`，不会把已关闭屏幕重新点亮；
- [ ] reboot / poweroff 不做自动 retry / fallback；
- [ ] reboot 用 boot id 在下一次启动后补充确认；
- [ ] high-risk action 有创建时确认；
- [ ] high-risk action 有运行时 Cancel / Snooze；
- [ ] Agent active guard 有 pure tests；
- [ ] Samsung SM-S908E 完成 sleep 真机验收；
- [ ] post-boot reconnect 通过后才开放 scheduled reboot；
- [ ] poweroff 在设备物理可接触条件下完成一次真实验收。

---

## 25. Recommended Priority

建议把这个功能列为 **P0/P1 交界的高价值基础设施**，但按风险拆开推进：

```text
P0.1 Scheduler Core
  ↓
P0.2 Scheduled Sleep
  ↓
P0.3 Destructive Safety Runtime
  ↓
P0.4 Post-boot reconnect gate
  ↓
P1.1 Scheduled Reboot
  ↓
P1.2 Scheduled Power Off
  ↓
P1.3 Agent / Managed Task triggers
```

不建议一开始直接做：

```text
Alarm -> su reboot
```

那样代码虽然很少，但会绕过项目现在已经建立的权限、审计、回连和 destructive action 安全体系。

---

## 26. External Technical References

- Android Developers — Schedule alarms / exact alarm permission:
  - https://developer.android.com/develop/background-work/services/alarms
- Android Developers — Android 14 exact alarm behavior:
  - https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Android Developers — Foreground service background-start exemptions:
  - https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android Developers — `DevicePolicyManager.lockNow()` / `reboot()`:
  - https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- AOSP — Android 16 `reboot` implementation (`-p` -> shutdown):
  - https://android.googlesource.com/platform/system/core/+/android16-release/reboot/reboot.c
- AOSP CTS / platform tests use `input keyevent KEYCODE_SLEEP` to make the device non-interactive.

