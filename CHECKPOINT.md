# CHECKPOINT 2643 — read me first, then TASKS.md

**Written:** 2026-10-07T00:16:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `8685544a` (this checkpoint is the commit after it)

## Just done
wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, first five minutes, data check, the 3-at-a-time save-each loop with the exact agent prompt, phase map + state: 9/9 study, 5/5 diag, 3/3 strategy, 11/30 verifiers saved, deliverables, rules, pitfalls); linked from TASKS DA8 and the old log; Tj re-sent the files (identical sha256 to the extracted copies)

## Do this next
FIRST read research/RESUME_v0701_ANALYSIS.md; then bash tools/install-hooks.sh; python3 -I tools/research/study_v0701/plan.py (19 verifiers left: verify-4-feasibility, verify-5..10 x 3), launch 3 at a time as background Agents, tools/save_agent.sh after each; then phase 4 (genphase4.py prompts: synth-diagnose, synth-study, synth-strategies, synthesis, critic, final); then research/scan_study_analysis_2026-10-06_v0.70.1.md + RESEARCH.md 97 + the app-fault release + the short-bullet answer to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/RESUME_v0701_ANALYSIS.md
     M research/scan_study_analysis_2026-10-06_v0.70.1_checkpoint.md

## Last ten checkpoints
```
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
  913eb393 ckpt 2640: 8th container: Tj re-sent both v0.70.1 files; extracted+prompts generated in
  50e5f7c8 ckpt 2639: v0.70.3 RELEASED and RECORDED (code 121, tag peels to ed80cfeb = ci-v0.70.3,
  367d6e39 ckpt 2638: RESUMED (8th container, branch ccr-8a6337ad-qbceu9): hooks ok; ci-v0.70.3 (e
  f3f4e7b6 ckpt 2637: v0.70.3 SHIPPED (pushed; code 121: Low API usage margin chip 1.5% + typed pe
  d168c5ac ckpt 2636: pre-release: v0.70.3: Low API usage bids' margin under the fair has a 1.5% c
  eeffb02a ckpt 2635: RESUMED (7th container, branch ccr-28ef9ece-n2y0gt): hooks installed; two v0
  5ac0a346 ckpt 2634: WRAP-UP: all 9 study + 3 strategy + 2 of 5 diag analysts and candidates.json
  9c9950c2 ckpt 2633: phase 2 complete: all 3 strategy builders saved, plan.py wrote candidates.js
```

(15 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
