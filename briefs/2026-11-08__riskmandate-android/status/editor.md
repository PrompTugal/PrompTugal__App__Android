# editor.android status

> First pass of the site is done on `work/editor`: builder, six pages, the article, validator and 46 tests; the gate passes. Not deployed. Waiting for the conductor's merge and for release.android to deploy.

## Doing
Nothing. Stopped after this push.

## Done (commit on work/editor)
1. `website/` for android.app.promptugal.ai: `build.py` (standard library only, writes `website/_site/`), `facts.py` (reads the facts), `template.html`, `site.css` (light and dark, responsive, no scripts), `markdown.py` and `twin.py`, `validate.py`, `test_site.py`.
2. Facts read at build time, never typed: application ID, minSdk, targetSdk, compileSdk (`app/build.gradle.kts`); permissions (`AndroidManifest.xml`); languages (`locales_config.xml`); summit-feature flags (`Features.kt` and each feature object); the latest release and the list (git tags `v0.1.N`, with CI run, commit, date); `@Test` counts (source count, not a pass result); licences (`docs/security/licences.md`); the privacy body (`docs/play/privacy.html`, its TODO markers turned into plain statements or the repository link); the icon (SVG from the launcher drawable).
3. Pages: home, features, install (from `docs/play/tester-guide-firebase.md` and `docs/lessons.md`; no tester address), privacy, open source, releases, articles index, and the article `website/pages/articles/m1-ci-to-a-tablet-in-one-night.md`. Every page has a Markdown twin; `llms.txt`, `sitemap.xml`, `robots.txt` are written (no `CNAME`: the workflow adds it).
4. Summit features A to D are "in progress" on the features page, and the validator refuses a build that shows one as "on" while the source has `enabled = false` (A to C) or `StandHost.kt` is still the placeholder (D).
5. The validator checks links, anchors, twins, llms and sitemap entries, JSON-LD, the facts against the source, the privacy page, scripts and handlers, em-dashes, and secret-shaped strings, email addresses and Google project paths in every output file. Every rule has a negative control in the tests.

Gate, all run on `work/editor`: `python3 -m unittest website/test_site.py` (46 tests, OK); `python3 website/build.py` (8 pages); `python3 website/validate.py` (ok); `bash scripts/secret-scan.sh` (clean). Checked by eye in Chromium at 390 px and 1100 px, light and dark, no horizontal overflow. Every commit hash quoted in the article was checked to exist.

## Next
Nothing until the conductor sends a task. When features A to D land the site picks them up by itself (flags are read from the source); the article is dated 9 November and is a record, so it should not be edited as the app changes.

## Blocked
Nothing.

## For release
To deploy (GitHub Pages workflow):
- **Build command:** `python3 website/build.py` (Python 3.11 or later, standard library only; no install step).
- **Output directory:** `website/_site/` with `index.html` at its root, matching release.android's `website.yml` (conductor's message, 9 Nov 01:12). The build writes no `CNAME` or `build.json`: the workflow adds them (CNAME `android.app.promptugal.ai`). The build uses no GitHub token, so it works with or without `GH_TOKEN`.
- **Checkout with tags and history:** `fetch-depth: 0` (or `git fetch --tags --unshallow`). The releases page and the home page's version come from the `v0.1.N` tags; with none the site says "no release yet" rather than failing.
- **Optional:** set `SITE_COMMIT: ${{ github.sha }}` for the footer stamp (otherwise it uses `git rev-parse HEAD`).
- **Before publishing, run the gate in the job:** `python3 -m unittest website/test_site.py`, then `python3 website/build.py`, then `python3 website/validate.py` (exit 1 stops the deploy).
- **DNS (Dinis or whoever owns the zone):** a CNAME record `android.app.promptugal.ai` pointing at the organisation's GitHub Pages host, and "Enforce HTTPS" in the repository's Pages settings. I have not seen the zone or the Pages settings.
- **Rebuild when:** a tag is pushed (a new release) and on push to `dev`, so the features page follows the source.
- A job that only builds and deploys `website/` needs `pages: write` and `id-token: write` and nothing from the `release` environment; no secret is used.

## For Dinis
- Please read the article before it is public. It names you as the operator, names agents by role, says the conductor runs on Opus and the others on Sonnet (from the plan), and states two things I cannot check: the CI run numbers and green results (taken from the conductor's board; marked "unverified" in the text), and what happened on the tablet (from the conductor's commits).
- The privacy page still says the publisher's name, address and contact are "to be confirmed". Decide those (and the copyright holder for the licence notice) before the Play listing; the page and `docs/play/privacy.html` change together.
- Play Console wants a public privacy-policy URL: once deployed it would be `https://android.app.promptugal.ai/privacy.html` (not `/privacy`, which docs/play suggested).
- The site links to the repository as public, because the security review says it is. Please confirm it is.
- The home page quotes three lines from riskmandate.ai exactly as play.android recorded them on 8 November (site version 1.40.3), and says nothing else about RiskMandate. Not re-read today.

## Note: secret-scan
My first commit `d722a6b` makes `scripts/secret-scan.sh` report a `private key block` in `website/validate.py` and `website/test_site.py`: they hold the detection pattern as literal text, not a key. Fixed in the next commit by splitting the string; history keeps the match (no rewrite). security.android may want to note it as a known false positive.
