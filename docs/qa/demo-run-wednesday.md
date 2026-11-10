# Demo run for Wednesday: a checklist for a team member with an Android tablet

Written 10 November 2026 by qa.android@promptugal.ai.dinis, for a team member who will show RiskMandate on an Android tablet. It needs no computer, no adb and no developer knowledge. About 25 minutes to prepare (most of it a model download, done the day before), then each demo takes 2 to 5 minutes.

## How to read this

Every screen name and button below is a real label in the app on `dev` as of this writing, except where a line says **[lands today: confirm]**. Chat, ABP case studies, on-device answers and parity with iOS are still landing. For those I give what has to be true and how to check it, and I mark the exact labels to confirm on the build you install. I have not run any of this on a device (no device here); the first rehearsal is the real test. If a screen differs from this page, trust the screen and tell the conductor by message.

Never claim more than riskmandate.ai and sharness.ai say. The fallback wording in each demo stays inside that.

## 0. The day before (do not skip)

1. Install the newest build from **App Tester** (see `docs/play/tester-guide-firebase.md`). Use the Google account that was invited. The home screen shows **Build 0.1.<number>** at the bottom: write the number down. It must be the build the conductor named for Wednesday; if it is older, do not demo from it.
2. Charge the tablet to at least 80%, and plan to keep it plugged in during the demo.
3. Turn off auto-rotate unless you want it: the ABP walk-through is made for **landscape**, held steady.
4. Set the screen timeout to 30 minutes (Settings, Display) so it does not sleep mid-demo. Turn **Do not disturb** on so no notification covers the screen.
5. **The model (only if you will show Chat or on-device answers).** Open the side menu, **Diagnostics**, **Preview**, **Model setup**. Download the smaller model with the screen open and on the same Wi-Fi you will use for rehearsal; keep the tablet awake until it says it is installed. Follow `docs/qa/device-run-idea-tab-plus.md` parts 1 and 2 if anything is unclear. If the download stops, tap resume; it keeps what it has.
6. **Rehearse once, fully, on the venue's network or with Wi-Fi off.** A demo you have not run on this tablet will fail in front of people.
7. Airplane mode check for the on-device claims: with Airplane mode on, Chat (below) must still answer. Turn it back on or off to suit the demo.

## 1. Home (30 seconds, every time)

Steps: open RiskMandate. Show the Now page: the stats row and the feature cards (**ABP in every layer**, the live mandate card, the site, release notes, and whatever else the build lists).

Expected: a card for each enabled feature; a **Preview** pill on any unfinished feature; no error message.

If it fails: force-stop the app (long-press the icon, App info, Force stop) and reopen. If the cards are missing, the build is wrong: say "this is a work in progress build", and go to the demo that works.

## 2. ABP in every layer (the safest demo; 3 minutes)

Steps:
1. Hold the tablet in landscape. Tap the **ABP in every layer** card (label: *Demonstration*).
2. A red banner says it is a demonstration, not a live attack. Leave it on screen; it cannot be dismissed.
3. Tap **Next** through the steps: the scenario first (**The scenario**), then the six layers one by one. Each step shows what that layer does, the rule that fires, and a five-notch strength meter with a word.
4. Optionally tap **Auto-play** (7 seconds a step, loops). Tapping Next or Back stops it. The screen stays awake while it plays.
5. Tap **Back** or use the system Back to leave.

What to say (all of it is on riskmandate.ai or sharness.ai): a line in the prompt is *told*, and the model keeps it because it chooses to; a hook is *checked*, but it runs on the agent's own machine; a proxy or gateway *holds* it from outside, configured from the rules in the ABP; SHarness is a policy the kernel enforces, and a tricked agent cannot widen it. Say the layer-1 (browser) and layer-6 (device) steps are **illustrations of the idea**, not products you are showing running.

If it fails:
- Layout looks wrong (one column, stacked buttons): the window is not wide enough or text is enlarged; rotate to landscape and set Font size to default.
- Auto-play does not advance: tap Next by hand; say so, and carry on.
- Never say "this is a live attack" or "this tablet is being protected right now". It is a walk-through.

## 3. The mandate card (2 minutes)

Steps: from Home, tap the card whose text starts "Live on this device". It shows this app's own reach, mandate and gap, read from the device as you open it.

Say: this is the idea of reach versus mandate, applied to the app itself. Only claim what the screen shows.

If it fails: go back and reopen once. If the numbers look odd, say "this is a first version" and move on; note the build number and what you saw.

## 4. Chat: a model on this tablet **[lands today: confirm]**

Real Chat is landing today. It is switched off in builds before that (it lives under Diagnostics, Preview). Confirm on the Wednesday build that **Chat** is a card on Home (kicker *On device*); if it still sits under Preview, tell the conductor and demo from Preview, saying it is not finished.

