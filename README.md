<<<<<<< HEAD
=======
# GlowDeck v1.7.2 — Built-in Live Effects Engine

Android application **1.7.2**, versionCode **14**, app package `com.glowdeck.tv`.
One APK for Fire TV, Amazon Fire tablets, and Android phones; all UDP runs **directly from the device running GlowDeck** and never requires a computer or cloud server.

## Get started

1. Connect the Android device and your WLED controllers to the same LAN, allowing local UDP.
2. Open **Segment Studio**. Choose a device segment → **Live Effects (streamed)**, or open **Multi-device Zones** → **Live Effects** for a saved zone.
3. Select **Chase / Comet, Color Wipe, Rainbow Flow, Pulse / Breathe, or Sparkle**. Choose primary and secondary color, speed, and brightness.
4. Press **Start streaming**. The app generates frames locally and sends them directly to the selected ranges via WLED's DNRGB realtime UDP protocol. You can change effects, colors, speed, and brightness while running.
5. Press **Stop & restore**, close the control dialog or leave the app to stop. A **90-second automatic cutoff** limits unattended streaming; the prior WLED logical state is restored on a best-effort basis.

## Critical caveats

- WLED realtime packets may temporarily **override or black out other LEDs on the same controller**, even if you select only a portion of that strip. WLED's typical realtime receiver takes control of the LED output, and segment-isolated continuous streaming is not guaranteed.
- Packets use UDP and carry **no delivery acknowledgments**. Packet counts report data sent by Android, not confirmed LED output. Effects will not continue after GlowDeck exits.
- The renderer runs at a conservative ~15 FPS, capped at 4096 LEDs/controller and 90 seconds/session. Controllers with a different realtime UDP port use `info.udpport` when available.
- The app refuses to overwrite an active outside realtime sender. GlowDeck captures recovery snapshots before streaming and attempts to restore after the 2-second UDP timeout; network failures or force-close may delay recovery. Reopen Segment Studio and use **UDP status / Restore pending lighting** if necessary.
- The first five live effects animate **each selected range separately**, even when multiple devices share a zone. Seamless single-canvas cross-controller animations are intended for v1.7.3.
- WLED's built-in effects remain the best option for autonomous lighting that must run when the Android app closes.

**Tests:** JVM coverage for all five algorithms, absolute pixel offsets, deterministic frames, brightness, and packet boundaries, plus existing segment/scene/Favorites tests. See `docs/V1_7_2_LIVE_EFFECTS_ACCEPTANCE.md`. Automated Android builds cannot substitute for actual WLED hardware tests.

---

# GlowDeck v1.7.1 — Live UDP Segment Identifier and DDP Test

Android app version **1.7.1** (`versionCode 13`, package `com.glowdeck.tv`). This is the first UDP streaming milestone, without a separate server.

## Use

1. Connect the Fire tablet, phone, or Fire TV and WLED controllers to a reachable local Wi-Fi network.
2. Open **Segment Studio → Devices & Segments**. Select a physical segment → **Live UDP identify / chase**; or select a **Multi-device Zone → Live UDP identification**. Choose **Solid**, **Chase**, or **Show start/end**.
3. When editing a numeric LED range, select **Live UDP: Chase / Show ends / Solid** to preview the chosen range (it must be inside an existing physical WLED segment).
4. To test DDP, select **Test DDP on entire strip** for an individual controller and explicitly confirm the warning. The temporary rainbow animation takes over the whole strip, not only the selected segment.
5. Stop using the visible button or let the session expire after 12 seconds. GlowDeck waits for WLED realtime to release, then attempts to restore previous effects and brightness. A recovery snapshot is saved before any UDP packet is sent.

## Networking and limitations

