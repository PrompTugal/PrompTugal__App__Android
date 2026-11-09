"""Backends. Each has chat(messages, tools, settings) -> {text, tool_calls, usage, cost, provider, model, latency_ms}.

openrouter: hosted weights (the provider decides the precision; recorded when the response names it).
local: see local_backend.py (LiteRT-LM on this machine's CPU).
"""
import json
import os
import ssl
import time
import urllib.error
import urllib.request

from .budget import Budget

URL = "https://openrouter.ai/api/v1/chat/completions"


class BackendError(RuntimeError):
    pass


def api_key():
    k = os.environ.get("OPEN_ROUTER_KEY") or os.environ.get("OPENROUTER_API_KEY")
    if not k:
        raise BackendError("no OpenRouter key in OPEN_ROUTER_KEY or OPENROUTER_API_KEY")
    return k


def scrub(obj, secret):
    """Replace the key in any structure we are about to write. Defence in depth: it should never appear."""
    s = json.dumps(obj, ensure_ascii=False)
    return json.loads(s.replace(secret, "[REDACTED]")) if secret and secret in s else obj


class OpenRouter:
    name = "openrouter"

    def __init__(self, budget: Budget, price=None, timeout=120):
        self.key = api_key()
        self.budget = budget
        self.timeout = timeout
        self.ctx = ssl.create_default_context()
        self.price = price or {}

    def chat(self, model, messages, tools, settings):
        body = {"model": model["id"], "messages": messages, "max_tokens": settings.get("max_tokens", 1024),
                "temperature": settings["temperature"], "usage": {"include": True}}
        if settings.get("seed") is not None:
            body["seed"] = settings["seed"]
        if settings.get("top_p") is not None:
            body["top_p"] = settings["top_p"]
        if tools:
            body["tools"] = tools
        req = urllib.request.Request(URL, data=json.dumps(body).encode(), method="POST", headers={
            "Authorization": "Bearer " + self.key, "Content-Type": "application/json",
            "X-Title": "RiskMandate eval harness (synthetic data only)"})
        last = None
        for attempt in range(4):
            self.budget.acquire()
            t0 = time.time()
            try:
                with urllib.request.urlopen(req, timeout=self.timeout, context=self.ctx) as r:
                    d = json.load(r)
            except urllib.error.HTTPError as e:
                self.budget.release_unused()
                last = f"HTTP {e.code}: {e.read()[:300].decode('utf-8', 'replace')}"
                if e.code in (429, 500, 502, 503, 504):
                    time.sleep(2 ** (attempt + 1))
                    continue
                raise BackendError(last)
            except (urllib.error.URLError, TimeoutError, ConnectionError, ValueError) as e:
                self.budget.release_unused()
                last = f"{type(e).__name__}: {e}"
                time.sleep(2 ** (attempt + 1))
                continue
            lat = int((time.time() - t0) * 1000)
            err = d.get("error") if isinstance(d.get("error"), dict) else None
            if err and err.get("code") in (408, 429, 500, 502, 503, 504):  # transient provider error inside a 200 body
                self.budget.release_unused()
                last = "provider error: " + json.dumps(err)[:200]
                time.sleep(2 ** (attempt + 1))
                continue
            u = d.get("usage") or {}
            cost = u.get("cost")
            src = "openrouter"
            if cost is None:  # fall back to list price x tokens, labelled
                p = self.price.get(model["id"], (0.0, 0.0))
                cost = u.get("prompt_tokens", 0) * p[0] + u.get("completion_tokens", 0) * p[1]
                src = "list-price-estimate"
            self.budget.release_and_record(float(cost), model["id"])
            if "error" in d or not d.get("choices"):
                raise BackendError("provider error: " + json.dumps(d.get("error", d))[:300])
            m = d["choices"][0]["message"]
            return {"text": m.get("content") or "", "native_calls": m.get("tool_calls") or [],
                    "reasoning": (m.get("reasoning") or "")[:2000] or None,
                    "finish": d["choices"][0].get("finish_reason"),
                    "usage": {"prompt_tokens": u.get("prompt_tokens"), "completion_tokens": u.get("completion_tokens")},
                    "cost": float(cost), "cost_source": src, "provider": d.get("provider"), "served_model": d.get("model"),
                    "latency_ms": lat}
        raise BackendError("gave up after retries: " + str(last))
