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

## Update: release flow rebuilt on security.android's design (docs/security/approvals.md)
- `scripts/verify-approval.mjs` + `verify-approval-lib.mjs` are security's verifier, copied (only the library's import line differs; a test fails on drift and runs their self-test against the copy); `verify-approval-human.mjs` = same check with `allowTrustPathChange`, only for a person-triggered upload. My own proposed format, verifier, selector and tests are removed.
- `promote.yml`: verifier runs from main's checkout; `scripts/promote-select.sh` takes the newest of the last 60 first-parent dev commits that the verifier accepts (fail closed on any non-zero exit or crash), needs a green `security-gate` on it. **Until main is bootstrapped (verifier + an active security key on main) it never fast-forwards: it opens a PR for the dev head and says nothing was verified.**
- `security-approval` (PRs to main) checks out the PR's base and runs the verifier from it. `play.yml` re-verifies with the main BEFORE the push as base (the bootstrap merge and trust-path commits upload nothing automatically; a person dispatches with sha + base for those).
- check job now also runs `node docs/security/approvals/selftest.mjs` and `node --test scripts/test/*.test.mjs`.
- **Secret scan is RED on history, a false positive:** `scripts/secret-scan.sh` (merged from dev) now flags class "password in a URL" on `${TOKEN}` in my earlier promote.yml (commits 888be5a on origin/work/release and the merge 970d8ea). I removed the URL (the token now goes in an Authorization header with `::add-mask::`), so the working tree is clean, but history cannot be rewritten and stays flagged on every ref that contains those commits, dev included once merged. For security.android: please exclude a password part that is a variable reference (`${...}` or `$NAME`) from that class, or give an allow-list entry for those commits; I did not touch your file. I pushed this fix on a scan whose only matches are those two historical commits, and say so here.

## Update: security's early notes on 888be5a, in the next head
(1) the trust-path refusal is security's verifier (done in 034b4ac); the gate lookup now takes only check runs from the github-actions app (`app.slug`). (2) Build provenance: `actions/attest-build-provenance` v4.1.1 (pinned) attests the signed APK and AAB in the release job; play.yml runs `gh attestation verify` on both (this repository, signer workflow android.yml, source digest = the release tag's commit) before the upload, fail closed; SHA256SUMS stays as a corruption check. (3) A PR opened with GITHUB_TOKEN starts no workflows: security-gate is already on the pinned commit from its dev run; for a VERIFIED promotion, promote.yml sets a `security-approval` commit status itself after running the verifier from main (not for the bootstrap PR). Untested live: the attestation and `gh attestation verify` flags, the status call. Noted: ask before pushing on red.

