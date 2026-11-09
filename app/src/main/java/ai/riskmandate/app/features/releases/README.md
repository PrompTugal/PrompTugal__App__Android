# What's new (feature F, `releases`)

Owner: dev.releases.android@promptugal.ai. One Markdown article per release, bundled in the app, read offline.

## Decisions
- **No manifest.** The app scans `assets/releases/*/article.md` (`ReleaseStore`). Nothing to forget to update; a folder that does not parse (no title, no date) is skipped, never fatal.
- **No Markdown library.** `Markdown.kt` is a small renderer for what articles need (below). Anything else shows as plain text.
- **Order:** front matter `date`, else the folder's `YYYY-MM-DD` prefix; newest first, same day by folder name descending.
- **Language:** `article.pt-PT.md` on Portuguese devices when present, else `article.md`.
- **Links:** clickable only if `Links.isAllowed` (riskmandate.ai over https), opened through `Browser.open`. Other links are plain text.
- **Images:** only `screenshots/<name>.(png|jpg|jpeg|webp)` of the article's own folder (`Markdown.safeImagePath`), decoded off the main thread and scaled.

## Article format
```
app/src/main/assets/releases/<YYYY-MM-DD>-<slug>/
    article.md            English (required)
    article.pt-PT.md      Portuguese (optional)
    screenshots/<name>.png
```
Front matter, then body:
```
---
title: "Shown in the list and as the heading"
date: 2026-11-09
summary: One or two sentences for the list.
features: [site, abpdemo]      # ids of the features the release touched (informational)
sample: true                   # only the sample article; shows a "Sample" mark
---
# Heading, paragraphs, - lists, 1. lists, **bold**, *italic*, `code`, [text](https://riskmandate.ai/)

![Caption, also the TalkBack description](screenshots/home-phone-portrait-light.png)
```
Lists are one line per item; an image sits alone on its line.

## Screenshots: the contract with CI (release.android's, adopted)
Generated PNGs are **not committed**; CI renders them from the commit it builds (`scripts/release-screenshots.sh`, docs/ci.md).
- An article folder may hold `screenshots.txt`, one line per image: `<target>.png <- <source>.png`. `<source>` is a file the
  Robolectric tests write to `app/build/screenshots/<screen>-<device>-<orientation>-<theme>.png`: `ScreenshotTest`
  (`home-*`, `licences-*`) and this package's `ReleasesScreenshotTest` (`releases-list-*`, `releases-reader-*`; phone portrait
  and tablet10 landscape, light and dark). `<target>` is the name the article uses as `screenshots/<target>.png`.
  Blank lines and `#` comments are allowed; a listed source that does not exist fails CI.
- CI copies each target into `<folder>/screenshots/` before `assembleRelease` and `bundleRelease`.
- A local build without those copies is fine: a missing image is simply not drawn (the caption stays), and the list shows no thumbnail.
- Exception: the sample article's `sample-home.png` is committed (a downscaled home screenshot) because the render test needs
  an image that is always there. Real articles list theirs in `screenshots.txt` instead.

## Show once after an update (host hook)
`ReleasesNotice.shouldOffer(context, BuildConfig.VERSION_CODE)`: true when the versionCode is newer than the last one
seen (SharedPreferences file `feature_releases`) and at least one article exists. Opening the screen calls `markSeen`.
The host offers a button (`R.string.feature_releases_offer`) or opens route `releases`; it then stops by itself.
