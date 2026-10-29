# Publish GlowDeck to GitHub

1. Open https://github.com/new while signed in as `ggrant111`.
2. Name the repository `glowdeck-android`. Set visibility to **Private** (you can change it later).
3. Check **Add a README file** when creating the repository so GitHub creates its initial `main` branch. Do not add a `.gitignore` or license; this project includes `.gitignore` and its own README will replace the starter.
4. Tell ChatGPT the repo URL (`https://github.com/ggrant111/glowdeck-android`) and ask it to publish the prepared source using the connected GitHub app.
5. When the files are pushed to the `main` branch, open the repository's **Actions** tab and inspect the build. Download the APK artifact if successful.

## Alternative: upload locally with Git

For the command-line method, create the repository **without** an initial README (unlike the connected-app method above), then run these commands from the directory holding `settings.gradle`:

```bash
git init
git branch -M main
git add .
git commit -m "Initial GlowDeck v1.4 universal Android app"
git remote add origin https://github.com/ggrant111/glowdeck-android.git
git push -u origin main
```

The Android package ID is `com.glowdeck.tv`. For existing installs, debug updates normally require signing with the same key as the previously installed APK. A GitHub Actions debug build uses a fresh debug key, so it may require uninstalling the older APK first; that clears app-local settings.

Do not commit API secrets, signing keys, or password files. No production credentials are needed for local WLED control.
