# CHECKPOINT 2060 — read me first, then TASKS.md

**Written:** 2026-09-29T07:13:07Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `31614eb3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.20.1: Check odds now is ~2x faster (3 pages at once, 500 ms pace) and also grades finished games in the same tap; bets on CNO's own wordings grade (Earned Runs Allowed, Outs Recorded, ...); football players with no stat in a box score count as 0, injured-out or DNP players void; the open list says 'odds read on N of M' and why a bet has no odds (versionCode 44, v0.20.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.20.1), then run: bash tools/record-release.sh v0.20.1 44 "v0.20.1: Check odds now is ~2x faster (3 pages at once, 500 ms pace) and also grades finished games in the same tap; bets on CNO's own wordings grade (Earned Runs Allowed, Outs Recorded, ...); football players with no stat in a box score count as 0, injured-out or DNP players void; the open list says 'odds read on N of M' and why a bet has no odds"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  31614eb3 ckpt 2059: full floor green (833 passed, 19 live skipped, exit 0); DNP voids say Novig 
  2747b6a0 ckpt 2058: v0.20.1 (code 44) versions bumped; test-protocols map updated; LiveCnoGradab
  29458a10 ckpt 2057: U1-U5 done and ticked with evidence; RESEARCH §35 written; Check odds now a
  7edc76bb ckpt 2056: U4 verified live: LiveUngradedBetsTest settles the 3 screenshot bets from re
  b825c950 ckpt 2055: U4 grading fixes: football absent-from-box = 0 (ZERO stats), injury-report O
  c3e46096 ckpt 2054: Recorded Tj's follow-up on the tracker (slow Check odds, ungradable bets, 61
  c0ed3975 ckpt 2053: v0.20.0 (code 43) released and recorded: CI green (run 36526989519), release
  c807f261 ckpt 2052: pre-release: v0.20.0: Tracker: Check odds now reads every open bet (no 40 ca
  d698b684 ckpt 2051: T9 full test: floor 837 green, live score check (NHL/NBA/tennis real), R8 bu
  d8925d3b ckpt 2050: Full-test sweep fixes: recheck never re-attaches books to a bet settled mid-
```
