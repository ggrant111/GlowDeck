@echo off
setlocal
cd /d "%~dp0"
echo GlowDeck v1.4 - Install on Android phone or tablet
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
  set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
) else (
  set "ADB=adb"
)
echo.
echo Enable Developer options and USB debugging on your Android device.
echo Plug the device into the PC, allow the USB debugging authorization popup.
echo.
"%ADB%" devices
if errorlevel 1 ( echo ERROR: adb is not available. & pause & exit /b 1 )
set /p "SERIAL=Device serial (press Enter if only one is connected): "
if "%SERIAL%"=="" (
  set "ADB_DEVICE="
) else (
  set "ADB_DEVICE=-s %SERIAL%"
)
echo.
echo Building with Gradle...
call "%~dp0gradlew.bat" assembleDebug
if errorlevel 1 ( echo ERROR: Build failed. Check Android Studio Build output. & pause & exit /b 1 )
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" ( echo ERROR: APK was not generated. & pause & exit /b 1 )
echo Installing...
"%ADB%" %ADB_DEVICE% install -r "%APK%"
if errorlevel 1 ( echo ERROR: Installation failed. & pause & exit /b 1 )
"%ADB%" %ADB_DEVICE% shell am start -n com.glowdeck.tv/.MainActivity
echo GlowDeck installed and launched. The Android device and WLED must be on the same LAN.
pause
