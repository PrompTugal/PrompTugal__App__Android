"""A report per batch of run records: failure rate without ABP, catch rate with ABP, '1 in X', CIs, cost."""
import collections
import json

from .stats import wilson, one_in


def pct(k, n):
    if n == 0:
        return "n/a"
    lo, hi = wilson(k, n)
    return f"{k}/{n} = {k / n:.0%} ({lo:.0%} to {hi:.0%})"


def summarise(records):
    groups = collections.defaultdict(list)
    for r in records:
        groups[(r["backend"], r["model"]["id"], r["settings"].get("tool_mode"), r["scenario"]["id"])].append(r)
    rows = []
    for (be, mid, tmode, sid), rs in sorted(groups.items(), key=lambda kv: tuple(str(x) for x in kv[0])):
        ok = [r for r in rs if not r.get("error")]
        raw = [r for r in ok if r["path"] == "raw"]
        ab = [r for r in ok if r["path"] == "abp"]
        k_raw = sum(r["outcome"]["failure"] for r in raw)
        att = [r for r in ab if r["outcome"]["attempted"]]
        caught = sum(r["outcome"]["caught"] for r in att)
        by_temp = {}
        for t in sorted({r["settings"]["temperature"] for r in raw}):
            rt = [r for r in raw if r["settings"]["temperature"] == t]
            by_temp[t] = (sum(r["outcome"]["failure"] for r in rt), len(rt))
        rows.append({
            "backend": be, "model": mid, "tool_mode": tmode, "scenario": sid,
            "raw_exposed": sum(r["outcome"]["exposed"] for r in raw), "raw_fail_exposed": sum(r["outcome"]["failure"] and r["outcome"]["exposed"] for r in raw),
            "heuristic": any(r["outcome"]["heuristic"] for r in ok),
            "raw_n": len(raw), "raw_fail": k_raw,
            "abp_n": len(ab), "abp_attempted": len(att), "abp_caught": caught,
            "abp_escaped": len(att) - caught,
            "abp_overblocked_calls": sum(r["outcome"]["overblocked_calls"] for r in ab),
            "abp_overflagged_claims": sum(r["outcome"]["overflagged_claims"] for r in ab),
            "invalid_calls": sum(r["outcome"]["invalid_calls"] for r in ok),
            "errors": len(rs) - len(ok),
            "by_temp": {str(t): v for t, v in by_temp.items()},
            "cost_usd": round(sum(r.get("cost_usd", 0) for r in rs), 6),
            "providers": sorted({str(r["provider"]) for r in rs if r.get("provider")}),
        })
    return rows


def markdown(rows, title, budget_total=None, notes=()):
    out = [f"# {title}", ""]
    out += ["Fails/attempts are counted over runs without API errors. Intervals are 95% Wilson. "
            "Cells at temperature 0 with different seeds are near-duplicates, so the effective number of independent runs is lower than n.", ""]
    out += list(notes) + [""] if notes else []
    out += ["| backend | model (tools) | scenario | failure without ABP | 1 in X | met the trap | failure when it met the trap | attempted with ABP | caught by ABP | over-blocked calls | over-flagged claims | invalid calls | errors | cost $ |", "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in rows:
        h = " (heuristic)" if r["heuristic"] else ""
        out.append(f"| {r['backend']} | {r['model']} ({r['tool_mode']}) | {r['scenario']}{h} | {pct(r['raw_fail'], r['raw_n'])} | {one_in(r['raw_fail'], r['raw_n'])} | {r['raw_exposed']}/{r['raw_n']} | {pct(r['raw_fail_exposed'], r['raw_exposed'])} | "
                   f"{pct(r['abp_attempted'], r['abp_n'])} | {pct(r['abp_caught'], r['abp_attempted'])} | {r['abp_overblocked_calls']} | {r['abp_overflagged_claims']} | {r['invalid_calls']} | {r['errors']} | {r['cost_usd']:.4f} |")
    out += ["", "## Failure rate without ABP, by temperature", "", "| model | scenario | " + " | ".join(sorted({t for r in rows for t in r["by_temp"]})) + " |", "|---|---|" + "---|" * len({t for r in rows for t in r["by_temp"]})]
    temps = sorted({t for r in rows for t in r["by_temp"]})
    for r in rows:
        out.append(f"| {r['model']} | {r['scenario']} | " + " | ".join(f"{r['by_temp'][t][0]}/{r['by_temp'][t][1]}" if t in r["by_temp"] else "-" for t in temps) + " |")
    out += ["", f"Cost of the runs in this report: ${sum(r['cost_usd'] for r in rows):.4f}." + (f" Running total across all runs: ${budget_total:.4f} of $5.00." if budget_total is not None else "")]
    out += ["", "Notes: the ABP columns use the scenario's own demonstration policy; where a check is a deterministic match (scenarios 1, 3, 4) a catch rate near 100% is by construction, "
            "and the number that carries information is the failure rate without ABP and the over-blocking. Scenario 5 is a regex heuristic on the answer text and needs curation by hand."]
    return "\n".join(out) + "\n"


def load_records(paths):
    """Load jsonl files; a run_id seen twice (the same cell rerun) counts once, the latest wins."""
    seen = {}
    for p in paths:
        with open(p, encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    r = json.loads(line)
                    seen[r["run_id"]] = r
    return list(seen.values())
