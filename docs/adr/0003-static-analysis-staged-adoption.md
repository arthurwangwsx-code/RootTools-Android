# ADR-0003: Stage Static Analysis Adoption Around the Current Kotlin Toolchain

## Status

Accepted — 2026-08-20

## Context

RootTools currently builds with Kotlin 2.4.x, AGP 9.x, and Gradle 9.x while several large legacy
files are being actively split by multiple AI tasks. Adding an aggressive formatter or a new
compiler-adjacent static-analysis plugin across the entire tree right now would create a large
mechanical diff and increase merge conflicts with feature work.

At the same time, postponing all automated analysis would allow new debt to accumulate.

## Decision

Use a staged quality stack now:

1. `.editorconfig` for the canonical baseline style;
2. Android Lint for Android/API/resource issues;
3. `scripts/quality_guard.py` for architecture, file growth, i18n, and dependency direction;
4. `scripts/security_guard.py` for privileged/exported-component invariants;
5. Kover + `scripts/coverage_guard.py` for pure/high-risk core regression;
6. `git diff --check` during local validation for whitespace errors.

Do **not** run a whole-tree formatter migration while `DashboardScreen`, `DashboardViewModel`, and
App Control are still being split. Re-evaluate Spotless/ktlint or a stable Detekt 2.x release after
the legacy host files are below their extraction thresholds and the active feature branches have
been consolidated.

## Consequences

- New architecture/security/i18n debt is blocked immediately without reformatting unrelated code.
- Current AI work produces smaller diffs and fewer formatting-only conflicts.
- Formatting remains partially editor-driven until the migration gate is reached.
- The decision is explicitly temporary; it must be revisited rather than silently becoming the
  permanent absence of a formatter.

## Revisit gate

Re-evaluate when all are true:

- `DashboardScreen.kt < 1500 LOC`;
- `DashboardViewModel.kt < 700 LOC`;
- App Control screen ownership is split into stable files;
- no high-priority feature branch is carrying a large formatting-sensitive diff.
