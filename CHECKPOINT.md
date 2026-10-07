# CHECKPOINT 2648 — read me first, then TASKS.md

**Written:** 2026-10-07T00:53:00Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `58cfe3eb` (this checkpoint is the commit after it)

## Just done
21/30 verifiers saved: rule 8 (R1: first look inside 6 h with EV>=2.5%) reproduces exactly (+2.54% on 116 closes) but fails: 75 of 116 closes are Tracker closes (mechanical), independent closes +0.81% [-0.04,+2.13] on 33, the 'EV>=2.5% at any time' comparator gives +1.59%, window effect on independent closes +0.49 [-2.05,+3.23]; rule 7 plus-money: the broad price gradient is probably real (a question for Tj: higher EV bar for favourites); rule 6 wait-and-confirm halves expected CLV dollars

## Do this next
running: verify-7-feasibility, verify-8-luck, verify-8-feasibility; then verify-9, verify-10 x3 (plan.py --running <labels>); then phase 4 per research/RESUME_v0701_ANALYSIS.md; after a restart: plan.py + json files first, SendMessage to resume stopped agents

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c391db77 ckpt 2647: 18/30 verifiers saved (rules 1-6 complete except rule 6 feasibility running;
  2c16c8de ckpt 2646: container restarted at ~00:35Z (new VM, data and prompts survived): verify-6
  dcf3d7a8 ckpt 2645: verify-4-feasibility, verify-5-reproduce, verify-5-luck saved (14/30 verifie
  1c6bf9ff ckpt 2644: RESUMED per Tj ('check the last status and checkpoint, then resume'): state 
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
  913eb393 ckpt 2640: 8th container: Tj re-sent both v0.70.1 files; extracted+prompts generated in
  50e5f7c8 ckpt 2639: v0.70.3 RELEASED and RECORDED (code 121, tag peels to ed80cfeb = ci-v0.70.3,
  367d6e39 ckpt 2638: RESUMED (8th container, branch ccr-8a6337ad-qbceu9): hooks ok; ci-v0.70.3 (e
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
