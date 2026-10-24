#!/usr/bin/env python3
"""The star rating's evidence table (SX, Tj 2026-10-24): what the history says about bets of each TYPE, for BetRating to read. Standard library only.

Reads point-in-time backtest rows (tools/star_backtest.py lines.csv.gz: Novig's price at a lead time against the fair Vigilant would have had then, scored on the blended close and the
result) and writes data/src/main/resources/star_evidence.json: for every cell of (sport, kind, side, lead band, edge band) and each coarser level down to the edge band alone, the
number of games, the mean CLV against the blended Pinnacle + Novig close (games weighted once each) with its standard error, and the ROI with its standard error.

  python3 -I tools/star_table.py build LINES.csv.gz [LINES2.csv.gz ...] OUT.json
  python3 -I tools/star_table.py selftest
"""
import csv, gzip, json, math, sys, time, collections

EV_BANDS = [(0.005, 0.01, "0.5-1"), (0.01, 0.02, "1-2"), (0.02, 0.03, "2-3"), (0.03, 0.05, "3-5"), (0.05, 0.08, "5-8"), (0.08, 0.12, "8-12"), (0.12, 0.2501, "12-25")]
LEAD_BANDS = [(0, 60, "0-1h"), (60, 180, "1-3h"), (180, 360, "3-6h"), (360, 720, "6-12h"), (720, 10 ** 9, "12h+")]
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


def keys(sport, kind, side, lead, ev):
    """From the most specific level to the coarsest: BetRating reads the first that has enough games."""
    return ["%s|%s|%s|%s|%s" % (sport, kind, side, lead, ev), "*|%s|%s|%s|%s" % (kind, side, lead, ev), "*|%s|%s|*|%s" % (kind, side, ev), "*|%s|*|*|%s" % (kind, ev), "*|*|*|*|%s" % ev]


def stat(by_game):
    ms = [sum(v) / len(v) for v in by_game.values() if v]
    n = len(ms)
    if n < 2:
        return n, (ms[0] if ms else None), None
    m = sum(ms) / n
    sd = math.sqrt(sum((a - m) ** 2 for a in ms) / (n - 1))
    return n, m, sd / math.sqrt(n)


def rows_of(paths):
    for p in paths:
        for x in csv.DictReader(gzip.open(p, "rt")):
            try:
                ev, lead_h, cost = float(x["ev_blend"]), float(x["lead_h"]), float(x["cost"])
            except Exception:
                continue
            evb = band(ev, EV_BANDS)
            kind = KINDS.get(x["kind"])
            if evb is None or kind is None:
                continue
            yield {
                "game": x["event_id"], "sport": SPORTS.get(x["sport"], x["sport"].upper()), "kind": kind, "side": side_of(kind, x["side"], cost),
                "lead": band(lead_h * 60, LEAD_BANDS), "ev": evb, "evv": ev,
                "clv": float(x["clv_mix"]) if x.get("clv_mix") else None, "roi": float(x["profit"]),
            }


def build(paths, out):
    acc = collections.defaultdict(lambda: {"clv": collections.defaultdict(list), "roi": collections.defaultdict(list), "ev": [], "n": 0})
    for r in rows_of(paths):
        for k in keys(r["sport"], r["kind"], r["side"], r["lead"], r["ev"]):
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
           "metric": "CLV against the average of Pinnacle's and Novig's devigged closes (research/star_rating_sx_2026-10-24.md finding 1), games weighted once; roi = profit per $1 at the price",
           "minGames": MIN_GAMES, "evBands": [b[2] for b in EV_BANDS], "leadBands": [b[2] for b in LEAD_BANDS], "cells": dict(sorted(cells.items()))}
    with open(out, "w") as f:
        json.dump(doc, f, indent=0, sort_keys=False)
        f.write("\n")
    print("%d cells -> %s" % (len(cells), out))
    return doc


def selftest():
    import os, tempfile
    d = tempfile.mkdtemp()
    p = os.path.join(d, "l.csv.gz")
    cols = ["event_id", "sport", "lead_h", "kind", "side", "cost", "ev_blend", "clv_mix", "profit"]
    with gzip.open(p, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        for g in range(40):
            w.writerow({"event_id": "g%d" % g, "sport": "basketball_nba", "lead_h": "3", "kind": "prop", "side": "Over", "cost": "0.5", "ev_blend": "0.025", "clv_mix": "%.3f" % (0.02 + (g % 2) * 0.01), "profit": "1" if g % 2 else "-1"})
            w.writerow({"event_id": "g%d" % g, "sport": "basketball_nba", "lead_h": "3", "kind": "prop", "side": "Over", "cost": "0.5", "ev_blend": "0.3", "clv_mix": "0", "profit": "-1"})
    doc = build([p], os.path.join(d, "o.json"))
    c = doc["cells"]["NBA|prop|over|1-3h|2-3"]
    assert c["games"] == 40 and abs(c["clv"] - 0.025) < 1e-9 and c["clvSe"] > 0 and abs(c["roi"]) < 1e-9
    assert "*|*|*|*|2-3" in doc["cells"] and not any(k.endswith("|12-25") for k in doc["cells"])   # the 30% row is outside every band
    assert keys("NBA", "prop", "over", "1-3h", "2-3")[0] == "NBA|prop|over|1-3h|2-3" and band(0.0049, EV_BANDS) is None and band(0.25, EV_BANDS) == "12-25"
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
