# ADR-0001: Composition Root Before a DI Framework

## Status

Accepted — 2026-08-20

## Context

RootTools is still a single `:app` Android module, but privileged infrastructure had begun to be
constructed independently by the main ViewModel, Quick Settings tiles, widgets, boot receivers,
automation receivers, and the CPU policy service. That pattern weakens process-level lifecycle
guarantees for `RootShell`, Shizuku/Sui Binder state, audit storage, and typed controllers.

The project needs dependency ownership and test seams now, but adding Hilt/Koin or another DI
framework would also add generated code, plugin/configuration surface, and migration work while the
feature/package boundaries are still changing quickly.

## Decision

Use an explicit application composition root:

```text
RootToolsApp
  -> AppContainer
       -> RootShell
       -> ShizukuBridge / UserService
       -> PrivilegeRouter
       -> stores / repositories / controllers
```

Android entrypoints obtain process dependencies from `RootToolsApp/AppContainer`. Feature-specific
controllers that need distinct audit attribution are created by typed factory methods such as
`createAdbController("QuickTile")` rather than by constructing a second privileged graph.

No DI framework is introduced at this stage.

## Consequences

- Root/privilege infrastructure has an explicit process owner.
- ViewModels and Android entrypoints stop owning dependency construction.
- Tests can progressively target constructors/factories without a framework test harness.
- `AppContainer` can become smaller feature factories as feature boundaries stabilize.
- If constructor graphs or module count later make manual wiring materially costly, a DI framework
  can be reconsidered with migration evidence instead of being adopted preemptively.

## Guardrail

`scripts/quality_guard.py` rejects direct `RootShell()` construction under UI, Tile, Widget,
Receiver, Service, and Boot entrypoint packages.
