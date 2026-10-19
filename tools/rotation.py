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
  cron [--tag TAG]                print the cron line this account's routine must have (the routine checks and repairs itself with it)
  checkin                         post a one-line "I ran" note on the board (every routine run does this FIRST, even when it then stops because the lease is held)
  missed                          list scheduled slots of the last 30 h whose account left no trace on the board (NO-SHOW: usage cap, no repo, dead routine) or checked in but never posted its finished-duty note (INTERRUPTED), and that nobody covered yet
  cover SLOT                      post "rotation-cover" for a missed slot after doing its duty (the first cover the whole duty, the second WATCH only), so no other account repeats it
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


GRACE_S = 5 * 3600     # an account has until 5 h after its slot to leave a trace (a long run, a lease wait, retries)
LOOKBACK_S = 30 * 3600
MAX_COVER = 2          # a run covers at most two missed slots (Tj, 2026-10-15: usage runs out on one or two accounts): the FIRST with the slot's whole duty, the second with WATCH only
DUTY_WORDS = ("ANALYST", "ENGINEER", "WATCH", "ALL")   # the handoff note a finished run posts starts with its duty: "ANALYST 2026-10-15"


def slot_instants(now, n_slots=3):
    """(slot, day, instant) for every scheduled slot instant in the last LOOKBACK_S before `now` (aware datetime). Pure."""
    from zoneinfo import ZoneInfo
    et = ZoneInfo(ZONE)
    out = []
    d = now.astimezone(et).date()
    for back in range(0, 3):
        day = d - datetime.timedelta(days=back)
        for s in range(n_slots):
            h, m = SLOTS[s].split(":")
            inst = datetime.datetime(day.year, day.month, day.day, int(h), int(m), tzinfo=et)
            age = (now - inst).total_seconds()
            if 0 <= age <= LOOKBACK_S:
                out.append((s, day, inst))
    return sorted(out, key=lambda x: x[2])


def missed_slots(roster, notes, now, away=None):
    """Slots (slot, day, instant, tag, state) that need covering: state NO-SHOW = the account left no board note between the slot instant and instant+GRACE_S (usage cap, no repo, dead routine);
    INTERRUPTED = it checked in but never posted its finished-duty note (cut off by its usage cap mid-task) and the duty was ANALYST or ENGINEER (a cut-off WATCH is cheap and not worth covering).
    Only slots past that grace, scheduled after the account joined, with no 'rotation-cover slot=S date=D' note. `notes` = [(meta, subject)]. Pure.
    `away` = {tag: ISO time}: an account Tj said is out of usage until then (research/coordination/away.json). Its slots before that time are state AWAY, covered at once (no grace, no waiting)."""
    from zoneinfo import ZoneInfo
    accounts = roster.get("accounts", [])
    if len(accounts) < 3:
        return []   # with fewer accounts every run already does ALL duties; nobody is "missing"
    out = []
    for s, day, inst in slot_instants(now):
        tag0 = accounts[s]["tag"]
        gone = (away or {}).get(tag0)
        is_away = False
        if gone:
            try:
                is_away = inst < datetime.datetime.fromisoformat(gone.replace("Z", "+00:00"))
            except Exception:
                is_away = False
        if (now - inst).total_seconds() < GRACE_S and not is_away:
            continue
        acc = accounts[s]
        tag = acc["tag"]
        try:
            joined = datetime.datetime.fromisoformat(acc.get("registeredAt", "1970-01-01T00:00:00Z").replace("Z", "+00:00"))
        except Exception:
            joined = datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc)
        if inst < joined:
            continue
        lo, hi = inst - datetime.timedelta(minutes=10), inst + datetime.timedelta(seconds=GRACE_S)
        seen, finished, covered = False, False, False
        for meta, subject in notes:
            try:
                at = datetime.datetime.fromisoformat(meta.get("at", "").replace("Z", "+00:00"))
            except Exception:
                continue
            frm = meta.get("from", "").split("/")[0]
            if frm == tag and lo <= at <= hi:
                seen = True
                if subject.split(" ")[0] in DUTY_WORDS or subject.startswith("rotation-cover") or subject.startswith("rotation-standdown"):
                    finished = True
            if subject.startswith("rotation-cover") and ("slot=%d" % s) in subject and ("date=%s" % day.isoformat()) in subject:
                covered = True
        if covered:
            continue
        if is_away and not (seen and finished):
            out.append((s, day, inst, tag, "AWAY"))
        elif not seen:
            out.append((s, day, inst, tag, "NO-SHOW"))
        elif not finished and duty_for(s, day) in ("ANALYST", "ENGINEER", "ALL"):
            out.append((s, day, inst, tag, "INTERRUPTED"))
    return out


