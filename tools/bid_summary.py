#!/usr/bin/env python3
"""Reads bids.csv.gz (tools/lines_dataset.py bids) and prints what Claude needs to tune the engine's bids (Tj, 2026-10-16: optimise bids over time, balancing fills with positive-EV quality).

    python3 -I tools/bid_summary.py <bids.csv.gz>        fill rate and fill quality by kind, by hours to the start, by margin band, by rung
    python3 -I tools/bid_summary.py selftest

Fill rate = bids with any fill / bids ended. Quality = mean fill_ev (the fair on the first scan after the fill against the price; negative = picked off). A change to a bid rule needs the ROUTINE.md class B evidence;
this table is where the evidence comes from: n, fill rate, mean fill_ev with its standard error, dollars filled.
"""
import csv, gzip, io, math, sys


def load(path):
    return list(csv.DictReader(io.StringIO(gzip.open(path).read().decode())))


def f(x):
    try:
        return float(x)
    except Exception:
        return None


def table(rows, key, title):
    groups = {}
    for r in rows:
        groups.setdefault(key(r), []).append(r)
    print(f"\n{title}\n{'group':28} {'n':>6} {'filled':>7} {'fill%':>6} {'fill$':>8} {'mean fill_ev':>13} {'se':>7}")
    for k, g in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        filled = [r for r in g if (f(r["filled"]) or 0) > 0]
        evs = [f(r["fill_ev"]) for r in filled if f(r["fill_ev"]) is not None]
        m = sum(evs) / len(evs) if evs else None
        se = (math.sqrt(sum((e - m) ** 2 for e in evs) / (len(evs) - 1)) / math.sqrt(len(evs))) if len(evs) > 1 else None
        print(f"{str(k)[:28]:28} {len(g):6d} {len(filled):7d} {100 * len(filled) / len(g):5.1f}% {sum(f(r['paid_usd']) or 0 for r in g):8.2f} {('%+.3f' % m) if m is not None else '-':>13} {('%.3f' % se) if se is not None else '-':>7}")


def band(x, edges):
    v = f(x)
    if v is None:
        return "?"
    for e in edges:
        if v < e:
            return f"<{e}"
    return f">={edges[-1]}"


def main(path):
    rows = [r for r in load(path) if r["status"] not in ("RESTING", "SENT", "CANCELING")]
    print(f"{len(rows)} ended bids")
    table(rows, lambda r: r["kind"], "by kind")
    table(rows, lambda r: band(r["hours_to_start"], [3, 6, 12, 24, 48]), "by hours to the start")
    table(rows, lambda r: band(r["margin"], [0.03, 0.04, 0.05, 0.07]), "by margin under the fair")
    table(rows, lambda r: "rung " + r["tier_rung"] + (" riskier" if r["overflow"] == "1" else ""), "by rung")
    table(rows, lambda r: r["league"], "by league")


def selftest():
    import tempfile, os
    p = os.path.join(tempfile.mkdtemp(), "b.csv.gz")
    cols = ["kind", "hours_to_start", "margin", "tier_rung", "overflow", "league", "status", "filled", "paid_usd", "fill_ev"]
    with gzip.open(p, "wt", newline="") as w:
        cw = csv.writer(w); cw.writerow(cols)
        cw.writerow(["PROP", "5", "0.04", "0", "0", "NHL", "FILLED", "10", "4.8", "0.02"])
        cw.writerow(["PROP", "5", "0.04", "0", "0", "NHL", "CANCELED", "0", "0", ""])
        cw.writerow(["PROP", "5", "0.04", "0", "0", "NHL", "RESTING", "0", "0", ""])
    main(p)
    print("selftest ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["selftest"]: selftest()
    elif len(sys.argv) == 2: main(sys.argv[1])
    else: print(__doc__); sys.exit(2)
