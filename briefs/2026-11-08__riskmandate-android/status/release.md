# release.android status

> First task list and R-rel-1 done on `work/release`; the gate passes. Workflow changes remain untested live. Stopped; waiting for the conductor.

## Doing
Nothing.

## Done (commit on work/release)
1. **Emulator smoke test**: new `smoke` job (after `release`, nothing depends on it), emulator-runner v2.37.0 by SHA, API 34 x86_64, `scripts/smoke-test.sh` asserts the home screen text. Installs the release APK; while unsigned it is zipaligned and signed with a throwaway key in the job so the minified build is tested. Flakiness: separate job, two fresh-boot attempts, red if both fail, summary names a second-attempt pass. Details in `docs/ci.md`.
2. **Screenshots**: `check` uploads `app/build/screenshots/` as `screenshots-<run>` (`if-no-files-found: ignore`).
3. **Licence gate**: `scripts/ci-licences.sh` runs `licences.sh` and fails on anything not Apache-2.0, MIT or BSD (or unknown), exceptions in `scripts/licences-allow.txt` (empty). Passes now: 84 artifacts, also with a clean Gradle cache; negative case checked.
4. **Dependabot**: `.github/dependabot.yml`, Gradle, GitHub Actions and the firebase-tools lock, weekly Monday, grouped, PRs target `dev` (why in `docs/ci.md`). Only active once it is on `main`.
5. **Release notes**: `scripts/release-notes.sh`, from commit subjects since the last `v*` tag, used for Firebase (`--release-notes-file`) and the GitHub release (`--notes-file`); tag logic checked in a scratch clone.
6. **Gradle dependency verification**: measured (420 components, 195 KB) and not added; reasons and the way to revisit in `docs/ci.md`.

Verified: `actionlint` v1.7.7 clean; `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease` passes (after Maven Central 429 retries); `bash scripts/secret-scan.sh` clean; `bash -n` on the new scripts.

## Untested (cannot run workflows or KVM here)
The `smoke` job end to end (emulator boot, `uiautomator dump` matching `text="..."` for Compose, throwaway signing and install), `download-artifact` in the job, `--release-notes-file` with firebase-tools 13.29.1, `gh release create --notes-file`, the `check` job's new steps on a runner, and Dependabot (needs `main`). First push to `dev` will show it; a red `smoke` job on the first run is most likely a script detail, not the app.

## Next
Nothing, until the conductor sends the next task.

## Blocked
Nothing.

## For other roles
- **security.android**: please confirm the licence allow-list on this page or in `scripts/licences-allow.txt`. Proposed: Apache-2.0, MIT, BSD-2/3 only; ISC and public domain, which `licences.sh` accepts, are not accepted by the CI gate without an entry. Also: `licences.sh` never exits non-zero and writes `docs/security/licences.md`; the CI wrapper parses that table, so keep its column layout (`| artifact | licence | url | flag |`), or tell me when it changes.
- **app.android**: the smoke test expects the home screen to show the texts "RiskMandate" and "Open riskmandate.ai" (`scripts/smoke-test.sh`, `expect_title`, `expect_button`); tell me if they change. Screenshots: write them to `app/build/screenshots/` and they are uploaded.
- **conductor**: Dependabot PRs will arrive on `dev` after the first release PR to `main`; they need merging like any change.

## For Dinis
- None that block this work. Optional: say if you want the smoke test on pull requests too (costs a second build), and whether Dependabot PRs may target `dev` as proposed.

## Update: R-rel-1 (security.android, low)
`scripts/ci-licences.sh` now requires every licence of a multi-licence artifact to be Apache-2.0, MIT or BSD; otherwise the artifact must be in `scripts/licences-allow.txt` with a reason. Checked on a doctored table (Apache+GPL and Apache+EPL fail; MIT+Apache passes; allow-list entries clear them); real run: 84 artifacts pass. Merged origin/dev first; gate, actionlint and secret-scan pass.

## Update: instrumented SmokeTest in the emulator job
The `smoke` job now runs `scripts/emulator-checks.sh` on the one emulator: the release-APK launch check, then `./gradlew connectedDebugAndroidTest` (app.android's `SmokeTest`), uninstalling the release APK in between (signature mismatch otherwise). Debug and test APKs are built before the emulator boots; Android test reports are in `smoke-<run>`. Budget: job timeout 40 min, estimated 10 to 15 min per attempt (not measured). Verified here: `assembleDebugAndroidTest` builds, actionlint, gate pass. Not run: the emulator part (no KVM).

## Update: Firebase auth via Workload Identity Federation (urgent, from conductor)
Release job: `id-token: write` added, `environment: release` kept. `google-github-actions/auth` v2.1.13 (pinned by SHA, lightweight tag, c200f36...) before the Firebase step, with `vars.GCP_WIF_PROVIDER` and `vars.GCP_SERVICE_ACCOUNT`; `fad.json` and `FIREBASE_SERVICE_ACCOUNT_JSON` removed (no fallback: keys cannot be created). Firebase step gated on both variables and `FIREBASE_APP_ID` (variable or secret). Summary reports a failed auth. `docs/ci.md` has the variables table, the WIF setup and the Play follow-up (its JSON key hits the same policy). actionlint, gate and secret-scan pass; the auth and Firebase steps are untested (no run possible from here).