## Update: security's HOLD on 034b4ac (R-rel-1 to R-rel-5)
- **R-rel-1:** `security-approval` is now its own workflow `security-approval.yml` on `pull_request_target` (base's copy of the file), checks out `base.sha`, fetches `refs/pull/<n>/head` as data only, no secrets, never runs head code; removed from android.yml.
- **R-rel-2 / R-rel-3:** in eee62bf (provenance attest + `gh attestation verify`; gate lookup limited to `app.slug == github-actions`).
- **R-rel-4:** `scripts/verify-approval-lib.mjs` is the new `approval-lib.mjs` from origin/work/security, byte for byte (the drift test and security's 16-case selftest on the copy pass). dev did not have it yet, so I also took `docs/security/approvals/` and `scripts/secret-scan.sh` (the scanner fix 3a51b3e) from origin/work/security by path, identical content, so the tests and the scan are green on this branch; they are security's files and will merge cleanly with the conductor's merge.
- **R-rel-5:** `docs/ci.md` says a promotion is a fast-forward or a merge commit, never squash or rebase.
- Gate, actionlint, node tests, selftest pass; the secret scan is CLEAN (exit 0). Untested live: every workflow.

## Update: security's two hardenings on the provenance check
`play.yml`: `gh attestation verify` now also has `--source-ref refs/heads/dev` and `--deny-self-hosted-runners`. dev merged in (the approval lib is identical to the copy). `security-approval` stays on `pull_request_target` for Dinis's own PRs; promote.yml's status is only for its own pull requests. Flags untested live (gh version on the runner not checked).

## Update: first live run of the release flow (dev afbd963, run 105; run 104 at f406d61 first)
- **Run 104 (old shape, before the security-gate): check, licences (with OSV) and release all passed, Firebase distributed (WIF works).** The `smoke` job failed both attempts, two separate causes:
  1. **Mine, fixed here:** `scripts/smoke-test.sh` required the texts "RiskMandate" and "Open riskmandate.ai", but the home screen was redesigned (the button is gone). The launch check now asks for the app's own window (uiautomator node package), the app name in some text (any case) and at least 3 non-empty texts; tested on stub dumps (good, blank, another app's launcher).
  2. **Not mine, for qa/app:** the instrumented SmokeTest failed 4 of 9 on the emulator with "The component is not displayed!": `licencesScreenShowsFullApacheTextAndGoesBack`, `diagnosticsScreenShowsTextAndGoesBack`, `rotationKeepsEveryEnabledFeatureScreen`, `everyEnabledFeatureOpensAndBackReturns`. Probably the redesigned home needs scrolling to reach those entries on the emulator's screen; the reports are in artifact `smoke-104` (`app/build/reports/androidTests/`). The smoke job stays red until they pass: that is a real signal, not a flake.
- Side effect: a red smoke job makes the whole android run red, so `website.yml` (needs a successful android run) is skipped, and `promote.yml` still evaluates (it accepts any non-cancelled run).
- Run 105 (afbd963, the first with `changes`, `scan`, `security-gate`): changes, licences and OSV passed; scan and check were still running when I wrote this.

## Update: the three live findings after Dinis merged PR #1 (main 1f6adb3)
1. **security-approval.yml run 1 (id 42655772816): startup_failure with no jobs.** The file is valid (actionlint clean, same structure as the others). The PR's base was main at the initial commit, which had no `security-approval.yml`; a `pull_request_target` run reads the file from the base, so the likeliest cause is that the file was not on the base then. It cannot be re-tested until the next pull request to main (a closed, merged PR cannot be re-run); the promote fallback PR also cannot trigger it. Not fixed because not shown to be broken; the next PR to main is the test.
2. **promote run 3 (id 42653959133) failed: expected, now a warning.** It took the bootstrap path (main was not bootstrapped), the branch `promote/0b21eef10c73` already existed, and `gh pr create` failed with "GitHub Actions is not permitted to create or approve pull requests": a repository setting (Dinis: Settings, Actions, General). It would be a red run on every android run until he changes it, hiding real failures, so that one error is now a `::warning::` plus a summary line and exit 0; every other error still fails. main is bootstrapped now (verifier and the security key are on it), so the next run takes the verified path.
3. **play.yml run 1 on main 1f6adb3 (id 42655940735) failed at the approval step: correct behaviour, not a bug.** Verifier output: "tip changes trust paths (.github/dependabot.yml, .github/firebase/package-lock.json, .github/firebase/package.json): a person promotes this one". Dinis's bootstrap merge changes trust paths, and the main before it (0b299ae) has no board/keys.json, so nobody can verify it; nothing is uploaded automatically for it, as designed. The failure summary now says this and how a person can dispatch an upload (sha and base). **So the first green "approval check + attestation verify" can only come from the first normal promotion**: security signs a PASS against base main=1f6adb3, promote.yml (PR merged by Dinis, or the App) moves main, and play.yml on that push verifies with before=1f6adb3. The attestation verify has not run live yet.
- Gate, full secret scan (clean), actionlint, node tests, release-notes test pass.

## Update: first judged play.yml run (main 43709b9, run 42658929597, before=1f6adb3)
- **Approval step PASSED live:** `{"ok":true,"reason":"PASS by security.android for 91503dc, 3 commits reviewed, review docs/security/reviews/2026-11-09-dev-91503dc.md"}` (verifier from main, base = the main before the push). A harmless `fatal: path 'board/security/approvals.jsonl' exists on disk, but not in '1f6adb3...'` is the verifier's `git show` on a main that had no approvals file yet.
- **find-release-tag FAILED, my bug, fixed here:** "no release tag matches 43709b9". Reproduced locally: the newest release is v0.1.105 (afbd963); since then the only files in my "build path" list that changed are `app/src/androidTest/.../SmokeTest.kt` and `scripts/smoke-test.sh`, which are never packaged, so the AAB of v0.1.105 is the right one. My list was the "does CI build" list, too wide for "is this still the same shipped app". New `changed-build-paths.sh --shipped` (build paths minus androidTest, `.github/`, `scripts/`, plus `scripts/release-screenshots.sh`; `app/src/test/` stays because it writes the release-article screenshots) used by `find-release-tag.sh`; on the real repository `find-release-tag.sh origin/main` now prints v0.1.105. The CI `changes` job keeps the wide list. Tests added (androidTest, scripts, workflow changes keep the build; app main, app tests, release-screenshots.sh, gradle files do not); mutation-checked.
- **Not yet seen live:** the SHA-256 check, the attestation verify and the application ID read, which come after find-release-tag. Run 105's release job should have produced attestations for v0.1.105; the next play run will show whether `gh attestation verify` accepts them (flags `--source-ref refs/heads/dev --deny-self-hosted-runners` are untested).
- **Trust paths:** this change touches `scripts/changed-build-paths.sh` and `scripts/find-release-tag.sh`, so its promotion to main needs Dinis (with e3f1b66's `.github/` changes). I did not copy security's new TAIL_PATHS lib, as asked.

## Update: `changes` compares with the latest dev release (from the conductor, found on run 113/114)
Run 113 (dev.chat's code change d84f3e0) was cancelled by the next push; run 114 (debrief text) judged only its own diff, so release and smoke were skipped and a real app change was never released or smoke-tested. New `scripts/changed-since-release.sh <sha>`: compares with the commit of the newest `v<number>` tag that is an ancestor (the last build actually released), same wide file list as before; the workflow step treats only exit 1 as "nothing changed" (a crash builds). On the real repository it now reports changes since v0.1.105 (ChatFeature.kt, ChatScreenTest.kt, SmokeTest.kt, scripts/smoke-test.sh), i.e. the missed release. Tests added (cancelled-run scenario, docs on a release, androidTest counts, no tag, foreign tag), mutation-checked (comparing with sha~1 fails one). Gate, scan, actionlint pass. Not in security's TRUST_PATHS: `scripts/changed-since-release.sh` decides whether a release is built, so security may want it there.

## Update: promote.yml must not run from dev's copy (security.repos HIGH; default branch is dev)
PREPARED for security's ruling, pushed because a session stop hook required it (work/release only, nothing on dev yet; I will amend on security's ruling). `promote.yml`: no `workflow_run`, `workflow_dispatch` only, refuses any ref but `main`. `android.yml`: new `promote-dispatch` job (after `security-gate`, `actions: write`) runs `gh workflow run promote.yml --ref main`, so main's copy runs on ref main. The real protection is a setting for Dinis: the App key as an ENVIRONMENT secret of `promote` (no repository copy), deployment branches `main` only; same for `play`. A `workflow_run` could not work with a main-only environment anyway (its ref is dev). Docs corrected, including the website notes that assumed default=main. Gate, full scan (clean), actionlint, 8 node tests pass. Untested live.
