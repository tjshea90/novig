#!/usr/bin/env python3
"""The engine's flat analysis datasets for Claude (Tj, 2026-10-16, TCL33): one row per bet the engine's scan saw, and one row per bid it posted.

    python3 -I tools/lines_dataset.py lines <dir with study-<day>.jsonl from engine_study.py assemble> <out dir>   -> <out>/lines-<day>.csv.gz for every day
    python3 -I tools/lines_dataset.py bids  <dir with bids-<day>-run<id>.jsonl.gz segments> <out dir>               -> <out>/bids.csv.gz (one row per bid, the newest record of each)
    python3 -I tools/lines_dataset.py dictionary                                                                   -> prints DATA_DICTIONARY.md
    python3 -I tools/lines_dataset.py selftest

WHY CSV: Claude (or pandas) reads one row per bet with a fixed header; the journal underneath (study-<day>.jsonl) stays the record. The files are DERIVED, so each run rebuilds them whole.
Every priced line within 2 points of break-even is in, shown on the list or not (`shown`, `not_shown_why`), because the near misses are what tell whether a rule is too strict.
"""
import csv, gzip, io, json, os, re, sys

LINES_COLS = [
    "id", "day", "first_seen_utc", "starts_utc", "min_to_start", "league", "sport", "kind", "market", "selection", "event",
    "shown", "not_shown_why", "american", "cost", "ev", "fair", "fair_american", "fair_method", "fair_age_s", "novig_age_s", "avail_usd",
    "books_two_sided", "books_agreeing", "agree_share", "sharp_in_fair", "sharp_agrees", "sharp_verdict", "stars", "fair_spread", "n_fair_books",
    "n_looks", "ev_max", "ev_last", "last_listed_min_to_start", "gone",
    "status", "profit_units", "close_fair", "close_via", "clv", "clv_note", "market_id", "outcome_id",
]


def r(x, n=5):
    return "" if x is None else (round(x, n) if isinstance(x, float) else x)


