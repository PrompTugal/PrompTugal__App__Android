# Proposal: the store ships only from main

For Dinis. Written by conductor.android@promptugal.ai.dinis on 9 Nov 2026, from his answer relayed by conductor@web-summit.dinis at about 13:40 UTC:
- dev no longer pushes to the store; only main does;
- every push to dev runs the security review;
- only when the review passes is the change promoted from dev to main;
- the push to main triggers the Google Play upload;
- main is protected later;
- the same applies to iOS.

**Status: a proposal. Nothing is built.** Dinis decides who or what merges to main (section 4).

## 1. Today
`.github/workflows/android.yml` runs on every push to dev that touches the app:
1. The jobs `check` and `licences` run: tests, lint, release builds, the secret scan and the licences.
2. The job `release` then:
   - signs the build;
   - sends the APK to Firebase App Distribution (the testers group "team");
   - creates a GitHub pre-release;
   - when the Play secrets exist, uploads the AAB to Google Play as a **draft** on the internal track.

The agents' own security review happens before the conductor merges a branch to dev. CI does not check it.

## 2. Proposed flow
```
work/<role> --(security PASS, conductor merges)--> dev --(gate green + security approval of that exact SHA)--> main --> Google Play (draft)
                                                    |
                                                    +--> Firebase testers, as today (not a store)
```

1. **dev keeps Firebase and stops Play.**
   - Testers still get every dev build on their devices. Firebase is a test channel, not a store.
   - The Play step is removed from dev runs.
2. **The security gate is a required check, made of two parts.**
   - *Automated*, on every push to dev and on every pull request to main: the existing secret scan, licences, dependency check (osv), lint and tests, reported as one check named `security-gate`.
   - *The agent's review*:
     - security.android reviews the dev head and appends a line `{sha, verdict, review}` to `board/security/approvals.jsonl`, signed with its session key, which is published in `board/keys.json`.
     - A check named `security-approval` passes only when the SHA being promoted carries a valid PASS signed by a key in `board/keys.json` for the security role.
     - No agent can mark its own work passed: the check accepts only the security role's key.
3. **Promotion: a fast-forward of main to an approved dev SHA.** The SHA on main is then the very commit the testers ran.
   - A workflow `promote.yml` runs when an approval lands.
   - It opens or updates the pull request `dev -> main`, pinned to the approved SHA.
   - It writes in the description the build that SHA produced, the security review and what changed since the last main.
4. **The Play upload runs on a push to main only.**
   - It does not rebuild: it takes the signed AAB that the dev run built for that SHA (from its GitHub release), checks its SHA-256, and uploads it to Play as a draft.
   - versionCode stays the workflow run number. It already increases across branches, so Play always sees a higher number.
   - A person still completes each release in the Play Console. The draft status is kept.
5. **main is protected later**, at Dinis's word. The protection would require `security-gate` and `security-approval`, allow no force-push, and allow fast-forward merges only.

## 3. Why this shape
- **One artifact.** What reaches Play is byte-for-byte what the testers used, so nothing is rebuilt between test and store.
- **The review is verifiable.** Today a security PASS is a message between sessions. With this, it is a signed record CI can check, and anyone can audit it in the repository.
- **It fits the estate's rule.** No agent pushes to main. The PrompTugal news site already works this way, with a pull request from dev to main.

## 4. Who merges to main: Dinis's decision
| Option | How | Good | Bad |
|---|---|---|---|
| **A. Dinis merges the pull request** (recommended for now) | `promote.yml` opens the PR. Dinis merges it in the GitHub app, from his phone. | Matches the standing rule (no agent pushes to main). A person sees each store release. Needs no new token. | Dinis is in the loop for every store build during the summit week. |
| B. The workflow merges after the gate | `promote.yml` fast-forwards main itself once both checks pass, using a GitHub App token held only in the repository's secrets. | No human wait, so it is fastest. | An agent-written commit can reach the store with no human seeing it. It needs a new App and secret, which Dinis adds. |
| C. Dinis approves, the workflow merges | The GitHub Environment `production` requires Dinis as reviewer. He taps Approve, and the workflow does the fast-forward and the upload. | One tap, and a person stays in the loop. The audit sits in GitHub. | It needs the same App token as B and the Environment set up by Dinis. |

The recommendation is A now and C after Web Summit: it keeps a person on every store release, and the Play draft status is a second human step either way.

## 5. What changes, and who does it, once Dinis says yes
| Who | What |
|---|---|
| ci (the scripts owner) | Remove the Play step from dev runs. Add the `security-gate` job, `promote.yml`, and the main-only Play job that reuses the dev AAB. Write the tests for the approval check. |
| security.android | The signed approvals file and its format. A review of the workflows before merge. |
| conductor.android | Docs (`docs/ci.md`), this proposal's decision recorded in `board/status.json`, and the same shape briefed to the iOS team through mobile@. |
| Dinis | The choice in section 4, and later the protection of main (a repository setting agents cannot change). |
