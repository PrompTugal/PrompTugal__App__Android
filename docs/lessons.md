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

## Open source and for sale
At Dinis's word (8 November 2026): these apps are open source and will be put for sale eventually. What follows from that:
- The code is Apache 2.0 (`LICENSE`). Anyone may build it; what is sold is the signed, supported build on the store. Nothing secret may ever be in the repository or its history: keys, service accounts and tester lists stay in GitHub secrets and Google's consoles.
- **Correction (8 November, from Google's Managed Google Play Help, answer 6145139):** an app restricted to organisations in Play Console stays private for ever; a public listing then needs a new package name. The iframe-only route cannot be made public either. So the organisation build must not use `ai.riskmandate.app`: recommended, a separate package ID for the private build (an `applicationIdSuffix` flavour) and `ai.riskmandate.app` kept for the public, paid app. Dinis decides (docs/play/README.md, step 8).
- Selling on Google Play needs a payments (merchant) profile on the Play Console account, owned by the selling company.
- The names and icons (RiskMandate, PrompTugal) are best kept as trademarks, outside the code licence, so a fork cannot sell itself as the original. A `NOTICE`/`TRADEMARKS` file says so once Dinis confirms.
- Every dependency must have a licence compatible with Apache 2.0 and with sale; CI should list them (a follow-up).
