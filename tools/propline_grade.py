#!/usr/bin/env python3
"""Every real bet and bid, graded against the close by PropLine's `POST /v1/clv/grade` (PS3, Tj 2026-10-25: "is the engine using the propline grade endpoint to grade my actual bets against the
close? This looks interesting").

  python3 tools/propline_grade.py run --bets <phone/bets dir> --trades trades.json --out <dir>
  python3 tools/propline_grade.py selftest

Inputs: the phone's own record of every bet and bid fill (`phone/bets/bets-<day>.jsonl` on the `phone-data` branch: by hand, the phone's auto-bet, CNO) and the engine's ledger (`trades.json` on
`cloud-feed`: its bets and bid fills). Each is pinned to PropLine's game (`/v1/sports/{sport}/scores?daysFrom=30` and `/events`, free: same teams, start within 3 h) and sent 500 at a time with
`bookmaker=novig`, the market key, selection, side, point and the American price taken. PropLine answers with Novig's closing line, the DE-VIGGED close of the sharpest book quoting that line
(`ev_vs_close_pct`, the honest CLV: `clv_pct` is price against price and flatters a juicy side), whether the bet beat the close, and the grade. Matching is fail-closed (an unmatched bet says why).
Out: `graded.csv.gz` (one row per bet, ours + PropLine's) and `SUMMARY.md` (overall, by who placed it, by kind, by league; unmatched reasons). Never a key in a file.
"""
import csv, gzip, io, json, os, re, sys, time, datetime, urllib.parse, urllib.request, urllib.error

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from propline_clv import STAT_KEYS, norm, same_team, stat_key  # noqa: E402

BASE = "https://api.prop-line.com/v1"
SPORT = {"NBA": "basketball_nba", "NHL": "hockey_nhl", "NFL": "football_nfl", "NCAAF": "football_ncaaf", "MLB": "baseball_mlb", "WNBA": "basketball_wnba", "NCAAB": "basketball_ncaab"}


def american(p):
    """A Novig price (chance 0-1) as American odds."""
    p = min(max(float(p), 0.01), 0.99)
    return round(-100 * p / (1 - p)) if p >= 0.5 else round(100 * (1 - p) / p)


def bets_from_phone(rows, ledger_ids=frozenset()):
    """phone/bets rows -> common bets. A fill the engine's ledger already holds (the phone imports every fill on the account) is left to the ledger, so nothing is graded twice."""
    out = []
    for r in rows:
        if r.get("origin") == "engine" or (r.get("marketId"), r.get("outcomeId")) in ledger_ids:
            continue
        who = ("hand (Novig app) " if r.get("origin") == "imported" else "hand " if r.get("how") == "manual" else "phone auto ") + ("bid" if r.get("made") == "bid" else "bet")
        out.append({"ref": "p:" + str(r.get("id")), "who": who, "league": r.get("league"), "event": r.get("event") or "", "startsMs": r.get("startsMs"), "market": r.get("market") or "",
                    "kind": r.get("kind") or "", "selection": r.get("selection") or "", "american": r.get("odds") if r.get("odds") is not None else (american(r["cost"]) if r.get("cost") else None), "stake": r.get("paid") or r.get("stake") or 0, "result": r.get("result"), "teams": teams(r.get("event") or "")})
    return out


def bets_from_ledger(entries):
    out = []
    for e in entries:
        if e.get("mode") != "LIVE" or e.get("kind") == "live" or not e.get("avgPrice"):
            continue
        who = "engine " + ("bid" if (e.get("note") or "").startswith("bid") else "bet")
        out.append({"ref": "e:" + str(e.get("id")), "who": who, "league": e.get("league"), "event": e.get("event") or "", "startsMs": e.get("startsTs"), "market": e.get("marketLabel") or "",
                    "kind": e.get("kind") or "", "selection": e.get("selection") or "", "american": american(e["avgPrice"]), "stake": e.get("cost") or 0, "result": e.get("status"), "teams": teams(e.get("event") or "")})
    return out


def team_of(name, b):
    """A side as the bet names it ('Sacramento State', or Novig's 'CHA') -> the event's full team name; None when not exactly one team fits."""
    away, home = b.get("teams") or (None, None)
    if not away:
        return name
    # Exact first, then one name's words inside the other's; the last-word rule (same_team) only when that leaves nothing: "Sacramento State" must not match "Kent State"
    hits = [t for t in (away, home) if norm(name) == norm(t)] or [t for t in (away, home) if set(norm(name)) <= set(norm(t)) or set(norm(t)) <= set(norm(name))] \
        or [t for t in (away, home) if same_team(name, t)]
    if not hits and re.fullmatch(r"[A-Z]{2,5}", name):
        ab = name.lower()
        hits = [t for t in (away, home) if "".join(w for w in norm(t) if w != "the").startswith(ab) or "".join(w[0] for w in norm(t)) == ab]
    return hits[0] if len(hits) == 1 else None