def paused_slots(roster, notes, now):
    """Slots whose routine was stopped because Tj arrived (a 'rotation-paused label=X date=D' note from coord.acquire) and that no later note of the routine's account or a cover resolved:
    (slot, day, instant, tag, "PAUSED"). No grace: it is resumable as soon as the lease is free. Pure."""
    from zoneinfo import ZoneInfo
    accounts = roster.get("accounts", [])
    tags = [a["tag"] for a in accounts]
    out = []
    for meta, subject in notes:
        if not subject.startswith("rotation-paused"):
            continue
        kv = dict(p.split("=", 1) for p in subject.split()[1:] if "=" in p)
        tag, date = kv.get("label", ""), kv.get("date", "")
        if tag not in tags or not date:
            continue
        try:
            at = datetime.datetime.fromisoformat(meta.get("at", "").replace("Z", "+00:00"))
            day = datetime.date.fromisoformat(date)
        except Exception:
            continue
        if (now - at).total_seconds() > LOOKBACK_S:
            continue
        s = tags.index(tag)
        resolved = False
        for m2, sub2 in notes:
            try:
                at2 = datetime.datetime.fromisoformat(m2.get("at", "").replace("Z", "+00:00"))
            except Exception:
                continue
            if at2 <= at:
                continue
            if m2.get("from", "").split("/")[0] == tag and (sub2.split(" ")[0] in DUTY_WORDS or sub2.startswith("rotation-resumed")):
                resolved = True
            if sub2.startswith("rotation-cover") and ("slot=%d" % s) in sub2 and ("date=%s" % date) in sub2:
                resolved = True
        if not resolved:
            h, m = SLOTS[s].split(":")
            out.append((s, day, datetime.datetime(day.year, day.month, day.day, int(h), int(m), tzinfo=ZoneInfo(ZONE)), tag, "PAUSED"))
    return out


