#!/usr/bin/env python3
"""Point-in-time backtest for the star rating (SX, Tj 2026-10-24: "Build a star rating system based on solid data from all the sources including apis historical data"). Standard library only.

For finished games in PropLine's resolved-prop archive (Releases propline-archive-*: resolved-<sport>-novig / -pinnacle .csv.gz), reads every book's price history in ONE call per game
(`/v1/sports/{sport}/events/{id}/odds/history?bookmakers=novig,pinnacle,<US books>&changes_only=true`, Pro), and at each lead time L before the start (24, 12, 6, 3, 1 h, 15 min) rebuilds
what Vigilant would have seen THEN, using only prices recorded at or before that moment:
  * Novig's taker price on each side (the bet), Pinnacle's two sides (the sharp book) and the US books' two sides;
  * each book's own fair (multiplicative devig of its two sides), the companies pricing both sides, how many of them make Novig's price +EV, whether Pinnacle does (sharp agrees),
    the spread (standard deviation) of the books' fairs, and two fairs: Pinnacle's alone and Vigilant's blend (0.7 sharp + 0.3 the other books' average, FairSettings' default weight);
then scores the bet against Pinnacle's devigged CLOSE (the archive's closing price, only when captured within 90 minutes of the start) and the real RESULT.

Output: lines.csv.gz (one row per outcome and lead with EV at or above -2% on either fair) and SUMMARY.txt. Nothing is ordered; nothing is sent anywhere but PropLine.

  python3 -I tools/star_backtest.py run ARCHIVE_DIR OUTDIR [--events N] [--sports a,b] [--books a,b] [--shard i/n]
  python3 -I tools/star_backtest.py selftest
Keys: PROPLINE_API_KEY (the Pro one is found by tools/propline.py's plan check).
"""
import csv, datetime, glob, gzip, os, statistics as st, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import propline as pl

LEADS_H = [24, 12, 6, 3, 1, 0.25]
US_BOOKS = ["draftkings", "fanduel", "betmgm", "williamhill_us", "espnbet", "bovada"]
SHARP_WEIGHT = 0.7
CLOSE_MAX_MIN = 90
COLS = ["event_id", "sport", "start_utc", "lead_h", "market", "kind", "player", "side", "line", "novig_american", "cost", "ev_pin", "ev_blend", "pin_fair", "blend_fair",
        "books", "agreeing", "sharp_agrees", "spread", "close_fair", "clv", "novig_close_fair", "mix_close_fair", "clv_mix", "result", "profit"]


def prob(a):
    a = float(a)
    return 100 / (a + 100) if a > 0 else -a / (-a + 100)


def ts(x):
    try:
        return datetime.datetime.fromisoformat(str(x).replace("Z", "+00:00")).timestamp()
    except Exception:
        return None


def pt(x):
    try:
        return round(float(x), 3)
    except Exception:
        return None


def kind_of(market, player):
    if player:
        return "prop"
    return {"h2h": "moneyline", "spreads": "spread", "totals": "total"}.get(market, market)


def last_before(snaps, t, point):
    """The last snapshot recorded at or before t at this point (None = no point)."""
    best, bt = None, None
    for s in snaps or []:
        r = ts(s.get("recorded_at") or "")
        if r is None or r > t or pt(s.get("point")) != point or s.get("price") in (None, ""):
            continue
        if bt is None or r >= bt:
            best, bt = s, r
    return best


def other_side(side, outcomes):
    """The opposing outcome name of a two-way market (Over/Under, or the other team)."""
    if side == "Over":
        return "Under"
    if side == "Under":
        return "Over"
    rest = [o for o in outcomes if o != side]
    return rest[0] if len(rest) == 1 else None


def opp_point(market, point):
    return -point if (market == "spreads" and point is not None) else point


