#!/usr/bin/env python3
"""Engine keeper: the GitHub engine runs 08:00 to 01:00 America/New_York every day without GitHub's timer (EA, Tj 2026-10-19).

Tj, 2026-10-19: "Did it start at 8am as scheduled? If not, when did it start and why?" (it started at 11:04 ET, when his phone asked: GitHub ran cloud-scan.yml's 15-minute timer only 3 to 8
times a day, research/engine_start_2026-10-19.md) and then: "I need the engine to automatically run from 8 am Eastern to 1am Eastern every day. The system must work".
So nothing here waits for a cron. The KEEPER (engine-alarm.yml) is a job on a free runner (the repo is public) that is always there: inside the hours it reads the engine's heartbeat every
2 minutes (status.json, beaten every 25 s) and, when something is off, runs the watchdog's decision (tools/engine_watchdog.py): start a missing engine, replace a hung one or one Novig refuses;
outside the hours it sleeps until 08:00 ET and the first look starts the engine. A job may run 6 h, so the keeper hands over to a new keeper after 340 minutes. The engine's gate, its end
step and the watchdog each start a keeper when none runs, and the keeper starts the engine: two chains, each renewing the other. cloud-scan.yml's gate still decides whether an engine runs
(the phone's switch, the hours, a manual Stop, STOP ALL), with the SAME rule as here (`switch`, `hours`). Nothing is started while the dead-man STOP is set.

  python3 -I -u tools/engine_alarm.py keep          the keeper's loop (engine-alarm.yml)
  python3 -I tools/engine_alarm.py ensure           start a keeper if none runs (the gate, the engine's end step, the watchdog)
  python3 -I tools/engine_alarm.py switch FILE      true/false: the phone's switch as the gate reads it (settings.json of cloud-config)
  python3 -I tools/engine_alarm.py hours FILE       open/closed: the engine's hours as the gate reads them
  python3 -I tools/engine_alarm.py plan             what the keeper would do now (reads GitHub, changes nothing)
  python3 -I tools/engine_alarm.py selftest
"""
import datetime
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from zoneinfo import ZoneInfo

ET = ZoneInfo("America/New_York")
OPEN = datetime.time(8, 0)
CLOSE = datetime.time(1, 0)
MAX_WAIT_MS = 340 * 60 * 1000          # a keeper hands over after this (the job's limit is 350 min; GitHub's is 6 h)
LATE_MS = 5 * 1000                      # wake a few seconds after 08:00, so the gate's clock is past it too
CHECK_MS = 120 * 1000                   # a look every 2 minutes inside the hours
STALE_BEAT_MS = 3 * 60 * 1000           # the engine beats every 25 s: 3 minutes of silence is worth a closer look
OUTSIDE_NAP_MS = 10 * 60 * 1000         # outside the hours: re-read the settings every 10 minutes (a manual Run opens them early)
STALL_MS = 10 * 60 * 1000
ADDRESS_CODES = ("RESTRICTED_NETWORK_REGION", "ANONYMIZED_NETWORK")
TRIES = 4
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


def switch_on(cfg, now_ms):
    """The phone's switch as cloud-scan.yml's gate reads it: on, or switched off before an 08:00 ET that has passed (TCL81: an off lasts until the next opening), never after STOP ALL
    (settings.killed) or once the token was removed."""
    cfg = cfg or {}
    on = bool(cfg.get("on", False))
    if not on and int(cfg.get("rev", 0) or 0) > 0 and cfg.get("device") != "token removed" and not cfg.get("settings", {}).get("killed", False):
        t = datetime.datetime.fromtimestamp(int(cfg["rev"]) / 1000, ET)
        day = t.date() + datetime.timedelta(days=1) if t.time() >= OPEN else t.date()
        if now_ms >= int(datetime.datetime(day.year, day.month, day.day, 8, 0, tzinfo=ET).timestamp() * 1000):
            on = True
    return on


