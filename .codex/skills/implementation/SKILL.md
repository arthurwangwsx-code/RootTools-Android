---
name: roottools-implementation
description: Implement RootTools features with testable policy first, typed privileged boundaries, i18n, and incremental verification.
---

# RootTools Implementation

Follow this order for non-trivial changes:

```text
contract / existing docs
  -> pure policy / parser tests
  -> backend adapter
  -> typed repository/controller
  -> audit + rollback
  -> feature state
  -> Compose UI
  -> targeted tests
  -> quality guard + lint + assemble
  -> device validation when required
  -> docs / ledger
```

## Hard rules

- Do not add user-visible Kotlin string literals; use resources.
- Do not add unrelated code to `DashboardScreen.kt` or `DashboardViewModel.kt`.
- Do not bypass `PrivilegeRouter` for framework operations.
- Do not accept arbitrary shell text from UI/Intent/Tile/Widget.
- Do not silently retry destructive writes on another backend.
- Do not overwrite unrelated working-tree changes.

Run `python3 scripts/quality_guard.py` before the Gradle gate.
