#!/usr/bin/env python3
"""ParlayAPI Pro helper (CH47, Tj 2026-10-20: "I upgraded one of my parlayapi keys to pro ... find out which one is the pro tier and use it to its full potential").

Runs on GitHub (.github/workflows/parlay.yml) with the PARLAY_API_KEY secret (several keys, comma separated); a session never sees a key. Tasks:
  plans    each key's tier and credits from the free GET /v1/usage (the key is shown only as "#N", never in clear)
  probe    one call to every Pro/live/arb endpoint with the biggest key; the answers (keyless, trimmed) are written to a directory the workflow puts in the `parlay-samples` Release
           so the app's parsers are checked against REAL shapes (SSE play-by-play frames included: a 20 s listen on the live PBP stream)
Costs of a probe (docs): arbitrage 10 a sport, inplay/arbs 5, live 3, live/points 1, book_latency 5, source-health 1, period_markets 2, sse 5: about 60 credits.
"""
import datetime, json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://parlay-api.com"
SPORTS = ["basketball_nba", "icehockey_nhl", "baseball_mlb", "americanfootball_nfl", "americanfootball_ncaaf", "basketball_ncaab", "tennis"]


def keys_from_env(env=None):
    raw = (os.environ if env is None else env).get("PARLAY_API_KEY", "")
    return [k for k in re.split(r"[\s,;]+", raw) if k]


