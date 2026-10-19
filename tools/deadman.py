#!/usr/bin/env python3
"""Dead-man switch (TCL50; Tj 2026-10-16, permanent): if Tj goes quiet, the whole system stops.

  python3 tools/deadman.py tick        (the hourly workflow; also safe by hand) one step of the state machine
  python3 tools/deadman.py resume      (a message from Tj arrived: clear STOP now, do not wait for the next tick)
  python3 tools/deadman.py status
  python3 tools/deadman.py selftest

Presence = a message FROM TJ: a chat message in the app (the new chat on the `chat` branch since 2026-10-19, CR, and the old app's chat/ on cloud-config) or an entry in INBOX.md (capture_inbox.sh writes only his typed words,
never a routine's stored prompt). The app merely syncing, phone-data pushes, bot commits and Claude's own messages are NOT presence.
  ok      -> 72 h without presence: send the check-in (a priority-1 phone message + a chat message), phase `asked`.
  asked   -> any message from Tj: back to ok. 24 h with none: STOP.
  STOP    -> stage 1: cloud-config `on:false` (the engine ends within ~10 min and takes its own bids down), deadman/STOP.json (every session and routine refuses to
             start: coord.py acquire), every workflow but this one and the engine's disabled. Stage 2 (>= 30 min later, so the engine's own clean-up ran): cancel a
             still-running engine and disable cloud-scan.yml. This workflow stays enabled: it is the watchdog and costs nothing.
  stopped -> a message from Tj: STOP.json removed, the disabled workflows enabled again; the engine's `on` and every trading switch stay as Tj sets them (never restarted by this).
State is deadman/state.json on cloud-config. Nothing here touches a key, a limit or money.
"""
import calendar, json, os, re, subprocess, sys, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import tell_tj  # noqa: E402

ASK_AFTER_MS = 72 * 3600 * 1000
GRACE_MS = 24 * 3600 * 1000
STAGE2_MS = 30 * 60 * 1000
KEEP_ENABLED = {"deadman.yml"}          # the watchdog itself
ENGINE_WF = "cloud-scan.yml"


def step(now, presence, st):
    """Pure: (now ms, last presence ms, state) -> (new state, actions). Actions: ask, stop1, stop2, resume."""
    st = dict(st or {})
    phase = st.get("phase", "ok")
    acts = []
    if phase == "ok":
        if now - presence > ASK_AFTER_MS:
            st.update(phase="asked", askedAt=now); acts.append("ask")
    elif phase == "asked":
        if presence > st.get("askedAt", 0):
            st.update(phase="ok"); st.pop("askedAt", None)
        elif now - st.get("askedAt", now) > GRACE_MS:
            st.update(phase="stopped", stoppedAt=now, stage=1); acts.append("stop1")
    elif phase == "stopped":
        if presence > st.get("stoppedAt", 0):
            st.update(phase="ok"); acts.append("resume")
        elif st.get("stage", 1) == 1 and now - st.get("stoppedAt", now) >= STAGE2_MS:
            st["stage"] = 2; acts.append("stop2")
    return st, acts


REF = "refs/vigilant/deadman-" + tell_tj.BRANCH   # never FETCH_HEAD: another fetch in the same checkout at the same moment would swap it (CR, 2026-10-19)


def fetch_config(cwd=None):
    tell_tj.git("fetch", "-q", "origin", "+refs/heads/%s:%s" % (tell_tj.BRANCH, REF), cwd=cwd, check=False)


def last_chat_from_tj(cwd=None):
    """Tj's newest app-chat message: the new chat (tools/chat.py, branch `chat`) or the old app's chat/ on cloud-config, whichever is later."""
    best = 0
    try:
        import chat
        best = chat.last_tj_at(cwd=cwd)
    except Exception:
        pass
    fetch_config(cwd)
    names = tell_tj.git("ls-tree", "-r", "--name-only", REF, cwd=cwd, check=False).stdout.split()
    for f in names:
        if not f.startswith("chat/") or "-ack-" in f:
            continue
        m = re.match(r"chat/(\d{13})-t\1", f)          # Tj's own messages are named <ms>-t<ms>.json
        if m:
            best = max(best, int(m.group(1)))
    return best


