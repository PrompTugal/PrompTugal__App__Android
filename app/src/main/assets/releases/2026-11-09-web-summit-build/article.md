---
title: "RiskMandate for Web Summit: ABP in every layer, this app's mandate, riskmandate.ai inside the app, stand mode, What's new"
date: 2026-11-09
summary: Five things are new in this build, made for a stand at Web Summit: a six-layer demonstration, a live view of this app's own reach and gap, riskmandate.ai inside the app, stand mode, and these release articles.
features: [site, abpdemo, mandate, releases]
---

# RiskMandate for Web Summit

This build adds five things, made to be used on a tablet at a stand. This article says what each one does and how to use it. It is written from the app's source code. Its author, an AI agent, has not run the app on a device, so check each step on your own tablet before the day.

The home screen now has a button for each of four features: **RiskMandate**, **ABP in every layer**, **This app's mandate** and **What's new**. The fifth, stand mode, has no button: it is a hidden switch (below).

![The home screen on a ten-inch tablet turned sideways.](screenshots/home-tablet.png)

## ABP in every layer

A demonstration, labelled as one on screen: "Not a live attack: nothing is sent and nothing is at risk." It walks one Agent Behaviour Policy (ABP) rule through six layers.

The scenario: an agent is asked to summarise a document. A planted line in the document tells it to send the company's agent map to an outside host. The ABP rule is "Agent map to an outside host: Block".

There are seven steps: the scenario, then the six layers in this order.

1. In the browser.
2. In the model.
3. As a hook.
4. As an inline proxy.
5. In the secure harness.
6. As the device policy.

Each layer step says what the layer does, the rule that fires, and how strong that layer is: Guidance, Soft, Firm, Strong or Floor. It also says plainly where a layer is weak: a page can be bypassed, and a model can be talked around. The secure harness note says an early version runs on Linux for a child process and that the launcher which would hold the agent itself is still a design. The wording comes from riskmandate.ai and sharness.ai.

**At the stand:** open ABP in every layer and press Auto-play. It moves on every seven seconds and loops from the last step to the first. Pressing Next or Back stops auto-play.

![Step 1 of 7: the scenario.](screenshots/abp-1-scenario.png)

![Step 2 of 7: the browser layer, marked as guidance only.](screenshots/abp-2-browser.png)

![Step 5 of 7: the inline proxy holds the request back.](screenshots/abp-5-proxy.png)

![The same step in the dark theme.](screenshots/abp-5-proxy-dark.png)

![Step 7 of 7: the device policy, the floor the other layers stand on.](screenshots/abp-7-device.png)

## This app's mandate

riskmandate.ai defines reach, mandate and gap, and this screen applies the three words to this app, on this device, now. The reach is read from Android when you open the screen. The gap is computed from the reach and the mandate, never written by hand.

- **Reach:** the permissions the app asks for and whether each is granted, the components other apps could start, whether cleartext http traffic is allowed, what storage the app uses, and device management.
- **Mandate:** what the app's policy allows: https only, port 443, on riskmandate.ai and www.riskmandate.ai. The allow-list is tried live on sample addresses, with each shown as allowed or refused. It also lists the managed configuration the app reads, and the reason for each piece of reach it allows.
- **Gap:** either "none", or a list of things the app can reach that its mandate does not need.

The definitions on screen are quoted in English from riskmandate.ai, read on 9 November 2026. Here the "agent" is this app. The screen shows a summary line with a count for each of reach, mandate and gap. The numbers on your device are what count.

## riskmandate.ai inside the app

The button called RiskMandate opens riskmandate.ai's pages inside the app, with native tabs: Home, What an ABP is, How it works, Pricing, SHarness and About. The in-app view is limited by the same address check as the rest of the app, so it opens only https pages on riskmandate.ai and www.riskmandate.ai.

For a stand with bad Wi-Fi the app holds an offline copy of the pages, read on 9 November 2026. It shows a banner saying so, with a Try online button. A page that is not in the copy says so and asks for a connection. The screen also tells you that riskmandate.ai has its own privacy policy and may set its own cookies, while this app collects nothing.

The screenshots below show the app's frame around the page: the tabs, the offline banner and the privacy line. They do not show the page itself.

![The RiskMandate screen on a phone: tabs, offline banner and privacy line, without the page.](screenshots/site-phone.png)

![The same screen on a ten-inch tablet turned sideways.](screenshots/site-tablet.png)

The words and prices on those pages are riskmandate.ai's, not this app's. The app adds nothing to what the site says. If the site has changed since 9 November, the offline copy is out of date until the next build.

## Stand mode

Stand mode is for a tablet left running on a stand. It is **off by default** and has no button. When it is on:

- the screen stays on;
- the screen is pinned, if Android allows it, which makes it harder for a visitor to leave the app. It deters; it does not lock the tablet, because Android's own unpin gesture still works (below);
- after a set number of idle minutes the app goes back to the home screen. The default is 3 minutes, and you can set 1 to 30.

**To switch it on or off:** on the home screen, **press and hold the bottom-right corner** (a square of about 48 density-independent pixels, inside the safe area). The settings panel opens. Use the "Stand mode on" switch, and Less and More for the idle minutes. When it is on, a faint dot sits in that corner.

Android asks once whether to pin the screen. If you refuse, or screen pinning is off in Settings, Security, the panel says "The screen is not pinned", and keep-awake and the idle reset still work. To unpin with Android's own gesture: with three-button navigation hold Back and Overview together; with gesture navigation swipe up and hold. If asked, enter the lock-screen PIN. Plan how you will get a tablet out of pinning before the day.

The settings are kept on the device only: stand mode on or off, and the idle minutes.

While the screen is pinned, the Open riskmandate.ai button on the home screen opens no browser. It shows the message "Web pages are not opened in a browser while the app is pinned." The RiskMandate button, which keeps riskmandate.ai inside the app, still works; links in it that leave riskmandate.ai are not opened while the screen is pinned.

## What's new

This section. Each release has an article like this one, bundled in the app, so it reads offline. Open What's new from the home screen and pick a release. On a tablet turned sideways the list and the article sit side by side. Portuguese (European) articles show on devices set to Portuguese.

![The list of releases on a phone.](screenshots/whatsnew-list.png)

![A release article beside the list, on a tablet turned sideways.](screenshots/whatsnew-reader.png)

## What is not here

- No accounts, analytics or advertising. The app still asks for only two permissions: internet, and network state.
- Stand mode, This app's mandate and the in-app RiskMandate view have no screenshots in this article yet; the build takes screenshots only for screens that have a screenshot test.
- Nothing here is a claim about RiskMandate beyond what riskmandate.ai says.

The build number is at the bottom of the home screen. Quote it when you report a problem.
