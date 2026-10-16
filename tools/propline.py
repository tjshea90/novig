#!/usr/bin/env python3
"""PropLine Pro tooling for GitHub Actions (TCL56; Tj 2026-10-16). Standard library only; no Claude usage.

  python3 tools/propline.py plans                      which key is the paid one (X-Daily-Limit of each) and what it unlocks
  python3 tools/propline.py probe OUTDIR               one call to every Pro endpoint on a real recent event; saves the answers (keyless) so the app's parsers can be checked against real shapes
  python3 tools/propline.py archive OUTDIR [--days N]  the resolved-prop archive: GET /v1/exports/resolved-props per sport (gzip CSV), the free accuracy/coverage reports; files for a Release
  python3 tools/propline.py selftest

Keys come from the environment variable PROPLINE_API_KEY (several keys: comma, space or newline separated; the phone's "send keys to GitHub" writes this secret). The key used is the one whose own
plan is largest (its `X-Daily-Limit`), so a Pro key is found among free ones. A key is never printed. See research/PROPLINE_PRO_2026-10-16.md for the endpoints.
"""
import datetime, gzip, json, os, re, sys, time, urllib.error, urllib.parse, urllib.request

BASE = "https://api.prop-line.com"
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
    # a finished event: closing, results, history
    done = None
    for sp in ("basketball_nba", "baseball_mlb", "hockey_nhl", "football_nfl", "football_ncaaf", "basketball_wnba"):
        st, h, data = request("/v1/sports/%s/scores" % sp, key)
        sc = j(data)
        if st == 200 and isinstance(sc, list):
            fin = [g for g in sc if g.get("completed")]
            if fin:
                done = (sp, fin[0])
                save("scores", st, h, data, "(%s)" % sp)
                break
    if done:
        dsp, g = done
        did = g["id"]
        st, h, data = request("/v1/sports/%s/events/%s/odds/closing" % (dsp, did), key, {"markets": "h2h,spreads,totals", "bookmakers": "novig,pinnacle"}); save("closing", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/results" % (dsp, did), key, {"bookmakers": "novig,pinnacle"}); save("results", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/odds/history" % (dsp, did), key, {"markets": "h2h", "bookmakers": "pinnacle", "changes_only": "true"}); save("history", st, h, data)
        st, h, data = request("/v1/sports/%s/events/%s/stats" % (dsp, did), key); save("stats", st, h, data)
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


def cmd_archive(outdir, days):
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
    for sp in SPORTS_ARCHIVE:
        st, h, data = request("/v1/exports/resolved-props", key, {"sport": sp, "since": since, "until": until}, accept_gzip=True, timeout=600)
        if st != 200:
            print("%s: HTTP %s %s" % (sp, st, data[:200].decode("utf8", "replace")))
            if st == 429:
                break
            continue
        raw = gzip.decompress(data) if h.get("content-encoding") == "gzip" else data
        name = "resolved-%s-%s_%s.csv.gz" % (sp, since[:10], until[:10])
        with gzip.open(os.path.join(outdir, name), "wb") as f:
            f.write(raw)
        rows = raw.count(b"\n") - 1
        made.append(name)
        print("%s: %d rows, archive starts %s, export calls left today %s" % (sp, rows, h.get("x-propline-archive-starts"), h.get("x-propline-export-daily-remaining")))
        time.sleep(1)
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


def selftest():
    assert tier_of(1000) == "free" and tier_of(5000) == "hobby" and tier_of(25000) == "pro" and tier_of(250000) == "streaming-lite" and tier_of(None) == "?"
    assert keys_from_env({"PROPLINE_API_KEY": "a, b\nc  d;e"}) == ["a", "b", "c", "d", "e"] and keys_from_env({}) == []
    plans = [{"status": 200, "limit": 1000}, {"status": 200, "limit": 25000}, {"status": 401, "limit": None}]
    assert best_key(["f", "p", "x"], plans)[0] == 1
    assert best_key(["x"], [{"status": 401, "limit": None}]) == (None, None)
    assert trim({"a": [1, 2, 3, 4, 5], "b": {"c": [[1, 2, 3, 4]]}}, 2) == {"a": [1, 2], "b": {"c": [[1, 2]]}}
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
    if a[0] == "archive":
        d = 3
        if "--days" in a:
            d = int(a[a.index("--days") + 1])
        sys.exit(cmd_archive(a[1] if len(a) > 1 and not a[1].startswith("--") else "out/propline-archive", d))
    print(__doc__)
