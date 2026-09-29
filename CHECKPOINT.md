# CHECKPOINT 2114 — read me first, then TASKS.md

**Written:** 2026-09-29T23:59:27Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `3d53f31e` (this checkpoint is the commit after it)

## Just done
Recorded Tj's request (true closing line value: find each bet's true close, % beat CLV, avg % beat, running forever, periods all/today/yesterday/3 days/week, remove >5% outliers) as D1-D5

## Do this next
D1 audit: how closingFair/closingSeenAtMs are written today (BetRecheck, BetTracker.applyFair/observe), TrackerStats CLV, StatsCards; RESEARCH.md on closing lines; then design D2

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  80a2c490 ckpt 2113: v0.24.0 (code 52) released and recorded; C1-C3 ticked
  c1a35dc8 ckpt 2112: pre-release: v0.24.0: the Tracker's Check odds now counter, pinned at the to
  eb300605 ckpt 2111: C1/C2 ticked; Diagnostics carries the counter; full floor 1046 green; versio
  be0b82f2 ckpt 2110: C1/C2 built and tested: CheckOddsStats (data; CheckOddsStatsTest 5), counter
  6ba15144 ckpt 2109: Recorded Tj's request (Check odds now counter: +EV/−EV counts, % +EV, rese
  3e6dce93 ckpt 2108: v0.23.0 (code 51) released and recorded; B1-B5 ticked
  a65d1c12 ckpt 2107: pre-release: v0.23.0: the Novig management key is entered once and saved on 
  a62443f2 ckpt 2106: B1-B4 ticked; version 0.23.0 (code 51); sweep fixes (keep() rename, section 
  bd62a7ef ckpt 2105: docs updated (NOVIG_API.md §14 saved management key, NovigSetup/NovigBettin
  ca5ef53e ckpt 2104: B1-B4 implemented and targeted tests green: ManagementKeyStoreTest (6), Wall
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
