# Momn’t Android test build

This project packages the interactive Momn’t HTML application in an Android WebView shell.
It preserves existing screens and local interactions; simulated AI and integrations remain simulated.

## Get your APK

1. Go to the **Actions** tab and enable workflows if prompted.
2. Open **Build Momnt test APK** and click **Run workflow** (or push to `main`).
3. Download **Momnt-test-apk** from the build artifacts. Unzip to get `Momnt-test.apk`.
4. Transfer to your Android device, allow "Install unknown apps", and install.

## Requirements

- Android 8.0+ (API 26+)
- Current Android System WebView
- Internet for Google Fonts on first use (app code and sample photos are bundled)

## What's included

- Launcher icon and standalone app window
- Bundled HTML/JS and 8 sample images served from a secure local origin
- Persistent localStorage data
- System file picker + optional camera for Photo capture
- Native Save As for JSON, CSV, and SVG exports
- Back button handling for dialogs and history
- Keyboard, status bar, nav bar, and display cutout insets

## Not included

- Live AI, OCR, speech, or document analysis
- OAuth, calendar/email sync, bank feeds, or payments
- Push notifications or background services
- Cross-device sync or encrypted storage

See `TEST-CHECKLIST.md` for device testing steps.
