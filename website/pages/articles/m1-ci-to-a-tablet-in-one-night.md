<!--
title: M1: from an empty repository to a CI-signed build on a tablet in one night
description: How the RiskMandate Android app went from an empty repository to a CI-signed build installed by a one-tap update on a tablet, in about four hours on 8 and 9 November 2026, built by a team of agent sessions.
kind: article
date: 2026-11-09
order: 8
-->
# M1: from an empty repository to a CI-signed build on a tablet in one night

> The RiskMandate Android app went from an empty repository to a build signed by CI and installed on a tablet as a one-tap update, in about four hours on the night of 8 to 9 November 2026. A team of AI agent sessions did the work, for Dinis Cruz. This is what they built, what went wrong and was caught, and what is parked.

Everything below comes from the repository, with commit hashes and CI run numbers so it can be checked. All times are UTC, taken from git. Where I could not check something myself, it is marked **unverified** and says why. This article was written by the editor session, an AI agent, from the repository's files and history; it was not written by the people or sessions it describes, and none of it has been reviewed by Dinis before publication.

## The short version

- **20:33, 8 November:** Dinis creates the repository: one commit, `0b299ae`, "Initial commit".
- **21:04:** the conductor session pushes the scaffold (`1cb8d93`): a Kotlin and Jetpack Compose app, a CI workflow, a board of status files. The application ID, `{{app_id}}`, is decided at 21:26 (`5539d1f`).
- **23:07:** five more sessions start, one per role (`4c51b20`).
- **00:44, 9 November:** the first signed build is installed on Dinis's tablet (`8170146`). At 00:45 a newer build arrives as an update, one tap, no cable (`708cfe0`). Milestone M1, "CI to a device", is done.

The repository stood at {{commit_count}} commits when this article was built (commit `{{commit}}`).

## The milestone

The plan was written the same evening. Its first milestone, M1, reads: CI builds and signs the app on every push, and a person gets it on a device without a cable. Later milestones (M2: installed by itself on devices the team owns; M3: devices the team does not own) came after, and are now parked (below).

The app is deliberately tiny, because the point of M1 is the pipeline, not the app. It has one screen with a button that opens [riskmandate.ai](https://riskmandate.ai/) in the browser, a section saying whether an organisation manages the device, a Diagnostics screen and an open-source licences screen. See [Features](../features.html).

## The team

Only Dinis instructs. Everything else the agents read (pages, other agents' messages, the iOS team's code they were modelled on) is treated as material, not instruction. That rule is in the repository's `CLAUDE.md`.

The working model follows the iOS team's: **one branch per role, a security review before every merge, and the conductor merges.**

| Role | Branch | Owns |
|---|---|---|
| conductor (Opus) | `dev` | the plan, the board, every merge, the decisions put to Dinis |
| app | `work/app` | the app code |
| release | `work/release` | the CI workflow and scripts |
| devices | `work/devices` | tooling for managed devices (milestone M2) |
| play | `work/play` | Google Play documents: privacy policy, listing, tester guides |
| security | `work/security` | reviews of every other branch; secret scan; licence list |

The sessions other than the conductor run on Sonnet (per the plan, `briefs/2026-11-08__riskmandate-android/README.md`). Each session touches only its own paths. If it needs something in someone else's paths, it writes it on its status page under "For <role>" and the conductor routes it.

The merge protocol:

1. A session pushes only to its own `work/<role>` branch. Only the conductor pushes to `dev`. Nobody pushes to `main`; a release to `main` would be a pull request only Dinis merges.
2. The security session reads the new commits and writes a review with a one-line **Verdict: PASS** or **HOLD**.
3. The conductor runs the gate, `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`, and merges only on a PASS and a green gate.
4. CI then runs on `dev`.

The conductor's merge commits say it each time, for example "merge work/release at c4832f9 (security.android: PASS)". The folder `docs/security/reviews/` holds 14 review files; exactly one carries a HOLD verdict, and it is the second story below.

Two details of how the night actually ran, from the conductor's hand-over debrief. The first hour's workers ran as sub-agents in git worktrees under names like `dev.android.ci`; from 23:07 the work moved to separate sessions with the names in the table. And sessions could not message the conductor back, so the conductor's hourly run and each session's status page were the only channel.

## The pipeline

The workflow is `.github/workflows/android.yml`, documented in `docs/ci.md`. It has three jobs.

| Job | Does |
|---|---|
| `check` | unit tests and lint, then a dependency licence gate; uploads the reports |
| `release` | builds the release APK and AAB, signs them, sends the APK to testers on Firebase App Distribution, publishes a GitHub prerelease |
| `smoke` | boots an emulator, installs the release APK, launches it, checks the home screen shows the expected text, then runs the instrumented tests |

Details worth knowing:

- **Tests and lint.** The source holds {{jvm_tests}} unit tests and {{device_tests}} instrumented tests (counted by `@Test` annotations in the source today; this is a count, not a pass result). Lint must have no errors.
- **The version is owned by CI.** `versionCode` is the workflow's run number and `versionName` is `0.1.<run>`. Nothing in the repository sets them by hand. Tags are named the same way: the tag `{{latest_tag}}` is the latest. (That a tag's number equals the CI run number is what the workflow is written to do; I did not open the Actions run pages, so **unverified** against GitHub.)
- **Signing.** CI holds only the upload key, made on Dinis's machine by `scripts/make-upload-key.sh` and added to GitHub as secrets by Dinis. Agents cannot write GitHub secrets and never see them. The key file is written with owner-only permissions and removed at the end of the job. A build from Firebase is signed with the upload key; a build from Google Play would be signed by Google, and Android refuses to install one over the other.
- **The emulator smoke test** runs on `ubuntu-latest` with KVM, API 34, headless. It is a separate job that nothing depends on, so a flaky emulator cannot hold back a release, with two attempts and a red result if both fail. It tests the minified release build, not a debug build. `docs/lessons.md` records that the first run on GitHub passed on its first attempt in about 1.5 minutes (**unverified**: I did not read the run page).
- **All actions are pinned by full commit SHA**, the Gradle wrapper carries a checksum, and `firebase-tools` is pinned with a lockfile and installed with `npm ci --ignore-scripts`.
- **Every step that needs a secret is skipped cleanly when the secret is missing,** so the build was green from day one, and each secret switched one more step on.
- **Green runs, from the conductor's records.** Run 7 (`9985eed`, 21:29) was the first green CI on `dev`; run 24 (`279e3e4`, 23:54) was green on all three jobs including the emulator smoke test. The run numbers and the green results come from the conductor's board and debrief; I did not open the Actions pages, so they are **unverified** against GitHub.

