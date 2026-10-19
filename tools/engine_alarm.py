#!/usr/bin/env python3
"""Engine alarm: wakes the GitHub engine at 08:00 America/New_York without GitHub's timer (EA, Tj 2026-10-19).

Tj, 2026-10-19: "Figure out when the GitHub engine started today. Did it start at 8am as scheduled? If not, when did it start and why?" It started at 11:04 ET, when his phone asked:
GitHub ran cloud-scan.yml's 15-minute timer only 3 to 8 times a day (research/engine_start_2026-10-19.md), and nothing else starts the morning engine. A dispatched run is delivered; a
timer tick is best effort. So this waits on a free runner (the repo is public) until the next 08:00 ET and then dispatches cloud-scan.yml, whose gate still decides everything (the phone's
switch, the hours, a manual stop, the kill switch). A job may run at most 6 h, so a longer wait ends by dispatching this workflow again. It never starts anything while the dead-man STOP is set.

  python3 -I -u tools/engine_alarm.py run        (engine-alarm.yml)
  python3 -I tools/engine_alarm.py plan          what a run would do now (reads nothing)
  python3 -I tools/engine_alarm.py selftest
"""
import datetime
import json
import os
import subprocess
import sys
import time
import urllib.request
from zoneinfo import ZoneInfo

ET = ZoneInfo("America/New_York")
OPEN = datetime.time(8, 0)
CLOSE = datetime.time(1, 0)
MAX_WAIT_MS = 340 * 60 * 1000          # the job's limit is 350 min (6 h at most on GitHub)
LATE_MS = 5 * 1000                      # wake a few seconds after 08:00, so the gate's clock is past it too
ENGINE_WF = "cloud-scan.yml"
ALARM_WF = "engine-alarm.yml"


def next_open(now_ms):
    """The next 08:00 America/New_York strictly after now (DST included)."""
    t = datetime.datetime.fromtimestamp(now_ms / 1000, ET)
    day = t.date() if t.time() < OPEN else t.date() + datetime.timedelta(days=1)
    return int(datetime.datetime(day.year, day.month, day.day, 8, 0, tzinfo=ET).timestamp() * 1000)


def in_hours(now_ms, cfg=None):
    """The engine's hours as cloud-scan.yml's gate reads them: 08:00 to 01:00 ET, unless the phone turned the window off (gates.cloudHours), a manual Run opened it or a manual Stop holds it."""
    cfg = cfg or {}
    if now_ms < int(cfg.get("manualStopUntilMs", 0) or 0):
        return False
    if not bool(cfg.get("settings", {}).get("gates", {}).get("cloudHours", True)):
        return True
    if now_ms < int(cfg.get("manualRunUntilMs", 0) or 0):
        return True
    t = datetime.datetime.fromtimestamp(now_ms / 1000, ET).time()
    return t >= OPEN or t < CLOSE


