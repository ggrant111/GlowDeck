# Change log

## v1.5.0 — Favorites & One-Tap Shortcuts
- Added actionable shortcuts for explicit power, brightness, solid color, WLED presets.
- Separate Favorites UI for Fire TV, tablet and phone. Replace TV shortcut strip with Favorites without increasing vertical footprint.
- Reusable Favorite model and WLED ActionDispatcher service; partial-failure reporting and per-controller preset name matching.
- Cross-screen shared local configuration repository, automatically migrates and retains legacy device/group settings.
- Add/manage/reorder/delete favorites with saved targets.
- Added unit tests, CI test execution, incremented Android versionCode to 7.
- No multi-device *scene editor*, cloud sync or Android widgets yet; those remain future milestones.

## v1.4.0 — Universal Adaptive UI
- Retains v1.3 Fire TV design, D-pad focus, settings drawers, WLED integration.
- Adds Android tablet interface, responsive two-column/wide and single-column/narrow layouts.
- Adds Android phone layout with bottom navigation and portrait support.
- Adds platform native SeekBar touch controls with command-on-release.
- Adds touch-friendly color swatches, larger preset/effect tiles and bottom-sheet phone drawers.
- Handles Android rotation and window resize; restores touch viewport policy per device.
- Removes manifest-enforced landscape orientation.
- Adds USB ADB tablet installation helper and documentation.

## v1.3.0
- D-pad focus restoration, slider adjust mode, live state updates without relayout.
