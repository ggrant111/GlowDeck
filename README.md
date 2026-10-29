# GlowDeck v1.6.2 — Command Status & WLED State Verification

**New:** A persistent, nonblocking in-app status banner shows **Request queued → Request sent, checking WLED state → Confirmed / Not confirmed / Failed** when GlowDeck changes a light.

Every command in the main WLED control panel, Favorites, WLED preset save, and multi-device scenes now independently checks each controller with `GET /json` after a successful `POST /json/state`; preset creation is verified via `GET /presets.json`. A successful HTTP POST is **not** enough to claim the change occurred. The app does not resend a command while verifying it. After four read attempts, an unmatched state is **not confirmed**, not falsely marked as successful or failed. Group and scene results distinguish confirmed/failed/unconfirmed/unsupported controllers.

The notification does not steal Fire TV D-pad focus. It works on Fire TV, Android tablets, and phones, automatically disappearing after a successful confirmation (approximately 4 seconds) or after an unsuccessful verification (approximately 8 seconds).

**Limits:** The API verifies the state **reported by WLED**, not the physical light output. Long fade transitions or changes made simultaneously by another client can still return **not confirmed**. This is a local-network feature and uses no cloud service or new account.

v1.6.1's older Fire OS SeekBar startup compatibility fix remains included. Configurations and saved devices, Favorites, groups, and scenes are preserved during an in-place install with the same signing key.

To build, use JDK 17 / SDK 35 and run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`. Inspect the Actions run on GitHub to download the debug APK. Run the device checks in `docs/V1_6_2_COMMAND_FEEDBACK_TESTS.md`.

---

# GlowDeck v1.6.1 — Fire Tablet compatibility hotfix

Version 1.6.1 (versionCode 9). Fixes a crash on older Fire OS devices caused by a call to unavailable `SeekBar.setMinHeight(int)` in `MainActivity.touchSeek`. Brightness, Speed, and Intensity sliders retain 48dp height from their existing layout parameters.

**Upgrade:** Keep the same application package (`com.glowdeck.tv`) and install over the old app using the same signing certificate to preserve saved WLED controllers, groups, Favorites, and scenes. Do not uninstall simply to resolve this crash. APKs with different debug signing certificates may be incompatible.

**Test:** See [Fire Tablet compatibility instructions](docs/FIRE_TABLET_COMPATIBILITY.md). Build/unit test success is not a substitute for physical Fire OS testing.

---

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
