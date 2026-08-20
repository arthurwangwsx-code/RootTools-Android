#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-100.91.126.56:5555}"
ADB_BIN="${ADB_BIN:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
PKG="com.arthur.roottools"

adb_cmd() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

section() {
  printf '\n== %s ==\n' "$1"
}

section "Connection"
adb_cmd get-state
adb_cmd shell getprop ro.product.model

section "Installed Root Tools"
if adb_cmd shell pm path "$PKG" >/dev/null 2>&1; then
  adb_cmd shell dumpsys package "$PKG" | grep -E 'versionCode=|versionName=' | head -n 4
else
  echo "Root Tools is not installed on $SERIAL"
fi

section "Root / ADB"
adb_cmd shell "su -c 'id -u' 2>/dev/null || true"
echo -n "ADB TCP port: "
adb_cmd shell getprop service.adb.tcp.port

section "Thermal"
adb_cmd shell "dumpsys thermalservice | grep -E 'Thermal Status:|mName=(AP|BAT|SKIN|USB|PATHM)' | head -n 12"

section "CPU policies"
adb_cmd shell 'for d in /sys/devices/system/cpu/cpufreq/policy*; do [ -d "$d" ] || continue; echo "${d##*policy} cpus=$(cat $d/related_cpus) cur=$(cat $d/scaling_cur_freq) max=$(cat $d/scaling_max_freq) hw=$(cat $d/cpuinfo_max_freq) gov=$(cat $d/scaling_governor)"; done'

section "Memory / PSI"
adb_cmd shell "grep -E '^(MemTotal|MemAvailable|Cached|AnonPages|Slab|SwapTotal|SwapFree):' /proc/meminfo"
adb_cmd shell cat /proc/pressure/memory
adb_cmd shell cat /proc/pressure/io

section "Network"
adb_cmd shell 'ip -4 -o addr show | grep -E " (tun0|wlan0|rmnet_data[0-9]*) " || true'
adb_cmd shell "su -c 'ss -ltn 2>/dev/null' | grep -E ':5555( |$)' || true"

section "ADB host RTT"
python3 - "$ADB_BIN" "$SERIAL" <<'PY'
import statistics
import subprocess
import sys
import time

adb, serial = sys.argv[1], sys.argv[2]
samples = []
for _ in range(3):
    start = time.perf_counter()
    completed = subprocess.run(
        [adb, "-s", serial, "shell", "true"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if completed.returncode == 0:
        samples.append((time.perf_counter() - start) * 1000)
if samples:
    print(f"min/avg/max = {min(samples):.1f}/{statistics.mean(samples):.1f}/{max(samples):.1f} ms")
else:
    print("RTT unavailable")
PY

section "Quick Settings"
adb_cmd shell settings get secure sysui_qs_tiles | tr ',' '\n' | grep -E 'com.arthur.roottools' || true

section "Vector / Magisk"
adb_cmd shell 'su -c "[ -e /data/adb/modules/zygisk_vector/disable ] && echo vector=disabled || echo vector=enabled; [ -e /data/adb/modules/zygisk_lsposed/disable ] && echo lsposed=disabled || echo lsposed=enabled"'
adb_cmd shell 'su -c /data/adb/lspd/cli modules --json ls 2>/dev/null | head -n 80' || true

section "Root Tools process"
PID="$(adb_cmd shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
if [[ -n "$PID" ]]; then
  adb_cmd shell "top -b -n 1 -p $PID 2>/dev/null | tail -n +5 | head -n 2"
else
  echo "Root Tools process is not running"
fi

echo
echo "Read-only validation complete. No install/reboot/ADB-off action was executed."
