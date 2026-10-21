#!/usr/bin/env python3
"""Closing-line value of the engine's own ORDERS, bets and bid fills alike, at the price each one was filled at (CH57, Tj 2026-10-21: "most accurate clv possible for all bet and bid records").

  python3 tools/orders_clv.py run --trades trades.json --clv propline-clv.csv.gz --out orders-clv.csv.gz [--summary orders-clv.md]
  python3 tools/orders_clv.py selftest

--trades: the engine's ledger (`trades.json` on the `cloud-feed` branch): one entry per auto-bet and per bid fill (a bid's note starts "bid"), with the price it was filled at (`avgPrice`), its cost, status and pnl.
--clv: Release `propline-clv` (`propline-clv.csv.gz`, tools/propline_clv.py + tools/clv_consensus.py): one row per line the engine's scan saw, with the closes from every source that held one (PropLine's Pinnacle,
SportsGameOdds' Pinnacle and Circa, ESPN's DraftKings, Novig's own last trades) and the consensus (`clv_consensus`, `clv_sources`, `clv_n_sources`, `clv_confirmed`), all measured at the line's FIRST-SEEN cost.
The join is on Novig's outcome id. A bid fills at a price that is usually BETTER than the first-seen cost of the line, so each order's CLV is recomputed at its own fill price: the close probability implied by the line
row (`(1 + clv) x cost`) over the order's price, minus 1. Written next to the order: which sources agreed (`clv_sources`, `clv_confirmed`), whether a sharp book was in the fair, the books that agreed, the lead
time, and the result. Orders with no matching line or no close keep their row with the CLV blank (the summary counts them). Never a key; reads files only.
"""
import argparse, csv, gzip, io, json, math, sys
from collections import defaultdict


def num(x):
    try:
        v = float(x)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


def load_lines(path):
    """outcome_id -> the line row (the first one if several), from propline-clv.csv.gz."""
    out = {}
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", newline="") as f:
        for r in csv.DictReader(f):
            oid = r.get("outcome_id") or ""
            if oid and oid not in out:
                out[oid] = r
    return out


def clv_at(price, line_cost, clv):
    """CLV of a fill at [price] when the line row's CLV [clv] was measured at its first-seen cost [line_cost]: the close probability is (1 + clv) x cost."""
    if price is None or line_cost is None or clv is None or price <= 0:
        return None
    return (1.0 + clv) * line_cost / price - 1.0


def rows(trades, lines):
    """One dict per ledger entry, joined to its line row."""
    out = []
    for e in trades:
        bid = 1 if str(e.get("note") or "").startswith("bid") else 0
        price = num(e.get("avgPrice"))
        cost = num(e.get("cost")) or 0.0
        status = e.get("status") or ""
        pnl = num(e.get("pnl"))
        settled = status in ("WON", "LOST")
        lead = None
        if e.get("startsTs") and e.get("atMs"):
            lead = (e["startsTs"] - e["atMs"]) / 3.6e6
        ln = lines.get(e.get("outcomeId") or "")
        row = {
            "order_id": e.get("id"), "placed_ms": e.get("atMs"), "mode": e.get("mode"), "bid": bid, "kind": e.get("kind"), "league": e.get("league"), "event": e.get("event"),
            "selection": e.get("selection"), "starts_ms": e.get("startsTs"), "lead_h": None if lead is None else round(lead, 2), "price": price, "cost": round(cost, 4), "fee": e.get("fee"),
            "fair": e.get("fair"), "ev_pct": e.get("evPercent"), "books": e.get("books"), "stars": e.get("stars"), "fair_age_s": e.get("fairAgeS"), "status": status, "pnl": pnl,
            "roi": round(pnl / cost, 4) if settled and pnl is not None and cost > 0 else None, "outcome_id": e.get("outcomeId"), "line_id": None,
            "sharp_in_fair": None, "books_agreeing": None, "clv_pinn": None, "clv_consensus": None, "clv_sources": None, "clv_n_sources": None, "clv_confirmed": None,
        }
        if ln:
            lc = num(ln.get("cost"))
            row["line_id"] = ln.get("id")
            row["sharp_in_fair"] = ln.get("sharp_in_fair")
            row["books_agreeing"] = ln.get("books_agreeing")
            pin = clv_at(price, lc, num(ln.get("clv_pinn")))
            cons = clv_at(price, lc, num(ln.get("clv_consensus")))
            row["clv_pinn"] = None if pin is None else round(pin, 5)
            row["clv_consensus"] = None if cons is None else round(cons, 5)
            row["clv_sources"] = ln.get("clv_sources")
            row["clv_n_sources"] = ln.get("clv_n_sources")
            row["clv_confirmed"] = ln.get("clv_confirmed")
        out.append(row)
    return out


