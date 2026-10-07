#!/usr/bin/env python3
"""save_workflow.py <workflow_dir> <outdir> <title> [--once]

Banks a running (or finished) Workflow on GitHub as it goes, so a usage cap or a lost container costs nothing and a NEW session (any account) can resume
(Tj, 2026-10-07: "Make sure to save all findings to GitHub from the workflow because usage may run out and interrupt you at any second"). <workflow_dir> is
the run's transcript directory (journal.jsonl and agent-<id>.jsonl files). Written under <outdir>, never deleted by a later pass or a later session:

  results/<label>.json        each FINISHED agent's structured result (one file per agent; a re-run of the same label replaces it, nothing else does)
  FINDINGS.md                 every result in results/, rendered readable, scouts first
  journal.<run>.jsonl         the raw journal of this run (so nothing the rendering drops is lost)
  partial/<label>.md          a compact trace (what each agent read and said, tool results cut short) of every agent STILL RUNNING, refreshed about every
                              90 s; removed when the agent finishes. A re-run of a cut-off agent reads it first and continues instead of starting over
  STATUS.md                   finished and in-flight labels, the run id, and how to resume

Every file is passed through tools/redact_keys.py (the repo is public) and the secret scan runs before each commit; a trip stops that commit and says
why (the scan would otherwise stop every checkpoint). Commits ONLY <outdir>, then tools/push.sh. Results are banked within ~10 s of arriving.
Stops after the 'final' agent's result is banked (or --once, or 8 h).
"""
import importlib.util, json, os, re, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
os.chdir(ROOT)
_spec = importlib.util.spec_from_file_location("redact_keys", os.path.join(ROOT, "tools", "redact_keys.py"))
_rk = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_rk)
redact = _rk.redact

ORDER = ["scout", "verify-claims", "verify-fit", "synth", "critic", "gap", "final"]
TRACE_EVERY_S = 90
TRACE_MAX = 60000


def safe(label):
    return re.sub(r"[^A-Za-z0-9._-]+", "_", label)


def sort_key(label):
    head = label.split(":")[0]
    return (ORDER.index(head) if head in ORDER else len(ORDER), label)


def render(v, depth=0):
    pad = "  " * depth
    if isinstance(v, dict):
        out = []
        for k, x in v.items():
            if isinstance(x, (dict, list)) and x:
                out.append(f"{pad}- **{k}**:")
                out.append(render(x, depth + 1))
            else:
                out.append(f"{pad}- **{k}**: {x}")
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


def load_journal(path):
    starts, results = {}, {}
    try:
        lines = open(path).read().splitlines()
    except OSError:
        return starts, results
    for line in lines:
        line = line.strip()
        if not line:
            continue
        try:
            d = json.loads(line)
        except Exception:
            continue  # a torn last line: the next pass reads it whole
        if "result" in d:
            results[d.get("agentId")] = d
        elif "label" in d:
            starts[d.get("agentId")] = d
    return starts, results


def trace(path):
    out = []
    try:
        lines = open(path).read().splitlines()
    except OSError:
        return ""
    for line in lines:
        try:
            d = json.loads(line)
        except Exception:
            continue
        msg = d.get("message") or {}
        content = msg.get("content")
        if d.get("type") == "assistant" and isinstance(content, list):
            for b in content:
                if b.get("type") == "text" and b.get("text", "").strip():
                    out.append("ASSISTANT: " + b["text"].strip())
                elif b.get("type") == "tool_use":
                    out.append("TOOL " + str(b.get("name")) + " " + json.dumps(b.get("input"), ensure_ascii=False)[:400])
        elif d.get("type") == "user" and isinstance(content, list):
            for b in content:
                if b.get("type") == "tool_result":
                    c = b.get("content")
                    text = c if isinstance(c, str) else " ".join(x.get("text", "") for x in c if isinstance(x, dict)) if isinstance(c, list) else ""
                    text = text.strip().replace("\n", " ")
                    out.append(f"  RESULT ({len(text)} chars): {text[:400]}")
    s = "\n".join(out)
    if len(s) > TRACE_MAX:
        s = s[:8000] + "\n[... middle of the trace left out to keep the file small ...]\n" + s[-(TRACE_MAX - 8000):]
    return s


def git(*a):
    return subprocess.run(["git", *a], capture_output=True, text=True)


