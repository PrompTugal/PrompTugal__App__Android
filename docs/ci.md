# CI: `.github/workflows/android.yml`

Only Dinis adds secrets (repository settings, Secrets and variables, Actions). Agents never see or write them. The build stays green with zero secrets: every step that needs one is skipped, and the job summary says which and why.

## Jobs

| Job | Runs on | Does |
|---|---|---|
| `check` | every push to `dev`, every pull request, manual | unit tests and lint, then the dependency licence gate; uploads the reports (`reports-<run>`), Robolectric screenshots when `app/build/screenshots/` exists (`screenshots-<run>`) and the licence list (`licences-<run>`), always, pass or fail |
| `release` | push to `dev` and manual runs on `dev`, after `check` passes, in the GitHub Environment `release` | release notes, release APK and AAB, signing, artifact `riskmandate-<version>`, distribution |
| `smoke` | after `release`, same triggers | emulator smoke test of the release APK (below); nothing depends on it |

Both jobs write a job summary: version, application ID (read from the built APK with `aapt2 dump badging`, never hardcoded, because it is provisional), signed or not, and each distribution step as ran, FAILED or skipped with the reason.

## Emulator smoke test (`smoke` job)

`ubuntu-latest` with KVM enabled by the udev rule from the emulator-runner README, `reactivecircus/android-emulator-runner` v2.37.0 (pinned by SHA), API 34, `default` target (no Google APIs, faster), x86_64, headless, animations off. It downloads the artifact the `release` job built and runs `scripts/smoke-test.sh`: install, launch with `monkey`, poll `uiautomator dump` until the home screen shows both "RiskMandate" and the button "Open riskmandate.ai" (up to 60 s), then check the process is still alive. Screenshot, UI dump and logcat go to artifact `smoke-<run>` pass or fail. If the home screen text in `HomeScreen.kt` changes, update `expect_title` and `expect_button` in the script.

Which APK: the one the release job built. Signed with the upload key once it exists. Until then the release APK is unsigned and cannot be installed, so the job zipaligns it and signs it with a throwaway key made in the job (a literal password, not a secret, key discarded with the runner). So the minified R8 build is what is tested, not a debug build. A debug APK would pass things the release build breaks.

Flakiness, how it is handled:
- It is a separate job after `release` and nothing `needs` it, so a flaky emulator cannot hold back signing, the artifact, Firebase, Play or the GitHub release. Cost: a build that does not launch is still distributed; the smoke result arrives a few minutes later and a red `smoke` job is the signal to stop using that build.
- Two attempts, each a fresh emulator boot (`continue-on-error` on the first, the second only runs if the first failed). The summary says "passed on the second attempt" when that happens, so a rising flake rate is visible.
- Generous boot timeout (400 s), 25-minute job timeout, `-no-snapshot` and software GPU for a reproducible boot.
- No AVD cache: it saves about a minute and adds a cache that can itself go stale. Add one if run time matters.
- If it fails twice it is red, not hidden. Do not make it `continue-on-error` at job level: an always-green smoke test tells nothing.
- Not run on pull requests: there is no release APK there, and a second Gradle build would double the cost. Add a `pull_request` build-and-smoke job if wanted.

**Untested from here** (no way to run workflows, no KVM in this session): the whole `smoke` job, the `uiautomator dump` text match on a real emulator (Compose text should appear as `text="..."`), and the throwaway-signing step. `actionlint` and shellcheck (through it) pass; `scripts/smoke-test.sh` was only syntax-checked.

## Dependency licence gate

