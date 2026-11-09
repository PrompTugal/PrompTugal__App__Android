# The signed security approvals

Designed by security.android@promptugal.ai.dinis, 9 Nov 2026, at the conductor's request after Dinis's rule of about 14:10 UTC (everybody pushes to `dev`; security reviews every push to `dev`; only Dinis and the conductors move `main`; the store builds only from `main`). This is the design for the `security-approval` check in `docs/release-workflow-proposal.md` (option B). The reference code is in `docs/security/approvals/` and its self-test passes (`node docs/security/approvals/selftest.mjs`, 19 cases). release.android builds the workflow around it; I review the workflow before it lands.

## 1. What is signed

One line in `board/security/approvals.jsonl` (append only) per review decision on a `dev` head `H`:

```
{"v":1,"head":"<40 hex>","base":"<40 hex: main when I reviewed>","range":"<sha-256 of the commits in base..head>","commits":N,
 "verdict":"PASS|HOLD|REVOKE","review":"docs/security/reviews/<file>.md","review_sha256":"<hex>",
 "ts":"<UTC>","expires":"<UTC, at most 7 days>","key":"security.android","sig":"<base64 Ed25519>"}
```

The signature is Ed25519 over a fixed byte string (a first line `riskmandate-approval/1`, then `name=value` lines in a fixed order, nothing re-serialised), so there is one way to build the bytes. `range` is the SHA-256 of `git rev-list --reverse base..head`: a PASS says "I reviewed exactly these commits", and a different history gives a different hash. `review_sha256` ties the line to the text of the review that explains it.

A PASS means: every commit in `main..H` is reviewed, and no HOLD of mine is open in that range. A HOLD says the opposite for `H`. A REVOKE withdraws an approval after the fact (a problem found later); a REVOKE for a commit in the range blocks promotion while it stands.

## 2. Where the keys come from

`board/keys.json` lists public keys: `{id, role, alg:"ed25519", public_key (base64 of the raw 32 bytes), fingerprint, status:"active"|"revoked"}`. The check accepts a line only if its `key` is an active key with `role: "security"`: no other role's key can approve, so an agent cannot approve its own work.

The check reads `board/keys.json` and the approvals file **as they are on `main`**, never as the candidate has them. A candidate may only append to the approvals file (the file on `main` must be a byte prefix of it). So a commit on `dev` that swaps in its own key, or rewrites or deletes a line, fails.

## 3. Where the private key lives

`sign-approval.mjs --init` made it in security.android's container at `~/.riskmandate/security-approval.ed25519` (mode 0600, outside the repository). It is never printed, never in the repository, a message, a log or this document. Only the public key and its fingerprint are published. The signer will not sign unless the review file exists, its `Verdict:` line says the verdict being signed, and its file name carries the head's short sha: the key cannot sign a verdict I did not write down.

Limits, plainly:
- The key stops other agents forging an approval. It does not stop me being fooled, or someone who takes over my container. Dinis or a conductor moving `main` stays the human backstop (option A or C in the proposal).
- The container is temporary. If it is lost, I cannot sign until a new key exists, and that goes through section 6, so a person sees it.

## 4. The check

`verify-approval.mjs --repo . --main <main sha> --tip <dev sha>`, run from a checkout of `main` (the script comes from `main` too). It passes only if all of these hold:
1. `tip` is a descendant of `main` (a fast-forward).
2. The tip's diff against `main` touches none of the trust paths (`board/keys.json`, `.github/`, the verifier and its library, the promotion selector `scripts/promote-select.sh`, `scripts/find-release-tag.sh`, `scripts/changed-build-paths.sh`, `scripts/changed-since-release.sh`, `scripts/resolve-commit.sh`, `docs/security/approvals/`, `CLAUDE.md`), apart from the approval record and the reviews. A change there is promoted by a person, not automatically.
3. The approvals file on the tip extends the one on `main`.
4. The latest valid line about the tip, or about an ancestor of it that is past `main`, is a PASS signed by an active security key, made against this `main`, with a `range` and `commits` equal to what `git` says now, dated not in the future, unexpired and issued for at most seven days.
5. If that line names an ancestor `H` of the tip, the commits after `H` change only `board/security/approvals.jsonl`, `docs/security/reviews/*.md` and plain status text that no build, test or workflow reads (`board/status.json`, `board/sessions.json`, `board/team/*/debriefs/*.md`, `briefs/*/status/*.md`). A hash cannot contain its own approval, so the record is committed after the commit it approves, and the conductor's status commits come after nearly every merge; code, keys, workflows and scripts never ride along.
6. No later REVOKE covers any commit up to the tip.

The workflow also needs, outside this check: the gate green for that commit, and (as the proposal says) the store build must be the AAB that the dev run built for that sha, with its SHA-256 checked.

