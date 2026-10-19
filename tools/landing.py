#!/usr/bin/env python3
"""landing.py - may this session's branch land on main yet? (CR, Tj 2026-10-19: "Any claude account can make changes to this repo, as long as they ... don't ... break anything")

main is what the GitHub engine runs and what every release is built from, and three accounts now push to it without a lease. So a branch's commits reach main (tools/push.sh
fast-forwards it) only when everything they change has been checked:
  free   docs, research/, notes, INBOX.md, TASKS.md, CHECKPOINT.md, BUILDLOG.md ...      always
  tools  tools/**, .github/**, ship.sh, bootstrap.sh, .gitattributes, .claude/            the fast checks passed on exactly this content (tools/ckpt.sh marks it)
  code   app/ data/ engine/ cloud/ lab/ mgm/; the gradle build files count for every module tools/test.sh passed on exactly this module's content (test.sh marks it)
The branch itself is always pushed, so nothing waits in the container; only main waits. Marks live in .git/vigilant-landing.json (never committed).

  python3 -I tools/landing.py decide            LAND (exit 0) or WAIT <what to run> (exit 1), for the commits origin/main lacks
  python3 -I tools/landing.py mark tools        the fast checks just passed (ckpt.sh)
  python3 -I tools/landing.py mark code <module ...> | mark code all     tools/test.sh just passed for these modules (test.sh)
  python3 -I tools/landing.py selftest
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile

MODULES = ("app", "data", "engine", "cloud", "lab", "mgm")
BUILD = ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradle", "gradlew", "gradlew.bat")
TOOLS = ("tools", ".github", "ship.sh", "bootstrap.sh", ".gitattributes", ".claude")


def git(*a, env=None, cwd=None, check=False):
    r = subprocess.run(["git"] + list(a), capture_output=True, text=True, env=env, cwd=cwd)
    if check and r.returncode:
        raise RuntimeError("git %s: %s" % (" ".join(a[:3]), r.stderr.strip()))
    return r


def gitdir(cwd=None):
    return git("rev-parse", "--git-dir", cwd=cwd).stdout.strip() or ".git"


def marks_path(cwd=None):
    d = gitdir(cwd)
    return os.path.join(cwd or "", d, "vigilant-landing.json") if not os.path.isabs(d) else os.path.join(d, "vigilant-landing.json")


def classify(path):
    top = path.split("/")[0]
    if top in MODULES:
        return "code:" + top
    if top in BUILD:
        return "code:*"
    if top in TOOLS:
        return "tools"
    return "free"


def worktree_tree(cwd=None):
    """The tree the working directory would commit (HEAD + every change, ignored files left out), without touching the real index."""
    with tempfile.TemporaryDirectory() as d:
        env = dict(os.environ, GIT_INDEX_FILE=os.path.join(d, "idx"))
        git("read-tree", "HEAD", env=env, cwd=cwd, check=True)
        git("add", "-A", env=env, cwd=cwd, check=True)
        return git("write-tree", env=env, cwd=cwd, check=True).stdout.strip()


def part_sha(tree, names, cwd=None):
    """One fingerprint for these top-level entries of a tree (missing ones count too)."""
    h = hashlib.sha1()
    for n in names:
        h.update(("%s=%s;" % (n, git("rev-parse", "-q", "--verify", "%s:%s" % (tree, n), cwd=cwd).stdout.strip())).encode())
    return h.hexdigest()


def fingerprints(tree, cwd=None):
    fp = {"tools": part_sha(tree, TOOLS, cwd)}
    for m in MODULES:
        fp["code:" + m] = part_sha(tree, (m,) + BUILD, cwd)
    return fp


def load_marks(cwd=None):
    try:
        with open(marks_path(cwd), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def save_marks(m, cwd=None):
    p = marks_path(cwd)
    with open(p + ".tmp", "w", encoding="utf-8") as f:
        json.dump(m, f)
    os.replace(p + ".tmp", p)


def mark(kind, modules=(), cwd=None):
    fp = fingerprints(worktree_tree(cwd), cwd)
    m = load_marks(cwd)
    if kind == "tools":
        m["tools"] = fp["tools"]
    else:
        mods = MODULES if (not modules or "all" in modules) else [x for x in modules if x in MODULES]
        for x in mods:
            m["code:" + x] = fp["code:" + x]
    save_marks(m, cwd)
    return m


def decide(base="refs/remotes/origin/main", head="HEAD", cwd=None):
    """(True, '') when every class the branch changes since `base` is verified; else (False, what to run)."""
    changed = [p for p in git("diff", "--name-only", "%s...%s" % (base, head), cwd=cwd).stdout.split("\n") if p]
    need = set()
    for p in changed:
        c = classify(p)
        if c == "code:*":
            need.update("code:" + m for m in MODULES if os.path.isdir(os.path.join(cwd or ".", m)))
        elif c != "free":
            need.add(c)
    if not need:
        return True, ""
    tree = git("rev-parse", head + "^{tree}", cwd=cwd).stdout.strip()
    fp = fingerprints(tree, cwd)
    m = load_marks(cwd)
    missing = sorted(c for c in need if m.get(c) != fp[c])
    if not missing:
        return True, ""
    todo = []
    if "tools" in missing:
        todo.append("bash tools/ckpt.sh (its fast checks) for tools/workflows")
    mods = [c.split(":")[1] for c in missing if c.startswith("code:")]
    if mods:
        todo.append("bash tools/test.sh " + " ".join(":%s:%s" % (x, "testDebugUnitTest" if x == "app" else "test") for x in mods))
    return False, "; ".join(todo)


def selftest():
    with tempfile.TemporaryDirectory() as d:
        def g(*a):
            return git(*a, cwd=d, check=True)
        g("init", "-q", "-b", "main")
        open(os.path.join(d, "README.md"), "w").write("x")
        g("add", "-A"); g("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "base")
        g("update-ref", "refs/remotes/origin/main", "HEAD")
        os.makedirs(os.path.join(d, "research")); open(os.path.join(d, "research", "n.md"), "w").write("n")
        g("add", "-A"); g("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "docs")
        assert decide(cwd=d) == (True, ""), decide(cwd=d)
        os.makedirs(os.path.join(d, "tools")); open(os.path.join(d, "tools", "a.py"), "w").write("print(1)")
        os.makedirs(os.path.join(d, "data")); open(os.path.join(d, "data", "X.kt"), "w").write("fun x() = 1")
        g("add", "-A"); g("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "tools+code")
        ok, why = decide(cwd=d)
        assert not ok and "ckpt.sh" in why and ":data:test" in why, why
        mark("tools", cwd=d)
        ok, why = decide(cwd=d)
        assert not ok and "ckpt" not in why and ":data:test" in why, why
        mark("code", ["data"], cwd=d)
        assert decide(cwd=d) == (True, ""), decide(cwd=d)
        # an edit after the test run is not verified
        open(os.path.join(d, "data", "X.kt"), "w").write("fun x() = 2")
        g("add", "-A"); g("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "edit")
        assert not decide(cwd=d)[0]
        # marking from the working tree (before autosave commits) counts for the commit that follows
        open(os.path.join(d, "data", "X.kt"), "w").write("fun x() = 3")
        mark("code", ["data"], cwd=d)
        g("add", "-A"); g("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "edit 2")
        assert decide(cwd=d) == (True, ""), decide(cwd=d)
        # a build file counts for every module present
        open(os.path.join(d, "build.gradle.kts"), "w").write("//")
        g("add", "-A"); g("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "build")
        ok, why = decide(cwd=d)
        assert not ok and ":data:test" in why, why
        assert classify("app/src/X.kt") == "code:app" and classify(".github/workflows/a.yml") == "tools" and classify("TASKS.md") == "free" and classify("gradle/libs.versions.toml") == "code:*"
    print("landing selftest ok")
    return 0


def main(argv):
    if argv[:1] == ["selftest"]:
        return selftest()
    if argv[:1] == ["decide"]:
        ok, why = decide()
        print("LAND" if ok else "WAIT " + why)
        return 0 if ok else 1
    if argv[:2] == ["mark", "tools"]:
        mark("tools")
        return 0
    if argv[:2] == ["mark", "code"]:
        mark("code", argv[2:])
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
