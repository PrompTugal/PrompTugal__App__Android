# dev.releases status

> Feature F "What's new" built end to end (list, reader, tablet two-pane, sample article, show-once logic, tests); `enabled = true`. Waiting on the host hook (app) and the screenshot copy step (release).

## Doing
Gate run and push.

## Done
- `features/releases/`: `Article.kt` (front matter), `Markdown.kt` (headings, paragraphs, lists, bold, italic, code, links, images), `ReleaseStore.kt` (scan of `assets/releases`, newest first, pt-PT fallback, scaled image decode), `ReleasesNotice.kt` (seen-version logic, prefs file `feature_releases`), `ReleasesFeature.kt` (screen), `README.md` (format and decisions).
- Strings `feature_releases.xml` in en and pt-PT. Uses only Compose locals (no Activity), so it works with FeatureContext v1 and v2.
- Sample article (marked sample, en and pt-PT) with a screenshot.
- Tests: Markdown, parser, list order, pt fallback, seen-version logic (JVM); Robolectric render with image, list/reader, back, two panes, link allow-list; `ReleasesScreenshotTest` writes `releases-list-*` and `releases-reader-*` PNGs.

## Next
After the host hook lands: nothing, unless review asks for changes.

## Blocked
Nothing.

## For other roles
- **For release:** before `assembleRelease` and after `testDebugUnitTest`, for the newest folder under `app/src/main/assets/releases/` copy every `app/build/screenshots/<name>` referenced as `screenshots/<name>` in its `article*.md` into `<folder>/screenshots/` (overwrite). Screenshots come from `ScreenshotTest` (`home-*`, `licences-*`) and `ReleasesScreenshotTest` (`releases-list-*`, `releases-reader-*`), later other features' tests. Details: `features/releases/README.md`.
- **For app (host):** on the home screen, `if (ReleasesFeature.enabled && ReleasesNotice.shouldOffer(this, BuildConfig.VERSION_CODE))` show a button with `R.string.feature_releases_offer` that sets the route to `releases` (or open it once at start). The feature marks the version seen itself. Optionally call `ReleasesNotice.markSeen(context, versionCode)` if the user dismisses the offer.
- **For editor:** write `article.md` and `article.pt-PT.md` in `assets/releases/<YYYY-MM-DD>-<slug>/` per the README; the sample (`2026-11-09-sample`) can be deleted once the first real article exists.
- **For architect:** no dependency added, no FeatureContext change needed.

## For Dinis
Nothing.
