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

## Update: fix to the WIF commit
The Firebase step had the `npm ci` line and the `firebase` command joined into one (my edit dropped a newline); now two commands, checked by parsing the YAML. "(provisional)" removed from the summary and `docs/ci.md`. actionlint passes; the Gradle gate was not re-run, since only the workflow and docs text changed after the last pass.

## Update: make-upload-key.sh message
Closing message now points to Settings > Environments > release > Environment secrets and lists `FIREBASE_APP_ID`, `GCP_WIF_PROVIDER`, `GCP_SERVICE_ACCOUNT` as environment variables; the header documents the no-JDK `docker run` form (note on root-owned files on Linux). `bash -n` and secret-scan pass.
- For play.android: `docs/play/README.md` line 89 still says "four repository secrets"; they now live under Settings > Environments > release.

## Update: CI cheap and fast (from conductor) and the licence-gate failure on dev
- **Found and fixed:** dev runs 28 to 31 all failed at my licence gate inside `check` (every artifact UNKNOWN: with the warm Gradle cache restored there are no POMs for `licences.sh` to read), so `release` has not run since run 27. The gate is now its own parallel job `licences` with a fresh Gradle home; `release` needs `check` and `licences`. Cause is inferred from the log; the next dev run is the test.
- **Docs-only pushes:** `push` has a `paths` filter (app/, gradle/, .github/, *.gradle.kts, gradle.properties, gradlew*, scripts/); docs, board, briefs, devices/ runs nothing (so no Firebase notification).
- **Concurrency per job:** check, licences, smoke cancel older runs; release never cancelled once running (group `release-<ref>`).
- **Run time:** median 5.6 min before the smoke job, 12.7 min with it (measured from the Actions API, in `docs/ci.md`), of which about 8 min was waiting for the previous run; expected about 7 to 8 min after this change, to re-measure.
- Gate, actionlint, secret-scan pass. Untested live: the paths filter, pending-release replacement, and the licences job on a runner.
- For conductor: a docs-only push to dev now produces no run; the latest run is not a signal for it.

## Update: website deploy (GitHub Pages)
`.github/workflows/website.yml`: build (unit tests, build.py, validate.py) then deploy (upload-pages-artifact v4.0.0, deploy-pages v4.0.5, SHA-pinned, pages and id-token write only on deploy) then verify (live `build.json` carries the commit). Skips cleanly until `website/build.py` exists and until Pages source is GitHub Actions. What Dinis does is in `docs/ci.md` (Pages source, custom domain, DNS CNAME `android.app` -> `promptugal.github.io`, allow `dev` in the `github-pages` environment). Untested live; `website/` is not on any branch yet.
- For editor.android: `build.py` must write the site to `website/_site/` (index.html at root); the workflow adds CNAME and build.json; `GH_TOKEN` (read-only) and `GITHUB_REPOSITORY` are set for the build step; no pip packages.

## Update: screenshots into the in-app release articles
`scripts/release-screenshots.sh` + two workflow steps: the check job verifies every listed screenshot exists; the release job downloads the check job's `screenshots-<run>` artifact and copies them into `app/src/main/assets/releases/<dir>/screenshots/` before assembleRelease/bundleRelease. Not committed (stale, history bloat in a public repo). Contract I proposed because dev.releases' was not on origin/work/dev.releases yet: `assets/releases/<dir>/screenshots.txt` lines `<target>.png <- <source>.png`, source = a file in `app/build/screenshots/`. `website.yml` now also triggers on `app/src/main/assets/releases/**`. Details and caveats in `docs/ci.md`. Gate, actionlint, secret-scan pass; untested live.
- For dev.releases: either use that manifest (no CI change per release) or tell me your contract; the article screen must cope with missing screenshots (local builds).
- For conductor: please add `app/src/main/assets/releases/*/screenshots/` to `.gitignore` (not my file).
- For editor.android: the site build does not run the Robolectric tests, so it sees articles without their generated PNGs.

