#!/usr/bin/env python3
"""pinnodds_audit_resume.py — what a NEW session needs to resume the Pinnodds live-engine audit (Tj, 2026-10-08) with no data loss.

  python3 tools/research/pinnodds_audit_resume.py     # prints status, then the Workflow args as JSON on the last line

Reads research/pinnodds_audit_2026-10-08/results/*.json (every agent that finished, banked by tools/research/save_workflow.py) and prints which lenses and
skeptics are done, the cut-off ones (partial/), and the JSON to pass as the Workflow tool's `args`: {"skip": [labels already done]}.
See research/pinnodds_audit_2026-10-08/RESUME.md for the whole procedure.
"""
import json, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "research", "pinnodds_audit_2026-10-08")
LENSES = ["wrong-side", "ev-fee-math", "stale-pinnacle", "stale-novig", "trader-safety", "market-equivalence", "pinnbook-parsing", "wiring-and-gates", "statistics", "pregame-design"]


def main():
    rdir = os.path.join(OUT, "results")
    done = {}
    for fn in sorted(os.listdir(rdir)) if os.path.isdir(rdir) else []:
        try:
            j = json.load(open(os.path.join(rdir, fn)))
            done[j["label"]] = j
        except Exception:
            print("unreadable result file (ignored, its agent will run again):", fn)
    print(f"{len(done)} agent results saved in {os.path.relpath(OUT, ROOT)}/results")
    lenses_done = [k for k in LENSES if f"audit:{k}" in done]
    print("LENSES DONE:", lenses_done)
    print("LENSES NOT DONE:", [k for k in LENSES if k not in lenses_done])
    verifiers = sorted(l for l in done if l.startswith("verify:"))
    print(f"SKEPTIC VERDICTS SAVED: {len(verifiers)}")
    print("SYNTHESIS:", "saved (results/synthesize.json): go to RESUME.md step 5" if "synthesize" in done else "not yet")
    partial = os.path.join(OUT, "partial")
    if os.path.isdir(partial):
        print("CUT-OFF AGENTS (a re-run reads these first):", sorted(os.listdir(partial)))
    print(json.dumps({"skip": sorted(done)}))


main()
