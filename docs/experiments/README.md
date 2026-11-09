# Tool tests: the case format, the checks and the results

Owner: dev.lab.android@promptugal.ai. Platform-neutral on purpose: plain UTF-8 JSON, lowercase enums, no Kotlin or Android names, explicit units, so the iPad app can read the same files and implement the same checker (shared repo PrompTugal__App__Mobile, to be agreed). The schema is [`tooltest-case.schema.json`](tooltest-case.schema.json) (JSON Schema draft 2020-12). Cases live in `app/src/main/assets/experiments/tooltests/`, one file per kind. All data is synthetic, the tools are fakes, and nothing executes for real.

## What a tool test is

A small, deterministic functional check of tool calling on a model: the model is given a conversation and 2 to 5 tools, and **code** (never a model) decides whether it called the right tool the right way. It measures two ways of asking for a call, per case:

| Mode | How the model is asked | Where its call is read from |
|---|---|---|
| `native` | the tools are declared to the runtime | the runtime's own tool-call proposals |
| `text` | the tools are described in the system prompt (see below) and the model writes a JSON call in its answer | the answer text |

The text protocol sentence the runner appends in `text` mode: the tools, one per line as `- name(arg: type, optional?: type): description`, then "To call a tool, reply with only a JSON object on its own, exactly in this form: `{"tool": "<name>", "arguments": {<arguments>}}`. You will then receive the tool result. When you have what you need, or when no tool fits, reply in plain text." A call is any JSON object in the answer with a string `tool` (or `name`) and an `arguments` object, found by a strict, bounded scan: at most 16,000 characters scanned, 16 calls read, 4 calls accepted per turn.

## A case file

```
{ "format": "abp-tooltest/1", "version": "1", "synthetic": true, "kind": "right_tool", "title": "Picks the right tool",
  "cases": [ { "id", "tests", "system"?, "messages", "tools", "fake_tools", "checks", "max_steps"?, "reference"?, "wrong"?, "judges"? } ] }
```

- `format` names the format and its version. `version` is the file's content version, bumped when its cases change. `kind` is a short lowercase name; the screen groups cases by it.
- `id`: lowercase letters, digits and hyphens, unique across the catalogue. `tests`: **one line** (at most 200 characters) saying what the case tests. Claims are never stronger than what the checks can see.
- `messages`: the starting conversation, first message the user's. Roles `user`, `assistant` (with optional `tool_calls`: `{name, arguments}`) and `tool` (with `name`, the tool whose result it is). A conversation that already holds a tool result tests what the model does after it.
- `tools`: 2 to 5 tools, each `{name, description, parameters}` with `parameters` a flat JSON schema: `{"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]}`. Argument types are `string`, `integer`, `number`, `boolean`; at most 6 arguments per tool; names `[a-z][a-z0-9_]{0,39}`. Nothing nested.
- `fake_tools`: exactly one entry per offered tool, `{"returns": <any JSON>}` or `{"by": "<arg>", "table": {...}, "default": ...}`. A fake returns canned data. Its result goes back to the model as text (a string as it is, anything else as compact JSON), cut at 2,000 characters.
- `max_steps` (default 3, at most 6): model turns allowed. A run ends when the model answers without calling a tool, or at the limit.
- `reference` / `wrong`: scripted runs, `{"steps": [{"text": "...", "calls": [{"name", "arguments"}]}, ...]}`. The reference **must pass every check** and the wrong one **must fail at least one**. They are fixtures for testing an implementation of the checker: run both through yours.
- `judges` (optional, for the judge layers): `{"ask": "<one question for a model judge about the output>", "expect": "pass" | "fail"}`, the verdict a correct judge gives for the reference run. The code checks stay the verdict; judges are recorded beside them and every disagreement is data.

## The checks

A check is `{"kind": ..., ...}` plus an optional `label`. Each kind, its parameters, and what a failure is called (a lowercase key that the screen shows as a plain phrase):

| kind | parameters | passes when | failure key |
|---|---|---|---|
| `tool_called` | `name`, `position` (`any` default, or `first`) | the tool was called (position `first`: it was the first call). By name, whatever the arguments | `wrong_tool` |
| `tools_called_in_order` | `names[]` | the calls, in order, are exactly these tools | `wrong_tool` |
| `args_valid` | | at least one call, and every call was accepted: offered tool, required arguments present, no unknown arguments, types right, strings within 2,000 characters, the argument object within 4,096 characters, nothing nested | `arguments_invalid` |
| `args_equal` | `name`, `arg`, `rule`, `which` (`first` default, `last`, `any`) and the rule's values | an **accepted** call to the tool has the expected value. Rules: `exact` + `value` (numbers compare numerically); `ci` + string `value` (trimmed, case-insensitive); `contains` + string `value` (case-insensitive); `range` + `min`, `max` (a number within, inclusive); `one_of` + `values[]` (trimmed, case-insensitive) | `value_mismatch` |
| `no_tool` | | no call at all, and a non-empty text answer | `tool_when_none_fits` |
| `no_unoffered_tool` | | every call names an offered tool | `unoffered_tool` |
| `value_absent` | `value` | the value is in no call's raw argument text (compared ignoring case and anything that is not a letter or digit) | `leaked_value` |
| `answers_after_result` | `contains[]?` | the last step made no call, the final text is not empty, and (when given) contains at least one of the words, case-insensitive | `no_answer_after_result` |
| `max_calls` | `max` (0 to 10) | at most this many calls | `too_many_calls` |
| `answer_contains` | `any[]` | the final text contains at least one of the words, case-insensitive | `answer_missing_words` |

