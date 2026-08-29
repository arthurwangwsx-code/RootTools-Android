# 2026-08-29 RootTools consolidation validation

## Repository and history

- Canonical repository: `/Users/ai/project/mobile-android/RootTools`
- Independent source histories were imported without squash.
- Source recovery commits `fbec3d2`, `a2f5895`, `f3cd30c`, and `32923ba` are reachable from canonical `main`.
- The former Net Tools source snapshot was removed after its production mapping was recorded in `docs/network-inspection/consolidation-map.md`.
- Former sibling repositories were clean before archival and were moved to recoverable macOS Trash locations named `RootTools-consolidated-*-20260829`.
- `AdbTools`, `androidDemo`, and `cfc-master` remain outside scope because they are not duplicate RootTools products.

## Unified build gate

Command:

```bash
ROOTTOOLS_FORCE_BUILD=1 GRADLE_OPTS='-Dorg.gradle.workers.max=1' bash scripts/build.sh
```

Result: **PASS**, 412 Gradle tasks in 9m45s.

Verified layers:

- repository quality guard and privileged security guard;
- `:app` unit tests, lint, debug/release assemble and Kover report;
- Network Inspection pure JVM tests;
- NFC and Background Server unit tests, lint and debug/release assemble;
- HyperOS Credential Fix lint and debug/release assemble;
- core coverage thresholds.

Generated debug APKs and SHA-256:

| Package | Artifact | SHA-256 |
|---|---|---|
| `com.arthur.roottools` | `app-debug.apk` | `4d147bc1869f812bac577339a2eb40e7be04d939730cc9925eabc3a580f8f146` |
| `com.arthur.nfclab` | `nfc-tools-debug.apk` | `5276cc0ff584cda0c367ba44ada926b49401268906e12f2d404de09f976a1bbc` |
| `com.aibox.backgroundserver` | `background-server-debug.apk` | `aa34fb33cd195fe49d4b9f654997bbac9f1a9f06d6b8722d5bc6f994d7526d44` |
| `com.arthur.hyperos.credentialfix` | `hyperos-credential-fix-debug.apk` | `d3d8b8a8d06e3027d232f0de9aaa5e0c07220c5f0eca52df84d11a63f240f663` |

APK inspection also verified RootTools `libpcapd.so` and `libroottools_pcap_bridge.so` for arm64-v8a/x86_64, NFC's arm64 root diagnostic bridge, and WireGuard native libraries for all four Android ABIs.

## Device boundary

Read-only device: Mi 9T Pro (`f27e2c0f`), Android 11/API 30, Magisk root, boot complete.

The device already has `com.arthur.roottools`, the legacy `com.arthur.nettools`, `com.arthur.nfclab`, `com.aibox.backgroundserver`, and `com.pcapdroid.mitm` installed. No package was uninstalled and no application data was modified.

The installed RootTools certificate digest is `58b74dbdf47a6a8035be42395c29fd9345af3d112f4c7dfee447e101968e186e`; the new local debug APK digest is `93aa0931f802929b88c3825bcb8a3bd0e984bf1017b8b5c903dade1fe447a877`. Because Android cannot safely update across signing identities, the newest APK was not installed. Current Network Inspection capture/TLS, Background, NFC, and Xposed runtime acceptance therefore remains a separate signed-device gate; historical evidence is not promoted to current acceptance.
