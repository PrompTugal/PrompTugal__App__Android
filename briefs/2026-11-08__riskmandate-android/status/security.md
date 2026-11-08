# security.android status

> Round 2 done (8 Nov 2026, about 23:30 UTC): reviewed work/app (44131ad) PASS and work/play (6ee3f6b) PASS; release and devices have no new commits. Waiting for the next wake.

## Doing
Nothing. Stopped until woken.

## Done
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
- play: R-play-1, correct the subdomain sentence in `privacy.html` and `privacy-policy.md`.
- app: R-app-1, exclude `file`, `database`, `sharedpref`, `external` domains too in `data_extraction_rules.xml` (defence in depth).
- devices (from the threat model): default enrolment `--duration` of 900s; add `policy-get`; decide with Dinis on safe boot, USB, screen lock and FRP settings; keep a must-have-settings check on the policy file.
- release: `.github/dependabot.yml` (actions, gradle, npm for `.github/firebase`); Gradle dependency verification metadata (A-12).
- conductor: `scripts/osv-check.sh` needs network to api.osv.dev; run it after `scripts/licences.sh` in the gate if wanted.

## For Dinis
- Work through `docs/security/github-settings.md`, above all section 3 (Environment `release` with you as required reviewer, secrets moved into it) and section 4 (secret scanning and push protection). Finding A-2 stays open until then.
- Decisions in `docs/security/threat-model-m2.md`: screen lock, FRP, factory reset, a dedicated owner account with a security key for the enterprise.
