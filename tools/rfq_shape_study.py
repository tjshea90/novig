#!/usr/bin/env python3
"""Which SHAPES of parlay pay a maker at the clearing price, and how sure is that? (PF1, Tj 2026-11-20: "Tweak the engine for maximum profit based on actual data").

  python3 -I tools/rfq_shape_study.py graded.csv.gz      graded.csv.gz: the `rfq-pnl-study` Release asset (tools/rfq_pnl_study.py: every pregame/in-play executed game-line parlay with its price, our fair per
                                                         leg and whether the buyer's parlay won; maker P&L per $1 of pot = price - 1[buyer won])
  python3 -I tools/rfq_shape_study.py selftest

Prints, for pregame parlays: the leg-count table, 2-leg parlays by the strongest leg (where the sharp heavy-favourite pocket starts), the leg-count / price grid with the market's own day splits, and the
SHARED-LEG NULL: legs are drawn from our own leg probabilities with one outcome per distinct leg and day, so a favourite that wins pays every parlay it is in at once; the spread of the class's P&L under that null
(not the naive standard error, which counts 1,200 parlays of ~270 distinct legs as independent) is what a result is judged against. Day splits are SPORTS days (ET 5 am to 5 am).
"""
import collections, csv, datetime, gzip, math, random, sys


def load(path):
    rows = []
    for r in csv.DictReader(gzip.open(path, "rt")):
        if r["live"] == "1":
            continue
        legs = [(x.rsplit("@", 1)[0], float(x.rsplit("@", 1)[1])) for x in r["legs"].split("|")]
        day = datetime.datetime.fromtimestamp(int(r["atMs"]) / 1000 - 10 * 3600, datetime.timezone.utc).strftime("%m-%d")
        rows.append({"price": float(r["price"]), "n": int(r["n"]), "pnl": float(r["pnl"]), "legs": legs, "ps": [p for _, p in legs], "day": day, "buyer": int(r["buyer"])})
    return rows


def seg(rows, name, f, out=print):
    s = [r for r in rows if f(r)]
    if len(s) < 30:
        out("%-52s n=%d (too few)" % (name, len(s))); return None
    m = sum(r["pnl"] for r in s) / len(s)
    sd = math.sqrt(sum((r["pnl"] - m) ** 2 for r in s) / (len(s) - 1))
    days = collections.defaultdict(list)
    for r in s:
        days[r["day"]].append(r["pnl"])
    split = " ".join("%s:%+.1f(n%d)" % (d, 100 * sum(v) / len(v), len(v)) for d, v in sorted(days.items()) if len(v) >= 25)
    roc = sum(r["pnl"] / (1 - r["price"]) for r in s) / len(s)
    out("%-52s n=%5d price %.2f ROI %+5.1f%% (naive se %.1f) on collateral %+5.1f%% | %s" % (name, len(s), sum(r["price"] for r in s) / len(s), 100 * m, 100 * sd / math.sqrt(len(s)), 100 * roc, split))
    return m


def shared_leg_null(rows, f, draws=4000, seed=7):
    """-> (observed ROI, null mean, null sd, P(null >= observed)) for the class f, one latent outcome per (day, leg label) drawn at that leg's mean probability."""
    s = [r for r in rows if f(r)]
    pm = collections.defaultdict(list)
    for r in s:
        for lab, p in r["legs"]:
            pm[(r["day"], lab)].append(p)
    pmean = {k: sum(v) / len(v) for k, v in pm.items()}
    obs = sum(r["pnl"] for r in s) / len(s)
    rnd = random.Random(seed)
    sims = []
    for _ in range(draws):
        out = {k: rnd.random() < p for k, p in pmean.items()}
        sims.append(sum(r["price"] - (1 if all(out[(r["day"], lab)] for lab, _ in r["legs"]) else 0) for r in s) / len(s))
    mu = sum(sims) / draws
    return obs, mu, math.sqrt(sum((x - mu) ** 2 for x in sims) / draws), sum(1 for x in sims if x >= obs) / draws


def report(rows, out=print):
    mx = lambda r: max(r["ps"])
    mn = lambda r: min(r["ps"])
    out("pregame graded executed parlays: %d" % len(rows))
    seg(rows, "all", lambda r: True, out)
    for n in (2, 3, 4):
        seg(rows, "%d legs" % n, lambda r, n=n: r["n"] == n, out)
    seg(rows, "5+ legs", lambda r: r["n"] >= 5, out)
    out("-- 2 legs by the strongest leg (a leg of .8+ is the sharp heavy-favourite pocket)")
    for lo, hi in [(0, .5), (.5, .7), (.7, .8), (.8, .85), (.85, .9), (.9, 1.01)]:
        seg(rows, "2 legs, strongest leg [%.2f,%.2f)" % (lo, hi), lambda r, lo=lo, hi=hi: r["n"] == 2 and lo <= mx(r) < hi, out)
    out("-- 2 legs, no leg >= .8, by price")
    for lo, hi in [(0, .15), (.15, .25), (.25, .35), (.35, .45), (.45, .55), (.55, .65)]:
        seg(rows, "2 legs price [%.2f,%.2f)" % (lo, hi), lambda r, lo=lo, hi=hi: r["n"] == 2 and mx(r) < .8 and lo <= r["price"] < hi, out)
    out("-- longer parlays with no leg >= .8")
    seg(rows, "3 legs", lambda r: r["n"] == 3 and mx(r) < .8, out)
    seg(rows, "4+ legs", lambda r: r["n"] >= 4 and mx(r) < .8, out)
    seg(rows, "3+ legs, all legs >= .5", lambda r: r["n"] >= 3 and mn(r) >= .5, out)
    out("-- the profile's class: 2 legs, no leg >= .8")
    cls = lambda r: r["n"] == 2 and mx(r) < .8
    seg(rows, "PROFILE (2 legs, no leg >= .8)", cls, out)
    seg(rows, "  of which both legs >= .5", lambda r: cls(r) and mn(r) >= .5, out)
    obs, mu, sd, p = shared_leg_null(rows, lambda r: cls(r) and mn(r) >= .5)
    out("shared-leg null for 'both legs >= .5': observed %+.1f%%, null mean %+.1f%% sd %.1f%%, P(null >= observed) = %.3f" % (100 * obs, 100 * mu, 100 * sd, p))
    obs, mu, sd, p = shared_leg_null(rows, cls)
    out("shared-leg null for the whole profile:  observed %+.1f%%, null mean %+.1f%% sd %.1f%%, P(null >= observed) = %.3f" % (100 * obs, 100 * mu, 100 * sd, p))


def selftest():
    rows = []
    rnd = random.Random(1)
    for i in range(400):
        a, b = rnd.choice([.55, .6, .65]), rnd.choice([.55, .6, .65])
        win = rnd.random() < a * b
        rows.append({"price": a * b, "n": 2, "pnl": a * b - (1 if win else 0), "legs": [("A%d" % (i % 20), a), ("B%d" % (i % 20), b)], "ps": [a, b], "day": "11-19", "buyer": int(win)})
    lines = []
    report(rows, out=lines.append)
    assert any("PROFILE" in l for l in lines)
    obs, mu, sd, p = shared_leg_null(rows, lambda r: True, draws=300)
    assert abs(mu) < 0.15 and 0 <= p <= 1 and sd > 0
    print("rfq_shape_study selftest ok"); return 0


if __name__ == "__main__":
    if sys.argv[1:] == ["selftest"]:
        sys.exit(selftest())
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    report(load(sys.argv[1]))
