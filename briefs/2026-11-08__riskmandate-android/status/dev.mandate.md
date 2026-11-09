# dev.mandate.android status

> Feature C works end to end and is switched on (`enabled = true`); gate green. On `dev` with FeatureContext v2 merged. The gap on release was 1 (exported ProfileInstallReceiver); app.android is removing it, so it should read 0 (see "For app").

## Doing
Waiting for conductor review. design.android's shared components have not landed (no `ui/` package on this branch), so the screen uses plain Material 3 with the app theme; swap them in when they land.

## Done
- `features/mandate/MandateModel.kt`: pure Kotlin. `ReachItem`, `Mandate`, `MandateReport` (gap = reach minus mandate, computed, never stored), `HostProbe` (the real `Links.isAllowed` tried on six sample addresses).
- `features/mandate/ReachCollector.kt`: reach read at runtime from `PackageManager` (requested and granted permissions, exported activities, services, receivers and providers with the permission that guards them), application flags (cleartext, backup) and the files the app keeps (files, databases, shared_prefs).
- `features/mandate/MandateFeature.kt`, `MandateScreen.kt`: the screen (reach, mandate, gap, device management state, managed configuration values, the three definitions quoted). Gap said in words, not colour alone. Back button 48dp, survives 200% font scale.
- Strings in `res/values/feature_mandate.xml` and `res/values-pt-rPT/feature_mandate.xml` (a test fails if the two differ). The title string `feature_mandate_title` stays in `features.xml` (not mine).
- Tests: `MandateModelTest` (11, JVM), `MandateScreenTest` (12, Robolectric: en and pt-PT render, a gap rendered, real collection on the real manifest, end-to-end `MandateFeature.Content`, 200% font, touch target, translation parity).
- Gate `./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease`: pass.

- Second round (conductor, 9 Nov): merged `origin/dev` (FeatureContext v2: the feature now takes `config` and `device` from the context, no Activity, no second read). qa's FeatureLinksTest flagged URL literals in `MandateModel.kt`; the on-screen refusal probes are now built from parts (`HostProbe.Sample(scheme, host, port, userinfo)`), so no URL literal remains in feature sources and the probes still run through the real `Links.isAllowed`.

- Third round (architect HOLD on 78ae8d6), merged `origin/dev` again:
  - A-man-1: the screen draws content only (no Back, no title, no insets); the host's FeatureFrame draws those.
  - A-man-2: already clean without an exemption: the probes are built from parts, no URL literal in feature sources, so `MandateModel.kt` needs no `// qa: probe` marker or qa exemption. Say if architect still wants the marker.
  - A-man-3: `context.config` and `context.device`, and the report is re-read when either changes.
  - A-man-4: reach now lists each file in files/, databases/ and shared_prefs/ by path, and app_webview/ and cache/ as directories with a file count. The mandate has entries for `shared_prefs/stand_mode.xml` (stand's two settings, taken from `work/dev.stand`'s `StandSettings.FILE`), `app_webview` and `cache` (the in-app view). A stray file is gap; the tests check both. If stand's file name changes, change the entry in `Mandate.current`.
  - A-man-6: reach is read on `Dispatchers.IO` in a `LaunchedEffect`, with a loading line.
  - A-man-7: `ManagedConfig.KEY_*` instead of literals.
  - A-man-5 not done: `Links.ALLOWED_HOSTS` is private (not my file). See "For app".

- Fourth round (security R-site-1): the "nothing leaves the device" sentence is gone. Network the app makes itself is now computed: `networkReach(enabledFeatureIds)` adds `connects:riskmandate.ai` and `connects:www.riskmandate.ai` as reach exactly when the `site` feature is enabled in `FEATURES`; the mandate allows those two hosts (reason: the in-app site view) and nothing else, so a connection to any other host would show as gap (tested). With site off the screen says the app connects to nothing itself (pages open in the browser, which makes its own connection). The data line now reads "Data collected by the app: none. It has no analytics, accounts or identifiers. The only network traffic is loading riskmandate.ai pages, in your browser or in the in-app view." (en and pt-PT).
- Sixth round (security HOLD on 5a43197, merged `origin/dev` 0535543): the two strings use security's wording ("Data collected by this app: none. The in-app view loads riskmandate.ai pages over HTTPS, like a browser. This app adds no identifier."; reason "to open riskmandate.ai in your browser or in the in-app view"), en and pt-PT; the dated Data safety reference is gone; the `MandateModel.kt` comment is updated. **One deliberate omission:** security's text adds "the site says it keeps no analytics and no logs". I re-read https://riskmandate.ai/ on 9 Nov 2026 and it says neither (it does not mention analytics, cookies or logs; its Privacy page is a separate page I did not read), so I left the sentence out: no claim beyond what riskmandate.ai says (CLAUDE.md). If security reads the Privacy page and finds the wording, I will add it with the URL. Adopted design's `FeatureContent`, `SectionCard` and `StatusChip` (gap chip with a glyph and a word).

