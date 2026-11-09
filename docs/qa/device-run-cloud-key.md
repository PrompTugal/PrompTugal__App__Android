# Device run: the cloud key screen (staff checklist, draft)

For staff. Drafted 9 Nov 2026 by qa.android@promptugal.ai at dev.chat's request. **DRAFT: not runnable yet.** The cloud package (`ai.riskmandate.app.cloud`: key store, switch, spend caps) is on `work/dev.chat` 14f671b and is not on `dev`; the key screen in Diagnostics is app.android's and does not exist yet. Labels marked **[confirm]** are guesses. This becomes a part of `device-run-idea-tab-plus.md` once the screen is in a build.

**Keys.** For every step below use a **fake key** that fits no scanner class, for example `sk-or-FAKE-key-for-tests` (the shape the app's check accepts: `sk-or-` prefix, printable ASCII, no spaces, a plausible length). A real key is for Dinis only, on his own tablet, never typed into notes, messages, screenshots or this repository. Nothing in this checklist needs a real key.

## Before you start
- [ ] A build that has the key screen. Build number ______ . Stand mode **off** (stand mode hides Diagnostics).
- [ ] Diagnostics: find the cloud key section [confirm: name and place].

## A. The key is never shown
- [ ] Paste the fake key and save. The screen says only **"key set"** [confirm wording]. It never shows the key, not even the last four characters, not in a field, a toast, a dialog or the accessibility text (turn TalkBack on once and swipe over the section: it reads "key set", not the key). ______
- [ ] A key with the wrong shape (no `sk-or-` prefix, spaces, too short or too long) is refused with a message that does not echo it. ______

## B. It survives a restart
- [ ] **Close the app completely** (swipe it away, or Settings, Apps, Force stop) and open it again. The screen still says "key set". ______

## C. Remove really removes
- [ ] Tap **Remove** [confirm]. The screen says **"no key"**. ______
- [ ] The **cloud switch** does nothing without a key: turning it on shows a message or stays off; no request is made. ______
- [ ] Close and reopen: still "no key"; paste a key again to leave the tablet as you want it. ______

## D. The screen protects itself
- [ ] **Screenshot** while the key screen is showing: Android refuses ("Can't take screenshot due to security policy") or the picture is black. ______
- [ ] **Recents** (the app switcher) while the key screen was open: the app's thumbnail is blank or hidden, not the screen's content. ______
- [ ] **Stand mode on**: Diagnostics, and so the key screen, cannot be reached (it is staff only). ______

## E. The claims stay true when cloud is on
When the cloud switch is on, the app can send what a person types to a server (openrouter.ai). Every statement elsewhere in the app that says otherwise would then be false. With the switch **on**, read each of these and write what it says:
- [ ] Chat badge (it says "On device, offline" when local): ______ ; it must not still claim that while a cloud model answers.
- [ ] Privacy line on Model setup ("Nothing you type leaves the device"): ______ ; must not be shown as true for cloud answers.
- [ ] This app's mandate screen: does it list **openrouter.ai** under the addresses the app connects to, and is the gap honest? ______
- [ ] Every answer from a cloud model is **labelled** as cloud (model name and "cloud"), never shown as the on-device model. ______
- [ ] Spend caps (session and tablet) are visible, and a request over the cap is refused with a message and no network call. ______
- [ ] With the switch **off** and after **Remove**, no cloud request is made (airplane mode off, watch the network chip; the chat still works on-device). ______

## F. What to copy back
Pass or fail per box with what you saw. **Never paste a key** (not even a fake one into a message that will be committed) and read the device label line before you paste anything from Diagnostics.
