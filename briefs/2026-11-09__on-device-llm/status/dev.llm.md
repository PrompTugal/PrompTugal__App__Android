# dev.llm.android status

> Step 1 (bench) is built, tested, and the gate is green locally, but it needs four lines in app.android's files (LiteRT-LM dependency, one compiler flag, an R8 keep rule, one manifest line), so it is not pushed. Asked the conductor at about 02:15 UTC whether I may carry them as a separate commit. Third model: every small Google model is gated, so that is a call for Dinis.

## Doing
Waiting for the conductor on the host lines (see "For app"). Then: push, message @parent, then Dinis runs the bench on the Idea Tab Plus.

## Done
- **Runtime found**: `com.google.ai.edge.litertlm:litertlm-android` on maven.google.com (Apache-2.0). Chose **0.18.1**, the version Google AI Edge Gallery ships today with the same Gemma 4 files (latest is 0.19.1). Kotlin API: `Engine(EngineConfig(modelPath, Backend.GPU()/CPU(), maxNumTokens, cacheDir))`, `engine.initialize()`, `engine.createConversation(ConversationConfig(samplerConfig))`, `conversation.sendMessageAsync(text, maxOutputToken)` (a Flow of messages), `cancelProcess()`, `getBenchmarkInfo()` (prefill and decode tokens and tok/s, behind `ExperimentalFlags.enableBenchmark`). Tool calling exists too (`OpenApiTool`: a JSON schema string plus `execute`), which step 2 will use.
- **Models pinned** (`llm/ModelCatalog.kt`): exact repo, full commit, file name, size, SHA-256, licence. I downloaded both Gemma 4 files from the pinned URLs and hashed them end to end, and both match:
  | Model | File | Revision | Bytes | SHA-256 | Licence |
  |---|---|---|---|---|---|
  | Gemma 4 E2B (default) | gemma-4-E2B-it.litertlm | 7fa1d784... | 2,583,085,056 | ab7838cd...7e42 | apache-2.0, ungated |
  | Gemma 4 E4B | gemma-4-E4B-it.litertlm | 9695417f... | 3,654,467,584 | f335f2bf...a7bc | apache-2.0, ungated |
  | Gemma 3 1B int4 | gemma3-1b-it-int4.litertlm | aa49427c... | 584,417,280 | 1325ae36...98be | gemma, **gated** |
  | Gemma 3 270M q8 | gemma3-270m-it-q8.litertlm | 9d209327... | 304,005,120 | 757e9119...dd63 | gemma, **gated** |
  The Gemma 4 revisions are the ones Edge Gallery's allow-list 1.0.23 offers. Hugging Face `main` already has a newer build with multi-token prediction; I did not take it, because Gallery pairs it with this runtime only behind a toggle. The hashes for the two gated files are Hugging Face's LFS values, not my own download (they need a login).
- **Hidden bench** (`features/models/ModelBenchScreen.kt`, `llm/LiteRtBench.kt`): lists catalogue files found in the app's folders, checks size and SHA-256 before the first load (wrong hash: deleted, never opened; wrong size: left alone, since adb may still be copying), then loads on GPU or CPU, streams the fixed prompt (`BenchPrompt` v1: about 450 tokens of neutral, fictional text, greedy, at most 256 output tokens) and shows the answer as plain text. Reports: SHA-256 check time, load time, time to first token, prefill tokens and tok/s, decode tokens and tok/s (runtime counters, plus a wall-clock figure), memory (process RSS and peak from /proc/self/status, system free and total) before, after load and after the answer. **Copy results** puts the plain-text report (device make, model, SoC, Android, app version; no identifiers) on the clipboard. Each run loads afresh and closes the engine; leaving the screen cancels a run. Only `LiteRtBench.kt` touches LiteRT-LM classes.
- Feature G stays `enabled = false`; its Content is the bench for now.
- Strings `feature_models.xml` en and pt-PT. Tests: `llm/ModelCatalogTest` (pinning, https on huggingface.co only, unique ids, licences), `llm/ModelFilesTest` (SHA-256 vector, ok, wrong hash deletes, wrong size keeps, catalogue names only, adb command), `llm/BenchReportTest` (report text, failures, /proc parsing, prompt names no product), `features/models/ModelBenchScreenTest` (no model, pushed file listed, truncated file refused without loading the runtime, copy).
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: **green locally with the four host lines** (276 tests). Without them the branch does not compile.

## How Dinis runs the bench (step 1)
The tablet gets release builds, which are not debuggable, so adb cannot write to internal storage. The bench therefore also reads the app's own folder on shared storage, which adb can write to and which other apps cannot read.
1. On a computer, download the file from its pinned URL (no login needed for Gemma 4):
   `curl -L -o gemma-4-E2B-it.litertlm https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm`
   `curl -L -o gemma-4-E4B-it.litertlm https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm`
