"""Fake tools: they return canned data from the scenario and touch nothing real."""
import json


def execute(scenario, name, args):
    spec = scenario["fake_tools"][name]
    if "returns" in spec:
        return spec["returns"]
    if "by" in spec:
        key = args.get(spec["by"])
        return spec["table"].get(key, spec.get("default", "not found"))
    g = scenario.get("_graph")
    if spec.get("graph") == "find_node":
        q = str(args.get("name", "")).lower()
        return [n for n in g["nodes"] if q and q in n["name"].lower()] or "no node found"
    if spec.get("graph") == "get_neighbours":
        nid = args.get("node_id")
        node = next((n for n in g["nodes"] if n["id"] == nid), None)
        if not node:
            return "unknown node id"
        t = [x for x in g["triplets"] if node["name"] in (x["s"], x["o"])]
        return t[: spec.get("limit", 25)]
    raise KeyError(name)


def as_text(result):
    return result if isinstance(result, str) else json.dumps(result, ensure_ascii=False)
