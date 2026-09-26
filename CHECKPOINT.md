# CHECKPOINT 396 — read me first, then TASKS.md

**Written:** 2026-09-26T01:57:08Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-betting-research-dkp593` · **builds on:** `7ba613f` (this checkpoint is the commit after it)

## Just done
C1 started: local SDK + Maven/Robolectric mirror set up (build trap 6); full floor green locally (237 tests, 2 live skipped). Research so far: CNO lists Novig with liquidity ($5-$15 rows, 4.8-6.1% EV), disclosed worst-case avg/median devig; OddsAssist consensus top rows are +2400..+4900 longshots on OG/Kalshi/ESPN Bet (28-76%), its Pinnacle page shows sane 2.7-4.9%. Found scanner bug: metered source that fails on league 1 skips later leagues without dropping their stale snapshots, and the final result prices with them.

## Do this next
Fix the stale-snapshot bug (failing test first), finish sweep (keys, store, tracker, UI, background), then features: median outlier guard, max-odds filter, maker bid price, devigger cross-check link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  449efd7 ckpt 395: Logged Tj's 2026-09-26 request (full tests, feature/scan improvements, OddsAss
  aa9d099 ckpt 394: SHIPPED v0.10.0 (code 14): release 36174969512 green, Release confirmed with v
  1e820db ckpt 393: pre-release: v0.10.0 (code 14): scans keep running in the background (foregrou
  6f96317 ckpt 392: CI 36174016113: all compiled; 175/177 data tests green; the 2 runner tests use
  6c401d2 ckpt 391: CI 36173670267: only error was ScannerTest's trailing progress lambda binding 
  7469ec6 ckpt 390: B1-B5 code drafted (not compiled yet: local Gradle blocked by Maven Central 42
  180810d ckpt 389: Logged Tj's 2026-09-25 ~18:05Z request (background scan, speed/streaming, Odds
  3528dbb ckpt 388: Shipped v0.9.0 (code 13): release.yml green, Release confirmed with vigilant-v
  4fe72d2 ckpt 387: pre-release: v0.9.0: sportsbook player props (DraftKings/FanDuel/BetMGM/Caesar
  a621d44 ckpt 386: Docs (RESEARCH §14, BRIEF sportsbook-props decision, CLAUDE surface list, TAS
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
