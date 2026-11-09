# Draft store listing: RiskMandate (ai.riskmandate.app)

Draft for Dinis, 8 November 2026. Nothing is submitted. Limits (verified in Play Console Help on 8 November 2026): title 30 characters or less; short description 80 characters; full description 4000 characters (that limit from memory, not on the page read). No text or image in the title, icon or developer name that suggests ranking, price or promotion; no "Best", "#1", "New", "Sale".

## What riskmandate.ai itself says (fetched 8 November 2026, site version v1.40.3)

Quoted from https://riskmandate.ai/ (the only claims used below):

- Page title: "RiskMandate: Give agents access. Not free rein." (the site separates the two parts with a dash)
- "Agent Behaviour Policies for the AI agents in Gmail, HR, payroll, CRM and meeting apps: what each one can reach, what you authorised it to do, and the gap between the two. Enforce them with the controls you already run, or with SHarness, the open-source secure harness we maintain. We are never in the request path."
- "Know their reach. Define their mandate. Control the gap."
- "Try it in five minutes ... On Claude or ChatGPT, in your browser, nothing to install."
- "RiskMandate writes the rules and is never in the request path."
- Pricing section: four levels, "£10, one price, once", "£50", "£500", "£1,500"; "Prices are in pounds, per agent, per deployment, paid once at the store; no subscription."
- Privacy page (https://riskmandate.ai/privacy, 24 September 2026): "Nothing on riskmandate.ai is measured, tracked or stored about you. There is no analytics, no tag manager, no advertising pixel and no cookie set by this site."

Not used, on purpose: the insurance and "Licence to Operate" pages (linked from the site index; I did not read them, and a store claim about insurability is exactly the kind Dinis must approve), the SHarness claims (the site marks parts as "design, not built"), and the prices (Play forbids price text in the title, icon and graphics; keep prices out of the listing too unless Dinis decides).

## What the app does (verified in the code)

- One screen, titled RiskMandate, with a button "Open riskmandate.ai" (`app/src/main/java/ai/riskmandate/app/HomeScreen.kt`) that opens the site in a Custom Tab, or a browser if none supports Custom Tabs (`Browser.kt`). It only opens HTTPS pages on riskmandate.ai or www.riskmandate.ai (`Links.kt`).
- A "Device" section says whether the device is managed by an organisation (fully managed, work profile, or not detected) and shows an optional device label (`DeviceStatus.kt`).
- An organisation's device management can set the page the app opens (`start_url`) and a device label (`device_label`) as managed configuration (`ManagedConfig.kt`, `app/src/main/res/xml/app_restrictions.xml`).
- Shows the build version. A Diagnostics screen (`DiagnosticsScreen.kt`) lists version, Android version, device model, management state, managed configuration and installer, with a Copy button (clipboard only on tap). Needs INTERNET and ACCESS_NETWORK_STATE only. No accounts, no data collected.

## Title (max 30)

`RiskMandate` (11 characters)

## Short description (max 80)

Option A (plain, nothing beyond the code): `Open riskmandate.ai from your phone or tablet.` (46 characters)

Option B (uses one riskmandate.ai claim; TODO for Dinis to approve): `Know their reach. Define their mandate. Control the gap.` (55 characters). The line is the site's own. It describes the website's idea, not what the app does, so Option B is only honest if the full description says the app opens the site. Recommendation: A for the first listing.

## Full description (max 4000; this draft is about 1,600)

```
RiskMandate: Give agents access. Not free rein.

This app opens riskmandate.ai, the RiskMandate website, in your browser. It is a small app: one button, and a few facts about the device it runs on.

What riskmandate.ai says RiskMandate is: Agent Behaviour Policies for the AI agents in Gmail, HR, payroll, CRM and meeting apps: what each one can reach, what you authorised it to do, and the gap between the two. RiskMandate writes the rules and is never in the request path. [TODO Dinis: approve every sentence of this paragraph; each is copied from riskmandate.ai]

What the app does
- Opens riskmandate.ai in a Custom Tab (your browser), only on riskmandate.ai over HTTPS.
- Shows whether your device is managed by an organisation, and an optional label your organisation sets, so that the people who run your devices can tell them apart.
- Lets your organisation choose which riskmandate.ai page the app opens, through managed configuration.
- Shows riskmandate.ai inside the app as well, with an offline copy of the main pages, and has an optional stand mode for a tablet on a stand (keeps the screen on, returns to the start screen after idle time). [TODO: confirm both are in the build that is submitted]
- Has a Diagnostics screen with the app version, device model, Android version and management state, which you can copy to share with support. Nothing is sent by the app.

What the app does not do
- It has no account and no sign-in.
- It has no analytics, shows no ads and does not ask for your personal data.
- It keeps a few settings on your device (stand mode, What's new) and never sends them. The in-app view loads riskmandate.ai the way a browser does. [TODO Dinis: check against the Data safety decision, `data-safety.md`, "R-stand-2"]
- It asks for two ordinary permissions: internet access and network state (to show an offline hint).

The app is open source under the Apache 2.0 licence. [TODO Dinis: add the repository URL once he decides it is public]

Publisher: [TODO Dinis: the company name]. Support: [TODO Dinis: a role email address].
```

Check before submission (TODO for Dinis): "your browser" is accurate only while a browser is installed; the app says so on screen if none is. The "organisations choose the page" sentence is true only for managed devices (M2 is not yet run on a real device; `REALITY.md`).

## Claim slots that need Dinis (all marked TODO above)

1. The paragraph about what RiskMandate is: kept to the site's own words; Dinis approves, or the paragraph is cut.
2. Whether the listing names SHarness, pricing or insurance: not in this draft.
3. The source code link and the licence line.
4. The publisher name and the support email.
5. Whether the listing mentions "managed devices" (accurate, but it invites the question of what an organisation gets that an individual does not; the answer today is nothing but the label and start page).

## Localisation

None in the first listing (English only). The site is English; Portuguese is a later decision.

## Graphics needed (specifications verified on 8 November 2026 from Play Console Help, "Add preview assets")

| Asset | Spec | State |
|---|---|---|
| App icon | 32-bit PNG with alpha, 512 x 512 px, up to 1024 KB, meets Google's icon design specifications; no badges or text suggesting ranking, price or categories | **Waits on the brand.** Today the app has a placeholder launcher icon (`app/src/main/res/drawable/ic_launcher_foreground.xml`, adaptive icon in `mipmap-anydpi-v26/ic_launcher.xml`). TODO designer@ and Dinis: the RiskMandate "RM" mark from riskmandate.ai is the candidate, if Dinis confirms it may be used as the app icon. |
| Feature graphic | JPEG or 24-bit PNG (no alpha), 1024 x 500 px; required to publish; avoid prominent branding that is similar to the icon; no ranking, price or call-to-action text | TODO designer@ |
| Phone screenshots | Up to 8; Google's page lists phone rules I did not read in full; use 9:16 portrait, at least 2 (from memory, verify in Console); no device frames needed | TODO: take from a real device or emulator: (1) the home screen unmanaged, (2) the Device section showing a managed device with a label, (3) riskmandate.ai open in the Custom Tab |
| 7-inch tablet screenshots | Up to 8; for tablets and Chromebooks a minimum of 4; each side between 1,080 and 7,680 px; 16:9 landscape or 9:16 portrait | TODO: needed because the managed tablets (M2) are the main devices; 4 screenshots as above plus one with a long label |
| 10-inch tablet screenshots | Same rule as the 7-inch row | TODO: same 4 |

Screenshots must show the real app and may not carry text that is not part of the app experience (page says it "can get cut off"). The app declares no tablet-only restriction in the manifest, so it is offered to phones and tablets alike; large-screen quality is checked in the Console under the app quality checklist. I have not verified how the single screen looks on a 10-inch tablet: it is a centred column (`HomeScreen.kt`), so it will look sparse.

## Category and tags (TODO for Dinis)

Suggested category: Business or Tools. Not a decision I can make. Contact details shown on the listing: email (required), website `https://riskmandate.ai/`, optionally phone. Privacy policy URL: see `privacy-policy.md`.
