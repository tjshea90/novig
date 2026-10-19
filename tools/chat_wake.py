#!/usr/bin/env python3
"""chat_wake.py - wake the chat account when Tj writes in the app chat and nobody is answering (rebuilt CR, Tj 2026-10-19; first version TCL70).

Runs on GitHub (free, zero Claude usage):
  - chat-wake.yml on the `chat` branch, on every push of a message (msgs/**):           run      (the new app's store)
  - chat-wake.yml on `cloud-config` (the old app, until Tj's phone has the new one):    run      (GITHUB_REF says which store; only inside chat.json's legacy window)
  - engine-watchdog.yml on main, every 5 minutes:                                      sweep    (both stores; catches a push event GitHub dropped)
The rules (decide(), pure, selftested):
  - only Tj's messages that no session has shown (no receipt) and nobody has answered count;
  - nothing happens while a session holds the chat seat (seat.json on chat-seat, heartbeat younger than 150 s): it is answering;
  - otherwise, 25 s after Tj's newest such message, fire ONLY the chat account's routine (research/coordination/chat.json, secrets CLAUDE_FIRE_<TAG>_URL and _TOKEN);
  - at most one fire per 3 minutes; after 3 fires in an hour that brought nobody to the seat, one an hour (an account out of usage is not hammered);
  - the wake is recorded in seat.json ("wake": when, which messages, the result), which the app shows in the chat's header; it is never posted as a chat message.
  python3 -I tools/chat_wake.py run | sweep | selftest
"""
import json
import os
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import chat  # noqa: E402

WAIT_MS = 25 * 1000          # a live daemon prints and writes the receipt within ~10 s
SWEEP_MIN_AGE_MS = 60 * 1000  # the sweep leaves the newest minute to the push-triggered run
GAP_MS = 3 * 60 * 1000
HOUR_MS = 3600 * 1000
FIRE_HEADERS = {"anthropic-beta": "experimental-cc-routine-2026-04-01", "anthropic-version": "2023-06-01", "Content-Type": "application/json"}


