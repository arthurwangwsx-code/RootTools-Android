# ADR-0002: Logical Modularization Before Gradle Module Proliferation

## Status

Accepted — 2026-08-20

## Context

RootTools has many product domains but is still small enough that splitting every card into its own
Android library would create more build files, dependency APIs, resource boundaries, and AI-edit
surface than the project currently needs. The immediate maintainability problems are giant host
files and mixed feature ownership, especially `DashboardScreen.kt`, `DashboardViewModel.kt`, and
App Control presentation code.

At the same time, staying structurally flat would keep increasing merge conflicts and let unrelated
features depend on each other's implementation details.

## Decision

Apply modularization in two stages.

### Stage 1 — package and ownership boundaries

Use explicit `app`, `core`, and `feature` packages, a process composition root, feature-specific
presentation/state files, a shared design system, and dependency-direction rules while retaining a
single Gradle `:app` module.

### Stage 2 — evidence-based Gradle extraction

Only create a Gradle library when at least one concrete boundary exists: meaningful reuse,
compilation/isolation benefit, stable API ownership, independent testing value, or sustained
parallel-development conflict that package boundaries cannot solve.

The first candidates, if the evidence threshold is reached, are:

```text
:core:model
:core:privilege
:core:designsystem
```

Feature modules are not created merely because a feature has a home-screen card.

## Consequences

- Current work targets the actual hot spots rather than moving the same coupling into many modules.
- Gradle configuration remains small while architecture contracts mature.
- The package structure is intentionally migration-friendly if physical modules become justified.
- The engineering ledger records extraction triggers so module creation remains a deliberate
  architecture decision rather than an aesthetic preference.
