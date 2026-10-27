#!/usr/bin/env bash
# Drives the TV browser on an Android TV emulator and captures screenshots.
set -uo pipefail

SERIAL="${SERIAL:-emulator-5554}"
ADB="adb -s ${SERIAL}"
PKG="dev.sxch.tvweb"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
SHOTS="${SHOTS:-/tmp/tvweb-shots}"
mkdir -p "$SHOTS"

key() { $ADB shell input keyevent "$1"; }
up() { key 19; }
down() { key 20; }
left() { key 21; }
right() { key 22; }
ok() { key 23; }
back() { key 4; }
menu() { key 82; }
type_text() { $ADB shell input text "$1"; }
wait_s() { sleep "${1:-1}"; }

install_apk() {
  $ADB install -r "$APK" | tail -n 1
}

start_app() {
  $ADB shell am force-stop "$PKG"
  wait_s 1
  $ADB shell am start -n "$PKG/.MainActivity" >/dev/null
  wait_s "${1:-5}"
}

shot() {
  local name="$1"
  $ADB shell screencap -p "/sdcard/${name}.png" >/dev/null
  $ADB pull "/sdcard/${name}.png" "$SHOTS/${name}.png" >/dev/null 2>&1
  echo "$SHOTS/${name}.png"
}

# Focus starts on the first start-page tile. MENU, then the first menu row, opens the address panel.
open_url() {
  local url="$1"
  local settle="${2:-12}"
  $ADB shell "am start -a android.intent.action.VIEW -d '$url' $PKG" >/dev/null
  wait_s "$settle"
}

# Focus starts on the address chip once a page is loaded.
focus_watch() {
  right
  wait_s 1
}

crash_count() {
  $ADB logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true
}

clear_log() { $ADB logcat -c; }

db_query() {
  $ADB shell "run-as $PKG sqlite3 databases/browser.db \"$1\"" 2>/dev/null
}

foreground_activity() {
  $ADB shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  cmd="${1:-help}"
  shift || true
  case "$cmd" in
    install) install_apk ;;
    start) start_app "$@" ;;
    shot) shot "${1:-shot}" ;;
    open) open_url "$@" ;;
    crash) crash_count ;;
    db) db_query "$1" ;;
    focus) foreground_activity ;;
    *|help) sed -n '2,4p' "$0" ;;
  esac
fi