def leg(b):
    """A bet -> (market key, selection, side, point) in /clv/grade's words, or (None, why). Full-game lines and the mapped prop stats; periods and team totals are not sent."""
    market, sel = b["market"], b["selection"].strip()
    low = market.lower()
    if re.search(r"\b(1h|2h|1st|2nd|first|half|quarter|q[1-4]|period|inning|innings|f5|set)\b", low):
        # PropLine grades a part of the game too (its `period` codes): a 1st half and baseball's first five innings, the parts Novig lists (PS3, 2026-10-25)
        code = "f5" if re.search(r"\b(f5|first 5|1st 5|five innings)\b", low) else "h1" if re.search(r"\b(1h|1st half|first half)\b", low) else None
        if not code:
            return None, "period market (%s)" % market
        b["period"] = code
        market = re.sub(r"(?i)\b(1h|1st half|first half|f5|first 5 innings|1st 5 innings|first five innings)\b", "", market).strip()
        low = market.lower()
    if "team total" in low or "team_total" in low:
        return None, "team total"
    # Novig's own label of an imported fill: "Joel Embiid 23.5 POINTS" with selection "Under 23.5"
    m = re.match(r"^(.*?)\s+([0-9.]+)\s+([A-Z_]+)$", market)
    s2 = re.match(r"^(Over|Under)\s+([0-9.]+)$", sel)
    if m and s2:
        key = stat_key(m.group(3))
        return ((key, m.group(1).strip(), s2.group(1), float(s2.group(2))), None) if key else (None, "prop stat not mapped (%s)" % m.group(3))
    m = re.match(r"^(.*?)\s+(Over|Under)\s+([0-9.]+)$", sel)
    if m and ("total" not in low or stat_key(market)):
        key = stat_key(market)
        if not key:
            return None, "prop stat not mapped (%s)" % market
        return (key, m.group(1).strip(), m.group(2), float(m.group(3))), None
    if s2 and ("total" in low or re.search(r"\bt\d", low)):
        return ("totals", s2.group(1), s2.group(1), float(s2.group(2))), None
    m = re.match(r"^(.*?)\s*([+-]\d+(?:\.\d+)?)$", sel)
    if m and ("spread" in low or "handicap" in low or "run line" in low or "puck line" in low or b.get("kind") == "Spreads" or re.fullmatch(r"[A-Z]{2,5} [+-][0-9.]+", market)):
        team = team_of(m.group(1).strip(), b)
        return (("spreads", team, None, float(m.group(2))), None) if team else (None, "team not named (%s)" % sel)
    if "moneyline" in low or "winner" in low or low in ("ml", "h2h", "match winner"):
        team = team_of(sel, b)
        return (("h2h", team, None, None), None) if team else (None, "team not named (%s)" % sel)
    return None, "market not mapped (%s)" % market


def teams(event):
    """'Away @ Home' -> (away, home)."""
    if " @ " in event:
        a, h = event.split(" @ ", 1)
        return a.strip(), h.strip()
    return None, None


def pin_event(b, games):
    """The PropLine game of bet [b]: same two teams, start within 3 h, exactly one. -> (id, None) or (None, why)."""
    away, home = teams(b["event"])
    if not away:
        return None, "event name not 'Away @ Home'"
    t = (b.get("startsMs") or 0) / 1000.0
    hits = [g for g in games if same_team(away, g.get("away_team") or "") and same_team(home, g.get("home_team") or "")
            and (not t or abs(g["_t"] - t) <= 3 * 3600)]
    if len(hits) == 1:
        return hits[0]["id"], None
    return None, "game not found at PropLine" if not hits else "two PropLine games match"


def batches(todo, most_bets=100, most_events=5):
    """/clv/grade takes at most 500 bets and 25 distinct events a request (its 422 says so), but a full 25-game batch timed out at PropLine's gateway (504, 2026-10-25): batches of 5 games / 100 bets."""
    by = {}
    for item in todo:
        by.setdefault((item[1]["sport_key"], item[1]["event_id"]), []).append(item)
    out, cur, events = [], [], 0
    for group in by.values():
        for i in range(0, len(group), most_bets):
            part = group[i:i + most_bets]
            if cur and (events + 1 > most_events or len(cur) + len(part) > most_bets):
                out.append(cur)
                cur, events = [], 0
            cur += part
            events += 1
    if cur:
        out.append(cur)
    return out


