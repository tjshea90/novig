#!/usr/bin/env python3
"""The engine's VOLUME of bets and bids, and what holds it back, as one short report (TCL30d + XO2, Tj 2026-10-15 "Volume AND quality, every run" and 2026-10-21 "diagnose the low volume of bid fills and auto bets").

  python3 tools/volume_report.py run --trades trades.json --bids bids.csv.gz --passes 'dir-or-files-of-tradepass-*.jsonl.gz' [--days 6] [--out volume.md]
  python3 tools/volume_report.py selftest

Reads files only (no key, no API): the engine's ledger (`trades.json` on the `cloud-feed` branch), the bid dataset (`bids.csv.gz`, Release `engine-dataset`, one row per bid with its whole life) and the
auto-bet's pass journals (`tradepass-<day>-run<id>.jsonl.gz`, Releases `cloud-archive-*`, one line per trading pass: looked / passed / ordered and a held-back reason with its count). Prints what the
2026-10-21 study (research/bid_volume_study_2026-10-21.md) did by hand: orders a day, bids posted and filled a day with the fill rate per 1000 postings, the fill rate by hours to the start and by how far
the bid sat under Novig's offer, and the auto-bet's held-back reasons by day, so an ANALYST run reads one table instead of re-crunching 340,000 rows. A volume that falls with the postings rising is the
signature of bids that cannot fill; an auto-bet whose top reason is the trap guard is a supply problem, not a fault.
"""
import argparse, csv, glob, gzip, io, json, os, re, sys
from collections import Counter, defaultdict
from datetime import datetime, timezone, timedelta

ET = timezone(timedelta(hours=-4))  # the engine's day is Eastern; the offset is only used to name days in the report


def day_of_ms(ms):
    return datetime.fromtimestamp(ms / 1000, tz=ET).strftime("%m-%d")


def open_text(path):
    with open(path, "rb") as f:
        gz = f.read(2) == b"\x1f\x8b"
    return gzip.open(path, "rt", newline="") if gz else open(path, "rt", newline="")


