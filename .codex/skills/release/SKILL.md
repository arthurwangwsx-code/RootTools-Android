---
name: roottools-release
description: Run RootTools release readiness checks, artifact validation, version notes, and residual-risk reporting.
---

# RootTools Release

Release readiness requires:

1. intended working-tree scope is understood;
2. `python3 scripts/quality_guard.py` passes;
3. unit tests, Android lint, and debug assemble pass;
4. full `scripts/build.sh` passes when release artifact validation is required;
5. relevant Samsung real-device acceptance passes for privileged changes;
6. versionCode/versionName and delivery ledger are correct;
7. generated artifacts and residual risks are explicitly reported.

Do not commit, tag, push, or publish unless the user explicitly requests that action.