A case passes when **every** check passes and the run finished. A run that did not finish (the runtime failed) is in no pass rate: it is counted apart.

## What a run records, and the results file

One run of one case in one mode is an `abp-eval-run/1` record (the same record eval reads for scenarios), inside the Lab's export envelope `abp-lab-loop-export/1` with `"kind": "tool-tests"`. Fields added for tool tests:

- `path`: `native` or `text`. `tool_test`: `{kind, case}`. `scenario`: `{id: <case id>, version, hash: <catalogue hash>}`; the hash is the first 16 hex characters of the SHA-256 of the canonical JSON (sorted keys, no spaces) of the catalogue's files.
- `steps[]`: `assistant_text`, `tool_calls[]` with `name`, `arguments`, `raw_arguments` (the text as the model wrote it), `valid`, `executed`, `result`, `refusal`.
- `checks[]`: `kind`, `label`, `passed`, `failure` (the key above, or null), `detail` (plain English).
- `prompt`: `{"chars": <characters of text sent in the first request in this mode>, "tokens": <what the runtime reported for it, or null>}`. Characters are the system prompt (with the text protocol in `text` mode), the messages, and, in `native` mode, the tools' names, descriptions and argument names and types.
- `total_ms`, `tokens.output`, `tokens.decode_tokens_per_second` (null where the runtime does not report them), `error` (null, or why it did not finish), `outcome.passed`.
- Envelope: `summary` with `passed`, `finished`, `not_finished` and `mean_ms` for `overall`, `by_kind` and `by_case`, each per mode; `battery` (`start_pct`, `end_pct`), `stop_reason`, `settings`, `device` (the OS version only: no identifiers).

## Caps

A run of many cases is capped like every unattended run in the app: at most 100 runs and 30 minutes, a stop at 15% battery when not charging, a stop on severe thermal status, a stop after 3 runs in a row that did not finish, and a stop on request. "All cases, both modes" is 40 runs per repeat. An implementation on another platform keeps the same caps.

## Versioning

Changing the meaning of a field or a check, or removing one, is `abp-tooltest/2` with its own schema file. Adding a case, or a new optional field a reader may ignore, bumps the file's `version` only.

## Judges: three layers, and every disagreement is data

A run is judged in layers, always in this order, and the verdicts are recorded side by side.

| Layer | Judge | What it is |
|---|---|---|
| `code` | the checks above | pure code; authoritative wherever it can decide |
| `local` | the on-device model | a short judging prompt; its reply is read strictly |
| `cloud` | a cloud model | the same prompt through the cloud service; shown with the "Cloud" badge, model id and "via openrouter.ai" |

A verdict is `pass`, `fail` or `unsure`, with a one-sentence `reason` (plain text, at most 300 characters), the `layer`, the `model_id` (null for code) and the `prompt_version` (null for code).

- **Code is authoritative where it can decide.** A run that finished and has checks gets a code verdict, and that is the final one. Model verdicts are recorded beside it but **never override it**. Where code cannot decide (the run did not finish, or the case has no checks) the final result is `unsure` and the model verdicts are shown side by side; none is promoted.
- **`unsure` is never a pass.** A model judge that errors, returns text that is not exactly one JSON object `{"verdict": "pass" | "fail" | "unsure", "reason": "..."}` (a fenced or prose-wrapped single object is read; two objects, a bad verdict word, a repeated key, a trailing comma, or more than 4,000 characters are not), or is asked about a run that did not finish, is `unsure`.
- **A disagreement** is two layers that both decided and decided differently. Every one is recorded (case, kind, mode, repeat and both verdicts, with the run in `runs`) and counted per case and per kind, with how often each model layer agreed with code.
- **What a judge sees:** the version-tagged prompt, the case's one-line `tests`, its `judges.ask` question, the offered tools (names and argument names), what the user asked, and the output under test as delimited data. Nothing else: not the case's system prompt, no device or user data, nothing from another feature. The output under test is untrusted: a judge can be argued with by it, which is why code is authoritative.

### Judge prompt files (`abp-judge-prompt/1`, schema [`judge-prompt.schema.json`](judge-prompt.schema.json))

`{format, version, id, system, template}` in `app/src/main/assets/experiments/judges/`. The template uses exactly `{{tests}}`, `{{ask}}`, `{{tools}}`, `{{request}}`, `{{output}}`. The `version` is stored with every verdict, so a change of prompt is never mistaken for a change of model; bump it whenever the text changes.

### In the results

Each run record gains `judges: {final, decided_by, disagreed, verdicts[]}` (each verdict `{layer, outcome, reason, model_id, prompt_version}`, `outcome` lowercase). The envelope gains `disagreements[]` (case, kind, path, rep, the two verdicts) and `summary.judges`: `judged`, `disagreements` (`overall`, `by_kind`, `by_case`) and `agreement_with_code` (`agreed` of `compared`, per model layer). A run with no judge panel carries none of these.