def last_inbox(cwd=None):
    """The newest '## <ISO time>' header in INBOX.md on origin/main (Tj's typed words only)."""
    tell_tj.git("fetch", "-q", "origin", "main", cwd=cwd, check=False)
    txt = tell_tj.git("show", "origin/main:INBOX.md", cwd=cwd, check=False).stdout
    best = 0
    for m in re.finditer(r"^## (\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ)", txt, re.M):
        best = max(best, calendar.timegm(time.strptime(m.group(1), "%Y-%m-%dT%H:%M:%SZ")) * 1000)
    return best


def presence(cwd=None):
    return max(last_chat_from_tj(cwd), last_inbox(cwd))


def read_json(path, cwd=None):
    fetch_config(cwd)
    r = tell_tj.git("show", REF + ":" + path, cwd=cwd, check=False)
    try:
        return json.loads(r.stdout) if r.returncode == 0 else None
    except ValueError:
        return None


def stopped(cwd=None):
    """True while deadman/STOP.json is on cloud-config (coord.py acquire refuses then). Any failure reads as 'not stopped': losing work is worse."""
    try:
        return read_json("deadman/STOP.json", cwd) is not None
    except Exception:
        return False


def api(method, path, body=None):
    tok = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "tjshea90/novig")
    req = urllib.request.Request("https://api.github.com/repos/%s/%s" % (repo, path), method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": "Bearer " + tok, "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        t = r.read()
        return json.loads(t) if t else {}


def workflows():
    return api("GET", "actions/workflows?per_page=100").get("workflows", [])


def put_json(path, obj, cwd=None):
    tell_tj.put(path, json.dumps(obj, indent=1) + "\n", cwd=cwd)


def engine_off(cwd=None):
    cfg = read_json("settings.json", cwd)
    if isinstance(cfg, dict) and cfg.get("on"):
        cfg["on"] = False
        cfg["rev"] = int(time.time() * 1000)
        put_json("settings.json", cfg, cwd)


def act(action, st, now, cwd=None):
    if action == "ask":
        tell_tj.say("Are you still there?", "You haven't messaged Claude for 3 days. Reply with anything in the Claude tab within 24 hours or the whole system stops: the GitHub engine, every workflow, and all Claude sessions and routines on all 3 accounts.", kind="alert", priority=1, who="claude:deadman", cwd=cwd)
        tell_tj.chat_reply(None, "Still there? No message from you for 3 days. Say anything here within 24 hours or I stop everything (engine, GitHub workflows, all Claude sessions and routines).", who="claude:deadman", cwd=cwd)
    elif action == "stop1":
        off = []
        for w in workflows():
            fn = os.path.basename(w["path"])
            if w["state"] == "active" and fn not in KEEP_ENABLED and fn != ENGINE_WF:
                api("PUT", "actions/workflows/%d/disable" % w["id"]); off.append(fn)
        engine_off(cwd)
        st["disabled"] = off
        put_json("deadman/STOP.json", {"stoppedAt": now, "reason": "no message from Tj for 3 days and no reply to the check-in within 24 h", "resumedBy": "any message from Tj"}, cwd)
    elif action == "stop2":
        runs = api("GET", "actions/workflows/%s/runs?status=in_progress&per_page=5" % ENGINE_WF).get("workflow_runs", [])
        for r in runs:
            api("POST", "actions/runs/%d/cancel" % r["id"])
        for w in workflows():
            if os.path.basename(w["path"]) == ENGINE_WF and w["state"] == "active":
                api("PUT", "actions/workflows/%d/disable" % w["id"]); st["disabled"] = st.get("disabled", []) + [ENGINE_WF]
    elif action == "resume":
        names = set(st.get("disabled", []))
        for w in workflows():
            if os.path.basename(w["path"]) in names:
                api("PUT", "actions/workflows/%d/enable" % w["id"])
        # STOP.json is removed by pushing a tree without it
        remove("deadman/STOP.json", cwd)
        tell_tj.say("Welcome back: everything is switched on again", "Back on: " + (", ".join(sorted(names)) or "nothing was disabled") + ". The engine's switch stays OFF until you turn it on in Settings > Cloud scan; trading switches are unchanged.", kind="info", priority=1, who="claude:deadman", cwd=cwd)
        st["disabled"] = []


def remove(path, cwd=None):
    import tempfile
    for i in range(5):
        fetch_config(cwd)
        parent = tell_tj.git("rev-parse", REF, cwd=cwd).stdout.strip()
        with tempfile.TemporaryDirectory() as d:
            env = dict(os.environ, GIT_INDEX_FILE=os.path.join(d, "idx"))
            tell_tj.git("read-tree", parent, env=env, cwd=cwd)
            tell_tj.git("update-index", "--force-remove", path, env=env, cwd=cwd)
            tree = tell_tj.git("write-tree", env=env, cwd=cwd).stdout.strip()
            cenv = dict(os.environ, GIT_AUTHOR_NAME="Claude", GIT_AUTHOR_EMAIL="noreply@anthropic.com", GIT_COMMITTER_NAME="Claude", GIT_COMMITTER_EMAIL="noreply@anthropic.com")
            c = tell_tj.git("commit-tree", tree, "-m", "deadman: remove " + path, "-p", parent, env=cenv, cwd=cwd).stdout.strip()
        if tell_tj.git("push", "-q", "origin", "%s:refs/heads/%s" % (c, tell_tj.BRANCH), cwd=cwd, check=False).returncode == 0:
            return
        time.sleep(1 + i)


def tick(cwd=None):
    now = int(time.time() * 1000)
    st0 = read_json("deadman/state.json", cwd) or {}
    pres = presence(cwd)
    st, acts = step(now, pres, st0)
    for a in acts:
        act(a, st, now, cwd)
    if st != st0 or not st0:        # no hourly commit while nothing changed
        put_json("deadman/state.json", st, cwd)
    print("deadman: phase=%s actions=%s presence=%s" % (st.get("phase"), acts or "-", pres))
    return 0


def resume(cwd=None):
    """Called by capture_inbox.sh when Tj's message arrives: lift STOP at once (the workflows come back on the next tick, which sees the same presence)."""
    try:
        if stopped(cwd):
            remove("deadman/STOP.json", cwd)
            print("DEADMAN: STOP lifted by Tj's message; GitHub workflows come back within the hour")
    except Exception as e:
        print("DEADMAN: could not lift STOP (%s)" % e, file=sys.stderr)
    return 0


def selftest():
    H = 3600 * 1000
    t = 1_000_000 * H
    st, a = step(t, t - 71 * H, {}); assert a == [] and st.get("phase", "ok") == "ok"
    st, a = step(t, t - 73 * H, {}); assert a == ["ask"] and st["phase"] == "asked"
    s2, a = step(t + 23 * H, t - 73 * H, st); assert a == [] and s2["phase"] == "asked"
    s2, a = step(t + 5 * H, t + 2 * H, st); assert s2["phase"] == "ok" and a == []          # replied
    s3, a = step(t + 25 * H, t - 73 * H, st); assert a == ["stop1"] and s3["phase"] == "stopped" and s3["stage"] == 1
    s4, a = step(t + 25 * H + 10 * 60000, t - 73 * H, s3); assert a == []
    s5, a = step(t + 25 * H + 31 * 60000, t - 73 * H, s3); assert a == ["stop2"] and s5["stage"] == 2
    s6, a = step(t + 30 * H, t - 73 * H, s5); assert a == [] and s6["phase"] == "stopped"   # stays stopped, indefinitely
    s7, a = step(t + 99 * H, t + 98 * H, s5); assert a == ["resume"] and s7["phase"] == "ok"
    print("deadman selftest ok")
    return 0


def main(argv):
    cmd = argv[0] if argv else "status"
    if cmd == "selftest": return selftest()
    if cmd == "tick": return tick()
    if cmd == "resume": return resume()
    print(json.dumps({"state": read_json("deadman/state.json"), "stopped": stopped(), "presenceAt": presence()}, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