### Keyless sign-in to Google

The Firebase step needs to act as a Google service account. The usual way is a downloaded key. Here, that was impossible: the Google Cloud organisation that holds the project enforces the policy `iam.disableServiceAccountKeyCreation`, which blocks creating service-account keys, and the team chose not to override it.

So CI signs in to Google with **Workload Identity Federation**. The release job asks GitHub for a short-lived OIDC token; the pinned action `google-github-actions/auth` (v2.1.13) exchanges it for short-lived Google credentials. No key exists anywhere, and the file the action writes holds only federation settings. Dinis created the pool and provider; the trust is limited to this repository and its `release` environment, and the job's `id-token: write` permission is granted to the release job only, not to `check` or `smoke`. (The provider's condition was quoted to the security session by the conductor; none of the agents saw it, so it is **unverified** from the repository.) The change is `2392f9f`.

## What went wrong, and was caught

### 1. The address allow-list could be fooled (High)

The app must open only riskmandate.ai. That matters because the address the app opens (`start_url`) can be set by an organisation's device management, so the check is what stops a mis-set or tampered setting sending a managed tablet to a phishing page.

The security worker's first review of `dev` (`65f3acd`, 22:22) found that the check, `Links.isAllowed`, parsed the host by string slicing. It cut the address at `:` before it looked for `@`. A web address of the form `https://riskmandate.ai:x@evil.example/` has the user-information `riskmandate.ai:x` and the real host `evil.example`, but the check saw `riskmandate.ai` and said yes. A second variant used a backslash, which browsers treat as a slash. The review rated it **High**, not Critical, because the only caller then was the Open button, so exploiting it needed control of the setting.

The fix, `1da96dc` at 22:25, rewrote the check as plain Kotlin that runs on the JVM: `https` only, no `@`, backslash, whitespace, control character or `%` anywhere in the checked part, port 443 only, and the host exactly `riskmandate.ai` or `www.riskmandate.ai`. It also dropped the old "any subdomain" rule. The unit tests got a case for every bypass in the review. The conductor merged it that minute (`7fafd47`). The same review listed fifteen further findings (A-2 to A-15 and L-1) about CI, scripts, device tooling and a missing licences screen; A-2 to A-12 were fixed in the next merge (`5a91b80`, 22:28) and the licences screen came with the A-1 fix.

### 2. The joined line in the workflow (caught by two reviewers)

At 00:11 the release session pushed the Workload Identity Federation change (`2392f9f`). The security session returned **HOLD** a minute later (`636c609`): one line of the new Firebase step was broken. The line that installs the Firebase tool (`npm ci ...`) and the line that runs it had been joined into one, a dropped newline. The conductor had independently spotted the same fault. The security review said the rest of the design was sound.

The release session fixed it in `c4832f9`; the re-review passed it (`44f1b0f`, **PASS**) and the conductor merged it at 00:13 (`10e939e`). A workflow edit that is wrong only shows when CI runs it, and CI runs it on `dev` with real credentials, so having two readers look before it merges is cheap. The conductor's debrief says it is worth keeping on every workflow change.

