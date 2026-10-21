#!/usr/bin/env python3
"""CLV consensus over the closes we already hold (CH57, Tj 2026-10-21): no API calls.

Input: propline-clv.csv.gz (Release `propline-clv`: the engine's lines with close_fair/close_via from the grader and
pl_close_fair = PropLine's Pinnacle devigged close at the same line, pl_novig_close_cost = Novig's own close).
Each bet gets a list of (source, fair) closes; a close more than OUTLIER points from the weighted median of the others
is dropped when two others agree; the bet's CLV is confirmed only when two survivors agree on the sign or one sharp
source stands alone. Prints a markdown report; `selftest` checks the rule.
"""
import csv, gzip, math, statistics as S, sys

OUTLIER = 0.03
SHARP = ("Pinnacle", "Circa")


def fl(x):
    try:
        return float(x) if x not in ("", None) else None
    except ValueError:
        return None


def closes(r):
    out = []
    cf, via = fl(r.get("close_fair")), r.get("close_via", "")
    if cf:
        if via.startswith("Novig's last trades"):
            n = int(''.join(c for c in via.split("(")[-1] if c.isdigit()) or 0)
            if n >= 5:
                out.append(("novig_trades", cf, 1.0))
        else:
            out.append((via.replace(" close", ""), cf, 3.0 if any(s in via for s in SHARP) else 1.5))
    pl = fl(r.get("pl_close_fair"))
    if pl and not r.get("pl_point_moved"):
        out.append(("PropLine Pinnacle", pl, 3.0))
    nv = fl(r.get("pl_novig_close_cost"))
    if nv:
        out.append(("PropLine Novig", nv, 1.0))
    return out


def consensus(cl, cost):
    """-> (clv or None, sources used, confirmed). cl: [(name, fair, weight)]."""
    if not cl or not cost:
        return None, [], False
    use = list(cl)
    if len(use) >= 3:
        med = S.median(f for _, f, _ in use)
        keep = [c for c in use if abs(c[1] - med) <= OUTLIER]
        if len(keep) >= 2:
            use = keep
    tw = sum(w for _, _, w in use)
    fair = sum(f * w for _, f, w in use) / tw
    clv = fair / cost - 1
    signs = {(f / cost - 1) > 0 for _, f, _ in use}
    sharp = any(any(s in n for s in SHARP) for n, _, _ in use)
    confirmed = (len(use) >= 2 and len(signs) == 1) or (len(use) == 1 and sharp)
    return clv, [n for n, _, _ in use], confirmed


def report(path):
    rows = [r for r in csv.DictReader(gzip.open(path, "rt"))]
    sh = [r for r in rows if r["shown"] == "1"]
    print(f"listed {len(sh)}")
    res = []
    for r in sh:
        c, srcs, ok = consensus(closes(r), fl(r["cost"]))
        res.append((r, c, srcs, ok))
    have = [x for x in res if x[1] is not None]
    conf = [x for x in have if x[3]]
    print(f"with any close {len(have)} ({100*len(have)/len(sh):.0f}%), confirmed {len(conf)}")
    for name, xs in (("any close", have), ("confirmed", conf), ("unconfirmed", [x for x in have if not x[3]])):
        if not xs:
            continue
        v = [x[1] for x in xs]
        st = [fl(x[0]["profit_units"]) for x in xs if fl(x[0]["profit_units"]) is not None]
        print(f"- {name}: n {len(xs)} mean CLV {100*S.mean(v):+.2f}% (se {100*S.stdev(v)/math.sqrt(len(v)):.2f}) beat {100*sum(1 for t in v if t>0)/len(v):.0f}% ROI {100*S.mean(st) if st else float('nan'):+.1f}% (n {len(st)})")
    # pairwise agreement: two sources on the same bet
    d = []
    for r, _, _, _ in res:
        cl = closes(r)
        for i in range(len(cl)):
            for j in range(i + 1, len(cl)):
                d.append((cl[i][0], cl[j][0], cl[i][1] - cl[j][1]))
    pairs = {}
    for a, b, x in d:
        pairs.setdefault(tuple(sorted((a, b))), []).append(x)
    for k, v in sorted(pairs.items(), key=lambda kv: -len(kv[1])):
        if len(v) >= 5:
            print(f"- {k[0]} vs {k[1]}: n {len(v)} mean diff {100*S.mean(v):+.2f} pts, median |diff| {100*S.median(abs(t) for t in v):.2f} pts")


def selftest():
    c, s, ok = consensus([("Pinnacle", 0.52, 3), ("DK", 0.521, 1.5), ("novig_trades", 0.60, 1)], 0.50)
    assert s == ["Pinnacle", "DK"] and ok and abs(c - (0.5207 / 0.5 - 1)) < 1e-3, (c, s, ok)
    c, s, ok = consensus([("DK", 0.52, 1.5), ("Novig", 0.48, 1)], 0.50)
    assert not ok  # signs disagree: unconfirmed
    c, s, ok = consensus([("Pinnacle", 0.51, 3)], 0.50)
    assert ok
    c, s, ok = consensus([("DK", 0.51, 1.5)], 0.50)
    assert not ok
    assert consensus([], 0.5) == (None, [], False)
    print("selftest ok")


if __name__ == "__main__":
    selftest() if sys.argv[1:] == ["selftest"] else report(sys.argv[1])
