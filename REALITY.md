# REALITY: what exists (8 November 2026)

- A Kotlin + Compose app `app/`, label "RiskMandate", application ID `ai.riskmandate.app` (**provisional**, waits on Dinis). One native screen with a button that opens https://riskmandate.ai/ in a Custom Tab; only riskmandate.ai over HTTPS is allowed (7 unit tests). minSdk 26, targetSdk 35. Placeholder icon.
- CI `.github/workflows/android.yml`: on push to `dev`, tests and lint, release APK and AAB as a workflow artifact. Signing, Firebase App Distribution and the GitHub release run only once the secrets exist. **None exist yet**, so builds are unsigned and nothing reaches a device.
- `scripts/make-upload-key.sh` for Dinis.

Does not exist yet: the secrets, the Firebase project and tester group, the Play Console account, the agent's key in `board/keys.json`, any device enrolment (M2), the vault, the other two apps.
