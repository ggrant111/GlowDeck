# Change log

## v1.6.3 — Effect-aware independent color pickers
- Load WLED 0.14+ `/json/fxdata` effect metadata and show only usable Fx, Bg, Cs slots; older versions use the legacy three-color fallback.
- Touch dashboards and Fire TV now have individual slot pickers and an RGB editor; quick colors modify the selected slot.
- Preserve active effect, palette, other color slots, and RGBW white channels when changing one color.
- Send complete segment color arrays to `/json/state` without setting `fx=0`; preserve v1.6.2 send/check/confirm feedback.
- Scene color editing now has individual slots and preserves selected effect.
- Add unit tests for metadata flags, slot preservation, and saved-scene effect preservation.


## v1.6.2 — Live command feedback and verified WLED state
- Immediately show a nonblocking in-app command banner on TV, tablet, and phone: Request queued → Request sent, checking WLED → Confirmed, not confirmed, or failed.
- Confirm device state through independent WLED JSON API reads following each HTTP POST; never equate HTTP 2xx with verified state.
- Apply verification to every state-changing main control, WLED preset save, Favorite and multi-device GlowDeck scene.
- Verify per-segment state, power, brightness, effects, palettes, color, speed/intensity and WLED preset state; saved presets are checked using `/presets.json`.
- Retry read-only verification up to four times for WLED transition and device response delays, never resend commands automatically.
- Surface partial success and indeterminate outcome distinctly from failures. Serial command queue remains intact and stale notifications cannot overwrite more recent actions.
- Preserve Fire OS `SeekBar` compatibility fix from v1.6.1 and all stored app settings.


## v1.6.1 — Fire Tablet startup compatibility hotfix
- Fix crash immediately after launch on older Fire OS / Android versions: removed unsupported `SeekBar.setMinHeight(int)` invocation in `MainActivity.touchSeek`.
- Slider retains its 48dp height via the existing layout parameter. Brightness, Speed and Intensity control behavior is otherwise unchanged.
- Preserve package ID and locally saved device, group, favorite and scene settings during same-signature upgrades.
- Increase Android versionCode to 9 and versionName to 1.6.1.
- Add reproducible Fire Tablet ADB verification instructions.


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
