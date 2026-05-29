#!/usr/bin/env bash
# Smoke-test RunnerUp on a connected device via adb (no UI screenshots).
# Re-dump coordinates if layout changes (1080x2376 reference device).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="${RUNNERUP_PKG:-org.runnerup.debug}"
MAIN="${PKG}/org.runnerup.features.MainLayout"
DETAIL="${PKG}/org.runnerup.features.DetailActivity"
DUMP="/sdcard/runnerup_ui_dump.xml"
LOCAL_DUMP="$(mktemp)"

log() { echo "[device-smoke] $*"; }
warn() { echo "[device-smoke] SKIP: $*" >&2; }
fail() { echo "[device-smoke] FAIL: $*" >&2; exit 1; }

prepare_ui() {
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 0.3
}

has_runnerup_node() {
  local rid="$1"
  python3 - "$PKG" "$DUMP" "$LOCAL_DUMP" "$rid" <<'PY'
import re, sys, subprocess
pkg, remote, local, rid = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
subprocess.run(["adb", "shell", "uiautomator", "dump", remote], check=True, capture_output=True)
subprocess.run(["adb", "pull", remote, local], check=True, capture_output=True)
raw = open(local, encoding="utf-8", errors="replace").read()
if f'package="{pkg}"' not in raw:
    sys.exit(1)
needle = rid if ":" in rid else f"{pkg}:id/{rid}"
nodes = re.findall(r"<node[^>]*package=\"" + re.escape(pkg) + r"\"[^>]*>", raw)
xml = "\n".join(nodes)
sys.exit(0 if re.search(re.escape(needle), xml) else 1)
PY
}

require_device() {
  local count
  count="$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | wc -l)"
  if [[ "$count" -lt 1 ]]; then
    fail "No adb device in 'device' state"
  fi
  if [[ "$count" -gt 1 ]]; then
    log "warning: multiple devices; using default adb target"
  fi
}

tap_rid() {
  local rid="$1"
  prepare_ui
  python3 - "$PKG" "$DUMP" "$LOCAL_DUMP" "$rid" <<'PY'
import re, sys, subprocess
pkg, remote, local, rid = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
subprocess.run(["adb", "shell", "uiautomator", "dump", remote], check=True, capture_output=True)
subprocess.run(["adb", "pull", remote, local], check=True, capture_output=True)
raw = open(local, encoding="utf-8", errors="replace").read()
pkg_pat = re.escape(pkg)
nodes = re.findall(r"<node[^>]*package=\"" + pkg_pat + r"\"[^>]*>", raw)
xml = "\n".join(nodes) if nodes else raw
needle = rid if ":" in rid else f"{pkg}:id/{rid}"
for m in re.finditer(
    r'resource-id="' + re.escape(needle) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
    xml,
):
    x1, y1, x2, y2 = map(int, m.groups())
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)], check=True)
    print(cx, cy)
    sys.exit(0)
for m in re.finditer(
    r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="' + re.escape(needle) + '"',
    xml,
):
    x1, y1, x2, y2 = map(int, m.groups())
    cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
    subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)], check=True)
    print(cx, cy)
    sys.exit(0)
sys.exit(1)
PY
}

screen_size() {
  python3 - <<'PY'
import re, subprocess
out = subprocess.run(["adb", "shell", "wm", "size"], capture_output=True, text=True, check=True)
override = re.search(r"Override size:\s*(\d+)x(\d+)", out.stdout)
if override:
    print(override.group(1), override.group(2))
else:
    sizes = [tuple(map(int, m.groups())) for m in re.finditer(r"(\d+)x(\d+)", out.stdout)]
    if sizes:
        w, h = min(sizes, key=lambda s: s[0])
        print(w, h)
    else:
        print("1080 2376")
PY
}

tap_screen_ratio() {
  local rx="$1"
  local ry="$2"
  read -r w h <<<"$(screen_size)"
  local cx=$(( w * rx / 1000 ))
  local cy=$(( h * ry / 1000 ))
  adb shell input tap "$cx" "$cy" >/dev/null
}

tap_nav_fallback() {
  adb shell cmd statusbar collapse >/dev/null 2>&1 || true
  local idx="$1"
  read -r w h <<<"$(screen_size)"
  local cx=$(( (idx * 2 + 1) * w / 10 ))
  local cy=$(( h - h / 16 ))
  adb shell input tap "$cx" "$cy" >/dev/null
  log "nav fallback ($cx, $cy) index $idx"
}

open_detail_fallback() {
  adb shell cmd statusbar collapse >/dev/null 2>&1 || true
  log "tap first history card (ratio fallback)"
  tap_screen_ratio 500 280
}

