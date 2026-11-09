# Models the app may offer

Owned by security.android. One row per model file, written **before** the model is offered in the app. A model that is not in this table is not offered, and the pinned SHA-256 in the code must equal the one here. Weight licences are separate from the licence of the runtime (LiteRT-LM, Apache 2.0).

| Model | Source URL (exact) | Licence (where read, date) | Size in bytes | SHA-256 | Offered as | Checked by, date |
|---|---|---|---|---|---|---|
| Gemma 4 E2B, instruction-tuned, LiteRT-LM, GPU build | `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm` | Apache-2.0 per the model card, as reported by the conductor on 9 November 2026; **not yet read by security** | TODO (dev.llm) | TODO (dev.llm, computed from the downloaded file) | default | TODO |
| Gemma 4 E4B, instruction-tuned, LiteRT-LM, GPU build | `https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-gpu.litertlm` | Apache-2.0 per the model card, as reported by the conductor on 9 November 2026; **not yet read by security** | TODO | TODO | optional | TODO |
| Smallest ungated Google LiteRT-LM model | TODO (dev.llm finds it; `gemma-3-270m-it` and `Gemma3-1B-IT` are gated, so not usable without a login) | TODO | TODO | TODO | fast fallback | TODO |

How a row is filled: dev.llm downloads the file once on a trusted machine, records the size and `sha256sum`, and writes them here and in code; security reads the licence on the model page, confirms the URL has no token or login, and records the date. A new version of a model is a new row; the old row is kept with the date it was dropped.
