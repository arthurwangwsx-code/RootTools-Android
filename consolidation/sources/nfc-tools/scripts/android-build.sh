#!/bin/sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
LOCK_DIR="${TMPDIR:-/tmp}/nfc-tools-gradle.lock"

if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /opt/homebrew/opt/openjdk@21/bin/java ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@21
  fi
fi

if [ -z "${ANDROID_HOME:-}" ]; then
  ANDROID_HOME="$HOME/Library/Android/sdk"
fi

export JAVA_HOME ANDROID_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:/opt/homebrew/bin:$PATH"

cd "$ROOT_DIR"

acquire_lock() {
  while ! mkdir "$LOCK_DIR" 2>/dev/null; do
    if [ -f "$LOCK_DIR/pid" ]; then
      holder="$(cat "$LOCK_DIR/pid" 2>/dev/null || true)"
      if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
        rm -rf "$LOCK_DIR"
        continue
      fi
    elif [ -d "$LOCK_DIR" ]; then
      rm -rf "$LOCK_DIR"
      continue
    fi
    sleep 1
  done
  echo "$$" > "$LOCK_DIR/pid"
}

release_lock() {
  if [ -f "$LOCK_DIR/pid" ] && [ "$(cat "$LOCK_DIR/pid" 2>/dev/null || true)" = "$$" ]; then
    rm -rf "$LOCK_DIR"
  fi
}

acquire_lock
trap release_lock EXIT INT TERM HUP

./gradlew "$@"
