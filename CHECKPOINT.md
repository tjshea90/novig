# CHECKPOINT 2050 — read me first, then TASKS.md

**Written:** 2026-09-29T05:25:07Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `dfff09d0` (this checkpoint is the commit after it)

## Just done
Full-test sweep fixes: recheck never re-attaches books to a bet settled mid-read (test), Vigilant price-now without fee (Kelly no double fee), Check odds/Re-read respect the Pause switch, CNO alert bets get their Novig market afterwards (AlertPlacement.attachMarket, test); skill map updated; floor 834 tests green

## Do this next
Continue T9 sweep: -Pscreenshots and look at every PNG; review remaining code paths (Settings copy, widget/mini window unaffected?), then version bump + ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M .claude/skills/test-protocols/SKILL.md
     M CHECKPOINT.md

## Last ten checkpoints
```
  b15d6a88 ckpt 2049: TASKS T2-T8 ticked with evidence; RESEARCH.md §34 (PropLine grading is paid
  62106480 ckpt 2048: T2-T8 ticked in TASKS.md with evidence; RESEARCH.md §34 (PropLine grading i
  ecc43d25 ckpt 2047: T2/T3/T4/T5/T6/T7 UI built: TrackerScreen (open list sorted by need, summary
  167165c0 ckpt 2046: T4/T3/T5/T6/T7 data layer done and tested: BetGrader (tennis, alternate tota
  bca5fd2a ckpt 2045: T4 in progress: FreeScores reads ESPN hockey box (goals/assists/points/SOG/b
  7f48f897 ckpt 2044: T2 built: BetRecheck reads every open bet (no cap), one Report that adds up 
  7e5855dc ckpt 2043: T1 done: tracker read end to end; root causes, PropLine grading research and
  f76a71d6 ckpt 2042: Recorded Tj's tracker request (T1-T9) in TASKS.md
  1d3ddf68 ckpt 2041: Z6 done: v0.19.7 (code 42) released and recorded (CI + release green); light
  7ad5dfc1 ckpt 2038: pre-release: v0.19.7: CNO's list leaves out futures and awards (games only; 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
