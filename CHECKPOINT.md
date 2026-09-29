# CHECKPOINT 2055 — read me first, then TASKS.md

**Written:** 2026-09-29T07:02:26Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `e6502097` (this checkpoint is the commit after it)

## Just done
U4 grading fixes: football absent-from-box = 0 (ZERO stats), injury-report Out => VOID, other sports DNP => VOID, lookalike names => tap, MIN_BOX waits, CNO market aliases (earned runs allowed, outs recorded, walks allowed, RBI, batter K/BB, 3-pointers) + statOf noise retry; RealBoxGradingTest on the real Bengals/Steelers + Panthers/Browns box scores

## Do this next
U5: honest counts in BetRecheck report + TrackerText (odds checked on N; reasons for unchecked bets); then run the full floor, live check the 3 screenshot bets, U6 ship v0.20.1

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c3e46096 ckpt 2054: Recorded Tj's follow-up on the tracker (slow Check odds, ungradable bets, 61
  c0ed3975 ckpt 2053: v0.20.0 (code 43) released and recorded: CI green (run 36526989519), release
  c807f261 ckpt 2052: pre-release: v0.20.0: Tracker: Check odds now reads every open bet (no 40 ca
  d698b684 ckpt 2051: T9 full test: floor 837 green, live score check (NHL/NBA/tennis real), R8 bu
  d8925d3b ckpt 2050: Full-test sweep fixes: recheck never re-attaches books to a bet settled mid-
  b15d6a88 ckpt 2049: TASKS T2-T8 ticked with evidence; RESEARCH.md §34 (PropLine grading is paid
  62106480 ckpt 2048: T2-T8 ticked in TASKS.md with evidence; RESEARCH.md §34 (PropLine grading i
  ecc43d25 ckpt 2047: T2/T3/T4/T5/T6/T7 UI built: TrackerScreen (open list sorted by need, summary
  167165c0 ckpt 2046: T4/T3/T5/T6/T7 data layer done and tested: BetGrader (tennis, alternate tota
  bca5fd2a ckpt 2045: T4 in progress: FreeScores reads ESPN hockey box (goals/assists/points/SOG/b
```

(24 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