def decide(pending, seat_doc, at, min_age_ms=WAIT_MS):
    """('fire' | 'wait' | 'none', reason, wait_ms). pending = Tj's unseen, unanswered messages. Pure."""
    if not pending:
        return "none", "nothing unanswered", 0
    s = (seat_doc or {}).get("seat") or {}
    if s.get("holder") and at - int(s.get("beat", 0)) <= chat.STALE_S * 1000:
        return "none", "a session holds the chat seat (%s): it answers" % s.get("holder"), 0
    w = (seat_doc or {}).get("wake") or {}
    fires = sorted(f for f in w.get("fires", []) if at - f < HOUR_MS)
    since = int(s.get("since", 0)) if s.get("holder") else 0
    unanswered_fires = [f for f in fires if f > since]   # fires after the last time a session took the seat
    if fires and at - fires[-1] < GAP_MS:
        return "none", "woken %d s ago; it needs a minute or two to start" % ((at - fires[-1]) // 1000), 0
    if len(unanswered_fires) >= 3 and at - fires[-1] < HOUR_MS:
        return "none", "3 wakes this hour brought nobody (out of usage?): next try within the hour", 0
    newest = max(int(m.get("at", 0)) for m in pending)
    if at - newest < min_age_ms:
        return "wait", "Tj's newest message is %d s old" % ((at - newest) // 1000), min_age_ms - (at - newest)
    return "fire", "%d message(s) of Tj's unseen and nobody holds the seat" % len(pending), 0


def fire(url, token, text, post=None):
    req = urllib.request.Request(url, data=json.dumps({"text": text}).encode(), headers=dict(FIRE_HEADERS, Authorization="Bearer " + token), method="POST")
    with (post or urllib.request.urlopen)(req, timeout=20) as r:
        return json.loads(r.read().decode() or "{}")


def fire_text(cfg, pending):
    first = pending[0]
    return ("CHAT: Tj wrote in the Vigilant app chat (%d unanswered, newest %s). You are the chat account (%s). In /home/user/novig run `python3 -I -u tools/chat.py watch` "
            "under Monitor (persistent); it prints every unanswered message and downloads attachments. Answer with `python3 -I tools/chat.py reply <id> \"...\"`. "
            "Rules: research/coordination/CHAT.md. There is no repo lease any more: never wait for one, never mention one." % (
                len(pending), chat.hhmm(int(pending[-1].get("at", 0))), cfg.get("name"))) if first else "CHAT: Tj wrote in the Vigilant app chat."


def record(g, at, ids, result):
    def m(doc):
        w = doc.get("wake") or {}
        fires = [f for f in w.get("fires", []) if at - f < 6 * HOUR_MS]
        if result.startswith("fired"):
            fires.append(at)
        doc["wake"] = {"at": at, "ids": ids[-10:], "result": result[:200], "fires": fires[-20:]}
        return doc
    try:
        chat.write_seat(g, m)
    except chat.ChatError as e:
        print("could not record the wake:", e)


def pending_for(g, cfg, store_kind, at):
    """Tj's messages in the given store with no answer (new store: seen or not)."""
    out = []
    if store_kind in ("new", "both"):
        if g.fetch(chat.CHAT):
            msgs, _ = chat.Store(g).load(at=at, days=2)
            # Seen or not: decide() never fires while a seat is live, so a receipt with no answer and no live seat means the session that saw it was cut off before it answered.
            out += chat.unanswered(msgs)
    if store_kind in ("old", "both"):
        since = chat.legacy_window(cfg, at)
        if since is not None and g.fetch(chat.LEGACY):
            lmsgs, presence = chat.legacy_load(g, since)
            if not presence:
                out += [m for m in chat.legacy_pending(lmsgs, since) if not m.get("_acked")]
    return sorted(out, key=lambda m: m.get("at", 0))


def run(store_kind=None, sweep=False, env=None, post=None, g=None, cfg=None, sleep=time.sleep):
    env = env if env is not None else os.environ
    cfg = cfg or chat.load_cfg()
    if store_kind is None:
        ref = env.get("GITHUB_REF", "")
        store_kind = "old" if ref.endswith("/" + chat.LEGACY) else "both" if sweep else "new"
    g = g or chat.Git(home=os.path.join(env.get("RUNNER_TEMP", "/tmp"), "chat-wake"))
    for attempt in range(3):
        at = chat.now_ms()
        pending = pending_for(g, cfg, store_kind, at)
        seat_doc, _ = chat.read_seat(g)
        what, why, wait = decide(pending, seat_doc, at, SWEEP_MIN_AGE_MS if sweep else WAIT_MS)
        print("%s: %s" % (what, why))
        if what == "wait" and not sweep:
            sleep(min(40, wait / 1000 + 1))
            continue
        if what != "fire":
            return 0
        tag = str(cfg.get("fire") or cfg.get("account", "").replace("acct-", "")).upper()
        url, tok = env.get("CLAUDE_FIRE_%s_URL" % tag), env.get("CLAUDE_FIRE_%s_TOKEN" % tag)
        ids = [m["id"] for m in pending]
        if not url or not tok:
            record(g, at, ids, "failed: the wake link of %s (%s) is not saved on GitHub (secrets CLAUDE_FIRE_%s_URL/_TOKEN)" % (cfg.get("account"), cfg.get("name"), tag))
            print("no wake link for", tag)
            return 1
        try:
            fire(url, tok, fire_text(cfg, pending), post)
            record(g, at, ids, "fired %s (%s)" % (cfg.get("account"), cfg.get("name")))
            print("woke", cfg.get("account"))
            return 0
        except Exception as e:  # noqa: BLE001 - the reason goes to the app
            record(g, at, ids, "failed: %s" % str(e)[:160])
            print("wake failed:", e)
            return 1
    return 0


def selftest():
    at = 10 ** 12
    tj = [{"id": "t1-ab", "from": "tj", "at": at - 30000}]
    assert decide([], {}, at)[0] == "none"
    assert decide(tj, {}, at)[0] == "fire"
    assert decide([{"id": "t", "from": "tj", "at": at - 5000}], {}, at)[0] == "wait"
    live = {"seat": {"holder": "acct-a/n", "beat": at - 30000, "since": at - 600000}}
    assert decide(tj, live, at)[0] == "none"
    stale = {"seat": {"holder": "acct-a/n", "beat": at - (chat.STALE_S + 10) * 1000, "since": at - 600000}}
    assert decide(tj, stale, at)[0] == "fire"
    just = dict(stale, wake={"fires": [at - 60000]})
    assert decide(tj, just, at)[0] == "none" and "woken" in decide(tj, just, at)[1]
    three = {"seat": {"holder": "acct-a/n", "beat": at - 65 * 60000, "since": at - 70 * 60000}, "wake": {"fires": [at - 50 * 60000, at - 40 * 60000, at - 30 * 60000]}}
    assert decide(tj, three, at)[0] == "none" and "out of usage" in decide(tj, three, at)[1]
    # fires that DID bring a session (it took the seat after them) do not count towards the back-off
    took = {"seat": {"holder": "acct-a/n", "beat": at - (chat.STALE_S + 10) * 1000, "since": at - 25 * 60000}, "wake": three["wake"]}
    assert decide(tj, took, at)[0] == "fire"
    assert decide(tj, {}, at, SWEEP_MIN_AGE_MS)[0] == "wait"
    calls = []

    class R:
        def __enter__(s): return s
        def __exit__(s, *a): return False
        def read(s): return b'{"type":"routine_fire"}'

    def post(req, timeout=0):
        calls.append((req.full_url, json.loads(req.data)["text"]))
        return R()
    fire("https://x/fire", "tok", fire_text({"name": "mirage"}, tj), post)
    assert calls and calls[0][0] == "https://x/fire" and "tools/chat.py watch" in calls[0][1] and "lease" in calls[0][1]
    print("chat_wake selftest ok")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if a == ["selftest"]:
        sys.exit(selftest())
    sys.exit(run(sweep=a[:1] == ["sweep"]))
