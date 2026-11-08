# security.android status

> Round 4 done (8 Nov 2026, about 23:55 UTC): reviewed app d36382f PASS and play 1e08cbb PASS (R-play-1 fixed); release ee893b2 and devices 7e130db unchanged since my PASS. Waiting for the next wake.

## Doing
Nothing. Stopped until woken.

## Done
- Round 4 reviews: `2026-11-08-app-d36382f.md` (PASS; new permission ACCESS_NETWORK_STATE, R-app-4 low: the privacy and Data safety docs say INTERNET only) and `2026-11-08-play-1e08cbb.md` (PASS; R-play-1 closed; R-play-6 low: update the permission statement when d36382f merges). The 7b5f0f8 review is superseded by 1e08cbb.
- Round 3 reviews in `docs/security/reviews/`: `2026-11-08-play-7b5f0f8.md` (PASS; R-play-1 still open, low), `2026-11-08-release-ee893b2.md` (PASS; R-rel-1 low: licence gate passes a multi-licence artifact if any one licence is Apache/MIT/BSD; both new action SHAs checked against upstream), `2026-11-08-devices-7e130db.md` (PASS; 68 device tests pass; hardened policy not applied; R-dev-1 low: wipe after 10 wrong PINs is a denial of service).
- Round 2 reviews: `docs/security/reviews/2026-11-08-app-44131ad.md` (PASS, 3 info) and `docs/security/reviews/2026-11-08-play-6ee3f6b.md` (PASS; R-play-1 low: privacy page says "and its subdomains", the app allows only riskmandate.ai and www.riskmandate.ai; fix before the page is hosted). release and devices: nothing beyond dev.
- Task 1, branch reviews: `git fetch origin`; `git log origin/dev..origin/work/{app,release,devices,play}` is empty for all four. No review file written (nothing to review). The next round reviews whatever is there.
- Task 2: `scripts/osv-check.sh` (reads the coordinates from `docs/security/licences.md`, queries OSV.dev's public batch API with only `group:artifact` and version, writes `docs/security/vulns.md`, exit 1 if any hit). Run on the 84 release runtime artifacts: **none known**. Pipeline checked by feeding it `protobuf-java:3.9.0`, which it flagged (5 advisories with fixed versions). Scope: release runtime classpath only, not build plugins or test libraries.
- Task 3: `docs/security/threat-model-m2.md` (12 threats, priorities before M2 goes live).
- Task 4: `docs/security/github-settings.md` (checklist, nothing verified as set: no admin access).
- Task 5: `scripts/secret-scan.sh` now covers every ref (`git rev-list --all`, local and origin branches, tags), skips identical trees, has 13 pattern classes (adds service-account JSON, npm, Anthropic/OpenAI keys, password in URL, keystore password assignment), drops the `devices/test` exclusion, prints refs and commit count, has `--fetch`. Tested against a planted fake key and a `.jks` on a side branch that was later deleted from the tip: both caught. Current result: clean, 23 commits, 8 refs.

## Next
At each wake: `git fetch origin`, review `origin/dev..origin/work/<role>` for each of app, release, devices, play into `docs/security/reviews/<date>-<role>-<short-sha>.md` with `Verdict: PASS` or `HOLD`; rerun `scripts/osv-check.sh` when dependencies change.

## Blocked
Nothing.

## For other roles
- play: R-play-6, when app d36382f merges, change "the only permission is internet access" in `privacy.html`, `privacy-policy.md`, `data-safety.md`.
- app: R-app-4, keep ACCESS_NETWORK_STATE and tell play, or drop it; R-app-5 optional intro line.
- release: R-rel-1, make `scripts/ci-licences.sh` require every listed licence to match, add a test row.
- play: R-play-4 repeat the switch-off-unknown-sources note in the Firebase guide; R-play-5 say how tester emails are kept.
- devices: R-dev-1 decide the PIN-wipe setting with Dinis before applying the hardened file; R-dev-5 scan the first generated PNG with a phone before tablet day.
- play: R-play-1, correct the subdomain sentence in `privacy.html` and `privacy-policy.md`.
- app: R-app-1, exclude `file`, `database`, `sharedpref`, `external` domains too in `data_extraction_rules.xml` (defence in depth).
- devices (from the threat model): default enrolment `--duration` of 900s; add `policy-get`; decide with Dinis on safe boot, USB, screen lock and FRP settings; keep a must-have-settings check on the policy file.
- release: `.github/dependabot.yml` (actions, gradle, npm for `.github/firebase`); Gradle dependency verification metadata (A-12).
- conductor: `scripts/osv-check.sh` needs network to api.osv.dev; run it after `scripts/licences.sh` in the gate if wanted.

## For Dinis
- Work through `docs/security/github-settings.md`, above all section 3 (Environment `release` with you as required reviewer, secrets moved into it) and section 4 (secret scanning and push protection). Finding A-2 stays open until then.
- Decisions in `docs/security/threat-model-m2.md`: screen lock, FRP, factory reset, a dedicated owner account with a security key for the enterprise.
