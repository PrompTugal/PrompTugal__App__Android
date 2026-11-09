# dev.chat.android status

> Feature H (chat with the local model) built against a stand-in `ChatLlm` interface and a fake. Gate green. `enabled = false` until it works on the tablet with a real model.

## Update (P7, adapter)
- P7 done: `ChatFeature` sets `icon` (`Icons.Filled.Face`, material-icons-core), `kicker` and `summary` (`chat_kicker`, `chat_summary`, en and pt-PT).
- Adapter to `LlmService` NOT done yet. Read dev.llm's contract (31a87ac): it has `state` only, no `installed` list and no `modelId`/`system` on `LlmRequest` (the architect's README has them), so the picker cannot be fed from it yet; `system` goes as a `LlmMessage.system` instead. Merging `origin/work/dev.llm` into this branch conflicts in `MainActivity.kt` (host lines against dev's new shell), which is not mine to resolve. Plan once `llm/` is on `dev`: `LlmServiceChat(service): ChatLlm` mapping Token to pieces and Error to an exception, `state` to the installed model, `maxTokens` capped, then read `context.llm` when architect lands it.

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
