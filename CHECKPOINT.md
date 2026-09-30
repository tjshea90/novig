# CHECKPOINT 2119 — read me first, then TASKS.md

**Written:** 2026-09-30T00:20:11Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `b76f82d6` (this checkpoint is the commit after it)

## Just done
pre-release: v0.25.0: true closing line value: Vigilant reads each bet's fair line about 6 minutes before its game starts (even when closed), and the Stats tab's Closing line value card shows the share of bets that beat the close and by how much on average, over every bet ever, with All time / Today / Yesterday / Last 3 days / Last week and Hide outliers (over ±5%) (versionCode 53, v0.25.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.25.0), then run: bash tools/record-release.sh v0.25.0 53 "v0.25.0: true closing line value: Vigilant reads each bet's fair line about 6 minutes before its game starts (even when closed), and the Stats tab's Closing line value card shows the share of bets that beat the close and by how much on average, over every bet ever, with All time / Today / Yesterday / Last 3 days / Last week and Hide outliers (over ±5%)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b76f82d6 ckpt 2118: D1-D4 ticked; RESEARCH.md §41; version 0.25.0 (code 53); two older tests re
  b1b9cd28 ckpt 2117: CLV app tests green (ClosingLineAppTest 7: alarm follows bets, paused captur
  d7b07ac1 ckpt 2116: D2-D4 code in: CLV card (own period chips + Hide outliers switch) replaces t
  2c00d584 ckpt 2115: D2 core written and compiling: ClosingLine (true close = pregame read within
  bf19dc84 ckpt 2114: Recorded Tj's request (true closing line value: find each bet's true close, 
  80a2c490 ckpt 2113: v0.24.0 (code 52) released and recorded; C1-C3 ticked
  c1a35dc8 ckpt 2112: pre-release: v0.24.0: the Tracker's Check odds now counter, pinned at the to
  eb300605 ckpt 2111: C1/C2 ticked; Diagnostics carries the counter; full floor 1046 green; versio
  be0b82f2 ckpt 2110: C1/C2 built and tested: CheckOddsStats (data; CheckOddsStatsTest 5), counter
  6ba15144 ckpt 2109: Recorded Tj's request (Check odds now counter: +EV/−EV counts, % +EV, rese
```
