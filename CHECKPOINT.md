# CHECKPOINT 2117 — read me first, then TASKS.md

**Written:** 2026-09-30T00:16:07Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `0badb827` (this checkpoint is the commit after it)

## Just done
CLV app tests green (ClosingLineAppTest 7: alarm follows bets, paused capture marks tried, card numbers/outliers/periods, in Stats); chips wrap (FlowRow); Diagnostics CLV + next capture; Avg EV line kept in 'Are the edges real?'

## Do this next
full floor, sweep, docs (RESEARCH.md/BRIEF note on true CLV), version 0.25.0 code 53, tick D1-D4, ship, release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/TrackerScreen.kt

## Last ten checkpoints
```
  d7b07ac1 ckpt 2116: D2-D4 code in: CLV card (own period chips + Hide outliers switch) replaces t
  2c00d584 ckpt 2115: D2 core written and compiling: ClosingLine (true close = pregame read within
  bf19dc84 ckpt 2114: Recorded Tj's request (true closing line value: find each bet's true close, 
  80a2c490 ckpt 2113: v0.24.0 (code 52) released and recorded; C1-C3 ticked
  c1a35dc8 ckpt 2112: pre-release: v0.24.0: the Tracker's Check odds now counter, pinned at the to
  eb300605 ckpt 2111: C1/C2 ticked; Diagnostics carries the counter; full floor 1046 green; versio
  be0b82f2 ckpt 2110: C1/C2 built and tested: CheckOddsStats (data; CheckOddsStatsTest 5), counter
  6ba15144 ckpt 2109: Recorded Tj's request (Check odds now counter: +EV/−EV counts, % +EV, rese
  3e6dce93 ckpt 2108: v0.23.0 (code 51) released and recorded; B1-B5 ticked
  a65d1c12 ckpt 2107: pre-release: v0.23.0: the Novig management key is entered once and saved on 
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
