# Tool tests: case format `abp-tooltest/1`

Owner: dev.lab. One JSON file per kind, read by `LabAssets.loadToolTests`. All data is synthetic and the tools are fakes.

```
{ "format": "abp-tooltest/1", "synthetic": true, "kind": "right_tool", "title": "Picks the right tool",
  "cases": [ {
    "id": "right-tool-weather",
    "tests": "One line: what this case tests.",
    "system": "optional system prompt",
    "messages": [ {"role": "user", "text": "..."},
                  {"role": "assistant", "text": "", "tool_calls": [{"name": "get_weather", "arguments": {"city": "Lisbon"}}]},
                  {"role": "tool", "name": "get_weather", "text": "18 C, sunny"} ],
    "tools": [ {"name", "description", "parameters": {"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]}} ],
    "fake_tools": { "get_weather": {"returns": "18 C, sunny"} },
    "checks": [ {"type": "first_call_is", "tool": "get_weather"}, {"type": "args_valid"} ],
    "max_steps": 3,
    "reference": {"steps": [ {"text": "", "calls": [{"name": "...", "arguments": {}}]}, {"text": "final answer"} ]},
    "wrong":     {"steps": [ ... ]}
  } ] }
```

- 2 to 5 tools per case, flat arguments (string, integer, number, boolean), at most 6 per tool, names `[a-z][a-z0-9_]{0,39}`.
- `fake_tools` per tool: `{"returns": X}` or `{"by": arg, "table": {...}, "default": ...}`.
- Check types: `first_call_is`, `calls_in_order`, `args_valid`, `arg_equals` (rules `exact`, `ci`, `contains`, `range`, `one_of`), `no_tool_call`, `no_unoffered_tool`, `no_secret_in_args`, `answers_after_result`, `max_calls`, `final_contains`. The parameters of each are in `ToolChecks.kt`.
- `reference` is a scripted run that must pass every check and `wrong` one that must fail at least one: the JVM tests run both over `FakeLlmService` in both modes, so CI checks the checks.
- Two modes are measured for every case: `NATIVE` (the runtime's tool calling) and `TEXT` (a JSON call written in the answer).
