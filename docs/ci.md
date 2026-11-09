# CI: `.github/workflows/android.yml`

Only Dinis adds secrets (repository settings, Secrets and variables, Actions). Agents never see or write them. The build stays green with zero secrets: every step that needs one is skipped, and the job summary says which and why.

## Jobs

| Job | Runs on | Does |
|---|---|---|
| `check` | every push to `dev` that touches a build path (below), every pull request, manual | unit tests and lint; uploads the reports (`reports-<run>`) and Robolectric screenshots when `app/build/screenshots/` exists (`screenshots-<run>`), always, pass or fail |
| `licences` | same, in parallel with `check` | the dependency licence gate (below); uploads the licence list (`licences-<run>`); `release` needs it |
| `release` | push to `dev` and manual runs on `dev`, after `check` and `licences` pass, in the GitHub Environment `release` | release notes, release APK and AAB, signing, artifact `riskmandate-<version>`, distribution |
| `smoke` | after `release`, same triggers | emulator smoke test of the release APK (below); nothing depends on it |

Both jobs write a job summary: version, application ID (read from the built APK with `aapt2 dump badging`, never hardcoded, so a change of ID needs no workflow edit), signed or not, and each distribution step as ran, FAILED or skipped with the reason.

## Emulator smoke test (`smoke` job)

`ubuntu-latest` with KVM enabled by the udev rule from the emulator-runner README, `reactivecircus/android-emulator-runner` v2.37.0 (pinned by SHA), API 34, `default` target (no Google APIs, faster), x86_64, headless, animations off. It downloads the artifact the `release` job built and runs `scripts/smoke-test.sh`: install, launch with `monkey`, poll `uiautomator dump` until the home screen shows both "RiskMandate" and the button "Open riskmandate.ai" (up to 60 s), then check the process is still alive. Screenshot, UI dump and logcat go to artifact `smoke-<run>` pass or fail. The same emulator then runs app.android's instrumented `SmokeTest` (`./gradlew connectedDebugAndroidTest`: home, licences and diagnostics screens) through `scripts/emulator-checks.sh`, which runs both checks, uninstalls the release APK in between (same application ID, different signing key, so the debug install would otherwise fail), and fails if either failed. The debug and test APKs are built in a step before the emulator boots so emulator minutes are not spent compiling; the Android test reports go in the same artifact. Time budget: one boot (about 3 to 5 minutes), about 1 minute for the launch check, the instrumented test on top, job timeout 40 minutes; with the build step expect roughly 10 to 15 minutes per attempt, not measured. If the home screen text in `HomeScreen.kt` changes, update `expect_title` and `expect_button` in the script.

Which APK: the one the release job built. Signed with the upload key once it exists. Until then the release APK is unsigned and cannot be installed, so the job zipaligns it and signs it with a throwaway key made in the job (a literal password, not a secret, key discarded with the runner). So the minified R8 build is what is tested, not a debug build. A debug APK would pass things the release build breaks.

Flakiness, how it is handled:
- It is a separate job after `release` and nothing `needs` it, so a flaky emulator cannot hold back signing, the artifact, Firebase, Play or the GitHub release. Cost: a build that does not launch is still distributed; the smoke result arrives a few minutes later and a red `smoke` job is the signal to stop using that build.
- Two attempts, each a fresh emulator boot (`continue-on-error` on the first, the second only runs if the first failed). The summary says "passed on the second attempt" when that happens, so a rising flake rate is visible.
- Generous boot timeout (400 s), 25-minute job timeout, `-no-snapshot` and software GPU for a reproducible boot.
- No AVD cache: it saves about a minute and adds a cache that can itself go stale. Add one if run time matters.
- If it fails twice it is red, not hidden. Do not make it `continue-on-error` at job level: an always-green smoke test tells nothing.
- Not run on pull requests: there is no release APK there, and a second Gradle build would double the cost. Add a `pull_request` build-and-smoke job if wanted.

**Untested from here** (no way to run workflows, no KVM in this session): the whole `smoke` job, the `uiautomator dump` text match on a real emulator (Compose text should appear as `text="..."`), and the throwaway-signing step. `actionlint` and shellcheck (through it) pass; `scripts/smoke-test.sh` was only syntax-checked.

## Cost under load: what runs, what is cancelled, how long it takes

**Docs-only pushes run nothing.** The `push` trigger has a `paths` filter: the workflow runs on a push to `dev` only if it changes a file under `app/`, `gradle/` or `.github/`, any `*.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat` or `scripts/`. A push that changes only `docs/`, `board/`, `briefs/`, `devices/`, `README.md` and the like has no run: no tests, no build, no Firebase notification, no GitHub release, and no run number used (so versions skip nothing). GitHub evaluates the filter on every commit of the push, so a push that carries one app change still builds. Pull requests and manual runs are not filtered. Consequences: the conductor sees no new run for a docs-only push (the last run stays the latest signal); `devices/` changes are not built (the APK does not contain them and its tests are not part of this workflow, run `node --test devices/test/*.test.mjs` as the gate says); a change to a path outside the list that the build reads would need adding to it.

