"""Persistent spend tracker with a hard stop. The total survives across runs and processes."""
import fcntl
import json
import os
import threading
import time

HARD_CAP_USD = 5.00  # Dinis's decision (9 Nov 2026): $5 in total across all runs. Never raise in code.


class BudgetExhausted(RuntimeError):
    pass


class LedgerError(RuntimeError):
    """The spend ledger is missing, unreadable or corrupt: refuse to spend, never assume $0."""


def default_ledger():
    return os.environ.get("EVAL_LEDGER") or os.path.expanduser("~/.eval-ledger/openrouter.json")


def init_ledger(path, seed_spent=0.0):
    """Create the ledger once, explicitly (seeded with money already spent). Refuses to overwrite."""
    if os.path.exists(path):
        raise LedgerError(f"{path} already exists")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump({"cap_usd": HARD_CAP_USD, "spent_usd": float(seed_spent), "calls": 0, "by_model": {}}, f, indent=1)


class Budget:
    def __init__(self, ledger_path, cap=HARD_CAP_USD, per_call_reserve=0.003, session_cap=None):
        self.path = ledger_path
        self.lock_path = self.path + ".lock"
        self._read()  # raises LedgerError now if the ledger is missing or corrupt
        self.cap = min(float(cap), HARD_CAP_USD)
        self.reserve = per_call_reserve
        self.session_cap = session_cap
        self.session_spent = 0.0
        self.inflight = 0.0
        self.tlock = threading.Lock()

    def _read(self):
        try:
            with open(self.path) as f:
                d = json.load(f)
            if not isinstance(d.get("spent_usd"), (int, float)) or d["spent_usd"] < 0 or not isinstance(d.get("by_model"), dict):
                raise ValueError("bad fields")
            return d
        except (OSError, ValueError, AttributeError) as e:
            raise LedgerError(f"spend ledger {self.path} is missing or unreadable ({type(e).__name__}); "
                              "refusing to spend. Restore it, or create it with: run.py ledger-init --seed <usd already spent>") from None

    def _locked(self, fn):
        with open(self.lock_path, "w") as lf:
            fcntl.flock(lf, fcntl.LOCK_EX)
            data = self._read()
            res = fn(data)
            tmp = self.path + ".tmp"
            with open(tmp, "w") as out:
                json.dump(data, out, indent=1)
            os.replace(tmp, self.path)
            return res

    def spent(self):
        return self._read()["spent_usd"]

    def acquire(self):
        """Call before a paid request. Raises BudgetExhausted if the worst case would pass a cap."""
        with self.tlock:
            total = self.spent()
            if total + self.inflight + self.reserve > self.cap:
                raise BudgetExhausted(f"total ${total:.4f} + in flight would pass the cap ${self.cap:.2f}")
            if self.session_cap is not None and self.session_spent + self.inflight + self.reserve > self.session_cap:
                raise BudgetExhausted(f"this invocation's cap ${self.session_cap:.2f} reached")
            self.inflight += self.reserve

    def release_and_record(self, cost, model):
        def upd(d):
            d["spent_usd"] = round(d["spent_usd"] + cost, 8)
            d["calls"] += 1
            m = d["by_model"].setdefault(model, {"usd": 0.0, "calls": 0})
            m["usd"] = round(m["usd"] + cost, 8)
            m["calls"] += 1
            d["updated"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        with self.tlock:
            self.inflight = max(0.0, self.inflight - self.reserve)
            self.session_spent += cost
            self._locked(upd)

    def release_unused(self):
        with self.tlock:
            self.inflight = max(0.0, self.inflight - self.reserve)
