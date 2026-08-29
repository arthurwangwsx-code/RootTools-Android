#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IOS_DIR="$ROOT/ios/NFCProbe"
PROJECT="$IOS_DIR/NFCProbe.xcodeproj"
SCHEME="NFCProbe"
BUNDLE_ID="com.arthur.nfctools.nfcprobe"
DERIVED_DATA="$ROOT/.build/ios-nfc-probe"
ACTION="${1:-install}"

log() { printf '[ios-probe] %s\n' "$*"; }
die() { printf '[ios-probe] ERROR: %s\n' "$*" >&2; exit 1; }

require() {
  command -v "$1" >/dev/null 2>&1 || die "缺少命令: $1"
}

detect_team() {
  if [[ -n "${TEAM_ID:-}" ]]; then
    printf '%s' "$TEAM_ID"
    return
  fi

  local last_selected identity
  last_selected="$(defaults read com.apple.dt.Xcode IDEProvisioningTeamManagerLastSelectedTeamID 2>/dev/null || true)"
  if [[ "$last_selected" =~ ^[A-Z0-9]{10}$ ]]; then
    printf '%s' "$last_selected"
    return
  fi

  identity="$(security find-identity -v -p codesigning 2>/dev/null \
    | sed -n 's/.*"Apple Development:.*(\([A-Z0-9]\{10\}\))".*/\1/p' \
    | head -n 1)"
  [[ -n "$identity" ]] || die "没有找到 Apple Development 签名身份。可用 TEAM_ID=... 显式指定。"
  printf '%s' "$identity"
}

xcode_has_account() {
  defaults read com.apple.dt.Xcode DVTDeveloperAccountManagerAppleIDLists 2>/dev/null \
    | grep -Eq '[[:alnum:]_.+-]+@[[:alnum:].-]+' || return 1
}

has_cached_nfc_profile() {
  python3 - "$BUNDLE_ID" <<'PY'
import glob, os, plistlib, subprocess, sys

bundle = sys.argv[1]
patterns = [
    os.path.expanduser("~/Library/Developer/Xcode/UserData/Provisioning Profiles/*.mobileprovision"),
    os.path.expanduser("~/Library/MobileDevice/Provisioning Profiles/*.mobileprovision"),
]
for path in [p for pat in patterns for p in glob.glob(pat)]:
    # Most machines accumulate hundreds of old profiles. The signed CMS still
    # contains the embedded plist strings, so cheaply reject unrelated bundle
    # IDs before invoking the much slower `security cms` decoder.
    try:
        probe = subprocess.run(
            ["strings", path],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            check=False,
        ).stdout
        if bundle.encode() not in probe:
            continue
    except Exception:
        pass
    try:
        raw = subprocess.run(
            ["security", "cms", "-D", "-i", path],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            check=True,
        ).stdout
        profile = plistlib.loads(raw)
    except Exception:
        continue
    ent = profile.get("Entitlements", {})
    app_id = ent.get("application-identifier", "")
    formats = ent.get("com.apple.developer.nfc.readersession.formats") or []
    if app_id.endswith("." + bundle) and "TAG" in formats and profile.get("ProvisionedDevices"):
        print(path)
        sys.exit(0)
sys.exit(1)
PY
}

detect_device() {
  if [[ -n "${DEVICE_ID:-}" ]]; then
    printf '%s' "$DEVICE_ID"
    return
  fi

  local json
  json="$(mktemp)"
  xcrun devicectl list devices --json-output "$json" >/dev/null
  python3 - "$json" <<'PY'
import json, sys

payload = json.load(open(sys.argv[1]))
devices = payload.get("result", {}).get("devices", [])
for d in devices:
    props = d.get("properties", {})
    hardware = props.get("hardware", {})
    connection = props.get("connection", {})
    state_props = props.get("state", {})
    reality = (hardware.get("reality") or d.get("hardwareProperties", {}).get("reality") or "").lower()
    state = (connection.get("state") or d.get("connectionProperties", {}).get("tunnelState") or "").lower()
    name = state_props.get("name") or d.get("deviceProperties", {}).get("name") or ""
    product = hardware.get("marketingName") or d.get("hardwareProperties", {}).get("marketingName") or ""
    identifier = d.get("identifier")
    text = f"{name} {product}".lower()
    if identifier and "iphone" in text and reality == "physical" and state == "connected":
        print(identifier)
        sys.exit(0)
sys.exit(1)
PY
  rm -f "$json"
}

