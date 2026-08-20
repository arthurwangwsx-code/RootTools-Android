---
name: roottools-refactor
description: Reduce RootTools structural debt without changing product behavior or privileged semantics.
---

# RootTools Refactor

1. Record behavior that must remain unchanged.
2. Prefer one boundary at a time: screen extraction, state extraction, composition-root extraction, or shared component extraction.
3. Do not mix a refactor with new privileged capability.
4. Preserve public/typed controller contracts whenever possible.
5. Add characterization tests before moving ambiguous policy logic.
6. Keep legacy debt ceilings moving downward; never raise them to make a change pass without documenting an explicit exception.
7. Verify after every extraction batch.

Priority debt targets are `DashboardScreen.kt` and `DashboardViewModel.kt`.
