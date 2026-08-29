#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/quality_guard.py
python3 scripts/security_guard.py
./gradlew \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:koverXmlReportDebug \
  :companion:hyperos-credential-fix:lintDebug \
  :companion:hyperos-credential-fix:assembleDebug \
  :companion:hyperos-credential-fix:assembleRelease
python3 scripts/coverage_guard.py

echo
echo "Artifacts:"
ls -lh \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  companion/hyperos-credential-fix/build/outputs/apk/debug/hyperos-credential-fix-debug.apk \
  companion/hyperos-credential-fix/build/outputs/apk/release/hyperos-credential-fix-release-unsigned.apk