def plan(now_ms, stopped=False):
    """Pure. -> (action, wait_ms, reason): 'stop' (the dead-man STOP), 'rearm' (wait the job's most, then dispatch this workflow again) or 'start' (wait, then dispatch the engine)."""
    if stopped:
        return "stop", 0, "the dead-man STOP is set: nothing is started"
    target = next_open(now_ms) + LATE_MS
    wait = target - now_ms
    when = datetime.datetime.fromtimestamp(target / 1000, ET).strftime("%a %H:%M:%S %Z")
    if wait > MAX_WAIT_MS:
        return "rearm", MAX_WAIT_MS, "08:00 ET is %.1f h away (%s): wait %d min, then hand over to a new alarm" % (wait / 3.6e6, when, MAX_WAIT_MS // 60000)
    return "start", wait, "wait %d min until %s, then start the engine" % (wait // 60000, when)


# ---- GitHub ------------------------------------------------------------------------------------------------------------------------------------------------------------------------

def api(method, path, body=None):
    tok = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "tjshea90/novig")
    req = urllib.request.Request("https://api.github.com/repos/%s/%s" % (repo, path), method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": "Bearer " + (tok or ""), "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        t = r.read()
        return json.loads(t) if t else {}


def dispatch(wf):
    api("POST", "actions/workflows/%s/dispatches" % wf, {"ref": "main"})


def runs(wf, statuses=("in_progress", "queued")):
    out = []
    for st in statuses:
        out += api("GET", "actions/workflows/%s/runs?status=%s&per_page=10" % (wf, st)).get("workflow_runs", [])
    return out


def stopped():
    """The dead-man STOP (deadman/STOP.json on cloud-config). An unreadable branch counts as not stopped: the engine's gate and the dead-man's disabled workflows still hold."""
    subprocess.run(["git", "fetch", "-q", "--depth=1", "origin", "cloud-config"], capture_output=True)
    return subprocess.run(["git", "cat-file", "-e", "FETCH_HEAD:deadman/STOP.json"], capture_output=True).returncode == 0


def run():
    action, wait, why = plan(int(time.time() * 1000), stopped())
    print("alarm:", action, "-", why, flush=True)
    if action == "stop":
        return 0
    time.sleep(wait / 1000)
    if stopped():
        print("alarm: the dead-man STOP was set while waiting: nothing is started")
        return 0
    if action == "rearm":
        dispatch(ALARM_WF)
        print("alarm: handed over to a new alarm")
        return 0
    me = int(os.environ.get("GITHUB_RUN_ID", "0") or 0)
    busy = [r for r in runs(ENGINE_WF) if r.get("id") != me]
    if busy:
        print("alarm: an engine run is already %s (run %s): nothing to start" % (busy[0].get("status"), busy[0].get("id")))
        return 0
    dispatch(ENGINE_WF)
    print("alarm: engine dispatched at %s (its gate decides: the phone's switch, the hours, a manual stop)" % datetime.datetime.now(ET).strftime("%H:%M:%S %Z"))
    return 0


def selftest():
    def ms(y, mo, d, h, mi, tz=ET):
        return int(datetime.datetime(y, mo, d, h, mi, tzinfo=tz).timestamp() * 1000)
    utc = datetime.timezone.utc
    # 01:00 EDT (the engine just closed): 7 h to 08:00, so the first alarm hands over after 340 min
    a, w, _ = plan(ms(2026, 10, 19, 5, 0, utc))
    assert a == "rearm" and w == MAX_WAIT_MS, (a, w)
    # the handed-over alarm at 06:40 EDT waits 80 min and starts the engine at 08:00:05
    a, w, _ = plan(ms(2026, 10, 19, 6, 40))
    assert a == "start" and w == 80 * 60000 + LATE_MS, (a, w)
    # an alarm made during the day (the phone switched the engine off at 15:00) aims at TOMORROW 08:00
    assert next_open(ms(2026, 10, 19, 15, 0)) == ms(2026, 10, 20, 8, 0)
    assert plan(ms(2026, 10, 19, 15, 0))[0] == "rearm"
    # exactly 08:00 counts as passed: the next opening is tomorrow (the engine's own run covers today)
    assert next_open(ms(2026, 10, 19, 8, 0)) == ms(2026, 10, 20, 8, 0)
    # 00:30 ET (still inside the hours): the next opening is the same calendar day
    assert next_open(ms(2026, 10, 20, 0, 30)) == ms(2026, 10, 20, 8, 0)
    # the clocks go back on Sunday 2026-11-01: 08:00 EST is 13:00 UTC
    assert next_open(ms(2026, 11, 1, 6, 0, utc)) == ms(2026, 11, 1, 13, 0, utc)
    # and forward on 2027-03-14: 08:00 EDT is 12:00 UTC
    assert next_open(ms(2027, 3, 14, 7, 0, utc)) == ms(2027, 3, 14, 12, 0, utc)
    # the dead-man STOP starts nothing
    assert plan(ms(2026, 10, 19, 7, 59), stopped=True)[0] == "stop"
    # the hours, as the gate reads them
    assert in_hours(ms(2026, 10, 19, 8, 0)) and in_hours(ms(2026, 10, 20, 0, 59)) and not in_hours(ms(2026, 10, 20, 1, 0)) and not in_hours(ms(2026, 10, 19, 7, 59))
    assert in_hours(ms(2026, 10, 19, 3, 0), {"settings": {"gates": {"cloudHours": False}}})
    assert in_hours(ms(2026, 10, 19, 3, 0), {"manualRunUntilMs": ms(2026, 10, 19, 4, 0)})
    assert not in_hours(ms(2026, 10, 19, 12, 0), {"manualStopUntilMs": ms(2026, 10, 20, 8, 0)})
    print("engine_alarm selftest ok")
    return 0


if __name__ == "__main__":
    c = sys.argv[1] if len(sys.argv) > 1 else "run"
    if c == "selftest":
        sys.exit(selftest())
    if c == "plan":
        print(*plan(int(time.time() * 1000)))
        sys.exit(0)
    sys.exit(run())