**Concurrency is per job** (it was per workflow, which made a push wait for the whole previous run, smoke test included):
- `check`, `licences`, `smoke`: `cancel-in-progress: true` per ref. A newer push cancels the older run's job; the older run's `release` then does not start (`needs`), so that commit is not built and the newer one is. Its release notes cover both.
- `release`: group `release-<ref>`, `cancel-in-progress: false`. A running release (it may be distributing to testers) is never cancelled; at most one more waits, and a newer waiting one replaces an older waiting one (GitHub cancels the pending run in the group, not the running one). Nothing else shares that group, so a newer push's `check` cannot cancel it.
- A cancelled older run shows as cancelled in the Actions list; that is the saving, not a failure. Not tested live: the pending-release replacement and how it interacts with a required reviewer on the `release` environment (a job waiting for approval may count as pending).

**Run time (measured, 8 to 9 November 2026, runs 1 to 31 of this workflow, from the Actions API: `run_started_at` to `updated_at`, so it includes time spent waiting for the workflow-level concurrency group):**

| Period | Runs (successful) | Median | Range |
|---|---|---|---|
| Before the smoke job (runs 1 to 18) | 12 | 5.6 min | 3.8 to 9.0 |
| With the smoke job (runs 22 to 27) | 6 | 12.7 min | 8.1 to 17.2 |
| Failed at the licence gate (runs 28 to 31) | 4 | 1.6 to 2.6 min | n/a |

Where one run's time goes (run 27, per-job timestamps): `check` 1 m 45 s (tests and lint 53 s), `release` 2 m 0 s (APK and AAB 75 s, Firebase 13 s), `smoke` 3 m (APK build 39 s, emulator launch check and instrumented test 1 m 43 s). That is about 7 minutes of work; the run took 15 minutes because it waited about 8 minutes for the previous run's whole workflow to finish (check started at 00:35:29, run started at 00:27:22). Per-job concurrency removes that wait: `check` no longer queues behind the previous run's smoke job. Expected after this change (not yet measured): about 7 to 8 minutes from push to Firebase notification for a build that changes app code, and nothing for a docs-only push. Re-measure after a few runs; the median above will be replaced.

## Dependency licence gate

Own job `licences` (beside `check`, so no wall-clock cost), with a **fresh Gradle home** (`GRADLE_USER_HOME` under `$RUNNER_TEMP`). Reason, found on runs 28 to 31: inside the `check` job, where `setup-gradle` restores a warm Gradle cache, `scripts/licences.sh` found no POM for any of the 84 artifacts (all UNKNOWN) and the gate failed every run, blocking `release`. The script reads each POM from the Gradle cache; a restored cache has the resolution metadata but not the POM files, so Gradle never re-fetched them. This is an inference from the log and from a clean-cache run passing locally; the fix is the first live test. A fresh home costs about a minute of metadata downloads, in parallel with `check`.

