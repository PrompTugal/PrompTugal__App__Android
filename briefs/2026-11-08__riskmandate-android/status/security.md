# security.android status

> Urgent review 9 Nov 2026 (about 01:50 UTC): release f0ee176 PASS (licence job, paths filter, per-job concurrency). work/editor appeared, no commits. Waiting for the next wake.

## Doing
Nothing. Stopped until woken.

## Done
- Urgent review `2026-11-09-release-f0ee176.md`: **PASS**. Licence gate still blocks the release (`needs: [check, licences]`) and no longer depends on a cold Gradle cache; the new job holds no secret; paths filter and concurrency checked. R-rel-8 (low): no CI runs for `devices/` or its policy tests; R-rel-9 (info): Gradle wrapper checksum.
- Reviews `2026-11-09-app-2bbc37b.md` (PASS: host routing, FeatureFrame; stale saved routes now resolve to Home; no manifest or permission change) and `2026-11-09-architect-8ab334d.md` (PASS: docs; its WebView rules agree with my checklist; R-arch-1 restrict the asset loader to /feature_site/, R-arch-2 add SSL-cancel, no DownloadListener, safe browsing, cookie clearing).
- Review `2026-11-09-play-fb617fd.md`: **PASS** (docs; R-play-7 info: a keyless Play route should use its own service account and condition).
- `docs/security/summit-checklist.md`: what I will check in work/dev.site (WebView: allow-list on every navigation, no JS bridge, no file access, WebViewAssetLoader for the offline copy), dev.stand (lock task only, no PIN, no device-owner calls), dev.mandate (own PackageManager reads only), and the host. Skeleton on dev (Feature.kt, Features.kt, StandHost placeholder) read: no new permission, no exported component; `FeatureContext` gives features the whole activity (info).
- Branches `work/architect`, `design`, `qa`, `dev.site`, `dev.abpdemo`, `dev.mandate`, `dev.stand`: no commits beyond dev at 01:25 UTC, nothing to review yet.
- Review `2026-11-09-release-8d3256d.md`: **PASS**; the script's behaviour is unchanged, the printed steps now put secrets in the `release` environment (the A-2 fix). R-key-1 pin the docker image by digest, R-key-2 `mkdir -m 700` first (both info).
- Re-review `2026-11-09-release-c4832f9.md`: **PASS**. The Firebase step is two commands (parsed and `bash -n` checked). 2392f9f HOLD is superseded.
- Urgent review `2026-11-09-release-2392f9f.md`: **HOLD**. R-wif-1: the `npm ci ... --prefix` line and the `firebase appdistribution:distribute` line were joined and the continuation lost, so the step fails (exit 127) and skips Play and the GitHub release. Fix described for release. Design checked: pin verified, `id-token: write` on the release job only, key removed. For Dinis: WIF condition should use repository and owner ids and the ref (R-wif-2, R-wif-3), service account least privilege (R-wif-4).
- Round 6 review: `2026-11-08-release-2d77de1.md` (PASS): the smoke job still holds no secret; new Gradle build there reads but does not write the cache (keep `cache-read-only` at its default); R-rel-7 info.
- Round 5 reviews: `2026-11-08-release-1106398.md` (PASS; gate tested on a doctored table: GPL+MIT and Apache+EPL now fail; R-rel-6 info: a URL containing apache, mit or bsd still passes a single-licence row) and `2026-11-08-play-8290a50.md` (PASS; permission and Diagnostics statements match the code; R-play-6 closed).
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
- release: R-rel-8, add a `devices` job (`node --test devices/test/*.test.mjs`, read-only, no secrets) on `devices/**` and pull requests.
- dev.site, dev.stand, dev.mandate, dev.abpdemo, app: read `docs/security/summit-checklist.md` before pushing; a WebView JS bridge, file access, a PIN in the source or a new permission without the docs changed is a HOLD.
- release: R-rel-6 (info), match the licence URL against known licence URLs instead of a substring.
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
- WIF provider condition: use `assertion.repository_id`, `assertion.repository_owner_id`, `assertion.environment=='release'` and `assertion.ref=='refs/heads/dev'`; bind workloadIdentityUser to that one principalSet; give the service account only Firebase App Distribution Admin (R-wif-2 to R-wif-4). Set the `release` environment reviewer and deployment branch `dev` (A-2).
- Work through `docs/security/github-settings.md`, above all section 3 (Environment `release` with you as required reviewer, secrets moved into it) and section 4 (secret scanning and push protection). Finding A-2 stays open until then.
- Decisions in `docs/security/threat-model-m2.md`: screen lock, FRP, factory reset, a dedicated owner account with a security key for the enterprise.
