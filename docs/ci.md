# CI: `.github/workflows/android.yml`

Only Dinis adds secrets (repository settings, Secrets and variables, Actions). Agents never see or write them. The build stays green with zero secrets: every step that needs one is skipped, and the job summary says which and why.

## Jobs

| Job | Runs on | Does |
|---|---|---|
| `changes` | every push to `dev` | whether the push touched anything the app is built from (`scripts/changed-build-paths.sh`); decides whether `release` runs |
| `scan` | every push to `dev`, every pull request, manual | the secret scan (`scripts/secret-scan.sh`) over the full history |
| `check` | every push to `dev`, every pull request, manual | unit tests and lint; uploads the reports (`reports-<run>`) and Robolectric screenshots when `app/build/screenshots/` exists (`screenshots-<run>`), always, pass or fail |
| `licences` | same, in parallel with `check` | the dependency licence gate and the OSV check (below); uploads the licence and vulnerability lists (`licences-<run>`) |
| `security-gate` | after `scan`, `check` and `licences`, always | **one check named `security-gate`**: passes only if all three passed; the name `promote.yml` reads and main's protection should require |
| `security-approval` | pull requests to `main` only | is the PR head approved by security.android (signed PASS); rules and keys from main (below) |
| `release` | push to `dev` and manual runs on `dev`, after `security-gate` passes and only if `changes` says the build changed, in the GitHub Environment `release` | release notes, release APK and AAB, signing, artifact `riskmandate-<version>`, Firebase, GitHub release with APK, AAB and `SHA256SUMS`. **No Google Play from dev.** |
| `smoke` | after `release`, same triggers | emulator smoke test of the release APK (below); nothing depends on it |

Both jobs write a job summary: version, application ID (read from the built APK with `aapt2 dump badging`, never hardcoded, so a change of ID needs no workflow edit), signed or not, and each distribution step as ran, FAILED or skipped with the reason.

## Release flow: dev to main to Google Play (option B)

Dinis's rules (9 November 2026, `CLAUDE.md`, `docs/release-workflow-proposal.md` option B): everybody pushes to `dev`; Firebase testers get dev builds; **the store builds only from `main`**; the workflow promotes a dev commit to `main` after the gate and security's approval; the push to `main` uploads to Google Play as a draft.

```
dev push --> security-gate (scan, licences + OSV, lint, tests) --> release (if the build changed): Firebase, GitHub release v0.1.N (APK, AAB, SHA256SUMS)
security.android signs a PASS for that SHA in board/security/approvals.jsonl (a push to dev)
promote.yml: signed PASS + green security-gate on that SHA  --> fast-forward main to it (App token) or open a PR pinned to it
play.yml (push to main): same SHA's v0.1.N release AAB, SHA-256 checked, uploaded as a DRAFT on internal (no rebuild)
```

**The signed approval is security.android's design** (`docs/security/approvals.md`; reference code and self-test in `docs/security/approvals/`). A signed line in `board/security/approvals.jsonl` approves exactly the commits in `main..head` (a hash of the range, the base main, a hash of the review text, an expiry of at most seven days); a HOLD or a later REVOKE blocks; only an active key with role `security` in `board/keys.json` counts. The check, `verify-approval.mjs --repo . --main <main sha> --tip <dev sha>`, also refuses a tip that changes a trust path (workflows, `board/keys.json`, the verifier, `docs/security/approvals/`, `CLAUDE.md`): those are promoted by a person, not automatically. Its copy is in `scripts/` as security specified (`verify-approval.mjs` plus `verify-approval-lib.mjs`, both under the `scripts/verify-approval` trust-path prefix; the library's import line is the only change). A test fails if the copy drifts from the original, and security.android's self-test is run against the copy as well as in the `check` job. `scripts/verify-approval-human.mjs` is the same check with `allowTrustPathChange`, used only when a person triggers an upload.

