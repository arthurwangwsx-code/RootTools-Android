#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/quality_guard.py
python3 scripts/security_guard.py
./gradlew \
  :core:privilege:test \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:koverXmlReportDebug \
  :feature:network-inspection:test \
  :companion:hyperos-credential-fix:lintDebug \
  :companion:hyperos-credential-fix:assembleDebug \
  :companion:hyperos-credential-fix:assembleRelease \
  :companion:background-server:testDebugUnitTest \
  :companion:background-server:lintDebug \
  :companion:background-server:assembleDebug \
  :companion:background-server:assembleRelease \
  :companion:nfc-tools:testDebugUnitTest \
  :companion:nfc-tools:lintDebug \
  :companion:nfc-tools:assembleDebug \
  :companion:nfc-tools:assembleRelease
python3 scripts/coverage_guard.py

echo
echo "Artifacts:"
find app/build/outputs/apk companion/*/build/outputs/apk \
  -type f -name '*.apk' -maxdepth 3 -print0 \
  | sort -z \
  | xargs -0 ls -lh
