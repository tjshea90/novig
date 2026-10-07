# CHECKPOINT 2647 — read me first, then TASKS.md

**Written:** 2026-10-07T00:43:51Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `420db6d2` (this checkpoint is the commit after it)

## Just done
18/30 verifiers saved (rules 1-6 complete except rule 6 feasibility running; rule 7 plus-money: reproduces +2.83% on 95 closes but adds only +0.28 pts over rule 5, independent closes +0.45%, price hypothesis plausible at about +1 pt, narrowing odds to +100..+130 is a question for Tj; rule 6 wait-and-confirm: family-wise p 0.92, not supported; rule 5 feasibility: the 6 h guard is an existing setting, a trade-off question: tail risk vs about half the volume)

## Do this next
running: verify-6-feasibility, verify-7-luck, verify-7-feasibility; then verify-8, 9, 10 x3 (plan.py --running <labels>); then phase 4 per research/RESUME_v0701_ANALYSIS.md; after a restart: plan.py + json files first, SendMessage to resume stopped agents

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2c16c8de ckpt 2646: container restarted at ~00:35Z (new VM, data and prompts survived): verify-6
  dcf3d7a8 ckpt 2645: verify-4-feasibility, verify-5-reproduce, verify-5-luck saved (14/30 verifie
  1c6bf9ff ckpt 2644: RESUMED per Tj ('check the last status and checkpoint, then resume'): state 
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
  913eb393 ckpt 2640: 8th container: Tj re-sent both v0.70.1 files; extracted+prompts generated in
  50e5f7c8 ckpt 2639: v0.70.3 RELEASED and RECORDED (code 121, tag peels to ed80cfeb = ci-v0.70.3,
  367d6e39 ckpt 2638: RESUMED (8th container, branch ccr-8a6337ad-qbceu9): hooks ok; ci-v0.70.3 (e
  f3f4e7b6 ckpt 2637: v0.70.3 SHIPPED (pushed; code 121: Low API usage margin chip 1.5% + typed pe
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
