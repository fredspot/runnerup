# History Tab (Second Tab) — UI / UX Reference

This document covers only the **History tab**: visible UI elements, available actions, and what happens when each control is used.

Primary implementation:
- `app/src/main/org/runnerup/features/HistoryFragment.java`
- `app/res/layout/history.xml`
- `app/res/layout/history_row.xml`
- `app/res/layout/filter_dialog.xml`

---

## Screen Layout (High Level)

1. **Top bar** with title **History**.
2. **Filter button** in the top-right area (text toggles between `Filter` and `Clear`).
3. **Activity list** (card-style rows), grouped by month headers.
4. **Floating action button (+)** in bottom-right for manual entry.

---

## Main Controls and What They Do

### 1) Filter button (`history_filter`)

- **Default state:** button text is `Filter`.
- **When no filter is active:**
  - Tap opens a dialog titled **“Filter by Month”**.
- **When a filter is active:**
  - Tap immediately clears the filter (no dialog).
  - List reloads to show all non-deleted activities again.
  - Button text returns to `Filter`.

### 2) Add button (`history_add`, FAB with + icon)

- Tap opens **ManualActivity** (manual workout entry screen).
- After returning, History reloads (so newly added/changed data appears).

### 3) Activity row tap (any item in list)

- Tap opens **DetailActivity** for that activity (`mode=details`).
- The app passes `source_tab=1` so navigation can return to History context.
- After returning from details, list reloads.

---

## Filter Dialog UX

Dialog layout uses two `NumberPicker`s:
- **Year picker** (`year_picker`)
- **Month picker** (`month_picker`, Jan..Dec)

Dialog actions:
- **Apply**
  - Stores selected year + month.
  - Reloads list with month range filter.
  - Changes top button text to `Clear`.
- **Clear**
  - Removes active filter.
  - Reloads full list.
  - Sets top button text to `Filter`.
- **Cancel**
  - Closes dialog with no changes.

---

## Important Current Behavior

### Year list is DB-backed (not static)

The year picker only shows years that actually exist in activity data:
- Derived from `start_time` values in the activity table.
- Uses only records where `deleted = 0`.
- Ordered newest year first.

If no years are found, it falls back to current year so the dialog remains usable.

### Month handling

- UI month picker is Jan..Dec.
- Internal filter month is stored in `0..11` format.
- Programmatic filter calls normalize either `1..12` or `0..11` input.

### Filter scope

When active, filtering is by:
- selected month start (inclusive)
- next month start (exclusive)
- plus `deleted == 0`

---

## List Row Content (per activity card)

Each row shows:
- **Sport icon**
- **Distance**
- **Date/time**
- **Duration**
- **Pace/speed text**
- **Additional info** (typically average HR if present)

Rows are visually grouped by month:
- First row of a month shows a month header.
- Consecutive rows in same month hide repeated header.

---

## What Is Not Present (Current UX)

- No inline swipe actions in History rows.
- No multi-select / bulk actions.
- No in-list search field.
- Filter is month+year only (no sport/type/date-range controls in this tab).

---

*Last aligned with `org.runnerup.features.HistoryFragment` and current `history*.xml` layouts.*