def cluster_mean(values_by_game):
    """Mean of all values and its standard error counting GAMES (a ratio estimator over clusters); (None, None, 0, 0) when empty."""
    xs = [(g, v) for g, vs in values_by_game.items() for v in vs]
    n = len(xs)
    if n == 0:
        return None, None, 0, 0
    mu = sum(v for _, v in xs) / n
    G = len(values_by_game)
    if G < 2:
        return mu, None, n, G
    ss = sum((sum(vs) - mu * len(vs)) ** 2 for vs in values_by_game.values())
    se = math.sqrt(G / (G - 1) * ss) / n
    return mu, se, n, G


def group_table(data, keyf, minn=10):
    groups = defaultdict(list)
    for r in data:
        groups[keyf(r)].append(r)
    lines = []
    for k in sorted(groups, key=lambda x: str(x)):
        g = groups[k]
        if len(g) < minn:
            continue
        clv, roi = defaultdict(list), defaultdict(list)
        for r in g:
            game = (r["event"], r["starts_ms"])
            c = r["clv_consensus"] if r["clv_consensus"] is not None else r["clv_pinn"]
            if c is not None:
                clv[game].append(c)
            if r["roi"] is not None:
                roi[game].append(r["roi"])
        cm, cse, cn, cg = cluster_mean(clv)
        rm, rse, rn, _ = cluster_mean(roi)
        beat = None
        allc = [v for vs in clv.values() for v in vs]
        if allc:
            beat = sum(1 for v in allc if v > 0) / len(allc)
        stake = sum(r["cost"] for r in g if r["roi"] is not None)
        pnl = sum(r["pnl"] for r in g if r["roi"] is not None)
        t = f"{cm / cse:+.1f}" if cm is not None and cse else "-"
        lines.append("| %s | %d | %d | %s | %s | %s | %d | %s |" % (
            k, len(g), cn, "-" if cm is None else f"{cm * 100:+.2f}%", t, "-" if beat is None else f"{beat * 100:.0f}%", rn,
            "-" if stake <= 0 else f"{pnl / stake * 100:+.1f}%"))
    return lines


def summary(data):
    pre = [r for r in data if r["kind"] != "live"]
    out = ["# CLV of the engine's own orders at their fill price (tools/orders_clv.py)", "",
           "Orders: %d (%d bets, %d bid fills); %d joined to a line; %d have a close. CLV = the consensus close (PropLine's Pinnacle, SGO's Pinnacle and Circa, ESPN's DraftKings, Novig's trades) over the price the order was filled at, minus 1; the standard error counts games, not orders. Live orders (kind `live`) are left out of the tables: their closes are not pregame closes." % (
               len(data), sum(1 for r in data if not r["bid"]), sum(1 for r in data if r["bid"]), sum(1 for r in data if r["line_id"]),
               sum(1 for r in data if r["clv_consensus"] is not None or r["clv_pinn"] is not None)), ""]
    head = ["| group | orders | with a close | mean CLV | t | beat the close | settled | ROI |", "|:--|--:|--:|--:|--:|--:|--:|--:|"]
    for title, keyf in (("bet or bid", lambda r: "bid fill" if r["bid"] else "auto bet"), ("kind", lambda r: r["kind"]), ("league", lambda r: r["league"]),
                        ("sharp book in the fair", lambda r: "sharp" if str(r["sharp_in_fair"]) in ("1", "1.0", "True") else "no sharp"),
                        ("closes confirmed (two sources agree, or one sharp)", lambda r: str(r["clv_confirmed"])),
                        ("lead time", lambda r: "<=3 h" if (r["lead_h"] or 0) <= 3 else "3-12 h" if r["lead_h"] <= 12 else "12-48 h" if r["lead_h"] <= 48 else ">48 h" if r["lead_h"] is not None else "?")):
        out += ["## " + title, ""] + head + group_table(pre, keyf) + [""]
    return "\n".join(out) + "\n"


