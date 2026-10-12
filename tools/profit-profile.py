#!/usr/bin/env python3
"""Builds data/src/main/resources/profit_profile.json (TPF2) from Vigilant bet-history files (gz or plain jsonl; the 'all-bets' files the
app shares). Usage:  python3 tools/profit-profile.py [files...]   (default: every research/uploads/**/*all-bets*)
Records are merged by id, so giving it every file Tj ever sent is right: the profile adapts as the history grows. The app's ProfitProfile
reads the numbers (n, ROI, t) and decides; the cut-offs live in the file (minN, tCut) so Claude can retune them here."""
import glob, gzip, json, os, sys, math, collections
sys.path.insert(0, os.path.dirname(__file__))
import profit_types as pt

MIN_N, T_CUT, KEEP = 40, 1.5, 15
OUT = os.path.join(os.path.dirname(__file__), "..", "data", "src", "main", "resources", "profit_profile.json")

def opener(p): return gzip.open(p, "rt", encoding="utf-8") if p.endswith(".gz") else open(p, encoding="utf-8")

def load(paths):
    seen = {}
    for p in paths:
        with opener(p) as f:
            for line in f:
                line = line.strip()
                if line.startswith("{"):
                    try: b = json.loads(line)
                    except Exception: continue
                    if "id" in b and b.get("made") in ("bet", "bid"): seen[b["id"]] = b
    return list(seen.values())

DIMS = {
    "league|kind|side": lambda b: f'{b.get("league") or ""}|{b.get("kind") or ""}|{pt.side(b)}',
    "league|kind": lambda b: f'{b.get("league") or ""}|{b.get("kind") or ""}',
    "kind|side": lambda b: f'{b.get("kind") or ""}|{pt.side(b)}',
    "league": lambda b: b.get("league") or "",
    "kind": lambda b: b.get("kind") or "",
    "side": pt.side,
    "ev": pt.evb,
    "lead": pt.lead,
}

def main(paths):
    recs = [b for b in load(paths) if not b.get("outlier") and not b.get("lockFor")]
    out = {"version": 1, "minN": MIN_N, "tCut": T_CUT, "records": len(recs),
           "asOf": max((b.get("day") or "" for b in recs), default=""), "groups": {}}
    for made in ("bet", "bid"):
        sub = [b for b in recs if b["made"] == made]
        out["groups"][made] = {}
        for dim, f in DIMS.items():
            groups = collections.defaultdict(list)
            for b in sub: groups[f(b)].append(b)
            d = {}
            for k, v in groups.items():
                s = pt.stats(v)
                if s and s["n"] >= KEEP:
                    d[k] = {"n": s["n"], "roi": round(s["roi"], 4), "t": round(s["t"], 2), "win": round(s["win"], 3)}
            out["groups"][made][dim] = d
        out["groups"][made]["_n"] = len([b for b in sub if b.get("result") in ("won", "lost")])
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=1, sort_keys=True)
    print(f'wrote {os.path.normpath(OUT)}: {len(recs)} records, asOf {out["asOf"]}')

if __name__ == "__main__":
    files = sys.argv[1:] or sorted(glob.glob(os.path.join(os.path.dirname(__file__), "..", "research", "uploads", "**", "*all-bets*"), recursive=True))
    main([f for f in files if "README" not in f and "posted" not in f])
