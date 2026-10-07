#!/usr/bin/env python3
"""rapid_resume.py — what a NEW session needs to resume the ten-sources research (Tj, 2026-10-07) with no data loss.

  python3 tools/research/rapid_resume.py          # prints status, then the Workflow args as JSON on the last line

Reads research/rapid_sources_workflow_2026-10-07/results/*.json (every agent that finished, saved by tools/research/save_workflow.py) and prints which
of the expected agents are done, which are not, the partial traces of the ones that were cut off, and the JSON to pass as the Workflow tool's `args`:
{"skip": [labels already done]}. See research/rapid_sources_workflow_2026-10-07/RESUME.md for the whole procedure.
"""
import json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "research", "rapid_sources_workflow_2026-10-07")
SLUGS = [
    (1, "medium-fastapi-odds-tracker"), (2, "bksignal-odds"), (3, "devto-stop-scraping"), (4, "scraperly-oddsshark"), (5, "surebetfusion"),
    (6, "roundproxies-scrape-sportsbooks"), (7, "scrapingproxies-websocket-scraping"), (8, "devto-pulsescore-typescript"),
    (9, "github-odds-stream-engine"), (10, "pulsescore-net"),
]


def main():
    rdir = os.path.join(OUT, "results")
    done = {}
    for fn in sorted(os.listdir(rdir)) if os.path.isdir(rdir) else []:
        try:
            j = json.load(open(os.path.join(rdir, fn)))
            done[j["label"]] = j
        except Exception:
            print("unreadable result file (ignored, its agent will run again):", fn)
    expected = []
    for n, slug in SLUGS:
        expected += [f"scout:{n}-{slug}", f"verify-claims:{slug}", f"verify-fit:{slug}"]
    expected += ["synth", "critic"]
    extra = sorted(l for l in done if l.startswith("gap:"))
    print(f"{len(done)} agent results saved in {os.path.relpath(OUT, ROOT)}/results")
    if "final" in done:
        print("ALL DONE: results/final.json exists. Skip to RESUME.md step 5 (write RESEARCH.md section 115, tick TASKS.md DO1-DO3, answer Tj).")
    print("NOT YET DONE:", [l for l in expected if l not in done] + ([] if "final" in done else ["(gap agents the critic asked for, if any)", "final"]))
    partial = os.path.join(OUT, "partial")
    if os.path.isdir(partial):
        print("CUT-OFF AGENTS (a re-run reads these first):", sorted(os.listdir(partial)))
    print("GAP AGENTS ALREADY DONE:", extra)
    print(json.dumps({"skip": sorted(done)}))


main()
