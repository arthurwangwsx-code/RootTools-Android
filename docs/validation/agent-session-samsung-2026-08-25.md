# Agent Session / Samsung 真机验收 — 2026-08-25

## Device

- Samsung Galaxy S22 Ultra
- model: `SM-S908E / b0q`
- Android 14 / API 34
- One UI 6.1 (`ro.build.version.oneui=60101`)
- Magisk Root: `uid=0(root)`
- transport: USB ADB

## Build under test

- package: `com.arthur.roottools`
- version: `0.3.0`
- targetSdk: 35
- Agent presence uses Android 14 `specialUse` foreground service.

## Verified so far

### Notification fallback without overlay permission

- `SYSTEM_ALERT_WINDOW` initially default / not granted;
- observation session starts successfully;
- `AgentSessionService` reports `isForeground=true` and foreground type `specialUse`;
- active notification channel: `agent_session_running`, importance 2;
- notification title/body: `后台 Agent / 正在观察后台任务。`;
- actions: `暂停 / 停止`;
- no `TYPE_APPLICATION_OVERLAY` window exists before permission is granted.

### Standard One UI overlay permission

- permission was granted through the Samsung Settings `悬浮窗` screen;
- no Root/AppOps silent grant was used;
- appop afterwards: `SYSTEM_ALERT_WINDOW: allow`.

### Collapsed / expanded overlay

- collapsed WindowManager type: `APPLICATION_OVERLAY`;
- collapsed size is roughly 58dp square on the current 1080×2316 logical display;
- collapsed process CPU sample: 0.0%;
- tapping the collapsed surface expands to roughly 320dp wide;
- expanded position is clamped into logical screen bounds after a Samsung high-DPI offset issue was found;
- expanded state is persisted as `overlayMode=EXPANDED`.

### Preview and Samsung safety behavior

- Shadow Display created as 720×1600 virtual display `displayId=6`;
- Samsung `cmd display get-displays` output differs from HyperOS ids-only output; shared probe was fixed to parse full `Display id N:` form first and keep ids-only as fallback;
- after the fix, typed `SHADOW_STATUS` returns `running=true / processAlive=true / displayActive=true`;
- Chrome launched on Display 6 and AgentSessionState updated to `targetPackage=com.android.chrome`, `currentStep=正在后台运行 Chrome。`;
- expanded presence generated `/data/local/tmp/roottools-shadow/preview.jpg` (~32 KB) while RootTools CPU sampled 0.0%;
- Chrome default-browser role prompt caused One UI to set `mForceHideNonSystemOverlayWindow=true`; RootTools overlay stayed created but policy-hidden. This is accepted anti-tapjacking behavior and must not be bypassed.

## Bugs found and fixed during Samsung validation

1. **OEM display probe**: Samsung full display output was not parsed, causing a valid VirtualDisplay to be reported inactive.
2. **Overlay expanded position**: physical/logical display scaling could place a 320dp card partly outside the logical screen; overlay now clamps x/y by logical bounds.
3. **Notification detail navigation**: a running `MainActivity` did not re-consume `open_screen=agent-session` from `onNewIntent`; navigation now receives repeatable external-screen requests. Final device regression is pending installation of the latest build.

## Remaining acceptance work

- [ ] install latest notification-navigation fix on Samsung and verify notification tap while RootTools is already alive;
- [ ] Pause → notification action changes to Resume → Resume restores Running;
- [ ] Stop removes AgentSessionService, active notification and overlay without stopping an independently owned Shadow Display;
- [ ] app/process restart recovery;
- [ ] screen-off / lock-screen persistence and no-wake behavior;
- [ ] latest full unit + lint + assemble + security gate;
- [ ] final cleanup and idle CPU check.

## Xiaomi follow-up boundary

Do not add HyperOS-specific business state before this generic matrix is complete. Xiaomi Focus Notification / HyperIsland must consume the same `AgentSessionState` and keep standard notification + overlay as fallback.
