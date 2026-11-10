#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/ from a checkout of PrompTugal__News__Website (dev.llm, the on-device
"over our data" layer). Deterministic: the same commit gives the same bytes.

  python3 -I scripts/knowledge/build_knowledge.py <news-website-checkout> [--out app/src/main/assets/knowledge]

Items:
  rm:*  RiskMandate: only the two sentences quoted verbatim from riskmandate.ai (CLAUDE.md: no claim beyond it).
  a:*   pt.news articles with `status: published` (CC BY 4.0). Articles that mention RiskMandate are left out, so
        nothing about RiskMandate comes from anywhere but riskmandate.ai.
  c:*   pt.news company cards (name, what, category, country, attending), from data/companies/<id>/company.json.
Each item is cut into passages of about 120 words; the app's index works on passages and cites the item.
Plain text only: markdown, links, images and directive blocks are removed. No network.
"""
import hashlib, json, os, re, subprocess, sys

RISKMANDATE = [
    ("rm:tagline", "RiskMandate, from riskmandate.ai", "Give agents access. Not free rein."),
    ("rm:what", "RiskMandate: Agent Behaviour Policies, from riskmandate.ai",
     "Agent Behaviour Policies for the AI agents in Gmail, HR, payroll, CRM and meeting apps: what each one can reach, what you authorised it to do, and the gap between the two."),
]
SITE = "https://promptugal.news"
WORDS = 120
MAX_ITEMS = 2000
MAX_TEXT = 40_000


def front_matter(text):
    if not text.startswith("---\n"):
        return {}, text
    end = text.find("\n---\n", 4)
    if end < 0:
        return {}, text
    meta = {}
    for line in text[4:end].splitlines():
        m = re.match(r"^([a-z_]+):\s*(.*)$", line)
        if m:
            v = m.group(2).strip()
            if len(v) >= 2 and v[0] == v[-1] and v[0] in "\"'":
                v = v[1:-1]
            meta[m.group(1)] = v
    return meta, text[end + 5:]


def plain(md):
    out = []
    fence = False
    for line in md.splitlines():
        s = line.strip()
        if s.startswith("```"):
            fence = not fence
            continue
        if fence or s.startswith("#") or s.startswith(":::") or s.startswith("<") or s.startswith("|---") or re.match(r"^!\[", s):
            continue
        s = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", s)           # images
        s = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", s)        # links keep their text
        s = re.sub(r"\{[#.][^}]*\}", "", s)                   # attribute blocks
        s = re.sub(r"<[^>]+>", "", s)                         # inline html
        s = re.sub(r"^#{1,6}\s*", "", s)
        s = re.sub(r"^[-*+]\s+", "", s)
        s = re.sub(r"^\d+\.\s+", "", s)
        s = s.replace("**", "").replace("__", "").replace("`", "")
        s = re.sub(r"(?<!\w)[*_](\S[^*_]*\S|\S)[*_](?!\w)", r"\1", s)
        s = s.replace("|", " ").strip()
        s = re.sub(r"\s+", " ", s)
        out.append(s)
    paras, cur = [], []
    for s in out:
        if s:
            cur.append(s)
        elif cur:
            paras.append(" ".join(cur)); cur = []
    if cur:
        paras.append(" ".join(cur))
    return [p for p in paras if len(p) > 1]


def passages(paras):
    """Paragraphs packed into passages of about WORDS words; a long paragraph is split on words."""
    out, cur, n = [], [], 0
    for p in paras:
        words = p.split()
        while len(words) > WORDS * 2:
            if cur:
                out.append(" ".join(cur)); cur, n = [], 0
            out.append(" ".join(words[:WORDS])); words = words[WORDS:]
        if n and n + len(words) > WORDS:
            out.append(" ".join(cur)); cur, n = [], 0
        cur += words; n += len(words)
    if cur:
        out.append(" ".join(cur))
    return out


def clean(s, cap):
    s = re.sub(r"[\x00-\x1f\x7f]", " ", s)
    return re.sub(r"\s+", " ", s).strip()[:cap]


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    src = argv[1]
    out_dir = argv[argv.index("--out") + 1] if "--out" in argv else "app/src/main/assets/knowledge"
    commit = subprocess.run(["git", "-C", src, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    items, skipped = [], []
    for iid, title, text in RISKMANDATE:
        items.append({"id": iid, "kind": "riskmandate", "title": title, "source": "riskmandate.ai", "passages": [text]})
    adir = os.path.join(src, "content", "articles")
    for name in sorted(os.listdir(adir)):
        if not name.endswith(".md") or name == "index.md":
            continue
        slug = name[:-3]
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,80}", slug):
            skipped.append(slug); continue
        meta, body = front_matter(open(os.path.join(adir, name), encoding="utf-8").read())
        if meta.get("status") != "published":
            skipped.append(slug); continue
        if re.search(r"risk\s*mandate", meta.get("title", "") + meta.get("summary", "") + body, re.I):
            skipped.append(slug + " (mentions RiskMandate)"); continue
        paras = ([clean(meta["summary"], 1000)] if meta.get("summary") else []) + plain(body)
        ps = [clean(p, 2000) for p in passages(paras)]
        if not ps:
            skipped.append(slug); continue
        items.append({"id": "a:" + slug, "kind": "article", "title": clean(meta.get("title", slug), 200),
                      "date": meta.get("date", "")[:10], "source": f"{SITE}/articles/{slug}.html", "passages": ps})
    cdir = os.path.join(src, "data", "companies")
    for cid in sorted(os.listdir(cdir)):
        f = os.path.join(cdir, cid, "company.json")
        if not os.path.isfile(f) or not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,80}", cid):
            continue
        c = json.load(open(f, encoding="utf-8"))
        if re.search(r"risk\s*mandate", json.dumps(c), re.I):
            skipped.append("c:" + cid + " (mentions RiskMandate)"); continue
        name = clean(c.get("name", cid), 120)
        bits = [f"{name}: {clean(c.get('what', ''), 1500)}"]
        if c.get("category"): bits.append(f"Category: {clean(c['category'], 80)}.")
        if c.get("country"): bits.append(f"Country: {clean(c['country'], 80)}.")
        if c.get("attending"): bits.append(f"At Web Summit 2026: {clean(c['attending'], 300)}.")
        items.append({"id": "c:" + cid, "kind": "company", "title": name, "source": f"{SITE}/companies/{cid}/index.html",
                      "passages": [" ".join(bits)]})
    assert len(items) <= MAX_ITEMS
    os.makedirs(out_dir, exist_ok=True)
    lines = [json.dumps(i, ensure_ascii=False, sort_keys=True, separators=(",", ":")) for i in items]
    for l in lines:
        assert len(l) <= MAX_TEXT, l[:80]
    data = ("\n".join(lines) + "\n").encode("utf-8")
    open(os.path.join(out_dir, "knowledge.v1.jsonl"), "wb").write(data)
    manifest = {
        "version": 1, "source_repository": "PrompTugal/PrompTugal__News__Website", "source_commit": commit,
        "items": len(items), "passages": sum(len(i["passages"]) for i in items),
        "kinds": {k: sum(1 for i in items if i["kind"] == k) for k in ("riskmandate", "article", "company")},
        "sha256": hashlib.sha256(data).hexdigest(), "left_out": skipped,
        "licence": "pt.news articles: CC BY 4.0, cited by title and link; RiskMandate: verbatim from riskmandate.ai",
    }
    open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8").write(json.dumps(manifest, indent=1, sort_keys=True) + "\n")
    print(json.dumps({k: manifest[k] for k in ("items", "passages", "kinds", "sha256")}))


if __name__ == "__main__":
    main(sys.argv)
