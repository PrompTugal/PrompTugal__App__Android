# pt.news on Android: the best route

For Dinis and the conductor. Written 10 November 2026 by play.android@promptugal.ai.dinis, on the restart brief (`PrompTugal__News__Website` `board/guidance/2026-11-10-the-restart.md`, read 10 Nov: "pt.news on Android: find the best way (the installed web app first; a Play listing if needed). Tell Dinis what he must do in Google."). **Docs only. I created no account, uploaded nothing, changed nothing in the news repository.** I could not test on an Android device or in Chrome from this session: everything below is from the repository (`ccd53a7`, site v0.1.942), the live site fetched on 10 November, and Google's pages read the same day.

## The answer

1. **Route 1, the installed web app (PWA), first and enough for the summit.** The site already has a manifest, a service worker with offline pages and, for the Web Summit app at `/app/`, an install button. Two small fixes make it more reliable (below). It needs nothing from Google and nothing from Dinis beyond a check on a real phone. **Ready in hours, not days.**
2. **Route 2, a Trusted Web Activity (TWA) in Google Play, only if a store presence is wanted.** It wraps the same PWA, so Route 1 must be good first. It needs a Play Console account, a package name, a signing key, an `assetlinks.json` file on the site and a Play review. **Realistically a week or more**, and not before the summit (account set-up alone can take days, and a personal account adds a 12-tester, 14-day test; see the table).
3. **For the team's tablets on Wednesday, a TWA does not need Play at all:** a TWA APK can go out through Firebase App Distribution like the RiskMandate build (same tester flow, `tester-guide-firebase.md`). Its fingerprint is the upload key's, not Google's; `assetlinks.json` can list both.
4. **One decision comes first: which host.** The site is served at **promptugal.news** (its `CNAME`). **pt.news** currently answers `200` with a one-line page that redirects to `/lander`, which looks like a parked or third-party domain; **I do not know who owns it.** A PWA is installed from, and a TWA is bound to, one exact host, and changing it later means a new install or an app update. Decide before building anything (Dinis, 10 minutes).

## What I checked