- WLED **DNRGB realtime UDP**: default port **21324** (configurable within WLED itself). DNRGB uses absolute 16-bit pixel indexes and 2-second UDP realtime timeout. Preview frames run at 8 FPS; the app sends only to explicitly chosen controllers and ranges. It does not work over WAN or through networks that block client-to-client UDP.
- **DDP**: unicast port **4048**; RGB24 packets have up to 480 pixels each with byte offsets and PUSH on the final packet. Test frames run at 10 FPS and control the **entire** controller output.
- WLED realtime typically interrupts/clears other effects on the controller. **Selected segment preview does not guarantee unaffected LEDs will continue animating.** No per-packet delivery acknowledgment is available.
- The app must stay open; if Android pauses Segment Studio, GlowDeck stops the sender and tries restoring lights. Sudden termination can delay recovery; the app retries any recorded recovery at next start.
- Preview is limited to 4096 LEDs per controller and 12 seconds to avoid overwhelming older Fire OS devices. DNRGB addresses are limited to 16-bit LED coordinates.
- If another realtime sender is already active (`info.live`), the app refuses to take over. Stop the other sender first.
- **No full custom Live Effects engine or background streaming** is included in v1.7.1; that is the planned v1.7.2 milestone.

See `docs/V1_7_1_UDP_ACCEPTANCE.md` for tests on real WLED hardware. Install in-place using the same signing key to preserve saved configuration.

---

# GlowDeck v1.7.0 — Segment Studio & multi-device zones

**Android versionCode 12.** Create and control individual physical WLED segments, identify LED ranges visually, and combine segments across controllers into a GlowDeck zone. TV and tablet users open **Segments**; phone users open **More → Segment Studio**. See [documentation](docs/V1_7_SEGMENT_STUDIO.md).

Device/zone storage remains local. No cloud sync or Android home widgets have been added yet. Physical-device tests are required.

---

# GlowDeck v1.6.3 — Effect-aware color slots

**Version 1.6.3 (versionCode 11).** Fixes the WLED effect-color workflow: choosing any color no longer switches your chosen effect to Solid. Select separate Fx (primary), Bg (background), or Cs (tertiary) pickers where the WLED effect supports them, then adjust using quick colors or a custom RGB picker. You can change each slot independently while keeping its other colors and RGBW white values.

**WLED integration:** Reads metadata from `GET /json/fxdata` for WLED 0.14+ and parses the second semicolon-separated section to determine active color slots. Older WLED devices use legacy three-color controls. Color updates send only `on` and `seg[].col` to `POST /json/state` (no `fx` or palette changes). A palette-driven effect may not visibly respond to manual color slots even though the WLED controller confirms the stored setting; change palette if needed.

**Scene editing:** Color edits to saved scenes now preserve effects; no live lighting changes until Play. Full-color RGB edit and quick swatches work with separate slots.

**Feedback:** v1.6.2 command sent/confirmed banners remain in effect; all requests are verified against the controller's reported state.

**Testing:** See `docs/V1_6_3_COLOR_TESTS.md`. Automatic CI builds/unit tests don't replace Fire OS hardware testing.

---

# GlowDeck v1.6.2 — Verified Command Feedback

One Android APK supports Fire TV, tablets and phones (`com.glowdeck.tv`, versionCode 10).

## Status notifications
Whenever GlowDeck changes WLED state via a control, Favorite, WLED preset, or saved multi-device scene, a nonblocking banner appears instantly. It shows **Request queued**, then **Request sent · checking WLED state**, then **Confirmed**, **Not confirmed**, or **Failed**. For groups, GlowDeck reports how many controllers were confirmed. This confirmation is based on independent `GET /json` reads after `POST /json/state`, not the HTTP send result. Saved preset creation is checked by reading `/presets.json`.

A timeout does not mean the command failed: if the reported WLED state has not caught up after the verification window, GlowDeck reports **not confirmed** rather than making a false success claim. Physical light output (e.g. visually observed color) cannot be certified by the WLED API; confirmation means the controller *reports* the requested state.

No changes to saved devices, groups, scenes, favorites, or WLED network settings; v1.6.1's Fire Tablet startup fix remains included.

Build: `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug` (JDK 17, Android SDK 35). See the GitHub Actions build artifact.

