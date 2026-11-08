# Review risk: minimal functionality and website-wrapper apps

For Dinis. Written 8 November 2026 by play.android@promptugal.ai. All Google pages below were fetched on 8 November 2026. I read them through a fetch tool that returns excerpts; quotes are as returned, each with its URL. Google's pages show no revision date (only "©2026 Google"), so I cannot date the policy text itself. Nothing here is submitted. **This is a proposal: app.android builds, Dinis chooses.**

## 1. What Google's policy says

**Functionality, Content, and User Experience, section "Limited Functionality and Content"**
https://support.google.com/googleplay/android-developer/answer/9898783

> "We do not allow apps that only have limited functionality and content."

Examples of common violations on that page:

> "Apps that are static without app-specific functionalities, for example, text only or PDF file apps"
> "Apps with very little content and that do not provide an engaging user experience, for example, single wallpaper apps"
> "Apps that are designed to do nothing or have no function"

The page does not itself mention webviews, website wrappers or link-only apps.

**Spam, section "Webviews and Affiliate Spam"**
https://support.google.com/googleplay/android-developer/answer/9899034

> "We don't allow apps whose primary purpose is to drive affiliate traffic to a website" (the policy also covers apps that provide a webview of a website).

It lists two examples, the second being "Apps whose primary purpose is to provide a webview of a website without permission" (the page's wording for the permission clause is "provide a webview of a website without permission from the website owner or administrator"). Its screenshot example is an app that "simply provides a webview of Google Shopping".

Reading: the Spam clauses are about affiliate traffic and about a website the developer does not own or have permission for. riskmandate.ai is Dinis's own site, there are no affiliate links, and the app does not use a WebView (it opens a Custom Tab). **I assess those two clauses as not applying.** The risk is the first policy, limited functionality.

**Google's own blog on common rejections** (Android Developers Blog, 16 October 2020, "Developer tips and guides: common policy violations")
https://android-developers.googleblog.com/2020/10/developer-tips-and-guides-common-policy.html

> "Lastly, we observe a large volume of app submissions that are just webviews of existing websites."
> "Most of these apps are submitted with a primary purpose of driving traffic rather than providing engaging app experiences"
> "Such apps are considered webview spam, and are removed from Play."

and its advice: consider what users can do with the app that they cannot do better on the web, and build features that enrich the experience. This is a 2020 blog, not the policy, and it is six years old; the current policy pages above do not use the words "webview spam". It shows how Google's reviewers think.

## 2. Honest assessment of this app

What the app does today (`REALITY.md`, `HomeScreen.kt`): one screen, one button that opens riskmandate.ai in a Custom Tab, a Device section (managed or not, optional label), managed configuration (`start_url`, `device_label`), an open-source licences screen, the build number. No accounts, no data.

- It is a link launcher with a status panel. A reviewer comparing it with the examples above ("static", "very little content", "no engaging user experience") could reasonably flag it. The Google blog's test, "what can users do with the app that they can't do on the web", gets the answer "nothing" for an ordinary user.
- The managed-device features are real function, but only for an organisation that manages devices and sets the configuration. A reviewer testing on a normal phone sees none of it.
- Mitigating: the site is ours, there is no affiliate or deceptive intent, the listing text is honest (`listing.md`), and the app has no ads and no tracking.
- **Likelihood of rejection at public production: material.** I cannot give a probability; nobody can without submitting. **For internal testing and Firebase: low.** Google's help says "Internal tests might not be subject to standard Play policy or security reviews" (https://support.google.com/googleplay/android-developer/answer/9845334, read 8 November 2026), and Firebase App Distribution has no store review. M1 to M3 are not blocked by this. Whether a private organisation-restricted app gets the same policy review as a public one: not found in any page I read; treat as unknown.
- Selling makes it worse: a paid app that opens a free website is hard to defend (see `open-source.md`, "What is actually for sale").

Do not try to hide this in the listing; Google's Metadata and Misrepresentation policies punish that more than they punish a thin app.

## 3. Native features that would fit

Constraints I used: fits M1 to M3 (testers, managed tablets, other people's devices); stays within what riskmandate.ai says (the site: Agent Behaviour Policies, "what each one can reach, what you authorised it to do, and the gap between the two"; "Know their reach. Define their mandate. Control the gap."); needs no new permission; collects no data; Apache 2.0 compatible dependencies only. Order is my recommendation, strongest first.

| # | Feature | What the user can do that the web cannot | Fits | Data and permissions | Size | Notes and risks |
|---|---|---|---|---|---|---|
| 1 | **Device report for the people who run the devices.** A screen showing management mode, managed configuration as received (`start_url`, `device_label`, whether valid), app version, Android version, and "Copy report" / share sheet | An administrator checking a fleet sees at once whether the policy arrived and the app is current, with no browser | M2, M3 (devices we do not own: support) | Reads the same local state as today; copy/share is user-initiated. No new permission. Data safety unchanged (nothing is transmitted by the app) | S | The strongest fit for the managed tablets; still thin for an unmanaged user. Pairs with feature 2 |
| 2 | **Pinned pages from managed configuration.** Managed config gains a list of riskmandate.ai pages (title and https URL, same allow-list as `Links.kt`); the home screen shows them as native buttons | One tap to the pages an organisation chose, per device; a kiosk-like launcher without a kiosk (kiosk is out of scope until M1 to M3) | M2 | Local only; each URL validated by `Links.isAllowed` | S to M | Changes `app_restrictions.xml` (a bundle-array restriction) and `devices/` policy; devices.android must change the policy too |
| 3 | **Mandate worksheet (reach, mandate, gap), local only.** The user names an AI agent, ticks what it can reach and what it was authorised to do, and the app shows the gap. Export as text through the share sheet | A small offline tool that follows the site's own three-part idea | M3 (real users on own devices) | Stored on the device (needs the privacy policy and Data safety wording to say "stays on the device, never sent"; Google's definition of "collect" is transmission off the device, so local storage is not collection, but re-read `data-safety.md` first). No permission | M | **Product and claim risk, Dinis decides:** it must not suggest the app enforces anything or gives assurance; the site says RiskMandate is "never in the request path". All wording is Dinis's, taken from the site. The most "app-like" of the options |
| 4 | **Open from a link (Android App Links).** riskmandate.ai links in email or chat open in the app and then the Custom Tab | Convenience only | M3 | Needs `assetlinks.json` hosted on riskmandate.ai by Dinis | S | Does not add function; it routes to the same site. Skip unless the site wants it |
| 5 | **"Try it in five minutes" as native steps.** The site's five-minute path as a checklist the user ticks off | Guidance on the phone | M3 | Local only | S | The text is the site's; a static guide is exactly "text only" in Google's example. Weak on its own |
| 6 | **Update check against a version file** (the app reads a small JSON from our host) | Admin sees "newer build available" | M1 to M3 | **The app would make a network request**, so Data safety and the policy change (an IP address is sent). Not recommended without a decision | M | Would end the "sends nothing" statement. Firebase and Play already notify testers |

What I do not propose: an in-app WebView (it moves towards a "webview of a website", needs a new privacy review of what pages can do, and removes the browser's safety UI); anything with accounts, sign-in or analytics; kiosk mode or the local model (not before M1 to M3, per `CLAUDE.md`).

**Recommendation:** 1 and 2 together for M2 (they serve the tablets and are small, local and honest), and 3 only if Dinis wants the public app to stand alone, after he writes or approves its wording. Neither 1 nor 2 will, on its own, satisfy a reviewer on an ordinary phone; 3 might. If the answer is "the public app is not worth submitting yet", keep to Firebase and the private route for M1 to M3 and revisit.

## 4. If Play review still says "minimal functionality"

Answer truthfully through the Console appeal route (policy page: Managing Policy Violations and Appeals, https://support.google.com/googleplay/android-developer/answer/9899142, not read in full today), say what the app does for managed fleets, and do not change the listing to promise more. A rejection costs time, not the account, but repeated misrepresentation can.

## 5. For app.android

Items 1 and 2 would be app.android's to build once Dinis chooses; item 2 also needs a change to the policy in `devices/` (devices.android). Screens for the store (`store-assets.md`) should be taken after the feature set is chosen.