| Item | Finding | Source (read 10 Nov 2026) |
|---|---|---|
| Live site | `https://promptugal.news/` answers 200, version v0.1.941 on the fetch; `viewport-fit=cover`, light and dark `theme-color`, `apple-touch-icon`, favicons | curl of the live page |
| Manifest, site | `assets/offline/manifest.webmanifest`, served `application/manifest+json`: name "PrompTugal.news", short name "PT.news", `start_url` and `scope` the site root, `display: standalone`, icons 192 and 512 (`any` purpose only, PNG, RGBA); **no `id`, no maskable icon, no screenshots, no `lang`** | repository file and live fetch |
| Manifest link | **Not in the page's HTML.** `assets/offline/offline.js` (`start()`) adds `<link rel="manifest">` with JavaScript once the page loads. Chrome reads a manifest added this way, but crawlers, Lighthouse runs without scripts and tools such as Bubblewrap and PWABuilder read the raw HTML and may see no manifest | `curl` of the home page: no `rel="manifest"`; `offline.js` line 108 |
| Manifest, app | `assets/app/manifest.webmanifest` for `/app/`: "PrompTugal: the Web Summit app", `id` `../../app/`, scope `/app/`, standalone, same two icons. **Its link is in the HTML** of `/app/` (`app.tmpl` line 16). The page also listens for `beforeinstallprompt` and shows an Install button on phones, with an iPhone how-to when the browser offers no prompt | `assets/app/app.js` lines 146 to 152; live `/app/` |
| Service worker | `/sw.js` (200, JavaScript), made at each release from `assets/offline/sw.tmpl`: network first, three caches, an offline page listing what was kept, never stores other origins, queries or pages that use a key. A kill switch in `assets/offline/switch.json`. It has a `fetch` handler | `sw.tmpl`, `offline.mjs`, live `sw.js` |
| Offline | The worker keeps the front page, summit, days, agenda data, the app (`/app/`, its code and manifest) and the newest articles; a reader can save more. Tested by the site team (`board/passes/2026-11-06-offline-mode.md`); **I did not test it on a device** | `offline.mjs` `keep()` |
| Chrome's install rules | To fire `beforeinstallprompt` Chrome needs HTTPS, a manifest with `name` or `short_name`, icons including 192 and 512, `start_url`, a `display` of standalone or similar, no `prefer_related_applications: true`, and the user to have tapped the page and spent 30 seconds on it. The page does not mention a service-worker rule, so I cannot confirm one. **The site meets every listed manifest rule on paper.** | https://web.dev/articles/install-criteria |
| `assetlinks.json` | `https://promptugal.news/.well-known/assetlinks.json` answers **404** today | live fetch |
| Pages deploy | The deploy workflow assembles the site with `rsync` into `/tmp/site` (dot-folders are not excluded except `.git` and `.github`), then `actions/upload-pages-artifact@v3`. **That action's README says hidden files and directories (those starting with a dot) are left out unless `include-hidden-files` is set**; I could not confirm whether the pinned `@v3` has the input or the same default. `.well-known` starts with a dot | `.github/workflows/deploy-pages.yml` lines 100 to 111; https://github.com/actions/upload-pages-artifact |
| What a TWA is | The user's browser shows your PWA full screen, with no browser bar once verified; the host app cannot read the web content, cookies or storage. Needs Chrome on Android 72 or later. If the browser lacks TWA support, "Chrome will fall back to a simple toolbar using a Custom Tab". If verification fails the app opens the site as a Custom Tab with the browser bar | https://developer.chrome.com/docs/android/trusted-web-activity/overview and `.../quick-start` |
| Digital Asset Links | The file lives at `https://<host>/.well-known/assetlinks.json`, "the official name and location"; an example: `[{ "relation": ["delegate_permission/common.handle_all_urls"], "target": { "namespace": "android_app", "package_name": "...", "sha256_cert_fingerprints": ["..."] } }]`. Content type and redirect rules are not on that page; serve it as JSON with no redirect to be safe | https://developers.google.com/digital-asset-links/v1/getting-started |
| Building a TWA | Bubblewrap (Node.js 18 or later): `bubblewrap init --manifest=<manifest URL>`, `bubblewrap build` (writes a signed APK); PWABuilder is a GUI on the same library. The page does not say whether Bubblewrap also makes the AAB Play needs or the signing key; check its output | https://developer.chrome.com/docs/android/trusted-web-activity/quick-start; https://github.com/GoogleChromeLabs/bubblewrap |
| Play's fingerprint | With Play App Signing, Google signs what users get, so the fingerprint for `assetlinks.json` is Google's "app signing key", not yours. Google's page: Play Console, **Protected with Play, Play Store protection, Manage Play app signing**, "App signing key" section, "Copy the required fingerprints (SHA-1 or SHA-256)" and "Update your `assetlinks.json` file with these fingerprints". Console menus move; if the path differs, search the Console for "app signing" | https://support.google.com/googleplay/android-developer/answer/9842756 |
| Play policy | Nothing on TWA policy in Chrome's docs. Google's Spam policy (read 8 Nov) bars an app whose "primary purpose is to provide a webview of a website without permission" from the owner: we own the site, so that clause does not apply. The risk is the **Limited Functionality** policy ("static", "very little content", `minimal-functionality.md`): a TWA is a thin wrapper, so it needs the PWA's real features (offline, picking sessions, the graph) to be what a reviewer sees | `minimal-functionality.md` |

## Route 1: the installed web app (what to do and what to check)

**Today, on Android Chrome:** open the site, use the browser's menu, "Install app" (or "Add to Home screen"), open it from the launcher: it should open without the browser bar (`display: standalone`), start at the site root, and open pages offline that were visited. For the **Web Summit app**, open `/app/` and use its own Install button. **Both are separate installed apps on one host** (different manifest `id` and scope), so say which one the team should install: I suggest `/app/` for the summit and the site's own install for readers.

Not tested by me: Chrome on a real phone, Samsung Internet (it also installs PWAs; behaviour differs), Android versions before 8.

### What the site team must change (exact files, in `PrompTugal__News__Website`)

None of these blocks installing today; they make it reliable and nicer. I changed none of them.

