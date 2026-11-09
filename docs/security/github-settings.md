# GitHub settings checklist for Dinis

By security.android@promptugal.ai, 8 November 2026. Agents cannot change repository settings and I could not read
them from here, so nothing below is verified as set or unset; each item says where to click and why. Repository:
`PrompTugal/PrompTugal__App__Android` (public, Apache 2.0). Tick the box when done; tell the conductor which are
done so the board is true.

Wording note: GitHub moves these screens around. If a name has changed, the intent in the "Why" column is what counts.

## 1. Who can push (Settings, Collaborators and teams)
- [ ] Only you (and the one bot or user the conductor pushes as) have **Write** or higher. Everyone else is Read
      or Triage. Remove stale invitations and outside collaborators.
- [ ] Agents' tokens (fine-grained PATs or the GitHub App the sessions use) are limited to **this repository**,
      with *Contents: write* and *Pull requests: write* only; no *Administration*, *Actions*, *Secrets*, *Workflows*
      (the *workflows* permission lets a token edit `.github/workflows/`, which is how a secret gets exfiltrated).
- [ ] Two-factor authentication required for all collaborators (Organisation settings, Authentication security).
- [ ] The five work branches (`work/app`, `work/release`, `work/devices`, `work/play`, `work/security`) may be
      pushed by any session token; `dev` and `main` may not (next section). This is how the rule "only the
      conductor pushes to `dev`" becomes a mechanism instead of a promise.

## 2. Branch protection (Settings, Branches, or Rules, Rulesets)
Use a **ruleset** (newer, can target several branches and has bypass lists).

`main`:
- [ ] Require a pull request before merging; require **1 approval**; dismiss stale approvals on new commits;
      require approval of the most recent push (so the author cannot approve their own last push).
- [ ] Require status checks to pass: the `check` job of the `android` workflow; require the branch to be up to date.
- [ ] Block force pushes; block deletions; require linear history optional.
- [ ] Restrict who can push: nobody directly (PR only). Only you merge. Include administrators (no bypass),
      or at least do not give the agents' identity bypass.
- [ ] Require signed commits (optional; agents would need signing set up first: do not enable until they can).

`dev`:
- [ ] Block force pushes and deletions.
- [ ] Restrict pushes to the conductor's identity (and you). Work branches reach `dev` only through the conductor.
- [ ] Require the `check` status before merge if the conductor merges by pull request; if the conductor pushes
      directly (the current flow), this is enforced by the gate it runs, so say so in `docs/ci.md`.
- [ ] No bypass for other session identities.

`work/*` (optional ruleset): block deletions and force pushes (the plan says never force-push).

Why: `dev` triggers the release job, which holds the signing and store credentials. Whoever can change `dev` or
`.github/workflows/` can run code with them unless the Environment gate in section 3 holds (finding A-2).

## 3. Environment `release` with a reviewer (Settings, Environments)
The workflow already says `environment: release` on the `release` job (`android.yml:84`). A GitHub Environment
that does not exist yet is created implicitly **with no protection**, so create it deliberately:
- [ ] New environment named exactly `release`.
- [ ] **Required reviewers**: you. Tick "Prevent self-review" if more than one reviewer exists.
- [ ] **Deployment branches and tags**: *Selected branches*, only `dev`. (`main` too once releases are cut there.)
- [ ] Move the secrets **into the Environment** (not repository secrets): `UPLOAD_KEYSTORE_BASE64`,
      `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`, `FIREBASE_SERVICE_ACCOUNT_JSON`,
      `FIREBASE_APP_ID`, `PLAY_SERVICE_ACCOUNT_JSON`. Repository-level secrets are readable by any workflow run
      on any branch, including a pushed workflow edit. Delete any repository-level copies.
- [ ] Optional: a wait timer is not needed; the reviewer is the control.
- [ ] Do **not** put the AMAPI service-account key anywhere in GitHub (threat model M2-2, M2-8).

Why: with the reviewer in place a push to `dev` that edits the workflow still stops at "waiting for review" and the
secrets are not released until you approve. Read the diff of the workflow in that run before approving.

## 4. Secret scanning and push protection (Settings, Code security)
- [ ] **Secret scanning**: enabled (free for public repositories).
- [ ] **Push protection**: enabled (blocks a push containing a recognised secret; this is the strongest guard
      against an agent pasting a key).
- [ ] Secret scanning for **non-provider patterns** and **validity checks**, if offered.
- [ ] **Dependabot alerts** and **Dependabot security updates**: enabled. Add `.github/dependabot.yml` for the
      `github-actions` and `gradle` ecosystems and `npm` for `.github/firebase` (release owns that file; ask).
- [ ] **Private vulnerability reporting**: enabled, plus a `SECURITY.md` (who to tell, how). Needed because the app
      will be sold and the repository is public.
- [ ] **CodeQL** default setup for Kotlin/Java and GitHub Actions workflows: optional, free for public repos.
- [ ] Notifications: you receive the secret-scanning alerts.
Every session also runs `bash scripts/secret-scan.sh` (all refs); it complements, never replaces, these.

