# Lessons: Android development and publishing

RiskMandate is the app we learn on (Dinis, 8 November 2026); the PrompTugal app comes after it and starts from this file. Each lesson: what happened, what we do now.

## Build and CI
- The cloud session can install the Android SDK (cmdline-tools, platform 35, build-tools 35) from dl.google.com and build locally; no emulator needed for unit and Robolectric tests.
- Maven Central through the session's proxy sometimes answers 429. Retry; it passes.
- CI owns the version: `versionCode` is the run number, `versionName` is `0.1.<run>`. Nothing in the repo sets them.
- Every step that needs a secret is skipped cleanly when the secret is absent, so the build stays green from day one and each secret switches one step on.
- The application ID is read from the built APK (`aapt2 dump badging`), not hardcoded in the workflow.

## Signing
- Play App Signing: CI holds only the upload key; Google holds the app-signing key. Agents cannot write GitHub secrets, so Dinis makes the upload key on his machine (`scripts/make-upload-key.sh`) and adds the secrets.
- A build from Firebase App Distribution is signed with the upload key; a build from Google Play is signed by Google. A device must uninstall one before installing the other.

## Publishing and device management
- The application ID is fixed for ever once on Google Play. Decide it before the first upload.
- The Android Management API's terms list in-house-only solutions as not permitted; check the route before relying on it (see `devices/README.md`).
- AMAPI device quota beyond the default needs a business justification to Google.
- Fully managed enrolment happens only at the welcome screen: a device already set up must be factory-reset.
