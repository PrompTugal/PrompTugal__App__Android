# REALITY: what exists (8 November 2026)

- A Kotlin + Compose app `app/`, label "RiskMandate", application ID `ai.riskmandate.app` (decided by Dinis, 8 November 2026). One native screen with a button that opens https://riskmandate.ai/ in a Custom Tab; only riskmandate.ai over HTTPS is allowed (7 unit tests). minSdk 26, targetSdk 35. Placeholder icon.
- Managed configuration for M2: app restrictions `start_url` (riskmandate.ai only) and `device_label`, re-read on resume and on change; a Device section shows fully managed, work profile or not detected (public APIs only; to be checked on a real managed device). Custom Tab, ACTION_VIEW fallback, a message if no browser. Cleartext off. 31 JVM tests (Robolectric and Compose UI included), lint 0 errors.
- `devices/`: the AMAPI CLI (no dependencies, 27 tests), the fully managed policy and the M2 runbook. Not yet run against Google.
- CI: `check` and `release` jobs, Play internal-track upload gated on `PLAY_SERVICE_ACCOUNT_JSON`, pinned actions, a run summary; `docs/ci.md`, `docs/lessons.md`.
- CI `.github/workflows/android.yml`: on push to `dev`, tests and lint, release APK and AAB as a workflow artifact. Signing, Firebase App Distribution and the GitHub release run only once the secrets exist. **None exist yet**, so builds are unsigned and nothing reaches a device.
- `scripts/make-upload-key.sh` for Dinis.

Does not exist yet: the secrets, the Firebase project and tester group, the Play Console account, the agent's key in `board/keys.json`, any device enrolment (M2), the vault, the other two apps.
