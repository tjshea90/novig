# CHECKPOINT 2644 — read me first, then TASKS.md

**Written:** 2026-10-07T00:17:33Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `a10b1cb0` (this checkpoint is the commit after it)

## Just done
RESUMED per Tj ('check the last status and checkpoint, then resume'): state confirmed (ckpt 2643 = the runbook; main == branch; nothing else pushed; data and prompts still in the fifth container); launched verify-4-feasibility, verify-5-reproduce, verify-5-luck at 00:20Z

## Do this next
running: verify-4-feasibility, verify-5-reproduce, verify-5-luck; as each finishes: bash tools/save_agent.sh <label>, then python3 -I tools/research/study_v0701/plan.py --running <labels> and launch the next (verify-5-feasibility, verify-6..10 x3); then phase 4 per research/RESUME_v0701_ANALYSIS.md

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
  913eb393 ckpt 2640: 8th container: Tj re-sent both v0.70.1 files; extracted+prompts generated in
  50e5f7c8 ckpt 2639: v0.70.3 RELEASED and RECORDED (code 121, tag peels to ed80cfeb = ci-v0.70.3,
  367d6e39 ckpt 2638: RESUMED (8th container, branch ccr-8a6337ad-qbceu9): hooks ok; ci-v0.70.3 (e
  f3f4e7b6 ckpt 2637: v0.70.3 SHIPPED (pushed; code 121: Low API usage margin chip 1.5% + typed pe
  d168c5ac ckpt 2636: pre-release: v0.70.3: Low API usage bids' margin under the fair has a 1.5% c
  eeffb02a ckpt 2635: RESUMED (7th container, branch ccr-28ef9ece-n2y0gt): hooks installed; two v0
  5ac0a346 ckpt 2634: WRAP-UP: all 9 study + 3 strategy + 2 of 5 diag analysts and candidates.json
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
