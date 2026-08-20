#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-${ROOTTOOLS_ANDROID_SERIAL:-}}"
PACKAGE="com.arthur.roottools"
RECEIVER="$PACKAGE/.debug.DebugDeviceSnapshotReceiver"
ACTION="$PACKAGE.DEBUG_DEVICE_SNAPSHOT"

if [[ -z "$SERIAL" ]]; then
  echo "usage: $0 <adb-serial>" >&2
  exit 2
fi

if ! adb devices | awk -v serial="$SERIAL" '$1 == serial && $2 == "device" { found = 1 } END { exit found ? 0 : 1 }'; then
  echo "device not online: $SERIAL" >&2
  exit 3
fi

# The receiver only exists in the Debug source set and is protected by android.permission.DUMP.
# A missing receiver here therefore means the installed artifact is not a debuggable validation build.
if ! adb -s "$SERIAL" shell dumpsys package "$PACKAGE" 2>/dev/null | grep -q 'DebugDeviceSnapshotReceiver'; then
  echo "DebugDeviceSnapshotReceiver is not present in the installed package" >&2
  exit 4
fi

adb -s "$SERIAL" shell am broadcast -W \
  -n "$RECEIVER" \
  -a "$ACTION"