- Fifth round (security HOLD on two strings): the data line and the INTERNET reason now say what is true: the app itself connects only to riskmandate.ai and www.riskmandate.ai, only through the in-app site view; pages opened in the browser are the browser's own traffic; links to other sites are not loaded in the app. I did not write "handed to the browser on a tap": `work/dev.site`'s `SitePolicy` hands them to `Browser.open`, which refuses what `Links.isAllowed` refuses, so I claim only what holds. Re-check against dev.site's final code.

- Seventh round (conductor): `MandateScreenshotTest` writes `app/build/screenshots/mandate-<size>-<theme>.png` for phone and 10" tablet, light and dark (viewport, and `-full` for the whole scrolling screen: 8 files). Fixed clean reach with the site view on, so the picture does not depend on the machine. Read them: no clipping at the tablet width, chip and sentence both say "no gap". The screen quotes riskmandate.ai's privacy page: "There is no analytics, no tag manager, no advertising pixel and no cookie set by this site." (riskmandate.ai/privacy, read 9 Nov 2026; verified on the page myself, not only from the relay). The page also says operators have "no log access"; I did not add that, since the app's own claim is only what the sentence above supports.

- Eighth round (conductor, P7 on dev 1202040): `MandateFeature` overrides `icon` (`Icons.Filled.CheckCircle`, material-icons-core), `kicker` ("Live on this device") and `summary` ("This app's own reach, mandate and gap, read from the device as you open it."), en and pt-PT, no claim about RiskMandate. Open from architect (A-llm-10): list `noBackupFilesDir/models/` in the mandate when dev.llm's model setup lands on dev; not on dev yet, so not done.

- Ninth round (conductor R-shell-1, architect A-llm-10), merged `origin/dev` 46be6dd and later: reach now also scans `noBackupFilesDir` (top-level files by path, folders as directories with a file count) and the app's own folder on shared storage (`getExternalFilesDir`, ids `external/...`), because chat history and models live there. New mandate entries, each with a translated reason, all "on this device only": `shared_prefs/shell.xml` (sidebar collapsed or not), `shared_prefs/feature_releases.xml` (last update note shown), `no_backup/feature_chat` (chat conversations, never backed up), `no_backup/models` and `external/models` (model files). A stray file in `no_backup/` is gap (tested). Models are mandated at folder level, not by file name: dev.llm's `ModelFiles` is on `work/dev.llm` (`DIR = "models"`), not on dev yet; if you want exact file names, tell me when `llm/` lands. `shell.xml` is taken from the conductor's message: no `getSharedPreferences("shell")` is on dev yet.

