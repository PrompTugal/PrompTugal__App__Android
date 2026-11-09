#!/usr/bin/env python3
"""eval harness CLI (Python standard library only). See eval/README.md.

  python3 eval/run.py validate
  python3 eval/run.py run --dry-run
  python3 eval/run.py run --max-cost 0.50 --reps 1
  python3 eval/run.py report results/<date> [--out file.md]
  python3 eval/run.py curate results/<date> --out <vault>/recordings
  python3 eval/run.py import-loop export.json
"""
import argparse
import concurrent.futures as cf
import itertools
import json
import os
import subprocess
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from abpeval import scenario as S, runner, report, recordings  # noqa: E402
from abpeval.backends import OpenRouter, scrub, api_key, BackendError  # noqa: E402
from abpeval.budget import Budget, BudgetExhausted  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def slug(s):
    return s.replace("/", "__")


def cfg():
    return json.load(open(os.path.join(HERE, "models.json")))


def common(p):
    p.add_argument("--vault", default=os.environ.get("EVAL_VAULT", os.path.expanduser("~/eval-vault")))
    p.add_argument("--results", default=os.environ.get("EVAL_RESULTS", os.path.expanduser("~/eval-results")))


def cmd_validate(a):
    sc = S.load_all(a.vault)
    for s in sc:
        print(f"ok {s['id']} v{s['version']} hash {s['_hash']} tools={len(s['tools'])} rules={len(s['policy']['rules'])}")
    print(f"{len(sc)} scenarios valid")


def jobs_for(a, c, scs, models):
    d = c["defaults"]
    temps = [float(t) for t in a.temps.split(",")] if a.temps else d["temperatures"]
    seeds = [int(x) for x in a.seeds.split(",")] if a.seeds else d["seeds"]
    reps = a.reps if a.reps is not None else d["reps"]
    jobs = []
    for m, s, t, sd, r, path in itertools.product(models, scs, temps, seeds, range(reps), ("raw", "abp")):
        seed = sd + 1000 * r
        jobs.append((m, s, path, {"temperature": t, "seed": seed, "top_p": a.top_p, "max_tokens": d["max_tokens"], "tool_mode": a.tool_mode or m.get("tool_mode") or d["tool_mode"]}, r))
    return jobs