## 5. What I need from the others

- **release.android**: the workflow runs the verifier from `main`, fails closed on any non-zero exit, and treats `ok:false` as "not approved". Add the selftest to the check job. Use `allowTrustPathChange` only for a human-triggered promotion. I review the workflow, `promote.yml` and the verifier's copy in `scripts/` before they land.
- **conductor / Dinis**: add the public key below to `board/keys.json` through `main`, with Dinis comparing the fingerprint on his own channel. Branch protection on `main` (require `security-gate` and `security-approval`, no force-push, fast-forward only) is a repository setting only Dinis can change.
- **Where my lines land**: my session's branch instruction is `work/security`, so I append lines there, and the conductor merges them to `dev` with the reviews, as now. If Dinis wants me to push to `dev` directly he can say so; until then I do not.

## 6. Rotation and revocation

A new key: security generates it, publishes the public key and fingerprint here and on the status page, a person merges it into `board/keys.json` on `main`, and the old key is marked `revoked` in the same change. A lost key is revoked the same way. Lines signed by a revoked key stop counting, so approvals have to be re-made with the new key (they last at most seven days anyway).

## 7. The public key (for `board/keys.json`)

```
{"id":"security.android","role":"security","alg":"ed25519","public_key":"b7sGfeTzCndCEQFXsl+9zyNqu8I8ohAZKz4XbeU/4Zc=","fingerprint":"a8a6 afbd 9974 80e7","status":"active"}
```
Fingerprint: the first 8 bytes of the SHA-256 of the raw public key, in groups of four hex digits. Dinis: please compare it with what I show on the status page and in a message you read yourself.

## 8. The review of each dev head

From now on I review each `dev` head after it lands: `docs/security/reviews/<date>-dev-<short sha>.md` with `Verdict: PASS` or `HOLD`, covering the commits since the last dev head I approved. Because the conductor already merges only branch heads I passed, the dev review looks at what merging adds: conflict resolutions, the combined effect of two heads (the compile break of R-app-3 is that kind), a change in a trust path, and the secret scan over all refs. Then I sign the verdict.

## 9. The first promotion (what happened, and what it means)

`main` was the initial commit, with no `board/keys.json`, so nothing on `main` could verify the first promotion: it was Dinis's own merge (PR #1, merge commit 1f6adb3 of dev 0eaa827, an identical tree). I signed a PASS for 0eaa827 with `base` 0b299ae as an audit record: its signature verifies against the key now on `main`, and its range hash recomputes. A workflow that asks the check about 1f6adb3 with the old `main` as base still fails closed ("no board/keys.json on main"), by design: there was no key then. The first run the check can actually judge is the next promotion, with `main` = 1f6adb3 as its base. Dinis's dry run of `play.yml` (without the Play secret) is therefore that one.

## 10. Who decides a trust-path change after 9 Nov 18:00 UTC

Dinis's decision, as the conductor records it in `CLAUDE.md` (03e58f5) and `board/status.json`: "this needs to be done by you or one of the conductors here since I cannot be a blocker". Where this document says "a person promotes" (a head that changes a trust path, a `play.yml` dispatch with the human variant), a conductor now does it, always on my signed PASS and a green gate; Dinis keeps GitHub settings, Environment secrets and store accounts.

What that changes, plainly: the human second look on trust-path changes (workflows, the verifier, keys, `CLAUDE.md`) is now two agents, me (the signed PASS) and a conductor (the promotion). The independent checks left are Dinis's own: the Environment rules (`main`-only deployment branches, secrets only at Environment level), the Play draft that a person completes in the Play Console, and the store accounts.

So that the two agents are not the whole story, I keep these rules, and the conductor and release should expect them:
1. **Keys.** I do not sign a PASS for a head that adds, revokes or rotates a key in `board/keys.json` until Dinis has confirmed the fingerprint on his own channel. `keys.json` is the root of trust; a conductor cannot make my key, and I will not vouch for another one on my own word.
2. **Rules about who decides.** A change to `CLAUDE.md` that moves who may approve, promote or hold a secret is passed only when it quotes Dinis and is also in `board/status.json` for him to read, as 03e58f5 does; I list it on my status page so he sees it.
3. **The signature stays mine.** A conductor does not sign approvals and I do not promote. A promotion without my signed PASS for exactly that head is refused by the verifier, whoever starts it.
4. **Draft only.** `play.yml` uploads a draft; nothing goes live without a person completing it in the Play Console.
5. **Reviewer on `play`** (Dinis as required reviewer) is now optional rather than required, because Dinis cannot be a blocker; with the draft rule it is a convenience. It stays recommended for the first uploads.
