#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=${ROOTTOOLS_GRADLE_VERSION:-9.5.0}

LOCK_DIR="$ROOT_DIR/.gradle/roottools-build.lock"
if [ "${ROOTTOOLS_BUILD_LOCK:-1}" != "0" ]; then
  mkdir -p "$ROOT_DIR/.gradle"
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    owner=""
    if [ -f "$LOCK_DIR/pid" ]; then
      owner=$(cat "$LOCK_DIR/pid" 2>/dev/null || true)
    fi
    case "$owner" in
      ''|*[!0-9]*) ;;
      *)
        if kill -0 "$owner" 2>/dev/null; then
          echo "Another RootTools Gradle invocation is active (pid $owner)." >&2
          echo "Refusing a duplicate build to avoid CPU/cache contention. Retry after it finishes." >&2
          exit 75
        fi
        ;;
    esac
    rm -rf "$LOCK_DIR"
    mkdir "$LOCK_DIR"
  fi
  echo "$$" > "$LOCK_DIR/pid"
  cleanup_lock() { rm -rf "$LOCK_DIR"; }
  trap cleanup_lock EXIT HUP INT TERM
fi

case " $* " in
  *" --version "*|*" -v "*|*" --status "*|*" --stop "*)
    ;;
  *)
    if [ "${ROOTTOOLS_FORCE_BUILD:-0}" != "1" ]; then
      CPU_COUNT=$(sysctl -n hw.ncpu 2>/dev/null || getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)
      LOAD_ONE=$(sysctl -n vm.loadavg 2>/dev/null | awk '{print $2}' || true)
      if [ -n "$LOAD_ONE" ] && [ -n "$CPU_COUNT" ]; then
        MAX_LOAD_PER_CORE=${ROOTTOOLS_MAX_LOAD_PER_CORE:-2.0}
        MAX_LOAD=$(awk -v cores="$CPU_COUNT" -v per_core="$MAX_LOAD_PER_CORE" 'BEGIN { printf "%.2f", cores * per_core }')
        if awk -v load="$LOAD_ONE" -v max="$MAX_LOAD" 'BEGIN { exit !(load > max) }'; then
          echo "RootTools build preflight refused to start under severe host load." >&2
          echo "1-minute load=$LOAD_ONE, logical CPUs=$CPU_COUNT, guard=$MAX_LOAD." >&2
          echo "This prevents slow duplicate/heavy builds from making host contention worse." >&2
          echo "Tune ROOTTOOLS_MAX_LOAD_PER_CORE or use ROOTTOOLS_FORCE_BUILD=1 only for an intentional override." >&2
          exit 76
        fi
      fi
    fi
    ;;
esac

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME:-}/bin/java" ]; then
  for candidate in \
    "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
    "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  do
    if [ -x "$candidate/bin/java" ]; then
      JAVA_HOME=$candidate
      export JAVA_HOME
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "RootTools build requires JDK 17+; set JAVA_HOME to a valid JDK." >&2
  exit 2
fi
PATH="$JAVA_HOME/bin:$PATH"
export PATH

if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  ANDROID_HOME="$HOME/Library/Android/sdk"
  export ANDROID_HOME
fi
if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -n "${ANDROID_HOME:-}" ]; then
  ANDROID_SDK_ROOT="$ANDROID_HOME"
  export ANDROID_SDK_ROOT
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
  echo "RootTools build requires Android SDK 36; set ANDROID_HOME to a valid SDK." >&2
  exit 3
fi

find_cached_gradle() {
  for candidate in \
    "$HOME"/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin/*/gradle-${GRADLE_VERSION}/bin/gradle \
    "$HOME"/.gradle/roottools-bootstrap/gradle-${GRADLE_VERSION}/bin/gradle
  do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

GRADLE_BIN=$(find_cached_gradle || true)
if [ -z "$GRADLE_BIN" ]; then
  BOOTSTRAP_DIR="$HOME/.gradle/roottools-bootstrap"
  ZIP="$BOOTSTRAP_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  mkdir -p "$BOOTSTRAP_DIR"
  command -v curl >/dev/null 2>&1 || { echo "curl is required to bootstrap Gradle." >&2; exit 4; }
  command -v unzip >/dev/null 2>&1 || { echo "unzip is required to bootstrap Gradle." >&2; exit 4; }
  echo "Bootstrapping Gradle ${GRADLE_VERSION}..." >&2
  curl -fL --retry 3 --connect-timeout 10 \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    -o "$ZIP"
  unzip -q -o "$ZIP" -d "$BOOTSTRAP_DIR"
  rm -f "$ZIP"
  GRADLE_BIN="$BOOTSTRAP_DIR/gradle-${GRADLE_VERSION}/bin/gradle"
fi

"$GRADLE_BIN" -p "$ROOT_DIR" "$@"
