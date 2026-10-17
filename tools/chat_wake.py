#!/usr/bin/env python3
"""chat_wake.py - wake the on-duty Claude account when Tj writes in the app chat and no live session has picked it up (TCL70, Tj 2026-10-17).

Runs on GitHub (workflow chat-wake.yml, free, zero Claude usage) whenever a file lands in chat/ on cloud-config. A session that is running tools/chat_watch.sh writes a read receipt (`ack`) within
seconds, so: wait WAIT_S after the message; if it has an ack or a reply, a session is alive and nothing happens. If not, write a notice into the chat ("waking <account>") and POST the on-duty
account's routine API trigger (https://code.claude.com/docs/en/routines, `/fire`, 30 an hour per routine). Secrets (repo secrets, never in a file): CLAUDE_FIRE_<TAG>_URL and CLAUDE_FIRE_<TAG>_TOKEN,
TAG = the account's 6 hex letters upper-case. Every failure is written into the chat in words, so Tj sees why nobody is awake.   python3 -I tools/chat_wake.py run | selftest
"""
import datetime, json, os, subprocess, sys, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
BRANCH = "cloud-config"
WAIT_S = 40            # a live watcher acks within ~6 s; this is generous
RECENT_S = 15 * 60     # only messages this young are woken for
DEBOUNCE_S = 10 * 60   # one wake per ten minutes: the woken session reads every message
FIRE_HEADERS = {"anthropic-beta": "experimental-cc-routine-2026-04-01", "anthropic-version": "2023-06-01", "Content-Type": "application/json"}


def unanswered(msgs, now_ms):
    """Tj's messages that are recent, with no ack file and no reply, and no wake notice since they were written. Pure; msgs = list of dicts."""
    seen = {m.get("ack") for m in msgs if m.get("ack")} | {m.get("replyTo") for m in msgs if m.get("replyTo")}
    wakes = [m for m in msgs if str(m.get("id", "")).startswith("w") and m.get("from") == "claude:github" and m.get("kind") == "wake"]
    out = []
    for m in msgs:
        if m.get("from") != "tj" or m.get("ack") or m.get("id") in seen:
            continue
        if now_ms - m.get("at", 0) > RECENT_S * 1000:
            continue
        if any(w.get("at", 0) >= m.get("at", 0) for w in wakes):
            continue
        out.append(m)
    return sorted(out, key=lambda m: m["at"])


def recent_wake(msgs, now_ms):
    return any(m.get("kind") == "wake" and now_ms - m.get("at", 0) < DEBOUNCE_S * 1000 for m in msgs)


def order(now, roster, away):
    """Account tags to try, the one whose slot is current first, then the others by slot order; accounts out of usage last."""
    import rotation
    accounts = [a["tag"].replace("acct-", "") for a in roster.get("accounts", [])]
    if not accounts:
        return []
    inst = rotation._instants_around(now)
    cur = [x for x in inst if x[2] <= now][-1][0]
    ordered = [accounts[(cur + i) % len(accounts)] for i in range(len(accounts)) if (cur + i) % len(accounts) < len(accounts)]
    def away_now(t):
        try:
            return datetime.datetime.fromisoformat(str((away or {}).get("acct-" + t, "")).replace("Z", "+00:00")) > now
        except Exception:
            return False
    return [t for t in ordered if not away_now(t)] + [t for t in ordered if away_now(t)]


def fire(url, token, text, post=None):
    req = urllib.request.Request(url, data=json.dumps({"text": text}).encode(), headers=dict(FIRE_HEADERS, Authorization="Bearer " + token), method="POST")
    with (post or urllib.request.urlopen)(req, timeout=20) as r:
        return json.loads(r.read().decode() or "{}")


def wake(msgs, now, roster, away, env, post=None):
    """Returns (outcome_text, ok). Pure apart from the POST."""
    tried = []
    for tag in order(now, roster, away):
        url, tok = env.get("CLAUDE_FIRE_%s_URL" % tag.upper()), env.get("CLAUDE_FIRE_%s_TOKEN" % tag.upper())
        if not url or not tok:
            tried.append("acct-%s has no wake link saved on GitHub" % tag)
            continue
        try:
            r = fire(url, tok, "CHAT: Tj wrote in the Vigilant app chat. Chat mode (see research/coordination/CHAT_WAKE.md): start tools/chat_watch.sh under Monitor, answer every message with tell_tj.py chat-reply. Do not take the repo lease for chat.", post)
            return "Waking acct-%s's Claude now (it needs about a minute to start); it will answer here." % tag, True
        except Exception as e:  # noqa: BLE001
            tried.append("acct-%s: %s" % (tag, str(e)[:80]))
    return "No Claude session was awake and none could be woken (%s). Open a Claude session on any account; it reads this chat at once." % ("; ".join(tried) or "no accounts registered"), False


