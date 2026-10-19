#!/usr/bin/env python3
"""Engine watchdog (TCL53; Tj 2026-10-16: "if this ever happens again, try to diagnose and fix it so it can auto resume").

  python3 tools/engine_watchdog.py check     (the 5-minute workflow, no Claude usage; GitHub runs it only a few times a day, so it is a backup, not the clock: engine-alarm.yml is)
  python3 tools/engine_watchdog.py dry       (decide and print with today's real state, change nothing)
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
# Novig refusing the runner's ADDRESS (RESTRICTED_NETWORK_REGION / ANONYMIZED_NETWORK): the engine's own hand-over missed this on 2026-10-17 (refused from 06:23Z, 5+ minutes, never handed over). Outside check:
ADDRESS_CODES = ("RESTRICTED_NETWORK_REGION", "ANONYMIZED_NETWORK")
ADDRESS_AFTER_MS = 30 * 1000           # Tj, 2026-10-18 (TCL115): replace a refused runner at once; the in-job hand-over does it in seconds, this is the outside backup
SWAP_WINDOW_MS = 30 * 60 * 1000
STALL_MS = 10 * 60 * 1000
MAX_SWAPS = 6                           # swaps in the window before the watchdog waits (each new run re-reads the fair-odds feeds: it costs API requests)


def decide(now, runs, heartbeat_ms, idle, switch_on, stopped, route="", status_run=None, recent_runs=(), progress_changed_ms=0, scanning=False, hours_open=True):
    """Pure. runs: list of {id, status, created_ms, started_ms}. -> (action, run id to cancel or None, reason). Actions: none, cancel, restart, start (dispatch an engine)."""
    if stopped:
        return "none", None, "dead-man STOP is set"
    live = [r for r in runs if r["status"] == "in_progress"]
    queued = [r for r in runs if r["status"] in ("queued", "pending", "waiting")]
    if not live:
        # No engine: GitHub's 15-minute timer comes only a few times a day (EA, 2026-10-19), so a missing engine is started here (the keeper runs this every 2 minutes).
        if queued or any(now - r["created_ms"] < RECENT_MS for r in recent_runs):
            return "none", None, "no engine running, but one was started in the last 10 minutes or is queued"
        if not hours_open:
            return "none", None, "outside the engine's hours (the keeper starts it at 08:00 ET)"
        if not switch_on:
            return "none", None, "the phone's switch is off"
        return "start", None, "no engine run inside the engine's hours"
    if not switch_on:
        return "none", None, "the phone's switch is off"
    cur = max(live, key=lambda r: r["started_ms"])
    if idle:
        return "none", None, "the engine is idle on purpose"
    if (route and any(c in route for c in ADDRESS_CODES) and now - cur["started_ms"] >= ADDRESS_AFTER_MS
            and (status_run is None or str(status_run) == str(cur["id"])) and heartbeat_ms and now - heartbeat_ms < STALE_MS):
        # no swap cap for an address refusal (TCL115): every refused runner is replaced until one is accepted
        if queued or any(now - r["created_ms"] < 60 * 1000 for r in runs if r["id"] != cur["id"]):
            return "cancel", cur["id"], "Novig refuses this runner's address; a replacement is already queued"
        return "restart", cur["id"], "Novig refuses this runner's address (%d min into the run)" % ((now - cur["started_ms"]) // 60000)
    # A scan whose progress has not moved for 10 minutes while the engine still heartbeats is STALLED (Tj, 2026-10-17: "Claude should be able to catch slow scanning or stalled scanning and fix it").
    if (scanning and progress_changed_ms and now - progress_changed_ms >= STALL_MS and now - cur["started_ms"] >= 5 * 60 * 1000
            and (status_run is None or str(status_run) == str(cur["id"]))):
        swaps = len([r for r in recent_runs if now - r["created_ms"] < SWAP_WINDOW_MS])
        if swaps > MAX_SWAPS:
            return "none", None, "the scan is stalled but %d runs were started in 30 minutes: waiting" % swaps
        if queued:
            return "cancel", cur["id"], "the scan's progress has not moved for %d min; a replacement is already queued" % ((now - progress_changed_ms) // 60000)
        return "restart", cur["id"], "the scan's progress has not moved for %d min" % ((now - progress_changed_ms) // 60000)
    if now - cur["started_ms"] < YOUNG_MS:
        return "none", None, "the run is young"
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
                                 headers=dict({"Accept": "application/vnd.github+json"}, **({"Authorization": "Bearer " + tok} if tok else {})))
    with urllib.request.urlopen(req, timeout=30) as r:
        t = r.read()
        return json.loads(t) if t else {}


def git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True)


class Unreadable(Exception):
    """A branch could not be fetched: what it holds is not known (never the same as "not there")."""


def show(branch, path):
    """A file on a branch, None when the branch has no such file. Raises Unreadable when the branch could not be fetched: on 2026-10-19 (GitHub degraded) a failed fetch left
    FETCH_HEAD on another branch, the status read as "no heartbeat", and a healthy engine was cancelled and started again."""
    f = git("fetch", "-q", "--depth=1", "origin", "+refs/heads/%s:refs/remotes/origin/%s" % (branch, branch))
    if f.returncode != 0:
        raise Unreadable("%s: %s" % (branch, (f.stderr or "").strip()[:160]))
    r = git("show", "refs/remotes/origin/%s:%s" % (branch, path))
    return r.stdout if r.returncode == 0 else None


def check(keeper=False, dry=False):
    """One look. keeper: called by the keeper itself (no keeper to start). dry: decide and print, change nothing."""
    now = int(time.time() * 1000)
    runs = []
    for st in ("in_progress", "queued"):
        for r in api("GET", "actions/workflows/%s/runs?status=%s&per_page=10" % (WF, st)).get("workflow_runs", []):
            runs.append({"id": r["id"], "status": r["status"], "created_ms": _ts(r["created_at"]), "started_ms": _ts(r.get("run_started_at") or r["created_at"])})
    # What cannot be read is not known: no engine is started, cancelled or replaced on a guess (the next look reads again).
    try:
        stat_raw = show("cloud-feed", "status.json")
        cfg_raw = show("cloud-config", "settings.json")
        stopped = show("cloud-config", "deadman/STOP.json") is not None
    except Unreadable as e:
        print("watchdog: none: GitHub could not be read (%s); nothing is changed until it can" % e, flush=True)
        return 0
    try:
        stat = json.loads(stat_raw or "{}")
    except ValueError:
        stat = {}
    try:
        cfg = json.loads(cfg_raw or "{}")
    except ValueError:
        cfg = {}
    recent = [{"id": r["id"], "created_ms": _ts(r["created_at"])} for r in api("GET", "actions/workflows/%s/runs?per_page=20" % WF).get("workflow_runs", [])]
    import engine_alarm
    # The switch and the hours exactly as cloud-scan.yml's gate reads them (one rule: tools/engine_alarm.py).
    action, rid, why = decide(now, runs, stat.get("heartbeatMs"), bool(stat.get("idle")), engine_alarm.switch_on(cfg, now), stopped, stat.get("novigRoute") or "", stat.get("runId"), recent,
                              int(stat.get("progressChangedAtMs") or 0), bool(stat.get("scanning")), engine_alarm.in_hours(now, cfg))
    print("watchdog:", action, why, flush=True)
    if dry:
        return 0
    if not keeper:
        engine_alarm.ensure()   # the keeper must always be there (the watchdog's own timer is one of GitHub's unreliable ones)
    if action == "start":
        engine_alarm.dispatch(WF)
        return 0
    if action in ("cancel", "restart"):
        api("POST", "actions/runs/%d/cancel" % rid)
        if action == "restart":
            time.sleep(8)
            engine_alarm.dispatch(WF)
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
    # no engine at all (EA, 2026-10-19: GitHub's timer came 3-8 times a day): start one inside the hours, arm the 08:00 alarm outside them
    assert decide(now, [], now - 99 * M, **ok)[0] == "start"
    assert decide(now, [], now - 99 * M, recent_runs=[{"id": 9, "created_ms": now - 3 * M}], **ok)[0] == "none"          # one was just dispatched
    assert decide(now, [run(9, "queued", 1)], now - 99 * M, **ok)[0] == "none"                                         # one is queued
    assert decide(now, [], now - 99 * M, switch_on=False, stopped=False, idle=False)[0] == "none"                      # the phone switched it off
    assert decide(now, [], now - 99 * M, hours_open=False, **ok)[0] == "none"                                         # outside the hours: the keeper's 08:00
    assert decide(now, [], now - 99 * M, switch_on=True, stopped=True, idle=False)[0] == "none"                         # dead-man STOP
    refused = "public routes: the read key was refused (Novig places this network's internet address outside the states it serves (RESTRICTED_NETWORK_REGION))"
    fresh = now - 10_000
    a = decide(now, [run(7, "in_progress", 6)], fresh, route=refused, status_run=7, **ok); assert a[:2] == ("restart", 7), a        # refused 6 minutes: swap
    assert decide(now, [run(7, "in_progress", 0.2)], fresh, route=refused, status_run=7, **ok)[0] == "none"                      # first seconds
    assert decide(now, [run(7, "in_progress", 6)], fresh, route=refused, status_run=6, **ok)[0] == "none"                          # the status is another run's
    assert decide(now, [run(7, "in_progress", 6)], fresh, route="Novig read-only key active", status_run=7, **ok)[0] == "none"    # accepted
    spin = [{"id": 100 + i, "created_ms": now - i * 2 * M} for i in range(8)]
    assert decide(now, [run(7, "in_progress", 6)], fresh, route=refused, status_run=7, recent_runs=spin, **ok)[0] == "restart"   # no swap cap for a refused address
    assert decide(now, [run(7, "in_progress", 6), run(8, "queued", 1)], fresh, route=refused, status_run=7, **ok)[0] == "cancel"
    st = dict(route="", status_run=7, scanning=True, **ok)
    assert decide(now, [run(7, "in_progress", 20)], fresh, progress_changed_ms=now - 11 * M, **st)[0] == "restart"      # alive but frozen 11 minutes
    assert decide(now, [run(7, "in_progress", 20)], fresh, progress_changed_ms=now - 4 * M, **st)[0] == "none"         # moving
    assert decide(now, [run(7, "in_progress", 20)], fresh, progress_changed_ms=0, **st)[0] == "none"                    # an older engine: no field
    assert decide(now, [run(7, "in_progress", 3)], fresh, progress_changed_ms=now - 11 * M, **st)[0] == "none"          # a young run
    print("engine_watchdog selftest ok")
    return 0


if __name__ == "__main__":
    c = sys.argv[1] if len(sys.argv) > 1 else "check"
    sys.exit(selftest() if c == "selftest" else check(dry=(c == "dry")))
