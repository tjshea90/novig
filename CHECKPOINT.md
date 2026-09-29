# CHECKPOINT 2112 — read me first, then TASKS.md

**Written:** 2026-09-29T23:12:22Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `eb300605` (this checkpoint is the commit after it)

## Just done
pre-release: v0.24.0: the Tracker's Check odds now counter, pinned at the top: how many open bets are +EV and −EV now at the price you got, the share that's +EV, and their average EV leaving out any over ±5%; it starts at 0 with each check and counts up as the refreshed odds come in (versionCode 52, v0.24.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.24.0), then run: bash tools/record-release.sh v0.24.0 52 "v0.24.0: the Tracker's Check odds now counter, pinned at the top: how many open bets are +EV and −EV now at the price you got, the share that's +EV, and their average EV leaving out any over ±5%; it starts at 0 with each check and counts up as the refreshed odds come in"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  eb300605 ckpt 2111: C1/C2 ticked; Diagnostics carries the counter; full floor 1046 green; versio
  be0b82f2 ckpt 2110: C1/C2 built and tested: CheckOddsStats (data; CheckOddsStatsTest 5), counter
  6ba15144 ckpt 2109: Recorded Tj's request (Check odds now counter: +EV/−EV counts, % +EV, rese
  3e6dce93 ckpt 2108: v0.23.0 (code 51) released and recorded; B1-B5 ticked
  a65d1c12 ckpt 2107: pre-release: v0.23.0: the Novig management key is entered once and saved on 
  a62443f2 ckpt 2106: B1-B4 ticked; version 0.23.0 (code 51); sweep fixes (keep() rename, section 
  bd62a7ef ckpt 2105: docs updated (NOVIG_API.md §14 saved management key, NovigSetup/NovigBettin
  ca5ef53e ckpt 2104: B1-B4 implemented and targeted tests green: ManagementKeyStoreTest (6), Wall
  121e16e5 ckpt 2103: B1-B4 code written (not compiled yet): ManagementKeyStore (data, sealed by K
  ae1154d4 ckpt 2102: Recorded Tj's request (wallet top-up by typed amount in Settings; 'add money
```