def selftest():
    import datetime as dt
    z = dt.timezone.utc
    roster = {"accounts": [{"tag": "a", "registeredAt": "2026-10-15T05:00:00Z"}, {"tag": "b", "registeredAt": "2026-10-15T05:00:00Z"}, {"tag": "c", "registeredAt": "2026-10-15T05:00:00Z"}]}
    # 2026-10-16 12:00Z = 08:00 ET: slot 0 of Oct 16 (07:20 ET) is 40 min old: still inside the grace; Oct 15 slots 1 and 2 are due
    now = dt.datetime(2026, 10, 16, 12, 0, tzinfo=z)
    m = missed_slots(roster, [], now)
    assert [(x[0], x[1].isoformat()) for x in m] == [(0, "2026-10-15"), (1, "2026-10-15"), (2, "2026-10-15")], m
    # a and b checked in at their slots (11:21Z and 17:21Z on the 15th): fine; c left nothing: slot 2 missed
    d15 = datetime.date(2026, 10, 15)
    notes = [({"from": "a/zz", "at": "2026-10-15T11:21:00Z"}, "rotation-checkin slot=0"), ({"from": "a/zz", "at": "2026-10-15T12:30:00Z"}, "%s 2026-10-15" % duty_for(0, d15)),
             ({"from": "b/xx", "at": "2026-10-15T17:21:00Z"}, "rotation-checkin slot=1"), ({"from": "b/xx", "at": "2026-10-15T18:10:00Z"}, "%s 2026-10-15" % duty_for(1, d15))]
    assert [x[0] for x in missed_slots(roster, notes, now)] == [2]
    # b checked in but never posted its finished-duty note and its duty that day was ANALYST/ENGINEER: INTERRUPTED (cover it); a cut-off WATCH is not covered
    interrupted = missed_slots(roster, notes + [({"from": "c/zz", "at": "2026-10-15T23:21:00Z"}, "rotation-checkin slot=2")], now)
    want_state = "INTERRUPTED" if duty_for(2, d15) in ("ANALYST", "ENGINEER") else None
    assert [x[4] for x in interrupted if x[0] == 2] == ([want_state] if want_state else []), interrupted
    done = missed_slots(roster, notes + [({"from": "c/zz", "at": "2026-10-15T23:21:00Z"}, "rotation-checkin slot=2"), ({"from": "c/zz", "at": "2026-10-16T01:00:00Z"}, "%s 2026-10-15" % duty_for(2, d15))], now)
    assert [x for x in done if x[0] == 2] == [], done
    # paused because Tj arrived: PAUSED until a later duty/resume note of that account or a cover; no grace
    pn = [({"from": "t/zz", "at": "2026-10-16T11:40:00Z"}, "rotation-paused label=c date=2026-10-16")]
    pz = paused_slots(roster, pn, now + datetime.timedelta(hours=1))
    assert [(x[0], x[4]) for x in pz] == [(2, "PAUSED")], pz
    assert paused_slots(roster, pn + [({"from": "c/q", "at": "2026-10-16T12:30:00Z"}, "rotation-resumed label=c")], now + datetime.timedelta(hours=1)) == []
    assert paused_slots(roster, pn + [({"from": "c/q", "at": "2026-10-16T12:30:00Z"}, "WATCH 2026-10-16")], now + datetime.timedelta(hours=1)) == []
    assert paused_slots(roster, pn + [({"from": "a/q", "at": "2026-10-16T12:30:00Z"}, "rotation-cover slot=2 date=2026-10-16")], now + datetime.timedelta(hours=1)) == []
    assert paused_slots(roster, [({"from": "t/zz", "at": "2026-10-16T11:40:00Z"}, "rotation-paused label=zzz date=2026-10-16")], now) == [], "an unknown account is ignored"
    # covered once by someone: gone
    notes.append(({"from": "a/yy", "at": "2026-10-16T11:30:00Z"}, "rotation-cover slot=2 date=2026-10-15"))
    assert missed_slots(roster, notes, now) == []
    # a routine inside an away account's slot window is dormant whatever its label says
    sd = slot_dormant(roster, dt.datetime(2026, 10, 16, 11, 40, tzinfo=dt.timezone.utc), {roster["accounts"][0]["tag"]: "2026-10-17T02:00:00Z"})
    assert sd and sd[0] == roster["accounts"][0]["tag"], sd
    assert slot_dormant(roster, dt.datetime(2026, 10, 16, 17, 40, tzinfo=dt.timezone.utc), {roster["accounts"][0]["tag"]: "2026-10-17T02:00:00Z"}) is None, "13:40 ET is slot 1's window"
    assert slot_dormant(roster, dt.datetime(2026, 10, 17, 11, 40, tzinfo=dt.timezone.utc), {roster["accounts"][0]["tag"]: "2026-10-17T02:00:00Z"}) is None, "after the away time"
    # dormant: away_until is set only for a future time
    assert away_until("nobody-at-all") is None
    # an account Tj said is out of usage: its slots before the 'away until' time are AWAY at once (no grace), and a cover removes them
    t0 = roster["accounts"][0]["tag"]
    soon = dt.datetime(2026, 10, 16, 11, 30, tzinfo=dt.timezone.utc)
    aw = missed_slots(roster, [], soon, {t0: "2026-10-17T02:00:00Z"})
    assert [(x[0], x[4]) for x in aw if x[0] == 0 and x[1].isoformat() == "2026-10-16"] == [(0, "AWAY")], aw
    assert [x for x in missed_slots(roster, [({"from": "a/yy", "at": "2026-10-16T11:35:00Z"}, "rotation-cover slot=0 date=2026-10-16")], soon, {t0: "2026-10-17T02:00:00Z"}) if x[0] == 0 and x[1].isoformat() == "2026-10-16"] == []
    assert [x for x in missed_slots(roster, [], soon, {t0: "2026-10-16T01:00:00Z"}) if x[0] == 0 and x[1].isoformat() == "2026-10-16"] == [], "past its away time: normal grace again"
    # an account is never blamed for a slot before it joined; with two accounts nothing is "missed"
    roster2 = {"accounts": [dict(a) for a in roster["accounts"]]}
    roster2["accounts"][2]["registeredAt"] = "2026-10-16T00:00:00Z"
    assert [x[0] for x in missed_slots(roster2, notes[:2], now)] == [1]
    assert missed_slots({"accounts": roster["accounts"][:2]}, [], now) == []
    # the ET slot instants follow daylight saving: 07:20 ET is 11:20Z in October (EDT) and 12:20Z in December (EST)
    assert slot_instants(dt.datetime(2026, 10, 16, 12, 0, tzinfo=z))[-1][2].astimezone(z).hour == 11
    assert [i for i in slot_instants(dt.datetime(2026, 12, 16, 13, 0, tzinfo=z)) if i[0] == 0][-1][2].astimezone(z).hour == 12

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
    # chat hand-over (Tj, 2026-10-16): roster a/b/c own the 07:20 / 13:20 / 19:20 ET windows (11:20Z / 17:20Z / 23:20Z)
    cz = lambda h, mi, d=16: dt.datetime(2026, 10, d, h, mi, tzinfo=z)
    ci = lambda who, h, mi, sub="rotation-checkin": ({"from": who + "/x", "at": cz(h, mi).isoformat().replace("+00:00", "Z")}, sub)
    # b's window, b is me: OWNER; a 10-min-to-go look at c's slot: b is told SOON
    assert chat_state(cz(18, 0), roster, [ci("b", 17, 21)], {}, "b")["state"] == "OWNER"
    assert chat_state(cz(23, 12), roster, [ci("b", 17, 21)], {}, "b")["state"] == "SOON"
    assert chat_state(cz(23, 12), roster, [], {}, "c")["state"] == "CONTINUE"                  # c's own slot is coming: not told to hand over
    # a (not the owner) while b's routine has checked in: stand down; before b checked in / never did: carry on
    assert chat_state(cz(18, 0), roster, [ci("b", 17, 21)], {}, "a")["state"] == "STANDDOWN"
    assert chat_state(cz(17, 25), roster, [], {}, "a")["state"] == "CONTINUE"                  # 5 min after the slot: wait for it
    assert chat_state(cz(18, 0), roster, [], {}, "a")["state"] == "CONTINUE"                   # 40 min on, no trace: not waited for
    assert chat_state(cz(18, 0), roster, [ci("b", 17, 21, "rotation-standdown")], {}, "a")["state"] == "CONTINUE"   # a stand-down is not serving the chat
    # b out of usage (away.json): its window is nobody's, a keeps the chat; and nobody is told to hand over to an away account
    assert chat_state(cz(18, 0), roster, [], {"b": "2026-10-17T02:00:00Z"}, "a")["state"] == "CONTINUE"
    assert chat_state(cz(23, 12), roster, [], {"c": "2026-10-17T02:00:00Z"}, "b")["state"] == "OWNER"
    assert chat_state(cz(12, 0), {"accounts": [{"tag": "a"}]}, [], {}, "a")["state"] == "CONTINUE"
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


