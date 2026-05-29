#!/usr/bin/env bash
# Guard against card ripple regressions (rectangular press on rounded cards).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail=0

if rg -q 'foreground="[^"]*selectableItemBackground[^"]*"' app/res/layout 2>/dev/null; then
  echo "FAIL: layout uses foreground=?selectableItemBackground on a view"
  rg 'foreground="[^"]*selectableItemBackground[^"]*"' app/res/layout || true
  fail=1
fi

if rg 'selectableItemBackground.*ripple_card|ripple_card.*selectableItemBackground' app/res/values/styles.xml 2>/dev/null | grep -q .; then
  echo "FAIL: AppTheme wires selectableItemBackground to ripple_card"
  fail=1
fi

if rg '@drawable/ripple_card' app/res/layout app/src 2>/dev/null | grep -q .; then
  echo "FAIL: ripple_card used in layouts or code (use @style/RunnerUpCard)"
  rg '@drawable/ripple_card' app/res/layout app/src || true
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi

echo "check-card-ripple: OK"
