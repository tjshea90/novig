#!/usr/bin/env python3
"""The star rating's evidence table (SX, Tj 2026-10-24: "5 Star means the bet is a type that is proven profitable from data"). Standard library only.

Writes data/src/main/resources/star_evidence.json for BetRating (Scale.SX) to read. For every TYPE of bet, at levels from the most specific to all bets:
    sport|kind|side|price   *|kind|side|price   sport|kind|side   *|kind|side   *|*|*|price   *|*|*
(kind: prop, total, spread, moneyline, team_total, period; side: over / under, else fav / dog by the price; price band: +150 longer, +100..+150, -150..-100, -150 shorter) it keeps
  * REAL: Vigilant's and CNO's own graded +EV lines (the engine dataset + the phone's scan study) scored on Novig's own close from Novig's public trade archive
    (tools/novig_trade_close.py): games, mean CLV (games weighted once) and its standard error, ROI and its standard error;
  * BACKTEST: the point-in-time backtest (tools/star_backtest.py, the app's worst-case fair on PropLine's price history) scored on Novig's devigged close: games, mean CLV, z.
Why Novig's close: it predicted the results better than Pinnacle's, and CLV against it predicted ROI one for one (research/star_rating_sx_2026-10-24.md, finding 4).

  python3 -I tools/star_table.py build OUT.json --real REAL.csv.gz [...] --backtest LINES.csv.gz [...]
  python3 -I tools/star_table.py selftest
"""
import csv, gzip, json, math, os, re, sys, time, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from star_backtest import devig_all

PRICE_BANDS = [(0.0, 0.4, "+150 longer"), (0.4, 0.5, "+100..+150"), (0.5, 0.6, "-150..-100"), (0.6, 1.0, "-150 shorter")]
SPORTS_PL = {"basketball_nba": "NBA", "basketball_wnba": "WNBA", "basketball_ncaab": "NCAAB", "football_nfl": "NFL", "football_ncaaf": "NCAAF", "hockey_nhl": "NHL", "baseball_mlb": "MLB",
             "tennis": "TENNIS", "mma_ufc": "MMA"}
KINDS_PL = {"prop": "prop", "total": "total", "spread": "spread", "moneyline": "moneyline", "team_totals": "team_total"}
KINDS_REAL = {"PROP": "prop", "TOTAL": "total", "SPREAD": "spread", "MONEYLINE": "moneyline", "TEAM_TOTAL": "team_total", "PERIOD": "period"}
US = {"NBA", "WNBA", "NCAAB", "NFL", "NCAAF", "NHL", "MLB"}
MIN_GAMES = 20
MIN_EV, MAX_EV = 0.005, 0.25


def band(v, bands):
    for lo, hi, name in bands:
        if lo <= v < hi:
            return name
    return None


def sport_of(league):
    """The same mapping as BetType.sportOf in the app."""
    l = (league or "").strip().upper()
    if l in US:
        return l
    if l == "MLB (USA)":
        return "MLB"
    if l.startswith(("ATP", "WTA", "ITF", "CHALLENGER")) or "TENNIS" in l:
        return "TENNIS"
    if "UFC" in l or "MMA" in l:
        return "MMA"
    return "*"


def side_of(selection, cost):
    """The same rule as BetType.of: over / under from the words, else favourite / underdog by the price."""
    if re.search(r"\bOver\b", selection or "", re.I):
        return "over"
    if re.search(r"\bUnder\b", selection or "", re.I):
        return "under"
    return "fav" if cost >= 0.5 else "dog"


def keys(sport, kind, side, price):
    """From the most specific level to all bets (StarEvidence.lookup builds the same keys)."""
    return ["%s|%s|%s|%s" % (sport, kind, side, price), "*|%s|%s|%s" % (kind, side, price), "%s|%s|%s" % (sport, kind, side), "*|%s|%s" % (kind, side), "*|*|*|%s" % price, "*|*|*"]


def stat(by_game):
    ms = [sum(v) / len(v) for v in by_game.values() if v]
    n = len(ms)
    if n < 2:
        return n, (ms[0] if ms else None), None
    m = sum(ms) / n
    return n, m, math.sqrt(sum((a - m) ** 2 for a in ms) / (n - 1)) / math.sqrt(n)