tap_nav_index() {
  # Bottom nav: 0=Run, 1=History, 2=BestTimes, 3=Statistics, 4=Settings
  local idx="$1"
  prepare_ui
  if ! python3 - "$PKG" "$DUMP" "$LOCAL_DUMP" "$idx" <<'PY'
import re, sys, subprocess
pkg, remote, local, idx = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
subprocess.run(["adb", "shell", "uiautomator", "dump", remote], check=True, capture_output=True)
subprocess.run(["adb", "pull", remote, local], check=True, capture_output=True)
raw = open(local, encoding="utf-8", errors="replace").read()
pkg_pat = re.escape(pkg)
nodes = re.findall(r"<node[^>]*package=\"" + pkg_pat + r"\"[^>]*>", raw)
xml = "\n".join(nodes)
tabs = []
for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    x1, y1, x2, y2 = map(int, m.groups())
    w, h = x2 - x1, y2 - y1
    if y1 >= 2100 and 80 <= w <= 250 and h >= 120:
        tabs.append(((x1 + x2) // 2, (y1 + y2) // 2))
tabs = sorted(set(tabs), key=lambda t: t[0])
if len(tabs) < 5:
    for m in re.finditer(
        r'class="android\.widget\.ImageView"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    ):
        x1, y1, x2, y2 = map(int, m.groups())
        if y1 >= 2200:
            tabs.append(((x1 + x2) // 2, (y1 + y2) // 2))
    tabs = sorted(set(tabs), key=lambda t: t[0])[:5]
tabs = sorted(set(tabs), key=lambda t: t[0])
if idx >= len(tabs):
    sys.exit(1)
cx, cy = tabs[idx]
subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)], check=True)
print(cx, cy)
PY
  then
    log "nav fallback tap index $idx"
    tap_nav_fallback "$idx"
  fi
}

current_focus() {
  adb shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' | sed 's/.*=//' || true
}

focused_app() {
  adb shell dumpsys window 2>/dev/null | grep -m1 'mFocusedApp=' | sed 's/.*=//' || true
}

assert_activity_resumed() {
  local short_name="$1"
  local focused
  focused="$(focused_app)"
  if [[ "$focused" == *"$short_name"* ]]; then
    return 0
  fi
  local focus
  focus="$(current_focus)"
  if [[ "$focus" == *"$short_name"* ]]; then
    return 0
  fi
  fail "Expected focused '$short_name', mFocusedApp='$focused' mCurrentFocus='$focus'"
}

check_logcat_fatal() {
  if adb logcat -d 2>/dev/null | grep -E 'FATAL EXCEPTION|AndroidRuntime:.*FATAL' | grep -q "$PKG"; then
    adb logcat -d -t 80 2>/dev/null | tail -40 >&2
    fail "Fatal exception in logcat for $PKG"
  fi
  log "no fatal logcat for $PKG"
}

main() {
  require_device
  adb logcat -c >/dev/null 2>&1 || true

  log "launch MainLayout"
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell cmd statusbar collapse >/dev/null 2>&1 || true
  adb shell am start -n "$MAIN" >/dev/null
  sleep 2
  assert_activity_resumed "MainLayout"
  adb shell cmd statusbar collapse >/dev/null 2>&1 || true

  log "bottom nav: History (index 1)"
  tap_nav_index 1 || fail "History tab not found"
  sleep 2
  assert_activity_resumed "MainLayout"

  log "open first history card"
  prepare_ui
  if has_runnerup_node "history_row_card"; then
    if tap_rid "history_row_card"; then
      sleep 2
    fi
    if ! focused_app | grep -q DetailActivity; then
      open_detail_fallback
      sleep 3
    fi
    if focused_app | grep -q DetailActivity; then
      assert_activity_resumed "DetailActivity"
      log "back to main"
      adb shell input keyevent KEYCODE_BACK >/dev/null
      sleep 1
      assert_activity_resumed "MainLayout"
    else
      fail "history_row_card visible but DetailActivity did not open"
    fi
  else
    warn "Detail — no saved activities (history_row_card not in UI)"
  fi

  log "bottom nav: Run / Start (index 0)"
  tap_nav_index 0 || fail "Start tab not found"
  sleep 2
  assert_activity_resumed "MainLayout"

  log "tap workout mode spinner"
  tap_rid "workout_mode_spinner" || log "warning: workout_mode_spinner not tappable (optional)"
  sleep 1

  log "bottom nav: Statistics (index 3)"
  tap_nav_index 3 || fail "Statistics tab not found"
  sleep 2
  assert_activity_resumed "MainLayout"

  check_logcat_fatal
  log "PASS"
}

main "$@"
