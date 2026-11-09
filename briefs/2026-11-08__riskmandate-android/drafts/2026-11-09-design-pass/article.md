---
title: "A new look: the PrompTugal design pass, in RiskMandate green"
date: 2026-11-09
summary: The app gets the look of the PrompTugal apps: warm paper, cards, serif headlines, a green accent and a navy-leaning dark mode, a redrawn RM mark, and a new frame with a sidebar and a Now page.
features: [releases, mandate, site, abpdemo]
---

# A new look

This build changes how the app looks and is laid out. Nothing in it adds a new capability for RiskMandate, and nothing here says anything about RiskMandate beyond what riskmandate.ai says. This article is written from the design documents and the source code. Its author, an AI agent, has not run the app on a device, so check each point on your own tablet.

## The look

The app now follows the look of the PrompTugal apps: warm paper for the background, cards with a thin rule and rounded corners, serif headlines, and a small uppercase label (a kicker) above each heading. Dinis Cruz chose variant A of two proposals.

- **Accent:** RiskMandate green marks the main button, the selected item and the kickers. Red is kept for things that are blocked and for errors.
- **Dark mode:** a navy-leaning dark theme. Light and dark follow the device setting.
- **Contrast:** the design notes record contrast measured with the WCAG formula for the text and accent colours.

The home screen as the screenshot test draws it, light and dark:

![The home screen on a ten-inch tablet turned sideways, light theme.](screenshots/home-tablet-light.png)

![The same home screen, dark theme.](screenshots/home-tablet-dark.png)

## The RM mark

The RM monogram was redrawn as vector images from the logo that Dinis Cruz supplied: a white RM with two corner brackets on a navy square. It is hand-drawn from a small reference image, so letter shapes differ in detail from the original logo. A proper vector from whoever drew the logo would replace it. The new launcher icon and the start-up splash come with the new frame below.

## Every feature gets a card

Each feature now has an icon, a kicker and a one-line summary, used on the home page and in the sidebar. For example:

- **RiskMandate:** "riskmandate.ai". The riskmandate.ai pages inside the app, with an offline copy for bad Wi-Fi.
- **ABP in every layer:** "Demonstration". One Agent Behaviour Policy kept at six layers, not a live attack.
- **This app's mandate:** "Live on this device". This app's own reach, mandate and gap, read from the device as you open it.
- **What's new:** "Release notes". One article per version, with screenshots of what changed.

## The new frame

This part arrives with the app's new shell. It is described from the design spec, so check it against your build.

- **Tablets (landscape, or 840 dp wide or more):** a permanent sidebar on the left. It holds the brand block, then one row per feature, in sections. It can collapse to a narrow icon rail, and the app remembers that on the device.
- **Stand mode:** the sidebar starts collapsed, so the content fills the screen. Staff can open it.
- **Phones and portrait:** the same panel as a drawer, opened by a labelled menu button.
- **Now:** the home page becomes a page of cards: three tiles (features on, the permissions this app asks for, anything that needs a look), then one card per feature.

The pictures below are renders of that design, drawn by the screenshot tests from sample content. They are not captures of the running app.

![The design of the frame on a tablet, light theme: the sidebar and the Now page (a render from sample content).](screenshots/frame-light.png)

![The same design, dark theme (a render from sample content).](screenshots/frame-dark.png)

![The Now page on a phone (a render from sample content).](screenshots/now-phone.png)

## The mandate screen reads more

This app's mandate looks at more of what the app keeps on the device: the app's no-backup storage and any files on shared storage. Its list of reasons now covers the frame's one display preference (sidebar collapsed or not), the number of the last update note you were shown, and, in builds that have those features, chat history and language model files. It lists each as kept on this device only and never backed up.

## The privacy texts

The privacy policy and the Data safety answers were updated to list every item the app stores: the layout preference, the number of the last update note shown, and, where the features are present, chat history and model files, with how to delete them. The app collects no personal data and sends nothing to its makers. The in-app riskmandate.ai view loads pages from riskmandate.ai the way a browser does, and the model setup downloads a file from Hugging Face only when you tap Download. See the privacy policy for the details. The app still asks for only two permissions: internet and network state.

The build number is at the bottom of the home screen. Quote it when you report a problem.