def bank(outdir, note):
    git("add", "-A", "--", outdir)
    if git("diff", "--cached", "--quiet", "--", outdir).returncode == 0:
        return "nothing new"
    if subprocess.run(["bash", "tools/secretscan.sh"], capture_output=True).returncode != 0:
        git("reset", "-q", "--", outdir)
        return "SECRET SCAN TRIPPED: not committed (look for a credential-shaped string under " + outdir + ")"
    c = git("commit", "-q", "-m", f"workflow progress: {note} ({outdir})", "--", outdir)
    if c.returncode != 0:
        return "commit failed: " + c.stderr.strip()[:200]
    subprocess.run(["bash", "tools/push.sh"], capture_output=True)
    return "committed and pushed"


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    text = redact(text)
    if os.path.exists(path) and open(path).read() == text:
        return
    open(path, "w").write(text)


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    wf, outdir, title = sys.argv[1:4]
    once = "--once" in sys.argv
    run = os.path.basename(os.path.normpath(wf))
    journal = os.path.join(wf, "journal.jsonl")
    t0, last_trace = time.time(), 0.0
    while True:
        starts, results = load_journal(journal)
        # 1. every finished agent's result, one file per label (never removed)
        for aid, d in results.items():
            label = (starts.get(aid) or {}).get("label") or d.get("label") or aid
            r = d["result"]
            if isinstance(r, str):
                try:
                    r = json.loads(r)
                except Exception:
                    pass
            write(os.path.join(outdir, "results", safe(label) + ".json"), json.dumps({"label": label, "agentId": aid, "run": run, "result": r}, indent=1, ensure_ascii=False) + "\n")
        # 2. the readable file, from everything in results/ (earlier sessions' results included)
        have = {}
        rdir = os.path.join(outdir, "results")
        for fn in sorted(os.listdir(rdir)) if os.path.isdir(rdir) else []:
            try:
                j = json.load(open(os.path.join(rdir, fn)))
                have[j["label"]] = j
            except Exception:
                continue
        parts = [f"# {title}\n", "One section per agent, scouts first. Raw per-agent files: `results/<label>.json`; each run's raw journal: `journal.<run>.jsonl`. Rendered by `tools/research/save_workflow.py`.\n",
                 f"Results saved so far: {len(have)}\n"]
        for lab in sorted(have, key=sort_key):
            r = have[lab]["result"]
            parts.append(f"\n## {lab}\n")
            parts.append(render(r) if isinstance(r, (dict, list)) else str(r))
        write(os.path.join(outdir, "FINDINGS.md"), "\n".join(parts) + "\n")
        # 3. the raw journal of this run
        try:
            write(os.path.join(outdir, f"journal.{run}.jsonl"), open(journal).read())
        except OSError:
            pass
        # 4. in-flight agents: a compact trace each, at most every TRACE_EVERY_S
        inflight = {aid: s for aid, s in starts.items() if aid not in results}
        if time.time() - last_trace >= TRACE_EVERY_S or not inflight:
            for aid, s in inflight.items():
                body = trace(os.path.join(wf, f"agent-{aid}.jsonl"))
                if body:
                    write(os.path.join(outdir, "partial", safe(s["label"]) + ".md"),
                          f"# PARTIAL TRACE of agent {s['label']} (still running when this was saved; run {run})\n\nWhat it has read and said so far. A re-run of this agent reads this first and continues.\n\n" + body + "\n")
            for aid, s in starts.items():
                if aid in results:
                    p = os.path.join(outdir, "partial", safe(s["label"]) + ".md")
                    if os.path.exists(p):
                        os.remove(p)
            last_trace = time.time()
        # 5. STATUS.md (no clock in it: it changes only when the state does)
        done_labels = sorted(have, key=sort_key)
        status = [f"# STATUS of {title}", "", f"Run `{run}`. Finished agents with a saved result: {len(have)}.", "", "FINISHED (results/<label>.json):"] + [f"- {l}" for l in done_labels]
        status += ["", "IN FLIGHT when last saved (partial/<label>.md has what each had read):"] + [f"- {s['label']}" for s in inflight.values()]
        status += ["", "RESUME: see RESUME.md in this folder (a new session, any account: `python3 tools/research/rapid_resume.py` prints the Workflow args)."]
        write(os.path.join(outdir, "STATUS.md"), "\n".join(status) + "\n")
        note = f"{len(have)} results, {len(inflight)} running"
        res = bank(outdir, note)
        if res != "nothing new":
            print(f"{time.strftime('%H:%M:%S')} {note}: {res}", flush=True)
        if once or "final" in have or time.time() - t0 > 8 * 3600:
            print("saver done:", len(have), "results", flush=True)
            return
        time.sleep(10)


main()
