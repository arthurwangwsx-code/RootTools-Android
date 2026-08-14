#!/usr/bin/env bash
set -euo pipefail

SERIAL="${1:-f27e2c0f}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT_DIR/artifacts/validation"
mkdir -p "$OUT"

APPS=(
  "com.google.android.youtube|YouTube"
  "com.bilibili.app.in|Bilibili"
  "com.android.vending|Google Play"
  "com.android.chrome|Chrome"
  "com.tencent.mm|WeChat"
  "com.tencent.wetype|WeType"
  "com.google.android.googlequicksearchbox|Google"
  "com.google.android.apps.maps|Maps"
  "com.google.android.gm|Gmail"
  "org.telegram.messenger|Telegram"
  "com.whatsapp|WhatsApp"
  "com.openai.chatgpt|ChatGPT"
  "com.instagram.android|Instagram"
  "com.xingin.xhs|Xiaohongshu"
  "com.zhihu.android|Zhihu"
  "com.jingdong.app.mall|JD"
  "com.taobao.taobao|Taobao"
  "com.netease.cloudmusic|NetEase Music"
  "com.facebook.katana|Facebook"
  "com.zhiliaoapp.musically|TikTok"
  "com.lazada.android|Lazada"
)

capture_cmd() {
  local command="$1" package="${2:-}"
  "$ADB" -s "$SERIAL" shell am broadcast \
    -a com.arthur.nettools.debug.CAPTURE_COMMAND \
    -n com.arthur.nettools/.debug.CaptureCommandReceiver \
    --es command "$command" ${package:+--es package "$package"} >/dev/null
}

for entry in "${APPS[@]}"; do
  IFS='|' read -r package label <<<"$entry"
  if ! "$ADB" -s "$SERIAL" shell pm path "$package" >/dev/null 2>&1; then
    echo "SKIP $label ($package): not installed"
    continue
  fi

  safe="${package//./_}"
  dir="$OUT/$safe"
  mkdir -p "$dir"
  echo "CAPTURE $label ($package)"
  capture_cmd recover
  capture_cmd start "$package"
  sleep 1
  "$ADB" -s "$SERIAL" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  sleep 6
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$dir/screen.png"
  capture_cmd stop
  sleep 2
done

"$ADB" -s "$SERIAL" pull \
  /sdcard/Android/data/com.arthur.nettools/files/captures \
  "$OUT/device-captures" >/dev/null

python3 "$ROOT_DIR/scripts/summarize-validation.py" "$OUT"

echo "Validation artifacts: $OUT"
