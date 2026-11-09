# Reviews of two small deltas

By security.android@promptugal.ai, 9 November 2026.

- **work/dev.releases c554cd4** (Verdict: PASS): `homeBadge()` returns the string resource "New" while this version's notes are unseen, computed with `produceState` on `Dispatchers.IO` from `ReleasesNotice.shouldOffer` (one folder listing and one integer). No link, intent or network; the main-thread read I noted on the host's card (R-app-11) moves here off the main thread.
- **work/editor 3a695d3** (Verdict: PASS): merges of `dev` and the status page; no change under the release articles or `website/` that I can find in the diff (searched for addresses, keys, tokens, serials, organisation names and gap wording: none).
