#!/usr/bin/env python3
"""Same-game-parlay study at Novig (SGP, Tj 2026-11-17: "You have permission to use my api key ... to test and build features").

PropLine's `POST /v1/sports/{sport}/events/{id}/sgp` with `bookmaker=all` quotes a two-leg slip at every book that prices it, NOVIG INCLUDED (first real answer, Raptors ML + Over 215.5, 2026-11-17:
Novig +325 against FanDuel +282, DraftKings +296, BetMGM +290, BetRivers/Unibet +275, BetOnline/LowVig +280; correlation factor, the share of the independent price the book pays: Novig 0.964, books 0.86-0.95).
Whether Novig's milder charge is edge depends on the TRUE correlation of the two legs, which only outcomes can say. So:

  python3 -I tools/propline_sgp.py log  OUTDIR [sport,...]   quote 4 slips per game starting in the next 14 h (home/away moneyline x over/under the main total) at every book -> OUTDIR/sgp-<day>-<hhmm>.jsonl.gz
  python3 -I tools/propline_sgp.py grade IN_GLOB... OUTDIR   settle every logged slip of a finished game with POST /v1/bets/settle, then ROI per book at its own quoted price -> OUTDIR/sgp-graded.jsonl.gz + SUMMARY.md
  python3 -I tools/propline_sgp.py selftest

Read-only: places nothing anywhere; the key is only read from PROPLINE_API_KEY (the comma-separated secret) and never written. The workflow is .github/workflows/propline-sgp.yml (Releases `propline-sgp-YYYY-MM-DD`, never delete).
"""
import datetime
import glob
import gzip
import json
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import propline as pl  # noqa: E402

SPORTS = ("basketball_nba", "hockey_nhl", "basketball_ncaab", "football_nfl", "football_ncaaf")


def main_total(odds):
    """The game total as an (Over price, Under price, point) near even money from a /odds answer ({bookmakers: [{key, markets: [...]}]}): game total only (no team, no period, not an alternate); None when none."""
    best = None
    for b in (odds or {}).get("bookmakers") or []:
        for m in b.get("markets") or []:
            if m.get("key") != "totals" or m.get("team") or m.get("period") or m.get("line_type") == "alternate":
                continue
            over = {o["point"]: o["price"] for o in m.get("outcomes") or [] if o.get("name") == "Over" and o.get("point") is not None and o.get("price") is not None}
            under = {o["point"]: o["price"] for o in m.get("outcomes") or [] if o.get("name") == "Under" and o.get("point") is not None and o.get("price") is not None}
            for pt in over.keys() & under.keys():
                skew = abs(over[pt] + under[pt] + 220) if over[pt] < 0 and under[pt] < 0 else abs(over[pt] - abs(under[pt])) + 1000
                if best is None or skew < best[0]:
                    best = (skew, pt)
    return best[1] if best else None


def slips(home, away, point):
    return [[{"market": "h2h", "name": team}, {"market": "totals", "name": side, "point": point}] for team in (home, away) for side in ("Over", "Under")]


def quote_rows(resp, sport, ev, legs, at):
    rows = []
    for q in (resp or {}).get("quotes") or []:
        if not q.get("quoted", True) or q.get("sgp_price") is None:
            continue
        rows.append({"at": at, "sport": sport, "event_id": ev["id"], "start": ev.get("commence_time"), "home": ev.get("home_team"), "away": ev.get("away_team"), "legs": legs,
                     "book": q.get("bookmaker"), "sgp_price": q.get("sgp_price"), "independent_price": q.get("independent_price"), "correlation_factor": q.get("correlation_factor")})
    return rows


def dec(american):
    return 1 + american / 100.0 if american > 0 else 1 + 100.0 / -american