## 5. Actions permissions (Settings, Actions, General)
- [ ] **Actions permissions**: allow only actions created by GitHub plus the specific third-party actions in use,
      listed by SHA or `owner/repo@*`: `gradle/actions`, `r0adkll/upload-google-play`. (A "selected actions" list.)
- [ ] **Require actions to be pinned to a full-length commit SHA** (new setting; the workflow already does this).
- [ ] **Workflow permissions**: *Read repository contents and packages permissions* (the default token is
      read-only); untick "Allow GitHub Actions to create and approve pull requests". Jobs ask for more with
      `permissions:` (the `release` job needs `contents: write`).
- [ ] **Fork pull request workflows from outside collaborators**: *Require approval for all outside collaborators*
      (the strictest option).
- [ ] **Fork pull requests**: do not enable "Send write tokens to workflows from pull requests" and do not
      "Send secrets and variables to workflows from pull requests".
- [ ] Never use the `pull_request_target` trigger or `workflow_run` with untrusted code in this repository (none
      today; the `pull_request` trigger on the `check` job is safe because it has no secrets).
- [ ] Artifact and log retention: 14 days or less (the release APKs are artifacts; they are signed and the repo
      is public, so they are downloadable by anyone; finding A-14).
- [ ] Self-hosted runners: none (never attach one to a public repository).

## 6. Other repository settings
- [ ] Settings, General: disable Wikis, Projects and Discussions unless wanted (less surface); "Allow auto-merge" off
      unless the conductor wants it; "Automatically delete head branches" off for `work/*` (sessions reuse them).
- [ ] Settings, Webhooks and Deploy keys, Integrations (Installed GitHub Apps): list them and remove what is not
      needed. Every app with *Contents: write* can push.
- [ ] Settings, Code security: "Prevent direct secret leaks" is the push protection above; also "Block commits
      that contain secrets" for the organisation if available.
- [ ] Organisation: audit log reviewed monthly; enable the audit log streaming only if there is somewhere to send it.
- [ ] Repository visibility stays public by decision; confirm nothing private (briefs with contact details,
      device serials, `devices/out/`) is in the tree. The secret scan checks the names and the key shapes only.
- [ ] CODEOWNERS (`.github/CODEOWNERS`, release owns `.github/`): list `.github/workflows/`, `scripts/`, `devices/`,
      `docs/security/` as owned by you, so branch protection can require your review on changes there. CODEOWNERS
      review only bites with the `main` rule in section 2 and with "Require review from Code Owners" ticked.

## What I could verify and what I could not
Not verifiable from here: every setting above (no admin API access). After you tick them, the conductor can ask
me to re-read what the Actions API shows (workflow runs waiting for review on `release` prove section 3 works).
Finding A-2 stays open until section 3 is done and the first release run waits for your approval.

## 7. The release flow: Environments `promote` and `play` (written 9 Nov 2026, after security.repos's HIGH on the default branch)

**What is true.** This repository's default branch is `dev`. GitHub runs a `workflow_run` workflow from the **default branch's** copy of the file, with that branch's ref. So `promote.yml` on `workflow_run` is `dev`'s copy, not `main`'s, and anyone who can push to `dev` can edit it. (`push` to `main` runs `main`'s copy; `pull_request_target` runs the copy on the pull request's base branch, `main`; `workflow_dispatch` runs the copy on the ref you choose.) Nothing is exposed today: there is no App, no promote secret and no `promote` Environment. The comment in `promote.yml` that said "GitHub runs it from the default branch, so a dev push cannot edit it" is wrong while `dev` is the default.

**What protects the secrets, whatever the default branch is: the Environment's branch rule.** Do this before any secret exists:
1. Settings, Environments, New environment `promote`: Deployment branches and tags, **Selected branches and tags**, add `main` only (not "Protected branches only"). Add the App's id and private key (`PROMOTE_APP_ID`, `PROMOTE_APP_PRIVATE_KEY`) as **Environment secrets of `promote`**, never as repository or organisation secrets.
2. The same for `play`: deployment branches `main` only, `PLAY_SERVICE_ACCOUNT_JSON` as an Environment secret of `play`. Adding yourself as **Required reviewer** on `play` is recommended for the first uploads and optional after Dinis's rule of 9 Nov 18:00 UTC that he is never the blocker: `play.yml`'s manual dispatch (the variant that accepts a trust-path change) can be started by anyone with write access, and a conductor now decides it on my signed PASS; the upload is a draft that a person completes in the Play Console either way.
3. A run whose ref is `dev` cannot get an Environment limited to `main`, so a `dev` copy of a workflow can read nothing in those two Environments.
4. `release` (the upload keystore) stays with deployment branches `dev` and a Required reviewer (section 3 above): a `dev` pusher who edits `android.yml` can otherwise use the upload key.

**Making `main` the default branch** (Settings, General, Default branch) is defence in depth: `workflow_run` and the `workflow_dispatch` listing then come from `main`'s copy too. It is not needed once the Environment rules above are in place and `promote.yml` is dispatch-only on `main`; do it if you want the stronger default, and note that new pull requests then target `main` by default.