---

# GlowDeck v1.6.1 — Fire Tablet compatibility hotfix

Version 1.6.1 (versionCode 9). This release resolves a crash that affected some older Amazon Fire tablets when creating the brightness slider. `MainActivity.touchSeek` no longer calls the unavailable `SeekBar.setMinHeight(int)` method; the slider still receives a 48dp height through its layout parameters.

**Upgrade:** Install over the existing GlowDeck app using the same signing certificate to retain devices, groups, favorites, and scenes. Don't uninstall just to fix the crash. If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the signing certificate is different; preserve or back up your configuration before choosing to uninstall.

**Test on Fire Tablet:** `adb logcat -c`, install and launch, then `adb logcat -d -b crash -v time`. See [Fire Tablet compatibility test](docs/FIRE_TABLET_COMPATIBILITY.md). GitHub Actions compiles and runs JVM tests but physical Fire OS confirmation is still required.

---

>>>>>>> 62916f5714b46463127f168628eceeae27726df4
# GlowDeck v1.5 — Favorites & Shortcuts

**Version:** 1.5.0 (Android application ID `com.glowdeck.tv`, versionCode 7). One APK for Fire TV, Android tablets, and phones.

## What's new in v1.5

- ★ **Favorites** now replaces the redundant Scenes item in the TV/tablet sidebar. Phone bottom navigation includes Faves. Saved WLED presets remain accessible under Presets.
- One-tap buttons on the TV's existing bottom shortcut strip (up to three) and a prominent Favorites card on touch devices (up to six). No additional vertical space added to the TV dashboard.
- Favorite actions: explicit Power On / Off, 25/50/75/100% brightness, eight quick colors, and any existing WLED preset.
- Choose **Lights** (one device, All Devices, or a saved Group) before **Favorites > Add Favorite** to set the destination for a shortcut.
- Manage Favorites: run, rename, move up/down, or remove without modifying the original WLED preset or device. Maximum 50 stored favorites.
- Favorites survive rotation and restarts; group actions follow saved membership changes. A missing target, unsupported preset, or offline device shows a meaningful result.
- Local shared configuration repository migrates saved v1.4 devices/groups without losing them and retains legacy keys during the v1.5 beta. Favorites are stored in versioned JSON accessible to future scene/widget components.
- New `ActionDispatcher` performs reusable WLED commands independently of `MainActivity`, with bounded serial execution through the existing WLED network executor. It is designed to be reused by the v1.6 Scene Builder and v1.8 Android widgets.
- Gradle unit tests cover favorite serialization, explicit power/brightness, multi-segment color, preset ID mapping, partial failure, and pre-catalog single-device presets. CI runs unit tests before building the debug APK.

### Get started

1. Open GlowDeck and choose **Lights** to select one controller, a Group, or All Devices.
2. Open **Favorites** (star icon), select **+ Add favorite**, then choose a type.
3. For a WLED preset, allow the device to load the saved presets first. Enter a short shortcut name and save.
4. Use the home-screen shortcut to run it anytime. Use **Manage** to rename/reorder/remove.

**Important:** A favorite acts on its saved target, not whichever device is currently selected. WLED traffic remains local and works without internet; there is no cloud sync yet. Multiple devices receive one request each; if a preset is absent from one controller it is skipped and reported as unsupported rather than applying an unrelated preset.

### Roadmap status

- v1.5: **Favorites and reusable action layer** (implemented, pending real-device acceptance checks).
- v1.6: Independently saved multi-device GlowDeck scenes (not implemented).
- v1.7: Optional cross-device sync / pairing (not implemented).
- v1.8: Android home-screen widgets (not implemented).

## Build, test and install

Open this folder in Android Studio, sync Gradle with JDK 17 and Android SDK 35, and build `app:assembleDebug`. Run `app:testDebugUnitTest` for the action tests. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Or download the latest successful GitHub Actions artifact.