def cmd_log(outdir, sports=SPORTS, hours=14, max_games=24):
    keys = pl.keys_from_env()
    key, p = pl.need_paid(keys) if keys else (None, None)
    if key is None:
        print("no usable key"); return 0
    os.makedirs(outdir, exist_ok=True)
    now = datetime.datetime.now(datetime.timezone.utc)
    rows, games = [], 0
    for sp in sports:
        st, h, data = pl.request("/v1/sports/%s/events" % sp, key)
        evs = pl.j(data) if st == 200 else None
        for ev in evs if isinstance(evs, list) else []:
            try:
                start = datetime.datetime.fromisoformat(ev["commence_time"].replace("Z", "+00:00"))
            except Exception:
                continue
            if not (0 < (start - now).total_seconds() < hours * 3600) or games >= max_games:
                continue
            st, h, data = pl.request("/v1/sports/%s/events/%s/odds" % (sp, ev["id"]), key, {"markets": "totals", "bookmakers": "fanduel,draftkings,pinnacle"})
            point = main_total(pl.j(data)) if st == 200 else None
            if point is None:
                continue
            games += 1
            for legs in slips(ev["home_team"], ev["away_team"], point):
                st, h, data = pl.request("/v1/sports/%s/events/%s/sgp" % (sp, ev["id"]), key, method="POST", body={"bookmaker": "all", "legs": legs})
                if st == 200:
                    rows += quote_rows(pl.j(data), sp, ev, legs, now.strftime("%Y-%m-%dT%H:%M:%SZ"))
                time.sleep(0.4)
    if rows:
        name = os.path.join(outdir, "sgp-%s.jsonl.gz" % now.strftime("%Y-%m-%d-%H%M"))
        with gzip.open(name, "wt") as f:
            for r in rows:
                f.write(json.dumps(r) + "\n")
    novig = [r for r in rows if r["book"] == "novig"]
    print("SGP log: %d games, %d quotes (%d at Novig), %d slips" % (games, len(rows), len(novig), len({(r["event_id"], json.dumps(r["legs"])) for r in rows})))
    return 0


def read_rows(patterns):
    rows = []
    for pat in patterns:
        for fn in sorted(glob.glob(pat)):
            with gzip.open(fn, "rt") as f:
                rows += [json.loads(l) for l in f if l.strip()]
    return rows


def settle_body(unique):
    """[{ref, stake, legs}] for POST /v1/bets/settle from {ref: (sport, event_id, legs)}."""
    def leg(sp, eid, l):
        # /sgp names a leg {market, name, point}; /bets/settle names it {market, selection, side, point}: the team (h2h) or Over/Under (totals) is the selection
        out = {"sport_key": sp, "event_id": int(eid) if str(eid).isdigit() else eid, "market": l["market"], "selection": l["name"]}
        if l["market"] == "totals":
            out["side"] = l["name"]
        if l.get("point") is not None:
            out["point"] = l["point"]
        return out
    return [{"ref": ref, "stake": 1, "legs": [leg(sp, eid, l) for l in legs]} for ref, (sp, eid, legs) in unique.items()]


def grade_rows(rows, results):
    """Rows with a `result` ('won'/'lost'/'push'/'void' or None) from {ref: result}."""
    out = []
    for r in rows:
        ref = "%s|%s" % (r["event_id"], json.dumps(r["legs"], sort_keys=True))
        out.append(dict(r, result=results.get(ref)))
    return out


def summary(graded):
    """Per book: n settled slips, hit rate, mean implied probability, ROI at the book's own quote (win pays dec-1, loss -1, push/void out), with a 95% half-width."""
    by = {}
    for r in graded:
        if r.get("result") not in ("won", "lost") or r.get("sgp_price") is None:
            continue
        d = dec(r["sgp_price"])
        x = (d - 1) if r["result"] == "won" else -1.0
        b = by.setdefault(r["book"], {"n": 0, "wins": 0, "roi": [], "imp": []})
        b["n"] += 1; b["wins"] += r["result"] == "won"; b["roi"].append(x); b["imp"].append(1 / d)
    lines = ["| book | slips | hit rate | implied | ROI at its quote | +/- 95% |", "|:--|--:|--:|--:|--:|--:|"]
    for book, b in sorted(by.items(), key=lambda kv: -kv[1]["n"]):
        n = b["n"]; m = sum(b["roi"]) / n
        sd = math.sqrt(sum((x - m) ** 2 for x in b["roi"]) / max(n - 1, 1))
        lines.append("| %s | %d | %.1f%% | %.1f%% | %+.1f%% | %.1f |" % (book, n, 100.0 * b["wins"] / n, 100.0 * sum(b["imp"]) / n, 100 * m, 196 * sd / math.sqrt(n)))
    return "\n".join(lines)


