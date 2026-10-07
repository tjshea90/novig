# CHECKPOINT 2650 — read me first, then TASKS.md

**Written:** 2026-10-07T01:27:08Z · **tests:** all 4 fast checks green
**Branch:** `ccr-c79f7430-lq8xfl` · **builds on:** `58a4585c` (this checkpoint is the commit after it)

## Just done
DB1/DB2/DB4: free ParlayAPI keys now serve scans in Tj's key order after the paid key's day share (floor = last 100 credits, CreditPace.FREE_RESERVE); KeyPool held-back message by case; meter marks the key a scan will use; HISTORICAL_LIMIT 403 no longer marks a key refused; KeyRotationTest (6 tests, 3 mutants killed) + CreditPaceTest updated, 50 green

## Do this next
DB3: audit the fallback when no ParlayAPI key can serve (scan, Check odds now, closes), then docs (PARLAY_API.md), full bash tools/test.sh, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b19f6a41 ckpt 2649: WRAP-UP (usage nearly out): 22/30 verifiers saved on GitHub plus 9/9 study, 
  6ee5134c ckpt 2648: 21/30 verifiers saved: rule 8 (R1: first look inside 6 h with EV>=2.5%) repr
  c391db77 ckpt 2647: 18/30 verifiers saved (rules 1-6 complete except rule 6 feasibility running;
  2c16c8de ckpt 2646: container restarted at ~00:35Z (new VM, data and prompts survived): verify-6
  dcf3d7a8 ckpt 2645: verify-4-feasibility, verify-5-reproduce, verify-5-luck saved (14/30 verifie
  1c6bf9ff ckpt 2644: RESUMED per Tj ('check the last status and checkpoint, then resume'): state 
  a955f73e ckpt 2643: wrote the standalone resume runbook research/RESUME_v0701_ANALYSIS.md (job, 
  35b62b25 ckpt 2642: DA6 feed-race recorder RESTARTED 22:09Z (370 min, ends ~04:20Z) in the 8th c
  691e51b1 ckpt 2641: added tools/research/study_v0701/genphase4.py (phase 4 prompts: 3 section wr
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