def body_of(b, event_id, sport, lg):
    key, sel, side, point = lg
    # event_id is an integer in PropLine's schema (a string id was refused with 422 for every batch, 2026-10-25)
    x = {"ref": b["ref"], "sport_key": sport, "event_id": int(event_id), "market": key, "bookmaker": "novig", "selection": sel, "price": int(round(float(b["american"]))), "stake": round(float(b["stake"] or 0), 2)}
    if b.get("period"):
        x["period"] = b["period"]
    if side:
        x["side"] = side
    if point is not None:
        x["point"] = point
    return x


def call(path, key, params=None, body=None, retries=3):
    url = BASE + path + (("?" + urllib.parse.urlencode(params)) if params else "")
    for i in range(retries + 1):
        req = urllib.request.Request(url, method="POST" if body is not None else "GET", data=json.dumps(body).encode() if body is not None else None)
        req.add_header("X-API-Key", key)
        req.add_header("Accept", "application/json")
        if body is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                return r.status, json.loads(r.read() or b"null")
        except urllib.error.HTTPError as e:
            if e.code in (429, 502, 503, 504) and i < retries:
                time.sleep(min(float(e.headers.get("Retry-After") or 2), 30) + 0.5)
                continue
            if e.code == 422 and not getattr(call, "told", False):
                call.told = True
                print("422 from %s: %s" % (path, e.read()[:600].decode("utf-8", "replace")))
            return e.code, None
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            if i < retries:
                time.sleep(2 * (i + 1))
                continue
            return 0, None
    return 0, None


def games_of(sport, key):
    out, seen = [], set()
    for path, params in (("/sports/%s/scores" % sport, {"daysFrom": 30}), ("/sports/%s/events" % sport, None)):
        st, body = call(path, key, params)
        for g in body if isinstance(body, list) else []:
            try:
                g["_t"] = datetime.datetime.fromisoformat(g["commence_time"].replace("Z", "+00:00")).timestamp()
            except (KeyError, ValueError, AttributeError):
                continue
            if g.get("id") in seen:   # /scores and /events both list an upcoming game: once is enough (else "two PropLine games match")
                continue
            seen.add(g.get("id"))
            out.append(g)
    return out


def summarize(rows):
    def block(title, groups):
        lines = ["", "## " + title, "", "| | bets | graded vs close | beat the close | avg EV vs the de-vigged close | avg CLV (price vs price) |", "|:--|--:|--:|--:|--:|--:|"]
        for name, rs in sorted(groups.items(), key=lambda t: -len(t[1])):
            fin = [r for r in rs if r.get("closing_is_final") == "True" and r.get("ev_vs_close_pct") not in ("", None)]
            if not fin:
                lines.append("| %s | %d | 0 | | | |" % (name, len(rs)))
                continue
            ev = sum(float(r["ev_vs_close_pct"]) for r in fin) / len(fin)
            clv = sum(float(r["clv_pct"]) for r in fin if r.get("clv_pct") not in ("", None)) / len(fin)
            beat = sum(r.get("beat_close") == "True" for r in fin) / len(fin) * 100
            lines.append("| %s | %d | %d | %.0f%% | %+.2f%% | %+.2f%% |" % (name, len(rs), len(fin), beat, ev, clv))
        return lines

    def by(f):
        g = {}
        for r in rows:
            g.setdefault(f(r), []).append(r)
        return g

    out = ["# Real bets graded against the close by PropLine /clv/grade (%s)" % time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), "",
           "EV vs the de-vigged close is the honest number (the sharpest book quoting the line at kickoff, Pinnacle first): above 0 = the price beat the fair close.",
           "Only bets on games that have started count in the averages (PropLine: a pre-kickoff close is just the latest price)."]
    out += block("All", {"all": rows})
    out += block("By who placed it", by(lambda r: r["who"]))
    out += block("By kind", by(lambda r: r["kind"] or "?"))
    out += block("By league", by(lambda r: r["league"] or "?"))
    why = by(lambda r: r.get("why") or "")
    why.pop("", None)
    out += ["", "## Not graded (fail-closed), by reason", ""] + ["- %s: %d" % (k, len(v)) for k, v in sorted(why.items(), key=lambda t: -len(t[1]))]
    return "\n".join(out) + "\n"


