#!/usr/bin/env bash
# End-to-end checks against the TV browser on an Android TV emulator.
# Every check reads state off the device, not off the UI's own claims.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
source tools/drive.sh

PASS=0
FAIL=0
RESULTS=()

check() {
  local name="$1" ok="$2" detail="${3:-}"
  if [[ "$ok" == "1" ]]; then
    PASS=$((PASS + 1))
    RESULTS+=("PASS  $name  $detail")
  else
    FAIL=$((FAIL + 1))
    RESULTS+=("FAIL  $name  $detail")
  fi
}

pull_db() {
  $ADB shell "run-as $PKG cat databases/browser.db" > /tmp/tvweb-verify.db 2>/dev/null
}

db() {
  sqlite3 /tmp/tvweb-verify.db "$1" 2>/dev/null | tr -d '\r'
}

prefs_get() {
  $ADB shell "run-as $PKG cat shared_prefs/settings.xml" 2>/dev/null | tr -d '\r'
}

# Focus clamps at a panel edge, so 20 presses land on the first or last row.
clamp_bottom() { for _ in $(seq 1 20); do down; done; }
clamp_top() { for _ in $(seq 1 20); do up; done; }

install_apk >/dev/null
# Start from a known state so toggles are checked against first-run defaults.
$ADB shell pm clear "$PKG" >/dev/null 2>&1
start_app 6
clear_log
pull_db
SEEDED=$(db "select count(*) from shortcuts")
check "first run seeds the start page" "$([[ "${SEEDED:-0}" -eq 5 ]] && echo 1 || echo 0)" "shortcuts=$SEEDED"

check "app is foreground" \
  "$(foreground_activity | grep -c 'dev.sxch.tvweb' | sed 's/^1$/1/;s/^0$/0/')" \
  "$(foreground_activity | tr -d '\r')"

open_url "https://en.wikipedia.org/wiki/Television" 14
shot 01-wikipedia >/dev/null

pull_db
URL_OK=$(db "select count(*) from history where url like '%wikipedia.org%'")
check "page load recorded in history" "$([[ "${URL_OK:-0}" -ge 1 ]] && echo 1 || echo 0)" "rows=$URL_OK"

for _ in 1 2 3; do down; wait_s 1; done
shot 02-scrolled >/dev/null

# Actions menu: last row is Settings.
menu; wait_s 2
clamp_bottom; wait_s 1
ok; wait_s 2
shot 03-settings >/dev/null
clamp_top; wait_s 1
down; wait_s 1; down; wait_s 1
ok; wait_s 2
shot 04-settings-toggled >/dev/null
PREF_ADS=$(prefs_get | grep -c 'name="block_ads" value="false"')
check "ad block setting toggled and stored" "$([[ "${PREF_ADS:-0}" -ge 1 ]] && echo 1 || echo 0)" "pref=false present=$PREF_ADS"
back; wait_s 2

# Bookmark the page: 6 rows above the last row (Settings).
menu; wait_s 2
clamp_bottom; wait_s 1
for _ in 1 2 3 4 5 6; do up; wait_s 1; done
ok; wait_s 2
pull_db
MARKS=$(db "select count(*) from bookmarks")
check "bookmark written to database" "$([[ "${MARKS:-0}" -ge 1 ]] && echo 1 || echo 0)" "bookmarks=$MARKS"

# Tabs panel: 2 rows above the last row.
menu; wait_s 2
clamp_bottom; wait_s 1
up; wait_s 1; up; wait_s 1
ok; wait_s 2
shot 05-tabs >/dev/null
back; wait_s 2

# Cinema mode on a video page.
open_url "https://m.youtube.com/watch?v=aqz-KE-bpKQ" 20
shot 06-video >/dev/null
right; wait_s 1; ok; wait_s 4
shot 07-cinema >/dev/null
back; wait_s 3
shot 08-cinema-exited >/dev/null

# Download handoff: a zip makes the WebView fire the download listener.
$ADB shell rm -f /sdcard/Download/hosts-master.zip >/dev/null 2>&1
open_url "https://github.com/StevenBlack/hosts/archive/refs/heads/master.zip" 12
shot 09-download-panel >/dev/null
ok
wait_s 20
DL_FILE=$($ADB shell ls /sdcard/Download/hosts-master.zip 2>/dev/null | tr -d '\r')
check "download saved to Downloads" "$([[ -n "$DL_FILE" ]] && echo 1 || echo 0)" "${DL_FILE:-missing}"

# DRM sites explain themselves instead of showing a broken player.
open_url "https://www.netflix.com/" 12
shot 10-drm-notice >/dev/null
back; wait_s 2

# Restart and confirm the setting survived.
start_app 6
PREF_ADS_AFTER=$(prefs_get | grep -c 'name="block_ads" value="false"')
check "setting survives restart" "$([[ "${PREF_ADS_AFTER:-0}" -ge 1 ]] && echo 1 || echo 0)" "pref=false present=$PREF_ADS_AFTER"
pull_db
MARKS_AFTER=$(db "select count(*) from bookmarks")
check "bookmarks survive restart" "$([[ "${MARKS_AFTER:-0}" -ge 1 ]] && echo 1 || echo 0)" "bookmarks=$MARKS_AFTER"

CRASHES=$(crash_count)
check "no crashes in logcat" "$([[ "${CRASHES:-1}" -eq 0 ]] && echo 1 || echo 0)" "crash-lines=$CRASHES"

echo
echo "===== TV Web verification ====="
for line in "${RESULTS[@]}"; do echo "$line"; done
echo "-----"
echo "$PASS passed, $FAIL failed"
echo "screenshots: $SHOTS"
exit $((FAIL > 0 ? 1 : 0))
