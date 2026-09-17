# Validation status

## Completed before delivery

- Parsed Android manifest and all resource XML files
- Checked bundled app and adapter JavaScript syntax
- Verified all 8 sample JPEG assets are packaged
- Verified CSV, JSON, and SVG export payloads with mocked Android channel
- Checked adapter load order and CSP
- Confirmed GitHub Actions workflow is included

Run `node scripts/check-project.cjs` to re-validate.

## Not completed

- Android compilation, lint, APK signing (no Android SDK in build environment)
- Browser rendering and e2e UI tests
- Real device testing (camera, picker, Save As, back gesture, rotation)

Use the GitHub Actions workflow for build/lint and TEST-CHECKLIST.md for device testing.