**Where the trust comes from.** The verifier runs from a checkout of **main** and reads the keys and the approvals as they are on main; the candidate may only append. `promote.yml` runs only from `workflow_run`/manual dispatch (GitHub runs those from the default branch's copy of the file), never on a push to dev, and in the Environment `promote`, so someone who can push to dev cannot edit it to reach the App secrets. `security-approval` on a pull request to main checks out the PR's base and fetches the head as data, but a pull request still triggers a workflow file the PR may have edited: the authoritative checks are `promote.yml` and `play.yml` (both re-verify with main's code), so protect main with a ruleset that requires `security-gate` and `security-approval` **and** restricts who can push (the App and Dinis). Every non-zero exit of the verifier, a crash and a missing verifier all mean "not approved".

**What it promotes.** `scripts/promote-select.sh` tries the newest commits on dev's first-parent line (60 at most) and takes the newest one the verifier accepts: a PASS on that commit or on an ancestor with only the approval record and reviews added since. `promote.yml` then needs a green `security-gate` check run on that exact commit; if there is none yet it waits for a later run (it re-evaluates after every finished android run on dev, and the approval commit itself triggers one). A commit whose gate a newer push cancelled is not promotable: ask security to approve a newer head. git refuses anything that is not a fast-forward.

**Fast-forward or pull request.** Until main is *bootstrapped* (it carries the verifier and an active security key in `board/keys.json`: today main is still the initial commit) `promote.yml` **never fast-forwards**: it opens a pull request for the dev head, says in its description that nothing was verified by automation, and Dinis merges that first one. After that, with the repository secrets `PROMOTE_APP_ID` and `PROMOTE_APP_PRIVATE_KEY` in the Environment `promote` (a GitHub App with contents write on this repository, made by Dinis; agents never see them) it fast-forwards main with a short-lived token from `actions/create-github-app-token` (pinned). Without them it opens a pull request from `promote/<sha12>` (a branch at the approved commit) with the build, the security basis and the changes since main in its description, and the run summary says the secrets are missing; this needs Settings, Actions, General, "Allow GitHub Actions to create and approve pull requests", else the run fails with that reason. A later approval makes a new branch and PR; close the old one.

**The Play job** (`play.yml`, push to main, Environment `play` holding `PLAY_SERVICE_ACCOUNT_JSON`): re-verifies the approval (keys and approvals as on the main *before* the push, the new main as tip; so the bootstrap merge and any commit that changes a trust path upload nothing automatically, and a person uploads one by dispatching the workflow with the commit and its base, which uses the human variant), finds the dev build for that commit with `scripts/find-release-tag.sh` (the newest `v<number>` tag that is an ancestor with no build-relevant change since, so a docs-only commit on top of a built one uses that build, and built code without a release is refused), downloads the release's APK, AAB and `SHA256SUMS`, runs `sha256sum --check --strict`, refuses an `-unsigned` AAB, reads the application ID from the APK, and uploads the AAB as a **draft on `internal`** (`status: draft`: a person completes it in the Play Console). It never rebuilds; versionCode is the dev run's number inside the AAB, which only goes up. Without the secret everything up to the upload still runs and the summary says the upload was skipped. `SHA256SUMS` guards against a corrupted or swapped download, not against someone who can rewrite the whole release; the upload key itself is the stronger check (Play rejects an AAB signed by another key). The signed AAB is now also a public release asset; it is signed by the upload key only and open source.

**Dinis does, once:** put the security public key into `board/keys.json` on main (the first pull request); make the GitHub App and add `PROMOTE_APP_ID` and `PROMOTE_APP_PRIVATE_KEY` to a new Environment `promote` (deployment branches: main only; optional required reviewer turns this into option C); create the Environment `play` with `PLAY_SERVICE_ACCOUNT_JSON` (deployment branches: main only; the key is blocked by the organisation policy, see the Play follow-up below); allow Actions to create pull requests if the PR route is used; and later protect main as above. **Bootstrap:** none of this runs until `promote.yml`, `play.yml`, the verifier, the selector and a `board/keys.json` with the security key exist on `main`, so the first release flow reaches main by a pull request Dinis merges (a `workflow_run` trigger also needs the file on main to fire).

**Tests (in the `check` job):** `node --test scripts/test/*.test.mjs` (the copy of the verifier is identical to security's original; security's self-test passes against the copy; `promote-select.sh` picks the newest approved commit and treats none, a crash and a missing verifier as "not approved"; `changed-build-paths.sh`; `find-release-tag.sh`; in throwaway git repositories) and `node docs/security/approvals/selftest.mjs`. Mutation-checked: making `promote-select.sh` accept any output, or changing the seven-day limit in the copy, makes tests fail. **Untested live:** every workflow in this section (`promote.yml`, `play.yml`, `security-approval`, the App token, the pull-request path, the Play upload, the `security-gate` check-run name read through the API). **For security.android:** review of the workflows, `promote.yml` and the copy in `scripts/` before they land. **For Dinis / the conductor:** the public key in `docs/security/approvals.md` section 7 must reach `board/keys.json` on main, the fingerprint compared on Dinis's own channel.

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

## Screenshots in the in-app release articles (feature F)

CI puts Robolectric screenshots into the release articles at build time; **generated PNGs are not committed.** Why: they would go stale against the code that renders them, each regeneration adds about a hundred KB per image to the history of a public repository for ever, and a PNG nobody can tell is current is worse than none. CI renders them from the same commit it builds.

Contract (`scripts/release-screenshots.sh`, proposed by release.android; dev.releases.android's own contract was not yet published on origin/work/dev.releases when this was written, so adapt it if it differs): an article is a folder `app/src/main/assets/releases/<dir>/`. If it has a `screenshots.txt`, each line is `<target>.png <- <source>.png`, where `<source>` is a file the screenshot tests write to `app/build/screenshots/` (today `ScreenshotTest` writes `<screen>-<device>-<orientation>-<theme>.png`) and `<target>` is the name the article uses. CI copies it to `app/src/main/assets/releases/<dir>/screenshots/<target>.png`. Blank lines and `#` comments are allowed; names are `[A-Za-z0-9._-]` and end `.png`; a listed source that does not exist is an error.

- **check job**: after the tests, `release-screenshots.sh --verify` fails if any listed source was not written, so a renamed test output is caught early.
- **release job**: downloads the check job's `screenshots-<run>` artifact (same commit, so no second Robolectric run), runs `release-screenshots.sh` before `assembleRelease` and `bundleRelease`, so the APK and AAB carry the PNGs.
- A local build has no `screenshots/` folders unless `./gradlew testDebugUnitTest` and the script were run by hand: the article screen must cope with a missing screenshot. The generated folders belong in `.gitignore` (asked of the conductor, the file is not release's).
- `website.yml` also runs when `app/src/main/assets/releases/**` changes. It does not render screenshots (that needs the Robolectric run), so a site build sees the articles without their generated PNGs unless `website/build.py` handles that: open for editor.android and dev.releases.
- Untested live; the script was tested locally with a stub tree (copy, verify, bad names, missing source).

## The website: `.github/workflows/website.yml` (GitHub Pages)

The Android site (`website/`, by editor.android) is built and deployed to **https://android.app.promptugal.ai** by its own workflow. Jobs: `build` (python3 unit tests `website/test_site.py`, `website/build.py`, `website/validate.py`), `deploy` (`actions/upload-pages-artifact` v4.0.0 and `actions/deploy-pages` v4.0.5, both pinned by SHA; `pages: write` and `id-token: write` only on this job) and `verify` (polls the live `build.json` until it carries the built commit, 4 minutes, fails if not).

Triggers: push to `dev` touching `website/**` or the workflow file; pull requests touching them (build and validate only, never deploy); a finished `android` run on `dev` (so the releases page refreshes; a release made with the built-in `GITHUB_TOKEN` fires no `release` event, a `workflow_run` does); a published release; manual. Runs caused by an android run or a release build the latest `dev`.

Contract with `website/build.py` (editor.android, tell release.android if it differs): it writes the site to **`website/_site/`** with `index.html` at the root; the workflow then adds `CNAME` (`android.app.promptugal.ai`) and `build.json` (`{"sha", "run"}`) to the artifact itself, so the repository needs neither. The build step has `GH_TOKEN` (read-only) and `GITHUB_REPOSITORY` in its environment if the script lists releases through the API. Python is the runner's `python3`, no third-party packages.

Skipped cleanly: until `website/build.py` exists the `build` job only writes a summary line and everything after is skipped. Until Pages is set to deploy from GitHub Actions (API `build_type` is `workflow`, or the repository variable `PAGES_ENABLED` is `true` as an override) the site is built and validated but not deployed; the summary says which.

**What Dinis does (once):**
1. Settings, Pages, Source: **GitHub Actions**.
2. Settings, Pages, Custom domain: `android.app.promptugal.ai`; tick "Enforce HTTPS" once the certificate is ready.
3. DNS at the promptugal.ai DNS host: `CNAME  android.app  ->  promptugal.github.io` (the `app` part of the name is a label under promptugal.ai: the record is `android.app.promptugal.ai`). Certificate issue takes minutes to an hour after DNS resolves.
4. Settings, Environments, `github-pages`: its deployment-branch rule must allow `dev` (a new Pages environment allows only the default branch, `main`); add `dev`, otherwise the deploy job is refused on pushes to `dev`. (Runs started by a `workflow_run` use `main`'s version of the workflow file and the default branch as their ref, so the refresh route needs `website.yml` on `main`; push, release and manual runs work from `dev`.)
5. Optional: set the repository variable `PAGES_ENABLED` to `true` if the Pages API cannot be read by the workflow.
The `verify` job stays red until DNS, the certificate and the settings above are in place if the Pages URL redirects to the custom domain; that is the signal, not a bug.

**Untested** (no way to run workflows or open Pages from here): all three jobs; the Pages API check; `deploy-pages` with the environment rule; the live check. The shell steps were run locally with a stub site (unit test file path form, build, marker JSON). `website/` does not exist on any branch yet, so the first real run is the first test. `actionlint` passes.

## Toolchain pins and the Kotlin bump (R-llm-5, from security's dev.llm review)

State: the compiler is Kotlin 2.0.21 (with the Compose compiler plugin of the same version), Compose BOM 2024.12.01, AGP 8.7.3, Gradle 8.11.1. The LiteRT-LM runtime (`com.google.ai.edge.litertlm:litertlm-android`, on `work/dev.llm`, not yet on `dev`) is built with Kotlin 2.4 metadata and pulls `kotlin-stdlib` 2.4.0 and `kotlinx-coroutines` 1.11.0 into the **whole app**, while the compiler stays at 2.0.21. It builds only because `app/build.gradle.kts` passes `-Xskip-metadata-version-check`, which turns off the compiler's guard against reading metadata from a newer Kotlin: a real incompatibility would show up as a runtime crash or silent miscompile, not a build error. Accepted for the summit build (Google's own sample app does the same); not a state to keep.

Plan to drop the flag (one coordinated change, owners: architect and app.android for `build.gradle.kts`, release for the wrapper and CI):
1. Pick the target by compatibility, not by newest: a Kotlin compiler that reads Kotlin 2.4 metadata (the compiler reads metadata up to one minor version newer than itself, so Kotlin 2.3 or later; confirm in the Kotlin docs when choosing), the Compose BOM and AGP that support it, and the Gradle version AGP needs (then update `distributionSha256Sum` in `gradle-wrapper.properties` with it).
2. One PR changing Kotlin and its Compose plugin, the BOM and AGP together. The earlier attempt to bump the androidx libraries alone failed the gate for exactly this reason (they pulled Compose 1.9 and Kotlin 2.1.20 against BOM 2024.12.01, per app.android's status page). Dependabot now groups them as `toolchain` so it proposes them together.
3. Remove `-Xskip-metadata-version-check`. The gate for the PR is the usual one plus the emulator smoke job (launch and instrumented SmokeTest) on the PR's merge to `dev`, since the flag's failure mode is at run time. Check the model screen on a real tablet before releasing.
4. Until then, any further dependency built with newer Kotlin metadata has the same risk: do not add one without a line here.

Dependabot (`.github/dependabot.yml`): groups are `toolchain` (Kotlin, Compose, AGP), `litertlm` (`com.google.ai.edge.*`), `testing`, `androidx` (the rest); a dependency joins the first group that matches. Not tested (Dependabot only reads the file from `main`).

ABI split, later: LiteRT-LM ships native libraries per ABI; only `arm64-v8a` matters on the tablets and phones, `x86_64` is needed only by the smoke emulator. The AAB already splits per ABI on Google Play. For the Firebase APK, a `splits { abi { ... } }` block in `app/build.gradle.kts` (not release's file) would produce an arm64 APK for testers and an x86_64 APK for the smoke job; the release job and smoke job would then pick the APK by ABI (a few lines in `android.yml`, `scripts/emulator-checks.sh` takes the APK path already). Not done: measure the APK size with litertlm on `dev` first, and decide with app.android whether one universal APK is acceptable. Do not restrict the release build to arm64 only: the x86_64 emulator image would then not install it.

## Cost under load: what runs, what is cancelled, how long it takes

**Docs-only pushes run the gate but do not build.** Every push to `dev` runs `scan`, `check`, `licences` and `security-gate` (a docs commit can leak a secret, and `promote.yml` needs a gate result on the exact commit that is approved). The `changes` job (`scripts/changed-build-paths.sh <before> <sha>`) decides whether `release`, and with it the Firebase notification, the GitHub release and the smoke test, runs: only if the push touched `app/`, `gradle/`, `.github/`, `scripts/`, any `*.gradle.kts`, `gradle.properties`, `gradlew` or `gradlew.bat`. A push that changes only `docs/`, `board/`, `briefs/`, `devices/`, `website/`, `README.md` and the like builds nothing and notifies no tester. This replaces the earlier `paths` filter on the whole workflow (which skipped the gate too). Cost: the secret scan reads the full history and takes about 3 minutes, now on every push. An unknown or non-ancestor `before` (first push, force-push) counts as changed.

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

`scripts/release-notes.sh <version>` writes Markdown from the commit subjects since the last `v<number>` tag (the tag being released is excluded, so a re-run on a tagged commit still works). Subject `<role>@promptugal.ai: <what>` or, after the 9 November 2026 rename, `<role>@promptugal.ai.dinis: <what>` (both are accepted, older commits keep the old form) becomes `- <what> (<role>, <sha>)` with the role without the domain; `scripts/test-release-notes.sh` tests both forms in a throwaway repository and runs in the `check` job; git merges and the conductor's `merge ...` commits are dropped, since the commits they bring are listed. Capped at 30 commits and 3500 characters. With no earlier tag (none exists yet: tags appear only when a signed GitHub release is made) it lists the last 30. The release job (checkout with full history) writes the file once and uses it for Firebase (`--release-notes-file`) and the GitHub release (`--notes-file`). Note that while builds are unsigned there are no tags, so the first signed release will list 30 commits.

## Dependabot

`.github/dependabot.yml`: Gradle, GitHub Actions (SHA pins and their version comments) and the firebase-tools npm lock, weekly on Monday 06:00 UTC, grouped (Gradle: androidx, kotlin, AGP, testing; Actions in one group; firebase-tools in one), commit prefix `deps`. Pull requests target **`dev`**: that is the branch CI builds and releases from and where the conductor merges, so a bump is tested by the same `check` job as any change; `work/release` is a short-lived role branch and `main` is only for Dinis's release PRs. Dependabot reads its config from the default branch, so this file does nothing until a release PR puts it on `main`; it is not tested. Open question for Dinis: Dependabot PRs are not "agent pushes to dev", and the conductor merges them like other changes. Gradle wrapper bumps also need the new `distributionSha256Sum`: check the PR does it, otherwise add it by hand.

## How CI owns the version

`VERSION_CODE` is the workflow's `github.run_number` and `VERSION_NAME` is `0.1.<run_number>`, set as workflow environment variables and read by `app/build.gradle.kts`. Nobody sets them by hand; a local build gets `1` and `0.0.0-local`.

## Secrets

| Secret | What it unlocks | Step | If missing |
|---|---|---|---|
| `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` (all four) | signing with the upload key (made with `scripts/make-upload-key.sh`), and everything below | "Decode the upload key", then "Release APK and AAB" signs | builds are unsigned and named `-unsigned`; Firebase, Play and the GitHub release are all skipped. Summary: `UNSIGNED: UPLOAD_KEYSTORE_* and UPLOAD_KEY_* secrets missing` |
| none: variables `GCP_WIF_PROVIDER`, `GCP_SERVICE_ACCOUNT` and `FIREBASE_APP_ID` (a variable or a secret) | M1: APK to testers group `team` on Firebase App Distribution (push only), authenticated keyless (below) | "Google auth (Workload Identity Federation)", then "Firebase App Distribution" | Summary: `skipped: variables GCP_WIF_PROVIDER, GCP_SERVICE_ACCOUNT or FIREBASE_APP_ID (variable or secret) missing`, or `FAILED at Google auth ...` |
| `PLAY_SERVICE_ACCOUNT_JSON` (Environment `play`, not the `release` Environment) | the signed AAB to Google Play, a draft on `internal`, **from main only** (`play.yml`, Release flow above): serves M3 and the managed Play private-app route of M2 | `play.yml` "Google Play (draft on internal)" | Summary: `not uploaded: PLAY_SERVICE_ACCOUNT_JSON is not set in the environment play` |
| `PROMOTE_APP_ID`, `PROMOTE_APP_PRIVATE_KEY` (Environment `promote`) | `promote.yml` fast-forwards main with a GitHub App token | `promote.yml` | opens a pull request instead; summary says why |
| none (uses the built-in `GITHUB_TOKEN`) | prerelease `v<version>` with the signed APK (push only, needs the upload key) | "GitHub release with the APK" | skipped with the key reason |

Play upload details: track `internal` only, from a push to `main` (no longer from dev, and no `alpha` choice). The release is created with `status: draft`, so nothing rolls out until a person completes it in the Play Console. The Play app must already exist with the first upload done by hand if the Console requires it, and the service account needs release permission on the app. The package name is read from the release's APK.

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

**Follow-up, Play:** the Play upload still uses `PLAY_SERVICE_ACCOUNT_JSON`, a service-account key. The same policy blocks creating that key, so Play needs the same keyless route (the `auth` step can serve both: the Play step would take the credentials file instead of `serviceAccountJson`, if `r0adkll/upload-google-play` accepts it, or the upload moves to a small script against the Play API) or a policy exception for that project. Until then the Play step in `play.yml` stays skipped (its checks still run).

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
