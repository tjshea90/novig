#!/usr/bin/env python3
"""Sportmicro probe (Tj, 2026-10-18, TCL118b): is Sportmicro's live socket faster/better than ESPN for live scores?

Runs on GitHub Actions only (secret SPORTMICRO_API_KEY, never printed, never written to a file). Three parts, all read-only:
  rest    which REST paths answer for basketball/hockey/american-football (status codes + trimmed bodies, key removed)
  listen  open wss://{sport}.sportmicro.com/live/score?token=KEY for N seconds and log every frame with the receive time
  race    poll ESPN's public scoreboard every 5 s in the same window and report, per game, who saw each score change first
Output: out/sportmicro/{rest.json,frames-<sport>.jsonl,espn.jsonl,REPORT.txt}. Selftest: python3 -I tools/sportmicro_probe.py selftest
"""
import asyncio, json, os, sys, time, urllib.request, urllib.error

SPORTS = {"basketball": "basketball/nba", "hockey": "hockey/nhl", "american-football": "football/nfl"}
REST_PATHS = ["matches-live", "matches", "live", "matches/live"]


def redact(text, key):
    return text.replace(key, "<KEY>") if key else text


def espn_scores(payload):
    """{event id: (home, away, status)} from an ESPN scoreboard answer."""
    out = {}
    for ev in payload.get("events", []):
        comp = (ev.get("competitions") or [{}])[0]
        sides = {c.get("homeAway"): c.get("score") for c in comp.get("competitors", [])}
        out[ev.get("id")] = (sides.get("home"), sides.get("away"), (ev.get("status", {}).get("type", {}) or {}).get("state"))
    return out


def first_seen(changes):
    """changes: [(source, game, score, t)] -> {(game,score): {source: earliest t}}"""
    seen = {}
    for src, game, score, t in changes:
        d = seen.setdefault((game, score), {})
        d[src] = min(t, d.get(src, t))
    return seen


def http_get(url, headers=None, timeout=15):
    req = urllib.request.Request(url, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(200000).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read(2000).decode("utf-8", "replace")
    except Exception as e:  # noqa
        return 0, str(e)[:300]


def rest(key, out):
    res = {}
    for sport in SPORTS:
        for path in REST_PATHS:
            url = "https://%s.sportmicro.com/%s" % (sport, path)
            code, body = http_get(url, {"Authorization": "Bearer " + key})
            res["%s/%s" % (sport, path)] = {"status": code, "body": redact(body, key)[:1500]}
    json.dump(res, open(os.path.join(out, "rest.json"), "w"), indent=1)
    return res


async def listen(key, sport, seconds, out, log):
    import websockets
    url = "wss://%s.sportmicro.com/live/score?token=%s" % (sport, key)
    path = os.path.join(out, "frames-%s.jsonl" % sport)
    end = time.time() + seconds
    n = 0
    with open(path, "w") as f:
        while time.time() < end:
            try:
                async with websockets.connect(url, subprotocols=["updates"], open_timeout=15, ping_interval=20) as ws:
                    log.append("%s: connected" % sport)
                    while time.time() < end:
                        try:
                            msg = await asyncio.wait_for(ws.recv(), timeout=max(1, end - time.time()))
                        except asyncio.TimeoutError:
                            break
                        n += 1
                        f.write(json.dumps({"t": time.time(), "sport": sport, "frame": redact(str(msg), key)[:4000]}) + "\n")
                        f.flush()
            except Exception as e:  # noqa
                log.append("%s: %s" % (sport, redact(repr(e), key)[:200]))
                await asyncio.sleep(5)
    log.append("%s: %d frames in %ds" % (sport, n, seconds))


async def espn_poll(seconds, out, log):
    path = os.path.join(out, "espn.jsonl")
    last, end = {}, time.time() + seconds
    loop = asyncio.get_running_loop()
    with open(path, "w") as f:
        while time.time() < end:
            for sport, league in SPORTS.items():
                code, body = await loop.run_in_executor(None, http_get, "https://site.api.espn.com/apis/site/v2/sports/%s/scoreboard" % league)
                if code != 200:
                    continue
                try:
                    cur = espn_scores(json.loads(body))
                except ValueError:
                    continue
                for gid, sc in cur.items():
                    if last.get((sport, gid)) != sc:
                        f.write(json.dumps({"t": time.time(), "sport": sport, "game": gid, "score": list(sc)}) + "\n")
                        last[(sport, gid)] = sc
            await asyncio.sleep(5)
    log.append("espn: polled %d games" % len(last))


def report(out, log, rest_res):
    lines = ["SPORTMICRO PROBE %s" % time.strftime("%Y-%m-%dT%H:%MZ", time.gmtime()), ""]
    lines += ["REST (status per path):"] + ["  %-34s %s" % (k, v["status"]) for k, v in sorted(rest_res.items())]
    lines += ["", "LOG:"] + ["  " + x for x in log]
    for sport in SPORTS:
        p = os.path.join(out, "frames-%s.jsonl" % sport)
        if os.path.exists(p):
            frames = [json.loads(x) for x in open(p)]
            lines += ["", "%s: %d frames; first 3:" % (sport, len(frames))] + ["  " + f["frame"][:300] for f in frames[:3]]
    espn = os.path.join(out, "espn.jsonl")
    if os.path.exists(espn):
        lines += ["", "ESPN score changes seen: %d (join to frames by hand: Sportmicro frame time vs ESPN change time per game)" % sum(1 for _ in open(espn))]
    open(os.path.join(out, "REPORT.txt"), "w").write("\n".join(lines) + "\n")


async def main(out, seconds):
    key = os.environ.get("SPORTMICRO_API_KEY", "").strip()
    os.makedirs(out, exist_ok=True)
    if not key:
        open(os.path.join(out, "REPORT.txt"), "w").write("No SPORTMICRO_API_KEY secret: add it under Settings > Secrets > Actions, then re-run.\n")
        return
    log = []
    rest_res = await asyncio.get_running_loop().run_in_executor(None, rest, key, out)
    await asyncio.gather(espn_poll(seconds, out, log), *[listen(key, s, seconds, out, log) for s in SPORTS])
    report(out, log, rest_res)


def selftest():
    p = {"events": [{"id": "1", "status": {"type": {"state": "in"}}, "competitions": [{"competitors": [{"homeAway": "home", "score": "10"}, {"homeAway": "away", "score": "7"}]}]}]}
    assert espn_scores(p) == {"1": ("10", "7", "in")}
    assert redact("a KEY b", "KEY") == "a <KEY> b"
    s = first_seen([("espn", "g", "1-0", 5.0), ("sm", "g", "1-0", 3.0), ("espn", "g", "1-0", 9.0)])
    assert s[("g", "1-0")] == {"espn": 5.0, "sm": 3.0}
    print("selftest ok")


if __name__ == "__main__":
    if sys.argv[1:2] == ["selftest"]:
        selftest()
    else:
        asyncio.run(main(sys.argv[1] if len(sys.argv) > 1 else "out/sportmicro", int(sys.argv[2]) if len(sys.argv) > 2 else 600))
