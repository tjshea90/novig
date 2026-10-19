#!/usr/bin/env python3
"""tools/chat.py - the Vigilant app chat, rebuilt from scratch (CR, Tj 2026-10-19).

ONE account answers the chat: research/coordination/chat.json (acct-5d1e2d, mirage). On the other two accounts every command here says so and does nothing.
ONE session of that account answers at a time: the seat (seat.json on the `chat-seat` branch, a heartbeat every 45 s). A second session waits quietly on standby and takes over
only when the first one stops, so two sessions can never both answer.

  python3 -I tools/chat.py watch                          the daemon: run it under Monitor (persistent). It prints Tj's new messages (lines starting CHAT), downloads their
                                                          attachments (it prints the local path: open it with Read) and writes the read receipts. Zero tokens while quiet.
  python3 -I tools/chat.py reply <id> [<id> ...] "text"   answer one or several of Tj's messages with one bubble (a follow-up to the same message is fine)
  python3 -I tools/chat.py done <id> [<id> ...]           a message that needs no words ("ok", "thanks"): a thumbs-up reaction instead of a bubble
  python3 -I tools/chat.py say "text"                     a message of Claude's own (a release link, a finished job)
  python3 -I tools/chat.py status working "building v1.0.6"   |   status idle      what the app's header says Claude is doing
  python3 -I tools/chat.py pending                        Tj's messages that have no answer yet (reads only)
  python3 -I tools/chat.py seat                           who answers the chat now
  python3 -I tools/chat.py file <message id> [dir]        download a message's attachments
  python3 -I tools/chat.py init                           create the `chat` and `chat-seat` branches (once; idempotent)
  python3 -I tools/chat.py selftest

The store is the `chat` branch, never rewritten and never cleaned (Tj's save-everything rule):
  msgs/<UTC day>/<id>.json         one immutable file per message: Tj's ids t<ms>-<hex>, Claude's c<ms>-<hex>; a reply names the ids it answers in replyTo (a list)
  msgs/<UTC day>/<id>.seen.json    the read receipt, written once, by the daemon, when the message was shown to the session (day = the message's own day)
  files/<message id>/<n>-<name>    attachments, uploaded by the app BEFORE the message that names them
Every git call here works in a private bare repo (~/.vigilant-chat/git.git, a partial clone: small blobs come with each fetch, big attachments only when opened) and reads only
refs of its own (refs/vigilant/*): nothing touches the session's checkout, index or FETCH_HEAD. Writes are plain pushes (compare-and-swap; a lost race re-reads and retries);
the seat is a single commit replaced with --force-with-lease. Until Tj's phone has the new app, messages from the old app (chat/ on cloud-config) are answered too, in the old format
(the legacy bridge, research/coordination/chat.json legacySince/legacyUntil). Words only: text that looks like a key or token is refused (the repo is public).
Design and the evidence for it: research/coordination/CHAT_REBUILD_2026-10-19.md; the rules for sessions: research/coordination/CHAT.md.
"""
import contextlib
import datetime
import difflib
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.environ.get("CHAT_REPO") or os.path.dirname(HERE)
sys.path.insert(0, HERE)

CHAT = os.environ.get("CHAT_BRANCH", "chat")
SEAT = os.environ.get("CHAT_SEAT_BRANCH", "chat-seat")
LEGACY = "cloud-config"
LEGACY_FILES = "chat-files"
BEAT_S = int(os.environ.get("CHAT_BEAT_S", 45))
STALE_S = int(os.environ.get("CHAT_STALE_S", 150))
POLL_S = float(os.environ.get("CHAT_POLL_S", 4))
COALESCE_S = float(os.environ.get("CHAT_COALESCE_S", 3))
WINDOW_DAYS = 14
DUP_RATIO = 0.9
DUP_WINDOW_MS = 3600 * 1000
OTHER_SESSION_MS = 10 * 60 * 1000
LEGACY_PRESENCE_MS = 4 * 60 * 1000
FILTER = "blob:limit=200k"
TJ_ID = re.compile(r"^t\d{13}-[0-9a-f]{4,8}$")
LEGACY_ID = re.compile(r"^t\d{13}$")
ANY_ID = re.compile(r"^[tc]\d{13}(-[0-9a-f]{4,8})?$")
SECRET = re.compile(r"(sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|[A-Fa-f0-9]{40,})")
EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"


class ChatError(Exception):
    """A refusal or failure worth one plain line to the session."""


def now_ms():
    return int(time.time() * 1000)


def day_of(at_ms):
    return time.strftime("%Y-%m-%d", time.gmtime(at_ms / 1000))


def hhmm(at_ms):
    return time.strftime("%H:%M UTC", time.gmtime(at_ms / 1000))


