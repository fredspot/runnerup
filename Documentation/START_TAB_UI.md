# Start Tab (First Tab) — UI / UX Reference

This document describes only the **first bottom-navigation tab** (“Run” / start new runs): what appears on screen, which controls exist, and what happens when you interact with them. Implementation lives primarily in `StartFragment` and `app/res/layout/start.xml` (plus nested `start_basic`, `start_interval`, `start_advanced`).

---

## Screen Layout (High Level)

1. **Top bar** — Title **“Run”** and a **workout mode** dropdown.
2. **Main content** — One of three **modes** (Basic / Interval / Advanced). The standard Android tab strip is **hidden**; switching modes is done via the dropdown (and internally via a `TabHost`).
3. **Status strip** (when relevant) — Collapsible row for **GPS / heart rate / Wear OS** status, optional **“enable GPS” / “start GPS” / “start tracker”** action on the right.
4. **Bottom area** — Large **primary action button** (“Start GPS” or “Start Activity”), a **heart-rate status icon**, and optionally **satellite count** (fixed / available).

---

## Top Bar Controls

### “Run” title

Static text label. Not tappable.

### Workout mode dropdown (`workout_mode_spinner`)

- **What it shows:** A spinner styled like a compact dropdown with three options:
  - **Basic**
  - **Interval**
  - **Advanced**
- **What happens when you change it:** The main content switches to the corresponding mode (same as changing the hidden tab). The spinner selection stays in sync if the mode is changed from code (e.g. opening with an intent that requests Advanced).

---

## Basic Mode

Layout: `start_basic.xml`.

All configuration rows are **TitleSpinner** controls: a labeled row that opens a picker or list when tapped (not separate “buttons” in the layout).

| Control | Purpose | What happens when you use it |
|--------|---------|------------------------------|
| **Audio cue settings** | Choose which audio cue scheme to use for this run. | Opens the TitleSpinner flow. If you pick the last list entry (reserved for configuration), the app opens **Audio cue settings** (`AudioCueSettingsActivity`) instead of selecting a scheme. |
| **Target** | Choose the workout target type (e.g. free run, target pace, heart-rate zone — per app string arrays / dimensions). | Opens target type selection. After you confirm, **Target pace** and **Target heart rate zone** visibility update: pace controls show for pace targets; HR zone spinner shows for HR zone targets. |
| **Target pace (HH:MM:SS)** | Sets pace target when target type is pace. | Opens duration-style picker; value is stored for the workout builder. Hidden when not applicable. |
| **Target heart rate zone** | Sets HR zone when target type is HR zone. | Opens HR zone list. Hidden when HR zones are not configured (the Target type can disable the HR zone option). |

---

## Interval Mode

Layout: `start_interval.xml` (inside a `ScrollView` for small screens).

| Control | Purpose | What happens when you use it |
|--------|---------|------------------------------|
| **Audio cue settings** | Same idea as Basic. | Same as Basic; last entry opens **Audio cue settings**. |
| **Repetitions** | Number of interval repeats. | Opens number picker; stored in preferences. |
| **Interval type** | Time-based vs distance-based interval. | Changing type **shows either** the **interval time** row **or** the **interval distance** row (the other is hidden). |
| **Interval time / distance** | Length of work interval. | Pick time or distance depending on interval type. |
| **Rest type** | Time-based vs distance-based rest. | Changing type **shows either** **rest time** or **rest distance** (the other is hidden). |
| **Rest time / distance** | Rest duration between work intervals. | Pick time or distance depending on rest type. |
| **Warm up** / **Cool down** checkboxes | Intended to toggle warm-up and cool-down phases. | **UI only in current code:** `WorkoutBuilder` builds interval workouts with warm-up/cool-down effectively **always on** (hard-coded). Tapping these checkboxes does **not** change saved preferences or workout structure today. |

---

## Advanced Mode

Layout: `start_advanced.xml`.

