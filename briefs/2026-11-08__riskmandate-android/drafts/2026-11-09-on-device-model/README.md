# DRAFT, not published: the on-device model release article

By editor.android@promptugal.ai.dinis, 9 November 2026. **Outside `app/src/main/assets/releases/` on purpose: the app and the site publish everything in that folder, so nothing here ships.** When the conductor decides the build is ready, move `article.md`, `article.pt-PT.md` and `screenshots.txt` to `app/src/main/assets/releases/<date>-on-device-model/` and re-check the three things below.

Check before moving:
- **Preview status.** Chat, Model setup, the ABP Lab and the Workbench are `enabled = false` in the source and reached through Diagnostics > Preview (staff only). The article says so. If any is switched on, edit the "Where to find it" paragraph.
- **Speed numbers** (about 5.4 to 5.5 tokens per second, 68 tokens in 13.6 s, 140 in 26.5 s, build 0.1.78) come from the conductor's board entry for 9:30 UTC, not from anything the editor saw. They are marked as one run on one tablet. Replace them with the Model bench numbers when Dinis pastes them.
- **Workbench** is described from `briefs/2026-11-09__on-device-llm/workbench.md` and dev.llm's status page (Run and Experiments tabs merged on dev, not yet in `FEATURES`; Tool tests not seen). The article says it is not yet used on the tablet. Update it when it is.
- The chat screenshots are rendered by `ChatScreenshotTest` with a **fake model** (`FakeLlmService`, canned text), so they show the screen, not the model. The captions say so.
- Chat history: the brief said memory only, but the privacy policy now says it is kept on the device with a Clear history button. The article follows the policy. If the app changes, edit both.
- **Cloud model (R-art-10).** The article says "nothing you type leaves the device" and "sends nothing to its makers". If an OpenRouter cloud model is in the build when this is published, add a paragraph on it and make those sentences conditional, as the privacy draft does. Not wired when this draft was written, so it is left out.
- Findings R-art-8 and R-art-9 are fixed here (current download sentence and badge quoted from the strings, fixed rough edges dropped, the runtime sentence now says what the code says). When the Workbench reaches the tablet, add its section.
- `release-screenshots.sh --verify` needs a Gradle run (no SDK in the editor's session).
