# RootTools Network Inspection Review Checklist

> Raw/TLS device evidence below was produced by the imported legacy package. Canonical `com.arthur.roottools` acceptance remains open until explicitly rerun and recorded in [validation.md](./validation.md).

Use this file as the entry point for code/product review.

## Source quality

- [x] Kotlin + Jetpack Compose + Material 3 application
- [x] RootTools domain navigation and nested inspection tabs
- [x] Dedicated ViewModels + repositories/engines + `StateFlow` state model
- [x] Debug and Release builds succeed
- [x] Android Lint succeeds
- [x] Unit tests cover PCAP parsing and plaintext/redaction parsing
- [x] Target SDK 37
- [x] Capture/interception data excluded from Android cloud backup and device transfer

## Root capture

- [x] Root detection
- [x] Whole-device tcpdump mode
- [x] PCAPdroid `pcapd` integration
- [x] True per-UID packet filter
- [x] Root-side Unix-socket/PCAP bridge for MIUI SELinux compatibility
- [x] DNS / TCP / UDP / TLS-SNI / QUIC / HTTP / ICMP local classification
- [x] PCAP + JSON + log persistence
- [x] Full session/detail UI

## Raw validation

- [x] 18 applications included in the test matrix
- [x] 15 applications produced non-zero traffic in the short validation window
- [x] Screenshots stored per tested app
- [x] PCAP/JSON/log evidence stored in `artifacts/validation/`
- [x] Aggregate Markdown and JSON report generated

## TLS decryption product path

- [x] Official PCAPdroid MITM add-on managed as an independent runtime
- [x] Add-on CA requested through Messenger IPC
- [x] Add-on CA imported into the legacy Net Tools device path
- [x] PEM CA staged through reversible Magisk system overlay
- [x] System trust verified after reboot on Mi 9T Pro
- [x] Per-UID transparent TCP redirect implemented
- [x] Optional per-UID UDP/443 reject for QUIC → TCP fallback
- [x] Live add-on CA fingerprint checked before every new session
- [x] Plaintext framing parser implemented for HTTP / WS / TCP / errors
- [x] Common secret-bearing headers/query values redacted in UI/metadata
- [x] Raw payload storage explicitly marked sensitive
- [x] Interception history persisted and reviewable after app restart
- [ ] Final newest RootTools build Chrome plaintext request/response acceptance run (see [validation.md](./validation.md))

## Navigation / interaction

- [x] Overview
- [x] Traffic / Capture
- [x] Traffic / Sessions
- [x] Raw capture session detail
- [x] TLS Decrypt readiness workflow
- [x] Target-app picker
- [x] QUIC/restart/payload compatibility switches
- [x] Live HTTP/extracted event view
- [x] Persisted decryption history
- [x] Decryption session detail
- [x] Plaintext event detail
- [x] Certificate Manager
- [x] Settings
- [x] Diagnostics / stale-rule cleanup
- [x] About / open-source attribution
- [x] System light/dark theme support

## Evidence directories

```text
artifacts/validation/       raw 18-app validation
artifacts/device-export/    pulled on-device RootTools data
artifacts/runtime/          MITM add-on/runtime artifacts
artifacts/product-review/   product screenshots which passed page validation
artifacts/decryption/       reserved for final plaintext acceptance evidence
```

`artifacts/` is intentionally Git-ignored because captures can contain private traffic.

## Known deliberate non-goals for this version

- non-root `VpnService` capture
- automatic Frida/LSPosed certificate-pinning bypass
- protobuf/gRPC schema-aware decoders
- PCAPNG/HAR export
- MCP/localhost query API

These remain explicit extension points rather than partially implemented UI promises.