def load_archive(archive_dir, sports):
    """-> rows of Novig (resolved, two-way) by game, and Pinnacle's devig-ready closes."""
    games, pin_close = {}, {}
    for f in glob.glob(os.path.join(archive_dir, "**", "resolved-*-pinnacle*.csv.gz"), recursive=True) + glob.glob(os.path.join(archive_dir, "**", "resolved-*-novig*.csv.gz"), recursive=True):
        book = "novig" if "-novig" in os.path.basename(f) else "pinnacle"
        for r in csv.DictReader(gzip.open(f, "rt")):
            if r["player_name"].startswith("(Water") or not r["closing_price"]:
                continue
            c, s0 = ts(r["closing_at"]), ts(r["commence_time"])
            if c is None or s0 is None or (s0 - c) / 60 > CLOSE_MAX_MIN or c > s0 + 60:
                continue
            # SX finding 1: at the close Novig is as sharp as Pinnacle, so both closes are kept (keyed by book: "pinnacle" closes stay under the old 5-tuple key)
            k = (r["event_id"], r["market"], r["player_name"], pt(r["closing_point"] or r["line"]), r["outcome_name"])
            pin_close[k if book == "pinnacle" else ("novig",) + k] = prob(r["closing_price"])
    for f in glob.glob(os.path.join(archive_dir, "**", "resolved-*-novig*.csv.gz"), recursive=True):
        sport = os.path.basename(f).split("-")[1]
        if sports and sport not in sports:
            continue
        for r in csv.DictReader(gzip.open(f, "rt")):
            if r["player_name"].startswith("(Water") or r["resolution"] not in ("won", "lost") or r["market"] in ("outrights",):
                continue
            g = games.setdefault((sport, r["event_id"]), {})
            g[(r["market"], r["player_name"], pt(r["closing_point"] or r["line"]), r["outcome_name"])] = r
    return games, pin_close


def close_fair(pin_close, eid, market, player, point, side, opp, book="pinnacle"):
    pre = () if book == "pinnacle" else (book,)
    a = pin_close.get(pre + (eid, market, player, point, side))
    b = pin_close.get(pre + (eid, market, player, opp_point(market, point), opp))
    return None if a is None or b is None or not 0.9 < a + b < 1.25 else a / (a + b)


def evaluate(sport, eid, start, rows, hist, pin_close, books):
    """rows: {(market, player, point, side): archive row} of ONE game (Novig). hist: {book: {(market, player, side): snapshots}}. -> output rows."""
    out = []
    outcomes_by_mkt = {}
    for (m, p, point, side) in rows:
        outcomes_by_mkt.setdefault((m, p), set()).add(side)
    for (m, p, point, side), r in rows.items():
        opp = other_side(side, outcomes_by_mkt.get((m, p), set()))
        if opp is None:
            continue
        cf = close_fair(pin_close, eid, m, p, point, side, opp)
        nf = close_fair(pin_close, eid, m, p, point, side, opp, "novig")
        mix = (cf + nf) / 2 if cf is not None and nf is not None else None
        win = r["resolution"] == "won"
        for L in LEADS_H:
            t = start - L * 3600
            nv = last_before(hist.get("novig", {}).get((m, p, side)), t, point)
            if not nv:
                continue
            cost = prob(nv["price"])
            if not 0.01 < cost < 0.99:
                continue
            fairs, pin_f = {}, None
            for bk in ["pinnacle"] + books:
                a = last_before(hist.get(bk, {}).get((m, p, side)), t, point)
                b = last_before(hist.get(bk, {}).get((m, p, opp)), t, opp_point(m, point))
                if not a or not b:
                    continue
                pa, pb = prob(a["price"]), prob(b["price"])
                if pa + pb < 0.9 or pa + pb > 1.25:
                    continue                      # one side stale or mismatched
                fairs[bk] = pa / (pa + pb)
            pin_f = fairs.get("pinnacle")
            if not fairs:
                continue
            others = [v for k, v in fairs.items() if k != "pinnacle"]
            if pin_f is not None:
                blend = SHARP_WEIGHT * pin_f + (1 - SHARP_WEIGHT) * st.mean(others) if others else pin_f
            else:
                blend = st.mean(others)
            ev_pin = (pin_f / cost - 1) if pin_f is not None else None
            ev_blend = blend / cost - 1
            if max(ev_blend, ev_pin if ev_pin is not None else -1) < -0.02:
                continue
            agreeing = sum(1 for v in fairs.values() if v / cost - 1 > 0)
            spread = st.pstdev(list(fairs.values())) if len(fairs) >= 2 else None
            d = 1 / cost
            out.append({
                "event_id": eid, "sport": sport, "start_utc": r["commence_time"], "lead_h": L, "market": m, "kind": kind_of(m, p), "player": p, "side": side, "line": "" if point is None else point,
                "novig_american": nv["price"], "cost": round(cost, 5), "ev_pin": "" if ev_pin is None else round(ev_pin, 5), "ev_blend": round(ev_blend, 5),
                "pin_fair": "" if pin_f is None else round(pin_f, 5), "blend_fair": round(blend, 5), "books": len(fairs), "agreeing": agreeing,
                "sharp_agrees": "" if ev_pin is None else (1 if ev_pin > 0 else 0), "spread": "" if spread is None else round(spread, 5),
                "close_fair": "" if cf is None else round(cf, 5), "clv": "" if cf is None else round(cf / cost - 1, 5),
                "novig_close_fair": "" if nf is None else round(nf, 5), "mix_close_fair": "" if mix is None else round(mix, 5), "clv_mix": "" if mix is None else round(mix / cost - 1, 5),
                "result": "won" if win else "lost",
                "profit": round(d - 1, 5) if win else -1.0,
            })
    return out


