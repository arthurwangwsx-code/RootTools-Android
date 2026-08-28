# 默认数字助理与电源键入口

## 1. Problem / User Story

- 用户场景：RootTools 所在的 Android 手机需要把“长按电源键 -> 系统数字助理”作为高频入口，并能在 ChatGPT、小米语音助手及其它合格 VoiceInteractionService 之间切换。
- 现有问题：HyperOS 的系统设置 UI 可能隐藏部分第三方 Assistant；直接 Hook 电源键或 SystemUI 会增加 OEM 耦合、后台复杂度和失联风险。
- 成功标准：RootTools 能读取当前 Android `ROLE_ASSISTANT`、动态发现合格语音助理、通过 typed privileged boundary 切换并回读确认，同时不修改关机/重启按键策略。

## 2. Scope

### In scope

- 读取当前 `android.app.role.ASSISTANT` holder；
- 发现声明 `android.service.voice.VoiceInteractionService` 且使用 `android.permission.BIND_VOICE_INTERACTION` 的已安装候选；
- 通过 Shizuku/Sui 优先、RootShell fallback 的 typed route 切换默认 Assistant；
- 切换前二次确认、切换后回读校验；
- Root action audit 记录 before / after / backend / rollback hint；
- 读取 Xiaomi/MIUI `long_press_power_key` 以及 AOSP power long-press raw values，作为当前入口诊断信息；
- System 一级域提供独立配置页面。

### Out of scope

- 不 Hook `PhoneWindowManager` / SystemUI / input driver；
- 不通过 LSPosed 改写电源键事件；
- v1 不写 `long_press_power_key`，避免误伤 power menu / emergency / very-long-press 行为；
- 不允许 UI 输入任意 package name；只能选择现场发现的合格候选；
- 不把普通 `ACTION_ASSIST` activity 当成可写目标，避免 intent interceptor 被错误设为系统助理。

## 3. Current State / Device Evidence

### Xiaomi 14 — 2026-08-25

设备：`23127PN0CC / houji / Android 15 / HyperOS 2.0.207.0.VNCCNXM`

只读验证：

```text
settings system long_press_power_key = launch_voice_assistant
settings global power_button_long_press = 0
settings global power_button_very_long_press = 1

ROLE_ASSISTANT holder = com.miui.voiceassist
```

系统识别到的 `VoiceInteractionService` 包括：

```text
com.miui.voiceassist/com.xiaomi.voiceassistant.AssistInteractionService
com.arlosoft.macrodroid/.voiceservice.MacroDroidVoiceService
com.openai.chatgpt/com.openai.feature.assistant.impl.AssistantVoiceInteractionService
```

ChatGPT 当前安装包同时声明：

```text
android.service.voice.VoiceInteractionService
permission = android.permission.BIND_VOICE_INTERACTION
android.intent.action.ASSIST
```

因此 Xiaomi 14 当前无需按键 Hook：只要改变 `ROLE_ASSISTANT` holder，现有 `launch_voice_assistant` 入口就会跟随默认数字助理。

## 4. Capability / Permission

| Capability | Backend | Permission | Fallback | Failure UI |
|---|---|---|---|---|
| Assistant role read | Shizuku/Sui | shell/root UserService | RootShell | 显示 read error，候选发现仍可展示 |
| Assistant role write | Shizuku/Sui | shell/root UserService | RootShell | 保留 backend + detail，不静默吞错 |
| Voice service discovery | PackageManager | `QUERY_ALL_PACKAGES` | none | Empty state |
| Power-key raw state read | Settings provider | normal read | none | Unknown |

`ROLE_CONTROL` 属于 Framework capability；和 package/component/AppOps 一样优先使用 Shizuku/Sui，RootShell 只作为幂等目标状态写入的 fallback。

## 5. Architecture

```text
AssistantSettingsScreen
  -> AssistantSettingsViewModel
      -> AssistantRepository
          -> PackageManager candidate discovery
          -> PrivilegeRouter.getAssistantRoleHolder()
      -> AssistantController
          -> AssistantSelectionPolicy
          -> PrivilegeRouter.setAssistantRoleHolder()
              -> IPrivilegeUserService (Shizuku/Sui)
              -> RootShell fallback
          -> verify holder
          -> RootActionAuditStore
```

