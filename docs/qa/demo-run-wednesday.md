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

## 4. Chat: a model on this tablet **[labels from dev.chat, 0cf6aec and later: confirm on the build]**

Where: the **Chat** card on Home and the sidebar, under **Demos**. It is **hidden for visitors in stand mode** (section 9), so you show it, a visitor does not run it. If the build still has Chat under Diagnostics, Preview, tell the conductor and demo from there, saying it is not finished.

Steps:
1. Tap **Chat**. Look at the chips at the top. Exactly one source chip must show: **Runs on this device** (good) or **Cloud: openrouter.ai**. Beside it, **Network: on** or **Network: off** shows the real state. If it says **Cloud: openrouter.ai**, **stop**: do not say "on this device" (section 8).
2. **Model: <name>** is shown read-only. If you see *No model installed* and a button **Open Models**, install one first (section 0.5).
3. On an empty chat tap a preset: **What is RiskMandate?** or **Explain an ABP** (the answers use bundled text from riskmandate.ai). The answer streams. Its label reads **On device, <model>**. Under the last answer: "N tokens per second, S seconds, T tokens".
4. **Stop** mid-answer: the text so far stays and **Stopped** shows. **New chat**. **History** lists saved chats (Back steps out of it) and says: "Saved chats stay on this tablet only: they are not backed up or shared. A message goes to the cloud only when the cloud is selected for it." Read that line as it is; do not paraphrase it. **Clear history** asks "Delete every saved chat?" with **Delete** / **Cancel**.
5. For the offline point: turn on **Airplane mode**; **Network: off** shows; send another message; it still answers.

Seven checks to run in rehearsal (from dev.chat):
1. A preset streams, labelled On device, with the stats line; any markup in the answer (`<b>`, `**bold**`) shows as plain words.
2. Stop mid-answer keeps the text and shows **Stopped**.
3. Rotate mid-chat and mid-answer: the same chat is open; a half answer comes back marked **Stopped**.
4. Close and reopen Chat: **History** lists the chat; **Clear history** empties it.
5. Ask about RiskMandate in your own words, not a preset ("what is risk mandate?"): the answer is about the product at riskmandate.ai, not the finance term. If it is the finance term, do not ask free questions on the day.
6. Cloud route off (the usual state): Chat shows no cloud choice.
7. Stand mode on: Chat is not in the list a visitor sees.

The presets **Summarise a risk** (fills the box for your text) and **Explain graph triplets** (disabled, with a note that the graph tools are not built) are not for the demo; do not tap the disabled one.

Cloud route: **not for Wednesday.** It has only met fakes, not a real key or device, and is checkable only after security and the architect enable it. If a **Answer with: Cloud / This device** row appears, the cloud route is on: choose **This device**, or stop and ask the conductor. With Cloud chosen no line may say "Runs on this device"; if one does, tell the conductor at once.

What to say: the model runs on this tablet and the chat stays on it (history is kept on this tablet only, not backed up, sent or shared; **Clear history** deletes it). Speed on this tablet was about 5.5 tokens per second in the last measurement (build 0.1.78): do not promise more. Do not say it is as good as a large cloud model.

If it fails:
- *The model could not be loaded*: close other apps, retry once, then pick the smaller model in Models.
- *This chat is too long for the model*: **New chat**.
- Slow first answer: it loads the model first; say so, wait, then ask the second question for the real speed.
- Odd or wrong answers: "this is a small model on a tablet"; stay with the presets.
- After the demo, **Clear history** so the next person does not see your chat (history persists on a shared tablet; Dinis decided not to clear it automatically when the stand resets after idle: "let that be a manual process", 9 Nov).

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

## 7. Side by side with the iOS tablet (from `docs/parity-ios.md`: iOS RiskMandate v0.4.61 against Android)

Only two demos exist on **both** tablets, so only those can be shown side by side:
1. **The ABP page.** iOS: Home, **Read about Agent Behaviour Policies**, opens riskmandate.ai/abp.html in Safari. Android: the RiskMandate (site) card shows the riskmandate.ai pages inside the app, with an offline copy (or **Open riskmandate.ai** opens the system browser). Same content, different frame: say that Android keeps it inside the app and offline.
2. **A prompt and an answer from a model on the tablet.** iOS: the Workbench ("Runs on this device") on Apple's own model. Android: **Chat** (or the Workbench) on Gemma 4. **The models differ, so never compare speeds across the two tablets** and never say one model is better. The iOS team plans to move the iPad to Gemma 4 E2B (the same model as Android) around 11 to 12 November; do not mention it as done until you see it on the iPad.

Only Android has: **ABP in every layer**, **the mandate card**, **What's new**, the **Lab** (case studies) and **Our data** (Workbench). If the iPad is next to the tablet, do not promise any of these on the iPad.

Before the demo: the Android model must be **installed beforehand** (section 0.5; about 2.6 GB for the smaller model), the iPad needs no download, and iOS tokens are an estimate (marked with an approximate sign) while Android's figures are counted: say so if asked.

If asked "which is ahead": say the two apps are built from the same texts and the same shared test cases; Android has more screens today, iOS runs on Apple's own on-device model. Check both build numbers (Android: bottom of Home) before you say anything more.

## 8. The cloud switch (check before every "on this device" demo)

If the build has an optional cloud mode for Chat (a key kept on the tablet), it must be **off** for the demo. The giveaway is the Chat badge: **Runs on this device** (good) versus **Cloud: openrouter.ai** (stop). While the cloud is on, statements like "nothing leaves the tablet" are false. Fix: Diagnostics, Preview, the cloud key screen, switch off; or ask the conductor. See `docs/qa/device-run-cloud-key.md`.

## 9. Stand mode (leaving the tablet running at a table)

Long-press the bottom-right corner of Home for stand mode. It resets to Home after idle. Anything staff-only is hidden in stand mode. Chat is hidden from visitors in stand mode; leave the ABP walk-through on Auto-play instead. If you used Chat, tap **Clear history** before you leave the tablet.

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