Steps:
1. Tap **Chat**. The badge must read **Runs on this device**. If it reads **Cloud: openrouter.ai**, the cloud switch is on: **stop and fix it before any demo that says "on this device"** (see section 8).
2. Check the line **Model: <name>**. If you see *No model installed*, tap **Open Models** and install one (section 0.5), then return.
3. Tap a preset: **What is RiskMandate?** or **Explain an ABP**. Watch it type. When it ends, a line shows tokens per second, seconds and tokens.
4. Show **Stop** mid-answer (it says *Stopped*), **New chat**, then **History** (*Chats are saved on this tablet only. They are not backed up, sent or shared.*).
5. For the privacy point: turn on **Airplane mode**, send another message, and show it still answers. Turn it back as needed.

Expected: answers appear within a few seconds of the first word; the **Network: off** line is shown while the chat runs on the device.

What to say: the model runs on this tablet and the chat stays on it. Do **not** quote a speed or say it is as good as a big cloud model; read the numbers on screen if asked.

If it fails:
- *The model could not be loaded*: close other apps, retry once, then pick the smaller model in Models.
- *This chat is too long for the model*: **New chat**.
- It is slow on the first answer (it loads the model first): say that, wait, then ask the second question for the real speed.
- If answers are wrong or odd, say "this is a small model on a tablet" and show the preset ABP answer rather than an open question. Do not demo free-text questions you have not rehearsed.

## 5. ABP case studies: the Lab, side by side **[labels from dev.lab, 84775bf on work/dev.lab, not on dev yet: confirm on the build]**

On Home: the **Lab** card, kicker *Side by side* (pt-PT *Lado a lado*). No model is needed: these are replays.

Steps:
1. Open the Lab. The scenario picker has two sets: **Case studies** (*Casos de estudo*) and **Samples** (*Amostras*). Pick from **Case studies**. The six titles:
   - Outreach drafts, a person sends
   - Security stops work, never restarts it
   - A message is data, not an instruction
   - Dev pushes to dev, not to main
   - Researcher stays out of members-only pages
   - Journalist gives each claim its source
2. Read the description under the title. It quotes the real policy in English between curly quotes, then says the team runs its policies in observe mode and the tools are fakes. Read that last part aloud.
3. Tap **Run** (*Executar*).

