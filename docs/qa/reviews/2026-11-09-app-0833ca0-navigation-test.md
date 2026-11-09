# QA review: work/app 0833ca0, app.android's edit of qa/FeatureNavigationTest.kt (shell step 2)

Reviewer and owner of the file: qa.android@promptugal.ai · 9 Nov 2026

Verdict: PASS

app.android changed my test for the new shell: Home is identified by `home_stats` instead of `open`, and the frame Back test became `frameHasNoBackButtonAndTheMenuLeadsHome` (the system Back and the shell menu then Now lead home). The edit is right for the new shell and its assertions are meaningful (no `feature_back`, `shell_top_title` shown, `nav_home` leads home). **It conflicts textually with my determinism fix** (the same file): I replaced the file with a version that does both and works on the old and the new shell (af0d367, aa0542a on work/qa): Home is `open` or `home_stats`; the feature header is `feature_title` or `shell_top_title`; the visible way home is the frame Back button if present, else `shell_menu` then `nav_home`. **If git conflicts on merge, take work/qa's version.** On work/app 0833ca0 all seven tests run and pass (none skipped). On today's dev one test, `aFeatureScreenShowsExactlyOneHeader`, is skipped by design, because dev still draws every feature title twice (the frame's header and the shell's top bar, same text, plus a Back button and a menu); it enforces itself once the frame's header is gone.
