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
        rows.append({"price": float(r["price"]), "fair": float(r["fair"]) if r.get("fair") else None, "n": int(r["n"]), "pnl": float(r["pnl"]), "legs": legs, "ps": [p for _, p in legs], "day": day, "buyer": int(r["buyer"])})
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


SHAPES = [("2 legs, no leg >= .8 (profile)", lambda r: r["n"] == 2 and max(r["ps"]) < .8), ("2 legs, both .5-.8", lambda r: r["n"] == 2 and min(r["ps"]) >= .5 and max(r["ps"]) < .8),
          ("3 legs", lambda r: r["n"] == 3), ("4+ legs", lambda r: r["n"] >= 4), ("3+ legs, all >= .5", lambda r: r["n"] >= 3 and min(r["ps"]) >= .5), ("a leg >= .8", lambda r: max(r["ps"]) >= .8)]


def margin_report(rows, out=print):
    """Is it the MARGIN over our fair or the SHAPE of the parlay that makes a maker profitable? (the other session's claim, tested 2026-11-20). `gap` is the market's executed price over our fair, in points."""
    rows = [r for r in rows if r.get("fair") is not None]
    for r in rows:
        r["gap"] = 100 * (r["price"] - r["fair"])
    out("")
    out("== Margin or shape? (%d executed parlays with our fair) ==" % len(rows))
    out("A. maker ROI of the executed parlays whose price was AT LEAST k points over our fair (a bad shape does not turn good at a bigger margin):")
    ks = (-9, 0, 0.5, 1, 2, 3)
    out("%-32s" % "shape" + "".join("%15s" % ("k>=%g" % k) for k in ks))
    for name, f in [("ALL", lambda r: True)] + SHAPES:
        sub = [r for r in rows if f(r)]
        cells = []
        for k in ks:
            t = [r for r in sub if r["gap"] >= k]
            cells.append("%15s" % ("%+.1f%% (n%d)" % (100 * sum(r["pnl"] for r in t) / len(t), len(t)) if len(t) >= 30 else "-"))
        out("%-32s" % name + "".join(cells))
    out("B. if WE had quoted our fair + m points on every parlay of the shape: share of the auctions won | ROI of what we win | expected profit per RFQ entered (% of pot):")
    ms = (-1.0, -0.5, 0.1, 0.5, 1.0, 2.0)
    out("%-32s" % "shape" + "".join("%22s" % ("m=%+g" % m) for m in ms))
    for name, f in SHAPES:
        sub = [r for r in rows if f(r)]
        cells = []
        for m in ms:
            w = [r for r in sub if r["gap"] >= m]
            cells.append("%22s" % ("%.0f%% | %+.1f%% | %+.2f%%" % (100 * len(w) / len(sub), 100 * (sum(r["fair"] + m / 100 - r["buyer"] for r in w) / len(w)), 100 * sum(r["fair"] + m / 100 - r["buyer"] for r in w) / len(sub)) if len(w) >= 20 else "-"))
        out("%-32s" % name + "".join(cells))

    def explained(key):
        g = collections.defaultdict(list)
        for r in rows:
            g[key(r)].append(r["pnl"])
        mean = sum(r["pnl"] for r in rows) / len(rows)
        return sum(len(v) * (sum(v) / len(v) - mean) ** 2 for v in g.values()) / sum((r["pnl"] - mean) ** 2 for r in rows)
    shape = lambda r: ("2" if r["n"] == 2 else "3" if r["n"] == 3 else "4+") + ("H" if max(r["ps"]) >= .8 else "L") + ("F" if min(r["ps"]) >= .5 else "D")
    bucket = lambda r: min(5, max(-3, int(math.floor(r["gap"]))))
    out("C. share of the variance of per-parlay maker P&L explained by the shape (legs x heavy leg x all-favourites): %.1f%%, by the margin over our fair (1-point buckets): %.1f%%, by both: %.1f%%" % (100 * explained(shape), 100 * explained(bucket), 100 * explained(lambda r: (shape(r), bucket(r)))))
    out("D. 2-leg parlays with no leg >= .8 by OUR parlay fair: ROI | collateral per $1 of stake (what a quote ties up while the game runs; the capital the wallet is short of):")
    base = [r for r in rows if r["n"] == 2 and max(r["ps"]) < .8]
    for lo, hi in [(0, .1), (.1, .2), (.2, .25), (.25, .3), (.3, .4), (.4, .5), (.5, .7)]:
        t = [r for r in base if lo <= r["fair"] < hi]
        if len(t) >= 30:
            out("   fair [%.2f,%.2f)  n=%4d  ROI %+5.1f%%  collateral $%.2f per $1 stake" % (lo, hi, len(t), 100 * sum(r["pnl"] for r in t) / len(t), sum((1 - r["price"]) / r["price"] for r in t) / len(t)))
    keep = [r for r in base if r["fair"] >= .25]
    drop = [r for r in base if r["fair"] < .25]
    if keep and drop:
        out("   minFair .25 keeps n=%d at %+.1f%% (collateral $%.2f per $1) and drops n=%d at %+.1f%% (collateral $%.2f per $1)" % (len(keep), 100 * sum(r["pnl"] for r in keep) / len(keep), sum((1 - r["price"]) / r["price"] for r in keep) / len(keep), len(drop), 100 * sum(r["pnl"] for r in drop) / len(drop), sum((1 - r["price"]) / r["price"] for r in drop) / len(drop)))


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
    for r in rows:
        r["fair"] = r["price"] + rnd.choice([-0.01, 0.0, 0.01])
    margin_report(rows, out=lines.append)
    assert any("Margin or shape" in l for l in lines) and any(l.startswith("C. share") for l in lines)
    obs, mu, sd, p = shared_leg_null(rows, lambda r: True, draws=300)
    assert abs(mu) < 0.15 and 0 <= p <= 1 and sd > 0
    print("rfq_shape_study selftest ok"); return 0


if __name__ == "__main__":
    if sys.argv[1:] == ["selftest"]:
        sys.exit(selftest())
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    rows_ = load(sys.argv[1])
    report(rows_)
    margin_report(rows_)