HANDOVER_LEAD_S = 10 * 60   # the account whose slot is about to start is told about 10 minutes before
OWNER_WAIT_S = 15 * 60      # after its slot started, the owner has this long to leave a trace before the chat is not waited for


def _instants_around(now):
    from zoneinfo import ZoneInfo
    et = ZoneInfo(ZONE)
    d = now.astimezone(et).date()
    out = []
    for back in (-1, 0, 1):
        day = d + datetime.timedelta(days=back)
        for s in range(len(SLOTS)):
            h, m = SLOTS[s].split(":")
            out.append((s, day, datetime.datetime(day.year, day.month, day.day, int(h), int(m), tzinfo=et)))
    return sorted(out, key=lambda x: x[2])


def chat_state(now, roster, notes, away, me):
    """Who answers Tj's chat now (Tj, 2026-10-16: "I should chat with the account scheduled at that time, and if it is almost time for a new account to start, the current claude account
    hands over the chat"). The account whose slot began last owns the chat until the next slot begins. Returns {state, owner, next, detail}; state is
      OWNER      this account's slot is current: answer the chat (and do the duty);
      SOON       the next slot (another account's) starts within HANDOVER_LEAD_S: tell Tj the chat moves, release the lease so that routine can take it, keep answering until it starts;
      STANDDOWN  another account's slot is current and it has checked in: do not work, do not answer; tell Tj who has the chat if he writes here ('force' takes it back);
      CONTINUE   nobody else is running the chat (fewer than 3 accounts, the owner is out of usage, its routine has not checked in yet or never did): carry on as usual.
    `notes` = [(meta, subject)] from the board; `away` = {tag: ISO until}. Pure."""
    accounts = roster.get("accounts", [])
    if len(accounts) < len(SLOTS):
        return {"state": "CONTINUE", "owner": None, "next": None, "detail": "fewer than 3 accounts are registered"}
    inst = _instants_around(now)
    cur = [x for x in inst if x[2] <= now][-1]
    nxt = [x for x in inst if x[2] > now][0]
    cur_tag, nxt_tag = accounts[cur[0]]["tag"], accounts[nxt[0]]["tag"]

    def away_at(tag, t):
        try:
            return datetime.datetime.fromisoformat(str((away or {}).get(tag, "")).replace("Z", "+00:00")) > t
        except Exception:
            return False

    def ran(tag, since):
        lo = since - datetime.timedelta(minutes=10)
        for meta, subject in notes:
            try:
                at = datetime.datetime.fromisoformat(meta.get("at", "").replace("Z", "+00:00"))
            except Exception:
                continue
            if meta.get("from", "").split("/")[0] == tag and lo <= at <= now and not subject.startswith("rotation-standdown"):
                return True
        return False

    base = {"owner": cur_tag, "next": nxt_tag}
    if (nxt[2] - now).total_seconds() <= HANDOVER_LEAD_S and nxt_tag != me and not away_at(nxt_tag, nxt[2]):
        return dict(base, state="SOON", detail="the chat moves to %s at %s ET" % (nxt_tag, SLOTS[nxt[0]]))
    if cur_tag == me:
        return dict(base, state="OWNER", detail="this account's slot is current until %s ET" % SLOTS[nxt[0]])
    if away_at(cur_tag, cur[2]):
        return dict(base, state="CONTINUE", detail="%s owns this window but is out of usage (away.json)" % cur_tag)
    if ran(cur_tag, cur[2]):
        return dict(base, state="STANDDOWN", detail="the chat belongs to %s (slot %s ET) until %s ET" % (cur_tag, SLOTS[cur[0]], SLOTS[nxt[0]]))
    if (now - cur[2]).total_seconds() < OWNER_WAIT_S:
        return dict(base, state="CONTINUE", detail="%s's routine has not checked in yet (waiting up to %d min)" % (cur_tag, OWNER_WAIT_S // 60))
    return dict(base, state="CONTINUE", detail="%s's routine left no trace this window: carrying on" % cur_tag)


def chat(tag):
    """The chat no longer rotates (CR, Tj 2026-10-19: "Assign just one account to the chat which never changes"): ONE account answers it, named in
    research/coordination/chat.json, and nobody stands down for it. chat_state() above is the old rotating rule, kept only for its selftest."""
    try:
        cfg = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "research", "coordination", "chat.json")))
    except Exception:
        cfg = {"account": "acct-5d1e2d", "name": "mirage"}
    mine = (tag or "") == cfg.get("account")
    print("CHATSTATE FIXED the app chat belongs to %s (%s) for good (research/coordination/chat.json); %s" % (
        cfg.get("account"), cfg.get("name"), "that is this account: run python3 -I -u tools/chat.py watch under Monitor" if mine else "this account never answers it and never stands down for it"))
    return 0