2. Open the bench once (it creates the folder), then:
   `adb shell mkdir -p /sdcard/Android/data/ai.riskmandate.app/files/feature_models`
   `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/ai.riskmandate.app/files/feature_models/`
3. In the bench: Refresh, pick the model, GPU, Run. Then CPU, Run. Repeat for E4B. Then Copy results and paste them back.
The bench shows the exact folder and commands on screen too. The first run of each file includes the SHA-256 check (seconds, shown separately).

## Next
1. Push step 1 once the host lines are settled; message @parent; Dinis measures E2B and E4B (GPU and CPU), plus 270M or 1B if he pushes them.
2. Step 2: `LlmService` (generate, stream, cancel, tool calls with a JSON schema), the LiteRT-LM implementation, `FakeLlmService`; architect adds it to `FeatureContext`.
3. Step 3: feature G (download with resume and progress, storage check, Wi-Fi advice, verify, delete, adb fallback, privacy text).

## Blocked
- The four host lines below (app.android's files). The branch does not compile without them.

## For app
Exact lines, tested locally (gate green):
1. `app/build.gradle.kts` dependencies: `implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")`. Transitive: `com.google.code.gson:gson:2.14.0`, `org.jetbrains.kotlin:kotlin-reflect:2.4.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0`.
2. `app/build.gradle.kts` `kotlinOptions { freeCompilerArgs += "-Xskip-metadata-version-check" }`. Every LiteRT-LM release since 0.9 is built with Kotlin 2.2 or 2.4 metadata, and we are on Kotlin 2.0.21. Google's Edge Gallery uses the same flag. The other fix is the Kotlin, AGP and BOM bump you already found blocks the gate.
3. `app/proguard-rules.pro`: `-keep class com.google.ai.edge.litertlm.** { *; }` (JNI entry points and reflection; the AAR ships no consumer rules). Checked: the mapping keeps `Engine` and the rest of the package.
4. `AndroidManifest.xml` inside `<application>`: `<uses-native-library android:name="libOpenCL.so" android:required="false" />`, so the GPU backend can load the vendor OpenCL on API 31+. No permission, no component.
5. A way to reach the bench on a release build: a "Model bench" button on the Diagnostics screen that opens `ModelsFeature.Content` (or `ModelBenchScreen()`) in `FeatureFrame` while `enabled` stays false. Title can be `feature_models_title`. Release builds from Firebase are not debuggable, so a debug toggle would not reach the tablet.
6. Size: the release APK goes from about 0.9 MB to **50 MB**, all of it `liblitertlm_jni.so` stored uncompressed for arm64-v8a (22 MB) and x86_64 (26 MB). AAB 24.8 MB; Play would split per ABI. `ndk { abiFilters += "arm64-v8a" }` would halve the APK, but release's x86_64 emulator smoke test could no longer install it. app and release decide.

## For architect
- Approve the new dependency (above). Step 2's `LlmService` shape comes next in this page.

## For security
- Licence and OSV for litertlm-android 0.18.1 and its three transitive deps. The AAR carries `THIRD_PARTY_NOTICE.txt` for its native code.
- Download path for G (step 3): `https://huggingface.co/<repo>/resolve/<commit>/<file>` answers 302 to Hugging Face's CDN (seen: `us.aws.cdn.hf.co`, signed URL). So the pinned host list needs the redirect host(s), or the downloader follows exactly one redirect to an https `*.hf.co` host. The SHA-256 check is what makes the bytes trustworthy either way.
- The adb folder (`/sdcard/Android/data/ai.riskmandate.app/files/feature_models/`) is app-specific external storage: on Android 11+ other apps cannot read or write it, but adb can. Files there are checked against the pinned SHA-256 before every first load in a screen visit.

## For dev.mandate
- Once G downloads, model files live in `files/feature_models/` (and the bench reads `Android/data/.../files/feature_models/`). Your reach view lists files under `files/`, so this needs a mandate entry when G lands.

## For Dinis
- Dinis: the third, smallest model. Every small Google LiteRT-LM model is gated on Hugging Face (Gemma licence, login required): Gemma 3 270M (304 MB) and Gemma 3 1B int4 (584 MB). The only ungated small Google files are FunctionGemma 270M "mobile actions" fine-tunes (Gemma licence), which are tool-calling demos, not chat models. Options: (a) you accept the Gemma licence on Hugging Face, download 270M or 1B yourself and push it with adb (the bench already knows both files and their hashes), and we decide later whether G offers it, which would need a download host other than gated Hugging Face; (b) the default list is just E2B and E4B; (c) something else. I recommend (a) for the measurement and (b) for G until you say otherwise.