`scripts/ci-licences.sh` runs `scripts/licences.sh` (security.android's; it lists the release runtime classpath from each POM) and then fails the `licences` job if any artifact's licence is not Apache-2.0, MIT or BSD, or is unknown; for an artifact with several licences, every one must be allowed (R-rel-1). `licences.sh` itself also accepts ISC and public domain and never exits non-zero; the wrapper is stricter on purpose and reads its table (`docs/security/licences.md`, regenerated in the job, uploaded as `licences-<run>`). Exceptions go in `scripts/licences-allow.txt` (`group:artifact` plus a reason; security.android reviews it). Checked: passes now (84 artifacts, with a clean Gradle cache so the POMs are fetched by the script's own Gradle call); a doctored table with a GPL and an UNKNOWN row fails with exit 1 and an allow-list line clears that row. Proposed policy, to agree with security.android on its status page: allow-list empty, ISC and public domain not accepted without an exception.

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
| none: variables `GCP_WIF_PROVIDER`, `GCP_SERVICE_ACCOUNT` and `FIREBASE_APP_ID` (a variable or a secret) | M1: APK to testers group `team` on Firebase App Distribution (push only), authenticated keyless (below) | "Google auth (Workload Identity Federation)", then "Firebase App Distribution" | Summary: `skipped: variables GCP_WIF_PROVIDER, GCP_SERVICE_ACCOUNT or FIREBASE_APP_ID (variable or secret) missing`, or `FAILED at Google auth ...` |
| `PLAY_SERVICE_ACCOUNT_JSON` (plus the upload key) | the signed AAB to Google Play, a draft release on the chosen track: serves M3 and the managed Play private-app route of M2 | "Google Play (draft release on the chosen track)" | Summary: `skipped: PLAY_SERVICE_ACCOUNT_JSON secret missing` |
| none (uses the built-in `GITHUB_TOKEN`) | prerelease `v<version>` with the signed APK (push only, needs the upload key) | "GitHub release with the APK" | skipped with the key reason |

Play upload details: track `internal` on push to `dev`; a manual run (Actions, android, Run workflow) offers `internal` or `alpha`. The release is created with `status: draft`, so nothing rolls out until a person completes it in the Play Console. The Play app must already exist with the first upload done by hand if the Console requires it, and the service account needs release permission on the app. The package name is read from the build.

Secret files (`upload.jks`, `play.json`) are written to `$RUNNER_TEMP` under `umask 077` (owner-only) and removed in an `always()` step. Secret values are never echoed.

### The `release` environment (Dinis creates it)

The release job declares `environment: release`, so the release secrets can be gated. What Dinis does, in the repository settings:

1. Settings, Environments, New environment, name it exactly `release`.
2. Add "Required reviewers" (Dinis) if he wants a manual gate: every release run then waits for his approval before any secret is exposed. Optionally limit "Deployment branches" to `dev`.
3. Move the secrets (`UPLOAD_KEYSTORE_*`, `UPLOAD_KEY_*`, `PLAY_SERVICE_ACCOUNT_JSON`, and `FIREBASE_APP_ID` if it is a secret) from repository secrets into the environment's secrets, then delete the repository copies. Until the repository copies are deleted, anyone who can edit the workflow can still read them without the gate.

What GitHub does while the environment does not exist yet: running a workflow that references a missing environment creates it, with no protection rules and no secrets (GitHub Docs, "Managing environments for deployment", read 8 November 2026). So the build does not break: the job runs ungated and keeps reading the repository secrets as today. Only repository admins can configure the environment afterwards. Note that anyone who can edit workflows can create environments this way, and that without required reviewers the environment gives no protection; the gate exists only once Dinis adds the reviewer and moves the secrets. Not tested live from here (no GitHub write access).

The `check` job references no secret and no environment. In Settings, Actions, also set "Require approval for all outside collaborators" for fork pull requests.

### Firebase auth: Workload Identity Federation (keyless)

The organisation (thecyberboardroom.com) blocks service-account keys (`iam.disableServiceAccountKeyCreation`), so there is no `FIREBASE_SERVICE_ACCOUNT_JSON` any more (removed, no fallback: a key cannot be created). The release job has `permissions: id-token: write`, and `google-github-actions/auth` v2.1.13 (pinned by SHA) exchanges the job's GitHub OIDC token for short-lived Google credentials and sets `GOOGLE_APPLICATION_CREDENTIALS` for firebase-tools. The file it writes holds only the federation config, no secret, and the action deletes it in its post step.

Repository (or `release` environment) **variables**, not secrets, because none of them is secret:
- `GCP_WIF_PROVIDER`: the provider resource name, `projects/<number>/locations/global/workloadIdentityPools/<pool>/providers/<provider>`.
- `GCP_SERVICE_ACCOUNT`: the service account's email; it needs only the role Firebase App Distribution Admin, and the pool principal needs `roles/iam.workloadIdentityUser` on it.
- `FIREBASE_APP_ID`: the Firebase app ID, as a variable or (still supported) a secret; the variable wins if both exist.

Dinis's setup (from the conductor's note, not seen or checked from here): the provider trusts only repository `PrompTugal/PrompTugal__App__Android` in environment `release`, so the job must keep `environment: release`; a workflow on another repository, branch-only job or a job without that environment gets no credentials. If the attribute condition is changed, change nothing here. The Firebase step runs only on push with the upload key and all three values present. **Untested**: no run has happened (no GitHub access from here); a wrong provider, service account or condition shows in the summary as `FAILED at Google auth`.

**Follow-up, Play:** the Play upload still uses `PLAY_SERVICE_ACCOUNT_JSON`, a service-account key. The same policy blocks creating that key, so Play needs the same keyless route (the `auth` step can serve both: the Play step would take the credentials file instead of `serviceAccountJson`, if `r0adkll/upload-google-play` accepts it, or the upload moves to a small script against the Play API) or a policy exception for that project. Until then the Play step stays skipped.

### firebase-tools

Firebase distribution runs `firebase-tools` 13.29.1 from `.github/firebase/package.json` and `package-lock.json` (integrity hashes for the whole tree) with `npm ci --ignore-scripts`, then `.github/firebase/node_modules/.bin/firebase`. Install scripts are skipped: three packages declare them (`fsevents`, macOS only; `protobufjs`, a version-scheme postinstall; `re2`, a native build used by functions features) and `appdistribution:distribute` runs without them (checked: `firebase --version` and `appdistribution:distribute --help` work after an `npm ci --ignore-scripts`). To bump it, change the version in `package.json` and regenerate the lock with `npm install --package-lock-only --ignore-scripts` in that folder, then review the diff. The Firebase service account (see above) should have only the role Firebase App Distribution Admin.

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
| google-github-actions/auth | v2.1.13 | c200f3691d83b41bf9bbd8638997a462592937ed |

Newer majors exist (checkout v6, setup-java v6, upload-artifact v6, gradle/actions v6); moving to them is a follow-up to test. `firebase-tools` is pinned at 13.29.1 with a lockfile (above).

## Lint

The workflow passes `actionlint` v1.7.7.
