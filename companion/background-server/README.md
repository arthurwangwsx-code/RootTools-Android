# Background Server

Root-capable Android control plane for long-running background workloads.

## Implemented

- Dashboard/list home page.
- Power management and screen-off work mode.
- Screen sleep/wake settings, including double-tap-to-wake integration.
- Runtime telemetry: work duration, battery-side power estimate, accumulated energy estimate, CPU usage, load averages, memory, traffic and temperature.
- Opt-in boot restoration and foreground runtime supervision.
- LAN/network information page with interface, CIDR, gateway and DNS diagnostics.
- Root capability probing and isolated root command boundary.
- WireGuard server page backed by the official Android userspace tunnel library.
- Root IPv4 forwarding and reversible iptables/NAT rules for a LAN WireGuard test peer.
- WireGuard RX/TX statistics and a copyable LAN client profile.

## Build

The project currently targets the locally installed modern Android toolchain (AGP 9.2 / compileSdk 37 / targetSdk 37), including Android 17 local-network permission handling required by the future LAN server role.

```bash
./gradlew :companion:background-server:assembleDebug
```

Install to the current Redmi test device:

```bash
adb -s f27e2c0f install -r companion/background-server/build/outputs/apk/debug/background-server-debug.apk
```

On rooted MIUI devices where normal ADB install is intercepted by Security Center,
use the root Package Manager path. This avoids MIUI's USB-install confirmation while
leaving Android APK signature validation intact:

```bash
bash scripts/background-server-install-rooted-device.sh f27e2c0f
```

## Architecture

See [`docs/companion/background-server/`](../../docs/companion/background-server/) for architecture, power, WireGuard, validation and roadmap records.
