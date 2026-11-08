# CI: `.github/workflows/android.yml`

Only Dinis adds secrets (repository settings, Secrets and variables, Actions). Agents never see or write them. The build stays green with zero secrets: every step that needs one is skipped, and the job summary says which and why.

## Jobs

| Job | Runs on | Does |
|---|---|---|
| `check` | every push to `dev`, every pull request, manual | unit tests and lint; uploads the reports as artifact `reports-<run>` (always, pass or fail) |
| `release` | push to `dev` and manual runs on `dev`, after `check` passes | release APK and AAB, signing, artifact `riskmandate-<version>`, distribution |

Both jobs write a job summary: version, application ID (read from the built APK with `aapt2 dump badging`, never hardcoded, because it is provisional), signed or not, and each distribution step as ran, FAILED or skipped with the reason.

## How CI owns the version

`VERSION_CODE` is the workflow's `github.run_number` and `VERSION_NAME` is `0.1.<run_number>`, set as workflow environment variables and read by `app/build.gradle.kts`. Nobody sets them by hand; a local build gets `1` and `0.0.0-local`.

## Secrets

| Secret | What it unlocks | Step | If missing |
|---|---|---|---|
| `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` (all four) | signing with the upload key (made with `scripts/make-upload-key.sh`), and everything below | "Decode the upload key", then "Release APK and AAB" signs | builds are unsigned and named `-unsigned`; Firebase, Play and the GitHub release are all skipped. Summary: `UNSIGNED: UPLOAD_KEYSTORE_* and UPLOAD_KEY_* secrets missing` |
| `FIREBASE_SERVICE_ACCOUNT_JSON`, `FIREBASE_APP_ID` | M1: APK to testers group `team` on Firebase App Distribution (push only) | "Firebase App Distribution" | Summary: `skipped: FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_APP_ID secret missing` |
| `PLAY_SERVICE_ACCOUNT_JSON` (plus the upload key) | the signed AAB to Google Play, a draft release on the chosen track: serves M3 and the managed Play private-app route of M2 | "Google Play (draft release on the chosen track)" | Summary: `skipped: PLAY_SERVICE_ACCOUNT_JSON secret missing` |
| none (uses the built-in `GITHUB_TOKEN`) | prerelease `v<version>` with the signed APK (push only, needs the upload key) | "GitHub release with the APK" | skipped with the key reason |

Play upload details: track `internal` on push to `dev`; a manual run (Actions, android, Run workflow) offers `internal` or `alpha`. The release is created with `status: draft`, so nothing rolls out until a person completes it in the Play Console. The Play app must already exist with the first upload done by hand if the Console requires it, and the service account needs release permission on the app. The package name is read from the build.

Secret files (`upload.jks`, `play.json`, `fad.json`) are written to `$RUNNER_TEMP` and removed in an `always()` step. Secret values are never echoed.

## Pinned actions

All pinned by full commit SHA, tag in the comment, resolved with `git ls-remote` on 8 November 2026 (annotated tags dereferenced to the commit):

| Action | Tag | SHA |
|---|---|---|
| actions/checkout | v4.2.2 | 11bd71901bbe5b1630ceea73d27597364c9af683 |
| actions/setup-java | v4.7.1 | c5195efecf7bdfc987ee8bae7a71cb8b11521c00 |
| gradle/actions/setup-gradle | v4.4.4 | 748248ddd2a24f49513d8f472f81c3a07d4d50e1 |
| actions/upload-artifact | v4.6.2 | ea165f8d65b6e75b540449e92b4886f43607fa02 |
| r0adkll/upload-google-play | v1.1.3 | 935ef9c68bb393a8e6116b1575626a7f5be3a7fb |

Newer majors exist (checkout v6, setup-java v6, upload-artifact v6, gradle/actions v6); moving to them is a follow-up to test. `firebase-tools` is pinned at 13.29.1 by npm version.

## Lint

The workflow passes `actionlint` v1.7.7.
