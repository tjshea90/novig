#!/usr/bin/env python3
"""tools/workflow_dryrun.py - run one GitHub Actions job's shell steps locally, BEFORE the workflow has ever run on main (NC1, 2026-11-05).

Why: a workflow that has never run can be broken in ways no unit test sees. ncaab-log.yml was: it cloned its data branch into `data/`, which is this repository's own Gradle module (the first
run would have pushed 868 module files to the data branch and every later run would have failed), and its "weekly" check read `git log` in a --depth 1 clone, which always says "today".
A local dry run found both in minutes: no GitHub run, no push, no Claude usage spent waiting for a runner.

It takes the REAL `run:` scripts of one job out of the YAML (nothing is retyped), and runs them in order in a depth-1 copy of this repository (what actions/checkout leaves), with the job's and each
step's `env:`, GITHUB_ENV / GITHUB_STEP_SUMMARY emulated between steps and `if: always()` honoured, against a LOCAL bare repository standing in for GitHub: every line of a script that starts with
`url=` is replaced by that bare repository. Deliberately NOT done: `git config --global ...` lines are skipped (this tool never touches git identity: commits use the identity already configured),
`uses:` steps are skipped, `${{ github.token }}` is a dummy and `${{ inputs.X }}` comes from --input X=V. Steps that read the network still read it (a step that asks ESPN asks ESPN).

  python3 -I tools/workflow_dryrun.py .github/workflows/ncaab-log.yml --job log --branch ncaab-data --runs 2 [--input from=2026-11-01]

After each run it lists what the local "remote" branch holds and flags files that look like this repository's own source (the nested-repo mistake). The scratch directory (checkouts, the local
remote, each step's script) is printed and left for the system's temp clean-up. Exit 0 only when every step of every run passed and nothing looked like source code.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile
import time

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is needed (python3 -m pip install pyyaml)")

SOURCE_SMELL = (".kt", ".kts", ".java", "gradlew")


def run(cmd, cwd=None, env=None, check=True):
    r = subprocess.run(cmd, cwd=cwd, env=env, text=True, capture_output=True)
    if check and r.returncode:
        sys.exit("FAILED: %s\n%s%s" % (" ".join(cmd), r.stdout[-2000:], r.stderr[-2000:]))
    return r


def expand(s, inputs):
    s = s.replace("${{ github.token }}", "dummy-token")
    return re.sub(r"\$\{\{\s*inputs\.(\w+)\s*\}\}", lambda m: inputs.get(m.group(1), ""), s)


def one_run(n, base, repo, job, remote, inputs):
    work = os.path.join(base, "checkout-%d" % n)
    run(["git", "clone", "-q", "--depth", "1", "file://" + repo, work])
    env0 = dict(os.environ)
    env0.update({str(k): str(v) for k, v in (job.get("env") or {}).items()})
    env0["GITHUB_REPOSITORY"] = os.environ.get("GITHUB_REPOSITORY", "owner/repo")
    genv = os.path.join(base, "github_env-%d" % n)
    summ = os.path.join(base, "summary-%d.md" % n)
    open(genv, "w").close()
    env0["GITHUB_ENV"], env0["GITHUB_STEP_SUMMARY"] = genv, summ
    failed = False
    print("\n================ RUN %d ================" % n)
    for i, step in enumerate(job["steps"]):
        name = step.get("name", step.get("uses", "?"))
        if "run" not in step:
            print("-- skip (uses): %s" % name)
            continue
        if failed and step.get("if") != "always()":
            print("-- not run (an earlier step failed): %s" % name)
            continue
        lines = []
        for ln in expand(step["run"], inputs).splitlines():
            if "git config --global" in ln:
                continue  # never touch git identity from here
            if ln.strip().startswith("url="):
                ln = 'url="file://%s"' % remote
            lines.append(ln)
        path = os.path.join(base, "step-%d-%d.sh" % (n, i))
        open(path, "w").write("\n".join(lines) + "\n")
        env = dict(env0)
        for k, v in (step.get("env") or {}).items():
            env[str(k)] = expand(str(v), inputs)
        t = time.time()
        r = subprocess.run(["bash", "-e", path], cwd=work, env=env, text=True, capture_output=True)
        print("-- %s: exit %d in %.0fs" % (name, r.returncode, time.time() - t))
        for ln in (r.stdout + r.stderr).strip().splitlines()[-12:]:
            print("     " + ln[:200])
        failed |= r.returncode != 0
        for ln in open(genv).read().splitlines():
            if "=" in ln:
                k, v = ln.split("=", 1)
                env0[k] = v
    if os.path.exists(summ):
        print("-- step summary:\n" + open(summ).read())
    return not failed


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("workflow")
    ap.add_argument("--job", required=True)
    ap.add_argument("--branch", required=True, help="the data branch the job pushes to (listed after each run)")
    ap.add_argument("--runs", type=int, default=2)
    ap.add_argument("--input", action="append", default=[], metavar="K=V")
    ap.add_argument("--keep", action="store_true", help="keep the scratch directory even when everything passed (it is always kept on a failure)")
    a = ap.parse_args()
    inputs = dict(x.split("=", 1) for x in a.input)
    repo = run(["git", "rev-parse", "--show-toplevel"]).stdout.strip()
    job = yaml.safe_load(open(a.workflow))["jobs"][a.job]
    base = tempfile.mkdtemp(prefix="wf-dryrun-")
    remote = os.path.join(base, "remote.git")
    run(["git", "init", "-q", "--bare", remote])
    ok_all = True
    for n in range(1, a.runs + 1):
        ok = one_run(n, base, repo, job, remote, inputs)
        names = run(["git", "--git-dir", remote, "ls-tree", "-r", "--name-only", a.branch], check=False).stdout.split()
        smell = [x for x in names if x.endswith(SOURCE_SMELL) or x.startswith(("src/", "build/"))]
        commits = run(["git", "--git-dir", remote, "rev-list", "--count", a.branch], check=False).stdout.strip() or "0"
        print("== after run %d: steps ok=%s | commits on %s=%s | files=%d | top-level=%s | SOURCE-LOOKING FILES=%s"
              % (n, ok, a.branch, commits, len(names), sorted({x.split("/")[0] for x in names})[:12], smell[:3] or "none"))
        ok_all = ok_all and ok and not smell
    if ok_all and not a.keep:
        shutil.rmtree(base, ignore_errors=True)  # our own mkdtemp directory: a depth-1 checkout per run is a few hundred MB
        print("\nOVERALL: PASS (scratch directory removed; --keep leaves it)")
    else:
        print("\nOVERALL:", "PASS" if ok_all else "FAIL", "| scratch kept:", base)
    sys.exit(0 if ok_all else 1)


main()
