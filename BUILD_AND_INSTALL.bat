@echo off
setlocal
cd /d "%~dp0"
echo.
echo =========================================
echo        GlowDeck v1.4 - Fire TV Build / Install
echo =========================================
echo.
set "TVIP=192.168.1.24"
set /p "USERIP=Fire TV IP [192.168.1.24]: "
if not "%USERIP%"=="" set "TVIP=%USERIP%"

if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
  set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
) else (
  set "ADB=adb"
)

echo [1/3] Building debug APK with Gradle...
call "%~dp0gradlew.bat" assembleDebug
if errorlevel 1 (
  echo ERROR: Build failed. Open the project in Android Studio and inspect the Build output.
  pause
  exit /b 1
)
if not exist "%~dp0app\build\outputs\apk\debug\app-debug.apk" (
  echo ERROR: The expected APK was not found.
  pause
  exit /b 1
)
echo [2/3] Connecting to Fire TV %TVIP%...
"%ADB%" connect "%TVIP%:5555"
if errorlevel 1 (
  echo ERROR: Could not connect. Check ADB debugging and the Fire TV IP.
  pause
  exit /b 1
)
echo [3/3] Installing APK...
"%ADB%" install -r "%~dp0app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 (
  echo ERROR: Installation failed.
  pause
  exit /b 1
)
echo.
echo Launching GlowDeck TV...
"%ADB%" shell am start -n com.glowdeck.tv/.MainActivity
echo.
echo Completed. Look for GlowDeck TV in Your Apps and Channels.
pause
