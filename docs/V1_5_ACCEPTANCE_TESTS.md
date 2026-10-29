# GlowDeck v1.5 — Device acceptance checklist

Test on Fire TV, phone portrait, 7–11 inch tablet portrait/landscape. Run `gradle :app:testDebugUnitTest :app:assembleDebug`.

## Core
- [ ] Existing v1.4.1 devices, renamed aliases and groups survive installation with the same signing key.
- [ ] Fireplace `192.168.1.71` still works as first-install default.
- [ ] Favorite named `Fireplace On` remains after app process death and orientation changes.
- [ ] Favorite action targets *saved device/group* even if another device is selected.
- [ ] Explicit On/Off is idempotent (same action twice does not unexpectedly toggle).
- [ ] 25/50/75/100 brightness correspond to correct 1..255 values.
- [ ] 8 quick colors set Solid on every active segment without modifying segment topology.
- [ ] WLED preset shortcut matches name on mixed version/ID controllers. Unsupported preset is reported and not replaced by an unrelated one.
- [ ] Disconnect one WLED controller: command updates healthy controllers and reports failed count.
- [ ] Remove/rename/reorder a favorite and restart; order and names persist.
- [ ] Delete a device/group: favorite shows unavailable target, never silently controls a different light.

## Fire TV
- [ ] All controls including Favorites accessible by D-pad and OK without using a mouse.
- [ ] Seven-icon rail still fits on one TV screen.
- [ ] TV bottom Favorites bar does not cause main screen vertical scrolling.
- [ ] Back closes nested modal and returns to opening control.
- [ ] No focus jumps during 8-second WLED refresh while on a favorite shortcut.

## Tablet and phone
- [ ] Favorites card appears above power controls in wide/compact windows.
- [ ] Touch controls >=48dp and no horizontal overflow in 360dp viewport.
- [ ] Drawer and keyboard remain usable in portrait and landscape, including split-screen.
- [ ] App works with Wi-Fi LAN but without external internet.

## Future releases
- No sync credentials or cloud network permission should be required in v1.5.
- No widget permission/service should be added before v1.8.
