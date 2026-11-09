"""Export formats: curated replay presets (abp-lab-recording/1) and the tablet's loop-mode export (abp-lab-loop-export/1).

Both carry run records (abp-eval-run/1) unchanged, so one report and one oracle serve every backend.
"""
import json
import os
import time

from . import LOOP_EXPORT_FORMAT, RECORDING_FORMAT, RUN_SCHEMA, abp

RUN_REQUIRED = ("schema", "run_id", "scenario", "model", "backend", "path", "settings", "steps", "final_text")


def events(rec):
    """A replay timeline: model text, tool calls, verdicts, results, flags, final answer, with t_ms offsets."""
    ev, t = [], 0
    for st in rec["steps"]:
        t += st.get("latency_ms") or 0
        if st.get("assistant_text"):
            ev.append({"t_ms": t, "type": "model_text", "text": st["assistant_text"]})
        for c in st.get("tool_calls", []):
            ev.append({"t_ms": t, "type": "tool_call", "name": c["name"], "arguments": c["arguments"], "valid": c.get("valid", True)})
            if c.get("verdict"):
                ev.append({"t_ms": t, "type": "verdict", **c["verdict"]})
            ev.append({"t_ms": t, "type": "tool_result", "name": c["name"], "executed": c.get("executed", False), "result": c.get("result")})
    for f in rec.get("abp_flags", []):
        ev.append({"t_ms": t, "type": "flag", **f})
    ev.append({"t_ms": t, "type": "final", "text": rec.get("final_text", "")})
    return ev


def curate(records, scenarios, out_dir):
    """Per scenario and model, keep the clearest pair (raw failed, ABP caught everything) at the lowest temperature."""
    pairs = {}
    for r in records:
        if r.get("error") or r["scenario"]["id"] not in scenarios:
            continue
        k = (r["scenario"]["id"], r["model"]["id"], r["settings"]["temperature"], r["settings"]["seed"], r.get("rep", 0))
        pairs.setdefault(k, {})[r["path"]] = r
    best = {}
    for k, p in pairs.items():
        if "raw" in p and "abp" in p and p["raw"]["outcome"]["failure"] and p["abp"]["outcome"]["caught"]:
            score = (k[2], len(p["raw"]["final_text"]) + len(p["abp"]["final_text"]), k[3])
            if (k[0], k[1]) not in best or score < best[(k[0], k[1])][0]:
                best[(k[0], k[1])] = (score, k, p)
    n = 0
    for (sid, mid), (_, k, p) in sorted(best.items()):
        sc = scenarios[sid]
        rec = {"format": RECORDING_FORMAT, "id": f"{sid}--{mid.replace('/', '__')}--t{k[2]}-s{k[3]}",
               "title": f"{sc['title']}: {p['raw']['model'].get('label') or mid}", "label": "failure-and-catch", "curated_by": "harness (first pass; a person confirms)",
               "scenario": p["raw"]["scenario"], "prompt": p["raw"]["prompt"],
               "source": {"kind": "device" if "device" in p["raw"]["backend"] else "hosted", "backend": p["raw"]["backend"], "model": p["raw"]["model"],
                          "settings": p["raw"]["settings"], "raw_run_id": p["raw"]["run_id"], "abp_run_id": p["abp"]["run_id"], "recorded_at": p["raw"]["ts"]},
               "raw": {"events": events(p["raw"]), "outcome": p["raw"]["outcome"]},
               "abp": {"events": events(p["abp"]), "outcome": p["abp"]["outcome"]}}
        d = os.path.join(out_dir, sid)
        os.makedirs(d, exist_ok=True)
        json.dump(rec, open(os.path.join(d, rec["id"] + ".json"), "w"), indent=1, ensure_ascii=False)
        n += 1
    return n


def import_loop_export(path, scenarios, results_dir):
    """Validate a tablet export, rescore every run with this harness's oracle, and write it as results jsonl."""
    d = json.load(open(path, encoding="utf-8"))
    msgs = []
    if d.get("format") != LOOP_EXPORT_FORMAT:
        raise ValueError(f"format must be {LOOP_EXPORT_FORMAT}")
    sc = scenarios.get(d["scenario"]["id"])
    if not sc:
        raise ValueError(f"unknown scenario {d['scenario']['id']}")
    if sc["_hash"] != d["scenario"]["hash"]:
        msgs.append(f"WARNING scenario hash differs (export {d['scenario']['hash']}, vault {sc['_hash']}): the app ran another version")
    out = os.path.join(results_dir, time.strftime("%Y-%m-%d", time.gmtime()), "device-" + d["model"]["id"].replace("/", "__"))
    os.makedirs(out, exist_ok=True)
    mismatches = 0
    with open(os.path.join(out, sc["id"] + ".jsonl"), "a", encoding="utf-8") as f:
        for r in d["runs"]:
            miss = [k for k in RUN_REQUIRED if k not in r]
            if miss:
                raise ValueError(f"run {r.get('run_id')} lacks {miss}")
            r["backend"] = d["runtime"]["name"] + "-device-" + d["runtime"].get("backend", "?")
            r["device"] = d.get("device")
            if r["path"] == "abp" and "abp_flags" not in r:
                r["abp_flags"] = abp.check_final(sc, r)
            mine = abp.outcome(sc, r)
            if r.get("outcome") and (r["outcome"].get("failure"), r["outcome"].get("caught")) != (mine["failure"], mine["caught"]):
                mismatches += 1
            r["outcome_app"], r["outcome"] = r.get("outcome"), mine
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    msgs.append(f"imported {len(d['runs'])} runs to {out}; {mismatches} where the app's verdict differs from the harness oracle (the harness verdict is used)")
    return msgs
