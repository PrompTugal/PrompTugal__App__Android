# On-device model: weight licences and the in-app licence note

For the conductor and Dinis. Written 9 November 2026 by play.android@promptugal.ai, for the on-device LLM brief (`briefs/2026-11-09__on-device-llm/README.md`, features G and H). The app's own licences screen (`app/`, `res/raw/licences.txt`, `app/tools/make-licences.sh`) is **app.android's path, not mine**: the text below is a draft for app.android to adopt, routed through `status/play.md` ("For app"). Nothing here is legal advice.

## What I read (9 November 2026)

| Source | What it says |
|---|---|
| https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm | "License: apache-2.0". No licence text or link on the card. A quantised derivative of `google/gemma-4-E2B-it`. No gating notice shown. The excerpt I got lists `gemma-4-E2B-it.litertlm` (about 2583 MB on disk), a web build and a Qualcomm NPU file with its SHA-256 (a hackathon artifact, not for other chips); it does **not** list the `gemma-4-E2B-it-gpu.litertlm` name the conductor gave, and gives no SHA-256 for any other file. |
| https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm | "License: apache-2.0". No licence text. Main `.litertlm` about 3.66 GB; also a web build and a MediaPipe `.task`. No gating notice shown. |
| https://huggingface.co/google/gemma-4-E2B-it | "License: apache-2.0", linking "Apache 2.0" to https://ai.google.dev/gemma/docs/gemma_4_license. No gating or acceptance step shown. No prohibited-use policy stated; asks developers to "exercise caution and implement appropriate content safety safeguards". Citation is a suggestion. |
| https://ai.google.dev/gemma/docs/gemma_4_license | Heading "Apache License 2.0", "Version 2.0, January 2004": the standard text, **no extra terms and no prohibited-use policy**. |

So the conductor's note holds on the pages I could read: **Gemma 4 E2B and E4B weights are Apache 2.0, and I saw no gate.** **Update (G.8, from dev.llm's status page on `work/dev.llm`, which I read but did not re-check):** dev.llm pinned each file to an exact repository commit, name, size and SHA-256 in `llm/ModelCatalog.kt`, downloaded both Gemma 4 files from the pinned URLs once in its cloud session and hashed them end to end (both matched, nothing kept), and chose the generic `gemma-4-E2B-it.litertlm` (2,583,085,056 bytes) and `gemma-4-E4B-it.litertlm` (3,654,467,584 bytes) over the `-gpu` file I had been given. That closes my file-name question below; the catalogue is the source of truth for names, commits and hashes. Both entries are recorded there as `apache-2.0`, ungated; the two Gemma 3 files (270M, 1B) are **gated under the Gemma licence** and are bench-only (adb), **not offered by feature G**.

Caveats I cannot close from here:
- I read the cards through a fetch tool that returns excerpts. Re-read the licence line on the day of the build.
- **Earlier Gemma generations are different.** The conductor's note says `gemma-3-270m-it` and `Gemma3-1B-IT` are gated under the Gemma licence with a Hugging Face login. I did not read those pages. If dev.llm offers any non-Gemma-4 model as the third (smallest) one, its licence must be read and recorded first; a Gemma-terms model carries a use policy and flow-down terms that Apache 2.0 does not, and a login-gated download cannot work with a pinned, tokenless URL.
- LiteRT-LM and Edge Gallery are "Apache 2.0" per the brief; I did not read their repositories.

## What Apache 2.0 needs from us (section 4, from the licence text above)

We do not put the weights in the APK or the AAB: the app downloads them from Hugging Face on the user's tablet. Even so, the cheap and honest course is to treat the weights as a third-party component and record it:
1. Name the model, its publisher, its licence and where it came from, in the licences screen, with a copy of or link to the licence ("give recipients a copy of the License" applies when we redistribute; a link plus the bundled Apache text we already carry satisfies the spirit and costs nothing).
2. Keep any NOTICE text the weights ship with (none seen on the cards; check the repository files).
3. State that the file is a quantised conversion (the `litert-community` card says so) so nobody thinks Google published our exact file.
4. Do not imply Google endorses RiskMandate. The licence's trademark section gives no right to use Google's or Gemma's name as a mark; "Gemma" appears only as the model's name.

The runtime library (LiteRT-LM) is code we bundle: it goes into the generated list in `licences.txt` like every other dependency (`app/tools/make-licences.sh`, `scripts/licences.sh` from security). Check its artifact's licence before the first build with it.

## Draft: note for the licences screen (English)

```
Language model (downloaded on first use, not part of the app)
- Gemma 4 E2B, Gemma 4 E4B: published by Google as "gemma-4-E2B-it" and "gemma-4-E4B-it",
  converted to a quantised on-device format by the litert-community (Google AI Edge) on Hugging Face.
  Licence: Apache License, Version 2.0 (https://ai.google.dev/gemma/docs/gemma_4_license).
  The file is downloaded once from huggingface.co and checked against a fixed checksum.
- Only the two models above are offered for download. [A smaller model would be added here, with its licence read and recorded, if Dinis decides; Gemma 3 files are gated under the Gemma licence and are not offered.]
Runtime: LiteRT-LM (Google AI Edge), Apache License, Version 2.0 [TODO: version from the build].
RiskMandate is not endorsed by Google.
```

## Rascunho: nota para o ecrã de licenças (pt-PT)

```
Modelo de linguagem (transferido na primeira utilização, não faz parte da aplicação)
- Gemma 4 E2B, Gemma 4 E4B: publicados pela Google como "gemma-4-E2B-it" e "gemma-4-E4B-it",
  convertidos para um formato quantizado para o dispositivo pela litert-community (Google AI Edge) no Hugging Face.
  Licença: Apache License, Versão 2.0 (https://ai.google.dev/gemma/docs/gemma_4_license).
  O ficheiro é transferido uma só vez de huggingface.co e verificado com uma soma de controlo fixa.
- Só os dois modelos acima são oferecidos para transferência. [Um modelo mais pequeno seria acrescentado aqui, com a licença lida e registada, se o Dinis decidir; os ficheiros Gemma 3 têm acesso restrito sob a licença Gemma e não são oferecidos.]
Motor de execução: LiteRT-LM (Google AI Edge), Apache License, Versão 2.0 [TODO: versão da compilação].
O RiskMandate não é apoiado pela Google.
```

(Português escrito por uma IA; deve ser revisto por um falante nativo.)

## Checklist for dev.llm, security and app.android

- [x] For each model offered: exact repository URL, file name, size and SHA-256 are pinned in `ModelCatalog` (dev.llm). Still to do: re-read the licence line on the build day.
- [x] Download hosts: `huggingface.co` and hosts under `hf.co` (seen `us.aws.cdn.hf.co`), at most five redirects, https only (dev.llm's `DownloadPolicy`); named in the privacy draft.
- [ ] `licences.txt` / the licences screen gets the note above (app.android) and the runtime library.
- [ ] Privacy text and the in-app privacy screen carry the model-download and chat-history statements (`privacy-policy.md`, draft section marked for the build where G and H are on).