def cmd_grade(patterns, outdir):
    keys = pl.keys_from_env()
    key, p = pl.need_paid(keys) if keys else (None, None)
    rows = read_rows(patterns)
    if key is None or not rows:
        print("nothing to grade (%d rows, key %s)" % (len(rows), "yes" if key else "no")); return 0
    now = datetime.datetime.now(datetime.timezone.utc)
    unique = {}
    for r in rows:
        try:
            start = datetime.datetime.fromisoformat(r["start"].replace("Z", "+00:00"))
        except Exception:
            continue
        if (now - start).total_seconds() > 4 * 3600:
            unique["%s|%s" % (r["event_id"], json.dumps(r["legs"], sort_keys=True))] = (r["sport"], r["event_id"], r["legs"])
    results, refs = {}, list(unique)
    for i in range(0, len(refs), 100):
        chunk = {k: unique[k] for k in refs[i:i + 100]}
        st, h, data = pl.request("/v1/bets/settle", key, method="POST", body=settle_body(chunk))
        d = pl.j(data) if st == 200 else None
        for b in (d or {}).get("bets", []):
            results[b.get("ref")] = b.get("result")
    graded = grade_rows(rows, results)
    os.makedirs(outdir, exist_ok=True)
    with gzip.open(os.path.join(outdir, "sgp-graded.jsonl.gz"), "wt") as f:
        for r in graded:
            f.write(json.dumps(r) + "\n")
    text = "# Same-game parlays at Novig and the books (%s UTC)\n\n%d logged quotes, %d distinct slips, %d settled (won/lost).\n\n%s\n" % (
        now.strftime("%Y-%m-%d %H:%M"), len(rows), len({(r["event_id"], json.dumps(r["legs"])) for r in rows}), sum(1 for r in graded if r.get("result") in ("won", "lost")), summary(graded))
    with open(os.path.join(outdir, "SUMMARY.md"), "w") as f:
        f.write(text)
    print(text)
    return 0


def selftest():
    odds = {"bookmakers": [{"key": "fanduel", "markets": [
        {"key": "totals", "team": None, "outcomes": [{"name": "Over", "point": 215.5, "price": -110}, {"name": "Under", "point": 215.5, "price": -110}]},
        {"key": "totals", "team": "Raptors", "outcomes": [{"name": "Over", "point": 100.5, "price": -370}, {"name": "Under", "point": 100.5, "price": 265}]},
        {"key": "totals", "team": None, "line_type": "alternate", "outcomes": [{"name": "Over", "point": 200.5, "price": -300}, {"name": "Under", "point": 200.5, "price": 240}]},
        {"key": "totals", "team": None, "period": "h1", "outcomes": [{"name": "Over", "point": 108.5, "price": -105}, {"name": "Under", "point": 108.5, "price": -115}]}]}]}
    assert main_total(odds) == 215.5 and main_total({}) is None
    sl = slips("H", "A", 215.5)
    assert len(sl) == 4 and sl[0] == [{"market": "h2h", "name": "H"}, {"market": "totals", "name": "Over", "point": 215.5}]
    ev = {"id": 7, "commence_time": "2026-11-18T00:30:00Z", "home_team": "H", "away_team": "A"}
    rows = quote_rows({"quotes": [{"bookmaker": "novig", "quoted": True, "sgp_price": 325, "independent_price": 341, "correlation_factor": 0.964}, {"bookmaker": "fanduel", "quoted": False, "sgp_price": None}]}, "basketball_nba", ev, sl[0], "t")
    assert len(rows) == 1 and rows[0]["book"] == "novig" and rows[0]["sgp_price"] == 325
    assert abs(dec(325) - 4.25) < 1e-9 and abs(dec(-110) - 1.909090909) < 1e-6
    body = settle_body({"7|x": ("basketball_nba", 7, sl[0])})
    assert body[0]["legs"][0] == {"sport_key": "basketball_nba", "event_id": 7, "market": "h2h", "selection": "H"} and body[0]["stake"] == 1
    assert body[0]["legs"][1] == {"sport_key": "basketball_nba", "event_id": 7, "market": "totals", "selection": "Over", "side": "Over", "point": 215.5}
    g = grade_rows(rows, {"7|" + json.dumps(sl[0], sort_keys=True): "won"})
    assert g[0]["result"] == "won"
    s = summary([dict(r, result="won") for r in g] + [dict(rows[0], result="lost"), dict(rows[0], result="lost"), dict(rows[0], result="push")])
    assert "| novig | 3 |" in s and "33.3%" in s, s       # 1 win in 3 settled; the push is out
    print("propline_sgp selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:] or ["selftest"]
    if a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "log":
        sys.exit(cmd_log(a[1] if len(a) > 1 else "out/sgp", tuple(x for x in a[2].split(",") if x) if len(a) > 2 else SPORTS))
    if a[0] == "grade":
        sys.exit(cmd_grade(a[1:-1], a[-1]))
    print(__doc__)
