#!/usr/bin/env python3
"""TAN3c (2026-10-12): the bid desk's 22,234 posted bids. What fills, what is wasted effort.
Run: python3 -I tools/research/tan_bids_2026-10-12.py
Metrics: fill rate (any fill), filled contracts share, mean time up (endedMs-postedMs), $ filled per 100 bids.
Posting effort is the API cost (each post + cancel is Novig calls); fills are the value."""
import gzip, json, glob, os, collections, math
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
p = sorted(glob.glob(os.path.join(ROOT, "research/uploads/2026-10-12/*bids-posted*.gz")))[0]
B = [json.loads(l) for l in gzip.open(p, "rt") if l.strip()]
print("bids", len(B), "status", collections.Counter(b["status"] for b in B))

def side(b):
    s = b.get("selection", "")
    return "Over" if " Over " in s or s.startswith("Over ") else "Under" if " Under " in s or s.startswith("Under ") else "other"

def rate(rows):
    n = len(rows)
    f = [b for b in rows if (b.get("filled") or 0) > 0]
    hrs = sum(max(0, (b.get("endedMs") or b["postedMs"]) - b["postedMs"]) for b in rows) / 3.6e6
    paid = sum(b.get("paid") or 0 for b in rows)
    ev = sum(b["ev"] for b in rows if b.get("ev") is not None) / max(1, sum(1 for b in rows if b.get("ev") is not None))
    return n, len(f), hrs, paid, ev

def show(title, keyf, minn=30):
    print("\n###", title)
    g = collections.defaultdict(list)
    for b in B: g[keyf(b)].append(b)
    for k in sorted(g, key=lambda k: -len(g[k])):
        if len(g[k]) < minn: continue
        n, f, hrs, paid, ev = rate(g[k])
        print(f"{str(k):<34} posted={n:<6} filled={f:<4} ({f/n*100:4.1f}%) bid-hours={hrs:7.1f} fills/100bh={f/hrs*100 if hrs else 0:6.1f} paid=${paid:7.2f} EVpost={ev*100:4.1f}%")

show("league", lambda b: b["league"])
show("kind", lambda b: b["kind"])
show("side", side)
show("margin", lambda b: b.get("margin"))
show("lead at post (h)", lambda b: min(int((b.get("postedLeadMin") or 0) / 60), 36))
show("books", lambda b: min(b.get("books") or 0, 10))
show("price band", lambda b: round(b["price"] * 10) / 10)
show("EV at post", lambda b: round(b["ev"] * 100) if b.get("ev") is not None else None)
def pos(b):
    bb = b.get("bestBidAtPost"); pr = b["price"]
    if bb is None: return "n/a"
    d = round((bb - pr) * 100)
    return "led or level (best bid <= ours)" if d <= 0 else f"behind by {min(d,5)}c"
show("position at post", pos)
show("scanner", lambda b: (b.get("scanner"), b.get("auto")))
# day/hour churn: posted per day
show("day", lambda b: b["day"], 100)
# distinct (event, selection) markets and how many times each was re-posted
mk = collections.Counter((b["event"], b["selection"]) for b in B)
print("\ndistinct markets", len(mk), "posts per market: mean %.1f, p50 %d, p90 %d, max %d" % (
    sum(mk.values()) / len(mk), sorted(mk.values())[len(mk) // 2], sorted(mk.values())[int(len(mk) * .9)], max(mk.values())))
fm = collections.Counter((b["event"], b["selection"]) for b in B if (b.get("filled") or 0) > 0)
print("markets with a fill", len(fm), "posts spent on markets that never filled: %d of %d" % (sum(v for k, v in mk.items() if k not in fm), len(B)))
