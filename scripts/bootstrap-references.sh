#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REFS="$ROOT_DIR/references"
mkdir -p "$REFS"

clone_at() {
  local name="$1" url="$2" commit="$3"
  local dir="$REFS/$name"
  if [[ ! -d "$dir/.git" ]]; then
    git clone --filter=blob:none --no-checkout "$url" "$dir"
  fi
  git -C "$dir" fetch --depth 1 origin "$commit"
  git -C "$dir" checkout --detach "$commit"
}

# Build dependency: prebuilt pcapd binaries.
clone_at pcapd-bin https://github.com/emanuele-f/pcapd-bin.git 6b54534635b53a0bcb62d8dbe788425a319b5611

# Research/reference trees. These stay outside the product Git history.
clone_at PCAPdroid https://github.com/emanuele-f/PCAPdroid.git c735049420c125d81b5ff3fe70d7c47357ea815c
clone_at PCAPdroid-mitm https://github.com/emanuele-f/PCAPdroid-mitm.git c7e8786b479b24c59239c661f5bb200659eed53e

if [[ ! -d "$REFS/mitmproxy/.git" ]]; then
  git clone --depth 1 https://github.com/mitmproxy/mitmproxy.git "$REFS/mitmproxy"
fi

if [[ ! -d "$REFS/frida-interception-and-unpinning/.git" ]]; then
  git clone --depth 1 https://github.com/httptoolkit/frida-interception-and-unpinning.git "$REFS/frida-interception-and-unpinning"
fi

echo "Reference checkouts are ready under $REFS"
