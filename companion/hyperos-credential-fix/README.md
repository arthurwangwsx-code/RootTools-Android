# HyperOS Credential Fix companion

This module builds the independent `com.arthur.hyperos.credentialfix` Xposed APK from the canonical RootTools repository.

It is intentionally not merged into the main RootTools APK: LSPosed loads the module into Xiaomi Credential Manager, while the RootTools app runs in its own process and privilege boundary.

## Scope

- Target package: `com.android.credentialmanager`.
- Target profile: Xiaomi clone profile user `999` only.
- Current product exception: the broken ChatGPT credential-selection flow only.
- Every unrelated package, profile, app name and credential state remains on the OEM implementation.

The `tools/LspDbTool.java` helper mutates the local LSPosed configuration database and is retained for controlled device recovery only. It is not compiled into either APK and must not be invoked automatically.

## Build

```bash
./gradlew :companion:hyperos-credential-fix:assembleDebug
```

The output APK is under `companion/hyperos-credential-fix/build/outputs/apk/`. Installation, LSPosed enablement and scope changes remain separate device-validation gates.