def ago(ms):
    s = max(0, int(ms / 1000))
    if s < 90:
        return "just now"
    if s < 5400:
        return "%d min ago" % (s // 60)
    if s < 172800:
        return "%.1f h ago" % (s / 3600)
    return "%d days ago" % (s // 86400)


# ---- configuration and identity ---------------------------------------------------------------------------------------------------------------------------------------------

def load_cfg():
    try:
        with open(os.path.join(REPO, "research", "coordination", "chat.json"), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"account": "acct-5d1e2d", "name": "mirage", "fire": "5D1E2D"}


def iso_ms(s):
    try:
        return int(datetime.datetime.fromisoformat(str(s).replace("Z", "+00:00")).timestamp() * 1000)
    except Exception:
        return None


def me():
    """(label, holder) of this session: label = acct-<6 hex>, holder = label/nonce. CHAT_ME=acct-x/nonce overrides (tests)."""
    if os.environ.get("CHAT_ME"):
        h = os.environ["CHAT_ME"]
        return h.split("/")[0], h
    try:
        import coord
        d = coord.identity()
        return d["label"], "%s/%s" % (d["label"], d["nonce"])
    except Exception:
        return "acct-unknown", "acct-unknown/" + secrets.token_hex(3)


def is_chat_account(label, cfg=None):
    return label == (cfg or load_cfg()).get("account")


def account_name(label, cfg=None):
    names = {"acct-5d1e2d": "mirage", "acct-7542bf": "tj outlook", "acct-27a37e": "tj Gmail"}
    return (cfg or {}).get("name") if label == (cfg or {}).get("account") else names.get(label, label)


def not_chat_account_line(label, cfg):
    return "CHAT this account (%s) does not answer the app chat; acct %s (%s) does (research/coordination/chat.json). Nothing to do here." % (
        label, cfg.get("account"), cfg.get("name"))


# ---- git, in a private bare repo ----------------------------------------------------------------------------------------------------------------------------------------------

class Git:
    def __init__(self, home=None, url=None, source=None):
        self.home = home or os.environ.get("CHAT_HOME") or os.path.join(os.path.expanduser("~"), ".vigilant-chat")
        self.dir = os.path.join(self.home, "git.git")
        src = source or REPO
        self.url = url or os.environ.get("CHAT_REMOTE_URL") or self._run(["git", "-C", src, "remote", "get-url", "origin"], check=False).strip()
        if not self.url:
            raise ChatError("no git remote to talk to")
        os.makedirs(self.home, exist_ok=True)
        if not os.path.isdir(os.path.join(self.dir, "objects")):
            self._run(["git", "init", "-q", "--bare", self.dir])
        cfg = {"remote.origin.url": self.url, "remote.origin.promisor": "true", "remote.origin.partialclonefilter": FILTER, "extensions.partialclone": "origin",
               "core.repositoryformatversion": "1", "protocol.version": "2", "gc.auto": "0"}
        for k, v in cfg.items():
            if self.g("config", "--get", k, check=False).strip() != v:
                self.g("config", k, v)
        # On GitHub Actions the checkout's token lives in its own config (actions/checkout): lend it to this repo so pushes work there too.
        hdr = self._run(["git", "-C", src, "config", "--get", "http.https://github.com/.extraheader"], check=False).strip()
        if hdr and self.g("config", "--get", "http.https://github.com/.extraheader", check=False).strip() != hdr:
            self.g("config", "http.https://github.com/.extraheader", hdr)

    @staticmethod
    def _run(args, inp=None, check=True, env=None, binary=False, timeout=90):
        r = subprocess.run(args, input=inp, capture_output=True, env=env, timeout=timeout, text=not binary)
        if check and r.returncode:
            err = r.stderr if not binary else r.stderr.decode("utf-8", "replace")
            raise ChatError("git %s: %s" % (" ".join(args[3:6]), err.strip()[:300]))
        return r.stdout

    def g(self, *a, inp=None, check=True, env=None, binary=False, timeout=90):
        return self._run(["git", "--git-dir", self.dir] + list(a), inp=inp, check=check, env=env, binary=binary, timeout=timeout)

    def ls_remote(self, *branches):
        out = self.g("ls-remote", "origin", *["refs/heads/" + b for b in branches], timeout=30)
        heads = {}
        for line in out.splitlines():
            sha, _, ref = line.partition("\t")
            heads[ref.replace("refs/heads/", "")] = sha
        return heads

    def fetch(self, branch, filt=None):
        """Fetch a branch into refs/vigilant/<branch>. Returns its sha, or None when the branch does not exist. The old store (cloud-config, a copy of the whole repo) comes
        without blobs and history (its trees only, so a push needs nothing lazily fetched); the chat branch with its small blobs (big attachments arrive when opened)."""
        filt = filt or ("blob:none" if branch == LEGACY else FILTER)
        extra = ["--depth=1"] if branch == LEGACY else []
        r = subprocess.run(["git", "--git-dir", self.dir, "-c", "protocol.version=2", "fetch", "-q", "--no-tags", "--filter=" + filt] + extra + ["origin",
                            "+refs/heads/%s:refs/vigilant/%s" % (branch, branch)], capture_output=True, text=True, timeout=120)
        if r.returncode:
            if "couldn't find remote ref" in r.stderr or "could not find remote ref" in r.stderr:
                return None
            raise ChatError("fetch %s: %s" % (branch, r.stderr.strip()[:200]))
        return self.ref(branch)

    def ref(self, branch):
        out = self.g("rev-parse", "-q", "--verify", "refs/vigilant/%s^{commit}" % branch, check=False).strip()
        return out or None

    def tree_entries(self, tree):
        """name -> (mode, type, sha) of one tree (lazily fetched in a partial clone)."""
        out = {}
        if tree == EMPTY_TREE:
            return out
        for line in self.g("ls-tree", "-z", tree).split("\0"):
            if not line:
                continue
            meta, _, name = line.partition("\t")
            mode, typ, sha = meta.split()
            out[name] = (mode, typ, sha)
        return out

    def subtree(self, commit, path):
        """The tree sha at `path` in `commit` (path '' = the root), or None."""
        if not commit:
            return None
        sha = self.g("rev-parse", "-q", "--verify", "%s^{tree}" % commit, check=False).strip()
        for part in [p for p in path.split("/") if p]:
            if not sha:
                return None
            e = self.tree_entries(sha).get(part)
            if not e or e[1] != "tree":
                return None
            sha = e[2]
        return sha or None

    def listdir(self, commit, path):
        t = self.subtree(commit, path)
        return self.tree_entries(t) if t else {}

    def blob(self, sha):
        return self.g("cat-file", "blob", sha, binary=True)

    def hash_blob(self, data):
        if isinstance(data, str):
            data = data.encode("utf-8")
        return self.g("hash-object", "-w", "--stdin", inp=data, binary=True).decode().strip()

    def mktree(self, entries):
        if not entries:
            return EMPTY_TREE
        text = "".join("%s %s %s\t%s\0" % (m, t, s, n) for n, (m, t, s) in sorted(entries.items()))
        return self.g("mktree", "-z", "--missing", inp=text).strip()

    def put_path(self, tree, parts, blob_sha):
        entries = self.tree_entries(tree) if tree else {}
        if len(parts) == 1:
            entries[parts[0]] = ("100644", "blob", blob_sha)
        else:
            sub = entries.get(parts[0])
            new_sub = self.put_path(sub[2] if sub and sub[1] == "tree" else None, parts[1:], blob_sha)
            entries[parts[0]] = ("040000", "tree", new_sub)
        return self.mktree(entries)

    def commit(self, tree, parent, message):
        env = dict(os.environ, GIT_AUTHOR_NAME="Claude", GIT_AUTHOR_EMAIL="noreply@anthropic.com", GIT_COMMITTER_NAME="Claude", GIT_COMMITTER_EMAIL="noreply@anthropic.com")
        args = ["commit-tree", tree, "-m", message] + (["-p", parent] if parent else [])
        return self.g(*args, env=env).strip()

    def push(self, commit, branch, lease=None):
        args = ["push", "-q", "origin", "%s:refs/heads/%s" % (commit, branch)]
        if lease is not None:
            args.insert(2, "--force-with-lease=refs/heads/%s:%s" % (branch, lease))
        r = subprocess.run(["git", "--git-dir", self.dir] + args, capture_output=True, text=True, timeout=60)
        return r.returncode == 0, r.stderr.strip()

    def add_files(self, branch, files, message, tries=8):
        """One commit adding/replacing `files` (path -> bytes|str) on `branch`, pushed without force; a lost race re-fetches and tries again. Returns the commit sha."""
        last = ""
        for i in range(tries):
            head = self.fetch(branch)
            tree = self.subtree(head, "") if head else None
            for path, data in files.items():
                tree = self.put_path(tree, [p for p in path.split("/") if p], self.hash_blob(data))
            c = self.commit(tree, head, message)
            ok, last = self.push(c, branch)
            if ok:
                self.g("update-ref", "refs/vigilant/" + branch, c)
                return c
            time.sleep(min(6, 0.6 * (i + 1)) + secrets.randbelow(400) / 1000)
        raise ChatError("could not write to %s after %d tries: %s" % (branch, tries, last[:200]))


@contextlib.contextmanager
def lock(g):
    """One writer at a time inside this container (the daemon and a reply may run at once)."""
    import fcntl
    path = os.path.join(g.home, "write.lock")
    with open(path, "w") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(f, fcntl.LOCK_UN)


# ---- pure logic (selftest covers it) -------------------------------------------------------------------------------------------------------------------------------------------

def is_tj(m):
    return m.get("from") == "tj"


def answers(m):
    r = m.get("replyTo")
    if isinstance(r, list):
        return [x for x in r if x]
    return [r] if r else []


def unanswered(msgs):
    """Tj's messages no Claude reply (or 'done' reaction) names yet, oldest first."""
    done = set()
    for m in msgs:
        if not is_tj(m):
            done.update(answers(m))
    return sorted([m for m in msgs if is_tj(m) and m.get("id") not in done], key=lambda m: m.get("at", 0))


def norm(text):
    return " ".join(re.sub(r"[^\w\s]", " ", str(text).lower()).split())


def near_duplicate(text, msgs, at, window_ms=DUP_WINDOW_MS, ratio=DUP_RATIO):
    """The Claude message of the last hour that `text` almost repeats, or None (Tj, 2026-10-19: back-to-back near-identical messages)."""
    a = norm(text)
    if not a:
        return None
    for m in sorted([m for m in msgs if not is_tj(m) and m.get("text") and at - m.get("at", 0) <= window_ms], key=lambda m: -m.get("at", 0)):
        b = norm(m["text"])
        if a == b or difflib.SequenceMatcher(None, a, b).ratio() >= ratio:
            return m
    return None


def seat_verdict(seat, holder, at, stale_s=STALE_S):
    """free | mine | stale | other for the seat dict (seat.json's 'seat')."""
    if not seat or not seat.get("holder"):
        return "free"
    if seat["holder"] == holder:
        return "mine"
    if at - int(seat.get("beat", 0)) > stale_s * 1000:
        return "stale"
    return "other"


def batch_lines(msgs, at, shown_before=()):
    """The lines the daemon prints for Tj's messages `msgs` (already sorted)."""
    if not msgs:
        return []
    ids = " ".join(m["id"] for m in msgs)
    head = "CHAT %d message%s from Tj%s. Answer with ONE reply: python3 -I tools/chat.py reply %s \"...\"" % (
        len(msgs), "" if len(msgs) == 1 else "s", "" if len(msgs) == 1 else " (sent together)", ids)
    out = [head]
    for m in msgs:
        age = at - int(m.get("at", at))
        late = ""
        if age > 30 * 60 * 1000:
            late = "  [sent %s: answer it now, say sorry for the wait in a few words; check TASKS.md/INBOX.md first in case it was already handled]" % ago(age)
        if m["id"] in shown_before:
            late += "  [shown before, still unanswered]"
        text = " / ".join(l.strip() for l in str(m.get("text", "")).splitlines() if l.strip())[:3000]
        out.append("CHAT [%s] %s (%s): %s%s%s" % (m["id"], hhmm(m.get("at", at)), ago(age), text or "(no words)", late, "  [old app]" if m.get("_legacy") else ""))
        for f in m.get("_local_files", []):
            out.append("CHAT   attachment: %s" % f)
        for f in m.get("_missing_files", []):
            out.append("CHAT   attachment NOT found on GitHub: %s" % f)
    return out


# ---- the store -------------------------------------------------------------------------------------------------------------------------------------------------------------

class Store:
    def __init__(self, g):
        self.g = g
        self.cache = {}  # blob sha -> parsed json

    def _json(self, sha):
        if sha not in self.cache:
            try:
                self.cache[sha] = json.loads(self.g.blob(sha).decode("utf-8"))
            except Exception:
                self.cache[sha] = None
        return self.cache[sha]

    def load(self, head=None, days=WINDOW_DAYS, at=None):
        """(messages, receipts) of the last `days` UTC days on the chat branch at `head` (default: the last fetch)."""
        head = head or self.g.ref(CHAT)
        at = at or now_ms()
        if not head:
            return [], set()
        wanted = {day_of(at - i * 86400 * 1000) for i in range(days)}
        msgs, seen = [], set()
        for d, (mode, typ, sha) in self.g.listdir(head, "msgs").items():
            if typ != "tree" or d not in wanted:
                continue
            for name, (_, t2, s2) in self.g.tree_entries(sha).items():
                if t2 != "blob":
                    continue
                if name.endswith(".seen.json"):
                    seen.add(name[:-len(".seen.json")])
                elif name.endswith(".json"):
                    m = self._json(s2)
                    if isinstance(m, dict) and m.get("id"):
                        m = dict(m, _day=d, _path="msgs/%s/%s" % (d, name))
                        msgs.append(m)
        msgs.sort(key=lambda m: m.get("at", 0))
        return msgs, seen

    def file_blob(self, path, branch=CHAT):
        parts = path.split("/")
        head = self.g.ref(branch)
        t = self.g.subtree(head, "/".join(parts[:-1]))
        e = self.g.tree_entries(t).get(parts[-1]) if t else None
        return e[2] if e and e[1] == "blob" else None


def safe_name(name):
    keep = "".join(c if (c.isalnum() or c in ".-_") else "_" for c in str(name))
    return keep[-80:] or "file"


def download(g, store, m, out_dir=None):
    """Attachments of message m into out_dir (default ~/.vigilant-chat/files/<id>/). Returns (local paths, missing paths)."""
    out_dir = out_dir or os.path.join(g.home, "files", re.sub(r"[^A-Za-z0-9-]", "", m["id"]))
    got, missing = [], []
    for f in m.get("files") or []:
        path = f.get("path") if isinstance(f, dict) else str(f)
        branch = LEGACY_FILES if path.startswith("chat-files/") else CHAT
        if branch == LEGACY_FILES and not g.ref(LEGACY_FILES):
            try:
                g.fetch(LEGACY_FILES)
            except ChatError:
                pass
        sha = store.file_blob(path, branch)
        if not sha:
            missing.append(path)
            continue
        try:
            data = g.blob(sha)
        except ChatError:
            missing.append(path)
            continue
        os.makedirs(out_dir, exist_ok=True)
        dest = os.path.join(out_dir, safe_name(path.split("/")[-1]))
        with open(dest, "wb") as fh:
            fh.write(data)
        kb = max(1, len(data) // 1024)
        got.append("%s (%d KB%s)" % (dest, kb, ", " + f["type"] if isinstance(f, dict) and f.get("type") else ""))
    return got, missing


# ---- the seat ---------------------------------------------------------------------------------------------------------------------------------------------------------------

def read_seat(g, fetch=True):
    head = g.fetch(SEAT) if fetch else g.ref(SEAT)
    if not head:
        return {"v": 2}, None
    e = g.listdir(head, "").get("seat.json")
    try:
        return json.loads(g.blob(e[2]).decode("utf-8")), head
    except Exception:
        return {"v": 2}, head


def write_seat(g, mutate, tries=8):
    """Read-modify-write seat.json as ONE parentless commit, swapped in with --force-with-lease (so the branch never grows). mutate(doc) returns the new doc, or None to leave it."""
    for i in range(tries):
        doc, head = read_seat(g)
        new = mutate(json.loads(json.dumps(doc)))
        if new is None:
            return doc
        new["v"] = 2
        tree = g.mktree({"seat.json": ("100644", "blob", g.hash_blob(json.dumps(new, indent=1) + "\n")),
                         "README.md": ("100644", "blob", g.hash_blob(SEAT_README))})
        c = g.commit(tree, None, "seat")
        ok, err = g.push(c, SEAT, lease=head or "")
        if ok:
            g.g("update-ref", "refs/vigilant/" + SEAT, c)
            return new
        time.sleep(0.5 + 0.5 * i + secrets.randbelow(300) / 1000)
    raise ChatError("could not update the seat: " + err[:200])


SEAT_README = ("# chat-seat\n\nWho answers the Vigilant app chat right now (`seat.json`: holder, heartbeat) and the last time GitHub woke Claude for it. ONE commit, replaced in place "
               "(--force-with-lease) by tools/chat.py and tools/chat_wake.py; the chat itself is on the `chat` branch. Rules: research/coordination/CHAT.md on main.\n")
CHAT_README = ("# chat\n\nThe Vigilant app chat (rebuilt 2026-10-19). One file per message under msgs/<UTC day>/, read receipts beside them (<id>.seen.json), attachments under "
               "files/<message id>/. Written by the phone app and by tools/chat.py; never rewritten, never cleaned. Rules: research/coordination/CHAT.md on main.\n")


def take_seat(g, label, holder, cfg, at=None, steal=False):
    """Take (or keep) the seat. Returns (verdict before, seat doc after or None when another live session has it)."""
    at = at or now_ms()
    out = {}

    def m(doc):
        v = seat_verdict(doc.get("seat"), holder, at)
        out["v"] = v
        out["other"] = doc.get("seat")
        if v == "other" and not steal:
            return None
        s = doc.get("seat") or {}
        since = s.get("since", at) if v == "mine" else at
        doc["seat"] = {"holder": holder, "account": label, "name": cfg.get("name"), "since": since, "beat": at,
                       "state": s.get("state", "idle") if v == "mine" else "idle", "note": s.get("note", "") if v == "mine" else "", "noteAt": s.get("noteAt", 0) if v == "mine" else 0}
        return doc
    doc = write_seat(g, m)
    return out.get("v"), (doc if out.get("v") != "other" or steal else None), out.get("other")


def seat_line(doc, at=None):
    at = at or now_ms()
    s = (doc or {}).get("seat") or {}
    if not s.get("holder"):
        return "nobody answers the chat right now (the next message of Tj's wakes the chat account)"
    age = at - int(s.get("beat", 0))
    state = "live" if age <= STALE_S * 1000 else "STOPPED (no heartbeat %s)" % ago(age)
    note = (", working: " + s["note"]) if s.get("state") == "working" and s.get("note") else ""
    return "%s (%s) holds the chat seat since %s, %s%s" % (s["holder"], s.get("name") or "?", hhmm(int(s.get("since", 0))), state, note)


# ---- the old store (the old app, until Tj updates it) -------------------------------------------------------------------------------------------------------------------------

def legacy_window(cfg, at=None):
    at = at or now_ms()
    since, until = iso_ms(cfg.get("legacySince")), iso_ms(cfg.get("legacyUntil"))
    if since is None or until is None or at > until:
        return None
    return since


def legacy_load(g, since):
    """Tj's messages (and the replies/acks to them) in chat/ on cloud-config written after `since`. Old format, marked _legacy."""
    head = g.ref(LEGACY)
    if not head:
        return [], False
    names = g.listdir(head, "chat")
    msgs = []
    store = Store(g)
    for name, (_, typ, sha) in sorted(names.items()):
        if typ != "blob" or not name.endswith(".json"):
            continue
        try:
            at = int(name[:13])
        except ValueError:
            continue
        if at < since - 3600 * 1000:
            continue
        m = store._json(sha)
        if isinstance(m, dict) and m.get("id"):
            msgs.append(dict(m, _legacy=True))
    presence = False
    t = now_ms()
    for name, (_, typ, sha) in g.listdir(head, "presence").items():
        p = store._json(sha) if typ == "blob" else None
        if isinstance(p, dict) and t - int(p.get("at", 0)) < LEGACY_PRESENCE_MS:
            presence = True
    return msgs, presence


def legacy_pending(msgs, since):
    acked = {m.get("ack") for m in msgs if m.get("ack")}
    done = {m.get("replyTo") for m in msgs if m.get("replyTo") and not is_tj(m)}
    out = [m for m in msgs if is_tj(m) and not m.get("ack") and not m.get("relayed") and m.get("at", 0) >= since and m["id"] not in done]
    for m in out:
        m["_acked"] = m["id"] in acked
    return sorted(out, key=lambda m: m.get("at", 0))


def legacy_name(at, ident):
    return "%013d-%s.json" % (at, re.sub(r"[^A-Za-z0-9-]", "", ident)[:24])


# ---- commands -------------------------------------------------------------------------------------------------------------------------------------------------------------

def check_words(text):
    if SECRET.search(text or ""):
        raise ChatError("not sent: the text looks like a key or token (the repo is public)")


def who_string(label, cfg):
    return "claude:%s (%s)" % (label, account_name(label, cfg))


def ensure_seat_for_writing(g, label, holder, cfg):
    v, doc, other = take_seat(g, label, holder, cfg)
    if doc is None:
        raise ChatError("not sent: another session answers the chat (%s). It has the seat; this session must not answer. (chat.py seat)" % seat_line({"seat": other}))
    return doc


def cmd_reply(ids, text, done=False, again=False, g=None, at=None):
    cfg = load_cfg()
    label, holder = me()
    if not is_chat_account(label, cfg):
        raise ChatError(not_chat_account_line(label, cfg))
    if not ids:
        raise ChatError("name the message id(s) you answer (they are printed in [brackets] by chat.py watch)")
    if not done and not str(text).strip():
        raise ChatError("nothing to send")
    check_words(text)
    g = g or Git()
    at = at or now_ms()
    with lock(g):
        g.fetch(CHAT)
        store = Store(g)
        msgs, _ = store.load(at=at)
        by_id = {m["id"]: m for m in msgs}
        new_ids = [i for i in ids if i in by_id]
        leg_ids = [i for i in ids if i not in by_id and LEGACY_ID.match(i)]
        unknown = [i for i in ids if i not in by_id and i not in leg_ids]
        lmsgs = []
        if leg_ids:
            g.fetch(LEGACY)
            since = legacy_window(cfg, at)
            lmsgs, _ = legacy_load(g, since or 0) if since is not None else ([], False)
            known = {m["id"] for m in lmsgs if is_tj(m)}
            unknown += [i for i in leg_ids if i not in known]
            leg_ids = [i for i in leg_ids if i in known]
        if unknown:
            raise ChatError("not sent: no message of Tj's has the id %s (copy the id from the CHAT line)" % ", ".join(unknown))
        if any(not is_tj(by_id[i]) for i in new_ids):
            raise ChatError("not sent: answer Tj's messages (t...), not Claude's")
        if not done and not again:
            dup = near_duplicate(text, msgs + [m for m in lmsgs if not is_tj(m)], at)
            if dup:
                raise ChatError("not sent: almost the same as what was sent %s (%s). Two near-identical messages are what Tj complained about. Add --again only if it must be repeated." % (
                    ago(at - dup.get("at", at)), dup.get("id")))
        # One session answers: another session's reply to the same message in the last 10 minutes means two sessions are at it.
        for i in new_ids:
            for m in msgs:
                if not is_tj(m) and i in answers(m) and m.get("holder") and m.get("holder") != holder and at - m.get("at", 0) < OTHER_SESSION_MS:
                    raise ChatError("not sent: %s was already answered %s by another session (%s)" % (i, ago(at - m["at"]), m.get("holder")))
        ensure_seat_for_writing(g, label, holder, cfg)
        sent = []
        if new_ids:
            cid = "c%d-%s" % (at, secrets.token_hex(2))
            m = {"v": 2, "id": cid, "at": at, "from": "claude", "account": label, "name": account_name(label, cfg), "holder": holder, "replyTo": new_ids,
                 "kind": "done" if done else "reply", "text": "" if done else str(text).strip()}
            g.add_files(CHAT, {"msgs/%s/%s.json" % (day_of(at), cid): json.dumps(m, ensure_ascii=False)}, "chat: %s %s" % ("done" if done else "reply", cid))
            sent.append(cid)
        if leg_ids:
            files = {}
            who = who_string(label, cfg)
            if not done:
                lid = "c%d-%s" % (at // 1000, secrets.token_hex(2))
                files["chat/" + legacy_name(at, lid)] = json.dumps({"id": lid, "at": at, "from": who, "text": str(text).strip(), "replyTo": leg_ids[-1]})
                sent.append(lid)
            have = {m.get("ack") for m in lmsgs if m.get("ack")}
            for i in (leg_ids if done else leg_ids[:-1]):
                if i not in have:
                    files["chat/%013d-ack-%s.json" % (at, re.sub(r"[^A-Za-z0-9]", "", i)[:20])] = json.dumps({"id": "a%d-%s" % (at // 1000, secrets.token_hex(2)), "at": at, "from": who, "text": "\U0001F44D", "ack": i})
            if done:
                # the old app has no 'done': a reply naming the message closes it there, the thumbs-up shows it was read
                for i in leg_ids:
                    did = "c%d-%s" % (at // 1000, secrets.token_hex(2))
                    files["chat/" + legacy_name(at + 1, did)] = json.dumps({"id": did, "at": at + 1, "from": who, "text": "\U0001F44D", "replyTo": i})
            g.add_files(LEGACY, files, "chat (old app): reply")
    return sent


def cmd_say(text, again=False, g=None, at=None):
    cfg = load_cfg()
    label, holder = me()
    if not is_chat_account(label, cfg):
        raise ChatError(not_chat_account_line(label, cfg))
    if not str(text).strip():
        raise ChatError("nothing to send")
    check_words(text)
    g = g or Git()
    at = at or now_ms()
    with lock(g):
        g.fetch(CHAT)
        msgs, _ = Store(g).load(at=at)
        if not again:
            dup = near_duplicate(text, msgs, at)
            if dup:
                raise ChatError("not sent: almost the same as what was sent %s (%s); add --again only if it must be repeated" % (ago(at - dup.get("at", at)), dup.get("id")))
        ensure_seat_for_writing(g, label, holder, cfg)
        cid = "c%d-%s" % (at, secrets.token_hex(2))
        m = {"v": 2, "id": cid, "at": at, "from": "claude", "account": label, "name": account_name(label, cfg), "holder": holder, "replyTo": [], "kind": "say", "text": str(text).strip()}
        g.add_files(CHAT, {"msgs/%s/%s.json" % (day_of(at), cid): json.dumps(m, ensure_ascii=False)}, "chat: say " + cid)
        # Until Tj's phone has the new app (no message of his on the new store yet), the old app gets it too.
        if legacy_window(cfg, at) is not None and not any(is_tj(x) for x in msgs):
            lid = "c%d-%s" % (at // 1000, secrets.token_hex(2))
            g.add_files(LEGACY, {"chat/" + legacy_name(at, lid): json.dumps({"id": lid, "at": at, "from": who_string(label, cfg), "text": str(text).strip(), "replyTo": None})}, "chat (old app): say")
    return cid


def set_state(g, holder, state, note, quiet=False):
    at = now_ms()

    def m(doc):
        s = doc.get("seat") or {}
        if s.get("holder") != holder:
            return None
        s.update({"state": state, "note": str(note)[:120], "noteAt": at, "beat": at})
        doc["seat"] = s
        return doc
    try:
        return write_seat(g, m)
    except ChatError:
        if not quiet:
            raise


def cmd_status(state, note=""):
    cfg = load_cfg()
    label, holder = me()
    if not is_chat_account(label, cfg):
        raise ChatError(not_chat_account_line(label, cfg))
    if state not in ("working", "idle"):
        raise ChatError("status working \"what\" | status idle")
    check_words(note)
    g = Git()
    with lock(g):
        ensure_seat_for_writing(g, label, holder, cfg)
        set_state(g, holder, state, note)
    return state


def cmd_init(g=None):
    """Create the chat branch (README + the wake workflow) and the seat branch, once."""
    g = g or Git()
    out = []
    with lock(g):
        if not g.fetch(CHAT):
            wf = open(os.path.join(HERE, "chat-wake", "chat-wake.yml"), encoding="utf-8").read()
            tree = g.put_path(None, ["README.md"], g.hash_blob(CHAT_README))
            tree = g.put_path(tree, [".github", "workflows", "chat-wake.yml"], g.hash_blob(wf))
            c = g.commit(tree, None, "chat: new store (CR, 2026-10-19)")
            ok, err = g.push(c, CHAT)
            if not ok:
                raise ChatError("could not create the chat branch: " + err[:200])
            out.append("created branch chat")
        if not read_seat(g)[1]:
            write_seat(g, lambda d: d)
            out.append("created branch chat-seat")
    return out or ["already there"]


def deploy_workflow(g=None):
    """Put the current tools/chat-wake/chat-wake.yml on the chat branch (when it changed)."""
    g = g or Git()
    wf = open(os.path.join(HERE, "chat-wake", "chat-wake.yml"), encoding="utf-8").read()
    with lock(g):
        head = g.fetch(CHAT)
        cur = g.listdir(head, ".github/workflows").get("chat-wake.yml")
        if cur and g.blob(cur[2]).decode("utf-8") == wf:
            return "unchanged"
        g.add_files(CHAT, {".github/workflows/chat-wake.yml": wf}, "chat: wake workflow")
    return "deployed"


def post_system(text, who="claude:github", cwd=None, g=None, at=None):
    """A message from GitHub's side (the dead-man check-in): no seat, no chat account needed. Into the new store, and the old app's while the switch lasts."""
    check_words(text)
    at = at or now_ms()
    g = g or Git(home=os.path.join(os.environ.get("RUNNER_TEMP") or os.path.join(os.path.expanduser("~"), ".vigilant-chat"), "system"), source=cwd or REPO)
    cid = "c%d-%s" % (at, secrets.token_hex(2))
    m = {"v": 2, "id": cid, "at": at, "from": "claude", "account": "github", "name": who.replace("claude:", ""), "replyTo": [], "kind": "system", "text": str(text).strip()}
    with lock(g):
        g.add_files(CHAT, {"msgs/%s/%s.json" % (day_of(at), cid): json.dumps(m, ensure_ascii=False)}, "chat: system " + cid)
        if legacy_window(load_cfg(), at) is not None:
            lid = "c%d-%s" % (at // 1000, secrets.token_hex(2))
            g.add_files(LEGACY, {"chat/" + legacy_name(at, lid): json.dumps({"id": lid, "at": at, "from": who, "text": str(text).strip(), "replyTo": None})}, "chat (old app): system")
    return cid


def last_tj_at(g=None, cwd=None):
    """When Tj last wrote in the new chat (ms; 0 = never in the last two weeks). The dead-man switch counts it as presence."""
    g = g or Git(home=os.path.join(os.environ.get("RUNNER_TEMP") or os.path.join(os.path.expanduser("~"), ".vigilant-chat"), "system"), source=cwd or REPO)
    if not g.fetch(CHAT):
        return 0
    msgs, _ = Store(g).load()
    return max([int(m.get("at", 0)) for m in msgs if is_tj(m)] or [0])


# ---- the daemon -----------------------------------------------------------------------------------------------------------------------------------------------------------

def say_line(*parts):
    print(" ".join(str(p) for p in parts), flush=True)


class Daemon:
    def __init__(self, g, label, holder, cfg, out=say_line, clock=now_ms):
        self.g, self.label, self.holder, self.cfg, self.out, self.clock = g, label, holder, cfg, out, clock
        self.store = Store(g)
        self.holding = False
        self.standby_said = False
        self.last_beat = 0
        self.last_heads = {}
        self.shown = set()
        self.shown_before = set(self._load_state().get("shown", []))
        self.fail_since = None

    def _state_path(self):
        return os.path.join(self.g.home, "shown.json")

    def _load_state(self):
        try:
            with open(self._state_path(), encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}

    def _save_state(self):
        try:
            with open(self._state_path(), "w", encoding="utf-8") as f:
                json.dump({"shown": sorted(self.shown | self.shown_before)[-500:]}, f)
        except Exception:
            pass

    def seat_step(self):
        at = self.clock()
        if self.holding and at - self.last_beat < BEAT_S * 1000:
            return
        if not self.holding and at - self.last_beat < 15 * 1000:
            return
        self.last_beat = at
        with lock(self.g):
            v, doc, other = take_seat(self.g, self.label, self.holder, self.cfg, at=at)
        if doc is None:
            if self.holding:
                self.out("CHAT the chat moved to another session (%s): this one stops answering and stays on standby." % seat_line({"seat": other}, at))
                self.holding = False
            elif not self.standby_said:
                self.out("CHAT standby: %s. This session stays quiet and takes over by itself if that one stops." % seat_line({"seat": other}, at))
                self.standby_said = True
            return
        if not self.holding:
            self.holding = True
            self.standby_said = False
            self.last_heads = {}
            self.out("CHAT this session answers the app chat now (the seat is yours; %s). Every message of Tj's appears here as CHAT lines; answer with python3 -I tools/chat.py reply <id> \"...\". "
                     "Rules: research/coordination/CHAT.md." % ("it was free" if v == "free" else "the last holder stopped" if v == "stale" else "kept"))

    def pending(self, at):
        msgs, seen = self.store.load(at=at)
        todo = unanswered(msgs)
        since = legacy_window(self.cfg, at)
        if since is not None:
            lmsgs, presence = legacy_load(self.g, since)
            if not presence:   # an old-style watcher is alive somewhere: it answers the old app; this one stays out to avoid two answers
                todo += legacy_pending(lmsgs, since)
        return sorted(todo, key=lambda m: m.get("at", 0)), seen

    def scan(self, force=False):
        """One look: fetch what changed, print the new unanswered messages as one batch, write receipts. Returns the ids printed."""
        at = self.clock()
        branches = [CHAT] + ([LEGACY] if legacy_window(self.cfg, at) is not None else [])
        heads = self.g.ls_remote(*branches)
        if not force and all(heads.get(b) == self.last_heads.get(b) for b in branches):
            return []
        with lock(self.g):
            for b in branches:
                if heads.get(b) and heads.get(b) != self.last_heads.get(b):
                    self.g.fetch(b)
        self.last_heads = heads
        todo, _ = self.pending(at)
        fresh = [m for m in todo if m["id"] not in self.shown]
        if not fresh:
            return []
        # Messages sent together are shown together: wait a moment for the rest, then look once more.
        time.sleep(COALESCE_S)
        with lock(self.g):
            for b in branches:
                self.g.fetch(b)
        self.last_heads = self.g.ls_remote(*branches)
        at = self.clock()
        todo, seen = self.pending(at)
        fresh = [m for m in todo if m["id"] not in self.shown]
        for m in fresh:
            m["_local_files"], m["_missing_files"] = download(self.g, self.store, m)
        for line in batch_lines(fresh, at, self.shown_before):
            self.out(line)
        self.shown.update(m["id"] for m in fresh)
        self._save_state()
        self.receipts(fresh, seen, at)
        return [m["id"] for m in fresh]

    def receipts(self, msgs, seen, at):
        files, old = {}, {}
        for m in msgs:
            if m.get("_legacy"):
                if not m.get("_acked"):
                    old["chat/%013d-ack-%s.json" % (at, re.sub(r"[^A-Za-z0-9]", "", m["id"])[:20])] = json.dumps(
                        {"id": "a%d-%s" % (at // 1000, secrets.token_hex(2)), "at": at, "from": who_string(self.label, self.cfg), "text": "\U0001F44D", "ack": m["id"]})
            elif m["id"] not in seen:
                files["msgs/%s/%s.seen.json" % (m.get("_day") or day_of(m.get("at", at)), m["id"])] = json.dumps({"v": 2, "seen": m["id"], "at": at, "by": self.label, "holder": self.holder})
        with lock(self.g):
            if files:
                self.g.add_files(CHAT, files, "chat: seen %d" % len(files))
            if old:
                self.g.add_files(LEGACY, old, "chat (old app): seen")

    def step(self):
        self.seat_step()
        if self.holding:
            self.scan(force=not self.last_heads)

    def run(self):
        while True:
            try:
                self.step()
                self.fail_since = None
            except ChatError as e:
                at = self.clock()
                if self.fail_since is None:
                    self.fail_since = at
                elif at - self.fail_since > 120 * 1000:
                    self.out("CHAT GitHub has not answered for %s (%s); still trying every few seconds." % (ago(at - self.fail_since), str(e)[:120]))
                    self.fail_since = at
            except Exception as e:  # noqa: BLE001 - the daemon must not die on one bad file
                self.out("CHAT watcher error (%s: %s); carrying on." % (type(e).__name__, str(e)[:160]))
                time.sleep(10)
            time.sleep(POLL_S)


def cmd_watch():
    cfg = load_cfg()
    label, holder = me()
    if not is_chat_account(label, cfg):
        say_line(not_chat_account_line(label, cfg))
        return 0
    g = Git()
    Daemon(g, label, holder, cfg).run()
    return 0


def cmd_pending():
    cfg = load_cfg()
    label, holder = me()
    if not is_chat_account(label, cfg):
        print(not_chat_account_line(label, cfg))
        return 0
    g = Git()
    g.fetch(CHAT)
    if legacy_window(cfg) is not None:
        g.fetch(LEGACY)
    d = Daemon(g, label, holder, cfg, out=lambda *a: None)
    todo, _ = d.pending(now_ms())
    for line in batch_lines(todo, now_ms()) or ["CHAT nothing unanswered"]:
        print(line)
    return 0


def cmd_file(mid, out_dir=None):
    g = Git()
    g.fetch(CHAT)
    store = Store(g)
    msgs, _ = store.load()
    m = next((x for x in msgs if x["id"] == mid), None)
    if not m and LEGACY_ID.match(mid):
        g.fetch(LEGACY)
        lm, _ = legacy_load(g, 0)
        m = next((x for x in lm if x.get("id") == mid), None)
    if not m:
        raise ChatError("no message %s in the last %d days" % (mid, WINDOW_DAYS))
    got, missing = download(g, store, m, out_dir)
    for x in got:
        print("saved", x)
    for x in missing:
        print("missing on GitHub:", x)
    return 0


# ---- selftest (a bare repo stands in for GitHub) ----------------------------------------------------------------------------------------------------------------------------

def selftest():
    global COALESCE_S
    COALESCE_S = 0
    with tempfile.TemporaryDirectory() as d:
        origin = os.path.join(d, "origin.git")
        subprocess.run(["git", "init", "-q", "--bare", origin], check=True)
        subprocess.run(["git", "--git-dir", origin, "config", "uploadpack.allowFilter", "true"], check=True)
        subprocess.run(["git", "--git-dir", origin, "config", "uploadpack.allowAnySHA1InWant", "true"], check=True)
        cfg = {"account": "acct-aaaaaa", "name": "testname", "fire": "AAAAAA"}

        def git_for(name):
            return Git(home=os.path.join(d, name), url="file://" + origin, source=d)
        os.environ["CHAT_ME"] = "acct-aaaaaa/n1"
        global load_cfg
        real_cfg = load_cfg
        load_cfg = lambda: dict(cfg)   # noqa: E731
        try:
            g1 = git_for("s1")
            # init creates both branches (the workflow file comes from tools/chat-wake/)
            assert "created branch chat" in cmd_init(g1)
            assert cmd_init(g1) == ["already there"]
            phone = git_for("phone")
            t0 = now_ms()

            def tj(text, at, files=None):
                mid = "t%d-%s" % (at, secrets.token_hex(2))
                fs = {}
                meta = []
                for i, (n, data) in enumerate(files or []):
                    p = "files/%s/%d-%s" % (mid, i + 1, n)
                    fs[p] = data
                    meta.append({"path": p, "name": n, "size": len(data), "type": "image/png"})
                if fs:
                    phone.add_files(CHAT, fs, "attachments")
                phone.add_files(CHAT, {"msgs/%s/%s.json" % (day_of(at), mid): json.dumps({"v": 2, "id": mid, "at": at, "from": "tj", "text": text, "files": meta})}, "tj")
                return mid
            a = tj("hello claude", t0 - 5000)
            b = tj("and a screenshot", t0 - 4000, [("shot.png", b"\x89PNG" + os.urandom(300 * 1024))])
            lines = []
            dm = Daemon(g1, "acct-aaaaaa", "acct-aaaaaa/n1", cfg, out=lines.append)
            dm.seat_step()
            assert dm.holding and any("answers the app chat now" in l for l in lines), lines
            ids = dm.scan(force=True)
            assert ids == [a, b], ids
            assert any(("[%s]" % a) in l for l in lines) and any("attachment: " in l and "shot.png" in l for l in lines), lines
            assert lines[1].startswith("CHAT 2 messages from Tj (sent together)"), lines[1]
            # the big attachment came over on demand and is byte-identical on disk
            local = [l for l in lines if "attachment:" in l][0].split("attachment: ")[1].split(" (")[0]
            assert os.path.getsize(local) == 300 * 1024 + 4
            # receipts were written, once per message
            g1.fetch(CHAT)
            _, seen = Store(g1).load()
            assert seen == {a, b}, seen
            # nothing new: a second scan prints nothing
            n = len(lines)
            assert dm.scan(force=True) == [] and len(lines) == n
            # a second session of the same account stays on standby while the first holds the seat
            os.environ["CHAT_ME"] = "acct-aaaaaa/n2"
            g2 = git_for("s2")
            l2 = []
            d2 = Daemon(g2, "acct-aaaaaa", "acct-aaaaaa/n2", cfg, out=l2.append)
            d2.step()
            assert not d2.holding and any("standby" in l for l in l2), l2
            # ... and may not answer
            try:
                cmd_reply([a], "from the wrong session", g=g2)
                raise AssertionError("a session without the seat answered")
            except ChatError as e:
                assert "another session answers the chat" in str(e), e
            # the seat holder answers both messages with one reply
            os.environ["CHAT_ME"] = "acct-aaaaaa/n1"
            sent = cmd_reply([a, b], "Hi Tj, I see the screenshot.", g=g1)
            assert len(sent) == 1
            g1.fetch(CHAT)
            msgs, _ = Store(g1).load()
            assert unanswered(msgs) == [], unanswered(msgs)
            # a near-identical second message is refused; --again sends it
            try:
                cmd_say("Hi Tj, I see the screenshot!", g=g1)
                raise AssertionError("near-duplicate was sent")
            except ChatError as e:
                assert "almost the same" in str(e)
            cmd_say("Hi Tj, I see the screenshot!", again=True, g=g1)
            # unknown ids and keys are refused
            for bad in (lambda: cmd_reply(["t1234567890123-abcd"], "x", g=g1), lambda: cmd_say("token ghp_" + "a" * 30, g=g1)):
                try:
                    bad()
                    raise AssertionError("should refuse")
                except ChatError:
                    pass
            # 'done' closes a message without a bubble
            c = tj("thanks", now_ms())
            assert dm.scan(force=True) == [c]
            cmd_reply([c], "", done=True, g=g1)
            g1.fetch(CHAT)
            msgs, _ = Store(g1).load()
            assert unanswered(msgs) == [] and any(m.get("kind") == "done" for m in msgs)
            # a fresh session (new container) after the first one stopped: it takes over and prints only what is still unanswered
            e = tj("are you there?", now_ms())
            os.environ["CHAT_ME"] = "acct-aaaaaa/n3"
            g3 = git_for("s3")
            l3 = []
            later = now_ms() + (STALE_S + 5) * 1000
            d3 = Daemon(g3, "acct-aaaaaa", "acct-aaaaaa/n3", cfg, out=l3.append, clock=lambda: later)
            d3.seat_step()
            assert d3.holding and any("the last holder stopped" in l for l in l3), l3
            assert d3.scan(force=True) == [e], l3
            # the first daemon notices it lost the seat and stops answering
            dm.clock = lambda: later + BEAT_S * 1000 + 1
            lines.clear()
            dm.seat_step()
            assert not dm.holding and any("moved to another session" in l for l in lines), lines
            # another account never runs the chat
            os.environ["CHAT_ME"] = "acct-bbbbbb/x"
            try:
                cmd_reply([e], "hi", g=g3)
                raise AssertionError("another account answered")
            except ChatError as err:
                assert "does not answer the app chat" in str(err)
            # pure helpers
            assert seat_verdict(None, "h", 0) == "free" and seat_verdict({"holder": "h", "beat": 0}, "h", 10 ** 9) == "mine"
            assert seat_verdict({"holder": "x", "beat": 0}, "h", (STALE_S + 1) * 1000) == "stale" and seat_verdict({"holder": "x", "beat": 0}, "h", 1000) == "other"
            assert near_duplicate("On it: checking", [{"from": "claude", "text": "On it - checking!", "at": 0}], 1000) and not near_duplicate("Done", [{"from": "claude", "text": "On it", "at": 0}], 1000)
            old = [{"id": "t1", "from": "tj", "at": 10}, {"id": "t2", "from": "tj", "at": 20}, {"id": "c", "from": "claude", "replyTo": ["t1"], "at": 30}]
            assert [m["id"] for m in unanswered(old)] == ["t2"]
            assert "sent 2.0 h ago" in batch_lines([{"id": "t9", "at": 0, "text": "x"}], 7200 * 1000)[1]
            # the old app (chat/ on cloud-config) is answered in its own format during the switch, and only while no old-style watcher is alive
            os.environ["CHAT_ME"] = "acct-aaaaaa/n3"
            t1 = now_ms()
            cfg.update(legacySince=datetime.datetime.fromtimestamp((t1 - 60000) / 1000, datetime.timezone.utc).isoformat(),
                       legacyUntil=datetime.datetime.fromtimestamp((t1 + 86400000) / 1000, datetime.timezone.utc).isoformat())
            lid = "t%d" % t1
            phone.add_files(LEGACY, {"chat/%013d-%s.json" % (t1, lid): json.dumps({"id": lid, "at": t1, "from": "tj", "text": "old app here"}),
                                     "chat/%013d-r%d-ab.json" % (t1 - 120000, t1): json.dumps({"id": "r%d-ab" % t1, "at": t1 - 120000, "from": "tj", "relayed": True, "text": "[typed to x] y"})}, "old")
            l4 = []
            d3.out = l4.append
            d3.clock = now_ms
            d3.last_beat = 0
            d3.seat_step()
            assert d3.scan(force=True) == [lid], l4
            assert any("[old app]" in l for l in l4), l4
            cmd_reply([lid], "Answered in the old app.", g=g3)
            g3.fetch(LEGACY)
            old_msgs, _ = legacy_load(g3, 0)
            assert legacy_pending(old_msgs, iso_ms(cfg["legacySince"])) == [] and any(m.get("ack") == lid for m in old_msgs), old_msgs
            # a fresh old-style heartbeat (an old watcher is still running) keeps the bridge out of the old app
            lid2 = "t%d" % (t1 + 5)
            phone.add_files(LEGACY, {"chat/%013d-%s.json" % (t1 + 5, lid2): json.dumps({"id": lid2, "at": t1 + 5, "from": "tj", "text": "second"}),
                                     "presence/acct-aaaaaa.json": json.dumps({"at": now_ms(), "who": "old"})}, "old 2")
            assert d3.scan(force=True) == [], l4
        finally:
            load_cfg = real_cfg
            os.environ.pop("CHAT_ME", None)
    print("chat selftest ok")
    return 0


# ---- main -------------------------------------------------------------------------------------------------------------------------------------------------------------------

def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    cmd, rest = argv[0], argv[1:]
    again = "--again" in rest
    rest = [a for a in rest if a != "--again"]
    try:
        if cmd == "selftest":
            return selftest()
        if cmd == "watch":
            return cmd_watch()
        if cmd in ("reply", "done"):
            if "--" in rest:
                k = rest.index("--")
                ids, text = rest[:k], " ".join(rest[k + 1:])
            elif cmd == "done":
                ids, text = rest, ""
            else:
                ids = [a for a in rest[:-1] if ANY_ID.match(a)]
                text = rest[-1] if rest else ""
                if len(ids) != len(rest) - 1:
                    raise ChatError('usage: chat.py reply <id> [<id> ...] "text"')
            sent = cmd_reply(ids, text, done=cmd == "done", again=again)
            print("sent" if sent else "done", " ".join(sent))
            return 0
        if cmd == "say":
            print("sent", cmd_say(" ".join(rest), again=again))
            return 0
        if cmd == "status":
            print("status", cmd_status(rest[0] if rest else "", " ".join(rest[1:])))
            return 0
        if cmd == "pending":
            return cmd_pending()
        if cmd == "seat":
            g = Git()
            print("CHAT", seat_line(read_seat(g)[0]))
            return 0
        if cmd == "file" and rest:
            return cmd_file(rest[0], rest[1] if len(rest) > 1 else None)
        if cmd == "init":
            print("; ".join(cmd_init()))
            return 0
        if cmd == "deploy-workflow":
            print(deploy_workflow())
            return 0
    except ChatError as e:
        print("CHAT " + str(e), file=sys.stderr)
        return 1
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
