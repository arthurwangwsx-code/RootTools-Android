#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

set +e
python3 scripts/quality_guard.py
QUALITY_STATUS=$?
set -e

if [[ $QUALITY_STATUS -ne 0 ]]; then
  echo "WARN: repository-wide quality guard has failures; continuing fast compile/test for local feedback." >&2
  echo "WARN: the full delivery gate remains strict and will still fail until those issues are resolved." >&2
fi

if [[ $# -gt 0 ]]; then
  ./gradlew :app:testDebugUnitTest --tests "$1"
else
  ./gradlew :app:testDebugUnitTest
fi

exit "$QUALITY_STATUS"
