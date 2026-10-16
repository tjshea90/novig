#!/usr/bin/env python3
"""Engine watchdog (TCL53; Tj 2026-10-16: "if this ever happens again, try to diagnose and fix it so it can auto resume").

  python3 tools/engine_watchdog.py check     (the 10-minute workflow, no Claude usage)
  python3 tools/engine_watchdog.py selftest

The engine job can hang (run 61, 2026-10-16: it logged "ending" and then sat in the Engine step, so the hand-over never started a replacement and the 15-minute schedule's gate saw an engine
"running"). This looks every 10 minutes: an engine run in progress for 12+ minutes whose heartbeat (status.json on cloud-feed) is 12+ minutes old, while the phone's switch is on and the
dead-man STOP is not set, is HUNG: the run is cancelled and a new one dispatched (unless one is already queued or was made in the last 10 minutes). It tells Tj in the app's messages. It never
touches money: a cancelled engine's resting bids are re-adopted by the next run from desk.json, and the engine's own clean-up runs on the next job's first pass.
"""
import json, os, subprocess, sys, time, urllib.request, calendar

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
STALE_MS = 12 * 60 * 1000
YOUNG_MS = 12 * 60 * 1000
RECENT_MS = 10 * 60 * 1000
WF = "cloud-scan.yml"


def decide(now, runs, heartbeat_ms, idle, switch_on, stopped):
    """Pure. runs: list of {id, status, created_ms, started_ms}. -> (action, run id to cancel or None, reason)."""
    if stopped:
        return "none", None, "dead-man STOP is set"
    if not switch_on:
        return "none", None, "the phone's switch is off"
    live = [r for r in runs if r["status"] == "in_progress"]
    queued = [r for r in runs if r["status"] in ("queued", "pending", "waiting")]
    if not live:
        return "none", None, "no engine run in progress (the 15-minute schedule starts one)"
    cur = max(live, key=lambda r: r["started_ms"])
    if now - cur["started_ms"] < YOUNG_MS:
        return "none", None, "the run is young"
    if idle:
        return "none", None, "the engine is idle on purpose"
    if heartbeat_ms and now - heartbeat_ms < STALE_MS:
        return "none", None, "the heartbeat is fresh"
    age = "none" if not heartbeat_ms else "%d min" % ((now - heartbeat_ms) // 60000)
    if queued or any(now - r["created_ms"] < RECENT_MS for r in runs if r["id"] != cur["id"]):
        return "cancel", cur["id"], "heartbeat %s old; a replacement is already queued" % age
    return "restart", cur["id"], "heartbeat %s old in a run that started %d min ago" % (age, (now - cur["started_ms"]) // 60000)


def _ts(s):
    return calendar.timegm(time.strptime(s, "%Y-%m-%dT%H:%M:%SZ")) * 1000


def api(method, path, body=None):
    tok = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "tjshea90/novig")
    req = urllib.request.Request("https://api.github.com/repos/%s/%s" % (repo, path), method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": "Bearer " + tok, "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        t = r.read()
        return json.loads(t) if t else {}


def git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True)


def show(branch, path):
    git("fetch", "-q", "--depth=1", "origin", branch)
    r = git("show", "FETCH_HEAD:" + path)
    return r.stdout if r.returncode == 0 else None


def check():
    now = int(time.time() * 1000)
    runs = []
    for st in ("in_progress", "queued"):
        for r in api("GET", "actions/workflows/%s/runs?status=%s&per_page=10" % (WF, st)).get("workflow_runs", []):
            runs.append({"id": r["id"], "status": r["status"], "created_ms": _ts(r["created_at"]), "started_ms": _ts(r.get("run_started_at") or r["created_at"])})
    try:
        stat = json.loads(show("cloud-feed", "status.json") or "{}")
    except ValueError:
        stat = {}
    try:
        cfg = json.loads(show("cloud-config", "settings.json") or "{}")
    except ValueError:
        cfg = {}
    stopped = show("cloud-config", "deadman/STOP.json") is not None
    action, rid, why = decide(now, runs, stat.get("heartbeatMs"), bool(stat.get("idle")), bool(cfg.get("on")), stopped)
    print("watchdog:", action, why)
    if action in ("cancel", "restart"):
        api("POST", "actions/runs/%d/cancel" % rid)
        if action == "restart":
            time.sleep(8)
            api("POST", "actions/workflows/%s/dispatches" % WF, {"ref": "main"})
        try:
            import tell_tj
            tell_tj.say("The engine was stuck: restarted", "GitHub engine run %d had gone quiet (%s). It was cancelled and a fresh run started by the watchdog; nothing for you to do." % (rid, why), kind="alert", priority=0, who="claude:watchdog")
        except Exception as e:
            print("could not tell Tj:", e)
    return 0


def selftest():
    M = 60000
    now = 10_000_000 * M
    def run(i, st, started_ago, created_ago=None):
        return {"id": i, "status": st, "started_ms": now - started_ago * M, "created_ms": now - (created_ago if created_ago is not None else started_ago) * M}
    ok = dict(switch_on=True, stopped=False, idle=False)
    assert decide(now, [run(1, "in_progress", 60)], now - 2 * M, **ok)[0] == "none"                 # fresh heartbeat
    assert decide(now, [run(1, "in_progress", 5)], now - 30 * M, **ok)[0] == "none"                 # young run
    a = decide(now, [run(1, "in_progress", 60)], now - 15 * M, **ok); assert a[:2] == ("restart", 1)   # hung
    assert decide(now, [run(1, "in_progress", 60)], None, **ok)[0] == "restart"                     # never heartbeated
    assert decide(now, [run(1, "in_progress", 60), run(2, "queued", 3)], now - 15 * M, **ok)[0] == "cancel"
    assert decide(now, [run(1, "in_progress", 60)], now - 15 * M, switch_on=False, stopped=False, idle=False)[0] == "none"
    assert decide(now, [run(1, "in_progress", 60)], now - 15 * M, switch_on=True, stopped=True, idle=False)[0] == "none"
    assert decide(now, [run(1, "in_progress", 60)], now - 15 * M, switch_on=True, stopped=False, idle=True)[0] == "none"
    assert decide(now, [], now - 99 * M, **ok)[0] == "none"
    print("engine_watchdog selftest ok")
    return 0


if __name__ == "__main__":
    c = sys.argv[1] if len(sys.argv) > 1 else "check"
    sys.exit(selftest() if c == "selftest" else check())
