# Validation record

Date: 2026-08-14 / 15

## Automated validation

- 原独立工程 `:app` 的 unit / assemble / lint 已通过；归一化后的等价任务为 `:companion:background-server:testDebugUnitTest :companion:background-server:assembleDebug :companion:background-server:lintDebug`。
- Android Lint: `No issues found.`
- Unit tests cover WireGuard backend capability selection and generic engine supervisor start/stop behavior.
- Debug APK: `companion/background-server/build/outputs/apk/debug/background-server-debug.apk`.
- Package: `com.aibox.backgroundserver`.
- minSdk: 26.
- compileSdk / targetSdk: 37 / 37.
- Merged manifest contains `BackgroundRuntimeService`, `BootCompletedReceiver`, and the WireGuard library's `GoBackend$VpnService` protected by `android.permission.BIND_VPN_SERVICE`.
- APK includes the WireGuard library native components (`libwg-go.so`, `libwg.so`, `libwg-quick.so`).
- Debug APK is currently about 45 MB after adding the QR-code renderer and WireGuard native ABIs. Release ABI filtering/shrinking is intentionally deferred until functional LAN acceptance.

## Redmi device facts

Device used for low-level validation:

- model: Mi 9T Pro (`raphael`).
- serial: `f27e2c0f`.
- Android: 11 / API 30.
- root: Magisk; an earlier direct probe on this device returned `uid=0`.
- double tap to wake: enabled (`secure.double_tap_to_wake=1`).
- Wi-Fi interface observed: `wlan0`.
- LAN IPv4 observed initially as `10.1.1.75/24`; later DHCP lease observed as `10.1.1.76/24`.
- CPU thermal source observed: `/sys/class/thermal/thermal_zone1`, type `cpu-0-0-usr`.

### Network/VPN capabilities observed

- `/dev/net/tun`: present.
- `/system/bin/iptables`: present, legacy 1.8.4.
- WireGuard kernel module/config: not present.
- `wg` and `wireguard-go` commands: not preinstalled.
- IPv4 forwarding default: `0`.

This capability set selects the official Android userspace WireGuard backend plus root forwarding/NAT instead of assuming a kernel WireGuard implementation exists.

## Power source validation

Direct reads of these battery sysfs files are denied to a non-root shell on this Redmi:

- `/sys/class/power_supply/battery/current_now`.
- `/sys/class/power_supply/battery/voltage_now`.

The app therefore uses `BatteryManager` for current and the framework battery broadcast for voltage, with readable thermal sysfs (or battery temperature) as the temperature source. This avoids spawning a root shell once per telemetry sample.

Framework battery data observed during validation:

- AC powered: true.
- level: 100%.
- voltage: 4378 mV.
- temperature: 33.7 °C.

## Screen-off behavior already validated

Before the app integration, the same Redmi was tested with a partial/kernel wake-lock experiment:

- display transitions from DOZE to physical `OFF`;
- a one-second heartbeat continues while the display is `OFF`;
- `KEYCODE_WAKEUP` returns the display to `ON`;
- double-tap-to-wake is enabled.

The app now wraps that model in `BackgroundRuntimeService` with a bounded and renewed Android `PARTIAL_WAKE_LOCK`.

### No-lock screen-off validation

The installed build now has a separate **无锁息屏** mode. On the Redmi test device, after enabling it and entering the blank state:

- `dumpsys power` remained `mWakefulness=Awake`;
- `MainActivity` remained the focused app/task;
- the current Compose route was not recreated;
- leaving/pausing the Activity restored normal brightness automatically.

The intended restore gesture is a physical finger double tap on the black surface. Two independent ADB `input tap` processes are not treated as a reliable timing-equivalent double tap, so that final gesture feel is left for direct finger acceptance while the underlying no-Keyguard state transition has been validated.

## WireGuard LAN traffic observed

After the generated QR code was scanned into the iPhone client, the Redmi `tun0` counters became non-zero:

- RX: 17 packets / 1040 bytes;
- TX: 19 packets / 1543 bytes.

Android connectivity reported the VPN network on `tun0` (`10.77.0.1/24`) as connected and validated. This confirms that the scanned iPhone client reached the Redmi WireGuard endpoint during LAN validation.

## Installation path

Normal ADB install previously returned:

```text
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
```

MIUI Security Center still owns the normal USB-install intercept. Because this development device is rooted, project installs now use a scoped root Package Manager path: push the already-built APK to `/data/local/tmp`, then run `su -c pm install -r -t`. This avoids the MIUI USB-install confirmation without disabling Android APK signature validation. The reusable entry point is `scripts/background-server-install-rooted-device.sh`.

The `0.1.0` debug APK was successfully installed this way on 2026-08-15. `com.aibox.backgroundserver/.MainActivity` was then started and confirmed as the resumed foreground activity.

## Remaining device acceptance

1. Physically double-tap the **无锁息屏** black surface and confirm the display immediately restores the same page.
2. Re-start WireGuard after the latest APK replacement and confirm the previously scanned iPhone profile reconnects without regenerating keys.
3. Verify Internet egress IP from the iPhone while the WireGuard profile is active, not only handshake/tunnel packet counters.
4. Run a longer screen-off workload test and compare standard `ScreenState=OFF` power against no-lock black-screen power.
5. Stop the engine and confirm its iptables rules are removed while unrelated firewall state is preserved.