def git(*a, check=True):
    r = subprocess.run(["git", *a], capture_output=True, text=True)
    if check and r.returncode:
        raise RuntimeError(r.stderr.strip())
    return r.stdout


def load():
    git("fetch", "-q", "origin", BRANCH)
    names = [n for n in git("ls-tree", "--name-only", "FETCH_HEAD", "chat/").split() if n.endswith(".json")]
    out = []
    for n in sorted(names)[-200:]:
        try:
            out.append(json.loads(git("show", "FETCH_HEAD:" + n)))
        except Exception:
            pass
    return out


def run():
    import tell_tj
    roster, away = {}, {}
    try:
        roster = json.load(open(os.path.join(HERE, "..", "research", "coordination", "roster.json")))
    except Exception:
        try:
            import rotation
            roster = rotation.read_roster()
        except Exception:
            pass
    try:
        away = json.load(open(os.path.join(HERE, "..", "research", "coordination", "away.json")))
    except Exception:
        pass
    now_ms = int(time.time() * 1000)
    msgs = load()
    todo = unanswered(msgs, now_ms)
    if not todo:
        print("nothing to wake for"); return 0
    wait = WAIT_S - (now_ms - todo[0]["at"]) / 1000
    if wait > 0:
        time.sleep(wait)
    msgs = load()
    now_ms = int(time.time() * 1000)
    todo = unanswered(msgs, now_ms)
    if not todo:
        print("a live session picked it up"); return 0
    if recent_wake(msgs, now_ms):
        print("already woken in the last 10 minutes"); return 0
    text, ok = wake(msgs, datetime.datetime.now(datetime.timezone.utc), roster, away, os.environ)
    at = int(time.time() * 1000)
    m = {"id": "w%d" % at, "at": at, "from": "claude:github", "text": text, "replyTo": None, "ack": None, "files": [], "kind": "wake"}
    tell_tj.put("chat/%013d-w%d.json" % (at, at), json.dumps(m))
    print(text)
    return 0 if ok else 0


def selftest():
    now = 1_000_000_000_000
    tj = {"id": "t1", "at": now - 50_000, "from": "tj", "text": "hi", "replyTo": None, "ack": None}
    assert [m["id"] for m in unanswered([tj], now)] == ["t1"]
    assert unanswered([tj, {"id": "a", "at": now, "from": "claude:s", "ack": "t1"}], now) == []
    assert unanswered([tj, {"id": "r", "at": now, "from": "claude:s", "replyTo": "t1"}], now) == []
    assert unanswered([dict(tj, at=now - RECENT_S * 1000 - 1)], now) == []
    wk = {"id": "w1", "at": now - 10_000, "from": "claude:github", "kind": "wake"}
    assert unanswered([tj, wk], now) == [] and recent_wake([tj, wk], now) and not recent_wake([dict(wk, at=now - DEBOUNCE_S * 1000 - 5)], now)
    roster = {"accounts": [{"tag": "acct-aaaaaa"}, {"tag": "acct-bbbbbb"}, {"tag": "acct-cccccc"}]}
    t = datetime.datetime(2026, 10, 17, 3, 0, tzinfo=datetime.timezone.utc)   # 23:00 ET: slot 2 (19:20) is current
    assert order(t, roster, {}) == ["cccccc", "aaaaaa", "bbbbbb"], order(t, roster, {})
    assert order(t, roster, {"acct-cccccc": "2026-10-17T05:00:00Z"}) == ["aaaaaa", "bbbbbb", "cccccc"]
    calls = []
    class R:
        def __enter__(s): return s
        def __exit__(s, *a): return False
        def read(s): return b'{"type":"routine_fire"}'
    def post(req, timeout=0):
        calls.append(req.full_url); return R()
    env = {"CLAUDE_FIRE_AAAAAA_URL": "https://x/fire", "CLAUDE_FIRE_AAAAAA_TOKEN": "tok"}
    text, ok = wake([], t, roster, {}, env, post)
    assert ok and "acct-aaaaaa" in text and calls == ["https://x/fire"], text
    text, ok = wake([], t, roster, {}, {}, post)
    assert not ok and "no wake link" in text
    print("chat_wake selftest ok"); return 0


if __name__ == "__main__":
    sys.exit(selftest() if sys.argv[1:] == ["selftest"] else run())
