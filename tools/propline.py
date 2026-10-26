#!/usr/bin/env python3
"""PropLine Pro tooling for GitHub Actions (TCL56; Tj 2026-10-16). Standard library only; no Claude usage.

  python3 tools/propline.py plans                      which key is the paid one (X-Daily-Limit of each) and what it unlocks
  python3 tools/propline.py probe OUTDIR               one call to every Pro endpoint on a real recent event; saves the answers (keyless) so the app's parsers can be checked against real shapes
  python3 tools/propline.py steam OUTDIR               line movement + steam signals for every game in the next 24 h (JSON lines)
  python3 tools/propline.py archive OUTDIR [--days N] [--books a,b] [--sports x,y]  the resolved-prop archive: GET /v1/exports/resolved-props per sport (gzip CSV), the free accuracy/coverage reports; files for a Release
  python3 tools/propline.py selftest

Keys come from the environment variable PROPLINE_API_KEY (several keys: comma, space or newline separated; the phone's "send keys to GitHub" writes this secret). The key used is the one whose own
plan is largest (its `X-Daily-Limit`), so a Pro key is found among free ones. A key is never printed. See research/PROPLINE_PRO_2026-10-16.md for the endpoints.
"""
import csv, datetime, gzip, io, json, os, re, shutil, sys, time, urllib.error, urllib.parse, urllib.request

BASE = "https://api.prop-line.com"
# The exports are huge (8 million graded outcomes in 30 days across all books), so the archive asks for the books that matter to Vigilant: Novig (where it bets) and Pinnacle (the sharp close).
BOOKS_ARCHIVE = ["novig", "pinnacle"]
SPORTS_ARCHIVE = ["baseball_mlb", "basketball_nba", "basketball_wnba", "hockey_nhl", "football_nfl", "football_ncaaf", "basketball_ncaab", "tennis", "mma_ufc"]
TIERS = [(1_000, "free"), (5_000, "hobby"), (25_000, "pro"), (250_000, "streaming-lite"), (1_000_000, "streaming")]


def tier_of(limit):
    if limit is None:
        return "?"
    for top, name in TIERS:
        if limit <= top:
            return name
    return "enterprise"


def keys_from_env(env=None):
    raw = (os.environ if env is None else env).get("PROPLINE_API_KEY", "")
    return [k for k in re.split(r"[\s,;]+", raw) if k]


def request(path, key, params=None, method="GET", body=None, accept_gzip=False, timeout=90, retries=3):
    """-> (status, headers dict (lower-case), bytes). Honours Retry-After on 429/503; never raises on HTTP errors."""
    url = BASE + path + (("?" + urllib.parse.urlencode(params)) if params else "")
    for attempt in range(retries + 1):
        req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None)
        req.add_header("X-API-Key", key)
        req.add_header("Accept", "application/json")
        if body is not None:
            req.add_header("Content-Type", "application/json")
        if accept_gzip:
            req.add_header("Accept-Encoding", "gzip")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.status, {k.lower(): v for k, v in r.headers.items()}, r.read()
        except urllib.error.HTTPError as e:
            data = e.read()
            hdr = {k.lower(): v for k, v in e.headers.items()}
            if e.code in (429, 503) and attempt < retries:
                wait = min(float(hdr.get("retry-after", "1") or 1), 30)
                if e.code == 429 and b"daily_limit_exceeded" in data:
                    return e.code, hdr, data
                time.sleep(wait + 0.5)
                continue
            return e.code, hdr, data
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            if attempt < retries:
                time.sleep(2 * (attempt + 1))
                continue
            raise
    return 0, {}, b""


def download(path, key, params, dest, timeout=1800):
    """Stream a (large) CSV export to the gzip file `dest` without holding it in memory. -> (status, headers, error text)."""
    url = BASE + path + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url)
    req.add_header("X-API-Key", key)
    req.add_header("Accept-Encoding", "gzip")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            hdr = {k.lower(): v for k, v in r.headers.items()}
            if hdr.get("content-encoding") == "gzip":
                with open(dest, "wb") as f:
                    shutil.copyfileobj(r, f, 1 << 20)
            else:
                with gzip.open(dest, "wb") as f:
                    shutil.copyfileobj(r, f, 1 << 20)
            return r.status, hdr, ""
    except urllib.error.HTTPError as e:
        return e.code, {k.lower(): v for k, v in e.headers.items()}, e.read()[:300].decode("utf8", "replace")


def count_rows(path):
    n = 0
    try:
        with gzip.open(path, "rb") as f:
            for _ in f:
                n += 1
    except (EOFError, OSError):
        return -1      # a truncated stream: the export was cut off
    return max(n - 1, 0)


def plan_of(key):
    st, h, _ = request("/v1/sports", key)
    lim = h.get("x-daily-limit")
    return {"status": st, "limit": int(lim) if lim and lim.isdigit() else None, "used": int(h["x-daily-used"]) if h.get("x-daily-used", "").isdigit() else None,
            "remaining": int(h["x-daily-remaining"]) if h.get("x-daily-remaining", "").isdigit() else None}


def best_key(keys, plans=None):
    """The key with the largest daily limit (a paid key among free ones); (index, plan) or (None, None)."""
    plans = plans or [plan_of(k) for k in keys]
    ranked = sorted(((p["limit"] or 0, i) for i, p in enumerate(plans) if p["status"] == 200), reverse=True)
    return (ranked[0][1], plans[ranked[0][1]]) if ranked else (None, None)


def need_paid(keys):
    plans = [plan_of(k) for k in keys]
    for i, p in enumerate(plans):
        print("PropLine key #%d: %s (limit %s, used %s, remaining %s, HTTP %s)" % (i + 1, tier_of(p["limit"]), p["limit"], p["used"], p["remaining"], p["status"]))
    i, p = best_key(keys, plans)
    return (keys[i], p) if i is not None else (None, None)


