---
name: roottools-device-validation
description: Validate RootTools on rooted Android devices safely, starting read-only and escalating only to explicitly required mutations.
---

# RootTools Device Validation

1. Read `docs/12-final-validation.md` and the feature-specific acceptance matrix.
2. Confirm device identity, Android build, root runtime, and current connection path.
3. Run read-only probes first.
4. Snapshot the relevant before-state for every mutation.
5. Execute only the feature action under test; do not combine unrelated device changes.
6. Verify UI state, backend attribution, system state, audit record, and rollback.
7. Restore the before-state when the test is not intended to persist configuration.
8. Record OEM-specific differences in the feature document rather than hiding them in UI branches.

Never disable the active remote ADB path without an alternate recovery path.
