# dev.llm.android status

> Step 1 (bench) pushed: the bench in features/models, plus one separate commit with the host lines for app.android's review (LiteRT-LM 0.18.1, one compiler flag, an R8 keep rule, one manifest line, a Diagnostics "Model bench" button). Gate green. Next: Dinis runs it on the Idea Tab Plus. Third model: every small Google model is gated, Dinis's call (the conductor is asking him).

## Doing
Waiting for Dinis's bench numbers from the tablet (Diagnostics > Model bench). Starting step 2 (`LlmService`, `LlmHost`) to architect's contract meanwhile.

## Done
- **Runtime found**: `com.google.ai.edge.litertlm:litertlm-android` on maven.google.com (Apache-2.0). Chose **0.18.1**, the version Google AI Edge Gallery ships today with the same Gemma 4 files (latest is 0.19.1). Kotlin API: `Engine(EngineConfig(modelPath, Backend.GPU()/CPU(), maxNumTokens, cacheDir))`, `engine.initialize()`, `engine.createConversation(ConversationConfig(samplerConfig))`, `conversation.sendMessageAsync(text, maxOutputToken)` (a Flow of messages), `cancelProcess()`, `getBenchmarkInfo()` (prefill and decode tokens and tok/s, behind `ExperimentalFlags.enableBenchmark`). Tool calling exists too (`OpenApiTool`: a JSON schema string plus `execute`), which step 2 will use.
- **Models pinned** (`llm/ModelCatalog.kt`): exact repo, full commit, file name, size, SHA-256, licence. I downloaded both Gemma 4 files from the pinned URLs and hashed them end to end, and both match:
  | Model | File | Revision | Bytes | SHA-256 | Licence |
  |---|---|---|---|---|---|
  | Gemma 4 E2B (default) | gemma-4-E2B-it.litertlm | 7fa1d784... | 2,583,085,056 | ab7838cd...7e42 | apache-2.0, ungated |
  | Gemma 4 E4B | gemma-4-E4B-it.litertlm | 9695417f... | 3,654,467,584 | f335f2bf...a7bc | apache-2.0, ungated |
  | Gemma 3 1B int4 | gemma3-1b-it-int4.litertlm | aa49427c... | 584,417,280 | 1325ae36...98be | gemma, **gated** |
  | Gemma 3 270M q8 | gemma3-270m-it-q8.litertlm | 9d209327... | 304,005,120 | 757e9119...dd63 | gemma, **gated** |
  **The end-to-end check was done once in this cloud session (about 02:00 UTC), before Dinis's instruction that models are downloaded by the tablet only reached me.** Both files were streamed into `sha256sum` and never written to disk; nothing is left in this session (checked: no `.litertlm` or `.task` file anywhere). From now on no model is downloaded here; the tablet confirms the pins on first download.
  The Gemma 4 revisions are the ones Edge Gallery's allow-list 1.0.23 offers.
  **Why the generic `.litertlm` file and not `-gpu`** (security's `models.md` pins `-gpu` at `main`): Google's Android reference, Edge Gallery, offers the generic file for Android (`gemma-4-E2B-it.litertlm`, accelerators CPU and GPU), and that is what Dinis already ran on this tablet. The `-gpu` file has exactly the size of the `-web.litertlm` file (2,008,432,640 bytes both), and the model card says the web build is "specially optimized" for the browser's memory limits and text-only; the card gives no Android numbers for `-gpu`. The Tensor, Qualcomm, Intel and NPU files are compiled for other chips, so none fits a Dimensity 6400 with a Mali GPU. The generic file runs on both CPU (XNNPACK) and GPU, so the bench can compare the two on this tablet. If Dinis wants `-gpu` measured too, it is one more catalogue entry (adb only). Hugging Face `main` already has a newer build with multi-token prediction; I did not take it, because Gallery pairs it with this runtime only behind a toggle. The hashes for the two gated files are Hugging Face's LFS values, not my own download (they need a login).
- **Hidden bench** (`llm/bench/ModelBenchScreen.kt`, `llm/LiteRtBench.kt`): lists catalogue files found in the app's folders, checks size and SHA-256 before the first load (wrong hash: deleted, never opened; wrong size: left alone, since adb may still be copying), then loads on GPU or CPU, streams the fixed prompt (`BenchPrompt` v1: about 450 tokens of neutral, fictional text, greedy, at most 256 output tokens) and shows the answer as plain text. Reports: SHA-256 check time, load time, time to first token, prefill tokens and tok/s, decode tokens and tok/s (runtime counters, plus a wall-clock figure), memory (process RSS and peak from /proc/self/status, system free and total) before, after load and after the answer. **Copy results** puts the plain-text report (device make, model, SoC, Android, app version; no identifiers) on the clipboard. Each run loads afresh and closes the engine; leaving the screen cancels a run. Only `LiteRtBench.kt` touches LiteRT-LM classes.
- Feature G stays `enabled = false`; its Content is the bench for now. Reached from **Diagnostics > Model bench** (host route `modelbench`), shown only on a debuggable build or once a catalogue file is in the adb folder (A-llm-5). The screen lives in `llm/bench/` so the host imports no feature package (A-llm-4).
- Model folders: internal `noBackupFilesDir/models/` (security G.3/G.5; architect's page says `filesDir/models/`, see "For architect") and, for adb, `/sdcard/Android/data/ai.riskmandate.app/files/models/`.
- Merged release manifest checked with LiteRT-LM in: the AAR adds no component, receiver, provider or permission. New entries: only our `<uses-native-library android:name="libOpenCL.so" android:required="false"/>`. The one provider (`androidx.startup.InitializationProvider`, `exported="false"`) was there before, from emoji2, lifecycle-process and profileinstaller.
- Strings `feature_models.xml` en and pt-PT. Tests: `llm/ModelCatalogTest` (pinning, https on huggingface.co only, unique ids, licences), `llm/ModelFilesTest` (SHA-256 vector, ok, wrong hash deletes, wrong size keeps, catalogue names only, adb command), `llm/BenchReportTest` (report text, failures, /proc parsing, prompt names no product), `llm/bench/ModelBenchScreenTest` (no model, pushed file listed, truncated file refused without loading the runtime, copy).
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: green with the host commit; `scripts/secret-scan.sh` clean; `node --test devices/test/*.test.mjs` 68 pass.

## How Dinis runs the bench (step 1)
The tablet gets release builds, which are not debuggable, so adb cannot write to internal storage. The bench therefore also reads the app's own folder on shared storage, which adb can write to and which other apps cannot read.
1. On a computer, download the file from its pinned URL (no login needed for Gemma 4):
   `curl -L -o gemma-4-E2B-it.litertlm https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm`
   `curl -L -o gemma-4-E4B-it.litertlm https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm`
2. Push it (the Diagnostics entry only appears once a model file is in this folder, architect A-llm-5):
   `adb shell mkdir -p /sdcard/Android/data/ai.riskmandate.app/files/models`
   `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/ai.riskmandate.app/files/models/`
3. Open Diagnostics > Model bench. Pick the model, GPU, Run. Then CPU, Run. Repeat for E4B. Then Copy results and paste them back.
The bench shows the exact folder and commands on screen too. The first run of each file includes the SHA-256 check (seconds, shown separately).

## Next
0. Architect fix-forwards on c02632a: A-llm-4 and A-llm-5 done (this commit); A-llm-3: the host lines in c02632a stay byte-identical to what app.android re-commits on work/app (I will not change them; if app's copy differs, I take app's on merge).
1. Dinis measures E2B and E4B (GPU and CPU), plus 270M or 1B if he pushes them; I record the numbers here and the device confirmation of the pins for security's models.md.
2. Step 2: `LlmService` (generate, stream, cancel, tool calls with a JSON schema), the LiteRT-LM implementation, `FakeLlmService`; architect adds it to `FeatureContext`.
3. Step 3: feature G (download with resume and progress, storage check, Wi-Fi advice, verify, delete, adb fallback, privacy text).

## Blocked
- Nothing. The bench needs Dinis and the tablet.

## For app
Carried on this branch, as conductor agreed, in one separate commit for your review ("host lines for app.android's review"):
1. `app/build.gradle.kts` dependencies: `implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")`. Transitive: `com.google.code.gson:gson:2.14.0`, `org.jetbrains.kotlin:kotlin-reflect:2.4.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0`.
2. `app/build.gradle.kts` `kotlinOptions { freeCompilerArgs += "-Xskip-metadata-version-check" }`. Every LiteRT-LM release since 0.9 is built with Kotlin 2.2 or 2.4 metadata, and we are on Kotlin 2.0.21. Google's Edge Gallery uses the same flag. The other fix is the Kotlin, AGP and BOM bump you already found blocks the gate.
3. `app/proguard-rules.pro`: `-keep class com.google.ai.edge.litertlm.** { *; }` (JNI entry points and reflection; the AAR ships no consumer rules). Checked: the mapping keeps `Engine` and the rest of the package.
4. `AndroidManifest.xml` inside `<application>`: `<uses-native-library android:name="libOpenCL.so" android:required="false" />`, so the GPU backend can load the vendor OpenCL on API 31+. No permission, no component.
5. Diagnostics: an optional `onModelBench` parameter (button `diagnostics_model_bench`, en and pt-PT, 48dp), and a built-in route `Routes.MODEL_BENCH = "modelbench"` that shows `ModelBenchScreen()` in `FeatureFrame(feature_models_title)`. Back goes home like every route. Test in `DiagnosticsTest`. When G is switched on, the route can go.
6. Size: the release APK goes from about 0.9 MB to **50 MB**, all of it `liblitertlm_jni.so` stored uncompressed for arm64-v8a (22 MB) and x86_64 (26 MB). AAB 24.8 MB; Play would split per ABI. `ndk { abiFilters += "arm64-v8a" }` would halve the APK, but release's x86_64 emulator smoke test could no longer install it. app and release decide.

## For architect
- Review the dependency and `-Xskip-metadata-version-check` (above).
- Storage: your page says `filesDir/models/`, security's G.3 says `noBackupFilesDir`. I used `noBackupFilesDir/models/` (never backed up, still app-private). Say if you want `filesDir/models/` instead.
- Step 2 follows your contract (`LlmHost`, `state: StateFlow<LlmState>`, `stream(LlmRequest): Flow<LlmEvent>`, cancellation by coroutine, the service never executes tools).

## For security
- Licence and OSV for litertlm-android 0.18.1 and its three transitive deps. The AAR carries `THIRD_PARTY_NOTICE.txt` for its native code. Merged-manifest diff: nothing added by the AAR (see Done).
- `models.md`: please change the rows to the generic files pinned to commits (values in the table above, reason under it), not `-gpu` at `main`. The two gated Gemma 3 rows are adb-only bench entries, not offered by G.
- Download path for G (step 3): `https://huggingface.co/<repo>/resolve/<commit>/<file>` answers 302 to Hugging Face's CDN (seen: `us.aws.cdn.hf.co`, signed URL). So the pinned host list needs the redirect host(s), or the downloader follows exactly one redirect to an https `*.hf.co` host. The SHA-256 check is what makes the bytes trustworthy either way.
- The adb folder (`/sdcard/Android/data/ai.riskmandate.app/files/models/`) is app-specific external storage: on Android 11+ other apps cannot read or write it, but adb can. Files there are checked against the pinned SHA-256 before the first load in each screen visit, keyed to path, size and modification time. Only the bench (step 1) loads from there directly; G (step 3) copies the file into `noBackupFilesDir/models/` and verifies it again (your G.4).

## For dev.mandate
- Once G downloads, model files live in `files/models/` (and the bench reads `Android/data/.../files/models/`). Your reach view lists files under `files/`, so this needs a mandate entry when G lands.

## For Dinis
- Dinis: the third, smallest model. Every small Google LiteRT-LM model is gated on Hugging Face (Gemma licence, login required): Gemma 3 270M (304 MB) and Gemma 3 1B int4 (584 MB). The only ungated small Google files are FunctionGemma 270M "mobile actions" fine-tunes (Gemma licence), which are tool-calling demos, not chat models. Options: (a) you accept the Gemma licence on Hugging Face, download 270M or 1B yourself and push it with adb (the bench already knows both files and their hashes), and we decide later whether G offers it, which would need a download host other than gated Hugging Face; (b) the default list is just E2B and E4B; (c) something else. I recommend (a) for the measurement and (b) for G until you say otherwise.
