#!/usr/bin/env python3
"""Every graded line's CLV against NOVIG'S OWN close, from Novig's public trade archive (SX, 2026-10-24). Standard library only; no key.

Why: on 30,829 resolved outcomes Novig's closing price predicted the result better than Pinnacle's (research/star_rating_sx_2026-10-24.md finding 4) and CLV against it predicted
ROI one for one (ROI = 0.82 x CLV), so it is the yardstick the star rating's evidence table (tools/star_table.py real) is built on.

For each line in the given lines-*.csv.gz files (tools/lines_dataset.py's columns: market_id, outcome_id, starts_utc, cost, ...) it reads, from data.novig.com's daily trades.csv
(every trade, NOVIG_API.md section 10), the market's STRAIGHT trades before the start and takes Novig's close as the mean implied price of the side over the last 10 trades
(a taker's price on the side, or 1 - the taker's price on the other side), when the last one is within 3 hours of the start. Output: one CSV row per line with a close.

  python3 -I tools/novig_trade_close.py run OUT.csv.gz LINES_DIR [LINES_DIR ...] [--days 2026-10-12,2026-10-22]
  python3 -I tools/novig_trade_close.py selftest
"""
import csv, datetime, glob, gzip, os, sys, urllib.request

BASE = "https://data.novig.com/reporting/trade-data"
LAST_N = 10
MAX_GAP_S = 3 * 3600


def ts(s):
    try:
        return datetime.datetime.fromisoformat(str(s).replace(" ", "T").replace("Z", "+00:00")).timestamp() if "+" in str(s) or "Z" in str(s) else datetime.datetime.fromisoformat(str(s).replace(" ", "T") + "+00:00").timestamp()
    except Exception:
        return None


def close_of(trades, outcome, start):
    """trades: [(t, taker_outcome, price)] of one market, any order. -> Novig's close for [outcome], or None."""
    pre = sorted((r for r in trades if r[0] is not None and r[0] < start), key=lambda r: r[0])
    if len(pre) < 3 or start - pre[-1][0] > MAX_GAP_S:
        return None
    last = pre[-LAST_N:]
    return sum(p if o == outcome else 1 - p for (_, o, p) in last) / len(last)


def read_trades(lines_iter, wanted):
    """data.novig.com trades.csv rows -> {market_id: [(t, taker_outcome, price)]} for STRAIGHT trades in [wanted] markets."""
    out = {}
    head = None
    for line in lines_iter:
        p = line.rstrip("\n").split(",")
        if head is None:
            head = {n: i for i, n in enumerate(p)}
            continue
        try:
            if p[head["tradeType"]] != "STRAIGHT" or p[head["side"]] != "TAKER" or p[head["marketId"]] not in wanted:
                continue
            c, q = float(p[head["cost"]]), float(p[head["qty"]])
        except Exception:
            continue
        if q > 0:
            out.setdefault(p[head["marketId"]], []).append((ts(p[head["timestamp"]]), p[head["outcomeId"]], c / q))
    return out


def lines_of(dirs):
    for d in dirs:
        for f in sorted(glob.glob(os.path.join(d, "**", "lines-*.csv.gz"), recursive=True) + glob.glob(os.path.join(d, "**", "phone-*.csv.gz"), recursive=True)):
            for x in csv.DictReader(gzip.open(f, "rt")):
                x["_file"] = os.path.basename(f)
                yield x


def cmd_run(out, dirs, days):
    rows = [x for x in lines_of(dirs) if x.get("market_id") and x.get("outcome_id")]
    wanted = {x["market_id"] for x in rows}
    if not days:
        import json
        days = json.load(urllib.request.urlopen(BASE + "/index.json", timeout=60))["dates"][-21:]
    trades = {}
    for d in days:
        try:
            with urllib.request.urlopen("%s/%s/trades.csv" % (BASE, d), timeout=1800) as r:
                got = read_trades((b.decode("utf-8", "replace") for b in r), wanted)
        except Exception as e:
            print("%s: %s" % (d, e))
            continue
        for m, v in got.items():
            trades.setdefault(m, []).extend(v)
        print("%s: %d markets with trades" % (d, len(got)), flush=True)
    n = 0
    seen = set()
    with gzip.open(out, "wt", newline="") as fh:
        cols = list(rows[0].keys()) + ["novig_close", "clv_novig"] if rows else ["novig_close", "clv_novig"]
        w = csv.DictWriter(fh, fieldnames=[c for c in cols if c != "_file"] + ["source_file"], extrasaction="ignore")
        w.writeheader()
        for x in rows:
            key = (x["outcome_id"], x.get("first_seen_utc"))
            if key in seen:
                continue
            seen.add(key)
            st = ts(x.get("starts_utc") or "")
            try:
                cost = float(x["cost"])
            except Exception:
                continue
            c = close_of(trades.get(x["market_id"], []), x["outcome_id"], st) if st else None
            if c is None or not cost:
                continue
            x["novig_close"] = round(c, 5)
            x["clv_novig"] = round(c / cost - 1, 5)
            x["source_file"] = x.pop("_file")
            w.writerow(x)
            n += 1
    print("%d lines with Novig's close -> %s" % (n, out))
    return 0


def selftest():
    t0 = ts("2026-10-20 20:00:00")
    tr = [(t0 - 7200, "A", 0.50), (t0 - 3600, "B", 0.45), (t0 - 60, "A", 0.56), (t0 + 60, "A", 0.90)]
    c = close_of(tr, "A", t0)
    assert abs(c - (0.50 + 0.55 + 0.56) / 3) < 1e-12, c                 # the in-play trade is left out; B's taker price is A's 1 - p
    assert close_of(tr[:2], "A", t0) is None                             # fewer than 3 trades
    assert close_of([(t0 - 5 * 3600, "A", 0.5)] * 3, "A", t0) is None    # the last one 5 h before the start
    hdr = "timestamp,outcomeId,marketId,contractSeries,league,marketType,tradeType,legs,cost,qty,side\n"
    rows = [hdr, "2026-10-20T19:00:00Z,A,M,x,NBA,MONEY,STRAIGHT,1,5,10,TAKER\n", "2026-10-20T19:00:00Z,B,M,x,NBA,MONEY,STRAIGHT,1,5,10,MAKER\n", "2026-10-20T19:00:00Z,C,Z,x,NBA,MONEY,STRAIGHT,1,5,10,TAKER\n"]
    got = read_trades(iter(rows), {"M"})
    assert list(got) == ["M"] and got["M"][0][1:] == ("A", 0.5)
    print("novig trade close selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "run" and len(a) >= 3:
        dd = a[a.index("--days") + 1].split(",") if "--days" in a else None
        if dd and len(dd) == 2 and len(dd[0]) == 10:
            d0, d1 = datetime.date.fromisoformat(dd[0]), datetime.date.fromisoformat(dd[1])
            dd = [(d0 + datetime.timedelta(days=i)).isoformat() for i in range((d1 - d0).days + 1)]
        dirs = [x for x in a[2:] if not x.startswith("--") and x not in (",".join(dd or []),) and not (dd and x == a[a.index("--days") + 1])]
        sys.exit(cmd_run(a[1], dirs, dd))
    print(__doc__)
