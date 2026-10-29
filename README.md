# GlowDeck Android — GitHub repository

This repository contains the full GlowDeck v1.4 Android Studio project for Fire TV, Android tablets, and Android phones. The GitHub Actions workflow at `.github/workflows/build-apk.yml` builds a **debug APK** on pushes to `main` and on manual runs. After a successful run, download `GlowDeck-v1.4-debug-apk` under the **Actions → latest successful run → Artifacts** section. You must be signed into GitHub to download the artifact. Debug builds are for sideloading and testing, not Play Store releases.

**Build status:** GitHub Actions successfully compiled and uploaded a debug APK. The workflow uses Gradle 8.9 with the official validated wrapper JAR. Physical Fire TV, tablet, and phone testing is still required.

---

# GlowDeck v1.4 — Universal Fire TV / Android Tablet / Android Phone

Native Java Android Studio project; supports WLED's local JSON API without a WebView or cloud account. Application ID remains `com.glowdeck.tv` so existing Fire TV users can upgrade (when signed with the same signing key).

## What's new (v1.4)

One APK chooses its interface according to device type and available screen width:

| Environment | Layout and input |
|---|---|
| Fire TV / Android TV (TV mode) | Original v1.3 fixed-height, D-pad focused 16:9 dashboard, 7-icon rail, animated right drawers |
| Android tablet — wide window (720dp+) | Touch-centric left icon rail, two-column dashboard, native touch sliders, scrolling as required |
| Android tablet — narrow window (600–719dp) | Left icon rail with one-column touch dashboard |
| Android phone / compact window (<600dp) | Portrait-friendly stacked cards, bottom icon navigation, bottom sheet-like drawers |

Rotation and split-screen resizing are supported, without losing saved devices/groups. Phone/tablet status and system navigation bars remain available; Fire TV remains immersive. Most interactive touch controls target at least 48dp. Quick colors, effects, actual WLED presets, groups, device scanning, power, brightness, segment support, and palettes are carried over.

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

GitHub Actions successfully built a debug APK; real-device behavior still needs validation on Fire TV, Android tablets and Android phones. Android Studio is also supported for local builds.