def parse_history(body):
    """PropLine /odds/history -> {book: {(market, player, side): snapshots}}."""
    hist = {}
    for bk in (body or {}).get("bookmakers", []):
        key = bk.get("key") or bk.get("bookmaker") or ""
        for m in bk.get("markets", []):
            mk = m.get("key") or m.get("market") or ""
            for o in m.get("outcomes", []):
                player = o.get("description") or o.get("player_name") or ""
                side = o.get("name") or o.get("outcome_name") or ""
                hist.setdefault(key, {}).setdefault((mk, player, side), []).extend(o.get("snapshots") or [])
    return hist


def summarize(rows):
    lines = ["STAR BACKTEST (tools/star_backtest.py): Novig's price at each lead time against the fair Vigilant would have had then, scored on Pinnacle's close and the result", ""]
    pos = [x for x in rows if x["ev_blend"] > 0]
    lines.append("%d rows, %d +EV on the blended fair, %d games" % (len(rows), len(pos), len({x["event_id"] for x in rows})))
    for lab, lo, hi in [("EV <0", -1, 0), ("0-0.5%", 0, 0.005), ("0.5-1%", 0.005, 0.01), ("1-2%", 0.01, 0.02), ("2-3%", 0.02, 0.03), ("3-5%", 0.03, 0.05), ("5-8%", 0.05, 0.08), ("8%+", 0.08, 9)]:
        sub = [x for x in rows if lo <= x["ev_blend"] < hi]
        c = [x["clv"] for x in sub if x["clv"] != ""]
        p = [x["profit"] for x in sub]
        if sub:
            lines.append("  %-8s %6d rows · CLV %5d %+.2f%% · ROI %+.2f%%" % (lab, len(sub), len(c), 100 * st.mean(c) if c else 0, 100 * st.mean(p)))
    return "\n".join(lines) + "\n"


def cmd_run(archive_dir, outdir, n_events, sports, books, shard=(0, 1)):
    keys = pl.keys_from_env()
    key, plan = pl.need_paid(keys) if keys else (None, None)
    if key is None:
        print("no paid PropLine key among the secrets: nothing to do")
        return 0
    os.makedirs(outdir, exist_ok=True)
    games, pin_close = load_archive(archive_dir, sports)
    now = datetime.datetime.now(datetime.timezone.utc).timestamp()
    cut = now - 88 * 86400                      # Pro's history depth is 90 days
    order = sorted(((k, v) for k, v in games.items() if (ts(next(iter(v.values()))["commence_time"]) or 0) > cut), key=lambda kv: -(ts(next(iter(kv[1].values()))["commence_time"]) or 0))[:n_events]
    order = [kv for j, kv in enumerate(order) if j % shard[1] == shard[0]]     # shard i of n: every n-th game, so the shards run side by side
    print("%d games in the archive, %d inside the history window, reading %d (shard %d of %d)" % (len(games), sum(1 for v in games.values() if (ts(next(iter(v.values()))["commence_time"]) or 0) > cut), len(order), shard[0], shard[1]), flush=True)
    tag = "" if shard[1] == 1 else "-%d" % shard[0]
    path = os.path.join(outdir, "lines%s.csv.gz" % tag)
    allrows, calls = [], 0
    with gzip.open(path, "wt", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=COLS)
        w.writeheader()
        for i, ((sport, eid), rows) in enumerate(order):
            start = ts(next(iter(rows.values()))["commence_time"])
            mkts = sorted({m for (m, p, point, side) in rows})
            hist = {}
            for j in range(0, len(mkts), 40):
                st_, h, data = pl.request("/v1/sports/%s/events/%s/odds/history" % (sport, eid), key,
                                          {"markets": ",".join(mkts[j:j + 40]), "bookmakers": ",".join(["novig", "pinnacle"] + books), "changes_only": "true", "relative_from": "-8d", "relative_to": "0"})
                calls += 1
                if st_ == 429:
                    print("429 after %d calls: stopping" % calls)
                    break
                if st_ != 200:
                    continue
                for bk, d in parse_history(pl.j(data)).items():
                    for k, v in d.items():
                        hist.setdefault(bk, {}).setdefault(k, []).extend(v)
            got = evaluate(sport, eid, start, rows, hist, pin_close, books)
            w.writerows(got)
            allrows += got
            if i % 50 == 0:
                print("%d/%d games, %d calls, %d rows" % (i, len(order), calls, len(allrows)), flush=True)
    s = summarize(allrows)
    open(os.path.join(outdir, "SUMMARY%s.txt" % tag), "w").write(s + "\n%d history calls\n" % calls)
    print(s)
    return 0


