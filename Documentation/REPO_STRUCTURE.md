# Repository Structure Guide

This repository was reorganized to keep the app behavior unchanged while making source code easier to navigate.

## Modules

- `app`: Android phone app UI, tracking flow, statistics, settings, syncing, and data access wiring.
- `common`: Shared constants/resources used by app and other modules.
- `hrdevice`: Heart-rate device integration module.
- `wear`: Wear OS companion module.

## App Package Layout

Under `app/src/main/org/runnerup/`:

- `features/`: User-facing screens and feature entry points (tabs, activities, fragments, feature adapters).
- `data/`: Database helpers, calculators, path utilities, and DB entities.
- `tracking/`: Tracker engine, GPS status/info, tracker components, and filters.
- `sync/`: Synchronizers, OAuth helpers, export formats, and sync utilities.
- `core/notification/`: Notification state and display strategy classes.
- `core/util/`: Shared utilities (formatting, backup helpers, graph wrapper, networking, parsing).
- `core/content/`: Android content/file providers.
- `core/workout/`: Workout model and workout feedback/trigger logic.
- `ui/common/widget/`: Reusable UI widgets and picker/spinner components.

## Quick Navigation Tips

- Bottom-tab navigation ownership starts in `features/MainLayout` and `features/BottomNavFragmentStateAdapter`.
- History flow starts in `features/HistoryFragment`.
- Statistics entry grid starts in `features/StatisticsFragment`.
- Run/session flow starts in `features/StartFragment` and `features/RunActivity`.
- DB and computed stats are centered in `data/DBHelper` and `data/*Calculator` classes.