def board_notes():
    import coord
    coord.sync()
    return [(meta, meta.get("subject", "")) for _, meta, _ in coord.notes()]


def away_until(tag, now=None):
    """The UTC datetime until which `tag` is dormant (out of usage, research/coordination/away.json), or None. Pure apart from reading the file."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    try:
        d = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "research", "coordination", "away.json")))
        until = datetime.datetime.fromisoformat(str(d.get(tag, "")).replace("Z", "+00:00"))
    except Exception:
        return None
    return until if until > now else None


def slot_dormant(roster, now=None, away=None):
    """A routine that starts inside the scheduled window of a slot whose account is away is that account's own routine, whatever label it carries (a fresh routine container is 'acct-unknown'
    until it says who it is). Returns (tag, until) or None. Window: 20 min before the slot to 3 h after. Pure given its arguments."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    if away is None:
        try:
            away = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "research", "coordination", "away.json")))
        except Exception:
            return None
    accounts = roster.get("accounts", [])
    from zoneinfo import ZoneInfo
    et = ZoneInfo(ZONE)
    d = now.astimezone(et).date()
    for day in (d - datetime.timedelta(days=1), d):
        for sidx, acc in enumerate(accounts[:len(SLOTS)]):
            h, m = SLOTS[sidx].split(":")
            inst = datetime.datetime(day.year, day.month, day.day, int(h), int(m), tzinfo=et)
            if inst - datetime.timedelta(minutes=20) <= now <= inst + datetime.timedelta(hours=3):
                try:
                    until = datetime.datetime.fromisoformat(str(away.get(acc["tag"], "")).replace("Z", "+00:00"))
                except Exception:
                    continue
                if until > now:
                    return acc["tag"], until
    return None


