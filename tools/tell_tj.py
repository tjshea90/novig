#!/usr/bin/env python3
"""Tell Tj something through the Vigilant phone app, or ask the phone to switch an API (TCL28; Tj 2026-10-15).

  python3 tools/tell_tj.py say "Title" "Body" [--kind recommendation|bet|alert|shipped|info] [--priority 0|1] [--action APIS]
  python3 tools/tell_tj.py set-api "reason" sgo=on parlay=off ...
  python3 tools/tell_tj.py chat-reply <message id> "answer"      (kept for old instructions: the same as python3 -I tools/chat.py reply <id> "answer")
  python3 tools/tell_tj.py selftest

THE APP CHAT moved to tools/chat.py (rebuilt from scratch, CR, Tj 2026-10-19: one chat account, one answering session, its own `chat` branch). chat-ack, chat-keep, presence,
chat-clean and relay are gone: the chat daemon writes the receipts, nothing is deleted any more, and a message typed into a session is answered by that session.

Each call adds ONE small file (messages/ or overrides/) to the `cloud-config` branch with a plain (non-force) push, so two writers never overwrite each other. The phone reads the folders every minute
and shows a message in Settings > Messages from Claude (priority 1 also sends a notification); a set-api request is applied once to the phone's settings, which then go back to GitHub, so both sides agree.
Words only: text that looks like a key/token is refused (the repo is public). API ids: sgo boltodds oddspapi oddspapi_free unabated parlay propline odds_api pinnwire pinnapi kalshi polymarket.
Never a money limit, key or trading switch: the phone ignores anything but API switches.
"""
import json, os, subprocess, sys, tempfile, time, re, secrets

IDS = "sgo boltodds oddspapi oddspapi_free unabated parlay propline odds_api pinnwire pinnapi kalshi polymarket".split()
SECRET = re.compile(r"(sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|[A-Fa-f0-9]{40,})")
BRANCH = "cloud-config"


def git(*a, env=None, inp=None, cwd=None, check=True):
    r = subprocess.run(["git", *a], capture_output=True, text=True, env=env, input=inp, cwd=cwd)
    if check and r.returncode:
        raise RuntimeError("git %s: %s" % (" ".join(a), r.stderr.strip()))
    return r


def me():
    try:
        out = subprocess.run(["python3", os.path.join(os.path.dirname(__file__), "coord.py"), "whoami"], capture_output=True, text=True, timeout=20).stdout
        m = re.search(r"acct-[0-9a-f]{6}", out)
        n = {"acct-5d1e2d": "mirage", "acct-7542bf": "tj outlook", "acct-27a37e": "tj Gmail"}.get(m.group(0)) if m else None  # Tj, 2026-10-18: name the account beside its id
        return "claude:" + (m.group(0) if m else "session") + (" (%s)" % n if n else "")
    except Exception:
        return "claude:session"


def put(path, text, remote="origin", cwd=None, tries=5):
    """Add one new file to the branch with a compare-and-swap push."""
    ref = "refs/vigilant/tell-" + BRANCH   # never FETCH_HEAD: another fetch in this checkout at the same moment would swap it (CR, 2026-10-19)
    for i in range(tries):
        git("fetch", "-q", remote, "+refs/heads/%s:%s" % (BRANCH, ref), cwd=cwd, check=False)
        have = git("rev-parse", "-q", "--verify", ref, cwd=cwd, check=False)
        parent = have.stdout.strip() if have.returncode == 0 else None
        with tempfile.TemporaryDirectory() as d:
            env = dict(os.environ, GIT_INDEX_FILE=os.path.join(d, "idx"))
            if parent:
                git("read-tree", parent, env=env, cwd=cwd)
            blob = git("hash-object", "-w", "--stdin", inp=text, cwd=cwd).stdout.strip()
            git("update-index", "--add", "--cacheinfo", "100644,%s,%s" % (blob, path), env=env, cwd=cwd)
            tree = git("write-tree", env=env, cwd=cwd).stdout.strip()
            cenv = dict(os.environ, GIT_AUTHOR_NAME="Claude", GIT_AUTHOR_EMAIL="noreply@anthropic.com", GIT_COMMITTER_NAME="Claude", GIT_COMMITTER_EMAIL="noreply@anthropic.com")
            args = ["commit-tree", tree, "-m", "tell_tj: " + path] + (["-p", parent] if parent else [])
            commit = git(*args, env=cenv, cwd=cwd).stdout.strip()
        r = git("push", "-q", remote, "%s:refs/heads/%s" % (commit, BRANCH), cwd=cwd, check=False)
        if r.returncode == 0:
            return True
        time.sleep(1 + i)
    raise RuntimeError("could not push %s after %d tries" % (path, tries))


