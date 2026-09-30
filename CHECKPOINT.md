# CHECKPOINT 2115 — read me first, then TASKS.md

**Written:** 2026-09-30T00:07:45Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `a6d82dc6` (this checkpoint is the commit after it)

## Just done
D2 core written and compiling: ClosingLine (true close = pregame read within 15 min of start, final at start; due/nextAt/retry), ClvStats + ClvPeriod, TrackedBet.closeTriedAtMs, BetTracker.markCloseTried, stats() now true-CLV only, BetRecheck.captureClosing(ids), app ClosingCapture/ClosingAlarm/ClosingReceiver/ClosingWorker (expedited), manifest, AppContainer watcher arms the alarm on tracker changes, boot reschedule

## Do this next
D3/D4 UI: CLV card in Stats (periods all/today/yesterday/3 days/week + hide >5% outliers), bet card CLV column = true CLV, sheet 'CLV so far', breakdown CLV; then tests (ClosingLineTest, ClvStats, BetTrackerTest fixes, capture test), Diagnostics line

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bf19dc84 ckpt 2114: Recorded Tj's request (true closing line value: find each bet's true close, 
  80a2c490 ckpt 2113: v0.24.0 (code 52) released and recorded; C1-C3 ticked
  c1a35dc8 ckpt 2112: pre-release: v0.24.0: the Tracker's Check odds now counter, pinned at the to
  eb300605 ckpt 2111: C1/C2 ticked; Diagnostics carries the counter; full floor 1046 green; versio
  be0b82f2 ckpt 2110: C1/C2 built and tested: CheckOddsStats (data; CheckOddsStatsTest 5), counter
  6ba15144 ckpt 2109: Recorded Tj's request (Check odds now counter: +EV/−EV counts, % +EV, rese
  3e6dce93 ckpt 2108: v0.23.0 (code 51) released and recorded; B1-B5 ticked
  a65d1c12 ckpt 2107: pre-release: v0.23.0: the Novig management key is entered once and saved on 
  a62443f2 ckpt 2106: B1-B4 ticked; version 0.23.0 (code 51); sweep fixes (keep() rename, section 
  bd62a7ef ckpt 2105: docs updated (NOVIG_API.md §14 saved management key, NovigSetup/NovigBettin
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
