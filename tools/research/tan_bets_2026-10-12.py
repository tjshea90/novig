#!/usr/bin/env python3
"""TAN3 (2026-10-12): bet-history study of Tj's 2026-10-11 export (1422 bets and bid fills).

Run:  python3 -I tools/research/tan_bets_2026-10-12.py [bets.jsonl[.gz]] [section ...]
Reads research/uploads/2026-10-12/*all-bets-p1*.gz by default. Output is saved in
research/tan_bets_2026-10-12_output.txt (plain text, small).

Every group prints: n settled, ROI, expected profit, z of (actual - expected) using the bet's own
fair chance, and CLV mean with t on bets with a TRUE close (and on `closeIndependent` ones).
A "z" of results is the honest test of "is the fair too optimistic here"; CLV t tests "does the
market agree later". Many groups are looked at, so only |z| or t >= 3 with n >= 100 is acted on.
"""
import gzip, json, math, sys, glob, collections, os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))


def load(path=None):
    if path is None:
        path = sorted(glob.glob(os.path.join(ROOT, "research/uploads/2026-10-12/*all-bets-p1*.gz")))[0]
    op = gzip.open if path.endswith(".gz") else open
    with op(path, "rt") as f:
        return [json.loads(l) for l in f if l.strip()]


def side(r):
    s = r.get("selection", "")
    if " Over " in s or s.startswith("Over "): return "Over"
    if " Under " in s or s.startswith("Under "): return "Under"
    return "other"


def settled(r):
    return r.get("result") in ("won", "lost") and r.get("profit") is not None


def stats(rows, label):
    """One line: results vs expectation and CLV."""
    S = [r for r in rows if settled(r) and r.get("ev") is not None and r.get("fair")]
    n = len(S)
    stake = sum(r["stake"] for r in S)
    prof = sum(r["profit"] for r in S)
    exp = sum(r["stake"] * r["ev"] for r in S)
    var = 0.0
    for r in S:
        c = r["cost"]; p = r["fair"]
        var += (r["stake"] / c) ** 2 * p * (1 - p)
    z = (prof - exp) / math.sqrt(var) if var > 0 else float("nan")
    roi = prof / stake if stake else float("nan")
    C = [r for r in rows if r.get("clv") is not None]
    cn = len(C)
    if cn > 1:
        m = sum(r["clv"] for r in C) / cn
        sd = math.sqrt(sum((r["clv"] - m) ** 2 for r in C) / (cn - 1))
        t = m / (sd / math.sqrt(cn)) if sd > 0 else float("nan")
        beat = sum(1 for r in C if r["clv"] > 0) / cn
    else:
        m = t = beat = float("nan")
    I = [r for r in C if r.get("closeIndependent")]
    im = sum(r["clv"] for r in I) / len(I) if I else float("nan")
    evm = sum(r["ev"] for r in S) / n if n else float("nan")
    return (f"{label:<42} n={n:<4} stake={stake:7.1f} ROI={roi*100:6.1f}% (EV {evm*100:4.1f}%) "
            f"profit={prof:7.2f} exp={exp:6.2f} z={z:5.1f} | CLV {m*100:5.2f}% n={cn:<4} t={t:5.1f} beat={beat*100:3.0f}% "
            f"| indep {im*100:5.2f}% n={len(I)}")


def split(rows, title, keyf, order=None, minn=8):
    print(f"\n### {title}")
    g = collections.defaultdict(list)
    for r in rows:
        g[keyf(r)].append(r)
    keys = order or sorted(g, key=lambda k: -len(g[k]))
    for k in keys:
        if k in g and len(g[k]) >= minn:
            print(stats(g[k], str(k)))


def band(x, edges, fmt="{}"):
    if x is None: return "n/a"
    for a, b in zip(edges, edges[1:]):
        if a <= x < b: return f"{a}-{b}"
    return f"{edges[-1]}+" if x >= edges[-1] else f"<{edges[0]}"


def main():
    args = [a for a in sys.argv[1:]]
    path = args[0] if args and (args[0].endswith(".gz") or args[0].endswith(".jsonl") or args[0].endswith(".txt")) else None
    rows = load(path)
    # the README's population: outliers (|EV| > 6%) are left out of its averages
    base = [r for r in rows if not r.get("outlier") and not r.get("lockFor")]
    bets = [r for r in base if r["made"] == "bet"]
    bids = [r for r in base if r["made"] == "bid"]
    print(f"rows {len(rows)}  non-outlier {len(base)}  bets {len(bets)}  bid fills {len(bids)}")
    print(stats(base, "ALL (non-outlier)"))
    print(stats(bets, "BETS (taker)"))
    print(stats(bids, "BID FILLS"))

    for name, pop in (("BETS", bets), ("BIDS", bids)):
        print(f"\n================ {name} ================")
        split(pop, "kind", lambda r: r.get("kind"))
        split(pop, "sport", lambda r: r.get("sport"))
        split(pop, "league", lambda r: r.get("league"))
        split(pop, "side (Over/Under) of props", lambda r: side(r) if r.get("kind") == "Player props" else "(not prop)")
        split(pop, "side x sport (props)", lambda r: (r.get("sport"), side(r)) if r.get("kind") == "Player props" else None)
        split(pop, "EV when placed", lambda r: band(r.get("ev"), [0, .01, .02, .03, .04, .05, .06]) if r.get("ev") is not None else "n/a")
        split(pop, "stars", lambda r: r.get("stars", "n/a"))
        split(pop, "lead (h)", lambda r: band((r.get("leadMin") or 0) / 60.0, [0, .5, 2, 6, 13, 24, 48]))
        split(pop, "odds band", lambda r: band(r.get("odds"), [-300, -200, -150, -110, 100, 130, 160, 200, 300]) if r.get("odds") else "n/a")
        split(pop, "books behind fair", lambda r: r.get("books", "n/a"))
        split(pop, "sharp", lambda r: r.get("sharp", "n/a"))
        split(pop, "how", lambda r: r.get("how"))
        split(pop, "origin", lambda r: r.get("origin", "n/a"))
        split(pop, "scanner", lambda r: r.get("scanner"))
        split(pop, "fairMethod", lambda r: r.get("fairMethod", "n/a"))
        split(pop, "close basis", lambda r: r.get("closeBasis", "n/a"))
        split(pop, "fair age (s)", lambda r: band(r.get("fairAgeSec"), [0, 30, 120, 300, 600]))
        split(pop, "page age (s)", lambda r: band(r.get("pageAgeSec"), [0, 30, 120, 600]))
        split(pop, "week", lambda r: r.get("day", "")[:10] if False else r.get("day"))
        split(pop, "app version", lambda r: r.get("appVersion", "n/a"))


if __name__ == "__main__":
    main()
