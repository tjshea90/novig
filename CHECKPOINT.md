# CHECKPOINT 2110 — read me first, then TASKS.md

**Written:** 2026-09-29T23:07:48Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `d4d9f0a0` (this checkpoint is the commit after it)

## Just done
C1/C2 built and tested: CheckOddsStats (data; CheckOddsStatsTest 5), counter row pinned in the Tracker bar + caption item below (TrackerUiTest 5 new, StickyHeadersTest pinned + 189dp limit), checkStartedAtMs saved in last_check.json and reloaded (CheckOddsCounterAppTest). Restored StickyHeadersTest after a script truncated it (autosave 22f46a96 had committed it empty; restored from 22f46a96~1)

## Do this next
render screenshot 4i and look at it, full floor, sweep, bump 0.24.0 code 52, tick C1/C2, ship, release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6ba15144 ckpt 2109: Recorded Tj's request (Check odds now counter: +EV/−EV counts, % +EV, rese
  3e6dce93 ckpt 2108: v0.23.0 (code 51) released and recorded; B1-B5 ticked
  a65d1c12 ckpt 2107: pre-release: v0.23.0: the Novig management key is entered once and saved on 
  a62443f2 ckpt 2106: B1-B4 ticked; version 0.23.0 (code 51); sweep fixes (keep() rename, section 
  bd62a7ef ckpt 2105: docs updated (NOVIG_API.md §14 saved management key, NovigSetup/NovigBettin
  ca5ef53e ckpt 2104: B1-B4 implemented and targeted tests green: ManagementKeyStoreTest (6), Wall
  121e16e5 ckpt 2103: B1-B4 code written (not compiled yet): ManagementKeyStore (data, sealed by K
  ae1154d4 ckpt 2102: Recorded Tj's request (wallet top-up by typed amount in Settings; 'add money
  4fa76cb1 ckpt 2101: v0.22.0 (code 50) released and recorded; A1-A6 ticked
  c1e67e57 ckpt 2100: pre-release: v0.22.0: the widget opens only from its button; Settings in sev
```

(14 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
