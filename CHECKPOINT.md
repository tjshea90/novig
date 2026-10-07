# CHECKPOINT 2649 — read me first, then TASKS.md

**Written:** 2026-10-07T00:56:27Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `a81c9159` (this checkpoint is the commit after it)

## Just done
WRAP-UP (usage nearly out): 22/30 verifiers saved on GitHub plus 9/9 study, 5/5 diag, 3/3 strategy; verify-8-luck, verify-8-feasibility, verify-9-reproduce were in flight (their JSONs are committed by autosave if they finish); the runbook research/RESUME_v0701_ANALYSIS.md now opens with a STATUS SNAPSHOT: exact cold-start commands, what is saved/missing/next, and what the verifiers concluded (timing is the one robust signal; every add-on gate unproven or refuted on independent closes; nothing in the app's rules changed)

## Do this next
COLD START: read research/RESUME_v0701_ANALYSIS.md (top block); bash tools/install-hooks.sh; python3 -I tools/research/study_v0701/plan.py; relaunch any missing in-flight labels (verify-8-luck, verify-8-feasibility, verify-9-reproduce), then verify-9-luck/feasibility and verify-10 x3, three at a time with tools/save_agent.sh after each; then phase 4 (genphase4.py), the report, RESEARCH 97, the app-fault release (v0.70.4: DA7), the short-bullet answer to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/RESUME_v0701_ANALYSIS.md

## Last ten checkpoints
```
  6ee5134c ckpt 2648: 21/30 verifiers saved: rule 8 (R1: first look inside 6 h with EV>=2.5%) repr
  c391db77 ckpt 2647: 18/30 verifiers saved (rules 1-6 complete except rule 6 feasibility running;
  2c16c8de ckpt 2646: container restarted at ~00:35Z (new VM, data and prompts survived): verify-6
  dcf3d7a8 ckpt 2645: verify-4-feasibility, verify-5-reproduce, verify-5-luck saved (14/30 verifie
  1c6bf9ff ckpt 2644: RESUMED per Tj ('check the last status and checkpoint, then resume'): state 
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
  913eb393 ckpt 2640: 8th container: Tj re-sent both v0.70.1 files; extracted+prompts generated in
  50e5f7c8 ckpt 2639: v0.70.3 RELEASED and RECORDED (code 121, tag peels to ed80cfeb = ci-v0.70.3,
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
