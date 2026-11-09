# dev.chat.android status

> Feature H (chat with the local model) built against a stand-in `ChatLlm` interface and a fake. Gate green. `enabled = false` until it works on the tablet with a real model.

## Update (P7, adapter)
- P7 done: `ChatFeature` sets `icon` (`Icons.Filled.Face`, material-icons-core), `kicker` and `summary` (`chat_kicker`, `chat_summary`, en and pt-PT).
- Adapter done (`LlmServiceChat.kt`, tests in `LlmServiceChatTest`, against work/dev.llm 8c1ea0c plus `FakeLlmService`). Per the conductor's ruling (no `installed` on LlmService): the model shown is the one in `LlmService.state`, as a read-only label ("Model: Gemma 4 E2B"), no picker unless a service ever lists several. The system block goes in as `LlmMessage.system`; `maxTokens` 512; tool proposals ignored; an Error becomes an exception with only the error code (no content). **Still to do when `FeatureContext.llm` lands:** `ChatFeature.Content` builds `LlmServiceChat(context.llm)` (null = no-model state) and `LocalChatLlm` goes. The no-model state now has an **Open Models** button calling `context.openFeature("models")` (by id only; the host refuses it in stand mode). **Still to do:** swap `LocalChatLlm` for `LlmServiceChat(context.llm)` as soon as `FeatureContext.llm` is on dev (not there yet at 25d9cb7+). Tokens per second, token count and time per answer now come from `LlmEvent.Done` (`LlmStats`: outputTokens, decodeTokensPerSecond, totalMs; the model-load time is not in it); when the runtime reports none, chat counts pieces and times it itself. The no-model text names Models in the sidebar until the host can open another feature by id.
- (Superseded) Adapter was blocked: Read dev.llm's contract (31a87ac): it has `state` only, no `installed` list and no `modelId`/`system` on `LlmRequest` (the architect's README has them), so the picker cannot be fed from it yet; `system` goes as a `LlmMessage.system` instead. Merging `origin/work/dev.llm` into this branch conflicts in `MainActivity.kt` (host lines against dev's new shell), which is not mine to resolve. Plan once `llm/` is on `dev`: `LlmServiceChat(service): ChatLlm` mapping Token to pieces and Error to an exception, `state` to the installed model, `maxTokens` capped, then read `context.llm` when architect lands it.

## Update (design ruling, architect copy)
- `Feature.icon` is back to null (design: the host's id-based line icons apply; filled Material icons are not allowed). kicker and summary stay. If chat needs its own icon: **For app:** id `chat`, a speech bubble with a small chip or tablet in it; design can draw it.
- Errors now use the service's reason for the copy (NO_MODEL, LOAD_FAILED, TOO_LONG, INFERENCE_FAILED), kept on the message as `failure` (the reason name only, never content) and saved with the chat.
- No colour literals in chat (design's ColourRulesTest).

## Update (context.llm wired)
- `ChatFeature.Content` now uses `LlmServiceChat(context.llm)`; null (or `LlmState.NoModel`) shows the no-model state with **Open Models** (`context.openFeature("models")`). `LocalChatLlm` is gone (`LocalChatStore` stays, tests only). Tests cover the production path with `FakeLlmService` (null, NoModel, streaming, markup shown as text, an allowed link as a button).
- Still true: `enabled = false` until Dinis checks it on the tablet with a real model.

## Screenshots (for Dinis)
`ChatScreenshotTest` writes `app/build/screenshots/chat-{no-model,empty,answer}-tablet-light.png` (1280x800 dp landscape, light). The model in them is `FakeLlmService`: scripted words, and the speed line ("5000 tokens per second") is the fake's, not a device measurement.

## Q-chat-1 (QA, rotation) fixed
- The open chat's id is kept in `rememberSaveable` and reselected by `start(reopen)` when it was saved; otherwise a new empty chat opens.
- The visitor's message is saved as soon as it is sent (no empty answer placeholder on disk); the partial answer is written from a `NonCancellable` block when the answer job ends, including when the scope is cancelled by a rotation, so it comes back marked "Stopped".
- Tests: QA's probe is now `ChatScreenTest.rotationKeepsTheOpenConversationOnScreen`; controller tests for reopen, save-before-answer and save-on-scope-cancel.
- Q-chat-2 (wipe on a shared stand) is still Dinis's call: `ChatPolicy.WIPE_ON_LEAVE` stays false.

## Q-chat-4 and Q-chat-5 (grounding texts verbatim)
`res/raw/feature_chat_riskmandate.txt` and `feature_chat_abp.txt` now contain only the pages' own sentences (checked by script against the bundled offline copy, v1.40.3: every sentence found, table cells joined with " | "). Removed my line "Only a boundary is a control." and my paraphrases of the barrier kinds and the inventory/finding passage, which are now the page's wording in full.

## After Dinis's tablet run (build 0.1.78, E2B, about 5.5 tokens/s)
1. Every request without a preset now carries a short base system block (`ChatPresets.BASE_SYSTEM`, about 85 tokens): this is the RiskMandate app, RiskMandate means the product at riskmandate.ai, and two sentences quoted verbatim from the bundled riskmandate.ai text. Presets keep their full grounding instead. **Not measured:** time to first token before and after; I have no tablet. The block is under 600 characters; Dinis or QA should compare TTFT with and without (dev.llm's bench reports it).
2. Badge now "Runs on this device" (pt-PT "Funciona neste dispositivo"); the network chip stays.
3. `PlainText.clean` drops `**` and paired `__` for display (history keeps the model's raw text). Nothing is rendered; `<b>` and markdown links still show as text.
4. Sampling was `Sampling.DEFAULT`: temperature 1.0, top-k 64, top-p 0.95 (Gemma's recommended). Chat now asks for temperature 0.4, top-k 64, top-p 0.9 (`ChatSampling`), no seed. Easy to change in one place; the lab and eval keep their own settings.

## R-chat-6 (security Low) fixed
`PlainText.clean` is now one linear pass with no regex (a run of `__` with no closing pair costs the same as normal text), and the bubble computes it once per text with `remember(m.text)`. Test: five hostile 100k-character strings each clean in under 200 ms, plus pairs, unpaired markers and whitespace cases.

## Flake in ChatScreenTest (conductor)
Cause: `start()` reads history on a real IO thread that the compose clock cannot see, so `waitForIdle` could return while the screen still said "looking for models". Every screen test now waits for the loaded state itself (the no-model notice or the input row); all `waitUntil` waits are 15 s. Stress: 30 runs of `ChatScreenTest` (18 tests each) under 4 busy CPU loops on 4 cores: 0 failures. An earlier 9-run attempt of the same code under the same load had one failure that I could not attribute (the report was overwritten); the 5 s waits are the likely cause, hence 15 s. Not run: 30 repetitions inside the full suite (the full suite is run once per push).

## Cloud client (package `ai.riskmandate.app.cloud`, architect 3dce2d9 and security section M)
Built, not wired: nothing in the host calls it yet, so no request can leave the device from this branch. Moved from `llm/cloud/` to the top-level `cloud/` as ruled (it imports `llm/`, never the reverse). 54 tests, fake transport and software cipher, no network.
- **Host:** `CloudEndpoint.isAllowed` (pure; https, `openrouter.ai` exactly, port 443 or none, no userinfo, path under `/api/v1/`; lookalike tests: `openrouter.ai.evil`, `evilopenrouter.ai`, `sub.openrouter.ai`, trailing dot, userinfo, port, scheme, case, whitespace, `%`). Fixed `User-Agent` and `X-Title`, `Accept-Encoding: identity`; no cookies or caches (`useCaches = false`); redirects not followed; a 3xx is a failure. The only file with `java.net` is `CloudTransport.kt` (test).
- **Models:** `anthropic/claude-sonnet-5.5`, `google/gemma-4-26b-a4b-it`, `google/gemma-3-4b-it`, pinned in code and checked against OpenRouter's public model list on 9 Nov 2026 (no key used). A model whose id or prices differ from the pinned entry cannot be constructed.
- **Key (`CloudKey`):** AES-256-GCM, key in the Android Keystore (`KeystoreCipher`), fresh IV per write, ciphertext in the folder the host passes (`noBackupFilesDir/feature_cloud/`). Shape check: `sk-or-` prefix, printable ASCII, no whitespace, 16 to 256 characters. **No `last4`** (security M.1.3: the screen says only "key set" or "no key"). `clear()` deletes the file and the Keystore key. The key reaches only the `Authorization` header through `withBearer` (it does not return it); `scrub()` removes the key, `Bearer ...` and any `sk-or-...` string from text. Never logged (test scans the package for `Log.`, `println`).
- **Switch (`CloudSwitch`):** off by default, memory only (off after a restart), refuses to turn on in stand mode and falls back to off, for good, when stand mode turns on. The service sends nothing while it is off.
- **Spend (`CloudSpend`, `CloudLimits`):** all limits in one object, none settable from a screen (there is no `setCap`): per session US$0.50, per tablet US$2.00 (security's proposal, placeholders until Dinis sets them), reserve US$0.01 when OpenRouter reports no cost, 2 s minimum gap between calls, request body at most 32 KB, response at most 1,000,000 characters, `max_tokens` at most 2,048. The ledger (`ledger.json`, a number) refuses to spend when **missing or damaged** (staff create it with `initialise()` when they save the key, or `reset()` on purpose). Each call is checked before sending against a worst-case estimate (prompt and `maxTokens` at list price); the cost is recorded when the answer ends, also when stopped or cut off. `worstCaseMicros(request)` is public so a sweep can show its worst case before it starts.
- **Service (`CloudLlmService(model, key, spend, switch)`):** same contract as the local service; one per model. `provider.data_collection = "deny"` is sent (OpenRouter's provider-routing documentation, read 9 Nov 2026: `data_collection` is `"allow" | "deny"`, default `"allow"`, "control whether to use providers that may store data"; security records the date and wording in `docs/security/models.md`). Error texts are fixed phrases; error bodies are never read. At most one try (no retry). `lastRun` carries cost, tokens, session and total spend.
- **Not done, waiting on others:** (1) `LlmService.location` and `LlmStats.location` (dev.llm): `CloudLlmService` returns CLOUD once it exists; chat's "Runs on this device" then reads it. (2) The RiskMandate base block to `assets/prompts/riskmandate_base.v1.txt` via `llm/prompts/` (dev.llm): not on dev yet; chat keeps its own copy and test until then. (3) `FeatureContext.cloudLlm`, `Feature.usesCloud` (architect), the host wiring, the Diagnostics key screen (paste field, "key set", Remove, `FLAG_SECURE`, clipboard clear, hidden in stand mode) and the "Cloud" badge and conditional "stays on this device" strings in the screens that can use the cloud (app, dev.lab, Workbench). Chat does not use the cloud.
- **Security's R-cloud findings (review of e84e63d):** R-cloud-1 (fixed `User-Agent`, `useCaches = false`), -2 (`sk-or-` prefix), -3 (no `last4`), -6 (request 32 KB, reply 1,000,000 characters; the leftover 200,000-character prompt constant is now the same 32 KB limit), -7 (`sk-or-` strings scrubbed) closed in 14f671b and this push. R-cloud-4: there is no `setCap`; `reset` now needs `confirmed = true` (it throws otherwise), and app must call it only from a confirmed action on the staff-only key screen. R-cloud-5: one gate, `CloudSpend.gate`, shared by every service that uses the same ledger: a test runs two services in parallel and sees one request in flight at most. R-cloud-8 is the key screen's text (app): suggested line: "A pasted key cannot be wiped from this app's memory while it runs; it is stored encrypted on this tablet only."
- **Asked of dev.llm (sent directly):** `LlmError.CAP_REACHED`, `LlmStats.costUsd: Double? = null`, and the names of `location` once added; and the move of the base block to `assets/prompts/riskmandate_base.v1.txt` via `llm/prompts/`. I switch the cloud service over when they are on dev.
- **KeystoreCipher on a device:** `app/src/androidTest/java/ai/riskmandate/app/cloud/KeystoreCipherTest.kt` (compiles in `assembleDebugAndroidTest`, **not run**: no device here). Asked qa to run it and to add the key-screen checklist to the device guide.
- **For app (API):** `CloudKey(File(noBackupFilesDir, "feature_cloud"), KeystoreCipher())`, `CloudSpend(sameFolder)`, `CloudSwitch(standMode = { ... })`; on saving a key call `key.save(pasted)` then `spend.initialise()`; Remove calls `key.clear()`; `key.check(pasted)` for the field's error text; `key.isSet()`; `spend.snapshot()` for a counter; `spend.reset(confirmed = true)` for staff, only from a confirmed action on the key screen.
- **For security:** `KeystoreCipher` is not covered on the JVM (no Keystore in Robolectric): one on-device check. `ChatFeature`'s lines "Nothing you type leaves the device" stay true because chat only takes `context.llm` (on-device); a test for the "no on-device line beside a cloud answer" belongs to the screens that show cloud answers.

## Doing
Waiting for dev.llm's `LlmService` to land, then swapping the stand-in for an adapter.

## Done (pushed to work/dev.chat)
- `features/chat/ChatLlm.kt`: `ChatLlm` stand-in (`installedModels()`, `stream(modelId, system, turns): Flow<String>`; stop = cancel the collector), `NoLlm` (empty state), `FakeChatLlm` (tests).
- `ChatController.kt`: streaming, stop (partial text kept, marked stopped, upstream flow cancelled), new chat, select chat, clear history, model choice, tokens per second (pieces after the first, over first piece to end) and time per answer, save after every answer (also after a stop). Everything is cancelled when the user leaves the feature.
- `ChatModel.kt`: messages, limits (30 chats, 200 messages, 20k characters a message, 12k characters of history sent, about 3K tokens), prompt builder (newest turns that fit, starts on a user turn, skips failed or empty turns), presets, `ChatLinks`.
- `ChatStore.kt`: history as one JSON file in `noBackupFilesDir/feature_chat/history.json` (app-private; see the next section), written to a temp file and renamed, a damaged file reads as empty. No new dependency (`org.json` is the platform's).
- `ChatScreen.kt`, `ChatFeature.kt`: content only inside `FeatureFrame`, using `FeatureContent`, `StatusChip` ("On device, offline"), `NoticeBanner`. Model picker only with more than one model, stop button, new chat, history list (own `BackHandler`), clear history with a confirmation, presets (shown on an empty chat), stats line under the last answer, no-model state. `HoldIdleWhile(generating)` keeps stand mode's reset off during an answer.
- Presets: "What is RiskMandate?" and "Explain an ABP" send the question with a bundled text (`res/raw/feature_chat_riskmandate.txt`, `feature_chat_abp.txt`, quoted from riskmandate.ai and riskmandate.ai/abp, read 9 Nov 2026) as the model's only source. "Summarise a risk" fills the box for the user's text. "Explain graph triplets" is disabled with a note until the graph tools exist (dev.lab / eval).
- Output safety: model text is drawn with `Text` (never parsed as HTML, markdown, links or code). The only thing taken from it is URLs that pass `Links.isAllowed`, shown as buttons that call `Browser.open` on a tap (at most three).
- Backup: confirmed. `data_extraction_rules.xml` (cloud-backup and device-transfer) and `backup_rules.xml` exclude every domain (root, file, database, sharedpref, external) and the manifest has `allowBackup="false"`. `ChatStoreTest.backupAndDeviceTransferExcludeEveryDomain` fails if any of that changes.
- Strings: `res/values/feature_chat.xml` and `values-pt-rPT/feature_chat.xml`, all prefixed `chat_`.
- Tests (34): `ChatControllerTest` (streaming, stats on a fake clock, stop, failure, no model, busy, history saved and reloaded, empty chats not saved, new chat, clear history, model picker, grounding, prompt budget, links), `ChatStoreTest` (round trip, atomic replace, damaged file, clear, limits, backup rules), `ChatScreenTest` (badge, no-model state, typing and sending, markup shown literally, links, stop, picker, presets, clear with confirmation, pt-PT).
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: green.

## After the conductor's inputs (architect e2675d4, design llm-screens.md, security H)
- Storage moved to `noBackupFilesDir/feature_chat/history.json` (security H.3; stricter than the architect's `filesDir/feature_chat/`, and still app-private). **For mandate/app:** list this path, not `filesDir`.
- "On device, offline" badge now shows only while a model is installed; a separate network chip ("Network: on/off", real state via `Browser.isOffline`, re-read after each answer) tells the truth beside it (design).
- Input capped at 2,000 characters (design). No logging anywhere in the package (a test scans for `Log.`, `println`, `printStackTrace`).
- Stand tablet (security H.3): leaving the chat already drops the on-screen conversation, and it opens on an empty chat. Wiping the saved history on leave is `ChatPolicy.WIPE_ON_LEAVE`, **off** until Dinis decides (it cannot tell Back from stand's idle reset; if he wants only the idle reset to wipe, stand/app must give features a signal). Also pending Dinis: `FLAG_SECURE` on the chat screen (blocks demo screenshots), so not done.
- Not yet done, waiting for the real `LlmService` (dev.llm has not pushed): the adapter from `LlmService.stream(LlmRequest): Flow<LlmEvent>` and `context.llm` (null = "set up a model" with a way to G through the host), generation cap through `maxTokens`, "loading model" state from `LlmState`, "Jump to latest", the design's empty-state preset cards and selectable text.

## For architect (FeatureContext)
`FeatureContext` has no `LlmService` yet. Until it does, chat reads it from `LocalChatLlm` (a `CompositionLocal` in the chat package, default `NoLlm`). When the architect adds it to the contract, chat reads `context.llm` instead: one line in `ChatFeature.Content` plus an adapter from `LlmService` to `ChatLlm`.

## For play (privacy text; conductor: play.android drafted it, DRAFT until H ships)
- The privacy text should say what Dinis decided: "Chat history is kept on this tablet only, in app-private storage. It is not backed up, transferred, sent or shared, and Clear history in Chat deletes it." (Strings are app's, not mine.)

## For dev.llm
What chat needs: the list of installed models (id, display name), a streaming call that emits pieces (one emission is about one token) and stops generating when its flow is cancelled, an optional system block, and the turns so far. If your interface can report real token counts, say so and I will show them instead of counting pieces.

## TODO for Dinis
1. pt-PT is my translation: please have a Portuguese reader check "Sugestões", "triplos do grafo", "Você".
2. Tablet check with a real model before `enabled = true` (speed, stop latency, long answers, rotation).

## Next
- Switch to dev.llm's `LlmService` when it lands on `origin/work/dev.llm`; keep the fake for tests.
- Graph-triplets preset once dev.lab / eval publish the graph tools.
- Build environment note: Android SDK installed outside the repo (scratchpad); Robolectric's jar fetched from repo1.maven.org (repo.maven.apache.org returned 429).