1. **Put the manifest link in the HTML head, not only in JavaScript.** `newsroom/build/lib/shell.mjs`, next to the `apple-touch-icon` line (line 94): add `<link rel="manifest" href="${up}assets/offline/manifest.webmanifest">`. Then `assets/offline/offline.js` `start()` still skips adding a second one (it checks first). **Why:** tools that read raw HTML (Bubblewrap, PWABuilder, search, validators) see nothing today.
2. **Complete the site manifest**, `assets/offline/manifest.webmanifest`: add `"id": "../../"`, `"lang": "en-GB"` (or `pt-PT`), `"description"` (present), a `"categories"`, and a **maskable** icon: add entries for `assets/brand/icon-maskable-192.png` and `icon-maskable-512.png` with `"purpose": "maskable"`, kept apart from the `any` icons (Android crops icons to its shape; a non-maskable icon gets a white border or is shrunk). Two `screenshots` (one `form_factor: wide`, one narrow) add Chrome's richer install sheet. Also `"theme_color"`: the manifest has one light value while the page offers light and dark; Android uses the manifest's.
3. **New artwork, from design:** `assets/brand/icon-maskable-192.png` and `icon-maskable-512.png` (the mark inside the central 80 percent), plus the two screenshots. **I did not make or change any brand file.** The RiskMandate icon and its redraw note (`docs/design/brand/README-redraw.md`) show how design handled the safe zone for that app.
4. **Same checks for the app manifest**, `assets/app/manifest.webmanifest`: add maskable icons and a `"lang"`; it already has an `id`.
5. **Validation:** `newsroom/build/features/offline.mjs` `validate()` already checks that the icons in the manifest exist; extend it to check that a maskable icon is listed and that the shell links the manifest.

## Route 2: a Trusted Web Activity in Google Play

Do this only after Route 1 and the host decision. Nothing here has been started.

### What the site team must change for it

6. **Serve `/.well-known/assetlinks.json`.** Add a build feature `newsroom/build/features/assetlinks.mjs` modelled on `offline.mjs` `api()` (which already returns `[{ path: 'sw.js', schema: null, data }]`) returning `path: '.well-known/assetlinks.json'` with the JSON above. Content: `package_name` (the one Dinis picks), and **both** fingerprints once they exist: the Firebase/upload key's (for team builds) and Google's app signing key (for the Play build); the array may hold several.
7. **Make sure the deploy keeps it.** `.github/workflows/deploy-pages.yml`, the `actions/upload-pages-artifact@v3` step (line 109): add `include-hidden-files: true` under `with:`, **if the pinned version supports it** (else move to a version that does). **Then test:** after a deploy, `curl -i https://<host>/.well-known/assetlinks.json` must answer 200 `application/json` with no redirect. Until then, assume Pages drops it.
8. **A check in the gate:** `newsroom/build/validate.js` fails if `assetlinks.json` is present and not valid JSON, or lists no fingerprint, or the host in `CNAME` is not the one the TWA uses.
9. **The host.** `CNAME` is `promptugal.news`. If the host changes to pt.news, `CNAME`, the canonical-host check (`validate.js` item 4), the manifest `id` and the TWA's start host all change together.

### What the app side needs (a new Android project; the conductor decides where)

