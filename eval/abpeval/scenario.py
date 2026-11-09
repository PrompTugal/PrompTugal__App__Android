"""Load and validate scenario JSON files (format abp-eval-scenario/1, see eval/FORMATS.md)."""
import hashlib
import json
import os

from . import SCENARIO_FORMAT, GRAPH_FORMAT

EFFECTS = {"read", "write", "egress"}
CHECKS = {"tool_allowlist", "read_only", "egress_recipients", "secret_egress", "citations_required", "state_consistency"}
FAILURES = {"tool_called", "effect_called", "secret_in_args", "unsupported_claim", "claims_remote"}
ACTIONS = {"block", "hold", "flag"}


# fields that do not change what the model sees or the policy, so editing them keeps the hash
HASH_EXCLUDED = {"exposure", "title", "description", "brief_ref"}


class ScenarioError(ValueError):
    pass


def scenario_hash(raw_text: str) -> str:
    """Hash of the canonical JSON, so formatting changes do not change it."""
    d = {k: v for k, v in json.loads(raw_text).items() if k not in HASH_EXCLUDED}
    canon = json.dumps(d, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(canon.encode()).hexdigest()[:16]


def validate(s: dict) -> None:
    def need(cond, msg):
        if not cond:
            raise ScenarioError(f"{s.get('id', '?')}: {msg}")
    need(s.get("format") == SCENARIO_FORMAT, f"format must be {SCENARIO_FORMAT}")
    for k in ("id", "version", "title", "system", "user", "tools", "fake_tools", "policy", "failure", "max_steps"):
        need(k in s, f"missing '{k}'")
    need(s.get("synthetic") is True, "scenario data must be marked synthetic: true")
    names = set()
    for t in s["tools"]:
        need({"name", "effect", "description", "parameters"} <= t.keys(), f"tool needs name, effect, description, parameters: {t}")
        need(t["effect"] in EFFECTS, f"tool {t['name']}: effect must be one of {sorted(EFFECTS)}")
        need(t["name"] not in names, f"duplicate tool {t['name']}")
        names.add(t["name"])
        need(t["name"] in s["fake_tools"], f"tool {t['name']} has no fake_tools entry")
    for r in s["policy"].get("rules", []):
        need(r.get("check") in CHECKS, f"rule {r.get('id')}: unknown check {r.get('check')}")
        need(r.get("on_violation") in ACTIONS, f"rule {r.get('id')}: on_violation must be one of {sorted(ACTIONS)}")
    need(s["failure"].get("type") in FAILURES, f"unknown failure type {s['failure'].get('type')}")
    need(isinstance(s["max_steps"], int) and s["max_steps"] >= 1, "max_steps must be a positive integer")


def load_graph(vault: str, rel: str) -> dict:
    g = json.load(open(os.path.join(vault, rel), encoding="utf-8"))
    if g.get("format") != GRAPH_FORMAT or g.get("synthetic") is not True:
        raise ScenarioError(f"{rel}: not a synthetic {GRAPH_FORMAT} graph")
    return g


def load_scenario(path: str, vault: str) -> dict:
    raw = open(path, encoding="utf-8").read()
    s = json.loads(raw)
    validate(s)
    s["_hash"] = scenario_hash(raw)
    s["_path"] = path
    if "graph" in s:
        s["_graph"] = load_graph(vault, s["graph"])
    return s


def load_all(vault: str, only=None) -> list:
    d = os.path.join(vault, "scenarios")
    out = []
    for fn in sorted(os.listdir(d)):
        if fn.endswith(".json"):
            s = load_scenario(os.path.join(d, fn), vault)
            if not only or s["id"] in only or any(s["id"].startswith(o) for o in only):
                out.append(s)
    return out
