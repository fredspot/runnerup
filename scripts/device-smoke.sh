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

tap_text_contains() {
  local needle="$1"
  prepare_ui
  python3 - "$PKG" "$DUMP" "$LOCAL_DUMP" "$needle" <<'PY'
import re, sys, subprocess
pkg, remote, local, needle = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
subprocess.run(["adb", "shell", "uiautomator", "dump", remote], check=True, capture_output=True)
subprocess.run(["adb", "pull", remote, local], check=True, capture_output=True)
raw = open(local, encoding="utf-8", errors="replace").read()
pkg_pat = re.escape(pkg)
nodes = re.findall(r"<node[^>]*package=\"" + pkg_pat + r"\"[^>]*>", raw)
xml = "\n".join(nodes) if nodes else raw
needle_l = needle.lower()
for m in re.finditer(
    r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml
):
    text, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
    if needle_l in text.lower():
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)], check=True)
        print(cx, cy)
        sys.exit(0)
for m in re.finditer(
    r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="([^"]*)"', xml
):
    x1, y1, x2, y2, text = map(int, m.groups()[:4]), m.group(5)
    if needle_l in text.lower():
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)], check=True)
        print(cx, cy)
        sys.exit(0)
sys.exit(1)
PY
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
      log "Detail: open Laps tab (index 1)"
      prepare_ui
      if python3 - "$PKG" "$DUMP" "$LOCAL_DUMP" <<'PY'
import re, sys, subprocess
pkg, remote, local = sys.argv[1], sys.argv[2], sys.argv[3]
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
    if 80 <= w <= 400 and 40 <= h <= 120 and y1 < 400:
        tabs.append(((x1 + x2) // 2, (y1 + y2) // 2, x1))
tabs = sorted(set(tabs), key=lambda t: t[2])
if len(tabs) > 1:
    cx, cy, _ = tabs[1]
    subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)], check=True)
    print(cx, cy)
    sys.exit(0)
sys.exit(1)
PY
      then
        sleep 1
        if has_runnerup_node "laplist" 2>/dev/null; then
          log "Detail Laps tab content visible"
        else
          warn "Detail Laps tab — lap list id not found (layout may differ)"
        fi
      else
        warn "Detail Laps tab — could not tap tab (SKIP)"
      fi
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

  log "Run tab: start_gps_button visible"
  prepare_ui
  if has_runnerup_node "start_gps_button"; then
    log "start_gps_button present"
    if tap_rid "start_gps_button"; then
      sleep 1
      check_logcat_fatal
    else
      warn "start_gps_button visible but not tappable (SKIP tap)"
    fi
  else
    fail "start_gps_button not found on Run tab"
  fi

  log "tap workout mode spinner"
  tap_rid "workout_mode_spinner" || log "warning: workout_mode_spinner not tappable (optional)"
  sleep 1

  log "bottom nav: Best Times (index 2)"
  tap_nav_index 2 || warn "Best Times tab not found (SKIP)"
  sleep 2
  assert_activity_resumed "MainLayout"

  log "bottom nav: Statistics (index 3)"
  tap_nav_index 3 || fail "Statistics tab not found"
  sleep 2
  assert_activity_resumed "MainLayout"

  log "open Month Comparison from Statistics (optional)"
  prepare_ui
  if tap_rid "statistics_card_label" 2>/dev/null; then
    sleep 2
    if focused_app | grep -q MonthlyComparisonActivity; then
      assert_activity_resumed "MonthlyComparisonActivity"
      log "MonthlyComparisonActivity opened"
      adb shell input keyevent KEYCODE_BACK >/dev/null
      sleep 1
      assert_activity_resumed "MainLayout"
    else
      warn "Month Comparison card tap did not open MonthlyComparisonActivity (SKIP)"
    fi
  elif tap_screen_ratio 270 420 2>/dev/null; then
    sleep 2
    if focused_app | grep -q MonthlyComparisonActivity; then
      assert_activity_resumed "MonthlyComparisonActivity"
      log "MonthlyComparisonActivity opened (ratio fallback)"
      adb shell input keyevent KEYCODE_BACK >/dev/null
      sleep 1
      assert_activity_resumed "MainLayout"
    else
      warn "Month Comparison ratio tap did not open activity (SKIP)"
    fi
  else
    warn "Month Comparison entry not tappable (SKIP)"
  fi

  log "bottom nav: Settings (index 4)"
  tap_nav_index 4 || warn "Settings tab not found (SKIP)"
  sleep 2
  assert_activity_resumed "MainLayout"

  log "Settings: Workout → Manage workouts (optional)"
  prepare_ui
  if tap_text_contains "Workout" 2>/dev/null; then
    sleep 1
    if tap_text_contains "Manage workouts" 2>/dev/null || tap_text_contains "Manage_workouts" 2>/dev/null; then
      sleep 2
      if focused_app | grep -q ManageWorkoutsActivity; then
        assert_activity_resumed "ManageWorkoutsActivity"
        if has_runnerup_node "expandable_list_view" 2>/dev/null; then
          log "Manage workouts list visible"
        else
          warn "Manage workouts — list id not found (layout may differ)"
        fi
        if has_runnerup_node "manage_workout_add_fab" 2>/dev/null; then
          log "Manage workouts FAB visible"
        else
          warn "Manage workouts FAB not found (SKIP)"
        fi
        adb shell input keyevent KEYCODE_BACK >/dev/null
        sleep 1
        adb shell input keyevent KEYCODE_BACK >/dev/null
        sleep 1
        assert_activity_resumed "MainLayout"
      else
        warn "Manage workouts preference did not open activity (SKIP)"
        adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
        sleep 1
      fi
    else
      warn "Manage workouts preference not tappable (SKIP)"
      adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
      sleep 1
    fi
  else
    warn "Settings Workout entry not found (SKIP)"
  fi

  log "Settings: Sensors → Heart rate (optional)"
  prepare_ui
  if tap_text_contains "Sensors" 2>/dev/null; then
    sleep 1
    if tap_text_contains "Heart rate" 2>/dev/null || tap_text_contains "Heart Rate" 2>/dev/null; then
      sleep 2
      if focused_app | grep -q HRSettingsActivity; then
        assert_activity_resumed "HRSettingsActivity"
        log "HRSettingsActivity opened"
        adb shell input keyevent KEYCODE_BACK >/dev/null
        sleep 1
        adb shell input keyevent KEYCODE_BACK >/dev/null
        sleep 1
        assert_activity_resumed "MainLayout"
      else
        warn "Heart rate preference did not open HRSettingsActivity (SKIP)"
        adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
        sleep 1
      fi
    else
      warn "Heart rate preference not tappable (SKIP)"
      adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
      sleep 1
    fi
  else
    warn "Settings Sensors entry not found (SKIP)"
  fi

  log "bottom nav: Statistics (index 3) — YearlyStats drill-down (optional)"
  tap_nav_index 3 || warn "Statistics tab not found (SKIP)"
  sleep 2
  assert_activity_resumed "MainLayout"
  prepare_ui
  if tap_screen_ratio 270 520 2>/dev/null; then
    sleep 2
    if focused_app | grep -qE 'YearlyStatsActivity|StatisticsDetailActivity'; then
      log "Statistics drill-down opened"
      adb shell input keyevent KEYCODE_BACK >/dev/null
      sleep 1
      assert_activity_resumed "MainLayout"
    else
      warn "Statistics card tap did not open drill-down (SKIP)"
    fi
  else
    warn "Statistics drill-down not tappable (SKIP)"
  fi

  check_logcat_fatal
  log "PASS"
}

main "$@"
