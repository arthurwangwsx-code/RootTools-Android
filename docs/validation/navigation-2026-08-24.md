# Xiaomi 14 — Product Navigation / Home UX Validation

Date: 2026-08-24
Device: Xiaomi 14 (`houji`, `23127PN0CC`)
OS: Android 15 / HyperOS 2.0
Connection: Root TCP ADB over Tailscale

## Scope

Validate the new 5-domain product navigation and Home UX from `23-product-navigation-and-home.md` without changing privileged behavior.

## Build / install

- `adb install -r app-debug.apk`: PASS
- Active management port after install: `5555`
- Tailscale endpoint remained reachable throughout validation.
- Installed APK was byte-identical to the locally built APK during the initial navigation validation (`SHA-256` comparison PASS).

## Home visual state

### Loading

Screenshot: `navigation-2026-08-24/home-loading.png`

Verified by on-device screenshot + macOS Vision text recognition:

- `RootTools` title visible;
- loading verdict is `正在读取设备状态`;
- CPU/Skin live metrics use placeholder instead of producing a false health warning;
- four quick actions are visible;
- five navigation labels are visible at the bottom.

### Ready

Screenshot: `navigation-2026-08-24/home-final.png`

Stable sample after validation load:

- verdict: `设备状态稳定`;
- CPU about 5% in captured UI state;
- Attention empty-state is visible;
- Recent Activity is visible below Attention;
- bottom labels `首页 / 应用 / 设备 / 诊断 / 系统` are all recognized and not clipped.

## Five top-level domains

| Domain | Screenshot | First-screen evidence | Result |
|---|---|---|---|
| Home | `home-final.png` | Verdict / Quick access / Attention / Recent activity | PASS |
| Apps | `apps.png` | Permissions / Startup / App Control / Ad Governance / Components / AppOps | PASS |
| Device | `device.png` | Performance / Shadow Display / ADB / Network / Storage / Battery | PASS |
| Diagnostics | `diagnostics.png` | Health Dashboard / Process Diagnostics / Environment Integrity | PASS |
| System | `system.png` | Root Modules / Common Actions / Shizuku-Sui / Developer Runtime | PASS |

The 1200×2670 portrait UI keeps all five bottom labels visible with approximately even spacing. No bottom-label truncation was observed.

## Navigation behavior

### Multiple back stacks

Sequence:

```text
Apps
  -> App Control
Device
  -> Performance
Apps
```

Observed result: switching back to Apps restored App Control rather than returning to the Apps landing page.

Evidence:

- `apps-detail.png`
- `performance-detail.png`
- `apps-restored.png`

Result: PASS.

### Feature back navigation

External Integrity entry -> Android Back returned to the Diagnostics landing page instead of Home/Activity exit.

Evidence: `integrity-back.png`.

Result: PASS.

### Typed external entry regression

`MainActivity.EXTRA_OPEN_SCREEN`:

- `adb` -> ADB Control Center under Device: PASS (`deeplink-adb.png`)
- `integrity` -> Environment Integrity under Diagnostics: PASS (`deeplink-integrity.png`)

## Performance / overhead

Three cold starts after navigation migration:

```text
1361 ms
1522 ms
1407 ms
```

Median: 1407 ms.

RootTools CPU observed during a foreground validation sample: about 6.8%. After pressing Home and waiting 5 seconds: `0.0%`.

Residual-process scan after UI validation found the app process only; no leftover RootTools `tr` or `timeout` process was present.

## Known limitations / follow-up

- HyperOS kills `uiautomator dump --compressed` with exit 137 on this device. Visual verification therefore uses real `screencap` images plus Vision text recognition, not OCR generated from stale XML.
- Material 3 Adaptive Navigation Suite is compiled into the shell, but this round validates the compact Xiaomi 14 bottom-navigation presentation only. Expanded-window Navigation Rail remains a future tablet/foldable visual sample rather than a Xiaomi 14 acceptance blocker.
- Existing Developer Runtime quality-guard debt (`DeveloperRuntimeScreen.kt` > 900 lines and legacy dependency count) predates this feature and is unchanged.