- Tenth round (conductor, from dev.llm G): mandate entries for `shared_prefs/feature_models.xml` (the chosen model) and for the network to `huggingface.co`, `hf.co` and `*.hf.co` (reason: the model setup's Download button, only when tapped). The hosts are what dev.llm's `DownloadPolicy` accepts (read on `work/dev.llm`; the code is not on dev yet). Reach counts these hosts when the `models` feature is enabled in `FEATURES` (same mechanism as `site`). **Caveat:** dev.llm keeps `models` at `enabled = false` and reaches the setup screen through its own route, so until that changes the screen will not list the Hugging Face hosts as reach even though the code can connect. Once `llm/` is on dev I will look for a better signal (or the route being reachable) and switch to exact model file names; tell me if you would rather count the hosts as reach whenever the download code is in the build. Tests now take the mandate's size from the mandate instead of a hardcoded number.

- Eleventh round (conductor ruling, llm/ on dev 25d9cb7): the mandate shows what the code can reach, not what is switched on. `modelDownloadReach()` asks the real `DownloadPolicy.hostAllowed` about seven candidate hosts (the three it accepts, and four look-alikes that must be refused), so Hugging Face (`huggingface.co`, `hf.co`, `*.hf.co`) is always reach, and if the policy ever accepts another host it shows as gap. Reason: "only when you tap Download in Model setup, to fetch a model from here" (en and pt-PT; the screen is titled "Model setup" / "Configurar modelo"). Model files are mandated by exact name from `ModelCatalog.ALL`: in `no_backup/models` the file, its `.part` and its `.<name>.verified` marker; in `external/models` the file and its marker. Any other file there is gap (tested). The collector lists the `models/` folders file by file. This changes the old caveat: the screen now lists the Hugging Face hosts on every build.

- Twelfth round (design ruling, `docs/design/android-theme.md` "Icons", on `work/design`): `Feature.icon` is left null, so the host's id-based line icon applies; my `Icons.Filled.CheckCircle` override is removed. Kicker and summary stay.

- Thirteenth round (conductor): mandate entry `no_backup/feature_workbench` for the Workbench run log (reason: settings, figures, the first 200 characters of each prompt and 300 of each answer, at most 500 runs, on this device only, never backed up; limits checked against `RunLog.kt` on `work/dev.llm`: `PROMPT_CHARS = 200`, `ANSWER_CHARS = 300`, `MAX_ENTRIES = 500`). Folder level, like chat. **Still to do, when dev.chat's policy code is on dev:** `no_backup/feature_cloud` (the encrypted key) and a `connects:openrouter.ai` entry derived from its URL policy the way the Hugging Face hosts are (asked of the real code, look-alikes included). I did not add them from the message alone: no such code is on dev, and the mandate must not claim storage or network that a build does not have.

- Fourteenth round (security R-wb-5, urgent): the Workbench run log is now mandated by exact file, `no_backup/feature_workbench/runs.jsonl`, built from `RunLogStore.DIR` and `RunLogStore.FILE` (the real code, on dev); the collector lists that folder file by file, so any other file there (for example a `.tmp`) is gap (tested). Reason wording: "settings, figures, the first 200 characters of each prompt and 300 of each answer; on this device only, never backed up; at most 500 runs, and a file over 4 MB is ignored". **Wording note:** `RunLog.kt`'s 4 MB (`MAX_BYTES`) is a read cap (a bigger file is ignored on load), not a limit on writing; the file stays small because it is trimmed to 500 entries and each prompt and answer is cut. I said "ignored", not "at most 4 MB", so the screen does not overstate.

## Vocabulary (riskmandate.ai, https://riskmandate.ai/, read 9 Nov 2026)
- Reach: "Everything the agent can actually access in this deployment, including what nobody thought to check."
- Mandate: "The job, written down: what the business authorised it to do."
- Gap: "Reach minus mandate. Never written by hand, and recomputed whenever either side moves."
Here the "agent" is this app. Claims on screen go no further than these sentences and `docs/play/data-safety.md` (data collected: none).

## Next
Nothing until review, then design components when they land.

## Blocked
Nothing.

## For app (app.android)
The release merged manifest exports `androidx.profileinstaller.ProfileInstallReceiver` (`exported="true"`, guarded by `android.permission.DUMP`, which only the shell and system hold). The mandate does not need it, so the screen's gap is **1** on a release build, shown plainly. Removing it is a manifest change outside my paths: `<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" tools:node="remove" />` (check first that dropping baseline-profile installation is acceptable). After that the gap reads none. Debug builds also export test activities from `ui-test-manifest`; those never ship.

Also (A-man-5): make `Links.ALLOWED_HOSTS` public so `Mandate.HOSTS` can use it instead of copying it. The run-time probes catch drift meanwhile.

Also: an icon for the `mandate` id in the host's line style (24 dp, 2 dp stroke), if the shell has none: it should show a check inside a shield or a gauge with a tick, as "this app's reach checked against its mandate"; design offered to draw it.

## For Dinis
Decision for the stand: leave the gap of 1 visible (it is true and makes the point of the feature), or have app.android remove the receiver so it reads none.