def real_rows(paths):
    """Graded real lines with Novig's close (tools/novig_trade_close.py output), +EV inside the trusted window."""
    for p in paths:
        for x in csv.DictReader(gzip.open(p, "rt")):
            try:
                ev, cost, clv = float(x["ev"]), float(x["cost"]), float(x["clv_novig"])
            except Exception:
                continue
            if not (MIN_EV <= ev <= MAX_EV) or x.get("sharp_agrees") == "0":
                continue
            kind = KINDS_REAL.get(x.get("kind", ""), "other")
            roi = float(x["profit_units"]) if x.get("status") in ("WON", "LOST") and x.get("profit_units") not in ("", None) else None
            yield {"game": (x.get("source_file", "")[:5]) + "|" + (x.get("event") or x.get("id")), "keys": keys(sport_of(x.get("league")), kind, side_of(x.get("selection"), cost), band(cost, PRICE_BANDS)),
                   "clv": clv, "roi": roi}


def backtest_rows(paths):
    for p in paths:
        for x in csv.DictReader(gzip.open(p, "rt")):
            try:
                ev, cost = float(x.get("ev_wc") or x["ev_blend"]), float(x["cost"])
                a, b = float(x["close_nov_raw"]), float(x["close_nov_raw_opp"])
            except Exception:
                continue
            if not (MIN_EV <= ev <= MAX_EV) or not (0 < a < 1 and 0 < b < 1) or a + b < 1.0:
                continue
            kind = KINDS_PL.get(x["kind"])
            if kind is None:
                continue
            side = x["side"].lower() if x["side"] in ("Over", "Under") else ("fav" if cost >= 0.5 else "dog")
            yield {"game": x["event_id"], "keys": keys(SPORTS_PL.get(x["sport"], "*"), kind, side, band(cost, PRICE_BANDS)), "clv": devig_all(a, b)[3] / cost - 1}


def accumulate(rows, with_roi):
    acc = collections.defaultdict(lambda: {"clv": collections.defaultdict(list), "roi": collections.defaultdict(list), "n": 0})
    for r in rows:
        for k in r["keys"]:
            a = acc[k]
            a["n"] += 1
            a["clv"][r["game"]].append(r["clv"])
            if with_roi and r.get("roi") is not None:
                a["roi"][r["game"]].append(r["roi"])
    return acc


def build(out, real_paths, backtest_paths):
    real = accumulate(real_rows(real_paths), True)
    bt = accumulate(backtest_rows(backtest_paths), False)
    cells = {}
    for k in sorted(set(real) | set(bt)):
        c = {}
        if k in real:
            g, m, se = stat(real[k]["clv"])
            gr, mr, sr = stat(real[k]["roi"])
            if g >= MIN_GAMES and se is not None:
                c.update({"games": g, "bets": real[k]["n"], "clv": round(m, 5), "clvSe": round(se, 5), "roi": None if mr is None else round(mr, 5), "roiSe": None if sr is None else round(sr, 5)})
        if k in bt:
            g, m, se = stat(bt[k]["clv"])
            if g >= MIN_GAMES and se:
                c.update({"btGames": g, "btClv": round(m, 5), "btZ": round(m / se, 2)})
        if c:
            cells[k] = c
    doc = {"asOf": time.strftime("%Y-%m-%d", time.gmtime()),
           "source": "tools/star_table.py: real = %s; backtest = %s" % (", ".join(os.path.basename(p) for p in real_paths), ", ".join(os.path.basename(p) for p in backtest_paths)),
           "metric": "CLV against Novig's own close (real: mean of the last 10 pregame trades; backtest: Shin-devigged closing price), games weighted once; roi = profit per $1",
           "priceBands": [b[2] for b in PRICE_BANDS], "cells": cells}
    with open(out, "w") as f:
        json.dump(doc, f, indent=0)
        f.write("\n")
    print("%d cells (%d with a real record) -> %s" % (len(cells), sum(1 for c in cells.values() if "games" in c), out))
    return doc