The package ID is unchanged. If updating from an APK signed with a different certificate, Android will reject in-place installation; back up configuration before removing the old app.

---

## Previous v1.4 documentation

# GlowDeck Android — GitHub repository

This repository contains the full GlowDeck v1.4.1 Android Studio project for Fire TV, Android tablets, and Android phones. The GitHub Actions workflow at `.github/workflows/build-apk.yml` builds a **debug APK** on pushes to `main` and on manual runs. After a successful run, download `GlowDeck-v1.6-scenes-debug-apk` under the **Actions → latest successful run → Artifacts** section. You must be signed into GitHub to download the artifact. Debug builds are for sideloading and testing, not Play Store releases.

**Note:** GitHub Actions has not been executed or verified; the source has passed previous syntax checks but must still compile on a machine with Android SDK. The workflow invokes Gradle 8.9 directly rather than the project's bundled custom bootstrap wrapper.

---

# GlowDeck v1.4.1 — Universal Fire TV / Android Tablet / Android Phone

Native Java Android Studio project; supports WLED's local JSON API without a WebView or cloud account. Application ID remains `com.glowdeck.tv` so existing Fire TV users can upgrade (when signed with the same signing key).

## What's new (v1.6)

One APK chooses its interface according to device type and available screen width:

| Environment | Layout and input |
|---|---|
| Fire TV / Android TV (TV mode) | Original v1.3 fixed-height, D-pad focused 16:9 dashboard, 7-icon rail, animated right drawers |
| Android tablet — wide window (720dp+) | Touch-centric left icon rail, two-column dashboard, native touch sliders, scrolling as required |
| Android tablet — narrow window (600–719dp) | Left icon rail with one-column touch dashboard |
| Android phone / compact window (<600dp) | Portrait-friendly stacked cards, bottom icon navigation, bottom sheet-like drawers |

Rotation and split-screen resizing are supported, without losing saved devices/groups. Phone/tablet status and system navigation bars remain available; Fire TV remains immersive. Most interactive touch controls target at least 48dp. Quick colors, effects, actual WLED presets, groups, device scanning, power, brightness, segment support, and palettes are carried over.

**Scenes (v1.6):** Open Scenes on Fire TV/tablet/phone, select **Capture new scene**, name the scene and check the WLED controllers to include. GlowDeck reads each controller's actual `/json` state; if any selected device cannot be captured, the scene is not saved and you can retry. Use Edit to change the per-light look (power, brightness, color, effect) *without changing live lights*, change membership, capture again, rename, duplicate, adjust transition, or add to Favorites. Press Play to apply different saved looks to each controller. Scene execution reports offline/unsupported controllers and avoids syncing those different looks between WLED devices with one-shot UDP suppression. Scenes persist locally in GlowDeck, not WLED preset slots.

**Known limitation:** Scenes are captured snapshots; saving or editing a GlowDeck scene does not save a WLED preset. Physical 3D/2D segment layout and bounds are never changed by a scene. Scene execution requires local LAN access and may show partial failure when effects or segments are no longer supported. Future v1.7 sync will share scenes across installations.

**Slider behavior:** drag the native Brightness, Speed, or Intensity slider. The percentage moves while dragging; a single command is sent when you release, avoiding a flood of network requests. Live polling updates the values without resetting the user's drag position. Fire TV retains the existing remote-specific adjustment mode.

**Drawer behavior:** wide touch devices use right-side drawers; compact phones use a bottom sheet-style drawer. The device can be selected from `Lights`; `More` contains Groups if you're on a phone.

## Build and install on an Android tablet or phone

