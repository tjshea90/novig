# CHECKPOINT 2646 — read me first, then TASKS.md

**Written:** 2026-10-07T00:36:47Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `c88cd894` (this checkpoint is the commit after it)

## Just done
container restarted at ~00:35Z (new VM, data and prompts survived): verify-6-reproduce had finished and is saved (15/30 verifiers: rule 6 wait-and-confirm reproduces exactly, no gain); the two interrupted verifiers (verify-5-feasibility, verify-6-luck) RESUMED via SendMessage from their saved transcripts; verify-6-feasibility launched; runbook pitfalls updated with the resume-by-SendMessage lesson

## Do this next
running: verify-5-feasibility, verify-6-luck, verify-6-feasibility; then verify-7..10 x3 (plan.py --running <labels>); then phase 4 per research/RESUME_v0701_ANALYSIS.md; if the container restarts again: check plan.py + the json files first, resume stopped agents with SendMessage

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  dcf3d7a8 ckpt 2645: verify-4-feasibility, verify-5-reproduce, verify-5-luck saved (14/30 verifie
  1c6bf9ff ckpt 2644: RESUMED per Tj ('check the last status and checkpoint, then resume'): state 
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
  913eb393 ckpt 2640: 8th container: Tj re-sent both v0.70.1 files; extracted+prompts generated in
  50e5f7c8 ckpt 2639: v0.70.3 RELEASED and RECORDED (code 121, tag peels to ed80cfeb = ci-v0.70.3,
  367d6e39 ckpt 2638: RESUMED (8th container, branch ccr-8a6337ad-qbceu9): hooks ok; ci-v0.70.3 (e
  f3f4e7b6 ckpt 2637: v0.70.3 SHIPPED (pushed; code 121: Low API usage margin chip 1.5% + typed pe
  d168c5ac ckpt 2636: pre-release: v0.70.3: Low API usage bids' margin under the fair has a 1.5% c
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
