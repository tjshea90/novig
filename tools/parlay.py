#!/usr/bin/env python3
"""ParlayAPI Pro helper (CH47, Tj 2026-10-20: "I upgraded one of my parlayapi keys to pro ... find out which one is the pro tier and use it to its full potential").

Runs on GitHub (.github/workflows/parlay.yml) with the PARLAY_API_KEY secret (several keys, comma separated); a session never sees a key. Tasks:
  plans    each key's tier and credits from the free GET /v1/usage (the key is shown only as "#N", never in clear)
  probe    one call to every Pro/live/arb endpoint with the biggest key; the answers (keyless, trimmed) are written to a directory the workflow puts in the `parlay-samples` Release
           so the app's parsers are checked against REAL shapes (SSE play-by-play frames included: a 20 s listen on the live PBP stream)
Costs of a probe (docs): arbitrage 10 a sport, inplay/arbs 5, live 3, live/points 1, book_latency 5, source-health 1, period_markets 2, sse 5: about 60 credits.
"""
import json
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


def selftest():
    assert keys_from_env({"PARLAY_API_KEY": "a, b;c\nd"}) == ["a", "b", "c", "d"]
    assert keys_from_env({}) == []
    assert trim({"a": [1, 2, 3, 4, 5]}) == {"a": [1, 2, 3, "... 2 more"]}
    assert rank({"total": 100000}) > rank({"total": 20000}) > rank({})
    print("parlay.py selftest ok")
    return 0


if __name__ == "__main__":
    c = sys.argv[1] if len(sys.argv) > 1 else "plans"
    if c == "selftest":
        sys.exit(selftest())
    if c == "plans":
        sys.exit(cmd_plans())
    if c == "probe":
        sys.exit(cmd_probe(sys.argv[2] if len(sys.argv) > 2 else "out/parlay"))
    print("usage: parlay.py plans | probe [dir] | selftest")
    sys.exit(2)