def run(args):
    trades = json.load(open(args.trades))
    entries = trades.get("entries", trades) if isinstance(trades, dict) else trades
    data = rows(entries, load_lines(args.clv))
    cols = list(data[0].keys()) if data else []
    with gzip.open(args.out, "wt", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        for r in data:
            w.writerow(r)
    if args.summary:
        open(args.summary, "w").write(summary(data))
    print("orders", len(data), "joined", sum(1 for r in data if r["line_id"]), "with a close", sum(1 for r in data if r["clv_consensus"] is not None or r["clv_pinn"] is not None))
    return 0


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(("ok   " if cond else "FAIL ") + name)
        ok = ok and cond

    # a fill at a better price than the line's first-seen cost has a higher CLV than the line row says
    check("clv at the fill price", abs(clv_at(0.40, 0.45, 0.02) - ((1.02 * 0.45) / 0.40 - 1)) < 1e-12)
    check("no price, no clv", clv_at(None, 0.45, 0.02) is None and clv_at(0.4, 0.45, None) is None)
    lines = {"o1": {"id": "L1", "cost": "0.45", "clv_consensus": "0.02", "clv_pinn": "0.03", "clv_sources": "PropLine Pinnacle", "clv_n_sources": "1", "clv_confirmed": "1", "sharp_in_fair": "1", "books_agreeing": "5"}}
    trades = [
        {"id": "a", "atMs": 0, "startsTs": 7_200_000, "mode": "LIVE", "outcomeId": "o1", "kind": "Team totals", "league": "NHL", "event": "A @ B", "selection": "B Under 3.5", "avgPrice": 0.40, "cost": 0.4, "note": "bid · posted at 40c", "status": "WON", "pnl": 0.6},
        {"id": "b", "atMs": 0, "startsTs": 3_600_000, "mode": "LIVE", "outcomeId": "nope", "kind": "Spreads", "league": "NBA", "event": "C @ D", "selection": "C +3.5", "avgPrice": 0.5, "cost": 0.5, "note": "25.0% Kelly", "status": "OPEN", "pnl": None},
    ]
    r = rows(trades, lines)
    check("bid flag and lead", r[0]["bid"] == 1 and r[0]["lead_h"] == 2.0 and r[1]["bid"] == 0)
    check("joined row carries the sources and the recomputed CLV", r[0]["line_id"] == "L1" and r[0]["clv_confirmed"] == "1" and abs(r[0]["clv_consensus"] - round((1.02 * 0.45) / 0.40 - 1, 5)) < 1e-9)
    check("an order with no line keeps its row, blank CLV", r[1]["line_id"] is None and r[1]["clv_consensus"] is None)
    check("roi of a settled order", abs(r[0]["roi"] - 1.5) < 1e-9 and r[1]["roi"] is None)
    mu, se, n, g = cluster_mean({"g1": [0.01, 0.03], "g2": [0.02]})
    check("cluster mean", abs(mu - 0.02) < 1e-12 and n == 3 and g == 2 and se is not None)
    check("summary renders", "bid fill" in summary(r) and "CLV of the engine's own orders" in summary(r))
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--trades", required=True)
    r.add_argument("--clv", required=True)
    r.add_argument("--out", required=True)
    r.add_argument("--summary")
    sub.add_parser("selftest")
    a = ap.parse_args()
    sys.exit(selftest() if a.cmd == "selftest" else run(a))


if __name__ == "__main__":
    main()
