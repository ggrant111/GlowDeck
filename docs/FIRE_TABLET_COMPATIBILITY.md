# Fire Tablet / Fire OS startup compatibility test — GlowDeck v1.6.1

## What was fixed
The crash log reported `NoSuchMethodError: SeekBar.setMinHeight(I)V` in `MainActivity.touchSeek` while drawing the phone/tablet dashboard. That method is unavailable on this Fire OS runtime. The redundant call was removed. The existing `group.addView(seek, sized(-1, 48))` already gives the slider a 48dp touch area.

## Acceptance test on hardware
1. Connect the tablet via ADB and run `adb devices`.
2. Install v1.6.1 using `adb install -r app-debug.apk` with the same signing certificate as the previous version.
3. Run `adb logcat -c`.
4. Run `adb shell am force-stop com.glowdeck.tv` and `adb shell am start -n com.glowdeck.tv/.MainActivity`.
5. Verify Home stays open, the brightness slider renders, and `adb logcat -d -b crash -v time` shows no new `com.glowdeck.tv` fatal exception.
6. Interact with Brightness, Speed, and Intensity sliders; verify the app stays responsive.
7. Verify previously saved devices, groups, favorites, and scenes are still present.
8. Test WLED controls on the local network. Also exercise phone/tablet orientation and TV D-pad navigation if available.

The GitHub build/unit tests do not reproduce a specific Fire OS runtime. Physical tablet validation is required.

**Signing warning:** `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means APK signing differs. Do not uninstall before preserving your locally saved configuration.