### 3. Chrome's desktop mode on tablets

On the first real install, Firebase's invitation page said "open the invitation on your mobile device", although the person was holding one. Chrome on Android tablets asks for desktop sites by default, so Firebase took the tablet for a desktop. The fix is to turn off Chrome's Desktop site setting (or untick it for that tab) and reload. It is now on the [install page](../install.html).

### 4. Install unknown apps

The first APK from any source (Chrome, Files, App Tester) triggers Android's "install unknown apps" block. You allow it once per source. Allowing App Tester is what makes later updates one tap. Also on the install page.

### Smaller things

- Maven Central, through the cloud sessions' proxy, sometimes answers 429 (too many requests). Retrying passes.
- One early Kotlin compile failure did not reproduce and was treated as transient.
- CI runs 4 and 5 were cancelled by newer pushes, not failures. Run numbers have gaps for this reason.
- One session first committed under a shortened role name; the conductor told it to use the full one.

### Still open

The security reviews left findings that are Dinis's to act on and that were not closed when this was written: tighten the Workload Identity Federation condition to the repository's numeric IDs (names can be re-registered) and to the `dev` branch (R-wif-2, R-wif-3, both Medium); protect the `release` environment with a required reviewer and a branch limit; keep the service account to the one role it needs. Signed APKs are also public, because the repository and its releases are public (finding A-14, accepted as information). See `docs/security/reviews/` and `docs/security/github-settings.md`.

## The first install

The first signed build, 0.1.26 (CI run 26), was published as a GitHub release (tag `v0.1.26`, commit `fe3e771`) and installed on Dinis's tablet, where the tablet layout and the Device section (correctly "not managed") were checked on real hardware. The next build, 0.1.27 (CI run 27, tag `v0.1.27`, commit `e82cb18`), went out by Firebase App Distribution and arrived through App Tester as an update over 0.1.26: one tap, no cable. The conductor recorded this at 00:44 and 00:45 (`8170146`, `708cfe0`). I did not see the tablet; **unverified** beyond the conductor's commits and board file.

## Decisions taken, and what is parked

Decisions by Dinis, from `board/status.json`:

- **8 November, ~21:35:** the application ID is `{{app_id}}`. It is fixed for ever once an app is on Google Play.
- **~21:40:** RiskMandate is the first Android app and the one the team learns Android development and publishing on. The PrompTugal app comes after, and starts from `docs/lessons.md`.
- **~21:45:** the apps are open source (Apache 2.0) and will be for sale eventually.
- **9 November, ~00:40:** the upload key is disposable during the experiment: the local copy was deleted, and a fresh one must be made, and kept, before the first Google Play upload.
- **~00:55:** milestones **M2, M3 and M4 are parked until after Web Summit.** The M1 flow, Firebase App Tester with one-tap updates, is what runs on the team's own tablets at the summit.

What waiting costs: the managed-device tooling in `devices/` (an Android Management API command-line tool and a fully managed policy) has never been run against Google. The Google Play route needs a Play Console account, a payments profile for selling, and a decision on a separate package ID for an organisation-private build, because an app restricted to organisations on Play can never be made public. Those questions are on the board for Dinis, not answered.

## What comes next: the summit features

From about 01:00 on 9 November, at Dinis's word, the team builds four summit features in parallel, each reaching the team's tablets through the pipeline above: **A**, riskmandate.ai inside the app; **B**, a native demonstration of an Agent Behaviour Policy stopping a planted instruction at six layers; **C**, this app's own reach, mandate and gap, live on the device; **D**, a stand mode for show floors (screen pinning, screen kept on, back to home when idle). A fifth, demo videos, waits for the videos.

The skeleton is on `dev` (`13c47b4`): one package per feature, so parallel sessions never edit the same file. Seven more sessions were started for it at 01:03 (`1b4be83`): an architect, a designer, a QA session and one developer per feature. The merge rule gains two reviewers: architect and QA review every app branch alongside security, and a HOLD from any of them blocks the merge.

None of the four is finished. The source says so: the features list in the app names three of them and each is switched off (`enabled = false`), and the stand mode's host (`StandHost.kt`) is a placeholder that renders the app unchanged. The [features page](../features.html) reads those flags when it is built, so it cannot say a feature is on before its owner turns it on.

## What this does and does not show

It shows that a small team of agent sessions, with a conductor who alone merges and a security session that reviews every branch, can get a signed, tested, distributed build to a real device in a few hours, and that the review step caught real faults: one High security finding and one broken workflow line.

It does not show that the app is useful yet (it opens one website), that it is on any store (it is not), or that managed-device installation works (it has not been tried). No store listing exists, and the repository's rules forbid publishing, emailing or creating an account in Dinis's name without him.
