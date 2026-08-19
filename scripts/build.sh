#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease

echo
echo "Artifacts:"
ls -lh \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
