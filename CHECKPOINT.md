# CHECKPOINT 2394 — read me first, then TASKS.md

**Written:** 2026-10-02T21:47:13Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `b9a33866` (this checkpoint is the commit after it)

## Just done
BB1+BB2 done: Novig only compares Novig's odds now with the odds bet at (offer, not middle; same odds = 0%), nothing from other books on the sheet; tests + mutants green

## Do this next
full floor, v0.49.0 (code 88, folds in v0.48.1's unreleased fixes), ship, release, answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M NOVIG_API.md
     M TASKS.md

## Last ten checkpoints
```
  73eb3b3f ckpt 2393: BB: Tj's Novig-only request (Novig's current odds are the fair; no other boo
  075cc8f1 ckpt 2392: pre-release: v0.48.1: full-test fixes: locks out of every CLV stat and the c
  cecc64c5 ckpt 2391: pre-ship: v0.48.1: full-test fixes: locks out of every CLV stat and the chec
  a1a166e8 ckpt 2390: BA6 fixes F1-F9 in, each with a failing-first test (StatsAccuracyTest F1-F4/
  ebabf7e1 ckpt 2389: BA1-BA4 audited: floor green + screenshots; fixes found F1-F9 (TASKS.md BA2-
  d7501949 ckpt 2388: BA: Tj's full-tests request (math, stats, closing lines) written into TASKS.
  b9f36b48 ckpt 2387: AZ done: v0.48.0 released, verified, recorded (hide locked bets + Locked in 
  511ed2bf ckpt 2386: pre-release: v0.48.0: locked markets hidden from the Tracker's lists and sta
  dfdc9a12 ckpt 2385: pre-ship: v0.48.0: locked markets hidden from the Tracker's lists and stats 
  4d2c5867 ckpt 2384: AZ2+AZ3 done (TASKS ticked): TrackerLocksTest + LockedBetsTest green, mutant
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
