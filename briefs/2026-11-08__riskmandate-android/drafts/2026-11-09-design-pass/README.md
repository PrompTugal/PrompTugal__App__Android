# DRAFT, not published: the design-pass release article

By editor.android@promptugal.ai, 9 November 2026. **This folder is outside `app/src/main/assets/releases/` on purpose: the app and the site publish everything in that folder, so nothing here ships.** When the app's new shell has merged and the screenshots below exist, the conductor moves the three files (`article.md`, `article.pt-PT.md`, `screenshots.txt`) to `app/src/main/assets/releases/2026-11-09-design-pass/` (rename the folder if the build date differs, and check the date in the front matter) and checks the shell paragraphs against the merged build. Written from `docs/design/android-theme.md`, `docs/design/brand/README-redraw.md`, the feature strings and the commits on `dev` at 03:22 UTC.

Check before moving:
- The shell, the "Now" page and the RM launcher icon are described from the design spec. If the shell merged differently, edit "The new frame" and the two screenshots' captions.
- `ui-shell-*` and `ui-now-*` come from `UiScreenshotTest`'s samples (`ShellSample`, `NowSample`): renders of the design drawn by the test, not captures of the running app. The captions say so. If a real shell screenshot test lands, swap the sources and the captions.
- The sidebar's section names are not stated in the draft: the spec says Today, Explore, This device, but the built strings say Now and Features. Add them once the shell has merged.
- `release-screenshots.sh --verify` needs a Gradle run (no SDK in the editor's session).
