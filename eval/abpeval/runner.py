"""Run one scenario down one path ('raw' or 'abp') against one model with one setting. Returns a run record."""
import hashlib
import json
import time

from . import RUN_SCHEMA, abp, fake_tools, protocol
from .oracle import failure_events  # noqa: F401  (re-exported for tests)


def run_id(scn, model_id, path, settings, rep):
    k = f"{scn['id']}|v{scn['version']}|{model_id}|{path}|{settings['temperature']}|{settings.get('seed')}|{rep}|{settings.get('top_p')}|{settings.get('tool_mode')}"
    return hashlib.sha1(k.encode()).hexdigest()[:16]


def run_one(backend, model, scn, path, settings, rep=0, backend_label="openrouter", batch=None):
    assert path in ("raw", "abp")
    mode = settings.get("tool_mode", "text")
    tools = scn["tools"]
    system = scn["system"] if mode == "native" else protocol.system_with_tools(scn["system"], tools)
    messages = [{"role": "system", "content": system}, {"role": "user", "content": scn["user"]}]
    rec = {"schema": RUN_SCHEMA, "run_id": run_id(scn, model["id"], path, settings, rep), "batch": batch,
           "ts": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
           "scenario": {"id": scn["id"], "version": scn["version"], "hash": scn["_hash"]},
           "model": {"id": model["id"], "label": model.get("label"), "closest_to": model.get("closest_to"), "quantisation": model.get("quantisation")},
           "backend": backend_label, "path": path, "rep": rep,
           "settings": {k: settings.get(k) for k in ("temperature", "top_p", "seed", "max_tokens", "tool_mode")},
           "prompt": {"system": system, "user": scn["user"]}, "steps": [], "final_text": "", "ended": None,
           "provider": None, "cost_usd": 0.0, "tokens": {"prompt": 0, "completion": 0}, "error": None}
    t0 = time.time()
    try:
        for si in range(scn["max_steps"]):
            r = backend.chat(model, messages, protocol.native_tools(tools) if mode == "native" and tools else None, settings)
            rec["provider"] = r.get("provider") or rec["provider"]
            rec["cost_usd"] += r.get("cost", 0.0)
            rec["tokens"]["prompt"] += r["usage"].get("prompt_tokens") or 0
            rec["tokens"]["completion"] += r["usage"].get("completion_tokens") or 0
            if mode == "native":
                calls = []
                for tc in r.get("native_calls", []):
                    try:
                        a = json.loads(tc["function"].get("arguments") or "{}")
                    except ValueError:
                        a = tc["function"].get("arguments")
                    calls.append({"name": tc["function"]["name"], "arguments": a})
            else:
                calls = protocol.parse_calls(r["text"]) if tools else []
            step = {"i": si, "assistant_text": r["text"], "reasoning": r.get("reasoning"), "finish": r.get("finish"),
                    "latency_ms": r["latency_ms"], "usage": r["usage"], "cost_usd": r.get("cost"), "tool_calls": []}
            rec["steps"].append(step)
            if not calls:
                rec["final_text"], rec["ended"] = r["text"], "answer"
                break
            if mode == "native":
                for k, c in enumerate(calls):
                    c["id"] = f"call_{si}_{k}"
                messages.append({"role": "assistant", "content": r["text"] or None, "tool_calls": [
                    {"id": c["id"], "type": "function", "function": {"name": c["name"], "arguments": json.dumps(c["arguments"])}} for c in calls]})
            else:
                messages.append({"role": "assistant", "content": r["text"]})
            results = []
            for c in calls:
                why = protocol.validate_call(c, tools)
                tc = {"name": c["name"], "arguments": c["arguments"], "valid": why is None, "invalid_reason": why,
                      "verdict": None, "executed": False, "result": None}
                if why:
                    tc["result"] = {"error": why}
                else:
                    if path == "abp":
                        tc["verdict"] = abp.check_call(scn, c)
                    if tc["verdict"] and tc["verdict"]["action"] != "allow":
                        word = "blocked" if tc["verdict"]["action"] == "block" else "held for approval"
                        tc["result"] = {"error": f"{word} by policy rule {tc['verdict']['rule']}: {tc['verdict']['reason']}"}
                    else:
                        tc["executed"] = True
                        tc["result"] = fake_tools.execute(scn, c["name"], c["arguments"])
                step["tool_calls"].append(tc)
                if mode == "native":
                    messages.append({"role": "tool", "tool_call_id": c["id"], "name": c["name"], "content": fake_tools.as_text(tc["result"])})
                else:
                    results.append(f"Tool result for {c['name']}: {fake_tools.as_text(tc['result'])}")
            if mode != "native":
                messages.append({"role": "user", "content": "\n".join(results)})
        else:
            rec["ended"] = "max_steps"
            rec["final_text"] = rec["steps"][-1]["assistant_text"] if rec["steps"] else ""
    except Exception as e:  # recorded, excluded from rates, never hides which model/cell failed
        rec["error"] = f"{type(e).__name__}: {str(e)[:300]}"
        rec["ended"] = "error"
    rec["total_ms"] = int((time.time() - t0) * 1000)
    rec["messages"] = messages
    if path == "abp":
        rec["abp_flags"] = abp.check_final(scn, rec)
    rec["outcome"] = abp.outcome(scn, rec) if not rec["error"] else None
    return rec
