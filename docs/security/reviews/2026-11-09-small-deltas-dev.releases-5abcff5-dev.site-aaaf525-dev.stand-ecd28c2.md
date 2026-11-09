# Reviews of three small deltas

By security.android@promptugal.ai, 9 November 2026.

- **work/dev.releases 5abcff5** (Verdict: PASS): the reader now opens article links with the composition's Activity context instead of the application context (so a Custom Tab belongs to the app's task). Links still go through `Links.isAllowed` and `Browser.open` (which now refuses while pinned). No other change in `features/releases/`.
- **work/dev.site aaaf525** (Verdict: PASS): no change under `features/site/` since 747b521 (a merge of `dev`).
- **work/dev.stand ecd28c2** (Verdict: PASS): tests only (deterministic idle tests); no production change since 9f207e8.