def cmd_run(a):
    c = cfg()
    scs = S.load_all(a.vault, a.scenarios.split(",") if a.scenarios else None)
    want = "local-litert-lm" if a.backend == "local" else "openrouter"
    models = [m for m in c["models"] if (m["id"] in a.models.split(",") if a.models else (m.get("enabled") or want == "local-litert-lm")) and m["backend"] == want]
    jobs = jobs_for(a, c, scs, models)
    est = 0.0
    for m, s, path, st, r in jobs:  # rough worst case: 2.5k prompt + 0.6k completion tokens per step
        pp, pc = m.get("price_per_mtok", [0.5, 2.0])
        est += s["max_steps"] * (2500 * pp + 600 * pc) / 1e6
    est = 0.0 if want == "local-litert-lm" else est
    print(f"{len(jobs)} runs, {len(models)} models, {len(scs)} scenarios; worst-case estimate ${est:.2f} (typical is a fraction of it)")
    if a.dry_run:
        return
    key = api_key() if want == "openrouter" else ""
    date = time.strftime("%Y-%m-%d", time.gmtime())
    batch = a.batch or time.strftime("%H%M%S", time.gmtime())
    os.makedirs(a.results, exist_ok=True)
    budget = Budget(a.results, session_cap=a.max_cost)
    print(f"budget so far ${budget.spent():.4f} of ${budget.cap:.2f}; this invocation capped at ${a.max_cost}")
    if want == "local-litert-lm":
        from abpeval.local_backend import LiteRtLm
        be = LiteRtLm(a.local_model, expect_sha256=models[0].get("sha256"), threads=a.threads)
        a.workers = 1
        label = "local-litert-lm-cpu"
        print(f"loaded {be.path} in {be.load_s:.1f}s, sha256 {be.sha256[:12]} verified")
    else:
        label = "openrouter"
    be = be if want == "local-litert-lm" else OpenRouter(budget, price={m["id"]: tuple(x / 1e6 for x in m["price_per_mtok"]) for m in c["models"] if "price_per_mtok" in m})
    wl = threading.Lock()
    done_ids = set()
    if a.resume:
        for root, _, fs in os.walk(os.path.join(a.results, date)):
            for fn in fs:
                if fn.endswith(".jsonl"):
                    with open(os.path.join(root, fn)) as f:
                        done_ids |= {r["run_id"] for r in map(json.loads, filter(str.strip, f)) if not r.get("error")}
    todo = [j for j in jobs if runner.run_id(j[1], j[0]["id"], j[2], j[3], j[4]) not in done_ids]
    print(f"{len(todo)} to run ({len(jobs) - len(todo)} already done)")
    stop = threading.Event()
    count = {"n": 0, "err": 0}

    def work(j):
        if stop.is_set():
            return
        m, s, path, st, r = j
        rec = runner.run_one(be, m, s, path, st, r, label, batch)
        rec = scrub(rec, key)
        if rec["error"] and "BudgetExhausted" in rec["error"]:
            stop.set()
            return
        d = os.path.join(a.results, date, slug(m["id"]))
        os.makedirs(d, exist_ok=True)
        with wl:
            with open(os.path.join(d, s["id"] + ".jsonl"), "a", encoding="utf-8") as f:
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            count["n"] += 1
            count["err"] += bool(rec["error"])
            if count["n"] % 20 == 0:
                print(f"  {count['n']}/{len(todo)} runs, errors {count['err']}, total spent ${budget.spent():.4f}", flush=True)

    with cf.ThreadPoolExecutor(a.workers) as ex:
        list(ex.map(work, todo))
    print(f"finished {count['n']} runs, {count['err']} with errors; stopped early on budget: {stop.is_set()}; total spent ${budget.spent():.4f} of ${budget.cap:.2f}")
    man = {"batch": batch, "date": date, "runs": count["n"], "errors": count["err"], "stopped_on_budget": stop.is_set(),
           "models": [m["id"] for m in models], "scenarios": [s["id"] for s in scs], "args": {k: v for k, v in vars(a).items() if k not in ("fn",)},
           "total_spent_usd": budget.spent(), "eval_git": subprocess.run(["git", "-C", HERE, "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()}
    os.makedirs(os.path.join(a.results, date, "_batches"), exist_ok=True)
    json.dump(man, open(os.path.join(a.results, date, "_batches", batch + ".json"), "w"), indent=1)


def jsonl_files(root):
    for r, dirs, fs in os.walk(root):
        dirs[:] = sorted(d for d in dirs if not d.startswith("_"))  # _batches, _superseded...
        for fn in sorted(fs):
            if fn.endswith(".jsonl"):
                yield os.path.join(r, fn)


def cmd_report(a):
    recs = report.load_records(jsonl_files(a.path))
    if a.batch:
        recs = [r for r in recs if r.get("batch") == a.batch]
    rows = report.summarise(recs)
    b = Budget(a.results).spent()
    md = report.markdown(rows, a.title, b)
    if a.out:
        open(a.out, "w").write(md)
        json.dump(rows, open(os.path.splitext(a.out)[0] + ".json", "w"), indent=1)
    print(md)


def cmd_rescore(a):
    """Recompute abp_flags and outcome for every record under PATH from the record alone, in place. Use after an oracle or scenario-scoring change."""
    from abpeval import abp
    scs = {s["id"]: s for s in S.load_all(a.vault)}
    n = changed = 0
    for fp in jsonl_files(a.path):
        out = []
        with open(fp, encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                r = json.loads(line)
                sc = scs.get(r["scenario"]["id"])
                if sc and not r.get("error") and r["scenario"]["version"] == sc["version"]:
                    if r["path"] == "abp":
                        r["abp_flags"] = abp.check_final(sc, r)
                    new = abp.outcome(sc, r)
                    changed += new != r.get("outcome")
                    r["outcome"] = new
                    r["scenario"]["hash"] = sc["_hash"]
                    n += 1
                out.append(json.dumps(r, ensure_ascii=False))
        with open(fp, "w", encoding="utf-8") as f:
            f.write("\n".join(out) + "\n")
    print(f"rescored {n} runs, {changed} outcomes changed")


def cmd_curate(a):
    scs = {s["id"]: s for s in S.load_all(a.vault)}
    n = recordings.curate(report.load_records(jsonl_files(a.path)), scs, a.out)
    print(f"wrote {n} recordings to {a.out}")


def cmd_import(a):
    scs = {s["id"]: s for s in S.load_all(a.vault)}
    msgs = recordings.import_loop_export(a.file, scs, a.results)
    print("\n".join(msgs))


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(required=True)
    v = sub.add_parser("validate"); common(v); v.set_defaults(fn=cmd_validate)
    r = sub.add_parser("run"); common(r)
    r.add_argument("--models"); r.add_argument("--scenarios"); r.add_argument("--temps"); r.add_argument("--seeds")
    r.add_argument("--reps", type=int); r.add_argument("--top-p", type=float, dest="top_p"); r.add_argument("--tool-mode", choices=["text", "native"])
    r.add_argument("--workers", type=int, default=6); r.add_argument("--max-cost", type=float, default=0.5, dest="max_cost")
    r.add_argument("--backend", choices=["openrouter", "local"], default="openrouter")
    r.add_argument("--local-model", default=os.path.expanduser("~/eval-local/models/gemma-4-E2B-it.litertlm"), dest="local_model")
    r.add_argument("--threads", type=int)
    r.add_argument("--resume", action="store_true"); r.add_argument("--dry-run", action="store_true"); r.add_argument("--batch")
    r.set_defaults(fn=cmd_run)
    q = sub.add_parser("report"); common(q); q.add_argument("path"); q.add_argument("--out"); q.add_argument("--batch"); q.add_argument("--title", default="Evidence run report"); q.set_defaults(fn=cmd_report)
    z = sub.add_parser("rescore"); common(z); z.add_argument("path"); z.set_defaults(fn=cmd_rescore)
    u = sub.add_parser("curate"); common(u); u.add_argument("path"); u.add_argument("--out", required=True); u.set_defaults(fn=cmd_curate)
    i = sub.add_parser("import-loop"); common(i); i.add_argument("file"); i.set_defaults(fn=cmd_import)
    a = p.parse_args()
    try:
        a.fn(a)
    except (BackendError, BudgetExhausted, S.ScenarioError) as e:
        sys.exit(f"stopped: {e}")


if __name__ == "__main__":
    main()