def num(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return None


def bid_report(rows, days):
    """rows: dicts of bids.csv (posted_utc 'YYYY-MM-DD HH:MM:SS', filled, hours_to_start, price, offer_at_post, why). Last [days] UTC days."""
    by_day = defaultdict(lambda: [0, 0, 0.0])      # posted, fills, paid
    bands_h = defaultdict(lambda: [0, 0])
    bands_g = defaultdict(lambda: [0, 0])
    why = Counter()
    all_days = sorted({r["posted_utc"][:10] for r in rows if r.get("posted_utc")})
    keep = set(all_days[-days:])
    HB = [(1, "<1 h"), (2, "1-2 h"), (6, "2-6 h"), (12, "6-12 h"), (24, "12-24 h"), (1e9, "24 h+")]
    GB = [(1, "0-1 pt"), (2, "1-2"), (3, "2-3"), (5, "3-5"), (15, "5-15"), (1e9, "15+")]
    for r in rows:
        d = (r.get("posted_utc") or "")[:10]
        if d not in keep:
            continue
        filled = (num(r.get("filled")) or 0) > 0
        a = by_day[d]
        a[0] += 1
        a[1] += 1 if filled else 0
        a[2] += num(r.get("paid_usd")) or 0.0
        h = num(r.get("hours_to_start"))
        if h is not None:
            for lim, name in HB:
                if h < lim:
                    bands_h[name][0] += 1; bands_h[name][1] += 1 if filled else 0; break
        p, o = num(r.get("price")), num(r.get("offer_at_post"))
        name = "no offer"
        if p is not None and o is not None:
            g = round((o - p) * 100, 6)
            name = next(n for lim, n in GB if g <= lim)
        bands_g[name][0] += 1; bands_g[name][1] += 1 if filled else 0
        why[(r.get("why") or "").strip()[:56] or "(no reason: filled or still resting)"] += 1
    return by_day, bands_h, bands_g, why, HB, GB


def pass_report(passes):
    """passes: dicts of a pass journal line {atMs, looked, passed, ordered, spent, skipped{reason: n}}."""
    by_day = defaultdict(lambda: {"passes": 0, "looked": 0, "passed": 0, "ordered": 0, "spent": 0.0, "why": Counter()})
    for p in passes:
        a = by_day[day_of_ms(p["atMs"])]
        a["passes"] += 1
        a["looked"] += p.get("looked", 0); a["passed"] += p.get("passed", 0); a["ordered"] += p.get("ordered", 0); a["spent"] += p.get("spent", 0.0) or 0.0
        for k, v in (p.get("skipped") or {}).items():
            a["why"][re.sub(r"[0-9]+(\.[0-9]+)?", "#", k)[:70]] += v
    return by_day


def orders_by_day(entries):
    out = defaultdict(lambda: {"bets": 0, "bid fills": 0, "stake": 0.0})
    for e in entries:
        a = out[day_of_ms(e["atMs"])]
        a["bid fills" if str(e.get("note") or "").startswith("bid") else "bets"] += 1
        a["stake"] += e.get("cost") or 0.0
    return out


def render(trades, bid_rows, passes, days):
    L = ["# The engine's volume (tools/volume_report.py)", ""]
    od = orders_by_day(trades)
    L += ["## Orders a day (the ledger, Eastern days)", "", "| day | auto-bets | bid fills | dollars staked |", "|:--|--:|--:|--:|"]
    for d in sorted(od)[-days:]:
        L.append(f"| {d} | {od[d]['bets']} | {od[d]['bid fills']} | ${od[d]['stake']:.2f} |")
    if bid_rows:
        by_day, bh, bg, why, HB, GB = bid_report(bid_rows, days)
        L += ["", "## Bids a day (UTC days; a fill rate under 2 per 1000 postings means most postings cannot fill)", "", "| day | posted | fills | fills per 1000 | paid |", "|:--|--:|--:|--:|--:|"]
        for d in sorted(by_day):
            a = by_day[d]
            L.append(f"| {d} | {a[0]:,} | {a[1]} | {a[1] / a[0] * 1000:.2f} | ${a[2]:.2f} |")
        L += ["", "Fills per 1000 postings by hours to the start: " + " · ".join(f"{n} {bh[n][1] / bh[n][0] * 1000:.1f} (of {bh[n][0]:,})" for _, n in HB if bh[n][0])]
        L += ["Fills per 1000 postings by how far the bid sat under Novig's offer: " + " · ".join(f"{n} {bg[n][1] / bg[n][0] * 1000:.1f} (of {bg[n][0]:,})" for n in [n for _, n in GB] + ["no offer"] if bg[n][0])]
        L += ["", "How the postings ended (top 6): " + " · ".join(f"{k} {v:,}" for k, v in why.most_common(6))]
    pd = pass_report(passes)
    if pd:
        L += ["", "## The auto-bet's passes (what it looked at, ordered, and why it did not)", "", "| day | passes | row-passes | ordered | spent | top held-back reasons (share of row-passes) |", "|:--|--:|--:|--:|--:|:--|"]
        for d in sorted(pd)[-days:]:
            a = pd[d]
            tot = sum(a["why"].values()) or 1
            L.append(f"| {d} | {a['passes']:,} | {a['looked']:,} | {a['ordered']} | ${a['spent']:.2f} | " + "; ".join(f"{v / tot * 100:.0f}% {k}" for k, v in a["why"].most_common(3)) + " |")
    return "\n".join(L) + "\n"


def load_passes(spec):
    files = []
    for part in spec:
        files += sorted(glob.glob(os.path.join(part, "*tradepass-*.jsonl.gz")) if os.path.isdir(part) else glob.glob(part))
    seen, out = set(), []
    for f in files:
        base = re.sub(r"^[^_]*__", "", os.path.basename(f))   # the same journal sits in several day Releases
        if base in seen:
            continue
        seen.add(base)
        try:
            with gzip.open(f, "rt") as fh:
                for line in fh:
                    line = line.strip()
                    if line.startswith("{"):
                        out.append(json.loads(line))
        except Exception:
            continue
    seen_at, uniq = set(), []
    for p in out:
        if p["atMs"] not in seen_at:
            seen_at.add(p["atMs"]); uniq.append(p)
    return uniq


def run(a):
    trades = json.load(open(a.trades))
    entries = trades.get("entries", trades) if isinstance(trades, dict) else trades
    rows = []
    if a.bids and os.path.exists(a.bids):
        with open_text(a.bids) as f:
            rows = list(csv.DictReader(f))
    text = render(entries, rows, load_passes(a.passes or []), a.days)
    if a.out:
        open(a.out, "w").write(text)
    else:
        sys.stdout.write(text)
    return 0


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(("ok   " if cond else "FAIL ") + name)
        ok = ok and cond

    t0 = 1_792_000_000_000
    entries = [{"atMs": t0, "cost": 0.5, "note": "bid · posted at 50c"}, {"atMs": t0 + 1000, "cost": 0.7, "note": "25% Kelly"}]
    od = orders_by_day(entries)
    check("orders split bets and bid fills", list(od.values())[0]["bets"] == 1 and list(od.values())[0]["bid fills"] == 1)
    rows = ([{"posted_utc": "2026-10-20 10:00:00", "filled": "0", "hours_to_start": "0.5", "price": "0.50", "offer_at_post": "0.51", "paid_usd": "", "why": "About to expire: re-posted"}] * 99
            + [{"posted_utc": "2026-10-20 10:00:00", "filled": "10", "hours_to_start": "0.5", "price": "0.50", "offer_at_post": "0.51", "paid_usd": "0.5", "why": ""}]
            + [{"posted_utc": "2026-10-20 11:00:00", "filled": "0", "hours_to_start": "50", "price": "0.40", "offer_at_post": "", "paid_usd": "", "why": "The fair price fell: re-posted lower"}] * 100)
    by_day, bh, bg, why, HB, GB = bid_report(rows, 3)
    check("bids a day: 200 posted, 1 fill", by_day["2026-10-20"][0] == 200 and by_day["2026-10-20"][1] == 1)
    check("near and close to the offer fills most, far and no offer least", bh["<1 h"][1] / bh["<1 h"][0] > 0 and bh["24 h+"][1] == 0 and bg["0-1 pt"][0] == 100 and bg["no offer"][0] == 100)
    passes = [{"atMs": t0, "looked": 10, "passed": 1, "ordered": 1, "spent": 0.4, "skipped": {"it starts in more than 6 h (trap guard)": 8, "its edge +1.0% is under the +2.9% it needs": 1}},
              {"atMs": t0 + 5, "looked": 4, "passed": 0, "ordered": 0, "spent": 0.0, "skipped": {"its edge +1.5% is under the +2.9% it needs": 4}}]
    pr = pass_report(passes)
    a = list(pr.values())[0]
    check("passes sum and numbers in reasons are folded", a["passes"] == 2 and a["looked"] == 14 and a["why"]["its edge +#% is under the +#% it needs"] == 5)
    text = render(entries, rows, passes, 3)
    check("the report renders its sections", "Orders a day" in text and "Bids a day" in text and "The auto-bet's passes" in text and "trap guard" in text)
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--trades", required=True)
    r.add_argument("--bids")
    r.add_argument("--passes", nargs="*")
    r.add_argument("--days", type=int, default=6)
    r.add_argument("--out")
    sub.add_parser("selftest")
    a = ap.parse_args()
    sys.exit(selftest() if a.cmd == "selftest" else run(a))


if __name__ == "__main__":
    main()
