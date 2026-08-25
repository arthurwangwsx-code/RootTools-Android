# Xiaomi 14 Rolling Lag Forensics Validation — 2026-08-25

## Device

- Xiaomi 14 / `houji`
- Model: `23127PN0CC`
- HyperOS: 2.0.207.0 / Android 15
- Root: Magisk available
- Validation transport: remote ADB `100.110.5.86:5555`
- Installed build: `app/build/outputs/apk/debug/app-debug.apk`

## Build validation

Focused JVM tests passed:

```text
CpuPolicyPollingPolicyTest: 4 tests / 0 failures / 0 errors
LagForensicsPolicyTest:     4 tests / 0 failures / 0 errors
```

Debug APK assembled successfully and was installed over the existing 0.3.0 package. Launch produced no new RootTools crash or ANR.

Lint report contains 102 warnings/issues and **0 Fatal/Error**. Project quality guard still reports pre-existing Developer Runtime debt unrelated to this feature.

## Background CPU

After returning RootTools to Home/background, eight consecutive `top` samples reported:

```text
0.0% CPU
0.0% CPU
0.0% CPU
0.0% CPU
0.0% CPU
0.0% CPU
0.0% CPU
0.0% CPU
```

To measure the actual sampling pulse rather than only idle moments, `/proc/<pid>/stat` CPU ticks were sampled across ~124 seconds:

```text
08:05:12 cpu_ticks=814
08:05:43 cpu_ticks=814
08:06:14 cpu_ticks=818
08:06:45 cpu_ticks=818
08:07:16 cpu_ticks=818
```

Only four process CPU ticks were added across the full window, concentrated around one scheduled polling pass. The following minute returned to zero additional process CPU ticks.

## Wakeup / scheduling behavior

- No RootTools AlarmManager entry was found.
- No RootTools periodic JobScheduler job was found.
- No continuously held RootTools WakeLock was found.
- `dumpsys power` only showed short system NotificationManager acquire/release pairs while posting the foreground notification during startup.
- Notification refresh is now suppressed when the notification text did not change.

## Root child process safety

After background sampling, the privileged-process scan found no residual:

```text
tr
timeout ... sh -c
```

This preserves the earlier RootShell process-group timeout fix and avoids reintroducing the 41-hour orphan-child failure mode.

## Pressure / thermal state

At the end of validation:

```text
Memory PSI some avg10 = 0.00
Memory PSI full avg10 = 0.00
IO PSI some avg10     = 0.00
IO PSI full avg10     = 0.00
Thermal Status        = 0
Battery temperature   ≈ 33.0°C
```

During the first background observation the battery temperature moved from about 32.2°C to 32.9°C while the device was otherwise active. There was no thermal-status escalation and no evidence of a RootTools-driven sustained temperature rise.

## Memory

With the debug UI opened and then backgrounded:

```text
TOTAL PSS        ≈ 133–135 MB
Private Dirty    ≈ 43–45 MB
Java Heap PSS    ≈ 14 MB
Native Heap PSS  ≈ 18 MB
Code PSS         ≈ 70 MB
```

The large RES figure from `top` was mostly shared/code mappings. Debug tooling and the unminified debug APK account for a large part of the code mapping. A process state with no Activity/ViewRoot present measured about 110 MB PSS, confirming that UI residency contributes some memory but there is no sign of an unbounded heap leak.

The release build already has R8/resource shrink configured. A fresh release shrink was attempted as an optional memory/size optimization, but R8 was intentionally stopped after a long build because the validated debug APK was already installed and the release optimization was not required for functional delivery.

## Installed runtime state

Final device state after validation:

- RootTools installed and launchable;
- `CpuPolicyService` running as a foreground service;
- RootTools returned to background/Home;
- latest `top` sample: 0.0% CPU;
- no RootTools crash/ANR observed after install;
- no residual privileged `tr` / `timeout` child found.

## Remaining validation

Full physical reboot validation of the new `BOOT_COMPLETED` / `USER_UNLOCKED` forensics restore path was not performed in this pass because it would disrupt the actively connected device. The path is compiled and shares the existing BootReceiver, but a later controlled reboot should confirm OEM foreground-service behavior after boot.