| Control | Purpose | What happens when you use it |
|--------|---------|------------------------------|
| **Audio cue settings** | Advanced-mode audio scheme. | Same pattern as other modes; last entry opens **Audio cue settings**. |
| **Workout** | Selects a saved advanced workout. | Opens list of workouts. Choosing a workout **loads** it and fills the **step list** below. The list’s last entry is reserved for “manage workouts,” but **creating/editing that screen is disabled**: the app **falls back to the first real workout** instead of opening the old manage activity. |
| **Step list** (`ListView`) | Read-only overview of steps in the selected workout. | Displays steps; tapping behavior is list-driven (no separate primary action here). |

---

## Status Strip (GPS / HR / Wear)

This block may be **hidden** depending on GPS/tracker state. When visible:

| Element | Role |
|--------|------|
| **Tap anywhere on the status area** (`status_layout`) | **Expands or collapses** extra detail (e.g. longer GPS text, HR text). The chevron icon updates (up/down). Also adjusts bottom spacing for the main button so content does not overlap. |
| **GPS icon + message** | Summarized GPS state (off, searching, poor / acceptable / good fix). |
| **Expanded GPS detail row** | More detail (e.g. satellite counts, accuracy) when expanded. |
| **HR icon + message** | Shown when an HR monitor is configured; message/detail follows expand state. |
| **Wear OS icon + message** | Shown when Wear is configured; reflects connection state. |
| **`gps_enable_button` (“Enable GPS” / “Start GPS” / “Start tracker”)** | Shown when GPS/search has **not** been started in the way the old flow expected. **Tap:** requests permissions if needed, then **starts GPS** (or starts the tracker) if not yet in the connected state. Text depends on GPS enabled vs sport-without-GPS. |

The small **expand chevron** next to the status text is visual feedback; the **whole status row** is the click target for expand/collapse.

---

## Primary Bottom Button (`start_gps_button`)

This is the main call-to-action. Label and style **change with state** (not three separate buttons):

| Visible state | Label (typical) | Enabled? | What happens on tap |
|---------------|-----------------|----------|----------------------|
| GPS not started | **Start GPS** | Yes | Checks **location (and related) permissions** (may show system dialog or settings). Then **starts GPS** / search. |
| GPS started but not fixed / tracker not connected | **Start Activity** | **No** (greyed style) | No action — user waits for fix / connection. |
| GPS fixed and tracker connected | **Start Activity** | Yes | **Starts the workout** with the current mode’s configuration, then **opens the run screen** (`RunActivity`). Also stops the “searching” GPS notification pattern used on the start screen. |

**Advanced mode extra rule:** If no advanced workout is loaded, the logic that would enable starting is not satisfied (you must have a valid selected workout).

---

## Indicators Below the Primary Button

| Control | Purpose |
|--------|---------|
| **`new_hr_indicator`** | Heart icon: **bright** when an HR sensor is connected, **faded grey** when not. Informational only (not a separate button). |
| **`new_satellite_info`** | When GPS has been started, shows **satellites fixed / available** (e.g. `3/12`). Hidden until GPS is active. |

---

## Permissions & System Dialogs (UX)

From this tab, the user may be interrupted by:

- **Location permission** rationale dialog, then the system permission sheet.
- On newer Android: **background location**, **activity recognition** (if cadence/step sensor is used), **Bluetooth** (if a BT HR device is configured), **notifications** (Android 13+).
- Optional **battery optimization** prompt (can be dismissed or “don’t show again” depending on path).

These are not separate buttons on the start layout; they are triggered by **Start GPS** / **gps_enable_button** / permission checks.

---

## Summary: “Buttons” vs Tappable Rows

- **Explicit buttons:** **Workout mode dropdown** (acts as mode switch), **primary bottom button** (Start GPS / Start Activity), **`gps_enable_button`** in the status strip when shown.
- **Tappable rows (TitleSpinners):** All **Basic / Interval / Advanced** labeled settings behave as **one row = one dialog/picker**, plus special cases for **audio** and **advanced workout** list endpoints as described above.
- **Tappable strip:** **Status area** toggles expanded device/GPS detail.
- **Checkboxes:** **Warm up / Cool down** in Interval — visible but **not connected** to workout building in current code.

---

*Last aligned with package layout: `org.runnerup.features.StartFragment` and `app/res/layout/start*.xml`.*
