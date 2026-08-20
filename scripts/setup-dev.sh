#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

git config core.hooksPath .githooks
chmod +x .githooks/commit-msg scripts/*.sh scripts/*.py

echo "RootTools developer hooks configured."
echo "commit-msg -> Conventional Commit guard"
