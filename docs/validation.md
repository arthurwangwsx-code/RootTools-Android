# Net Tools Validation Ledger

This ledger separates **verified on-device facts** from implementation that has only passed build/unit tests.

## Raw capture acceptance

Target device: Mi 9T Pro, Android 11, Magisk root.

The per-app backend was validated with real UID-filtered `pcapd` output. A YouTube acceptance run captured 156 packets / roughly 132 KB with the target UID filter active, while `pcapd` reported no packet drops.

A broader automated matrix produced evidence for 18 installed applications. Fifteen generated non-zero traffic during the short launch window; Google Play, WeChat, and NetEase Music produced zero matching packets in that particular cold-window run and remain recorded as zero rather than being hidden.

Artifacts:

- `artifacts/validation/REPORT.md`
- `artifacts/validation/summary.json`
- `artifacts/validation/<package>/screen.png`
- `artifacts/validation/device-captures/*.pcap`

Applications included in the matrix:

YouTube, Bilibili, Chrome, Google Play, WeChat, WeType, Google Search, Maps, Gmail, Telegram, WhatsApp, ChatGPT, Instagram, Xiaohongshu, Zhihu, JD, Taobao, and NetEase Music.

## Certificate / runtime acceptance

Verified on the same Redmi device:

- official stable PCAPdroid MITM add-on installed as `com.pcapdroid.mitm`
- add-on Messenger service successfully bound from Net Tools
- add-on-generated mitmproxy CA successfully requested and imported
- PEM CA staged into a reversible Magisk system-trust module
- after reboot, the CA was visible in the active Android system trust store as `81c450f1.0`
- MIUI background policy for the MITM add-on was switched to unrestricted during first-run setup

## TLS plaintext acceptance

The first Chrome cold-start attempt reached the mitmproxy process, but the then-current build used a 20-second startup deadline and stopped the runtime before an add-on `running` event arrived. No plaintext event was recorded in that attempt.

That failure produced concrete fixes in the current source:

1. the live add-on CA is requested before proxy startup, which also warms the runtime;
2. the live CA fingerprint is compared against the certificate trusted by Android;
3. the cold-start readiness deadline is 60 seconds;
4. interrupted/closed plaintext sockets no longer crash the Net Tools process;
5. stable add-on v1.4 uses its default mitmproxy option set instead of options introduced/fixed only in newer source.

**Final rerun status:** the Redmi later returned to an unlocked state and the installed product build still showed a healthy Overview screen, active system CA, and “Transparent interception ready”. However, the execution environment subsequently blocked both replacement installation of the newest APK and another programmatic interception-start action. Net Tools does not attempt to evade that execution-layer restriction.

The final plaintext rerun therefore remains the only unclosed acceptance item. When device-write/interception execution is available again, use the newest APK and Chrome with a neutral HTTPS endpoint, then optionally YouTube to characterize QUIC/pinning behavior.

Expected evidence location:

- `artifacts/decryption/chrome/`
- `artifacts/decryption/youtube/`
- `artifacts/decryption/REPORT.md`

## Build / unit-test acceptance

The current source is expected to pass:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Unit tests cover:

- credential/header/query-token redaction
- HTTP request metadata parsing
- HTTP response status parsing
- synthetic raw IPv4 HTTP PCAP parsing
- empty-PCAP handling

## UI acceptance

Navigation implemented in the current source:

- Overview
- Traffic / Capture
- Traffic / Sessions
- Capture session detail
- Decrypt readiness/workspace
- Decryption history
- Decryption session detail
- Plaintext event detail
- Certificate Manager
- Settings
- Diagnostics
- About / licenses

Final screenshots of the newest build belong under `artifacts/product-review/`. Existing package screenshots in `artifacts/validation/` demonstrate traffic-triggering applications, not the final Net Tools UI.

## Acceptance boundary

Current product scope is:

- verified raw root capture
- verified per-UID capture
- verified certificate/add-on/system-trust lifecycle
- implemented standard TLS MITM path with per-UID transparent routing and QUIC fallback

The final plaintext acceptance run remains the only device-side gate not closed at the time of this ledger entry. This is deliberately reported as **not verified**, not inferred from implementation readiness.
