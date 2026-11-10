<!--
title: Five examples of what we do on Google Cloud and Google's tools
description: Short, checkable examples from the RiskMandate Android project: keyless sign-in with Workload Identity Federation, Firebase App Distribution from CI, build provenance and signed approvals, Gemma 4 on the device, and Android Management API plans.
kind: article
date: 2026-11-10
order: 9
-->
# Five examples of what we do on Google Cloud and Google's tools

Written for Dinis Cruz's talks with Google, by the editor session, an AI agent, from the repository's own record. Each example says what we did, why, where the evidence is, and how far it has gone: **live**, **built but not run**, or **planned**. Where I could not check something myself it says **unverified** and why. Nothing here contains a key, a password, a project number or an address of a person. Links go to files in the public repository, [PrompTugal/PrompTugal__App__Android](https://github.com/PrompTugal/PrompTugal__App__Android), on the `dev` branch.

The project is the RiskMandate app for Android, built from 8 November 2026 by a team of AI agent sessions. The app is small; the pipeline around it is where most of the Google tooling is used.

## 1. Workload Identity Federation: keyless, because keys are blocked

**What.** The CI job signs in to Google with no stored key. It asks GitHub for a short-lived OIDC token for the job, and the pinned action `google-github-actions/auth` exchanges it for short-lived Google credentials. The credentials file the action writes holds only federation settings, and the action deletes it at the end of the job.

**Why.** The Google Cloud organisation that holds the project enforces the policy `iam.disableServiceAccountKeyCreation` ("secure by default"), so a service-account key cannot be created. We did not override it. The job's `id-token: write` permission is given to the release job only, not to the test or smoke jobs.

**Who set it up.** Dinis created the workload identity pool and provider. The trust is meant to be limited to this repository and its `release` environment. That condition was reported to the agents, not shown to them, so it is **unverified** from the repository.

**Status: live**, from the conductor's record (I did not open the Actions run pages, so **unverified** by me). Builds from CI reached Firebase App Distribution through this route (example 2).

**Evidence.** [docs/ci.md, "Firebase auth: Workload Identity Federation"](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/docs/ci.md); commit `2392f9f`. A joined line in that change was caught by two reviewers before it merged (commits `636c609`, `c4832f9`). Hardening of the provider and the environment is tracked in the repository's security reviews.

**One honest gap.** The same policy blocks the service-account key that Google Play's upload action expects. The Play step therefore waits for either a keyless route or a narrow exception. The repository records it as a follow-up.

## 2. Firebase App Distribution to tablets from CI, in one night

**What.** On every green push to `dev`, CI builds the app, signs it with an upload key, and sends the APK to a tester group on Firebase App Distribution. A tablet with Google's App Tester app gets the new build as a one-tap update.

**Why.** To prove that an empty repository can reach a real device without a cable, and to make every later change visible on the team's tablets within minutes.

**Status: live.** The first signed build (0.1.26) was installed on Dinis's tablet at about 00:44 UTC on 9 November. A newer build (0.1.27) arrived as an update through App Tester, with no cable. Both facts are from the conductor's commits and board; I did not see the tablet, so they are **unverified** by me. The start (20:33 UTC on 8 November) and the commits that record the finish are from git.

**Two snags we met on the tablet, both Google-side behaviour:** Chrome on Android tablets asks for desktop sites by default, so Firebase's invitation page said "open the invitation on your mobile device"; and the first APK from any source needs "install unknown apps" to be allowed once.

**Evidence.** [The M1 article](2026-11-09-m1-ci-to-a-tablet-in-one-night.html); commits `8170146` and `708cfe0`; [docs/ci.md](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/docs/ci.md).

## 3. Build provenance and signed approvals before anything reaches Google Play

**What.** Three controls stand between a commit and the Play Store.

- **Provenance.** The release job makes a build provenance attestation (SLSA, Sigstore, stored by GitHub) for the signed APK and AAB: a signed statement that this workflow built these exact files from this commit. Before any upload to Google Play, `play.yml` checks it with `gh attestation verify`, pinned to the repository, the signer workflow, the source commit and the branch, and refusing self-hosted runners. It also checks the files' SHA-256 sums against the release.
- **Signed approval.** The security agent signs, with its own Ed25519 key, a statement that it reviewed exactly the commits between `main` and a given head. A check reads the keys and approvals as they are on `main`, so a commit on `dev` cannot add its own key or rewrite an approval. Only a key with the role "security" can approve.
- **Promotion.** `promote.yml` runs only from `main`'s copy of the workflow, needs a green security check on that exact commit, and promotes a commit only if a valid signed approval covers it. Until `main` is set up for it, it opens a pull request instead of moving `main`.

**Why.** Dinis's rule of 9 November: everybody pushes to `dev`, the store builds only from `main`, and nothing reaches `main` without a signed review. The agents cannot approve their own work.

**Status: built; checked live up to the upload.** On 9 November the Play workflow was run by hand on `main` with no Play credential. Every check before the upload passed: the approval, the release tag, the SHA-256 sums, and `gh attestation verify` on both files. Only the upload step was skipped, because the Play credential did not exist yet. **Nothing has been uploaded to Google Play.** The record is in [docs/ci.md, "First live run of the whole Play chain"](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/docs/ci.md).

**Evidence.** [docs/security/approvals.md](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/docs/security/approvals.md) (the design and its limits, stated plainly); `scripts/verify-approval.mjs` and its self-test in `docs/security/approvals/`; the workflows `.github/workflows/android.yml`, `play.yml`, `promote.yml` and `security-approval.yml`. Attestation and Sigstore are not Google services. They are the supply-chain half of a pipeline whose delivery half is Firebase and Google Play.

## 4. Gemma 4 on the tablet, through LiteRT-LM, and a capped cloud model beside it

**What.** The app can download a Gemma 4 model once and chat with it on the device, with no network after setup. It runs through LiteRT-LM, Google AI Edge's on-device runtime (Apache 2.0). The model files are Gemma 4 E2B (the default) and E4B, in the form that Google AI Edge Gallery's allow-list (version 1.0.23) offers.

**How we handle the files.** The download host and exact file addresses are fixed in the code. Each file's size and SHA-256 are fixed in the code and in the security table of models ([docs/security/models.md](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/docs/security/models.md)), with the licence read from the model card and recorded; both Gemma 4 files read Apache 2.0. A file that does not match is deleted and never opened.

**Beside it, a cloud model.** The same app can call a cloud model through OpenRouter, for staff only and off by default. That is not a Google service; it is here because the Workbench compares the on-device Gemma with a cloud model on the same tests. The key is saved on the tablet in Android's Keystore and excluded from backup. Spend is capped in code at US$5 per tablet and US$1 per session, and the Workbench shows a cost line before a cloud sweep and stops at the cap.

**Status: live on the team's tablets, as a preview.** On 9 November, on a Lenovo Idea Tab Plus, build 0.1.78: Gemma 4 E2B downloaded on the tablet and answered at about 5.4 to 5.5 tokens per second (68 tokens in 13.6 seconds; 140 in 26.5 seconds). That is one run on one tablet, from the conductor's board entry; **unverified** by me. The screens are switched off for everyone and reached through Diagnostics, Preview.

**Evidence.** [docs/security/models.md](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/docs/security/models.md); `app/src/main/java/ai/riskmandate/app/llm/ModelCatalog.kt`; [the on-device model brief](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/briefs/2026-11-09__on-device-llm/README.md). The app collects no personal data and sends nothing to its makers; the model download contacts Hugging Face and its download servers only when you tap Download, and the cloud model contacts OpenRouter only when it is switched on.

## 5. Android Management API: a fully managed tablet policy

**What.** Tooling to enrol a factory-fresh tablet as fully managed through the Android Management API, scanning a QR code at the welcome screen, and to force-install the app as a private app on managed Google Play. It has a zero-dependency command-line tool, a QR encoder, a fully managed device policy, and a rehearsal script for the day.

**How it was checked.** Every policy field is validated offline against a trimmed copy of Google's own discovery document (revision 20261104), and every command has a dry-run mode that shows the exact request. No network and no credentials are needed for the tests.

**Status: built, not run against Google.** The Google Cloud project, the Play Console account and the enterprise did not exist when the tooling was written, and milestones M2 to M4 were parked on 9 November until after Web Summit. As of 9 November, the tablets in use get the app through Firebase App Distribution instead.

**Questions we would like to put to Google.** The tooling's README records them as open, not as findings:
- Google's permissible-usage page lists solutions "developed and used exclusively for first party in-house applications" as not allowed. A company managing its own tablets for its own app looks close to that line. Is it inside it?
- An app restricted to organisations in Play Console stays private for ever, so the organisation build needs its own package name. We plan a separate ID for it.
- Initial device quota beyond the default needs a business justification.

**Evidence.** [devices/README.md](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/devices/README.md) and `devices/REHEARSAL.md`; the policy `devices/policies/riskmandate-fully-managed.json`; [the M2 threat model](https://github.com/PrompTugal/PrompTugal__App__Android/blob/dev/docs/security/threat-model-m2.md).

## What this page does not claim

- No example here says that anything is on Google Play: nothing is.
- Nothing here is a claim about RiskMandate beyond what riskmandate.ai says.
- Where a result comes from the project's notes and not from something I saw, I marked it **unverified**. The commit hashes were read from the repository.
