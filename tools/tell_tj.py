#!/usr/bin/env python3
"""Tell Tj something through the Vigilant phone app, or ask the phone to switch an API (TCL28; Tj 2026-10-15).

  python3 tools/tell_tj.py say "Title" "Body" [--kind recommendation|bet|alert|shipped|info] [--priority 0|1] [--action APIS]
  python3 tools/tell_tj.py set-api "reason" sgo=on parlay=off ...
  python3 tools/tell_tj.py chat-reply <message id> "answer"      (the chat; tools/chat_watch.sh prints Tj's new messages)
  python3 tools/tell_tj.py chat-ack <message id> [<id> ...]     (a thumbs-up under Tj's message: this session has read it; chat_watch.sh does it by itself)
  python3 tools/tell_tj.py chat-file <path on the branch> <out dir>   (download an attachment Tj added, from the chat-files branch: paths are printed by chat_watch.sh)
  python3 tools/tell_tj.py chat-keep <message id>               (flag the message's attachments important: the hourly cleanup deletes the rest after 24 h)
  python3 tools/tell_tj.py relay "<Tj's typed message>"          (capture_inbox.sh does this by itself when a message is typed in a session that does not hold the lease)
  python3 tools/tell_tj.py selftest

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
    for i in range(tries):
        git("fetch", "-q", remote, BRANCH, cwd=cwd, check=False)
        have = git("rev-parse", "-q", "--verify", "FETCH_HEAD", cwd=cwd, check=False)
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


def drop_old_notes(cwd=None, tries=5):
    """Remove Claude's own '(old message...' stand-in replies from chat/ (Tj, 2026-10-19: he does not want them shown). Returns how many files went."""
    for i in range(tries):
        git("fetch", "-q", "origin", BRANCH, cwd=cwd, check=False)
        names = [n for n in git("ls-tree", "--name-only", "FETCH_HEAD", "chat/", cwd=cwd).stdout.split() if n.endswith(".json")]
        gone = []
        for n in names:
            try:
                m = json.loads(git("show", "FETCH_HEAD:" + n, cwd=cwd).stdout)
            except Exception:
                continue
            if m.get("from") != "tj" and str(m.get("text", "")).startswith("(old message"):
                gone.append(n)
        if not gone:
            return 0
        with tempfile.TemporaryDirectory() as d:
            env = dict(os.environ, GIT_INDEX_FILE=os.path.join(d, "idx"))
            git("read-tree", "FETCH_HEAD", env=env, cwd=cwd)
            for n in gone:
                git("update-index", "--force-remove", n, env=env, cwd=cwd)
            tree = git("write-tree", env=env, cwd=cwd).stdout.strip()
            parent = git("rev-parse", "FETCH_HEAD", cwd=cwd).stdout.strip()
            cenv = dict(os.environ, GIT_AUTHOR_NAME="Claude", GIT_AUTHOR_EMAIL="noreply@anthropic.com", GIT_COMMITTER_NAME="Claude", GIT_COMMITTER_EMAIL="noreply@anthropic.com")
            commit = git("commit-tree", tree, "-m", "tell_tj: drop old-message notes", "-p", parent, env=cenv, cwd=cwd).stdout.strip()
        if git("push", "-q", "origin", "%s:refs/heads/%s" % (commit, BRANCH), cwd=cwd, check=False).returncode == 0:
            return len(gone)
        time.sleep(1 + i)
    raise RuntimeError("could not drop notes")


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
    """One reply in the app's chat thread (chat/ on cloud-config), answering Tj's message `reply_to`."""
    if SECRET.search(text):
        raise SystemExit("refused: the text looks like a key or token (the repo is public)")
    at = int(time.time() * 1000)
    ident = "c%d-%s" % (at // 1000, secrets.token_hex(2))
    m = {"id": ident, "at": at, "from": who or me(), "text": text, "replyTo": reply_to}
    put("chat/" + fname(at, ident), json.dumps(m), **kw)
    return ident


def chat_relay(text, who=None, **kw):
    """Tj typed a request in a session that does not hold the lease (Tj, 2026-10-18): put it into the chat as his message, so the session that holds the lease (its chat_watch.sh prints it as CHAT <id>)
    or, if none is watching, chat-wake.yml (wakes the on-duty account) reads it and starts at once. Marked with the relaying account so nobody mistakes it for the app."""
    if SECRET.search(text):
        raise SystemExit("refused: the text looks like a key or token (the repo is public)")
    at = int(time.time() * 1000)
    ident = "r%d-%s" % (at // 1000, secrets.token_hex(2))
    m = {"id": ident, "at": at, "from": "tj", "relayed": True, "text": "[typed to %s, relayed to whoever holds the lease] %s" % (who or me().replace("claude:", ""), text)}
    put("chat/" + fname(at, ident), json.dumps(m), **kw)
    return ident


def chat_ack(ids, who=None, **kw):
    """A read receipt for each of Tj's messages in `ids` (the app shows a thumbs-up under it). Idempotent per (message, account): a message already acknowledged by this account is skipped."""
    me_ = who or me()
    git("fetch", "-q", "origin", BRANCH, cwd=kw.get("cwd"), check=False)
    have = git("ls-tree", "-r", "--name-only", "FETCH_HEAD", cwd=kw.get("cwd"), check=False).stdout.split()
    done = 0
    for i in ids:
        tag = "ack-" + re.sub(r"[^A-Za-z0-9]", "", i)
        if any(tag in f for f in have if f.startswith("chat/")):
            continue
        at = int(time.time() * 1000)
        m = {"id": "a%d-%s" % (at // 1000, secrets.token_hex(2)), "at": at, "from": me_, "text": "\U0001F44D", "ack": i}
        put("chat/%013d-%s.json" % (at, tag[:24]), json.dumps(m), **kw)
        done += 1
    return done


def chat_file(path, out, cwd=None):
    """Download an attachment (a path under chat-files/) from the branch into `out`; prints where it landed."""
    if not path.startswith("chat-files/") or ".." in path:
        raise SystemExit("only chat-files/ paths")
    git("fetch", "-q", "origin", "chat-files", cwd=cwd)
    os.makedirs(out, exist_ok=True)
    dest = os.path.join(out, os.path.basename(path))
    r = subprocess.run(["git", "show", "FETCH_HEAD:" + path], capture_output=True, cwd=cwd)
    if r.returncode:
        raise SystemExit("not on the chat-files branch (deleted after 24 h unless flagged?): " + path)
    open(dest, "wb").write(r.stdout)
    return dest


def chat_keep(msg_id, who=None, **kw):
    """Flag the attachments of message `msg_id` as important: chat-cleanup.yml leaves them alone."""
    put("chat-keep/" + re.sub(r"[^A-Za-z0-9-]", "", msg_id)[:40], json.dumps({"id": msg_id, "by": who or me(), "at": int(time.time() * 1000)}), **kw)


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
        c = chat_reply("t1", "hello Tj", who="claude:test", cwd=work)
        cf = [f for f in git("ls-tree", "-r", "--name-only", BRANCH, cwd=bare).stdout.split() if f.startswith("chat/")]
        assert len(cf) == 1 and json.loads(git("show", "%s:%s" % (BRANCH, cf[0]), cwd=bare).stdout)["replyTo"] == "t1"
        r = chat_relay("please change X", who="acct-aaaaaa", cwd=work)
        rf = [f for f in git("ls-tree", "-r", "--name-only", BRANCH, cwd=bare).stdout.split() if r in f]
        rm = json.loads(git("show", "%s:%s" % (BRANCH, rf[0]), cwd=bare).stdout)
        assert rm["from"] == "tj" and rm["relayed"] and "please change X" in rm["text"] and "acct-aaaaaa" in rm["text"], rm
        a1 = chat_ack(["t1"], who="claude:test", cwd=work); a2 = chat_ack(["t1"], who="claude:test", cwd=work)
        assert (a1, a2) == (1, 0), (a1, a2)
        af = [f for f in git("ls-tree", "-r", "--name-only", BRANCH, cwd=bare).stdout.split() if f.startswith("chat/") and "ack-t1" in f]
        assert len(af) == 1 and json.loads(git("show", "%s:%s" % (BRANCH, af[0]), cwd=bare).stdout)["ack"] == "t1"
        chat_keep("t9", who="claude:test", cwd=work)
        assert "chat-keep/t9" in git("ls-tree", "-r", "--name-only", BRANCH, cwd=bare).stdout.split()
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
        print("sent", chat_reply(argv[1], " ".join(argv[2:]))); return 0
    if argv[0] == "relay" and len(argv) >= 2:
        print("relayed", chat_relay(" ".join(argv[1:]))); return 0
    if argv[0] == "chat-ack" and len(argv) >= 2:
        print("acked", chat_ack(argv[1:])); return 0
    if argv[0] == "chat-clean":
        print("removed", drop_old_notes()); return 0
    if argv[0] == "chat-file" and len(argv) >= 3:
        print(chat_file(argv[1], argv[2])); return 0
    if argv[0] == "chat-keep" and len(argv) >= 2:
        chat_keep(argv[1]); print("kept", argv[1]); return 0
    if argv[0] == "set-api" and len(argv) >= 3:
        print("sent", set_api(argv[1], argv[2:])); return 0
    print(__doc__); return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