def cmd_plans():
    keys = keys_from_env()
    if not keys:
        print("no PROPLINE_API_KEY secret")
        return 0
    key, p = need_paid(keys)
    if key is None:
        print("no key answered")
    elif (p["limit"] or 0) <= 1_000:
        print("only free keys: history, closing lines, +EV, best line, results, CLV and exports come back redacted")
    else:
        print("paid key found: %s plan, %s requests a day" % (tier_of(p["limit"]), p["limit"]))
    return 0


def j(data):
    try:
        return json.loads(data)
    except ValueError:
        return None


def trim(x, n=3):
    """A sample of a big answer: lists cut to n items, deeply."""
    if isinstance(x, list):
        return [trim(i, n) for i in x[:n]]
    if isinstance(x, dict):
        return {k: trim(v, n) for k, v in x.items()}
    return x


def cmd_probe(outdir):
    keys = keys_from_env()
    key, p = need_paid(keys) if keys else (None, None)
    if key is None:
        print("no usable key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    report = []

    def save(name, st, h, data, note=""):
        body = j(data)
        red = isinstance(body, dict) and body.get("redacted")
        report.append("%-34s HTTP %s%s %s" % (name, st, " REDACTED (free tier)" if red else "", note))
        with open(os.path.join(outdir, name + ".json"), "w") as f:
            json.dump({"status": st, "quota": {k: h.get(k) for k in h if k.startswith("x-daily") or k.startswith("ratelimit")}, "body": trim(body) if body is not None else data[:2000].decode("utf8", "replace")}, f, indent=1)

    sport = "baseball_mlb"
    st, h, data = request("/v1/sports", key); save("sports", st, h, data)
    # an event: today's upcoming first, else any
    event = None
    for sp in ("basketball_nba", "hockey_nhl", "baseball_mlb", "football_nfl", "football_ncaaf", "basketball_wnba", "tennis"):
        st, h, data = request("/v1/sports/%s/events" % sp, key)
        ev = j(data)
        if st == 200 and isinstance(ev, list) and ev:
            sport, event = sp, ev[0]
            save("events", st, h, data, "(%s, %d events)" % (sp, len(ev)))
            break
    if event is None:
        print("no events listed")
        return 0
    eid = event["id"]
    st, h, data = request("/v1/sports/%s/events/%s/markets" % (sport, eid), key); save("markets", st, h, data)
    mk = [m["key"] for m in (j(data) or [])[:3]] if st == 200 else []
    mkq = ",".join(mk) or "h2h"
    st, h, data = request("/v1/sports/%s/events/%s/odds" % (sport, eid), key, {"markets": mkq, "bookmakers": "novig,pinnacle,draftkings", "includeBookIds": "true", "includeDepth": "true"}); save("event_odds", st, h, data)
    st, h, data = request("/v1/sports/%s/events/%s/ev" % (sport, eid), key, {"markets": mkq, "bookmakers": "novig", "max_age": "600"}); save("ev_novig", st, h, data)
    st, h, data = request("/v1/sports/%s/events/%s/ev" % (sport, eid), key, {"markets": mkq, "fair_source": "consensus"}); save("ev_consensus", st, h, data)
    st, h, data = request("/v1/sports/%s/events/%s/best-line" % (sport, eid), key, {"markets": mkq}); save("best_line", st, h, data)
    st, h, data = request("/v1/sports/%s/events/%s/movement" % (sport, eid), key, {"markets": "h2h,spreads,totals"}); save("movement", st, h, data)
    st, h, data = request("/v1/sports/%s/events/%s/projections" % (sport, eid), key, {"markets": mkq}); save("projections", st, h, data)
    # a finished event (the newest in a small export), for closing lines, results, history, box score, context and CLV grading
    done = None
    for sp in ("basketball_nba", "hockey_nhl", "football_ncaaf", "football_nfl", "baseball_mlb"):
        st, h, data = request("/v1/exports/resolved-props", key, {"sport": sp, "bookmaker": "pinnacle", "since": (datetime.datetime.utcnow() - datetime.timedelta(days=2)).strftime("%Y-%m-%dT00:00:00Z")}, accept_gzip=True)
        raw = (gzip.decompress(data) if h.get("content-encoding") == "gzip" else data).decode("utf8", "replace")
        rows = list(csv.DictReader(io.StringIO(raw))) if st == 200 else []
        rows = [r for r in rows if r.get("event_id") and not (r.get("home_team") or "").startswith("(Watermark)")]
        if rows:
            r0 = rows[0]
            done = (sp, {"id": r0["event_id"], "home_team": r0["home_team"], "away_team": r0["away_team"]})
            report.append("%-34s found a finished %s event %s (%s @ %s) in %d rows" % ("finished event", sp, r0["event_id"], r0["away_team"], r0["home_team"], len(rows)))
            break
    if not done:
        # The export is capped at 50 calls a day and the archive may have used them: fall back to a finished game from the 2026-10-16 archive (LAD v MIL, event 351344) so the results/trends/closing/CLV shapes are still captured.
        done = ("baseball_mlb", {"id": "351344", "home_team": "Los Angeles Dodgers", "away_team": "Milwaukee Brewers"})
        report.append("%-34s export gave none (daily cap?): using the archive's finished game 351344" % "finished event")
    if done:
        dsp, g = done
        did = g["id"]
        st, h, data = request("/v1/sports/%s/players" % dsp, key, {"search": "ohtani" if dsp == "baseball_mlb" else "a", "limit": 3}); save("players_search", st, h, data)
        pl = (j(data) or {}).get("players", []) if st == 200 else []
        if not pl:
            # the search answered 422 on the first run: the trends endpoint also takes a plain name, so capture that shape anyway (market narrows it to one stat)
            nm = "Shohei Ohtani" if dsp == "baseball_mlb" else "LeBron James"
            st, h, data = request("/v1/sports/%s/players/%s/trends" % (dsp, urllib.parse.quote(nm, safe="")), key, {"market": "batter_hits" if dsp == "baseball_mlb" else "player_points"}); save("player_trends", st, h, data, "(by name %s)" % nm)
        if pl:
            pid = pl[0]["player_id"]
            st, h, data = request("/v1/sports/%s/players/%s/trends" % (dsp, urllib.parse.quote(pid, safe="")), key); save("player_trends", st, h, data, "(%s)" % pid)
            st, h, data = request("/v1/sports/%s/players/%s/games" % (dsp, urllib.parse.quote(pid, safe="")), key, {"limit": 5}); save("player_games", st, h, data)
            mk0 = next(iter((j(data) or {}).get("games", [{}])[0].get("stats", {}) or {}), None) if st == 200 else None
            st, h, data = request("/v1/sports/%s/players/%s/history" % (dsp, urllib.parse.quote(pid, safe="")), key, {"market": "player_points", "limit": 5}); save("player_history", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/odds/closing" % (dsp, did), key, {"markets": "h2h,spreads,totals", "bookmakers": "novig,pinnacle"}); save("closing", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/results" % (dsp, did), key, {"bookmakers": "novig,pinnacle"}); save("results", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/odds/history" % (dsp, did), key, {"markets": "h2h", "bookmakers": "pinnacle", "changes_only": "true"}); save("history", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/stats" % (dsp, did), key); save("stats", st, h, data)
        st, h, data = request("/v1/sports/%s/scores" % dsp, key, {"daysFrom": "3"}); save("scores", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/context" % (dsp, did), key); save("context", st, h, data)
        body = [{"ref": "probe1", "sport_key": dsp, "event_id": int(did) if str(did).isdigit() else did, "market": "h2h", "bookmaker": "pinnacle", "selection": g.get("home_team"), "side": g.get("home_team"), "price": -110, "stake": 1}]
        st, h, data = request("/v1/clv/grade", key, method="POST", body=body); save("clv_grade", st, h, data)
    st, h, data = request("/v1/books/accuracy", key, {"days": 30}); save("books_accuracy", st, h, data)
    st, h, data = request("/v1/markets/resolution-summary", key, {"days": 30}); save("resolution_summary", st, h, data)
    st, h, data = request("/v1/coverage/history", key, {"sport": "basketball_nba"}); save("coverage_history", st, h, data)
    st, h, data = request("/v1/exports/resolved-props", key, {"sport": "baseball_mlb", "since": (datetime.datetime.utcnow() - datetime.timedelta(days=2)).strftime("%Y-%m-%dT00:00:00Z")}, accept_gzip=True)
    head = (gzip.decompress(data) if h.get("content-encoding") == "gzip" else data)[:6000].decode("utf8", "replace")
    report.append("%-34s HTTP %s  header: %s" % ("export_resolved_props", st, head.splitlines()[0][:400] if head else ""))
    with open(os.path.join(outdir, "export_head.csv"), "w") as f:
        f.write("\n".join(head.splitlines()[:6]))
    with open(os.path.join(outdir, "REPORT.txt"), "w") as f:
        f.write("PropLine Pro probe, %s UTC, plan %s (%s a day)\n" % (datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M"), tier_of(p["limit"]), p["limit"]) + "\n".join(report) + "\n")
    print("\n".join(report))
    return 0


def cmd_steam(outdir):
    """Line movement and steam for every game starting in the next 24 hours (`/movement`, Hobby+): the cross-book sharp-money signal, saved as JSON lines for later study against the close (TCL56d).
    One request per game; the file is `steam-YYYY-MM-DDTHHMM.jsonl.gz` (one line per game: the event, when we read it, the whole `steam` array and the count of moving lines)."""
    keys = keys_from_env()
    key, p = need_paid(keys) if keys else (None, None)
    if key is None:
        print("no paid key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    now = time.time()
    stamp = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H%M")
    path = os.path.join(outdir, "steam-%s.jsonl.gz" % stamp)
    games = steamy = 0
    with gzip.open(path, "wt") as out:
        for sp in ("baseball_mlb", "basketball_nba", "basketball_wnba", "hockey_nhl", "football_nfl", "football_ncaaf"):
            st, h, data = request("/v1/sports/%s/events" % sp, key)
            evs = j(data) if st == 200 else None
            for ev in evs or []:
                t = parse_ts(ev.get("commence_time") or "")
                if not t or not (now < t < now + 24 * 3600):
                    continue
                st, h, data = request("/v1/sports/%s/events/%s/movement" % (sp, ev["id"]), key, {"markets": "h2h,spreads,totals"})
                body = j(data) if st == 200 else None
                if not isinstance(body, dict):
                    continue
                st2 = body.get("steam") or []
                games += 1
                steamy += 1 if st2 else 0
                out.write(json.dumps({"sport": sp, "event_id": ev["id"], "home": ev.get("home_team"), "away": ev.get("away_team"), "commence": ev.get("commence_time"), "read_at": int(now), "steam": st2, "keys": sorted(body.keys())}) + "\n")
    print("steam: %d games read, %d with a steam signal -> %s" % (games, steamy, path))
    return 0


def pct(v, p):
    v = sorted(v)
    return v[min(len(v) - 1, int(len(v) * p))] if v else None


def parse_ts(t):
    try:
        return datetime.datetime.fromisoformat(t.replace("Z", "+00:00")).timestamp()
    except Exception:
        return None


def cmd_speed(outdir):
    """Speed (response times) and freshness (age of each book's quote at the moment it is read) of the paid key, measured on real calls."""
    keys = keys_from_env()
    key, p = need_paid(keys) if keys else (None, None)
    if key is None:
        print("no usable key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    lat = {}

    def timed(name, path, params=None, **kw):
        t0 = time.time()
        st, h, data = request(path, key, params, **kw)
        ms = (time.time() - t0) * 1000
        lat.setdefault(name, []).append(ms)
        return st, h, data, ms

    for _ in range(6):
        timed("sports (tiny)", "/v1/sports")
    rows = []
    ages_lines = {}
    ages_props = {}
    seen_lines = {}
    seen_props = {}
    best_sport = None
    best_events = []
    for sp in ("basketball_nba", "hockey_nhl", "baseball_mlb", "football_nfl", "football_ncaaf", "basketball_wnba"):
        st, h, data, ms = timed("events list", "/v1/sports/%s/events" % sp)
        ev = j(data)
        if st == 200 and isinstance(ev, list) and len(ev) > len(best_events):
            best_sport, best_events = sp, ev
    rows.append("sport used: %s (%d events)" % (best_sport, len(best_events)))
    books = "pinnacle,draftkings,fanduel,betmgm,novig,bovada,kalshi,polymarket,caesars,hardrock"
    now_ts = time.time()
    for _ in range(3):
        st, h, data, ms = timed("bulk game lines, 10 books", "/v1/sports/%s/odds" % best_sport, {"markets": "h2h,spreads,totals", "bookmakers": books})
        body = j(data)
        now_ts = time.time()
        for ev in body or []:
            for bk in ev.get("bookmakers", []):
                for m in bk.get("markets", []):
                    for o in m.get("outcomes", []):
                        t = parse_ts(o.get("last_change_at") or "")
                        ls = parse_ts(o.get("last_seen_at") or "")
                        if t:
                            ages_lines.setdefault(bk["key"], []).append(now_ts - t)
                        if ls:
                            seen_lines.setdefault(bk["key"], []).append(now_ts - ls)
    # props of the three soonest events
    from_ev = sorted(best_events, key=lambda e: e.get("commence_time", ""))[:3]
    propkeys = {"basketball_nba": "player_points,player_rebounds,player_assists,player_threes,player_points_rebounds_assists,player_steals", "hockey_nhl": "player_goals,player_shots_on_goal,player_points,player_assists,goalie_saves,player_power_play_points",
                "baseball_mlb": "batter_hits,batter_total_bases,pitcher_strikeouts,batter_home_runs,batter_rbis,pitcher_outs", "football_nfl": "player_pass_yds,player_rush_yds,player_reception_yds,player_receptions,player_pass_tds,player_anytime_td",
                "football_ncaaf": "player_pass_yds,player_rush_yds,player_reception_yds,player_receptions,player_pass_tds,player_anytime_td", "basketball_wnba": "player_points,player_rebounds,player_assists,player_threes,player_points_rebounds_assists,player_double_double"}[best_sport]
    for e in from_ev:
        for _ in range(2):
            st, h, data, ms = timed("props of one game, 6 markets, 6 books", "/v1/sports/%s/events/%s/odds" % (best_sport, e["id"]), {"markets": propkeys, "bookmakers": "pinnacle,draftkings,fanduel,betmgm,novig,bovada"})
        body = j(data)
        now_ts = time.time()
        for bk in (body or {}).get("bookmakers", []):
            for m in bk.get("markets", []):
                for o in m.get("outcomes", []):
                    t = parse_ts(o.get("last_change_at") or "")
                    if t:
                        ages_props.setdefault(bk["key"], []).append(now_ts - t)
                    ls = parse_ts(o.get("last_seen_at") or "")
                    if ls:
                        seen_props.setdefault(bk["key"], []).append(now_ts - ls)
        timed("+EV of one game (all markets, novig)", "/v1/sports/%s/events/%s/ev" % (best_sport, e["id"]), {"bookmakers": "novig"})
        timed("best-line of one game (all markets)", "/v1/sports/%s/events/%s/best-line" % (best_sport, e["id"]))
    out = ["PropLine Pro speed and freshness, measured %s UTC, plan %s" % (datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M"), tier_of(p["limit"])), ""] + rows + ["", "RESPONSE TIME (ms): n, p50, p90, max"]
    for name, v in lat.items():
        out.append("%-44s n=%-3d p50=%-6d p90=%-6d max=%d" % (name, len(v), pct(v, .5), pct(v, .9), max(v)))
    for title, ages, seen in (("GAME LINES", ages_lines, seen_lines), ("PLAYER PROPS", ages_props, seen_props)):
        out += ["", "%s: seconds since the price last CHANGED (a quiet line is old by nature) | seconds since the book last DELIVERED the outcome (the real freshness: how recently PropLine confirmed it is still on the board)" % title]
        for bk, v in sorted(ages.items(), key=lambda kv: pct(seen.get(kv[0], kv[1]), .5) or 0):
            sv = seen.get(bk, [])
            out.append("%-14s n=%-5d changed p50=%-6d p90=%-7d | delivered p50=%-5s p90=%-6s" % (bk, len(v), pct(v, .5), pct(v, .9), pct(sv, .5) and int(pct(sv, .5)), pct(sv, .9) and int(pct(sv, .9))))
    text = "\n".join(out) + "\n"
    open(os.path.join(outdir, "speed.txt"), "w").write(text)
    print(text)
    return 0


def cmd_archive(outdir, days, books=None, sports=None):
    keys = keys_from_env()
    key, p = need_paid(keys) if keys else (None, None)
    if key is None or (p["limit"] or 0) < 25_000:
        print("no Pro key (25,000 a day) among the secrets: the export needs Pro. Nothing archived.")
        return 0
    os.makedirs(outdir, exist_ok=True)
    now = datetime.datetime.utcnow()
    since = (now - datetime.timedelta(days=days)).strftime("%Y-%m-%dT00:00:00Z")
    until = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    made = []
    stop = False
    for sp in (sports or SPORTS_ARCHIVE):
        for book in (books or BOOKS_ARCHIVE):
            name = "resolved-%s-%s-%s_%s.csv.gz" % (sp, book, since[:10], until[:10])
            dest = os.path.join(outdir, name)
            st, h, err = download("/v1/exports/resolved-props", key, {"sport": sp, "bookmaker": book, "since": since, "until": until}, dest)
            if st != 200:
                print("%s %s: HTTP %s %s" % (sp, book, st, err))
                if os.path.exists(dest):
                    os.remove(dest)
                if st == 429:
                    stop = True
                    break
                continue
            rows = count_rows(dest)
            if rows < 0:
                print("%s %s: the export was cut off (truncated gzip); dropped" % (sp, book))
                os.remove(dest)
                continue
            if rows <= 2:      # only the two watermark canary rows: nothing resolved in the window
                os.remove(dest)
                print("%s %s: nothing resolved in the window (archive starts %s)" % (sp, book, h.get("x-propline-archive-starts")))
                continue
            made.append(name)
            print("%s %s: %d rows, archive starts %s, export calls left today %s" % (sp, book, rows, h.get("x-propline-archive-starts"), h.get("x-propline-export-daily-remaining")))
            time.sleep(1)
        if stop:
            break
    # the free reports (daily record for Claude)
    stamp = now.strftime("%Y-%m-%d")
    for name, path, params in [("books-accuracy", "/v1/books/accuracy", {"days": 30}), ("resolution-summary", "/v1/markets/resolution-summary", {"days": 30})]:
        st, h, data = request(path, key, params)
        if st == 200:
            with open(os.path.join(outdir, "%s-%s.json" % (name, stamp)), "wb") as f:
                f.write(data)
            made.append("%s-%s.json" % (name, stamp))
    with open(os.path.join(outdir, "MANIFEST.txt"), "w") as f:
        f.write("PropLine resolved-prop archive %s, window %s .. %s\n" % (stamp, since, until) + "\n".join(made) + "\nEvery export carries two '(Watermark)' canary rows: filter player_name NOT LIKE '(Watermark)%'.\n")
    print("files: %d" % len(made))
    return 0


def coverage_stats(boards):
    """[(sport, events)] -> what PS2 asks (Tj 2026-10-25: "even though they offer dozens of sports books odds, most of my scanned results only show odds for a few sports books. Is this a matching issue?"):
    per book the games it prices, per Novig line how many books post that SAME number (the only quotes a fair line can use) against how many price the market at all, and every pair of books whose
    prices are identical on 80%+ of 100+ shared quotes (the one-company rule's measured test: books on one odds desk count once)."""
    games, quotes, novig_lines = {}, {}, []
    for sport, events in boards:
        for e in events:
            eid = "%s:%s" % (sport, e.get("id"))
            per_mkt = {}
            for b in e.get("bookmakers") or []:
                bk = b.get("key")
                if not bk:
                    continue
                games.setdefault(bk, set()).add(eid)
                for m in b.get("markets") or []:
                    mk = m.get("key")
                    if mk not in ("h2h", "spreads", "totals") or m.get("period") not in (None, "", "full", "FULL") or m.get("team"):
                        continue
                    for o in m.get("outcomes") or []:
                        pr = o.get("price")
                        if pr is None:
                            continue
                        q = (eid, mk, (o.get("name") or "").lower(), o.get("point"))
                        quotes.setdefault(q, {})[bk] = pr
                        per_mkt.setdefault((mk, o.get("point") if mk == "totals" else abs(o.get("point") or 0)), set()).add(bk)
                        per_mkt.setdefault((mk, "any"), set()).add(bk)
            for (mk, pt), books in per_mkt.items():
                if pt != "any" and "novig" in books:
                    novig_lines.append((sport, mk, len(books - {"novig"}), len(per_mkt.get((mk, "any"), set()) - {"novig"})))
    pairs = {}
    for q, by in quotes.items():
        bks = sorted(by)
        for i in range(len(bks)):
            for k in range(i + 1, len(bks)):
                c = pairs.setdefault((bks[i], bks[k]), [0, 0])
                c[0] += 1
                c[1] += by[bks[i]] == by[bks[k]]
    same_desk = sorted(((a, b, n, same / n) for (a, b), (n, same) in pairs.items() if n >= 100 and same / n >= 0.8), key=lambda t: -t[3])
    return {"games": {b: len(v) for b, v in games.items()}, "novig_lines": novig_lines, "same_desk": same_desk}


def cmd_coverage(outdir, sports=("football_nfl", "football_ncaaf", "basketball_nba", "hockey_nhl")):
    """PS2: every book's game lines for the scanned leagues (no `bookmakers=`: all of them), measured by [coverage_stats]; a handful of requests of the paid key's 250,000 a day."""
    keys = keys_from_env()
    i, _ = best_key(keys, [plan_of(k) for k in keys])
    if i is None:
        print("no usable key")
        return 0
    key = keys[i]
    os.makedirs(outdir, exist_ok=True)
    boards = []
    for sp in sports:
        st, _, data = request("/v1/sports/%s/odds" % sp, key, {"markets": "h2h,spreads,totals"})
        body = j(data)
        events = body if isinstance(body, list) else (body or {}).get("data") or []
        print("%s: HTTP %s, %d games" % (sp, st, len(events)))
        boards.append((sp, events))
    r = coverage_stats(boards)
    lines = ["# PropLine book coverage (PS2, %s)" % time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), "", "## Games priced by each book (game lines, %s)" % ", ".join(sports), ""]
    for b, n in sorted(r["games"].items(), key=lambda t: -t[1]):
        lines.append("- %s: %d" % (b, n))
    lines += ["", "## Novig's lines: books posting the SAME number vs books pricing that market at all", ""]
    by = {}
    for sp, mk, same, any_ in r["novig_lines"]:
        by.setdefault((sp, mk), []).append((same, any_))
    for (sp, mk), v in sorted(by.items()):
        v.sort()
        lines.append("- %s %s: %d Novig lines, books at the same number median %d (any number: median %d)" % (sp, mk, len(v), v[len(v) // 2][0], sorted(x[1] for x in v)[len(v) // 2]))
    lines += ["", "## Pairs of books with identical prices on 80%+ of 100+ shared quotes (one odds desk)", ""]
    for a, b, n, rate in r["same_desk"]:
        lines.append("- %s = %s: %.0f%% of %d" % (a, b, rate * 100, n))
    with open(os.path.join(outdir, "coverage.md"), "w") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.join(outdir, "coverage.json"), "w") as f:
        json.dump({"games": r["games"], "same_desk": r["same_desk"], "novig_lines": r["novig_lines"][:5000]}, f)
    print("\n".join(lines[:80]))
    return 0


OURS = ["pinnacle", "draftkings", "fanduel", "betmgm", "fanatics", "betrivers", "hardrock", "bovada", "betonlineag", "lowvig", "betus", "unibet", "onexbet", "marathon", "fliff", "betway",
        "matchbook", "smarkets", "sbobet", "bookmaker_eu", "betfair_ex", "thescorebet", "heritage", "tab_au", "tipico", "winamax", "betclic_pt", "stoiximan", "paddypower", "livescorebet",
        "superbet_ro", "superbet_pl", "ladbrokes_au", "supersport", "betway_za"]  # PropLineClient.KNOWN (v1.0.83)


def _ts(x):
    try:
        return datetime.datetime.fromisoformat(str(x).replace("Z", "+00:00")).timestamp()
    except (TypeError, ValueError):
        return None


def alt_stats(events, now):
    """PX (Tj 2026-10-25: "many results only have 1 book behind the fair odds for alternate markets that are popular and available at many books"): for every Novig spread/total rung, the
    books of [OURS] posting the SAME number, how old each quote is (now - the older side's last_seen_at), whether the rung is the book's main line, and the market shapes the engine's parser
    would drop (a market with other than 2 outcomes, a period or team market)."""
    lines, shapes, ages = [], {}, {}
    for e in events:
        rungs = {}
        for b in e.get("bookmakers") or []:
            bk = b.get("key")
            for m in b.get("markets") or []:
                mk = m.get("key")
                if mk not in ("spreads", "totals"):
                    continue
                outs = [o for o in m.get("outcomes") or [] if o.get("price") is not None]
                shapes[(mk, len(outs), bool(m.get("period")), bool(m.get("team")))] = shapes.get((mk, len(outs), bool(m.get("period")), bool(m.get("team"))), 0) + 1
                if len(outs) != 2 or m.get("period") or m.get("team"):
                    continue
                if mk == "spreads":
                    h = [o for o in outs if o.get("side") == "home" or o.get("name") == e.get("home_team")]
                    if not h or h[0].get("point") is None:
                        continue
                    key = (mk, float(h[0]["point"]))
                else:
                    if outs[0].get("point") is None:
                        continue
                    key = (mk, float(outs[0]["point"]))
                seen = [_ts(o.get("last_seen_at")) for o in outs]
                age = (now - min(seen)) if all(seen) else None
                rungs.setdefault(key, {})[bk] = (age, m.get("line_type"))
        for key, by in rungs.items():
            if "novig" not in by:
                continue
            ours = {k: v for k, v in by.items() if k in OURS}
            fresh5 = [k for k, (a, _) in ours.items() if a is not None and a <= 300]
            fresh10 = [k for k, (a, _) in ours.items() if a is not None and a <= 600]
            alt = by["novig"][1] == "alternate"
            lines.append({"game": "%s @ %s" % (e.get("away_team"), e.get("home_team")), "market": key[0], "point": key[1], "novig_line_type": by["novig"][1], "ours": len(ours),
                          "ours_fresh5": len(fresh5), "ours_fresh10": len(fresh10), "all_books": len(by) - 1,
                          "detail": {k: [None if a is None else int(a), lt] for k, (a, lt) in sorted(ours.items())}})
            for k, (a, _) in ours.items():
                if a is not None:
                    ages.setdefault(k, []).append(a)
    return {"lines": lines, "shapes": {"%s outcomes=%d period=%s team=%s" % k: v for k, v in sorted(shapes.items())},
            "book_age_p50": {k: sorted(v)[len(v) // 2] for k, v in sorted(ages.items())}}


def cmd_shape(sport, markets):
    """PX11e: the SHAPE of one league's raw board, printed to the job log (no key, no prices kept): which books, which market keys with which period/team fields, outcome names and
    points of a few games. A parser for a new league (tennis, 2026-10-26) is written from a real answer, never from the docs alone."""
    keys = keys_from_env()
    i, _ = best_key(keys, [plan_of(k) for k in keys])
    if i is None:
        print("no usable key")
        return 0
    st, _, data = request("/v1/sports/%s/odds" % sport, keys[i], {"markets": markets, "bookmakers": ",".join(OURS + ["novig"]), "includeBookIds": "true"})
    events = j(data)
    events = events if isinstance(events, list) else (events or {}).get("data") or []
    print("HTTP", st, len(events), "events")
    shapes, books, titles = {}, {}, {}
    for e in events:
        titles[e.get("sport_title") or e.get("sport_key") or "?"] = titles.get(e.get("sport_title") or e.get("sport_key") or "?", 0) + 1
        for b in e.get("bookmakers") or []:
            books[b.get("key")] = books.get(b.get("key"), 0) + 1
            for m in b.get("markets") or []:
                k = (m.get("key"), m.get("period"), bool(m.get("team")), len(m.get("outcomes") or []))
                shapes[k] = shapes.get(k, 0) + 1
    print("event keys:", sorted((events[0] if events else {}).keys()))
    print("titles:", titles)
    print("books:", sorted(books.items(), key=lambda x: -x[1]))
    print("market shapes (key, period, team, outcomes):", sorted(shapes.items(), key=lambda x: -x[1])[:40])
    for e in events[:3]:
        print("EVENT", {k: e.get(k) for k in ("id", "sport_key", "sport_title", "home_team", "away_team", "commence_time")})
        for b in (e.get("bookmakers") or [])[:2] + [b for b in e.get("bookmakers") or [] if b.get("key") == "pinnacle"]:
            print("  BOOK", b.get("key"), sorted(k for k in b.keys() if k not in ("markets",)))
            for m in (b.get("markets") or [])[:12]:
                print("    MKT", {k: m.get(k) for k in m.keys() if k != "outcomes"}, [{k: o.get(k) for k in ("name", "description", "point")} for o in (m.get("outcomes") or [])[:3]])
    return 0


def cmd_altprobe(outdir, sports=("football_nfl", "football_ncaaf")):
    """PX: the league boards the engine reads (its books + Novig, includeBookIds) measured by [alt_stats]."""
    keys = keys_from_env()
    i, _ = best_key(keys, [plan_of(k) for k in keys])
    if i is None:
        print("no usable key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    out = {}
    L = ["# PropLine alternate lines vs Vigilant's books (PX, %s)" % time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), ""]
    for sp in sports:
        st, _, data = request("/v1/sports/%s/odds" % sp, keys[i], {"markets": "spreads,totals", "bookmakers": ",".join(OURS + ["novig"]), "includeBookIds": "true"})
        events = j(data)
        events = events if isinstance(events, list) else (events or {}).get("data") or []
        with gzip.open(os.path.join(outdir, "board-%s.json.gz" % sp), "wb") as f:   # the raw answer (no key in it), to replay the engine's parser on
            f.write(data or b"")
        r = alt_stats(events, time.time())
        out[sp] = r
        ls = r["lines"]
        def med(v):
            v = sorted(v)
            return v[len(v) // 2] if v else 0
        for kind in ("main", "alternate"):
            sel = [x for x in ls if (x["novig_line_type"] == "main") == (kind == "main")]
            L.append("- %s %s Novig %s lines: %d; our books at the same number median %d (fresh 5 min %d, 10 min %d); all PropLine books %d" % (
                sp, "", kind, len(sel), med([x["ours"] for x in sel]), med([x["ours_fresh5"] for x in sel]), med([x["ours_fresh10"] for x in sel]), med([x["all_books"] for x in sel])))
        L.append("  market shapes: %s" % r["shapes"])
        L.append("  p50 quote age by book (s): %s" % {k: int(v) for k, v in r["book_age_p50"].items()})
        for x in sorted(ls, key=lambda x: x["ours"])[:12]:
            L.append("  thin: %s %s %s (%s): ours %d, all %d, %s" % (x["game"], x["market"], x["point"], x["novig_line_type"], x["ours"], x["all_books"], x["detail"]))
        print(sp, "HTTP", st, len(events), "games")
    with open(os.path.join(outdir, "altprobe.md"), "w") as f:
        f.write("\n".join(L) + "\n")
    with open(os.path.join(outdir, "altprobe.json"), "w") as f:
        json.dump(out, f)
    print("\n".join(L))
    return 0


def engine_books(src="data/src/main/kotlin/com/tjshea/vigilant/data/reference/PropLineClient.kt"):
    """The engine's PropLine book list, read from the Kotlin source (KNOWN + its additions, less EXCLUDED), sorted as `books(settings)` sends it: (old PS2 list, PX9c additions)."""
    import re
    text = open(src).read()

    def grab(name):
        m = re.search(r"val " + name + r" = setOf\((.*?)\)", text, re.S)
        return re.findall(r'"([^"]+)"', m.group(1)) if m else []
    ex = set(grab("EXCLUDED"))
    old = sorted(set(grab("KNOWN") + grab("ADDED_2026_10_25")) - ex)
    new = sorted(set(grab("ADDED_2026_10_25_MEASURED")) - ex - set(old))
    return old, new


def cmd_bookparam(outdir, sport="basketball_nba"):
    """PX9e (2026-10-25): every PropLine source on the engine answered HTTP 400 after PX9c grew `bookmakers` from 35 books to 69. Asks one league board with the engine's list whole, the old list, the new
    books alone and growing counts, and records each status and error body, to find the rule (a count cap, a length cap or one key) without guessing."""
    keys = keys_from_env()
    i, _ = best_key(keys, [plan_of(k) for k in keys])
    if i is None:
        print("no usable key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    old, new = engine_books()
    full = sorted(set(old + new)) + ["novig"]
    tries = [("engine list", full), ("old list", old + ["novig"]), ("new books alone", new)]
    tries += [("first %d" % n, full[:n]) for n in (40, 45, 50, 51, 55, 60, 65) if n < len(full)]
    L = ["# PropLine bookmakers parameter (PX9e, %s, %s)" % (time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), sport), ""]
    for name, books in tries:
        st, _, data = request("/v1/sports/%s/odds" % sport, keys[i], {"markets": "h2h", "bookmakers": ",".join(books)}, retries=1)
        L.append("- %s: %d books, %d chars -> HTTP %s %s" % (name, len(books), len(",".join(books)), st, "" if st == 200 else (data or b"")[:300].decode("utf-8", "replace")))
    with open(os.path.join(outdir, "bookparam.md"), "w") as f:
        f.write("\n".join(L) + "\n")
    print("\n".join(L))
    return 0


PROP_KEYS = {
    "football_nfl": "player_pass_yds,player_rush_yds,player_reception_yds,player_receptions,player_pass_tds,player_pass_attempts,player_pass_completions,player_rush_attempts",
    "basketball_nba": "player_points,player_rebounds,player_assists,player_threes,player_points_rebounds_assists",
    "hockey_nhl": "player_shots_on_goal,player_points,player_assists,goalie_saves",
}


def prop_pin(events, now):
    """PX6: per Novig prop rung (player, stat, number), whether Pinnacle posts the same rung at PropLine, and Pinnacle's quote age."""
    nov, pin, ages = set(), {}, []
    for e in events:
        for b in e.get("bookmakers") or []:
            bk = b.get("key")
            for m in b.get("markets") or []:
                for o in m.get("outcomes") or []:
                    if (o.get("name") or "").lower() != "over" or o.get("point") is None:
                        continue
                    k = (e.get("id"), m.get("key"), (o.get("description") or "").lower(), float(o["point"]))
                    if bk == "novig":
                        nov.add(k)
                    elif bk == "pinnacle":
                        t = _ts(o.get("last_seen_at"))
                        pin[k] = None if t is None else now - t
    for k in nov & set(pin):
        if pin[k] is not None:
            ages.append(pin[k])
    ages.sort()
    return {"novig": len(nov), "pinnacle": len(pin), "same": len(nov & set(pin)), "age_p50": int(ages[len(ages) // 2]) if ages else None}


def cmd_proppin(outdir):
    keys = keys_from_env()
    i, _ = best_key(keys, [plan_of(k) for k in keys])
    if i is None:
        print("no usable key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    L = ["# Pinnacle props at PropLine vs Novig's prop lines (PX6, %s)" % time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), ""]
    for sp, mk in PROP_KEYS.items():
        st, _, data = request("/v1/sports/%s/odds" % sp, keys[i], {"markets": mk, "bookmakers": "pinnacle,novig"})
        ev = j(data)
        ev = ev if isinstance(ev, list) else (ev or {}).get("data") or []
        r = prop_pin(ev, time.time())
        L.append("- %s (HTTP %s, %d games): Novig prop rungs %d, Pinnacle prop rungs %d, Pinnacle at Novig's number %d, Pinnacle age p50 %s s" % (sp, st, len(ev), r["novig"], r["pinnacle"], r["same"], r["age_p50"]))
    with open(os.path.join(outdir, "proppin.md"), "w") as f:
        f.write("\n".join(L) + "\n")
    print("\n".join(L))
    return 0


def selftest():
    assert tier_of(1000) == "free" and tier_of(5000) == "hobby" and tier_of(25000) == "pro" and tier_of(250000) == "streaming-lite" and tier_of(None) == "?"
    assert keys_from_env({"PROPLINE_API_KEY": "a, b\nc  d;e"}) == ["a", "b", "c", "d", "e"] and keys_from_env({}) == []
    plans = [{"status": 200, "limit": 1000}, {"status": 200, "limit": 25000}, {"status": 401, "limit": None}]
    assert best_key(["f", "p", "x"], plans)[0] == 1
    assert best_key(["x"], [{"status": 401, "limit": None}]) == (None, None)
    assert trim({"a": [1, 2, 3, 4, 5], "b": {"c": [[1, 2, 3, 4]]}}, 2) == {"a": [1, 2], "b": {"c": [[1, 2]]}}
    ev = [{"id": 1, "bookmakers": [
        {"key": k, "markets": [{"key": "totals", "outcomes": [{"name": "Over", "price": -110 if k != "c" else -105, "point": 44.5 if k != "d" else 45.5}, {"name": "Under", "price": -110, "point": 44.5 if k != "d" else 45.5}]}]}
        for k in ("novig", "a", "b", "c", "d")]}]
    cs = coverage_stats([("s", ev * 1)])
    assert cs["games"]["a"] == 1 and ("s", "totals", 3, 4) in cs["novig_lines"]
    ev2 = [{"home_team": "H", "away_team": "A", "bookmakers": [
        {"key": k, "markets": [{"key": "spreads", "line_type": "alternate", "outcomes": [{"name": "H", "side": "home", "price": -110, "point": -4.5, "last_seen_at": "2026-10-25T16:00:00Z"},
                                                                                     {"name": "A", "side": "away", "price": -110, "point": 4.5, "last_seen_at": "2026-10-25T16:00:00Z"}]}]}
        for k in ("novig", "draftkings", "fanduel", "somebook")]}]
    r2 = alt_stats(ev2, _ts("2026-10-25T16:02:00Z"))
    assert r2["lines"][0]["ours"] == 2 and r2["lines"][0]["ours_fresh5"] == 2 and r2["lines"][0]["all_books"] == 3, r2
    print("propline selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:] or ["plans"]
    if a[0] == "selftest":
        sys.exit(selftest())
    if a[0] == "plans":
        sys.exit(cmd_plans())
    if a[0] == "probe":
        sys.exit(cmd_probe(a[1] if len(a) > 1 else "out/propline-probe"))
    if a[0] == "steam":
        sys.exit(cmd_steam(a[1] if len(a) > 1 else "out/propline-steam"))
    if a[0] == "coverage":
        sys.exit(cmd_coverage(a[1] if len(a) > 1 else "out/propline-coverage"))
    if a[0] == "proppin":
        sys.exit(cmd_proppin(a[1] if len(a) > 1 else "out/propline-proppin"))
    if a[0] == "shape":
        sys.exit(cmd_shape(a[1], a[2] if len(a) > 2 and a[2] else "h2h,spreads,totals"))
    if a[0] == "altprobe":
        # PX9: the sports are a third argument ("basketball_nba,hockey_nhl"); none = football's two
        sys.exit(cmd_altprobe(a[1] if len(a) > 1 else "out/propline-altprobe", tuple(x for x in a[2].split(",") if x) if len(a) > 2 and a[2] else ("football_nfl", "football_ncaaf")))
    if a[0] == "bookparam":
        sys.exit(cmd_bookparam(a[1] if len(a) > 1 else "out/propline-bookparam", a[2] if len(a) > 2 and a[2] else "basketball_nba"))
    if a[0] == "speed":
        sys.exit(cmd_speed(a[1] if len(a) > 1 else "out/propline-speed"))
    if a[0] == "archive":
        d = 3
        if "--days" in a:
            d = int(a[a.index("--days") + 1])
        # SX (2026-10-24): --books / --sports pull other books' resolved props (open + close + result) for the star study; the daily archive keeps Novig and Pinnacle.
        bk = a[a.index("--books") + 1].split(",") if "--books" in a else None
        sp = a[a.index("--sports") + 1].split(",") if "--sports" in a else None
        sys.exit(cmd_archive(a[1] if len(a) > 1 and not a[1].startswith("--") else "out/propline-archive", d, bk, sp))
    print(__doc__)
