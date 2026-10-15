#!/usr/bin/env python3
"""The engine's own orders (trades.json on cloud-feed, TCL20d) as tables for the daily digest (TCL20e): totals, then by mode, league and kind.

    python3 -I tools/trade_summary.py trades.json     |     python3 -I tools/trade_summary.py selftest
ROI is pnl / cost of the SETTLED orders; read the counts before the percentages (a handful of orders says nothing).
"""
import json, sys
from collections import defaultdict


def rows(ledger):
    return ledger.get("entries", [])


def group(es, key):
    g = defaultdict(list)
    for e in es:
        g[key(e)].append(e)
    return g


def line(name, es):
    done = [e for e in es if e.get("status") in ("WON", "LOST", "PUSH", "VOID", "FMV")]
    cost = sum(e["cost"] for e in done)
    pnl = sum(e.get("pnl") or 0.0 for e in done)
    wins = sum(1 for e in done if e["status"] == "WON")
    ev = sum(e["evPercent"] * e["cost"] for e in es) / sum(e["cost"] for e in es) if es and sum(e["cost"] for e in es) else 0.0
    roi = f"{100 * pnl / cost:+.1f}%" if cost else "n/a"
    return f"{name:<28} orders {len(es):>4}  at risk ${sum(e['cost'] for e in es):>9.2f}  settled {len(done):>4}  won {wins:>4}  pnl ${pnl:>9.2f}  ROI {roi:>8}  avg EV when placed {100 * ev:+.1f}%"


def report(ledger):
    es = rows(ledger)
    out = [f"ENGINE ORDERS: {len(es)} in the ledger; stopped: {ledger.get('stopped') or 'no'}; day start equity ${ledger.get('dayStartEquity', 0):.2f}"]
    if not es:
        return "\n".join(out + ["no orders yet (trading is off, or nothing passed the rules)"])
    out.append(line("ALL", es))
    for title, key in (("mode", lambda e: e["mode"]), ("league", lambda e: e["league"]), ("kind", lambda e: e["kind"])):
        out.append(f"-- by {title} --")
        for name, g in sorted(group(es, key).items(), key=lambda kv: -len(kv[1])):
            out.append(line(str(name), g))
    unconfirmed = [e for e in es if e.get("status") == "UNCONFIRMED"]
    if unconfirmed:
        out.append(f"!! {len(unconfirmed)} UNCONFIRMED order(s): sent, end never seen. Reconcile with Novig's fills: " + ", ".join(e["id"][:8] for e in unconfirmed[:10]))
    return "\n".join(out)


def selftest():
    def e(i, status, pnl, cost=10.0, mode="PAPER", league="NBA", kind="Spread"):
        return dict(id=f"id{i}xxxxxxxx", status=status, pnl=pnl, cost=cost, mode=mode, league=league, kind=kind, evPercent=0.05)
    r = report({"entries": [e(1, "WON", 9.0), e(2, "LOST", -10.0), e(3, "OPEN", None, league="NHL"), e(4, "UNCONFIRMED", None)], "stopped": ""})
    assert "ALL" in r and "orders    4" in r and "settled    2" in r and "pnl $    -1.00" in r, r
    assert "UNCONFIRMED" in r and "NHL" in r
    assert "no orders yet" in report({"entries": []})
    print("trade_summary selftest: ok")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["selftest"]: selftest()
    elif len(a) == 1:
        try: print(report(json.load(open(a[0]))))
        except Exception as ex: print(f"ENGINE ORDERS: no usable trades.json ({ex.__class__.__name__})")
    else: print(__doc__); sys.exit(2)