def utc(ms):
    import datetime
    return "" if not ms else datetime.datetime.fromtimestamp(ms / 1000, datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def lines(src, out):
    os.makedirs(out, exist_ok=True)
    for f in sorted(os.listdir(src)):
        m = re.match(r"^study-(\d{4}-\d{2}-\d{2})\.jsonl$", f)
        if not m:
            continue
        day = m.group(1)
        bets, looks, res = {}, {}, {}
        for raw in open(os.path.join(src, f), "rb"):
            try:
                d = json.loads(raw)
            except Exception:
                continue
            e, i = d.get("e"), d.get("id")
            if e == "bet":
                bets.setdefault(i, d)
            elif e == "s" and d.get("s", {}).get("k") == "v":
                looks.setdefault(i, []).append(d)
            elif e == "res" and d.get("r"):
                res.setdefault(i, {}).update({k: v for k, v in d["r"].items() if v is not None})
        rows = []
        for i, d in bets.items():
            b = d.get("b", {})
            a = b.get("atBet") or {}
            rules = a.get("rules") or ""
            hidden = rules.startswith("NOT SHOWN (")
            why = rules[len("NOT SHOWN ("):rules.index(")")] if hidden else ""
            ls = looks.get(i, [])
            evs = [x["s"].get("ev") for x in ls if x["s"].get("ev") is not None]
            rs = res.get(i, {})
            cost = b.get("cost")
            status = rs.get("status") or b.get("status") or "PENDING"
            profit = None
            if cost and status == "WON":
                profit = (1 - cost) / cost
            elif status == "LOST":
                profit = -1.0
            elif status in ("PUSH", "VOID"):
                profit = 0.0
            cf = rs.get("closeFair") if rs.get("closeFair") is not None else b.get("closeFair")
            clv = (cf / cost - 1) if (cf and cost) else None
            two, agree = a.get("twoSided"), a.get("agreeing")
            rows.append([
                i, day, utc(d.get("t")), utc(b.get("startsTs")), a.get("minutesToStart"), b.get("league"), a.get("sport"), a.get("kind"), b.get("marketLabel"), b.get("selection"), b.get("eventName"),
                0 if hidden else 1, why, b.get("american") or a.get("american"), r(cost), r(a.get("ev") if a.get("ev") is not None else b.get("evPercentAtBet")), r(a.get("fair")), a.get("fairAmerican"),
                a.get("fairMethod"), a.get("fairAgeSec"), a.get("novigAgeSec"), r(a.get("available"), 2),
                two, agree, r(agree / two, 3) if two else "", 1 if a.get("fairSharp") or (b.get("fairBasis") or {}).get("sharp") else 0,
                "" if a.get("sharpAgrees") is None else int(bool(a.get("sharpAgrees"))), a.get("sharpVerdict"), a.get("stars"), r(a.get("fairSpread")), len(a.get("fairBooks") or []),
                len(ls), r(max(evs) if evs else None), r(evs[-1] if evs else None), "", "",
                status, r(profit), r(cf), rs.get("closeVia") or b.get("closeVia"), r(clv), rs.get("closeNote") or "", b.get("marketId"), b.get("outcomeId"),
            ])
        path = os.path.join(out, f"lines-{day}.csv.gz")
        with gzip.open(path, "wt", newline="", encoding="utf-8", compresslevel=9) as w:
            cw = csv.writer(w)
            cw.writerow(LINES_COLS)
            cw.writerows(rows)
        print(f"{path}: {len(rows)} bets ({sum(1 for x in rows if x[11] == 0)} not shown), {sum(1 for x in rows if x[LINES_COLS.index('status')] in ('WON','LOST','PUSH'))} settled")


BIDS_COLS = [
    "client_id", "day", "posted_utc", "ended_utc", "starts_utc", "hours_to_start", "league", "kind", "market", "selection", "event", "tier_rung", "overflow", "obscure", "price", "contracts",
    "fair", "ev_at_fair", "margin", "books", "fair_age_s", "best_bid_at_post", "offer_at_post", "status", "filled", "paid_usd", "fill_delay_s", "fair_at_fill", "fill_ev", "why",
]


def bids(src, out):
    os.makedirs(out, exist_ok=True)
    latest = {}
    for f in sorted(os.listdir(src)):
        if not re.match(r"^bids-\d{4}-\d{2}-\d{2}-run\d+\.jsonl\.gz$", f):
            continue
        try:
            data = gzip.open(os.path.join(src, f)).read().decode()
        except Exception as e:
            print(f"skipped {f}: {e}", file=sys.stderr)
            continue
        for raw in data.splitlines():
            try:
                b = json.loads(raw)
            except Exception:
                continue
            old = latest.get(b.get("clientId"))
            if old is None or (b.get("endedAtMs") or 0) >= (old.get("endedAtMs") or 0):
                latest[b["clientId"]] = b
    rows = []
    for c, b in latest.items():
        posted, ended, starts = b.get("postedAtMs"), b.get("endedAtMs"), b.get("startsTs")
        fair_at_fill = b.get("fairAtFill")
        price = b.get("price")
        fill_ev = (fair_at_fill / price - 1) if (fair_at_fill and price) else None
        rows.append([
            c, utc(posted)[:10], utc(posted), utc(ended), utc(starts), r((starts - posted) / 3.6e6, 2) if starts and posted else "", b.get("league"), b.get("kind"), b.get("marketLabel"), b.get("selection"), b.get("eventName"),
            b.get("rung", 0), int(bool(b.get("overflow"))), int(bool(b.get("obscure"))), price, b.get("contracts"), r(b.get("fair")), r(b.get("evAtFair")), b.get("margin"), b.get("books"), b.get("fairAgeSec"),
            b.get("bestBidAtPost"), b.get("offerAtPost"), b.get("status"), b.get("filled", 0), r(b.get("paid", 0.0), 4),
            r(((b["firstFillAtMs"] - posted) / 1000) if b.get("firstFillAtMs") and posted else None, 1), r(fair_at_fill), r(fill_ev), b.get("why"),
        ])
    rows.sort(key=lambda x: x[2])
    path = os.path.join(out, "bids.csv.gz")
    with gzip.open(path, "wt", newline="", encoding="utf-8", compresslevel=9) as w:
        cw = csv.writer(w)
        cw.writerow(BIDS_COLS)
        cw.writerows(rows)
    filled = sum(1 for x in rows if (x[BIDS_COLS.index("filled")] or 0) > 0)
    print(f"{path}: {len(rows)} bids, {filled} filled")


DICTIONARY = """# Engine datasets (Tj, 2026-10-16, TCL33)

Built by `tools/lines_dataset.py` from the engine's journals (Releases `cloud-archive-*`: `study-<day>-run<id>.jsonl.gz`, `bids-<day>-run<id>.jsonl.gz`; grades in `engine-grades`). Rebuilt whole by `engine-grade.yml` twice a day and
kept in the `engine-dataset` Release as `lines-<day>.csv.gz` and `bids.csv.gz`. Probabilities are 0-1, edges are fractions (0.036 = 3.6%), `cost` is what a $1 payout costs at Novig's price (the implied probability; no fee pregame).

## lines-<day>.csv.gz: one row per bet the engine's scan saw (EV at or above -2%), shown on the list or not
id, day (UTC), first_seen_utc, starts_utc, min_to_start (minutes at the first look), league, sport, kind (MONEYLINE, SPREAD, TOTAL, TEAM_TOTAL, PROP...), market, selection, event,
shown (1 = the list's filters showed it at the first look), not_shown_why (edge under the minimum, not +EV, odds outside the window, ...), american (Novig's price then), cost, ev (edge at that price), fair (devigged probability), fair_american,
fair_method, fair_age_s (age of the oldest book price behind the fair), novig_age_s, avail_usd (dollars Novig had at the price),
books_two_sided (companies pricing both sides), books_agreeing (of those, how many say the bet is +EV), agree_share, sharp_in_fair (a sharp book is part of the fair), sharp_agrees (1/0, blank = unknown), sharp_verdict, stars (the 0.5-5 rating), fair_spread (disagreement between books), n_fair_books,
n_looks (scans that saw it), ev_max, ev_last, status (PENDING, WON, LOST, PUSH, VOID), profit_units (per $1 staked at the first price: win = (1-cost)/cost, loss = -1), close_fair (the side's devigged probability at the start),
close_via (where the close came from), clv (close_fair / cost - 1: positive = beat the close; the best early predictor of profit), clv_note (why there is no close), market_id, outcome_id.
Sample sizes: a bet is a row once; the same line re-priced later is the same row (n_looks). Judge rules with CLV first (many more rows than results), then ROI with t-statistics (ROI is noisy).

## bids.csv.gz: one row per bid the engine posted (its whole life)
client_id, day, posted_utc, ended_utc, starts_utc, hours_to_start, league, kind, market, selection, event, tier_rung (0 usual, 1 deeper), overflow (riskier bid that keeps the wallet working), obscure, price, contracts, fair, ev_at_fair, margin, books, fair_age_s,
best_bid_at_post, offer_at_post, status (RESTING..FILLED, CANCELED, EXPIRED, VOIDED, REFUSED), filled (contracts), paid_usd, fill_delay_s, fair_at_fill (the fair on the first scan after the fill), fill_ev (fair_at_fill / price - 1; negative = picked off), why.
Fill rate = share of bids with filled > 0, by kind / hours_to_start / margin / books. Quality = fill_ev and, once graded, the matching bid-fill entry in trades.json (note starts with bid) and its CLV in lines.
"""


def selftest():
    import tempfile
    d = tempfile.mkdtemp()
    bet = {"e": "bet", "id": "a", "t": 1792103553700, "b": {"id": "a", "league": "NBA", "eventName": "A @ B", "startsTs": 1792107000000, "marketLabel": "Spread", "selection": "A -2.5", "cost": 0.5, "american": 100,
           "atBet": {"rules": "NOT SHOWN (edge under the 2.9% minimum) · x", "kind": "SPREAD", "ev": 0.01, "twoSided": 8, "agreeing": 6, "sharpAgrees": True, "stars": 3.0, "fairBooks": ["x", "y"], "minutesToStart": 60}}}
    look = {"e": "s", "id": "a", "t": 1, "s": {"k": "v", "ev": 0.02}}
    resl = {"e": "res", "id": "a", "t": 2, "r": {"status": "WON", "closeFair": 0.55, "closeVia": "pinnacle"}}
    open(os.path.join(d, "study-2026-10-16.jsonl"), "w").write("\n".join(json.dumps(x) for x in (bet, look, resl)) + "\n")
    lines(d, d)
    rows = list(csv.DictReader(io.StringIO(gzip.open(os.path.join(d, "lines-2026-10-16.csv.gz")).read().decode())))
    assert len(rows) == 1 and rows[0]["shown"] == "0" and rows[0]["not_shown_why"].startswith("edge under"), rows
    assert rows[0]["status"] == "WON" and rows[0]["profit_units"] == "1.0" and rows[0]["clv"] == "0.1" and rows[0]["agree_share"] == "0.75" and rows[0]["sharp_agrees"] == "1", rows
    b = {"clientId": "c1", "postedAtMs": 1792103553700, "endedAtMs": 1792103653700, "startsTs": 1792113553700, "price": 0.48, "contracts": 50, "filled": 10, "paid": 4.8, "fair": 0.52, "evAtFair": 0.083, "status": "FILLED", "fairAtFill": 0.5, "firstFillAtMs": 1792103563700, "league": "NHL"}
    with gzip.open(os.path.join(d, "bids-2026-10-16-run1.jsonl.gz"), "wt") as w:
        w.write(json.dumps(b) + "\n" + json.dumps({**b, "endedAtMs": 1792103753700, "filled": 12}) + "\n")
    bids(d, d)
    rows = list(csv.DictReader(io.StringIO(gzip.open(os.path.join(d, "bids.csv.gz")).read().decode())))
    assert len(rows) == 1 and rows[0]["filled"] == "12" and rows[0]["fill_delay_s"] == "10.0" and rows[0]["hours_to_start"] == "2.78", rows
    print("selftest ok")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["lines"] and len(a) == 3: lines(a[1], a[2])
    elif a[:1] == ["bids"] and len(a) == 3: bids(a[1], a[2])
    elif a[:1] == ["dictionary"]: print(DICTIONARY)
    elif a[:1] == ["selftest"]: selftest()
    else: print(__doc__); sys.exit(2)