def request(path, key, params=None, timeout=60, retries=2):
    """-> (status, headers (lower-case), bytes). Never raises on HTTP errors; the key goes only in the X-API-Key header."""
    url = BASE + path + (("?" + urllib.parse.urlencode(params)) if params else "")
    for attempt in range(retries + 1):
        req = urllib.request.Request(url)
        req.add_header("X-API-Key", key)
        req.add_header("Accept", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.status, {k.lower(): v for k, v in r.headers.items()}, r.read()
        except urllib.error.HTTPError as e:
            data = e.read()
            hdr = {k.lower(): v for k, v in e.headers.items()}
            if e.code in (429, 503) and attempt < retries:
                time.sleep(min(float(hdr.get("retry-after", "1") or 1), 20) + 0.5)
                continue
            return e.code, hdr, data
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            if attempt < retries:
                time.sleep(2 * (attempt + 1))
                continue
            return 0, {}, b""
    return 0, {}, b""


def j(data):
    try:
        return json.loads(data)
    except (ValueError, TypeError):
        return None


def trim(x, n=3, depth=0):
    """A short copy of an answer: lists keep n items, nesting stops at 8 levels."""
    if depth > 8:
        return "..."
    if isinstance(x, list):
        return [trim(v, n, depth + 1) for v in x[:n]] + (["... %d more" % (len(x) - n)] if len(x) > n else [])
    if isinstance(x, dict):
        return {k: trim(v, n, depth + 1) for k, v in x.items()}
    return x


def usage_of(key):
    st, h, data = request("/v1/usage", key)
    d = j(data) if st == 200 else None
    if not isinstance(d, dict):
        return {"status": st}
    return {"status": st, "tier": d.get("tier") or d.get("plan"), "total": d.get("credits_total"), "remaining": d.get("credits_remaining"), "used": d.get("credits_used"),
            "period_end": d.get("period_end")}


def rank(u):
    return u.get("total") or 0


def cmd_plans():
    keys = keys_from_env()
    print("%d key(s)" % len(keys))
    best = None
    for i, k in enumerate(keys, 1):
        u = usage_of(k)
        print("#%d HTTP %s tier=%s credits %s/%s remaining" % (i, u.get("status"), u.get("tier"), u.get("remaining"), u.get("total")))
        if u.get("status") == 200 and (best is None or rank(u) > rank(best[1])):
            best = (i, u)
    if best:
        print("biggest: #%d (%s)" % (best[0], best[1].get("tier")))
    return 0


def listen_sse(key, sport, seconds, out):
    """Open the live PBP stream for [seconds] and keep the raw frames (text), at most 60 lines. The key goes in the header, never the URL."""
    req = urllib.request.Request(BASE + "/v1/sports/%s/live/sse" % sport)
    req.add_header("X-API-Key", key)
    req.add_header("Accept", "text/event-stream")
    lines = []
    end = time.time() + seconds
    try:
        with urllib.request.urlopen(req, timeout=seconds + 10) as r:
            lines.append("HTTP %s content-type %s" % (r.status, r.headers.get("content-type")))
            while time.time() < end and len(lines) < 60:
                raw = r.readline()
                if not raw:
                    break
                lines.append(raw.decode("utf8", "replace").rstrip()[:1500])
    except urllib.error.HTTPError as e:
        lines.append("HTTP %s %s" % (e.code, e.read()[:500].decode("utf8", "replace")))
    except Exception as e:  # a closed or timed-out stream still gave its first frames
        lines.append("ended: %s" % type(e).__name__)
    with open(out, "w") as f:
        f.write("\n".join(lines) + "\n")
    return lines


def cmd_probe(outdir):
    keys = keys_from_env()
    ranked = sorted(((usage_of(k), k) for k in keys), key=lambda t: -rank(t[0]))
    if not ranked or ranked[0][0].get("status") != 200:
        print("no usable key")
        return 0
    u, key = ranked[0]
    os.makedirs(outdir, exist_ok=True)
    report = []

    def save(name, st, h, data, note=""):
        body = j(data)
        report.append("%-34s HTTP %s %s" % (name, st, note))
        with open(os.path.join(outdir, name + ".json"), "w") as f:
            json.dump({"status": st, "credit_headers": {k: v for k, v in h.items() if "credit" in k or "requests-" in k},
                       "body": trim(body) if body is not None else data[:2000].decode("utf8", "replace")}, f, indent=1)

    save("usage", 200, {}, json.dumps(u).encode(), "tier %s, %s of %s credits" % (u.get("tier"), u.get("remaining"), u.get("total")))
    st, h, data = request("/v1/meta/limits", key); save("meta_limits", st, h, data)
    st, h, data = request("/v1/inplay/arbs", key); save("inplay_arbs", st, h, data)
    live_sport = None
    for sp in SPORTS:
        st, h, data = request("/v1/sports/%s/live/points" % sp, key)
        d = j(data)
        n = len(d.get("results", d.get("matches", []))) if isinstance(d, dict) else (len(d) if isinstance(d, list) else 0)
        save("live_points_" + sp, st, h, data, "(%d in play)" % n)
        if st == 200 and n and live_sport is None:
            live_sport = sp
    for sp in ("basketball_nba", "icehockey_nhl", "baseball_mlb"):
        st, h, data = request("/v1/sports/%s/arbitrage" % sp, key, {"limit": 10}); save("arbitrage_" + sp, st, h, data)
    sp = live_sport or "basketball_nba"
    st, h, data = request("/v1/sports/%s/live" % sp, key); save("live_games_" + sp, st, h, data)
    st, h, data = request("/v1/sports/%s/live/book_latency" % sp, key); save("book_latency_" + sp, st, h, data)
    st, h, data = request("/v1/sports/%s/live/source-health" % sp, key); save("source_health_" + sp, st, h, data)
    st, h, data = request("/v1/sports/%s/live/period_markets" % sp, key, {"period": "all", "market": "total"}); save("period_markets_" + sp, st, h, data)
    lines = listen_sse(key, sp, 20, os.path.join(outdir, "sse_%s.txt" % sp))
    report.append("%-34s %d lines: %s" % ("live/sse " + sp, len(lines), (lines[0] if lines else "")[:120]))
    with open(os.path.join(outdir, "REPORT.txt"), "w") as f:
        f.write("ParlayAPI Pro probe, %s UTC, biggest key tier %s (%s credits)\n" % (time.strftime("%Y-%m-%d %H:%M", time.gmtime()), u.get("tier"), u.get("total")) + "\n".join(report) + "\n")
    print("\n".join(report))
    return 0


def cmd_listen(outdir, sport="tennis", wait_min=40, seconds=120):
    """Wait (up to [wait_min] minutes, one 1-credit /live/points a minute) for a match of [sport] to be in play, then listen to its PBP stream for [seconds] and keep the raw frames: the shapes a live model is built on."""
    keys = keys_from_env()
    ranked = sorted(((usage_of(k), k) for k in keys), key=lambda t: -rank(t[0]))
    if not ranked or ranked[0][0].get("status") != 200:
        print("no usable key")
        return 0
    key = ranked[0][1]
    os.makedirs(outdir, exist_ok=True)
    end = time.time() + wait_min * 60
    while time.time() < end:
        st, h, data = request("/v1/sports/%s/live/points" % sport, key)
        d = j(data)
        n = len(d.get("matches", [])) if isinstance(d, dict) else 0
        print("%s: %d in play" % (time.strftime("%H:%M:%S"), n))
        if st == 200 and n:
            with open(os.path.join(outdir, "points_%s.json" % sport), "w") as f:
                json.dump(trim(d, 6), f, indent=1)
            lines = listen_sse(key, sport, seconds, os.path.join(outdir, "sse_%s_live.txt" % sport))
            print("listened: %d lines" % len(lines))
            return 0
        time.sleep(60)
    print("no %s match in play within %d min" % (sport, wait_min))
    return 0


def listen_url(key, path, params, seconds, out, max_lines=400, cut=4000):
    """Any SSE route for [seconds]: the raw frames (each cut to [cut] chars; None = whole), at most [max_lines]. The key goes in the header, never in the saved file."""
    req = urllib.request.Request(BASE + path + (("?" + urllib.parse.urlencode(params)) if params else ""))
    req.add_header("X-API-Key", key)
    req.add_header("Accept", "text/event-stream")
    lines = []
    end = time.time() + seconds
    try:
        with urllib.request.urlopen(req, timeout=seconds + 15) as r:
            lines.append("HTTP %s content-type %s" % (r.status, r.headers.get("content-type")))
            while time.time() < end and len(lines) < max_lines:
                raw = r.readline()
                if not raw:
                    break
                t = raw.decode("utf8", "replace").rstrip()
                if t:
                    lines.append(t[:cut] if cut else t)
    except urllib.error.HTTPError as e:
        lines.append("HTTP %s %s" % (e.code, e.read()[:500].decode("utf8", "replace")))
    except Exception as e:
        lines.append("ended: %s" % type(e).__name__)
    with open(out, "w") as f:
        f.write("\n".join(lines).replace(key, "<key>") + "\n")
    return lines


def cmd_streamprobe(outdir, sport="icehockey_nhl"):
    """Business tier (PB10, Tj 2026-10-25): the odds SSE, the hot SSE and the line-move SSE for [sport] (60 s each), and the freshness routes, saved keyless so the engine's stream parser is built on real frames."""
    keys = keys_from_env()
    ranked = sorted(((usage_of(k), k) for k in keys), key=lambda t: -rank(t[0]))
    if not ranked or ranked[0][0].get("status") != 200:
        print("no usable key")
        return 0
    u, key = ranked[0]
    print("biggest key: tier %s, %s/%s credits left" % (u.get("tier"), u.get("remaining"), u.get("total")))
    os.makedirs(outdir, exist_ok=True)
    for name, path, params in [
        ("sse_odds_%s.txt" % sport, "/v1/sse/odds/%s" % sport, {"kinds": "game,prop", "limit": 50, "heartbeat_s": 5}),
        ("sse_hot_%s.txt" % sport, "/v1/sse/hot/%s" % sport, {"kinds": "game", "limit": 50, "heartbeat_s": 5}),
        ("sse_drop_%s.txt" % sport, "/v1/odds-drop/%s" % sport, {"threshold": 10, "heartbeat_s": 5}),
    ]:
        got = listen_url(key, path, params, 60, os.path.join(outdir, name), max_lines=120, cut=None)
        print("%s: %d lines" % (name, len(got)))
        # One whole row of every (book, kind, market) shape the stream sent, for the engine's parser (PB11).
        shapes = {}
        for t in got:
            if not t.startswith("data: "):
                continue
            try:
                d = json.loads(t[6:], strict=False)
            except ValueError:
                continue
            for r in d.get("data") or []:
                if isinstance(r, dict):
                    shapes.setdefault("%s|%s|%s" % (r.get("bookmaker"), r.get("kind"), r.get("market_key")), r)
        with open(os.path.join(outdir, name.replace(".txt", "_shapes.json")), "w") as f:
            json.dump(shapes, f, indent=1)
    # The REST board for the same sport (3 credits), to match the stream's event ids and rows to the boards the scan reads.
    st, h, data = request("/v1/sports/%s/odds" % sport, key, {"markets": "h2h,spreads,totals", "oddsFormat": "american", "regions": "us", "includeLinks": "false"})
    with open(os.path.join(outdir, "rest_odds_%s.json" % sport), "w") as f:
        json.dump({"status": st, "body": trim(j(data), 3) if j(data) is not None else data[:2000].decode("utf8", "replace")}, f, indent=1)
    for name, path in [("source_health_%s.json" % sport, "/v1/sports/%s/live/source-health" % sport), ("meta_source_health.json", "/v1/meta/source-health"),
                       ("book_freshness_pinnacle.json", "/v1/bookmakers/pinnacle/freshness"), ("book_freshness_fanduel.json", "/v1/bookmakers/fanduel/freshness"),
                       ("pinnacle_coverage.json", "/v1/pinnacle-coverage"), ("usage.json", "/v1/usage")]:
        st, h, data = request(path, key)
        body = j(data)
        if isinstance(body, dict):  # a public Release: never the account's e-mail or key fingerprint
            for k in ("email", "api_key_fingerprint", "user_id", "account_id"):
                body.pop(k, None)
        with open(os.path.join(outdir, name), "w") as f:
            json.dump({"status": st, "credits_last": h.get("x-requests-last"), "body": trim(body, 12) if body is not None else data[:2000].decode("utf8", "replace")}, f, indent=1)
        print("%s: HTTP %s" % (name, st))
    return 0


def cmd_arbscan(outdir):
    """Does ParlayAPI's arb feed ever pair the two venues Tj can bet (Novig and Kalshi)? Counts the book pairs of the pregame /arbitrage (200 per sport) and of /inplay/arbs; ~70 credits."""
    keys = keys_from_env()
    ranked = sorted(((usage_of(k), k) for k in keys), key=lambda t: -rank(t[0]))
    if not ranked or ranked[0][0].get("status") != 200:
        print("no usable key")
        return 0
    key = ranked[0][1]
    os.makedirs(outdir, exist_ok=True)
    pairs = {}
    rows = []

    def add(src, a, b, profit, mkt):
        k = tuple(sorted((a or "?", b or "?")))
        pairs[(src,) + k] = pairs.get((src,) + k, 0) + 1
        if "kalshi" in k:
            rows.append({"src": src, "a": a, "b": b, "profit": profit, "market": mkt})

    for sp in ("basketball_nba", "icehockey_nhl", "baseball_mlb", "americanfootball_nfl", "americanfootball_ncaaf", "basketball_ncaab"):
        st, h, data = request("/v1/sports/%s/arbitrage" % sp, key, {"limit": 200, "min_profit_pct": 0.1})
        d = j(data)
        for a in d if isinstance(d, list) else []:
            if isinstance(a, dict):
                add("pre:" + sp, (a.get("side_a") or {}).get("bookmaker"), (a.get("side_b") or {}).get("bookmaker"), a.get("profit_pct"), a.get("market_key"))
        print(sp, st, len(d) if isinstance(d, list) else "?")
    st, h, data = request("/v1/inplay/arbs", key)
    d = j(data)
    for a in (d.get("arbs", []) if isinstance(d, dict) else []):
        add("inplay", (a.get("over") or {}).get("bookmaker"), (a.get("under") or {}).get("bookmaker"), a.get("profit_pct"), a.get("market_key"))
    with open(os.path.join(outdir, "arbscan.json"), "w") as f:
        json.dump({"pairs": sorted(([list(k), v] for k, v in pairs.items()), key=lambda t: -t[1])[:80], "kalshi_rows": rows[:50]}, f, indent=1)
    print("pairs with kalshi:", len(rows), "total pair kinds:", len(pairs))
    nov_k = [r for r in rows if "novig" in (r["a"], r["b"])]
    print("novig+kalshi pairs:", len(nov_k))
    return 0


def _ms(x):
    if isinstance(x, (int, float)):
        return float(x) if x > 1e12 else float(x) * 1000
    try:
        return datetime.datetime.fromisoformat(str(x).replace("Z", "+00:00")).timestamp() * 1000
    except (TypeError, ValueError):
        return None


def pin_lines(events, now_ms):
    """PX6 (Tj 2026-10-25: "get the most odds from Pinnacle including props using both propline and parlayapi"): an /odds answer (Odds-API shape, Pinnacle + Novig, main and alternate
    markets) -> per Novig spread/total rung whether Pinnacle posts the same number, and Pinnacle's quote age."""
    out = []
    for e in events:
        rungs = {}
        for b in e.get("bookmakers") or []:
            bk = b.get("key")
            at = _ms(b.get("last_update_ms") or b.get("last_update"))
            for m in b.get("markets") or []:
                mk = (m.get("key") or "").replace("alternate_", "")
                if mk not in ("spreads", "totals"):
                    continue
                for o in m.get("outcomes") or []:
                    if o.get("point") is None:
                        continue
                    if mk == "spreads" and o.get("name") != e.get("home_team"):
                        continue
                    if mk == "totals" and (o.get("name") or "").lower() != "over":
                        continue
                    rungs.setdefault((mk, float(o["point"])), {})[bk] = None if at is None else (now_ms - at) / 1000.0
        for (mk, pt), by in rungs.items():
            if "novig" in by:
                out.append({"game": "%s @ %s" % (e.get("away_team"), e.get("home_team")), "market": mk, "point": pt, "pinnacle": "pinnacle" in by, "pin_age": by.get("pinnacle")})
    return out


def cmd_pinprobe(outdir, sports=("americanfootball_nfl", "americanfootball_ncaaf", "basketball_nba", "icehockey_nhl")):
    """PX6: Pinnacle's coverage of Novig's game lines (main + alternates) and props at ParlayAPI, on the biggest key; ~5 credits a league for lines, 3 for props."""
    keys = keys_from_env()
    best = max(keys, key=lambda k: rank(usage_of(k)), default=None)
    if not best:
        print("no key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    L = ["# Pinnacle at ParlayAPI vs Novig's lines (PX6, %s)" % time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), ""]
    for sp in sports:
        st, _, data = request("/v1/sports/%s/odds" % sp, best, {"bookmakers": "pinnacle,novig", "markets": "spreads,totals,alternate_spreads,alternate_totals", "oddsFormat": "american"})
        ev = j(data) if st == 200 else None
        ev = ev if isinstance(ev, list) else []
        ls = pin_lines(ev, time.time() * 1000)
        ages = sorted(x["pin_age"] for x in ls if x["pinnacle"] and x["pin_age"] is not None)
        L.append("- %s game lines (HTTP %s, %d games): Novig rungs %d, Pinnacle at the same number %d; Pinnacle age p50 %s s" % (
            sp, st, len(ev), len(ls), sum(1 for x in ls if x["pinnacle"]), int(ages[len(ages) // 2]) if ages else "-"))
        st2, _, data2 = request("/v1/sports/%s/props" % sp, best, {"bookmakers": "pinnacle,novig", "oddsFormat": "american", "limit": "10000"})
        pr = j(data2) if st2 == 200 else None
        rows = pr if isinstance(pr, list) else ((pr or {}).get("data") or (pr or {}).get("props") or []) if isinstance(pr, dict) else []
        nov, pin = set(), set()
        for r in rows:
            if not isinstance(r, dict):
                continue
            k = (r.get("event_id") or r.get("game_id"), (r.get("player") or r.get("player_name") or "").lower(), r.get("market_key") or r.get("market"), r.get("line") or r.get("point"))
            (nov if r.get("bookmaker") == "novig" else pin if r.get("bookmaker") == "pinnacle" else set()).add(k)
        L.append("  props (HTTP %s, %d rows): Novig prop lines %d, Pinnacle at the same player/stat/number %d; Pinnacle prop lines %d%s" % (
            st2, len(rows), len(nov), len(nov & pin), len(pin), "" if rows else " (" + str(data2[:200]) + ")"))
        if rows and not nov:
            L.append("  first row: %s" % json.dumps(rows[0])[:400])
    with open(os.path.join(outdir, "pinprobe.md"), "w") as f:
        f.write("\n".join(L) + "\n")
    print("\n".join(L))
    return 0


def game_view(events, now_ms, home_sub, away_sub):
    """PX8 (Tj 2026-10-25: "Why is there no parlayapi?"): one game of an /odds answer as {(market, point, side): {book: age_s}}, so the books at each number and how old each is can be read."""
    out = {}
    for e in events:
        if home_sub.lower() not in (e.get("home_team") or "").lower() or away_sub.lower() not in (e.get("away_team") or "").lower():
            continue
        for b in e.get("bookmakers") or []:
            at = _ms(b.get("last_update_ms") or b.get("last_update"))
            for m in b.get("markets") or []:
                for o in m.get("outcomes") or []:
                    side = (o.get("name") or "")[:14]
                    if m.get("key") in ("totals", "alternate_totals"):
                        side = side if side in ("Over", "Under") else side
                    k = (m.get("key"), o.get("point"), side)
                    out.setdefault(k, {})[b.get("key")] = None if at is None else int((now_ms - at) / 1000)
    return out


def cmd_gameprobe(outdir, sport="americanfootball_nfl", home="Cardinals", away="Broncos"):
    """PX8: every book ParlayAPI has for one game, by market and number (REST /odds with the alternates, and /live/period_markets for the half), with each quote's age. ~12 credits."""
    keys = keys_from_env()
    best = max(keys, key=lambda k: rank(usage_of(k)), default=None)
    if not best:
        print("no key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    books = "pinnacle,bookmaker_eu,prophetx,betonline,bet365,bovada,draftkings,fanduel,caesars,betmgm,fanatics,betrivers,hardrock,fliff,parx"
    L = ["# ParlayAPI for %s @ %s (%s, PX8, %s)" % (away, home, sport, time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime())), ""]
    now = time.time() * 1000
    st, hdr, data = request("/v1/sports/%s/odds" % sport, best, {"bookmakers": books, "markets": "h2h,spreads,totals,alternate_spreads,alternate_totals", "oddsFormat": "american"})
    ev = j(data) if st == 200 else None
    ev = ev if isinstance(ev, list) else []
    view = game_view(ev, now, home, away)
    L.append("## /odds (HTTP %s, %d games, game found: %s)" % (st, len(ev), bool(view)))
    for (mk, pt, side), by in sorted(view.items(), key=lambda kv: (str(kv[0][0]), kv[0][1] if kv[0][1] is not None else 0, str(kv[0][2]))):
        if mk in ("h2h",) or side in ("Over", "Away") or mk in ("spreads", "alternate_spreads"):
            L.append("- %s %s %s: %d books: %s" % (mk, pt, side, len(by), ", ".join("%s %ss" % (b, a) for b, a in sorted(by.items()))))
    st2, _, data2 = request("/v1/sports/%s/live/period_markets" % sport, best, {"period": "1H"})
    d2 = j(data2) if st2 == 200 else None
    rows = (d2.get("results") if isinstance(d2, dict) else d2) or []
    rows = [r for r in rows if isinstance(r, dict) and home.lower() in (r.get("home_team") or "").lower() and away.lower() in (r.get("away_team") or "").lower()]
    L += ["", "## /live/period_markets 1H (HTTP %s, %d rows for the game)" % (st2, len(rows))]
    by = {}
    for r in rows:
        age = r.get("observed_age_seconds")
        by.setdefault((r.get("market"), r.get("line"), r.get("side")), {})[r.get("source")] = None if age is None else int(age)
    for k, v in sorted(by.items(), key=lambda kv: str(kv[0])):
        L.append("- %s %s %s: %d books: %s" % (k[0], k[1], k[2], len(v), ", ".join("%s %ss" % (b, a) for b, a in sorted(v.items(), key=lambda x: str(x[0])))))
    with open(os.path.join(outdir, "gameprobe.md"), "w") as f:
        f.write("\n".join(L) + "\n")
    print("\n".join(L))
    return 0


def cmd_bookprobe(outdir, sports=("basketball_nba", "americanfootball_nfl", "icehockey_nhl")):
    """PX9 (Tj 2026-10-25: "odds from as many books as possible"): the books ParlayAPI lists (/v1/bookmakers?all=true, free) and, per league, every book that quotes a full-game line on the board with
    no `bookmakers=` filter (regions us,us2,eu,uk,au), with its line count and quote age: the books Vigilant's 15-book request leaves out. About 12 credits a league."""
    keys = keys_from_env()
    best = max(keys, key=lambda k: rank(usage_of(k)), default=None)
    if not best:
        print("no key")
        return 0
    os.makedirs(outdir, exist_ok=True)
    L = ["# ParlayAPI books (PX9, %s)" % time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), ""]
    st, _, data = request("/v1/bookmakers", best, {"all": "true"})
    reg = j(data)
    rows = reg if isinstance(reg, list) else (reg or {}).get("bookmakers") or (reg or {}).get("data") or []
    L.append("## /v1/bookmakers?all=true (HTTP %s, %d entries)" % (st, len(rows)))
    for r in rows:
        if isinstance(r, dict):
            L.append("- %s | %s | status %s | %s" % (r.get("key"), r.get("title") or r.get("name"), r.get("status"), (r.get("note") or r.get("merged_into") or "")[:80]))
    now = time.time() * 1000
    for sp in sports:
        st2, _, d2 = request("/v1/sports/%s/odds" % sp, best, {"regions": "us,us2,eu,uk,au", "markets": "h2h,spreads,totals", "oddsFormat": "american"})
        ev = j(d2) if st2 == 200 else None
        ev = ev if isinstance(ev, list) else []
        per = {}
        for e in ev:
            for b in e.get("bookmakers") or []:
                at = _ms(b.get("last_update_ms") or b.get("last_update"))
                r = per.setdefault(b.get("key"), {"lines": 0, "games": set(), "ages": []})
                r["games"].add(e.get("id"))
                if at is not None:
                    r["ages"].append((now - at) / 1000.0)
                for m in b.get("markets") or []:
                    r["lines"] += len(m.get("outcomes") or [])
        L += ["", "## %s (HTTP %s, %d games): book | games | outcomes | age p50 s" % (sp, st2, len(ev))]
        for k, r in sorted(per.items(), key=lambda kv: -kv[1]["lines"]):
            ages = sorted(r["ages"])
            L.append("- %s | %d | %d | %s" % (k, len(r["games"]), r["lines"], int(ages[len(ages) // 2]) if ages else "-"))
    # Team totals are not on /odds ("served by /props?markets=team_totals"): who quotes them, and the row shape.
    for sp in sports:
        st3, _, d3 = request("/v1/sports/%s/props" % sp, best, {"markets": "team_totals", "oddsFormat": "american", "limit": "2000"})
        b3 = j(d3) if st3 == 200 else None
        rows3 = b3 if isinstance(b3, list) else ((b3 or {}).get("data") or (b3 or {}).get("props") or []) if isinstance(b3, dict) else []
        per = {}
        for r in rows3:
            if isinstance(r, dict):
                per[r.get("bookmaker")] = per.get(r.get("bookmaker"), 0) + 1
        L += ["", "## %s team_totals via /props (HTTP %s, %d rows): %s" % (sp, st3, len(rows3), ", ".join("%s %d" % kv for kv in sorted(per.items(), key=lambda kv: -kv[1])))]
        if rows3 and isinstance(rows3[0], dict):
            L.append("first row: " + json.dumps(rows3[0])[:900])
        elif not rows3:
            L.append("answer: " + (d3[:300].decode("utf-8", "replace") if d3 else ""))
    with open(os.path.join(outdir, "bookprobe.md"), "w") as f:
        f.write("\n".join(L) + "\n")
    print("\n".join(L))
    return 0


def selftest():
    assert keys_from_env({"PARLAY_API_KEY": "a, b;c\nd"}) == ["a", "b", "c", "d"]
    assert keys_from_env({}) == []
    assert trim({"a": [1, 2, 3, 4, 5]}) == {"a": [1, 2, 3, "... 2 more"]}
    assert rank({"total": 100000}) > rank({"total": 20000}) > rank({})
    ev = [{"home_team": "H", "away_team": "A", "bookmakers": [
        {"key": "novig", "last_update_ms": 1792000000000, "markets": [{"key": "alternate_spreads", "outcomes": [{"name": "H", "point": -4.5, "price": -110}, {"name": "A", "point": 4.5, "price": -110}]}]},
        {"key": "pinnacle", "last_update_ms": 1792000000000, "markets": [{"key": "alternate_spreads", "outcomes": [{"name": "H", "point": -4.5, "price": -105}, {"name": "A", "point": 4.5, "price": -115}]}]}]}]
    pl = pin_lines(ev, 1792000060000)
    assert pl == [{"game": "A @ H", "market": "spreads", "point": -4.5, "pinnacle": True, "pin_age": 60.0}], pl
    gv = game_view([{"home_team": "Arizona Cardinals", "away_team": "Denver Broncos", "bookmakers": [{"key": "draftkings", "last_update_ms": 1792000000000, "markets": [{"key": "totals", "outcomes": [{"name": "Over", "point": 44.5, "price": -110}]}]}]}], 1792000030000, "Cardinals", "Broncos")
    assert gv == {("totals", 44.5, "Over"): {"draftkings": 30}}, gv
    print("parlay.py selftest ok")
    return 0


if __name__ == "__main__":
    c = sys.argv[1] if len(sys.argv) > 1 else "plans"
    if c == "selftest":
        sys.exit(selftest())
    if c == "plans":
        sys.exit(cmd_plans())
    if c == "arbscan":
        sys.exit(cmd_arbscan(sys.argv[2] if len(sys.argv) > 2 else "out/parlay"))
    if c == "listen":
        sys.exit(cmd_listen(sys.argv[2] if len(sys.argv) > 2 else "out/parlay", sys.argv[3] if len(sys.argv) > 3 else "tennis"))
    if c == "streamprobe":
        sys.exit(cmd_streamprobe(sys.argv[2] if len(sys.argv) > 2 else "out/parlay", sys.argv[3] if len(sys.argv) > 3 else "icehockey_nhl"))
    if c == "pinprobe":
        sys.exit(cmd_pinprobe(sys.argv[2] if len(sys.argv) > 2 else "out/parlay"))
    if c == "bookprobe":
        sys.exit(cmd_bookprobe(*(sys.argv[2:3] if len(sys.argv) > 2 else ["out/parlay"])))
    if c == "gameprobe":
        sys.exit(cmd_gameprobe(*(sys.argv[2:6] if len(sys.argv) > 2 else ["out/parlay"])))
    if c == "probe":
        sys.exit(cmd_probe(sys.argv[2] if len(sys.argv) > 2 else "out/parlay"))
    print("usage: parlay.py plans | probe [dir] | listen [dir] [sport] | selftest")
    sys.exit(2)