def summary(new_path, old_path=None):
    """The cells with 100+ real games, and what changed against the shipped table (for the weekly workflow's report)."""
    new = json.load(open(new_path))["cells"]
    old = json.load(open(old_path))["cells"] if old_path and os.path.exists(old_path) else {}
    out = ["cells with 100+ games of real lines (CLV against Novig's close; z; the backtest's z):"]
    for k, c in sorted(new.items(), key=lambda kv: -(kv[1]["clv"] / kv[1]["clvSe"]) if kv[1].get("games", 0) >= 100 and kv[1].get("clvSe") else 0):
        if c.get("games", 0) < 100 or not c.get("clvSe"):
            continue
        o = old.get(k, {})
        was = (" (was %+.2f%% over %d)" % (100 * o["clv"], o["games"])) if o.get("games") else " (new)"
        out.append("  %-36s %4d games  CLV %+.2f%% ±%.2f  z %+.2f  backtest z %s%s" % (k, c["games"], 100 * c["clv"], 196 * c["clvSe"], c["clv"] / c["clvSe"], c.get("btZ"), was))
    print("\n".join(out))
    return 0


def selftest():
    import tempfile
    d = tempfile.mkdtemp()
    rp, bp = os.path.join(d, "r.csv.gz"), os.path.join(d, "b.csv.gz")
    with gzip.open(rp, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["event", "league", "kind", "selection", "cost", "ev", "clv_novig", "status", "profit_units", "sharp_agrees", "source_file"])
        w.writeheader()
        for g in range(30):
            w.writerow({"event": "g%d" % g, "league": "NBA", "kind": "TOTAL", "selection": "Under 220.5", "cost": "0.52", "ev": "0.02", "clv_novig": "%.3f" % (-0.01 - (g % 2) * 0.01),
                        "status": "LOST", "profit_units": "-1", "sharp_agrees": "1", "source_file": "lines-x"})
            w.writerow({"event": "g%d" % g, "league": "NBA", "kind": "TOTAL", "selection": "Under 220.5", "cost": "0.52", "ev": "0.4", "clv_novig": "0.5", "status": "LOST", "profit_units": "-1", "sharp_agrees": "1", "source_file": "lines-x"})
    with gzip.open(bp, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["event_id", "sport", "kind", "side", "cost", "ev_wc", "close_nov_raw", "close_nov_raw_opp"])
        w.writeheader()
        for g in range(25):
            w.writerow({"event_id": "e%d" % g, "sport": "basketball_nba", "kind": "total", "side": "Under", "cost": "0.52", "ev_wc": "0.02", "close_nov_raw": "%.3f" % (0.5 + 0.002 * (g % 3)), "close_nov_raw_opp": "0.53"})
    doc = build(os.path.join(d, "o.json"), [rp], [bp])
    c = doc["cells"]["NBA|total|under|-150..-100"]
    assert c["games"] == 30 and abs(c["clv"] + 0.015) < 1e-9 and c["roi"] == -1.0 and c["btGames"] == 25        # the 40% row is outside the trusted window
    want = sum(devig_all(0.5 + 0.002 * (g % 3), 0.53)[3] / 0.52 - 1 for g in range(25)) / 25
    assert abs(c["btClv"] - round(want, 5)) < 1e-9
    assert keys("NBA", "prop", "under", "-150 shorter")[0] == "NBA|prop|under|-150 shorter" and keys("x", "a", "b", "c")[-1] == "*|*|*"
    assert side_of("Joe Under 1.5", 0.7) == "under" and side_of("Lakers -3.5", 0.55) == "fav" and sport_of("ATP Paris") == "TENNIS" and sport_of("MLS (USA)") == "*"
    print("star table selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "summary" and len(a) >= 2:
        sys.exit(summary(a[1], a[2] if len(a) > 2 else None))
    if a[0] == "build" and len(a) >= 2:
        def opt(name):
            if name not in a:
                return []
            i = a.index(name) + 1
            out = []
            while i < len(a) and not a[i].startswith("--"):
                out.append(a[i])
                i += 1
            return out
        build(a[1], opt("--real"), opt("--backtest"))
        sys.exit(0)
    print(__doc__)
