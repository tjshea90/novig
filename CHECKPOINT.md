# CHECKPOINT 516 — read me first, then TASKS.md

**Written:** 2026-09-27T17:29:00Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `2be5c99` (this checkpoint is the commit after it)

## Just done
U3 part 2: PropLine last_seen_at per quote; re-use capped at 2 min everywhere (settings defaults/choices, sources); feed/widget/mini/Games/bet sheet hide EVs past 5 min (feedAt, fairIsOld, 'Odds too old to compare', 'odds aging'); recheck scans instead near the limit; CNO rows hidden when CNO's odds >5 min old, books pages >5 min not shown/agreed (booksAt), green-check lane re-reads every 4 min. Tests: FreshOddsTest (4 failed on old code), FreshOddsAppTest 4, PropLineClientTest last-seen, ScreenshotTest oldOddsLeaveTheFeed/agingOdds/cnoTab. app 161 pass

## Do this next
Widget rescan hint copy; RESEARCH §24.3; CLAUDE.md; tick T4/U2-U3; bump v0.16.4 code 31; full floor + screenshots; ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0fec760 ckpt 515: U3 part 1: Freshness (5 min per quote, 2 min re-use), Scanner stamps each quot
  af6c68b ckpt 514: U1 audit done: RESEARCH.md §24.1 (re-use windows 15-60 min, stale-limit keep,
  70c9568 ckpt 513: T1-T3 done: PropLine relays Novig's prices in the same calls (RefSnapshot.novi
  b9a59a9 ckpt 512: Wrote Tj's second request (no stale sportsbook odds in any comparison; after T
  2abdfb9 ckpt 511: Wrote Tj's request (PropLine's Novig prices order Novig reads, fallback to ori
  c15cb5c ckpt 510: SHIPPED v0.16.3 code 30 (S1-S5 ticked): https://github.com/tjshea90/novig/rele
  e46361a ckpt 509: pre-release: v0.16.3: PropLine first for sportsbook odds, The Odds API only as
  51e51af ckpt 508: S1-S4 ticked: fallback chain built and tested, full-test fixes done, floor 567
  c066f28 ckpt 507: S4 sweep fixes 3-4: player-roster lane gated on CNO on (UiState.cnoTeamRows; w
  6663b09 ckpt 506: S4 sweep fix 2: a fallback standing by drops its own older snapshot (Odds API'
```

(16 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