Expected on the result:
- Two chips: **Replay of a recorded run** and **Case study: written to show the policy, not a model run**. The *sample* chip must **never** appear on a case study.
- Two lanes: **Without ABP** and **Through the ABP**. On the raw lane the move runs (**Ran (fake)**). On the ABP lane the same step shows **Blocked** with the rule's reason in plain words. The journalist case shows a **flag**, not a block.
- A summary: "Without the ABP the model went outside the policy at step N" and "Through the ABP it was stopped at step N", with the **same N**.
- In pt-PT the title, description, model lines and reasons are Portuguese; the policy quote and the tool data (fake results, the journalist's claim lines) stay English. That is correct, not a bug.
- Rotating the tablet keeps the chosen case study. On a narrow screen the picker scrolls sideways.

What to say: the case studies are **written to show the policy**, they are not a model misbehaving on camera; the tools are fakes, nothing real is touched; a hook or an ABP *checks*, a prompt line only *tells* (riskmandate.ai). Never present a case study as a recorded attack on a real system.

If it fails:
- The sample chip appears on a case study, or the two N differ: stop using that case study, tell the conductor which one, and show a different one.
- The picker shows no **Case studies** set: the build is older than 84775bf; fall back to section 2.
- Run does nothing: go Back and open the Lab again once; then section 2.

## 6. On-device answers **[lands today: confirm]**

The idea: an answer produced on this tablet, grounded in RiskMandate's own texts, with no network. The Chat presets already do this for *What is RiskMandate?* and *Explain an ABP*. Check on the Wednesday build:
1. With **Airplane mode** on, the answer is produced and is about RiskMandate (not a generic answer).
2. The badge says **Runs on this device**, never a cloud label.
3. Any link in an answer (**Open <host>**) only opens riskmandate.ai, and only when you tap it.

What to say: "this answer was written on the tablet; nothing was sent". Do not add a claim about accuracy or compliance.

If it fails: show the same question once more; if still wrong, move to section 2 and say you will follow up.

## 6b. "Our data": ask the bundled pt.news and RiskMandate data, offline **[labels from dev.llm, d67105b: confirm on the build]**

This is tab 4 of the **Workbench** (tab name **Our data**, pt-PT **Os nossos dados**). The Workbench stays off the home screen until it is verified on the tablet, so open it from **Diagnostics, Preview, Workbench**. It is a staff screen: show it, do not leave it running at a table.

What the screen has (English labels as dev.llm gave them; check each on the build):
- Card **Ask our data**, kicker *On this device, offline*, chip **No network**. A line says what the bundle holds ("45 articles and 120 company cards from pt.news, in 553 passages.").
- Field **Question**; chips **2 sources**, **4 sources** (the default), **6 sources**; buttons **Ask**, **Stop**, **Run the 24-question eval**.
- No model installed: *No model on this device yet. Set one up first.* and a **Change model** button. Install one first (section 0.5).
- Answer card **Answer** (kicker *On this device*) with the figures **Time to first token**, **Search**, **Total, load included**, **Decode speed**. Notes when they apply: *This answer cites no source.* or *Cited sources that do not exist: N.*
- **Sources given to the model**: each line "[n] title", a **Cited** chip when the answer cites it, then the kind (pt.news article, pt.news company card or RiskMandate), the source and the date. If nothing matched: *Nothing in the bundled data matched the question.*
- **Eval** card: "X of 24 questions: sources found A, answers right B, median time to first token T ms.", one line per question, and **Copy report**.

Checks to run in rehearsal (from dev.llm):
1. **Airplane mode on.** Ask *Where is Portugal's stand at Web Summit?* The answer cites [1] "Where to find Portugal at Web Summit", and that source shows the **Cited** chip.
2. Ask *What is the capital of Australia?* The answer says it does not know from our data, and cites nothing (*This answer cites no source.*). If it answers from general knowledge, say nothing about it on the day and tell the conductor.
3. On the Run tab choose **Cloud**; then open **Our data** again. It must still say *On this device* and **No network**. If not, do not demo it.
4. **Run the 24-question eval**, tap **Copy report**, and send it to dev.llm by message (it has no keys or personal data; check before sending).

What to say: the answer is produced on this tablet from a bundled set of pt.news articles and company cards and RiskMandate's own texts, and it shows which passages it was given. Do not say it is always right, and do not quote the eval score as a quality claim; read it out as what it is ("X of 24 on this test").

If it fails: *Nothing in the bundled data matched*: rephrase using words from an article title. Slow first answer: it is loading the model; ask again. Stop and say "this is a staff test screen" if it errors; go to section 2.

## 7. Parity with iOS **[lands today: confirm]**

Only claim parity for what you have seen side by side. Before the demo, open the same screen on the iPad (if one is present) and the tablet and compare: the same wording, the same order of steps, the same banner. Anything that differs, you do not call "the same". If asked which is ahead, say "the iPad app and this one are built from the same texts; some screens are ahead on one or the other", and look at the build numbers.

## 8. The cloud switch (check before every "on this device" demo)

If the build has an optional cloud mode for Chat (a key kept on the tablet), it must be **off** for the demo. The giveaway is the Chat badge: **Runs on this device** (good) versus **Cloud: openrouter.ai** (stop). While the cloud is on, statements like "nothing leaves the tablet" are false. Fix: Diagnostics, Preview, the cloud key screen, switch off; or ask the conductor. See `docs/qa/device-run-cloud-key.md`.

## 9. Stand mode (leaving the tablet running at a table)

Long-press the bottom-right corner of Home for stand mode. It resets to Home after idle. Anything staff-only is hidden in stand mode. In stand mode do **not** leave the app on Chat if visitors will type: history is kept on the tablet. Use the ABP walk-through with Auto-play instead.

## 10. If everything fails

1. Say: "this is an early build; let me show you the website", then open the site card (**riskmandate.ai**, with an offline copy).
2. Have riskmandate.ai and sharness.ai open on a phone as a backup.
3. After the demo, write down: build number, tablet model and Android version, which demo, what you did, what you saw. Send it to the conductor by message. Never send a password, a key or a screenshot of an account page.

## Quick card to keep in your pocket

| Demo | Open | Say | If it breaks |
|---|---|---|---|
| ABP in every layer | Home card, landscape | told, checked, held; a walk-through, not a live attack | Next by hand |
| Mandate | "Live on this device" card | reach versus mandate, of this app | reopen once, move on |
| Chat | Chat, badge **Runs on this device** | runs and stays on the tablet | New chat; smaller model; show the preset |
| Our data (Workbench tab 4) | Diagnostics, Preview, Workbench, Our data | answered on the tablet from bundled data, with its sources | rephrase; show ABP in every layer |
| Case studies, on-device answers, iOS parity | confirm on the build | only what the screen and the sites say | back to ABP in every layer |
| Anything | Home | nothing beyond riskmandate.ai and sharness.ai | the website |
