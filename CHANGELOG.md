# Change log

## v1.6.2 — Verified WLED command status
- Show immediate, nonblocking in-app notifications for queued, sent/awaiting verification, confirmed and not confirmed/failed commands.
- After WLED state changes, read the device to verify requested power, brightness, effect, color, segment and preset state.
- Read `/presets.json` to verify a preset save operation.
- Cover main controls, Favorites, groups and multi-device scene activation; report partial confirmations.
- Retry GET reads without resending mutations. Do not infer successful changes from HTTP acknowledgement alone.
- Preserve the v1.6.1 Fire OS startup fix, existing WLED features and stored configurations.
- Add unit tests and device acceptance checklist.

## v1.6.1 — Fire Tablet startup compatibility hotfix
- Fix immediate launch crash on older Fire OS: remove unsupported `SeekBar.setMinHeight(int)` call from `MainActivity.touchSeek`.
- Keep existing 48dp slider height via layout parameters; WLED controls otherwise unchanged.
- Preserve local devices, groups, Favorites, and scenes during an in-place upgrade signed with the same key.
- Update Android versionCode to 9 and versionName to 1.6.1; add Fire Tablet acceptance test guide.


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
