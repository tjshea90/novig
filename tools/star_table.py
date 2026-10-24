#!/usr/bin/env python3
"""The star rating's evidence table (SX, Tj 2026-10-24): what the history says about bets of each TYPE, for BetRating to read. Standard library only.

Reads point-in-time backtest rows (tools/star_backtest.py lines.csv.gz: Novig's price at a lead time against the fair Vigilant would have had then, scored on the blended close and the
result) and writes data/src/main/resources/star_evidence.json: for every cell of (sport, kind, side, lead band, edge band) and each coarser level down to the edge band alone, the
number of games, the mean CLV against the blended Pinnacle + Novig close (games weighted once each) with its standard error, and the ROI with its standard error.

  python3 -I tools/star_table.py build LINES.csv.gz [LINES2.csv.gz ...] OUT.json
  python3 -I tools/star_table.py selftest
"""
import csv, gzip, json, math, os, sys, time, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from star_backtest import devig_all

EV_BANDS = [(0.005, 0.03, "0.5-3"), (0.03, 0.08, "3-8"), (0.08, 0.2501, "8-25")]
LEAD_BANDS = [(0, 60, "0-1h"), (60, 180, "1-3h"), (180, 360, "3-6h"), (360, 720, "6-12h"), (720, 10 ** 9, "12h+")]
PRICE_BANDS = [(0.0, 0.4, "+150 longer"), (0.4, 0.5, "+100..+150"), (0.5, 0.6, "-150..-100"), (0.6, 1.0, "-150 shorter")]
SPORTS = {"basketball_nba": "NBA", "basketball_wnba": "WNBA", "basketball_ncaab": "NCAAB", "football_nfl": "NFL", "football_ncaaf": "NCAAF", "hockey_nhl": "NHL", "baseball_mlb": "MLB",
          "tennis": "TENNIS", "mma_ufc": "MMA"}
KINDS = {"prop": "prop", "total": "total", "spread": "spread", "moneyline": "moneyline", "team_totals": "team_total"}
MIN_GAMES = 30


def band(v, bands):
    for lo, hi, name in bands:
        if lo <= v < hi:
            return name
    return None


def side_of(kind, side, cost):
    if side in ("Over", "Under"):
        return side.lower()
    return "fav" if cost >= 0.5 else "dog"


def keys(sport, kind, side, price, lead, ev):
    """From the most specific level to the coarsest: BetRating reads the first that has enough games (StarEvidence.lookup builds the same keys)."""
    return ["%s|%s|%s|%s|%s|*" % (sport, kind, side, price, lead), "*|%s|%s|%s|%s|*" % (kind, side, price, lead), "%s|%s|%s|%s|*|*" % (sport, kind, side, price),
            "*|%s|%s|%s|*|%s" % (kind, side, price, ev), "*|%s|%s|%s|*|*" % (kind, side, price), "*|%s|%s|*|*|*" % (kind, side), "*|*|*|*|*|*"]


def stat(by_game):
    ms = [sum(v) / len(v) for v in by_game.values() if v]
    n = len(ms)
    if n < 2:
        return n, (ms[0] if ms else None), None
    m = sum(ms) / n
    sd = math.sqrt(sum((a - m) ** 2 for a in ms) / (n - 1))
    return n, m, sd / math.sqrt(n)


def novig_close_clv(x, cost):
    """CLV against Novig's OWN devigged close (Shin): the yardstick the results validated (research/star_rating_sx_2026-10-24.md finding 4: ROI = 0.82 x this, intercept 0)."""
    try:
        a, b = float(x["close_nov_raw"]), float(x["close_nov_raw_opp"])
    except Exception:
        return None
    if not (0 < a < 1 and 0 < b < 1) or a + b < 1.0:
        return None
    return devig_all(a, b)[3] / cost - 1


def rows_of(paths):
    for p in paths:
        for x in csv.DictReader(gzip.open(p, "rt")):
            try:
                ev, lead_h, cost = float(x.get("ev_wc") or x["ev_blend"]), float(x["lead_h"]), float(x["cost"])
            except Exception:
                continue
            evb = band(ev, EV_BANDS)
            kind = KINDS.get(x["kind"])
            if evb is None or kind is None:
                continue
            yield {
                "game": x["event_id"], "sport": SPORTS.get(x["sport"], x["sport"].upper()), "kind": kind, "side": side_of(kind, x["side"], cost),
                "price": band(cost, PRICE_BANDS), "lead": band(lead_h * 60, LEAD_BANDS), "ev": evb, "evv": ev,
                "clv": novig_close_clv(x, cost), "roi": float(x["profit"]),
            }