doctor() {
  require xcrun
  require xcodebuild
  require xcodegen
  require python3
  require security

  local ok=1 team="" device="" profile=""
  team="$(detect_team 2>/dev/null || true)"
  if [[ -n "$team" ]]; then
    log "签名 Team: $team"
  else
    log "签名 Team: 未检测到"
    ok=0
  fi

  if xcode_has_account; then
    log "Xcode Developer Account: 已登录"
  elif profile="$(has_cached_nfc_profile 2>/dev/null || true)" && [[ -n "$profile" ]]; then
    log "Xcode Developer Account: 未登录，但已有可用 NFC Development profile"
  else
    log "Xcode Developer Account: 未登录，且当前没有 $BUNDLE_ID 的 NFC Development profile"
    log "一次性操作：打开 Xcode > Settings > Accounts，登录你的 Apple Developer Program Apple ID。"
    ok=0
  fi

  if device="$(detect_device 2>/dev/null || true)" && [[ -n "$device" ]]; then
    log "物理 iPhone: connected ($device)"
  else
    log "物理 iPhone: 当前没有 connected 设备"
    log "安装前请解锁 iPhone、开启 Developer Mode，并通过 USB 或已配对 Wi-Fi 连接 Mac。"
    ok=0
  fi

  if [[ "$ok" -eq 1 ]]; then
    log "环境检查通过"
    return 0
  fi
  return 1
}

generate() {
  require xcodegen
  log "生成 Xcode 工程"
  (cd "$IOS_DIR" && xcodegen generate --spec project.yml >/dev/null)
}

build_simulator() {
  generate
  log "编译 iOS Simulator 版本（仅验证编译；Core NFC 需真机）"
  xcodebuild \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -configuration Debug \
    -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$DERIVED_DATA-sim" \
    CODE_SIGNING_ALLOWED=NO \
    build
}

build_device() {
  generate
  local team="$1"
  local device="$2"
  log "使用 Team $team 为设备 $device 构建"
  xcodebuild \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -configuration Debug \
    -destination "platform=iOS,id=$device" \
    -derivedDataPath "$DERIVED_DATA" \
    -allowProvisioningUpdates \
    DEVELOPMENT_TEAM="$team" \
    CODE_SIGN_STYLE=Automatic \
    build
}

app_path() {
  printf '%s/Build/Products/Debug-iphoneos/NFC Probe.app' "$DERIVED_DATA"
}

install() {
  require xcrun
  require python3
  require security
  local team device app
  doctor || die "安装环境尚未满足；处理上面的项目后重新执行 ./scripts/ios-probe.sh install"
  team="$(detect_team)"
  if ! device="$(detect_device)"; then
    die "没有找到当前可用的物理 iPhone。请解锁 iPhone、确认已信任此 Mac，并保持 USB/Wi-Fi Developer Mode 连接；也可 DEVICE_ID=... 显式指定。"
  fi
  build_device "$team" "$device"
  app="$(app_path)"
  [[ -d "$app" ]] || die "构建成功但未找到 app: $app"

  log "安装到 iPhone"
  xcrun devicectl device install app --device "$device" "$app"
  log "启动 $BUNDLE_ID"
  xcrun devicectl device process launch --terminate-existing --device "$device" "$BUNDLE_ID"
  log "完成。打开 NFC Probe，点击“开始 ISO14443 扫描”。"
}

case "$ACTION" in
  doctor) doctor ;;
  generate) generate ;;
  build-sim) build_simulator ;;
  install|run) install ;;
  devices) xcrun devicectl list devices ;;
  *)
    cat <<EOF
Usage: $0 [doctor|generate|build-sim|install|run|devices]

Optional environment variables:
  TEAM_ID=<Apple Developer Team ID>
  DEVICE_ID=<CoreDevice UUID / UDID / device name>
EOF
    exit 2
    ;;
esac

