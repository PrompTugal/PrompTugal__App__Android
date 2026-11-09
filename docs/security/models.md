# Models the app may offer

Owned by security.android. One row per model file, written **before** the model is offered in the app. A model that is not in this table is not offered, and the pinned SHA-256 in the code must equal the one here. Weight licences are separate from the licence of the runtime (LiteRT-LM, Apache 2.0).

| Model | Source URL (exact) | Licence (where read, date) | Size in bytes | SHA-256 | Offered as | Checked by, date |
|---|---|---|---|---|---|---|
| Gemma 4 E2B, instruction-tuned, LiteRT-LM, GPU build | `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm` | Apache-2.0 per the model card (conductor and play, 9 November 2026); **security has not read the card** | 2008432640 (`x-linked-size` header, 9 November 2026 about 02:05 UTC) | `a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5` (`x-linked-etag` header, which for a Hugging Face large file is its SHA-256) **to be confirmed by hashing the downloaded file (dev.llm)** | default | security, header values only |
| Gemma 4 E4B, instruction-tuned, LiteRT-LM, GPU build | `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-gpu.litertlm` | Apache-2.0 per the model card (conductor and play, 9 November 2026); **security has not read the card** | 2969059328 (`x-linked-size`) | `4912bb5a9c30993c51a7711f763212077458529312175df0573a78323a2bb7ff` (`x-linked-etag`) **to be confirmed by hashing the downloaded file (dev.llm)** | optional | security, header values only |
| Smallest ungated Google LiteRT-LM model | TODO (dev.llm finds it; `gemma-3-270m-it` and `Gemma3-1B-IT` are gated, so not usable without a login) | TODO | TODO | TODO | fast fallback | TODO |

How a row is filled: dev.llm downloads the file once on a trusted machine, records the size and `sha256sum`, and writes them here and in code; security reads the licence on the model page, confirms the URL has no token or login, and records the date. A new version of a model is a new row; the old row is kept with the date it was dropped.

## Download path observed on 9 November 2026 (HEAD requests only, no file downloaded)
`https://huggingface.co/litert-community/<repo>/resolve/main/<file>` answers **302** to `https://us.aws.cdn.hf.co/...` (a signed, short-lived URL), which answers 200 with the full length (equal to `x-linked-size`). So the download touches two hosts: `huggingface.co` and `us.aws.cdn.hf.co`. The CDN host name can vary by region; the pinned redirect rule is "https, at most five hops, final host ends in `.hf.co` or is `huggingface.co`", not the one exact name. Play's privacy text names both hosts.

## Trust note on the pinned values
The SHA-256 above comes from the same service that serves the file, so it proves the file is the one Hugging Face lists, not that it is trustworthy. dev.llm confirms it by hashing a file downloaded on a trusted machine (and, if cheap, a second download from another network) before pinning it in code. A later change of the file on Hugging Face makes the app refuse it (checksum mismatch) until the row and the code are updated together, with the licence re-read.