## Update: R-llm-5 (security's dev.llm review)
`docs/ci.md` "Toolchain pins and the Kotlin bump": the -Xskip-metadata-version-check state and risk, a plan to drop it (coordinated Kotlin + Compose BOM + AGP + Gradle bump, gate plus smoke job, check the model screen on a tablet), and the ABI-split notes (arm64 for testers, x86_64 for the smoke job; not done, needs app/build.gradle.kts and a size measurement; do not go arm64-only). `dependabot.yml` groups: `toolchain` (Kotlin, Compose, AGP together), `litertlm` (com.google.ai.edge.*), `testing`, `androidx`. No build files touched, so the Gradle gate was not re-run; litertlm is not on dev yet so the group is untested.
- For architect/app.android: the bump plan needs your `build.gradle.kts`; I did not pick target versions.

## Update: team rename (from conductor; checked against CLAUDE.md on dev at b3bea2b)
I am release.android@promptugal.ai.dinis from this commit. `scripts/release-notes.sh` accepts both `@promptugal.ai: ` and `@promptugal.ai.dinis: ` subjects (role shown without the domain); new `scripts/test-release-notes.sh` (both forms, merges dropped, range since the tag, tag being released excluded; confirmed to fail on the old script) runs in the `check` job; `docs/ci.md` updated. actionlint and secret-scan pass.
- Note for security.android: `scripts/secret-scan.sh` now takes about 3 minutes locally (full history, all refs); fine for a gate, slow to run often.

## Update: release flow, option B (from conductor; Dinis's rule at CLAUDE.md 9ab77ba)
Built, for security.android's review before it lands on dev (full text in `docs/ci.md` "Release flow"):
1. **Play removed from dev runs**; Firebase stays. The dev release now carries APK, AAB and `SHA256SUMS`.
2. **`security-gate`**: one check named `security-gate` = `scan` (secret scan) + `check` (tests, lint) + `licences` (licences + OSV), on every dev push and every PR. The earlier `paths` filter on the whole workflow is gone (a docs commit can leak a secret, and promotion needs a gate result on the approved SHA); a `changes` job now decides only whether `release` (Firebase, GitHub release, smoke) runs.
3. **`security-approval`** (PRs to main) and `scripts/verify-approval.mjs`: signed PASS check on Node's built-in Ed25519, no dependency.
4. **`promote.yml`**: runs from `workflow_run`/dispatch only (default-branch copy, so a dev push cannot edit it to reach the App secrets), Environment `promote`; verifier, selector and `board/keys.json` come from main, approvals from dev; fast-forwards main with a GitHub App token, or opens a PR pinned to `promote/<sha12>` and says why.
5. **`play.yml`** (push to main, Environment `play`): re-verifies the approval, finds the SHA's dev release, `sha256sum --check`, refuses an unsigned AAB, uploads the AAB as a draft on `internal`; never rebuilds.
6. **Tests**: `scripts/test/*.test.mjs` (18; keys generated at run time; mutation-checked), run in the `check` job.
Gate, secret scan (clean), node tests, actionlint pass. **Untested live:** every workflow above.
- **For security.android (please decide):** `board/keys.json` is empty and the repository has no PKI tool (`pki-agent.mjs` is not in it), so the approval record and key format in `docs/ci.md` are MY PROPOSAL; replace them if you design otherwise (then `verify-approval.mjs` and its tests change). Everything fails closed until a key is in `board/keys.json` on main. Please review the trust design: verifier/keys from main, approvals from dev; `promote.yml` not triggerable from a dev push; HOLD wins and newest `signed_at` wins.
- **For Dinis (asks, also in docs/ci.md):** the GitHub App (`PROMOTE_APP_ID`, `PROMOTE_APP_PRIVATE_KEY`) in a new Environment `promote`; the Environment `play` with `PLAY_SERVICE_ACCOUNT_JSON` (the org policy blocks creating that key: keyless Play route or an exception); allow Actions to create pull requests for the fallback; protect main later (require `security-gate` and `security-approval`, restrict pushers to the App and Dinis). Bootstrap: the flow reaches main first by a pull request Dinis merges.