FIELDS = ["ref", "who", "league", "kind", "event", "market", "selection", "american", "stake", "result", "why", "matched", "closing_price", "closing_point", "closing_is_final", "fair_source",
          "closing_fair_prob", "clv_pct", "ev_vs_close_pct", "beat_close", "resolution"]


def run(bets_dir, trades, outdir):
    keys = [k for k in re.split(r"[\s,;]+", os.environ.get("PROPLINE_API_KEY", "")) if k]
    if not keys:
        print("no PropLine key")
        return 0
    key = keys[0]
    for k in keys:  # the paid key: its own X-Daily-Limit is the biggest
        try:
            req = urllib.request.Request(BASE + "/sports", headers={"X-API-Key": k})
            with urllib.request.urlopen(req, timeout=30) as r:
                if int(r.headers.get("X-Daily-Limit") or 0) > 1000:
                    key = k
                    break
        except Exception:
            continue
    phone = []
    if bets_dir and os.path.isdir(bets_dir):
        for f in sorted(os.listdir(bets_dir)):
            if f.startswith("bets-") and f.endswith(".jsonl"):
                for line in open(os.path.join(bets_dir, f)):
                    try:
                        phone.append(json.loads(line))
                    except ValueError:
                        pass
    ledger = []
    if trades and os.path.isfile(trades):
        d = json.load(open(trades))
        ledger = d.get("entries", []) if isinstance(d, dict) else d
    ids = frozenset((e.get("marketId"), e.get("outcomeId")) for e in ledger)
    bets = bets_from_phone(phone, ids) + bets_from_ledger(ledger)
    print("bets: %d from the phone, %d from the engine's ledger" % (len(bets) - len(bets_from_ledger(ledger)), len(bets_from_ledger(ledger))))
    games = {}
    rows, todo = [], []
    for b in bets:
        r = dict(b)
        sport = SPORT.get(b.get("league") or "")
        lg, why = leg(b) if sport else (None, "league not graded by PropLine (%s)" % b.get("league"))
        if lg and b.get("american") is None:
            lg, why = None, "no price recorded"
        if lg:
            if sport not in games:
                games[sport] = games_of(sport, key)
            eid, why = pin_event(b, games[sport])
            if eid:
                todo.append((r, body_of(b, eid, sport, lg)))
        r["why"] = why or ""
        rows.append(r)
    # One bet first, timed: how long PropLine takes to grade decides the batch size (2026-10-25: 25-game batches 504'd, 5-game ones ran 35 min with no answer)
    if todo:
        t0 = time.time()
        st, res = call("/clv/grade", key, body=[todo[0][1]], retries=0)
        print("probe: one bet graded in %.1f s, HTTP %s: %s" % (time.time() - t0, st, json.dumps(res)[:400] if res else ""), flush=True)
    failed_in_row, t_all = 0, time.time()
    chunks = batches(todo)
    for n, chunk in enumerate(chunks):
        if failed_in_row >= 5:
            for r, _ in chunk:
                r["why"] = "not asked (PropLine failed 5 batches in a row)"
            continue
        t0 = time.time()
        st, res = call("/clv/grade", key, body=[x for _, x in chunk], retries=1)
        failed_in_row = 0 if st == 200 else failed_in_row + 1
        print("batch %d/%d: %d bets, HTTP %s, %.1f s (%.0f s in all)" % (n + 1, len(chunks), len(chunk), st, time.time() - t0, time.time() - t_all), flush=True)
        got = {x.get("ref"): x for x in (res or {}).get("bets", [])}
        for r, x in chunk:
            g = got.get(x["ref"])
            if not g:
                r["why"] = "no answer (HTTP %s)" % st
                continue
            for f in ("matched", "closing_price", "closing_point", "closing_is_final", "fair_source", "closing_fair_prob", "clv_pct", "ev_vs_close_pct", "beat_close", "resolution"):
                r[f] = "" if g.get(f) is None else str(g.get(f))
            if not g.get("matched"):
                r["why"] = "PropLine: %s" % g.get("unmatched_reason")
    os.makedirs(outdir, exist_ok=True)
    with gzip.open(os.path.join(outdir, "graded.csv.gz"), "wt", newline="") as f:
        w = csv.DictWriter(f, FIELDS, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in FIELDS})
    s = summarize([{k: str(r.get(k, "")) if r.get(k) is not None else "" for k in FIELDS} for r in rows])
    open(os.path.join(outdir, "SUMMARY.md"), "w").write(s)
    print(s)
    return 0