`scripts/ci-licences.sh` runs `scripts/licences.sh` (security.android's; it lists the release runtime classpath from each POM) and then fails the `check` job if any artifact's licence is not Apache-2.0, MIT or BSD, or is unknown. `licences.sh` itself also accepts ISC and public domain and never exits non-zero; the wrapper is stricter on purpose and reads its table (`docs/security/licences.md`, regenerated in the job, uploaded as `licences-<run>`). Exceptions go in `scripts/licences-allow.txt` (`group:artifact` plus a reason; security.android reviews it). Checked: passes now (84 artifacts, with a clean Gradle cache so the POMs are fetched by the script's own Gradle call); a doctored table with a GPL and an UNKNOWN row fails with exit 1 and an allow-list line clears that row. Proposed policy, to agree with security.android on its status page: allow-list empty, ISC and public domain not accepted without an exception.

## Release notes

`scripts/release-notes.sh <version>` writes Markdown from the commit subjects since the last `v<number>` tag (the tag being released is excluded, so a re-run on a tagged commit still works). Subject `<role>@promptugal.ai: <what>` becomes `- <what> (<role>, <sha>)`; git merges and the conductor's `merge ...` commits are dropped, since the commits they bring are listed. Capped at 30 commits and 3500 characters. With no earlier tag (none exists yet: tags appear only when a signed GitHub release is made) it lists the last 30. The release job (checkout with full history) writes the file once and uses it for Firebase (`--release-notes-file`) and the GitHub release (`--notes-file`). Note that while builds are unsigned there are no tags, so the first signed release will list 30 commits.

## Dependabot

`.github/dependabot.yml`: Gradle, GitHub Actions (SHA pins and their version comments) and the firebase-tools npm lock, weekly on Monday 06:00 UTC, grouped (Gradle: androidx, kotlin, AGP, testing; Actions in one group; firebase-tools in one), commit prefix `deps`. Pull requests target **`dev`**: that is the branch CI builds and releases from and where the conductor merges, so a bump is tested by the same `check` job as any change; `work/release` is a short-lived role branch and `main` is only for Dinis's release PRs. Dependabot reads its config from the default branch, so this file does nothing until a release PR puts it on `main`; it is not tested. Open question for Dinis: Dependabot PRs are not "agent pushes to dev", and the conductor merges them like other changes. Gradle wrapper bumps also need the new `distributionSha256Sum`: check the PR does it, otherwise add it by hand.

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

`gradle-wrapper.properties` carries `distributionSha256Sum` for Gradle 8.11.1 (the `.sha256` published beside the distribution on services.gradle.org), so a tampered download fails. The wrapper jar is validated in CI by `gradle/actions/setup-gradle` v4.4.4: its `validate-wrappers` input defaults to `true` (read from the action's `action.yml` at the pinned SHA), so no separate `wrapper-validation` step is needed. When Gradle is upgraded, update the checksum with it.

### Gradle dependency verification: not added, and why

Measured on 8 November 2026: `./gradlew --write-verification-metadata sha256 testDebugUnitTest lintDebug assembleRelease bundleRelease` on a clean Gradle home produces `gradle/verification-metadata.xml` with 420 components, 3096 lines, 195 KB. It works, but it is not maintainable yet:
- Every dependency bump, including each weekly Dependabot PR, fails CI with "Dependency verification failed" until someone regenerates the file and reviews a diff of hundreds of lines. Dependabot cannot regenerate it. That turns the automated updates (a security control of its own) into manual work.
- The component list depends on which tasks ran (lint, R8, Robolectric and the Android build tools pull different artifacts), so a task added later fails until the file is regenerated. Plugin and build-tool artifacts must be covered too, not only app dependencies.
- It does not cover everything that is downloaded: Robolectric fetches its `android-all` runtime jar with its own Maven resolver at test time, outside Gradle, so those bytes are unverified either way.
- What it buys on a build that already uses pinned versions, the Maven Central TLS channel and a checksummed wrapper: protection against a republished or tampered artifact at the same version. Real, and worth having once the dependency set is stable.

Revisit when the dependency set settles and Dependabot is in use: regenerate with the command above (add `--export-keys` and verify signatures where keys are published), run the gate with `--dependency-verification strict`, and make a documented step of "regenerate the metadata" in every dependency PR (a CI step that fails with that message is enough). Until then the controls are exact versions, the wrapper checksum, the licence gate and the weekly Dependabot review.

## Pinned actions

All pinned by full commit SHA, tag in the comment, resolved with `git ls-remote --tags` on 8 November 2026 and re-verified the same day by a second pass (annotated tags dereferenced with `^{}`; `gradle/actions` v4.4.4 and the emulator runner v2.37.0 are annotated, the others are lightweight tags; the last two rows were resolved later the same day by release.android, once):

| Action | Tag | SHA |
|---|---|---|
| actions/checkout | v4.2.2 | 11bd71901bbe5b1630ceea73d27597364c9af683 |
| actions/setup-java | v4.7.1 | c5195efecf7bdfc987ee8bae7a71cb8b11521c00 |
| gradle/actions/setup-gradle | v4.4.4 | 748248ddd2a24f49513d8f472f81c3a07d4d50e1 |
| actions/upload-artifact | v4.6.2 | ea165f8d65b6e75b540449e92b4886f43607fa02 |
| actions/cache | v4.2.3 | 5a3ec84eff668545956fd18022155c47e93e2684 |
| actions/download-artifact | v4.3.0 | d3f86a106a0bac45b974a628896c90dbdf5c8093 |
| reactivecircus/android-emulator-runner | v2.37.0 | e89f39f1abbbd05b1113a29cf4db69e7540cae5a |
| r0adkll/upload-google-play | v1.1.3 | 935ef9c68bb393a8e6116b1575626a7f5be3a7fb |

Newer majors exist (checkout v6, setup-java v6, upload-artifact v6, gradle/actions v6); moving to them is a follow-up to test. `firebase-tools` is pinned at 13.29.1 with a lockfile (above).

## Lint

The workflow passes `actionlint` v1.7.7.
