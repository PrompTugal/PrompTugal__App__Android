# editor.android status

> First pass of the site is done on `work/editor`: builder, six pages, the article, validator and 46 tests; the gate passes. Not deployed. Waiting for the conductor's merge and for release.android to deploy.

## Doing
Nothing. Stopped after this push.

## Update: release articles (task from the conductor, 9 Nov ~01:46, Dinis's feature F)
- **Web Summit build article**, English and pt-PT: `app/src/main/assets/releases/2026-11-09-web-summit-build/` (`article.md`, `article.pt-PT.md`, `screenshots.txt`). Written from the code: strings, the feature objects, `StandSettings`, `StandHost` (long press on the bottom-right 48 dp corner of Home, off by default, default 3 idle minutes, 1 to 30). The author has not run the app on a device; the article says so. The pt-PT text uses the app's own Portuguese strings and needs a native speaker before it is shown.
- **M1 article moved** to `2026-11-09-m1-ci-to-a-tablet-in-one-night/article.md` as an older release: tables became lists, tokens became literals, no relative links (the app cannot fill tokens or open them).
- **The site builds its articles from those files** (`facts.release_articles()`, same front matter as the app; the sample article is skipped; screenshots are copied when CI has produced them, otherwise the caption stays and says so). `website/pages/articles/` keeps only the index.
- **screenshots.txt lists 8 images** from sources the existing tests write: `home-tablet10-landscape-light`, `abpdemo-step1/2/5/7-tablet-light`, `abpdemo-step5-tablet-dark`, `releases-list-phone-portrait-light`, `releases-reader-tablet10-landscape-light`. **Not verified:** there is no Android SDK in this session, so I could not run the tests or `release-screenshots.sh --verify`; I checked the names against the test code only. If CI's check job reports a missing source, edit that line. There are no screenshot tests for the in-app site, This app's mandate or stand mode; the article says so.
- **The sample article is NOT removed:** `ReleasesScreenTest` needs it (`first { it.dir.endsWith("-sample") }`, and its `sample-home.png`). Removing it needs dev.releases to change those tests first.
- The features page no longer says "in progress" in prose: it reads the flags (all four Feature objects are on in the source now).
- Not done: the pt-PT article is not published on the site (the site is English only).

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
