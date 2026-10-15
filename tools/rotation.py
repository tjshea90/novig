#!/usr/bin/env python3
"""rotation.py - which of the three Claude accounts does what today (Tj, 2026-10-15: "each Claude account should be used in rotation so that the Claude usage is split evenly. Each Claude account
should have scheduled check-ins on a rotating schedule so as not to interfere with each other").

  * Three SLOTS a day, six hours apart (07:20, 13:20, 19:20 America/New_York), one account per slot, so scheduled runs never overlap. An account keeps its slot (its routine is created once, at that time).
  * The DUTY rotates by the date: ANALYST (reads the digest, finds patterns, may change rules), ENGINEER (works the backlog, ships improvements), WATCH (cheap health check and handoff note).
    duty = DUTIES[(day number + slot) mod 3], so over any three days each account does each duty once and the usage is split evenly.
  * With fewer than three accounts registered, nobody could cover the missing slots' duties, so each registered account's duty is ALL (WATCH, then ANALYST if there is new data, then ENGINEER if there is a backlog).
  * The roster is `roster.json` on the claude-coord branch (the branch tools/coord.py keeps), written by compare-and-swap pushes, so two accounts registering at once get different slots.

  register [--tag TAG]            add this account to the roster (idempotent) and print its slot
  today [--tag TAG] [--date D]    print "<slot> <time> <duty>" for this account today (America/New_York date)
  roster                          print the roster
  selftest                        run the pure checks
Exit status: 0 ok, 2 could not reach GitHub or not registered (callers carry on).
"""
import argparse
import datetime
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

SLOTS = ["07:20", "13:20", "19:20"]
DUTIES = ["ANALYST", "ENGINEER", "WATCH"]
ZONE = "America/New_York"
ROSTER_FILE = "roster.json"


def today_et(now=None):
    try:
        from zoneinfo import ZoneInfo
        n = now or datetime.datetime.now(ZoneInfo(ZONE))
        return n.astimezone(ZoneInfo(ZONE)).date()
    except Exception:
        return (now or datetime.datetime.utcnow()).date()


def duty_for(slot, day, n_accounts=3):
    """The duty of slot `slot` (0-2) on `day` (a datetime.date). Pure."""
    if n_accounts < 3:
        return "ALL"
    return DUTIES[(day.toordinal() + slot) % 3]


def slot_of(tag, roster):
    tags = [a["tag"] for a in roster.get("accounts", [])]
    return tags.index(tag) if tag in tags else None


def cron_for(slot):
    h, m = SLOTS[slot].split(":")
    return "CRON_TZ=%s %d %d * * *" % (ZONE, int(m), int(h))


def selftest():
    d0 = datetime.date(2026, 10, 15)
    # over three consecutive days each slot does each duty exactly once
    for s in range(3):
        got = sorted(duty_for(s, d0 + datetime.timedelta(days=k)) for k in range(3))
        assert got == sorted(DUTIES), (s, got)
    # on any one day the three slots do three different duties
    for k in range(10):
        day = d0 + datetime.timedelta(days=k)
        assert len({duty_for(s, day) for s in range(3)}) == 3
    assert duty_for(0, d0, n_accounts=2) == "ALL" and duty_for(1, d0, n_accounts=1) == "ALL"
    assert slot_of("b", {"accounts": [{"tag": "a"}, {"tag": "b"}]}) == 1 and slot_of("z", {"accounts": []}) is None
    assert cron_for(1) == "CRON_TZ=America/New_York 20 13 * * *"
    assert today_et(datetime.datetime(2026, 10, 16, 3, 0, tzinfo=datetime.timezone.utc)) == datetime.date(2026, 10, 15), "ET date, not UTC"
    print("selftest ok")
    return 0


def my_tag(arg):
    if arg:
        return arg
    import coord
    return coord.identity()["label"]


def read_roster():
    import coord
    coord.sync()
    return coord.load(os.path.join(coord.CLONE, ROSTER_FILE), {"v": 1, "accounts": []})


def register(tag):
    import coord
    for _ in range(4):
        try:
            roster = read_roster()
        except Exception as e:
            print("ROTATION  could not reach the coordination branch (%s)" % e, file=sys.stderr)
            return 2
        s = slot_of(tag, roster)
        if s is not None:
            print("ROTATION  %s is slot %d (%s ET): %s" % (tag, s, SLOTS[s], cron_for(s)))
            return 0
        if len(roster["accounts"]) >= 3:
            print("ROTATION  the roster already holds three accounts: %s" % ", ".join(a["tag"] for a in roster["accounts"]), file=sys.stderr)
            return 2
        roster["accounts"].append({"tag": tag, "registeredAt": coord.iso(coord.now())})
        if coord.push({ROSTER_FILE: json.dumps(roster, indent=1) + "\n"}, "roster: " + tag):
            s = len(roster["accounts"]) - 1
            print("ROTATION  registered %s as slot %d (%s ET): %s" % (tag, s, SLOTS[s], cron_for(s)))
            return 0
    return 2


def main(argv):
    ap = argparse.ArgumentParser(prog="rotation.py")
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("register"); r.add_argument("--tag")
    t = sub.add_parser("today"); t.add_argument("--tag"); t.add_argument("--date")
    sub.add_parser("roster"); sub.add_parser("selftest")
    a = ap.parse_args(argv)
    if a.cmd == "selftest":
        return selftest()
    try:
        if a.cmd == "register":
            return register(my_tag(a.tag))
        roster = read_roster()
        if a.cmd == "roster":
            for i, acc in enumerate(roster.get("accounts", [])):
                print("slot %d %s ET  %s  (since %s)" % (i, SLOTS[i], acc["tag"], acc.get("registeredAt", "?")))
            return 0
        tag = my_tag(a.tag)
        s = slot_of(tag, roster)
        if s is None:
            print("ROTATION  %s is not on the roster: run  python3 -I tools/rotation.py register" % tag)
            return 2
        day = datetime.date.fromisoformat(a.date) if a.date else today_et()
        print("slot %d %s ET duty %s (%s, %d of 3 accounts registered)" % (s, SLOTS[s], duty_for(s, day, len(roster["accounts"])), day, len(roster["accounts"])))
        return 0
    except Exception as e:
        print("ROTATION  tool error (%s)" % e, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