def selftest():
    snaps = [{"recorded_at": "2026-10-10T00:00:00Z", "price": 110, "point": 5.5}, {"recorded_at": "2026-10-10T10:00:00Z", "price": 120, "point": 5.5}]
    t = ts("2026-10-10T12:00:00Z")
    assert last_before(snaps, t, 5.5)["price"] == 120 and last_before(snaps, t - 11 * 3600, 5.5)["price"] == 110 and last_before(snaps, t - 13 * 3600, 5.5) is None
    assert other_side("Over", {"Over", "Under"}) == "Under" and other_side("A", {"A", "B"}) == "B" and other_side("A", {"A"}) is None
    assert opp_point("spreads", -3.5) == 3.5 and opp_point("totals", 5.5) == 5.5
    start = ts("2026-10-10T13:00:00Z")
    row = {"commence_time": "2026-10-10T13:00:00Z", "resolution": "won"}
    rows = {("totals", "", 5.5, "Over"): row, ("totals", "", 5.5, "Under"): dict(row, resolution="lost")}
    hist = {"novig": {("totals", "", "Over"): [{"recorded_at": "2026-10-10T11:00:00Z", "price": 110, "point": 5.5}], ("totals", "", "Under"): [{"recorded_at": "2026-10-10T11:00:00Z", "price": -130, "point": 5.5}]},
            "pinnacle": {("totals", "", "Over"): [{"recorded_at": "2026-10-10T10:00:00Z", "price": -105, "point": 5.5}], ("totals", "", "Under"): [{"recorded_at": "2026-10-10T10:00:00Z", "price": -115, "point": 5.5}]},
            "draftkings": {("totals", "", "Over"): [{"recorded_at": "2026-10-10T09:00:00Z", "price": -110, "point": 5.5}], ("totals", "", "Under"): [{"recorded_at": "2026-10-10T09:00:00Z", "price": -110, "point": 5.5}]}}
    pin_close = {("e", "totals", "", 5.5, "Over"): prob(-120), ("e", "totals", "", 5.5, "Under"): prob(100),
                 ("novig", "e", "totals", "", 5.5, "Over"): prob(-110), ("novig", "e", "totals", "", 5.5, "Under"): prob(-110)}
    got = evaluate("x", "e", start, rows, hist, pin_close, ["draftkings"])
    over1 = [g for g in got if g["side"] == "Over" and g["lead_h"] == 1][0]
    pa, pb = prob(-105), prob(-115)
    pf = pa / (pa + pb)
    assert abs(over1["pin_fair"] - round(pf, 5)) < 1e-9 and over1["books"] == 2 and over1["sharp_agrees"] == (1 if pf / prob(110) > 1 else 0)
    assert abs(over1["blend_fair"] - round(0.7 * pf + 0.3 * 0.5, 5)) < 1e-9
    cf = prob(-120) / (prob(-120) + prob(100))
    assert abs(over1["clv"] - round(cf / prob(110) - 1, 5)) < 1e-9 and over1["profit"] == round(1 / prob(110) - 1, 5)
    assert abs(over1["novig_close_fair"] - 0.5) < 1e-9 and abs(over1["clv_mix"] - round(((cf + 0.5) / 2) / prob(110) - 1, 5)) < 1e-9
    assert not [g for g in got if g["lead_h"] == 3]          # Novig had no price 3 h out
    body = {"bookmakers": [{"key": "novig", "markets": [{"key": "totals", "outcomes": [{"name": "Over", "snapshots": [{"price": 100}]}]}]}]}
    assert parse_history(body) == {"novig": {("totals", "", "Over"): [{"price": 100}]}}
    assert "STAR BACKTEST" in summarize(got)
    print("star backtest selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "run":
        n = int(a[a.index("--events") + 1]) if "--events" in a else 1500
        sp = set(a[a.index("--sports") + 1].split(",")) if "--sports" in a else None
        bk = a[a.index("--books") + 1].split(",") if "--books" in a else US_BOOKS
        sh = tuple(int(v) for v in a[a.index("--shard") + 1].split("/")) if "--shard" in a else (0, 1)
        sys.exit(cmd_run(a[1], a[2], n, sp, bk, sh))
    print(__doc__)
