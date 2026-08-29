#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-f27e2c0f}"
APK="${2:-companion/background-server/build/outputs/apk/debug/background-server-debug.apk}"
REMOTE_APK="/data/local/tmp/BackgroundServer-debug.apk"

if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  exit 1
fi

adb -s "$SERIAL" get-state >/dev/null
adb -s "$SERIAL" shell su -c id | grep -q 'uid=0'

adb -s "$SERIAL" push "$APK" "$REMOTE_APK" >/dev/null
adb -s "$SERIAL" shell su -c "chmod 644 '$REMOTE_APK' && pm install -r -t '$REMOTE_APK' && rm -f '$REMOTE_APK'"

echo "Installed com.aibox.backgroundserver on $SERIAL"