def selftest():
    assert american(0.5) == -100 and american(0.4) == 150 and american(0.6) == -150
    assert leg({"market": "Spread", "selection": "Sacramento State +9.5"})[0] == ("spreads", "Sacramento State", None, 9.5)
    assert leg({"market": "Joel Embiid 23.5 POINTS", "selection": "Under 23.5"})[0] == ("player_points", "Joel Embiid", "Under", 23.5)
    assert leg({"market": "MIA @ CHA t229.5", "selection": "Under 229.5"})[0] == ("totals", "Under", "Under", 229.5)
    assert leg({"market": "CHA +1.5", "selection": "CHA +1.5", "teams": ("Miami Heat", "Charlotte Hornets")})[0] == ("spreads", "Charlotte Hornets", None, 1.5)
    assert leg({"market": "CIT +17.5", "selection": "CIT +17.5", "teams": ("The Citadel", "Clemson")})[0] == ("spreads", "The Citadel", None, 17.5)
    assert leg({"market": "Team Total", "selection": "New Jersey Devils Over 3.5"})[0] is None
    assert leg({"market": "Total", "selection": "Over 44.5"})[0] == ("totals", "Over", "Over", 44.5)
    b1 = {"market": "1H Total", "selection": "Over 22.5"}
    assert leg(b1)[0] == ("totals", "Over", "Over", 22.5) and b1["period"] == "h1"
    assert leg({"market": "2nd Half Total", "selection": "Over 22.5"})[0] is None
    assert leg({"market": "Player Receiving Yards", "selection": "Puka Nacua Over 80.5"})[0] == ("player_reception_yds", "Puka Nacua", "Over", 80.5)
    assert leg({"market": "Tackles + Assists", "selection": "Fred Warner Over 7.5"})[0] == ("player_tackles_assists", "Fred Warner", "Over", 7.5)
    assert leg({"market": "Points Rebounds Assists", "selection": "Kayla Thornton Over 13.5"})[0] == ("player_points_rebounds_assists", "Kayla Thornton", "Over", 13.5)
    assert leg({"market": "Moneyline", "selection": "Boston Celtics"})[0] == ("h2h", "Boston Celtics", None, None)
    g = [{"id": "7", "away_team": "Kent State Golden Flashes", "home_team": "Sacramento State Hornets", "_t": 1793413800}]
    assert len(batches([(None, {"sport_key": "s", "event_id": i % 30}) for i in range(60)], 500, 25)) == 2
    assert all(len({x["event_id"] for _, x in c}) <= 5 for c in batches([(None, {"sport_key": "s", "event_id": i}) for i in range(80)]))
    assert team_of("Sacramento State", {"teams": ("Kent State", "Sacramento State")}) == "Sacramento State"
    assert leg({"market": "Total Bases", "selection": "Aaron Judge Over 1.5"})[0] == ("batter_total_bases", "Aaron Judge", "Over", 1.5)
    assert pin_event({"event": "Kent State @ Sacramento State", "startsMs": 1793413800000}, g) == ("7", None)
    assert pin_event({"event": "Kent State @ Sacramento State", "startsMs": 1793413800000 + 86400000}, g)[0] is None
    assert bets_from_phone([{"id": "a", "origin": "engine"}, {"id": "b", "how": "manual", "made": "bet", "odds": 108, "market": "Spread", "selection": "X +1.5"}])[0]["who"] == "hand bet"
    assert bets_from_phone([{"id": "c", "origin": "imported", "marketId": "m", "outcomeId": "o"}], frozenset({("m", "o")})) == []
    assert bets_from_phone([{"id": "d", "how": "manual", "cost": 0.4, "market": "Total", "selection": "Over 44.5"}])[0]["american"] == 150
    assert bets_from_phone([{"id": "e", "how": "manual", "market": "Total", "selection": "Over 44.5"}])[0]["american"] is None
    assert bets_from_ledger([{"id": "x", "mode": "LIVE", "avgPrice": 0.485, "note": "bid filled"}])[0]["who"] == "engine bid"
    print("propline_grade selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "run":
        def opt(n, d=None):
            return a[a.index(n) + 1] if n in a else d
        sys.exit(run(opt("--bets"), opt("--trades"), opt("--out", "out/propline-grade")))
    print(__doc__)
