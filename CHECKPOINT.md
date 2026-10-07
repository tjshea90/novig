# CHECKPOINT 2645 — read me first, then TASKS.md

**Written:** 2026-10-07T00:31:46Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `e5444bb6` (this checkpoint is the commit after it)

## Just done
verify-4-feasibility, verify-5-reproduce, verify-5-luck saved (14/30 verifiers): rule 5 (first listed <=6 h and EV>=2.5%) reproduces exactly (+3.01% on 64 closes) and is not luck on all closes, but only +1.64% on 13 independent closes and 39 of 64 closes are Tracker closes (circular): provisional; rule 4 (6 h + falling edge) is effectively the 6 h guard alone and not a credible improvement; the 6 h guard itself replicates (+0.58% [+0.02,+1.35] on 128 independent closes)

## Do this next
running: verify-5-feasibility, verify-6-reproduce, verify-6-luck; then verify-6-feasibility, verify-7..10 x3 (plan.py --running <labels>); then phase 4 per research/RESUME_v0701_ANALYSIS.md

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1c6bf9ff ckpt 2644: RESUMED per Tj ('check the last status and checkpoint, then resume'): state 
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
  913eb393 ckpt 2640: 8th container: Tj re-sent both v0.70.1 files; extracted+prompts generated in
  50e5f7c8 ckpt 2639: v0.70.3 RELEASED and RECORDED (code 121, tag peels to ed80cfeb = ci-v0.70.3,
  367d6e39 ckpt 2638: RESUMED (8th container, branch ccr-8a6337ad-qbceu9): hooks ok; ci-v0.70.3 (e
  f3f4e7b6 ckpt 2637: v0.70.3 SHIPPED (pushed; code 121: Low API usage margin chip 1.5% + typed pe
  d168c5ac ckpt 2636: pre-release: v0.70.3: Low API usage bids' margin under the fair has a 1.5% c
  eeffb02a ckpt 2635: RESUMED (7th container, branch ccr-28ef9ece-n2y0gt): hooks installed; two v0
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
