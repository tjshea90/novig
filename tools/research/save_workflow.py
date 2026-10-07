#!/usr/bin/env python3
"""save_workflow.py <journal.jsonl> <outdir> <title> [--once]

Banks a running (or finished) Workflow's results on GitHub as they arrive (Tj, 2026-10-07: "Make sure to save all findings to GitHub from the
workflow because usage may run out and interrupt"). Reads the workflow's journal, writes

  <outdir>/journal.jsonl   a copy of the raw journal (every agent's structured result)
  <outdir>/FINDINGS.md     the results rendered readable, one section per agent label, in the order they finished

then (unless the secret scan trips) commits ONLY <outdir> and pushes with tools/push.sh. Loops every 45 s until the journal has a result for
a label named "critic" and "final" (or --once, or 3 h). The repo is public: numbers and findings only; a result that looks like a credential
stops the commit (the scan says why) and is never pushed.
"""
import json, os, subprocess, sys, time

def render(v, depth=0):
    pad = "  " * depth
    if isinstance(v, dict):
        out = []
        for k, x in v.items():
            if isinstance(x, (dict, list)) and x:
                out.append(f"{pad}- **{k}**:")
                out.append(render(x, depth + 1))
            else:
                out.append(f"{pad}- **{k}**: {x if not isinstance(x, str) else x}")
        return "\n".join(out)
    if isinstance(v, list):
        out = []
        for x in v:
            if isinstance(x, (dict, list)):
                out.append(f"{pad}-")
                out.append(render(x, depth + 1))
            else:
                out.append(f"{pad}- {x}")
        return "\n".join(out)
    return f"{pad}{v}"

def load(path):
    starts, results = {}, []
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                d = json.loads(line)
            except Exception:
                continue  # a torn last line: the next pass reads it whole
            if d.get("type") == "start" or ("label" in d and "result" not in d):
                starts[d.get("agentId") or d.get("key")] = d
            elif "result" in d:
                results.append(d)
    return starts, results

def label_of(d, starts):
    s = starts.get(d.get("agentId")) or starts.get(d.get("key")) or {}
    return s.get("label") or d.get("label") or d.get("agentId") or d.get("key") or "agent"

def write(journal, outdir, title):
    starts, results = load(journal)
    os.makedirs(outdir, exist_ok=True)
    raw = open(journal).read()
    open(os.path.join(outdir, "journal.jsonl"), "w").write(raw if raw.endswith("\n") or not raw else raw + "\n")
    parts = [f"# {title}\n", "Raw: `journal.jsonl` (every agent's structured result). Rendered by `tools/research/save_workflow.py` as results arrive.\n",
             f"Results saved so far: {len(results)}\n"]
    labels = []
    for d in results:
        lab = label_of(d, starts)
        labels.append(lab)
        r = d["result"]
        if isinstance(r, str):
            try:
                r = json.loads(r)
            except Exception:
                pass
        parts.append(f"\n## {lab}\n")
        parts.append(render(r) if isinstance(r, (dict, list)) else str(r))
    open(os.path.join(outdir, "FINDINGS.md"), "w").write("\n".join(parts) + "\n")
    return labels

def git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True)

def bank(outdir, n):
    git("add", "--", outdir)
    if git("diff", "--cached", "--quiet", "--", outdir).returncode == 0:
        return "nothing new"
    if subprocess.run(["bash", "tools/secretscan.sh"], capture_output=True).returncode != 0:
        git("reset", "-q", "--", outdir)
        return "SECRET SCAN TRIPPED: not committed"
    c = git("commit", "-q", "-m", f"workflow results: {n} saved ({outdir})", "--", outdir)
    if c.returncode != 0:
        return "commit failed: " + c.stderr.strip()[:200]
    subprocess.run(["bash", "tools/push.sh"], capture_output=True)
    return "committed and pushed"

def main():
    if len(sys.argv) < 4:
        print(__doc__); sys.exit(1)
    journal, outdir, title = sys.argv[1:4]
    once = "--once" in sys.argv
    t0 = time.time()
    last = -1
    while True:
        labels = write(journal, outdir, title)
        if len(labels) != last:
            print(f"{len(labels)} results: {bank(outdir, len(labels))}", flush=True)
            last = len(labels)
        if once or ("final" in labels and "critic" in labels) or time.time() - t0 > 3 * 3600:
            print("saver done:", len(labels), "results", flush=True)
            return
        time.sleep(45)

main()
