# Adaptive Thermal Validation — 2026-08-25

## Scope

Validation for the AI-handset adaptive thermal work documented in
`docs/27-adaptive-ai-handset-thermal.md`.

This validation intentionally starts read-only on the Xiaomi 14 because a HyperOS system upgrade
is planned. No Xiaomi private charging, refresh-rate, Memory Extension, or thermal vendor setting
was mutated during this validation.

## Device

- Model: Xiaomi 14 / `23127PN0CC`
- Codename: `houji`
- Android: 15
- Current system family: HyperOS 2
- ADB paths observed: USB + Tailscale/root TCP (`100.110.5.86:5555`)

## 1. Thermal source-of-truth validation

Read-only probe:

```text
adb -s 100.110.5.86:5555 shell dumpsys thermalservice
```

Observed cached section:

```text
Thermal Status: 0
battery = 37.8°C
skin = 41.475°C
CPU cached sensors = roughly 81.5–89.9°C
```

Observed `Current temperatures from HAL` in the same dump:

```text
battery = 34.7°C
skin = 38.047°C
CPU current sensors = roughly 38.7–42.6°C
```

Result: **PASS**.

The old parser could miss Xiaomi lower-case `battery` / `skin` names and had no explicit current-HAL
preference. `ThermalProbeParser` now detects the current block, uses it when available, and only
falls back to cached values when no current block exists.

At the observed interactive skin temperature of about 38.0°C, `AdaptiveThermalPolicy` selects
`WARM`, which trims the inefficient performance/Prime high-frequency tail while preserving the
efficiency-cluster peak. The stale cached values therefore no longer force a false severe decision.

## 2. Pure policy / parser tests

The repository has unrelated in-progress Assistant changes that currently prevent the whole Android
module from reaching unit-test execution. To validate the new pure logic independently, the exact
new source and JUnit tests were compiled directly with the locally cached Kotlin 2.4.10 compiler,
JDK 21, JUnit 4.13.2, and the same JVM target family.

Result:

```text
JUnit version 4.13.2
..........
Time: 0.297

OK (10 tests)
```

Covered:

- Xiaomi lower-case sensors;
- current HAL wins over stale cache;
- Samsung `AP/BAT/SKIN` fallback;
- interactive cool device -> Normal;
- unattended device -> Warm floor;
- charging heat -> Warm;
- skin 40°C -> Moderate;
- skin 42°C -> Severe;
- OEM Thermal severe -> Severe;
- first install / same fingerprint / changed fingerprint compatibility handling.

## 3. Project guards

`scripts/security_guard.py`: **PASS**.

`scripts/quality_guard.py`: the adaptive-thermal feature introduced one initial feature-to-legacy
policy dependency from `PerformanceScreen`; it was removed by moving the decision result through the
performance UI state. A repeat guard no longer reports the performance feature.

The remaining quality-guard failures are pre-existing/in-progress work outside this change:

```text
DeveloperRuntimeScreen.kt > 900 lines
AssistantController.kt -> legacy data/policy dependency
DeveloperRuntimeViewModel.kt -> legacy data/policy dependency
```

Those files were not modified as part of this work.

## 4. Whole-module compile blocker

The Android module currently has unrelated in-progress Assistant navigation changes. The first full
compile attempt reached `compileDebugKotlin` and stopped on:

```text
ToolboxNavigation.kt: missing ToolId.ASSISTANT branch
DashboardScreen.kt: missing ToolId.ASSISTANT branch
```

No adaptive-thermal Kotlin compile error was reported before those blockers. A second incremental
compile is used as the final check after the UI-state boundary correction; its result should be
recorded here when the command completes.

## 5. Safety result

PASS for the current implementation boundary:

- no OEM Thermal disable;
- no governor replacement;
- no `scaling_min_freq` mutation;
- no CPU hotplug / binding;
- no charging-current mutation;
- no Xiaomi private thermal-node mutation;
- no automatic Memory Extension mutation before HyperOS 3 re-probe;
- vendor CPU cap remains authoritative;
- system fingerprint change invalidates Root Tools ownership/baseline state before re-application.

## 6. HyperOS 3 post-upgrade acceptance

After the planned upgrade, validate in this order:

1. Root / Magisk availability;
2. current HAL thermal sensor names and values;
3. CPU policy IDs, frequency tables, governor, and vendor caps;
4. Root Tools compatibility event after fingerprint change;
5. absence of OS2 `owned_max_*` / baseline assumptions;
6. AUTO low-temperature interactive behavior;
7. screen-off Warm-floor behavior;
8. 15–30 minute temperature trend under ChatGPT / automation load;
9. only then probe HyperOS 3 charging, refresh-rate, and Memory Extension adapters for optional
   typed integration.

