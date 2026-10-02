# CHECKPOINT 2391 — read me first, then TASKS.md

**Written:** 2026-10-02T21:33:45Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `919a4ed5` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.48.1: full-test fixes: locks out of every CLV stat and the check counter (no close capture or look-up spent on them), locked markets graded from the score feeds instead of a tap, closes merged from Vigilant's read dated by their oldest book price, Novig-only view keeps Novig's own trade close, live CNO bet's fair and Kelly at cost with the fee, a pushed live API bet loses its fee, 'profit if all win' wording

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  a1a166e8 ckpt 2390: BA6 fixes F1-F9 in, each with a failing-first test (StatsAccuracyTest F1-F4/
  ebabf7e1 ckpt 2389: BA1-BA4 audited: floor green + screenshots; fixes found F1-F9 (TASKS.md BA2-
  d7501949 ckpt 2388: BA: Tj's full-tests request (math, stats, closing lines) written into TASKS.
  b9f36b48 ckpt 2387: AZ done: v0.48.0 released, verified, recorded (hide locked bets + Locked in 
  511ed2bf ckpt 2386: pre-release: v0.48.0: locked markets hidden from the Tracker's lists and sta
  dfdc9a12 ckpt 2385: pre-ship: v0.48.0: locked markets hidden from the Tracker's lists and stats 
  4d2c5867 ckpt 2384: AZ2+AZ3 done (TASKS ticked): TrackerLocksTest + LockedBetsTest green, mutant
  25465506 ckpt 2383: AZ2/AZ3 code: LockedBets (markets/partly/ids/hide/stats) + LockStats, ScanSe
  cec190b2 ckpt 2382: AZ1 done (TASKS ticked): misses were missing Novig ids never retried, Replac
  bc1dbe75 ckpt 2381: AZ1 code + data tests: NovigBetFinder.locate, NovigIds (re-look up missing i
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
