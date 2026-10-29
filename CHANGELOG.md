# Change log

## v1.6.0 — Multi-Device Scene Builder
- Create independent GlowDeck scenes by capturing actual WLED states from selected controllers.
- Edit each scene light offline: on/off, brightness, colors, effects, re-capture, add/remove controllers.
- Play scenes across devices with explicit state, per-segment IDs and effect/palette name mapping; skip unsupported settings and report partial failure.
- Prevent unintended WLED broadcast synchronization while applying distinct scene looks; transitions are per-command.
- Use stable WLED device IDs where available, falling back to MAC and host to prevent control of the wrong light.
- Link multi-device scenes as existing one-tap Favorites; add Scenes section on Fire TV, tablet, and phone.
- Persist scenes independently from controller presets, with backwards-compatible local configuration.
- Add JVM tests covering capture, serialization, safe editing, cross-firmware mapping and partial failure.
- Cloud sync and Android widgets remain future v1.7/v1.8 work.

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