def build(paths, out):
    acc = collections.defaultdict(lambda: {"clv": collections.defaultdict(list), "roi": collections.defaultdict(list), "ev": [], "n": 0})
    for r in rows_of(paths):
        for k in keys(r["sport"], r["kind"], r["side"], r["price"], r["lead"], r["ev"]):
            a = acc[k]
            a["n"] += 1
            a["ev"].append(r["evv"])
            a["roi"][r["game"]].append(r["roi"])
            if r["clv"] is not None:
                a["clv"][r["game"]].append(r["clv"])
    cells = {}
    for k, a in acc.items():
        gc, mc, sc = stat(a["clv"])
        gr, mr, sr = stat(a["roi"])
        if gc < MIN_GAMES or mc is None or sc is None:
            continue
        cells[k] = {"games": gc, "bets": a["n"], "ev": round(sum(a["ev"]) / len(a["ev"]), 5), "clv": round(mc, 5), "clvSe": round(sc, 5),
                    "roi": None if mr is None else round(mr, 5), "roiSe": None if sr is None else round(sr, 5), "roiGames": gr}
    doc = {"asOf": time.strftime("%Y-%m-%d", time.gmtime()), "source": "tools/star_table.py over %s" % ", ".join(p.split("/")[-1] for p in paths),
           "metric": "CLV against Novig's own devigged (Shin) close at the bet's price, games weighted once (research/star_rating_sx_2026-10-24.md finding 4); roi = profit per $1 at the price",
           "minGames": MIN_GAMES, "evBands": [b[2] for b in EV_BANDS], "leadBands": [b[2] for b in LEAD_BANDS], "priceBands": [b[2] for b in PRICE_BANDS], "cells": dict(sorted(cells.items()))}
    with open(out, "w") as f:
        json.dump(doc, f, indent=0, sort_keys=False)
        f.write("\n")
    print("%d cells -> %s" % (len(cells), out))
    return doc


def selftest():
    import tempfile
    d = tempfile.mkdtemp()
    p = os.path.join(d, "l.csv.gz")
    cols = ["event_id", "sport", "lead_h", "kind", "side", "cost", "ev_wc", "close_nov_raw", "close_nov_raw_opp", "profit"]
    with gzip.open(p, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        for g in range(40):
            w.writerow({"event_id": "g%d" % g, "sport": "basketball_nba", "lead_h": "3", "kind": "prop", "side": "Under", "cost": "0.6", "ev_wc": "0.025", "close_nov_raw": "0.66", "close_nov_raw_opp": "0.38", "profit": "0.666" if g % 2 else "-1"})
            w.writerow({"event_id": "g%d" % g, "sport": "basketball_nba", "lead_h": "3", "kind": "prop", "side": "Under", "cost": "0.6", "ev_wc": "0.3", "close_nov_raw": "0.66", "close_nov_raw_opp": "0.38", "profit": "-1"})
    doc = build([p], os.path.join(d, "o.json"))
    c = doc["cells"]["NBA|prop|under|-150 shorter|3-6h|*"]
    want = devig_all(0.66, 0.38)[3] / 0.6 - 1
    assert c["games"] == 40 and abs(c["clv"] - round(want, 5)) < 1e-9 and c["clvSe"] == 0.0 or c["clvSe"] >= 0
    assert "*|*|*|*|*|*" in doc["cells"] and not any(k.endswith("|8-25") for k in doc["cells"])   # the 30% row is outside every band
    assert keys("NBA", "prop", "under", "-150 shorter", "3-6h", "0.5-3")[0] == "NBA|prop|under|-150 shorter|3-6h|*" and band(0.0049, EV_BANDS) is None and band(0.25, EV_BANDS) == "8-25"
    assert side_of("spread", "Lakers", 0.55) == "fav" and side_of("prop", "Under", 0.3) == "under"
    print("star table selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "build" and len(a) >= 3:
        build(a[1:-1], a[-1])
        sys.exit(0)
    print(__doc__)
