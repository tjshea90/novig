#!/usr/bin/env python3
"""Change any engine/app setting or switch from a Claude session (Tj, 2026-10-18, TCL113: "any Claude session to be able to change and toggle on and off any setting and any switch anywhere in the app
and on GitHub except the stop all master switch").

  python3 tools/engine_set.py vigGuards.bidDeployKelly=20 gates.cloudTrading=LIVE [--on | --off] [--why "reason"]
  python3 tools/engine_set.py --show vigGuards.bidDeployKelly

Writes ONE commit onto `cloud-config` (plain, non-force push): settings.json with the `settings` tree changed and `device` = "claude:<account>". The engine reads it at once; the phone adopts it the next time it looks
(`CloudClaudeConfig`, then publishes its own copy). --on sets `on` and gates.cloudScan, --off turns them off; the engine's 08:00-01:00 hours and Tj's manual Run/Stop timestamps are left alone.
Dotted paths address numbers, booleans and strings that already exist in the settings. NEVER: `killed` / `killedAtMs` (STOP ALL, Tj's alone), keys, tokens or secrets (none are in the settings anyway).
Log what you changed in research/review/CHANGES.md with a revert rule.
"""
import json, os, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from tell_tj import git, me, BRANCH  # noqa

NEVER = ("killed", "killedatms", "key", "token", "pem", "secret")


def parse(v, old):
    if isinstance(old, bool):
        if v not in ("true", "false"):
            raise SystemExit("refused: %r must be true or false" % v)
        return v == "true"
    if isinstance(old, str):
        return v
    f = float(v)
    return int(f) if isinstance(old, int) else f


def set_path(tree, path, v):
    parts = path.split(".")
    for p in parts[:-1]:
        tree = tree[p]
    old = tree[parts[-1]]
    if old is None or isinstance(old, (dict, list)):
        raise SystemExit("refused: %s is not a plain number, boolean or text setting" % path)
    tree[parts[-1]] = parse(v, old)
    return old


def main(argv):
    why = argv[argv.index("--why") + 1] if "--why" in argv else "engine_set"
    rest = [a for i, a in enumerate(argv) if a not in ("--why", "--on", "--off", "--show") and not (i and argv[i - 1] == "--why")]
    # never FETCH_HEAD: another fetch in this checkout at the same moment would swap it and settings would be written from the wrong branch (CR, 2026-10-19)
    git("fetch", "-q", "origin", "+refs/heads/%s:refs/vigilant/engine-set-%s" % (BRANCH, BRANCH))
    parent = git("rev-parse", "refs/vigilant/engine-set-" + BRANCH).stdout.strip()
    cfg = json.loads(git("show", parent + ":settings.json").stdout)
    if "--show" in argv:
        for a in rest:
            t = cfg["settings"]
            for p in a.split("."):
                t = t[p]
            print(a, t)
        return 0
    if not rest and "--on" not in argv and "--off" not in argv:
        print(__doc__); return 2
    for pr in rest:
        k, v = pr.split("=", 1)
        if any(n in k.lower().split(".")[-1] or k.lower().split(".")[-1] == n for n in NEVER):
            raise SystemExit("refused: %s (STOP ALL and keys are not Claude's to change)" % k)
        print(k, set_path(cfg["settings"], k, v), "->", v)
    if "--on" in argv:
        cfg["on"] = True; cfg["settings"]["gates"]["cloudScan"] = True; cfg["settings"]["pausedByHand"] = False
    if "--off" in argv:
        cfg["on"] = False; cfg["settings"]["gates"]["cloudScan"] = False
    cfg["device"] = "claude:" + me().replace("claude:", "")[:30]
    cfg["rev"] = max(int(time.time() * 1000), int(cfg.get("rev", 0)) + 1)
    text = json.dumps(cfg, separators=(",", ":"))
    with tempfile.TemporaryDirectory() as d:
        env = dict(os.environ, GIT_INDEX_FILE=os.path.join(d, "idx"))
        git("read-tree", parent, env=env)
        blob = git("hash-object", "-w", "--stdin", inp=text).stdout.strip()
        git("update-index", "--add", "--cacheinfo", "100644,%s,settings.json" % blob, env=env)
        tree = git("write-tree", env=env).stdout.strip()
        cenv = dict(os.environ, GIT_AUTHOR_NAME="Claude", GIT_AUTHOR_EMAIL="noreply@anthropic.com", GIT_COMMITTER_NAME="Claude", GIT_COMMITTER_EMAIL="noreply@anthropic.com")
        c = git("commit-tree", tree, "-m", "engine_set: " + why[:100], "-p", parent, env=cenv).stdout.strip()
    r = git("push", "-q", "origin", "%s:refs/heads/%s" % (c, BRANCH), check=False)
    if r.returncode:
        print("push failed (cloud-config moved meanwhile; run again):", r.stderr.strip()); return 1
    print("pushed", c[:9]); return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
