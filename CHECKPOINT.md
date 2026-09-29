# CHECKPOINT 2051 — read me first, then TASKS.md

**Written:** 2026-09-29T05:35:46Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `c79be3a7` (this checkpoint is the commit after it)

## Just done
T9 full test: floor 837 green, live score check (NHL/NBA/tennis real), R8 build, sweep fixes (closing-line capture, checkOne lock, replace cleanup, warnings), version 0.20.0 code 43

## Do this next
bash ship.sh; wait for CI green on the commit; trigger release.yml; confirm Release; tools/record-release.sh; send Tj the link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  d8925d3b ckpt 2050: Full-test sweep fixes: recheck never re-attaches books to a bet settled mid-
  b15d6a88 ckpt 2049: TASKS T2-T8 ticked with evidence; RESEARCH.md §34 (PropLine grading is paid
  62106480 ckpt 2048: T2-T8 ticked in TASKS.md with evidence; RESEARCH.md §34 (PropLine grading i
  ecc43d25 ckpt 2047: T2/T3/T4/T5/T6/T7 UI built: TrackerScreen (open list sorted by need, summary
  167165c0 ckpt 2046: T4/T3/T5/T6/T7 data layer done and tested: BetGrader (tennis, alternate tota
  bca5fd2a ckpt 2045: T4 in progress: FreeScores reads ESPN hockey box (goals/assists/points/SOG/b
  7f48f897 ckpt 2044: T2 built: BetRecheck reads every open bet (no cap), one Report that adds up 
  7e5855dc ckpt 2043: T1 done: tracker read end to end; root causes, PropLine grading research and
  f76a71d6 ckpt 2042: Recorded Tj's tracker request (T1-T9) in TASKS.md
  1d3ddf68 ckpt 2041: Z6 done: v0.19.7 (code 42) released and recorded (CI + release green); light
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
