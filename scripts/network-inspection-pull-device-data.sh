#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-}"
ADB="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT_DIR/artifacts/device-export/$STAMP"
BASE="/sdcard/Android/data/com.arthur.roottools/files"

if [[ -z "$SERIAL" ]]; then
  echo "Usage: $0 <adb-serial>" >&2
  exit 2
fi

mkdir -p "$OUT"

for name in captures intercepts network-inspection/certificates; do
  if "$ADB" -s "$SERIAL" shell test -d "$BASE/$name"; then
    mkdir -p "$OUT/$name"
    "$ADB" -s "$SERIAL" pull "$BASE/$name/." "$OUT/$name/" >/dev/null
  fi
done

mkdir -p "$ROOT_DIR/artifacts/device-export"
rm -f "$ROOT_DIR/artifacts/device-export/latest"
ln -s "$STAMP" "$ROOT_DIR/artifacts/device-export/latest"

if [[ -d "$OUT/intercepts" ]]; then
  python3 "$ROOT_DIR/scripts/network-inspection-summarize-intercepts.py" "$OUT/intercepts" --output "$OUT/INTERCEPT_REPORT.md"
fi

echo "$OUT"