- A **separate package name** from `ai.riskmandate.app` (that ID is RiskMandate's and, per `README.md` step 8, must not be spent). Suggestion for Dinis to confirm: `news.promptugal.app` or `ai.promptugal.news` (the choice is permanent on Google Play).
- Bubblewrap project with the manifest URL, generated by `bubblewrap init`; a **new upload keystore** (not RiskMandate's), made by Dinis (`scripts/make-upload-key.sh` is the pattern), never in a repository.
- A separate repository or a `twa/` folder is the conductor's call. I suggest a separate repository so the two apps have separate histories and secrets.
- Android 8 or later is a safe minimum; Chrome 72 or later is needed on the phone.

### What Dinis must do in Google Play and GCP (step by step, with time)

Steps marked **(Dinis)** need a person; **(agent)** can be done by a session without secrets. The Play steps repeat `README.md` in short; read it for the detail.

| # | Step | Who | Time |
|---|---|---|---|
| 0 | Decide the **host** (promptugal.news or pt.news; if pt.news, confirm Dinis owns it and where it should point), the **package name** and **which Play account** will own the app | Dinis | 10 min |
| 1 | If there is no Play Console developer account: get a free D-U-N-S (organisation account) and register; US$25 once; identity check. Organisation accounts have no tester gate in the pages I read; **personal accounts need a closed test with 12 testers for 14 days** (`README.md` re-check, 8 Nov). The Play pages say registration can take up to 48 hours | Dinis | D-U-N-S: days (unknown, ask Dun and Bradstreet); registration: up to 48 h; effort 30 min |
| 2 | **Trader status declaration** in the Console (Manage account, Account details, About you), due by 1 Feb 2027; its details become public on 1 Mar 2027 (`README.md` re-check) | Dinis | 10 min |
| 3 | Create the app in Play Console: name, the package name from step 0, default language, free or paid (**a free app can never become paid**, `README.md`), contact email | Dinis | 15 min |
| 4 | Make the **upload key** on his own machine; keep it off every repository and off every chat | Dinis | 5 min |
| 5 | Build the TWA with Bubblewrap and sign it with the upload key (the build runs wherever the keystore can be given to it as a secret; agents never see it) | agent builds, Dinis supplies the key | 30 to 60 min first time |
| 6 | **First upload by hand** to **internal testing** (Test and release, Testing, Internal testing, Create new release; accept Play App Signing; upload the AAB; Save, Review, Start rollout). Internal tests "might not be subject to standard Play policy or security reviews" and a new bundle is available "within minutes" | Dinis | 20 min |
| 7 | Copy the **app signing key SHA-256** from the Console (Protected with Play, Play Store protection, Manage Play app signing, "App signing key"), and give it to the site team | Dinis | 5 min |
| 8 | Site team adds that fingerprint (and the upload key's, for Firebase builds) to `assetlinks.json`, deploys; **check with `curl`**, then install from the Play internal link and confirm the app opens **with no browser bar** (if there is a bar, verification failed: check the package name and fingerprint) | site team (agent), Dinis tests | deploy 10 to 30 min; check 10 min |
| 9 | Fill in the store listing (a text and an icon from design), a **privacy policy URL** (the site's own privacy page, which says what the site collects; I read it for RiskMandate's policy on 9 Nov and it is dated 24 September 2026), Data safety, content rating, target audience, ads, app access (`data-safety.md` has the method; the site may need its own answers because it has forms, a chat and a login for contributors) | Dinis, with a prepared draft from an agent | 1 to 2 h |
| 10 | Personal account only: closed test with 12 testers for 14 days, then apply for production access | Dinis and testers | 14+ days |
| 11 | Submit to production and wait for review. The Play help pages I read say a first app on a new account can take **up to seven days or longer** (README: "stated on the Publish your app page", not re-read today) | Dinis | up to 7 days or more |

**GCP is not needed for the first release.** A hand upload (step 6) uses only the Console. GCP matters only to automate later uploads: the Play Developer API, a service account invited in Play Console with the app-level permission "Release apps to testing tracks" and **no key**, because the organisation policy refuses service-account keys; the keyless route (Workload Identity Federation) is the one already used for Firebase (`docs/ci.md`), and the Play side of it is untested (`README.md` step 9). Effort for Dinis when it is wanted: about 30 to 40 minutes across Google Cloud and Play Console; a policy exception is the alternative.

## Risks and unknowns

- **I did not test on a device.** The install and offline behaviour are from code and docs. A first test on one phone (Route 1, 10 minutes) turns most of this into fact.
- **The host (pt.news)**: unknown owner; currently redirects to `/lander`. A TWA tied to a host that changes is a new app.
- **Hidden files in the Pages artifact:** the `.well-known` folder may be dropped; test after deploy.
- **Play review of a thin wrapper:** the Limited Functionality risk (`minimal-functionality.md`). The PWA's own features are the defence; a TWA that only shows the front page is the weak case.
- **Two PWAs on one host** (the site and `/app/`): fine in Chrome by `id`, but a TWA binds to one start URL; choose which one the store app starts at (I suggest `/app/` for the summit, the site for readers).
- **iPhone** has no TWA; the `/app/` page already explains Add to Home Screen. The brief says the news app is not going to the App Store now.
- **Brand and claims:** the store listing needs text from Dinis or the site (nothing invented here), and no claim for RiskMandate beyond what riskmandate.ai says applies to the RiskMandate app only.
