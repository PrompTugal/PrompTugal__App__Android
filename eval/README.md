# eval: the evidence harness

Runs the Lab scenarios down two paths (raw, and through the ABP checks) against a list of models, and reports how often a small model fails and how often the policy catches it. Python standard library only; no secrets in code.

Scenario files, the demo graph and all results live in the data vault, not in this repository (Dinis, 9 Nov 2026). This directory holds the code, the scenario format and the export formats only.

```
python3 eval/run.py validate                 # load and check every scenario in the vault
python3 eval/run.py run --dry-run            # how many runs, worst-case cost
python3 eval/run.py run --max-cost 0.50      # run; hard stop at this invocation's cap and at $5.00 in total
python3 eval/run.py report ~/eval-results/2026-11-09 --out report.md
python3 eval/run.py curate ~/eval-results/2026-11-09 --out <vault>/recordings
python3 eval/run.py import-loop export.json  # the tablet's loop-mode export, rescored here
python3 -I eval/tests/test_harness.py        # unit tests, no network
```

Paths: `--vault` or `$EVAL_VAULT` (default `~/eval-vault`: `scenarios/`, `graph/`), `--results` or `$EVAL_RESULTS` (default `~/eval-results`).

## What a run is

For each model, scenario, temperature (0, 0.3, 0.7, 1.0), seed (three by default) and repetition, two runs: `raw` (the model's tool calls run on fake tools that record) and `abp` (every call is checked first; a blocked call returns a refusal to the model, which may try again). The seed is `base seed + 1000 x repetition`. Tools are driven by one text protocol for every model (`abpeval/protocol.py`), so hosted and on-device results are comparable; `--tool-mode native` uses the API's own tool calling.

- Oracle (`oracle.py`): what counts as a failure, judged from the run record alone.
- ABP checks (`abp.py`): the demonstration policy of each scenario: tool allow-list, read-only, egress recipients, secret egress, citations required, state consistency. One module, pure functions.
- The two are separate on purpose. Where the ABP check is a deterministic match (scenarios 1, 3, 4) a catch rate near 100% is by construction; the figures that carry information are the failure rate without ABP and the over-blocking. Scenario 5 is a regex heuristic on the answer text and is marked `(heuristic)`; curate by hand.

## Money

`abpeval/budget.py`: every call's cost comes from OpenRouter's `usage.cost`, is added to `<results>/budget.json` (persisted, file-locked, shared by all processes) and the harness refuses any call that could pass $5.00 in total or the invocation's `--max-cost`. The cap is clamped to $5.00 in code. The key comes from `OPEN_ROUTER_KEY` or `OPENROUTER_API_KEY`, is sent only to openrouter.ai, and is scrubbed from every record before it is written.

## Backends

1. `openrouter`: hosted weights, precision chosen by the provider (recorded: `provider`, and `quantisation: provider-chosen, unknown`). `models.json` says which Google size stands in for which on-device model; OpenRouter lists no Gemma 4 E2B or E4B.
2. `local-litert-lm`: the real Gemma 4 E2B weights (`gemma-4-E2B-it.litertlm`, checksum pinned in `models.json`, verified before load) on this machine's CPU through LiteRT-LM's Python API (`pip install litert-lm-api` in a venv outside the repository; run `run.py --backend local` with that interpreter). Native tool calling, as the app will use. 4 cores, about 10 to 20 s per run.
3. The tablet: loop-mode export in the same format, see `FORMATS.md`.
