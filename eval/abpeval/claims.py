"""Shared text helpers for the oracle (what counts as a failure) and the ABP checks (what stops it)."""
import re

CITE = re.compile(r"\bT\d{2,}\b")


def norm(s):
    return re.sub(r"[^a-z0-9]+", " ", str(s).lower()).strip()


def name_match(a, b):
    a, b = norm(a), norm(b)
    return bool(a and b and (a == b or (len(a) >= 4 and a in b) or (len(b) >= 4 and b in a)))


def pred_match(a, b):
    wa, wb = set(norm(a).split()), set(norm(b).split())
    return bool(wa and wb and (wa <= wb or wb <= wa))


def triple_match(claim, trip):
    return name_match(claim["s"], trip["s"]) and name_match(claim["o"], trip["o"]) and pred_match(claim["p"], trip["p"])


def parse_claims(text):
    """Lines of the form `subject | predicate | object | [Txx]`. Returns list of dicts with idx and line."""
    out = []
    for line in (text or "").splitlines():
        line = line.strip().lstrip("-*0123456789.) \t").strip()
        parts = [p.strip().strip("`*") for p in line.split("|")]
        if len(parts) < 3 or parts[0].lower() in ("subject", "") or parts[1].lower() == "predicate":
            continue
        m = CITE.search(" ".join(parts[3:])) if len(parts) > 3 else None
        out.append({"idx": len(out), "line": line, "s": parts[0], "p": parts[1], "o": parts[2], "cite": m.group(0) if m else None})
    return out


def fetched_triplets(scenario, record):
    """Triplets returned by graph neighbour calls that actually ran (valid, executed) in this run."""
    got = {}
    for st in record["steps"]:
        for c in st.get("tool_calls", []):
            spec = scenario["fake_tools"].get(c["name"], {})
            if spec.get("graph") == "get_neighbours" and c.get("executed") and isinstance(c.get("result"), list):
                for t in c["result"]:
                    if isinstance(t, dict) and "id" in t:
                        got[t["id"]] = t
    return got


_CLOUD = re.compile(r"\b(cloud|data ?cent(?:er|re)s?|servers?|remote(?:ly)?|internet|online|google'?s? (?:infrastructure|systems|network)|sent to|transmitted|uploaded|leaves? (?:the|this|your) device)\b", re.I)
_NEG = re.compile(r"\b(not|no|never|nothing|none|neither|without|instead of|rather than|offline|locally|don'?t|doesn'?t|isn'?t|aren'?t|won'?t|can'?t|cannot)\b|n't", re.I)


def remote_claims(text):
    """Sentences that assert cloud/remote execution or that input leaves the device. Regex heuristic."""
    hits = []
    for sent in re.split(r"(?<=[.!?])\s+|[;\n]+|,\s+(?:but|and|so|while|whereas)\s+|\s+(?:but|whereas)\s+", text or ""):
        if _CLOUD.search(sent) and not _NEG.search(sent):
            hits.append(sent.strip())
    return hits
