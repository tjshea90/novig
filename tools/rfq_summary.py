#!/usr/bin/env python3
"""The parlay maker's shadow evidence (MK, Tj 2026-11-18; research/novig_rfq_maker_2026-11-18.md).

  python3 -I tools/rfq_summary.py run FILES...        files: the engine's `rfq-<day>*.jsonl` or `.jsonl.gz` journals (Releases `cloud-archive-YYYY-MM-DD`)
  python3 -I tools/rfq_summary.py selftest

Answers, from what the engine logged while it only LISTENED: how many parlays Novig is asked to price, how many the engine could price, why not, how big the stakes are, and the number that
decides everything: for the trades that happened, **what the market paid over our fair price** (executed price minus our fair probability, in points of pot) and how often a quote at each margin
would have WON (our price <= the executed price) and what it would have earned (price minus fair, points of pot, if the fair is true). The LIVE rule (200+ priced trades, median market margin above the
margin plus a cushion) is read off the table at the end.
"""
import collections, glob, gzip, json, sys


def rows(files):
    for pat in files:
        for fn in sorted(glob.glob(pat)):
            op = gzip.open if fn.endswith(".gz") else open
            with op(fn, "rt") as f:
                for line in f:
                    try:
                        r = json.loads(line)
                        r["d"] = json.loads(r.get("data") or "{}")
                        yield r
                    except ValueError:
                        continue


def summarize(rs):
    created, executed, reasons, legs, stakes = 0, [], collections.Counter(), collections.Counter(), []
    quoteable = 0
    seen = {}
    for r in rs:
        k, d = r.get("kind"), r["d"]
        if k == "CREATED":
            created += 1
            legs[len(d.get("outcomeIds", []))] += 1
            if d.get("decision") == "quote":
                quoteable += 1
            else:
                reasons[__import__("re").sub(r"[0-9.]+", "#", d.get("reason", "?"))] += 1
            seen[r["rfqId"]] = d
        elif k == "EXECUTED":
            executed.append(d)
            stakes.append(d.get("wager", 0))
    priced = [e for e in executed if e.get("fair") is not None]
    out = ["RFQs seen %d: priceable %d (%.0f%%); legs %s" % (created, quoteable, 100.0 * quoteable / max(created, 1), dict(sorted(legs.items()))),
           "declined because: " + "; ".join("%s %d" % (k, v) for k, v in reasons.most_common(6)),
           "trades seen %d (priced by us %d); stake median $%.0f, p90 $%.0f" % (len(executed), len(priced), pct(stakes, .5), pct(stakes, .9))]
    if priced:
        gap = [(e["price"] - e["fair"]) * 100 for e in priced]
        rel = [(e["price"] / e["fair"] - 1) * 100 for e in priced if e["fair"] > 0]
        out.append("the market paid over our fair: median %+.2f points of pot (%+.1f%% relative), p25 %+.2f, p75 %+.2f" % (pct(gap, .5), pct(rel, .5), pct(gap, .25), pct(gap, .75)))
        out.append("")
        out.append("| our margin | would have won | share | earning if fair is true (points of pot each) |")
        out.append("|--:|--:|--:|--:|")
        for m in (0.0, 0.02, 0.04, 0.06, 0.08, 0.10, 0.12):
            won = [e for e in priced if e["fair"] * (1 + m) <= e["price"] + 1e-9]
            edge = sum(e["fair"] * m * 100 for e in won) / len(won) if won else 0.0
            out.append("| %d%% | %d of %d | %.0f%% | %+.2f |" % (m * 100, len(won), len(priced), 100.0 * len(won) / len(priced), edge))
    return "\n".join(out)


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else 0.0


def selftest():
    rs = [{"kind": "CREATED", "rfqId": "a", "d": {"outcomeIds": ["1", "2"], "decision": "quote"}},
          {"kind": "CREATED", "rfqId": "b", "d": {"outcomeIds": ["1", "2", "3"], "decision": "decline", "reason": "2 legs in one game"}},
          {"kind": "EXECUTED", "rfqId": "a", "d": {"price": 0.23, "fair": 0.20, "wager": 25.0}},
          {"kind": "EXECUTED", "rfqId": "x", "d": {"price": 0.30, "fair": None, "wager": 10.0}}]
    s = summarize(rs)
    assert "RFQs seen 2: priceable 1 (50%)" in s and "trades seen 2 (priced by us 1)" in s and "+3.00 points" in s, s
    assert "| 8% | 1 of 1 |" in s and "| 20%" not in s
    print("rfq_summary selftest ok"); return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if a == ["selftest"]:
        sys.exit(selftest())
    if a and a[0] == "run":
        print(summarize(rows(a[1:]))); sys.exit(0)
    print(__doc__)
