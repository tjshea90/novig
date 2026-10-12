#!/usr/bin/env python3
"""Profit by TYPE of bet / bid from Vigilant's bet-history file (TPF1). Usage: tools/profit_types.py bets.jsonl [more.jsonl ...]
Prints groups with n, win%, ROI, profit, CLV; the same grouping `tools/profit-profile.py` turns into the app's profile."""
import json, sys, math, collections

def load(paths):
    seen = {}
    for p in paths:
        for line in open(p, encoding="utf-8"):
            line = line.strip()
            if line.startswith("{"):
                try: b = json.loads(line)
                except Exception: continue
                if "id" in b: seen[b["id"]] = b
    return list(seen.values())

def side(b):
    s = (b.get("selection") or "").lower()
    if s.startswith("over ") or " over " in s: return "over"
    if s.startswith("under ") or " under " in s: return "under"
    return "other"

def lead(b):
    m = b.get("leadMin")
    if m is None: return "?"
    return "<1h" if m < 60 else "1-3h" if m < 180 else "3-6h" if m < 360 else "6-13h" if m < 780 else ">13h"

def evb(b):
    e = b.get("ev")
    if e is None: return "?"
    return "<2%" if e < .02 else "2-4%" if e < .04 else "4-6%" if e < .06 else "6%+"

def price(b):
    c = b.get("cost")
    if c is None: return "?"
    return "<30c" if c < .30 else "30-45c" if c < .45 else "45-55c" if c < .55 else "55-70c" if c < .70 else "70c+"

DIMS = {
    "made": lambda b: b.get("made"),
    "league": lambda b: b.get("league"),
    "kind": lambda b: b.get("kind"),
    "side": side,
    "lead": lead,
    "ev": evb,
    "price": price,
    "league+kind": lambda b: f'{b.get("league")} {b.get("kind")}',
    "kind+side": lambda b: f'{b.get("kind")} {side(b)}',
    "league+side": lambda b: f'{b.get("league")} {side(b)}',
    "league+kind+side": lambda b: f'{b.get("league")} {b.get("kind")} {side(b)}',
}

def stats(bs):
    settled = [b for b in bs if b.get("result") in ("won", "lost") and b.get("stake")]
    n = len(settled)
    if not n: return None
    stake = sum(b["stake"] for b in settled); profit = sum(b.get("profit", 0) for b in settled)
    rois = [b.get("profit", 0) / b["stake"] for b in settled]
    mean = profit / stake
    # per-bet ROI standard error (stake-weighted not needed at these sizes)
    mu = sum(rois) / n
    sd = math.sqrt(sum((r - mu) ** 2 for r in rois) / max(1, n - 1))
    t = mu / (sd / math.sqrt(n)) if sd > 0 else 0
    clvs = [b["clv"] for b in settled if b.get("clv") is not None]
    return dict(n=n, win=sum(b["result"] == "won" for b in settled) / n, roi=mean, profit=profit, t=t,
                clv=(sum(clvs) / len(clvs) if clvs else None), nclv=len(clvs), stake=stake)

def main(paths, minn=15):
    bets = [b for b in load(paths) if not b.get("outlier") and not b.get("lockFor")]
    for made in ("bet", "bid"):
        sub = [b for b in bets if b.get("made") == made]
        print(f"\n######## {made.upper()}S: {len(sub)} records")
        for name, f in DIMS.items():
            if name == "made": continue
            groups = collections.defaultdict(list)
            for b in sub: groups[f(b)].append(b)
            rows = [(k, stats(v)) for k, v in groups.items()]
            rows = [(k, s) for k, s in rows if s and s["n"] >= minn]
            rows.sort(key=lambda r: -r[1]["roi"])
            print(f"\n-- {name} (n>={minn})")
            for k, s in rows:
                clv = f'{s["clv"]*100:+.1f}%/{s["nclv"]}' if s["clv"] is not None else "n/a"
                print(f'  {str(k):38s} n={s["n"]:4d} win={s["win"]*100:3.0f}% ROI={s["roi"]*100:+6.1f}% profit={s["profit"]:+8.2f} t={s["t"]:+5.2f} CLV={clv}')

if __name__ == "__main__":
    main(sys.argv[1:])