def checkin(tag):
    import coord
    until = away_until(tag)
    if until is None:
        try:
            hit = slot_dormant(read_roster())
            until = hit[1] if hit else None
        except Exception:
            until = None
    if until is not None:
        print("ROTATION  DORMANT: %s is out of usage until %s (research/coordination/away.json). Do nothing: no lease, no push, no workflow. Say so in one line and stop." % (tag, until.isoformat()))
        return 5
    roster = read_roster()
    s = slot_of(tag, roster)
    day = today_et()
    duty = duty_for(s, day, len(roster["accounts"])) if s is not None else "?"
    return coord.post("rotation-checkin slot=%s date=%s duty=%s" % (s, day.isoformat(), duty), "%s ran its routine. If this is the only line, it then stood down (lease held or nothing to do)." % tag)


def missed():
    roster = read_roster()
    now = datetime.datetime.now(datetime.timezone.utc)
    notes = board_notes()
    away = {}
    try:
        away = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "research", "coordination", "away.json")))
    except Exception:
        pass
    m = paused_slots(roster, notes, now) + missed_slots(roster, notes, now, away)
    if not m:
        print("ROTATION  no missed slots")
        return 0
    for i, (s, day, inst, tag, state) in enumerate(m[:6]):
        what = "its whole duty (%s), under the usual skip rules" % duty_for(s, day) if i == 0 else "WATCH checks only"
        if state == "PAUSED":
            what = "resume it from the routine-wip-%s branch (read CHECKPOINT.md), once the lease is free; then post: rotation-resumed" % day.isoformat()
        print("ROTATION  %s slot %d (%s ET on %s, account %s); duty then: %s; cover with: %s" % (state, s, SLOTS[s], day.isoformat(), tag, duty_for(s, day), what))
    print("ROTATION  cover at most %d (the first with the whole duty, the next with WATCH only), then for each: python3 -I tools/rotation.py cover <slot> --date <date>" % MAX_COVER)
    return 0


def cover(slot, date):
    import coord
    return coord.post("rotation-cover slot=%d date=%s" % (slot, date), "Duty done for a slot whose account left no trace or was cut off (usage cap, no repo, a dead routine).")


def main(argv):
    ap = argparse.ArgumentParser(prog="rotation.py")
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("register"); r.add_argument("--tag")
    t = sub.add_parser("today"); t.add_argument("--tag"); t.add_argument("--date")
    sub.add_parser("roster"); sub.add_parser("selftest")
    ci = sub.add_parser("checkin"); ci.add_argument("--tag")
    cr = sub.add_parser("cron"); cr.add_argument("--tag")
    sub.add_parser("missed")
    chp = sub.add_parser("chat"); chp.add_argument("--tag")
    cv = sub.add_parser("cover"); cv.add_argument("slot", type=int); cv.add_argument("--date", required=True)
    a = ap.parse_args(argv)
    if a.cmd == "selftest":
        return selftest()
    try:
        if a.cmd == "register":
            return register(my_tag(a.tag))
        if a.cmd == "checkin":
            return checkin(my_tag(a.tag))
        if a.cmd == "missed":
            return missed()
        if a.cmd == "chat":
            return chat(my_tag(a.tag))
        if a.cmd == "cron":
            slot = slot_of(my_tag(a.tag), read_roster())
            if slot is None:
                print("ROTATION  not on the roster: run  python3 -I tools/rotation.py register")
                return 2
            print(cron_for(slot))
            return 0
        if a.cmd == "cover":
            return cover(a.slot, a.date)
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
