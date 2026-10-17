#!/usr/bin/env python3
"""P&L of every bet and bid in the phone-data bets-*.jsonl files, split by made/how/kind/league/EV/lead/odds/day (TCL72). Usage: python3 -I tools/research/pnl_split.py DIR [since-day]
ROI = profit / cost over SETTLED; z = mean(roi per $) / stderr with stake-weight ignored (a rough t on per-bet return); read the counts first."""
import glob, json, math, os, sys
from collections import defaultdict

def load(d, since="0000"):
    rows = []
    for f in sorted(glob.glob(os.path.join(d, "bets-*.jsonl"))):
        for line in open(f):
            try:
                r = json.loads(line)
            except Exception:
                continue
            if r.get("day", "") >= since:
                rows.append(r)
    return rows

def settled(rows):
    return [r for r in rows if r.get("result") in ("won", "lost", "push") and r.get("cost")]

def stat(rs):
    n = len(rs); cost = sum(r["cost"] for r in rs); pr = sum(r.get("profit") or 0 for r in rs)
    rets = [(r.get("profit") or 0) / r["cost"] for r in rs]
    m = sum(rets) / n if n else 0
    sd = math.sqrt(sum((x - m) ** 2 for x in rets) / (n - 1)) if n > 1 else 0
    z = m / (sd / math.sqrt(n)) if sd else 0
    clv = [r["clv"] for r in rs if r.get("clv") is not None]
    ev = [r["ev"] for r in rs if r.get("ev") is not None]
    return "n %4d  stake $%8.2f  profit $%8.2f  ROI %+6.1f%%  z %+5.2f  avgEV %+5.1f%%  avgCLV %+5.1f%% (n%d)  win %4.1f%%" % (
        n, cost, pr, 100 * pr / cost if cost else 0, z, 100 * sum(ev) / len(ev) if ev else 0, 100 * sum(clv) / len(clv) if clv else 0, len(clv), 100 * sum(1 for r in rs if r["result"] == "won") / n if n else 0)

def split(rs, title, key, order=None):
    print("\n-- " + title)
    g = defaultdict(list)
    for r in rs:
        g[key(r)].append(r)
    for k in (order or sorted(g, key=lambda k: -len(g[k]))):
        if k in g:
            print("  %-28s %s" % (k, stat(g[k])))

def band(v, edges, fmt):
    if v is None: return "n/a"
    for e in edges:
        if v < e: return fmt % e
    return fmt.replace("<", ">=") % edges[-1]

if __name__ == "__main__":
    rows = settled(load(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "0000"))
    print("SETTLED", len(rows), "days", min(r["day"] for r in rows), "..", max(r["day"] for r in rows))
    print("ALL      ", stat(rows))
    split(rows, "made / how", lambda r: "%s/%s" % (r.get("made"), r.get("how")))
    split(rows, "scanner", lambda r: r.get("scanner"))
    split(rows, "day", lambda r: r["day"], sorted({r["day"] for r in rows}))
    split(rows, "kind", lambda r: r.get("kind"))
    split(rows, "league", lambda r: r.get("league"))
    split(rows, "EV at placing", lambda r: band(r.get("ev"), [0.01, 0.02, 0.03, 0.05, 0.08], "<%.2f"))
    split(rows, "lead (min before start)", lambda r: band(r.get("leadMin"), [60, 360, 1440, 4320], "<%d"))
    split(rows, "cost per $ (price): <.25 longshot .. >.75 fav", lambda r: band(r["cost"] / r["stake"] if r.get("stake") else None, [0.25, 0.4, 0.5, 0.6, 0.75], "<%.2f"))