1. Extract the ZIP and choose **Open** in Android Studio. Select the `GlowDeckTV` folder that contains `settings.gradle`.
2. Sync Gradle; allow Android Studio to download required SDK 35, Gradle 8.9, and Android Gradle Plugin 8.7.3. A JDK 17+ is needed.
3. In Android Studio, choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The result is `app/build/outputs/apk/debug/app-debug.apk`.
4. EITHER transfer `app-debug.apk` to the tablet (via USB or other means), tap to install and approve **Install unknown apps** for the file manager, OR connect the tablet by USB, enable **Developer options > USB debugging**, approve the authorization prompt and run `adb install -r app-debug.apk` in Android Studio's terminal from the folder containing the APK.
5. Open **GlowDeck**. Connect tablet Wi-Fi to the same local network as your WLED controllers. The starting device is **Fireplace: 192.168.1.71**; choose **Lights > Scan / Add** if needed.

**Windows helper:** `BUILD_AND_INSTALL_ANDROID.bat` builds and sends the app to a connected USB / ADB device. Use `adb devices` to identify it. **Do not** run the Fire TV helper for tablet installation.

## Upgrade Fire TV

Same `com.glowdeck.tv` package as v1.3, with unchanged TV UI and remote navigation. To build and install on Fire TV, run `BUILD_AND_INSTALL.bat` or:

```powershell
adb connect 192.168.1.24:5555
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.glowdeck.tv/.MainActivity
```

The two devices do not automatically synchronize their *local* app preferences. The default Fireplace entry is included on each new installation; other devices can be scanned/added. WLED presets live on the controller, and are therefore visible from both devices after connecting.

## WLED feature coverage

- Read `GET /json` to obtain status, version, effects, palettes and segments.
- Read `GET /presets.json` for genuine saved presets.
- Send `POST /json/state` for `on`, `bri`, `fx`, `pal`, `sx`, `ix`, `seg[].col`, `ps`, and `psave`.
- Discover with Android NSD (`_wled._tcp.`) and `GET /json/nodes`; enter IP addresses manually if discovery is blocked by Wi-Fi router settings.
- Create local groups; match effects/palettes/presets by name when a group spans different WLED firmware catalogs.

Official API: https://github.com/wled/WLED-Docs/blob/main/docs/interfaces/json-api.md

## Troubleshooting

- If the Fireplace is offline, visit `http://192.168.1.71/json` in the tablet's browser. If unreachable, check the WLED device's current IP and ensure that tablet and lights are on the same Wi-Fi LAN (not an isolated guest Wi-Fi).
- If `adb` reports multiple devices, include `-s <serial>` in your command or choose a serial in the batch installer.
- If an update fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the current app was built with a different signing key. Back up any device/group configuration you need, then uninstall/reinstall; uninstalling removes locally saved app settings.
- If long effect lists are clipped in landscape phone mode, scroll inside the drawer.
- The app is local-LAN HTTP; don't expose your WLED HTTP interface to the public Internet.

## Validation

Java source parsed successfully and ZIP/XML structure checked. This environment lacks Android SDK and the physical devices, so an APK was not compiled and behavior still requires real-device testing. Android Studio is needed to build.

## App branding in v1.4.1
The new cyan-to-blue G/home/Wi-Fi brand mark is included as the Android launcher icon (`mipmap-*`), the adaptive/round launcher icon (`mipmap-anydpi-v26`), and the 320x180 Fire TV launcher banner (`drawable-nodpi/tv_banner.png`). Full transparent logo assets are kept in `drawable-nodpi/glowdeck_brand_full.png` and `glowdeck_brand_mark.png`. Existing screens, saved devices, package ID and WLED functionality are unchanged. Version code is incremented to 6.

## v1.6 manual acceptance checklist

- Create Movie Night by capturing at least two reachable WLED lights with distinct looks; activate and confirm each receives only its own saved look.
- Edit one light brightness, color and effect; confirm the live lights do not change until Play.
- Edit membership without recapturing existing lights, duplicate and rename, and verify persistence across app restart.
- Confirm one disconnected device does not prevent other lights from activating, and errors name the skipped/failed controller.
- Save scene as Favorite and verify one-click activation on Fire TV with D-pad and on Android touchscreen.
- Check phone portrait, tablet landscape, and 16:9 TV drawer focus; install upgrade over v1.5 using the same signing certificate.