def healthy(status, now_ms):
    """Pure. (True, why) when the engine's own status says all is well: a heartbeat in the last 3 minutes, Novig accepting the runner, a scan that moves."""
    st = status or {}
    beat = int(st.get("heartbeatMs") or 0)
    if not beat or now_ms - beat > STALE_BEAT_MS:
        return False, "no heartbeat for %s" % ("ever" if not beat else "%d min" % ((now_ms - beat) // 60000))
    if any(c in str(st.get("novigRoute") or "") for c in ADDRESS_CODES):
        return False, "Novig refuses the runner's address"
    moved = int(st.get("progressChangedAtMs") or 0)
    if st.get("scanning") and moved and now_ms - moved >= STALL_MS:
        return False, "the scan has not moved for %d min" % ((now_ms - moved) // 60000)
    return True, "heartbeat %d s old" % ((now_ms - beat) // 1000)


def step(now_ms, started_ms, cfg, status, stopped=False):
    """Pure: the keeper's next move. -> (action, wait_ms, why): stop | handover | check (run the watchdog's decision, then wait) | sleep."""
    if stopped:
        return "stop", 0, "the dead-man STOP is set: nothing is started"
    left = MAX_WAIT_MS - (now_ms - started_ms)
    if left <= 0:
        return "handover", 0, "340 minutes on this runner: hand over to a new keeper"
    if in_hours(now_ms, cfg):
        if not switch_on(cfg, now_ms):
            return "sleep", min(CHECK_MS, left), "the phone's switch is off (or STOP ALL): nothing to keep"
        ok, why = healthy(status, now_ms)
        return ("sleep" if ok else "check"), min(CHECK_MS, left), why
    wait = min(next_open(now_ms) + LATE_MS - now_ms, OUTSIDE_NAP_MS, left)
    return "sleep", max(wait, 1000), "outside the engine's hours (08:00 ET is %.1f h away)" % ((next_open(now_ms) - now_ms) / 3.6e6)


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


_etags = {}


def read(ref, path):
    """A file's text on a branch (None when it is not there), with an ETag: an unchanged file is a 304, which GitHub does not count against the token's hourly limit."""
    tok = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "tjshea90/novig")
    key = ref + ":" + path
    h = {"Authorization": "Bearer " + (tok or ""), "Accept": "application/vnd.github.raw+json"}
    if key in _etags:
        h["If-None-Match"] = _etags[key][0]
    req = urllib.request.Request("https://api.github.com/repos/%s/contents/%s?ref=%s" % (repo, path, ref), headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode("utf-8", "replace")
            if r.headers.get("ETag"):
                _etags[key] = (r.headers.get("ETag"), text)
            return text
    except urllib.error.HTTPError as e:
        if e.code == 304 and key in _etags:
            return _etags[key][1]
        if e.code == 404:
            _etags.pop(key, None)
            return None
        raise


def read_json(ref, path):
    try:
        return json.loads(read(ref, path) or "{}")
    except ValueError:
        return {}


def dispatch(wf):
    """A workflow_dispatch on main, tried again a few times (a lost dispatch is the one thing that could break a chain)."""
    for i in range(TRIES):
        try:
            api("POST", "actions/workflows/%s/dispatches" % wf, {"ref": "main"})
            return True
        except Exception as e:  # noqa: BLE001 - tried again, then reported
            print("dispatch %s failed (%s), try %d of %d" % (wf, e, i + 1, TRIES), flush=True)
            time.sleep(10 * (i + 1))
    return False


def runs(wf, statuses=("in_progress", "queued")):
    out = []
    for st in statuses:
        out += api("GET", "actions/workflows/%s/runs?status=%s&per_page=10" % (wf, st)).get("workflow_runs", [])
    return out


def stopped():
    """The dead-man STOP (deadman/STOP.json on cloud-config). Unreadable counts as not stopped: the dead-man also disables the workflows themselves."""
    try:
        return read("cloud-config", "deadman/STOP.json") is not None
    except Exception:  # noqa: BLE001
        return False


def ensure():
    """Start a keeper when none runs or waits (cloud-scan's gate and end step, the watchdog)."""
    if stopped():
        print("keeper: the dead-man STOP is set: none is started")
        return 0
    me = int(os.environ.get("GITHUB_RUN_ID", "0") or 0)
    busy = [r for r in runs(ALARM_WF) if r.get("id") != me]
    if busy:
        print("keeper: run %s is %s" % (busy[0].get("id"), busy[0].get("status")))
        return 0
    ok = dispatch(ALARM_WF)
    print("keeper: started" if ok else "keeper: could not be started")
    return 0 if ok else 1


def keep(clock=lambda: int(time.time() * 1000), sleep=time.sleep):
    """The keeper's loop. Every failure to read GitHub only waits for the next look."""
    import engine_watchdog
    started = clock()
    print("keeper: up at %s" % datetime.datetime.now(ET).strftime("%a %H:%M:%S %Z"), flush=True)
    last = ""
    while True:
        now = clock()
        try:
            stop = stopped()
            cfg = read_json("cloud-config", "settings.json")
            status = read_json("cloud-feed", "status.json") if in_hours(now, cfg) else {}
        except Exception as e:  # noqa: BLE001
            print("keeper: GitHub could not be read (%s): looking again in 2 minutes" % e, flush=True)
            sleep(CHECK_MS / 1000)
            continue
        action, wait, why = step(now, started, cfg, status, stop)
        line = "%s: %s" % (action, why)
        if action != "sleep" or line.split(" (")[0] != last.split(" (")[0]:
            print("keeper %s %s" % (datetime.datetime.now(ET).strftime("%H:%M:%S"), line), flush=True)
        last = line
        if action == "stop":
            return 0
        if action == "handover":
            return 0 if dispatch(ALARM_WF) else 1
        if action == "check":
            try:
                engine_watchdog.check(keeper=True)
            except Exception as e:  # noqa: BLE001
                print("keeper: the watchdog's check failed (%s)" % e, flush=True)
        sleep(wait / 1000)


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
    # the phone's switch as the gate reads it (TCL81): an off lasts until the next 08:00 ET, never after STOP ALL or a removed token
    off_9pm = {"on": False, "rev": ms(2026, 10, 19, 21, 0), "device": "phone"}
    assert not switch_on(off_9pm, ms(2026, 10, 20, 7, 59)) and switch_on(off_9pm, ms(2026, 10, 20, 8, 0))
    off_7am = {"on": False, "rev": ms(2026, 10, 20, 7, 0), "device": "phone"}
    assert switch_on(off_7am, ms(2026, 10, 20, 8, 0))                                   # written before 08:00: resumes that same morning
    assert not switch_on(dict(off_9pm, settings={"killed": True}), ms(2026, 10, 21, 9, 0))   # STOP ALL holds
    assert not switch_on(dict(off_9pm, device="token removed"), ms(2026, 10, 21, 9, 0))
    assert switch_on({"on": True}, 0) and not switch_on({}, ms(2026, 10, 20, 9, 0))
    # the engine's own status
    beat = ms(2026, 10, 19, 12, 0)
    assert healthy({"heartbeatMs": beat - 30_000}, beat)[0]
    assert not healthy({"heartbeatMs": beat - 4 * 60_000}, beat)[0] and not healthy({}, beat)[0]
    assert not healthy({"heartbeatMs": beat, "novigRoute": "refused (RESTRICTED_NETWORK_REGION)"}, beat)[0]
    assert not healthy({"heartbeatMs": beat, "scanning": True, "progressChangedAtMs": beat - 11 * 60_000}, beat)[0]
    assert healthy({"heartbeatMs": beat, "scanning": True, "progressChangedAtMs": beat - 60_000}, beat)[0]
    # the keeper's moves: a healthy engine is left alone, a silent one is checked, the night is slept through to 08:00:05, the job hands over at 340 min, STOP stops
    on = {"on": True}
    noon = ms(2026, 10, 19, 12, 0)
    assert step(noon, noon, on, {"heartbeatMs": noon - 20_000})[:2] == ("sleep", CHECK_MS)
    assert step(noon, noon, on, {"heartbeatMs": noon - 30 * 60_000})[0] == "check"
    assert step(noon, noon, on, {})[0] == "check"
    night = ms(2026, 10, 20, 2, 0)
    assert step(night, night, on, {}) == ("sleep", OUTSIDE_NAP_MS, step(night, night, on, {})[2])
    late = ms(2026, 10, 20, 7, 55)
    assert step(late, late - 60_000, on, {})[:2] == ("sleep", 5 * 60_000 + LATE_MS)              # wakes at 08:00:05 ...
    assert step(ms(2026, 10, 20, 8, 0) + LATE_MS, late, on, {})[0] == "check"                   # ... and the first look starts the engine
    assert step(noon, noon - MAX_WAIT_MS, on, {})[0] == "handover"
    assert step(noon, noon - MAX_WAIT_MS + 30_000, on, {"heartbeatMs": noon})[1] == 30_000           # never sleeps past its hand-over
    assert step(noon, noon, on, {}, stopped=True)[0] == "stop"
    assert step(noon, noon, {"on": True, "manualStopUntilMs": noon + 3_600_000}, {})[0] == "sleep"    # a manual Stop: nothing is started
    assert step(noon, noon, {"on": False, "rev": noon - 60_000}, {})[0] == "sleep"                     # switched off this morning: nothing to keep
    assert step(noon, noon, {"on": True, "settings": {"killed": True}}, {})[0] == "check"              # on + killed: the gate runs it and the engine stands down itself
    print("engine_alarm selftest ok")
    return 0


if __name__ == "__main__":
    c = sys.argv[1:] or ["keep"]
    if c[0] == "selftest":
        sys.exit(selftest())
    if c[0] in ("switch", "hours"):
        try:
            cfg = json.load(open(c[1])) if len(c) > 1 else {}
        except Exception:  # noqa: BLE001 - the gate's own fallback: no settings = off / hours apply
            cfg = {}
        now = int(time.time() * 1000)
        print(str(switch_on(cfg, now)).lower() if c[0] == "switch" else ("open" if in_hours(now, cfg) else "closed"))
        sys.exit(0)
    if c[0] == "ensure":
        sys.exit(ensure())
    if c[0] == "plan":
        now = int(time.time() * 1000)
        cfg = read_json("cloud-config", "settings.json")
        print(*step(now, now, cfg, read_json("cloud-feed", "status.json"), stopped()))
        print("switch:", switch_on(cfg, now), "hours:", "open" if in_hours(now, cfg) else "closed")
        sys.exit(0)
    sys.exit(keep())