Privileged adapter 只暴露两个固定语义方法：

- `getAssistantRoleHolder()`；
- `setAssistantRoleHolder(packageName)`。

不存在任意 `cmd role ...` 或 shell text 入口。

## 6. Domain Model / API

- `AssistantCandidate`：package、label、VoiceInteractionService component；
- `AssistantSnapshot`：current holder、candidates、power key state、read backend/error；
- `AssistantSelectionPolicy`：invalid / not eligible / no-op / switch；
- `AssistantSwitchResult`：switched / already selected / invalid / not eligible / write failed / verify failed；
- `PowerKeyAssistantState`：OEM raw binding + AOSP raw behavior，仅诊断。

## 7. Safety / Rollback

- 风险等级：Caution，可逆的系统默认角色变更；
- 前值：每次动作前读取当前 holder；
- 回滚：audit 保存原 holder，用户可在同一候选列表中切回；
- 二次确认：必须；
- 写后校验：必须重新读取 holder 与 target 比较；
- 远程失联：不会修改 ADB、网络、电源菜单或 RootTools 自身启用状态；
- power key：v1 只读，保留 OEM 原始策略。

## 8. UI / UX

- 入口：`系统 -> 默认数字助理`；
- Current card：当前 label/package、role read backend、voice service；
- Power key card：显示是否检测到 `launch_voice_assistant` 和 raw values；
- Candidate cards：动态列举合格 VoiceInteractionService；
- 当前 holder 禁止重复切换；
- 非当前候选点击后先显示确认 Dialog；
- 失败时展示 backend detail；
- 默认英文 + `values-zh-rCN` 简体中文。

## 9. Test Matrix

- happy path：小米语音助手 -> ChatGPT -> 回读 holder 为 ChatGPT；
- no-op：目标与当前 holder 相同；
- unavailable：无 Shizuku/Sui 且 Root 不可用时明确失败；
- invalid input：`;`、`&&`、空格、换行等 package token 拒绝；
- not eligible：未出现在 qualified candidate 集合的 package 拒绝；
- fallback：ROLE_CONTROL 按 routing policy 支持 Shizuku ADB / Shizuku Root / Sui Root -> RootShell；
- rollback：ChatGPT -> 原 holder；
- regression：已有 package/component/AppOps routing 不改变；
- real device：Xiaomi 14 做真实 mutation；Samsung 做 discovery/read smoke test，并在需要时做可回滚 mutation。

## 10. Implementation Plan

1. pure selection policy + JVM tests；
2. `ROLE_CONTROL` routing capability；
3. typed AIDL / PrivilegeRouter adapter；
4. repository + controller + audit + verify；
5. ViewModel + Compose screen；
6. navigation + i18n；
7. quality / unit / lint / assemble；
8. Xiaomi 14 真机切换与恢复验证。

## 11. Acceptance Criteria

- [ ] System landing 可进入“默认数字助理”；
- [ ] Xiaomi 14 页面识别当前 `com.miui.voiceassist`；
- [ ] 页面动态显示 ChatGPT candidate；
- [ ] 页面识别 `long_press_power_key=launch_voice_assistant`；
- [ ] 切换 ChatGPT 成功且 holder 回读一致；
- [ ] 长按电源键实际唤起 ChatGPT；
- [ ] 可从页面切回原 Assistant；
- [ ] audit 有 before/after/backend/rollback；
- [ ] JVM policy tests、quality guard、lint、debug assemble 通过；
- [ ] 不改变 power button long/very-long press raw settings。

## 12. Open Questions / Future

- Samsung Side Key、Pixel AOSP power behavior 等 OEM 入口可在后续加入 adapter；
- 若未来需要在 RootTools 内开启/关闭“长按电源键呼出助理”，应单独建立 OEM PowerKeyPolicy，并保存可验证的 before-state，不能直接在此页面裸写 Settings。
