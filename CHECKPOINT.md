# CHECKPOINT 2390 — read me first, then TASKS.md

**Written:** 2026-10-02T21:23:58Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `9e87df4d` (this checkpoint is the commit after it)

## Just done
BA6 fixes F1-F9 in, each with a failing-first test (StatsAccuracyTest F1-F4/F7/F9, ApiSettlerTest locked market, AtBetTest live Kelly, TrackerUiTest label): 24/24 green

## Do this next
full floor (tools/test.sh), then mutants on the new guards, BA5 sweep, ship v0.48.1

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ebabf7e1 ckpt 2389: BA1-BA4 audited: floor green + screenshots; fixes found F1-F9 (TASKS.md BA2-
  d7501949 ckpt 2388: BA: Tj's full-tests request (math, stats, closing lines) written into TASKS.
  b9f36b48 ckpt 2387: AZ done: v0.48.0 released, verified, recorded (hide locked bets + Locked in 
  511ed2bf ckpt 2386: pre-release: v0.48.0: locked markets hidden from the Tracker's lists and sta
  dfdc9a12 ckpt 2385: pre-ship: v0.48.0: locked markets hidden from the Tracker's lists and stats 
  4d2c5867 ckpt 2384: AZ2+AZ3 done (TASKS ticked): TrackerLocksTest + LockedBetsTest green, mutant
  25465506 ckpt 2383: AZ2/AZ3 code: LockedBets (markets/partly/ids/hide/stats) + LockStats, ScanSe
  cec190b2 ckpt 2382: AZ1 done (TASKS ticked): misses were missing Novig ids never retried, Replac
  bc1dbe75 ckpt 2381: AZ1 code + data tests: NovigBetFinder.locate, NovigIds (re-look up missing i
  97845a81 ckpt 2380: AZ1 investigation redone (workflow results lost with the old container): mis
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
