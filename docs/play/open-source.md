# Selling an Apache 2.0 app on Google Play: what it means in practice

For Dinis, 8 November 2026. Everything here is a recommendation, not a decision, and none of it is legal advice: before the first sale a lawyer should read the licence, trademark and company choices. I read `LICENSE` (Apache License 2.0, in the repository root) and the build files; I read no legal source today.

## What the licence allows (Apache 2.0, from the licence text and general knowledge)

- You may sell the app. The licence does not forbid charging for copies, for the binary or for support.
- So may anyone else. Anyone can take the source, rebuild it, and distribute it, free or paid, on Play or elsewhere, keeping the licence and the notices, stating their changes (section 4). Nothing in the licence stops a fork from competing at a lower price, or at zero.
- The licence gives **no trademark rights** (section 6): a rebuilder may not use the name, logo or product names of the licensor except to describe where the code came from. This is the main protection for what is sold.
- The licence has a patent grant (section 3) from contributors, with a termination clause if someone sues over patents in the work.
- No warranty and no liability (sections 7 and 8). Selling a supported build is a separate promise from the licence.

## What is actually for sale

Not the code. The code is public. What a buyer gets, and a fork does not:

1. The signed build from the official Play listing, updated by us, installed through Play with Play's billing, refunds and update channel.
2. The name and icon (trademarks, below).
3. Support and the people: a role mailbox, replies, fixes.
4. Whatever the website riskmandate.ai sells that the app unlocks (today the app unlocks nothing: it opens the site).

Honest observation: today the app is one button that opens a free website. Nobody has a reason to pay for it. A paid app only makes sense once it holds something worth paying for that is not already free on riskmandate.ai. That is a product decision, not a licence problem; I would not set a price before that is decided. The site sells its policies at its own store, in pounds, once; putting the app on sale at the same time muddies what is being sold.

## Practical consequences

- **Others may rebuild and distribute.** Expect this to be legal. Plan for it: our listing must say who we are (the verified developer name), the official source link, and that other builds are not ours.
- **A fork cannot use our Play listing or our upload key.** The upload key and the Play account stay private; they are not in the repository (CLAUDE.md, `docs/lessons.md`). A fork that rebuilds gets a different signature, so it is a different app and cannot update ours.
- **Package name.** A fork may in theory ship `ai.riskmandate.app` elsewhere (APK sideload), but cannot publish it on Play because we own it there. Reasons to keep the ID ours: `README.md` step 8.
- **Nothing secret in the repository or its history.** Already the rule (`CLAUDE.md`, `.gitignore` covers `*.jks`, `*.keystore`, `google-services.json`, `*service-account*.json`). One leaked key in history is permanent; a public repository is scraped within minutes. Recommendation: add secret scanning (GitHub push protection) when the repository goes public.
- **Contributions.** If outside people may contribute, decide whether contributions come under Apache 2.0 as is (section 5 already says inbound contributions are under the licence unless stated) or under a short contributor agreement. The licence says nothing about assigning copyright; if a relicence is ever wanted (for example a commercial version), a contributor agreement now is far cheaper than asking every contributor later. Recommendation: no outside contributions until Dinis decides; then add a DCO sign-off (cheap) and think about a CLA only if relicensing is a real option.
- **Is the repository public today?** I do not know. The brief for this task says the app is open source; `README.md` and `CLAUDE.md` do not say the repository is public. TODO for Dinis.

## The NOTICE file

Apache 2.0 section 4(d): if the work has a NOTICE file, redistributors must carry its attribution notices. There is **no `NOTICE` file in the repository today**. That is allowed: it is optional. Recommendation: add one, short:

```
RiskMandate for Android
Copyright 2026 [TODO Dinis: the copyright holder, a company or Dinis Cruz]

This product includes software developed by third parties under the Apache License 2.0
(see THIRD-PARTY-LICENSES).
```

The copyright holder is Dinis's decision (it should be the entity that will sell). Add the same holder to `LICENSE`'s appendix line only if he wants the boilerplate notice in source files; not required.

## Trademarks

Recommendation: a short `TRADEMARKS.md` saying that "RiskMandate", "PrompTugal", the logo and icon are trademarks of [TODO Dinis: the holder], are not licensed under Apache 2.0, and that forks must use another name and icon. This is what `docs/lessons.md` already proposed ("a NOTICE/TRADEMARKS file says so once Dinis confirms").

- A statement alone gives little force; registered marks give more. Registration (EUIPO or the Portuguese INPI, and UK if selling there) is a company and legal decision with fees. TODO for Dinis: whether to register "RiskMandate" (and the icon).
- Google Play's Impersonation and Intellectual Property policies protect a listing from lookalikes if the trademark is clear. A registered mark helps in a dispute. I did not read those policies today.
- The icon: the real icon waits on the brand (`listing.md`). Keep the icon files in a separate folder from the code with a note that they are not under Apache 2.0, or keep them out of the public repository (then a fork must make its own).
- Careful with wording: "RiskMandate" is also the name of a website (riskmandate.ai) that Dinis runs; the app should not claim more than the site does.

## Dependency licences

Runtime dependencies in `app/build.gradle.kts` (all libraries from Google's AndroidX and Compose): `androidx.core`, `activity-compose`, `lifecycle-runtime-ktx`, `compose` (ui, material3, ui-tooling-preview), `androidx.browser`. These are, to my knowledge, Apache 2.0 (not checked against each artifact's POM today). Kotlin standard library: Apache 2.0. Compatible with Apache 2.0 and with sale.

Test-only (not shipped in the app): JUnit 4.13.2 (EPL 1.0), Robolectric 4.13 (MIT), AndroidX test. Not distributed in the AAB, so they place no obligation on what is sold.

Not in the app: the Google Play services and Firebase libraries (their licence is different and carries terms); the app has none.

Recommendation, as `docs/lessons.md` already says: CI should list every runtime dependency and licence (for example a Gradle licence report) and fail on a new licence outside an allowlist (Apache 2.0, MIT, BSD). Ship the list in the repository as `THIRD-PARTY-LICENSES`, and in the app's About screen if a notice-in-the-binary is wanted (Apache 2.0 requires giving recipients a copy of the licence; a copy in the source repository satisfies it for the source, the binary on Play counts as redistribution: the About screen or the listing's link to the source is the usual way). TODO dev.android: add the report and the About link, when Dinis agrees.

## Google Play side

- The Developer Distribution Agreement and Play policies bind what is sold on Play, not the licence of the source. Nothing in them forbids an open-source app.
- An open-source paid app is allowed; many exist. The risk is commercial (forks), not procedural.
- The licence you pick for a store listing text and graphics is separate: the listing is ours, not under Apache 2.0.

## Recommendation, as a list (for Dinis)

1. Keep Apache 2.0 for the code.
2. Decide the copyright holder, then add `NOTICE` and `TRADEMARKS.md`; keep name and icon out of the licence.
3. Decide whether to register the trademark before the first paid sale.
4. Do not price the app until it offers something the free site does not.
5. Add a dependency licence report to CI and `THIRD-PARTY-LICENSES` to the repository.
6. Turn on secret scanning and push protection the day the repository goes public.
7. No outside contributions until the contribution terms are decided.
8. Lawyer review of items 1 to 3 before the first sale.

## TODOs for Dinis from this file

- Copyright holder (and company that sells).
- Is the repository public, and when.
- Register the trademark or not.
- Contribution terms.
- Price: whether the app is sold at all, and what it unlocks.
