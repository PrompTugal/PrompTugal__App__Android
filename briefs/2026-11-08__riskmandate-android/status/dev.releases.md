# dev.releases status

> Feature F "What's new" built end to end (list, reader, tablet two-pane, sample article, show-once logic, tests); `enabled = true`. Waiting on the host hook (app) and the screenshot copy step (release).

## Doing
Gate run and push.

## Done
- `homeBadge()` implemented ("New" / "Novo" while this versionCode's notes are unseen and an article exists; `produceState` on `Dispatchers.IO`; test `ReleasesBadgeTest`). Merged `origin/work/architect` into this branch at the conductor's word to get the interface.
- A-rel-3: sample article and its image removed from the app's assets; tests use `Fixture` (test code and `src/test/resources/releases-fixture/`) through a new `ArticleImages` source; a test checks every bundled article parses. (Fixed on the way: an image-decode regression this refactor briefly introduced, now covered by a test.)
- A-rel-2: links open with the composition's own context (the Activity), not the application context.
- `features/releases/`: `Article.kt` (front matter), `Markdown.kt` (headings, paragraphs, lists, bold, italic, code, links, images), `ReleaseStore.kt` (scan of `assets/releases`, newest first, pt-PT fallback, scaled image decode), `ReleasesNotice.kt` (seen-version logic, prefs file `feature_releases`), `ReleasesFeature.kt` (screen), `README.md` (format and decisions).
- Strings `feature_releases.xml` in en and pt-PT. Uses only Compose locals (no Activity), so it works with FeatureContext v1 and v2.
- Sample article (marked sample, en and pt-PT) with a screenshot.
- Tests: Markdown, parser, list order, pt fallback, seen-version logic (JVM); Robolectric render with image, list/reader, back, two panes, link allow-list; `ReleasesScreenshotTest` writes `releases-list-*` and `releases-reader-*` PNGs.

## Next
- P7 (architect, work/architect c9233a0): override `icon` (material-icons-core), `kicker`, `summary` in `ReleasesFeature` once `Feature.kt` on `dev` has them. The strings `feature_releases_kicker` and `feature_releases_summary` (en, pt-PT) are already in.
After the host hook lands: nothing, unless review asks for changes.

## Blocked
Nothing.

## For other roles
- **For release:** your `screenshots.txt` contract (e7a242f) is adopted; `features/releases/README.md` documents it. The sample article keeps one committed image on purpose (test fixture), so the `.gitignore` rule for `assets/releases/*/screenshots/` must not untrack it (it is already tracked, so it stays). `ReleasesScreenshotTest` adds `releases-list-*` and `releases-reader-*` sources.
- **For app (host):** on the home screen, `if (ReleasesFeature.enabled && ReleasesNotice.shouldOffer(this, BuildConfig.VERSION_CODE))` show a button with `R.string.feature_releases_offer` that sets the route to `releases` (or open it once at start). The feature marks the version seen itself. Optionally call `ReleasesNotice.markSeen(context, versionCode)` if the user dismisses the offer.
- **For editor:** list each image in `screenshots.txt` (`<target>.png <- <source>.png`) and reference `screenshots/<target>.png`; write `article.md` and `article.pt-PT.md` in `assets/releases/<YYYY-MM-DD>-<slug>/` per the README; the sample (`2026-11-09-sample`) can be deleted once the first real article exists.
- **For architect:** no dependency added, no FeatureContext change needed.

## Notes
- design.android's `ui/` components (FeatureContent, SectionCard, StatusChip, NoticeBanner) are not on this branch yet; the screen uses plain Material 3 and can adopt them after `work/design` merges to `dev`. The 'New in this version' banner is the host's (see For app).

## For Dinis
Nothing.
