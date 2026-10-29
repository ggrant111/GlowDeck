# Change log

## v1.7.2 — Native Live Effects Engine
- Five standalone UDP-streamed effects: Chase/Comet, Color Wipe, Rainbow Flow, Pulse/Breathe, Sparkle.
- Live Android controls for effect, primary/secondary color, speed, and brightness on saved physical segments and cross-device zones.
- Native Java frame generator streams 15 FPS directly over WLED realtime UDP to selected LED indexes without a server.
- Combined 1.7.1 DNRGB segment identifier and DDP full-strip test remain available.
- Conservatively limited 90-second sessions, stop-on-dismiss/background, recovery snapshots, active-realtime-source check, and WLED-configured UDP port support.
- Added JVM unit tests for algorithms and offset packetization. VersionCode 14.


## v1.7.1 — No-server live UDP segment identification and DDP test
- Add native unicast UDP WLED streaming from the Android device: DNRGB port 21324 for targeted segment identification and DDP RGB24 port 4048 for a whole-strip connectivity test.
- Preview Solid amber, Chase, or Start/End markers on physical segments or multiple controllers in a saved zone. Additional range-editor preview works without saving segment changes.
- Add 12-second session timeout, explicit stop, saved recovery snapshots, controller identity/range checks, WLED live-stream conflict checks, and best-effort restoration after realtime timeout.
- DDP test deliberately takes over the full selected strip; realtime streaming can pause other effects. Clearly label UDP packets as unacknowledged, not device-confirmed.
- Add pure-Java protocol regression tests for DNRGB headers/chunking, DDP push/offset, 16-bit bounds, and marker animations.
- No background service, cloud or separate server needed; live streaming requires GlowDeck to remain in foreground. Full custom effects engine deferred to v1.7.2.


## v1.7.0 — Segment Studio & multi-device zones
- Physical segment discovery and non-overlapping split/create/resize/rename/delete controls.
- Multi-controller zones targeting individual WLED segment IDs and boundaries.
- Temporary amber physical LED identification with stop, timeout, and recovery snapshots.
- Per-segment effects, palette, brightness, Fx/Bg/Cs colors, power and speed/intensity.
- Android TV and tablet navigation access, phone More entry; continued v1.6.3 color features.
- Optional explicit preset save with WLED segment bounds.
- See docs/V1_7_SEGMENT_STUDIO.md for limitations and hardware acceptance testing.


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
