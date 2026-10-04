#!/usr/bin/env python3
"""Which Novig markets do takers actually trade? "Popular" against "obscure" props, measured (RESEARCH.md §81.4).

Tj, 2026-10-04: "If the auto bid function isn't getting enough bids taken … with more attractive bets that involve bets that are more popular than obscure players props."

A bid fills only when a taker crosses it, so a market's popularity is the dollars takers trade in it. Novig publishes, for every day, every listed market with its `dailyVolume`
(NOVIG_API.md §10: `/<date>/markets.csv`, zero-volume markets included), keyed by `reportTicker` ("NFL-RECEIVING_YARDS"). For each ticker this prints how many markets were listed,
what share traded at all, and the mean dollars traded per LISTED market (the number a bid placed on a random market of that type can hope to meet), then the Kotlin map
`MarketPopularity.DAILY_DOLLARS` is built from (league|type -> dollars).

    python3 tools/research/novig_popularity_study.py [--dates 2026-09-27,2026-09-28,2026-10-01,2026-10-02] [--cache DIR] [--min-listed 60] [--kotlin]

Pure Python (csv): no pandas. A market listed on several days counts once, with its largest daily volume.
"""
import argparse, csv, glob, os, statistics, urllib.request

BASE = "https://data.novig.com/reporting/trade-data"
LEAGUES = ("NFL", "NCAAF", "NHL", "MLB", "WNBA")


def fetch(date, cache):
    path = os.path.join(cache, f"markets_{date}.csv")
    if not os.path.exists(path):
        os.makedirs(cache, exist_ok=True)
        urllib.request.urlretrieve(f"{BASE}/{date}/markets.csv", path)
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dates", default="2026-09-27,2026-09-28,2026-10-01,2026-10-02")
    ap.add_argument("--cache", default="/tmp/novig_popularity")
    ap.add_argument("--min-listed", type=int, default=60)
    ap.add_argument("--kotlin", action="store_true", help="print the Kotlin map entries")
    a = ap.parse_args()
    volume, ticker = {}, {}
    for d in a.dates.split(","):
        with open(fetch(d, a.cache), newline="") as fh:
            for row in csv.DictReader(fh):
                m = row["marketId"]
                volume[m] = max(volume.get(m, 0.0), float(row["dailyVolume"] or 0))
                ticker[m] = row["reportTicker"]
    by = {}
    for m, t in ticker.items():
        by.setdefault(t, []).append(volume[m])
    rows = []
    for t, v in by.items():
        if len(v) < a.min_listed or not t.startswith(tuple(l + "-" for l in LEAGUES)):
            continue
        traded = [x for x in v if x > 0]
        rows.append((t, len(v), 100.0 * len(traded) / len(v), sum(v) / len(v), statistics.median(traded) if traded else 0.0))
    rows.sort(key=lambda r: (r[0].split("-")[0], -r[3]))
    print("ticker | listed markets | % that traded | mean $ per listed market | median $ among the traded")
    for r in rows:
        print(f"{r[0]:40} {r[1]:6} {r[2]:6.1f}% {r[3]:10.1f} {r[4]:10.1f}")
    if a.kotlin:
        print()
        for r in rows:
            league, kind = r[0].split("-", 1)
            if r[3] > 0:
                print(f'        "{league}|{kind}" to {r[3]:.0f}.0,')


if __name__ == "__main__":
    main()
