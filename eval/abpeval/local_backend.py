"""Backend 2: the real on-device weights on this machine's CPU, through LiteRT-LM's Python API (Apache 2.0).

Run the harness with the interpreter that has `litert-lm-api` installed (a venv outside the repository).
One engine, one request at a time. Temperature 0 is run as greedy (top_k = 1). Weights are the
litert-community .litertlm file, whose card says: a mix of 2, 4 and 8 bit weights (Gemma 4 mobile quantisation).
"""
import hashlib
import json
import os
import threading
import time


def file_sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


class LiteRtLm:
    name = "local-litert-lm"

    def __init__(self, model_path, expect_sha256=None, threads=None, ctx=8192):
        import litert_lm  # imported late: only this backend needs it
        self.lm = litert_lm
        self.path = model_path
        self.sha256 = file_sha256(model_path)
        if expect_sha256 and self.sha256 != expect_sha256:
            raise RuntimeError("model file checksum mismatch: refusing to load")
        litert_lm.set_min_log_severity(litert_lm.LogSeverity.ERROR)
        t0 = time.time()
        self.engine = litert_lm.Engine(model_path, backend=litert_lm.Backend.CPU(thread_count=threads), max_num_tokens=ctx)
        self.load_s = time.time() - t0
        self.lock = threading.Lock()
        self.threads = threads
        self.version = getattr(litert_lm, "__version__", "0.19.1")

    @staticmethod
    def _text(msg):
        c = msg["content"]
        if isinstance(c, str):
            return c
        return "".join(p.get("text", "") for p in c if isinstance(p, dict))

    def _tool_objects(self, tools):
        lm = self.lm

        class Fake(lm.interfaces.Tool):  # never executed: automatic_tool_calling is off, calls come back to the harness
            def __init__(self, spec):
                self.spec = spec

            def get_tool_description(self):
                return self.spec

            def execute(self, param):
                raise RuntimeError("the harness executes tools, not the runtime")
        return [Fake(t) for t in tools]

    @staticmethod
    def _convert(messages):
        """OpenAI-style history (as kept by the runner) to LiteRT-LM messages."""
        out = []
        for m in messages:
            if m["role"] == "assistant" and m.get("tool_calls"):
                out.append({"role": "assistant", "tool_calls": [
                    {"type": "function", "function": {"name": c["function"]["name"], "arguments": json.loads(c["function"]["arguments"])}} for c in m["tool_calls"]]})
            elif m["role"] == "tool":
                part = {"type": "tool_response", "name": m["name"], "response": m["content"]}
                if out and out[-1]["role"] == "tool":
                    out[-1]["content"].append(part)
                else:
                    out.append({"role": "tool", "content": [part]})
            else:
                out.append({"role": m["role"], "content": m["content"]})
        return out

    def chat(self, model, messages, tools, settings):
        lm = self.lm
        t = settings["temperature"]
        sc = lm.SamplerConfig(top_k=1 if t == 0 else None, top_p=settings.get("top_p"), temperature=None if t == 0 else t, seed=settings.get("seed"))
        conv_msgs = self._convert(messages)
        system = conv_msgs[0]["content"] if conv_msgs[0]["role"] == "system" else None
        body = conv_msgs[1:] if system is not None else conv_msgs
        hist, last = body[:-1], body[-1]
        with self.lock:
            conv = self.engine.create_conversation(
                system_message=system, messages=hist or None, sampler_config=sc, max_output_tokens=settings.get("max_tokens", 1024),
                tools=self._tool_objects(tools) if tools else None, automatic_tool_calling=False)
            t0 = time.time()
            try:
                resp = conv.send_message(last)
            finally:
                if hasattr(conv, "close"):
                    conv.close()
            lat = int((time.time() - t0) * 1000)
            text = self._text(resp) if resp.get("content") else ""
            calls = [{"function": {"name": c["function"]["name"], "arguments": json.dumps(c["function"].get("arguments", {}))}} for c in resp.get("tool_calls", [])]
            n_in = sum(len(self.engine.tokenize(m["content"] if isinstance(m.get("content"), str) else json.dumps(m))) for m in messages)
            n_out = len(self.engine.tokenize(text or json.dumps(calls)))
        return {"text": text, "native_calls": calls, "reasoning": None, "finish": None,
                "usage": {"prompt_tokens": n_in, "completion_tokens": n_out}, "cost": 0.0, "cost_source": "local",
                "provider": f"litert-lm {self.version} cpu threads={self.threads or 'default'} sha256={self.sha256[:12]}",
                "served_model": os.path.basename(self.path), "latency_ms": lat}