def fname(at, ident):
    return "%013d-%s.json" % (at, re.sub(r"[^A-Za-z0-9-]", "", ident)[:24])


def say(title, body="", kind="info", priority=0, action=None, who=None, **kw):
    if SECRET.search(title + " " + body):
        raise SystemExit("refused: the text looks like a key or token (the repo is public)")
    at = int(time.time() * 1000)
    ident = "m%d-%s" % (at // 1000, secrets.token_hex(2))
    m = {"id": ident, "at": at, "from": who or me(), "kind": kind, "title": title, "body": body, "priority": int(priority)}
    if action:
        m["action"] = action
    put("messages/" + fname(at, ident), json.dumps(m), **kw)
    return ident


def set_api(reason, pairs, who=None, **kw):
    apis = {}
    for p in pairs:
        k, _, v = p.partition("=")
        if k not in IDS or v not in ("on", "off"):
            raise SystemExit("bad pair %r: use <id>=on|off with id in %s" % (p, " ".join(IDS)))
        apis[k] = v == "on"
    if SECRET.search(reason):
        raise SystemExit("refused: the text looks like a key or token")
    at = int(time.time() * 1000)
    ident = "o%d-%s" % (at // 1000, secrets.token_hex(2))
    r = {"id": ident, "at": at, "by": who or me(), "reason": reason, "apis": apis}
    put("overrides/" + fname(at, ident), json.dumps(r), **kw)
    return ident


def chat_reply(reply_to, text, who=None, **kw):
    """Kept for old instructions: the app chat is tools/chat.py now."""
    import chat
    return chat.cmd_reply([reply_to], text)


def selftest():
    with tempfile.TemporaryDirectory() as d:
        bare = os.path.join(d, "bare.git"); work = os.path.join(d, "w")
        git("init", "-q", "--bare", bare); git("init", "-q", work)
        git("remote", "add", "origin", bare, cwd=work)
        a = say("Refill SGO", "It has 4% left.", kind="recommendation", priority=1, action="APIS", who="claude:test", cwd=work)
        b = set_api("silent for 3 scans", ["parlay=off", "sgo=on"], who="claude:test", cwd=work)
        files = git("ls-tree", "-r", "--name-only", BRANCH, cwd=bare).stdout.split()
        assert len([f for f in files if f.startswith("messages/")]) == 1 and len([f for f in files if f.startswith("overrides/")]) == 1, files
        m = json.loads(git("show", "%s:%s" % (BRANCH, [f for f in files if f.startswith("messages/")][0]), cwd=bare).stdout)
        assert m["title"] == "Refill SGO" and m["priority"] == 1 and m["action"] == "APIS" and m["id"] == a, m
        o = json.loads(git("show", "%s:%s" % (BRANCH, [f for f in files if f.startswith("overrides/")][0]), cwd=bare).stdout)
        assert o["apis"] == {"parlay": False, "sgo": True} and o["id"] == b, o
        for bad in (lambda: say("x", "ghp_" + "a" * 30, cwd=work), lambda: set_api("r", ["nope=on"], cwd=work)):
            try:
                bad(); raise AssertionError("should refuse")
            except SystemExit:
                pass
    print("selftest ok")


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__); return 0
    if argv[0] == "selftest":
        selftest(); return 0
    if argv[0] == "say" and len(argv) >= 2:
        opts = {"--kind": "info", "--priority": "0", "--action": None}
        pos = []; i = 1
        while i < len(argv):
            if argv[i] in opts: opts[argv[i]] = argv[i + 1]; i += 2
            else: pos.append(argv[i]); i += 1
        print("sent", say(pos[0], pos[1] if len(pos) > 1 else "", opts["--kind"], opts["--priority"], opts["--action"])); return 0
    if argv[0] == "chat-reply" and len(argv) >= 3:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        import chat
        try:
            print("sent", " ".join(chat.cmd_reply([argv[1]], " ".join(argv[2:])))); return 0
        except chat.ChatError as e:
            print("CHAT " + str(e), file=sys.stderr); return 1
    if argv[0] in ("relay", "presence", "chat-ack", "chat-clean", "chat-keep", "chat-file"):
        print("%s is gone: the app chat is tools/chat.py now (CR, 2026-10-19): python3 -I tools/chat.py --help" % argv[0]); return 0
    if argv[0] == "set-api" and len(argv) >= 3:
        print("sent", set_api(argv[1], argv[2:])); return 0
    print(__doc__); return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
