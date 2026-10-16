#!/usr/bin/env python3
"""Rotation watch (TCL54b; Tj 2026-10-16, "yes"): tell Tj in the app when a scheduled Claude slot left no trace.

  python3 tools/rotation_watch.py check      (hourly workflow, no Claude usage)
  python3 tools/rotation_watch.py selftest

`rotation.py missed` already knows the slots of the last 30 h whose account left no check-in on the board (NO-SHOW: usage cap, no repository attached, deleted/disabled routine) or checked in and
never finished (INTERRUPTED). This reads its lines and sends ONE priority-1 message per (day, slot, state) to the phone, remembered as rotation/alerts/<day>-slot<N>-<STATE>.json on cloud-config.
AWAY (Tj said the account is out of usage) and PAUSED (Tj chatted) are expected and never alerted. Silent while the dead-man STOP is set.
"""
import json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
LINE = re.compile(r"^ROTATION\s+(NO-SHOW|INTERRUPTED)\s+slot\s+(\d+)\s+\((\d\d:\d\d) ET on (\d{4}-\d\d-\d\d), account ([\w-]+)\)")
WHY = {
    "NO-SHOW": "left no check-in within 5 h of its time: out of usage, the routine has no repository attached, or it was deleted or switched off. Open claude.ai/code/routines in that account (research/coordination/SETUP_FOR_TJ.md).",
    "INTERRUPTED": "checked in but never posted its finished note: it was cut off (usage cap) mid-task. The next account covers it.",
}


def parse(text):
    """-> [(day, slot, state, hhmm, account)] from `rotation.py missed` output."""
    out = []
    for ln in text.splitlines():
        m = LINE.match(ln.strip())
        if m:
            out.append((m.group(4), int(m.group(2)), m.group(1), m.group(3), m.group(5)))
    return out


def check():
    import tell_tj
    tell_tj.git("fetch", "-q", "origin", tell_tj.BRANCH, check=False)
    have = tell_tj.git("ls-tree", "-r", "--name-only", "FETCH_HEAD", check=False).stdout.split()
    if "deadman/STOP.json" in have:
        print("rotation_watch: dead-man STOP is set; silent")
        return 0
    r = subprocess.run([sys.executable, "-I", os.path.join(HERE, "rotation.py"), "missed"], capture_output=True, text=True, timeout=120)
    print((r.stdout + r.stderr).strip()[:1500])
    sent = 0
    for day, slot, state, hhmm, acct in parse(r.stdout):
        key = "rotation/alerts/%s-slot%d-%s.json" % (day, slot, state)
        if key in have:
            continue
        tell_tj.say("A scheduled Claude session did not run (%s ET slot)" % hhmm,
                    "%s on %s: account %s %s" % (state, day, acct, WHY[state]), kind="alert", priority=1, who="claude:rotation-watch")
        tell_tj.put(key, json.dumps({"day": day, "slot": slot, "state": state, "account": acct}))
        sent += 1
    print("rotation_watch: %d alert(s) sent" % sent)
    return 0


def selftest():
    t = ("ROTATION  AWAY slot 0 (07:20 ET on 2026-10-16, account acct-7542bf); duty then: ANALYST; cover with: x\n"
         "ROTATION  NO-SHOW slot 1 (13:20 ET on 2026-10-16, account acct-27a37e); duty then: ENGINEER; cover with: WATCH checks only\n"
         "ROTATION  INTERRUPTED slot 2 (19:20 ET on 2026-10-15, account acct-5d1e2d); duty then: WATCH; cover with: x\n"
         "ROTATION  PAUSED slot 1 (13:20 ET on 2026-10-15, account acct-27a37e); duty then: x; cover with: y\n"
         "ROTATION  cover at most 2 (the first with the whole duty)\n")
    assert parse(t) == [("2026-10-16", 1, "NO-SHOW", "13:20", "acct-27a37e"), ("2026-10-15", 2, "INTERRUPTED", "19:20", "acct-5d1e2d")], parse(t)
    assert parse("ROTATION  no missed slots") == []
    print("rotation_watch selftest ok")
    return 0


if __name__ == "__main__":
    sys.exit(selftest() if (sys.argv[1:] or ["check"])[0] == "selftest" else check())
