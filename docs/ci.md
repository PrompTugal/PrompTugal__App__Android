# CI: `.github/workflows/android.yml`

Only Dinis adds secrets (repository settings, Secrets and variables, Actions). Agents never see or write them. The build stays green with zero secrets: every step that needs one is skipped, and the job summary says which and why.

## Jobs

| Job | Runs on | Does |
|---|---|---|
| `check` | every push to `dev`, every pull request, manual | unit tests and lint; uploads the reports as artifact `reports-<run>` (always, pass or fail) |
| `release` | push to `dev` and manual runs on `dev`, after `check` passes, in the GitHub Environment `release` | release APK and AAB, signing, artifact `riskmandate-<version>`, distribution |

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

Secret files (`upload.jks`, `play.json`, `fad.json`) are written to `$RUNNER_TEMP` under `umask 077` (owner-only) and removed in an `always()` step. Secret values are never echoed.

### The `release` environment (Dinis creates it)

The release job declares `environment: release`, so the release secrets can be gated. What Dinis does, in the repository settings:

1. Settings, Environments, New environment, name it exactly `release`.
2. Add "Required reviewers" (Dinis) if he wants a manual gate: every release run then waits for his approval before any secret is exposed. Optionally limit "Deployment branches" to `dev`.
3. Move the secrets (`UPLOAD_KEYSTORE_*`, `UPLOAD_KEY_*`, `FIREBASE_*`, `PLAY_SERVICE_ACCOUNT_JSON`) from repository secrets into the environment's secrets, then delete the repository copies. Until the repository copies are deleted, anyone who can edit the workflow can still read them without the gate.

What GitHub does while the environment does not exist yet: running a workflow that references a missing environment creates it, with no protection rules and no secrets (GitHub Docs, "Managing environments for deployment", read 8 November 2026). So the build does not break: the job runs ungated and keeps reading the repository secrets as today. Only repository admins can configure the environment afterwards. Note that anyone who can edit workflows can create environments this way, and that without required reviewers the environment gives no protection; the gate exists only once Dinis adds the reviewer and moves the secrets. Not tested live from here (no GitHub write access).

The `check` job references no secret and no environment. In Settings, Actions, also set "Require approval for all outside collaborators" for fork pull requests.

### firebase-tools

Firebase distribution runs `firebase-tools` 13.29.1 from `.github/firebase/package.json` and `package-lock.json` (integrity hashes for the whole tree) with `npm ci --ignore-scripts`, then `.github/firebase/node_modules/.bin/firebase`. Install scripts are skipped: three packages declare them (`fsevents`, macOS only; `protobufjs`, a version-scheme postinstall; `re2`, a native build used by functions features) and `appdistribution:distribute` runs without them (checked: `firebase --version` and `appdistribution:distribute --help` work after an `npm ci --ignore-scripts`). To bump it, change the version in `package.json` and regenerate the lock with `npm install --package-lock-only --ignore-scripts` in that folder, then review the diff. The Firebase service account should have only the role Firebase App Distribution Admin.

### Gradle wrapper

`gradle-wrapper.properties` carries `distributionSha256Sum` for Gradle 8.11.1 (the `.sha256` published beside the distribution on services.gradle.org), so a tampered download fails. The wrapper jar is validated in CI by `gradle/actions/setup-gradle` v4.4.4: its `validate-wrappers` input defaults to `true` (read from the action's `action.yml` at the pinned SHA), so no separate `wrapper-validation` step is needed. When Gradle is upgraded, update the checksum with it. Dependency verification metadata (`gradle/verification-metadata.xml`) is not added here.

## Pinned actions

All pinned by full commit SHA, tag in the comment, resolved with `git ls-remote --tags` on 8 November 2026 and re-verified the same day by a second pass (annotated tags dereferenced with `^{}`; `gradle/actions` v4.4.4 is annotated, the others are lightweight tags):

| Action | Tag | SHA |
|---|---|---|
| actions/checkout | v4.2.2 | 11bd71901bbe5b1630ceea73d27597364c9af683 |
| actions/setup-java | v4.7.1 | c5195efecf7bdfc987ee8bae7a71cb8b11521c00 |
| gradle/actions/setup-gradle | v4.4.4 | 748248ddd2a24f49513d8f472f81c3a07d4d50e1 |
| actions/upload-artifact | v4.6.2 | ea165f8d65b6e75b540449e92b4886f43607fa02 |
| actions/cache | v4.2.3 | 5a3ec84eff668545956fd18022155c47e93e2684 |
| r0adkll/upload-google-play | v1.1.3 | 935ef9c68bb393a8e6116b1575626a7f5be3a7fb |

Newer majors exist (checkout v6, setup-java v6, upload-artifact v6, gradle/actions v6); moving to them is a follow-up to test. `firebase-tools` is pinned at 13.29.1 with a lockfile (above).

## Lint

The workflow passes `actionlint` v1.7.7.
