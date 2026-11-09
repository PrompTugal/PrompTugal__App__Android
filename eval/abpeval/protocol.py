"""Text tool-call protocol, so every model (hosted or on device) is driven the same way.

The model replies with one JSON object per call: {"tool": "name", "arguments": {...}}.
Every call is parsed and validated here before anything runs: the model proposes, code decides.
"""
import json
import re

TEXT_PROTOCOL = (
    "You can call these tools:\n{tools}\n\n"
    "To call a tool, reply with only a JSON object on its own, exactly in this form:\n"
    '{{"tool": "<name>", "arguments": {{<arguments>}}}}\n'
    "You will then receive the tool result. When you have what you need, reply in plain text."
)


def tools_block(tools):
    lines = []
    for t in tools:
        props = t["parameters"].get("properties", {})
        sig = ", ".join(f"{k}: {v.get('type', 'string')}" for k, v in props.items())
        lines.append(f"- {t['name']}({sig}): {t['description']}")
    return "\n".join(lines)


def system_with_tools(system, tools):
    if not tools:
        return system
    return system + "\n\n" + TEXT_PROTOCOL.format(tools=tools_block(tools))


_GEMMA_CALL = re.compile(r"<\|tool_call>\s*call:\s*([A-Za-z_][\w]*)\s*\((.*?)\)\s*<tool_call\|>", re.S)
_KV = re.compile(r'([A-Za-z_]\w*)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|<\|"\|>(.*?)<\|"\|>|([^,\s)]+))', re.S)


def _gemma_calls(text):
    """Gemma 4 sometimes answers in its own call syntax: <|tool_call>call:name(key="value")<tool_call|>."""
    out = []
    for m in _GEMMA_CALL.finditer(text):
        args = {}
        for k in _KV.finditer(m.group(2)):
            args[k.group(1)] = next(g for g in (k.group(2), k.group(3), k.group(4)) if g is not None)
        out.append({"name": m.group(1), "arguments": args})
    return out


def parse_calls(text):
    """Return a list of {'name','arguments'} found in text: JSON objects with a 'tool' key, or Gemma 4's own call syntax."""
    calls, dec, i = _gemma_calls(text), json.JSONDecoder(), 0
    while True:
        i = text.find("{", i)
        if i < 0:
            return calls
        try:
            obj, end = dec.raw_decode(text, i)
        except ValueError:
            i += 1
            continue
        i = end
        if isinstance(obj, dict) and isinstance(obj.get("tool") or obj.get("name"), str):
            args = obj.get("arguments", obj.get("args", obj.get("parameters", {})))
            calls.append({"name": (obj.get("tool") or obj.get("name")).strip().removesuffix("()"), "arguments": args})


def validate_call(call, tools):
    """None if valid, else a short reason. Checks the name is offered and required arguments are present."""
    spec = next((t for t in tools if t["name"] == call["name"]), None)
    if spec is None:
        return f"unknown tool '{call['name']}'"
    args = call["arguments"]
    if not isinstance(args, dict):
        return "arguments must be an object"
    for k in spec["parameters"].get("required", []):
        if k not in args:
            return f"missing argument '{k}'"
    for k in args:
        if k not in spec["parameters"].get("properties", {}):
            return f"unexpected argument '{k}'"
    return None


def native_tools(tools):
    return [{"type": "function", "function": {"name": t["name"], "description": t["description"], "parameters": t["parameters"]}} for t in tools]
