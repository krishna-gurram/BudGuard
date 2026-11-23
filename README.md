# BudGuard

Find My for Galaxy Buds. A personal Android app for the Galaxy Z Fold 8 and Buds4 Pro.

## v0.1 (Phases 0 and 1)

- Logs every connect and disconnect with time and location, anywhere.
- Notifies you when the buds disconnect, with the saved spot.
- Saves the spot when battery hits 15% and 5% (if Android reports battery).
- Diagnostics screen answers the two Phase 0 questions: does Android report bud battery, and does the Android companion link wake the app.

Everything stays on the phone. No accounts, no servers.

## Builds

Every push to `main` builds a signed APK on GitHub Actions and publishes it under Releases.
The signing key in `app/budguard.jks` is for this private personal app only, so each new APK installs over the last one.
